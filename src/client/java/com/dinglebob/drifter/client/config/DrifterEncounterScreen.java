package com.dinglebob.drifter.client.config;

import com.dinglebob.drifter.DrifterConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Encounters (milestone 21): on or off, how often, and each kind on its own. In a world on a server, the server's
 * settings are what count (`/drifter config encounters ...`).
 */
public final class DrifterEncounterScreen extends Screen {
	private static final int WIDTH = DrifterConfigScreen.WIDTH;
	private static final int ROW = 24;
	/** Each kind's id and name here. */
	private static final String[][] KINDS = {
		{"bounty_hunters", "Hunters"}, {"scout", "Scouts"}, {"revenge", "Revenge"}, {"wanted", "Wanted"},
		{"injured_traveller", "Injured"}, {"thief", "Thieves"}, {"burglar", "Burglars"}, {"merchant", "Merchants"},
		{"ambush", "Ambushes"}, {"mugging", "Muggings"}, {"sniper", "Snipers"}, {"village_raid", "Raids"},
		{"watcher", "Watcher"}, {"duel", "Duels"}, {"race", "Races"}, {"feud", "Feuds"},
		{"hungry_traveller", "Hungry"}, {"trapped_miner", "Miners"}, {"escort", "Escorts"}, {"hostage", "Hostages"},
		{"lost_dog", "Lost dogs"}, {"horde", "Hordes"}, {"campfire_party", "Parties"}, {"mourner", "Mourners"},
		{"convoy", "Convoys"}, {"builders", "Builders"}};
	/** Each kind's switch: four a row (twenty-six of them: short names). */
	private static final int PER_ROW = 4;
	private static final int KIND_WIDTH = 104;

	private final Screen parent;

	public DrifterEncounterScreen(Screen parent) {
		super(Component.literal("Drifter encounters"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH - 5;
		int right = this.width / 2 + 5;
		int y = 40;
		this.addRenderableWidget(DrifterConfigScreen.toggle(left, y, "Encounters", DrifterConfig.encounters(), DrifterConfig::setEncounters));
		// In quarter days: a quarter of a day to ten days.
		this.addRenderableWidget(new DrifterConfigScreen.IntSlider(right, y, "About one every", 1, 40, (int)Math.round(DrifterConfig.encounterDays() * 4.0),
			v -> (v % 4 == 0 ? String.valueOf(v / 4) : String.valueOf(v / 4.0)) + (v == 4 ? " day" : " days"), v -> DrifterConfig.setEncounterDays(v / 4.0)));
		y += ROW + 8;
		int x0 = this.width / 2 - (KIND_WIDTH * PER_ROW + 4 * (PER_ROW - 1)) / 2;
		for (int i = 0; i < KINDS.length; i++) {
			String id = KINDS[i][0];
			this.addRenderableWidget(net.minecraft.client.gui.components.CycleButton.onOffBuilder(!DrifterConfig.encounterOff(id))
				.create(x0 + (i % PER_ROW) * (KIND_WIDTH + 4), y, KIND_WIDTH, 20, Component.literal(KINDS[i][1]), (button, on) -> DrifterConfig.setEncounterOff(id, !on)));
			if (i % PER_ROW == PER_ROW - 1) {
				// (Rows a little closer: seven of them above the Done button.)
				y += ROW - 2;
			}
		}

		this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
			.bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
		graphics.drawCenteredString(this.font, Component.literal("Random events with Drifters you meet once. Per player, in-game days."),
			this.width / 2, 20, 0xA0A0A0);
	}

	@Override
	public void onClose() {
		DrifterConfig.save();
		if (this.minecraft != null) {
			this.minecraft.setScreen(this.parent);
		}
	}
}
