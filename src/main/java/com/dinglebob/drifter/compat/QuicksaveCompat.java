package com.dinglebob.drifter.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.dinglebob.drifter.Drifter;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Quicksave (Bethesda-style saves: dying puts the world back to the last save), when it's installed (optional, detected at
 * runtime). An autosave asked for at the moments a player would want one (the user, 0.23.37: "when you walk into a town
 * or an encounter the game autosaves"), through Quicksave's own {@code SaveService.trySave}, so its own guard still says
 * no in a fight, falling or in lava: then it's tried again every two seconds for half a minute. Read through reflection;
 * if anything doesn't match, it switches itself off.
 */
public final class QuicksaveCompat {
	private QuicksaveCompat() {
	}

	/** A refused autosave is tried again this often (ticks), for this long. */
	private static final int RETRY_EVERY = 40;
	private static final long RETRY_FOR = 20L * 30;
	/** Quicksave's refusals worth waiting out (the moment passes); any other, the autosave is dropped. */
	private static final Set<String> WAIT_OUT = Set.of("COMBAT", "FALLING", "IN_LAVA", "BUSY", "NO_PLAYER");

	private static boolean looked;
	private static boolean failed;
	private static Method trySave;
	private static Method success;
	private static Method refusal;
	private static Object autoSlot;

	/** An autosave asked for: whose, what it's called in Quicksave's list, until when it's tried. */
	private record Ask(UUID player, String label, long until) {
	}

	private static final List<Ask> asks = new ArrayList<>();

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!asks.isEmpty() && server.getTickCount() % RETRY_EVERY == 0) {
				tick(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> asks.clear());
	}

	/** Quicksave is installed. */
	public static boolean present() {
		return FabricLoader.getInstance().isModLoaded("quicksave");
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static boolean ready() {
		if (!looked) {
			looked = true;
			try {
				Class<?> service = Class.forName("com.muto118.quicksave.snapshot.SaveService");
				Class<?> slot = Class.forName("com.muto118.quicksave.snapshot.SlotType");
				Class<?> result = Class.forName("com.muto118.quicksave.snapshot.SaveService$Result");
				autoSlot = Enum.valueOf((Class)slot, "AUTO");
				trySave = service.getMethod("trySave", MinecraftServer.class, ServerPlayer.class, slot, String.class);
				success = result.getMethod("success");
				refusal = result.getMethod("refusal");
				Drifter.LOGGER.info("[Drifter] Quicksave found: autosaves on reaching a town and before an encounter");
			} catch (Throwable e) {
				failed = true;
				Drifter.LOGGER.warn("[Drifter] Quicksave is here but doesn't match what Drifter expects; no autosaves from Drifter ({})", e.toString());
			}
		}

		return !failed;
	}

	/**
	 * Saves now, if Quicksave will: null if it did, else why not ("COMBAT", "FALLING", ... Quicksave's refusal; "failed":
	 * it tried and couldn't; "unavailable": no Quicksave).
	 */
	public static String saveNow(ServerPlayer player, String label) {
		if (!present() || !ready() || player.getServer() == null) {
			return "unavailable";
		}

		try {
			Object result = trySave.invoke(null, player.getServer(), player, autoSlot, label);
			if (Boolean.TRUE.equals(success.invoke(result))) {
				Drifter.LOGGER.info("[Drifter] Quicksave autosave: {}", label);
				return null;
			}

			Object why = refusal.invoke(result);
			return why == null ? "failed" : ((Enum<?>)why).name();
		} catch (Throwable e) {
			failed = true;
			Drifter.LOGGER.warn("[Drifter] Quicksave autosave failed; no more autosaves from Drifter ({})", e.toString());
			return "failed";
		}
	}

	/** An autosave asked for now, tried again for half a minute if Quicksave says no for the moment (a fight, a fall). */
	public static void autosave(ServerPlayer player, String label) {
		if (!present() || !ready() || player.getServer() == null) {
			return;
		}

		asks.removeIf(ask -> ask.player.equals(player.getUUID()));
		String why = saveNow(player, label);
		if (why != null && WAIT_OUT.contains(why)) {
			asks.add(new Ask(player.getUUID(), label, player.getServer().getTickCount() + RETRY_FOR));
		}
	}

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		for (Ask ask : new ArrayList<>(asks)) {
			ServerPlayer player = server.getPlayerList().getPlayer(ask.player);
			if (player == null || now > ask.until) {
				asks.remove(ask);
				continue;
			}

			String why = saveNow(player, ask.label);
			if (why == null || !WAIT_OUT.contains(why)) {
				asks.remove(ask);
			}
		}
	}
}
