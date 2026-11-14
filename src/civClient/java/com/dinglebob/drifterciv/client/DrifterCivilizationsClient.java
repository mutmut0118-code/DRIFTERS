package com.dinglebob.drifterciv.client;

import com.dinglebob.drifterciv.Citizens;
import com.dinglebob.drifterciv.Journal;
import com.dinglebob.drifterciv.WorldBuild;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * The addon's client side: the loading screen while a new civilized world is built ({@link WorldBuildScreen}), and the
 * player's journal on the J key ({@link JournalScreen}).
 */
public final class DrifterCivilizationsClient implements ClientModInitializer {
	/** The journal's key (J; in Controls under Drifter: Civilizations). */
	static net.minecraft.client.KeyMapping journalKey;
	/** Debug: the journal's pages to show and screenshot one after another (tab, line or -1), and ticks till the next. */
	private static java.util.List<int[]> tour;
	private static int tourWait;

	/** The world's sounds (not music, not the menu's clicks). */
	private static final net.minecraft.sounds.SoundSource[] QUIET = {net.minecraft.sounds.SoundSource.AMBIENT, net.minecraft.sounds.SoundSource.WEATHER,
		net.minecraft.sounds.SoundSource.BLOCKS, net.minecraft.sounds.SoundSource.HOSTILE, net.minecraft.sounds.SoundSource.NEUTRAL,
		net.minecraft.sounds.SoundSource.PLAYERS, net.minecraft.sounds.SoundSource.RECORDS, net.minecraft.sounds.SoundSource.VOICE};

