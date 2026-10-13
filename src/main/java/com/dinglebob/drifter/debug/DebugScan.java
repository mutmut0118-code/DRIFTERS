package com.dinglebob.drifter.debug;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * DEBUG ONLY. Reads the world directly, which breaks the "knowledge only through perception" rule (DESIGN.md
 * section 1). Used only by scripted test sequences until perception and memory exist (milestone 3).
 * Never use this from AI code.
 */
public final class DebugScan {
	private DebugScan() {
	}

	/** Blocks matching the filter within a radius, nearest first. */
	public static List<BlockPos> findBlocks(DrifterEntity drifter, int radius, Predicate<BlockState> filter) {
		Level level = drifter.level();
		BlockPos center = drifter.blockPosition();
		List<BlockPos> found = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			if (filter.test(level.getBlockState(pos))) {
				found.add(pos.immutable());
			}
		}

		Vec3 from = drifter.position();
		found.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(from)));
		return found;
	}

	public static boolean isExposed(Level level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			if (level.getBlockState(pos.relative(direction)).getCollisionShape(level, pos.relative(direction)).isEmpty()) {
				return true;
			}
		}

		return false;
	}

	/** A spot a player could stand on: sturdy floor, two blocks of open space, no fluid. */
	public static boolean isStandable(Level level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
			&& level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
			&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
			&& level.getFluidState(pos).isEmpty()
			&& level.getFluidState(pos.above()).isEmpty();
	}

	/** The nearest spot (to the Drifter) from which a standing player could see and reach the target block. */
	@Nullable
	public static BlockPos standingSpotFor(DrifterEntity drifter, BlockPos target) {
		Level level = drifter.level();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(target.offset(-3, -4, -3), target.offset(3, 1, 3))) {
			if (pos.equals(target) || pos.equals(target.below()) || !isStandable(level, pos)) {
				continue;
			}

			Vec3 eye = new Vec3(pos.getX() + 0.5, pos.getY() + Player.DEFAULT_EYE_HEIGHT, pos.getZ() + 0.5);
			boolean reachable = false;
			for (Direction face : Direction.values()) {
				if (Reach.aimPointFrom(drifter, eye, target, face).isPresent()) {
					reachable = true;
					break;
				}
			}

			double distance = pos.distToCenterSqr(drifter.position());
			if (reachable && distance < bestDistance) {
				bestDistance = distance;
				best = pos.immutable();
			}
		}

		return best;
	}

	/** An empty spot next to the Drifter (not where it stands) where a block can sit on a sturdy floor. */
	public static Optional<BlockPos> placementSpotNear(DrifterEntity drifter, Set<BlockPos> excluded) {
		Level level = drifter.level();
		BlockPos feet = drifter.blockPosition();
		AABB body = drifter.getBoundingBox();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 1, 2))) {
			BlockPos below = pos.below();
			if (excluded.contains(pos)
				|| !level.getBlockState(pos).canBeReplaced()
				|| !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
				|| new AABB(pos).intersects(body)
				|| Reach.aimPoint(drifter, below, Direction.UP).isEmpty()) {
				continue;
			}

			double distance = pos.distToCenterSqr(drifter.position());
			if (distance < bestDistance) {
				bestDistance = distance;
				best = pos.immutable();
			}
		}

		return Optional.ofNullable(best);
	}
}
