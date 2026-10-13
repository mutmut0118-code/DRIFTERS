package com.dinglebob.drifter.motor;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Moves the Drifter's head like a mouse moves a player's camera (DESIGN.md section 16.1):
 * <ul>
 *   <li>seeks the look target with acceleration up to a max speed, then decelerates;</li>
 *   <li>fast turns overshoot by 1 to 5 degrees, then snap back (more overshoot at low Skill);</li>
 *   <li>tiny idle jitter while settled, so the head is never perfectly frozen.</li>
 * </ul>
 * The model only produces a camera yaw and pitch. Like a player, the Drifter's yaw <em>is</em> its camera
 * yaw; the body follows separately (see DrifterEntity#tickHeadTurn).
 *
 * <p>All angles are degrees, all speeds are degrees per tick.
 */
public class CameraModel {
	/** Turn speed bands from the section 16.1 table, in degrees per second. */
	public enum TurnSpeed {
		IDLE(30.0F, 90.0F, 4.0F),
		NORMAL(180.0F, 360.0F, 2.5F),
		COMBAT(700.0F, 1000.0F, 1.5F);

		final float minDegPerSec;
		final float maxDegPerSec;
		/** Ticks to reach max speed from rest, so acceleration = maxSpeed / rampTicks. */
		final float rampTicks;

		TurnSpeed(float minDegPerSec, float maxDegPerSec, float rampTicks) {
			this.minDegPerSec = minDegPerSec;
			this.maxDegPerSec = maxDegPerSec;
			this.rampTicks = rampTicks;
		}

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	/** Turns bigger than this (degrees) at NORMAL or COMBAT speed overshoot. */
	public static final float OVERSHOOT_MIN_TURN = 15.0F;
	public static final float OVERSHOOT_MIN_DEG = 1.0F;
	public static final float OVERSHOOT_MAX_DEG = 5.0F;
	/** A tracked target that jumps by more than this in one tick counts as a new flick. */
	public static final float NEW_FLICK_THRESHOLD = 15.0F;
	/** Correction after an overshoot is a quick snap: at least this fast. */
	public static final float CORRECTION_MIN_SPEED = 6.0F;
	/** Idle jitter: a damped random walk clamped to this amplitude ("a fraction of a degree"). */
	public static final float JITTER_AMPLITUDE = 0.4F;
	public static final float JITTER_STEP = 0.08F;
	public static final float JITTER_DAMPING = 0.9F;
	public static final float SETTLED_ANGLE = 0.5F;

	private enum TargetKind {
		NONE, ANGLES, POSITION, ENTITY
	}

	private final RandomSource random;
	private final Axis yaw = new Axis(true);
	private final Axis pitch = new Axis(false);

	private TargetKind targetKind = TargetKind.NONE;
	private float targetYaw;
	private float targetPitch;
	private Vec3 targetPos = Vec3.ZERO;
	@Nullable
	private Entity targetEntity;
	private TurnSpeed speed = TurnSpeed.NORMAL;
	/** Changes every time a new target is set, so callers can tell whether "their" target is still the current one. */
	private int targetToken;
	private float maxSpeed;
	private float accel;
	private boolean hasPreviousTarget;
	private float jitterYaw;
	private float jitterPitch;
	/** 0 to 1; until personality exists (milestone 7) every Drifter uses the middle value. */
	private float skill = 0.5F;

	public CameraModel(RandomSource random) {
		this.random = random;
		this.setSpeed(TurnSpeed.NORMAL);
	}

	public void setSkill(float skill) {
		this.skill = Mth.clamp(skill, 0.0F, 1.0F);
	}

	public void setSpeed(TurnSpeed speed) {
		this.speed = speed;
		float degPerSec = Mth.lerp(this.random.nextFloat(), speed.minDegPerSec, speed.maxDegPerSec);
		this.maxSpeed = degPerSec / 20.0F;
		this.accel = this.maxSpeed / speed.rampTicks;
	}

	public TurnSpeed getSpeed() {
		return this.speed;
	}

	/** Stops seeking. The head holds where it is (with jitter). */
	public void clearTarget() {
		this.targetKind = TargetKind.NONE;
		this.targetEntity = null;
		this.hasPreviousTarget = false;
	}

	public void lookAt(float yaw, float pitch, TurnSpeed speed) {
		this.targetToken++;
		this.setSpeed(speed);
		this.targetKind = TargetKind.ANGLES;
		this.targetYaw = yaw;
		this.targetPitch = Mth.clamp(pitch, -90.0F, 90.0F);
		this.hasPreviousTarget = false;
	}

	public void lookAt(Vec3 position, TurnSpeed speed) {
		this.targetToken++;
		this.setSpeed(speed);
		this.targetKind = TargetKind.POSITION;
		this.targetPos = position;
		this.hasPreviousTarget = false;
	}

	public void lookAt(Entity entity, TurnSpeed speed) {
		this.targetToken++;
		this.setSpeed(speed);
		this.targetKind = TargetKind.ENTITY;
		this.targetEntity = entity;
		this.hasPreviousTarget = false;
	}

	public boolean hasTarget() {
		return this.targetKind != TargetKind.NONE;
	}

	public int targetToken() {
		return this.targetToken;
	}

	/** True when the head has stopped on its target (or has no target): not mid-turn, not overshooting. */
	public boolean isSettled() {
		return this.yaw.isSettled() && this.pitch.isSettled();
	}

	public String describeTarget() {
		return switch (this.targetKind) {
			case NONE -> "none";
			case ANGLES -> String.format(Locale.ROOT, "yaw %.1f pitch %.1f", this.targetYaw, this.targetPitch);
			case POSITION -> String.format(Locale.ROOT, "position %.1f %.1f %.1f", this.targetPos.x, this.targetPos.y, this.targetPos.z);
			case ENTITY -> this.targetEntity == null ? "entity (gone)" : "entity " + this.targetEntity.getName().getString();
		} + " (" + this.speed.id() + ")";
	}

	/**
	 * Advances one tick.
	 *
	 * @param eyePosition where the Drifter's eyes are, for position and entity targets
	 * @param currentYaw the entity's yaw now (something else, such as a command teleport, may have moved it)
	 * @param currentPitch the entity's pitch now
	 * @return {yaw, pitch} to apply this tick
	 */
	public float[] tick(Vec3 eyePosition, float currentYaw, float currentPitch) {
		this.yaw.syncTo(currentYaw - this.jitterYaw);
		this.pitch.syncTo(currentPitch - this.jitterPitch);

		if (this.targetKind == TargetKind.ENTITY && (this.targetEntity == null || this.targetEntity.isRemoved())) {
			this.clearTarget();
		}

		if (this.targetKind != TargetKind.NONE) {
			float[] wanted = this.resolveTarget(eyePosition);
			boolean newFlick = !this.hasPreviousTarget
				|| Math.abs(Mth.wrapDegrees(wanted[0] - this.yaw.target)) > NEW_FLICK_THRESHOLD
				|| Math.abs(wanted[1] - this.pitch.target) > NEW_FLICK_THRESHOLD;
			this.yaw.setTarget(wanted[0], newFlick);
			this.pitch.setTarget(wanted[1], newFlick);
			this.hasPreviousTarget = true;
			this.yaw.step();
			this.pitch.step();
		} else {
			this.yaw.hold();
			this.pitch.hold();
		}

		this.updateJitter();
		float outYaw = this.yaw.angle + this.jitterYaw;
		float outPitch = Mth.clamp(this.pitch.angle + this.jitterPitch, -90.0F, 90.0F);
		return new float[]{outYaw, outPitch};
	}

	private float[] resolveTarget(Vec3 eye) {
		if (this.targetKind == TargetKind.ANGLES) {
			return new float[]{this.targetYaw, this.targetPitch};
		}

		Vec3 point = this.targetKind == TargetKind.ENTITY && this.targetEntity != null ? this.targetEntity.getEyePosition() : this.targetPos;
		double dx = point.x - eye.x;
		double dy = point.y - eye.y;
		double dz = point.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float yawTo = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float pitchTo = (float)(-(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG));
		return new float[]{yawTo, Mth.clamp(pitchTo, -90.0F, 90.0F)};
	}

	private void updateJitter() {
		boolean settled = this.yaw.isSettled() && this.pitch.isSettled();
		if (settled) {
			this.jitterYaw = Mth.clamp(this.jitterYaw * JITTER_DAMPING + (float)this.random.nextGaussian() * JITTER_STEP, -JITTER_AMPLITUDE, JITTER_AMPLITUDE);
			this.jitterPitch = Mth.clamp(this.jitterPitch * JITTER_DAMPING + (float)this.random.nextGaussian() * JITTER_STEP, -JITTER_AMPLITUDE, JITTER_AMPLITUDE);
		} else {
			this.jitterYaw *= 0.5F;
			this.jitterPitch *= 0.5F;
		}
	}

	/** One rotation axis: a position, a velocity, and an overshoot phase. */
	private final class Axis {
		final boolean wraps;
		float angle;
		float velocity;
		float target;
		/** Extra degrees past the target, aimed for during the overshoot phase; 0 when correcting or tracking. */
		float overshoot;
		boolean correcting;

		Axis(boolean wraps) {
			this.wraps = wraps;
		}

		float diff(float to, float from) {
			return this.wraps ? Mth.wrapDegrees(to - from) : to - from;
		}

		void syncTo(float actual) {
			if (Math.abs(this.diff(actual, this.angle)) > 0.01F) {
				this.angle = actual;
			}
		}

		void setTarget(float newTarget, boolean newFlick) {
			if (newFlick) {
				float error = this.diff(newTarget, this.angle);
				boolean fast = CameraModel.this.speed != TurnSpeed.IDLE;
				if (fast && Math.abs(error) > OVERSHOOT_MIN_TURN) {
					// Lower skill overshoots more: 1.4x the rolled amount at skill 0, 0.6x at skill 1.
					float skillFactor = Mth.lerp(CameraModel.this.skill, 1.4F, 0.6F);
					float amount = Mth.lerp(CameraModel.this.random.nextFloat(), OVERSHOOT_MIN_DEG, OVERSHOOT_MAX_DEG) * skillFactor;
					this.overshoot = Math.signum(error) * amount;
				} else {
					this.overshoot = 0.0F;
				}

				this.correcting = false;
			}

			this.target = newTarget;
		}

		void hold() {
			this.target = this.angle;
			this.velocity = 0.0F;
			this.overshoot = 0.0F;
			this.correcting = false;
		}

		void step() {
			float aim = this.target + this.overshoot;
			float error = this.diff(aim, this.angle);
			float maxSpeed = CameraModel.this.maxSpeed;
			float accel = CameraModel.this.accel;
			if (this.correcting) {
				maxSpeed = Math.max(maxSpeed, CORRECTION_MIN_SPEED);
				accel = Math.max(accel, CORRECTION_MIN_SPEED / 2.0F);
			}

			// Fastest speed from which we can still brake to a stop at the aim point.
			float brakingSpeed = (float)Math.sqrt(2.0F * accel * Math.abs(error));
			float desired = Math.signum(error) * Math.min(maxSpeed, brakingSpeed);
			this.velocity += Mth.clamp(desired - this.velocity, -accel, accel);

			if (Math.abs(error) <= Math.abs(this.velocity) || Math.abs(error) < 0.05F) {
				// Arrived at the aim point this tick.
				this.angle = this.wraps ? this.angle + error : aim;
				this.velocity = 0.0F;
				if (this.overshoot != 0.0F) {
					this.overshoot = 0.0F;
					this.correcting = true;
				} else {
					this.correcting = false;
				}
			} else {
				this.angle += this.velocity;
			}

			if (!this.wraps) {
				this.angle = Mth.clamp(this.angle, -90.0F, 90.0F);
			}
		}

		boolean isSettled() {
			return Math.abs(this.velocity) < 0.05F && this.overshoot == 0.0F && Math.abs(this.diff(this.target, this.angle)) < SETTLED_ANGLE;
		}
	}
}
