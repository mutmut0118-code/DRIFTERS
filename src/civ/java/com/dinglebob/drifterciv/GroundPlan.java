package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

/**
 * A town's ground, shaped once for all of it (milestone 23, docs/LIVED_WORLD.md W2; the user, 2026-10-30: "terraform the
 * terrain in a most possible natural way that way it does fit, every single time ... instead of an air box"). Before, each
 * part of a town flattened its own patch and eased ten blocks round it at most: cut into a hill, it left a box with walls
 * of dirt.
 *
 * <p>Now every column of the town's ground gets one height: its plaza, store, fields and houses ({@link Pad}s) at their
 * own heights, a ring a block wide round each at that height (a doorstep), and the land round them falling or rising a
 * block every two from there until it meets the land as it was (a gentle bank, within a pad's reach); its roads at the
 * grades they're laid at, with shoulders. Where two of them ask for more than banks can give (a house above the road beside
 * it), the nearer wins and between them stands a retaining wall: a wall of stone the height of the step, grass on top.
 * Gentle land is left as it is. Filled ground gets the land's own layers (its top block, dirt or sandstone under it), cut
 * ground its top block again where earth shows; trees on moved ground come down whole, water is never filled. Shaped a
 * piece at a time ({@link #apply}), the same heights whichever piece goes first.
 *
 * <p>Looking natural (the user, 2026-10-30, with pictures: "the terraforming comes off a little bit too unnatural"): a
 * bank's edge wanders (round rectangles it made square rings of steps, like a stepped pit); past a pad's reach the land may
 * stand a {@link #WALL} more off each {@link #TERRACE} before it's moved (it cut 24 blocks into a hill before, a quarry with
 * a wall of cobblestone ten high at the back), so a steep hill gets low terraces and no wall's much taller than that; filled ground slopes down to water instead of standing
 * over it as a cliff of dirt; a slot one or two wide left between higher ground is filled; cut rock gets earth and grass
 * back on top, lava a cut opens is sealed; and with Terrain Slabs, each step of moved ground gets the land's own slab at
 * its foot, as the land round it has.
 */
final class GroundPlan {
	/** Ground held at a height (the top block): its rectangle, how far round it the land's eased to it by banks alone, room cleared over it. */
	record Pad(TownBuilder.Rect rect, int height, int reach, int clear) {
	}

	/** Pads are looked up by squares this wide. */
	private static final int CELL = 16;
	/** How far round a road's columns the land's eased to it at most (its shoulders a block wide, then gentle banks). */
	private static final int ROAD_REACH = 12;
	/** Round a road, banks alone this far; past it the land may stand a {@link #WALL} off before it's moved. */
	private static final int ROAD_BANK = 6;
	/**
	 * How far round a pad the land's eased to it at most (past its own reach, only what stands more than terraces take). The
	 * land past it is left as it is; a pad's spot must fit within it (at 24, a house on a peak was judged by the foot of the
	 * mountain, and a hill town found room for none).
	 */
	static final int MOST_REACH = 12;
	/** The highest step left where land too steep for banks meets moved ground: a retaining wall's height at most. */
	static final int WALL = 3;
	/**
	 * Past a reach, a terrace this deep for each further wall's height the land may stand off (a hillside steeper than banks
	 * can take: low terraces stacked up it, a wall at the back of each, not one tall wall; with one alone, a hill town found
	 * room for two houses of ten).
	 */
	private static final int TERRACE = 6;
	/** Filled ground within this of water slopes down to it. */
	private static final int SHORE = 8;

	private final ServerLevel level;
	private final RandomSource random;
	/** A desert town: sand and sandstone for its fills and walls. */
	private final boolean sandy;
	private final long salt;
	private final Map<Long, List<Pad>> index = new HashMap<>();
	private final Map<Long, Integer> roads = new HashMap<>();
	private final Map<Long, Integer> natural = new HashMap<>();
	private final Map<Long, Boolean> water = new HashMap<>();
	private final Map<Long, Integer> raw = new HashMap<>();
	private final Map<Long, Integer> targets = new HashMap<>();
	private int minX = Integer.MAX_VALUE;
	private int minZ = Integer.MAX_VALUE;
	private int maxX = Integer.MIN_VALUE;
	private int maxZ = Integer.MIN_VALUE;
	/** The roads' pull on the land round them, worked out once over the whole area (null: not yet, or a road added since). */
	@Nullable
	private RoadField field;

