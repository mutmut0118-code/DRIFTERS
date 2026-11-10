package com.dinglebob.drifter.executor.task;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.dinglebob.drifter.compat.TrinketsCompat;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.gear.GearKinds;
import com.dinglebob.drifter.gear.GearTaste;
import com.dinglebob.drifter.gear.GearValue;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;

/**
 * Equip (trinkets part; Trinkets, optional): opens its inventory and puts the trinkets it carries in its trinket slots, one
 * slot per click, swapping out worse ones, the way {@link WearArmorTask} does armor. "Best" is the trinket's value
 * ({@link GearValue#trinketIn}: its attribute modifiers there, the powers it believes it has, its rarity) with its taste in
 * it, and the good-enough rule applies per slot. Only what it carries goes on. When it dies they drop by Trinkets' own rules
 * (Trinkets drops any mob's trinket slots at the end of its death drops), as a player's do.
 */
public class WearTrinketsTask extends Task {
	/** How long a "has an upgrade" answer stands before it is worked out again (the equip goal asks often). */
	public static final int UPGRADE_CHECK_TICKS = 40;

	/** The last answer to {@link #hasUpgrade} per Drifter (server thread only). */
	private static final Map<DrifterEntity, Check> CHECKS = new WeakHashMap<>();

	private static final class Check {
		long at = Long.MIN_VALUE;
		boolean upgrade;
	}

	private int pause;
	private int wait;
	private int slotIndex;
	private int worn;
	private List<TrinketsCompat.Slot> slots = List.of();

	public WearTrinketsTask(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "put on its best trinkets";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 10;
	}

	@Override
	protected void onStart() {
		this.drifter.getKeys().releaseAll();
		this.pause = InventoryPause.roll(this.drifter, InventoryPause.MOVE_ITEM_MIN, InventoryPause.MOVE_ITEM_MAX);
		this.slots = TrinketsCompat.slots(this.drifter);
	}

	@Override
	protected void onStop() {
		forget(this.drifter);
	}

	@Override
	protected Status onTick() {
		if (--this.pause > 0 || --this.wait > 0) {
			return Status.RUNNING;
		}

		while (this.slotIndex < this.slots.size()) {
			if (wearBest(this.drifter, this.slots.get(this.slotIndex++))) {
				this.worn++;
				this.wait = InventoryPause.CLICK_TICKS;
				return Status.RUNNING;
			}
		}

		if (this.worn > 0) {
			this.drifter.getPersonality().feel(com.dinglebob.drifter.personality.Mood.CONFIDENT, 0.05 * this.worn, "new trinkets");
			return Status.SUCCESS;
		}

		return this.fail("has no better trinket to put on");
	}

	/**
	 * It carries a trinket it would rather wear than what is in one of its slots. Cheap when it can't matter: false at once
	 * without Trinkets or with no trinket in its inventory; otherwise the answer stands for {@link #UPGRADE_CHECK_TICKS}.
	 */
	public static boolean hasUpgrade(DrifterEntity drifter) {
		if (!TrinketsCompat.active() || !carriesTrinket(drifter)) {
			return false;
		}

		long now = drifter.level().getGameTime();
		Check check = CHECKS.get(drifter);
		if (check == null) {
			check = new Check();
			CHECKS.put(drifter, check);
		}

		if (now - check.at >= 0L && now - check.at < UPGRADE_CHECK_TICKS) {
			return check.upgrade;
		}

		check.at = now;
		check.upgrade = false;
		for (TrinketsCompat.Slot slot : TrinketsCompat.slots(drifter)) {
			if (bestIndex(drifter, slot) >= 0) {
				check.upgrade = true;
				break;
			}
		}

		return check.upgrade;
	}

	/** The next {@link #hasUpgrade} works it out afresh (what it wears or carries just changed). */
	private static void forget(DrifterEntity drifter) {
		Check check = CHECKS.get(drifter);
		if (check != null) {
			check.at = Long.MIN_VALUE;
		}
	}

