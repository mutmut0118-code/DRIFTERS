package com.dinglebob.drifter.whim;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.personality.Mood;
import com.dinglebob.drifter.personality.Trait;

/**
 * One whim (docs/WHIMS.md, "How whims work"): a data entry the whim goal can pick when nothing urgent is going on. What
 * it does is its {@link #action} (one of the action types in {@link WhimActions}) with its own {@link #params}; the rest
 * says who likes it, when it fits, what it needs and how often.
 *
 * <p>Data files: {@code data/<namespace>/drifter_whims/*.json}, each {@code {"whims": [ ... ]}} (datapacks can add more).
 */
public record Whim(String id, String category, String action, JsonObject params, double weight, Map<Archetype, Double> archetypes,
	Map<Trait, Double> traits, Map<Mood, Double> moods, int stage, When when, List<Need> needs, int duration, int cooldown, Gates gates,
	List<String> chat, String thought, Offscreen offscreen) {

	/** Every field a whim may have (anything else is a typo: the load checks say so). */
	public static final java.util.Set<String> FIELDS = java.util.Set.of("id", "category", "action", "params", "weight", "archetypes", "traits",
		"moods", "stage", "when", "needs", "duration", "cooldown", "gates", "chat", "thought", "offscreen", "comment");

	/**
	 * What it does when nobody is about (milestone 20, docs/OFFSCREEN_WHIMS.md; the user's pick: A + B + C + E). Each action
	 * has its own default ({@link WhimActions}); a whim can say otherwise.
	 */
	public enum Offscreen {
		/** Only for show: with nobody near, settled at once (the thought, the mood, the cooldown); while away, remembered. */
		SHOW,
		/** Leaves something behind: done for real when loaded; while away, remembered, and now and then left at home. */
		TRACE,
		/** Uses up or changes something (food, a gift, a craft): done for real when loaded; while away, not done. */
		REAL,
		/** Only with someone about to see it: not done with nobody near, nor while away. */
		SKIP
	}

	/** When it fits: the time of day or the weather (it looks at the sky, which it can see). */
	public enum When {
		ANY, DAY, NIGHT, SUNSET, SUNRISE, RAIN, THUNDER, CLEAR
	}

	/** Something it has to carry (an item id, or a tag as "#namespace:tag"), and how many. */
	public record Need(String item, int count) {
	}

	/**
	 * docs/WHIMS.md gates: [G] griefing or harm to others (needs hostility and the griefing setting), [M] multiplayer
	 * (only with a player or another Drifter about), [R] risky (cautious ones rarely), [L] later (left out for now).
	 */
	public record Gates(boolean griefing, boolean multiplayer, boolean risky, boolean later) {
	}

	/** Reads one whim; a mistake that leaves it unusable throws, with a message that says what it is. */
	public static Whim parse(JsonObject json) {
		if (!json.has("id") || !json.has("action")) {
			throw new IllegalArgumentException("every whim needs an \"id\" and an \"action\"");
		}

		String id = json.get("id").getAsString();
		Map<Archetype, Double> archetypes = new EnumMap<>(Archetype.class);
		if (json.has("archetypes")) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("archetypes").entrySet()) {
				archetypes.put(named(Archetype.class, entry.getKey(), "archetype"), entry.getValue().getAsDouble());
			}
		}

		Map<Trait, Double> traits = new EnumMap<>(Trait.class);
		if (json.has("traits")) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("traits").entrySet()) {
				traits.put(named(Trait.class, entry.getKey(), "trait"), entry.getValue().getAsDouble());
			}
		}

		Map<Mood, Double> moods = new EnumMap<>(Mood.class);
		if (json.has("moods")) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("moods").entrySet()) {
				moods.put(named(Mood.class, entry.getKey(), "mood"), entry.getValue().getAsDouble());
			}
		}

		List<Need> needs = new ArrayList<>();
		if (json.has("needs")) {
			for (JsonElement element : json.getAsJsonArray("needs")) {
				JsonObject need = element.getAsJsonObject();
				needs.add(new Need(need.get("item").getAsString(), need.has("count") ? need.get("count").getAsInt() : 1));
			}
		}

		String gates = json.has("gates") ? json.get("gates").getAsString().toUpperCase(Locale.ROOT) : "";
		List<String> chat = new ArrayList<>();
		if (json.has("chat")) {
			json.getAsJsonArray("chat").forEach(line -> chat.add(line.getAsString()));
		}

		String action = json.get("action").getAsString();
		return new Whim(id, string(json, "category", "misc"), action,
			json.has("params") ? json.getAsJsonObject("params") : new JsonObject(), json.has("weight") ? json.get("weight").getAsDouble() : 1.0,
			archetypes, traits, moods, json.has("stage") ? json.get("stage").getAsInt() : 0,
			named(When.class, string(json, "when", "any"), "\"when\""), List.copyOf(needs),
			json.has("duration") ? json.get("duration").getAsInt() : 20 * 20, json.has("cooldown") ? json.get("cooldown").getAsInt() : 24000,
			new Gates(gates.contains("G"), gates.contains("M"), gates.contains("R"), gates.contains("L")), List.copyOf(chat),
			string(json, "thought", "felt like " + id.replace('_', ' ')),
			json.has("offscreen") ? named(Offscreen.class, json.get("offscreen").getAsString(), "\"offscreen\"") : WhimActions.offscreen(action));
	}

	/** One of an enum's names, any case; a wrong one throws, saying which it is close to and what there is. */
	static <E extends Enum<E>> E named(Class<E> type, String name, String what) {
		try {
			return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			List<String> names = new ArrayList<>();
			for (E value : type.getEnumConstants()) {
				names.add(value.name().toLowerCase(Locale.ROOT));
			}

			throw new IllegalArgumentException("no " + what + " called \"" + name + "\"" + WhimCheck.didYouMean(name.toLowerCase(Locale.ROOT), names)
				+ " (there are: " + String.join(", ", names) + ")");
		}
	}

	private static String string(JsonObject json, String key, String fallback) {
		return json.has(key) ? json.get(key).getAsString() : fallback;
	}

	/** A text parameter, or the fallback. */
	public String param(String key, String fallback) {
		return this.params.has(key) ? this.params.get(key).getAsString() : fallback;
	}

	public int param(String key, int fallback) {
		return this.params.has(key) ? this.params.get(key).getAsInt() : fallback;
	}

	public boolean param(String key, boolean fallback) {
		return this.params.has(key) ? this.params.get(key).getAsBoolean() : fallback;
	}

	/** A list parameter (a single string counts as a list of one). */
	public List<String> params(String key) {
		List<String> out = new ArrayList<>();
		if (this.params.has(key)) {
			JsonElement value = this.params.get(key);
			if (value.isJsonArray()) {
				value.getAsJsonArray().forEach(e -> out.add(e.getAsString()));
			} else {
				out.add(value.getAsString());
			}
		}

		return out;
	}

	/** An object parameter of string to string (feed: mob to food, and the like). */
	public Map<String, String> paramMap(String key) {
		Map<String, String> out = new HashMap<>();
		if (this.params.has(key)) {
			for (Map.Entry<String, JsonElement> entry : this.params.getAsJsonObject(key).entrySet()) {
				out.put(entry.getKey(), entry.getValue().getAsString());
			}
		}

		return out;
	}
}
