package com.dinglebob.drifter.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's settings button for Drifter (only loaded when Mod Menu is installed: it's an optional entrypoint). */
public class DrifterModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return DrifterConfigScreen::new;
	}
}
