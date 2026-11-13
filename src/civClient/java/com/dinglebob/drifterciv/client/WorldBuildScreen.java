package com.dinglebob.drifterciv.client;

import com.dinglebob.drifterciv.WorldBuild;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The loading screen while a new civilized world is built before it's played ({@link WorldBuild}; the user, 2026-10-30:
 * "it could be like an extra loading screen?"): the title screen's panorama behind, what it's on and how far through, a
 * bar. The world doesn't show through it, and it can't be closed; it goes when the server says the world's built.
 */
final class WorldBuildScreen extends Screen {
	private WorldBuild.Progress progress;

	WorldBuildScreen(WorldBuild.Progress progress) {
		super(Component.literal("Building the world"));
		this.progress = progress;
	}

	void update(WorldBuild.Progress progress) {
		this.progress = progress;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderPanorama(graphics, partialTick);
		renderMenuBackgroundTexture(graphics, MENU_BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.height);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int middle = this.width / 2;
		int top = this.height / 2 - 40;
		graphics.drawCenteredString(this.font, this.title, middle, top, 0xFFFFFF);
		graphics.drawCenteredString(this.font, Component.literal("Its nations, towns and roads, before you play"), middle, top + 14, 0xA0A0A0);
		graphics.drawCenteredString(this.font, Component.literal(this.progress.stage()), middle, top + 40, 0xFFFFFF);
		int barWidth = Math.min(240, this.width - 40);
		int left = middle - barWidth / 2;
		int barTop = top + 56;
		graphics.fill(left - 1, barTop - 1, left + barWidth + 1, barTop + 7, 0xFF000000);
		graphics.fill(left, barTop, left + barWidth, barTop + 6, 0xFF404040);
		if (this.progress.total() > 0) {
			int filled = (int)((long)barWidth * Math.min(this.progress.done(), this.progress.total()) / this.progress.total());
			graphics.fill(left, barTop, left + filled, barTop + 6, 0xFF6FBF4A);
		} else {
			// (Planning: no numbers yet, a block going back and forth.)
			int sweep = (int)(System.currentTimeMillis() / 8 % (2L * barWidth));
			int at = sweep < barWidth ? sweep : 2 * barWidth - sweep;
			graphics.fill(left + Math.max(0, at - 20), barTop, left + Math.min(barWidth, at + 20), barTop + 6, 0xFF6FBF4A);
		}

		int seconds = this.progress.seconds();
		graphics.drawCenteredString(this.font, Component.literal(seconds / 60 + ":" + String.format("%02d", seconds % 60)), middle, barTop + 14, 0xA0A0A0);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
