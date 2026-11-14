package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/**
 * The roads between the civilized world's towns (milestone 23, docs/LIVED_WORLD.md W5; the user: "all the roads are
 * connected ... long roads connected to different nations across the world"): routed over the land with the world plan
 * ({@link WorldPlan}), built a stretch at a time (about 48 blocks) as a player comes within twelve chunks of it, its land
 * loaded first, as towns are. A worn dirt path three wide, built up a block a step over dips, with a slab or stairs where it
 * climbs and a railed bridge on piers over water; the stretch at a town's end waits for the town and runs on to meet its
 * roads, a lamp with a distance sign there saying where it goes and how far.
 */
final class Highways {
	private Highways() {
	}

	/** Points a stretch (four blocks apart: 48 blocks). */
	static final int PIECE = 12;
	private static final int LOAD_RADIUS = 3;
	/** At most this many stretches' land loading at once (while the world's built before it's played: more). */
	private static final int AT_ONCE = 3;
	private static final int BUILDING_AT_ONCE = 6;
	/** How far a stretch at a town's end may reach to meet its roads. */
	private static final int MEET_WITHIN = 64;
	private static final TicketType<ChunkPos> TICKET = TicketType.create("drifter_civ_road", Comparator.comparingLong(ChunkPos::toLong));
	/**
	 * The stretches whose land is loading, by "road:stretch": the chunk its land is asked for round, the chunks out to the
	 * town's roads it runs on to, and the tick it was asked for.
	 */
	private record Loading(ChunkPos chunk, List<ChunkPos> far, int since) {
	}

