package com.dinglebob.drifterciv;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Can a town be walked round (milestone 23, docs/LIVED_WORLD.md A11: "easy to get around without the town being covered
 * with cobblestone pillaring and bridging")? From the plaza, every spot reachable on foot as a player walks: a block up
 * with a jump, up to three down, two blocks of room, through doors and gates; no blocks put down or broken, no swimming.
 * Which of the places that matter (its doors, the store, the stall, the fields) it can't reach.
 */
final class TownWalk {
	private TownWalk() {
	}

	/** Most a walk drops in one step (a player takes three without much harm; Drifters take them too). */
	private static final int DROP = 3;

	/**
	 * The names of the {@code places} (each with the spots one could use it from: a field from any side) not reachable on
	 * foot from {@code from} within {@code area}.
	 */
	static List<String> unreachable(ServerLevel level, BoundingBox area, BlockPos from, Map<String, List<BlockPos>> places) {
		BlockPos start = settle(level, from);
		Set<Long> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		if (start != null) {
			seen.add(start.asLong());
			queue.add(start);
		}

		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			for (Direction way : Direction.Plane.HORIZONTAL) {
				BlockPos next = step(level, at, way);
				if (next != null && area.isInside(next) && seen.add(next.asLong())) {
					queue.add(next);
				}
			}
		}

		List<String> missed = new ArrayList<>();
		for (Map.Entry<String, List<BlockPos>> place : places.entrySet()) {
			boolean reached = false;
			for (BlockPos pos : place.getValue()) {
				BlockPos spot = settle(level, pos);
				// (Reached if it, or the spot beside it, was: standing next to a door or a chest does.)
				reached |= spot != null && (seen.contains(spot.asLong()) || Direction.Plane.HORIZONTAL.stream()
					.anyMatch(way -> seen.contains(spot.relative(way).asLong()) || seen.contains(spot.relative(way).above().asLong())
						|| seen.contains(spot.relative(way).below().asLong())));
			}

			if (!reached) {
				missed.add(place.getKey());
			}
		}

		return missed;
	}

	/** Where one would stand at this spot: on the ground at or a little below or above it. */
	private static BlockPos settle(ServerLevel level, BlockPos pos) {
		for (int dy = 0; dy <= 3; dy++) {
			if (standable(level, pos.below(dy))) {
				return pos.below(dy);
			}

			if (standable(level, pos.above(dy))) {
				return pos.above(dy);
			}
		}

		return null;
	}

	/** One step from {@code at} this way: up a block (room for the jump), level, or down as far as {@link #DROP}. */
	private static BlockPos step(ServerLevel level, BlockPos at, Direction way) {
		BlockPos side = at.relative(way);
		if (standable(level, side.above()) && open(level, at.above(2))) {
			return side.above();
		}

		if (standable(level, side)) {
			return side;
		}

		if (!open(level, side) || !open(level, side.above())) {
			return null;
		}

		for (int down = 1; down <= DROP; down++) {
			BlockPos below = side.below(down);
			if (standable(level, below)) {
				return below;
			}

			if (!open(level, below)) {
				return null;
			}
		}

		return null;
	}

	/** Feet here: something firm under them (not a fence or wall: too tall to step on), room for a player, no water. */
	static boolean standable(ServerLevel level, BlockPos feet) {
		BlockPos under = feet.below();
		BlockState ground = level.getBlockState(under);
		if (ground.getCollisionShape(level, under).isEmpty() || ground.is(BlockTags.FENCES) || ground.is(BlockTags.WALLS)
			|| ground.getBlock() instanceof FenceGateBlock) {
			return false;
		}

		return open(level, feet) && open(level, feet.above()) && level.getFluidState(feet).isEmpty();
	}

	/** A player fits through here: nothing in the way (a door or gate opens). */
	private static boolean open(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock || state.getCollisionShape(level, pos).isEmpty()
			&& !state.is(net.minecraft.world.level.block.Blocks.LAVA);
	}
}
