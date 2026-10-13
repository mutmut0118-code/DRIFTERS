package com.dinglebob.drifter.executor.task;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;

import net.minecraft.world.entity.item.ItemEntity;

/**
 * Walks over to dropped items nearby so the normal pickup collects them (players walk over their drops too).
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
	@Nullable
	private ItemEntity current;
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
			Optional<ItemEntity> next = this.drifter.level()
				.getEntitiesOfClass(ItemEntity.class, this.drifter.getBoundingBox().inflate(this.radius),
					item -> !this.skipped.contains(item.getId()) && item.getY() - this.drifter.getY() <= MAX_CLIMB)
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
			if (this.current.hasPickUpDelay()) {
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
