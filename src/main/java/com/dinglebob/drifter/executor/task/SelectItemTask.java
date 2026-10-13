package com.dinglebob.drifter.executor.task;

import java.util.function.Predicate;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.Task;

import net.minecraft.world.item.ItemStack;

/**
 * Equip (hotbar part, DESIGN.md section 8): gets a matching item into the hand. If it's on the hotbar, switches to
 * it (with the 1 to 3 tick human delay). If it's only in the main inventory, the Drifter "opens its inventory"
 * (a short pause standing still) and moves it to the hotbar first.
 * TODO(milestone 7): scroller vs number-key switching, and the per-Drifter hotbar layout (section 10.18).
 */
public class SelectItemTask extends Task {
	private enum Phase {
		FIND, INVENTORY_PAUSE, SWITCHING
	}

	private final Predicate<ItemStack> wanted;
	private final String label;
	private Phase phase = Phase.FIND;
	private int pause;
	private int fromSlot;

	public SelectItemTask(DrifterEntity drifter, Predicate<ItemStack> wanted, String label) {
		super(drifter);
		this.wanted = wanted;
		this.label = label;
	}

	@Override
	public String describe() {
		return "take out " + this.label;
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 5;
	}

	@Override
	protected Status onTick() {
		DrifterInventory inventory = this.drifter.getInventory();
		switch (this.phase) {
			case FIND -> {
				if (!inventory.isSwitching() && this.wanted.test(inventory.getSelectedItem()) && !inventory.getSelectedItem().isEmpty()) {
					return Status.SUCCESS;
				}

				int hotbarSlot = inventory.findSlot(this.wanted, 0, DrifterInventory.HOTBAR_SIZE);
				if (hotbarSlot >= 0) {
					inventory.requestSelect(hotbarSlot);
					this.phase = Phase.SWITCHING;
					return Status.RUNNING;
				}

				this.fromSlot = inventory.findSlot(this.wanted, DrifterInventory.HOTBAR_SIZE, DrifterInventory.MAIN_SIZE);
				if (this.fromSlot < 0) {
					return this.fail("doesn't have " + this.label);
				}

				this.drifter.getKeys().releaseAll();
				this.pause = InventoryPause.roll(this.drifter, InventoryPause.MOVE_ITEM_MIN, InventoryPause.MOVE_ITEM_MAX);
				this.phase = Phase.INVENTORY_PAUSE;
				return Status.RUNNING;
			}
			case INVENTORY_PAUSE -> {
				if (--this.pause > 0) {
					return Status.RUNNING;
				}

				int target = firstEmptyHotbarSlot(inventory);
				if (target < 0) {
					target = inventory.getSelected();
				}

				inventory.swap(this.fromSlot, target);
				inventory.requestSelect(target);
				this.phase = Phase.SWITCHING;
				return Status.RUNNING;
			}
			default -> {
				if (inventory.isSwitching()) {
					return Status.RUNNING;
				}

				return this.wanted.test(inventory.getSelectedItem()) ? Status.SUCCESS : this.fail(this.label + " moved while switching");
			}
		}
	}

	private static int firstEmptyHotbarSlot(DrifterInventory inventory) {
		for (int slot = 0; slot < DrifterInventory.HOTBAR_SIZE; slot++) {
			if (inventory.getItem(slot).isEmpty()) {
				return slot;
			}
		}

		return -1;
	}
}
