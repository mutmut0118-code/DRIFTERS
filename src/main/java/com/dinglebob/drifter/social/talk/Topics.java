package com.dinglebob.drifter.social.talk;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.google.gson.JsonArray;
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
 * What Drifters talk about with each other (the dynamic chat, phase 1), from the data files {@code
 * data/<namespace>/drifter_talk/} (a datapack can add more). A topic is a short exchange between two of them, {@code a}
 * (who brings it up) and {@code b}: turns, each said by one of them, from lines in their voices (as {@code Lines}),
 * filled in from what each knows ({@code {a.ore}}, {@code {b.death}}..., {@code {a}} and {@code {b}} their names).
 *
 * <p>A topic needs its conditions ({@code "needs"}: {@code "a:found_ore"}, {@code "friends"}, {@code "rain"}, a {@code
 * !} in front for not); a turn can have conditions of its own ({@code "if"}), a chance, or options (the first whose
 * conditions hold). See docs/CHAT_LINES.md.
 */
public final class Topics {
	private Topics() {
	}

	public static final String DIRECTORY = "drifter_talk";

	/**
	 * One line's choice: its conditions, its lines (voice → lines), and the answers some of them want (a question's line →
	 * the lines the other answers it with, in place of the next turn's: "where's your base?" gets "around", not "nice").
	 */
	public record Option(List<String> conditions, Map<String, List<String>> lines, Map<String, List<String>> then) {
	}

	/** A turn: who says it, its chance, its conditions, and its options. */
	public record Turn(String by, double chance, List<String> conditions, List<Option> options) {
	}

	/** A topic: its id, how often it comes up, what it needs, its turns, and what it does ("news", "trust", "rivalry"). */
	public record Topic(String id, double weight, List<String> needs, List<Turn> turns, List<String> effects) {
	}

	private static List<Topic> topics = List.of();

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return Drifter.id("talk");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				load(manager);
			}
		});
	}

	public static List<Topic> all() {
		return topics;
	}

	@Nullable
	public static Topic byId(String id) {
		return topics.stream().filter(topic -> topic.id().equals(id)).findFirst().orElse(null);
	}

	private static void load(ResourceManager manager) {
		List<Topic> loaded = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = file.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				for (JsonElement element : json.getAsJsonArray("topics")) {
					JsonObject topic = element.getAsJsonObject();
					List<Turn> turns = new ArrayList<>();
					for (JsonElement turn : topic.getAsJsonArray("turns")) {
						turns.add(turn(turn.getAsJsonObject()));
					}

					loaded.add(new Topic(topic.get("id").getAsString(), topic.has("weight") ? topic.get("weight").getAsDouble() : 1.0, strings(topic, "needs"),
						turns, strings(topic, "effects")));
				}
			} catch (Exception e) {
				Drifter.LOGGER.error("Couldn't read Drifter talk file {}", file.getKey(), e);
			}
		}

		topics = List.copyOf(loaded);
		int lines = 0;
		for (Topic topic : loaded) {
			for (Turn turn : topic.turns()) {
				for (Option option : turn.options()) {
					lines += option.lines().values().stream().mapToInt(List::size).sum() + option.then().values().stream().mapToInt(List::size).sum();
				}
			}
		}

		Drifter.LOGGER.info("Loaded {} Drifter conversation topics ({} lines)", loaded.size(), lines);
	}

	private static Turn turn(JsonObject json) {
		List<Option> options = new ArrayList<>();
		if (json.has("options")) {
			for (JsonElement option : json.getAsJsonArray("options")) {
				options.add(option(option.getAsJsonObject()));
			}
		} else {
			Map<String, List<String>> then = new HashMap<>();
			options.add(new Option(List.of(), voices(json.get("lines"), then), then));
		}

		return new Turn(json.get("by").getAsString(), json.has("chance") ? json.get("chance").getAsDouble() : 1.0, strings(json, "if"), options);
	}

	private static Option option(JsonObject json) {
		Map<String, List<String>> then = new HashMap<>();
		return new Option(strings(json, "if"), voices(json.get("lines"), then), then);
	}

	/**
	 * Lines as a list (everyone's) or voices (as the chat data); a line can be {@code {"line": "...", "then": [answers]}},
	 * its answers noted in {@code then}.
	 */
	private static Map<String, List<String>> voices(JsonElement element, Map<String, List<String>> then) {
		Map<String, List<String>> voices = new HashMap<>();
		if (element.isJsonArray()) {
			voices.put("any", lines(element.getAsJsonArray(), then));
		} else {
			for (Map.Entry<String, JsonElement> voice : element.getAsJsonObject().entrySet()) {
				voices.put(voice.getKey(), lines(voice.getValue().getAsJsonArray(), then));
			}
		}

		return voices;
	}

	private static List<String> lines(JsonArray array, Map<String, List<String>> then) {
		List<String> lines = new ArrayList<>();
		for (JsonElement line : array) {
			if (line.isJsonObject()) {
				String text = line.getAsJsonObject().get("line").getAsString();
				lines.add(text);
				List<String> answers = new ArrayList<>();
				line.getAsJsonObject().getAsJsonArray("then").forEach(answer -> answers.add(answer.getAsString()));
				then.put(text, answers);
			} else {
				lines.add(line.getAsString());
			}
		}

		return lines;
	}

	private static List<String> strings(JsonObject json, String key) {
		List<String> out = new ArrayList<>();
		if (json.has(key)) {
			JsonArray array = json.getAsJsonArray(key);
			array.forEach(element -> out.add(element.getAsString()));
		}

		return out;
	}

	/** These conditions hold, for a (who brought it up) and b, in this world. */
	public static boolean holds(List<String> conditions, Facts a, Facts b, Set<String> world) {
		for (String condition : conditions) {
			boolean not = condition.startsWith("!");
			String c = not ? condition.substring(1) : condition;
			boolean is;
			if (c.startsWith("a:")) {
				is = a.flags.contains(c.substring(2));
			} else if (c.startsWith("b:")) {
				is = b.flags.contains(c.substring(2));
			} else {
				is = world.contains(c) || a.flags.contains(c);
			}

			if (is == not) {
				return false;
			}
		}

		return true;
	}
}
