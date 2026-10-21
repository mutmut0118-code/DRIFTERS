package com.dinglebob.drifterciv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterEvents;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Drifter: Civilizations (milestone 22, docs/CIVILIZATION.md): an addon for Drifter, in the same repository (the user,
 * 2026-10-21: "same repo, call it Drifter: Civilizations, go ahead"). It reaches into Drifter only through
 * {@code com.dinglebob.drifter.api} (docs/API.md), and asks for exactly the Drifter it was built with.
 *
 * <p>For now an empty shell that loads: `/drifterciv` says it's there and what it has heard through Drifter's API.
 */
public final class DrifterCivilizations implements ModInitializer {
	public static final String MOD_ID = "drifter_civ";
	public static final Logger LOGGER = LoggerFactory.getLogger("Drifter: Civilizations");

	/** Drifter deaths heard of through the API this run (the door working end to end). */
	private static int deathsHeard;
	/** News Drifters passed on to each other this run. */
	private static int newsTold;
	/** Trades between Drifters this run. */
	private static int trades;

	@Override
	public void onInitialize() {
		DrifterEvents.DIED.register((drifter, source, comesBack) -> deathsHeard++);
		DrifterEvents.HEARD_NEWS.register((drifter, news, from) -> newsTold++);
		DrifterEvents.TRADED.register((asker, giver, paid, got) -> trades++);
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(Commands.literal("drifterciv")
			.executes(ctx -> {
				ctx.getSource().sendSuccess(() -> Component.literal("Drifter: Civilizations " + version() + " on Drifter " + DrifterApi.version()
					+ " (API " + DrifterApi.API_VERSION + "). Nothing to see yet: the towns come next. Drifter deaths heard of this run: " + deathsHeard
					+ ", news passed between Drifters: " + newsTold + ", trades between Drifters: " + trades), false);
				return 1;
			})));
		LOGGER.info("Drifter: Civilizations {} loaded, on Drifter {} (API {})", version(), DrifterApi.version(), DrifterApi.API_VERSION);
	}

	public static String version() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?");
	}
}
