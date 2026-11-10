package com.dinglebob.drifterciv.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's settings button for Drifter: Civilizations (loaded only when Mod Menu is installed: an optional entrypoint). */
public class CivModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return CivConfigScreen::new;
	}
}
