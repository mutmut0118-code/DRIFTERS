package com.dinglebob.drifterciv.client;

import com.dinglebob.drifterciv.WorldBuild;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The addon's client side: the loading screen while a new civilized world is built ({@link WorldBuildScreen}). */
public final class DrifterCivilizationsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(WorldBuild.Progress.TYPE, (progress, context) -> {
			var client = context.client();
			if (progress.finished()) {
				if (client.screen instanceof WorldBuildScreen) {
					client.setScreen(null);
				}
			} else if (client.screen instanceof WorldBuildScreen screen) {
				screen.update(progress);
			} else {
				client.setScreen(new WorldBuildScreen(progress));
			}
		});
		// Debug only: DRIFTER_CIV_SCREEN set, the Friendship settings page opens once a world's been open a while (a client
		// test screenshots it; the test harness can't click through Mod Menu).
		if (System.getenv("DRIFTER_CIV_SCREEN") != null) {
			int[] ticks = {0};
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
				if (client.level != null && ticks[0] >= 0 && ++ticks[0] == 300) {
					client.setScreen(new FriendshipScreen(new CivConfigScreen(null)));
					ticks[0] = -1;
				}
			});
		}
	}
}