	GroundPlan(ServerLevel level, List<Pad> pads, RandomSource random, boolean sandy) {
		this.level = level;
		this.random = random;
		this.sandy = sandy;
		this.salt = level.getSeed() * 31L + 0x5DEECE66DL;
		for (Pad pad : pads) {
			TownBuilder.Rect rect = pad.rect();
			int r = MOST_REACH;
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
		this.field = null;
		this.raw.clear();
		this.targets.clear();
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

	/**
	 * Water here (never filled), or land not loaded (never touched: reading or shaping it made the game load or generate the
	 * land there and then; a road between towns stopped the server for ten seconds).
	 */
	boolean wet(int x, int z) {
		if (!this.known(x, z)) {
			return true;
		}

		this.natural(x, z);
		return this.water.get(BlockPos.asLong(x, 0, z));
	}

	/** This column's chunk loaded (to read and shape). */
	private boolean known(int x, int z) {
		return this.level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
	}

	private final Map<Long, Boolean> loaded = new HashMap<>();

	/**
	 * This column's chunk and every chunk round it loaded: to fell a tree here (its leaves reach over; felling one at the edge
	 * of what's loaded made the game generate the land past it). Only that: with it for everything, a road between towns
	 * left whole pieces of itself unshaped where the land round them wasn't loaded yet, cut off in the middle (the user's
	 * pictures).
	 */
	private boolean loaded(int x, int z) {
		return this.loaded.computeIfAbsent(ChunkPos.asLong(x >> 4, z >> 4), key -> {
			int cx = ChunkPos.getX(key);
			int cz = ChunkPos.getZ(key);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (this.level.getChunkSource().getChunkNow(cx + dx, cz + dz) == null) {
						return false;
					}
				}
			}

			return true;
		});
	}

	/** The height here by the pads alone (before its roads are laid out on it). */
	int base(int x, int z) {
		Integer height = this.height(x, z, false);
		return height != null ? height : this.natural(x, z);
	}

	/** The height the ground goes to here: as the pads and roads ask, a slot between higher ground filled. */
	int target(int x, int z) {
		long key = BlockPos.asLong(x, 0, z);
		Integer known = this.targets.get(key);
		if (known == null) {
			known = this.raw(x, z);
			if (!this.roads.containsKey(key) && this.padAt(x, z) == null && !this.wet(x, z)) {
				known = this.unslot(x, z, known);
			}

			this.targets.put(key, known);
		}

		return known;
	}

	private int raw(int x, int z) {
		long key = BlockPos.asLong(x, 0, z);
		Integer known = this.raw.get(key);
		if (known == null) {
			Integer height = this.height(x, z, true);
			known = height != null ? height : this.natural(x, z);
			this.raw.put(key, known);
		}

		return known;
	}

	/**
	 * A slot one or two wide between ground two or more higher on both sides, filled to the lower side (in a hill city,
	 * streets and terraces left deep slots between their walls, stairs at the bottom: found in the user's pictures).
	 */
	private int unslot(int x, int z, int t) {
		int fill = t;
		for (Direction side : new Direction[] {Direction.EAST, Direction.SOUTH}) {
			int sx = side.getStepX();
			int sz = side.getStepZ();
			Integer a = this.higher(x, z, -sx, -sz, t);
			Integer b = a == null ? null : this.higher(x, z, sx, sz, t);
			if (a != null && b != null) {
				fill = Math.max(fill, Math.min(a, b));
			}
		}

		return fill;
	}

