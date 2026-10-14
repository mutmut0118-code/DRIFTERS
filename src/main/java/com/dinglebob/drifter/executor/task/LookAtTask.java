package com.dinglebob.drifter.executor.task;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.motor.CameraModel;

import net.minecraft.world.phys.Vec3;

/** Looks at a spot for a moment (peeking into a hole, taking in a cave): its senses do the rest. */
public class LookAtTask extends Task {
	private final Vec3 point;
	private final int ticks;
	private int looked;

	public LookAtTask(DrifterEntity drifter, Vec3 point, int ticks) {
		super(drifter);
		this.point = point;
		this.ticks = ticks;
	}

	@Override
	public String describe() {
		return "look into the opening";
	}

	@Override
	protected int timeoutTicks() {
		return this.ticks + 40;
	}

	@Override
	protected Status onTick() {
		this.drifter.getKeys().releaseAll();
		this.drifter.getCamera().track(this.point, CameraModel.TurnSpeed.NORMAL);
		if (this.drifter.getCamera().isSettled() && ++this.looked >= this.ticks) {
			return Status.SUCCESS;
		}

		return Status.RUNNING;
	}

	@Override
	protected void onStop() {
		this.drifter.getCamera().clearTarget();
	}
}
