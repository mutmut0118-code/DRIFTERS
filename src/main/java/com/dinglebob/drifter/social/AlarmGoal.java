package com.dinglebob.drifter.social;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.TownEdge;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.brain.activity.ShelterActivity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.WaitTask;
import com.dinglebob.drifter.nav.Legs;
import com.dinglebob.drifter.perception.BellHearing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Its town's alarm bell heard (0.23.30; the user, 2026-11-08: "for like major stuff like a massacre or a raid will people
 * spam ring the bell?"): its guards and its brave go to the trouble (where the ringer said it was, else the bell) and look
 * about there, and fight whatever they find as they would anyway; the rest go into their houses and stay in till the
 * alarm's over (a minute after it was last rung), as villagers do. Only its own town's bell: another's, or a bell out in
 * the wild, it only notes.
 */
public final class AlarmGoal extends Goals.Goal {
	/** An alarm heard counts this long (its town has it rung again every forty seconds or so while the trouble lasts). */
	public static final long FRESH = 20L * 60;
	/**
	 * Down the town's mine, one who answers runs to its mouth and shouts down it (those digging there hear no bell): this
	 * far from the mouth they hear (a shout down a shaft, along the tunnels). The user, 0.23.35: "alot of citizens were in
	 * a mining trip or doing something else than defending an invaded town".
	 */
	private static final double CALL_DOWN = 48.0;
	/** Who runs to the mine for each town's alarm (by its bell; the first to answer that isn't a guard), since when, and done. */
	private record Run(java.util.UUID who, long since, boolean done) {
	}

	private static final java.util.Map<BlockPos, Run> runners = new java.util.HashMap<>();
	/** One run to the mine an alarm: another this long after (the bell is rung again every forty seconds or so). */
	private static final long RUN_AGAIN = 20L * 60 * 5;
	/** Hurt this badly: it goes in whatever it's like. */
	private static final float HURT = 0.5F;
	/** Near enough the trouble: it looks about there. */
	private static final double THERE = 8.0;
	/** How urgent going to the trouble, and going in, are. */
	private static final double ANSWER = 0.9;
	private static final double HIDE = 0.9;
	/** The trouble said to be this far from the bell is somewhere else: it goes to the bell. */
	private static final double TOO_FAR = 160.0;

	@Override
	public String id() {
		return "alarm";
	}

	@Override
	public String name(Goals.Context context) {
		return answers(context.drifter()) ? "answer the alarm" : "go inside till the alarm's over";
	}

	/** Its town's alarm, heard lately; else null. */
	@Nullable
	static BellHearing.Heard alarm(DrifterEntity drifter) {
		BellHearing.Heard heard = drifter.getSenses().heardAlarm();
		TownWork town = drifter.getTownWork();
		if (heard == null || town == null || drifter.level().getGameTime() - heard.at() > FRESH) {
			return null;
		}

		BlockPos ours = town.place(drifter, "bell");
		return (ours != null ? ours.closerThan(heard.bell(), 12.0) : TownEdge.inTown(drifter, heard.bell())) ? heard : null;
	}

	/**
	 * It goes to the trouble (a guard, or anyone not timid, and not badly hurt); else it goes in. (Till 0.23.35 only the
	 * brave, half and up: in the user's massacre most of a city went in, and nobody fought once its guards were dead.)
	 */
	static boolean answers(DrifterEntity drifter) {
		TownWork town = drifter.getTownWork();
		if (drifter.getHealth() < drifter.getMaxHealth() * HURT) {
			return false;
		}

		return town != null && town.guard(drifter) || !Mode.timid(drifter);
	}

	/** The way down its town's mine starts here (its mouth), or null: it has none. */
	@Nullable
	private static BlockPos mineMouth(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		return home == null || home.townMinePath.isEmpty() ? null : home.townMinePath.get(0);
	}

