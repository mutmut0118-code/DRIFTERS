package com.dinglebob.drifter.brain;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.brain.activity.LabelChestsActivity;
import com.dinglebob.drifter.building.ChestLabels;
import com.dinglebob.drifter.entity.DrifterEntity;

/**
 * Labelling its house's chests when it's home with time on its hands (the user's furnishing guidebook: every chest says
 * what's in it). Low: a chore for a quiet afternoon, by day, once its house is built; not again for a while if it
 * couldn't.
 */
public final class LabelChestsGoal extends Goals.Goal {
	private static final double URGENCY = 0.3;
	/** How often it looks (it reads its memory of every chest), and how long it leaves it after a go that failed. */
	private static final long CHECK_EVERY = 20L * 60;
	private static final long AFTER_FAILING = 20L * 60 * 20;

	private long checkAfter;
	private boolean wanted;
	private long failedAt = Long.MIN_VALUE / 2;

	@Override
	public String id() {
		return "label_chests";
	}

	@Override
	public String name(Goals.Context context) {
		return "label its chests";
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		Home home = drifter.getDrifterBrain().home();
		long now = drifter.level().getGameTime();
		if (home == null || !home.planDone || !home.near(drifter, 48.0) || Schedule.isNight(drifter) || now - this.failedAt < AFTER_FAILING) {
			return 0.0;
		}

		if (now >= this.checkAfter) {
			this.checkAfter = now + CHECK_EVERY;
			this.wanted = !ChestLabels.wanted(drifter, home).isEmpty();
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
		return new LabelChestsActivity(context.drifter());
	}
}
