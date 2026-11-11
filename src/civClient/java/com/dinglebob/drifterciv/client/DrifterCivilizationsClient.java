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

	@Override
	public void onInitializeClient() {
		journalKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(new net.minecraft.client.KeyMapping(
			"key.drifter_civ.journal", org.lwjgl.glfw.GLFW.GLFW_KEY_J, "key.categories.drifter_civ"));
		// The key asks the server for the journal (none without the addon there: nothing happens); it opens when it comes.
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (journalKey.consumeClick()) {
				if (client.screen == null && client.player != null && ClientPlayNetworking.canSend(Journal.Ask.TYPE)) {
					ClientPlayNetworking.send(new Journal.Ask());
				}
			}

			tourTick(client);
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
					// (A world still being built: on to its loading screen, the world not shown between.)
					client.setScreen(who.first() && who.progress() != null ? new WorldBuildScreen(who.progress()) : null);
				}
			}
		});
		// Debug only: DRIFTER_CIV_SCREEN set, the Friendship settings page opens once a world's been open a while (a client
		// test screenshots it; the test harness can't click through Mod Menu).
		String debug = System.getenv("DRIFTER_CIV_SCREEN");
		if (debug != null && debug.startsWith("who")) {
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
	private static Journal.Book tourBook;
	private static int tourShot;

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
