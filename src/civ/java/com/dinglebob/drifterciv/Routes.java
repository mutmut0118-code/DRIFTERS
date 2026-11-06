package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;

/**
 * The way along the roads between towns, for Drifters going far (raids, trade runs, envoys: the main mod's long walks; the
 * user, 2026-11-06: "when drifters set off to another town, their priority path should be following the road", and a war's
 * leader lost between towns). The world plan's roads are a web: each road's points, joined where roads meet and to the
 * towns they end at. The shortest way through it to a place is worked out once for that place (from everywhere at once) and
 * kept; the way from anywhere is then read off it.
 */
final class Routes {
	private Routes() {
	}

	/** Road points of two roads this close are joined (a road's points are 4 apart). */
	private static final int LINK = 5;
	/** Road points this far past a place's reach are joined to it. */
	private static final int EDGE = 24;
	/** A road this far off at most is joined (from where it is, or near where it's going). */
	private static final int JOIN = 64;
	/** The road's way is taken unless it's this many times as long as straight there (and a bit). */
	private static final double DETOUR = 3.0;

	/** The roads as a web: points (x, z) of the roads, then the places (their middles). */
	private record Graph(Object stamp, int roads, int[] xs, int[] zs, int[][] to, float[][] cost, WorldPlan.Place[] places) {
		int size() {
			return this.xs.length;
		}
	}

	/** The way to one place from everywhere: how far, and the next step there. */
	private record Field(float[] far, int[] next) {
	}

	@Nullable
	private static Graph graph;
	private static final Map<Integer, Field> fields = new HashMap<>();

	/** The way along the roads from {@code from} to {@code to}: from itself, the roads' points, then {@code to}; or empty. */
	static List<BlockPos> between(BlockPos from, BlockPos to) {
		if (!WorldPlan.ready) {
			return List.of();
		}

		Graph g = graph();
		if (g == null) {
			return List.of();
		}

		int end = node(g, to, -1);
		if (end < 0) {
			return List.of();
		}

		Field field = fields.computeIfAbsent(end, e -> field(g, e));
		// From: the place it's in, or the road near it that's the shortest way on.
		int start = -1;
		double best = Double.MAX_VALUE;
		int in = placeAt(g, from);
		if (in >= 0 && field.far[in] < Float.MAX_VALUE) {
			start = in;
			best = field.far[in];
		}

		for (int n = 0; n < g.roads; n++) {
			double off = Math.hypot(g.xs[n] - from.getX(), g.zs[n] - from.getZ());
			if (off <= JOIN && field.far[n] < Float.MAX_VALUE && off + field.far[n] < best) {
				best = off + field.far[n];
				start = n;
			}
		}

		if (start < 0 || start == end) {
			return List.of();
		}

		double straight = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
		if (best > straight * DETOUR + 100.0) {
			return List.of();
		}

		List<BlockPos> way = new ArrayList<>();
		way.add(from);
		int n = start;
		for (int guard = 0; n >= 0 && n != end && guard < g.size(); guard++) {
			// (Not the middle of the place it's leaving: straight on to where its road leaves.)
			if (n != start || n < g.roads) {
				way.add(new BlockPos(g.xs[n], 0, g.zs[n]));
			}

			n = field.next[n];
		}

		way.add(to);
		return way;
	}

	/** The road points within {@code within} blocks of {@code at} (x, z), on the server's thread; none while there's no plan. */
	static List<BlockPos> near(BlockPos at, int within) {
		if (!WorldPlan.ready) {
			return List.of();
		}

		Graph g = graph();
		if (g == null) {
			return List.of();
		}

		List<BlockPos> found = new ArrayList<>();
		long reach = (long)within * within;
		for (int n = 0; n < g.roads; n++) {
			long dx = g.xs[n] - at.getX();
			long dz = g.zs[n] - at.getZ();
			if (dx * dx + dz * dz <= reach) {
				found.add(new BlockPos(g.xs[n], 0, g.zs[n]));
			}
		}

		return found;
	}

