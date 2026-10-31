package com.dinglebob.drifter.world.gen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

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

	/**
	 * The tree this log or grown leaf is part of, felled whole: its trunk and its crown (the leaves another tree holds
	 * stay), as a player clears a tree that's in the way of a build instead of cutting it off at the edge (user,
	 * 2026-10-23: "trees are always half cutoff"). A leaf held by more than one tree fells each in turn. Logs of a build
	 * (someone's, or not standing on the ground with leaves on them) are left alone. True if anything was felled.
	 */
	public static boolean fellTree(LevelAccessor level, BlockPos pos) {
		boolean any = false;
		for (int tries = 0; tries < 3; tries++) {
			BlockState state = level.getBlockState(pos);
			BlockPos log = state.is(BlockTags.LOGS) ? pos : grown(state) ? holdingLog(level, pos) : null;
			if (log == null) {
				return any;
			}

			Set<BlockPos> trunk = connected(level, log.immutable(), s -> s.is(BlockTags.LOGS), 160, 1);
			if (!aTree(level, trunk)) {
				return any;
			}

			Set<BlockPos> leaves = leavesOf(level, trunk);
			for (BlockPos gone : trunk) {
				level.setBlock(gone, Blocks.AIR.defaultBlockState(), 2);
				// (The dirt a tree keeps under its trunk grassed over, as the ground round it: found by the user, a line of bare
				// squares across a town's grass where a row of birches stood.)
				BlockPos under = gone.below();
				if (!trunk.contains(under) && level.getBlockState(under).is(Blocks.DIRT) && writable(level, under)) {
					level.setBlock(under, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
				}
			}

			for (BlockPos gone : leaves) {
				if (writable(level, gone)) {
					level.setBlock(gone, Blocks.AIR.defaultBlockState(), 2);
				}
			}

			for (Set<BlockPos> felled : List.of(trunk, leaves)) {
				for (BlockPos gone : felled) {
					if (writable(level, gone.above()) && level.getBlockState(gone.above()).is(Blocks.SNOW) && level.getBlockState(gone).isAir()) {
						level.setBlock(gone.above(), Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}

			any = true;
			// (A leaf that was the tree's own is gone now; one another tree held too is still there: that tree next.)
			if (state.is(BlockTags.LOGS)) {
				return true;
			}
		}

		return any;
	}

	/** The nearest log holding this leaf (within the game's leaf distance, through leaves), or null. */
	@Nullable
	private static BlockPos holdingLog(LevelAccessor level, BlockPos leaf) {
		Map<BlockPos, Integer> steps = new HashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		steps.put(leaf.immutable(), 0);
		queue.add(leaf.immutable());
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			int step = steps.get(pos);
			for (Direction side : Direction.values()) {
				BlockPos next = pos.relative(side);
				if (steps.containsKey(next) || !readable(level, next)) {
					continue;
				}

				BlockState state = level.getBlockState(next);
				if (state.is(BlockTags.LOGS)) {
					return next;
				}

				if (step + 1 < LEAF_HOLD && grown(state)) {
					steps.put(next, step + 1);
					queue.add(next);
				}
			}
		}

		return null;
	}

	/** A tree's trunk: no one's build, standing on the ground, with grown leaves on it. */
	private static boolean aTree(LevelAccessor level, Set<BlockPos> trunk) {
		boolean grounded = false;
		boolean leafy = false;
		for (BlockPos log : trunk) {
			if (!writable(level, log) || level instanceof net.minecraft.world.level.Level real && com.dinglebob.drifter.world.PlayerPlacedBlocks.isBuilt(real, log)) {
				return false;
			}

			BlockState under = level.getBlockState(log.below());
			grounded |= !trunk.contains(log.below()) && (under.is(BlockTags.DIRT) || under.is(Blocks.SAND) || under.is(Blocks.GRAVEL)
				|| under.is(Blocks.MANGROVE_ROOTS) || under.is(Blocks.MUDDY_MANGROVE_ROOTS) || under.is(Blocks.SNOW_BLOCK));
			for (Direction side : Direction.values()) {
				leafy |= grown(level.getBlockState(log.relative(side)));
			}
		}

		return grounded && leafy;
	}

	/** How far round a build loose bits of trees are looked for. */
	private static final int TIDY_REACH = 8;
	/** Leaves stay while a tree's log is this many steps away through leaves (the game's own leaf distance: 7 rots). */
	private static final int LEAF_HOLD = 6;
	/** A piece of trunk bigger than this is left alone (not a tree's). */
	private static final int MOST_PIECE = 512;

	/**
	 * Loose bits of trees round something put up all at once (a hut or house caught up, a camp, a town made ready): pieces
	 * of trunk left in the air where the build cut through a tree, and leaves with no tree's log near enough to hold them.
	 * A player clearing for a build takes the tree whole; and the game's own leaf rot never comes for these (blocks set
	 * at once don't tell their neighbours), so they'd hang there for good (user, 2026-10-22: "there are floating leaves
	 * everywhere"). {@code built}: the build itself (its logs hold no leaves, and a trunk resting on it is loose), or null
	 * for an area with no one build in it (a town's ground, roads and fields).
	 */
	public static void tidy(LevelAccessor level, BoundingBox area, @Nullable BoundingBox built) {
		BoundingBox near = new BoundingBox(area.minX() - TIDY_REACH, area.minY() - 2, area.minZ() - TIDY_REACH, area.maxX() + TIDY_REACH,
			area.maxY() + TIDY_REACH + 4, area.maxZ() + TIDY_REACH);
		int hold = LEAF_HOLD + 1;
		BoundingBox scan = new BoundingBox(near.minX() - hold, near.minY() - hold, near.minZ() - hold, near.maxX() + hold, near.maxY() + hold,
			near.maxZ() + hold);

		// 1. Pieces of trunk in the air: not standing on the ground, with a tree's leaves on them (or cut off by the build).
		Set<BlockPos> felled = new HashSet<>();
		Set<BlockPos> looked = new HashSet<>();
		for (BlockPos pos : BlockPos.betweenClosed(near.minX(), near.minY(), near.minZ(), near.maxX(), near.maxY(), near.maxZ())) {
			if (built != null && built.isInside(pos) || looked.contains(pos) || !readable(level, pos) || !level.getBlockState(pos).is(BlockTags.LOGS)) {
				continue;
			}

			Set<BlockPos> piece = piece(level, pos.immutable(), built, looked);
			if (piece != null && loose(level, piece, built)) {
				for (BlockPos log : piece) {
					level.setBlock(log, Blocks.AIR.defaultBlockState(), 2);
					felled.add(log);
				}
			}
		}

		// 2. Leaves: those a tree's log holds (within LEAF_HOLD steps through leaves) stay, the rest go.
		Map<BlockPos, Integer> held = new HashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos pos : BlockPos.betweenClosed(scan.minX(), scan.minY(), scan.minZ(), scan.maxX(), scan.maxY(), scan.maxZ())) {
			if ((built == null || !built.isInside(pos)) && readable(level, pos) && level.getBlockState(pos).is(BlockTags.LOGS)) {
				BlockPos log = pos.immutable();
				held.put(log, 0);
				queue.add(log);
			}
		}

		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			int step = held.get(pos);
			if (step >= LEAF_HOLD) {
				continue;
			}

			for (Direction side : Direction.values()) {
				BlockPos next = pos.relative(side);
				if (!held.containsKey(next) && scan.isInside(next) && readable(level, next) && grown(level.getBlockState(next))) {
					held.put(next, step + 1);
					queue.add(next);
				}
			}
		}

		for (BlockPos pos : BlockPos.betweenClosed(near.minX(), near.minY(), near.minZ(), near.maxX(), near.maxY(), near.maxZ())) {
			// (Only where it could see every log that might hold it.)
			if (!held.containsKey(pos) && readable(level, pos) && grown(level.getBlockState(pos)) && readable(level, pos.offset(-hold, 0, -hold))
				&& readable(level, pos.offset(hold, 0, -hold)) && readable(level, pos.offset(-hold, 0, hold)) && readable(level, pos.offset(hold, 0, hold))) {
				BlockPos leaf = pos.immutable();
				level.setBlock(leaf, Blocks.AIR.defaultBlockState(), 2);
				felled.add(leaf);
			}
		}

		// (Snow that lay on them would be left in the air.)
		for (BlockPos pos : felled) {
			if (readable(level, pos.above()) && level.getBlockState(pos.above()).is(Blocks.SNOW) && level.getBlockState(pos).isAir()) {
				level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	/** Leaves a tree grew (not placed ones). */
	private static boolean grown(BlockState state) {
		return state.is(BlockTags.LEAVES) && !(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT));
	}

	/** The logs joined to this one (diagonals too), outside the build; null if it can't tell (too big, or runs off what's loaded). */
	@Nullable
	private static Set<BlockPos> piece(LevelAccessor level, BlockPos start, @Nullable BoundingBox built, Set<BlockPos> looked) {
		Set<BlockPos> found = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		found.add(start);
		queue.add(start);
		boolean sure = true;
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			for (BlockPos next : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
				if (found.contains(next) || built != null && built.isInside(next)) {
					continue;
				}

				if (!readable(level, next)) {
					sure = false;
					continue;
				}

				if (level.getBlockState(next).is(BlockTags.LOGS)) {
					BlockPos fixed = next.immutable();
					found.add(fixed);
					if (found.size() > MOST_PIECE) {
						looked.addAll(found);
						return null;
					}

					queue.add(fixed);
				}
			}
		}

		looked.addAll(found);
		return sure ? found : null;
	}

	/** A piece of trunk left in the air by a build: nothing under it but air, leaves or the build, and part of a tree. */
	private static boolean loose(LevelAccessor level, Set<BlockPos> piece, @Nullable BoundingBox built) {
		boolean tree = false;
		for (BlockPos log : piece) {
			BlockPos below = log.below();
			// (Next to what it can't see: it can't tell.)
			for (Direction side : Direction.values()) {
				if (!readable(level, log.relative(side))) {
					return false;
				}
			}

			if (!piece.contains(below) && (built == null || !built.isInside(below))) {
				BlockState under = level.getBlockState(below);
				if (!under.isAir() && !under.is(BlockTags.LEAVES) && !under.canBeReplaced()) {
					return false;
				}
			}

			// (Someone's build: never.)
			if (level instanceof net.minecraft.world.level.Level real && com.dinglebob.drifter.world.PlayerPlacedBlocks.isBuilt(real, log)) {
				return false;
			}

			for (Direction side : Direction.values()) {
				BlockPos next = log.relative(side);
				tree |= built != null && built.isInside(next) || grown(level.getBlockState(next));
			}
		}

		return tree;
	}

	/** World generation may read and write only round the chunk it's working on; a level, only what's loaded. */
	private static boolean readable(LevelAccessor level, BlockPos pos) {
		return pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight() && writable(level, pos);
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

		// (Really there: hasChunkAt says yes for land still being made, and reading it waited for it there and then; a road
		// between towns tidying its trees stopped the server six seconds.)
		if (level instanceof net.minecraft.server.level.ServerLevel server) {
			return server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
		}

		return level.hasChunkAt(pos);
	}
}
