package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * A stretch of a road between towns, the second making of it (the user, 2026-11-13, with pictures: "lets revamp the roads
 * ... the path blocks are kinda janky, the grass sometimes over taking the path, bridge fences sometimes not connecting or
 * switching wood mid bridge, little divots that go up and down rapidly with the slabs ... you can tell it was terraformed
 * ... the terrain under the paths doesnt follow the rule where its 3-5 blocks of dirt then stone").
 *
 * <p>Its top is a worn mix of packed mud, rooted dirt and coarse dirt (the user's picture: mud down the middle, coarser
 * out to its edges), four or five wide with ragged edges (smooth sandstone in a desert). Its heights are worked out along
 * its middle line and every column takes the height of the step of the line it's nearest, so a row across it is one
 * height; they change only when the land has moved a good way off them (no going up and down by the block), a block every
 * three where the land lets it. With Terrain Slabs it rises half a block at a time on the mod's own slabs of the three
 * blocks; without, a block at a time. Under it the ground is made good: earth for three to five blocks, rock under that,
 * anything open (a cave, lava) filled.
 *
 * <p>Over water a bridge after the user's picture: a deck of planks three wide, a beam of logs along each side with a
 * fence on it, a post of logs every few blocks with a lantern on top, and under each post over the water a pier of logs
 * on a foot of mossy stone, braced under the beam. One wood for the whole of it (a stretch that meets a bridge already
 * begun takes its wood). Water only at the road's edge is left out of the road (it was a stub of bridge on the shore), and
 * a trickle two or three wide gets planks laid flat, no railing.
 */
final class WildRoad {
	private WildRoad() {
	}

	/** Its middle three (a bridge's deck) are within this of its line. */
	private static final double CORE = 1.5;
	/** Land this many steps long or less in the middle of a crossing is bridged over. */
	private static final int ISLET = 8;
	/** It's cut or built at most this far from the ground. */
	private static final int DEEPEST = 6;
	/** A post (and a pier under it) every this many steps of a bridge. */
	private static final int POSTS = 6;
	/** Water this many steps across or less: planks laid flat over it, no railings. */
	private static final int TRICKLE = 3;
	/** Its height changes when the land's this far (half blocks) off it. */
	private static final double HOLD = 0.8;
	/** Debug: each stretch's columns and heights logged. */
	static boolean DEBUG = System.getenv("DRIFTER_ROAD_DEBUG") != null;
	static final Map<Long, Integer> LAID = new HashMap<>();

	static void build(ServerLevel level, List<double[]> line, Set<Long> keep, Set<Long> avoid) {
		int n = line.size();
		if (n < 2) {
			return;
		}

		long salt = level.getSeed() * 31L + 0x52D0AL;
		double[] mid = line.get(n / 2);
		BlockPos middle = new BlockPos(Mth.floor(mid[0]), 0, Mth.floor(mid[1]));
		TownBuilder.style = TownBuilder.Style.at(level, middle.atY(TownBuilder.ground(level, middle.getX(), middle.getZ()) + 1));
		boolean desert = TownBuilder.style == TownBuilder.Style.DESERT;
		boolean halves = desert || slabOf(Blocks.PACKED_MUD.defaultBlockState()) != null;

		// Its columns: each with the step of the line it's nearest and how far off it is.
		Map<Long, Integer> cellT = new HashMap<>();
		Map<Long, Double> cellD = new HashMap<>();
		for (int t = 0; t < n; t++) {
			double[] p = line.get(t);
			int bx = Mth.floor(p[0]);
			int bz = Mth.floor(p[1]);
			for (int x = bx - 3; x <= bx + 3; x++) {
				for (int z = bz - 3; z <= bz + 3; z++) {
					double d = Math.hypot(x + 0.5 - p[0], z + 0.5 - p[1]);
					long key = BlockPos.asLong(x, 0, z);
					Double had = cellD.get(key);
					if (d <= 3.0 && (had == null || d < had)) {
						cellD.put(key, d);
						cellT.put(key, t);
					}
				}
			}
		}

		Set<Long> cells = new HashSet<>();
		for (var cell : cellD.entrySet()) {
			if (cell.getValue() <= half(BlockPos.getX(cell.getKey()), BlockPos.getZ(cell.getKey()), salt)) {
				cells.add(cell.getKey());
			}
		}

		// Ruins in its way and on its shoulders cleared first, so they aren't taken for the ground.
		for (long cell : cells) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					long column = BlockPos.asLong(BlockPos.getX(cell) + dx, 0, BlockPos.getZ(cell) + dz);
					if (!keep.contains(column)) {
						TownBuilder.clearRuin(level, BlockPos.getX(column), BlockPos.getZ(column));
					}
				}
			}
		}

		TownBuilder.GROUND.clear();
		// The ground along its line (its middle three's highest dry column), and where its line is over water.
		double[] ground = new double[n];
		java.util.Arrays.fill(ground, Double.NEGATIVE_INFINITY);
		boolean[] wetAt = new boolean[n];
		int[] waterTop = new int[n];
		Set<Long> standing = new HashSet<>();
		// (A bridge's deck laid already, by the stretch before it: part of this one's bridge, not something built in its way.
		// Taken for that, the bridge stopped at it and began again past it: a gap in its railings, the two not one bridge.)
		Map<Long, Integer> laidDeck = new HashMap<>();
		for (long cell : cells) {
			int x = BlockPos.getX(cell);
			int z = BlockPos.getZ(cell);
			if (!TownBuilder.loadedAt(level, x, z)) {
				continue;
			}

			if (!keep.contains(cell) && cellD.get(cell) <= CORE) {
				int top = TownBuilder.ground(level, x, z);
				if (level.getBlockState(new BlockPos(x, top, z)).is(BlockTags.PLANKS) && overWater(level, new BlockPos(x, top, z))) {
					laidDeck.put(cell, top);
					continue;
				}
			}

			// (Something built on its way, a house: left as it is, not taken for the ground, not paved over.)
			if (!keep.contains(cell) && TownBuilder.built(level, x, z)) {
				standing.add(cell);
				continue;
			}

			if (cellD.get(cell) <= CORE && !TownBuilder.wet(level, x, z)) {
				int t = cellT.get(cell);
				ground[t] = Math.max(ground[t], TownBuilder.paved(level, x, z));
			}
		}

		for (int t = 0; t < n; t++) {
			BlockPos at = at(line, t);
			boolean loaded = TownBuilder.loadedAt(level, at.getX(), at.getZ());
			Integer deck = laidDeck.get(BlockPos.asLong(at.getX(), 0, at.getZ()));
			if (deck != null) {
				wetAt[t] = true;
				waterTop[t] = deck - 2;
			} else if (loaded && !standing.contains(BlockPos.asLong(at.getX(), 0, at.getZ())) && TownBuilder.wet(level, at.getX(), at.getZ())) {
				wetAt[t] = true;
				waterTop[t] = TownBuilder.ground(level, at.getX(), at.getZ());
			}

			if (ground[t] == Double.NEGATIVE_INFINITY) {
				ground[t] = loaded || t == 0 ? TownBuilder.ground(level, at.getX(), at.getZ()) : ground[t - 1];
			}
		}

		// Its ends: where a stretch beside it is laid already, that one's height (to the half block); else the ground there.
		int pin0 = level2(level, at(line, 0));
		int pin1 = level2(level, at(line, n - 1));
		if (!halves) {
			pin0 &= ~1;
			pin1 &= ~1;
		}

		// Crossings: the water along its line, a little land in the middle of it bridged over too.
		int[] span = new int[n];
		java.util.Arrays.fill(span, -1);
		List<int[]> spans = new ArrayList<>();
		double[] natural = ground.clone();
		for (int t = 0; t < n; t++) {
			if (!wetAt[t]) {
				continue;
			}

			int s = t;
			int e = t;
			int water = waterTop[t];
			while (true) {
				int k = e + 1;
				while (k < n && wetAt[k]) {
					water = Math.max(water, waterTop[k]);
					e = k++;
				}

				int land = k;
				boolean low = true;
				while (land < n && !wetAt[land] && land - k <= ISLET) {
					low &= natural[land] <= water + 3;
					land++;
				}

				if (land < n && wetAt[land] && land - k <= ISLET && low) {
					e = land;
					continue;
				}

				break;
			}

			int length = e - s + 1;
			boolean trickle = length <= TRICKLE;
			int base = water + (trickle ? 1 : 2);
			double a = s > 0 ? natural[s - 1] : Double.NaN;
			double b = e < n - 1 ? natural[e + 1] : Double.NaN;
			if (s == 0) {
				a = pin0 / 2.0;
			}

			if (e == n - 1) {
				b = pin1 / 2.0;
			}

			for (int k = s; k <= e; k++) {
				double f = (k - s + 1) / (double)(length + 1);
				double across = Double.isNaN(a) ? b : Double.isNaN(b) ? a : Mth.lerp(f, a, b);
				ground[k] = Math.max(base, Double.isNaN(across) ? base : Math.floor(across + 0.5));
				span[k] = spans.size();
			}

			spans.add(new int[] {s, e, trickle ? 1 : 0});
			t = e;
		}

		// Its heights: gentle where the land lets it (a block every three), a block every two where it doesn't; smoothed; held
		// to its ends; then settled in half blocks (whole ones without slabs), changing only when the land's well off them.
		double[] h = grade(ground, 1.0 / 3.0);
		double worst = 0;
		for (int t = 0; t < n; t++) {
			worst = Math.max(worst, Math.abs(h[t] - ground[t]));
		}

		if (worst > 4) {
			h = grade(ground, 0.5);
		}

		for (int pass = 0; pass < 2; pass++) {
			double[] was = h.clone();
			for (int t = 2; t < n - 2; t++) {
				if (span[t] < 0) {
					h[t] = (was[t - 2] + 2 * was[t - 1] + 3 * was[t] + 2 * was[t + 1] + was[t + 2]) / 9.0;
				}
			}
		}

		for (int t = 0; t < n; t++) {
			h[t] = span[t] >= 0 ? ground[t] : Mth.clamp(h[t], natural[t] - DEEPEST, natural[t] + DEEPEST);
		}

		// (An end held only where a stretch is laid there already: held to the ground at both ends, a stretch up a steep
		// slope couldn't reach its far end, and was pulled off its near one: eleven blocks' step where two met.)
		boolean fixed0 = laid(level, at(line, 0));
		boolean fixed1 = laid(level, at(line, n - 1));
		if (!fixed0) {
			pin0 = (int)Math.round(2 * h[0] / (halves ? 1 : 2)) * (halves ? 1 : 2);
		}

		if (!fixed1) {
			pin1 = (int)Math.round(2 * h[n - 1] / (halves ? 1 : 2)) * (halves ? 1 : 2);
		}

		for (int t = 0; t < n; t++) {
			if (span[t] >= 0) {
				continue;
			}

			h[t] = Mth.clamp(h[t], pin0 / 2.0 - t * 0.5, pin0 / 2.0 + t * 0.5);
			h[t] = Mth.clamp(h[t], pin1 / 2.0 - (n - 1 - t) * 0.5, pin1 / 2.0 + (n - 1 - t) * 0.5);
		}

		int unit = halves ? 1 : 2;
		int[] q = new int[n];
		q[0] = span[0] >= 0 ? 2 * (int)ground[0] : pin0;
		for (int t = 1; t < n; t++) {
			if (span[t] >= 0) {
				q[t] = 2 * (int)ground[t];
				continue;
			}

			// (A step, then a step level at least, unless the land's running away from it: columns side by side at a bend are a
			// step or two apart along it, and stood a block apart.)
			double want = 2 * h[t];
			q[t] = q[t - 1];
			boolean stepped = t >= 2 && q[t - 1] != q[t - 2];
			if (Math.abs(want - q[t]) >= HOLD * unit && (!stepped || Math.abs(want - q[t]) >= 2 * unit)) {
				// (A whole block's step where the land's left it three halves behind: steep land, a step to jump.)
				int most = Math.abs(want - q[t]) >= 3 * unit ? 2 : 1;
				q[t] += unit * Mth.clamp((int)Math.round((want - q[t]) / unit), -most, most);
			}
		}

		// Its far end held: back from it a step at a time until it meets the way from the near end. (Where both ends are held
		// and too far apart to meet, the step that's left is in the middle: walked all the way back, it pulled the near end
		// off the stretch before it.)
		if (span[n - 1] < 0 && fixed1) {
			int[] back = q.clone();
			back[n - 1] = pin1;
			int t = n - 2;
			for (; t >= 0 && span[t] < 0 && Math.abs(q[t] - back[t + 1]) > unit; t--) {
				back[t] = back[t + 1] + (q[t] > back[t + 1] ? unit : -unit);
			}

			int from = t < 0 && fixed0 ? n / 2 : t + 1;
			System.arraycopy(back, from, q, from, n - from);
		}

		// What each column is: road, a bridge's deck, or left out (water at its edge; the land under a bridge).
		Map<Long, Integer> paved = new HashMap<>();
		Map<Long, Integer> decks = new HashMap<>();
		Set<Long> flat = new HashSet<>();
		for (long cell : cells) {
			int x = BlockPos.getX(cell);
			int z = BlockPos.getZ(cell);
			if (keep.contains(cell) || standing.contains(cell) || !TownBuilder.loadedAt(level, x, z)) {
				continue;
			}

			int t = cellT.get(cell);
			int y = q[t] >> 1;
			boolean wet = TownBuilder.wet(level, x, z);
			boolean core = cellD.get(cell) <= CORE;
			Integer laid = laidDeck.get(cell);
			if (laid != null) {
				decks.put(cell, laid);
				if (span[t] >= 0 && spans.get(span[t])[2] == 1) {
					flat.add(cell);
				}
			} else if (span[t] >= 0) {
				// (Land in a crossing lower than the deck is bridged over, not built up to it: a sandbar beside the bridge was
				// raised to the deck as the road's shoulder, a column of sand through the railings.)
				if (core && (wet || TownBuilder.ground(level, x, z) < y)) {
					decks.put(cell, y);
					if (spans.get(span[t])[2] == 1) {
						flat.add(cell);
					}
				} else if (!wet && TownBuilder.ground(level, x, z) >= y) {
					paved.put(cell, q[t]);
				}
			} else if (!wet) {
				paved.put(cell, q[t]);
			}
		}

		// The land shaped to it, its banks eased into the land round it; a town's roads it meets held as they are.
		GroundPlan plan = new GroundPlan(level, List.of(), level.getRandom(), desert);
		plan.spareBuilt();
		plan.wildSlopes();
		for (var cell : paved.entrySet()) {
			plan.road(cell.getKey(), cell.getValue() >> 1);
		}

		for (long column : keep) {
			int x = BlockPos.getX(column);
			int z = BlockPos.getZ(column);
			boolean near = false;
			for (int dx = -3; dx <= 3 && !near; dx++) {
				for (int dz = -3; dz <= 3 && !near; dz++) {
					near = cells.contains(BlockPos.asLong(x + dx, 0, z + dz));
				}
			}

			if (near) {
				plan.road(column, TownBuilder.ground(level, x, z));
			}
		}

		// The roads about (the stretches either side, laid or not) held as they are: easing its banks, a stretch moved the
		// ground of the one before it where they bend, and put grass back over its top.
		// (All of them within reach of its banks and a bank's reach more: with only the nearest held, the rest of the stretch
		// before it, lower down a steep hill, was built up as the bank of the part that was held.)
		Set<Long> held = new HashSet<>();
		int x0 = Integer.MAX_VALUE;
		int z0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int z1 = Integer.MIN_VALUE;
		for (long cell : cells) {
			x0 = Math.min(x0, BlockPos.getX(cell) - 36);
			z0 = Math.min(z0, BlockPos.getZ(cell) - 36);
			x1 = Math.max(x1, BlockPos.getX(cell) + 36);
			z1 = Math.max(z1, BlockPos.getZ(cell) + 36);
		}

		for (long way : avoid) {
			int wx = BlockPos.getX(way);
			int wz = BlockPos.getZ(way);
			if (wx < x0 || wx > x1 || wz < z0 || wz > z1) {
				continue;
			}

			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					long column = BlockPos.asLong(wx + dx, 0, wz + dz);
					if (paved.containsKey(column) || decks.containsKey(column) || !held.add(column) || !TownBuilder.loadedAt(level, wx + dx, wz + dz)
						|| TownBuilder.wet(level, wx + dx, wz + dz)) {
						continue;
					}

					int y = TownBuilder.paved(level, wx + dx, wz + dz);
					// (At its top as it stands, a slab laid on it and all: held at the block under, the slab was cut off as loose
					// ground and the block grassed over.)
					// (Left alone, not held as a road of this plan: held, the land round them was eased to them too, and what
					// lay past that, the rest of the road before it, was built up as their bank.)
					if (worn(level.getBlockState(new BlockPos(wx + dx, y, wz + dz))) || level.getBlockState(new BlockPos(wx + dx, y, wz + dz)).is(BlockTags.PLANKS)) {
						plan.leave(column);
					}
				}
			}
		}

		for (var piece : plan.pieces(32)) {
			plan.apply(piece);
		}

		TownBuilder.GROUND.clear();
		// Its top: the worn mix, a slab of the same where it's half a block up; the ground under it made good.
		for (var cell : paved.entrySet()) {
			int x = BlockPos.getX(cell.getKey());
			int z = BlockPos.getZ(cell.getKey());
			BlockPos top = new BlockPos(x, cell.getValue() >> 1, z);
			BlockState block = desert ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : mix(x, z, cellD.get(cell.getKey()), salt);
			TownBuilder.set(level, top, block);
			under(level, top, desert, salt);
			TownBuilder.fellIn(level, x, z, top.getY() + 1, top.getY() + 4);
			clearOver(level, top, 4);
			if ((cell.getValue() & 1) == 1) {
				BlockState slab = desert ? Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState() : slabOf(block);
				if (slab != null) {
					TownBuilder.set(level, top.above(), slab);
				}
			}
		}

		if (!decks.isEmpty()) {
			bridge(level, line, decks, flat, paved, cellT, cellD, span, spans, keep, avoid, standing);
		}

		TownBuilder.tidy(level, cells);

		// The test: steps along it that need a jump (over half a block between columns side by side, off the bridges).
		int pairs = 0;
		int jumps = 0;
		for (var cell : paved.entrySet()) {
			for (Direction side : new Direction[] {Direction.EAST, Direction.SOUTH}) {
				Integer next = paved.get(BlockPos.asLong(BlockPos.getX(cell.getKey()) + side.getStepX(), 0, BlockPos.getZ(cell.getKey()) + side.getStepZ()));
				if (next != null) {
					pairs++;
					jumps += Math.abs(cell.getValue() - next) > 1 ? 1 : 0;
				}
			}
		}

		if (DEBUG) {
			int core = 0;
			int corePaved = 0;
			StringBuilder why = new StringBuilder();
			for (long cell : cells) {
				if (cellD.get(cell) > CORE) {
					continue;
				}

				core++;
				if (paved.containsKey(cell) || decks.containsKey(cell)) {
					corePaved++;
				} else if (why.length() < 400) {
					int x = BlockPos.getX(cell);
					int z = BlockPos.getZ(cell);
					why.append(x).append(',').append(z).append(keep.contains(cell) ? " keep" : standing.contains(cell) ? " standing:"
						+ TownBuilder.builtBy(level, x, z) : TownBuilder.wet(level, x, z) ? " wet" : " t" + cellT.get(cell) + " span" + span[cellT.get(cell)]).append("; ");
				}
			}

			int sheer = 0;
			StringBuilder sides = new StringBuilder();
			for (var cell : paved.entrySet()) {
				for (Direction side : Direction.Plane.HORIZONTAL) {
					int nx = BlockPos.getX(cell.getKey()) + side.getStepX();
					int nz = BlockPos.getZ(cell.getKey()) + side.getStepZ();
					if (cells.contains(BlockPos.asLong(nx, 0, nz)) || !TownBuilder.loadedAt(level, nx, nz)) {
						continue;
					}

					int there = TownBuilder.ground(level, nx, nz);
					if (Math.abs(there - (cell.getValue() >> 1)) >= 1 && !TownBuilder.wet(level, nx, nz)) {
						sheer++;
						if (sides.length() < 500) {
							sides.append(nx).append(',').append(nz).append(" road ").append(cell.getValue() >> 1).append(" beside ").append(there).append(" target ")
								.append(plan.target(nx, nz)).append(TownBuilder.built(level, nx, nz) ? " built:" + TownBuilder.builtBy(level, nx, nz) : "").append("; ");
						}
					}
				}
			}

			DrifterCivilizations.LOGGER.info("[road] debug: {} sides of its columns stand a block or more off the land beside them: {}", sheer, sides);
			int covered = 0;
			StringBuilder what = new StringBuilder();
			for (var cell : paved.entrySet()) {
				int x = BlockPos.getX(cell.getKey());
				int z = BlockPos.getZ(cell.getKey());
				int topY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				int want = (cell.getValue() >> 1) + (cell.getValue() & 1);
				if (topY != want) {
					covered++;
					if (what.length() < 500) {
						what.append(x).append(',').append(z).append(" t").append(cellT.get(cell.getKey())).append(" laid ").append(cell.getValue()).append(" top ").append(topY).append(' ')
							.append(BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, topY, z)).getBlock()).getPath()).append(" at laid ")
							.append(BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, cell.getValue() >> 1, z)).getBlock()).getPath()).append("; ");
					}
				}
			}

			DrifterCivilizations.LOGGER.info("[road] debug: {} of {} topped columns have something else on top: {}", covered, paved.size(), what);
			int spoiled = 0;
			StringBuilder which = new StringBuilder();
			for (var cell : LAID.entrySet()) {
				int x = BlockPos.getX(cell.getKey());
				int z = BlockPos.getZ(cell.getKey());
				if (cells.contains(cell.getKey()) || !TownBuilder.loadedAt(level, x, z)) {
					continue;
				}

				int topY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				int want = (cell.getValue() >> 1) + (cell.getValue() & 1);
				if (topY != want) {
					spoiled++;
					if (which.length() < 600) {
						which.append(x).append(',').append(z).append(" laid ").append(cell.getValue()).append(" top ").append(topY).append(' ')
							.append(BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, topY, z)).getBlock()).getPath()).append(held.contains(cell.getKey()) ? " held" : " not held")
							.append(avoid.contains(cell.getKey()) ? " avoid" : "").append("; ");
					}
				}
			}

			LAID.putAll(paved);
			DrifterCivilizations.LOGGER.info("[road] debug: {} columns of stretches before it changed: {}", spoiled, which);
			int[] land = new int[n];
			for (int t = 0; t < n; t++) {
				land[t] = (int)Math.round(natural[t] * 2);
			}

			DrifterCivilizations.LOGGER.info("[road] debug: from {} to {}, pins {} {}, land {}", at(line, 0).toShortString(), at(line, n - 1).toShortString(), pin0, pin1,
				java.util.Arrays.toString(land));
			DrifterCivilizations.LOGGER.info("[road] debug: {} cells, {} of {} of its middle topped; q {}; not: {}", cells.size(), corePaved, core, java.util.Arrays.toString(q), why);
		}

		if (jumps > 0) {
			DrifterCivilizations.LOGGER.info("[road] a stretch at {}: {} of {} steps along it need a jump", middle.toShortString(), jumps, pairs);
		}
	}

	private static BlockPos at(List<double[]> line, int t) {
		return new BlockPos(Mth.floor(line.get(t)[0]), 0, Mth.floor(line.get(t)[1]));
	}

	/** The height here in half blocks: the ground's, and one more under a slab that was laid on it (a road's, not the land's). */
	private static int level2(ServerLevel level, BlockPos at) {
		if (!TownBuilder.loadedAt(level, at.getX(), at.getZ())) {
			return 2 * TownBuilder.ground(level, at.getX(), at.getZ());
		}

		int y = TownBuilder.paved(level, at.getX(), at.getZ());
		BlockState over = level.getBlockState(new BlockPos(at.getX(), y + 1, at.getZ()));
		boolean slab = over.getBlock() instanceof SlabBlock && !com.dinglebob.drifter.api.DrifterApi.naturalSlab(over);
		return 2 * y + (slab ? 1 : 0);
	}

	/** Open under this (a deck's plank) with water within a few blocks under it. */
	private static boolean overWater(ServerLevel level, BlockPos deck) {
		if (!open(level.getBlockState(deck.below()))) {
			return false;
		}

		for (int k = 1; k <= 4; k++) {
			if (level.getFluidState(deck.below(k)).is(net.minecraft.tags.FluidTags.WATER)) {
				return true;
			}
		}

		return false;
	}

	/** A road or a bridge laid here already (its top the worn mix, sandstone or planks). */
	private static boolean laid(ServerLevel level, BlockPos at) {
		if (!TownBuilder.loadedAt(level, at.getX(), at.getZ())) {
			return false;
		}

		BlockState top = level.getBlockState(new BlockPos(at.getX(), TownBuilder.paved(level, at.getX(), at.getZ()), at.getZ()));
		return top.is(Blocks.PACKED_MUD) || top.is(Blocks.ROOTED_DIRT) || top.is(Blocks.SMOOTH_SANDSTONE) || top.is(BlockTags.PLANKS);
	}

	/** Halfway between built up over dips and cut down through bumps, {@code most} a block at most. */
	private static double[] grade(double[] ground, double most) {
		int n = ground.length;
		double[] up = ground.clone();
		double[] down = ground.clone();
		for (int pass = 0; pass < 2; pass++) {
			for (int t = 1; t < n; t++) {
				up[t] = Math.max(up[t], up[t - 1] - most);
				down[t] = Math.min(down[t], down[t - 1] + most);
			}

			for (int t = n - 2; t >= 0; t--) {
				up[t] = Math.max(up[t], up[t + 1] - most);
				down[t] = Math.min(down[t], down[t + 1] + most);
			}
		}

		double[] h = new double[n];
		for (int t = 0; t < n; t++) {
			h[t] = (up[t] + down[t]) / 2.0;
		}

		return h;
	}

	/** How far out from its line it reaches here: about two (five wide), wandering, its edge ragged. */
	private static double half(int x, int z, long salt) {
		// (Grass comes in from its sides in patches, so its width changes as it goes, three to five: on a dead straight run
		// it was five wide to the block all the way, its edges two ruled lines; the user, 2026-11-13. Its middle three always.)
		return Math.min(2.9, 1.55 + 1.5 * noise(x, z, 9, salt) + (hash(x, z, salt) - 0.5) * 0.5);
	}

	/**
	 * The worn top (the user's picture: "a gradient contained of packed mud, rooted dirt, and coarse dirt"): mostly packed mud
	 * down its middle, more rooted and coarse dirt toward its edges, in patches.
	 */
	static BlockState mix(int x, int z, double off, long salt) {
		double out = Mth.clamp(off / 2.4 + (noise(x, z, 4, salt ^ 0x9E37L) - 0.5) * 0.5, 0, 1);
		double roll = hash(x, z, salt ^ 0x51L);
		double mud = out < 0.4 ? 0.72 : out < 0.75 ? 0.38 : 0.1;
		double rooted = out < 0.4 ? 0.16 : out < 0.75 ? 0.32 : 0.35;
		return (roll < mud ? Blocks.PACKED_MUD : roll < mud + rooted ? Blocks.ROOTED_DIRT : Blocks.COARSE_DIRT).defaultBlockState();
	}

	/** One of the worn mix's blocks (a road's or a hamlet's street's top). */
	static boolean worn(BlockState state) {
		return state.is(Blocks.PACKED_MUD) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.COARSE_DIRT);
	}

	private static final Map<Block, java.util.Optional<BlockState>> SLABS = new HashMap<>();

	/** Terrain Slabs' slab of this block, as one laid (not the land's own: those are cleared like plants), or null. */
	@Nullable
	static BlockState slabOf(BlockState block) {
		return SLABS.computeIfAbsent(block.getBlock(), key -> {
			BlockState slab = GroundPlan.slabFor(block);
			if (slab == null) {
				return java.util.Optional.empty();
			}

			if (slab.getBlock().getStateDefinition().getProperty("generated") instanceof BooleanProperty generated) {
				slab = slab.setValue(generated, false);
			}

			return java.util.Optional.of(slab);
		}).orElse(null);
	}

	/** A smooth wander, 0 to 1, over squares {@code size} wide. */
	private static double noise(int x, int z, int size, long salt) {
		int gx = Math.floorDiv(x, size);
		int gz = Math.floorDiv(z, size);
		double fx = (x - gx * size) / (double)size;
		double fz = (z - gz * size) / (double)size;
		fx = fx * fx * (3 - 2 * fx);
		fz = fz * fz * (3 - 2 * fz);
		double a = Mth.lerp(fx, hash(gx, gz, salt), hash(gx + 1, gz, salt));
		double b = Mth.lerp(fx, hash(gx, gz + 1, salt), hash(gx + 1, gz + 1, salt));
		return Mth.lerp(fz, a, b);
	}

	private static double hash(int x, int z, long salt) {
		long h = Mth.getSeed(x, 0, z) ^ salt;
		h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}

	private static boolean open(BlockState state) {
		return state.isAir() || TownBuilder.plant(state) || !state.getFluidState().isEmpty();
	}

	/**
	 * The ground under a road's top made good (the user: "the terrain under the paths doesnt follow the rule where its 3-5
	 * blocks of dirt then stone"; a road over a cave stood on a shell of dirt, lava under it): earth for three to five
	 * blocks where it's open, rock under that down to solid ground.
	 */
	private static void under(ServerLevel level, BlockPos top, boolean desert, long salt) {
		int earth = 3 + (int)(hash(top.getX(), top.getZ(), salt ^ 0x77L) * 3);
		for (int k = 1; k <= 40; k++) {
			BlockPos pos = top.below(k);
			if (pos.getY() <= level.getMinBuildHeight()) {
				return;
			}

			boolean open = open(level.getBlockState(pos));
			if (!open && k > earth) {
				return;
			}

			if (open) {
				TownBuilder.set(level, pos, (desert ? Blocks.SANDSTONE : k <= earth ? Blocks.DIRT : Blocks.STONE).defaultBlockState());
			}
		}
	}

	/** What grows or lies on the land, a tree's wood and leaves and the land's own slabs, cleared over {@code top}. */
	private static void clearOver(ServerLevel level, BlockPos top, int high) {
		for (int y = 1; y <= high; y++) {
			BlockPos above = top.above(y);
			BlockState state = level.getBlockState(above);
			if (!state.isAir() && state.getFluidState().isEmpty() && (TownBuilder.plant(state) || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
				|| com.dinglebob.drifter.api.DrifterApi.naturalSlab(state) || TownBuilder.litter(state))) {
				TownBuilder.set(level, above, Blocks.AIR.defaultBlockState());
			}
		}
	}

	private static Block wood(String wood, String kind) {
		Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(wood + "_" + kind));
		return block == Blocks.AIR ? BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace("oak_" + kind)) : block;
	}

	/** The wood of a bridge begun already beside these columns (planks there), or null. */
	@Nullable
	private static String woodNear(ServerLevel level, Map<Long, Integer> decks) {
		// (Well round it: a second bridge just past a sandbar, on the same crossing, was another wood.)
		Set<Long> looked = new HashSet<>();
		for (var deck : decks.entrySet()) {
			for (int dx = -14; dx <= 14; dx += 2) {
				for (int dz = -14; dz <= 14; dz += 2) {
					int x = BlockPos.getX(deck.getKey()) + dx;
					int z = BlockPos.getZ(deck.getKey()) + dz;
					if (!looked.add(BlockPos.asLong(x, 0, z)) || !TownBuilder.loadedAt(level, x, z)) {
						continue;
					}

					for (int y = deck.getValue() - 2; y <= deck.getValue() + 2; y++) {
						BlockState state = level.getBlockState(new BlockPos(x, y, z));
						String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
						if (state.is(BlockTags.PLANKS) && id.endsWith("_planks")) {
							return id.substring(0, id.length() - "_planks".length());
						}
					}
				}
			}
		}

		return null;
	}

	/** The bridge: its deck, the beams along its sides with their fences, posts and lanterns, piers under the posts. */
	private static void bridge(ServerLevel level, List<double[]> line, Map<Long, Integer> decks, Set<Long> flat, Map<Long, Integer> paved,
		Map<Long, Integer> cellT, Map<Long, Double> cellD, int[] span, List<int[]> spans, Set<Long> keep, Set<Long> avoid, Set<Long> standing) {
		String wood = woodNear(level, decks);
		if (wood == null) {
			long first = decks.keySet().iterator().next();
			wood = TownBuilder.Style.at(level, new BlockPos(BlockPos.getX(first), decks.get(first) + 1, BlockPos.getZ(first))).main();
		}

		BlockState planks = wood(wood, "planks").defaultBlockState();
		for (var deck : decks.entrySet()) {
			int x = BlockPos.getX(deck.getKey());
			int z = BlockPos.getZ(deck.getKey());
			int y = deck.getValue();
			TownBuilder.set(level, new BlockPos(x, y, z), planks);
			// (Another road's railing where this deck goes: off it. Two bridges side by side had a fence across where they met.)
			for (int up = 1; up <= 3; up++) {
				BlockPos over = new BlockPos(x, y + up, z);
				BlockState state = level.getBlockState(over);
				if (state.getBlock() instanceof FenceBlock || state.is(Blocks.LANTERN) || up <= 2 && (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS))) {
					TownBuilder.set(level, over, Blocks.AIR.defaultBlockState());
				}
			}

			clearOver(level, new BlockPos(x, y, z), 3);
		}

		// Where the deck steps up a block: a slab of its wood at the foot.
		for (var deck : decks.entrySet()) {
			int x = BlockPos.getX(deck.getKey());
			int z = BlockPos.getZ(deck.getKey());
			for (Direction side : Direction.Plane.HORIZONTAL) {
				long next = BlockPos.asLong(x + side.getStepX(), 0, z + side.getStepZ());
				Integer there = decks.containsKey(next) ? decks.get(next) : paved.containsKey(next) ? Integer.valueOf(paved.get(next) >> 1) : null;
				BlockPos over = new BlockPos(x, deck.getValue() + 1, z);
				if (there != null && there == deck.getValue() + 1 && level.getBlockState(over).isAir()) {
					TownBuilder.set(level, over, wood(wood, "slab").defaultBlockState());
					break;
				}
			}
		}

		// The beams: every column beside the deck that isn't the road's own way.
		Map<Long, Integer> beams = new HashMap<>();
		Map<Long, Integer> beamT = new HashMap<>();
		for (var deck : decks.entrySet()) {
			if (flat.contains(deck.getKey())) {
				continue;
			}

			int x = BlockPos.getX(deck.getKey());
			int z = BlockPos.getZ(deck.getKey());
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int nx = x + dx;
					int nz = z + dz;
					long next = BlockPos.asLong(nx, 0, nz);
					Double off = cellD.get(next);
					if (off != null && off <= CORE || decks.containsKey(next) || paved.containsKey(next) || avoid.contains(next) || keep.contains(next)
						|| standing.contains(next) || !TownBuilder.loadedAt(level, nx, nz)) {
						continue;
					}

					// (The bank beside it, standing over the deck: no beam dug into it.)
					if (!TownBuilder.wet(level, nx, nz) && TownBuilder.ground(level, nx, nz) > deck.getValue()) {
						continue;
					}

					if (TownBuilder.built(level, nx, nz)) {
						continue;
					}

					beams.merge(next, deck.getValue(), Math::max);
					beamT.putIfAbsent(next, cellT.containsKey(next) ? cellT.get(next) : cellT.get(deck.getKey()));
				}
			}
		}

		// Posts: one a side every few steps and at each end of the crossing, the beam column nearest the line at that step.
		Map<Long, Long> posts = new HashMap<>();
		Map<Long, Double> postOff = new HashMap<>();
		for (var beam : beams.entrySet()) {
			int t = beamT.get(beam.getKey());
			int[] crossing = span[t] >= 0 ? spans.get(span[t]) : null;
			boolean end = crossing != null && (t == crossing[0] || t == crossing[1]);
			if (!end && (Math.floorMod(t, POSTS) != POSTS / 2 || crossing != null && (t - crossing[0] < 3 || crossing[1] - t < 3))) {
				continue;
			}

			double[] p = line.get(t);
			double ox = BlockPos.getX(beam.getKey()) + 0.5 - p[0];
			double oz = BlockPos.getZ(beam.getKey()) + 0.5 - p[1];
			boolean left = Math.cos(p[2]) * oz - Math.sin(p[2]) * ox > 0;
			long key = t * 2L + (left ? 1 : 0);
			double off = Math.hypot(ox, oz);
			Double had = postOff.get(key);
			if (had == null || off < had) {
				postOff.put(key, off);
				posts.put(key, beam.getKey());
			}
		}

		Set<Long> postAt = new HashSet<>(posts.values());
		List<BlockPos> fences = new ArrayList<>();
		BlockState fence = wood(wood, "fence").defaultBlockState();
		Block log = wood(wood, "log");
		for (var beam : beams.entrySet()) {
			int x = BlockPos.getX(beam.getKey());
			int z = BlockPos.getZ(beam.getKey());
			int y = beam.getValue();
			double heading = line.get(beamT.get(beam.getKey()))[2];
			Direction.Axis along = Math.abs(Math.cos(heading)) >= Math.abs(Math.sin(heading)) ? Direction.Axis.X : Direction.Axis.Z;
			// (On a slant its edge steps: each log lies the way its run goes, a log on end at each corner.)
			boolean runX = beams.containsKey(BlockPos.asLong(x - 1, 0, z)) || beams.containsKey(BlockPos.asLong(x + 1, 0, z));
			boolean runZ = beams.containsKey(BlockPos.asLong(x, 0, z - 1)) || beams.containsKey(BlockPos.asLong(x, 0, z + 1));
			along = runX && runZ ? Direction.Axis.Y : runX ? Direction.Axis.X : runZ ? Direction.Axis.Z : along;
			BlockPos at = new BlockPos(x, y, z);
			boolean post = postAt.contains(beam.getKey());
			TownBuilder.set(level, at, axis(log, post ? Direction.Axis.Y : along));
			BlockState over = level.getBlockState(at.above());
			if (!(over.isAir() || TownBuilder.plant(over) || over.getBlock() instanceof FenceBlock)) {
				continue;
			}

			if (!post) {
				TownBuilder.set(level, at.above(), fence);
				fences.add(at.above());
				continue;
			}

			TownBuilder.set(level, at.above(), axis(log, Direction.Axis.Y));
			if (open(level.getBlockState(at.above(2))) && level.getFluidState(at.above(2)).isEmpty()) {
				TownBuilder.set(level, at.above(2), Blocks.LANTERN.defaultBlockState());
			}

			pier(level, at, wood, log, beams);
		}

		// (Each fence joined to those beside it: put down one by one, a fence only joined the next one put down by it.)
		for (BlockPos pos : fences) {
			TownBuilder.place(level, pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos));
		}
	}

	private static BlockState axis(Block log, Direction.Axis axis) {
		BlockState state = log.defaultBlockState();
		return state.hasProperty(RotatedPillarBlock.AXIS) ? state.setValue(RotatedPillarBlock.AXIS, axis) : state;
	}

	/** A pier under a post: logs down through the water to its bed, a foot of mossy stone, a brace under the beam each side. */
	private static void pier(ServerLevel level, BlockPos post, String wood, Block log, Map<Long, Integer> beams) {
		List<BlockPos> column = new ArrayList<>();
		for (BlockPos pos = post.below(); column.size() < 48 && pos.getY() > level.getMinBuildHeight() && open(level.getBlockState(pos)); pos = pos.below()) {
			column.add(pos);
		}

		if (column.size() < 2 || column.size() >= 48 || level.getFluidState(post.below(2)).isEmpty() && level.getFluidState(post.below(3)).isEmpty()) {
			return;
		}

		for (int i = 0; i < column.size(); i++) {
			boolean foot = i >= column.size() - 2;
			BlockState state = foot ? (Math.floorMod(post.getX() * 7 + post.getZ() * 3 + i, 3) == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.MOSSY_COBBLESTONE)
				.defaultBlockState() : axis(log, Direction.Axis.Y);
			TownBuilder.set(level, column.get(i), state);
		}

		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos brace = post.below().relative(side);
			if (beams.containsKey(BlockPos.asLong(brace.getX(), 0, brace.getZ())) && level.getBlockState(brace).isAir()) {
				BlockState stairs = wood(wood, "stairs").defaultBlockState();
				if (stairs.hasProperty(StairBlock.FACING)) {
					TownBuilder.set(level, brace, stairs.setValue(StairBlock.FACING, side.getOpposite()).setValue(StairBlock.HALF, Half.TOP));
				}
			}
		}
	}
}
