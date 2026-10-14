package com.dinglebob.drifter.executor.task;

import java.util.function.Predicate;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.motor.CameraModel;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.ItemStack;

/**
 * Looting a chest minecart (a mineshaft's loot, DESIGN.md section 10.11): look at it, open it (entity reach, like a
 * player), look over what's inside for a moment, then shift-click out what it wants, one stack per click.
 */
public class LootCartTask extends Task {
	private enum Phase {
		LOOK, LOOK_OVER, TRANSFER
	}

	private final MinecartChest cart;
	private final Predicate<ItemStack> filter;
	private Phase phase = Phase.LOOK;
	private int wait;
	private int taken;

	public LootCartTask(DrifterEntity drifter, MinecartChest cart, Predicate<ItemStack> filter) {
		super(drifter);
		this.cart = cart;
		this.filter = filter;
	}

	@Override
	public String describe() {
		return "loot the chest minecart";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 20;
	}

	@Override
	protected Status onTick() {
		if (this.cart.isRemoved()) {
			return this.fail("the minecart is gone");
		}

		double reach = this.drifter.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
		if (this.cart.getBoundingBox().distanceToSqr(this.drifter.getEyePosition()) > reach * reach) {
			return this.taken > 0 ? Status.SUCCESS : this.fail("out of reach");
		}

		switch (this.phase) {
			case LOOK -> {
				this.drifter.getCamera().track(this.cart.getBoundingBox().getCenter(), CameraModel.TurnSpeed.NORMAL);
				if (this.drifter.getCamera().isSettled()) {
					this.drifter.swing(InteractionHand.MAIN_HAND);
					this.drifter.getKeys().releaseAll();
					this.wait = InventoryPause.roll(this.drifter, InventoryPause.CONTAINER_MIN, InventoryPause.CONTAINER_MAX);
					this.phase = Phase.LOOK_OVER;
				}

				return Status.RUNNING;
			}
			case LOOK_OVER -> {
				if (--this.wait <= 0) {
					this.phase = Phase.TRANSFER;
				}

				return Status.RUNNING;
			}
			default -> {
				if (--this.wait > 0) {
					return Status.RUNNING;
				}

				this.wait = InventoryPause.CLICK_TICKS;
				for (int slot = 0; slot < this.cart.getContainerSize(); slot++) {
					ItemStack stack = this.cart.getItem(slot);
					if (stack.isEmpty() || !this.filter.test(stack)) {
						continue;
					}

					ItemStack moving = stack.copy();
					this.drifter.getInventory().add(moving);
					int moved = stack.getCount() - moving.getCount();
					if (moved > 0) {
						stack.shrink(moved);
						this.cart.setChanged();
						this.taken += moved;
						return Status.RUNNING;
					}
				}

				return this.taken > 0 ? Status.SUCCESS : this.fail("nothing worth taking, or no room");
			}
		}
	}

	@Override
	protected void onStop() {
		this.drifter.getCamera().clearTarget();
	}
}
