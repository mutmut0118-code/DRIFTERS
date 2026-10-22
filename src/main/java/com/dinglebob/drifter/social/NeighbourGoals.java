package com.dinglebob.drifter.social;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.WaitTask;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Neighbours (Drifter: Civilizations turns this on, {@link DrifterBonds#neighbourly}; the town plan the user agreed: "neighbours
 * who help each other grow friendly faster"): now and then a Drifter walks over to a neighbour it knows (it has seen where
 * they live) and spends a while round them, the way players drop by. Time spent talking (close by) builds trust faster
 * with this on, and that's when news passes and trades happen. The more sociable, the more often. Off in the plain mod.
 */
public final class NeighbourGoals {
	private NeighbourGoals() {
	}

	public static List<Goals.Goal> all() {
		return List.of(new Visit());
	}

	public static final class Visit extends Goals.Goal {
		/** Their home this close to its own: a neighbour. */
		private static final double NEIGHBOURS = 96.0;
		/** Seen this close to its home this lately: someone who lives round here. */
		private static final double SEEN_ROUND_HOME = 48.0;
		private static final long LATELY = 24000L;
		/** How long it stays. */
		private static final int VISIT_TICKS = 20 * 60 * 3;
		/** Game ticks between visits: two thirds of a day for the least sociable, a quarter of one for the most. */
		private static final long LEAST_OFTEN = 16000L;
		private static final long MOST_OFTEN = 6000L;

		@Override
		public String id() {
			return "visit";
		}

		@Override
		public String name(Goals.Context context) {
			DrifterEntity other = target(context);
			return other == null ? "visit a neighbour" : "visit " + other.getName().getString();
		}

		@Nullable
		private static DrifterEntity target(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			Home home = drifter.getDrifterBrain().home();
			if (!DrifterBonds.neighbourly || home == null || Schedule.isNight(drifter) || drifter.encounter() != null
				|| !(drifter.level() instanceof net.minecraft.server.level.ServerLevel level)) {
				return null;
			}

			// On a visit now: the same one, till it's over (found in testing: counted as visited from the start, it gave up
			// the visit for the first chore that came along).
			DrifterBonds bonds = drifter.getBonds();
			if (onVisit(drifter)) {
				return level.getEntity(bonds.visiting) instanceof DrifterEntity other && other.isAlive() ? other : null;
			}

			// (A visit cut short is over when its time is.)
			bonds.visiting = null;

			double sociable = drifter.getPersonality().get(Trait.SOCIABILITY);
			long gap = (long)(LEAST_OFTEN - (LEAST_OFTEN - MOST_OFTEN) * sociable);
			if (level.getGameTime() - bonds.lastVisit < gap) {
				return null;
			}

			var group = com.dinglebob.drifter.world.DrifterGroups.of(drifter.getUUID());
			DrifterEntity best = null;
			double bestScore = Double.NEGATIVE_INFINITY;
			long now = level.getGameTime();
			for (Relationship r : drifter.getBonds().all()) {
				// Someone it gets along with (or doesn't know well yet) who lives round here (it has seen their home near its
				// own, or them near its home lately), and not its own group mate (they live together already). One just seen
				// close by first (it wanders over, as people drop in: found in testing, most who went to a neighbour's home
				// found nobody in), then the one it likes best.
				BlockPos where = place(r, now);
				boolean near = r.knownBase != null ? home.center.closerThan(r.knownBase, NEIGHBOURS)
					: r.lastSeenPos != null && home.center.closerThan(r.lastSeenPos, SEEN_ROUND_HOME) && now - r.lastSeenAt < LATELY;
				double score = r.trust + (now - r.lastSeenAt < JUST_SEEN ? 50.0 : 0.0);
				if (where == null || !near || r.trust <= DrifterBonds.AT_ODDS || score < bestScore || group != null && group.members.contains(r.player)) {
					continue;
				}

				if (level.getEntity(r.player) instanceof DrifterEntity other && other.isAlive() && other.distanceTo(drifter) < NEIGHBOURS * 1.5) {
					best = other;
					bestScore = score;
				}
			}

			return best;
		}

		/** On a visit now (its time not up). */
		private static boolean onVisit(DrifterEntity drifter) {
			return drifter.getBonds().visiting != null && drifter.level().getGameTime() < drifter.getBonds().visitEnds;
		}

		/** Seen this lately: where it saw them is where they are. */
		private static final long JUST_SEEN = 2400L;

		/** Where to look for them: where it just saw them, else their home if it knows it, else where it last saw them. */
		@Nullable
		private static BlockPos place(Relationship r, long now) {
			return now - r.lastSeenAt < JUST_SEEN || r.knownBase == null ? r.lastSeenPos : r.knownBase;
		}

		@Override
		public double urgency(Goals.Context context) {
			if (target(context) == null) {
				return 0.0;
			}

			// (Once there, it stays its while: found in testing, a chore that came along cut nearly every visit short.)
			return onVisit(context.drifter()) ? 0.62 : 0.3 + 0.25 * context.drifter().getPersonality().get(Trait.SOCIABILITY);
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public double risk(Goals.Context context) {
			// (Not setting off as the light goes: found in testing, visits begun at dusk were over at nightfall.)
			return onVisit(context.drifter()) ? 1.0 : Goals.lateInTheDayRisk(context, 0.6);
		}

		@Override
		public int minCommitTicks() {
			return 20 * 30;
		}

		@Override
		public Activity start(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			DrifterEntity other = target(context);
			if (other == null) {
				return null;
			}

			DrifterBonds bonds = drifter.getBonds();
			if (!onVisit(drifter)) {
				bonds.lastVisit = drifter.level().getGameTime();
				bonds.visiting = other.getUUID();
				bonds.visitEnds = bonds.lastVisit + VISIT_TICKS;
				drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "went over to visit " + other.getName().getString());
			}

			return new Activity(drifter) {
				private boolean greeted;

				@Override
				public String describe() {
					return "visit " + other.getName().getString();
				}

				@Override
				protected int timeoutTicks() {
					return VISIT_TICKS;
				}

				@Override
				protected Status next() {
					if (!other.isAlive() || other.isRemoved() || this.drifter.level().getGameTime() >= this.drifter.getBonds().visitEnds) {
						this.drifter.getBonds().visiting = null;
						return Status.SUCCESS;
					}

					// Over to where they live (or were last seen); once it sees them, along with them. (Not straight to them: it
					// only knows where they are by seeing them.)
					boolean seen = other.distanceTo(this.drifter) < 6.0 || this.drifter.getSenses().tracked().stream()
						.anyMatch(awareness -> awareness.entity == other && awareness.visibleNow);
					if (seen) {
						if (!this.greeted && other.distanceTo(this.drifter) < 6.0) {
							this.greeted = true;
							this.drifter.getBonds().greeted(other);
						}

						return this.begin(NavigateTask.follow(this.drifter, other, 4.0));
					}

					Relationship r = this.drifter.getBonds().get(other.getUUID());
					BlockPos where = r == null ? null : place(r, this.drifter.level().getGameTime());
					if (where == null) {
						return Status.SUCCESS;
					}

					if (this.drifter.blockPosition().closerThan(where, 6.0)) {
						// There: it looks round for them a while.
						return this.begin(new WaitTask(this.drifter, "look round for " + other.getName().getString(),
							() -> this.drifter.getSenses().tracked().stream().anyMatch(awareness -> awareness.entity == other && awareness.visibleNow), 100));
					}

					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(where), 3.0, "walk over to " + other.getName().getString() + "'s"));
				}
			};
		}
	}
}
