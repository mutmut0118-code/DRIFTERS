package com.dinglebob.drifterciv.client;

import java.util.ArrayList;
import java.util.List;

import com.dinglebob.drifterciv.Journal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

/**
 * The player's journal (the J key; docs/superpowers/specs/2026-11-11-player-journal-design.md; the user, 2026-11-11: an
 * open journal with bookmarks, "made only using vanilla resources"): two pages of the vanilla book side by side (the
 * left one mirrored, so their stitching meets at the spine), bookmarks along the top, the vanilla font in a book's ink.
 * The left page is always the player; the right one the chosen bookmark's lines, scrolled with the wheel; a line with
 * more to it opens on a click. What it shows is worked out by the server ({@link Journal}); this only draws it.
 */
final class JournalScreen extends Screen {
	private static final ResourceLocation BOOK = ResourceLocation.withDefaultNamespace("textures/gui/book.png");
	/** The book texture: the page's leather from 20 to 166 across and 1 to 181 down, of an image 192 square in a 256 sheet. */
	private static final int IMAGE = 192;
	private static final int COVER_LEFT = 20;
	private static final int COVER_RIGHT = 166;
	private static final int COVER_BOTTOM = 182;
	/** A page's writing: this far in from the spine, this wide, from this far down to this far down. */
	private static final int INSET = 17;
	private static final int TEXT_WIDTH = 112;
	private static final int TEXT_TOP = 16;
	private static final int TEXT_BOTTOM = 164;
	private static final int ROW = 11;
	private static final int TAB_HEIGHT = 13;
	private static final int INK = 0xFF000000;
	private static final int GOOD = 0xFF2A6A2A;
	private static final int BAD = 0xFFAA0000;
	private static final int FAINT = 0xFF8A7A5A;
	private static final int RULE = 0xFFD9CBA6;
	private static final int PAPER = 0xFFFDF8E8;
	private static final int PAPER_DARK = 0xFFD6C49A;
	private static final int LEATHER = 0xFF6B3A1A;

	private Journal.Book book;
	private int tab;
	/** The line opened on the right page (-1: the list). */
	private int open = -1;
	private int scroll;

	JournalScreen(Journal.Book book, int tab, int open) {
		super(Component.literal("Journal"));
		this.book = book;
		this.tab = Math.max(0, Math.min(tab, book.tabs().size() - 1));
		this.open = open >= 0 && open < this.lines().size() && !this.lines().get(open).detail().isEmpty() ? open : -1;
	}

	void update(Journal.Book book) {
		this.book = book;
		this.tab = Math.max(0, Math.min(this.tab, book.tabs().size() - 1));
		if (this.open >= this.lines().size()) {
			this.open = -1;
		}
	}

	private List<Journal.Line> lines() {
		return this.book.tabs().isEmpty() ? List.of() : this.book.tabs().get(this.tab).lines();
	}

	// ----- Where things are -----

	/** The open book with its bookmarks fills this share of the window, at most. */
	private static final double FILLS = 0.92;
	/** The book is never drawn smaller or bigger than this many times its own size. */
	private static final float SMALLEST = 0.5F;
	private static final float BIGGEST = 4.0F;

	/**
	 * How much bigger than its own size the book and everything on it are drawn (the user, 2026-11-12: "the journal needs
	 * to be a bit bigger in scale (the book texture being auto scale) while the text is a bit smaller or bigger depending
	 * on the scale"): as big as fits the window, whatever the GUI scale setting, in steps of a whole screen pixel so its
	 * letters stay sharp.
	 */
	private float zoom() {
		double fits = Math.min(this.width * FILLS / (2.0 * (COVER_RIGHT - COVER_LEFT)), this.height * FILLS / (COVER_BOTTOM + TAB_HEIGHT + 4.0));
		double gui = this.minecraft == null ? 1.0 : Math.max(1.0, this.minecraft.getWindow().getGuiScale());
		double snapped = Math.floor(fits * gui) / gui;
		return (float)Math.max(SMALLEST, Math.min(BIGGEST, snapped > 0 ? snapped : fits));
	}

	private int middle() {
		return (int)(this.width / this.zoom()) / 2;
	}

