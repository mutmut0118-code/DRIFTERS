package com.dinglebob.drifter.whim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * The checks a whim gets as it loads (user, 2026-10-20: "can you make a system where its more easier to add new whims in
 * the future?"): a misspelt field or param, an item, block or mob the game doesn't have, a gate letter that means
 * nothing. Each is a warning in the log naming the whim and its file, with a guess at what was meant. Only a whim that
 * can't work at all is left out (a wrong name where only certain names will do, an id that isn't an id, a broken steps
 * list): {@link Whim#parse} and the checks here throw for those. docs/WHIMS_HOWTO.md is the guide.
 */
final class WhimCheck {
	/** Item matchers that aren't ids ({@link WhimActions#itemMatcher}). */
	static final Set<String> MATCHERS = Set.of("@jukebox_playable", "@modded_bow", "@modded_shield");

	private WhimCheck() {
	}

	/** Warnings for this whim (it parsed); throws if it can't work. */
	static List<String> check(JsonObject json, Whim whim) {
		List<String> warnings = new ArrayList<>();
		for (String field : json.keySet()) {
			if (!Whim.FIELDS.contains(field)) {
				warnings.add("unknown field \"" + field + "\"" + didYouMean(field, Whim.FIELDS) + " (ignored)");
			}
		}

		if (json.has("gates")) {
			for (char letter : json.get("gates").getAsString().toUpperCase(java.util.Locale.ROOT).toCharArray()) {
				if ("GMRL".indexOf(letter) < 0) {
					warnings.add("gate letter '" + letter + "' means nothing (G, M, R or L)");
				}
			}
		}

		for (Whim.Need need : whim.needs()) {
			item(need.item(), "needs", warnings);
		}

		Set<String> known = WhimActions.params(whim.action());
		if (known != null) {
			List<String> all = new ArrayList<>(known);
			all.addAll(WhimActions.COMMON_PARAMS);
			for (String key : whim.params().keySet()) {
				if (!all.contains(key)) {
					warnings.add("the " + whim.action() + " action has no param \"" + key + "\"" + didYouMean(key, all) + " (ignored)");
				}
			}
		}

		// The ids in its params (the same param names mean the same kind of id in every action).
		for (Map.Entry<String, String> entry : whim.paramMap("make").entrySet()) {
			item(entry.getKey(), "make", warnings);
			item(entry.getValue(), "make", warnings);
		}

		for (String id : whim.params("items")) {
			item(id, "items", warnings);
		}

		if (whim.params().has("item") && !whim.param("item", "").isEmpty()) {
			item(whim.param("item", ""), "item", warnings);
		}

		if (whim.params().has("block")) {
			item(whim.param("block", ""), "block", warnings);
		}

		for (String id : whim.params("mobs")) {
			mob(id, "mobs", warnings);
		}

		if (whim.params().has("food")) {
			for (Map.Entry<String, String> entry : whim.paramMap("food").entrySet()) {
				mob(entry.getKey(), "food", warnings);
				item(entry.getValue(), "food", warnings);
			}
		}

		if (whim.params().has("blocks")) {
			if (whim.action().equals("build")) {
				int n = 0;
				for (JsonElement element : whim.params().getAsJsonArray("blocks")) {
					n++;
					var cell = element.getAsJsonArray();
					if (cell.size() != 4) {
						throw new IllegalArgumentException("build block " + n + " should be [dx, dy, dz, \"item\"], not " + cell);
					}

					item(cell.get(3).getAsString(), "build block " + n, warnings);
				}
			} else {
				for (String id : whim.params("blocks")) {
					block(id, "blocks", warnings);
				}
			}
		}

		WhimAction action = WhimActions.get(whim.action());
		if (action != null) {
			action.check(whim, warnings);
		}

		return warnings;
	}

	/** An item id, a "#tag" or an "@matcher": a mistake in how it's written throws (it would break the whim), one the game lacks warns. */
	static void item(String id, String where, List<String> warnings) {
		if (id.startsWith("@")) {
			if (!MATCHERS.contains(id)) {
				throw new IllegalArgumentException(where + ": no item matcher called \"" + id + "\"" + didYouMean(id, MATCHERS) + " (there are: "
					+ String.join(", ", MATCHERS) + ")");
			}

			return;
		}

		id(BuiltInRegistries.ITEM, id, "item", where, warnings);
	}

	static void block(String id, String where, List<String> warnings) {
		id(BuiltInRegistries.BLOCK, id, "block", where, warnings);
	}

	static void mob(String id, String where, List<String> warnings) {
		if (id.startsWith("#")) {
			throw new IllegalArgumentException(where + ": mobs are listed by id, not by tag (\"" + id + "\")");
		}

		id(BuiltInRegistries.ENTITY_TYPE, id, "mob", where, warnings);
	}

	/** Tags only have their spelling checked (the game reads their contents later). */
	private static void id(Registry<?> registry, String id, String what, String where, List<String> warnings) {
		boolean tag = id.startsWith("#");
		ResourceLocation location = ResourceLocation.tryParse(tag ? id.substring(1) : id);
		if (location == null) {
			throw new IllegalArgumentException(where + ": \"" + id + "\" isn't written like an id (lowercase, like minecraft:oak_log"
				+ (tag ? " after the #" : "") + ")");
		}

		if (!tag && !registry.containsKey(location)) {
			List<String> ids = new ArrayList<>();
			registry.keySet().forEach(key -> ids.add(key.toString()));
			warnings.add(where + ": no " + what + " \"" + id + "\" in this game" + didYouMean(location.toString(), ids));
		}
	}

	/** " - did you mean "x"?" for the closest of these (two letters off at most), or nothing. */
	static String didYouMean(String word, Collection<String> options) {
		String best = null;
		int bestDistance = 3;
		for (String option : options) {
			if (Math.abs(option.length() - word.length()) >= bestDistance) {
				continue;
			}

			int distance = distance(word, option);
			if (distance < bestDistance && distance > 0) {
				bestDistance = distance;
				best = option;
			}
		}

		return best == null ? "" : " - did you mean \"" + best + "\"?";
	}

	/** Letters to add, drop or change to get from one to the other. */
	private static int distance(String a, String b) {
		int[] previous = new int[b.length() + 1];
		int[] current = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			previous[j] = j;
		}

		for (int i = 1; i <= a.length(); i++) {
			current[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int change = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + change);
			}

			int[] swap = previous;
			previous = current;
			current = swap;
		}

		return previous[b.length()];
	}
}
