package com.dinglebob.drifter.social;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.personality.Trait;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

/**
 * Drifters type like players (the dynamic chat, phase 4; user, 2026-10-23, from the guidebooks "How Minecraft Players
 * Chat" and "The Science of Gamer Text Chat"): now and then a typo (two letters swapped, one dropped or doubled, the key
 * next to it), sometimes fixed a moment later with "*word"; a longer line with a comma sent in two ("found 3 diamonds
 * today" ... "wbu"). The careless and the new make more typos, the tidy fix theirs. Never in names or numbers (coords stay
 * right). The setting "Typos and split messages" turns it off.
 */
public final class Typing {
	private Typing() {
	}

	/** A part still to come: who, what, when. */
	private record Later(DrifterEntity drifter, String text, long at) {
	}

	private static final List<Later> later = new ArrayList<>();
	/** Chance a line with a comma (long enough) goes in two. */
	private static final double SPLIT = 0.35;
	/** The keys next to each key (QWERTY), for a fat-fingered letter. */
	private static final String[] ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Typing::tick);
	}

	/** It types this: sent now, or in parts, a typo here and there. */
	static void type(DrifterEntity drifter, String text) {
		if (!DrifterConfig.typing()) {
			ChatLines.deliver(drifter, text);
			return;
		}

		RandomSource random = drifter.getRandom();
		long now = drifter.level().getGameTime();
		List<String> parts = split(text, random);
		long at = now;
		for (int i = 0; i < parts.size(); i++) {
			String part = parts.get(i);
			String[] typed = typo(drifter, part, random);
			if (i == 0) {
				ChatLines.deliver(drifter, typed[0]);
			} else {
				// (Typing the rest: a moment, longer for more.)
				at += 15 + part.length() * 2L + random.nextInt(15);
				later.add(new Later(drifter, typed[0], at));
			}

			// "*word": it noticed.
			if (typed[1] != null) {
				later.add(new Later(drifter, "*" + typed[1], at + 12 + random.nextInt(20)));
			}
		}
	}

	/** The line in one or two parts: at its first ", " (or a "? " or "! " midway), when both halves say something. */
	private static List<String> split(String text, RandomSource random) {
		if (text.length() < 16 || random.nextDouble() > SPLIT) {
			return List.of(text);
		}

		for (String mark : new String[] {", ", "? ", "! "}) {
			int at = text.indexOf(mark);
			if (at >= 6 && text.length() - at - mark.length() >= 3) {
				String first = mark.equals(", ") ? text.substring(0, at) : text.substring(0, at + 1);
				return List.of(first, text.substring(at + mark.length()));
			}
		}

		return List.of(text);
	}

	/** This part, maybe with a typo; and the word to fix it with, if it fixes it (else null). */
	private static String[] typo(DrifterEntity drifter, String text, RandomSource random) {
		if (random.nextDouble() > chance(drifter)) {
			return new String[] {text, null};
		}

		String[] words = text.split(" ", -1);
		List<Integer> fair = new ArrayList<>();
		Set<String> names = names(drifter);
		for (int i = 0; i < words.length; i++) {
			// Letters only, 4 or more, and not a name: never a coord, a name, "o/" or "gg".
			if (words[i].length() >= 4 && words[i].chars().allMatch(c -> c >= 'a' && c <= 'z') && !names.contains(words[i])) {
				fair.add(i);
			}
		}

		if (fair.isEmpty()) {
			return new String[] {text, null};
		}

		int index = fair.get(random.nextInt(fair.size()));
		String word = words[index];
		String wrong = mistype(word, random);
		if (wrong.equals(word)) {
			return new String[] {text, null};
		}

		words[index] = wrong;
		// The tidy fix theirs more often.
		boolean fixes = random.nextDouble() < 0.25 + 0.5 * drifter.getPersonality().get(Trait.TIDINESS);
		return new String[] {String.join(" ", words), fixes ? word : null};
	}

	/** One slip: two letters swapped, one dropped, one doubled, or the key next to it. */
	private static String mistype(String word, RandomSource random) {
		int i = 1 + random.nextInt(word.length() - 1);
		return switch (random.nextInt(4)) {
			case 0 -> i < word.length() - 1 ? word.substring(0, i) + word.charAt(i + 1) + word.charAt(i) + word.substring(i + 2) : word;
			case 1 -> word.substring(0, i) + word.substring(i + 1);
			case 2 -> word.substring(0, i) + word.charAt(i) + word.substring(i);
			default -> {
				char near = neighbour(word.charAt(i), random);
				yield word.substring(0, i) + near + word.substring(i + 1);
			}
		};
	}

	private static char neighbour(char c, RandomSource random) {
		for (String row : ROWS) {
			int at = row.indexOf(c);
			if (at >= 0) {
				int to = at == 0 ? 1 : at == row.length() - 1 ? at - 1 : at + (random.nextBoolean() ? 1 : -1);
				return row.charAt(to);
			}
		}

		return c;
	}

	/** How often it slips: the less skilled more, a newbie most, a careful builder or hermit least. */
	private static double chance(DrifterEntity drifter) {
		double skill = drifter.getPersonality().get(Trait.SKILL);
		Archetype archetype = drifter.getPersonality().archetype();
		double chance = 0.03 + 0.08 * (1.0 - skill);
		if (archetype == Archetype.NEWBIE) {
			chance += 0.06;
		} else if (archetype == Archetype.CASUAL || archetype == Archetype.SPEEDRUNNER) {
			chance += 0.03;
		} else if (archetype == Archetype.BUILDER || archetype == Archetype.HERMIT) {
			chance -= 0.02;
		}

		return Math.max(0.01, chance);
	}

	/** Names it won't misspell: its own, the players', the other Drifters' near it (lower case, each part). */
	private static Set<String> names(DrifterEntity drifter) {
		Set<String> names = new java.util.HashSet<>();
		java.util.function.Consumer<String> add = name -> {
			for (String part : name.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
				names.add(part);
			}
		};
		add.accept(drifter.getName().getString());
		if (drifter.getServer() != null) {
			drifter.getServer().getPlayerList().getPlayers().forEach(player -> add.accept(player.getGameProfile().getName()));
		}

		drifter.level().getEntitiesOfClass(DrifterEntity.class, drifter.getBoundingBox().inflate(48.0)).forEach(other -> add.accept(other.getName().getString()));
		return names;
	}

	private static void tick(MinecraftServer server) {
		if (later.isEmpty()) {
			return;
		}

		for (Iterator<Later> it = later.iterator(); it.hasNext();) {
			Later part = it.next();
			if (part.drifter().isRemoved() || !part.drifter().isAlive()) {
				it.remove();
				continue;
			}

			if (part.drifter().level().getGameTime() >= part.at()) {
				it.remove();
				ChatLines.deliver(part.drifter(), part.text());
			}
		}
	}
}
