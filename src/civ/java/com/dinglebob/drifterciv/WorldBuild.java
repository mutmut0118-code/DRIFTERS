package com.dinglebob.drifterciv;

import java.util.HashMap;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A new civilized world built before it's played (the user, 2026-10-30: "the whole world plan needs to be pregenerated
 * before i load the world that way i dont see everything unfold in my eyes ... it could be like an extra loading screen?"):
 * once, as it first opens, the plan's made, then every town in it and every road between them, their land generated for
 * them, as fast as the server goes, before anyone plays. Players in it meanwhile wait behind a loading screen (the
 * addon's client shows it, {@link Progress}), held where they stand and unhurt. Old worlds, and towns where villages would
 * be, are made as players come near, as before.
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
			if (!started) {
				started = true;
				TownSites.makeAllPlanned();
				DrifterCivilizations.LOGGER.info("[build] the plan's ready: building {} places and {} stretches of road", WorldPlan.places.size(),
					Highways.stretches());
			}

			int places = WorldPlan.places.size();
			int made = (int)WorldPlan.places.stream().filter(place -> place.made).count();
			int stretches = Highways.stretches();
			int built = Highways.built();
			stage = made < places ? "Building towns (" + made + " of " + places + ")" : "Laying roads (" + built + " of " + stretches + ")";
			// (A town counts as much as twenty stretches of road: about the time each takes.)
			done = made * 20 + built;
			total = places * 20 + stretches;
			if (made >= places && built >= stretches) {
				finish(server);
				return;
			}
		}

		if (server.getTickCount() % 10 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				send(player, false);
			}
		}
	}

	private static void finish(MinecraftServer server) {
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
