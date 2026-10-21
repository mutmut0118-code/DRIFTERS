package com.dinglebob.drifter.client.config;

import com.dinglebob.drifter.DrifterConfig;

import net.minecraft.client.gui.screens.Screen;

/**
 * The spawn settings (user's request, 2026-10-17: "make all the spawn chances for each event configurable"): how common
 * each kind of new Drifter is, and the most loaded at once. The same ones as {@code /drifter config spawn}.
 */
public class DrifterSpawnScreen extends DrifterOptionsScreen {
	public DrifterSpawnScreen(Screen parent) {
		super(parent, "Drifter spawning");
	}

	@Override
	protected void addOptions() {
		this.note("New chunks only. Past the most loaded at once, no new ones are made (a camp is left empty).");
		this.heading("How common");
		DrifterConfig.Spawn[] all = DrifterConfig.Spawn.values();
		for (int i = 0; i < all.length; i += 2) {
			this.row(this.slider(all[i]), i + 1 < all.length ? this.slider(all[i + 1]) : null);
		}

		// How new Drifters look: real players' skins, downloaded like a multiplayer server's (user, 2026-10-20).
		this.heading("Looks");
		this.row(toggle("Real player skins", DrifterConfig.realSkins(), DrifterConfig::setRealSkins,
			"Real players' skins, downloaded as a multiplayer server does (needs the internet; default skins without it)."), null);
	}

	/** Each setting's short name here (the full one is in its tooltip), and its value in few words. */
	private static final java.util.Map<String, String> SHORT = java.util.Map.ofEntries(
		java.util.Map.entry("max_drifters", "Most at once"), java.util.Map.entry("camps", "Camps"), java.util.Map.entry("camp_spacing", "Camp spacing"),
		java.util.Map.entry("camps_lived_in", "Camps lived in"), java.util.Map.entry("camp_houses", "Camp houses"), java.util.Map.entry("wanderers", "Wanderers"),
		java.util.Map.entry("villages", "Villages lived in"), java.util.Map.entry("outposts", "Bandit outposts"), java.util.Map.entry("delvers", "Delved mineshafts"),
		java.util.Map.entry("house_designs", "House designs"), java.util.Map.entry("corpse_days", "Corpses last"));

	private IntSlider slider(DrifterConfig.Spawn setting) {
		String name = SHORT.getOrDefault(setting.id, setting.label);
		return slider(name, setting.min, setting.max, DrifterConfig.spawn(setting), v -> shortValue(setting, v), value -> DrifterConfig.setSpawn(setting, value),
			setting.label + ": " + setting.text.apply(DrifterConfig.spawn(setting)) + " now (/drifter config spawn " + setting.id + ").");
	}

	/** The value without its long tail ("12% of spots": "12%"; "4 per 1000 chunks": "4/1000"). */
	private static String shortValue(DrifterConfig.Spawn setting, int value) {
		String text = setting.text.apply(value);
		return text.replace(" of spots", "").replace(" per 1000 chunks", " /1000").replace(" Drifters", "").replace(" designs", "");
	}
}
