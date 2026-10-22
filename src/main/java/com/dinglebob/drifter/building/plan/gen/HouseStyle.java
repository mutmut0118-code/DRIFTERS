package com.dinglebob.drifter.building.plan.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

/**
 * A house style for the plan generator (milestone 15; user, 2026-10-17: "lets make whole milestone dedicated to generating
 * houseplans, im talking hundreds"): the materials and proportions a family of plans shares. Read from
 * {@code data/<namespace>/drifter_house_styles/*.json}, so new styles are a data file. A material may be one block id or a
 * list to pick from (each plan picks once). Oak and spruce stand for the Drifter's main and second wood and cobblestone for
 * its stone (HousePalette swaps them for what it has), so a style writes them as such. See the folder's _README.txt.
 */
public final class HouseStyle {
	private final String id;
	private final JsonObject json;

	private HouseStyle(String id, JsonObject json) {
		this.id = id;
		this.json = json;
	}

	public static HouseStyle parse(String id, JsonObject json) {
		return new HouseStyle(id, json);
	}

	public String id() {
		return this.id;
	}

	public double weight() {
		return this.num("weight", 1.0);
	}

	/** A number (true and false count as 1 and 0, so a yes/no field is a sure chance). */
	public double num(String key, double fallback) {
		if (!this.json.has(key) || !this.json.get(key).isJsonPrimitive()) {
			return fallback;
		}

		var value = this.json.getAsJsonPrimitive(key);
		return value.isBoolean() ? (value.getAsBoolean() ? 1.0 : 0.0) : value.getAsDouble();
	}

	/** A whole number between the range's two ends ({@code [min, max]} or a single number). */
	public int between(String key, int low, int high, RandomSource random) {
		int[] range = {low, high};
		if (this.json.has(key)) {
			JsonElement value = this.json.get(key);
			range = value.isJsonArray() ? new int[] {value.getAsJsonArray().get(0).getAsInt(), value.getAsJsonArray().get(1).getAsInt()}
				: new int[] {value.getAsInt(), value.getAsInt()};
		}

		return range[0] + random.nextInt(Math.max(1, range[1] - range[0] + 1));
	}

	public boolean chance(String key, double fallback, RandomSource random) {
		return random.nextDouble() < this.num(key, fallback);
	}

	/** One of the material's options (or the fallback; null fallback means "none" when the style leaves it out). */
	@Nullable
	public Block material(String key, @Nullable Block fallback, RandomSource random) {
		if (!this.json.has(key) || this.json.get(key).isJsonNull()) {
			return fallback;
		}

		List<Block> options = new ArrayList<>();
		JsonElement value = this.json.get(key);
		if (value.isJsonArray()) {
			value.getAsJsonArray().forEach(element -> {
				Block block = block(element.isJsonNull() ? null : element.getAsString());
				// (null in a list: "none" is one of the options.)
				options.add(block);
			});
		} else {
			options.add(block(value.getAsString()));
		}

		return options.isEmpty() ? fallback : options.get(random.nextInt(options.size()));
	}

	@Nullable
	private static Block block(@Nullable String id) {
		if (id == null || id.equals("none")) {
			return null;
		}

		ResourceLocation location = ResourceLocation.tryParse(id);
		return location != null && BuiltInRegistries.BLOCK.containsKey(location) ? BuiltInRegistries.BLOCK.get(location) : null;
	}

	/** One of the style's footprints ({@code [[width, depth], ...]}: width along the front), or the fallback. */
	public int[] size(String key, int[] fallback, RandomSource random) {
		if (!this.json.has(key) || !this.json.get(key).isJsonArray() || this.json.getAsJsonArray(key).isEmpty()) {
			return fallback;
		}

		JsonElement pick = this.json.getAsJsonArray(key).get(random.nextInt(this.json.getAsJsonArray(key).size()));
		return new int[] {pick.getAsJsonArray().get(0).getAsInt(), pick.getAsJsonArray().get(1).getAsInt()};
	}

	/** Whole numbers to choose from ({@code [3, 4]}), in order (the first that fits wins). */
	public List<Integer> numbers(String key, List<Integer> fallback) {
		if (!this.json.has(key) || !this.json.get(key).isJsonArray()) {
			return fallback;
		}

		List<Integer> out = new ArrayList<>();
		this.json.getAsJsonArray(key).forEach(element -> out.add(element.getAsInt()));
		return out;
	}

	/** Roof types and their weights (gable, gable_z, shed, flat). */
	public Map<String, Double> roofs() {
		Map<String, Double> roofs = new LinkedHashMap<>();
		if (this.json.has("roofs")) {
			for (Map.Entry<String, JsonElement> entry : this.json.getAsJsonObject("roofs").entrySet()) {
				roofs.put(entry.getKey(), entry.getValue().getAsDouble());
			}
		}

		if (roofs.isEmpty()) {
			roofs.put("gable", 1.0);
		}

		return roofs;
	}
}
