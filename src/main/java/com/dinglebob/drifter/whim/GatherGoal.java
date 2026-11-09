package com.dinglebob.drifter.whim;

import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A town's gathering (the user, 2026-11-06: "when festivals happen civs should mainly gather around the plaza"): while
 * its town has one on ({@link TownWork#gathering}), it goes there, and there it mingles: a spot in the crowd, a look at
 * whoever's about, a happy hop now and then. Its whims and talks still come (more of them on such a day), round the plaza.
 */
public final class GatherGoal extends Goals.Goal {
	/** Away from it: over it goes (above most of its work). */
	private static final double GO = 0.6;
	/** There: it stays about, between its whims and talks. */
	private static final double STAY = 0.42;
	/** Within this of the gathering's spot it's there. */
	private static final double THERE = 14.0;

	@Override
	public String id() {
		return "gather";
	}

	@Override
	public String name(Goals.Context context) {
		return "join the crowd at the plaza";
	}

	private static BlockPos spot(DrifterEntity drifter) {
		TownWork town = drifter.getTownWork();
		return town == null ? null : town.gathering(drifter);
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		BlockPos spot = spot(drifter);
		if (spot == null || context.threat().urgent() || drifter.encounter() != null) {
			return 0.0;
		}

		return drifter.blockPosition().closerThan(spot, THERE) ? STAY : GO;
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
		DrifterEntity drifter = context.drifter();
		BlockPos spot = spot(drifter);
		if (spot == null) {
			return null;
		}

		WhimActions.Steps steps = new WhimActions.Steps(drifter, "join the crowd at the plaza", 20 * 90);
		for (int i = 0; i < 4; i++) {
			steps.then(() -> drifter.blockPosition().distSqr(spot) > 40 * 40 ? com.dinglebob.drifter.nav.Legs.toward(drifter, spot, 4.0, "go to the plaza") : null);
		}

		steps.then(() -> drifter.position().distanceTo(Vec3.atBottomCenterOf(spot)) > 2.5
			? new NavigateTask(drifter, Vec3.atBottomCenterOf(spot), 1.5, "go to the plaza", NavigateTask.Pace.STROLL) : null);
		// Whoever's about: a look at them, a happy hop now and then.
		steps.then(() -> {
			Entity company = WhimActions.Chat.nearestCompany(drifter);
			return company != null ? new WhimActions.Gaze(drifter, () -> company.isAlive() ? company.getEyePosition() : null, 80 + drifter.getRandom().nextInt(80),
				true, "join the crowd at the plaza") : new com.dinglebob.drifter.executor.task.LookAroundTask(drifter);
		});
		return steps;
	}
}
