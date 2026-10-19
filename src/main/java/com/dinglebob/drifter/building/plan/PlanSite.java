package com.dinglebob.drifter.building.plan;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Where a plan's house goes (house plans, 2026-10-16): a spot near home it knows, out of the way of its hut, farm and pen,
 * as flat and dry as it can find, with as few trees in the way as can be; turned whichever of four ways suits, the door
 * toward home if it can. The ground there gets levelled before it builds (user's rule: never floating), so a little
 * unevenness is fine; a lot (more than 4 blocks up or down) isn't.
 */
public final class PlanSite {
	public record Site(BlockPos origin, Rotation rotation, double cost) {
	}

	/** A plan it could build, with its best spot and the woods it would use. */
	public record Choice(HousePlan plan, Site site, HousePalette palette) {
		/** How well the spot suits the plan: its cost per column of the footprint (a big house isn't marked down for its size). */
		double fit() {
			return this.site.cost() / Math.max(1, this.plan.size.getX() * this.plan.size.getZ());
		}
	}

	/** A plan whose spot suits it worse than the best one's by more than this (per column) is out of the running. */
	private static final double FIT_SLACK = 0.5;

	/**
	 * Which house it builds: as a player picks a house they like and then a spot for it, any plan whose spot is about as
	 * good as the best one's, chosen at random (found in testing: picking the cheapest spot outright, the smallest
	 * footprints always won, so with hundreds of plans it built the same two towers). Null if there's none.
	 */
	@Nullable
	public static Choice pick(List<Choice> choices, net.minecraft.util.RandomSource random) {
		double best = choices.stream().mapToDouble(Choice::fit).min().orElse(Double.MAX_VALUE);
		List<Choice> good = choices.stream().filter(choice -> choice.fit() <= best + FIT_SLACK).toList();
		return good.isEmpty() ? null : good.get(random.nextInt(good.size()));
	}

	private static final int MAX_STEP = 4;
	/** Most of a footprint it may not have seen the ground of. */
	private static final double MAX_UNSEEN = 0.35;
	private static final int[] RINGS = {6, 9, 12, 15, 18, 22, 26, 30, 36, 42};
	private static final int ANGLES = 16;

	private PlanSite() {
	}

	/** Why spots were turned down on the last search (for its thoughts: "nowhere ... (too steep 40, ...)"). */
	private static final java.util.Map<String, Integer> REJECTED = new java.util.TreeMap<>();

	/** Why the last search turned spots down, most common first. */
	public static String lastRejections() {
		return REJECTED.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(4)
			.map(e -> e.getKey() + " " + e.getValue()).collect(java.util.stream.Collectors.joining(", "));
	}

	private static Site reject(String why) {
		REJECTED.merge(why, 1, Integer::sum);
		return null;
	}

	/** The best spot for this plan round its home, or null if there's none it knows. */
	@Nullable
	public static Site choose(DrifterEntity drifter, HousePlan plan, HousePalette palette) {
		return choose(drifter, plan, palette, false);
	}

	/**
	 * {@code lived}: catch-up, where it lived round its home for days (the ground there counts as seen, though its eyes'
	 * memory of this visit is fresh).
	 */
	@Nullable
	public static Site choose(DrifterEntity drifter, HousePlan plan, HousePalette palette, boolean lived) {
		Home home = drifter.getDrifterBrain().home();
		if (home == null) {
			return null;
		}

		Level level = drifter.level();
		REJECTED.clear();
		Columns columns = Columns.of(drifter, home, lived);
		boolean groundAtBottom = plan.cells.stream().anyMatch(c -> c.pos().getY() == 0 && PlacedPlan.isGround(c.state()));
		Site best = null;
		for (Rotation rotation : Rotation.values()) {
			PlacedPlan shape = new PlacedPlan(plan, BlockPos.ZERO, rotation, palette);
			BlockPos door = shape.build.stream().filter(c -> c.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
				.map(PlacedPlan.Cell::pos).findFirst().orElse(new BlockPos(shape.sizeX / 2, 0, shape.sizeZ / 2));
			for (int ring : RINGS) {
				for (int a = 0; a < ANGLES; a++) {
					double angle = Math.PI * 2.0 * a / ANGLES;
					int cx = home.center.getX() + (int)Math.round(Math.cos(angle) * (ring + Math.max(shape.sizeX, shape.sizeZ) / 2.0));
					int cz = home.center.getZ() + (int)Math.round(Math.sin(angle) * (ring + Math.max(shape.sizeX, shape.sizeZ) / 2.0));
					BlockPos corner = new BlockPos(cx - shape.sizeX / 2, 0, cz - shape.sizeZ / 2);
					Site site = score(columns, home, shape, corner, rotation, groundAtBottom, door);
					if (site != null && (best == null || site.cost() < best.cost())) {
						best = site;
					}
				}
			}
		}

		return best;
	}

	/**
	 * It has seen this column's ground, or what lies on it (snow, grass, flowers, the leaves of a tree over it: found in
	 * testing, a snowy taiga's ground was all "unseen" because it had only ever seen the snow on it).
	 */
	private static boolean seenDownTo(DrifterEntity drifter, BlockPos from, BlockPos ground) {
		for (BlockPos pos = from; pos.getY() >= ground.getY(); pos = pos.below()) {
			if (drifter.getSenses().terrain().isKnown(pos)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * What each ground column round its home is like (its ground's height, a tree on it, water, ground it hasn't seen, in
	 * the way of its own things), worked out once per column for all the spots and turns tried, and kept for the tick (a
	 * catch-up tries several house designs in a row). Neighbouring spots share nearly all their columns: working each out
	 * again for every spot was a quarter of a second for one catch-up (milestone 16).
	 */
	private static final class Columns {
		static final byte BLOCKED = 1;
		static final byte UNLOADED = 2;
		static final byte UNSEEN = 3;
		static final byte WATER = 4;
		static final byte GROUND = 5;
		static final byte TREE = 6;

		private final DrifterEntity drifter;
		private final Home home;
		private final Level level;
		private final boolean lived;
		private final NavWorldReader reader;
		/** By column round its home (a square of side 2 * REACH + 1; 0: not worked out yet), and beyond that a map. */
		private static final int REACH = 96;
		private final long[] grid = new long[(2 * REACH + 1) * (2 * REACH + 1)];
		private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap info = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
		private final long tick;
		private final BlockPos center;

		@Nullable
		private static Columns last;

		private Columns(DrifterEntity drifter, Home home, boolean lived) {
			this.drifter = drifter;
			this.home = home;
			this.level = drifter.level();
			this.lived = lived;
			this.reader = new NavWorldReader(this.level);
			this.tick = this.level.getGameTime();
			this.center = home.center.immutable();
			this.built = home.hutBuilt + ":" + home.planDone + ":" + home.planOrigin;
		}

		/** What it had built when the table was made (a house put up since changes which columns are free). */
		private final String built;

		static Columns of(DrifterEntity drifter, Home home, boolean lived) {
			Columns known = last;
			if (known != null && known.drifter == drifter && known.home == home && known.lived == lived && known.tick == drifter.level().getGameTime()
				&& known.center.equals(home.center) && known.built.equals(home.hutBuilt + ":" + home.planDone + ":" + home.planOrigin)) {
				return known;
			}

			last = new Columns(drifter, home, lived);
			return last;
		}

		/** The column's kind (low byte) and, for ground, its height (above). */
		long get(int x, int z) {
			int gx = x - this.center.getX() + REACH;
			int gz = z - this.center.getZ() + REACH;
			if (gx >= 0 && gz >= 0 && gx <= 2 * REACH && gz <= 2 * REACH) {
				int index = gx * (2 * REACH + 1) + gz;
				long value = this.grid[index];
				if (value == 0L) {
					value = this.compute(x, z);
					this.grid[index] = value;
				}

				return value;
			}

			long key = net.minecraft.world.level.ChunkPos.asLong(x, z);
			if (this.info.containsKey(key)) {
				return this.info.get(key);
			}

			long value = this.compute(x, z);
			this.info.put(key, value);
			return value;
		}

		private long compute(int x, int z) {
			BlockPos column = new BlockPos(x, 0, z);
			// Clear of its own things, with a block's margin.
			if (this.home.inOwnBuilding(column.atY(this.home.center.getY()), 2) || this.home.farm != null && this.home.farm.closerThan(column.atY(this.home.farm.getY()), 6)
				|| this.home.pen != null && this.home.pen.closerThan(column.atY(this.home.pen.getY()), 7)
				|| this.home.enchantingTable != null && this.home.enchantingTable.closerThan(column.atY(this.home.enchantingTable.getY()), 4)) {
				return BLOCKED;
			}

			var chunk = this.reader.chunk(x >> 4, z >> 4);
			if (chunk == null) {
				return UNLOADED;
			}

			// The ground under any tree: down past logs, leaves and plants.
			int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) - 1;
			BlockPos.MutableBlockPos top = new BlockPos.MutableBlockPos(x, y, z);
			boolean tree = false;
			while (top.getY() > this.level.getMinBuildHeight()) {
				BlockState state = chunk.getBlockState(top);
				if (!(state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.canBeReplaced() && state.getFluidState().isEmpty())) {
					break;
				}

				tree |= state.is(BlockTags.LOGS);
				top.move(0, -1, 0);
			}

			// Ground it hasn't seen (under trees, behind a rise) it guesses is like the rest, as a player sizing up a spot
			// does; only if it has seen too little of it does it look elsewhere.
			BlockPos ground = top.immutable();
			if (!this.lived && !seenDownTo(this.drifter, column.atY(chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) - 1), ground)) {
				return UNSEEN;
			}

			BlockState state = chunk.getBlockState(ground);
			if (!state.getFluidState().isEmpty() || !chunk.getBlockState(ground.above()).getFluidState().isEmpty() || state.is(net.minecraft.world.level.block.Blocks.ICE)) {
				return WATER;
			}

			return (long)ground.getY() << 8 | (tree ? TREE : GROUND);
		}
	}

	/** Chunks read straight, the last one kept (columns are read in runs). */
	private static final class NavWorldReader {
		private final Level level;
		private long lastKey = Long.MIN_VALUE;
		@Nullable
		private net.minecraft.world.level.chunk.LevelChunk lastChunk;

		NavWorldReader(Level level) {
			this.level = level;
		}

		@Nullable
		net.minecraft.world.level.chunk.LevelChunk chunk(int cx, int cz) {
			long key = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
			if (key != this.lastKey) {
				this.lastKey = key;
				this.lastChunk = com.dinglebob.drifter.world.TickChunks.get(this.level, cx, cz);
			}

			return this.lastChunk;
		}
	}

	@Nullable
	private static Site score(Columns columns, Home home, PlacedPlan shape, BlockPos corner, Rotation rotation, boolean groundAtBottom, BlockPos door) {
		int[] heights = new int[shape.sizeX * shape.sizeZ];
		int count = 0;
		int trees = 0;
		int unknown = 0;
		for (int dx = -1; dx <= shape.sizeX; dx++) {
			for (int dz = -1; dz <= shape.sizeZ; dz++) {
				long info = columns.get(corner.getX() + dx, corner.getZ() + dz);
				byte kind = (byte)(info & 0xFF);
				if (kind == Columns.BLOCKED) {
					return reject("in the way of its things");
				}

				if (dx < 0 || dz < 0 || dx >= shape.sizeX || dz >= shape.sizeZ) {
					continue;
				}

				switch (kind) {
					case Columns.UNLOADED -> {
						return reject("not loaded");
					}
					case Columns.UNSEEN -> unknown++;
					case Columns.WATER -> {
						return reject("water");
					}
					default -> {
						trees += kind == Columns.TREE ? 1 : 0;
						heights[count++] = (int)(info >> 8);
					}
				}
			}
		}

		if (unknown > shape.sizeX * shape.sizeZ * MAX_UNSEEN) {
			return reject("ground it hasn't seen");
		}

		if (count == 0) {
			return reject("ground it hasn't seen");
		}

		java.util.Arrays.sort(heights, 0, count);
		int base = heights[count / 2];
		double cost = 0.0;
		for (int i = 0; i < count; i++) {
			int h = heights[i];
			if (Math.abs(h - base) > MAX_STEP) {
				return reject("too steep");
			}

			cost += Math.abs(h - base);
		}

		BlockPos origin = corner.atY(groundAtBottom ? base : base + 1);
		double distance = Math.sqrt(origin.offset(shape.sizeX / 2, 0, shape.sizeZ / 2).distSqr(home.center.atY(origin.getY())));
		BlockPos doorAt = origin.offset(door.getX(), 0, door.getZ());
		double doorToHome = Math.sqrt(doorAt.distSqr(home.center.atY(doorAt.getY())));
		return new Site(origin, rotation, cost + trees * 6.0 + distance * 0.3 + doorToHome * 0.4);
	}
}
