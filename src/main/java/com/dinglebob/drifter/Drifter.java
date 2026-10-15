package com.dinglebob.drifter;

import com.dinglebob.drifter.brain.Sources;
import com.dinglebob.drifter.brain.Threats;
import com.dinglebob.drifter.command.DrifterCommands;
import com.dinglebob.drifter.debug.overlay.DebugOverlayServer;
import com.dinglebob.drifter.registry.ModEntities;
import com.dinglebob.drifter.world.MobTargeting;
import com.dinglebob.drifter.world.PlayerPlacedBlocks;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Drifter implements ModInitializer {
	public static final String MOD_ID = "drifter";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		DrifterConfig.load();
		ModEntities.register();
		PlayerPlacedBlocks.register();
		DrifterCommands.register();
		DebugOverlayServer.register();
		Sources.register();
		Threats.register();
		com.dinglebob.drifter.identity.Usernames.register();
		MobTargeting.register();
		com.dinglebob.drifter.perception.CreatureHearing.register();
		com.dinglebob.drifter.world.AnimalTempting.register();
		com.dinglebob.drifter.social.SocialWatcher.register();
		LOGGER.info("Drifter initialized");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
