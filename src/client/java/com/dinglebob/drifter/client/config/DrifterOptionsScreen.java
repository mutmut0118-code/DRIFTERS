package com.dinglebob.drifter.client.config;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.DrifterConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A page of Drifter settings, laid out like the game's own option screens (the user, 2026-10-20: "some stuff is off the
 * screen and it kind of looks a bit ghetto, i would have to adjust my scale to reach some options"): a title bar, a
 * scrolling list of settings two to a row under section headings, a tooltip on each saying what it does, and Done
 * always on screen. Changes apply at once; the file is saved when the page closes.
 */
abstract class DrifterOptionsScreen extends OptionsSubScreen {
	/** A setting's width (two to a row, as the game's options are), and a full row's. */
	static final int WIDTH = 150;
	static final int WIDE = 310;

	DrifterOptionsScreen(Screen parent, String title) {
		super(parent, Minecraft.getInstance().options, Component.literal(title));
	}

	/** A section heading. */
	protected void heading(String text) {
		this.list.addSmall(new StringWidget(WIDE, 20, Component.literal(text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), this.font).alignCenter(), null);
	}

	/** A line or two of grey explanation, centred across the row. */
	protected void note(String text) {
		MultiLineTextWidget note = new MultiLineTextWidget(Component.literal(text).withStyle(ChatFormatting.GRAY), this.font) {
			@Override
			public int getWidth() {
				return WIDE;
			}
		};
		this.list.addSmall(note.setMaxWidth(WIDE).setMaxRows(2).setCentered(true), null);
	}

	/** Two settings side by side (or one). */
	protected void row(AbstractWidget left, @Nullable AbstractWidget right) {
		this.list.addSmall(left, right);
	}

	/** A button to another page. */
	protected Button page(String label, String tooltip, java.util.function.Supplier<Screen> screen) {
		return tip(Button.builder(Component.literal(label), button -> this.minecraft.setScreen(screen.get())).width(WIDTH).build(), tooltip);
	}

	/** What a setting does, shown when hovered. */
	static <T extends AbstractWidget> T tip(T widget, String tooltip) {
		widget.setTooltip(Tooltip.create(Component.literal(tooltip)));
		return widget;
	}

	static CycleButton<Boolean> toggle(String label, boolean value, Consumer<Boolean> set, String tooltip) {
		return tip(CycleButton.onOffBuilder(value).create(0, 0, WIDTH, 20, Component.literal(label), (button, on) -> set.accept(on)), tooltip);
	}

	static IntSlider slider(String label, int min, int max, int value, IntFunction<String> text, IntConsumer set, String tooltip) {
		return tip(new IntSlider(label, min, max, value, text, set), tooltip);
	}

	@Override
	public void onClose() {
		DrifterConfig.save();
		super.onClose();
	}

	/** A slider over whole numbers, showing its label and value, setting the value as it moves. */
	static final class IntSlider extends AbstractSliderButton {
		private final String label;
		private final int min;
		private final int max;
		private final IntFunction<String> text;
		private final IntConsumer set;

		IntSlider(String label, int min, int max, int value, IntFunction<String> text, IntConsumer set) {
			super(0, 0, WIDTH, 20, Component.empty(), (Mth.clamp(value, min, max) - min) / (double)Math.max(1, max - min));
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
