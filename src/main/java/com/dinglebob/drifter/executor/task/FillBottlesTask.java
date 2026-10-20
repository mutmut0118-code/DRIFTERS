package com.dinglebob.drifter.executor.task;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.skills.Potions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Filling glass bottles at the water (for brewing): a bottle in hand, a look at the water, a right-click each (the bottle
 * item's own use, by its helper player: water bottles out of the water's source blocks only).
 */
public class FillBottlesTask extends Task {
	private final BlockPos water;
	private final int wanted;
	@Nullable
	private SelectItemTask select;
	private int filled;
	private int ticks;
	private int misses;

	public FillBottlesTask(DrifterEntity drifter, BlockPos water, int wanted) {
		super(drifter);
		this.water = water.immutable();
		this.wanted = wanted;
	}

	@Override
	public String describe() {
		return "fill " + this.wanted + " bottle" + (this.wanted == 1 ? "" : "s") + " with water";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 20;
	}

	@Override
	protected Status onTick() {
		this.drifter.getKeys().releaseAll();
		if (this.filled >= this.wanted || this.drifter.getInventory().count(stack -> stack.is(Items.GLASS_BOTTLE)) == 0) {
			return this.filled > 0 ? Status.SUCCESS : this.fail("has no empty bottles");
		}

		if (!this.drifter.getMainHandItem().is(Items.GLASS_BOTTLE)) {
			if (this.select == null) {
				this.select = new SelectItemTask(this.drifter, stack -> stack.is(Items.GLASS_BOTTLE), "a glass bottle");
			}

			if (this.select.tick() == Status.FAILURE) {
				return this.fail("has no empty bottles");
			}

			return Status.RUNNING;
		}

		this.select = null;
		Level level = this.drifter.level();
		Vec3 surface = new Vec3(this.water.getX() + 0.5, this.water.getY() + level.getFluidState(this.water).getHeight(level, this.water) - 0.05,
			this.water.getZ() + 0.5);
		this.drifter.getCamera().lookAt(surface, CameraModel.TurnSpeed.NORMAL);
		if (!this.drifter.getCamera().isSettled() || ++this.ticks < 4) {
			return Status.RUNNING;
		}

		this.ticks = 0;
		int before = this.drifter.getInventory().count(Potions::isWater);
		FakePlayerBridge.act(this.drifter, fake -> fake.gameMode.useItem(fake, fake.level(), fake.getMainHandItem(), InteractionHand.MAIN_HAND));
		int after = this.drifter.getInventory().count(Potions::isWater);
		if (after > before) {
			this.drifter.swing(InteractionHand.MAIN_HAND);
			this.filled += after - before;
		} else if (++this.misses > 4) {
			return this.filled > 0 ? Status.SUCCESS : this.fail("couldn't get water into the bottle");
		}

		return Status.RUNNING;
	}

	@Override
	protected void onStop() {
		if (this.select != null) {
			this.select.stop();
		}
	}
}
