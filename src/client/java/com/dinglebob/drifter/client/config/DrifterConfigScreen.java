package com.dinglebob.drifter.client.config;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import com.dinglebob.drifter.DrifterConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Drifter's settings in-game (user's wish; milestone 12 step 6), opened from Mod Menu: every setting in config/drifter.json,
 * the same ones the /drifter config commands change. Changes apply at once and are saved when the screen closes. In single
 * player they reach the running world; on a server they only change this computer's file (the server's file rules there).
 */
public class DrifterConfigScreen extends Screen {
	private static final int ROW = 22;
	static final int WIDTH = 150;
	private final Screen parent;

	public DrifterConfigScreen(Screen parent) {
		super(Component.literal("Drifter settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH - 5;
		int right = this.width / 2 + 5;
		int y = 34;

		this.addRenderableWidget(new IntSlider(left, y, "How far they see", DrifterConfig.MIN_SIGHT_CHUNKS, DrifterConfig.MAX_SIGHT_CHUNKS,
			DrifterConfig.sightChunks(), v -> v + " chunks", DrifterConfig::setSightChunks));
		this.addRenderableWidget(new IntSlider(right, y, "How far they plan routes", DrifterConfig.MIN_PATH_RANGE, DrifterConfig.MAX_PATH_RANGE,
			DrifterConfig.pathRange(), v -> v + " blocks", DrifterConfig::setPathRange));
		y += ROW;
		this.addRenderableWidget(new IntSlider(left, y, "Their field of view", DrifterConfig.MIN_FOV, DrifterConfig.MAX_FOV, DrifterConfig.fov(),
			v -> v + " degrees", DrifterConfig::setFov));
		this.addRenderableWidget(new IntSlider(right, y, "Kept running when away", 0, DrifterConfig.MAX_SIMULATED, DrifterConfig.simulatedDrifters(),
			v -> v + (v == 1 ? " Drifter" : " Drifters"), DrifterConfig::setSimulatedDrifters));
		y += ROW;
		this.addRenderableWidget(new IntSlider(left, y, "Aggression to players", 0, 100, DrifterConfig.playerAggression(),
			DrifterConfigScreen::aggressionText, DrifterConfig::setPlayerAggression));
		this.addRenderableWidget(new IntSlider(right, y, "Aggression to each other", 0, 100, DrifterConfig.drifterAggression(),
			DrifterConfigScreen::aggressionText, DrifterConfig::setDrifterAggression));
		y += ROW;
		this.addRenderableWidget(toggle(left, y, "Chat lines", DrifterConfig.chatLines(), DrifterConfig::setChatLines));
		this.addRenderableWidget(toggle(right, y, "Share reputation", DrifterConfig.shareReputation(), DrifterConfig::setShareReputation));
		y += ROW;
		this.addRenderableWidget(toggle(left, y, "Raid players' chests", DrifterConfig.raiding(), DrifterConfig::setRaiding));
		this.addRenderableWidget(toggle(right, y, "Thoughts in the log", DrifterConfig.logThoughts(), DrifterConfig::setLogThoughts));
		y += ROW;
		this.addRenderableWidget(toggle(left, y, "World-gen camps", DrifterConfig.worldgenCamps(), DrifterConfig::setWorldgenCamps));
		this.addRenderableWidget(toggle(right, y, "Old strip mines", DrifterConfig.worldgenMines(), DrifterConfig::setWorldgenMines));
		y += ROW;
		this.addRenderableWidget(toggle(left, y, "Torches in caves", DrifterConfig.worldgenCaveTorches(), DrifterConfig::setWorldgenCaveTorches));
		this.addRenderableWidget(toggle(right, y, "Drifter villages", DrifterConfig.worldgenVillages(), DrifterConfig::setWorldgenVillages));
		y += ROW;
		this.addRenderableWidget(toggle(left, y, "Bandit outposts", DrifterConfig.worldgenOutposts(), DrifterConfig::setWorldgenOutposts));
		this.addRenderableWidget(toggle(right, y, "Group wars and raids", DrifterConfig.drifterWars(), DrifterConfig::setDrifterWars));
		y += ROW;
		this.addRenderableWidget(toggle(left, y, "Whims (things for fun)", DrifterConfig.whims(), DrifterConfig::setWhims));
		this.addRenderableWidget(toggle(right, y, "Outpost Bandits grief", DrifterConfig.banditGriefing(), DrifterConfig::setBanditGriefing));
		y += ROW;
		this.addRenderableWidget(toggle(left, y, "Wandering Drifters", DrifterConfig.worldgenWanderers(), DrifterConfig::setWorldgenWanderers));
		this.addRenderableWidget(toggle(right, y, "Mineshaft delvers", DrifterConfig.worldgenDelvers(), DrifterConfig::setWorldgenDelvers));
		y += ROW;
		this.addRenderableWidget(toggle(left, y, "Bandits kill on sight", DrifterConfig.banditsKillOnSight(), DrifterConfig::setBanditsKillOnSight));
		this.addRenderableWidget(toggle(right, y, "Parkour jumps", DrifterConfig.parkour(), DrifterConfig::setParkour));
		y += ROW;
		this.addRenderableWidget(CycleButton.<DrifterConfig.OfflineProgression>builder(mode -> Component.literal(switch (mode) {
				case OFF -> "off";
				case GAME_TIME -> "game time";
				case REAL_TIME -> "real time";
			}))
			.withValues(DrifterConfig.OfflineProgression.values())
			.withInitialValue(DrifterConfig.offlineProgression())
			.create(left, y, WIDTH * 2 + 10, 20, Component.literal("Catch-up while the world was closed"),
				(button, mode) -> DrifterConfig.setOfflineProgression(mode)));

		// How common each kind of new Drifter is, and the most loaded at once: their own page.
		this.addRenderableWidget(Button.builder(Component.literal("Spawning..."), button -> this.minecraft.setScreen(new DrifterSpawnScreen(this)))
			.bounds(this.width / 2 - 100, this.height - 52, 200, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
			.bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
	}

	private static String aggressionText(int value) {
		return value <= 0 ? "peaceful" : value >= 100 ? "always hostile" : value == DrifterConfig.DEFAULT_AGGRESSION ? "normal (50%)" : value + "%";
	}

	private static CycleButton<Boolean> toggle(int x, int y, String label, boolean value, java.util.function.Consumer<Boolean> set) {
		return CycleButton.onOffBuilder(value).create(x, y, WIDTH, 20, Component.literal(label), (button, on) -> set.accept(on));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
		graphics.drawCenteredString(this.font, Component.literal("Also: /drifter config. World-gen settings apply to new chunks."),
			this.width / 2, 20, 0xA0A0A0);
	}

	@Override
	public void onClose() {
		DrifterConfig.save();
		if (this.minecraft != null) {
			this.minecraft.setScreen(this.parent);
		}
	}

	/** A slider over whole numbers, showing its label and value, setting the value as it moves. */
	static final class IntSlider extends AbstractSliderButton {
		private final String label;
		private final int min;
		private final int max;
		private final IntFunction<String> text;
		private final IntConsumer set;

		IntSlider(int x, int y, String label, int min, int max, int value, IntFunction<String> text, IntConsumer set) {
			super(x, y, WIDTH, 20, Component.empty(), (Mth.clamp(value, min, max) - min) / (double)Math.max(1, max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.text = text;
			this.set = set;
			this.updateMessage();
		}

		private int intValue() {
			return this.min + (int)Math.round(this.value * (this.max - this.min));
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.literal(this.label + ": " + this.text.apply(this.intValue())));
		}

		@Override
		protected void applyValue() {
			this.set.accept(this.intValue());
		}
	}
}
