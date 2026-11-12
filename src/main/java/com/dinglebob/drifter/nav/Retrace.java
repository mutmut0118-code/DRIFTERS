package com.dinglebob.drifter.nav;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Going back the way it came (the user's town, 2026-11-12): down a mine, with home far above, the walk back along its own
 * tunnel and up its own stairs often leaves the box one path search looks in, so no single search can find it, and every
 * route home dug a shaft straight up beside the stairs. Its trail (the cells its feet walked, {@code DrifterMemory#walked},
 * the last few thousand, saved) is that way: cells next to each other on it are a walk it has made. This finds the cheapest
 * spot on it to head for (the walk back there plus digging straight on from it) and the stops on the way, each a short
 * walk inside one search's reach. Taken only when that costs well under digging the straight line from here; otherwise it
 * digs, as before.
 */
public final class Retrace {
	/** Seconds per block of its trail walked back (walking pace with a stair's jump now and then, rounded up). */
	public static final double WALK_BACK = PathSearch.WALK * 2.0;
	/** The walk back must cost under this share of digging straight there, or it's "much longer" and it digs. */
	public static final double WORTH = 0.8;
	/** Its feet must be this near a cell of its trail for the trail to start here. */
	private static final double ON_TRAIL = 2.5;
	/** Most blocks across (each axis) one stop is from the last, inside one search's reach. */
	private static final int MAX_LEG = 40;
	/** A way back must take it at least this far, or it isn't worth the stop. */
	private static final double MIN_BACK = 4.0;

	private Retrace() {
	}

	/**
	 * The stops back along its trail toward the goal, nearest first, or null if digging straight is about as cheap (or it
	 * isn't on its trail). {@code range} is one search's reach.
	 */
	@Nullable
	public static ArrayDeque<BlockPos> plan(LongOpenHashSet trail, BlockPos here, Vec3 goal, double radius, int range) {
		// Where it stands on its trail: the nearest cell of it.
		long start = Long.MIN_VALUE;
		double nearest = ON_TRAIL;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					BlockPos at = here.offset(dx, dy, dz);
					double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (d <= nearest && trail.contains(at.asLong())) {
						nearest = d;
						start = at.asLong();
					}
				}
			}
		}

		if (start == Long.MIN_VALUE) {
			return null;
		}

		// First the quick look: no cell of its trail could do it even walked to in a straight line (the usual case while it
		// digs on toward something new): no search of the trail (it runs on the server thread).
		double dig = digCost(here, goal, radius);
		boolean any = false;
		for (long key : trail) {
			BlockPos at = BlockPos.of(key);
			if (Math.sqrt(at.distSqr(here)) * WALK_BACK + digCost(at, goal, radius) < dig * WORTH) {
				any = true;
				break;
			}
		}

		if (!any) {
			return null;
		}

		// Walking distances along the trail from there (cells touching, a step up or down included), and the cheapest spot.
		Long2DoubleOpenHashMap walked = new Long2DoubleOpenHashMap();
		walked.defaultReturnValue(Double.MAX_VALUE);
		Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
		PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		walked.put(start, 0.0);
		open.add(new double[] {0.0, Double.longBitsToDouble(start)});
		long best = Long.MIN_VALUE;
		double bestCost = dig * WORTH;
		while (!open.isEmpty()) {
			double[] top = open.poll();
			long key = Double.doubleToRawLongBits(top[1]);
			if (top[0] > walked.get(key)) {
				continue;
			}

			BlockPos at = BlockPos.of(key);
			double cost = top[0] * WALK_BACK + digCost(at, goal, radius);
			if (top[0] >= MIN_BACK && cost < bestCost) {
				bestCost = cost;
				best = key;
			}

			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}

						long next = BlockPos.asLong(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
						if (!trail.contains(next)) {
							continue;
						}

						double d = top[0] + Math.sqrt(dx * dx + dy * dy + dz * dz);
						if (d < walked.get(next)) {
							walked.put(next, d);
							parent.put(next, key);
							open.add(new double[] {d, Double.longBitsToDouble(next)});
						}
					}
				}
			}
		}

		if (best == Long.MIN_VALUE) {
			return null;
		}

		// The cells from here to there, then the stops: from each, as far on as the way stays within one search's reach.
		List<BlockPos> way = new ArrayList<>();
		for (long key = best; key != start; key = parent.get(key)) {
			way.add(BlockPos.of(key));
		}

		java.util.Collections.reverse(way);
		int leg = Math.max(8, Math.min(MAX_LEG, range - 8));
		ArrayDeque<BlockPos> stops = new ArrayDeque<>();
		BlockPos anchor = here;
		int i = 0;
		while (i < way.size()) {
			int next = i;
			while (next + 1 < way.size() && within(way.get(next + 1), anchor, leg)) {
				next++;
			}

			anchor = way.get(next);
			stops.add(anchor);
			i = next + 1;
		}

		return stops;
	}

	/** Digging the straight line from there to the goal, roughly (see {@link PathSearch#DIG_WAY_PER_BLOCK}). */
	private static double digCost(BlockPos from, Vec3 goal, double radius) {
		return Math.max(0.0, Vec3.atBottomCenterOf(from).distanceTo(goal) - radius) * PathSearch.DIG_WAY_PER_BLOCK;
	}

	private static boolean within(BlockPos a, BlockPos b, int leg) {
		return Math.abs(a.getX() - b.getX()) <= leg && Math.abs(a.getY() - b.getY()) <= leg && Math.abs(a.getZ() - b.getZ()) <= leg;
	}
}
