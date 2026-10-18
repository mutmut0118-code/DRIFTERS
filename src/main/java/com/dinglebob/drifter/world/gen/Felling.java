package com.dinglebob.drifter.world.gen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.dinglebob.drifter.building.plan.HousePalette;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Trees round a house a Drifter built (in world generation or caught up) cut down, about as much wood as the house took
 * (user, 2026-10-17: "some trees around it are immediately deleted, giving the illusion that they use the resources
 * around the area to build the house"): the nearest trees first, trunk and leaves, sometimes leaving a stump, as a
 * player clears the trees round their build.
 */
public final class Felling {
	private Felling() {
	}

	/** How far round the house it looks for trees. */
	private static final int RADIUS = 14;
	/** Most trees it takes. */
	private static final int MOST_TREES = 8;
	/** Leaves reach this far from a log. */
	private static final int LEAF_REACH = 5;

	/** About how many logs went into these blocks (planks a quarter each, stairs three eighths, and so on). */
	public static int logsIn(Iterable<BlockState> blocks) {
		double logs = 0.0;
		for (BlockState state : blocks) {
			String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
			if (HousePalette.woodOf(path) == null) {
				continue;
			}

			logs += path.endsWith("_log") || path.endsWith("_wood") ? 1.0 : path.endsWith("_planks") ? 0.25 : path.endsWith("_stairs") ? 0.375
				: path.endsWith("_slab") ? 0.125 : 0.3;
		}

		return (int)Math.ceil(logs);
	}

	/** The blocks in a box (a hut or house just built, to count its wood). */
	public static List<BlockState> blocksIn(LevelAccessor level, BoundingBox box) {
		List<BlockState> states = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			states.add(level.getBlockState(pos));
		}

		return states;
	}

	/** Fells trees round {@code house} until about {@code logs} logs are gone (or none are left near). Logs taken. */
	public static int fell(LevelAccessor level, BoundingBox house, int logs, RandomSource random) {
		if (logs <= 0) {
			return 0;
		}

		BlockPos middle = house.getCenter();
		List<BlockPos> bases = new ArrayList<>();
		for (int x = house.minX() - RADIUS; x <= house.maxX() + RADIUS; x++) {
			for (int z = house.minZ() - RADIUS; z <= house.maxZ() + RADIUS; z++) {
				if (house.isInside(new BlockPos(x, house.minY(), z)) || !writable(level, new BlockPos(x, 0, z))) {
					continue;
				}

				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
				if (!level.getBlockState(top).is(BlockTags.LOGS)) {
					continue;
				}

				BlockPos base = top;
				while (level.getBlockState(base.below()).is(BlockTags.LOGS) && top.getY() - base.getY() < 32) {
					base = base.below();
				}

				if (level.getBlockState(base.below()).is(BlockTags.DIRT)) {
					bases.add(base.immutable());
				}
			}
		}

		bases.sort(Comparator.comparingDouble(base -> base.distSqr(middle)));
		Set<BlockPos> felled = new HashSet<>();
		int taken = 0;
		int trees = 0;
		for (BlockPos base : bases) {
			if (taken >= logs || trees >= MOST_TREES) {
				break;
			}

			if (felled.contains(base)) {
				continue;
			}

			Set<BlockPos> trunk = connected(level, base, state -> state.is(BlockTags.LOGS), 96, 1);
			// (A log far off the ground isn't a tree's: someone's build. Leave those.)
			if (trunk.isEmpty() || trunk.stream().anyMatch(pos -> !writable(level, pos))) {
				continue;
			}

			// Now and then a stump.
			int stumpY = random.nextDouble() < 0.4 ? base.getY() : Integer.MIN_VALUE;
			Set<BlockPos> leaves = leavesOf(level, trunk);
			for (BlockPos pos : trunk) {
				felled.add(pos);
				if (pos.getY() != stumpY) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					taken++;
				}
			}

			for (BlockPos pos : leaves) {
				if (writable(level, pos)) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				}
			}

			// (Snow that lay on the crown would be left in the air.)
			for (Set<BlockPos> gone : List.of(trunk, leaves)) {
				for (BlockPos pos : gone) {
					if (writable(level, pos.above()) && level.getBlockState(pos.above()).is(Blocks.SNOW) && level.getBlockState(pos).isAir()) {
						level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}

			trees++;
		}

		return taken;
	}

	/** Blocks matching from a start, through neighbors (diagonals too when {@code reach} is 1), up to a count. */
	private static Set<BlockPos> connected(LevelAccessor level, BlockPos start, java.util.function.Predicate<BlockState> match, int most, int reach) {
		Set<BlockPos> found = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		found.add(start);
		queue.add(start);
		while (!queue.isEmpty() && found.size() < most) {
			BlockPos pos = queue.poll();
			for (BlockPos next : BlockPos.betweenClosed(pos.offset(-reach, -reach, -reach), pos.offset(reach, reach, reach))) {
				if (!found.contains(next) && match.test(level.getBlockState(next))) {
					BlockPos fixed = next.immutable();
					found.add(fixed);
					queue.add(fixed);
				}
			}
		}

		return found;
	}

	/** The tree's own leaves: grown ones (not placed) within reach of its logs. */
	private static Set<BlockPos> leavesOf(LevelAccessor level, Set<BlockPos> trunk) {
		Set<BlockPos> leaves = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		java.util.Map<BlockPos, Integer> steps = new java.util.HashMap<>();
		for (BlockPos log : trunk) {
			steps.put(log, 0);
			queue.add(log);
		}

		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			int step = steps.get(pos);
			if (step >= LEAF_REACH) {
				continue;
			}

			for (Direction side : Direction.values()) {
				BlockPos next = pos.relative(side);
				if (steps.containsKey(next)) {
					continue;
				}

				BlockState state = level.getBlockState(next);
				// (Not leaves another tree near by holds: its crown stays whole.)
				if (state.is(BlockTags.LEAVES) && !(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT))
					&& !otherLogNear(level, next, trunk)) {
					steps.put(next, step + 1);
					leaves.add(next);
					queue.add(next);
				}
			}
		}

		return leaves;
	}

	/** A log of another tree within leaf reach of this leaf. */
	private static boolean otherLogNear(LevelAccessor level, BlockPos leaf, Set<BlockPos> trunk) {
		for (BlockPos pos : BlockPos.betweenClosed(leaf.offset(-3, -3, -3), leaf.offset(3, 3, 3))) {
			if (!trunk.contains(pos) && level.getBlockState(pos).is(BlockTags.LOGS)) {
				return true;
			}
		}

		return false;
	}

	/** World generation may write only round the chunk it's working on. */
	private static boolean writable(LevelAccessor level, BlockPos pos) {
		if (level instanceof WorldGenRegion region) {
			var center = region.getCenter();
			return Math.abs((pos.getX() >> 4) - center.x) <= 1 && Math.abs((pos.getZ() >> 4) - center.z) <= 1;
		}

		return level.hasChunkAt(pos);
	}
}
