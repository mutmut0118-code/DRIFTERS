package com.dinglebob.drifter.executor.task;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.Task;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Equip (armor part, DESIGN.md sections 8 and 10.17): opens its inventory and puts on the best armor it carries,
 * one piece per click, swapping out worse pieces. "Best" is armor points, then toughness.
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
		return "put on its best armor";
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

		return this.wornPieces > 0 ? Status.SUCCESS : this.fail("has no better armor to put on");
	}

	private boolean wearBest(EquipmentSlot slot) {
		DrifterInventory inventory = this.drifter.getInventory();
		int bestSlot = -1;
		double bestScore = score(this.drifter.getItemBySlot(slot), slot);
		for (int i = 0; i < DrifterInventory.MAIN_SIZE; i++) {
			double candidate = score(inventory.getItem(i), slot);
			if (candidate > bestScore) {
				bestScore = candidate;
				bestSlot = i;
			}
		}

		if (bestSlot < 0) {
			return false;
		}

		ItemStack newPiece = inventory.getItem(bestSlot);
		ItemStack oldPiece = this.drifter.getItemBySlot(slot);
		inventory.setItem(bestSlot, oldPiece);
		this.drifter.setItemSlot(slot, newPiece);
		return true;
	}

	private static double score(ItemStack stack, EquipmentSlot slot) {
		if (stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == slot) {
			return armor.getDefense() * 10.0 + armor.getToughness();
		}

		return stack.isEmpty() ? 0.0 : -1.0;
	}
}
