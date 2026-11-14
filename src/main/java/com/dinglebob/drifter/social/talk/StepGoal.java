package com.dinglebob.drifter.social.talk;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.motor.Steering;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Going somewhere because a player asked (docs/PLAYER_TALK.md E1.4, E1.5, E1.8, F2.4): out of their way, out of its
 * house, to meet them at the plaza, home to see what's wrong. It walks there (as it walks anywhere), waits there a while
 * if asked to, then does what was to be done on arriving ({@code then}).
 */
public final class StepGoal extends Goals.Goal {
	private record Step(BlockPos to, double near, int waits, String what, @Nullable Runnable then, long since) {
	}

	private static final long AT_MOST = 20L * 60 * 4;
	private static final Map<UUID, Step> steps = new HashMap<>();

	/** It goes to {@code to} (within {@code near}), stays {@code waits} ticks, then runs {@code then}. */
	public static void go(DrifterEntity drifter, BlockPos to, double near, int waits, String what, @Nullable Runnable then) {
		steps.put(drifter.getUUID(), new Step(to.immutable(), near, waits, what, then, drifter.level().getGameTime()));
	}

	public static boolean going(DrifterEntity drifter) {
		return steps.containsKey(drifter.getUUID());
	}

	@Override
	public String id() {
		return "step";
	}

	@Override
	public String name(Goals.Context context) {
		Step step = steps.get(context.drifter().getUUID());
		return step == null ? "go where a player asked" : step.what();
	}

	@Override
	public double urgency(Goals.Context context) {
		Step step = steps.get(context.drifter().getUUID());
		if (step == null) {
			return 0.0;
		}

		if (context.drifter().level().getGameTime() - step.since() > AT_MOST) {
			steps.remove(context.drifter().getUUID());
			return 0.0;
		}

		return context.threat().urgent() ? 0.0 : 0.79;
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
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		Step step = steps.get(drifter.getUUID());
		if (step == null) {
			return null;
		}

		return new Activity(drifter) {
			private long arrivedAt = -1;

			@Override
			public String describe() {
				return step.what();
			}

			@Override
			protected int timeoutTicks() {
				return (int)AT_MOST;
			}

			@Override
			protected boolean countsAsFailure(Task task) {
				return false;
			}

			@Override
			protected Status next() {
				if (steps.get(this.drifter.getUUID()) != step) {
					return Status.SUCCESS;
				}

				long now = this.drifter.level().getGameTime();
				Vec3 at = Vec3.atBottomCenterOf(step.to());
				if (this.arrivedAt < 0 && this.drifter.position().distanceTo(at) > step.near() + 1.0) {
					return this.begin(com.dinglebob.drifter.nav.Legs.toward(this.drifter, step.to(), step.near(), step.what(), NavigateTask.Pace.STROLL));
				}

				if (this.arrivedAt < 0) {
					this.arrivedAt = now;
				}

				Steering.stopMoving(this.drifter);
				if (now - this.arrivedAt < step.waits()) {
					return Status.RUNNING;
				}

				steps.remove(this.drifter.getUUID(), step);
				if (step.then() != null) {
					step.then().run();
				}

				return Status.SUCCESS;
			}
		};
	}
}
