package com.dinglebob.drifterciv.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * A page of Drifter: Civilizations settings, laid out as Drifter's own pages are (the game's option screens: a title, a
 * scrolling list two to a row under headings, a tooltip on each, Done always on screen). Drifter's base page is its own,
 * so the addon has this one.
 */
abstract class CivOptionsScreen extends OptionsSubScreen {
	static final int WIDTH = 150;
	static final int WIDE = 310;

	CivOptionsScreen(Screen parent, String title) {
		super(parent, Minecraft.getInstance().options, Component.literal(title));
	}

	protected void heading(String text) {
		this.list.addSmall(new StringWidget(WIDE, 20, Component.literal(text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), this.font).alignCenter(), null);
	}

	protected void note(String text) {
		MultiLineTextWidget note = new MultiLineTextWidget(Component.literal(text).withStyle(ChatFormatting.GRAY), this.font) {
			@Override
			public int getWidth() {
				return WIDE;
			}
		};
		this.list.addSmall(note.setMaxWidth(WIDE).setMaxRows(2).setCentered(true), null);
	}

	protected void row(AbstractWidget left, @Nullable AbstractWidget right) {
		this.list.addSmall(left, right);
	}

	protected Button page(String label, String tooltip, java.util.function.Supplier<Screen> screen) {
		return tip(Button.builder(Component.literal(label), button -> this.minecraft.setScreen(screen.get())).width(WIDTH).build(), tooltip);
	}

	static <T extends AbstractWidget> T tip(T widget, String tooltip) {
		widget.setTooltip(Tooltip.create(Component.literal(tooltip)));
		return widget;
	}

	/** A slider over steps of a number, showing its label and value, setting it as it moves. */
	static final class StepSlider extends AbstractSliderButton {
		private final String label;
		private final double min;
		private final double max;
		private final double step;
		private final java.util.function.DoubleFunction<String> text;
		private final java.util.function.DoubleConsumer set;

		StepSlider(String label, double min, double max, double step, double value, java.util.function.DoubleFunction<String> text,
			java.util.function.DoubleConsumer set) {
			super(0, 0, WIDTH, 20, Component.empty(), (Math.max(min, Math.min(max, value)) - min) / Math.max(1e-9, max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.step = step;
			this.text = text;
			this.set = set;
			this.updateMessage();
		}

		private double number() {
			return Math.max(this.min, Math.min(this.max, this.min + Math.round(this.value * (this.max - this.min) / this.step) * this.step));
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.literal(this.label + ": " + this.text.apply(this.number())));
		}

		@Override
		protected void applyValue() {
			this.set.accept(this.number());
		}
	}
}
