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

		// (A page of the Chronicle turned to: it has both pages to itself.)
		if (!this.turned.isEmpty()) {
			this.article(graphics, mouseX, mouseY);
			return;
		}

		this.leftPage(graphics, mouseX, mouseY);
		this.rightPage(graphics, mouseX, mouseY);
	}

	// ----- The Chronicle's pages (the chronicle revamp: a front page, and pages on from it) -----

	/** The pages turned to, the one showing last (empty: the bookmark's list). */
	private final java.util.ArrayDeque<String> turned = new java.util.ArrayDeque<>();
	/** Pages the server has sent, by id. */
	private final java.util.Map<String, Journal.Page> pages = new java.util.HashMap<>();
	/** How far down the page showing is scrolled (things skipped at its top). */
	private int pageScroll;
	/** Where a click turns to another page, as drawn last: left, top, right, bottom, and the page. */
	private final List<Object[]> hits = new ArrayList<>();

	/** A page of the Chronicle is printed this much smaller than the rest of the book (about: in whole screen pixels). */
	private static final float PRINT = 0.7F;
	/** How wide a column of it is, in its own measure. */
	private int wide = TEXT_WIDTH;

	/** The print's size against the book's: as near {@link #PRINT} as whole screen pixels allow. */
	private float print() {
		double gui = this.minecraft == null ? 1.0 : Math.max(1.0, this.minecraft.getWindow().getGuiScale());
		double pixels = this.zoom() * gui;
		double want = Math.max(1.0, Math.round(pixels * PRINT));
		return (float)Math.min(1.0, want / pixels);
	}

	/** A page arrived. */
	void page(Journal.Page page) {
		this.pages.put(page.id(), page);
	}

	private void turnTo(String id) {
		if (id.isEmpty()) {
			return;
		}

		this.turned.addLast(id);
		this.pageScroll = 0;
		this.pageScrollRight = 0;
		this.pages.remove(id);
		if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(Journal.Open.TYPE)) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new Journal.Open(id));
		}

		this.turn();
	}

	private void turnBack() {
		this.turned.pollLast();
		this.pageScroll = 0;
		this.pageScrollRight = 0;
		this.turn();
	}

	/** A skin as the server names it (a Drifter's texture, a bar, the real player's skin it wears) as a texture to draw; null: none. */
	private static ResourceLocation skin(String skin) {
		if (skin.isEmpty()) {
			return null;
		}

		int bar = skin.indexOf('|');
		String real = bar < 0 ? "" : skin.substring(bar + 1);
		if (!real.isEmpty()) {
			ResourceLocation downloaded = com.dinglebob.drifter.client.render.DrifterSkins.texture(real);
			if (downloaded != null) {
				return downloaded;
			}
		}

		return ResourceLocation.tryParse(bar < 0 ? skin : skin.substring(0, bar));
	}

	/** A head, or a banner, or nothing, in a box this big at most; true if something was drawn. */
	private boolean picture(GuiGraphics graphics, String picture, int x, int y, int size) {
		if (picture.startsWith("b")) {
			try {
				var item = new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.BannerBlock.byColor(net.minecraft.world.item.DyeColor.byId(
					Integer.parseInt(picture.substring(1)))));
				graphics.pose().pushPose();
				graphics.pose().translate(x, y, 0);
				graphics.pose().scale(size / 16.0F, size / 16.0F, 1.0F);
				graphics.renderItem(item, 0, 0);
				graphics.pose().popPose();
				return true;
			} catch (NumberFormatException bad) {
				return false;
			}
		}

		ResourceLocation texture = picture.startsWith("s") ? skin(picture.substring(1)) : null;
		if (texture == null) {
			return false;
		}

		net.minecraft.client.gui.components.PlayerFaceRenderer.draw(graphics, texture, x, y, size);
		return true;
	}

	private List<FormattedCharSequence> wrap(String text, int width) {
		return this.font.split(Component.literal(text), Math.max(20, width));
	}

	/** How tall one thing of a page is. */
	private int tall(Journal.El el) {
		return switch (el.type()) {
			case Journal.HEADLINE -> this.wrap(el.a(), (int)(this.wide / 1.5F)).size() * 14 + 1;
			case Journal.DATELINE -> this.font.width(el.a()) + this.font.width(el.b()) + 6 <= this.wide || el.a().isEmpty() || el.b().isEmpty() ? 10 : 19;
			case Journal.RULE, Journal.THIN -> 6;
			case Journal.TEXT -> this.wrap(el.a(), this.wide).size() * 9 + 3;
			case Journal.SUB -> 13;
			case Journal.BANNERS -> 24 + 10 + 3;
			case Journal.HEADS -> {
				boolean captions = el.more().stream().anyMatch(one -> one.split("\t", -1).length > 3 && !one.split("\t", -1)[3].isEmpty());
				yield ((el.more().size() + 2) / 3) * (16 + 10 + (captions ? 9 : 0) + 3);
			}
			case Journal.PERSON -> {
				int lines = 1;
				for (int i = 1; i < el.more().size(); i++) {
					lines += this.wrap(el.more().get(i), this.wide - 30).size();
				}

				yield Math.max(28, lines * 9 + 4) + 2;
			}
			case Journal.QUOTE -> this.wrap("\"" + el.a() + "\"", this.wide - 22).size() * 9 + this.wrap(el.b(), this.wide - 22).size() * 9 + 8 + 4;
			case Journal.LINK -> 12;
			default -> 0;
		};
	}

	/** The page turned to, over both pages: its things top to bottom on the left, then on the right. */
	private void article(GuiGraphics graphics, int mouseX, int mouseY) {
		this.hits.clear();
		int top = this.top();
		int left = this.leftText();
		boolean hoverBack = mouseX >= left && mouseX < left + this.font.width("< Back") + 4 && mouseY >= top + TEXT_TOP - 2 && mouseY < top + TEXT_TOP + 10;
		graphics.drawString(this.font, "< Back", left, top + TEXT_TOP, hoverBack ? INK : FAINT, false);
		Journal.Page page = this.pages.get(this.turned.peekLast());
		if (page == null) {
			graphics.drawString(this.font, "...", left, top + TEXT_TOP + 14, FAINT, false);
			return;
		}

		// (In smaller print: laid out in the print's own measure, drawn scaled, the mouse where it'd be in that.)
		float print = this.print();
		this.wide = (int)(TEXT_WIDTH / print);
		int leftX = (int)(left / print);
		int rightX = (int)(this.rightText() / print);
		int firstY = (int)((top + TEXT_TOP) / print);
		int bottom = (int)((top + TEXT_BOTTOM + 2) / print);
		int mx = (int)(mouseX / print);
		int my = (int)(mouseY / print);
		// Its left page and its right page, each with its own things (the page's break parts them), each scrolled on its
		// own with the mouse over it (the user, 2026-11-12: "im scrolling both pages at the same time"; "keep the scrolling
		// for the new chronicle articles but just keep that fix as well").
		List<Journal.El> leftEls = new ArrayList<>();
		List<Journal.El> rightEls = new ArrayList<>();
		boolean right = false;
		for (Journal.El el : page.els()) {
			if (el.type() == Journal.BREAK) {
				right = true;
			} else {
				(right ? rightEls : leftEls).add(el);
			}
		}

		graphics.pose().pushPose();
		graphics.pose().scale(print, print, 1.0F);
		this.pageScroll = this.column(graphics, leftEls, this.pageScroll, leftX, firstY + (int)(12 / print), bottom, mx, my);
		this.pageScrollRight = this.column(graphics, rightEls, this.pageScrollRight, rightX, firstY, bottom, mx, my);
		graphics.pose().popPose();
		this.wide = TEXT_WIDTH;
	}

	/** How far down the right page of the page showing is scrolled. */
	private int pageScrollRight;

	/** One page's things from the one scrolled to, as many as fit; "more" under it if some don't. The scroll, kept in bounds. */
	private int column(GuiGraphics graphics, List<Journal.El> els, int scroll, int x, int y, int bottom, int mouseX, int mouseY) {
		// (Never scrolled past where the rest fits.)
		int last = els.size();
		int room = bottom - y;
		while (last > 0 && room - this.tall(els.get(last - 1)) >= 0) {
			room -= this.tall(els.get(--last));
		}

		scroll = Math.max(0, Math.min(scroll, last));
		int i = scroll;
		for (; i < els.size(); i++) {
			int height = this.tall(els.get(i));
			if (y + height > bottom) {
				break;
			}

			this.draw(graphics, els.get(i), x, y, mouseX, mouseY);
			y += height;
		}

		if (i < els.size() || scroll > 0) {
			String text = i < els.size() ? "scroll for more" : "scroll up";
			graphics.drawString(this.font, text, x + (this.wide - this.font.width(text)) / 2, bottom, FAINT, false);
		}

		return scroll;
	}

	private void hit(int x0, int y0, int x1, int y1, String page) {
		if (!page.isEmpty()) {
			this.hits.add(new Object[] {x0, y0, x1, y1, page});
		}
	}

	private static boolean in(int mouseX, int mouseY, int x0, int y0, int x1, int y1) {
		return mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
	}

	private void draw(GuiGraphics graphics, Journal.El el, int x, int y, int mouseX, int mouseY) {
		switch (el.type()) {
			case Journal.HEADLINE -> {
				graphics.pose().pushPose();
				graphics.pose().translate(x + this.wide / 2.0F, y, 0);
				graphics.pose().scale(1.5F, 1.5F, 1.0F);
				int line = 0;
				for (FormattedCharSequence part : this.wrap(el.a(), (int)(this.wide / 1.5F))) {
					graphics.drawString(this.font, part, -this.font.width(part) / 2, line, INK, false);
					line += 9;
				}

				graphics.pose().popPose();
			}
			case Journal.DATELINE -> {
				boolean two = this.tall(el) > 10;
				graphics.drawString(this.font, el.a(), x, y, FAINT, false);
				graphics.drawString(this.font, el.b(), x + this.wide - this.font.width(el.b()), two ? y + 9 : y, FAINT, false);
			}
			case Journal.RULE -> {
				graphics.fill(x, y + 1, x + this.wide, y + 3, INK);
				graphics.fill(x, y + 4, x + this.wide, y + 5, INK);
			}
			case Journal.THIN -> graphics.fill(x, y + 2, x + this.wide, y + 3, PAPER_DARK);
			case Journal.TEXT -> {
				int line = y;
				for (FormattedCharSequence part : this.wrap(el.a(), this.wide)) {
					graphics.drawString(this.font, part, x, line, ink(el.ink()), false);
					line += 9;
				}
			}
			case Journal.SUB -> graphics.drawString(this.font, el.a(), x, y + 3, FAINT, false);
			case Journal.BANNERS -> {
				int n = Math.max(1, el.more().size());
				int cell = this.wide / n;
				for (int i = 0; i < el.more().size(); i++) {
					String[] one = el.more().get(i).split("\t", -1);
					int cx = x + cell * i + cell / 2;
					boolean hover = one.length > 2 && !one[2].isEmpty() && in(mouseX, mouseY, cx - cell / 2, y, cx + cell / 2, y + 35);
					if (hover) {
						graphics.fill(cx - cell / 2 + 1, y, cx + cell / 2 - 1, y + 35, 0x30A08040);
					}

					this.picture(graphics, one[0], cx - 12, y, 24);
					String caption = this.clip(one.length > 1 ? one[1] : "", cell - 2);
					graphics.drawString(this.font, caption, cx - this.font.width(caption) / 2, y + 25, hover ? INK : 0xFF6B5C40, false);
					this.hit(cx - cell / 2, y, cx + cell / 2, y + 35, one.length > 2 ? one[2] : "");
				}

				if (el.more().size() == 2) {
					String word = "and";
					graphics.drawString(this.font, word, x + this.wide / 2 - this.font.width(word) / 2, y + 8, FAINT, false);
				}
			}
			case Journal.HEADS -> {
				boolean captions = el.more().stream().anyMatch(one -> one.split("\t", -1).length > 3 && !one.split("\t", -1)[3].isEmpty());
				int row = 16 + 10 + (captions ? 9 : 0) + 3;
				int cell = this.wide / 3;
				for (int i = 0; i < el.more().size(); i++) {
					String[] one = el.more().get(i).split("\t", -1);
					int cx = x + cell * (i % 3) + cell / 2;
					int cy = y + row * (i / 3);
					String page = one.length > 2 ? one[2] : "";
					boolean hover = !page.isEmpty() && in(mouseX, mouseY, cx - cell / 2, cy, cx + cell / 2, cy + row - 2);
					if (hover) {
						graphics.fill(cx - cell / 2 + 1, cy - 1, cx + cell / 2 - 1, cy + row - 3, 0x30A08040);
					}

					if (!this.picture(graphics, "s" + one[0], cx - 8, cy, 16)) {
						graphics.fill(cx - 8, cy, cx + 8, cy + 16, PAPER_DARK);
					}

					String name = this.clip(one.length > 1 ? one[1] : "", cell - 2);
					graphics.drawString(this.font, name, cx - this.font.width(name) / 2, cy + 17, INK, false);
					if (!page.isEmpty()) {
						graphics.fill(cx - this.font.width(name) / 2, cy + 26, cx + this.font.width(name) / 2, cy + 27, hover ? INK : RULE);
					}

					if (captions && one.length > 3) {
						String caption = this.clip(one[3], cell - 2);
						graphics.drawString(this.font, caption, cx - this.font.width(caption) / 2, cy + 27, 0xFF6B5C40, false);
					}

					this.hit(cx - cell / 2, cy, cx + cell / 2, cy + row - 2, page);
				}
			}
			case Journal.PERSON -> {
				String power = el.more().isEmpty() ? "" : el.more().get(0);
				if (!this.picture(graphics, "s" + el.a(), x, y + 1, 24) && !this.picture(graphics, power, x, y + 1, 24)) {
					// (Nobody's face known: a blank where it'd be.)
					graphics.fill(x, y + 1, x + 24, y + 25, PAPER_DARK);
				} else if (power.startsWith("b")) {
					this.picture(graphics, power, x + this.wide - 12, y, 12);
				}

				graphics.drawString(this.font, this.clip(el.b(), this.wide - 30 - (power.startsWith("b") && !el.a().isEmpty() ? 12 : 0)), x + 29, y + 2, INK, false);
				int line = y + 12;
				for (int i = 1; i < el.more().size(); i++) {
					for (FormattedCharSequence part : this.wrap(el.more().get(i), this.wide - 30)) {
						graphics.drawString(this.font, part, x + 29, line, 0xFF6B5C40, false);
						line += 9;
					}
				}
			}
			case Journal.QUOTE -> {
				int height = this.tall(el) - 4;
				graphics.fill(x, y, x + this.wide, y + 1, INK);
				graphics.fill(x, y + height - 1, x + this.wide, y + height, INK);
				graphics.fill(x, y, x + 1, y + height, INK);
				graphics.fill(x + this.wide - 1, y, x + this.wide, y + height, INK);
				if (!el.more().isEmpty() && !this.picture(graphics, "s" + el.more().get(0), x + 4, y + 4, 12)) {
					graphics.fill(x + 4, y + 4, x + 16, y + 16, PAPER_DARK);
				}

				int line = y + 4;
				for (FormattedCharSequence part : this.wrap("\"" + el.a() + "\"", this.wide - 22)) {
					graphics.drawString(this.font, part, x + 19, line, INK, false);
					line += 9;
				}

				for (FormattedCharSequence part : this.wrap(el.b(), this.wide - 22)) {
					graphics.drawString(this.font, part, x + 19, line, 0xFF6B5C40, false);
					line += 9;
				}
			}
			case Journal.LINK -> {
				String text = this.clip(el.a(), this.wide - 8);
				int width = this.font.width(text);
				boolean hover = in(mouseX, mouseY, x, y, x + this.wide, y + 11);
				if (hover) {
					graphics.fill(x - 1, y - 1, x + this.wide + 1, y + 10, 0x30A08040);
				}

				graphics.drawString(this.font, text, x, y + 1, INK, false);
				graphics.fill(x, y + 10, x + width, y + 11, hover ? INK : FAINT);
				graphics.drawString(this.font, ">", x + this.wide - this.font.width(">"), y + 1, FAINT, false);
				this.hit(x, y, x + this.wide, y + 11, el.b());
			}
			default -> {
			}
		}
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
		int nameLeft = x + (this.wide - nameWidth) / 2;
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
			InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, (int)((x + this.wide / 2 - 26) * zoom), (int)(y * zoom),
				(int)((x + this.wide / 2 + 26) * zoom), (int)((y + 62) * zoom), (int)(27 * zoom), 0.0625F, mouseX * zoom, mouseY * zoom, this.minecraft.player);
			graphics.pose().popPose();
		}

		y += 66;
		// (Its lines scroll on their own, the mouse over this page, when there are more than fit.)
		List<FormattedCharSequence> parts = new ArrayList<>();
		List<Integer> inks = new ArrayList<>();
		for (Journal.Line line : this.book.you()) {
			for (FormattedCharSequence part : this.font.split(Component.literal(line.left()), TEXT_WIDTH)) {
				parts.add(part);
				inks.add(ink(line.ink()));
			}
		}

		int fits = Math.max(1, (top + TEXT_BOTTOM + 4 - y) / 9);
		this.youScroll = Math.max(0, Math.min(this.youScroll, parts.size() - fits));
		for (int i = this.youScroll; i < parts.size() && i < this.youScroll + fits; i++) {
			graphics.drawString(this.font, parts.get(i), x, y, inks.get(i), false);
			y += 9;
		}

		if (parts.size() > this.youScroll + fits) {
			String text = "more below";
			graphics.drawString(this.font, text, x + (TEXT_WIDTH - this.font.width(text)) / 2, top + TEXT_BOTTOM + 1, FAINT, false);
		}
	}

	/** How far the left page's lines are scrolled. */
	private int youScroll;


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
				parts.addAll(this.font.split(Component.literal(more), this.wide));
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
			for (FormattedCharSequence part : this.font.split(Component.literal(tab.empty()), this.wide)) {
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

			boolean hover = !line.detail().isEmpty() && mouseX >= x && mouseX < x + this.wide && mouseY >= y - 1 && mouseY < y + height - 1;
			int rightWidth = this.font.width(line.right());
			if (hover) {
				graphics.fill(x - 1, y - 1, x + this.wide + 1, y + height - 2, 0x30A08040);
			}

			graphics.drawString(this.font, this.clip(line.left(), this.wide), x, y, INK, false);
			// (How it stands to the right of it, or on a line of its own when they don't both fit.)
			int rightY = height > ROW ? y + 9 : y;
			graphics.drawString(this.font, this.clip(line.right(), this.wide), x + this.wide - Math.min(rightWidth, this.wide), rightY, ink(line.ink()), false);
			// (A dotted rule under each, as in a ledger.)
			for (int dot = x; dot < x + this.wide; dot += 2) {
				graphics.fill(dot, y + height - 2, dot + 1, y + height - 1, RULE);
			}

			y += height;
		}

		this.more(graphics, x, top, i < tab.lines().size());
	}

	/** A line's height on the page: one row, or two when its two ends don't fit side by side. */
	private int height(Journal.Line line) {
		return line.right().isEmpty() || this.font.width(line.left()) + this.font.width(line.right()) + 4 <= this.wide ? ROW : ROW + 9;
	}

	private String clip(String text, int width) {
		return this.font.width(text) <= width ? text : this.font.plainSubstrByWidth(text, width - this.font.width("...")) + "...";
	}

	/** "more" at the foot of the page while there is more below. */
	private void more(GuiGraphics graphics, int x, int top, boolean below) {
		if (below || this.scroll > 0) {
			String text = below ? this.scroll > 0 ? "scroll for more" : "scroll down for more" : "scroll up";
			graphics.drawString(this.font, text, x + (this.wide - this.font.width(text)) / 2, top + TEXT_BOTTOM + 1, FAINT, false);
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
		// A page of the Chronicle showing: back (its word, or the right button), or on to another page.
		if (!this.turned.isEmpty()) {
			int left = this.leftText();
			if (button == 1 || button == 0 && mouseX >= left && mouseX < left + this.font.width("< Back") + 4 && mouseY >= top + TEXT_TOP - 2
				&& mouseY < top + TEXT_TOP + 10) {
				this.turnBack();
				return true;
			}

			if (button == 0) {
				float print = this.print();
				for (Object[] hit : this.hits) {
					if (in((int)(mouseX / print), (int)(mouseY / print), (int)hit[0], (int)hit[1], (int)hit[2], (int)hit[3])) {
						this.turnTo((String)hit[4]);
						return true;
					}
				}

				// (A bookmark: off the page, to its list.)
				int[][] marks = this.tabs();
				for (int i = 0; i < marks.length; i++) {
					if (over((int)mouseX, (int)mouseY, marks[i], top)) {
						this.turned.clear();
						this.tab = i;
						this.open = -1;
						this.scroll = 0;
						this.turn();
						return true;
					}
				}
			}

			return false;
		}
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
					List<String> detail = lines.get(index).detail();
					if (detail.size() == 1 && detail.get(0).startsWith("@page:")) {
						this.turnTo(detail.get(0).substring(6));
						return true;
					}

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
	/** Debug (the screenshot tour): the page showing, the pages it leads on to, and a turn straight to one. */
	String debugPage() {
		return this.turned.peekLast();
	}

	List<String> debugLinks() {
		List<String> out = new ArrayList<>();
		for (Object[] hit : this.hits) {
			out.add((String)hit[4]);
		}

		return out;
	}

	void debugTurn(String id) {
		this.turned.clear();
		this.turnTo(id);
	}

	@Override
	protected void init() {
		// (Made already open at a line that's a page of the Chronicle: turned to now there's a game to ask.)
		if (this.open >= 0 && this.open < this.lines().size()) {
			List<String> detail = this.lines().get(this.open).detail();
			if (detail.size() == 1 && detail.get(0).startsWith("@page:")) {
				this.open = -1;
				if (this.turned.isEmpty()) {
					this.turnTo(detail.get(0).substring(6));
				}
			}
		}
	}

	private void turn() {
		if (this.minecraft != null) {
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!this.turned.isEmpty()) {
			// (The page under the mouse, each on its own.)
			if (mouseX / this.zoom() < this.middle()) {
				this.pageScroll = Math.max(0, this.pageScroll - (int)Math.signum(scrollY));
			} else {
				this.pageScrollRight = Math.max(0, this.pageScrollRight - (int)Math.signum(scrollY));
			}

			return true;
		}

		// (The page under the mouse, each on its own.)
		if (mouseX / this.zoom() < this.middle()) {
			this.youScroll = Math.max(0, this.youScroll - (int)Math.signum(scrollY));
			return true;
		}

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
