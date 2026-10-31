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
 * roads, a signpost there saying where it goes and how far.
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
	private static final Map<String, ChunkPos> loading = new HashMap<>();
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
					loading.put(key, chunk);
					for (int[] end : ends) {
						ChunkPos far = new ChunkPos(end[2] >> 4, end[3] >> 4);
						level.getChunkSource().addRegionTicket(TICKET, far, LOAD_RADIUS, far);
					}

					continue;
				}

				if (!loaded(level, road, a, b) || ends.stream().anyMatch(end -> !loadedAlong(level, end))) {
					continue;
				}

				for (int[] end : ends) {
					ChunkPos far = new ChunkPos(end[2] >> 4, end[3] >> 4);
					level.getChunkSource().removeRegionTicket(TICKET, far, LOAD_RADIUS, far);
				}

				long started = System.nanoTime();
				build(level, road, p, a, b);
				long ms = (System.nanoTime() - started) / 1_000_000L;
				if (ms > 50) {
					DrifterCivilizations.LOGGER.info("[road] a stretch at {}, {} took {} ms", mid[0], mid[1], ms);
				}

				road.built[p] = true;
				level.getChunkSource().removeRegionTicket(TICKET, chunk, LOAD_RADIUS, chunk);
				loading.remove(key);
				Towns.save(server);
				// (One stretch a go: a few milliseconds each.)
				return;
			}
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

	/** One stretch built: its points, joined at a town's end to the nearest of the town's roads, a signpost there. */
	private static void build(ServerLevel level, WorldPlan.Highway road, int piece, int a, int b) {
		List<double[]> corners = new ArrayList<>();
		for (int i = a; i <= b; i++) {
			corners.add(new double[] {road.points.get(i)[0] + 0.5, road.points.get(i)[1] + 0.5});
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

		TownBuilder.highway(level, line(corners), townRoads);
		if (first) {
			sign(level, corners, true, road.to);
		}

		if (last) {
			sign(level, corners, false, road.from);
		}
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

	/** A signpost by the road a little out from a town: where the road goes, and how far. */
	private static void sign(ServerLevel level, List<double[]> corners, boolean atStart, java.util.UUID to) {
		WorldPlan.Place place = WorldPlan.place(to);
		if (place == null || corners.size() < 3) {
			return;
		}

		double[] at = atStart ? corners.get(2) : corners.get(corners.size() - 3);
		double[] toward = atStart ? corners.get(3 < corners.size() ? 3 : corners.size() - 1) : corners.get(Math.max(0, corners.size() - 4));
		double heading = Math.atan2(toward[1] - at[1], toward[0] - at[0]);
		int x = (int)Math.floor(at[0] + Math.cos(heading + Math.PI / 2) * 2.6);
		int z = (int)Math.floor(at[1] + Math.sin(heading + Math.PI / 2) * 2.6);
		BlockPos spot = new BlockPos(x, TownBuilder.ground(level, x, z) + 1, z);
		if (!level.getBlockState(spot).isAir() || !level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), net.minecraft.core.Direction.UP)) {
			return;
		}

		// (Facing along the road the way it goes: a player walking out reads it.)
		float yaw = (float)Math.toDegrees(heading) - 90;
		int rotation = net.minecraft.util.Mth.floor((yaw + 180) * 16.0 / 360.0 + 0.5) & 15;
		level.setBlock(spot, Blocks.OAK_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION, rotation), 3);
		if (level.getBlockEntity(spot) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
			int far = (int)Math.round(Math.hypot(place.x - at[0], place.z - at[1]));
			var text = new net.minecraft.world.level.block.entity.SignText().setMessage(1, Component.literal(place.name)).setMessage(2, Component.literal(far
				+ " blocks"));
			sign.setText(text, true);
			sign.setText(text, false);
		}
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

		return WorldPlan.highways.size() + " roads, " + built + " of " + pieces + " stretches built";
	}
}
