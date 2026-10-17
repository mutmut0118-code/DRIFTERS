package com.dinglebob.drifter.client.config;

import com.dinglebob.drifter.DrifterConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The spawn settings (user's request, 2026-10-17: "make all the spawn chances for each event configurable"): how common
 * each kind of new Drifter is, and the most loaded at once. The same ones as {@code /drifter config spawn}.
 */
public class DrifterSpawnScreen extends Screen {
	private static final int ROW = 22;
	private final Screen parent;

	public DrifterSpawnScreen(Screen parent) {
		super(Component.literal("Drifter spawning"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - DrifterConfigScreen.WIDTH - 5;
		int right = this.width / 2 + 5;
		int y = 34;
		DrifterConfig.Spawn[] all = DrifterConfig.Spawn.values();
		for (int i = 0; i < all.length; i++) {
			DrifterConfig.Spawn setting = all[i];
			this.addRenderableWidget(new DrifterConfigScreen.IntSlider(i % 2 == 0 ? left : right, y, setting.label, setting.min, setting.max,
				DrifterConfig.spawn(setting), setting.text, value -> DrifterConfig.setSpawn(setting, value)));
			if (i % 2 == 1) {
				y += ROW;
			}
		}

		this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
			.bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
		graphics.drawCenteredString(this.font, Component.literal("New chunks only. Past the most loaded, no new ones are made (a camp is left empty)."),
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
