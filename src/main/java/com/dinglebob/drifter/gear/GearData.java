package com.dinglebob.drifter.gear;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.personality.Trait;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * The gear tables (docs/superpowers/specs/2026-11-09-weapons-armor-compat-design.md): one reload listener for
 * {@code data/<namespace>/drifter_gear/*.json}. A file may say {@code "requires": "<mod id>"} and is skipped when that
 * mod is absent (as {@code drifter_weapons} does); a later file with the same kind id replaces the earlier one. Today the
 * tables hold the weapon kinds; clothing styles and power words come in later tasks.
 */
public final class GearData {
	public static final String DIRECTORY = "drifter_gear";

	/** One kind of weapon: how to recognise it (words in the id or name, item tags, Better Combat categories). */
	public record KindDef(String id, GearKinds.Use use, List<String> words, List<TagKey<Item>> tags, List<String> betterCombat) {
	}

	/** One taste style: how each trait (pull -1..1 around 0.5) and archetype (a factor) leans a Drifter to it. */
	public record StyleDef(Map<Trait, Double> traits, Map<Archetype, Double> archetypes) {
	}

	/** The style bonuses, each added to a piece's bonus when it fits the style. */
	public record Bonus(double flashyRarityStep, double flashyEnchanted, double flashyModded, double flashyCost, double loyalKind, double loyalSet,
		double followerShare, double favoriteFamily) {
	}

	/** Taste (spec goal 4): how strong it is, the noise between Drifters, the good-enough rule, the styles and their bonuses. */
	public record Taste(double strength, double noise, double goodEnough, Map<GearTaste.Style, StyleDef> styles, Bonus bonus) {
		public static final Taste DEFAULT = new Taste(0.25, 0.05, 0.75, Map.of(), new Bonus(0.25, 0.2, 0.2, 0.2, 0.8, 0.6, 0.8, 0.4));
	}

	/** Everything the gear files said. {@code byWord}: every kind word with its kind, longest word first. */
	public record Tables(List<KindDef> kinds, List<Map.Entry<String, KindDef>> byWord, Taste taste) {
		public static final Tables EMPTY = new Tables(List.of(), List.of(), Taste.DEFAULT);

		@Nullable
		public KindDef kind(String id) {
			for (KindDef kind : this.kinds) {
				if (kind.id().equals(id)) {
					return kind;
				}
			}

			return null;
		}
	}

	private static volatile Tables tables = Tables.EMPTY;
	/** Bumped on every reload: anything worked out from the tables (a Drifter's style) is stale when this moves. */
	private static volatile int version;
	private static volatile MinecraftServer server;

	private GearData() {
	}

	public static Tables tables() {
		return tables;
	}

	public static int version() {
		return version;
	}

	/** The overworld, for recipe lookups where there is no level at hand (null before a server runs). */
	@Nullable
	public static ServerLevel level() {
		MinecraftServer current = server;
		return current == null ? null : current.overworld();
	}

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return Drifter.id("gear");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				load(manager);
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(started -> {
			server = started;
			clearCaches();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
			server = null;
			clearCaches();
		});
		// Recipes may reload after our own listener: drop what was worked out from the old ones.
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((started, manager, success) -> clearCaches());
	}

	private static void clearCaches() {
		GearKinds.clear();
		GearValue.clear();
		RecipeIndex.clear();
		GearTaste.clear();
	}

	private static void load(ResourceManager manager) {
		Map<String, KindDef> loaded = new LinkedHashMap<>();
		Taste taste = Taste.DEFAULT;
		for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = file.getValue().openAsReader()) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				if (root.has("requires") && !FabricLoader.getInstance().isModLoaded(root.get("requires").getAsString())) {
					continue;
				}

				if (root.has("kinds")) {
					for (JsonElement element : root.getAsJsonArray("kinds")) {
						KindDef kind = kind(element.getAsJsonObject());
						if (kind != null) {
							loaded.put(kind.id(), kind);
						}
					}
				}

				if (root.has("styles")) {
					taste = taste(root, taste);
				}
			} catch (Exception e) {
				Drifter.LOGGER.error("Couldn't read Drifter gear file {}", file.getKey(), e);
			}
		}

		List<Map.Entry<String, KindDef>> words = new ArrayList<>();
		for (KindDef kind : loaded.values()) {
			for (String word : kind.words()) {
				words.add(Map.entry(word, kind));
			}
		}

		words.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
		tables = new Tables(List.copyOf(loaded.values()), List.copyOf(words), taste);
		version++;
		clearCaches();
		Drifter.LOGGER.info("Loaded {} Drifter gear kinds and {} taste styles", loaded.size(), taste.styles().size());
	}

	private static Taste taste(JsonObject root, Taste before) {
		Taste base = before;
		Map<GearTaste.Style, StyleDef> styles = new EnumMap<>(GearTaste.Style.class);
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("styles").entrySet()) {
			GearTaste.Style style;
			try {
				style = GearTaste.Style.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				Drifter.LOGGER.warn("Drifter gear style {} is unknown", entry.getKey());
				continue;
			}

			JsonObject def = entry.getValue().getAsJsonObject();
			Map<Trait, Double> traits = new EnumMap<>(Trait.class);
			if (def.has("traits")) {
				for (Map.Entry<String, JsonElement> trait : def.getAsJsonObject("traits").entrySet()) {
					try {
						traits.put(Trait.valueOf(trait.getKey().toUpperCase(Locale.ROOT)), trait.getValue().getAsDouble());
					} catch (IllegalArgumentException e) {
						Drifter.LOGGER.warn("Drifter gear style {} has an unknown trait {}", entry.getKey(), trait.getKey());
					}
				}
			}

			Map<Archetype, Double> archetypes = new EnumMap<>(Archetype.class);
			if (def.has("archetypes")) {
				for (Map.Entry<String, JsonElement> archetype : def.getAsJsonObject("archetypes").entrySet()) {
					try {
						archetypes.put(Archetype.valueOf(archetype.getKey().toUpperCase(Locale.ROOT)), archetype.getValue().getAsDouble());
					} catch (IllegalArgumentException e) {
						Drifter.LOGGER.warn("Drifter gear style {} has an unknown archetype {}", entry.getKey(), archetype.getKey());
					}
				}
			}

			styles.put(style, new StyleDef(Map.copyOf(traits), Map.copyOf(archetypes)));
		}

		Bonus bonus = base.bonus();
		if (root.has("bonus")) {
			JsonObject b = root.getAsJsonObject("bonus");
			bonus = new Bonus(num(b, "flashy_rarity_step", bonus.flashyRarityStep()), num(b, "flashy_enchanted", bonus.flashyEnchanted()),
				num(b, "flashy_modded", bonus.flashyModded()), num(b, "flashy_cost", bonus.flashyCost()), num(b, "loyal_kind", bonus.loyalKind()),
				num(b, "loyal_set", bonus.loyalSet()), num(b, "follower_share", bonus.followerShare()), num(b, "favorite_family", bonus.favoriteFamily()));
		}

		return new Taste(num(root, "taste_strength", base.strength()), num(root, "noise", base.noise()), num(root, "good_enough", base.goodEnough()),
			Map.copyOf(styles), bonus);
	}

	private static double num(JsonObject object, String key, double fallback) {
		return object.has(key) ? object.get(key).getAsDouble() : fallback;
	}

	@Nullable
	private static KindDef kind(JsonObject entry) {
		String id = entry.get("id").getAsString();
		GearKinds.Use use;
		try {
			use = GearKinds.Use.valueOf(entry.get("use").getAsString().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			Drifter.LOGGER.warn("Drifter gear kind {} has an unknown use {}", id, entry.get("use"));
			return null;
		}

		return new KindDef(id, use, strings(entry, "words"), tags(entry), strings(entry, "better_combat"));
	}

	private static List<String> strings(JsonObject entry, String key) {
		List<String> list = new ArrayList<>();
		if (entry.has(key)) {
			for (JsonElement element : entry.getAsJsonArray(key)) {
				list.add(element.getAsString().toLowerCase(Locale.ROOT));
			}
		}

		return List.copyOf(list);
	}

	private static List<TagKey<Item>> tags(JsonObject entry) {
		List<TagKey<Item>> list = new ArrayList<>();
		if (entry.has("tags")) {
			for (JsonElement element : entry.getAsJsonArray("tags")) {
				ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
				if (id != null) {
					list.add(TagKey.create(Registries.ITEM, id));
				}
			}
		}

		return List.copyOf(list);
	}
}
