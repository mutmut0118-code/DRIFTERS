package com.dinglebob.drifterciv.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifterciv.Citizens;
import com.dinglebob.drifterciv.WorldBuild;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;

/**
 * "Who are you?" (the start-of-world menu; the user, 2026-11-11: "we get a menu, picking our name and pronouns, and a
 * nation we want to be a citizen of, or no nation at all"): a name, pronouns, and the world's nations to pick from, each
 * told of in a few words, with "No nation" last; the towns being built shown along the bottom meanwhile. The first time
 * it can't be closed; opened again from the journal it changes the name and pronouns only. Made of the game's own parts
 * (its menu background, text box, buttons, font; a nation's banner is the banner item).
 */
final class WhoScreen extends Screen {
	private static final String[] PRONOUNS = {"they", "he", "she"};
	private static final int LEFT_WIDTH = 104;
	private static final int GAP = 10;
	private static final int TOP = 44;

	private Citizens.Menu menu;
	private EditBox name;
	private Button pronounsButton;
	private Button begin;
	private String typed;
	private int pronouns;
	/** The card picked: -1 none yet, the nations in order, then "No nation". */
	private int picked = -1;
	private int scroll;
	private String refused = "";
	private boolean sent;
	@Nullable
	private WorldBuild.Progress progress;
	/** Debug (DRIFTER_CIV_SCREEN=who@ticks:card[:name]): shot after this many ticks open, then this card picked and begun. */
	static int debugAt = -1;
	static int debugCard = -1;
	static String debugName = "";
	private int open;

	WhoScreen(Citizens.Menu menu, @Nullable WorldBuild.Progress progress) {
		super(Component.literal("Who are you?"));
		this.menu = menu;
		this.progress = progress;
		this.typed = menu.name();
		for (int i = 0; i < PRONOUNS.length; i++) {
			if (PRONOUNS[i].equals(menu.pronouns())) {
				this.pronouns = i;
			}
		}
	}

	/** The nations came (they were still being planned), or the menu was sent again: what's typed is kept. */
	void update(Citizens.Menu menu) {
		this.menu = menu;
		this.picked = -1;
		this.scroll = 0;
		this.sent = false;
		this.rebuildWidgets();
	}

	/** The world's story came: its button's there now. */
	void storyCame() {
		this.rebuildWidgets();
	}

	private boolean story() {
		return this.menu.first() && !DrifterCivilizationsClient.story.isEmpty();
	}

	void progress(WorldBuild.Progress progress) {
		this.progress = progress;
	}

	@Nullable
	WorldBuild.Progress progress() {
		return this.progress;
	}

	boolean first() {
		return this.menu.first();
	}

	/** The server's answer: taken (the screen goes) or why not. */
	void refused(String why) {
		this.refused = why;
		this.sent = false;
	}

	private boolean nations() {
		return this.menu.first() && (this.menu.drawing() || !this.menu.cards().isEmpty());
	}

	private int cards() {
		return this.menu.cards().size() + 1;
	}

	private int left() {
		return this.nations() ? this.width / 2 - 152 : this.width / 2 - LEFT_WIDTH / 2;
	}

	private int listLeft() {
		return this.left() + LEFT_WIDTH + GAP;
	}

	private int listWidth() {
		return 304 - LEFT_WIDTH - GAP;
	}

	private int listBottom() {
		return this.height - (this.menu.building() ? 62 : 40);
	}