	private int top() {
		return Math.max(TAB_HEIGHT + 2, ((int)(this.height / this.zoom()) - COVER_BOTTOM) / 2 + TAB_HEIGHT / 2);
	}

	private int rightText() {
		return this.middle() + INSET;
	}

	private boolean overName(int mouseX, int mouseY) {
		int width = this.font.width(this.book.name());
		int left = this.leftText() + (TEXT_WIDTH - width) / 2;
		int y = this.top() + TEXT_TOP;
		return mouseX >= left - 2 && mouseX < left + width + 2 && mouseY >= y - 2 && mouseY < y + 11;
	}

	/**
	 * (The left page is the right one mirrored, drawn so its stitching meets the other's: its paper starts and ends
	 * further right than a plain mirror would have it. The user, 2026-11-12: "text looks janky on edge".)
	 */
	private static final int LEFT_SHIFT = 7;

	private int leftText() {
		return this.middle() - INSET - TEXT_WIDTH + LEFT_SHIFT;
	}

	/** Each bookmark's left edge and width, along the top from the left page's corner. */
	private int[][] tabs() {
		int[][] out = new int[this.book.tabs().size()][2];
		int x = this.middle() - (COVER_RIGHT - COVER_LEFT) + 10;
		for (int i = 0; i < out.length; i++) {
			int w = this.font.width(this.book.tabs().get(i).title()) + 10;
			out[i][0] = x;
			out[i][1] = w;
			x += w + 2;
		}

		return out;
	}

