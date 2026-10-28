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
 *
 * <p>Topics link into a web, so a conversation can go on from one to the next ({@link Conversations}): a topic's {@code
 * "tags"} say what it's about, its {@code "leads"} what it can lead on to (topics with those tags), its {@code "next"}
 * the topics it leads to most of all; {@code "cost"} how much of their interest it uses up, {@code "about"} a word for it
 * ("anyway, about the iron"), {@code "follow_up": true} that it only comes up following another, {@code "opener": true} that
 * it only starts a conversation (a greeting). An option can {@code
 * "mark"} the talk ({@code "disagree"}: later topics' conditions read {@code t:disagree}) and add {@code "leads"} of its
 * own (an argument leads on to more arguing). A file's {@code "links"} give these for topics written elsewhere (topic id
 * to the same fields), so the web can grow without touching the topics' own files.
 */
public final class Topics {
	private Topics() {
	}

	public static final String DIRECTORY = "drifter_talk";

	/**
	 * One line's choice: its conditions, its lines (voice → lines), and the answers some of them want (a question's line →
	 * the lines the other answers it with, in place of the next turn's: "where's your base?" gets "around", not "nice").
	 */
	public record Option(List<String> conditions, Map<String, List<String>> lines, Map<String, List<String>> then, List<String> marks, List<String> leads) {
	}

	/** A turn: who says it, its chance, its conditions, and its options. */
	public record Turn(String by, double chance, List<String> conditions, List<Option> options) {
	}

	/**
	 * A topic: its id, how often it comes up, what it needs, its turns, what it does ("news", "trust", "rivalry"), what a
	 * Drifter standing by might add at the end ({@code chime}, chat phase 3), and (a topic it brings up with a player) the
	 * question it ends on, if any.
	 */
	public record Topic(String id, double weight, List<String> needs, List<Turn> turns, List<String> effects, Map<String, List<String>> chime,
		@Nullable Question question, Web web) {
	}

	/**
	 * Where a topic sits in the web: what it's about (tags), what it leads on to (tags; topic ids above all), the interest
	 * it uses up, a word for it, whether it only comes up following another, and whether only to start a talk.
	 */
	public record Web(List<String> tags, List<String> leads, List<String> next, double cost, String about, boolean followUp, boolean opener) {
	}

	/**
	 * A question to a player (chat phase 3: "wanna team up?"): what it says to a yes and what it then does ({@code do}:
	 * "team", "follow", "come", or nothing), and to a no.
	 */
	public record Question(Map<String, List<String>> yes, String does, Map<String, List<String>> no) {
	}

	private static List<Topic> topics = List.of();
	/** Topics it brings up with a player (a's turns only; the player is b). */
	private static List<Topic> playerTopics = List.of();
	/** The Drifter topics by id, and by tag (for the web: what a topic leads on to, at thousands of topics). */
	private static Map<String, Topic> byId = Map.of();
	private static Map<String, List<Topic>> byTag = Map.of();
	/** Topic words that aren't what it's about (a topic with no tags is about the words of its id, less these). */
	private static final Set<String> NOT_TAGS = Set.of("a", "b", "the", "and", "of", "to", "with", "vs", "talk", "chat", "story", "show", "lately");

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

	public static List<Topic> forPlayers() {
		return playerTopics;
	}

	@Nullable
	public static Topic byId(String id) {
		return byId.get(id);
	}

	/** The topics about this (tagged with it). */
	public static List<Topic> tagged(String tag) {
		return byTag.getOrDefault(tag, List.of());
	}

	@Nullable
	public static Topic playerTopic(String id) {
		return playerTopics.stream().filter(topic -> topic.id().equals(id)).findFirst().orElse(null);
	}

