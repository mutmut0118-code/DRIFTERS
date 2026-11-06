package com.dinglebob.drifter.whim;

import java.util.Set;

import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.UseOnBlockTask;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Ringing its town's bell when something's begun (the user, 2026-11-06: "when an event happens (festival, market day, a
 * war, etc) someone should ring the bell to alert everybody"): the one its town picked ({@link TownWork#bell}) walks
 * over, rings it three times as a player would (struck on a side it rings from), and calls out why.
 */
public final class BellGoal extends Goals.Goal {
	@Override
	public String id() {
		return "bell";
	}

	@Override
	public String name(Goals.Context context) {
		TownWork.Bell bell = bell(context.drifter());
		return bell == null ? "ring the town bell" : "ring the bell for " + bell.why();
	}

	private static TownWork.Bell bell(DrifterEntity drifter) {
		TownWork town = drifter.getTownWork();
		return town == null ? null : town.bell(drifter);
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		TownWork.Bell bell = bell(drifter);
		if (bell == null || context.threat().urgent() || !drifter.level().isLoaded(bell.pos())
			|| !(drifter.level().getBlockState(bell.pos()).getBlock() instanceof net.minecraft.world.level.block.BellBlock)) {
			return 0.0;
		}

		return 0.88;
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
	public int minCommitTicks() {
		return 20 * 30;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		TownWork town = drifter.getTownWork();
		TownWork.Bell bell = bell(drifter);
		if (town == null || bell == null) {
			return null;
		}

		BlockPos pos = bell.pos();
		WhimActions.Steps steps = new WhimActions.Steps(drifter, "ring the bell for " + bell.why(), 20 * 90);
		// Over to it: from far, a stretch at a time; then to a spot it can reach it from.
		for (int i = 0; i < 6; i++) {
			steps.then(() -> drifter.blockPosition().distSqr(pos) > 40 * 40 ? com.dinglebob.drifter.nav.Legs.toward(drifter, pos, 4.0, "go to the bell") : null);
		}

		steps.then(() -> {
			if (com.dinglebob.drifter.hands.Reach.canReach(drifter, pos) && WhimActions.UseBlock.face(drifter, pos) != null) {
				return null;
			}

			BlockPos stand = KnownBlocks.standingSpotFor(drifter, pos, Set.of());
			return stand == null ? new NavigateTask(drifter, Vec3.atBottomCenterOf(pos), 2.0, "go to the bell")
				: new NavigateTask(drifter, Vec3.atBottomCenterOf(stand), 0.6, "go to the bell");
		});
		for (int i = 0; i < 3; i++) {
			steps.then(() -> {
				Direction face = WhimActions.UseBlock.face(drifter, pos);
				return face == null ? null : UseOnBlockTask.useBlock(drifter, pos, face, "ring the bell");
			});
			steps.then(() -> new WhimActions.Gaze(drifter, () -> Vec3.atCenterOf(pos), 12, false, "ring the bell"));
		}

		steps.then(() -> {
			if (!bell.lines().isEmpty()) {
				com.dinglebob.drifter.social.ChatLines.speak(drifter, bell.lines().get(drifter.getRandom().nextInt(bell.lines().size())));
			}

			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "rang the bell for " + bell.why());
			town.rang(drifter);
			return null;
		});
		return steps;
	}
}
