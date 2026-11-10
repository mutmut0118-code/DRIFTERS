package com.dinglebob.drifterciv;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player walking into a town (0.23.37, the user: "when you walk into a town or an encounter the game autosaves"): with
 * Quicksave installed, an autosave called after the town, so dying on the road on puts them back there. Not more than once
 * every five minutes, nor the same town again within twenty; never while a new world is still being built.
 */
final class Arrivals {
	private Arrivals() {
	}

	/** Looked at this often (ticks). */
	private static final int EVERY = 20;
	/** Two of these autosaves this far apart at least (ticks); the same town's, further. */
	private static final long GAP = 20L * 60 * 5;
	private static final long SAME_TOWN = 20L * 60 * 20;
	/** In a town: this near one of its houses, or its middle. */
	private static final int NEAR_HOUSE = 10;
	private static final int NEAR_MIDDLE = 24;

	/** Each player's town as of the last look (absent: not looked at yet; null: out of any). */
	private static final Map<UUID, UUID> in = new HashMap<>();
	/** Each player's last of these autosaves: the town and when. */
	private record Saved(UUID town, long at) {
	}

	private static final Map<UUID, Saved> saved = new HashMap<>();

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY == 0 && DrifterApi.quicksave() && !WorldBuild.building) {
				check(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			in.clear();
			saved.clear();
		});
	}

	private static void check(MinecraftServer server) {
		long now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Town town = townAt(player);
			boolean seen = in.containsKey(player.getUUID());
			UUID was = in.put(player.getUUID(), town == null ? null : town.id);
			// (Walked in from outside it: not just joined in one, nor moved from one to the next without leaving.)
			if (town == null || !seen || was != null) {
				continue;
			}

			Saved last = saved.get(player.getUUID());
			if (last != null && (now - last.at < GAP || last.town.equals(town.id) && now - last.at < SAME_TOWN)) {
				continue;
			}

			saved.put(player.getUUID(), new Saved(town.id, now));
			DrifterApi.autosave(player, town.name);
		}
	}

	/** The town it's in (near one of its houses, or its middle), or null. */
	@Nullable
	private static Town townAt(ServerPlayer player) {
		String dimension = player.level().dimension().location().toString();
		for (Town town : Towns.all()) {
			if (!town.dimension.equals(dimension) || !town.center.closerThan(player.blockPosition(), 400.0)) {
				continue;
			}

			double dx = player.getX() - town.center.getX();
			double dz = player.getZ() - town.center.getZ();
			if (dx * dx + dz * dz <= NEAR_MIDDLE * NEAR_MIDDLE) {
				return town;
			}

			for (Town.Plot plot : town.plots) {
				int x0 = Math.min(plot.origin.getX(), plot.origin.getX() + plot.sizeX);
				int x1 = Math.max(plot.origin.getX(), plot.origin.getX() + plot.sizeX);
				int z0 = Math.min(plot.origin.getZ(), plot.origin.getZ() + plot.sizeZ);
				int z1 = Math.max(plot.origin.getZ(), plot.origin.getZ() + plot.sizeZ);
				if (player.getX() >= x0 - NEAR_HOUSE && player.getX() <= x1 + NEAR_HOUSE && player.getZ() >= z0 - NEAR_HOUSE && player.getZ() <= z1 + NEAR_HOUSE) {
					return town;
				}
			}
		}

		return null;
	}
}
