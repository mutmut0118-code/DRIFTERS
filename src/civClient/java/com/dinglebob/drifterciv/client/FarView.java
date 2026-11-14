package com.dinglebob.drifterciv.client;

import com.dinglebob.drifterciv.FarPaint;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Paper towns, the client's side: which painter a far-view hookup running in this game asks. The server's own when
 * this game hosts the world (single player, a LAN host: same program), else the one filled from the server's packets.
 */
public final class FarView {
	private FarView() {
	}

	private static volatile FarPaint painter = FarPaint.SERVER;

	public static FarPaint painter() {
		return painter;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> painter = client.getSingleplayerServer() != null ? FarPaint.SERVER : FarPaint.CLIENT);
	}
}
