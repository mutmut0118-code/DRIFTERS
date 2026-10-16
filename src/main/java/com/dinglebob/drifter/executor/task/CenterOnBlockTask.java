package com.dinglebob.drifter.executor.task;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;

import net.minecraft.world.phys.Vec3;

/** Shuffles to the middle of the block it stands on, slowly (sneaking), with the movement keys. */
public class CenterOnBlockTask extends Task {
	private final Vec3 middle;

	public CenterOnBlockTask(DrifterEntity drifter, Vec3 middle) {
		super(drifter);
		this.middle = middle;
	}

	@Override
	public String describe() {
		return "step to the middle of the block";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 3;
	}

	@Override
	protected Status onTick() {
		if (Math.abs(this.drifter.getX() - this.middle.x) <= 0.15 && Math.abs(this.drifter.getZ() - this.middle.z) <= 0.15) {
			com.dinglebob.drifter.motor.Steering.stopMoving(this.drifter);
			this.drifter.getKeys().set(com.dinglebob.drifter.motor.VirtualKeys.Key.SNEAK, false);
			return Status.SUCCESS;
		}

		// Not on soul sand: it already slows it to a crawl, and sneaking there it didn't move at all (found in testing: the
		// shuffle before a pillar on soul sand under a fortress never got anywhere).
		boolean soulSand = this.drifter.level().getBlockState(this.drifter.getBlockPosBelowThatAffectsMyMovement())
			.is(net.minecraft.world.level.block.Blocks.SOUL_SAND);
		this.drifter.getKeys().set(com.dinglebob.drifter.motor.VirtualKeys.Key.SNEAK, !soulSand);
		com.dinglebob.drifter.motor.Steering.pressToward(this.drifter, this.middle);
		return Status.RUNNING;
	}

	@Override
	protected void onStop() {
		com.dinglebob.drifter.motor.Steering.stopMoving(this.drifter);
		this.drifter.getKeys().set(com.dinglebob.drifter.motor.VirtualKeys.Key.SNEAK, false);
	}
}
