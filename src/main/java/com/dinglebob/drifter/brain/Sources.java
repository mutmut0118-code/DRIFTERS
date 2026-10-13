package com.dinglebob.drifter.brain;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.dinglebob.drifter.Drifter;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The curated "where things come from" table for the planner (DESIGN.md section 8, "Acquisition rules"): which
 * blocks and mobs give which items. Crafting and smelting are not here; they come from the game's own recipe manager.
 * Loaded from the data files {@code data/<namespace>/drifter_sources/*.json}, so datapacks can extend it.
 *
 * <p>TODO(milestone 7): the Knowledge trait limits which entries a Drifter knows.
 */
public final class Sources {
	public static final String DIRECTORY = "drifter_sources";

	/** One way to get an item: break a block, or kill a mob. */
	public record Source(String itemId, List<String> blockIds, boolean dropsSelf, @Nullable ResourceLocation tool, @Nullable ResourceLocation mob) {
		public boolean givesItem(Item item) {
			return matchesItem(this.itemId, item);
		}

		public boolean isBlock() {
			return !this.blockIds.isEmpty();
		}

		/** Breaking this block gives the item (for "drops self", only the block whose item it is). */
		public boolean blockGives(BlockState state, Item item) {
			if (!this.isBlock() || !this.blockIds.stream().anyMatch(id -> Sources.matchesBlock(id, state))) {
				return false;
			}

			return !this.dropsSelf || state.getBlock().asItem() == item;
		}

		public boolean matchesBlock(BlockState state) {
			return this.blockIds.stream().anyMatch(id -> Sources.matchesBlock(id, state));
		}

		@Nullable
		public Item toolItem() {
			return this.tool == null ? null : BuiltInRegistries.ITEM.get(this.tool);
		}

		@Nullable
		public EntityType<?> mobType() {
			return this.mob == null ? null : BuiltInRegistries.ENTITY_TYPE.get(this.mob);
		}
	}

	private static volatile List<Source> sources = List.of();

	private Sources() {
	}

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return Drifter.id("sources");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				load(manager);
			}
		});
	}

	public static List<Source> all() {
		return sources;
	}

	/** Every source that gives this item. */
	public static List<Source> forItem(Item item) {
		return sources.stream().filter(source -> source.givesItem(item)).toList();
	}

	private static void load(ResourceManager manager) {
		List<Source> loaded = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = file.getValue().openAsReader()) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				for (JsonElement element : root.getAsJsonArray("sources")) {
					JsonObject entry = element.getAsJsonObject();
					List<String> blocks = new ArrayList<>();
					if (entry.has("block")) {
						JsonElement block = entry.get("block");
						if (block instanceof JsonArray array) {
							array.forEach(id -> blocks.add(id.getAsString()));
						} else {
							blocks.add(block.getAsString());
						}
					}

					loaded.add(new Source(entry.get("item").getAsString(), List.copyOf(blocks),
						entry.has("drops") && entry.get("drops").getAsString().equals("self"),
						entry.has("tool") ? ResourceLocation.parse(entry.get("tool").getAsString()) : null,
						entry.has("mob") ? ResourceLocation.parse(entry.get("mob").getAsString()) : null));
				}
			} catch (Exception e) {
				Drifter.LOGGER.error("Couldn't read Drifter sources file {}", file.getKey(), e);
			}
		}

		sources = List.copyOf(loaded);
		Drifter.LOGGER.info("Loaded {} Drifter item sources", loaded.size());
	}

	/** An item id or {@code #tag} against an item. */
	public static boolean matchesItem(String id, Item item) {
		if (id.startsWith("#")) {
			return new ItemStack(item).is(TagKey.create(Registries.ITEM, ResourceLocation.parse(id.substring(1))));
		}

		return BuiltInRegistries.ITEM.getKey(item).equals(ResourceLocation.parse(id));
	}

	/** A block id or {@code #tag} against a block state. */
	public static boolean matchesBlock(String id, BlockState state) {
		if (id.startsWith("#")) {
			return state.is(TagKey.create(Registries.BLOCK, ResourceLocation.parse(id.substring(1))));
		}

		Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
		return state.is(block);
	}
}
