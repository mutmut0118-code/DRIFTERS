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

	/** Road points of two roads this close are joined (a road's points are 4 apart; the plan shares a way within 10). */
	private static final int LINK = 12;
	/** A road's loose end (into another road) is joined to the nearest other road this close. */
	private static final double LOOSE_END = 40.0;
	/** Road points this far past a place's reach are joined to it. */
	private static final int EDGE = 24;
	/** A road this far off at most is joined (from where it is, or near where it's going). */
	private static final int JOIN = 64;
	/** Off the roads, the road it goes back to may be this far off. */
	private static final int FAR_JOIN = 192;
	/** The road's way is taken unless it's this many times as long as straight there (and a bit). */
	private static final double DETOUR = 3.0;

	/** The roads as a web: points (x, z) of the roads, then the places (their middles); the boat crossings between points. */
	private record Graph(Object stamp, int roads, int[] xs, int[] zs, int[][] to, float[][] cost, WorldPlan.Place[] places, java.util.Set<Long> boats) {
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

		// Off the roads (across country, over a river): back to the road that's the best way on, further off, the way to it
		// counted double (off the road a war's leader went straight on across country for good, never back on it; a test).
		if (start < 0) {
			for (int n = 0; n < g.roads; n++) {
				double off = Math.hypot(g.xs[n] - from.getX(), g.zs[n] - from.getZ());
				if (off <= FAR_JOIN && field.far[n] < Float.MAX_VALUE && off * 2.0 + field.far[n] < best) {
					best = off * 2.0 + field.far[n];
					start = n;
				}
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
			// (A point the way goes on from by boat: marked, for the walker, Legs.FERRY.)
			int on = field.next[n];
			if (n != start || n < g.roads) {
				way.add(new BlockPos(g.xs[n], on >= 0 && g.boats.contains(pair(n, on)) ? com.dinglebob.drifter.nav.Legs.FERRY : 0, g.zs[n]));
			}

			n = on;
		}

		way.add(to);
		return streets(way);
	}

	/**
	 * The way through the towns at its ends along their own streets: out of the one it's leaving to where the road between
	 * towns meets them, and from where the road reaches the one it's going to, on to there (straight across a city to where
	 * its road began, a war's leader went through berry bushes and over a river by boat, its band left on the far bank; a
	 * test of it, and the user, 2026-11-16: "make sure its on a direct continuous path"). As it is where a town has no
	 * streets laid, or they don't join up.
	 */
	private static List<BlockPos> streets(List<BlockPos> way) {
		if (way.size() < 3) {
			return way;
		}

		List<BlockPos> out = new ArrayList<>(way);
		Town end = townAt(out.get(out.size() - 1));
		if (end != null) {
			List<BlockPos> in = streetWay(end, out.get(out.size() - 2), out.get(out.size() - 1));
			if (!in.isEmpty()) {
				out.addAll(out.size() - 1, in);
			}
		}

		Town start = townAt(out.get(0));
		if (start != null) {
			List<BlockPos> leaving = streetWay(start, out.get(0), out.get(1));
			startStreets = leaving.size();
			startWhy = streetsWhy;
			if (!leaving.isEmpty()) {
				out.addAll(1, leaving);
			}
		} else {
			startStreets = 0;
			startWhy = "not in a town with streets";
		}

		return out;
	}

	/** For /drifterciv route: why the last way through a town's streets wasn't found, or empty. */
	private static String streetsWhy = "";
	private static int startStreets;
	private static String startWhy = "";

	/** A street this far from where it is at most is gone to; a road point this far from the streets at most meets them. */
	private static final double ON_STREET = 24.0;
	// (As far as a road's built end runs on straight to meet a town's streets: Highways.MEET_WITHIN.)
	private static final double STREET_MEETS = 128.0;
	/** A point every this many blocks of street. */
	private static final int STREET_STEP = 3;

	/** The made town {@code pos} is in (within its land), or null. */
	@Nullable
	private static Town townAt(BlockPos pos) {
		Town town = Towns.nearest("minecraft:overworld", pos);
		return town == null || town.roadColumns.length == 0 || !town.center.closerThan(new BlockPos(pos.getX(), town.center.getY(), pos.getZ()), town.land() + 16)
			? null : town;
	}

	/** Each town's streets as a set of columns (x, z), and the way to a column from every other (kept per town and column). */
	private static final Map<java.util.UUID, java.util.Set<Long>> streetSets = new HashMap<>();
	private static final Map<Long, Map<Long, Long>> streetFields = new java.util.LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Map<Long, Long>> eldest) {
			return this.size() > 64;
		}
	};

	/** The street points from by {@code a} to by {@code b} (in order, every few blocks, neither end), or none. */
	private static List<BlockPos> streetWay(Town town, BlockPos a, BlockPos b) {
		java.util.Set<Long> set = streetSets.get(town.id);
		if (set == null || set.size() != town.roadColumns.length) {
			set = new java.util.HashSet<>(town.roadColumns.length * 2);
			for (long column : town.roadColumns) {
				set.add(cell(BlockPos.getX(column), BlockPos.getZ(column)));
			}

			streetSets.put(town.id, set);
			streetFields.clear();
		}

		long from = nearestStreet(town, a, ON_STREET);
		long goal = nearestStreet(town, b, STREET_MEETS);
		if (from == Long.MIN_VALUE || goal == Long.MIN_VALUE || from == goal) {
			streetsWhy = town.name + ": " + (from == Long.MIN_VALUE ? "no street near " + a.toShortString() : goal == Long.MIN_VALUE ? "no street near the road at "
				+ b.toShortString() : "already there");
			return List.of();
		}

		// (Out from where it's going, along the streets: each column's next step there.)
		final java.util.Set<Long> streets = set;
		long key = town.id.getMostSignificantBits() * 31 + town.id.getLeastSignificantBits() * 17 + goal;
		Map<Long, Long> next = streetFields.computeIfAbsent(key, k -> {
			Map<Long, Long> field = new HashMap<>();
			java.util.ArrayDeque<Long> open = new java.util.ArrayDeque<>();
			field.put(goal, goal);
			open.add(goal);
			while (!open.isEmpty()) {
				long c = open.poll();
				int x = (int)(c >> 32);
				int z = (int)c;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						long d = cell(x + dx, z + dz);
						if ((dx != 0 || dz != 0) && streets.contains(d) && !field.containsKey(d)) {
							field.put(d, c);
							open.add(d);
						}
					}
				}
			}

			return field;
		});

		if (!next.containsKey(from)) {
			streetsWhy = town.name + ": its streets from " + a.toShortString() + " don't join up with those by the road at " + b.toShortString() + " (" + next.size()
				+ " of " + streets.size() + " reached)";
			return List.of();
		}

		streetsWhy = "";

		List<BlockPos> points = new ArrayList<>();
		long c = from;
		for (int i = 0; c != goal && i < 100000; i++) {
			if (i % STREET_STEP == 0) {
				points.add(new BlockPos((int)(c >> 32), 0, (int)c));
			}

			c = next.get(c);
		}

		points.add(new BlockPos((int)(goal >> 32), 0, (int)goal));
		return points;
	}

	/** The town's street column nearest {@code pos} within {@code within}, as a cell, or Long.MIN_VALUE. */
	private static long nearestStreet(Town town, BlockPos pos, double within) {
		long best = Long.MIN_VALUE;
		double far = within;
		for (long column : town.roadColumns) {
			double d = Math.hypot(BlockPos.getX(column) - pos.getX(), BlockPos.getZ(column) - pos.getZ());
			if (d <= far) {
				far = d;
				best = cell(BlockPos.getX(column), BlockPos.getZ(column));
			}
		}

		return best;
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

		Object stamp = List.of(highways.size(), placesNow.size(), System.identityHashCode(highways.get(0)), System.identityHashCode(placesNow.get(0)),
			WorldPlan.ferries.size());
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

		// Each road's town ends joined to that town, however far out from its middle they are: the plan says where its road
		// begins (a city's roads began past what its reach above covers, and weren't joined to it: a war's march from
		// Redhaven to Ironhollow, 909 blocks apart, came out at 4,848 along the roads and went straight across country instead;
		// the user, 2026-11-16: "i just noticed they dont use the road").
		Map<java.util.UUID, Integer> placeNode = new HashMap<>();
		for (int i = 0; i < places.length; i++) {
			placeNode.put(places[i].id, roads + i);
		}

		int[] firstOf = new int[highways.size()];
		int[] lastOf = new int[highways.size()];
		n = 0;
		for (int h = 0; h < highways.size(); h++) {
			WorldPlan.Highway highway = highways.get(h);
			firstOf[h] = n;
			lastOf[h] = n + highway.points.size() - 1;
			n += highway.points.size();
			Integer from = highway.fromEnd ? placeNode.get(highway.from) : null;
			Integer to = highway.toEnd ? placeNode.get(highway.to) : null;
			if (from != null) {
				link(edges, xs, zs, firstOf[h], from);
			}

			if (to != null) {
				link(edges, xs, zs, lastOf[h], to);
			}
		}

		// Where roads meet: a road's piece joins another where it runs on with it, or crosses it. (Within the plan's own
		// distance for a shared way: a road that runs on with one already planned was cut off up to that far from it, more
		// where a short piece between was left out; at a few blocks, the pieces of the way weren't joined up.)
		Map<Long, List<Integer>> cells = new HashMap<>();
		for (int i = 0; i < roads; i++) {
			cells.computeIfAbsent(cell(xs[i] >> 4, zs[i] >> 4), c -> new ArrayList<>()).add(i);
		}

		for (int i = 0; i < roads; i++) {
			int cx = xs[i] >> 4;
			int cz = zs[i] >> 4;
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

		// A road's end that's neither at a town nor at a dock runs into another road: joined to the nearest point of another
		// road near it, if nothing joined it yet.
		for (int h = 0; h < highways.size(); h++) {
			WorldPlan.Highway highway = highways.get(h);
			for (int end : new int[] {firstOf[h], lastOf[h]}) {
				boolean town = end == firstOf[h] ? highway.fromEnd || highway.fromDock : highway.toEnd || highway.toDock;
				boolean joined = false;
				for (int[] e : edges.get(end)) {
					joined |= e[0] >= roads || road[e[0]] != h;
				}

				if (town || joined) {
					continue;
				}

				int best = -1;
				double far = LOOSE_END;
				for (int r = 0; r < roads; r++) {
					if (road[r] != h && Math.abs(xs[r] - xs[end]) <= far && Math.abs(zs[r] - zs[end]) <= far) {
						double d = Math.hypot(xs[r] - xs[end], zs[r] - zs[end]);
						if (d < far) {
							far = d;
							best = r;
						}
					}
				}

				if (best >= 0) {
					link(edges, xs, zs, end, best);
				}
			}
		}

		// Boat crossings: the road point by each dock to the one by the other, the way across dearer than walking it.
		java.util.Set<Long> boats = new java.util.HashSet<>();
		for (WorldPlan.Ferry ferry : WorldPlan.ferries) {
			int a = nearestRoad(xs, zs, roads, ferry.x1(), ferry.z1());
			int b = nearestRoad(xs, zs, roads, ferry.x2(), ferry.z2());
			if (a >= 0 && b >= 0 && a != b) {
				int d = Float.floatToIntBits((float)(Math.hypot(xs[a] - xs[b], zs[a] - zs[b]) * BOAT));
				edges.get(a).add(new int[] {b, d});
				edges.get(b).add(new int[] {a, d});
				boats.add(pair(a, b));
				boats.add(pair(b, a));
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

		graph = new Graph(stamp, roads, xs, zs, to, cost, places, boats);
		return graph;
	}

	/** Crossing by boat counts this many times its length. */
	private static final double BOAT = 1.5;

	private static long pair(int a, int b) {
		return ((long)a << 32) | (b & 0xFFFFFFFFL);
	}

	/** The road point nearest x, z (within 16), or -1. */
	private static int nearestRoad(int[] xs, int[] zs, int roads, int x, int z) {
		int best = -1;
		double far = 16;
		for (int n = 0; n < roads; n++) {
			double d = Math.hypot(xs[n] - x, zs[n] - z);
			if (d <= far) {
				far = d;
				best = n;
			}
		}

		return best;
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

		return "out along " + startStreets + " street points" + (startWhy.isEmpty() ? "" : " (" + startWhy + ")") + ", along the roads, " + Math.round(length) + " blocks (" + Math.round(Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ()))
			+ " straight), " + way.size() + " points, within " + x0 + ", " + z0 + " to " + x1 + ", " + z1;
	}
}
