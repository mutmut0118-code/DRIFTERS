package com.dinglebob.drifter.executor.task;

import java.util.Locale;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;

import net.minecraft.util.Mth;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.phys.Vec3;

/**
 * MoveTo, STRAIGHT-LINE STUB ONLY: faces the target and walks there with the virtual keys, sprinting when it's more
 * than 10 blocks away (section 10.9) and jumping when it bumps into a 1-block step. No pathfinding: it can't go
 * around obstacles, cross gaps or dig. The real pathfinder is milestone 4; this exists so milestone 2's scripted
 * test can walk to a tree on open ground.
 */
public class MoveToTask extends Task {
	/** Sprint when farther than this (section 10.9). */
	private static final double SPRINT_DISTANCE = 10.0;
	/** "Looks slightly down when walking" (section 16.1). */
	private static final float WALKING_PITCH = 10.0F;
	/** Fail if the distance hasn't improved by this much over STUCK_TICKS. */
	private static final double STUCK_PROGRESS = 0.5;
	private static final int STUCK_TICKS = 60;
	/** Arriving counts only if the feet are within this many blocks of the target's height. */
	private static final double MAX_HEIGHT_DIFFERENCE = 1.0;

	private final Vec3 target;
	private final double radius;
	private double bestDistance = Double.MAX_VALUE;
	private int ticksSinceProgress;
	private int timeout;

	public MoveToTask(DrifterEntity drifter, Vec3 target, double radius) {
		super(drifter);
		this.target = target;
		this.radius = radius;
	}

	@Override
	public String describe() {
		return String.format(Locale.ROOT, "walk to %.1f %.1f %.1f", this.target.x, this.target.y, this.target.z);
	}

	@Override
	protected int timeoutTicks() {
		return this.timeout;
	}

	@Override
	protected void onStart() {
		// Walking speed is about 4.3 blocks/s; allow 3x.
		this.timeout = (int)(horizontalDistance(this.drifter.position(), this.target) / 4.3 * 20.0 * 3.0) + 60;
	}

	@Override
	protected Status onTick() {
		double distance = horizontalDistance(this.drifter.position(), this.target);
		if (distance <= this.radius) {
			// Straight-line walking can end up above or below the spot (a cave under it, a ledge over it).
			return Math.abs(this.drifter.getY() - this.target.y) <= MAX_HEIGHT_DIFFERENCE ? Status.SUCCESS
				: this.fail("can't get there: it's at a different height (no pathfinder yet)");
		}

		if (distance < this.bestDistance - STUCK_PROGRESS) {
			this.bestDistance = distance;
			this.ticksSinceProgress = 0;
		} else if (++this.ticksSinceProgress > STUCK_TICKS) {
			return this.fail("stuck (straight-line walking only until the pathfinder exists)");
		}

		double dx = this.target.x - this.drifter.getX();
		double dz = this.target.z - this.drifter.getZ();
		float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		this.drifter.getCamera().lookAt(yaw, WALKING_PITCH, CameraModel.TurnSpeed.NORMAL);

		VirtualKeys keys = this.drifter.getKeys();
		boolean facing = Math.abs(Mth.wrapDegrees(yaw - this.drifter.getYRot())) < 60.0F;
		keys.set(VirtualKeys.Key.FORWARD, facing);
		keys.set(VirtualKeys.Key.SPRINT, facing && distance > SPRINT_DISTANCE && this.drifter.getFoodData().getFoodLevel() > FoodConstants.SPRINT_LEVEL);
		keys.set(VirtualKeys.Key.JUMP, this.drifter.horizontalCollision && this.drifter.onGround());
		return Status.RUNNING;
	}

	@Override
	protected void onStop() {
		this.drifter.getKeys().releaseAll();
		this.drifter.getCamera().clearTarget();
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
