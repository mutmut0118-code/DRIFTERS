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
import com.dinglebob.drifter.personality.Trait;

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
	/** As brave as this (or a guard, and not timid): it goes to the trouble. */
	private static final double ANSWERS = 0.5;
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

	/** It goes to the trouble (a guard, or brave and not timid, and not badly hurt); else it goes in. */
	static boolean answers(DrifterEntity drifter) {
		TownWork town = drifter.getTownWork();
		if (drifter.getHealth() < drifter.getMaxHealth() * HURT) {
			return false;
		}

		return town != null && town.guard(drifter) || !Mode.timid(drifter) && drifter.getPersonality().get(Trait.BRAVERY) >= ANSWERS;
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
			// (Something in sight to fight or flee: that first.)
			return context.threat().urgent() ? 0.0 : ANSWER;
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

	/** To the trouble, and a look about there. */
	private static final class Answer extends Activity {
		private int walks;
		private int looks;
		private boolean said;

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

			BlockPos to = heard.trouble() != null && heard.trouble().closerThan(heard.bell(), TOO_FAR) ? heard.trouble() : heard.bell();
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

			this.think("looked about where the alarm was: nothing there now");
			this.drifter.getSenses().alarmDone(this.now());
			return Status.SUCCESS;
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
