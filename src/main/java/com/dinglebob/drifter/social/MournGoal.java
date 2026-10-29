package com.dinglebob.drifter.social;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;

import net.minecraft.world.phys.Vec3;

/**
 * A friend gone for good (stakes, part 2; the user, 2026-10-28: "they can still grieve and have grudges and stuff"): once,
 * in the day and with nothing after it, it goes to where they died (it saw it, or was told where) and stands there a
 * while looking at the spot. Now and then a word, the way a player would ("o7"); as often, nothing.
 */
public final class MournGoal extends Goals.Goal {
	/** How far it goes for it. */
	private static final double RANGE = 160.0;
	/** How long it stands there (ticks). */
	private static final int STANDS = 20 * 12;

	@Override
	public String id() {
		return "mourn";
	}

	@Override
	public String name(Goals.Context context) {
		NewsBook.Mourning m = visit(context.drifter());
		return m == null ? "visit where a friend died" : "visit where " + m.name + " died";
	}

	@Nullable
	private static NewsBook.Mourning visit(DrifterEntity drifter) {
		String dimension = drifter.level().dimension().location().toString();
		for (NewsBook.Mourning m : drifter.getNews().mourning()) {
			if (!m.visited && m.where != null && m.dimension.equals(dimension) && m.where.closerThan(drifter.blockPosition(), RANGE)) {
				return m;
			}
		}

		return null;
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		if (context.threat().urgent() || Schedule.isNight(drifter) || drifter.getHealth() < drifter.getMaxHealth() * 0.5F) {
			return 0.0;
		}

		NewsBook.Mourning m = visit(drifter);
		return m == null ? 0.0 : 0.5 + 0.2 * m.closeness;
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
		NewsBook.Mourning m = visit(drifter);
		if (m == null) {
			return null;
		}

		Vec3 spot = Vec3.atBottomCenterOf(m.where);
		return new Activity(drifter) {
			private int walks;
			private boolean there;

			@Override
			public String describe() {
				return "visit where " + m.name + " died";
			}

			@Override
			protected int timeoutTicks() {
				return 20 * 120;
			}

			@Override
			protected boolean countsAsFailure(com.dinglebob.drifter.executor.Task step) {
				return false;
			}

			@Override
			protected Status next() {
				if (this.there) {
					m.visited = true;
					this.think("stood where " + m.name + " died for a while");
					return Status.SUCCESS;
				}

				if (this.drifter.position().distanceTo(spot) <= 3.5) {
					this.there = true;
					if (this.drifter.getRandom().nextFloat() < 0.4F) {
						ChatLines.sayLine(this.drifter, Lines.pick(this.drifter, "grief.visit", "who", m.name));
					}

					return this.begin(new LookAtTask(this.drifter, spot, STANDS));
				}

				if (this.walks++ > 2) {
					// (Somewhere it can't get to: it lets it be.)
					m.visited = true;
					return this.fail("can't get to where " + m.name + " died");
				}

				return this.begin(new NavigateTask(this.drifter, spot, 2.5, "walk to where " + m.name + " died"));
			}
		};
	}
}