	/** Is it the one to run to the mine for this alarm (once an alarm: the first to answer that isn't a guard)? */
	private static boolean runner(DrifterEntity drifter, BellHearing.Heard heard) {
		TownWork town = drifter.getTownWork();
		if (mineMouth(drifter) == null || town != null && town.guard(drifter)) {
			return false;
		}

		long now = drifter.level().getGameTime();
		if (runners.size() > 64) {
			runners.values().removeIf(run -> now - run.since > RUN_AGAIN);
		}

		Run run = runners.get(heard.bell());
		if (run == null || now - run.since > RUN_AGAIN) {
			run = new Run(drifter.getUUID(), now, false);
			runners.put(heard.bell(), run);
		}

		return !run.done && run.who.equals(drifter.getUUID());
	}

	/** The run to the mine for this alarm is over (shouted down, or it couldn't get there). */
	private static void ran(BellHearing.Heard heard) {
		runners.computeIfPresent(heard.bell(), (bell, run) -> new Run(run.who, run.since, true));
	}

	/** It sees someone it's out to get (wanted in its town): the hunt for them, not the alarm, is its business now. */
	private static boolean seesQuarry(DrifterEntity drifter) {
		for (net.minecraft.world.entity.player.Player player : drifter.level().players()) {
			var seen = drifter.getSenses().awarenessOf(player);
			if (seen != null && seen.identified && seen.visibleNow && Mode.pursuesWanted(drifter, player)) {
				return true;
			}
		}

		return false;
	}