	// ----- Drawing -----

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		// (Everything on the book is laid out at its own size and drawn bigger or smaller together: the mouse is where it'd
		// be at that size.)
		float zoom = this.zoom();
		graphics.pose().pushPose();
		graphics.pose().scale(zoom, zoom, 1.0F);
		this.book(graphics, (int)(mouseX / zoom), (int)(mouseY / zoom));
		graphics.pose().popPose();
	}

	private void book(GuiGraphics graphics, int mouseX, int mouseY) {
		int middle = this.middle();
		int top = this.top();

		// Bookmarks first (the book's edge covers their feet), the chosen one last and taller.
		int[][] tabs = this.tabs();
		for (int i = 0; i < tabs.length; i++) {
			if (i != this.tab) {
				this.bookmark(graphics, tabs[i], top, false, this.book.tabs().get(i).title(), over(mouseX, mouseY, tabs[i], top));
			}
		}

		// The two pages: the left one mirrored, its stitching against the right one's.
		graphics.blit(BOOK, middle - COVER_RIGHT + 1, top, IMAGE, COVER_BOTTOM, IMAGE, 0, -IMAGE, COVER_BOTTOM, 256, 256);
		graphics.blit(BOOK, middle - COVER_LEFT - 1, top, 0, 0, IMAGE, COVER_BOTTOM);
		if (tabs.length > 0) {
			this.bookmark(graphics, tabs[this.tab], top, true, this.book.tabs().get(this.tab).title(), false);
		}

		this.leftPage(graphics, mouseX, mouseY);
		this.rightPage(graphics, mouseX, mouseY);
	}

	private static boolean over(int mouseX, int mouseY, int[] tab, int top) {
		return mouseX >= tab[0] && mouseX < tab[0] + tab[1] && mouseY >= top - TAB_HEIGHT && mouseY < top + 4;
	}

	private void bookmark(GuiGraphics graphics, int[] tab, int top, boolean chosen, String title, boolean hovered) {
		int y = top - TAB_HEIGHT + (chosen ? 0 : 2);
		int bottom = top + (chosen ? 6 : 3);
		graphics.fill(tab[0], y, tab[0] + tab[1], bottom, LEATHER);
		graphics.fill(tab[0] + 1, y + 1, tab[0] + tab[1] - 1, bottom, chosen ? PAPER : hovered ? 0xFFE6D7B0 : PAPER_DARK);
		graphics.drawString(this.font, title, tab[0] + 5, y + 3, chosen ? INK : 0xFF4A3A22, false);
	}

	/** The left page: their name, them, and how the world sees them. */
	private void leftPage(GuiGraphics graphics, int mouseX, int mouseY) {
		int x = this.leftText();
		int top = this.top();
		int y = top + TEXT_TOP;
		int nameWidth = this.font.width(this.book.name());
		int nameLeft = x + (TEXT_WIDTH - nameWidth) / 2;
		graphics.drawString(this.font, this.book.name(), nameLeft, y, INK, false);
		// (Their name's a way to change it and their pronouns: underlined under the mouse.)
		if (this.overName(mouseX, mouseY)) {
			graphics.fill(nameLeft, y + 9, nameLeft + nameWidth, y + 10, INK);
			this.setTooltipForNextRenderPass(Component.literal("Change your name and pronouns"));
		}

		y += 12;
		if (this.minecraft != null && this.minecraft.player != null) {
			// (Drawn in the window's own measure: it cuts itself off at a box given in that.)
			float zoom = this.zoom();
			graphics.pose().pushPose();
			graphics.pose().scale(1.0F / zoom, 1.0F / zoom, 1.0F);
			InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, (int)((x + TEXT_WIDTH / 2 - 26) * zoom), (int)(y * zoom),
				(int)((x + TEXT_WIDTH / 2 + 26) * zoom), (int)((y + 62) * zoom), (int)(27 * zoom), 0.0625F, mouseX * zoom, mouseY * zoom, this.minecraft.player);
			graphics.pose().popPose();
		}

		y += 66;
		for (Journal.Line line : this.book.you()) {
			for (FormattedCharSequence part : this.font.split(Component.literal(line.left()), TEXT_WIDTH)) {
				if (y + 9 > top + TEXT_BOTTOM + 4) {
					return;
				}

				graphics.drawString(this.font, part, x, y, ink(line.ink()), false);
				y += 9;
			}

			y += 2;
		}
	}

	/** The right page: the bookmark's lines, or the one that's open. */
	private void rightPage(GuiGraphics graphics, int mouseX, int mouseY) {
		if (this.book.tabs().isEmpty()) {
			return;
		}

		int x = this.rightText();
		int top = this.top();
		int y = top + TEXT_TOP;
		Journal.Tab tab = this.book.tabs().get(this.tab);
		if (this.open >= 0) {
			boolean hover = mouseX >= x && mouseX < x + this.font.width("< Back") && mouseY >= y && mouseY < y + 9;
			graphics.drawString(this.font, "< Back", x, y, hover ? INK : FAINT, false);
			y += 13;
			List<FormattedCharSequence> parts = new ArrayList<>();
			for (String more : tab.lines().get(this.open).detail()) {
				parts.addAll(this.font.split(Component.literal(more), TEXT_WIDTH));
				parts.add(FormattedCharSequence.EMPTY);
			}

			int shown = (top + TEXT_BOTTOM - y) / 9;
			this.scroll = Math.max(0, Math.min(this.scroll, parts.size() - shown));
			for (int i = this.scroll; i < parts.size() && i < this.scroll + shown; i++) {
				graphics.drawString(this.font, parts.get(i), x, y, INK, false);
				y += 9;
			}

			this.more(graphics, x, top, parts.size() > this.scroll + shown);
			return;
		}

		if (tab.lines().isEmpty()) {
			for (FormattedCharSequence part : this.font.split(Component.literal(tab.empty()), TEXT_WIDTH)) {
				graphics.drawString(this.font, part, x, y, FAINT, false);
				y += 9;
			}

			return;
		}

		this.scroll = Math.max(0, Math.min(this.scroll, tab.lines().size() - 1));
		int i = this.scroll;
		for (; i < tab.lines().size(); i++) {
			Journal.Line line = tab.lines().get(i);
			int height = this.height(line);
			if (y + height > top + TEXT_BOTTOM + 2) {
				break;
			}

			boolean hover = !line.detail().isEmpty() && mouseX >= x && mouseX < x + TEXT_WIDTH && mouseY >= y - 1 && mouseY < y + height - 1;
			int rightWidth = this.font.width(line.right());
			if (hover) {
				graphics.fill(x - 1, y - 1, x + TEXT_WIDTH + 1, y + height - 2, 0x30A08040);
			}

			graphics.drawString(this.font, this.clip(line.left(), TEXT_WIDTH), x, y, INK, false);
			// (How it stands to the right of it, or on a line of its own when they don't both fit.)
			int rightY = height > ROW ? y + 9 : y;
			graphics.drawString(this.font, this.clip(line.right(), TEXT_WIDTH), x + TEXT_WIDTH - Math.min(rightWidth, TEXT_WIDTH), rightY, ink(line.ink()), false);
			// (A dotted rule under each, as in a ledger.)
			for (int dot = x; dot < x + TEXT_WIDTH; dot += 2) {
				graphics.fill(dot, y + height - 2, dot + 1, y + height - 1, RULE);
			}

			y += height;
		}

		this.more(graphics, x, top, i < tab.lines().size());
	}

	/** A line's height on the page: one row, or two when its two ends don't fit side by side. */
	private int height(Journal.Line line) {
		return line.right().isEmpty() || this.font.width(line.left()) + this.font.width(line.right()) + 4 <= TEXT_WIDTH ? ROW : ROW + 9;
	}

	private String clip(String text, int width) {
		return this.font.width(text) <= width ? text : this.font.plainSubstrByWidth(text, width - this.font.width("...")) + "...";
	}

	/** "more" at the foot of the page while there is more below. */
	private void more(GuiGraphics graphics, int x, int top, boolean below) {
		if (below || this.scroll > 0) {
			String text = below ? this.scroll > 0 ? "scroll for more" : "scroll down for more" : "scroll up";
			graphics.drawString(this.font, text, x + TEXT_WIDTH - this.font.width(text), top + TEXT_BOTTOM + 1, FAINT, false);
		}
	}

	private static int ink(int ink) {
		return switch (ink) {
			case Journal.GOOD -> GOOD;
			case Journal.BAD -> BAD;
			case Journal.FAINT -> FAINT;
			default -> INK;
		};
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderTransparentBackground(graphics);
	}

	// ----- Input -----

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		float zoom = this.zoom();
		return this.clicked(mouseX / zoom, mouseY / zoom, button);
	}

	/** A click, where it'd be with the book at its own size. */
	private boolean clicked(double mouseX, double mouseY, int button) {
		int top = this.top();
		if (button == 1 && this.open >= 0) {
			this.back();
			return true;
		}

		if (button != 0) {
			return false;
		}

		if (this.overName((int)mouseX, (int)mouseY)
			&& net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(com.dinglebob.drifterciv.Citizens.Change.TYPE)) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.dinglebob.drifterciv.Citizens.Change());
			return true;
		}

		int[][] tabs = this.tabs();
		for (int i = 0; i < tabs.length; i++) {
			if (over((int)mouseX, (int)mouseY, tabs[i], top)) {
				if (i != this.tab || this.open >= 0) {
					this.tab = i;
					this.open = -1;
					this.scroll = 0;
					this.turn();
				}

				return true;
			}
		}

		int x = this.rightText();
		int y = top + TEXT_TOP;
		if (this.open >= 0) {
			if (mouseX >= x && mouseX < x + this.font.width("< Back") + 4 && mouseY >= y - 2 && mouseY < y + 11) {
				this.back();
				return true;
			}

			return false;
		}

		if (mouseX >= x && mouseX < x + TEXT_WIDTH && mouseY >= y - 1) {
			List<Journal.Line> lines = this.lines();
			for (int index = this.scroll; index < lines.size(); index++) {
				int height = this.height(lines.get(index));
				if (y + height > top + TEXT_BOTTOM + 2) {
					break;
				}

				if (mouseY < y + height - 1) {
					if (!lines.get(index).detail().isEmpty()) {
						this.listScroll = this.scroll;
						this.open = index;
						this.scroll = 0;
						this.turn();
						return true;
					}

					break;
				}

				y += height;
			}
		}

		return false;
	}

	/** Where the list was scrolled to when a line was opened (to come back to). */
	private int listScroll;

	private void back() {
		this.open = -1;
		this.scroll = this.listScroll;
		this.turn();
	}

	/** A page turned: the book's own sound. */
	private void turn() {
		if (this.minecraft != null) {
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		this.scroll = Math.max(0, this.scroll - (int)Math.signum(scrollY) * (this.open >= 0 ? 2 : 1));
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (DrifterCivilizationsClient.journalKey != null && DrifterCivilizationsClient.journalKey.matches(keyCode, scanCode)) {
			this.onClose();
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
