package com.dinglebob.drifter.identity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.registry.ModEntities;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * What everyone calls someone (the user, 2026-10-31: "the name would be like xXfrostXx but everybody (even strangers)
 * will refer to them as frost ... that way its realistic and nobody is typing a complex name everytime"): the short name
 * a username comes down to, the same for everyone, worked out from the name itself. The styling comes off (xX..Xx, itz,
 * the, mc, ttv, digits, underscores, letters for numbers, a held letter), the name splits into its words, and the word
 * people would say is kept: a first name if there is one, the thing rather than the adjective (sleepy_fox: fox), else
 * the first word (pinewick42: pine). Players' names too (only what's said; the name over their head stays as it is).
 */
public final class Nicknames {
	private Nicknames() {
	}

	/** Shortest a nickname may be. */
	private static final int MIN = 3;
	/** Words that aren't anyone's name in a sentence-name (CallMeFern: fern, TheRealOtter: otter). */
	private static final Set<String> FILLER = Set.of("call", "me", "just", "a", "an", "is", "here", "on", "top", "the", "real", "not", "im", "i", "am",
		"its", "itz", "my", "your", "fan", "main", "lover", "enjoyer", "gaming", "games", "official", "mc", "yt", "ttv", "tv", "live", "pro", "plays",
		"xd", "lol", "no", "life", "was", "hey", "ask", "team", "guy", "forever", "time", "mr", "mister", "x", "xx", "o", "z", "uwu", "ez", "gg", "hd",
		"pvp", "boi", "yo", "hq", "irl", "btw", "lil", "big");
	/** Titles that go in front (DrSloth: sloth); a word that's a title on its own stays (Captain: captain). */
	private static final Set<String> TITLES = Set.of("dr", "captain", "sir", "lord", "lady", "professor", "agent", "chef", "king", "queen", "prince",
		"count", "baron", "doctor", "general", "major", "officer", "uncle", "auntie", "grandpa", "granny", "saint", "miss");

	private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

	/** Forgets what it worked out (the word lists were loaded again). */
	static void clear() {
		CACHE.clear();
	}

	/** What everyone calls the one with this username. */
	public static String of(String username) {
		return CACHE.computeIfAbsent(username, Nicknames::work);
	}

	/** What everyone calls this Drifter. */
	public static String of(DrifterEntity drifter) {
		return of(drifter.getName().getString());
	}

