package com.dinglebob.drifter.social;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
 * And {@code "answers"}: an intent ("yes", "no", "ready", "unsure", "greeting"...) to the phrases that mean it.
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
	/** Intent → phrases (normalized). */
	private static Map<String, List<String>> answers = Map.of();

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
		Map<String, List<String>> newAnswers = new LinkedHashMap<>();
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
						List<String> phrases = newAnswers.computeIfAbsent(entry.getKey(), key -> new ArrayList<>());
						entry.getValue().getAsJsonArray().forEach(phrase -> {
							String normal = normalize(phrase.getAsString());
							if (!normal.isBlank()) {
								phrases.add(normal);
							}
						});
					}
				}
			} catch (Exception e) {
				Drifter.LOGGER.error("Couldn't read Drifter chat file {}", file.getKey(), e);
			}
		}

		lines = newLines;
		answers = newAnswers;
		int count = newLines.values().stream().mapToInt(voices -> voices.values().stream().mapToInt(List::size).sum()).sum();
		int phrases = newAnswers.values().stream().mapToInt(List::size).sum();
		Drifter.LOGGER.info("Loaded {} Drifter chat lines ({} kinds) and {} answer phrases", count, newLines.size(), phrases);
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

		List<String> fresh = pool.stream().filter(line -> !said(drifter, line)).toList();
		List<String> from = fresh.isEmpty() ? pool : fresh;
		String line = from.get(drifter.getRandom().nextInt(from.size()));
		remember(drifter, line);
		return line;
	}

	/** A line from {@link #template} with its words filled in. */
	public static String filled(DrifterEntity drifter, String line, Object... vars) {
		return fill(drifter, line, vars);
	}

	/** Lines each Drifter said lately (it doesn't say the same thing over and over; not saved). */
	private static final Map<java.util.UUID, java.util.ArrayDeque<String>> lately = new HashMap<>();
	private static final int LATELY = 24;

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

	/** The line with its words filled in: {name} is the Drifter's own, the rest from {@code vars}. */
	private static String fill(DrifterEntity drifter, String line, Object... vars) {
		String filled = line.replace("{name}", drifter.getName().getString());
		for (int i = 0; i + 1 < vars.length; i += 2) {
			filled = filled.replace("{" + vars[i] + "}", String.valueOf(vars[i + 1]));
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
	 * What this message means, of these intents ("yes", "no"...): the one whose phrase found in it is longest ("no
	 * problem" is a yes, though it has "no" in it; "hell no" a no, though "hell" isn't), a tie going to the earlier intent
	 * given. Null if none of them.
	 */
	@Nullable
	public static String answer(String text, String... intents) {
		String said = normalize(text);
		String best = null;
		int bestLength = 0;
		for (String intent : intents) {
			for (String phrase : answers.getOrDefault(intent, List.of())) {
				if (phrase.length() > bestLength && said.contains(phrase)) {
					best = intent;
					bestLength = phrase.length();
				}
			}
		}

		return best;
	}
}
