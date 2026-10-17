package com.dinglebob.drifter.building.plan.gen;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * A house style for the plan generator (milestone 15; user, 2026-10-17: "lets make whole milestone dedicated to generating
 * houseplans, im talking hundreds"): the materials and proportions a family of plans shares. Read from
 * {@code data/<namespace>/drifter_house_styles/*.json}, so new styles are a data file. Oak and spruce stand for the
 * Drifter's main and second wood and cobblestone for its stone (HousePalette swaps them for what it has), so a style
 * writes them as such. Missing fields take the defaults below.
 */
public record HouseStyle(String id, double weight, int[] width, int[] depth, double twoFloors, int[] wallHeight, Block floor, @Nullable Block base,
	@Nullable Block frame, int postsEvery, boolean beam, Block infill, Block upperInfill, Block window, int windowSpacing, double tallWindows,
	@Nullable Block sill, Map<String, Double> roofs, Block stair, Block slab, Block gableFill, @Nullable Block parapet, Block door, Block upperFloor) {

	public static HouseStyle parse(String id, JsonObject json) {
		return new HouseStyle(id, num(json, "weight", 1.0), range(json, "width", 5, 9), range(json, "depth", 5, 8), num(json, "two_floors", 0.3),
			range(json, "wall_height", 3, 4), block(json, "floor", Blocks.OAK_PLANKS), blockOrNull(json, "base"), blockOrNull(json, "frame"),
			(int)num(json, "posts_every", 0), json.has("beam") && json.get("beam").getAsBoolean(), block(json, "infill", Blocks.OAK_PLANKS),
			block(json, "upper_infill", block(json, "infill", Blocks.OAK_PLANKS)), block(json, "window", Blocks.GLASS_PANE),
			(int)num(json, "window_spacing", 2), num(json, "tall_windows", 0.3), blockOrNull(json, "sill"), roofs(json),
			block(json, "stair", Blocks.SPRUCE_STAIRS), block(json, "slab", Blocks.SPRUCE_SLAB), block(json, "gable_fill", Blocks.OAK_PLANKS),
			blockOrNull(json, "parapet"), block(json, "door", Blocks.OAK_DOOR), block(json, "upper_floor", Blocks.OAK_PLANKS));
	}

	private static double num(JsonObject json, String key, double fallback) {
		return json.has(key) ? json.get(key).getAsDouble() : fallback;
	}

	private static int[] range(JsonObject json, String key, int low, int high) {
		if (!json.has(key)) {
			return new int[] {low, high};
		}

		JsonElement value = json.get(key);
		if (value.isJsonArray()) {
			return new int[] {value.getAsJsonArray().get(0).getAsInt(), value.getAsJsonArray().get(1).getAsInt()};
		}

		return new int[] {value.getAsInt(), value.getAsInt()};
	}

	private static Block block(JsonObject json, String key, Block fallback) {
		Block block = blockOrNull(json, key);
		return block == null ? fallback : block;
	}

	@Nullable
	private static Block blockOrNull(JsonObject json, String key) {
		if (!json.has(key) || json.get(key).isJsonNull()) {
			return null;
		}

		ResourceLocation id = ResourceLocation.tryParse(json.get(key).getAsString());
		return id != null && BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.get(id) : null;
	}

	private static Map<String, Double> roofs(JsonObject json) {
		Map<String, Double> roofs = new LinkedHashMap<>();
		if (json.has("roofs")) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("roofs").entrySet()) {
				roofs.put(entry.getKey(), entry.getValue().getAsDouble());
			}
		}

		if (roofs.isEmpty()) {
			roofs.put("gable", 1.0);
		}

		return roofs;
	}
}
