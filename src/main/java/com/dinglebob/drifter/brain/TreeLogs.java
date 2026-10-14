package com.dinglebob.drifter.brain;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Telling a tree from a building (user's question in session 2: "would they mine village houses for resources?"). A
 * log is part of a tree when it connects, through logs, to natural leaves (leaves a tree grew, not ones someone placed).
 * Village house posts, log cabins and other builds have none, so they're not wood to gather and not something to cut
 * through on the way somewhere.
 */
public final class TreeLogs {
	/** Logs it follows from one log looking for leaves (a big jungle or dark oak tree has fewer reachable this way). */
	private static final int MAX_LOGS = 64;

	private final Level level;
	/** Answers for whole connected groups of logs, so a scan over a forest checks each tree once. */
	private final Map<BlockPos, Boolean> known = new HashMap<>();

	public TreeLogs(Level level) {
		this.level = level;
	}

	/** One-off check (no memo). */
	public static boolean isTree(Level level, BlockPos pos) {
		return new TreeLogs(level).check(pos);
	}

	public boolean check(BlockPos start) {
		Boolean cached = this.known.get(start);
		if (cached != null) {
			return cached;
		}

		if (!this.level.getBlockState(start).is(BlockTags.LOGS)) {
			return false;
		}

		Set<BlockPos> group = new HashSet<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		open.add(start.immutable());
		group.add(start.immutable());
		boolean tree = false;
		while (!open.isEmpty() && group.size() <= MAX_LOGS && !tree) {
			BlockPos log = open.poll();
			for (Direction side : Direction.values()) {
				if (naturalLeaves(this.level.getBlockState(log.relative(side)))) {
					tree = true;
					break;
				}
			}

			// Branches can step diagonally (acacia, big oaks): follow logs in the 3x3x3 around this one.
			for (BlockPos next : BlockPos.betweenClosed(log.offset(-1, -1, -1), log.offset(1, 1, 1))) {
				if (!group.contains(next) && this.level.isLoaded(next) && this.level.getBlockState(next).is(BlockTags.LOGS)) {
					BlockPos copy = next.immutable();
					group.add(copy);
					open.add(copy);
				}
			}
		}

		for (BlockPos log : group) {
			this.known.put(log, tree);
		}

		return tree;
	}

	private static boolean naturalLeaves(BlockState state) {
		return state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT);
	}
}
