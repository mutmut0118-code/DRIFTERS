package com.dinglebob.drifter.building.plan.gen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.building.plan.HousePlan;
import com.dinglebob.drifter.building.plan.HousePlans;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The house parts laid out to look at and edit by hand (the user, 2026-10-21: "a flat world creative ... containing all of
 * the parts that build a house, but defaulted to oak so it can translate to other plans; i want to edit some of the stuff
 * by hand, and then you can take the world back and update the generated house parts"; again for the guidebook revamp,
 * 2026-10-22: "send a new world of the new revamped houses"). `/drifter house parts` (debug):
 * <ol>
 *   <li>each part of a generated house on its own, on the same plain house (13 by 9: walls in depth, door, windows, a
 *       gable; then each roof, two and three storeys with their stairs, the door in the gable, a double door, an
 *       off-centre door, sills and lintels, tall and wide windows, a tower, an L and a T wing, two rooms,
 *       a vaulted roof, lanterns, a log ridge, a floor border);</li>
 *   <li>a house of each style;</li>
 *   <li>the hand-made plans (the mod's and the world's).</li>
 * </ol>
 * In the generator's own materials: oak for a Drifter's main wood, spruce for its second, cobblestone for its stone (what
 * HousePalette swaps for the Drifter's own). A sign by each. What's where (and how each was made) is written to
 * {@code drifter_house_parts.json} in the world's folder, to read the edits back.
 */
public final class PartsExhibit {
	private PartsExhibit() {
	}

	/** Rows this far apart (blocks, north to south); houses along a row at least this far apart. */
	private static final int ROW = 40;
	private static final int GAP = 6;

	private record Part(String label, String detail, JsonObject style, @Nullable String feature) {
	}

	/** The plain house every part sits on: 13 by 9 (three bays of three by two), one storey, a gable, nothing else. */
	private static JsonObject plain() {
		JsonObject s = new JsonObject();
		s.add("gaps", numbers(3));
		s.add("long_bays", numbers(3));
		s.add("short_bays", numbers(2));
		s.add("floors", numbers(1, 1));
		s.addProperty("foundation", "minecraft:cobblestone");
		s.addProperty("plinth", "minecraft:cobblestone_stairs");
		s.addProperty("floor", "minecraft:oak_planks");
		s.addProperty("frame", "minecraft:spruce_log");
		s.addProperty("infill", "minecraft:oak_planks");
		s.addProperty("window", "minecraft:glass");
		s.addProperty("stair", "minecraft:spruce_stairs");
		s.addProperty("slab", "minecraft:spruce_slab");
		s.addProperty("door", "minecraft:spruce_door");
		s.addProperty("chimney_block", "minecraft:cobblestone");
		for (String none : new String[] {"gable_front", "lintels", "ridge_log", "lanterns", "tall_windows", "wide_windows", "vaulted", "chimney", "wing",
			"tower", "partition"}) {
			s.addProperty(none, 0);
		}

		roofs(s, "gable");
		return s;
	}

	private static JsonArray numbers(int... values) {
		JsonArray array = new JsonArray();
		for (int value : values) {
			array.add(value);
		}

		return array;
	}

	private static void roofs(JsonObject s, String roof) {
		JsonObject roofs = new JsonObject();
		roofs.addProperty(roof, 1);
		s.add("roofs", roofs);
	}

	private static JsonObject with(Object... pairs) {
		JsonObject s = plain();
		for (int i = 0; i < pairs.length; i += 2) {
			String key = (String)pairs[i];
			Object value = pairs[i + 1];
			if (key.equals("roof")) {
				roofs(s, (String)value);
			} else if (value instanceof Number n) {
				s.addProperty(key, n);
			} else if (value instanceof Boolean b) {
				s.addProperty(key, b);
			} else if (value instanceof int[] range) {
				s.add(key, numbers(range));
			} else {
				s.addProperty(key, (String)value);
			}
		}

		return s;
	}

	private static List<Part> parts() {
		return List.of(
			new Part("Walls, door,", "windows, gable", with(), "gable"),
			new Part("Roof: steep", "2 up, 1 across", with("roof", "steep"), "steep"),
			new Part("Roof: hip", "", with("roof", "hip"), "hip"),
			new Part("Hip: square", "9 x 9", with("long_bays", new int[] {2}), "hip"),
			new Part("Two storeys", "and stairs", with("floors", new int[] {2, 2}), "2 floors"),
			new Part("Three storeys", "9 x 9", with("long_bays", new int[] {2}, "floors", new int[] {3, 3}), "3 floors"),
			new Part("Door in", "the gable end", with("gable_front", 1), "door in the gable"),
			new Part("Double door", "4-wide bays", with("gaps", new int[] {4}), "double door"),
			new Part("Door at 1/3", "4 bays", with("long_bays", new int[] {4}), "off-centre door"),
			new Part("Sills and", "lintels", with("sill", "minecraft:spruce_stairs", "lintels", 1), "sills"),
			new Part("Tall windows", "", with("tall_windows", 1), "tall windows"),
			new Part("Wide windows", "5-wide bays", with("gaps", new int[] {5}, "wide_windows", 1), null),
			new Part("Corner tower", "", with("tower", 1), "tower"),
			new Part("Wing (L)", "21 x 13", with("long_bays", new int[] {5}, "short_bays", new int[] {3}, "wing", 1, "wing_t", 0), "L wing"),
			new Part("Wing (T)", "21 x 13", with("long_bays", new int[] {5}, "short_bays", new int[] {3}, "wing", 1, "wing_t", 1), "T wing"),
			new Part("Two rooms", "17 long", with("long_bays", new int[] {4}, "partition", 1), "two rooms"),
			new Part("Vaulted roof", "2 thick", with("vaulted", 1), "vaulted"),
			new Part("Lanterns", "", with("lanterns", 1), "lanterns"),
			new Part("Roof: gambrel", "barn roof", with("roof", "gambrel"), "gambrel"),
			new Part("Dormers", "17 x 13", with("long_bays", new int[] {4}, "short_bays", new int[] {3}, "dormers", 1), "dormers"),
			new Part("Balcony", "over the door", with("floors", new int[] {2, 2}, "balcony", 1), "balcony"),
			new Part("Cross house", "21 x 13", with("long_bays", new int[] {5}, "short_bays", new int[] {3}, "wing", 1, "wing_t", 1, "cross", 1), "cross"),
			new Part("Log ridge,", "floor border", with("ridge_log", 1, "floor_border", "minecraft:spruce_planks"), null));
	}

	/** A plan from this style with this feature (draws again until it has it), or null. */
	@Nullable
	private static HousePlan draw(String id, HouseStyle style, @Nullable String feature) {
		for (long seed = 1; seed <= 400; seed++) {
			HousePlan plan = HouseGenerator.generate(id, style, RandomSource.create(seed * 7919L));
			if (plan != null && PlanCheck.problem(plan) == null
				&& (feature == null || HouseGenerator.lastFeatures.stream().anyMatch(f -> f.equals(feature) || f.endsWith(feature)))) {
				return plan;
			}
		}

		return null;
	}

	/** Lays it all out from {@code at} (the first row's north-west corner) eastward, rows going south. What it made, in words. */
	public static String build(ServerLevel level, BlockPos at) {
		JsonArray map = new JsonArray();
		int made = 0;
		// 1. The parts.
		int x = 0;
		int z = 0;
		int number = 1;
		for (Part part : parts()) {
			HousePlan plan = draw("parts:" + number, HouseStyle.parse("part_" + number, part.style()), part.feature());
			if (plan != null) {
				x += place(level, at.offset(x, 0, z), plan, "#" + number + " " + part.label(), part.detail(), map, "part", part.style(), number);
				made++;
			}

			number++;
		}

		// 2. A house of each style.
		x = 0;
		z += ROW;
		for (Map.Entry<String, HouseStyle> entry : GeneratedPlans.styles().entrySet()) {
			HousePlan plan = draw("style:" + entry.getKey(), entry.getValue(), null);
			if (plan != null) {
				x += place(level, at.offset(x, 0, z), plan, "Style", entry.getKey().replace('_', ' '), map, "style", null, number++);
				made++;
			}
		}

		// 3. The hand-made plans.
		x = 0;
		z += ROW;
		for (HousePlan plan : HousePlans.all(level.getServer()).values()) {
			if (plan.source.equals("generated") || plan.source.equals("personal")) {
				continue;
			}

			x += place(level, at.offset(x, 0, z), plan, "Plan", plan.id.replace("personal:", "").replace("world:", "").replace('_', ' '), map, "plan", null,
				number++);
			made++;
		}

		Path file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("drifter_house_parts.json");
		JsonObject root = new JsonObject();
		root.addProperty("origin", at.getX() + " " + at.getY() + " " + at.getZ());
		root.add("exhibits", map);
		try {
			Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "built " + made + ", but couldn't write " + file + ": " + e.getMessage();
		}

		return "built " + made + " (parts, styles, plans); the list is in " + file.getFileName();
	}

	/** One house as drawn, a sign in front (north of it); the room it took along the row. */
	private static int place(ServerLevel level, BlockPos corner, HousePlan plan, String line1, String line2, JsonArray map, String kind,
		@Nullable JsonObject style, int number) {
		for (HousePlan.Cell cell : plan.cells) {
			level.setBlock(corner.offset(cell.pos()), cell.state(), Block.UPDATE_CLIENTS);
		}

		var box = new net.minecraft.world.level.levelgen.structure.BoundingBox(corner.getX(), corner.getY(), corner.getZ(),
			corner.getX() + plan.size.getX() - 1, corner.getY() + plan.size.getY() - 1, corner.getZ() + plan.size.getZ() - 1);
		com.dinglebob.drifter.world.PlayerPlacedBlocks.recordBuiltBox(level, box);
		BlockPos signAt = corner.offset(plan.size.getX() / 2, 0, -3);
		level.setBlock(signAt, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8), Block.UPDATE_ALL);
		if (level.getBlockEntity(signAt) instanceof SignBlockEntity sign) {
			SignText text = new SignText();
			String[] lines = {line1, line2, plan.size.getX() + " x " + plan.size.getZ(), plan.id.length() > 15 ? plan.id.substring(0, 15) : plan.id};
			for (int i = 0; i < 4; i++) {
				text = text.setMessage(i, Component.literal(lines[i].length() > 15 ? lines[i].substring(0, 15) : lines[i]));
			}

			sign.setText(text, true);
			sign.setText(text, false);
		}

		JsonObject entry = new JsonObject();
		entry.addProperty("number", number);
		entry.addProperty("kind", kind);
		entry.addProperty("label", (line1 + " " + line2).trim());
		entry.addProperty("plan", plan.id);
		entry.addProperty("from", box.minX() + " " + box.minY() + " " + box.minZ());
		entry.addProperty("to", box.maxX() + " " + box.maxY() + " " + box.maxZ());
		if (style != null) {
			entry.add("style", JsonParser.parseString(style.toString()));
		}

		map.add(entry);
		return Math.max(20, plan.size.getX() + GAP);
	}
}