	/** Ground two or more over {@code t} a block that way, or two (the block between no more than one off it), or null. */
	@Nullable
	private Integer higher(int x, int z, int dx, int dz, int t) {
		for (int step = 1; step <= 2; step++) {
			int nx = x + dx * step;
			int nz = z + dz * step;
			if (this.wet(nx, nz)) {
				return null;
			}

			int there = this.raw(nx, nz);
			if (there >= t + 2) {
				return there;
			}

			if (Math.abs(there - t) > 1 || this.roads.containsKey(BlockPos.asLong(nx, 0, nz))) {
				return null;
			}
		}

		return null;
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
		double nearLo = Double.MAX_VALUE;
		double nearHi = Double.MAX_VALUE;
		boolean any = false;
		List<Pad> near = this.index.get(BlockPos.asLong(Math.floorDiv(x, CELL), 0, Math.floorDiv(z, CELL)));
		if (near != null) {
			for (Pad pad : near) {
				double d = distance(pad.rect(), x, z);
				if (d > MOST_REACH) {
					continue;
				}

				any = true;
				int e = this.allowance(d, pad.reach(), x, z);
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

		int roadAway = Integer.MAX_VALUE;
		if (withRoads && !this.roads.isEmpty()) {
			RoadField roads = this.field();
			int i = roads.at(x, z);
			if (i >= 0 && roads.loD[i] <= ROAD_REACH) {
				any = true;
				roadAway = Math.min(roads.loD[i], roads.hiD[i]);
				if (roads.lo[i] > lo || roads.lo[i] == lo && roads.loD[i] < nearLo) {
					lo = roads.lo[i];
					nearLo = roads.loD[i];
				}

				if (roads.hi[i] < hi || roads.hi[i] == hi && roads.hiD[i] < nearHi) {
					hi = roads.hi[i];
					nearHi = roads.hiD[i];
				}
			}
		}

		if (!any || this.wet(x, z)) {
			return null;
		}

		// Asked for more than banks can give: the nearer has its way, and a wall stands between (see apply).
		int g = this.natural(x, z);
		int t = lo > hi ? nearLo <= nearHi ? lo : hi : Mth.clamp(g, lo, hi);
		// Cut down beside water: no lower than the water (it spilled down a road cut below a stream: the user's picture).
		if (t < g) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				int nx = x + side.getStepX();
				int nz = z + side.getStepZ();
				if (this.known(nx, nz) && this.wet(nx, nz)) {
					t = Math.min(g, Math.max(t, this.natural(nx, nz)));
				}
			}
		}

		// Built up near water: no higher than a slope down to it (it stood over a river as a cliff of dirt). Not a road's
		// shoulder: a bridge's ramp is held at its height.
		if (t > g && roadAway > 1) {
			Integer shore = this.shore(x, z);
			if (shore != null) {
				t = Math.max(g, Math.min(t, shore));
			}
		}

		return t;
	}

	/** The highest the ground may be built up to here for a slope down to the water near it (null: none near). */
	@Nullable
	private Integer shore(int x, int z) {
		Integer cap = null;
		for (int dx = -SHORE; dx <= SHORE; dx++) {
			for (int dz = -SHORE; dz <= SHORE; dz++) {
				if (!this.known(x + dx, z + dz) || !this.wet(x + dx, z + dz)) {
					continue;
				}

				int d = Math.max(Math.abs(dx), Math.abs(dz));
				int most = this.natural(x + dx, z + dz) + 1 + (d - 1) / 2;
				cap = cap == null ? most : Math.min(cap, most);
			}
		}

		return cap;
	}

