package com.dinglebob.drifter.social;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.Errand;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.WaitTask;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.world.phys.Vec3;

/**
 * An errand to another town ({@link Errand}; milestone 22 step 6, an envoy): on foot there, up to the one it's sent to
 * (or anyone of theirs), its message said to their face, a moment for the answer, and home with it.
 */
public final class ErrandGoal extends Goals.Goal {
	/** Close enough to talk. */
	private static final double TALK = 3.0;
	/** How far round the other town's middle it looks for the one it's sent to, and how long it waits for them. */
	private static final double LOOKS = 64.0;
	private static final long WAITS = 20L * 60 * 3;

	@Override
	public String id() {
		return "errand";
	}

	@Override
	public String name(Goals.Context context) {
		Errand errand = context.drifter().getErrand();
		return errand == null ? "go on an errand" : "take a message to " + errand.thereName();
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		// (The town's errand: above its own chores, below staying alive, eating and sleeping; by day.)
		return drifter.getErrand() == null || context.threat().urgent() || Schedule.isNight(drifter) ? 0.0 : 0.72;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 30;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		Errand errand = drifter.getErrand();
		return errand == null ? null : new Trip(drifter, errand);
	}

	private static final class Trip extends Activity {
		private final Errand errand;
		private boolean told;
		private int walks;
		private long waitingSince = -1;
		private boolean wentToDoor;

		Trip(DrifterEntity drifter, Errand errand) {
			super(drifter);
			this.errand = errand;
			this.told = errand.told();
		}

		@Override
		public String describe() {
			return this.told ? "bring the answer home from " + this.errand.thereName() : "take a message to " + this.errand.thereName();
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60 * 15;
		}

		@Override
		protected boolean countsAsFailure(Task step) {
			return false;
		}

		@Override
		protected Status next() {
			if (this.drifter.getErrand() != this.errand) {
				return Status.SUCCESS;
			}

			if (!this.told) {
				Vec3 there = Vec3.atBottomCenterOf(this.errand.there());
				if (this.drifter.position().distanceTo(there) > 16.0) {
					if (this.walks++ > 30) {
						return this.giveUp("couldn't find the way to " + this.errand.thereName());
					}

					return this.begin(new NavigateTask(this.drifter, there, 8.0, "walk to " + this.errand.thereName()));
				}

				DrifterEntity met = this.whom();
				if (met == null) {
					if (this.waitingSince < 0) {
						this.waitingSince = this.now();
					}

					// Not about the middle: to their door (found in testing: everyone out at work, it waited in an empty plaza).
					var door = this.errand.meetAt();
					if (door != null && !this.wentToDoor && this.now() - this.waitingSince > 20 * 20) {
						this.wentToDoor = true;
						return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(door), 3.0, "go to " + this.errand.thereName() + "'s leader's house"));
					}

					if (this.now() - this.waitingSince < WAITS) {
						// (Looking about as it waits: whoever comes by.)
						return this.begin(new com.dinglebob.drifter.executor.task.LookAroundTask(this.drifter));
					}

					this.told = true;
					this.walks = 0;
					this.think("found nobody to tell in " + this.errand.thereName());
					this.errand.arrived(this.drifter, null);
					return Status.RUNNING;
				}

				if (this.drifter.distanceTo(met) > TALK) {
					return this.begin(new NavigateTask(this.drifter, met.position(), TALK - 0.8, "walk up to " + met.getName().getString()));
				}

				this.told = true;
				this.walks = 0;
				ChatLines.speak(this.drifter, this.errand.message());
				this.think("gave " + met.getName().getString() + " the message from home");
				this.errand.arrived(this.drifter, met);
				return this.begin(new LookAtTask(this.drifter, met.getEyePosition(), 40));
			}

			Vec3 home = Vec3.atBottomCenterOf(this.errand.home());
			if (this.drifter.position().distanceTo(home) > 24.0) {
				if (this.walks++ > 30) {
					return this.giveUp("couldn't find the way home");
				}

				return this.begin(new NavigateTask(this.drifter, home, 12.0, "walk home"));
			}

			this.think("back from " + this.errand.thereName() + " with the answer");
			this.drifter.setErrand(null);
			this.errand.back(this.drifter);
			return Status.SUCCESS;
		}

		/** The one it's sent to if it can see them about, else anyone of their town about. */
		@Nullable
		private DrifterEntity whom() {
			DrifterEntity anyone = null;
			for (var awareness : this.drifter.getSenses().tracked()) {
				if (!(awareness.entity instanceof DrifterEntity other) || !other.isAlive() || !awareness.identified
					|| other.distanceToSqr(Vec3.atBottomCenterOf(this.errand.there())) > LOOKS * LOOKS && other.distanceTo(this.drifter) > 24.0) {
					continue;
				}

				if (other.getUUID().equals(this.errand.meet())) {
					return other;
				}

				var mine = DrifterGroups.of(this.drifter.getUUID());
				var theirs = DrifterGroups.of(other.getUUID());
				if (anyone == null && theirs != null && theirs != mine) {
					anyone = other;
				}
			}

			// (Its leader not about: anyone of theirs, after a while.)
			return this.waitingSince >= 0 && this.now() - this.waitingSince > WAITS / 2 ? anyone : null;
		}

		private Status giveUp(String why) {
			this.think("gave up the errand: " + why);
			this.drifter.setErrand(null);
			this.errand.gaveUp(this.drifter, why);
			return Status.SUCCESS;
		}
	}
}
