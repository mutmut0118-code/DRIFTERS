package com.dinglebob.drifter.whim;

import java.util.ArrayList;
import java.util.List;

import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.social.ChatLines;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Its town's crier (the user, 2026-11-06: "the idea of a drifter passing around written books of major news"): of a
 * morning, on the plaza, it calls out the day's headlines ({@link TownWork#news}); to each player about (two at most) it
 * hands a copy of the paper, written up in a book and quill of its own (one used up a copy; none, no copy: it makes them
 * with what it has, {@code Goals.TownPaper}).
 */
public final class CrierGoal extends Goals.Goal {
	/** Players this near the plaza get a copy. */
	private static final double HANDS_OUT = 16.0;

	@Override
	public String id() {
		return "crier";
	}

	@Override
	public String name(Goals.Context context) {
		return "cry the news on the plaza";
	}

	private static TownWork.News news(DrifterEntity drifter) {
		TownWork town = drifter.getTownWork();
		return town == null ? null : town.news(drifter);
	}

	@Override
	public double urgency(Goals.Context context) {
		return news(context.drifter()) == null || context.threat().urgent() ? 0.0 : 0.62;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 30;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		TownWork town = drifter.getTownWork();
		TownWork.News news = news(drifter);
		if (town == null || news == null) {
			return null;
		}

		BlockPos plaza = town.place(drifter, "plaza");
		BlockPos spot = plaza != null ? plaza : town.center();
		WhimActions.Steps steps = new WhimActions.Steps(drifter, "cry the news on the plaza", 20 * 120);
		for (int i = 0; i < 4; i++) {
			steps.then(() -> drifter.blockPosition().distSqr(spot) > 40 * 40 ? com.dinglebob.drifter.nav.Legs.toward(drifter, spot, 3.0, "go to the plaza") : null);
		}

		steps.then(() -> drifter.position().distanceTo(Vec3.atBottomCenterOf(spot)) > 3.0
			? new NavigateTask(drifter, Vec3.atBottomCenterOf(spot), 2.0, "go to the plaza", NavigateTask.Pace.STROLL) : null);
		for (int i = 0; i < news.headlines().size(); i++) {
			String headline = news.headlines().get(i);
			String opening = i == 0 ? com.dinglebob.drifter.social.Lines.pick(drifter, "crier.open", "news", headline) : null;
			String line = i > 0 ? headline : opening != null ? opening : "extra extra! " + headline;
			steps.then(() -> {
				ChatLines.speak(drifter, line);
				return new WhimActions.Gaze(drifter, () -> {
					var company = WhimActions.Chat.nearestCompany(drifter);
					return company != null ? company.getEyePosition() : null;
				}, 70, false, "cry the news");
			});
		}

		// A copy for each player about, while it has a book and quill to write it in.
		List<Player> given = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			steps.then(() -> {
				if (drifter.getInventory().count(stack -> stack.is(Items.WRITABLE_BOOK)) == 0) {
					return null;
				}

				Player to = null;
				for (Player player : drifter.level().players()) {
					if (!player.isSpectator() && player.isAlive() && !given.contains(player) && player.distanceTo(drifter) <= HANDS_OUT
						&& (to == null || player.distanceTo(drifter) < to.distanceTo(drifter))) {
						to = player;
					}
				}

				if (to == null) {
					return null;
				}

				Player reader = to;
				given.add(reader);
				return new WhimActions.Gaze(drifter, reader::getEyePosition, 15, false, "hand over the paper") {
					@Override
					protected Status onTick() {
						Status status = super.onTick();
						if (status == Status.SUCCESS) {
							int slot = -1;
							for (int s = 0; s < drifter.getInventory().getContainerSize(); s++) {
								if (drifter.getInventory().getItem(s).is(Items.WRITABLE_BOOK)) {
									slot = s;
									break;
								}
							}

							if (slot >= 0) {
								drifter.getInventory().removeItem(slot, 1);
								drifter.tossForward(news.paper().copy());
								String hand = com.dinglebob.drifter.social.Lines.pick(drifter, "crier.paper");
								ChatLines.speak(drifter, hand != null ? hand : "here, todays paper");
							}
						}

						return status;
					}
				};
			});
		}

		steps.then(() -> {
			town.cried(drifter);
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "cried the news on the plaza");
			return null;
		});
		return steps;
	}
}
