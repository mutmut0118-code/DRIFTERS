package com.dinglebob.drifter.brain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.perception.BlockNotes;
import com.dinglebob.drifter.perception.TerrainKnowledge;
import com.dinglebob.drifter.world.PlayerPlacedBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Block queries for decisions, through perception only (DESIGN.md section 1, "Knowledge only through perception"):
 * only blocks the Drifter has seen ({@link TerrainKnowledge}) are considered, the same rule the pathfinder follows.
 * This replaces the milestone 2 test shortcut that scanned the world directly.
 */
public final class KnownBlocks {
	private KnownBlocks() {
	}

	/** Known blocks matching the filter within a radius, nearest first, at most {@code max}. */
	public static List<BlockPos> find(DrifterEntity drifter, int radius, Predicate<BlockState> filter, int max) {
		Level level = drifter.level();
		TerrainKnowledge terrain = drifter.getSenses().terrain();
		Vec3 from = drifter.position();
		List<BlockPos> found = new ArrayList<>();
		terrain.forEachKnown(drifter.blockPosition(), radius, pos -> {
			if (level.isLoaded(pos) && filter.test(level.getBlockState(pos))) {
				found.add(pos.immutable());
			}
		});
		found.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(from)));
		return found.size() > max ? new ArrayList<>(found.subList(0, max)) : found;
	}

	public static boolean any(DrifterEntity drifter, int radius, Predicate<BlockState> filter) {
		return !find(drifter, radius, filter, 1).isEmpty();
	}

	/**
	 * Not someone's build, not a container or other block entity, and not a station (its own crafting table): fair
	 * game to mine for resources.
	 */
	public static boolean mayTake(Level level, BlockPos pos) {
		if (PlayerPlacedBlocks.isPlayerPlaced(level, pos) || level.getBlockEntity(pos) != null) {
			return false;
		}

		BlockNotes.Note note = BlockNotes.classify(level.getBlockState(pos));
		return note == null || note.kind() != MemoryKind.STATION && note.kind() != MemoryKind.STORAGE;
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

	/** The nearest dry spot it has seen where it could stand (for getting out of water), or null. */
	@Nullable
	public static BlockPos nearestStandable(DrifterEntity drifter, int radius) {
		Level level = drifter.level();
		Vec3 from = drifter.position();
		BlockPos[] best = {null};
		double[] bestDistance = {Double.MAX_VALUE};
		drifter.getSenses().terrain().forEachKnown(drifter.blockPosition(), radius, pos -> {
			BlockPos above = pos.above();
			if (level.isLoaded(pos) && isStandable(level, above) && level.getFluidState(pos).isEmpty()) {
				double distance = above.distToCenterSqr(from);
				if (distance < bestDistance[0]) {
					bestDistance[0] = distance;
					best[0] = above.immutable();
				}
			}
		});
		return best[0];
	}

	/**
	 * The nearest spot (to the Drifter) whose floor it has seen and from which a standing player could see and reach
	 * the target block.
	 */
	@Nullable
	public static BlockPos standingSpotFor(DrifterEntity drifter, BlockPos target, Set<BlockPos> excluded) {
		Level level = drifter.level();
		TerrainKnowledge terrain = drifter.getSenses().terrain();
		// Nearest standable spots first; the first one it could reach the target from wins (few raycasts).
		List<BlockPos> spots = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(target.offset(-3, -4, -3), target.offset(3, 1, 3))) {
			if (!pos.equals(target) && !pos.equals(target.below()) && !excluded.contains(pos) && terrain.isKnown(pos.below())
				&& isStandable(level, pos)) {
				spots.add(pos.immutable());
			}
		}

		Vec3 from = drifter.position();
		spots.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(from)));
		for (BlockPos pos : spots) {
			Vec3 eye = new Vec3(pos.getX() + 0.5, pos.getY() + Player.DEFAULT_EYE_HEIGHT, pos.getZ() + 0.5);
			for (Direction face : Direction.values()) {
				if (Reach.aimPointFrom(drifter, eye, target, face).isPresent()) {
					return pos;
				}
			}
		}

		return null;
	}

	/** An empty spot next to the Drifter (not where it stands) where a block can sit on a sturdy floor it can see. */
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
				|| !level.getFluidState(pos).isEmpty()
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
