package com.dinglebob.drifter.executor.task;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Walks over to dropped items and experience orbs nearby so the normal pickup collects them (players walk over their
 * drops too; orbs only fly to players, so it walks over those as well).
 * Uses the pathfinder, and skips items it can't find a way to.
 * TODO: only items it has seen (its ITEM memories) once the brain chooses what to collect.
 */
public class CollectItemsTask extends Task {
	/** Items higher than this above its feet (on leaves, on a ledge) are skipped. */
	private static final double MAX_CLIMB = 1.2;
	/** Close enough for the normal pickup to grab it. */
	private static final double PICKUP_REACH = 0.8;

	private final double radius;
	private final Set<Integer> skipped = new HashSet<>();
	/** The item it's stepping right onto (one in a hole below its feet). */
	private int closer = -1;
	private int orbTicks;
	@Nullable
	private Entity current;
	@Nullable
	private NavigateTask walk;

	public CollectItemsTask(DrifterEntity drifter, double radius) {
		super(drifter);
		this.radius = radius;
	}

	@Override
	public String describe() {
		return "pick up nearby drops";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 30;
	}

	/** It gives up on fetching its thrown trident this long after it first went for it ("lost its trident"). */
	public static final int TRIDENT_FETCH_TICKS = 20 * 20;
	/** It goes for its thrown trident this far off even when its other drops are only looked for nearer (a missed throw flies on). */
	public static final double TRIDENT_FETCH_RADIUS = 48.0;
	/** Standing by its trident this long without it going in (still shaking, or its pack full): it leaves it. */
	private static final int TRIDENT_ARRIVED_TICKS = 40;

	@Nullable
	private NavigateTask tridentWalk;
	private int tridentArrived;

	/**
	 * Its thrown trident first (the real item, out in the world: ThrowTridentTask): a Loyalty one, or one still in the air, is
	 * waited for; one it has seen lying there (now, or as it landed) within a walk ({@link #TRIDENT_FETCH_RADIUS}, or its radius
	 * if wider) is walked to, and the normal pickup takes it (DrifterEntity#touchThrownTrident). One it can't see, can't get to,
	 * or hasn't got within {@link #TRIDENT_FETCH_TICKS} of first going for it, is lost. Null when there's nothing to do about a
	 * trident.
	 */
	@Nullable
	private Status fetchTrident() {
		if (this.drifter.thrownTrident() == null) {
			// (Picked up on the way: the walk ends.)
			this.stopTridentWalk();
			return null;
		}

		net.minecraft.world.entity.projectile.AbstractArrow trident = this.drifter.thrownTridentEntity();
		long now = this.drifter.level().getGameTime();
		if (trident == null) {
			this.stopTridentWalk();
			this.drifter.loseThrownTrident();
			return null;
		}

		boolean over = now - this.drifter.tridentFetchSince(now) > TRIDENT_FETCH_TICKS;
		// A Loyalty one comes back by itself (out of lava too): it waits for it and never walks after it. Past the wait it goes
		// on with the rest, but the trident is still its own: it goes in whenever it arrives.
		if (this.drifter.thrownTridentLoyal(trident)) {
			this.stopTridentWalk();
			if (over) {
				return null;
			}

			this.drifter.getCamera().track(trident.position(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
			return Status.RUNNING;
		}

		if (over) {
			this.stopTridentWalk();
			this.drifter.loseThrownTrident();
			return null;
		}

		// Still flying: it waits for it to land.
		if (trident.isNoPhysics() || !((com.dinglebob.drifter.mixin.AbstractArrowAccessor)trident).drifter$isInGround()) {
			this.stopTridentWalk();
			this.drifter.getCamera().track(trident.position(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
			return Status.RUNNING;
		}

		// Only one it has seen lying there (section 1: now, or as it landed), within a walk; otherwise it can't find it.
		if (this.tridentWalk == null) {
			if (this.drifter.canSeeThrownTrident(trident)) {
				this.drifter.sawThrownTrident();
			}

			if (!this.drifter.thrownTridentSeen() || this.drifter.distanceTo(trident) > Math.max(this.radius, TRIDENT_FETCH_RADIUS)) {
				this.drifter.loseThrownTrident();
				return null;
			}
		}

		if (this.tridentWalk == null) {
			this.drifter.getMemory().thoughts().add(now, "going to fetch its trident");
			this.tridentWalk = new NavigateTask(this.drifter, trident.position(), PICKUP_REACH, "walk to its trident");
		}

		Status walking = this.tridentWalk.tick();
		if (walking == Status.FAILURE) {
			// No way there (in lava, over a drop, walled off): gone for it.
			this.stopTridentWalk();
			this.drifter.loseThrownTrident();
			return null;
		}

		if (walking == Status.SUCCESS && ++this.tridentArrived > TRIDENT_ARRIVED_TICKS) {
			this.stopTridentWalk();
			this.drifter.loseThrownTrident();
			return null;
		}

		return Status.RUNNING;
	}

	private void stopTridentWalk() {
		this.tridentArrived = 0;
		if (this.tridentWalk != null) {
			this.tridentWalk.stop();
			this.tridentWalk = null;
		}
	}

	@Override
	protected Status onTick() {
		Status trident = this.fetchTrident();
		if (trident != null) {
			this.stopWalk();
			this.current = null;
			return trident;
		}

		if (this.current == null || this.current.isRemoved()) {
			this.stopWalk();
			Optional<Entity> next = this.drifter.level()
				.getEntitiesOfClass(Entity.class, this.drifter.getBoundingBox().inflate(this.radius),
					item -> (item instanceof ItemEntity || item instanceof ExperienceOrb) && !this.skipped.contains(item.getId())
						&& item.getY() - this.drifter.getY() <= MAX_CLIMB)
				.stream()
				.min(Comparator.comparingDouble(this.drifter::distanceToSqr));
			if (next.isEmpty()) {
				return Status.SUCCESS;
			}

			this.current = next.get();
			this.walk = new NavigateTask(this.drifter, this.current.position(), PICKUP_REACH, "walk to a dropped item");
		}

		Status walking = this.walk.tick();
		if (walking == Status.FAILURE) {
			this.skipped.add(this.current.getId());
			this.current = null;
		} else if (walking == Status.SUCCESS && !this.current.isRemoved()) {
			this.walk.stop();
			// Arrived but not picked up yet (pickup delay, or inventory full): wait a moment, then give up on it.
			if (this.current instanceof ItemEntity item && item.hasPickUpDelay()) {
				return Status.RUNNING;
			}

			// Orbs go one every 2 ticks: a moment for a merged one to go in.
			if (this.current instanceof ExperienceOrb && this.drifter.getBoundingBox().inflate(1.0, 0.5, 1.0).intersects(this.current.getBoundingBox())
				&& ++this.orbTicks < 40) {
				return Status.RUNNING;
			}

			// In a hole below its feet (an obsidian mould, a dip): the pickup reaches only half a block down, so it steps
			// right onto it, once (found in testing: it "arrived" at the edge and left the obsidian lying in the hole).
			if (this.current.getY() < this.drifter.getY() - 0.4 && this.closer != this.current.getId()) {
				this.closer = this.current.getId();
				this.walk = new NavigateTask(this.drifter, this.current.position(), 0.25, "step down to a dropped item");
				return Status.RUNNING;
			}

			this.skipped.add(this.current.getId());
			this.current = null;
		}

		return Status.RUNNING;
	}

	private void stopWalk() {
		if (this.walk != null) {
			this.walk.stop();
			this.walk = null;
		}
	}

	@Override
	protected void onStop() {
		this.stopWalk();
		this.stopTridentWalk();
	}
}
