package com.dinglebob.drifterciv.client;

import net.minecraft.client.gui.screens.Screen;

/** Drifter: Civilizations' settings, from Mod Menu: its pages (for now, Friendship). Its older settings are on Drifter's screen. */
public class CivConfigScreen extends CivOptionsScreen {
	public CivConfigScreen(Screen parent) {
		super(parent, "Drifter: Civilizations settings");
	}

	@Override
	protected void addOptions() {
		this.note("Towns, nations and villages are on Drifter's own settings screen. Changes apply at once (also: /drifterciv config).");
		this.heading("Pages");
		this.row(this.page("Friendship...", "Making friends with Drifters: presets, how fast, cooling off, each perk, favors.", () -> new FriendshipScreen(this)), null);
	}
}