	private static boolean carriesTrinket(DrifterEntity drifter) {
		DrifterInventory inventory = drifter.getInventory();
		for (int i = 0; i < DrifterInventory.MAIN_SIZE; i++) {
			if (wearable(inventory.getItem(i))) {
				return true;
			}
		}

		return false;
	}

	/**
	 * A trinket it would put in a trinket slot: not a weapon, armor, a shield or a totem, even if a pack tags one as a trinket
	 * (those it holds or wears the usual way).
	 */
	private static boolean wearable(ItemStack stack) {
		return !stack.isEmpty() && TrinketsCompat.isTrinket(stack) && GearKinds.of(stack) == null && GearValue.armorSlot(stack) == null
			&& !(stack.getItem() instanceof ShieldItem) && !stack.is(Items.TOTEM_OF_UNDYING);
	}

	/** Puts its best trinket in every slot at once (catch-up, which has no time to walk through it, like WearArmorTask's). */
	public static void equipAllNow(DrifterEntity drifter) {
		if (!TrinketsCompat.active() || !carriesTrinket(drifter)) {
			return;
		}

		for (TrinketsCompat.Slot slot : TrinketsCompat.slots(drifter)) {
			wearBest(drifter, slot);
		}

		forget(drifter);
	}

	/** Swaps its best carried trinket for this slot into it; false if it keeps what it has there. */
	private static boolean wearBest(DrifterEntity drifter, TrinketsCompat.Slot slot) {
		int index = bestIndex(drifter, slot);
		if (index < 0) {
			return false;
		}

		DrifterInventory inventory = drifter.getInventory();
		ItemStack carried = inventory.getItem(index);
		ItemStack old = slot.worn();
		if (carried.getCount() > 1 && !old.isEmpty() && inventory.getFreeSlot() < 0) {
			return false;
		}

		ItemStack one = carried.copyWithCount(1);
		carried.shrink(1);
		if (carried.isEmpty()) {
			inventory.setItem(index, old);
		} else {
			inventory.setItem(index, carried);
			if (!old.isEmpty()) {
				inventory.add(old);
			}
		}

		slot.inventory().setItem(slot.index(), one);
		return true;
	}

	/**
	 * The inventory slot of the trinket it would rather have in this slot than what is there, or -1. As
	 * {@code WearArmorTask.bestIndex}: the good-enough rule per slot, and the same choice every time for the same items, so a
	 * trinket it just took off never looks better again.
	 */
	private static int bestIndex(DrifterEntity drifter, TrinketsCompat.Slot slot) {
		DrifterInventory inventory = drifter.getInventory();
		ItemStack worn = slot.worn();
		double wornRaw = worn.isEmpty() ? 0.0 : GearValue.trinketIn(drifter, slot, worn);
		double best = wornRaw;
		boolean any = false;
		for (int i = 0; i < DrifterInventory.MAIN_SIZE; i++) {
			ItemStack stack = inventory.getItem(i);
			if (wearable(stack) && TrinketsCompat.fits(drifter, slot, stack)) {
				any = true;
				best = Math.max(best, GearValue.trinketIn(drifter, slot, stack));
			}
		}

		if (!any || best <= 0.0) {
			return -1;
		}

		double threshold = GearTaste.goodEnoughFraction() * best;
		double top = worn.isEmpty() ? 0.0 : wornRaw >= threshold ? GearTaste.liking(drifter, worn, wornRaw) : -1.0;
		int chosen = -1;
		for (int i = 0; i < DrifterInventory.MAIN_SIZE; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!wearable(stack) || !TrinketsCompat.fits(drifter, slot, stack)) {
				continue;
			}

			double raw = GearValue.trinketIn(drifter, slot, stack);
			if (raw <= 0.0 || raw < threshold) {
				continue;
			}

			double liked = GearTaste.liking(drifter, stack, raw);
			if (liked > top) {
				top = liked;
				chosen = i;
			}
		}

		return chosen;
	}
}
