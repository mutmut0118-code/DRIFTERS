package com.dinglebob.drifterciv.client;

import java.util.Arrays;

import com.dinglebob.drifterciv.CivConfig;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Friendship page (D2; the user, 2026-11-10: "customizable, with presets aswell"): a preset first (picking one sets
 * every slider), then every setting; touching one makes the preset Custom. In single player it changes the running world;
 * on a server only this computer's file.
 */
public class FriendshipScreen extends CivOptionsScreen {
	private final Screen parent;
	private CycleButton<CivConfig.Preset> preset;

	public FriendshipScreen(Screen parent) {
		super(parent, "Friendship");
		this.parent = parent;
	}

	@Override
	protected void addOptions() {
		this.note("Pick a preset, or set each one yourself. Hover over a setting to see what it does.");
		this.preset = tip(CycleButton.<CivConfig.Preset>builder(p -> Component.literal(p.label))
			.withValues(Arrays.asList(CivConfig.Preset.values()))
			.withInitialValue(CivConfig.friends().preset)
			.create(0, 0, WIDE, 20, Component.literal("Preset"), (button, chosen) -> {
				if (chosen != CivConfig.Preset.CUSTOM) {
					CivConfig.applyPreset(chosen);
					// (Every slider to the preset's values.)
					this.minecraft.setScreen(new FriendshipScreen(this.parent));
				}
			}), "Relaxed: friends in about a day. Normal: a few days. Slow burn: twice as long, smaller perks. Hardcore: hard won, cools fast.");
		this.row(this.preset, null);
		this.heading("Getting close");
		this.add("on", "pace");
		this.add("bestFriendHarder", "coolDays");
		this.add("agreeWeight", "disagreeWeight");
		this.add("spamFalloff", "announce");
		this.heading("What friends do");
		this.add("perkWelcome", "perkFood");
		this.add("perkInvite", "perkBackup");
		this.add("perkTips", "tipDays");
		this.add("perkWarnings", "whisper");
		this.add("favors", "favorDays");
		this.add("discount", "townsOnly");
	}

	private void add(String left, String right) {
		this.row(this.widget(CivConfig.setting(left)), this.widget(CivConfig.setting(right)));
	}

	private AbstractWidget widget(CivConfig.Setting s) {
		if (s.bool()) {
			return tip(CycleButton.onOffBuilder(s.value() >= 0.5).create(0, 0, WIDTH, 20, Component.literal(s.label()), (button, on) -> {
				s.change(on ? 1 : 0);
				this.preset.setValue(CivConfig.Preset.CUSTOM);
			}), s.tip());
		}

		return tip(new StepSlider(s.label(), s.min(), s.max(), s.step(), s.value(), s.shown(), v -> {
			s.change(v);
			if (this.preset != null) {
				this.preset.setValue(CivConfig.Preset.CUSTOM);
			}
		}), s.tip());
	}
}