	@Override
	protected void init() {
		if (this.name != null) {
			this.typed = this.name.getValue();
		}

		int x = this.left();
		this.name = new EditBox(this.font, x, TOP + 12, LEFT_WIDTH, 18, Component.literal("Name"));
		this.name.setMaxLength(16);
		this.name.setValue(this.typed);
		this.name.setHint(Component.literal(this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getGameProfile().getName() : ""));
		this.name.setResponder(text -> this.refused = "");
		this.addRenderableWidget(this.name);
		this.pronounsButton = this.addRenderableWidget(Button.builder(this.pronounsLabel(), button -> {
			this.pronouns = (this.pronouns + 1) % PRONOUNS.length;
			button.setMessage(this.pronounsLabel());
		}).bounds(x, TOP + 36, LEFT_WIDTH, 20).build());
		if (this.story()) {
			this.addRenderableWidget(Button.builder(Component.literal("The story so far"), button -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(new StoryScreen(this));
				}
			}).bounds(x, TOP + 60, LEFT_WIDTH, 20).build());
		}

		this.begin = this.addRenderableWidget(Button.builder(Component.literal(this.menu.first() ? "Begin" : "Done"), button -> this.choose())
			.bounds(this.width / 2 - 60, this.listBottom() + 6, 120, 20).build());
		this.setInitialFocus(this.name);
	}

	private Component pronounsLabel() {
		return Component.literal("Pronouns: " + PRONOUNS[this.pronouns]);
	}

	private void choose() {
		if (this.sent || !ClientPlayNetworking.canSend(Citizens.Choice.TYPE)) {
			return;
		}

		String nation = this.picked >= 0 && this.picked < this.menu.cards().size() ? this.menu.cards().get(this.picked).id() : "";
		this.sent = true;
		ClientPlayNetworking.send(new Citizens.Choice(this.name.getValue(), PRONOUNS[this.pronouns], nation));
	}

	@Override
	public void tick() {
		super.tick();
		// (A nation, or none, picked first; none to pick from: straight on.)
		this.begin.active = !this.sent && (!this.nations() || !this.menu.drawing() && this.picked >= 0);
		if (debugAt >= 0 && !this.menu.drawing() && this.minecraft != null) {
			this.open++;
			if (this.open == debugAt) {
				if (!debugName.isEmpty()) {
					this.name.setValue(debugName);
				}

				this.picked = Math.min(debugCard < 0 ? this.cards() - 1 : debugCard, this.cards() - 1);
			} else if (this.open == debugAt + 5 && this.story()) {
				this.minecraft.setScreen(new StoryScreen(this));
			} else if (this.open == debugAt + 10) {
				net.minecraft.client.Screenshot.grab(this.minecraft.gameDirectory, "who_" + (!this.menu.first() ? "change" : debugCard < 0 ? "none" : debugCard) + ".png",
					this.minecraft.getMainRenderTarget(), message -> {
					});
			} else if (this.open == debugAt + 20) {
				this.choose();
			}
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (this.menu.first()) {
			this.renderPanorama(graphics, partialTick);
			renderMenuBackgroundTexture(graphics, MENU_BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.height);
		} else {
			super.renderBackground(graphics, mouseX, mouseY, partialTick);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int middle = this.width / 2;
		graphics.drawCenteredString(this.font, this.title, middle, 14, 0xFFFFFF);
		graphics.drawCenteredString(this.font, Component.literal(this.menu.first() ? "Before you step into the world" : "What the world calls you"), middle, 26,
			0xA0A0A0);
		int x = this.left();
		graphics.drawString(this.font, "Name", x, TOP, 0xA0A0A0);
		int y = TOP + (this.story() ? 86 : 62);
		if (!this.refused.isEmpty()) {
			for (FormattedCharSequence part : this.font.split(Component.literal(this.refused), LEFT_WIDTH)) {
				graphics.drawString(this.font, part, x, y, 0xFF5555);
				y += 10;
			}
		}

		// Them, as in the inventory, where there's room under the name.
		int bottom = this.listBottom();
		if (this.minecraft != null && this.minecraft.player != null && bottom - y >= 56) {
			int size = Math.min(34, (bottom - y) / 2 - 2);
			InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x, y, x + LEFT_WIDTH, bottom, size, 0.0625F, mouseX, mouseY, this.minecraft.player);
		}

		if (this.nations()) {
			this.list(graphics, mouseX, mouseY);
		}

		if (this.menu.building() && this.progress != null) {
			this.bar(graphics);
		}
	}

	/** Each card's lines, wrapped. */
	private List<FormattedCharSequence> lines(int card) {
		List<String> lines = card < this.menu.cards().size() ? this.menu.cards().get(card).lines()
			: List.of(this.menu.building() ? "You start on the road, a stranger to everyone." : "A stranger to everyone, as you are now.");
		List<FormattedCharSequence> out = new ArrayList<>();
		for (String line : lines) {
			out.addAll(this.font.split(Component.literal(line), this.listWidth() - 30));
		}

		return out;
	}

	private int cardHeight(int card) {
		return 16 + this.lines(card).size() * 10 + 4;
	}

	private void list(GuiGraphics graphics, int mouseX, int mouseY) {
		int x = this.listLeft();
		int width = this.listWidth();
		int bottom = this.listBottom();
		graphics.drawString(this.font, "Where are you from?", x, TOP, 0xA0A0A0);
		int top = TOP + 12;
		graphics.fill(x - 1, top - 1, x + width + 1, bottom + 1, 0xFF000000);
		graphics.fill(x, top, x + width, bottom, 0xFF202020);
		if (this.menu.drawing()) {
			graphics.drawCenteredString(this.font, Component.literal("The nations are being drawn up..."), x + width / 2, (top + bottom) / 2 - 4, 0xA0A0A0);
			return;
		}

		int total = 0;
		for (int i = 0; i < this.cards(); i++) {
			total += this.cardHeight(i);
		}

		this.scroll = Math.max(0, Math.min(this.scroll, total - (bottom - top)));
		graphics.enableScissor(x, top, x + width, bottom);
		int y = top - this.scroll;
		for (int i = 0; i < this.cards(); i++) {
			int height = this.cardHeight(i);
			boolean over = mouseX >= x && mouseX < x + width && mouseY >= Math.max(top, y) && mouseY < Math.min(bottom, y + height);
			if (i == this.picked) {
				graphics.fill(x, y, x + width, y + height, 0xFF3A3A3A);
				graphics.renderOutline(x, y, width, height, 0xFFFFFFFF);
			} else if (over) {
				graphics.fill(x, y, x + width, y + height, 0xFF2C2C2C);
			}

			boolean none = i >= this.menu.cards().size();
			if (!none) {
				Citizens.Card card = this.menu.cards().get(i);
				graphics.renderItem(new ItemStack(BannerBlock.byColor(DyeColor.byId(card.colour()))), x + 4, y + 2);
				graphics.drawString(this.font, card.name(), x + 26, y + 5, 0xFFFFFF);
			} else {
				graphics.drawString(this.font, "No nation", x + 26, y + 5, 0xFFFFFF);
			}

			int textY = y + 17;
			for (FormattedCharSequence line : this.lines(i)) {
				graphics.drawString(this.font, line, x + 26, textY, 0xA0A0A0);
				textY += 10;
			}

			y += height;
		}

		graphics.disableScissor();
		if (total > bottom - top) {
			// (A bar at the side: how far down the list it is.)
			int track = bottom - top;
			int thumb = Math.max(12, track * track / total);
			int at = top + (track - thumb) * this.scroll / Math.max(1, total - track);
			graphics.fill(x + width - 3, at, x + width, at + thumb, 0xFF808080);
		}
	}

	/** The world being built behind it, as on {@link WorldBuildScreen}. */
	private void bar(GuiGraphics graphics) {
		int middle = this.width / 2;
		int barWidth = Math.min(240, this.width - 40);
		int left = middle - barWidth / 2;
		int barTop = this.height - 14;
		graphics.drawCenteredString(this.font, Component.literal(this.progress.stage()), middle, barTop - 12, 0xA0A0A0);
		graphics.fill(left - 1, barTop - 1, left + barWidth + 1, barTop + 7, 0xFF000000);
		graphics.fill(left, barTop, left + barWidth, barTop + 6, 0xFF404040);
		if (this.progress.total() > 0) {
			int filled = (int)((long)barWidth * Math.min(this.progress.done(), this.progress.total()) / this.progress.total());
			graphics.fill(left, barTop, left + filled, barTop + 6, 0xFF6FBF4A);
		} else {
			int sweep = (int)(System.currentTimeMillis() / 8 % (2L * barWidth));
			int at = sweep < barWidth ? sweep : 2 * barWidth - sweep;
			graphics.fill(left + Math.max(0, at - 20), barTop, left + Math.min(barWidth, at + 20), barTop + 6, 0xFF6FBF4A);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && this.nations() && !this.menu.drawing()) {
			int x = this.listLeft();
			int top = TOP + 12;
			int bottom = this.listBottom();
			if (mouseX >= x && mouseX < x + this.listWidth() && mouseY >= top && mouseY < bottom) {
				int y = top - this.scroll;
				for (int i = 0; i < this.cards(); i++) {
					int height = this.cardHeight(i);
					if (mouseY >= y && mouseY < y + height) {
						this.picked = i;
						if (this.minecraft != null) {
							this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
								net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
						}

						return true;
					}

					y += height;
				}
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.nations()) {
			this.scroll -= (int)(scrollY * 16);
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if ((keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) && this.begin.active) {
			this.choose();
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !this.menu.first();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
