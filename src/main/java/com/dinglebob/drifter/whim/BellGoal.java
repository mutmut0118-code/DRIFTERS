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
 * over, rings it three times as a player would (struck on a side it rings from), and calls out why. An alarm (0.23.30; the
 * user, 2026-11-08: "for like major stuff like a massacre or a raid will people spam ring the bell?"): it runs, rings it
 * a dozen times fast, and shouts what's wrong; everyone within earshot hears an alarm
 * ({@link com.dinglebob.drifter.perception.BellHearing}), and its town has it rung again while the trouble lasts.
 */
public final class BellGoal extends Goals.Goal {
	/** Strokes for an alarm (fast), and the pause between them; a festival's three, slow. */
	private static final int ALARM_STROKES = 12;
	private static final int ALARM_PAUSE = 3;
	private static final int STROKES = 3;
	private static final int PAUSE = 12;

	@Override
	public String id() {
		return "bell";
	}

	@Override
	public String name(Goals.Context context) {
		TownWork.Bell bell = bell(context.drifter());
		return bell == null ? "ring the town bell" : (bell.alarm() ? "ring the alarm bell for " : "ring the bell for ") + bell.why();
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

		// (An alarm: over going in or to the trouble, so the one picked to ring it does.)
		return bell.alarm() ? 0.97 : 0.88;
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
		boolean alarm = bell.alarm();
		WhimActions.Steps steps = new WhimActions.Steps(drifter, (alarm ? "ring the alarm bell for " : "ring the bell for ") + bell.why(), 20 * 90);
		// Over to it: from far, a stretch at a time; then to a spot it can reach it from.
		for (int i = 0; i < 6; i++) {
			steps.then(() -> drifter.blockPosition().distSqr(pos) > 40 * 40 ? com.dinglebob.drifter.nav.Legs.toward(drifter, pos, 4.0, "go to the bell") : null);
		}

		steps.then(() -> {
			if (com.dinglebob.drifter.hands.Reach.canReach(drifter, pos) && WhimActions.UseBlock.face(drifter, pos) != null) {
				return null;
			}

			// (In front of it or behind it, where a stroke rings it: found in testing, 0.23.35, it stood beside one, every stroke
			// missed, and nobody heard the alarm for a killing.)
			BlockPos stand = ringingSpot(drifter, pos);
			if (stand == null) {
				stand = KnownBlocks.standingSpotFor(drifter, pos, Set.of());
			}
			return stand == null ? new NavigateTask(drifter, Vec3.atBottomCenterOf(pos), 2.0, "go to the bell")
				: new NavigateTask(drifter, Vec3.atBottomCenterOf(stand), 0.6, "go to the bell");
		});
		// (An alarm: what for and where, for those who hear it.)
		if (alarm && drifter.level() instanceof net.minecraft.server.level.ServerLevel level) {
			steps.then(() -> {
				com.dinglebob.drifter.perception.BellHearing.say(level, pos, bell.why(), bell.trouble());
				return null;
			});
		}

		int strokes = alarm ? ALARM_STROKES : STROKES;
		for (int i = 0; i < strokes; i++) {
			steps.then(() -> {
				Direction face = WhimActions.UseBlock.face(drifter, pos);
				return face == null ? null : UseOnBlockTask.useBlock(drifter, pos, face, "ring the bell");
			});
			// (An alarm's shouted a few strokes in, so it's heard while the bell's still going.)
			if (alarm && i == 2) {
				steps.then(() -> {
					shout(drifter, bell);
					return null;
				});
			}

			steps.then(() -> new WhimActions.Gaze(drifter, () -> Vec3.atCenterOf(pos), alarm ? ALARM_PAUSE : PAUSE, false, "ring the bell"));
		}

		steps.then(() -> {
			if (!alarm) {
				shout(drifter, bell);
			}

			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), (alarm ? "rang the alarm bell for " : "rang the bell for ") + bell.why());
			town.rang(drifter);
			return null;
		});
		return steps;
	}

	/**
	 * A spot to stand and ring it from: a block or two off it on a side a stroke rings it on (BellBlock#isProperHit: along
	 * its facing on the floor, across it on a wall), with ground under it and room to stand; the nearest; or null.
	 */
	@org.jetbrains.annotations.Nullable
	private static BlockPos ringingSpot(DrifterEntity drifter, BlockPos bell) {
		var level = drifter.level();
		var state = level.getBlockState(bell);
		if (!(state.getBlock() instanceof net.minecraft.world.level.block.BellBlock)) {
			return null;
		}

		var attach = state.getValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT);
		Direction facing = state.getValue(net.minecraft.world.level.block.BellBlock.FACING);
		BlockPos best = null;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			boolean rings = switch (attach) {
				case FLOOR -> facing.getAxis() == side.getAxis();
				case SINGLE_WALL, DOUBLE_WALL -> facing.getAxis() != side.getAxis();
				case CEILING -> true;
			};
			if (!rings) {
				continue;
			}

			for (int out = 1; out <= 2; out++) {
				for (int dy = -1; dy <= 0; dy++) {
					BlockPos at = bell.relative(side, out).above(dy);
					if (level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
						&& !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()
						&& (best == null || at.distSqr(drifter.blockPosition()) < best.distSqr(drifter.blockPosition()))) {
						best = at;
					}
				}
			}
		}

		return best;
	}

	private static void shout(DrifterEntity drifter, TownWork.Bell bell) {
		if (!bell.lines().isEmpty()) {
			com.dinglebob.drifter.social.ChatLines.speak(drifter, bell.lines().get(drifter.getRandom().nextInt(bell.lines().size())));
		}
	}
}
