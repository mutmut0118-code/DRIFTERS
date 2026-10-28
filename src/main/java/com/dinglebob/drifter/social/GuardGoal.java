package com.dinglebob.drifter.social;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.perception.Awareness;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * A town's guard collecting a fine (milestone 22 step 5, part 2; the user, 2026-10-28: "Guards and fines"): the town says
 * whom ({@link TownWork#arrest}: a player who broke its law, seen in the town). It walks up to them, tells them what they
 * owe and why, and waits a minute for the emeralds (thrown to it: it picks them up as a player would). Paid: done. Not
 * paid, or they walk off: it tells the town, which exiles them. It doesn't start the fighting; if they hit it, it fights
 * back as anyone would.
 */
public final class GuardGoal extends Goals.Goal {
	/** How long it waits for the fine. */
	private static final int WAITS = 20 * 60;
	/** Close enough to say it. */
	private static final double CLOSE = 3.0;
	/** They walked this far off: refused. */
	private static final double WALKED_OFF = 24.0;

	@Override
	public String id() {
		return "guard";
	}

	@Override
	public String name(Goals.Context context) {
		TownWork.Arrest arrest = arrest(context.drifter());
		return arrest == null ? "keep the peace" : "collect a fine from " + arrest.name();
	}

	@Nullable
	private static TownWork.Arrest arrest(DrifterEntity drifter) {
		TownWork town = drifter.getTownWork();
		return town == null ? null : town.arrest(drifter);
	}

	/** The player, in this world, and seen by it lately. */
	@Nullable
	private static Player culprit(DrifterEntity drifter, TownWork.Arrest arrest) {
		Player player = drifter.level().getPlayerByUUID(arrest.player());
		Awareness seen = player == null ? null : drifter.getSenses().awarenessOf(player);
		return player != null && player.isAlive() && seen != null && seen.identified ? player : null;
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		TownWork.Arrest arrest = arrest(drifter);
		if (arrest == null || context.threat().urgent() || drifter.getHealth() < drifter.getMaxHealth() * 0.5F || culprit(drifter, arrest) == null) {
			return 0.0;
		}

		return 0.78;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public boolean urgent(Goals.Context context) {
		// (Its duty: it stops what it's doing at the next checkpoint.)
		return true;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 20;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		TownWork town = drifter.getTownWork();
		TownWork.Arrest arrest = arrest(drifter);
		Player player = arrest == null ? null : culprit(drifter, arrest);
		if (town == null || player == null) {
			return null;
		}

		return new Activity(drifter) {
			private boolean told;
			private long toldAt;
			private int before;

			@Override
			public String describe() {
				return "collect a fine from " + arrest.name();
			}

			@Override
			protected int timeoutTicks() {
				return WAITS + 20 * 60;
			}

			@Override
			protected boolean countsAsFailure(com.dinglebob.drifter.executor.Task step) {
				return false;
			}

			@Override
			protected Status next() {
				if (!player.isAlive() || player.isRemoved()) {
					return Status.SUCCESS;
				}

				int emeralds = this.drifter.getInventory().count(stack -> stack.is(Items.EMERALD));
				if (this.told) {
					int paid = emeralds - this.before;
					if (paid >= arrest.fine()) {
						ChatLines.speak(this.drifter, Lines.pick(this.drifter, "guard.paid", "player", arrest.name()));
						this.think(arrest.name() + " paid the fine: " + paid + " emerald" + (paid == 1 ? "" : "s"));
						town.arrested(this.drifter, arrest.player(), paid, false);
						return Status.SUCCESS;
					}

					if (this.now() - this.toldAt > WAITS || this.drifter.distanceTo(player) > WALKED_OFF) {
						var group = com.dinglebob.drifter.world.DrifterGroups.of(this.drifter.getUUID());
						ChatLines.speak(this.drifter, Lines.pick(this.drifter, "guard.refused", "player", arrest.name(), "town", group == null ? "town" : group.name));
						this.think(arrest.name() + " wouldn't pay the fine");
						town.arrested(this.drifter, arrest.player(), Math.max(0, paid), true);
						return Status.SUCCESS;
					}

					// Keeps an eye on them while it waits (and stays close).
					if (this.drifter.distanceTo(player) > CLOSE + 2.0) {
						return this.begin(new NavigateTask(this.drifter, player.position(), CLOSE, "keep close to " + arrest.name()));
					}

					return this.begin(new LookAtTask(this.drifter, player.getEyePosition(), 20));
				}

				if (this.drifter.distanceTo(player) > CLOSE) {
					return this.begin(new NavigateTask(this.drifter, player.position(), CLOSE - 0.5, "walk up to " + arrest.name()));
				}

				this.told = true;
				this.toldAt = this.now();
				this.before = emeralds;
				ChatLines.speak(this.drifter, Lines.pick(this.drifter, "guard.demand", "player", arrest.name(), "fine", arrest.fine(), "crime", arrest.crime()));
				this.think("told " + arrest.name() + " to pay " + arrest.fine() + " emeralds for " + arrest.crime());
				return this.begin(new LookAtTask(this.drifter, player.getEyePosition(), 20));
			}
		};
	}
}