	/** Steps out from this rectangle to x, z, as the crow flies (0 inside it). */
	private static double distance(TownBuilder.Rect rect, int x, int z) {
		int dx = Math.max(Math.max(rect.x0() - x, x - rect.x1()), 0);
		int dz = Math.max(Math.max(rect.z0() - z, z - rect.z1()), 0);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * How far the land may stand off what's held, {@code d} blocks out from it: nothing in a ring a block wide (a doorstep),
	 * then a block every two (a gentle bank: a block a step looked like a pit dug round a field, found in the first pictures),
	 * its edge wandering; past {@code reach}, a wall's height more.
	 */
	private int allowance(double d, int reach, int x, int z) {
		if (d <= 1.5) {
			return 0;
		}

		return (int)Math.floor((d + this.wobble(x, z)) / 2.0) + terraces(d, reach);
	}

	/** Walls' heights more the land may stand off, {@code d} out from what's held past its {@code reach}: a wall each terrace. */
	private static int terraces(double d, int reach) {
		return d > reach ? WALL * (int)Math.ceil((d - reach) / TERRACE) : 0;
	}

	/** A smooth wander, 0 to 2.5, for banks' edges: the same for a column whoever asks. */
	private double wobble(int x, int z) {
		int gx = Math.floorDiv(x, 6);
		int gz = Math.floorDiv(z, 6);
		double fx = (x - gx * 6) / 6.0;
		double fz = (z - gz * 6) / 6.0;
		fx = fx * fx * (3 - 2 * fx);
		fz = fz * fz * (3 - 2 * fz);
		double a = Mth.lerp(fx, this.hash(gx, gz), this.hash(gx + 1, gz));
		double b = Mth.lerp(fx, this.hash(gx, gz + 1), this.hash(gx + 1, gz + 1));
		return Mth.lerp(fz, a, b) * 2.5;
	}

	private double hash(int x, int z) {
		long h = Mth.getSeed(x, 0, z) ^ this.salt;
		h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}

	/** The roads' pull on each column round them: the most and least height they allow and how far the nearest is. */
	private static final class RoadField {
		final int x0;
		final int z0;
		final int w;
		final int h;
		final int[] lo;
		final int[] hi;
		final int[] loD;
		final int[] hiD;

		RoadField(int x0, int z0, int x1, int z1) {
			this.x0 = x0;
			this.z0 = z0;
			this.w = x1 - x0 + 1;
			this.h = z1 - z0 + 1;
			int n = this.w * this.h;
			this.lo = new int[n];
			this.hi = new int[n];
			this.loD = new int[n];
			this.hiD = new int[n];
			java.util.Arrays.fill(this.lo, Integer.MIN_VALUE);
			java.util.Arrays.fill(this.hi, Integer.MAX_VALUE);
			java.util.Arrays.fill(this.loD, Integer.MAX_VALUE);
			java.util.Arrays.fill(this.hiD, Integer.MAX_VALUE);
		}

		int at(int x, int z) {
			int i = x - this.x0;
			int k = z - this.z0;
			return i < 0 || k < 0 || i >= this.w || k >= this.h ? -1 : k * this.w + i;
		}
	}

	/** Worked out once from every road column (a look round each column for roads, every column, took a while on a city). */
	private RoadField field() {
		if (this.field != null) {
			return this.field;
		}

		RoadField f = new RoadField(this.minX, this.minZ, this.maxX, this.maxZ);
		for (var road : this.roads.entrySet()) {
			int rx = BlockPos.getX(road.getKey());
			int rz = BlockPos.getZ(road.getKey());
			int height = road.getValue();
			for (int dx = -ROAD_REACH; dx <= ROAD_REACH; dx++) {
				for (int dz = -ROAD_REACH; dz <= ROAD_REACH; dz++) {
					int i = f.at(rx + dx, rz + dz);
					if (i < 0) {
						continue;
					}

					int d = Math.max(Math.abs(dx), Math.abs(dz));
					int e = d <= 1 ? 0 : (int)Math.floor((d + this.wobble(rx + dx, rz + dz)) / 2.0) + terraces(d, ROAD_BANK);
					if (height - e > f.lo[i] || height - e == f.lo[i] && d < f.loD[i]) {
						f.lo[i] = height - e;
						f.loD[i] = d;
					}

					if (height + e < f.hi[i] || height + e == f.hi[i] && d < f.hiD[i]) {
						f.hi[i] = height + e;
						f.hiD[i] = d;
					}
				}
			}
		}

		this.field = f;
		return f;
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
				if (pad.rect().away(x, z) <= MOST_REACH) {
					return true;
				}
			}
		}

		if (!this.roads.isEmpty()) {
			RoadField roads = this.field();
			int i = roads.at(x, z);
			return i >= 0 && roads.loD[i] <= ROAD_REACH;
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
		boolean onRoad = this.roads.containsKey(BlockPos.asLong(x, 0, z));
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
			// (Left as it was; lava showing on it sealed; a slab at its foot where ground moved beside it now stands a block over it.)
			this.sealLava(x, z, g, t);
			this.footSlab(x, z, t, onRoad);
			return;
		}

		BlockState top = this.level.getBlockState(new BlockPos(x, g, z));
		BlockState surface = this.surface(top);
		BlockState under = this.under(surface);
		// A slab the land had on its top (Terrain Slabs' half step): gone with the old surface (cut down, it was left floating).
		BlockPos half = new BlockPos(x, g + 1, z);
		BlockState halfState = this.level.getBlockState(half);
		if (com.dinglebob.drifter.api.DrifterApi.naturalSlab(halfState) && halfState.getFluidState().isEmpty()) {
			TownBuilder.set(this.level, half, Blocks.AIR.defaultBlockState());
		}

