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

	@Override
	protected Status onTick() {
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
	}
}
