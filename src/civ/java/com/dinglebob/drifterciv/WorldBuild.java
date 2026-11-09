package com.dinglebob.drifterciv;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A new civilized world readied before it's played (the user, 2026-10-30: "the whole world plan needs to be pregenerated
 * before i load the world that way i dont see everything unfold in my eyes ... it could be like an extra loading screen?";
 * 2026-10-31: "the goal is to get this down to a minute at most"): once, as it first opens, the plan's made, then every
 * town and road within sight of spawn and a little more ({@link #FIRST_CHUNKS}), their land generated for them, before
 * anyone plays. Players wait meanwhile
 * behind a loading screen (the addon's client shows it, {@link Progress}), held where they stand and unhurt. The rest is
 * built as they play, that far ahead of them: out of sight, never in front of them. (Everything at once took five minutes
 * on the user's machine, ten thousand chunks of land generated for it.)
 */
public final class WorldBuild {
	private WorldBuild() {
	}

	/** This world's being built now (a new planned world, not built yet). */
	static volatile boolean building;
	/** Where each waiting player is held, and whether they were unhurtable before (to leave them as they were). */
	private static final Map<UUID, Vec3> held = new HashMap<>();
	private static final Map<UUID, Boolean> wasInvulnerable = new HashMap<>();
	private static boolean started;
	private static long since;
	private static String stage = "";
	private static int done;
	private static int total;
	/** Built this many chunks out past a player's view distance; before play, all this many out from spawn's. */
	private static final int AHEAD_CHUNKS = 20;
	private static final int FIRST_CHUNKS = 6;
	/**
	 * Before play, once what's in sight is built, more: the towns and roads this far from spawn, nearest first, till they're
	 * all built or the loading screen's been up {@link #MOST_MS} (the user, 2026-11-08: "dont be afraid to make the loading
	 * screen time a bit longer, in fact, we can load more towns if the loading screen is up to 1:30", then "lets make it
	 * capped at 2": a few seconds short of two minutes, for the last step to finish). Never less than what's in sight,
	 * however long that takes.
	 */
	static final int LOADING_REACH = 2000;
	private static final long MOST_MS = 110_000L;
	private static boolean more;

	static void register() {
		PayloadTypeRegistry.playS2C().register(Progress.TYPE, Progress.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(WorldBuild::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (building) {
				send(handler.getPlayer(), false);
			}
		});
	}

	/** As the world loads: to be built (new, planned) or not. */
	static void decide(boolean newPlanned, boolean builtBefore, boolean buildingBefore) {
		building = !builtBefore && (newPlanned || buildingBefore);
		started = false;
		more = false;
		spawnSpot = null;
		held.clear();
		wasInvulnerable.clear();
		since = System.currentTimeMillis();
		if (building) {
			DrifterCivilizations.LOGGER.info("[build] a new civilized world: its towns and roads built before it's played");
		}
	}

	private static void tick(MinecraftServer server) {
		if (!building) {
			return;
		}

		hold(server);
		if (!WorldPlan.ready) {
			stage = "Planning the nations";
			done = 0;
			total = 0;
		} else {
			ServerLevel level = server.overworld();
			// (Before play, only what could be seen from spawn and a little more: the rest goes on being built ahead as they play,
			// out of sight. Everything ahead first took over a minute, most of it waiting for land to be generated.)
			int ahead = (server.getPlayerList().getViewDistance() + FIRST_CHUNKS) * 16;
			// The places and stretches of road near enough to build now (a stretch at a town's end once its town is).
			List<WorldPlan.Place> near = WorldPlan.places.stream().filter(place -> distance(level, place.x, place.z) < ahead + (place.kind.equals("city") ? 128 : 0))
				.toList();
			int places = near.size();
			int made = (int)near.stream().filter(place -> place.made).count();
			int[] roads = Highways.nearStretches(level, ahead);
			if (!started) {
				started = true;
				DrifterCivilizations.LOGGER.info("[build] the plan's ready: {} places of {} and {} stretches of road of {} within {} blocks built first", places,
					WorldPlan.places.size(), roads[1], Highways.stretches(), ahead);
			}

			stage = made < places ? "Building towns (" + made + " of " + places + ")" : "Laying roads (" + roads[0] + " of " + roads[1] + ")";
			// (A town counts as much as twenty stretches of road: about the time each takes.)
			done = made * 20 + roads[0];
			total = places * 20 + roads[1];
			if (made >= places && roads[0] >= roads[1]) {
				// What's in sight is built: more while there's time (nearest first), then play.
				List<WorldPlan.Place> further = WorldPlan.places.stream().filter(place -> distance(level, place.x, place.z) < LOADING_REACH).toList();
				int furtherMade = (int)further.stream().filter(place -> place.made).count();
				int[] furtherRoads = Highways.nearStretches(level, LOADING_REACH);
				long elapsed = System.currentTimeMillis() - since;
				if (!more) {
					more = true;
					DrifterCivilizations.LOGGER.info("[build] in sight built at {} s: more till {} s ({} places and {} stretches of road within {} blocks)", elapsed / 1000,
						MOST_MS / 1000, further.size(), furtherRoads[1], LOADING_REACH);
				}

				if (elapsed >= MOST_MS || furtherMade >= further.size() && furtherRoads[0] >= furtherRoads[1]) {
					DrifterCivilizations.LOGGER.info("[build] {} of {} places and {} of {} stretches of road within {} blocks built before play", furtherMade,
						further.size(), furtherRoads[0], furtherRoads[1], LOADING_REACH);
					finish(server);
					return;
				}

				stage = "Building more of the world (" + furtherMade + " of " + further.size() + " towns)";
				// (By the time it may take: the bar fills as the time runs out, or as the towns are done if that's sooner.)
				int byTowns = further.isEmpty() ? 100 : furtherMade * 100 / further.size();
				int byTime = (int)Math.min(100, elapsed * 100 / MOST_MS);
				done = Math.max(byTowns, byTime);
				total = 100;
			}
		}

		if (server.getTickCount() % 10 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				send(player, false);
			}
		}
	}

	private static void finish(MinecraftServer server) {
		// (The road there built now, its trees cleared: the spot settled on that, its players put on it.)
		if (spawnSpot != null) {
			int[] spot = spawnSpot;
			moveSpawn(server, spot[0], spot[1], spot[2], spot[3]);
			spawnSpot = null;
		}

		building = false;
		WorldPlan.built = true;
		Towns.save(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Boolean was = wasInvulnerable.get(player.getUUID());
			if (was != null) {
				player.setInvulnerable(was);
			}

			send(player, true);
		}

		held.clear();
		wasInvulnerable.clear();
		DrifterCivilizations.LOGGER.info("[build] the world's built ({} s)", (System.currentTimeMillis() - since) / 1000);
	}

	/**
	 * How far out from the players towns and roads are built: their view distance and {@link #AHEAD_CHUNKS} more (a town
	 * put up twelve chunks off was put up in plain view).
	 */
	static int ahead(MinecraftServer server) {
		// (Before play: further, while the loading screen's up.)
		if (building && more) {
			return LOADING_REACH;
		}

		return Math.max(256, Math.min(1024, (server.getPlayerList().getViewDistance() + AHEAD_CHUNKS) * 16));
	}

	/** From the nearest player to here (across: x and z), or from spawn while the world's readied before it's played. */
	static double distance(ServerLevel level, int x, int z) {
		double best = Double.MAX_VALUE;
		for (ServerPlayer player : level.players()) {
			best = Math.min(best, Math.hypot(player.getX() - x, player.getZ() - z));
		}

		if (building) {
			BlockPos spawn = level.getSharedSpawnPos();
			best = Math.min(best, Math.hypot(spawn.getX() - x, spawn.getZ() - z));
		}

		return best;
	}

	/**
	 * The world's spawn moved (0.23.31: by the town nearest the plan's middle, outside it), on solid ground near this spot,
	 * facing the town; players waiting for the world to be readied moved there.
	 */
	static void moveSpawn(MinecraftServer server, int x, int z, int townX, int townZ) {
		ServerLevel level = server.overworld();
		// (Open sky over it first: found in testing, the first spot was on the floor of a dark forest, leaves all round.)
		BlockPos at = null;
		for (int pass = 0; pass < 2 && at == null; pass++) {
			for (BlockPos.MutableBlockPos spot : BlockPos.spiralAround(new BlockPos(x, 0, z), pass == 0 ? 32 : 12, net.minecraft.core.Direction.EAST,
				net.minecraft.core.Direction.SOUTH)) {
				BlockPos here = standOn(level, spot.getX(), spot.getZ());
				if (here != null && (pass == 1 || open(level, here))) {
					at = here;
					break;
				}
			}
		}

		if (at == null) {
			level.getChunk(x >> 4, z >> 4);
			at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
		}

		// (Yaw: 0 faces south, 90 west.)
		float yaw = (float)Math.toDegrees(Math.atan2(-(townX - at.getX()), townZ - at.getZ()));
		BlockPos was = level.getSharedSpawnPos();
		spawnSpot = new int[] {x, z, townX, townZ};
		level.setDefaultSpawnPos(at, yaw);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level() == level) {
				player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
				held.put(player.getUUID(), player.position());
			}
		}

		DrifterCivilizations.LOGGER.info("[build] spawn moved from {} to {} (by the nearest town, at {}, {})", was.toShortString(), at.toShortString(), townX, townZ);
	}

	/** The sky over it and nothing leafy about its head: not under trees. */
	private static boolean open(ServerLevel level, BlockPos feet) {
		if (!level.canSeeSky(feet.above())) {
			return false;
		}

		for (BlockPos near : BlockPos.betweenClosed(feet.offset(-1, 0, -1), feet.offset(1, 2, 1))) {
			if (level.getBlockState(near).is(net.minecraft.tags.BlockTags.LEAVES)) {
				return false;
			}
		}

		return true;
	}

	/** Where the spawn was moved to (its spot and the town it faces), to settle on the ground there once it's built. */
	@org.jetbrains.annotations.Nullable
	private static int[] spawnSpot;

	/** Where to stand on this column: on a full block's top, nothing liquid there, room over it; null if not. */
	@org.jetbrains.annotations.Nullable
	private static BlockPos standOn(ServerLevel level, int x, int z) {
		// (Its land generated first: the height of land not loaded yet reads as the bottom of the world.)
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos feet = new BlockPos(x, y, z);
		var floor = level.getBlockState(feet.below());
		if (y <= level.getMinBuildHeight() || !floor.getFluidState().isEmpty() || floor.is(net.minecraft.tags.BlockTags.LOGS)
			|| !net.minecraft.world.level.block.Block.isFaceFull(floor.getCollisionShape(level, feet.below()), net.minecraft.core.Direction.UP)) {
			return null;
		}

		return level.getBlockState(feet).getFluidState().isEmpty() && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
			&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty() ? feet : null;
	}

	/** Waiting players kept where they stand, unhurt (nothing gets at them while they can't see). */
	private static void hold(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Vec3 at = held.computeIfAbsent(player.getUUID(), id -> player.position());
			wasInvulnerable.computeIfAbsent(player.getUUID(), id -> player.isInvulnerable());
			player.setInvulnerable(true);
			player.setDeltaMovement(Vec3.ZERO);
			player.fallDistance = 0;
			if (player.position().distanceToSqr(at) > 0.25) {
				player.teleportTo(player.serverLevel(), at.x, at.y, at.z, player.getYRot(), player.getXRot());
			}
		}
	}

	private static void send(ServerPlayer player, boolean finished) {
		if (ServerPlayNetworking.canSend(player, Progress.TYPE)) {
			long seconds = (System.currentTimeMillis() - since) / 1000;
			ServerPlayNetworking.send(player, new Progress(stage, done, total, (int)seconds, finished));
		}
	}

	/** How the build's going: what it's on, how far through (done of total; total 0: no numbers yet), finished. */
	public record Progress(String stage, int done, int total, int seconds, boolean finished) implements CustomPacketPayload {
		public static final Type<Progress> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("drifter_civ", "world_build"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Progress> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Progress::stage,
			ByteBufCodecs.VAR_INT, Progress::done, ByteBufCodecs.VAR_INT, Progress::total, ByteBufCodecs.VAR_INT, Progress::seconds, ByteBufCodecs.BOOL,
			Progress::finished, Progress::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
