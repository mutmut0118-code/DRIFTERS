package com.dinglebob.drifter.gear;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.dinglebob.drifter.Drifter;

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

	/** Everything the gear files said. {@code byWord}: every kind word with its kind, longest word first. */
	public record Tables(List<KindDef> kinds, List<Map.Entry<String, KindDef>> byWord) {
		public static final Tables EMPTY = new Tables(List.of(), List.of());

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
	private static volatile MinecraftServer server;

	private GearData() {
	}

	public static Tables tables() {
		return tables;
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
	}

	private static void load(ResourceManager manager) {
		Map<String, KindDef> loaded = new LinkedHashMap<>();
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
		tables = new Tables(List.copyOf(loaded.values()), List.copyOf(words));
		clearCaches();
		Drifter.LOGGER.info("Loaded {} Drifter gear kinds", loaded.size());
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
