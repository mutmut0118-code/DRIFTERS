package com.dinglebob.drifter.social;

import java.util.List;

import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.Threats;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.NavigateTask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * A town's guard on its rounds (the user, 2026-11-06: "in a city there should be a higher ratio of guards, patrolling at
 * night so random civs dont get killed"): from spot to spot along the town's roads ({@link TownWork#patrol}), at a walk,
 * a look round at each. By night it's its watch (it doesn't go in); by day, a round now and then. Whatever it sees that's
 * after someone, the fight goal takes over (it stops its round when there's a threat about).
 */
public final class PatrolGoal extends Goals.Goal {
	/** By night: above going in for the night and its other work. */
	private static final double NIGHT = 0.85;
	/** By day: a round now and then, between its other work. */
	private static final double DAY = 0.3;

	/** The next spot of its rounds. */
	private int next = -1;

	@Override
	public String id() {
		return "patrol";
	}

	@Override
	public String name(Goals.Context context) {
		return dark(context) ? "keep watch over the town" : "walk the town's rounds";
	}

	private static boolean dark(Goals.Context context) {
		long time = context.time();
		return Schedule.isNight(time) || time >= Schedule.shelterUrgent(context.drifter());
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		TownWork town = drifter.getTownWork();
		if (town == null || !town.guard(drifter) || context.threat().urgent() || drifter.encounter() != null
			|| drifter.getHealth() < drifter.getMaxHealth() * 0.5F || town.patrol(drifter).isEmpty()) {
			return 0.0;
		}

		return dark(context) ? NIGHT : DAY;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 15;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		TownWork town = drifter.getTownWork();
		List<BlockPos> rounds = town == null ? List.of() : town.patrol(drifter);
		if (rounds.isEmpty()) {
			return null;
		}

		// (Picking up its rounds where it's nearest; then on round them in order.)
		if (this.next < 0 || this.next >= rounds.size()) {
			double best = Double.MAX_VALUE;
			for (int i = 0; i < rounds.size(); i++) {
				double d = rounds.get(i).distSqr(drifter.blockPosition());
				if (d < best) {
					best = d;
					this.next = i;
				}
			}
		}

		BlockPos spot = rounds.get(this.next);
		this.next = (this.next + 1) % rounds.size();
		boolean night = dark(context);
		return new Activity(drifter) {
			private boolean there;
			private int tries;
			private int legs;

			@Override
			public String describe() {
				return night ? "keep watch over the town" : "walk the town's rounds";
			}

			@Override
			protected int timeoutTicks() {
				return 20 * 60 * 3;
			}

			@Override
			protected boolean countsAsFailure(Task step) {
				return false;
			}

			@Override
			protected Status next() {
				// (Something about: the fight's, or its flight's, to see to.)
				if (Threats.assess(this.drifter).main() != null) {
					return Status.SUCCESS;
				}

				if (!this.there) {
					// (A far spot: a stretch at a time, along the town's roads where they go.)
					double far = this.drifter.position().distanceTo(Vec3.atBottomCenterOf(spot));
					if (far > 40.0 && this.legs++ < 8) {
						return this.begin(com.dinglebob.drifter.nav.Legs.toward(this.drifter, spot, 2.0, night ? "on watch" : "on its rounds",
							NavigateTask.Pace.STROLL));
					}

					if (far > 3.0 && this.tries++ < 2) {
						return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 2.0, night ? "on watch" : "on its rounds",
							NavigateTask.Pace.STROLL));
					}

					this.there = true;
					return this.begin(new LookAroundTask(this.drifter));
				}

				return Status.SUCCESS;
			}
		};
	}
}
