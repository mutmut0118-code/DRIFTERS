package com.dinglebob.drifter.executor.task;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.Task;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Equip (armor part, DESIGN.md sections 8 and 10.17): opens its inventory and puts on the best armor it carries,
 * one piece per click, swapping out worse pieces. "Best" is the armor value of its attributes with its taste in it (GearValue, GearTaste).
 */
public class WearArmorTask extends Task {
	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private int pause;
	private int wait;
	private int slotIndex;
	private int wornPieces;

	public WearArmorTask(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "put on its best armor and shield";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 10;
	}

	@Override
	protected void onStart() {
		this.drifter.getKeys().releaseAll();
		this.pause = InventoryPause.roll(this.drifter, InventoryPause.MOVE_ITEM_MIN, InventoryPause.MOVE_ITEM_MAX);
	}

	@Override
	protected Status onTick() {
		if (--this.pause > 0 || --this.wait > 0) {
			return Status.RUNNING;
		}

		while (this.slotIndex < ARMOR_SLOTS.length) {
			EquipmentSlot slot = ARMOR_SLOTS[this.slotIndex++];
			if (this.wearBest(slot)) {
				this.wornPieces++;
				this.wait = InventoryPause.CLICK_TICKS;
				return Status.RUNNING;
			}
		}

		// A shield goes in the offhand (section 13, "Shield").
		if (this.slotIndex == ARMOR_SLOTS.length) {
			this.slotIndex++;
			int shield = shieldSlot(this.drifter);
			if (shield >= 0) {
				this.drifter.getInventory().swap(shield, DrifterInventory.OFFHAND_SLOT);
				this.wornPieces++;
				this.wait = InventoryPause.CLICK_TICKS;
				return Status.RUNNING;
			}
		}

		if (this.wornPieces > 0) {
			// Section 16.5 "First iron armor: equips it piece by piece, then a quick spin" (and a little confidence).
			this.drifter.getPersonality().feel(com.dinglebob.drifter.personality.Mood.CONFIDENT, 0.1 * this.wornPieces, "new gear");
			if (this.drifter.getArmorValue() >= 6) {
				this.drifter.getMannerisms().event(com.dinglebob.drifter.mannerism.Mannerisms.Event.NEW_GEAR);
			}

			return Status.SUCCESS;
		}

		return this.fail("has no better armor to put on");
	}

	/** Has armor better than what it wears, or a shield it isn't holding in its offhand. */
	public static boolean hasUpgrade(DrifterEntity drifter) {
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			if (bestIndex(drifter, slot) >= 0) {
				return true;
			}
		}

