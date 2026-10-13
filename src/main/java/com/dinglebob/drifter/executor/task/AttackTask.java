package com.dinglebob.drifter.executor.task;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.Combat;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.Steering;
import com.dinglebob.drifter.motor.VirtualKeys;
import com.dinglebob.drifter.perception.Awareness;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.phys.Vec3;

/**
 * Attack (DESIGN.md section 8): chases a living target and hits it until it dies. Far away or out of sight it uses
 * the pathfinder to follow; close up it steers straight at it with the camera on it, sprinting in for a sprint hit,
 * and swings only at full attack strength with the target under its crosshair within 3 blocks (player reach and
 * cooldown). Chasing a fleeing animal is clumsy, overshooting and turning back, like a player.
 * TODO(milestone 6): combat protocols, strafing, shields, critical-hit timing, retreating.
 */
public class AttackTask extends Task {
	/** Closer than this (and in sight) it steers by hand instead of pathfinding. */
	private static final double CLOSE_RANGE = 5.0;
	/** It stops pressing forward this close, so it doesn't run into the target. */
	private static final double STOP_RANGE = 1.8;
	/** Lost from sight this long, it gives up. */
	private static final int LOST_TICKS = 20 * 6;
	private static final float FULL_STRENGTH = 0.95F;

	private final LivingEntity target;
	private final String label;
	@Nullable
	private NavigateTask follow;
	private int unseenTicks;
	private boolean closeMode;

	public AttackTask(DrifterEntity drifter, LivingEntity target, String label) {
		super(drifter);
		this.target = target;
		this.label = label;
	}

	@Override
	public String describe() {
		return "attack the " + this.label;
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 40;
	}

	@Override
	protected Status onTick() {
		if (!this.target.isAlive() || this.target.isRemoved()) {
			return Status.SUCCESS;
		}

		Awareness awareness = this.drifter.getSenses().awarenessOf(this.target);
		boolean visible = awareness != null && awareness.visibleNow;
		this.unseenTicks = visible ? 0 : this.unseenTicks + 1;
		if (this.unseenTicks > LOST_TICKS) {
			return this.fail("lost sight of it");
		}

		double distance = this.drifter.getEyePosition().distanceTo(this.target.getBoundingBox().getCenter());
		if (distance > CLOSE_RANGE || !visible) {
			if (this.closeMode || this.follow == null) {
				this.closeMode = false;
				this.follow = NavigateTask.follow(this.drifter, this.target, 1.5);
			}

			Status status = this.follow.tick();
			if (status == Status.FAILURE) {
				return this.fail("can't get to it: " + this.follow.failureReason());
			}

			return Status.RUNNING;
		}

		if (!this.closeMode) {
			this.closeMode = true;
			if (this.follow != null) {
				this.follow.stop();
				this.follow = null;
			}

			this.drifter.getCamera().lookAt(this.target, CameraModel.TurnSpeed.COMBAT);
		}

		VirtualKeys keys = this.drifter.getKeys();
		Vec3 at = this.target.position();
		if (distance > STOP_RANGE) {
			Steering.pressToward(this.drifter, at);
			keys.set(VirtualKeys.Key.SPRINT, keys.isDown(VirtualKeys.Key.FORWARD)
				&& this.drifter.getFoodData().getFoodLevel() > FoodConstants.SPRINT_LEVEL);
		} else {
			Steering.stopMoving(this.drifter);
		}

		keys.set(VirtualKeys.Key.JUMP, this.drifter.horizontalCollision && this.drifter.onGround());
		if (Combat.attackStrength(this.drifter) >= FULL_STRENGTH && Combat.canHit(this.drifter, this.target)) {
			Combat.attack(this.drifter, this.target);
		}

		return Status.RUNNING;
	}

	@Override
	protected void onStop() {
		if (this.follow != null) {
			this.follow.stop();
			this.follow = null;
		}

		this.drifter.getKeys().releaseAll();
		this.drifter.getCamera().clearTarget();
	}
}