	private static void load(ResourceManager manager) {
		List<Topic> loaded = new ArrayList<>();
		List<Topic> withPlayers = new ArrayList<>();
		Map<String, JsonObject> links = new HashMap<>();
		for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = file.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				if (json.has("topics")) {
					for (JsonElement element : json.getAsJsonArray("topics")) {
						loaded.add(topic(element.getAsJsonObject()));
					}
				}

				if (json.has("player_topics")) {
					for (JsonElement element : json.getAsJsonArray("player_topics")) {
						withPlayers.add(topic(element.getAsJsonObject()));
					}
				}

				if (json.has("links")) {
					for (Map.Entry<String, JsonElement> link : json.getAsJsonObject("links").entrySet()) {
						JsonObject into = links.computeIfAbsent(link.getKey(), id -> new JsonObject());
						link.getValue().getAsJsonObject().entrySet().forEach(field -> into.add(field.getKey(), field.getValue()));
					}
				}
			} catch (Exception e) {
				Drifter.LOGGER.error("Couldn't read Drifter talk file {}", file.getKey(), e);
			}
		}

		// (Links from other files: a topic's place in the web, written apart from it.)
		loaded.replaceAll(topic -> links.containsKey(topic.id()) ? withWeb(topic, links.get(topic.id())) : topic);
		Map<String, Topic> ids = new HashMap<>();
		Map<String, List<Topic>> tags = new HashMap<>();
		for (Topic topic : loaded) {
			ids.put(topic.id(), topic);
			for (String tag : topic.web().tags()) {
				tags.computeIfAbsent(tag, t -> new ArrayList<>()).add(topic);
			}
		}

		topics = List.copyOf(loaded);
		playerTopics = List.copyOf(withPlayers);
		byId = ids;
		byTag = tags;
		loaded.addAll(withPlayers);
		int lines = 0;
		for (Topic topic : loaded) {
			for (Turn turn : topic.turns()) {
				for (Option option : turn.options()) {
					lines += option.lines().values().stream().mapToInt(List::size).sum() + option.then().values().stream().mapToInt(List::size).sum();
				}
			}
		}

		Drifter.LOGGER.info("Loaded {} Drifter conversation topics, {} to bring up with players ({} lines; {} tags in the web)", topics.size(), playerTopics.size(),
			lines, tags.size());
	}

	private static Topic topic(JsonObject topic) {
		List<Turn> turns = new ArrayList<>();
		for (JsonElement turn : topic.getAsJsonArray("turns")) {
			turns.add(turn(turn.getAsJsonObject()));
		}

		Map<String, List<String>> chime = topic.has("chime") ? voices(topic.get("chime"), new HashMap<>()) : Map.of();
		Question question = null;
		if (topic.has("question")) {
			JsonObject q = topic.getAsJsonObject("question");
			JsonObject yes = q.getAsJsonObject("yes");
			question = new Question(voices(yes.get("lines"), new HashMap<>()), yes.has("do") ? yes.get("do").getAsString() : "",
				voices(q.getAsJsonObject("no").get("lines"), new HashMap<>()));
		}

		String id = topic.get("id").getAsString();
		return new Topic(id, topic.has("weight") ? topic.get("weight").getAsDouble() : 1.0, strings(topic, "needs"), turns, strings(topic, "effects"), chime,
			question, web(id, topic, null));
	}

	/** Its place in the web, from its own fields (over what it had). */
	private static Web web(String id, JsonObject json, @Nullable Web had) {
		List<String> tags = json.has("tags") ? strings(json, "tags") : had != null ? had.tags() : defaultTags(id);
		List<String> leads = json.has("leads") ? strings(json, "leads") : had != null ? had.leads() : tags;
		List<String> next = json.has("next") ? strings(json, "next") : had != null ? had.next() : List.of();
		double cost = json.has("cost") ? json.get("cost").getAsDouble() : had != null ? had.cost() : 1.0;
		String about = json.has("about") ? json.get("about").getAsString() : had != null ? had.about() : tags.isEmpty() ? "that" : tags.get(0).replace('_', ' ');
		boolean followUp = json.has("follow_up") ? json.get("follow_up").getAsBoolean() : had != null && had.followUp();
		boolean opener = json.has("opener") ? json.get("opener").getAsBoolean() : had != null && had.opener();
		return new Web(List.copyOf(tags), List.copyOf(leads), List.copyOf(next), cost, about, followUp, opener);
	}

	private static Topic withWeb(Topic topic, JsonObject link) {
		return new Topic(topic.id(), topic.weight(), topic.needs(), topic.turns(), topic.effects(), topic.chime(), topic.question(), web(topic.id(), link, topic.web()));
	}

	/** A topic with no tags is about the words of its id ("found_ore": found, ore). */
	private static List<String> defaultTags(String id) {
		List<String> tags = new ArrayList<>();
		for (String word : id.split("_")) {
			if (!word.isEmpty() && !NOT_TAGS.contains(word)) {
				tags.add(word);
			}
		}

		return tags;
	}

	private static Turn turn(JsonObject json) {
		List<Option> options = new ArrayList<>();
		if (json.has("options")) {
			for (JsonElement option : json.getAsJsonArray("options")) {
				options.add(option(option.getAsJsonObject()));
			}
		} else {
			Map<String, List<String>> then = new HashMap<>();
			options.add(new Option(List.of(), voices(json.get("lines"), then), then, strings(json, "mark"), strings(json, "leads")));
		}

		return new Turn(json.get("by").getAsString(), json.has("chance") ? json.get("chance").getAsDouble() : 1.0, strings(json, "if"), options);
	}

	private static Option option(JsonObject json) {
		Map<String, List<String>> then = new HashMap<>();
		return new Option(strings(json, "if"), voices(json.get("lines"), then), then, strings(json, "mark"), strings(json, "leads"));
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
