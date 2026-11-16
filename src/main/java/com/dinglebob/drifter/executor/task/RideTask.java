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

	/** In a hurry (a gallop all the way: a chase, called to war). */
	private final boolean hurry;
	@Nullable
	private com.dinglebob.drifter.skills.Gait gait;

	public RideTask(DrifterEntity drifter, AbstractHorse horse, Vec3 target, double offAt) {
		this(drifter, horse, target, offAt, false);
	}

	public RideTask(DrifterEntity drifter, AbstractHorse horse, Vec3 target, double offAt, boolean hurry) {
		super(drifter);
		this.horse = horse;
		this.target = target;
		this.offAt = offAt;
		this.hurry = hurry;
		this.end = target;
	}

	/** Where the whole trip ends (its gait goes by that, not by this stretch's end). */
	private Vec3 end;
	/** A stretch of a longer trip: it stays in the saddle at the end of it, for the next. */
	private boolean stayOn;
	/** It got to the end of this stretch still riding. */
	private boolean arrived;
	/** It stops on coming into a town from outside. */
	private boolean stopInTown;
	private boolean wasOutside;

	/**
	 * One stretch of a road trip ending at {@code end} (found in testing: a trip along a road goes a stretch of 32 blocks at
	 * a time, each too short to be worth getting on a horse for, so Drifters walked every road with their horse led behind
	 * the stable): at the stretch's end it stays on for the next stretch.
	 */
	public RideTask onTheWayTo(Vec3 end) {
		this.end = end;
		this.stayOn = true;
		this.stopInTown = true;
		return this;
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
		this.drifter.getSenses().touched(this.horse);
		// Its gait for now (horse culture): a walk in town and at the end, a trot on the road, a gallop in a hurry.
		var now = this.keepUp != null ? this.keepUpGait() : com.dinglebob.drifter.skills.Gait.choose(this.drifter, this.end, this.hurry);
		if (now != this.gait) {
			if (this.gait != null) {
				this.drifter.getMemory().thoughts().add(this.drifter.level().getGameTime(), now.words() + " now");
			}

			this.gait = now;
			com.dinglebob.drifter.skills.Gait.set(this.horse, now);
		}

		Vec3 here = this.horse.position();
		// Into a town from the road (a stretch of a trip): it stops at its edge, to get off and lead the horse in.
		boolean inTown = com.dinglebob.drifter.api.DrifterApi.settled(this.horse.level(), this.horse.blockPosition());
		if (this.stopInTown && inTown && this.wasOutside) {
			this.arrived = true;
			return Status.SUCCESS;
		}

		this.wasOutside |= !inTown;
		double distance = Math.sqrt((this.target.x - here.x) * (this.target.x - here.x) + (this.target.z - here.z) * (this.target.z - here.z));
		// (A stretch of a longer way passed by (its end behind it): reached, no turning back round for it; found by the user,
		// riders at a canter overshot it and went round in circles.)
		Vec3 facing = Vec3.directionFromRotation(0.0F, this.horse.getYRot());
		boolean passed = this.stayOn && distance < 10.0 && (this.target.x - here.x) * facing.x + (this.target.z - here.z) * facing.z < 0.0;
		if (distance <= this.offAt || passed) {
			if (this.stayOn) {
				this.arrived = true;
			} else {
				this.getOff();
			}

			return Status.SUCCESS;
		}

		// (On the horse's own way round: getting on means moving, not nearer as the crow flies: a way round houses goes away
		// from the place for a while; found in testing, a rider on its way out of town "stuck" while it rode round a corner.)
		boolean onRoute = this.route != null;
		if (onRoute && (this.lastMoved == null || here.distanceTo(this.lastMoved) >= PROGRESS)) {
			this.lastMoved = here;
			this.sinceProgress = 0;
		}

		if (distance < this.bestDistance - PROGRESS) {
			this.bestDistance = distance;
			this.sinceProgress = 0;
		} else if (++this.sinceProgress > STUCK_TICKS) {
			// (In a town: a new way round for the horse, a few times, before it gives up: the user's pick, "stay on" rather
			// than off and on again.)
			// (Out on the land too: the horse's own way round for a while, found in testing: a raid's leader stuck on a bank by
			// the road got off, and its whole band with it.)
			if (++this.reroutes <= REROUTES) {
				this.sinceProgress = 0;
				this.route = null;
				this.routeUntil = this.ticksRunning() + 20 * 10;
			} else {
				if (!this.keepSeat) {
					this.getOff();
				}

				return this.fail("the horse can't get through this way");
			}
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
		// (Only a horse it may ride: never another's.)
		if (!com.dinglebob.drifter.api.DrifterApi.mayRide(this.drifter, this.horse)) {
			return this.fail("not its horse");
		}

		// (Its lead off it first, back in its pack.)
		com.dinglebob.drifter.skills.StableGoal.unlead(this.drifter, this.horse);

		// (A horse with Icy's cart hitched takes only a player's click on it: its own horse, it gets on as a player does.)
		boolean on = com.dinglebob.drifter.compat.IcyHorses.cart(this.horse) != null ? this.drifter.startRiding(this.horse, true)
			: this.drifter.startRiding(this.horse);
		this.drifter.meansToRide(false);
		if (!on) {
			return Status.RUNNING;
		}

		this.drifter.getMemory().thoughts().add(this.drifter.level().getGameTime(), "rode off on its horse");
		return Status.RUNNING;
	}

	/** Toward the place, round anything the horse can't go over (a wall, water, a drop it would take damage from). */
	/** In a town: the way the horse fits through (worked out for its own body: a horse finds its footing, its rider points it). */
	@Nullable
	private net.minecraft.world.level.pathfinder.Path route;
	private int routeAge;
	private int reroutes;
	private static final int REROUTES = 3;

	/** Stuck, it stays in the saddle (a war band's riders: off and on again went nowhere; found by the user). */
	private boolean keepSeat;

	public RideTask keepSeat() {
		this.keepSeat = true;
		return this;
	}

	/** Ticks till it may jump again (a tap, not a held key). */
	private int jumpCooldown;

	/** It always keeps to the horse's own way (riding with someone: to its place behind them, never straight across). */
	private boolean byRoute;

	public RideTask byRoute() {
		this.byRoute = true;
		return this;
	}

	/** Where the horse was when it last got on a couple of blocks (on a route). */
	@Nullable
	private Vec3 lastMoved;

	/** Till this tick it keeps to the horse's own way out on the land too (after getting stuck). */
	private int routeUntil;

	private boolean inTownNow() {
		return com.dinglebob.drifter.api.DrifterApi.settled(this.horse.level(), this.horse.blockPosition());
	}

	/**
	 * Where to steer now: in a town, the next point of the horse's way there (found in testing: steered straight at the
	 * place, horses stuck on houses, fences and lamp posts, and riders got off and on again); out on the land, straight at
	 * it, round what's in the way.
	 */
	private Vec3 aim(Vec3 here) {
		if (!this.byRoute && !this.inTownNow() && this.ticksRunning() > this.routeUntil) {
			this.route = null;
			return this.target;
		}

		if (this.route == null || this.route.isDone() || ++this.routeAge > 20 * 5) {
			this.routeAge = 0;
			this.route = this.horse.getNavigation().createPath(BlockPos.containing(this.target), 1);
		}

		var route = this.route;
		if (route == null || route.isDone()) {
			return this.target;
		}

		while (!route.isDone()) {
			BlockPos node = route.getNextNodePos();
			if (Math.hypot(node.getX() + 0.5 - here.x, node.getZ() + 0.5 - here.z) > 1.3) {
				return Vec3.atBottomCenterOf(node);
			}

			route.advance();
		}

		return this.target;
	}

	private void steer(Vec3 here) {
		VirtualKeys keys = this.drifter.getKeys();
		Vec3 aim = this.aim(here);
		float straight = (float)Math.toDegrees(Math.atan2(aim.z - here.z, aim.x - here.x)) - 90.0F;
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
		// (A horse steps up a block by itself; it jumps only what's higher (a fence, a wall), and a tap of the key, not held:
		// held, a horse's jump charges up, found by the user: horses and their band leaping high along the way.)
		var shape = level.getBlockState(front).getCollisionShape(level, front);
		boolean high = !shape.isEmpty() && shape.max(net.minecraft.core.Direction.Axis.Y) > 1.0
			|| !level.getBlockState(front.above()).getCollisionShape(level, front.above()).isEmpty();
		boolean jump = this.horse.onGround() && high && this.jumpCooldown <= 0;
		if (jump) {
			this.jumpCooldown = 12;
		} else if (this.jumpCooldown > 0) {
			this.jumpCooldown--;
		}

		keys.set(VirtualKeys.Key.JUMP, jump);
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

		if (this.arrived || this.keepSeat && this.drifter.getVehicle() == this.horse) {
			// (Still on, for the next stretch: where it is now remembered all the same.)
			this.drifter.getDrifterBrain().setHorse(this.horse.getUUID(), this.horse.blockPosition());
		} else {
			this.getOff();
		}
	}

	/** Riding with someone (its band's leader): at its gait once up with it, faster to catch up. */
	@Nullable
	private net.minecraft.world.entity.Entity keepUp;

	public RideTask keepingUpWith(net.minecraft.world.entity.Entity leader) {
		this.keepUp = leader;
		return this;
	}

	private com.dinglebob.drifter.skills.Gait keepUpGait() {
		// (A little give each way, so it doesn't change at every step about the line: found in testing, gallop, canter,
		// gallop, a dozen times a minute.)
		double far = this.drifter.distanceTo(this.keepUp);
		boolean galloping = this.gait == com.dinglebob.drifter.skills.Gait.GALLOP;
		boolean cantering = this.gait == com.dinglebob.drifter.skills.Gait.CANTER;
		if (far > (galloping ? 16.0 : 26.0)) {
			return com.dinglebob.drifter.skills.Gait.GALLOP;
		}

		if (far > (cantering || galloping ? 8.0 : 14.0)) {
			return com.dinglebob.drifter.skills.Gait.CANTER;
		}

		return this.keepUp.getVehicle() instanceof AbstractHorse theirs ? com.dinglebob.drifter.skills.Gait.of(theirs) : com.dinglebob.drifter.skills.Gait.WALK;
	}

	/** The last stretch: off at the end after all (its gait still going by the trip's end). */
	public RideTask getOffAtEnd() {
		this.stayOn = false;
		return this;
	}

	/** It's at this stretch's end still in the saddle. */
	public boolean stillOn() {
		return this.arrived && this.drifter.getVehicle() == this.horse;
	}
}
