package com.dinglebob.drifter.social;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Everything Drifters say, and the answers they understand, from the data files {@code data/<namespace>/drifter_chat/}
 * (a datapack can add more; files add to each other). Written the way players type in Minecraft chat (the user's two
 * guidebooks, 2026-10-23: "How Minecraft Players Chat" and "The Science of Gamer Text Chat"): lowercase, no final period,
 * clipped words, rituals ("o/", "wb", "rip", "gg", "gn"), and each personality its own voice.
 *
 * <p>A file has {@code "lines"}: a key (an event: "hi", "rip", "encounter.escort.ask"...) to either a list (said by
 * anyone) or an object of voices to lists: {@code "any"} and personality archetypes ({@code "pvp_sweat"}, {@code
 * "newbie"}...), whose own lines it mostly picks from. Words in braces are filled in ({@code {player}}, {@code {name}}...).
 * And {@code "answers"}: an intent ("yes", "no", "ready", "unsure", "greeting"...) to the phrases that mean it. A phrase
 * can be a pattern: {@code (a|b)} one of these, {@code [a|b]} one of these or nothing ("(u|you) (got|have) [any|some]
 * food" is twelve phrases).
 *
 * <p>And for what's understood loosely (only when nothing else is: {@link #loosely}): {@code "fillers"}, words that only
 * fill ("any", "pls"), and {@code "same"}, groups of words that mean the same here ("got", "have").
 */
public final class Lines {
	private Lines() {
	}

	public static final String DIRECTORY = "drifter_chat";
	/** How often a Drifter with lines of its own voice uses them rather than everyone's. */
	private static final double OWN_VOICE = 0.65;
	/** How often it says something other than its habit, for the lines it has a habit for. */
	private static final double OFF_HABIT = 0.3;

	/** Key → voice → lines. */
	private static Map<String, Map<String, List<String>>> lines = Map.of();
	/** Intent → phrases (as understood: normalized, slang put plainly; a gapped one's parts joined by {@code *}). */
	private static Map<String, List<String>> answers = Map.of();
	/** Slang → what it means, word for word ("u" → "you", "whatchu" → "what you"), both normalized. */
	private static Map<String, String> slang = Map.of();
	/** Words that only fill (left out when nothing else is understood: "u got ANY food PLS"), understood. */
	private static Set<String> fillers = Set.of();
	/** A word → the words that mean the same here (each tried in the others' place when nothing else is understood). */
	private static Map<String, List<String>> same = Map.of();
	/** Every word of the answer phrases (for a dropped g: "huntin" is "hunting" when that's a word of them). */
	private static Set<String> phraseWords = Set.of();
	/** Every word it knows, and how many answer phrases have it (made when first needed). */
	@Nullable
	private static Map<String, Integer> known;

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return Drifter.id("chat");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				load(manager);
			}
		});
	}

	private static void load(ResourceManager manager) {
		Map<String, Map<String, List<String>>> newLines = new HashMap<>();
		Map<String, List<String>> rawAnswers = new LinkedHashMap<>();
		Map<String, String> newSlang = new HashMap<>();
		List<String> rawFillers = new ArrayList<>();
		List<List<String>> rawSame = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = file.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				if (json.has("lines")) {
					for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("lines").entrySet()) {
						Map<String, List<String>> voices = newLines.computeIfAbsent(entry.getKey(), key -> new HashMap<>());
						if (entry.getValue().isJsonArray()) {
							entry.getValue().getAsJsonArray().forEach(line -> voices.computeIfAbsent("any", v -> new ArrayList<>()).add(line.getAsString()));
						} else {
							for (Map.Entry<String, JsonElement> voice : entry.getValue().getAsJsonObject().entrySet()) {
								voice.getValue().getAsJsonArray().forEach(line -> voices.computeIfAbsent(voice.getKey(), v -> new ArrayList<>()).add(line.getAsString()));
							}
						}
					}
				}

				if (json.has("answers")) {
					for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("answers").entrySet()) {
						List<String> phrases = rawAnswers.computeIfAbsent(entry.getKey(), key -> new ArrayList<>());
						entry.getValue().getAsJsonArray().forEach(phrase -> phrases.add(phrase.getAsString()));
					}
				}

				if (json.has("fillers")) {
					json.getAsJsonArray("fillers").forEach(word -> rawFillers.add(word.getAsString()));
				}

				if (json.has("same")) {
					json.getAsJsonArray("same").forEach(group -> {
						List<String> words = new ArrayList<>();
						group.getAsJsonArray().forEach(word -> words.add(word.getAsString()));
						rawSame.add(words);
					});
				}

				if (json.has("slang")) {
					for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("slang").entrySet()) {
						String word = normalize(entry.getKey()).trim();
						String means = normalize(entry.getValue().getAsString()).trim();
						if (!word.isEmpty() && !word.contains(" ") && !means.isEmpty()) {
							newSlang.put(word, means);
						}
					}
				}
			} catch (Exception e) {
				Drifter.LOGGER.error("Couldn't read Drifter chat file {}", file.getKey(), e);
			}
		}

		// (The phrases put plainly too, once all the slang is in: "what do u think of" and "whatchu think of" alike.)
		slang = newSlang;
		Map<String, List<String>> newAnswers = new LinkedHashMap<>();
		rawAnswers.forEach((intent, raw) -> {
			List<String> phrases = new ArrayList<>();
			for (String pattern : raw) {
				for (String phrase : expand(pattern)) {
					String plain = plainPhrase(phrase);
					if (plain != null && !phrases.contains(plain)) {
						phrases.add(plain);
					}
				}
			}

			newAnswers.put(intent, phrases);
		});
		Set<String> newFillers = new HashSet<>();
		for (String word : rawFillers) {
			String plain = understood(word).trim();
			if (!plain.isEmpty() && !plain.contains(" ")) {
				newFillers.add(plain);
			}
		}

		Map<String, List<String>> newSame = new HashMap<>();
		for (List<String> group : rawSame) {
			List<String> plain = new ArrayList<>();
			for (String word : group) {
				String one = understood(word).trim();
				if (!one.isEmpty() && !plain.contains(one)) {
					plain.add(one);
				}
			}

			for (String word : plain) {
				if (!word.contains(" ")) {
					List<String> others = newSame.computeIfAbsent(word, w -> new ArrayList<>());
					plain.stream().filter(other -> !other.equals(word) && !others.contains(other)).forEach(others::add);
				}
			}
		}

		Set<String> newPhraseWords = new HashSet<>();
		newAnswers.values().forEach(phrases -> phrases.forEach(phrase -> newPhraseWords.addAll(List.of(phrase.replace('*', ' ').trim().split(" +")))));
		lines = newLines;
		answers = newAnswers;
		phraseWords = newPhraseWords;
		fillers = newFillers;
		same = newSame;
		known = null;
		int count = newLines.values().stream().mapToInt(voices -> voices.values().stream().mapToInt(List::size).sum()).sum();
		int phrases = newAnswers.values().stream().mapToInt(List::size).sum();
		Drifter.LOGGER.info("Loaded {} Drifter chat lines ({} kinds), {} answer phrases and {} slang words", count, newLines.size(), phrases, newSlang.size());
	}

	/** The lines for this, by voice (empty if none). */
	public static Map<String, List<String>> voices(String key) {
		return lines.getOrDefault(key, Map.of());
	}

	/** It has lines for this. */
	public static boolean has(String key) {
		return lines.containsKey(key);
	}

	/** A line for this, in its voice, words filled in ({@code vars}: name, value, name, value...); null if there are none. */
	@Nullable
	public static String pick(DrifterEntity drifter, String key, Object... vars) {
		return pickFrom(drifter, lines.get(key), vars);
	}

	/** A line from these voices (a conversation's turn), in its voice, not one it said lately if it has others. */
	@Nullable
	public static String pickFrom(DrifterEntity drifter, @Nullable Map<String, List<String>> voices, Object... vars) {
		String line = template(drifter, voices, vars);
		return line == null ? null : fill(drifter, line, vars);
	}

	/** The line it picks from these voices, its words not yet filled in (null if none suits); see {@link #filled}. */
	@Nullable
	public static String template(DrifterEntity drifter, @Nullable Map<String, List<String>> voices, Object... vars) {
		List<String> pool = fillable(pool(drifter, voices), vars);
		if (pool.isEmpty()) {
			return null;
		}

		// Not what it said lately, nor what anyone just said (two Drifters' "gm gm" in a row: one says "morning").
		long now = drifter.level().getGameTime();
		List<String> mine = pool.stream().filter(line -> !said(drifter, line)).toList();
		List<String> fresh = mine.stream().filter(line -> now - justSaid.getOrDefault(line, Long.MIN_VALUE / 2) > JUST).toList();
		List<String> from = !fresh.isEmpty() ? fresh : !mine.isEmpty() ? mine : pool;
		String line = from.get(drifter.getRandom().nextInt(from.size()));
		remember(drifter, line);
		justSaid.put(line, now);
		if (justSaid.size() > 256) {
			justSaid.values().removeIf(at -> now - at > JUST);
		}

		return line;
	}

	/** A line from {@link #template} with its words filled in. */
	public static String filled(DrifterEntity drifter, String line, Object... vars) {
		return fill(drifter, line, vars);
	}

	/** Lines each Drifter said lately (it doesn't say the same thing over and over; not saved). */
	private static final Map<java.util.UUID, java.util.ArrayDeque<String>> lately = new HashMap<>();
	private static final int LATELY = 24;
	/** Lines anyone said in the last half minute, and when. */
	private static final Map<String, Long> justSaid = new HashMap<>();
	private static final long JUST = 20L * 30;

	private static boolean said(DrifterEntity drifter, String line) {
		java.util.ArrayDeque<String> mine = lately.get(drifter.getUUID());
		return mine != null && mine.contains(line);
	}

	private static void remember(DrifterEntity drifter, String line) {
		java.util.ArrayDeque<String> mine = lately.computeIfAbsent(drifter.getUUID(), id -> new java.util.ArrayDeque<>());
		mine.addLast(line);
		while (mine.size() > LATELY) {
			mine.removeFirst();
		}
	}

	/**
	 * Its own way of saying this: the same line each time (the same Drifter always greets the same way, as players do),
	 * now and then another; null if there are none.
	 */
	@Nullable
	public static String habit(DrifterEntity drifter, String key, Object... vars) {
		List<String> pool = fillable(pool(drifter, key), vars);
		if (pool.isEmpty()) {
			return null;
		}

		if (drifter.getRandom().nextDouble() < OFF_HABIT) {
			return fill(drifter, pool.get(drifter.getRandom().nextInt(pool.size())), vars);
		}

		long seed = drifter.getUUID().getLeastSignificantBits() ^ key.hashCode() * 31L;
		return fill(drifter, pool.get((int)Math.floorMod(seed, pool.size())), vars);
	}

	/** The lines it picks from this time: its personality's own, mostly, or everyone's. */
	private static List<String> pool(DrifterEntity drifter, String key) {
		return pool(drifter, lines.get(key));
	}

	private static List<String> pool(DrifterEntity drifter, @Nullable Map<String, List<String>> voices) {
		if (voices == null) {
			return List.of();
		}

		List<String> own = voices.get(drifter.getPersonality().archetype().name().toLowerCase(Locale.ROOT));
		List<String> any = voices.getOrDefault("any", List.of());
		if (own != null && !own.isEmpty() && (any.isEmpty() || drifter.getRandom().nextDouble() < OWN_VOICE)) {
			return own;
		}

		return any.isEmpty() && own != null ? own : any;
	}

	private static final java.util.regex.Pattern WORD = java.util.regex.Pattern.compile("\\{([a-z_.]+)}");

	/** The lines whose every word in braces it has (a line asking for {player} with no player to name isn't said). */
	private static List<String> fillable(List<String> pool, Object... vars) {
		java.util.Set<String> have = new java.util.HashSet<>();
		have.add("name");
		// (A line about a player may use their pronouns: {they}, {them}...)
		have.addAll(com.dinglebob.drifter.identity.PlayerNames.WORDS);
		for (int i = 0; i + 1 < vars.length; i += 2) {
			if (vars[i + 1] != null) {
				have.add(String.valueOf(vars[i]));
			}
		}

		List<String> ok = new ArrayList<>();
		for (String line : pool) {
			java.util.regex.Matcher words = WORD.matcher(line);
			boolean all = true;
			while (words.find() && all) {
				all = have.contains(words.group(1));
			}

			if (all) {
				ok.add(line);
			}
		}

		return ok;
	}

	/**
	 * The line with its words filled in: {name} is the Drifter's own, the rest from {@code vars}. Anyone's username comes
	 * out as their nickname (xXFrostXx: frost; the user, 2026-10-31: "everybody (even strangers) will refer to them as
	 * frost"); a nickname a player asked for ({nick}) as they asked.
	 */
	private static String fill(DrifterEntity drifter, String line, Object... vars) {
		String filled = line.replace("{name}", com.dinglebob.drifter.identity.Nicknames.of(drifter));
		// (The player it's about, by the name it was given: their pronouns, "they" if it wasn't told.)
		String about = null;
		for (int i = 0; i + 1 < vars.length; i += 2) {
			if ("player".equals(vars[i]) && vars[i + 1] instanceof String player) {
				about = player;
			}
		}

		filled = com.dinglebob.drifter.identity.PlayerNames.fill(filled, com.dinglebob.drifter.identity.PlayerNames.pronounsOf(about));
		for (int i = 0; i + 1 < vars.length; i += 2) {
			String value = String.valueOf(vars[i + 1]);
			if (vars[i + 1] instanceof String text && !"nick".equals(vars[i])) {
				value = com.dinglebob.drifter.identity.Nicknames.spoken(drifter, text);
			}

			filled = filled.replace("{" + vars[i] + "}", value);
		}

		return filled;
	}

	// ---------------------------------------------------------------------------------------------
	// Understanding answers
	// ---------------------------------------------------------------------------------------------

	/**
	 * Chat as it's matched: lower case, apostrophes dropped ("i'm" is "im"), anything else not a letter or digit a space,
	 * and a letter said over and over once ("nooo" is "no", "yesss" is "yes": both sides of a match go the same way).
	 */
	public static String normalize(String text) {
		String lower = text.toLowerCase(Locale.ROOT).replace("'", "").replace("’", "").replaceAll("[^a-z0-9]+", " ").trim();
		StringBuilder out = new StringBuilder();
		char last = 0;
		for (char c : lower.toCharArray()) {
			if (c != last || c == ' ' || Character.isDigit(c)) {
				out.append(c);
			}

			last = c;
		}

		return " " + out + " ";
	}

	/**
	 * Chat as it's understood: {@link #normalize normalized}, then slang put plainly word by word ("whatchu think bout me" is
	 * "what you think about me"), from the {@code "slang"} of the chat data. Phrases are understood the same way, so either
	 * spelling of a phrase matches the other.
	 */
	public static String understood(String text) {
		String normal = normalize(text);
		if (slang.isEmpty()) {
			return normal;
		}

		StringBuilder out = new StringBuilder(" ");
		for (String word : normal.trim().split(" ")) {
			if (!word.isEmpty()) {
				String plain = slang.getOrDefault(word, word);
				// (A dropped g: "huntin", "minin", "makin".)
				if (plain.equals(word) && word.length() >= 5 && word.endsWith("in") && phraseWords.contains(word + "g") && !phraseWords.contains(word)) {
					plain = word + "g";
				}

				out.append(plain).append(' ');
			}
		}

		return out.toString();
	}

	/**
	 * A phrase as it's matched: understood, and a gapped one ({@code "think * of me"}: these words in this order, anything
	 * between) its parts joined by {@code *}. Null if nothing's left.
	 */
	@Nullable
	private static String plainPhrase(String phrase) {
		if (!phrase.contains("*")) {
			String plain = understood(phrase);
			return plain.isBlank() ? null : plain;
		}

		List<String> parts = new ArrayList<>();
		for (String part : phrase.split("\\*")) {
			String plain = understood(part);
			if (!plain.isBlank()) {
				parts.add(plain);
			}
		}

		return parts.isEmpty() ? null : String.join("*", parts);
	}

	/**
	 * How much of this message the phrase matched (its length; a gapped one's parts', less a little: a phrase said whole is
	 * the surer match), or -1 if it isn't in it.
	 */
	private static int matched(String said, String phrase) {
		if (phrase.indexOf('*') < 0) {
			return said.contains(phrase) ? phrase.length() : -1;
		}

		String[] parts = phrase.split("\\*");
		int length = 0;
		for (String part : parts) {
			length += part.length();
		}

		// (Each place the first part is, the rest after it, each close behind the last.)
		for (int start = said.indexOf(parts[0]); start >= 0; start = said.indexOf(parts[0], start + 1)) {
			if (follows(said, parts, 1, start + parts[0].length() - 1)) {
				return length - GAPPED_LESS;
			}
		}

		return -1;
	}

	/** The parts from this one on are in the message in order, each within a few words of where the last ended. */
	private static boolean follows(String said, String[] parts, int i, int from) {
		if (i == parts.length) {
			return true;
		}

		for (int at = said.indexOf(parts[i], from); at >= 0; at = said.indexOf(parts[i], at + 1)) {
			if (said.substring(from, at + 1).chars().filter(c -> c == ' ').count() - 1 > MOST_GAP_WORDS) {
				return false;
			}

			// (The space a part ends on can start the next.)
			if (follows(said, parts, i + 1, at + parts[i].length() - 1)) {
				return true;
			}
		}

		return false;
	}

	/** How much shorter a gapped phrase counts than the same words said whole. */
	private static final int GAPPED_LESS = 2;
	/** The most words a gapped phrase's gap can be ("think * of me": "think" and "of me" at most this far apart). */
	private static final int MOST_GAP_WORDS = 2;

	/** The longest phrase of this intent found in the message (as understood: lower case, no punctuation, slang plain), or null. */
	@Nullable
	public static String phrase(String text, String intent) {
		String said = understood(text);
		String best = null;
		int bestLength = 0;
		for (String phrase : answers.getOrDefault(intent, List.of())) {
			int length = matched(said, phrase);
			if (length > bestLength) {
				best = phrase;
				bestLength = length;
			}
		}

		return best == null ? null : best.replace('*', ' ').replaceAll(" +", " ").trim();
	}

	/**
	 * What this message means, of these intents ("yes", "no"...): the one whose phrase found in it is longest ("no
	 * problem" is a yes, though it has "no" in it; "hell no" a no, though "hell" isn't), a tie going to the earlier intent
	 * given. Null if none of them.
	 */
	@Nullable
	public static String answer(String text, String... intents) {
		String said = understood(text);
		String best = null;
		int bestLength = 0;
		for (String intent : intents) {
			for (String phrase : answers.getOrDefault(intent, List.of())) {
				int length = matched(said, phrase);
				if (length > bestLength) {
					best = intent;
					bestLength = length;
				}
			}
		}

		return best;
	}

	// ---------------------------------------------------------------------------------------------
	// Phrase patterns
	// ---------------------------------------------------------------------------------------------

	/** The most phrases one pattern makes. */
	private static final int MOST_FROM_PATTERN = 512;

	/**
	 * A phrase as the phrases it stands for: {@code (a|b)} is one of these, {@code [a|b]} one of these or nothing
	 * ("(u|you) (got|have) [any|some] food" is twelve; "(any|some)thing" two). A phrase with neither is itself.
	 */
	public static List<String> expand(String phrase) {
		List<String> out = new ArrayList<>();
		expand(phrase, out);
		return out;
	}

	private static void expand(String phrase, List<String> out) {
		if (out.size() >= MOST_FROM_PATTERN) {
			return;
		}

		int open = -1;
		for (int i = 0; i < phrase.length() && open < 0; i++) {
			if (phrase.charAt(i) == '(' || phrase.charAt(i) == '[') {
				open = i;
			}
		}

		if (open < 0) {
			String one = phrase.replaceAll(" +", " ").trim();
			if (!one.isEmpty() && !out.contains(one)) {
				out.add(one);
			}

			return;
		}

		int depth = 0;
		int end = -1;
		for (int i = open; i < phrase.length() && end < 0; i++) {
			char c = phrase.charAt(i);
			if (c == '(' || c == '[') {
				depth++;
			} else if ((c == ')' || c == ']') && --depth == 0) {
				end = i;
			}
		}

		if (end < 0) {
			// (Never closed: the brackets left out, the words as written.)
			expand(phrase.replace('(', ' ').replace('[', ' '), out);
			return;
		}

		List<String> choices = new ArrayList<>();
		int from = open + 1;
		depth = 0;
		for (int i = open + 1; i < end; i++) {
			char c = phrase.charAt(i);
			if (c == '(' || c == '[') {
				depth++;
			} else if (c == ')' || c == ']') {
				depth--;
			} else if (c == '|' && depth == 0) {
				choices.add(phrase.substring(from, i));
				from = i + 1;
			}
		}

		choices.add(phrase.substring(from, end));
		if (phrase.charAt(open) == '[') {
			choices.add("");
		}

		String before = phrase.substring(0, open);
		String after = phrase.substring(end + 1);
		for (String choice : choices) {
			expand(before + choice + after, out);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Understood loosely
	// ---------------------------------------------------------------------------------------------

	/** Every intent but these: what "means something" is, for {@link #meansSomething}. */
	public static boolean meansSomething(String text, Set<String> leaveOut) {
		String said = understood(text);
		for (Map.Entry<String, List<String>> entry : answers.entrySet()) {
			if (!leaveOut.contains(entry.getKey())) {
				for (String phrase : entry.getValue()) {
					if (matched(said, phrase) > 0) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * The message put the way one of the answer phrases has it, when it isn't already (the user, 2026-11-14: "can you find
	 * a way for me to ask/tell something how ever i want"): its typos put right ("wher", "hungy"), the words that only fill
	 * left out ("u got ANY food PLS"), a word put as another that means the same ("got", "have"), and in any order ("food,
	 * got any?"). The first of these that {@code means} takes, or null. Only for when nothing else is understood: these
	 * are guesses. {@code keep} are words never put right (names).
	 */
	@Nullable
	public static String loosely(String text, Set<String> keep, Predicate<String> means) {
		String plain = understood(text).trim();
		if (plain.isEmpty()) {
			return null;
		}

		List<String> words = new ArrayList<>(List.of(plain.split(" ")));
		List<String> fixed = new ArrayList<>();
		for (String word : words) {
			String right = keep.contains(word) ? null : typoOf(word);
			fixed.add(right == null ? word : right);
		}

		List<String> bare = new ArrayList<>(fixed);
		bare.removeIf(fillers::contains);
		Set<String> tries = new LinkedHashSet<>();
		tries.add(String.join(" ", fixed));
		if (!bare.isEmpty()) {
			tries.add(String.join(" ", bare));
		}

		for (List<String> base : List.of(fixed, bare)) {
			for (int i = 0; i < base.size(); i++) {
				for (String other : same.getOrDefault(base.get(i), List.of())) {
					List<String> swapped = new ArrayList<>(base);
					swapped.set(i, other);
					tries.add(String.join(" ", swapped));
				}
			}
		}

		tries.addAll(anyOrder(fixed, bare.isEmpty() ? fixed.size() : bare.size()));
		String asked = text.trim().endsWith("?") ? "?" : "";
		for (String one : tries) {
			if (!one.isBlank() && !one.equals(plain) && means.test(one + asked)) {
				return one + asked;
			}
		}

		return null;
	}

	/** The most words more than a phrase's a message has, to be that phrase in another order. */
	private static final int MOST_MORE_WORDS = 3;

	/**
	 * The message as the phrases whose every word is in it, in another order ("food got you any" has "you got any"), the
	 * longest first, each with the message's other words after it (so "food" is still there to be what's asked for).
	 * {@code size}: how many words it has that aren't only filling.
	 */
	private static List<String> anyOrder(List<String> words, int size) {
		Set<String> have = new HashSet<>(words);
		List<String> found = new ArrayList<>();
		for (List<String> phrases : answers.values()) {
			for (String phrase : phrases) {
				// (Every word of it, fillers too: "whats up there" isn't in "whats up". Its words that aren't fillers, two at least.)
				List<String> parts = List.of(phrase.replace('*', ' ').trim().split(" +"));
				long meant = parts.stream().filter(part -> !fillers.contains(part)).count();
				if (meant >= 2 && size <= meant + MOST_MORE_WORDS && have.containsAll(parts) && !found.contains(phrase)) {
					found.add(phrase);
				}
			}
		}

		found.sort((a, b) -> b.length() - a.length());
		List<String> out = new ArrayList<>();
		for (String phrase : found.subList(0, Math.min(6, found.size()))) {
			String said = phrase.replace('*', ' ').trim().replaceAll(" +", " ");
			Set<String> used = new HashSet<>(List.of(said.split(" ")));
			StringBuilder rest = new StringBuilder(said);
			for (String word : words) {
				if (!used.contains(word)) {
					rest.append(' ').append(word);
				}
			}

			out.add(rest.toString());
		}

		return out;
	}

	private static final java.util.regex.Pattern A_WORD = java.util.regex.Pattern.compile("[A-Za-z']+");

	/**
	 * The message with its typos put right, word for word, all else as typed (names in {@code keep} left alone); the same
	 * message if none. For when that says more than the message as typed ({@link #longest}): found in play, "its been a
	 * busy dayt" was "busy?", what it's doing.
	 */
	public static String typosFixed(String text, Set<String> keep) {
		java.util.regex.Matcher m = A_WORD.matcher(text);
		StringBuilder out = new StringBuilder();
		int last = 0;
		boolean changed = false;
		while (m.find()) {
			String word = m.group();
			String plain = normalize(word).trim();
			String right = plain.isEmpty() || plain.contains(" ") || keep.contains(plain) ? null : typoOf(plain);
			out.append(text, last, m.start()).append(right != null ? right : word);
			changed |= right != null;
			last = m.end();
		}

		out.append(text.substring(last));
		return changed ? out.toString() : text;
	}

	/** How long the longest phrase of any meaning found in the message is (0: none). */
	public static int longest(String text) {
		String said = understood(text);
		int best = 0;
		for (List<String> phrases : answers.values()) {
			for (String phrase : phrases) {
				best = Math.max(best, matched(said, phrase));
			}
		}

		return best;
	}

	/** The word it knows that this one is a typo of ("wher": "where"), or null (it knows it, or nothing's close). */
	@Nullable
	private static String typoOf(String word) {
		Map<String, Integer> words = known();
		if (word.length() < 4 || words.containsKey(word) || word.chars().anyMatch(Character::isDigit)) {
			return null;
		}

		// (One letter off in a short word, two in a long one; a word starting the same, and in more phrases, first.)
		int most = word.length() >= 7 ? 2 : 1;
		String best = null;
		int bestDistance = most + 1;
		long bestScore = -1;
		for (Map.Entry<String, Integer> entry : words.entrySet()) {
			String other = entry.getKey();
			// (A short word starts with the same letter: "qwer" isn't "wer", where. A typo there is rare.)
			if (other.length() < 3 || Math.abs(other.length() - word.length()) > most || word.length() <= 5 && other.charAt(0) != word.charAt(0)) {
				continue;
			}

			int distance = distance(word, other, most);
			long score = (other.charAt(0) == word.charAt(0) ? 1_000_000_000L : 0L) + entry.getValue();
			if (distance < bestDistance || distance == bestDistance && score > bestScore) {
				best = other;
				bestDistance = distance;
				bestScore = score;
			}
		}

		return bestDistance <= most ? best : null;
	}

	/** Letters to change, add, drop or swap to make one word the other (more than {@code most}: most + 1). */
	private static int distance(String a, String b, int most) {
		int[][] d = new int[a.length() + 1][b.length() + 1];
		for (int i = 0; i <= a.length(); i++) {
			d[i][0] = i;
		}

		for (int j = 0; j <= b.length(); j++) {
			d[0][j] = j;
		}

		for (int i = 1; i <= a.length(); i++) {
			int rowBest = Integer.MAX_VALUE;
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				int v = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
				if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
					v = Math.min(v, d[i - 2][j - 2] + 1);
				}

				d[i][j] = v;
				rowBest = Math.min(rowBest, v);
			}

			if (rowBest > most) {
				return most + 1;
			}
		}

		return d[a.length()][b.length()];
	}

	/**
	 * Every word it knows, to tell a typo from a word: the answer phrases' (counted: the more phrases, the likelier
	 * meant), the slang's, its own chat lines', and every item's and mob's name ("bread" is a word, not a typo of "break").
	 */
	private static Map<String, Integer> known() {
		Map<String, Integer> words = known;
		if (words != null) {
			return words;
		}

		Map<String, Integer> made = new HashMap<>();
		for (List<String> phrases : answers.values()) {
			for (String phrase : phrases) {
				for (String word : phrase.replace('*', ' ').trim().split(" +")) {
					made.merge(word, 1, Integer::sum);
				}
			}
		}

		List<String> more = new ArrayList<>(slang.values());
		more.addAll(slang.keySet());
		more.addAll(fillers);
		same.forEach((word, others) -> {
			more.add(word);
			more.addAll(others);
		});
		lines.values().forEach(voices -> voices.values().forEach(more::addAll));
		net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().forEach(id -> more.add(id.getPath().replace('_', ' ')));
		net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet().forEach(id -> more.add(id.getPath().replace('_', ' ')));
		for (String text : more) {
			for (String word : normalize(text).trim().split(" ")) {
				if (!word.isEmpty()) {
					made.putIfAbsent(word, 0);
				}
			}
		}

		made.remove("");
		known = made;
		return made;
	}
}
