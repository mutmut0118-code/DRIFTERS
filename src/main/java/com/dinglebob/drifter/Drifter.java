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
		com.dinglebob.drifter.memory.ThoughtLog.echo = DrifterConfig.logThoughts();
		ModEntities.register();
		PlayerPlacedBlocks.register();
		DrifterCommands.register();
		DebugOverlayServer.register();
		Sources.register();
		com.dinglebob.drifter.brain.Weapons.register();
		com.dinglebob.drifter.building.plan.gen.GeneratedPlans.register();
		Threats.register();
		com.dinglebob.drifter.world.CatchUp.register();
		com.dinglebob.drifter.compat.RagdollCompat.register();
		com.dinglebob.drifter.entity.RealSkins.register();
		com.dinglebob.drifter.world.DrifterRegistry.register();
		com.dinglebob.drifter.world.Respawns.register();
		com.dinglebob.drifter.encounter.Encounters.register();
		com.dinglebob.drifter.world.DrifterCensus.register();
		com.dinglebob.drifter.world.DrifterGroups.register();
		com.dinglebob.drifter.world.DrifterWars.register();
		com.dinglebob.drifter.whim.Whims.register();
		com.dinglebob.drifter.world.gen.DrifterFeatures.register();
		com.dinglebob.drifter.world.gen.LivedInStructures.register();
		com.dinglebob.drifter.world.gen.Delvers.register();
		com.dinglebob.drifter.world.DrifterTravel.register();
		// House plans read once at start-up, so world generation (which can't read files itself) has them.
		// (Before the world loads: the spawn area is generated during start-up.)
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			// Optional combat mods: say at start whether they're found and match.
			com.dinglebob.drifter.compat.BetterCombatCompat.active();
			com.dinglebob.drifter.compat.CombatRollCompat.active();
			try {
				com.dinglebob.drifter.building.plan.HousePlans.invalidate();
				com.dinglebob.drifter.building.plan.HousePlans.cached(server);
			} catch (RuntimeException e) {
				LOGGER.warn("[Drifter] couldn't read house plans at start-up: {}", e.toString());
			}
		});
		com.dinglebob.drifter.world.OfflineClock.register();
		com.dinglebob.drifter.identity.Usernames.register();
		com.dinglebob.drifter.social.Lines.register();
		com.dinglebob.drifter.social.ChatReplies.register();
		com.dinglebob.drifter.social.DrifterSaidPayload.register();
		com.dinglebob.drifter.social.Typing.register();
		com.dinglebob.drifter.social.talk.Diary.register();
		com.dinglebob.drifter.social.talk.Topics.register();
		com.dinglebob.drifter.social.talk.Conversations.register();
		com.dinglebob.drifter.social.talk.PlayerTalk.register();
		com.dinglebob.drifter.social.talk.Spar.register();
		com.dinglebob.drifter.social.CrookGoal.Brawl.register();
		com.dinglebob.drifter.social.talk.PlayerChats.register();
		MobTargeting.register();
		com.dinglebob.drifter.perception.CreatureHearing.register();
		com.dinglebob.drifter.world.AnimalTempting.register();
		com.dinglebob.drifter.social.SocialWatcher.register();
		com.dinglebob.drifter.social.Manhunt.register();
		com.dinglebob.drifter.world.DrifterSimulation.register();
		com.dinglebob.drifter.debug.DrifterPerf.register();
		com.dinglebob.drifter.nav.WorldCopy.register();
		com.dinglebob.drifter.nav.PathWorkers.register();
		com.dinglebob.drifter.world.Battle.register();
		com.dinglebob.drifter.world.WhimTest.register();
		com.dinglebob.drifter.world.NavTest.register();
		com.dinglebob.drifter.trade.Trading.register();
		com.dinglebob.drifter.trade.DrifterDeals.register();
		com.dinglebob.drifter.perception.SightWork.register();
		com.dinglebob.drifter.world.RareEntities.register();
		com.dinglebob.drifter.perception.SoundHub.register();
		LOGGER.info("Drifter initialized");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
