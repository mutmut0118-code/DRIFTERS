package com.dinglebob.drifter.executor.task;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Riding its horse most of the way somewhere far (DESIGN.md section 10.9: "for long overland trips once it has one"), as a
 * player rides: up into the saddle, then across country toward the place, steering round what's in the way, jumping a
 * step, keeping out of water; off again near the end (the last bit on foot finds the way in), or when the way is blocked.
 * Its keys steer the horse (DrifterRiding).
 */
public class RideTask extends Task {
	private final AbstractHorse horse;
	private final Vec3 target;
	/** Close enough to get off and walk the rest. */
	private final double offAt;
	@Nullable
	private NavigateTask walkTo;
	private int mountTries;
	private int looked;
	private double bestDistance = Double.MAX_VALUE;
	private int sinceProgress;
	/** The way it's steering now (degrees) and for how long it keeps a detour. */
	private float heading = Float.NaN;
	private int detourTicks;
	private static final int STUCK_TICKS = 20 * 6;
	private static final double PROGRESS = 2.0;

	public RideTask(DrifterEntity drifter, AbstractHorse horse, Vec3 target, double offAt) {
		super(drifter);
		this.horse = horse;
		this.target = target;
		this.offAt = offAt;
	}

	@Override
	public String describe() {
		return "ride the horse toward " + BlockPos.containing(this.target).toShortString();
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 5;
	}

	@Override
	protected Status onTick() {
		if (!this.horse.isAlive() || this.horse.isRemoved()) {
			return this.fail("the horse is gone");
		}

		if (this.drifter.getVehicle() != this.horse) {
			return this.mount();
		}

		this.drifter.keepRiding();
		Vec3 here = this.horse.position();
		double distance = Math.sqrt((this.target.x - here.x) * (this.target.x - here.x) + (this.target.z - here.z) * (this.target.z - here.z));
		if (distance <= this.offAt) {
			this.getOff();
			return Status.SUCCESS;
		}

		if (distance < this.bestDistance - PROGRESS) {
			this.bestDistance = distance;
			this.sinceProgress = 0;
		} else if (++this.sinceProgress > STUCK_TICKS) {
			this.getOff();
			return this.fail("the horse can't get through this way");
		}

		this.steer(here);
		return Status.RUNNING;
	}

	/** Up into the saddle: over to it on foot, then on (in reach, looking at it, as a player right-clicks a horse). */
	private Status mount() {
		double reach = this.drifter.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		if (this.horse.getBoundingBox().distanceToSqr(this.drifter.getEyePosition()) > reach * reach) {
			if (this.walkTo == null) {
				this.walkTo = NavigateTask.follow(this.drifter, this.horse, 1.5);
			}

			Status status = this.walkTo.tick();
			if (status == Status.FAILURE) {
				return this.fail("can't get to the horse");
			}

			return Status.RUNNING;
		}

		if (this.walkTo != null) {
			this.walkTo.stop();
			this.walkTo = null;
		}

		this.drifter.getKeys().releaseAll();
		this.drifter.getCamera().track(this.horse.getEyePosition(), CameraModel.TurnSpeed.NORMAL);
		if (++this.looked < 6) {
			return Status.RUNNING;
		}

		if (this.horse.isVehicle() || ++this.mountTries > 60) {
			return this.fail("couldn't get on the horse");
		}

		this.drifter.meansToRide(true);
		boolean on = this.drifter.startRiding(this.horse);
		this.drifter.meansToRide(false);
		if (!on) {
			return Status.RUNNING;
		}

		this.drifter.getMemory().thoughts().add(this.drifter.level().getGameTime(), "rode off on its horse");
		return Status.RUNNING;
	}

	/** Toward the place, round anything the horse can't go over (a wall, water, a drop it would take damage from). */
	private void steer(Vec3 here) {
		VirtualKeys keys = this.drifter.getKeys();
		float straight = (float)Math.toDegrees(Math.atan2(this.target.z - here.z, this.target.x - here.x)) - 90.0F;
		if (Float.isNaN(this.heading) || --this.detourTicks <= 0) {
			this.heading = straight;
		}

		if (!this.clear(this.heading)) {
			float chosen = Float.NaN;
			for (float turn : new float[] {30.0F, -30.0F, 60.0F, -60.0F, 90.0F, -90.0F, 135.0F, -135.0F}) {
				if (this.clear(straight + turn)) {
					chosen = straight + turn;
					break;
				}
			}

			if (Float.isNaN(chosen)) {
				keys.releaseAll();
				return;
			}

			this.heading = chosen;
			this.detourTicks = 30;
		}

		this.drifter.getCamera().lookAt(this.heading, 5.0F, CameraModel.TurnSpeed.NORMAL);
		keys.set(VirtualKeys.Key.FORWARD, true);
		keys.set(VirtualKeys.Key.SPRINT, true);
		// A step up ahead: jump it (a horse clears a block).
		Vec3 ahead = Vec3.directionFromRotation(0.0F, this.heading);
		BlockPos front = BlockPos.containing(here.x + ahead.x * 1.6, here.y + 0.2, here.z + ahead.z * 1.6);
		Level level = this.horse.level();
		keys.set(VirtualKeys.Key.JUMP, this.horse.onGround() && !level.getBlockState(front).getCollisionShape(level, front).isEmpty());
	}

	/** The horse can go that way: no wall over a block high, no water, no drop over 3 within the next few blocks. */
	private boolean clear(float yaw) {
		Level level = this.horse.level();
		Vec3 direction = Vec3.directionFromRotation(0.0F, yaw);
		Vec3 here = this.horse.position();
		for (double out = 1.5; out <= 4.5; out += 1.5) {
			BlockPos feet = BlockPos.containing(here.x + direction.x * out, here.y + 0.2, here.z + direction.z * out);
			boolean blocked = !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
				&& !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty();
			if (blocked || !level.getBlockState(feet.above(2)).getCollisionShape(level, feet.above(2)).isEmpty()) {
				return false;
			}

			if (level.getFluidState(feet).is(FluidTags.WATER) || level.getFluidState(feet.below()).is(FluidTags.WATER)
				|| level.getFluidState(feet).is(FluidTags.LAVA) || level.getFluidState(feet.below()).is(FluidTags.LAVA)) {
				return false;
			}

			int drop = 0;
			while (drop < 5 && level.getBlockState(feet.below(drop + 1)).getCollisionShape(level, feet.below(drop + 1)).isEmpty()) {
				drop++;
			}

			if (drop > 3) {
				return false;
			}
		}

		return true;
	}

	/** Off the horse (it stays where it is), remembered there. */
	private void getOff() {
		this.drifter.getKeys().releaseAll();
		if (this.drifter.getVehicle() == this.horse) {
			this.drifter.stopRiding();
		}

		this.drifter.getDrifterBrain().setHorse(this.horse.getUUID(), this.horse.blockPosition());
	}

	@Override
	protected void onStop() {
		if (this.walkTo != null) {
			this.walkTo.stop();
		}

		this.getOff();
	}
}