		if (this.loaded(x, z)) {
			TownBuilder.fellIn(this.level, x, z, Math.min(g, t) + 1, Math.max(g, t) + 3);
		}

		if (t > g) {
			TownBuilder.buried(this.level, new BlockPos(x, g, z));
			for (int y = g + 1; y <= t; y++) {
				TownBuilder.set(this.level, new BlockPos(x, y, z), y == t ? surface : under);
			}
		} else if (t < g) {
			for (int y = t + 1; y <= g; y++) {
				TownBuilder.set(this.level, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
			}

			// Earth showing on top again gets the land's top block; rock cut into under soil gets earth and its top block back
			// (grey steps of bare stone down a grassy hill looked like a quarry, in the user's pictures).
			BlockPos now = new BlockPos(x, t, z);
			BlockState state = this.level.getBlockState(now);
			boolean soil = !surface.is(BlockTags.BASE_STONE_OVERWORLD) && !surface.is(Blocks.TERRACOTTA);
			if (state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) && !surface.is(Blocks.COARSE_DIRT)) {
				TownBuilder.set(this.level, now, soil ? surface : Blocks.GRASS_BLOCK.defaultBlockState());
			} else if (soil && state.is(BlockTags.BASE_STONE_OVERWORLD)) {
				TownBuilder.set(this.level, now, surface);
				if (this.level.getBlockState(now.below()).is(BlockTags.BASE_STONE_OVERWORLD)) {
					TownBuilder.set(this.level, now.below(), under);
				}
			}
		}

		for (int y = face + 1; y <= t - 1; y++) {
			TownBuilder.set(this.level, new BlockPos(x, y, z), this.wall());
		}

		this.sealLava(x, z, g, t);
		// Plants left floating or buried, and room over a pad (its trees felled whole; the store's, a house's height).
		int clear = pad != null ? pad.clear() : 2;
		if (this.loaded(x, z)) {
			TownBuilder.fellIn(this.level, x, z, t + 1, t + clear);
		}

		// (Up to two over the old top too: grass on ground cut away under it, left hanging now nothing breaks it.)
		for (int y = t + 1; y <= Math.max(t, g) + 2; y++) {
			BlockPos over = new BlockPos(x, y, z);
			BlockState state = this.level.getBlockState(over);
			if (!state.isAir() && (TownBuilder.plant(state) || com.dinglebob.drifter.api.DrifterApi.naturalSlab(state)) && state.getFluidState().isEmpty()) {
				TownBuilder.set(this.level, over, Blocks.AIR.defaultBlockState());
			}
		}