	private static final Map<String, Loading> loading = new HashMap<>();
	/**
	 * A stretch's land not loaded in this long is let go (asked for again later), so it doesn't hold a place in the
	 * {@link #AT_ONCE} the others wait for.
	 */
	private static final int LOAD_PATIENCE = 1200;
	/** Debug: build every stretch near this (forced, player or not). */
	@Nullable
	static BlockPos forcedNear;

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Highways::tick);
		// (A world left: the stretches it was loading are forgotten, not waited for in the next.)
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			loading.clear();
			forcedNear = null;
		});
	}

	private static void tick(MinecraftServer server) {
		if (!WorldPlan.ready || server.getTickCount() % (WorldBuild.building ? 1 : 10) != 0) {
			return;
		}

		ServerLevel level = server.overworld();
		if (level.players().isEmpty() && forcedNear == null && !WorldBuild.building) {
			return;
		}

		List<WorldPlan.Highway> roads = WorldPlan.highways;
		// The stretches near enough, nearest first.
		List<int[]> wanted = new ArrayList<>();
		for (int h = 0; h < roads.size(); h++) {
			WorldPlan.Highway road = roads.get(h);
			for (int p = 0; p < road.built.length; p++) {
				int a = p * PIECE;
				int[] mid = road.points.get((a + Math.min(road.points.size() - 1, a + PIECE)) / 2);
				if (!road.built[p] && near(level, mid)) {
					wanted.add(new int[] {h, p, (int)WorldBuild.distance(level, mid[0], mid[1])});
				}
			}
		}

		wanted.sort(Comparator.comparingInt(one -> one[2]));
		// (Stretches no longer near enough, built, or waiting too long, let go with their land: kept, they held their places
		// in the few loading at once for good, and no other road was built all game; the user, 2026-11-14: "some roads
		// between towns are not spawning?". The loading screen ended with six of them, far out, a player flying or
		// teleporting left them behind.)
		java.util.Set<String> still = new java.util.HashSet<>();
		for (int[] one : wanted) {
			still.add(one[0] + ":" + one[1]);
		}

		for (String key : new ArrayList<>(loading.keySet())) {
			if (!still.contains(key) || server.getTickCount() - loading.get(key).since() > LOAD_PATIENCE) {
				release(level, key);
			}
		}

		for (int[] one : wanted) {
			int h = one[0];
			int p = one[1];
			WorldPlan.Highway road = roads.get(h);
			{
				int a = p * PIECE;
				int b = Math.min(road.points.size() - 1, a + PIECE);
				int[] mid = road.points.get((a + b) / 2);

				// (The stretch at a town's end runs on to meet its roads: the town first.)
				boolean first = p == 0 && road.fromEnd;
				boolean last = p == road.built.length - 1 && road.toEnd;
				if (first && !made(road.from) || last && !made(road.to)) {
					continue;
				}

				String key = h + ":" + p;
				ChunkPos chunk = new ChunkPos(mid[0] >> 4, mid[1] >> 4);
				// (At a town's end it runs on as far as the town's nearest road, up to 64 blocks: that land loaded too. Read
				// while it was still loading, the server stopped for 30 s, found on the user's Tectonic.)
				List<int[]> ends = new ArrayList<>();
				if (first) {
					long meet = meetPoint(road.from, road.points.get(a));
					if (meet != Long.MIN_VALUE) {
						ends.add(new int[] {road.points.get(a)[0], road.points.get(a)[1], BlockPos.getX(meet), BlockPos.getZ(meet)});
					}
				}

				if (last) {
					long meet = meetPoint(road.to, road.points.get(b));
					if (meet != Long.MIN_VALUE) {
						ends.add(new int[] {road.points.get(b)[0], road.points.get(b)[1], BlockPos.getX(meet), BlockPos.getZ(meet)});
					}
				}

				if (!loading.containsKey(key)) {
					// (A few stretches' land asked for at a time, not every road ahead's at once.)
					if (loading.size() >= (WorldBuild.building ? BUILDING_AT_ONCE : AT_ONCE)) {
						continue;
					}

					level.getChunkSource().addRegionTicket(TICKET, chunk, LOAD_RADIUS, chunk);
					List<ChunkPos> fars = new ArrayList<>();
					for (int[] end : ends) {
						ChunkPos far = new ChunkPos(end[2] >> 4, end[3] >> 4);
						level.getChunkSource().addRegionTicket(TICKET, far, LOAD_RADIUS, far);
						fars.add(far);
					}

					loading.put(key, new Loading(chunk, fars, server.getTickCount()));
					continue;
				}

				if (!loaded(level, road, a, b) || ends.stream().anyMatch(end -> !loadedAlong(level, end))) {
					continue;
				}

				long started = System.nanoTime();
				build(level, road, p, a, b);
				long ms = (System.nanoTime() - started) / 1_000_000L;
				if (ms > 50) {
					DrifterCivilizations.LOGGER.info("[road] a stretch at {}, {} took {} ms", mid[0], mid[1], ms);
				}

				road.built[p] = true;
				release(level, key);
				Towns.save(server);
				// (One stretch a go: a few milliseconds each.)
				return;
			}
		}
	}

	/** A stretch's land let go: its tickets taken back, its place in the loading freed. */
	private static void release(ServerLevel level, String key) {
		Loading was = loading.remove(key);
		if (was == null) {
			return;
		}

		level.getChunkSource().removeRegionTicket(TICKET, was.chunk(), LOAD_RADIUS, was.chunk());
		for (ChunkPos far : was.far()) {
			level.getChunkSource().removeRegionTicket(TICKET, far, LOAD_RADIUS, far);
		}
	}

	/**
	 * The stretches within this of a player (or spawn) to build now (at a town's end, once the town's made): how many are
	 * built, and how many there are.
	 */
	static int[] nearStretches(ServerLevel level, int within) {
		int built = 0;
		int all = 0;
		for (WorldPlan.Highway road : WorldPlan.highways) {
			for (int p = 0; p < road.built.length; p++) {
				int a = p * PIECE;
				int[] mid = road.points.get((a + Math.min(road.points.size() - 1, a + PIECE)) / 2);
				boolean first = p == 0 && road.fromEnd;
				boolean last = p == road.built.length - 1 && road.toEnd;
				if (WorldBuild.distance(level, mid[0], mid[1]) >= within || first && !made(road.from) || last && !made(road.to)) {
					continue;
				}

				all++;
				built += road.built[p] ? 1 : 0;
			}
		}

		return new int[] {built, all};
	}

	/** All the plan's stretches of road, and how many are built. */
	static int stretches() {
		return WorldPlan.highways.stream().mapToInt(road -> road.built.length).sum();
	}

	static int built() {
		int built = 0;
		for (WorldPlan.Highway road : WorldPlan.highways) {
			for (boolean one : road.built) {
				built += one ? 1 : 0;
			}
		}

		return built;
	}

	/** Near enough to build: well out past what a player can see ({@link WorldBuild#ahead}; it was twelve chunks, in plain view). */
	private static boolean near(ServerLevel level, int[] at) {
		if (forcedNear != null && Math.hypot(forcedNear.getX() - at[0], forcedNear.getZ() - at[1]) < 1000) {
			return true;
		}

		return WorldBuild.distance(level, at[0], at[1]) < WorldBuild.ahead(level.getServer());
	}

	private static boolean made(java.util.UUID id) {
		WorldPlan.Place place = WorldPlan.place(id);
		return place == null || place.made;
	}

	private static boolean loaded(ServerLevel level, WorldPlan.Highway road, int a, int b) {
		for (int i = Math.max(0, a - 1); i <= Math.min(road.points.size() - 1, b + 1); i++) {
			int[] at = road.points.get(i);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (level.getChunkSource().getChunkNow((at[0] >> 4) + dx, (at[1] >> 4) + dz) == null) {
						return false;
					}
				}
			}
		}

		return true;
	}

	/** One stretch built: its points, joined at a town's end to the nearest of the town's roads, a lamp with a distance sign there. */
	private static void build(ServerLevel level, WorldPlan.Highway road, int piece, int a, int b) {
		List<double[]> corners = new ArrayList<>();
		List<double[]> smooth = smooth(road);
		for (int i = a; i <= b; i++) {
			corners.add(smooth.get(i).clone());
		}

		java.util.Set<Long> townRoads = new java.util.HashSet<>();
		boolean first = piece == 0 && road.fromEnd;
		boolean last = piece == road.built.length - 1 && road.toEnd;
		if (first) {
			meet(road.from, corners, true, townRoads);
		}

		if (last) {
			meet(road.to, corners, false, townRoads);
		}

		java.util.Set<Long> ways = waysAbout(corners, road, a, b);
		TownBuilder.highway(level, line(corners), townRoads, ways);
		lamps(level, road, piece, a, b, ways);
		// A dock where it meets water too wide to bridge (its boat crossing's end).
		if (piece == 0 && road.fromDock && road.points.size() > 1) {
			int[] p = road.points.get(0);
			int[] q = road.points.get(1);
			TownBuilder.dock(level, p[0], p[1], p[0] - q[0], p[1] - q[1], placeName(road.to));
		}

		if (piece == road.built.length - 1 && road.toDock && road.points.size() > 1) {
			int[] p = road.points.get(road.points.size() - 1);
			int[] q = road.points.get(road.points.size() - 2);
			TownBuilder.dock(level, p[0], p[1], p[0] - q[0], p[1] - q[1], placeName(road.to));
		}
		// (Where it leaves a town, a lamp with a distance sign; it was a standing signpost.)
		if (first || last) {
			List<double[]> smooth2 = smooth(road);
			double[] s = RoadLamps.along(smooth2);
			java.util.Set<Long> notOn = new java.util.HashSet<>(ways);
			notOn.addAll(townRoads);
			if (first) {
				RoadLamps.leaving(level, smooth2, s, true, placeName(road.from), placeName(road.to), notOn);
			}

			if (last) {
				RoadLamps.leaving(level, smooth2, s, false, placeName(road.from), placeName(road.to), notOn);
			}
		}
	}

	/**
	 * The lamps along this stretch ({@link RoadLamps}): its own, away from its ends; and those either side of where it meets
	 * a stretch built already (left till both were built: the second one's banks dug under a lamp by its end).
	 */
	private static void lamps(ServerLevel level, WorldPlan.Highway road, int piece, int a, int b, java.util.Set<Long> ways) {
		List<double[]> smooth = smooth(road);
		double[] s = RoadLamps.along(smooth);
		boolean lastPiece = piece == road.built.length - 1;
		long salt = level.getSeed() ^ road.from.getLeastSignificantBits() * 31L ^ road.to.getMostSignificantBits();
		String from = placeName(road.from);
		String to = placeName(road.to);
		int join = RoadLamps.JOIN;
		RoadLamps.light(level, smooth, s, piece == 0 ? s[a] : s[a] + join, lastPiece ? s[b] + 1 : s[b] - join, from, to, salt, ways);
		if (piece > 0 && road.built[piece - 1]) {
			RoadLamps.light(level, smooth, s, s[a] - join, s[a] + join, from, to, salt, ways);
		}

		if (!lastPiece && road.built[piece + 1]) {
			RoadLamps.light(level, smooth, s, s[b] - join, s[b] + join, from, to, salt, ways);
		}
	}

	/** A place of the plan's name (for a dock's sign). */
	private static String placeName(java.util.UUID id) {
		for (WorldPlan.Place place : WorldPlan.places) {
			if (place.id.equals(id)) {
				return place.name;
			}
		}

		return "the far shore";
	}

	private static final Map<WorldPlan.Highway, List<double[]>> SMOOTH = new java.util.IdentityHashMap<>();

	/**
	 * A road's points with its small wiggles taken out (the user, 2026-11-13: "the road pathing is kinda chaotic"): each
	 * the weighted middle of itself and the two either side (its ends where they are), worked out once for the whole road,
	 * so its stretches meet.
	 */
	static List<double[]> smooth(WorldPlan.Highway road) {
		List<double[]> known = SMOOTH.get(road);
		if (known != null && known.size() == road.points.size()) {
			return known;
		}

		int n = road.points.size();
		List<double[]> out = new ArrayList<>();
		int[] weights = {3, 2, 1};
		for (int i = 0; i < n; i++) {
			// (A bridge's line stays where the plan put it: squared, or two lines and a corner.)
			int reach = road.points.get(i).length > 2 && road.points.get(i)[2] == 1 ? 0 : Math.min(2, Math.min(i, n - 1 - i));
			double x = 0;
			double z = 0;
			double sum = 0;
			for (int k = -reach; k <= reach; k++) {
				int w = weights[Math.abs(k)];
				x += w * (road.points.get(i + k)[0] + 0.5);
				z += w * (road.points.get(i + k)[1] + 0.5);
				sum += w;
			}

			out.add(new double[] {x / sum, z / sum});
		}

		SMOOTH.put(road, out);
		return out;
	}

	/**
	 * The columns of every planned road near this stretch (three wide, as they're built): its own stretches either side and
	 * any other's, so no railing goes up on one.
	 */
	private static java.util.Set<Long> waysAbout(List<double[]> corners, WorldPlan.Highway own, int a, int b) {
		int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		for (double[] c : corners) {
			x0 = Math.min(x0, (int)Math.floor(c[0]) - 40);
			z0 = Math.min(z0, (int)Math.floor(c[1]) - 40);
			x1 = Math.max(x1, (int)Math.floor(c[0]) + 40);
			z1 = Math.max(z1, (int)Math.floor(c[1]) + 40);
		}

		java.util.Set<Long> ways = new java.util.HashSet<>();
		for (WorldPlan.Highway other : WorldPlan.highways) {
			for (int i = 0; i + 1 < other.points.size(); i++) {
				// (Not this stretch itself: its own railings go where they go.)
				if (other == own && i >= a && i < b) {
					continue;
				}

				double[] p = smooth(other).get(i);
				double[] q = smooth(other).get(i + 1);
				if (Math.max(p[0], q[0]) < x0 || Math.min(p[0], q[0]) > x1 || Math.max(p[1], q[1]) < z0 || Math.min(p[1], q[1]) > z1) {
					continue;
				}

				int steps = Math.max(1, (int)Math.ceil(Math.hypot(q[0] - p[0], q[1] - p[1])));
				for (int s = 0; s <= steps; s++) {
					int x = (int)Math.floor(p[0] + (q[0] - p[0]) * s / (double)steps);
					int z = (int)Math.floor(p[1] + (q[1] - p[1]) * s / (double)steps);
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							ways.add(BlockPos.asLong(x + dx, 0, z + dz));
						}
					}
				}
			}
		}

		return ways;
	}

	/** The town's road column nearest {@code end} within reach of it (where the stretch there runs on to), or Long.MIN_VALUE. */
	private static long meetPoint(java.util.UUID id, int[] end) {
		Town town = Diplomacy.byId(id.toString());
		if (town == null || town.roadColumns.length == 0) {
			return Long.MIN_VALUE;
		}

		long best = Long.MIN_VALUE;
		double bestDistance = MEET_WITHIN;
		for (long column : town.roadColumns) {
			double d = Math.hypot(BlockPos.getX(column) + 0.5 - (end[0] + 0.5), BlockPos.getZ(column) + 0.5 - (end[1] + 0.5));
			if (d <= bestDistance) {
				bestDistance = d;
				best = column;
			}
		}

		return best;
	}

	/** The land along a straight run (x0, z0 to x1, z1) loaded, every few blocks, a chunk each side. */
	private static boolean loadedAlong(ServerLevel level, int[] run) {
		double length = Math.hypot(run[2] - run[0], run[3] - run[1]);
		for (double d = 0; d <= length + 8; d += 8) {
			double f = length == 0 ? 0 : Math.min(1, d / length);
			int x = (int)Math.floor(run[0] + (run[2] - run[0]) * f);
			int z = (int)Math.floor(run[1] + (run[3] - run[1]) * f);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (level.getChunkSource().getChunkNow((x >> 4) + dx, (z >> 4) + dz) == null) {
						return false;
					}
				}
			}
		}

		return true;
	}

	/** The stretch at a town's end run on straight to the town's nearest road (within reach of it). */
	private static void meet(java.util.UUID id, List<double[]> corners, boolean atStart, java.util.Set<Long> townRoads) {
		Town town = Diplomacy.byId(id.toString());
		if (town == null || town.roadColumns.length == 0) {
			return;
		}

		double[] end = atStart ? corners.get(0) : corners.get(corners.size() - 1);
		long best = 0;
		double bestDistance = Double.MAX_VALUE;
		for (long column : town.roadColumns) {
			townRoads.add(column);
			double d = Math.hypot(BlockPos.getX(column) + 0.5 - end[0], BlockPos.getZ(column) + 0.5 - end[1]);
			if (d < bestDistance) {
				bestDistance = d;
				best = column;
			}
		}

		if (bestDistance > MEET_WITHIN) {
			return;
		}

		double[] meet = {BlockPos.getX(best) + 0.5, BlockPos.getZ(best) + 0.5};
		if (atStart) {
			corners.add(0, meet);
		} else {
			corners.add(meet);
		}
	}

	/** Points a block apart along these corners, each with its heading. */
	private static List<double[]> line(List<double[]> corners) {
		List<double[]> line = new ArrayList<>();
		for (int k = 0; k < corners.size() - 1; k++) {
			double[] p = corners.get(k);
			double[] q = corners.get(k + 1);
			double length = Math.hypot(q[0] - p[0], q[1] - p[1]);
			double heading = Math.atan2(q[1] - p[1], q[0] - p[0]);
			for (double d = 0; d < length; d += 1) {
				line.add(new double[] {p[0] + (q[0] - p[0]) * d / length, p[1] + (q[1] - p[1]) * d / length, heading});
			}
		}

		double[] end = corners.get(corners.size() - 1);
		double[] before = corners.get(Math.max(0, corners.size() - 2));
		line.add(new double[] {end[0], end[1], Math.atan2(end[1] - before[1], end[0] - before[0])});
		return line;
	}

	/**
	 * Debug: a road from {@code from} the way {@code yaw} looks, bending a little as it goes, built in stretches as the
	 * plan's roads are (each told of the others' ways), on land that's loaded. How many stretches.
	 */
	static int demo(ServerLevel level, net.minecraft.world.phys.Vec3 from, float yaw, int length, int shape) {
		double heading = Math.toRadians(yaw + 90);
		List<double[]> corners = new ArrayList<>();
		double x = from.x;
		double z = from.z;
		for (int d = 0; d <= length; d += 4) {
			corners.add(new double[] {x, z});
			// (0: bending a little; 1: straight; 2: straight, a square corner to the right halfway.)
			double bend = heading + (shape == 0 ? 0.5 * Math.sin(d / 38.0) : shape == 2 && d >= length / 8 * 4 ? Math.PI / 2 : 0);
			x += Math.cos(bend) * 4;
			z += Math.sin(bend) * 4;
		}

		List<List<double[]>> lines = new ArrayList<>();
		for (int a = 0; a < corners.size() - 1; a += PIECE) {
			lines.add(line(corners.subList(a, Math.min(corners.size(), a + PIECE + 1))));
		}

		WildRoad.DEBUG = true;
		for (int i = 0; i < lines.size(); i++) {
			java.util.Set<Long> avoid = new java.util.HashSet<>();
			for (int k = 0; k < lines.size(); k++) {
				if (k == i) {
					continue;
				}

				for (double[] p : lines.get(k)) {
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							avoid.add(BlockPos.asLong((int)Math.floor(p[0]) + dx, 0, (int)Math.floor(p[1]) + dz));
						}
					}
				}
			}

			TownBuilder.highway(level, lines.get(i), java.util.Set.of(), avoid);
		}

		WildRoad.DEBUG = System.getenv("DRIFTER_ROAD_DEBUG") != null;
		double[] s = RoadLamps.along(corners);
		int lamps = RoadLamps.light(level, corners, s, 0, s[s.length - 1] + 1, "Ashford", "Brindlemoor", level.getSeed(), java.util.Set.of());
		// What its middle three are topped with, and where it runs (to look at it).
		Map<String, Integer> tops = new java.util.TreeMap<>();
		StringBuilder way = new StringBuilder();
		int step = 0;
		for (List<double[]> part : lines) {
			for (double[] p : part) {
				int px = (int)Math.floor(p[0]);
				int pz = (int)Math.floor(p[1]);
				int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz) - 1;
				tops.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(px, y, pz)).getBlock()).getPath(), 1, Integer::sum);
				if (step++ % 16 == 0) {
					way.append(px).append(',').append(y).append(',').append(pz).append("  ");
				}
			}
		}

		DrifterCivilizations.LOGGER.info("[road] demo: along its middle {}; {} lamps; by {}", tops, lamps, way);

		return lines.size();
	}

	/** Debug: how many stretches are built of how many. */
	static String describe() {
		int pieces = 0;
		int built = 0;
		for (WorldPlan.Highway road : WorldPlan.highways) {
			pieces += road.built.length;
			for (boolean one : road.built) {
				built += one ? 1 : 0;
			}
		}

		// (And the stretches whose land is loading now, road:stretch at chunk: a stretch never let go of held up every road.)
		return WorldPlan.highways.size() + " roads, " + built + " of " + pieces + " stretches built; loading " + loading.size() + " " + loading.keySet();
	}
}
