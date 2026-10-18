package com.dinglebob.drifter.executor.task;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.motor.CameraModel;

/**
 * A slow look around (DESIGN.md section 24, "slow 360 look"): turns the head a quarter turn at a time, pausing at each,
 * so its eyes take in everything around it. Standing still.
 */
public class LookAroundTask extends Task {
	private static final int PAUSE_TICKS = 14;
	private static final int TURNS = 4;
	private final int turns;
	private final int pauseTicks;
	private int turn;
	private int pause;
	private float startYaw;

	public LookAroundTask(DrifterEntity drifter) {
		this(drifter, TURNS, PAUSE_TICKS);
	}

	/** A quicker one (a glance round a corner while searching for someone): fewer turns, shorter pauses. */
	public LookAroundTask(DrifterEntity drifter, int turns, int pauseTicks) {
		super(drifter);
		this.turns = turns;
		this.pauseTicks = pauseTicks;
	}

	@Override
	public String describe() {
		return "look around";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 10;
	}

	/** The head is busy looking around; hands and legs are free (a crouch-spam after a kill, a hotbar fidget). */
	@Override
	public java.util.Set<BodyPart> freeParts() {
		return java.util.EnumSet.of(BodyPart.HANDS, BodyPart.LEGS);
	}

	@Override
	protected void onStart() {
		this.drifter.getKeys().releaseAll();
		this.startYaw = this.drifter.getYRot();
	}

	@Override
	protected Status onTick() {
		if (this.pause > 0) {
			this.pause--;
			return Status.RUNNING;
		}

		if (this.turn >= this.turns) {
			return Status.SUCCESS;
		}

		this.turn++;
		float pitch = this.turn % 2 == 0 ? 15.0F : 5.0F;
		this.drifter.getCamera().lookAt(this.startYaw + 360.0F / this.turns * this.turn, pitch,
			this.turns < TURNS ? CameraModel.TurnSpeed.NORMAL : CameraModel.TurnSpeed.IDLE);
		this.pause = this.pauseTicks;
		return Status.RUNNING;
	}
}
