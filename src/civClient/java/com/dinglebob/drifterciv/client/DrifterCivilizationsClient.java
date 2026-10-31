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
	}
}
