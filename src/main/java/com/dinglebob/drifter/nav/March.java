package com.dinglebob.drifter.nav;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;

import net.minecraft.core.BlockPos;

/**
 * A whole trip somewhere far in one go, as a player who knows the way walks it: the stretches {@link Legs} cuts it into
 * (along the roads between towns where they go) walked one after the other without stopping between them, the next one
 * worked out on the move as the last one ends (the user, 2026-11-16, of a war's leader: "they stop at random places at
 * town ... i dont like the little pauses they do they should know where their going"). Done once it's within
 * {@code near} of there (across the ground; height aside).
 */
public final class March extends Activity {
	private final BlockPos to;
	private final double near;
	private final String what;
	private final NavigateTask.Pace pace;
	private boolean mayRide;

	/** On foot (a war band's march: the leader walks with its people). */
	public March(DrifterEntity drifter, BlockPos to, double near, String what, NavigateTask.Pace pace) {
		super(drifter);
		this.to = to;
		this.near = near;
		this.what = what;
		this.pace = pace;
	}

	/** It fetches its horse from the stable first, if it keeps one, and rides (a trip of its own). */
	public March mayRide() {
		this.mayRide = true;
		return this;
	}

	@Override
	public String describe() {
		return this.what;
	}

	@Override
	protected int timeoutTicks() {
		// (Its stretches each have their own; the trip is long: a thousand blocks at a walk is four minutes and more.)
		return 20 * 60 * 30;
	}

	/** Within {@code near} of there, across the ground. */
	private boolean there() {
		return Math.hypot(this.to.getX() + 0.5 - this.drifter.getX(), this.to.getZ() + 0.5 - this.drifter.getZ()) <= this.near;
	}

	@Override
	protected boolean finishedEarly() {
		return this.there();
	}

	@Override
	protected Status next() {
		if (this.there()) {
			return Status.SUCCESS;
		}

		// Its horse from the stable first, if it may ride and keeps one (a stop: it's off to get it).
		if (this.mayRide) {
			double distance = Math.hypot(this.to.getX() + 0.5 - this.drifter.getX(), this.to.getZ() + 0.5 - this.drifter.getZ());
			Task horse = distance >= com.dinglebob.drifter.skills.StableGoal.RIDE_TRIP ? com.dinglebob.drifter.skills.StableGoal.fetch(this.drifter, distance) : null;
			if (horse != null) {
				return this.begin(horse);
			}
		}

		// The next stretch, picked up on the move: a walk passes through its end instead of stopping there.
		Task stretch = Legs.toward(this.drifter, this.to, Math.min(this.near, 3.0), this.what, this.pace, false);
		if (stretch instanceof NavigateTask walk) {
			walk.through();
		}

		return this.begin(stretch);
	}

	@Override
	protected void onStop() {
		super.onStop();
		// (Its last stretch passed through its end with the keys still down.)
		this.drifter.getKeys().releaseAll();
	}
}
