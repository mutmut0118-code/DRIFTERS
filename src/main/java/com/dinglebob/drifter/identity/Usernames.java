package com.dinglebob.drifter.identity;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.registry.ModEntities;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;

/**
 * Player-like usernames (DESIGN.md section 4, "Identity"): 3 to 16 characters of letters, digits and underscores, in
 * the patterns real players use (a word plus digits, two words joined, words with underscores, a first name and a
 * year, the odd styled one like "xXFrostXx"), mostly lowercase. Never a name already in use by a player online or a
 * loaded Drifter, never a listed public figure or creator, and filtered for offensive words. Word lists come from the
 * data files {@code data/<namespace>/drifter_names/} (a datapack can add more).
 * TODO(milestone 12): the world-level identity registry (section 4), so departed Drifters' names stay reserved.
 */
public final class Usernames {
	public static final String DIRECTORY = "drifter_names";
	public static final int MIN_LENGTH = 3;
	public static final int MAX_LENGTH = 16;
	private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_]{" + MIN_LENGTH + "," + MAX_LENGTH + "}");
	private static final int MAX_TRIES = 200;

	private record Lists(List<String> words, List<String> adjectives, List<String> firstNames, List<String> prefixes, List<String> suffixes,
		Set<String> banned, List<String> bannedParts) {
	}

	private static final Lists FALLBACK = new Lists(List.of("drift", "stone", "pine", "fox", "moss", "ember"), List.of("lazy", "swift", "quiet"),
		List.of("sam", "alex", "max"), List.of("xX", "itz"), List.of("Xx", "mc"), Set.of(), List.of());
	private static volatile Lists lists = FALLBACK;

	private Usernames() {
	}

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return Drifter.id("names");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				load(manager);
			}
		});
	}

	private static void load(ResourceManager manager) {
		List<String> words = new ArrayList<>();
		List<String> adjectives = new ArrayList<>();
		List<String> firstNames = new ArrayList<>();
		List<String> prefixes = new ArrayList<>();
		List<String> suffixes = new ArrayList<>();
		Set<String> banned = new HashSet<>();
		List<String> bannedParts = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = file.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				addAll(json, "words", words);
				addAll(json, "adjectives", adjectives);
				addAll(json, "first_names", firstNames);
				addAll(json, "styled_prefixes", prefixes);
				addAll(json, "styled_suffixes", suffixes);
				List<String> bannedNames = new ArrayList<>();
				addAll(json, "banned", bannedNames);
				bannedNames.forEach(name -> banned.add(name.toLowerCase(Locale.ROOT)));
				addAll(json, "banned_parts", bannedParts);
			} catch (Exception e) {
				Drifter.LOGGER.error("Couldn't read Drifter names file {}", file.getKey(), e);
			}
		}

		if (words.isEmpty() || adjectives.isEmpty() || firstNames.isEmpty()) {
			Drifter.LOGGER.warn("Drifter name lists are empty; using a small built-in list");
			lists = FALLBACK;
			return;
		}

		bannedParts.replaceAll(part -> part.toLowerCase(Locale.ROOT));
		lists = new Lists(List.copyOf(words), List.copyOf(adjectives), List.copyOf(firstNames), prefixes.isEmpty() ? FALLBACK.prefixes() : List.copyOf(prefixes),
			suffixes.isEmpty() ? FALLBACK.suffixes() : List.copyOf(suffixes), Set.copyOf(banned), List.copyOf(bannedParts));
		Drifter.LOGGER.info("Loaded {} words for Drifter usernames", words.size() + adjectives.size() + firstNames.size());
	}

	private static void addAll(JsonObject json, String key, List<String> into) {
		if (json.has(key)) {
			JsonArray array = json.getAsJsonArray(key);
			array.forEach(element -> into.add(element.getAsString()));
		}
	}

	/** A fresh username not used by anyone the server knows of right now. */
	public static String generate(RandomSource random, MinecraftServer server) {
		Set<String> taken = taken(server);
		for (int i = 0; i < MAX_TRIES; i++) {
			String name = candidate(random, lists);
			if (allowed(name) && !taken.contains(name.toLowerCase(Locale.ROOT))) {
				return name;
			}
		}

		// Practically never: a plain word with enough digits to be unique.
		return pick(random, lists.words()) + (1000 + random.nextInt(9000));
	}

	/** Valid characters and length, not a banned name, nothing offensive inside. */
	public static boolean allowed(String name) {
		if (!VALID.matcher(name).matches()) {
			return false;
		}

		String lower = name.toLowerCase(Locale.ROOT);
		String squashed = lower.replace("_", "");
		if (lists.banned().contains(lower) || lists.banned().contains(squashed)) {
			return false;
		}

		for (String part : lists.bannedParts()) {
			if (lower.contains(part) || squashed.contains(part)) {
				return false;
			}
		}

		return true;
	}

	/** Names in use: players online and every loaded Drifter. */
	private static Set<String> taken(MinecraftServer server) {
		Set<String> taken = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			taken.add(player.getGameProfile().getName().toLowerCase(Locale.ROOT));
		}

		for (ServerLevel level : server.getAllLevels()) {
			for (DrifterEntity drifter : level.getEntities(ModEntities.DRIFTER, DrifterEntity::hasCustomName)) {
				taken.add(drifter.getName().getString().toLowerCase(Locale.ROOT));
			}
		}

		return taken;
	}

	private static String candidate(RandomSource random, Lists l) {
		int roll = random.nextInt(100);
		String name;
		if (roll < 28) {
			// pinewick42, moss_77, Ember2009
			name = pick(random, l.words()) + (random.nextInt(3) == 0 ? pick(random, l.words()) : "") + (random.nextInt(4) == 0 ? "_" : "") + digits(random);
		} else if (roll < 55) {
			// frostotter, FrostOtter, sleepyfox
			String first = random.nextBoolean() ? pick(random, l.adjectives()) : pick(random, l.words());
			name = random.nextInt(3) == 0 ? capital(first) + capital(pick(random, l.words())) : first + pick(random, l.words());
		} else if (roll < 70) {
			// sleepy_fox, lost_cobble_3
			name = pick(random, l.adjectives()) + "_" + pick(random, l.words()) + (random.nextInt(4) == 0 ? "_" + random.nextInt(10) : "");
		} else if (roll < 88) {
			// leo2011, mia_09, Kai_Otter
			String first = pick(random, l.firstNames());
			name = switch (random.nextInt(4)) {
				case 0 -> first + (2000 + random.nextInt(16));
				case 1 -> first + "_" + String.format(Locale.ROOT, "%02d", random.nextInt(100));
				case 2 -> capital(first) + "_" + capital(pick(random, l.words()));
				default -> first + pick(random, l.words());
			};
		} else {
			// The occasional styled one: xXFrostXx, itzmossy, NoodleMC, TheOtter.
			String word = random.nextBoolean() ? pick(random, l.words()) : pick(random, l.adjectives()) + pick(random, l.words());
			name = switch (random.nextInt(4)) {
				case 0 -> "xX" + capital(word) + "Xx";
				case 1 -> pick(random, l.prefixes()) + capital(word);
				case 2 -> capital(word) + pick(random, l.suffixes()).toUpperCase(Locale.ROOT);
				default -> pick(random, l.prefixes()).toLowerCase(Locale.ROOT) + word + (random.nextBoolean() ? "" : digits(random));
			};
		}

		// Most players type their name in lowercase; a few use capitals all the way.
		if (random.nextInt(25) == 0) {
			name = name.toUpperCase(Locale.ROOT);
		}

		return name;
	}

	private static String digits(RandomSource random) {
		return switch (random.nextInt(4)) {
			case 0 -> Integer.toString(random.nextInt(10));
			case 1, 2 -> Integer.toString(10 + random.nextInt(90));
			default -> Integer.toString(100 + random.nextInt(900));
		};
	}

	private static String pick(RandomSource random, List<String> list) {
		return list.get(random.nextInt(list.size()));
	}

	private static String capital(String word) {
		return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}
}