	/** The place {@code pos} is in (within its reach), or a road point near it; or -1. */
	private static int node(Graph g, BlockPos pos, int fallback) {
		int place = placeAt(g, pos);
		if (place >= 0) {
			return place;
		}

		int nearest = fallback;
		double best = JOIN;
		for (int n = 0; n < g.roads; n++) {
			double d = Math.hypot(g.xs[n] - pos.getX(), g.zs[n] - pos.getZ());
			if (d < best) {
				best = d;
				nearest = n;
			}
		}

		return nearest;
	}

	/** The node of the place {@code pos} is in (the nearest whose reach it's within), or -1. */
	private static int placeAt(Graph g, BlockPos pos) {
		int found = -1;
		double best = Double.MAX_VALUE;
		for (int i = 0; i < g.places.length; i++) {
			WorldPlan.Place place = g.places[i];
			double d = Math.hypot(place.x - pos.getX(), place.z - pos.getZ());
			if (d <= place.reach() && d < best) {
				best = d;
				found = g.roads + i;
			}
		}

		return found;
	}

	/** The shortest ways to {@code end} from every node (Dijkstra, out from it). */
	private static Field field(Graph g, int end) {
		float[] far = new float[g.size()];
		int[] next = new int[g.size()];
		Arrays.fill(far, Float.MAX_VALUE);
		Arrays.fill(next, -1);
		far[end] = 0;
		PriorityQueue<long[]> open = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
		open.add(new long[] {0, end});
		while (!open.isEmpty()) {
			long[] top = open.poll();
			int n = (int)top[1];
			if (top[0] > (long)(far[n] * 16)) {
				continue;
			}

			for (int k = 0; k < g.to[n].length; k++) {
				int m = g.to[n][k];
				float d = far[n] + g.cost[n][k];
				if (d < far[m]) {
					far[m] = d;
					next[m] = n;
					open.add(new long[] {(long)(d * 16), m});
				}
			}
		}

		return new Field(far, next);
	}

	/** The web of the plan's roads as it is now (made again when the plan changes). */
	@Nullable
	private static Graph graph() {
		List<WorldPlan.Highway> highways = WorldPlan.highways;
		List<WorldPlan.Place> placesNow = WorldPlan.places;
		if (highways.isEmpty() || placesNow.isEmpty()) {
			return null;
		}

		Object stamp = List.of(highways.size(), placesNow.size(), System.identityHashCode(highways.get(0)), System.identityHashCode(placesNow.get(0)));
		if (graph != null && graph.stamp.equals(stamp)) {
			return graph;
		}

		fields.clear();
		WorldPlan.Place[] places = placesNow.toArray(new WorldPlan.Place[0]);
		int roads = 0;
		for (WorldPlan.Highway highway : highways) {
			roads += highway.points.size();
		}

		int size = roads + places.length;
		int[] xs = new int[size];
		int[] zs = new int[size];
		int[] road = new int[roads];
		List<List<int[]>> edges = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			edges.add(new ArrayList<>(2));
		}

		int n = 0;
		for (int h = 0; h < highways.size(); h++) {
			WorldPlan.Highway highway = highways.get(h);
			int first = n;
			for (int[] point : highway.points) {
				xs[n] = point[0];
				zs[n] = point[1];
				road[n] = h;
				if (n > first) {
					link(edges, xs, zs, n - 1, n);
				}

				n++;
			}

		}

		// Each place joined to the road points by its edge (the roads between towns begin there, past its own; a road's ends
		// can't be trusted to say: a piece shared with another road out of the same town is neither road's end).
		for (int i = 0; i < places.length; i++) {
			xs[roads + i] = places[i].x;
			zs[roads + i] = places[i].z;
			double edge = places[i].reach() + EDGE;
			for (int r = 0; r < roads; r++) {
				if (Math.abs(xs[r] - places[i].x) <= edge && Math.abs(zs[r] - places[i].z) <= edge
					&& Math.hypot(xs[r] - places[i].x, zs[r] - places[i].z) <= edge) {
					link(edges, xs, zs, r, roads + i);
				}
			}
		}

		// Where roads meet (a road's piece joins another where it runs on with it, or crosses it).
		Map<Long, List<Integer>> cells = new HashMap<>();
		for (int i = 0; i < roads; i++) {
			cells.computeIfAbsent(cell(xs[i] >> 3, zs[i] >> 3), c -> new ArrayList<>()).add(i);
		}