	/** Where it goes in: its house (by its bed), or null if it has none near. */
	@Nullable
	private static BlockPos inside(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		BlockPos bed = home == null ? null : home.houseBed(drifter.level());
		if (bed == null || !home.center.closerThan(drifter.blockPosition(), 200.0)) {
			return null;
		}

		return home.houseBed != null ? home.center : ShelterActivity.besideBed(drifter.level(), bed);
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		BellHearing.Heard heard = alarm(drifter);
		if (heard == null || drifter.getSenses().alarmDone() >= heard.at()) {
			return 0.0;
		}

		// (Over any work: found in testing, at 0.74 its farm and its stall won and nobody went. Under fighting and fleeing.)
		if (answers(drifter)) {
			// (Something in sight to fight or flee: that first. The one it's out to get in sight: after them, not looking about.)
			return context.threat().urgent() || seesQuarry(drifter) ? 0.0 : ANSWER;
		}

		// (Asleep in its bed: it's in already.)
		return drifter.isSleeping() || inside(drifter) == null ? 0.0 : HIDE;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public boolean urgent(Goals.Context context) {
		return true;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 10;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		if (alarm(drifter) == null) {
			return null;
		}

		return answers(drifter) ? new Answer(drifter) : new Hide(drifter);
	}

	/**
	 * To the trouble, and a look about there; and it keeps watch there, going wherever the trouble's said to be next, till
	 * the alarm's over (0.23.35: it went back to its work after one look, while the one it was for went on killing). The one
	 * picked to, first runs to the town's mine and shouts down it.
	 */
	private static final class Answer extends Activity {
		private int walks;
		private int looks;
		private boolean said;
		private boolean watching;
		@Nullable
		private BlockPos went;
		private boolean calledDown;
		private int runs;

		Answer(DrifterEntity drifter) {
			super(drifter);
		}

		@Override
		public String describe() {
			return "answer the alarm";
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 120;
		}

		@Override
		protected boolean countsAsFailure(Task step) {
			return false;
		}

		@Override
		protected Status next() {
			BellHearing.Heard heard = alarm(this.drifter);
			if (heard == null) {
				this.drifter.getSenses().alarmDone(this.now());
				return Status.SUCCESS;
			}

			// The one it's out to get, in sight: after them (another goal's), not on looking about.
			if (seesQuarry(this.drifter)) {
				this.think("there they are");
				return Status.SUCCESS;
			}

			// The mine first, if it's the one to call those down there up.
			BlockPos mouth = mineMouth(this.drifter);
			if (!this.calledDown && mouth != null && runner(this.drifter, heard)) {
				if (this.runs == 0) {
					this.think("running to the mine to call everyone up" + (heard.why() == null ? "" : " (" + heard.why() + ")"));
				}

				double far = Math.sqrt(this.drifter.blockPosition().distSqr(mouth));
				if (far > 40.0 && this.runs < 8) {
					this.runs++;
					return this.begin(Legs.toward(this.drifter, mouth, 4.0, "run to the mine"));
				}

				if (far > 4.0 && this.runs < 8) {
					this.runs++;
					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(mouth), 3.0, "run to the mine", NavigateTask.Pace.TRAVEL));
				}

				this.calledDown = true;
				ran(heard);
				if (far <= 8.0) {
					int heardIt = BellHearing.callDown(this.drifter, heard, mouth, CALL_DOWN);
					String shout = heard.why() == null ? Lines.pick(this.drifter, "alarm.shout_plain") : Lines.pick(this.drifter, "alarm.shout", "why", heard.why());
					com.dinglebob.drifter.social.ChatLines.speak(this.drifter, shout != null ? shout
						: "everyone up! " + (heard.why() == null ? "the alarm's ringing!" : heard.why() + "!"));
					this.think("shouted down the mine: " + heardIt + " heard");
				} else {
					this.think("couldn't get to the mine");
				}
			}

			BlockPos to = heard.trouble() != null && heard.trouble().closerThan(heard.bell(), TOO_FAR) ? heard.trouble() : heard.bell();
			// The trouble's somewhere else now (the ringer said where it went): there instead, looking afresh.
			if (this.went == null || !to.closerThan(this.went, THERE)) {
				this.went = to;
				this.walks = 0;
				this.looks = 0;
				this.watching = false;
			}

			if (!this.said) {
				this.said = true;
				this.think("answering the alarm" + (heard.why() == null ? "" : " (" + heard.why() + ")") + ": going to " + (to == heard.bell() ? "the bell" : "the trouble"));
			}

			double far = Math.sqrt(this.drifter.blockPosition().distSqr(to));
			if (far > 40.0 && this.walks < 8) {
				this.walks++;
				return this.begin(Legs.toward(this.drifter, to, THERE, "answer the alarm"));
			}

			if (far > THERE && this.walks < 8) {
				this.walks++;
				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(to), THERE - 2.0, "answer the alarm"));
			}

			if (this.looks < 3) {
				this.looks++;
				return this.begin(new LookAroundTask(this.drifter, 4, 15));
			}

			// Nothing there now: it keeps watch there till the alarm's over or the trouble's said to be elsewhere.
			if (!this.watching) {
				this.watching = true;
				this.think("looked about where the alarm was: nothing there now. keeping watch");
			}

			this.looks = 1;
			BlockPos at = this.went;
			return this.begin(new WaitTask(this.drifter, "keep watch for the trouble", () -> {
				BellHearing.Heard now = alarm(this.drifter);
				return now == null || now.trouble() != null && !now.trouble().closerThan(at, THERE);
			}, 20 * 4));
		}
	}

	/** Into its house, and in till the alarm's over. */
	private static final class Hide extends Activity {
		private int walks;
		private boolean said;

		Hide(DrifterEntity drifter) {
			super(drifter);
		}

		@Override
		public String describe() {
			return "go inside till the alarm's over";
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60 * 6;
		}

		@Override
		protected boolean countsAsFailure(Task step) {
			return false;
		}

		@Override
		protected Status next() {
			BellHearing.Heard heard = alarm(this.drifter);
			BlockPos inside = inside(this.drifter);
			if (heard == null || inside == null) {
				if (this.said) {
					this.think("the alarm's over: back out");
				}

				this.drifter.getSenses().alarmDone(this.now());
				return Status.SUCCESS;
			}

			double far = Math.sqrt(this.drifter.blockPosition().distSqr(inside));
			if (far > 40.0 && this.walks < 10) {
				this.walks++;
				return this.begin(Legs.toward(this.drifter, inside, 2.0, "go inside, the alarm's ringing"));
			}

			if (far > 2.0 && this.walks < 10) {
				this.walks++;
				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(inside), 0.5, "go inside, the alarm's ringing"));
			}

			if (!this.said) {
				this.said = true;
				this.think(far <= 2.0 ? "in the house till the alarm's over" : "couldn't get into the house: keeping still till the alarm's over");
			}

			return this.begin(new WaitTask(this.drifter, "wait for the alarm to be over", () -> alarm(this.drifter) == null, 20 * 10));
		}
	}
}
