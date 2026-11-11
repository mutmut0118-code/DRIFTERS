package com.dinglebob.drifterciv.client;

import net.minecraft.client.gui.screens.Screen;

/** Drifter: Civilizations' settings, from Mod Menu: its pages (for now, Friendship). Its older settings are on Drifter's screen. */
public class CivConfigScreen extends CivOptionsScreen {
	public CivConfigScreen(Screen parent) {
		super(parent, "Drifter: Civilizations settings");
	}

	private static net.minecraft.network.chat.Component paceLabel() {
		return net.minecraft.network.chat.Component.literal("World events: " + com.dinglebob.drifterciv.CivConfig.WORLD_PACES[com.dinglebob.drifterciv.CivConfig.worldPace()]);
	}

	@Override
	protected void addOptions() {
		this.note("Towns, nations and villages are on Drifter's own settings screen. Changes apply at once (also: /drifterciv config).");
		this.heading("The world");
		this.row(net.minecraft.client.gui.components.Button.builder(paceLabel(), button -> {
			com.dinglebob.drifterciv.CivConfig.setWorldPace((com.dinglebob.drifterciv.CivConfig.worldPace() + 1) % com.dinglebob.drifterciv.CivConfig.WORLD_PACES.length);
			button.setMessage(paceLabel());
		}).tooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal(
			"How often things happen between nations where nobody's near: pacts, quarrels, wars, towns taken. Quiet: as in the world's written past. "
				+ "Normal: something every week or two, a war every fifty to a hundred days. Lively: about twice that."))).build(), null);
		this.heading("Pages");
		this.row(this.page("Friendship...", "Making friends with Drifters: presets, how fast, cooling off, each perk, favors.", () -> new FriendshipScreen(this)), null);
	}
}
