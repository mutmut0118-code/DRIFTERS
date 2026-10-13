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
 * How it switches is a habit (section 16.3): scrollers visibly cycle through the slots in between (the items flash
 * in its hand), number-keyers snap straight to the slot. Low Skill sometimes grabs the slot next to it first and then
 * corrects (section 16.4 "wrong slot, then correct").
 * TODO(milestone 8): the per-Drifter hotbar layout (section 10.18).
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
	/** The hotbar slot it's heading for, and whether it grabbed the wrong one first. */
	private int targetSlot = -1;
	private boolean wrongFirst;

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
					this.switchTo(hotbarSlot);
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
				this.switchTo(target);
				return Status.RUNNING;
			}
			default -> {
				if (inventory.isSwitching() || --this.pause > 0) {
					return Status.RUNNING;
				}

				if (inventory.getSelected() != this.targetSlot) {
					this.stepToward(inventory);
					return Status.RUNNING;
				}

				return this.wanted.test(inventory.getSelectedItem()) ? Status.SUCCESS : this.fail(this.label + " moved while switching");
			}
		}
	}

	private void switchTo(int slot) {
		DrifterInventory inventory = this.drifter.getInventory();
		this.targetSlot = slot;
		this.phase = Phase.SWITCHING;
		com.dinglebob.drifter.personality.Personality p = this.drifter.getPersonality();
		float slip = (1.0F - p.getF(com.dinglebob.drifter.personality.Trait.SKILL)) * 0.12F
			+ (p.is(com.dinglebob.drifter.personality.Mood.PANICKED) ? 0.1F : 0.0F);
		if (inventory.getSelected() != slot && this.drifter.getRandom().nextFloat() < slip) {
			// The slot next to the right one, then a beat to notice, then the right one.
			int wrong = Math.floorMod(slot + (this.drifter.getRandom().nextBoolean() ? 1 : -1), DrifterInventory.HOTBAR_SIZE);
			if (wrong != inventory.getSelected()) {
				this.wrongFirst = true;
				inventory.requestSelect(wrong);
				this.pause = 3 + this.drifter.getRandom().nextInt(4);
				return;
			}
		}

		this.stepToward(inventory);
	}

	/** One move toward the target slot: a scroll notch for a scroller, the number key for everyone else. */
	private void stepToward(DrifterInventory inventory) {
		int from = inventory.getSelected();
		if (from == this.targetSlot) {
			return;
		}

		if (!this.drifter.getPersonality().habits().scroller) {
			inventory.requestSelect(this.targetSlot);
			return;
		}

		int forward = Math.floorMod(this.targetSlot - from, DrifterInventory.HOTBAR_SIZE);
		int step = forward <= DrifterInventory.HOTBAR_SIZE / 2 ? 1 : -1;
		inventory.requestSelect(Math.floorMod(from + step, DrifterInventory.HOTBAR_SIZE), 1);
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