	private static String work(String username) {
		Usernames.Lists lists = Usernames.lists();
		Set<String> words = new HashSet<>();
		lists.words().forEach(word -> words.add(word.toLowerCase(Locale.ROOT)));
		Set<String> adjectives = new HashSet<>();
		lists.adjectives().forEach(word -> adjectives.add(word.toLowerCase(Locale.ROOT)));
		Set<String> firstNames = new HashSet<>();
		lists.firstNames().forEach(word -> firstNames.add(word.toLowerCase(Locale.ROOT)));

		// A last digit standing for a letter, when that makes a word (a5tr0: astro).
		String end = lastAsLetter(username);
		if (end != null) {
			String decoded = decode(end.substring(0, end.length() - 1)).toLowerCase(Locale.ROOT) + end.charAt(end.length() - 1);
			if (words.contains(decoded) || firstNames.contains(decoded)) {
				return decoded;
			}
		}

		// The xX...Xx wrapper off first (xXSillynimbusXx).
		String bare = username;
		String lowered = username.toLowerCase(Locale.ROOT);
		if (lowered.length() > 5 && lowered.startsWith("xx")) {
			bare = bare.substring(2);
		}

		if (bare.length() > 5 && bare.toLowerCase(Locale.ROOT).endsWith("xx")) {
			bare = bare.substring(0, bare.length() - 2);
		}

		// Letters for numbers inside a word (c0bble, m0ss: not the digits at its end, leo2011).
		username = bare;
		StringBuilder plain = new StringBuilder();
		for (int i = 0; i < username.length(); i++) {
			char c = username.charAt(i);
			// (Or the very first, before a letter: 5ock.)
			boolean inside = i < username.length() - 1 && Character.isLetter(username.charAt(i + 1))
				&& (i == 0 || Character.isLetter(username.charAt(i - 1)));
			char letter = inside ? switch (c) {
				case '0' -> 'o';
				case '3' -> 'e';
				case '4' -> 'a';
				case '1' -> 'i';
				case '5' -> 's';
				default -> c;
			} : c;
			// (In capitals among capitals: S1GIL is SIGIL, not split at the i.)
			boolean shouting = i > 0 && Character.isUpperCase(username.charAt(i - 1));
			plain.append(shouting && letter != c ? Character.toUpperCase(letter) : letter);
		}

		// Its parts: split at underscores and digits, and where a capital starts a word (FrostOtter, xXFrostXx).
		List<String> parts = new ArrayList<>();
		StringBuilder part = new StringBuilder();
		String name = plain.toString();
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			boolean upperStart = Character.isUpperCase(c) && i > 0 && (Character.isLowerCase(name.charAt(i - 1))
				|| Character.isUpperCase(name.charAt(i - 1)) && i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1)));
			if (!Character.isLetter(c) || upperStart) {
				flush(parts, part);
				if (Character.isLetter(c)) {
					part.append(c);
				}
			} else {
				part.append(c);
			}
		}

		flush(parts, part);
		List<String> kept = new ArrayList<>();
		for (String p : parts) {
			String lower = collapse(p.toLowerCase(Locale.ROOT));
			// (Styling glued on: itzmossy, NoodleMC, thereal...: the known bits off the front and the end.)
			for (int pass = 0; pass < 3; pass++) {
				lower = strip(lower, lists, words, firstNames, adjectives);
			}
			if (lower.length() >= 2 && !FILLER.contains(lower) && !TITLES.contains(lower)) {
				kept.add(lower);
			}
		}

		if (kept.isEmpty()) {
			// Nothing but styling or a title (Captain, xXProXx): the longest part as it is.
			String longest = parts.stream().map(p -> collapse(p.toLowerCase(Locale.ROOT))).max(java.util.Comparator.comparingInt(String::length)).orElse("");
			return longest.length() >= MIN ? longest : username.toLowerCase(Locale.ROOT);
		}

		// A first name, if it has one (Kai_Otter: kai).
		for (String p : kept) {
			if (firstNames.contains(p)) {
				return p;
			}
		}

		// One part made of two words (pinewick, sleepyfox, veratoast): split it where the first known word ends, and a
		// first name in it is the name.
		if (kept.size() == 1) {
			kept = split(kept.get(0), words, adjectives, firstNames);
			for (String p : kept) {
				if (firstNames.contains(p)) {
					return p;
				}
			}
		}

		// The thing rather than what it's like (sleepy_fox: fox), else the first word (frost_otter: frost).
		List<String> named = kept.stream().filter(p -> !TITLES.contains(p) && !FILLER.contains(p)).toList();
		if (!named.isEmpty()) {
			kept = named;
		}

		String pick = kept.get(0);
		if (adjectives.contains(pick) && kept.size() > 1) {
			pick = kept.get(1);
		}

		if (pick.length() < MIN) {
			pick = kept.stream().filter(p -> p.length() >= MIN).findFirst().orElse(String.join("", kept));
		}

		return pick;
	}

	/** The name with its last digit as the letter it stands for (a5tr0: "a5tro"), if it ends in one after a letter; else null. */
	@org.jetbrains.annotations.Nullable
	private static String lastAsLetter(String name) {
		if (name.length() < 4 || !Character.isLetter(name.charAt(name.length() - 2))) {
			return null;
		}

		char letter = switch (name.charAt(name.length() - 1)) {
			case '0' -> 'o';
			case '3' -> 'e';
			case '4' -> 'a';
			case '1' -> 'i';
			case '5' -> 's';
			default -> 0;
		};
		return letter == 0 ? null : name.substring(0, name.length() - 1) + letter;
	}

	/** Digits standing for letters, every one (for the check above). */
	private static String decode(String text) {
		return text.replace('0', 'o').replace('3', 'e').replace('4', 'a').replace('1', 'i').replace('5', 's');
	}

	private static void flush(List<String> parts, StringBuilder part) {
		if (!part.isEmpty()) {
			parts.add(part.toString());
			part.setLength(0);
		}
	}

	/** A letter held three or more times once (slowww: slow). */
	private static String collapse(String word) {
		// (Held at the end, one; in the middle, two, for a word that has two: tufffox is tuff and fox.)
		return word.replaceAll("(.)\\1{2,}$", "$1").replaceAll("(.)\\1{2,}", "$1$1");
	}

	/** Known styling off the front and the end of one part (itzmossy: mossy, noodlemc: noodle), a word left behind. */
	private static String strip(String part, Usernames.Lists lists, Set<String> words, Set<String> firstNames, Set<String> adjectives) {
		if (words.contains(part) || firstNames.contains(part) || adjectives.contains(part)) {
			return part;
		}

		// A letter doubled that the word hasn't (buunny, foxx): one of them off, if that's a word.
		for (int i = 1; i < part.length(); i++) {
			if (part.charAt(i) == part.charAt(i - 1)) {
				String once = part.substring(0, i) + part.substring(i + 1);
				if (words.contains(once) || firstNames.contains(once) || adjectives.contains(once)) {
					return once;
				}
			}
		}

		// (Two words already, cobaltolive: nothing to take off.)
		if (split(part, words, adjectives, firstNames).size() > 1) {
			return part;
		}

		List<String> prefixes = lists.prefixes().stream().map(prefix -> prefix.toLowerCase(Locale.ROOT).replace("_", "")).distinct()
			.sorted(java.util.Comparator.comparingInt(String::length).reversed()).toList();
		for (String p : prefixes) {
			if (!p.isEmpty() && part.startsWith(p) && part.length() - p.length() >= MIN) {
				String rest = part.substring(p.length());
				// (A one-letter one, x or o, only if a word is left: xafkmaroon, not xenon.)
				if (p.length() >= 2 || words.contains(rest) || adjectives.contains(rest) || split(rest, words, adjectives, firstNames).size() > 1) {
					return rest;
				}
			}
		}

		for (String suffix : lists.suffixes()) {
			String s = suffix.toLowerCase(Locale.ROOT).replace("_", "");
			if (s.length() >= 2 && part.endsWith(s) && part.length() - s.length() >= MIN) {
				return part.substring(0, part.length() - s.length());
			}
		}

		return part;
	}

	/** One part as two known words if it is two (pinewick: pine, wick), else as it is. */
	private static List<String> split(String part, Set<String> words, Set<String> adjectives, Set<String> firstNames) {
		for (int cut = part.length() - 2; cut >= MIN - 1; cut--) {
			String head = part.substring(0, cut);
			String tail = part.substring(cut);
			boolean headKnown = words.contains(head) || adjectives.contains(head) || firstNames.contains(head);
			if (headKnown && (words.contains(tail) || firstNames.contains(tail))) {
				return List.of(head, tail);
			}

			// (A letter both words have, written once: tuffox for tuff and fox.)
			if (headKnown && words.contains(head.charAt(head.length() - 1) + tail)) {
				return List.of(head, head.charAt(head.length() - 1) + tail);
			}
		}

		return List.of(part);
	}

	// ---------------------------------------------------------------------------------------------
	// Who's a person (only their names are shortened in what's said; a town or an item stays as it is)
	// ---------------------------------------------------------------------------------------------

	private static Set<String> people = Set.of();
	private static long peopleAt = Long.MIN_VALUE;
	private static final long PEOPLE_EVERY = 100L;

	/** Someone's username: a player online, a loaded Drifter, or someone it knows of. */
	public static boolean isPerson(DrifterEntity speaker, String text) {
		if (text.isEmpty() || text.length() > 16 || !text.matches("[A-Za-z0-9_]+")) {
			return false;
		}

		String lower = text.toLowerCase(Locale.ROOT);
		MinecraftServer server = speaker.getServer();
		if (server != null) {
			long now = server.getTickCount();
			if (now - peopleAt >= PEOPLE_EVERY || now < peopleAt) {
				Set<String> fresh = new HashSet<>();
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					fresh.add(player.getGameProfile().getName().toLowerCase(Locale.ROOT));
				}

				for (ServerLevel level : server.getAllLevels()) {
					for (DrifterEntity drifter : level.getEntities(ModEntities.DRIFTER, DrifterEntity::hasCustomName)) {
						fresh.add(drifter.getName().getString().toLowerCase(Locale.ROOT));
					}
				}

				people = fresh;
				peopleAt = now;
			}
		}

		if (people.contains(lower)) {
			return true;
		}

		for (var relationship : speaker.getSocial().all()) {
			if (relationship.name != null && relationship.name.equalsIgnoreCase(text)) {
				return true;
			}
		}

		for (var bond : speaker.getBonds().all()) {
			if (bond.name != null && bond.name.equalsIgnoreCase(text)) {
				return true;
			}
		}

		return false;
	}

	/** The text as said: a username comes out as its nickname, anything else as it is. */
	public static String spoken(DrifterEntity speaker, String text) {
		return isPerson(speaker, text) ? of(text) : text;
	}
}
