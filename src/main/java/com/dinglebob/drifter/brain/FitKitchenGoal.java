package com.dinglebob.drifter.brain;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.brain.activity.FitKitchenActivity;
import com.dinglebob.drifter.entity.DrifterEntity;

/**
 * Fitting out the kitchen (Farmer's Delight, optional): kitchen things it carries (the stove, the cooking pot, the cutting
 * board, a skillet, cabinets) go in their places in its house's layout, as the smoker goes in the hut. Only with the mod
 * and a house of its own; not again for a while if it couldn't.
 */
public final class FitKitchenGoal extends Goals.Goal {
	private static final double URGENCY = 0.46;
	private static final long CHECK_EVERY = 20L * 20;
	private static final long AFTER_FAILING = 20L * 60 * 10;

	private long checkAfter;
	private boolean wanted;
	private long failedAt = Long.MIN_VALUE / 2;

	@Override
	public String id() {
		return "kitchen";
	}

	@Override
	public String name(Goals.Context context) {
		return "fit out its kitchen";
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		if (!Kitchen.wanted(drifter)) {
			return 0.0;
		}

		if (drifter.getDrifterBrain().currentGoal() == this && drifter.getDrifterBrain().activity() instanceof FitKitchenActivity activity && !activity.finished()) {
			return URGENCY + 0.15;
		}

		long now = drifter.level().getGameTime();
		Home home = drifter.getDrifterBrain().home();
		if (home == null || !home.near(drifter, 32.0) || Schedule.isNight(drifter) || now - this.failedAt < AFTER_FAILING) {
			return 0.0;
		}

		if (now >= this.checkAfter) {
			this.checkAfter = now + CHECK_EVERY;
			this.wanted = FitKitchenActivity.nextPiece(drifter) != null;
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
		return new FitKitchenActivity(context.drifter());
	}
}
