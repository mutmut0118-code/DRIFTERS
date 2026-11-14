package com.dinglebob.drifterciv;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A town's name at the top of the screen as a player comes into it, fading in and out (the user, 2026-11-14: "the town name
 * popping up at the top of the screen when you enter it (a little fade in and fade out)"). The server says when; the
 * client draws it.
 */
public final class TownBanner {
	private TownBanner() {
	}

	/** Out of a town this far past its land before coming back in shows its name again (no flicker at its edge). */
	private static final int LEAVE_PAST = 24;
	/** Looked at this often (ticks). */
	private static final int EVERY = 10;
	/** The town each player was last in, or none. */
	private static final Map<UUID, UUID> inside = new HashMap<>();

	/** A town's name to show. */
	public record Show(String name) implements CustomPacketPayload {
		public static final Type<Show> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("drifter_civ", "town_banner"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Show> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Show::name, Show::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.playS2C().register(Show.TYPE, Show.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(TownBanner::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> inside.clear());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}

		List<Town> towns = Towns.all();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			String dimension = player.level().dimension().location().toString();
			UUID was = inside.get(player.getUUID());
			Town in = null;
			for (Town town : towns) {
				if (!town.dimension.equals(dimension)) {
					continue;
				}

				double dx = player.getX() - town.center.getX();
				double dz = player.getZ() - town.center.getZ();
				// (Still in the one it was in until well past its edge.)
				int reach = town.land() + (town.id.equals(was) ? LEAVE_PAST : 0);
				if (dx * dx + dz * dz <= (double)reach * reach) {
					in = town;
					if (town.id.equals(was)) {
						break;
					}
				}
			}

			UUID now = in == null ? null : in.id;
			if (now == null) {
				inside.remove(player.getUUID());
			} else if (!now.equals(was)) {
				inside.put(player.getUUID(), now);
				if (ServerPlayNetworking.canSend(player, Show.TYPE)) {
					ServerPlayNetworking.send(player, new Show(in.name));
				}
			}
		}
	}
}