		if (pad == null) {
			this.footSlab(x, z, t, onRoad);
		}
	}

	/**
	 * Lava a cut opened or lying on the land (found in the user's pictures: a pool of it on a terrace in a town, and by a road),
	 * here under its top and in the ground beside it down the face: stone.
	 */
	private void sealLava(int x, int z, int g, int t) {
		for (int y = t - 3; y <= Math.max(g, t) + 1; y++) {
			this.stoneOverLava(new BlockPos(x, y, z));
		}

		for (Direction side : Direction.Plane.HORIZONTAL) {
			for (int y = t - 1; y <= Math.max(g, t) + 1; y++) {
				this.stoneOverLava(new BlockPos(x + side.getStepX(), y, z + side.getStepZ()));
			}
		}
	}

	private void stoneOverLava(BlockPos pos) {
		if (this.level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null && (this.level.getFluidState(pos).is(Fluids.LAVA) || this.level.getFluidState(pos).is(Fluids.FLOWING_LAVA))) {
			TownBuilder.set(this.level, pos, Blocks.STONE.defaultBlockState());
		}
	}

	/**
	 * With Terrain Slabs: where ground beside this column (moved) stands a block over it, the land's own slab on its top at
	 * the foot of the step, as the mod puts on the land round it (moved ground had sharp steps among the land's soft ones).
	 * Not on a road (it gets its paving's slab), not into anything but air or a plant.
	 */
	private void footSlab(int x, int z, int t, boolean onRoad) {
		if (onRoad) {
			return;
		}

		BlockPos over = new BlockPos(x, t + 1, z);
		BlockState there = this.level.getBlockState(over);
		if (!(there.isAir() || TownBuilder.plant(there)) || !there.getFluidState().isEmpty()) {
			return;
		}

		boolean step = false;
		boolean moved = t != this.natural(x, z);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			int nx = x + side.getStepX();
			int nz = z + side.getStepZ();
			if (this.wet(nx, nz)) {
				continue;
			}

			int nt = this.target(nx, nz);
			if (nt == t + 1 && (moved || nt != this.natural(nx, nz))) {
				step = true;
				break;
			}
		}

		if (!step) {
			return;
		}

		BlockState slab = slabFor(this.level.getBlockState(new BlockPos(x, t, z)));
		if (slab != null) {
			TownBuilder.set(this.level, over, slab);
		}
	}

	private static final Map<Block, Optional<BlockState>> SLABS = new HashMap<>();

	/** Terrain Slabs' slab of this block, as the mod puts on the land (its "generated" set), or null (none, or the mod's not here). */
	@Nullable
	static BlockState slabFor(BlockState top) {
		return SLABS.computeIfAbsent(top.getBlock(), block -> {
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
			if (!"minecraft".equals(id.getNamespace())) {
				return Optional.empty();
			}

			String name = switch (id.getPath()) {
				case "grass_block" -> "grass_slab";
				case "coarse_dirt" -> "coarse_slab";
				case "snow_block" -> "snow_slab";
				case "dirt_path" -> "path_slab";
				default -> id.getPath() + "_slab";
			};
			for (String path : new String[] {name, "terrain_" + name}) {
				Optional<Block> slab = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("terrain_slabs", path));
				if (slab.isPresent()) {
					BlockState state = slab.get().defaultBlockState();
					if (state.getBlock().getStateDefinition().getProperty("generated") instanceof BooleanProperty generated) {
						state = state.setValue(generated, true);
					}

					return Optional.of(state);
				}
			}

			return Optional.empty();
		}).orElse(null);
	}

	/**
	 * How badly a pad here at {@code base} suits the land round it (0: well), at worst: how far the land at the edge of what
	 * it may move stands off more than its banks and terraces take there (a wall taller than {@link #WALL} where the moved
	 * ground meets the land: on a steep hill, a quarry with a wall at the back; within, moved ground is terraces, its walls
	 * no taller than that), and how far the shore would be built up over the water's edge (a cliff of dirt into a river).
	 */
	static int misfit(ServerLevel level, TownBuilder.Rect rect, int base, int reach) {
		int worst = 0;
		why = "";
		for (int x = rect.x0() - MOST_REACH; x <= rect.x1() + MOST_REACH; x++) {
			for (int z = rect.z0() - MOST_REACH; z <= rect.z1() + MOST_REACH; z++) {
				double d = distance(rect, x, z);
				// (Land not loaded is left out: its height reads as the bottom of the world, and a hill town found no house fit.)
				if (d <= 1.5 || d > MOST_REACH || level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
					continue;
				}

				int g = TownBuilder.ground(level, x, z);
				int bank = (int)Math.floor(d / 2.0);
				if (TownBuilder.wet(level, x, z)) {
					// (The pad's bank down to here, at the least, over the water's edge by more than a block where the land beside
					// the water's lower: a fill standing over it; up to a wall's height, a wall by the pad instead.)
					int shore = g + 1;
					for (Direction side : Direction.Plane.HORIZONTAL) {
						if (!TownBuilder.wet(level, x + side.getStepX(), z + side.getStepZ())) {
							shore = Math.max(shore, TownBuilder.ground(level, x + side.getStepX(), z + side.getStepZ()));
						}
					}

					int over = base - bank - terraces(d, reach) - shore - WALL;
					if (over > worst) {
						worst = over;
						why = "water at " + x + "," + z + " (" + g + ", shore " + shore + ") d " + (int)d;
					}
				} else if (d >= MOST_REACH - 1) {
					int over = Math.abs(g - base) - bank - terraces(d, reach);
					if (over > worst) {
						worst = over;
						why = "land at " + x + "," + g + "," + z + " d " + (int)d;
					}
				}
			}
		}

		return worst;
	}

	/** Debug: what the last {@link #misfit} found worst. */
	static String why = "";

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