		for (int i = 0; i < roads; i++) {
			int cx = xs[i] >> 3;
			int cz = zs[i] >> 3;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					List<Integer> there = cells.get(cell(cx + dx, cz + dz));
					if (there == null) {
						continue;
					}

					for (int j : there) {
						if (j > i && road[j] != road[i] && (xs[i] - xs[j]) * (xs[i] - xs[j]) + (zs[i] - zs[j]) * (zs[i] - zs[j]) <= LINK * LINK) {
							link(edges, xs, zs, i, j);
						}
					}
				}
			}
		}

		int[][] to = new int[size][];
		float[][] cost = new float[size][];
		for (int i = 0; i < size; i++) {
			List<int[]> out = edges.get(i);
			to[i] = new int[out.size()];
			cost[i] = new float[out.size()];
			for (int k = 0; k < out.size(); k++) {
				to[i][k] = out.get(k)[0];
				cost[i][k] = Float.intBitsToFloat(out.get(k)[1]);
			}
		}

		graph = new Graph(stamp, roads, xs, zs, to, cost, places);
		return graph;
	}

	private static void link(List<List<int[]>> edges, int[] xs, int[] zs, int a, int b) {
		int d = Float.floatToIntBits((float)Math.hypot(xs[a] - xs[b], zs[a] - zs[b]));
		edges.get(a).add(new int[] {b, d});
		edges.get(b).add(new int[] {a, d});
	}

	private static long cell(int x, int z) {
		return ((long)x << 32) ^ (z & 0xFFFFFFFFL);
	}

	/** For /drifterciv: how the way from here to there goes (its length along the roads, and how many points). */
	static String describe(BlockPos from, BlockPos to) {
		List<BlockPos> way = between(from, to);
		if (way.isEmpty()) {
			Graph g = graph();
			if (g == null) {
				return "no road there (straight on): no roads planned";
			}

			int end = node(g, to, -1);
			int in = placeAt(g, from);
			String why = "end node " + end + (end >= g.roads ? " (" + g.places[end - g.roads].name + ", " + g.to[end].length + " roads in)" : "")
				+ ", start place " + (in >= 0 ? g.places[in - g.roads].name + " (" + g.to[in].length + " roads in)" : "none");
			if (end >= 0) {
				Field field = fields.computeIfAbsent(end, e -> field(g, e));
				int reached = 0;
				for (float f : field.far) {
					if (f < Float.MAX_VALUE) {
						reached++;
					}
				}

				why += ", " + reached + " of " + g.size() + " nodes reach it" + (in >= 0 ? ", from here " + field.far[in] : "");
			}

			if (end >= g.roads) {
				WorldPlan.Place place = g.places[end - g.roads];
				double near = Double.MAX_VALUE;
				for (int r = 0; r < g.roads; r++) {
					near = Math.min(near, Math.hypot(g.xs[r] - place.x, g.zs[r] - place.z));
				}

				why += ", nearest road point " + Math.round(near) + " off; its roads:";
				for (WorldPlan.Highway highway : WorldPlan.highways) {
					if (highway.from.equals(place.id) || highway.to.equals(place.id)) {
						int[] a = highway.points.get(0);
						int[] b = highway.points.get(highway.points.size() - 1);
						why += " [" + a[0] + "," + a[1] + " to " + b[0] + "," + b[1] + ", " + highway.fromEnd + "/" + highway.toEnd + "]";
					}
				}
			}

			return "no road there (straight on): " + why;
		}

		double length = 0;
		int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		for (int i = 0; i < way.size(); i++) {
			if (i > 0) {
				length += Math.hypot(way.get(i).getX() - way.get(i - 1).getX(), way.get(i).getZ() - way.get(i - 1).getZ());
			}

			x0 = Math.min(x0, way.get(i).getX());
			z0 = Math.min(z0, way.get(i).getZ());
			x1 = Math.max(x1, way.get(i).getX());
			z1 = Math.max(z1, way.get(i).getZ());
		}

		return "along the roads, " + Math.round(length) + " blocks (" + Math.round(Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ()))
			+ " straight), " + way.size() + " points, within " + x0 + ", " + z0 + " to " + x1 + ", " + z1;
	}
}
