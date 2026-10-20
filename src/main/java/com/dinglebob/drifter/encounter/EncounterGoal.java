package com.dinglebob.drifter.encounter;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * An encounter's Drifter playing its part (milestone 21): what the encounter wants of it, as a goal like any other, so
 * staying alive still comes first (a fight, fleeing, eating). Once the encounter is over: it heads off, away from
 * everyone, and is gone once out of sight.
 */
public final class EncounterGoal extends Goals.Goal {
	/** Leaving comes before nearly everything (only a chase it's in, or staying alive, beats it). */
	private static final double LEAVING = 0.96;
	/** How far off it heads when it leaves. */
	private static final double LEAVE_DISTANCE = 140.0;

	@Override
	public String id() {
		return "encounter";
	}

	@Override
	public String name(Goals.Context context) {
		Encounter encounter = context.drifter().encounter();
		return encounter == null ? "its part in something" : encounter.isOver() ? "head off on its way" : encounter.describe(context.drifter());
	}

	@Override
	public double urgency(Goals.Context context) {
		Encounter encounter = context.drifter().encounter();
		if (encounter == null) {
			return 0.0;
		}

		return encounter.isOver() ? LEAVING : encounter.urgency(context.drifter());
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	/** Its part comes first whenever it wants doing (it's what it's there for; staying alive still beats it on score). */
	@Override
	public boolean urgent(Goals.Context context) {
		Encounter encounter = context.drifter().encounter();
		return encounter != null && (encounter.isOver() || encounter.urgent(context.drifter()) || encounter.urgency(context.drifter()) > 0.0);
	}

	@Override
	public int minCommitTicks() {
		return 20 * 5;
	}

	@Nullable
	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		Encounter encounter = drifter.encounter();
		if (encounter == null) {
			return null;
		}

		return encounter.isOver() ? leave(drifter) : encounter.start(drifter);
	}

	/** Away from the nearest player, a long way, at a walk (it isn't running from anything). */
	static Activity leave(DrifterEntity drifter) {
		Player nearest = drifter.level().getNearestPlayer(drifter, 256.0);
		Vec3 from = nearest == null ? drifter.position().add(1.0, 0.0, 0.0) : nearest.position();
		Vec3 to = Encounter.beyond(from, drifter.position(), LEAVE_DISTANCE);
		return new Script(drifter, "head off on its way", 20 * 60 * 3)
			.then(() -> new NavigateTask(drifter, to, 4.0, "head off", NavigateTask.Pace.STROLL));
	}
}