	@Override
	public void onInitializeClient() {
		FarFollow.register();
		journalKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(new net.minecraft.client.KeyMapping(
			"key.drifter_civ.journal", org.lwjgl.glfw.GLFW.GLFW_KEY_J, "key.categories.drifter_civ"));
		// The key asks the server for the journal (none without the addon there: nothing happens); it opens when it comes.
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (journalKey.consumeClick()) {
				if (client.screen == null && client.player != null && ClientPlayNetworking.canSend(Journal.Ask.TYPE)) {
					ClientPlayNetworking.send(new Journal.Ask());
				}
			}

			// (Asked who they are and not answered: the menu comes back if something closed it; they're held until they say.)
			if (asked != null && client.level == null) {
				asked = null;
			} else if (asked != null && client.screen == null) {
				client.setScreen(new WhoScreen(asked, asked.building() ? lastProgress : null));
			}

			// (Behind the loading screen and the first "Who are you?" the world isn't shown: not heard either.)
			if (client.level != null && (client.screen instanceof WorldBuildScreen || client.screen instanceof WhoScreen who && who.first())) {
				for (net.minecraft.sounds.SoundSource source : QUIET) {
					client.getSoundManager().stop(null, source);
				}
			}

			tourTick(client);
		});
		ClientPlayNetworking.registerGlobalReceiver(Journal.Page.TYPE, (page, context) -> {
			if (context.client().screen instanceof JournalScreen screen) {
				screen.page(page);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Journal.Book.TYPE, (book, context) -> {
			var client = context.client();
			if (client.screen instanceof JournalScreen screen) {
				screen.update(book);
			} else if (client.screen == null) {
				client.setScreen(new JournalScreen(book, 0, -1));
				client.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F));
				if (tour != null && tour.isEmpty()) {
					// (Debug: every bookmark, and its first line with more to it, in turn.)
					for (int tab = 0; tab < book.tabs().size(); tab++) {
						tour.add(new int[] {tab, -1});
						for (int line = 0; line < book.tabs().get(tab).lines().size(); line++) {
							if (!book.tabs().get(tab).lines().get(line).detail().isEmpty()) {
								tour.add(new int[] {tab, line});
								// (The Chronicle: its first few stories, not only the first.)
								if (book.tabs().get(tab).lines().get(line).detail().get(0).startsWith("@page:") && line < 6) {
									continue;
								}

								break;
							}
						}
					}

					tourBook = book;
					tourWait = 20;
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(WorldBuild.Progress.TYPE, (progress, context) -> {
			var client = context.client();
			lastProgress = progress.finished() ? null : progress;
			if (client.screen instanceof WhoScreen who) {
				// (Saying who they are meanwhile: the bar's along the bottom of that.)
				who.progress(progress.finished() ? null : progress);
			} else if (progress.finished()) {
				if (client.screen instanceof WorldBuildScreen) {
					client.setScreen(null);
				}
			} else if (client.screen instanceof WorldBuildScreen screen) {
				screen.update(progress);
			} else {
				client.setScreen(new WorldBuildScreen(progress));
			}
		});
		// "Who are you?": asked as they first enter a world (it can't be closed), or to change their name from the journal.
		ClientPlayNetworking.registerGlobalReceiver(Citizens.Menu.TYPE, (menu, context) -> {
			var client = context.client();
			asked = menu.first() ? menu : asked;
			if (client.screen instanceof WhoScreen who && who.first() == menu.first()) {
				who.update(menu);
			} else {
				client.setScreen(new WhoScreen(menu, menu.building() ? lastProgress : null));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Citizens.Answer.TYPE, (answer, context) -> {
			var client = context.client();
			if (client.screen instanceof WhoScreen who) {
				if (!answer.ok()) {
					who.refused(answer.why());
				} else {
					asked = null;
					// (A world still being built: on to its loading screen, the world not shown between.)
					client.setScreen(who.first() && who.progress() != null ? new WorldBuildScreen(who.progress()) : null);
				}
			}
		});
		// Debug only: DRIFTER_CIV_SCREEN set, the Friendship settings page opens once a world's been open a while (a client
		// test screenshots it; the test harness can't click through Mod Menu).
		String debug = System.getenv("DRIFTER_CIV_SCREEN");
		if (debug != null && debug.startsWith("whochange")) {
			// Debug only: DRIFTER_CIV_SCREEN=whochange@<ticks>:<name>: that long after a world opens, the change of name is
			// asked for as from the journal; the name's typed, a shot taken (screenshots/who_change.png), and Done pressed.
			String[] parts = debug.substring(debug.indexOf('@') + 1).split(":");
			int[] ticks = {0};
			try {
				int at = Integer.parseInt(parts[0]);
				WhoScreen.debugName = parts.length < 2 ? "" : parts[1];
				net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
					if (client.level != null && ticks[0] >= 0 && ++ticks[0] == at && ClientPlayNetworking.canSend(Citizens.Change.TYPE)) {
						WhoScreen.debugAt = 20;
						ClientPlayNetworking.send(new Citizens.Change());
						ticks[0] = -1;
					}
				});
			} catch (NumberFormatException e) {
				WhoScreen.debugAt = -1;
			}
		} else if (debug != null && debug.startsWith("who")) {
			// Debug only: DRIFTER_CIV_SCREEN=who@<ticks>:<card or none>[:<name>]: that long after "Who are you?" opens, that
			// card's picked (and the name typed), a shot taken (screenshots/who_<card>.png), and Begin pressed.
			String[] parts = debug.substring(debug.indexOf('@') + 1).split(":");
			try {
				WhoScreen.debugAt = Integer.parseInt(parts[0]);
				WhoScreen.debugCard = parts.length < 2 || parts[1].equals("none") ? -1 : Integer.parseInt(parts[1]);
				WhoScreen.debugName = parts.length < 3 ? "" : parts[2];
			} catch (NumberFormatException e) {
				WhoScreen.debugAt = -1;
			}
		} else if (debug != null && debug.startsWith("journal")) {
			// Debug only: DRIFTER_CIV_SCREEN=journal@<ticks>: that long after a world opens the journal is asked for, then each
			// of its pages is shown and screenshotted (screenshots/journal_<n>.png).
			int at = debug.contains("@") ? Integer.parseInt(debug.substring(debug.indexOf('@') + 1)) : 300;
			int[] ticks = {0};
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
				if (client.level != null && ticks[0] >= 0 && ++ticks[0] == at) {
					tour = new java.util.ArrayList<>();
					client.setScreen(null);
					ClientPlayNetworking.send(new Journal.Ask());
					ticks[0] = -1;
				}
			});
		} else if (debug != null) {
			int[] ticks = {0};
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
				if (client.level != null && ticks[0] >= 0 && ++ticks[0] == 300) {
					client.setScreen(new FriendshipScreen(new CivConfigScreen(null)));
					ticks[0] = -1;
				}
			});
		}
	}

	/** How the world's build was going as last told (for the menu opened over it). */
	private static WorldBuild.Progress lastProgress;
	/** The first-time menu as last sent, until it's answered. */
	private static Citizens.Menu asked;
	private static Journal.Book tourBook;
	private static int tourShot;
	private static final java.util.List<String> tourPages = new java.util.ArrayList<>();
	private static final java.util.Set<String> tourDone = new java.util.HashSet<>();

	private static void tourTick(net.minecraft.client.Minecraft client) {
		if (tourBook == null || --tourWait > 0) {
			return;
		}

		// (A shot of the page shown, then the next one.)
		if (tourShot > 0 || client.screen instanceof JournalScreen) {
			if (tourShot > 0) {
				net.minecraft.client.Screenshot.grab(client.gameDirectory, "journal_" + tourShot + ".png", client.getMainRenderTarget(), message -> {
				});
			}

			// (A page of the Chronicle showing: the pages it leads on to are shot too, each once, a dozen at most.)
			if (client.screen instanceof JournalScreen showing && showing.debugPage() != null) {
				tourDone.add(showing.debugPage());
				for (String link : showing.debugLinks()) {
					if (!tourDone.contains(link) && !tourPages.contains(link) && tourDone.size() + tourPages.size() < 14) {
						tourPages.add(link);
					}
				}

				if (!tourPages.isEmpty()) {
					showing.debugTurn(tourPages.remove(0));
					tourShot++;
					tourWait = 20;
					return;
				}
			}

			if (tour.isEmpty()) {
				tourBook = null;
				tour = null;
				return;
			}

			int[] next = tour.remove(0);
			client.setScreen(new JournalScreen(tourBook, next[0], next[1]));
			tourShot++;
			tourWait = 20;
		}
	}
}
