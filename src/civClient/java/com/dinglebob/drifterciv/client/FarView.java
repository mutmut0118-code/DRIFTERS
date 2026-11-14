package com.dinglebob.drifterciv.client;

import com.dinglebob.drifterciv.DrifterCivilizations;
import com.dinglebob.drifterciv.FarPaint;
import com.dinglebob.drifterciv.FarSync;
import com.dinglebob.drifterciv.FarVoxyPlugin;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Paper towns, the client's side: which painter a far-view hookup running in this game asks (the server's own when this
 * game hosts the world, single player or a LAN host; else the one filled from the server's packets), and asking the
 * server for the sketches when this game is a guest with such a hookup on.
 */
public final class FarView {
	private FarView() {
	}

	/** Debug, DRIFTER_FAR_TEST=1: the sketches are asked for and what comes is logged, hookup or not, host or not. */
	private static final boolean TEST = "1".equals(System.getenv("DRIFTER_FAR_TEST"));

	private static volatile FarPaint painter = FarPaint.SERVER;
	private static boolean asked;

	public static FarPaint painter() {
		return painter;
	}

	private static volatile int minY = -64;

	/** The world's floor (a far-view mod counts a column's heights from it). */
	public static int minY() {
		return minY;
	}

	/** A far-view hookup that runs in this game is on (Voxy with Voxy SeedGen; Distant Horizons draws on the host). */
	private static boolean hookup() {
		return FarVoxyPlugin.WANTED;
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(FarSync.Part.TYPE, (part, context) -> context.client().execute(() -> FarSync.part(part)));
		ClientPlayNetworking.registerGlobalReceiver(FarSync.State.TYPE, (state, context) -> context.client().execute(() -> {
			FarSync.state(state);
			if (TEST) {
				DrifterCivilizations.LOGGER.info("[far] test: the client holds {}", FarSync.held());
			}
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			asked = false;
			FarSync.forget();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean host = client.getSingleplayerServer() != null;
			painter = host ? FarPaint.SERVER : FarPaint.CLIENT;
			if (client.level != null) {
				minY = client.level.getMinBuildHeight();
			}

			// (Asked once, when the game's in the world and the server knows the packet: a server without the addon never does.)
			if (!asked && client.player != null && (TEST || !host && hookup()) && ClientPlayNetworking.canSend(FarSync.Want.TYPE)) {
				asked = true;
				ClientPlayNetworking.send(new FarSync.Want());
			}
		});
	}
}
