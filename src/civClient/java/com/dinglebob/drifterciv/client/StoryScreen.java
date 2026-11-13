package com.dinglebob.drifterciv.client;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * "The story so far" (the user, 2026-11-12: "there should be more lore before i load the world": the world's history):
 * the world's written past to read before it's played, opened from "Who are you?" and back to it. The same words are
 * under the bar on the loading screen ({@link WorldBuildScreen}). Made of the game's own parts.
 */
final class StoryScreen extends Screen {
	final Screen parent;
	private int scroll;

	StoryScreen(Screen parent) {
		super(Component.literal("The story so far"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> this.onClose()).bounds(this.width / 2 - 60, this.height - 28, 120, 20).build());
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.setScreen(this.parent);
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderPanorama(graphics, partialTick);
		renderMenuBackgroundTexture(graphics, MENU_BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.height);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
		graphics.drawCenteredString(this.font, Component.literal("What everyone in this world knows"), this.width / 2, 26, 0xA0A0A0);
		int width = Math.min(340, this.width - 40);
		this.scroll = text(graphics, this.font, DrifterCivilizationsClient.story, this.width / 2 - width / 2, 44, width, this.height - 36, this.scroll);
	}

	private int open;

	@Override
	public void tick() {
		super.tick();
		// (Debug, DRIFTER_CIV_SCREEN=who@...: a picture of it, then back.)
		if (WhoScreen.debugAt >= 0 && this.minecraft != null) {
			this.open++;
			if (this.open == 20) {
				net.minecraft.client.Screenshot.grab(this.minecraft.gameDirectory, "who_story.png", this.minecraft.getMainRenderTarget(), message -> {
				});
			} else if (this.open == 30) {
				this.onClose();
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		this.scroll -= (int)(scrollY * 16);
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/**
	 * The story in a box from top to bottom, scrolled this far: headings (lines that begin with #) white, the rest grey, a
	 * bar at the side if there's more than fits. Gives back the scroll, kept within what there is.
	 */
	static int text(GuiGraphics graphics, Font font, List<String> story, int x, int top, int width, int bottom, int scroll) {
		graphics.fill(x - 1, top - 1, x + width + 1, bottom + 1, 0xFF000000);
		graphics.fill(x, top, x + width, bottom, 0xFF202020);
		int total = 6;
		for (String line : story) {
			total += line.startsWith("#") ? 16 : font.split(Component.literal(line), width - 16).size() * 10 + 6;
		}

		scroll = Math.max(0, Math.min(scroll, total - (bottom - top)));
		graphics.enableScissor(x, top, x + width, bottom);
		int y = top + 6 - scroll;
		for (String line : story) {
			if (line.startsWith("#")) {
				graphics.drawString(font, line.substring(1), x + 8, y + 3, 0xFFFFFF);
				y += 16;
			} else {
				for (FormattedCharSequence part : font.split(Component.literal(line), width - 16)) {
					graphics.drawString(font, part, x + 8, y, 0xC0C0C0);
					y += 10;
				}

				y += 6;
			}
		}

		graphics.disableScissor();
		if (total > bottom - top) {
			int track = bottom - top;
			int thumb = Math.max(12, track * track / total);
			int at = top + (track - thumb) * scroll / Math.max(1, total - track);
			graphics.fill(x + width - 3, at, x + width, at + thumb, 0xFF808080);
		}

		return scroll;
	}
}
