package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A town's ground, shaped once for all of it (milestone 23, docs/LIVED_WORLD.md W2; the user, 2026-10-30: "terraform the
 * terrain in a most possible natural way that way it does fit, every single time ... instead of an air box"). Before, each
 * part of a town flattened its own patch and eased ten blocks round it at most: cut into a hill, it left a box with walls
 * of dirt.
 *
 * <p>Now every column of the town's ground gets one height: its plaza, store, fields and houses ({@link Pad}s) at their
 * own heights, a ring a block wide round each at that height (a doorstep), and the land round them falling or rising a
 * block every two from there until it meets the land as it was (a gentle bank, however far that takes, within a pad's
 * reach); its roads at the grades they're laid at, with shoulders. Where two of them ask for more than banks can
 * give (a house above the road beside it), the nearer wins and between them stands a retaining wall: a wall of stone the
 * height of the step, grass on top. Gentle land is left as it is. Filled ground gets the land's own layers (its top block,
 * dirt or sandstone under it), cut ground its top block again where earth shows; trees on moved ground come down whole,
 * water is never filled. Shaped a piece at a time ({@link #apply}), the same heights whichever piece goes first.
 */
final class GroundPlan {
	/** Ground held at a height (the top block): its rectangle, how far round it the land's eased to it, room cleared over it. */
	record Pad(TownBuilder.Rect rect, int height, int reach, int clear) {
	}

	/** Pads are looked up by squares this wide. */
	private static final int CELL = 16;
	/** How far round a road's columns the land's eased to it (its shoulders a block wide, then gentle banks). */
	private static final int ROAD_REACH = 6;

	private final ServerLevel level;
	private final RandomSource random;
	/** A desert town: sand and sandstone for its fills and walls. */
	private final boolean sandy;
	private final Map<Long, List<Pad>> index = new HashMap<>();
	private final Map<Long, Integer> roads = new HashMap<>();
	private final Map<Long, Integer> natural = new HashMap<>();
	private final Map<Long, Boolean> water = new HashMap<>();
	private final Map<Long, Integer> targets = new HashMap<>();
	private int minX = Integer.MAX_VALUE;
	private int minZ = Integer.MAX_VALUE;
	private int maxX = Integer.MIN_VALUE;
	private int maxZ = Integer.MIN_VALUE;

	GroundPlan(ServerLevel level, List<Pad> pads, RandomSource random, boolean sandy) {
		this.level = level;
		this.random = random;
		this.sandy = sandy;
		for (Pad pad : pads) {
			TownBuilder.Rect rect = pad.rect();
			int r = pad.reach();
			this.grow(rect.x0() - r, rect.z0() - r, rect.x1() + r, rect.z1() + r);
			for (int cx = Math.floorDiv(rect.x0() - r, CELL); cx <= Math.floorDiv(rect.x1() + r, CELL); cx++) {
				for (int cz = Math.floorDiv(rect.z0() - r, CELL); cz <= Math.floorDiv(rect.z1() + r, CELL); cz++) {
					this.index.computeIfAbsent(BlockPos.asLong(cx, 0, cz), key -> new ArrayList<>()).add(pad);
				}
			}
		}
	}

	private void grow(int x0, int z0, int x1, int z1) {
		this.minX = Math.min(this.minX, x0);
		this.minZ = Math.min(this.minZ, z0);
		this.maxX = Math.max(this.maxX, x1);
		this.maxZ = Math.max(this.maxZ, z1);
	}

	/** A road's column held at this height (where two roads meet, the higher). */
	void road(long column, int height) {
		this.roads.merge(column, height, Math::max);
		int x = BlockPos.getX(column);
		int z = BlockPos.getZ(column);
		this.grow(x - ROAD_REACH, z - ROAD_REACH, x + ROAD_REACH, z + ROAD_REACH);
	}

	/** The top of the ground here as it was before the town (asked before anything here is moved, then kept). */
	int natural(int x, int z) {
		long key = BlockPos.asLong(x, 0, z);
		Integer known = this.natural.get(key);
		if (known == null) {
			known = TownBuilder.ground(this.level, x, z);
			this.natural.put(key, known);
			this.water.put(key, TownBuilder.wet(this.level, x, z));
		}

		return known;
	}

	boolean wet(int x, int z) {
		this.natural(x, z);
		return this.water.get(BlockPos.asLong(x, 0, z));
	}

	/** The height here by the pads alone (before its roads are laid out on it). */
	int base(int x, int z) {
		Integer height = this.height(x, z, false);
		return height != null ? height : this.natural(x, z);
	}

	/** The height the ground goes to here. */
	int target(int x, int z) {
		long key = BlockPos.asLong(x, 0, z);
		Integer known = this.targets.get(key);
		if (known == null) {
			Integer height = this.height(x, z, true);
			known = height != null ? height : this.natural(x, z);
			this.targets.put(key, known);
		}

		return known;
	}

	/** Moved here at all (a pad or a road within reach); null if the land's left as it is. */
	@Nullable
	private Integer height(int x, int z, boolean withRoads) {
		if (withRoads) {
			Integer road = this.roads.get(BlockPos.asLong(x, 0, z));
			if (road != null) {
				return road;
			}
		}

		int lo = Integer.MIN_VALUE;
		int hi = Integer.MAX_VALUE;
		int nearLo = Integer.MAX_VALUE;
		int nearHi = Integer.MAX_VALUE;
		boolean any = false;
		List<Pad> near = this.index.get(BlockPos.asLong(Math.floorDiv(x, CELL), 0, Math.floorDiv(z, CELL)));
		if (near != null) {
			for (Pad pad : near) {
				int d = pad.rect().away(x, z);
				if (d > pad.reach()) {
					continue;
				}

				any = true;
				int e = bank(d);
				if (pad.height() - e > lo || pad.height() - e == lo && d < nearLo) {
					lo = pad.height() - e;
					nearLo = d;
				}

				if (pad.height() + e < hi || pad.height() + e == hi && d < nearHi) {
					hi = pad.height() + e;
					nearHi = d;
				}
			}
		}

		if (withRoads && !this.roads.isEmpty()) {
			for (int dx = -ROAD_REACH; dx <= ROAD_REACH; dx++) {
				for (int dz = -ROAD_REACH; dz <= ROAD_REACH; dz++) {
					Integer road = this.roads.get(BlockPos.asLong(x + dx, 0, z + dz));
					if (road == null) {
						continue;
					}

					any = true;
					int e = bank(Math.max(Math.abs(dx), Math.abs(dz)));
					int d = Math.max(Math.abs(dx), Math.abs(dz));
					if (road - e > lo || road - e == lo && d < nearLo) {
						lo = road - e;
						nearLo = d;
					}

					if (road + e < hi || road + e == hi && d < nearHi) {
						hi = road + e;
						nearHi = d;
					}
				}
			}
		}

		if (!any || this.wet(x, z)) {
			return null;
		}

		// Asked for more than banks can give: the nearer has its way, and a wall stands between (see apply).
		if (lo > hi) {
			return nearLo <= nearHi ? lo : hi;
		}

		return Mth.clamp(this.natural(x, z), lo, hi);
	}

	/**
	 * How far the land may rise or fall {@code d} blocks out from what's held: a ring a block wide at its height (a doorstep),
	 * then a block every two (a gentle bank: a block a step looked like a pit dug round a field, found in the first pictures).
	 */
	private static int bank(int d) {
		return d / 2;
	}

	/** Anything here or beside it to shape (a quick look, before any ground is asked). */
	private boolean touched(int x, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (Math.abs(dx) + Math.abs(dz) <= 1 && this.near(x + dx, z + dz)) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean near(int x, int z) {
		List<Pad> pads = this.index.get(BlockPos.asLong(Math.floorDiv(x, CELL), 0, Math.floorDiv(z, CELL)));
		if (pads != null) {
			for (Pad pad : pads) {
				if (pad.rect().away(x, z) <= pad.reach()) {
					return true;
				}
			}
		}

		for (int dx = -ROAD_REACH; dx <= ROAD_REACH; dx++) {
			for (int dz = -ROAD_REACH; dz <= ROAD_REACH; dz++) {
				if (this.roads.containsKey(BlockPos.asLong(x + dx, 0, z + dz))) {
					return true;
				}
			}
		}

		return false;
	}

	/** The pad this column is in, if any. */
	@Nullable
	private Pad padAt(int x, int z) {
		List<Pad> pads = this.index.get(BlockPos.asLong(Math.floorDiv(x, CELL), 0, Math.floorDiv(z, CELL)));
		if (pads != null) {
			for (Pad pad : pads) {
				if (pad.rect().contains(x, z)) {
					return pad;
				}
			}
		}

		return null;
	}

	/** Its ground in pieces this wide each way, for a step each. */
	List<BoundingBox> pieces(int size) {
		List<BoundingBox> list = new ArrayList<>();
		if (this.minX > this.maxX) {
			return list;
		}

		for (int x = this.minX; x <= this.maxX; x += size) {
			for (int z = this.minZ; z <= this.maxZ; z += size) {
				list.add(new BoundingBox(x, 0, z, Math.min(x + size - 1, this.maxX), 0, Math.min(z + size - 1, this.maxZ)));
			}
		}

		return list;
	}

	/** This piece of its ground shaped (only the y-less x and z of the box count). */
	void apply(BoundingBox piece) {
		for (int x = piece.minX(); x <= piece.maxX(); x++) {
			for (int z = piece.minZ(); z <= piece.maxZ(); z++) {
				if (this.touched(x, z)) {
					this.column(x, z);
				}
			}
		}
	}

	private void column(int x, int z) {
		if (this.wet(x, z)) {
			return;
		}

		int g = this.natural(x, z);
		int t = this.target(x, z);
		boolean moved = t != g;
		// A face: land two or more lower beside it, where ground was moved (here or there): a retaining wall down to it.
		int face = t;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			int nx = x + side.getStepX();
			int nz = z + side.getStepZ();
			if (this.wet(nx, nz)) {
				continue;
			}

			int nt = this.target(nx, nz);
			if (nt <= t - 2 && (moved || nt != this.natural(nx, nz))) {
				face = Math.min(face, nt);
			}
		}

		Pad pad = this.padAt(x, z);
		if (!moved && face == t && pad == null) {
			return;
		}

		BlockState top = this.level.getBlockState(new BlockPos(x, g, z));
		BlockState surface = this.surface(top);
		BlockState under = this.under(surface);
		TownBuilder.fellIn(this.level, x, z, Math.min(g, t) + 1, Math.max(g, t) + 3);
		if (t > g) {
			TownBuilder.buried(this.level, new BlockPos(x, g, z));
			for (int y = g + 1; y <= t; y++) {
				TownBuilder.set(this.level, new BlockPos(x, y, z), y == t ? surface : under);
			}
		} else if (t < g) {
			for (int y = t + 1; y <= g; y++) {
				TownBuilder.set(this.level, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
			}

			// (Earth showing on top again gets the land's top block; rock is left as rock.)
			BlockPos now = new BlockPos(x, t, z);
			BlockState state = this.level.getBlockState(now);
			if (state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) && !surface.is(Blocks.COARSE_DIRT)) {
				TownBuilder.set(this.level, now, surface.is(BlockTags.BASE_STONE_OVERWORLD) ? Blocks.GRASS_BLOCK.defaultBlockState() : surface);
			}
		}

		for (int y = face + 1; y <= t - 1; y++) {
			TownBuilder.set(this.level, new BlockPos(x, y, z), this.wall());
		}

		// Plants left floating or buried, and room over a pad (its trees felled whole; the store's, a house's height).
		int clear = pad != null ? pad.clear() : 2;
		TownBuilder.fellIn(this.level, x, z, t + 1, t + clear);
		for (int y = t + 1; y <= t + 2; y++) {
			BlockPos over = new BlockPos(x, y, z);
			BlockState state = this.level.getBlockState(over);
			if (!state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty()) {
				TownBuilder.set(this.level, over, Blocks.AIR.defaultBlockState());
			}
		}
	}

	/** The land's own top block for moved ground: its grass, podzol, sand, snow or rock; grass where it was anything else. */
	private BlockState surface(BlockState top) {
		if (top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.PODZOL) || top.is(Blocks.MYCELIUM) || top.is(Blocks.COARSE_DIRT) || top.is(Blocks.SAND)
			|| top.is(Blocks.RED_SAND) || top.is(Blocks.SNOW_BLOCK) || top.is(Blocks.GRAVEL) || top.is(Blocks.MOSS_BLOCK) || top.is(Blocks.MUD)
			|| top.is(BlockTags.BASE_STONE_OVERWORLD) || top.is(Blocks.SANDSTONE) || top.is(Blocks.TERRACOTTA)) {
			return top.getBlock().defaultBlockState();
		}

		return this.sandy ? Blocks.SAND.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
	}

	/** What's under it: earth under soil, sandstone under sand, rock under rock. */
	private BlockState under(BlockState surface) {
		if (surface.is(Blocks.SAND) || surface.is(Blocks.SANDSTONE)) {
			return Blocks.SANDSTONE.defaultBlockState();
		}

		if (surface.is(Blocks.RED_SAND)) {
			return Blocks.RED_SANDSTONE.defaultBlockState();
		}

		if (surface.is(BlockTags.BASE_STONE_OVERWORLD) || surface.is(Blocks.TERRACOTTA)) {
			return surface;
		}

		return Blocks.DIRT.defaultBlockState();
	}

	/** A retaining wall's block: cobblestone, some mossy, some stone (sandstone in the desert). */
	private BlockState wall() {
		float roll = this.random.nextFloat();
		if (this.sandy) {
			return (roll < 0.7F ? Blocks.SANDSTONE : Blocks.SMOOTH_SANDSTONE).defaultBlockState();
		}

		return (roll < 0.6F ? Blocks.COBBLESTONE : roll < 0.85F ? Blocks.MOSSY_COBBLESTONE : Blocks.STONE).defaultBlockState();
	}
}
