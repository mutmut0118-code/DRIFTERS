package com.dinglebob.drifterciv.client;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifterciv.TownBanner;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * A town's name at the top of the screen as you come into it (the user, 2026-11-14: "the town name popping up at the top of
 * the screen when you enter it (a little fade in and fade out)"): large, a thin rule under it, fading in, held a moment,
 * fading out.
 */
final class TownBannerHud {
	private TownBannerHud() {
	}

	/** Fading in, held, fading out (milliseconds). */
	private static final long IN = 700;
	private static final long HOLD = 2600;
	private static final long OUT = 1200;
	private static final float SCALE = 2.0F;

	@Nullable
	private static String name;
	private static long since;

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(TownBanner.Show.TYPE, (show, context) -> {
			name = show.name();
			since = System.currentTimeMillis();
		});
		HudRenderCallback.EVENT.register(TownBannerHud::draw);
	}

	private static void draw(GuiGraphics graphics, net.minecraft.client.DeltaTracker tick) {
		String shown = name;
		Minecraft client = Minecraft.getInstance();
		if (shown == null || client.options.hideGui) {
			return;
		}

		long t = System.currentTimeMillis() - since;
		if (t > IN + HOLD + OUT) {
			name = null;
			return;
		}

		float alpha = t < IN ? t / (float)IN : t < IN + HOLD ? 1.0F : 1.0F - (t - IN - HOLD) / (float)OUT;
		alpha = Mth.clamp(alpha, 0.0F, 1.0F);
		// (The font draws anything nearly see-through as solid: below this, not at all.)
		int a = (int)(alpha * 255);
		if (a < 8) {
			return;
		}

		Font font = client.font;
		int width = graphics.guiWidth();
		int y = 22;
		graphics.pose().pushPose();
		graphics.pose().translate(width / 2.0F, y, 0);
		graphics.pose().scale(SCALE, SCALE, 1.0F);
		graphics.drawString(font, shown, -font.width(shown) / 2, 0, a << 24 | 0xF2E6C8, true);
		graphics.pose().popPose();
		// (A thin rule under it, a little wider than the name, fading at its ends.)
		int half = (int)(font.width(shown) * SCALE / 2) + 18;
		int ruleY = y + (int)(font.lineHeight * SCALE) + 3;
		int middle = width / 2;
		for (int x = -half; x < half; x++) {
			float edge = 1.0F - Math.abs(x) / (float)half;
			int ra = (int)(a * Math.min(1.0F, edge * 2.5F) * 0.8F);
			if (ra > 0) {
				graphics.fill(middle + x, ruleY, middle + x + 1, ruleY + 1, ra << 24 | 0xF2E6C8);
			}
		}
	}
}
