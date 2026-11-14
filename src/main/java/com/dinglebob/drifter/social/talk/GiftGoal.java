package com.dinglebob.drifter.social.talk;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.social.Mode;
import com.dinglebob.drifter.social.SocialWatcher;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * A gift it didn't catch (docs/PLAYER_TALK.md E6.2; the user, 2026-11-12: "i tried to give it steak but it didnt stop for a
 * gift since it was fighting a zombie"): something a player it doesn't mind threw toward it lately, lying near and in
 * sight. Once it's out of danger it walks over and picks it up (picking up is by touch, as a player's: the gift and the
 * thanks follow as ever).
 */
public final class GiftGoal extends Goals.Goal {
	/** Thrown this lately (ticks), this near (blocks). */
	private static final int FRESH = 20 * 90;
	private static final double NEAR = 10.0;

	@Nullable
	private static ItemEntity gift(DrifterEntity drifter) {
		ItemEntity best = null;
		for (ItemEntity item : drifter.level().getEntitiesOfClass(ItemEntity.class, drifter.getBoundingBox().inflate(NEAR, 4.0, NEAR), ItemEntity::isAlive)) {
			if (item.getAge() > FRESH || item.hasPickUpDelay() || !(item.getOwner() instanceof ServerPlayer thrower) || !SocialWatcher.isRealPlayer(thrower)
				|| thrower.distanceTo(drifter) > 16.0 || Mode.atOdds(drifter, thrower) || !drifter.hasLineOfSight(item)) {
				continue;
			}

			// (Not one thrown at another Drifter that's nearer to it.)
			DrifterEntity nearer = drifter.level().getEntitiesOfClass(DrifterEntity.class, item.getBoundingBox().inflate(3.0), d -> d != drifter && d.isAlive()
				&& d.distanceToSqr(item) < drifter.distanceToSqr(item)).stream().findFirst().orElse(null);
			if (nearer == null && (best == null || item.distanceToSqr(drifter) < best.distanceToSqr(drifter))) {
				best = item;
			}
		}

		return best;
	}

	@Override
	public String id() {
		return "gift";
	}

	@Override
	public String name(Goals.Context context) {
		return "pick up what a player threw it";
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		boolean running = drifter.getDrifterBrain().currentGoal() == this;
		if (context.threat().urgent() || drifter.inAFight() || drifter.isSleeping()) {
			return 0.0;
		}

		// (Looked for twice a second; kept once it's on its way.)
		if (running) {
			return 0.72;
		}

		if (drifter.tickCount % 10 != 0) {
			return 0.0;
		}

		return gift(drifter) == null ? 0.0 : 0.72;
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
		if (gift(drifter) == null) {
			return null;
		}

		return new Activity(drifter) {
			@Override
			public String describe() {
				return "pick up a gift";
			}

			@Override
			protected int timeoutTicks() {
				return 20 * 20;
			}

			@Override
			protected boolean countsAsFailure(Task step) {
				return false;
			}

			@Override
			protected Status next() {
				ItemEntity item = gift(this.drifter);
				if (item == null) {
					return Status.SUCCESS;
				}

				return this.begin(new NavigateTask(this.drifter, item.position(), 0.6, "pick up a gift", NavigateTask.Pace.STROLL));
			}
		};
	}
}
