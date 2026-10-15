package com.dinglebob.drifter.social;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Optional chat lines (DESIGN.md section 14): short, lowercase, canned lines tied to events, never generated text. Each
 * personality has its own small vocabulary and a strict rate limit; quiet ones (low Sociability) rarely speak. On by
 * default, `/drifter config chat false` turns them off.
 */
public final class ChatLines {
	private ChatLines() {
	}

	public enum Kind {
		HI, THANKS, GG, STOP, OUCH, NICE
	}

	/** Lines per kind; a personality picks from its own slice of these. */
	private static final Map<Kind, List<String>> LINES = Map.of(
		Kind.HI, List.of("hi", "hey", "hello", "yo", "o/", "sup"),
		Kind.THANKS, List.of("ty", "thx", "thanks", "tysm", "ty!"),
		Kind.GG, List.of("gg", "rip", "gg wp", "ggs"),
		Kind.STOP, List.of("go away", "stop", "back off", "wait", "dont"),
		Kind.OUCH, List.of("bruh", "?", "hey!", "stop"),
		Kind.NICE, List.of("nice", "lol", "ok", "nice one"));

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

	/** It might say something about this (personality and rate limits decide). */
	public static void say(DrifterEntity drifter, Kind kind) {
		MinecraftServer server = drifter.getServer();
		if (!DrifterConfig.chatLines() || server == null) {
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

		lastLine.put(drifter.getUUID(), now);
		lastKind.put(kindKey, now);
		recent.addLast(now);
		server.getPlayerList().broadcastSystemMessage(Component.literal("<" + drifter.getName().getString() + "> " + line(drifter, kind)), false);
	}

	/**
	 * Its own way of saying it: a fixed pick from its personality (the same Drifter always greets the same way, as
	 * players do), varied a little.
	 */
	private static String line(DrifterEntity drifter, Kind kind) {
		List<String> lines = LINES.get(kind);
		long seed = drifter.getUUID().getLeastSignificantBits() ^ kind.ordinal() * 31L;
		int habit = (int)Math.floorMod(seed, lines.size());
		// Now and then another of the lines (a quarter of the time); PvP sweats always "gg".
		if (kind == Kind.GG && drifter.getPersonality().archetype() == Archetype.PVP_SWEAT) {
			return "gg";
		}

		return drifter.getRandom().nextFloat() < 0.25F ? lines.get(drifter.getRandom().nextInt(lines.size())) : lines.get(habit);
	}
}