		return shieldSlot(drifter) >= 0;
	}

	private static final double GOLD_IN_THE_NETHER = 1000.0;

	/** In the Nether, or on its way there. */
	private static boolean goldTime(DrifterEntity drifter) {
		var trip = drifter.getDrifterBrain().netherTrip();
		return drifter.level().dimension() == net.minecraft.world.level.Level.NETHER
			|| trip != null && trip.phase == com.dinglebob.drifter.brain.NetherTrip.Phase.GOING;
	}

	/** It should be wearing its gold piece now and isn't, and it has one to put on. */
	public static boolean goldPending(DrifterEntity drifter) {
		if (!goldTime(drifter) || PiglinAiAccess.wearingGold(drifter)) {
			return false;
		}

		DrifterInventory inventory = drifter.getInventory();
		for (int i = 0; i < DrifterInventory.MAIN_SIZE; i++) {
			if (inventory.getItem(i).getItem() instanceof ArmorItem armor && armor.getMaterial().is(net.minecraft.world.item.ArmorMaterials.GOLD)) {
				return true;
			}
		}

		return false;
	}

	/** (PiglinAi's own check, so it means exactly what piglins look for.) */
	private static final class PiglinAiAccess {
		static boolean wearingGold(DrifterEntity drifter) {
			return net.minecraft.world.entity.monster.piglin.PiglinAi.isWearingGold(drifter);
		}
	}

	/** A gold piece worn in another armor slot. */
	private static boolean goldElsewhere(DrifterEntity drifter, EquipmentSlot slot) {
		for (EquipmentSlot other : ARMOR_SLOTS) {
			if (other != slot && drifter.getItemBySlot(other).getItem() instanceof ArmorItem armor
				&& armor.getMaterial().is(net.minecraft.world.item.ArmorMaterials.GOLD)) {
				return true;
			}
		}

		return false;
	}

	/** Its best shield in the main inventory, if the offhand holds no shield or a worse one, or -1. */
	private static int shieldSlot(DrifterEntity drifter) {
		int best = com.dinglebob.drifter.hands.Shields.bestSlot(drifter);
		ItemStack held = drifter.getOffhandItem();
		if (best < 0 || com.dinglebob.drifter.hands.Shields.isShield(held) && com.dinglebob.drifter.hands.Shields.score(held) >= com.dinglebob.drifter.hands.Shields.score(drifter.getInventory().getItem(best))) {
			return -1;
		}

		return best;
	}

	private boolean wearBest(EquipmentSlot slot) {
		return wearBest(this.drifter, slot);
	}

	/** Puts on its best armor in every slot and its shield, all at once (catch-up, which has no time to walk through it:
	 * found by the user, caught-up Drifters left their armor in the chest and came back in none). */
	public static void equipAllNow(DrifterEntity drifter) {
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			wearBest(drifter, slot);
		}

		int shield = shieldSlot(drifter);
		if (shield >= 0) {
			drifter.getInventory().swap(shield, DrifterInventory.OFFHAND_SLOT);
		}
	}

	private static boolean wearBest(DrifterEntity drifter, EquipmentSlot slot) {
		DrifterInventory inventory = drifter.getInventory();
		int bestSlot = bestIndex(drifter, slot);
		if (bestSlot < 0) {
			return false;
		}

		ItemStack newPiece = inventory.getItem(bestSlot);
		ItemStack oldPiece = drifter.getItemBySlot(slot);
		inventory.setItem(bestSlot, oldPiece);
		drifter.setItemSlot(slot, newPiece);
		return true;
	}

	/**
	 * The inventory slot of the piece it would rather wear than what it wears in this slot, or -1 to keep what it has. The
	 * good-enough rule applies per slot (taste only chooses among pieces close to the best value, so a flashy Drifter keeps
	 * iron over leather), and the choice is the same every time for the same items, so a piece it just took off never looks
	 * better again.
	 */
	private static int bestIndex(DrifterEntity drifter, EquipmentSlot slot) {
		DrifterInventory inventory = drifter.getInventory();
		ItemStack worn = drifter.getItemBySlot(slot);
		double best = rawScore(drifter, worn, slot);
		for (int i = 0; i < DrifterInventory.MAIN_SIZE; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				best = Math.max(best, rawScore(drifter, stack, slot));
			}
		}

		if (best <= 0.0) {
			return -1;
		}

		double threshold = com.dinglebob.drifter.gear.GearTaste.goodEnoughFraction() * best;
		double top = !worn.isEmpty() && rawScore(drifter, worn, slot) >= threshold ? score(drifter, worn, slot) : (worn.isEmpty() ? 0.0 : -1.0);
		int chosen = -1;
		for (int i = 0; i < DrifterInventory.MAIN_SIZE; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}

			double raw = rawScore(drifter, stack, slot);
			if (raw <= 0.0 || raw < threshold) {
				continue;
			}

			double liked = com.dinglebob.drifter.gear.GearTaste.liking(drifter, stack, raw);
			if (liked > top) {
				top = liked;
				chosen = i;
			}
		}

		return chosen;
	}

	/** What a piece is worth in this slot with its taste in it: gold first in the Nether, else its attribute value; empty 0; else -1. */
	public static double score(DrifterEntity drifter, ItemStack stack, EquipmentSlot slot) {
		double raw = rawScore(drifter, stack, slot);
		return raw <= 0.0 ? raw : com.dinglebob.drifter.gear.GearTaste.liking(drifter, stack, raw);
	}

	/** {@link #score} without taste. */
	private static double rawScore(DrifterEntity drifter, ItemStack stack, EquipmentSlot slot) {
		if (stack.isEmpty()) {
			return 0.0;
		}

		if (stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == slot) {
			// In the Nether one gold piece comes first, the one thing that keeps piglins friendly (section 19 piglin
			// etiquette; it packs one for the trip): over iron in its slot, unless another slot already has gold.
			if (armor.getMaterial().is(net.minecraft.world.item.ArmorMaterials.GOLD) && goldTime(drifter) && !goldElsewhere(drifter, slot)) {
				return GOLD_IN_THE_NETHER + armor.getDefense();
			}
		}

		// An elytra, pumpkin or head has no armor value: never the choice (and never replaces a chestplate).
		return com.dinglebob.drifter.gear.GearValue.wornSlot(drifter, stack) == slot ? com.dinglebob.drifter.gear.GearValue.armor(stack, slot) : -1.0;
	}
}
