package com.dinglebob.drifter.brain;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.brain.activity.DecorateActivity;
import com.dinglebob.drifter.building.Decor;
import com.dinglebob.drifter.entity.DrifterEntity;

/**
 * Making its house a home once it's built (the user, 2026-10-30: "rugs and other decoration"): a rug in the main room, a
 * painting on a blank wall. A chore for a quiet day at home, like labelling its chests; not again for a while if it
 * couldn't.
 */
public final class DecorateGoal extends Goals.Goal {
	private static final double URGENCY = 0.36;
	private static final long CHECK_EVERY = 20L * 60 * 2;
	private static final long AFTER_FAILING = 20L * 60 * 30;

	private long checkAfter;
	private boolean wanted;
	private long failedAt = Long.MIN_VALUE / 2;

	@Override
	public String id() {
		return "decorate";
	}

	@Override
	public String name(Goals.Context context) {
		return "decorate its house";
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		Home home = drifter.getDrifterBrain().home();
		long now = drifter.level().getGameTime();
		// Under way: it keeps at it (the rug half laid otherwise, or no painting).
		if (drifter.getDrifterBrain().currentGoal() == this && drifter.getDrifterBrain().activity() instanceof DecorateActivity activity && !activity.finished()) {
			return URGENCY + 0.2;
		}

		if (home == null || !home.planDone || home.planBed(drifter.level()) == null || !home.near(drifter, 32.0) || Schedule.isNight(drifter)
			|| now - this.failedAt < AFTER_FAILING) {
			return 0.0;
		}

		if (now >= this.checkAfter) {
			this.checkAfter = now + CHECK_EVERY;
			this.wanted = Decor.rug(drifter.level(), home) != null || Decor.painting(drifter.level(), home) != null;
		}

		return this.wanted ? URGENCY : 0.0;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public Activity start(Goals.Context context) {
		// (Counted as failed until it's done: a run that ends early waits a while before the next.)
		this.failedAt = context.drifter().level().getGameTime();
		this.checkAfter = 0L;
		return new DecorateActivity(context.drifter());
	}
}
