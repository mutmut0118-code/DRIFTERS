package com.dinglebob.drifter.client.config;

import com.dinglebob.drifter.DrifterConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Performance settings (milestone 16: "1000 drifters in a world"): how many of the computer's threads Drifters' path
 * searches and eye rays may use besides the server's own. The same as {@code /drifter config paththreads}.
 */
public class DrifterPerformanceScreen extends Screen {
	private final Screen parent;

	public DrifterPerformanceScreen(Screen parent) {
		super(Component.literal("Drifter performance"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - DrifterConfigScreen.WIDTH / 2;
		int y = 44;
		int processors = Runtime.getRuntime().availableProcessors();
		int automatic = Math.max(1, Math.min(6, processors / 4));
		this.addRenderableWidget(new DrifterConfigScreen.IntSlider(left, y, "Helper threads", -1, Math.min(DrifterConfig.MAX_PATH_THREADS, processors),
			DrifterConfig.pathThreads(), v -> v < 0 ? "automatic (" + automatic + ")" : v == 0 ? "off" : String.valueOf(v), DrifterConfig::setPathThreads));
		this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
			.bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
		graphics.drawCenteredString(this.font, Component.literal("Threads for Drifters' route-finding and eyes, besides the server's own."),
			this.width / 2, 22, 0xA0A0A0);
		graphics.drawCenteredString(this.font, Component.literal("Off: all on the server thread, as before (slower with many Drifters)."),
			this.width / 2, 72, 0xA0A0A0);
	}

	@Override
	public void onClose() {
		DrifterConfig.save();
		if (this.minecraft != null) {
			this.minecraft.setScreen(this.parent);
		}
	}
}
