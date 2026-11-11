package com.dinglebob.drifter.identity;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

/**
 * What a player asked the world to call them, and their pronouns (the user, 2026-11-11: "picking our name and pronouns"):
 * told to Drifter by an addon ({@code DrifterApi.callPlayer}); nothing told, a player is their Minecraft name and "they",
 * as before. Drifters say the chosen name as typed wherever they'd say the nickname of the name over a player's head
 * ({@link Nicknames#of(String)}): it's what they're called, nothing hidden. Commands, logs and thoughts keep the
 * Minecraft name. Not saved here (whoever tells it keeps it).
 */
public final class PlayerNames {
	private PlayerNames() {
	}

	/** A chosen name (empty: none) and pronouns ("they", "he", "she"). */
	public record Called(String name, String pronouns) {
	}

	private static final Called PLAIN = new Called("", "they");
	private static final Map<UUID, Called> BY_ID = new ConcurrentHashMap<>();
	/** By Minecraft name, lowercase: what's said is worked out from names. */
	private static final Map<String, Called> BY_NAME = new ConcurrentHashMap<>();
	private static final Map<UUID, String> NAME_OF = new ConcurrentHashMap<>();

	/** This player (their id and Minecraft name) is called this from now on (empty name: their Minecraft name). */
	public static void set(UUID id, String minecraftName, String name, String pronouns) {
		String key = minecraftName.toLowerCase(Locale.ROOT);
		String was = NAME_OF.put(id, key);
		if (was != null) {
			BY_NAME.remove(was);
		}

		Called called = new Called(name == null ? "" : name.trim(), switch (pronouns == null ? "" : pronouns) {
			case "he" -> "he";
			case "she" -> "she";
			default -> "they";
		});
		BY_ID.put(id, called);
		BY_NAME.put(key, called);
	}

	/** Everything forgotten (the server stopped). */
	public static void clear() {
		BY_ID.clear();
		BY_NAME.clear();
		NAME_OF.clear();
	}

	/** The name the one with this Minecraft name chose, or null (none chosen, or not a player it was told of). */
	@Nullable
	public static String chosen(String minecraftName) {
		Called called = BY_NAME.get(minecraftName.toLowerCase(Locale.ROOT));
		return called == null || called.name().isEmpty() ? null : called.name();
	}

	/** What this player is called: the name they chose, or their Minecraft name. */
	public static String of(UUID id, String minecraftName) {
		Called called = BY_ID.get(id);
		return called == null || called.name().isEmpty() ? minecraftName : called.name();
	}

	/** "they", "he" or "she". */
	public static String pronouns(@Nullable UUID id) {
		return id == null ? PLAIN.pronouns() : BY_ID.getOrDefault(id, PLAIN).pronouns();
	}

	/** By Minecraft name (as lines are filled: from the name they were given). */
	public static String pronounsOf(@Nullable String minecraftName) {
		return minecraftName == null ? PLAIN.pronouns() : BY_NAME.getOrDefault(minecraftName.toLowerCase(Locale.ROOT), PLAIN).pronouns();
	}

	private static final String[] WORD_LIST = {"they", "them", "their", "theirs", "themself", "theyre", "theyve"};
	/** The words in braces a line about a player may use. */
	public static final Set<String> WORDS = Set.of(WORD_LIST);

	/**
	 * A line about someone with these pronouns, its words in braces filled in: {they} {them} {their} {theirs} {themself}
	 * {theyre} {theyve} (he, him, his, his, himself, he's, he's; she, her, her, hers, herself, she's, she's), and the same
	 * with a capital ({They}).
	 */
	public static String fill(String line, String pronouns) {
		if (line.indexOf('{') < 0) {
			return line;
		}

		String[] forms = switch (pronouns) {
			case "he" -> new String[] {"he", "him", "his", "his", "himself", "he's", "he's"};
			case "she" -> new String[] {"she", "her", "her", "hers", "herself", "she's", "she's"};
			default -> new String[] {"they", "them", "their", "theirs", "themself", "they're", "they've"};
		};
		String filled = line;
		for (int i = 0; i < WORD_LIST.length; i++) {
			filled = filled.replace("{" + WORD_LIST[i] + "}", forms[i]);
			filled = filled.replace("{" + Character.toUpperCase(WORD_LIST[i].charAt(0)) + WORD_LIST[i].substring(1) + "}",
				Character.toUpperCase(forms[i].charAt(0)) + forms[i].substring(1));
		}

		return filled;
	}

	/** {@link #fill(String, String)} for this player. */
	public static String fill(String line, @Nullable UUID player) {
		return fill(line, pronouns(player));
	}
}
