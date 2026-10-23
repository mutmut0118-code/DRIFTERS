package com.dinglebob.drifter.social;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Optional chat lines (DESIGN.md section 14): short, lowercase, canned lines tied to events, never generated text, from
 * the chat data ({@link Lines}: each personality its own voice). A strict rate limit; quiet ones (low Sociability) rarely
 * speak. On by default, `/drifter config chat false` turns them off.
 */
public final class ChatLines {
	private ChatLines() {
	}

	/** The canned lines' kinds, each a key in the chat data ({@link Lines}). */
	public enum Kind {
		HI("hi"), THANKS("thanks"), GG_WON("gg_won"), GG_LOST("gg_lost"), STOP("stop"), OUCH("ouch"), HUH("huh"), THIEF("thief"), WAR_CRY("war_cry");

		public final String key;

		Kind(String key) {
			this.key = key;
		}
	}

	/** Least time between two lines from one Drifter. */
	private static final long PER_DRIFTER = 20 * 20;
	/** Least time between two lines of the same kind from one Drifter. */
	private static final long PER_KIND = 20 * 60;
	/** At most this many lines from all Drifters together in {@link #WINDOW}. */
	private static final int SERVER_CAP = 4;
	private static final long WINDOW = 20 * 10;

	private static final Map<UUID, Long> lastLine = new HashMap<>();
	private static final Map<String, Long> lastKind = new HashMap<>();
	private static final java.util.ArrayDeque<Long> recent = new java.util.ArrayDeque<>();

	/**
	 * Talking to someone (an encounter's lines: a demand, an offer, an answer): the chat setting applies, the rate limits
	 * don't (found in a test: "let's go!" after the duel challenge came too soon after it and was never said). Its other
	 * chatter waits after it.
	 */
	public static void speak(DrifterEntity drifter, @org.jetbrains.annotations.Nullable String text) {
		MinecraftServer server = drifter.getServer();
		if (!DrifterConfig.chatLines() || server == null || text == null) {
			return;
		}

		lastLine.put(drifter.getUUID(), drifter.level().getGameTime());
		send(drifter, text);
	}

	/**
	 * Its line, to the players near enough to hear it (user, 2026-10-23: only players nearby see what Drifters say), and to
	 * the player an encounter of its is with, wherever they are (it's talking to them: a call for help carries).
	 */
	public static void send(DrifterEntity drifter, String text) {
		Component line = Component.literal("<" + drifter.getName().getString() + "> " + text);
		double range = DrifterConfig.chatRange();
		java.util.UUID audience = drifter.encounter() != null ? drifter.encounter().player : null;
		for (net.minecraft.server.level.ServerPlayer player : drifter.getServer().getPlayerList().getPlayers()) {
			if (player.getUUID().equals(audience) || player.level() == drifter.level() && player.distanceTo(drifter) <= range) {
				DrifterSaidPayload.send(player, drifter, text);
				player.sendSystemMessage(line);
			}
		}

		// (The server's log has it too, as it has players' chat.)
		drifter.getServer().sendSystemMessage(line);
	}

	/**
	 * A line in a conversation with another Drifter ({@code talk.Conversations}): the setting applies; the conversation sets
	 * its own pace, so the rate limits don't (its other chatter waits after it).
	 */
	public static void converse(DrifterEntity drifter, String text) {
		if (!DrifterConfig.chatLines() || drifter.getServer() == null) {
			return;
		}

		lastLine.put(drifter.getUUID(), drifter.level().getGameTime());
		send(drifter, text);
	}

	/** A line of its own (a whim's), under the same rate limits and setting as the canned ones. */
	public static void sayLine(DrifterEntity drifter, @org.jetbrains.annotations.Nullable String text) {
		MinecraftServer server = drifter.getServer();
		// (In a conversation it keeps to it.)
		if (!DrifterConfig.chatLines() || server == null || text == null || com.dinglebob.drifter.social.talk.Conversations.of(drifter) != null) {
			return;
		}

		long now = drifter.level().getGameTime();
		if (now - lastLine.getOrDefault(drifter.getUUID(), Long.MIN_VALUE / 2) < PER_DRIFTER) {
			return;
		}

		while (!recent.isEmpty() && now - recent.peekFirst() > WINDOW) {
			recent.pollFirst();
		}

		if (recent.size() >= SERVER_CAP) {
			return;
		}

		lastLine.put(drifter.getUUID(), now);
		recent.addLast(now);
		send(drifter, text);
	}

	/** It might say something about this (personality and rate limits decide); {@code vars}: words for the line (player...). */
	public static void say(DrifterEntity drifter, Kind kind, Object... vars) {
		MinecraftServer server = drifter.getServer();
		if (!DrifterConfig.chatLines() || server == null || com.dinglebob.drifter.social.talk.Conversations.of(drifter) != null) {
			return;
		}

		long now = drifter.level().getGameTime();
		String kindKey = drifter.getUUID() + ":" + kind;
		if (now - lastLine.getOrDefault(drifter.getUUID(), Long.MIN_VALUE / 2) < PER_DRIFTER
			|| now - lastKind.getOrDefault(kindKey, Long.MIN_VALUE / 2) < PER_KIND) {
			return;
		}

		while (!recent.isEmpty() && now - recent.peekFirst() > WINDOW) {
			recent.pollFirst();
		}

		if (recent.size() >= SERVER_CAP) {
			return;
		}

		// Quiet ones mostly don't bother (a hermit almost never), chatty ones usually do.
		double sociability = drifter.getPersonality().get(Trait.SOCIABILITY);
		if (drifter.getRandom().nextDouble() > 0.15 + 0.8 * sociability) {
			return;
		}

		String line = Lines.habit(drifter, kind.key, vars);
		if (line == null) {
			return;
		}

		lastLine.put(drifter.getUUID(), now);
		lastKind.put(kindKey, now);
		recent.addLast(now);
		send(drifter, line);
	}
}
