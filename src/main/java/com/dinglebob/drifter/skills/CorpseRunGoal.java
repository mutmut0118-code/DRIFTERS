package com.dinglebob.drifter.skills;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.world.Respawns;

import net.minecraft.world.phys.Vec3;

/**
 * The corpse run (DESIGN.md section 20): back from the dead with nothing, it has five minutes before what it dropped is
 * gone, so it decides at once whether to go back for it. Worth it (a few things that aren't junk, not lost in lava or the
 * void), near enough, in time; and if a player or a Drifter killed it, only the bolder go back (the cautious write it off:
 * they may still be there). It hurries there, picks up what it finds, and that's that.
 */
public final class CorpseRunGoal extends Goals.Goal {
	/** Dropped things last five minutes (while loaded); it gives up on them a little before. */
	private static final long ITEMS_LAST = 20L * 60 * 5;
	private static final long MARGIN = 20L * 30;
	/** Farther than this from where it comes back: written off. */
	private static final double TOO_FAR = 200.0;
	/** Fewer things worth having than this: not worth the trip. */
	private static final int WORTH_IT = 3;
	/** Killed by someone: only this bold or bolder goes back. */
	private static final double DARE = 0.4;

	@Override
	public String id() {
		return "corpse_run";
	}

	@Override
	public String name(Goals.Context context) {
		return "go back for its things";
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		Respawns.Corpse corpse = drifter.corpse();
		if (corpse == null || !drifter.isAlive()) {
			return 0.0;
		}

		long now = drifter.level().getGameTime();
		String skip = this.writeOff(drifter, corpse, now);
		if (skip != null) {
			drifter.forgetCorpse();
			drifter.getMemory().thoughts().add(now, skip);
			return 0.0;
		}

		// (Not in that world now: it waits, while there's time.)
		if (!drifter.level().dimension().location().equals(corpse.dimension())) {
			return 0.0;
		}

		// The sooner the better: its things won't wait.
		return 0.82;
	}

	/** Why it won't go back (null: it will). */
	@Nullable
	private String writeOff(DrifterEntity drifter, Respawns.Corpse corpse, long now) {
		if (corpse.worth() < WORTH_IT) {
			return corpse.worth() == 0 ? "its things are gone for good" : "nothing worth going back for";
		}

		if (now - corpse.at() > ITEMS_LAST - MARGIN) {
			return "too late to get its things back";
		}

		if (drifter.level().dimension().location().equals(corpse.dimension()) && drifter.position().distanceTo(Vec3.atBottomCenterOf(corpse.pos())) > TOO_FAR) {
			return "too far to go back for its things";
		}

		double bravery = drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.BRAVERY);
		if (corpse.killer() != null && bravery < DARE) {
			return "not going back for its things: whoever killed it may still be there";
		}

		return null;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public double risk(Goals.Context context) {
		return Goals.lateInTheDayRisk(context, 0.7);
	}

	/** Its things won't wait: it goes at once. */
	@Override
	public boolean urgent(Goals.Context context) {
		return true;
	}

	@Nullable
	@Override
	public Activity start(Goals.Context context) {
		Respawns.Corpse corpse = context.drifter().corpse();
		return corpse == null ? null : new Run(context.drifter(), corpse);
	}

	/** There at a run, a look round, everything picked up. */
	private static final class Run extends Activity {
		private final Respawns.Corpse corpse;
		private final Vec3 spot;
		private int walks;
		private int looks;
		private int before = -1;

		Run(DrifterEntity drifter, Respawns.Corpse corpse) {
			super(drifter);
			this.corpse = corpse;
			this.spot = Vec3.atBottomCenterOf(corpse.pos());
		}

		@Override
		public String describe() {
			return "go back for its things at " + this.corpse.pos().toShortString();
		}

		@Override
		protected int timeoutTicks() {
			return (int)ITEMS_LAST;
		}

		@Override
		protected boolean countsAsFailure(Task step) {
			return false;
		}

		@Override
		protected Status next() {
			if (this.drifter.corpse() != this.corpse) {
				return Status.SUCCESS;
			}

			if (this.before < 0) {
				this.before = this.drifter.getInventory().count(stack -> !stack.isEmpty());
				this.think("going back for its things: " + this.corpse.worth() + " left at " + this.corpse.pos().toShortString());
			}

			if (this.drifter.position().distanceTo(this.spot) > 3.0 && this.walks++ < 3) {
				return this.begin(new NavigateTask(this.drifter, this.spot, 2.0, "run back to where it died", NavigateTask.Pace.TRAVEL));
			}

			// There (or as near as it gets): a look round for its things, and pick up what it sees.
			if (this.looks++ < 2) {
				return this.begin(this.looks == 1 ? new LookAroundTask(this.drifter, 2, 4) : new CollectItemsTask(this.drifter, 10.0));
			}

			int got = this.drifter.getInventory().count(stack -> !stack.isEmpty()) - this.before;
			this.think(got > 0 ? "got some of its things back" : "its things were gone");
			this.drifter.forgetCorpse();
			return Status.SUCCESS;
		}
	}
}
