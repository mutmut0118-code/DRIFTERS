package com.dinglebob.drifter.social;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.WaitTask;
import com.dinglebob.drifter.nav.Legs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * A night out in its town (0.23.40, the user: "at night the town is really empty, especially in a city where its brightly
 * lit and no mobs spawn, would be nice if some bustling night life happened with a few drifters going to sleep"): one its
 * town keeps up tonight ({@link TownWork#bedtime}) goes where people gather (the fire, the plaza, the well, the stall) and
 * stays about there, looking round, talking with whoever else is out (the talk goal takes over when someone's there), now
 * and then strolling on to the next; at its bedtime it goes home to bed as ever.
 */
public final class NightOutGoal extends Goals.Goal {
	/**
	 * How urgent: over its work and errands at night, under fighting, fleeing, eating, a chat. (0.6 at first: found in
	 * testing, its farm, a chest to loot and a mining trip with a friend all came first, and nobody was out.)
	 */
	private static final double NIGHT_OUT = 0.75;
	/** Where people gather, in the order it tries them (each its own turn of it). */
	private static final String[] SPOTS = {"fire", "plaza", "well", "stall"};
	/** It stays at a spot about this long before it may stroll on (ticks). */
	private static final int STAY_MIN = 20 * 50;
	private static final int STAY_MORE = 20 * 70;

	@Override
	public String id() {
		return "nightout";
	}

	@Override
	public String name(Goals.Context context) {
		return "out in town for the evening";
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		long time = Schedule.believedTime(drifter);
		if (!Schedule.isNight(time) || !Schedule.outInTown(drifter) || context.threat().urgent() || drifter.getTownWork() == null) {
			return 0.0;
		}

		return NIGHT_OUT;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 20;
	}

	@Override
	public Activity start(Goals.Context context) {
		return context.drifter().getTownWork() == null ? null : new Out(context.drifter());
	}

	private static final class Out extends Activity {
		private int spot;
		private int walks;
		private long arrived = -1;
		private int stay;
		private boolean said;
		@Nullable
		private BlockPos at;

		Out(DrifterEntity drifter) {
			super(drifter);
			// (Each its own first spot, by who it is and the night: not all to the fire at once.)
			long night = drifter.level().getDayTime() / 24000L;
			this.spot = (int)Math.floorMod(drifter.getUUID().getLeastSignificantBits() ^ night * 31L, (long)SPOTS.length);
			this.stay = STAY_MIN + drifter.getRandom().nextInt(STAY_MORE);
		}

		@Override
		public String describe() {
			return "out in town for the evening";
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60 * 4;
		}

		@Override
		protected boolean countsAsFailure(Task step) {
			return false;
		}

		@Override
		protected Status next() {
			TownWork town = this.drifter.getTownWork();
			if (town == null || !Schedule.outInTown(this.drifter)) {
				return Status.SUCCESS;
			}

			if (this.at == null) {
				for (int tries = 0; tries < SPOTS.length && this.at == null; tries++) {
					this.at = town.place(this.drifter, SPOTS[this.spot]);
					if (this.at == null) {
						this.spot = (this.spot + 1) % SPOTS.length;
					}
				}

				if (this.at == null) {
					return Status.SUCCESS;
				}

				this.walks = 0;
				this.arrived = -1;
			}

			double far = Math.sqrt(this.drifter.blockPosition().distSqr(this.at));
			if (far > 40.0 && this.walks < 6) {
				this.walks++;
				return this.begin(Legs.toward(this.drifter, this.at, 3.0, "go out for the evening"));
			}

			if (far > 3.0 && this.walks < 6) {
				this.walks++;
				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.at), 1.5, "go out for the evening", NavigateTask.Pace.STROLL));
			}

			if (this.arrived < 0) {
				this.arrived = this.now();
				if (!this.said) {
					this.said = true;
					long bed = town.bedtime(this.drifter);
					this.think("out " + where(SPOTS[this.spot]) + " for the evening" + (bed > 0 ? " (bed about " + clock(bed) + ")" : ""));
				}
			}

			// Strolled on to the next spot now and then, once it's been here a while.
			if (this.now() - this.arrived > this.stay) {
				this.spot = (this.spot + 1) % SPOTS.length;
				this.at = null;
				this.stay = STAY_MIN + this.drifter.getRandom().nextInt(STAY_MORE);
				return this.begin(new WaitTask(this.drifter, "stroll on", () -> true, 1));
			}

			return this.drifter.getRandom().nextBoolean() ? this.begin(new LookAroundTask(this.drifter, 3, 20))
				: this.begin(new WaitTask(this.drifter, "pass the evening", () -> !Schedule.outInTown(this.drifter), 20 * 8));
		}

		private static String where(String spot) {
			return switch (spot) {
				case "fire" -> "by the fire";
				case "plaza" -> "on the plaza";
				case "well" -> "by the well";
				default -> "by the stall";
			};
		}

		/** A time of day as a clock (6 o'clock at tick 0). */
		private static String clock(long ticks) {
			long hour = (ticks / 1000L + 6L) % 24L;
			long minute = (ticks % 1000L) * 60L / 1000L;
			return String.format(java.util.Locale.ROOT, "%d:%02d", hour, minute);
		}
	}
}
