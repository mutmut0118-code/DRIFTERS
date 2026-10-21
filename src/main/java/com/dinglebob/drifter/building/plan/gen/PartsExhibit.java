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
 * by hand, and then you can take the world back and update the generated house parts"). `/drifter house parts` (debug):
 * <ol>
 *   <li>each part of a generated house on its own, on the same small house (walls, door, windows; each roof; base row,
 *       frame, beam, upper floor; sills, tall windows; porch, chimney, an off-centre door, loft, corner tower, wing, attic
 *       windows);</li>
 *   <li>a house of each style;</li>
 *   <li>the hand-made plans.</li>
 * </ol>
 * In the generator's own materials: oak for a Drifter's main wood, spruce for its second, cobblestone for its stone (what
 * HousePalette swaps for the Drifter's own). A sign by each. What's where (and how each was made) is written to
 * {@code drifter_house_parts.json} in the world's folder, to read the edits back.
 */
public final class PartsExhibit {
	private PartsExhibit() {
	}

	/** Rows this far apart (blocks, north to south); houses along a row at least this far apart. */
	private static final int ROW = 32;
	private static final int GAP = 6;

	private record Part(String label, String detail, JsonObject style, @Nullable String feature) {
	}

	/** The plain house every part sits on: 7 by 6, oak, glass, an oak door, a gable roof, nothing else. */
	private static JsonObject plain() {
		JsonObject s = new JsonObject();
		s.addProperty("width", 7);
		s.addProperty("depth", 6);
		s.addProperty("wall_height", 3);
		s.addProperty("two_floors", 0);
		s.addProperty("floor", "minecraft:oak_planks");
		s.addProperty("infill", "minecraft:oak_planks");
		s.addProperty("window", "minecraft:glass");
		s.addProperty("window_spacing", 2);
		s.addProperty("tall_windows", 0);
		s.addProperty("stair", "minecraft:spruce_stairs");
		s.addProperty("slab", "minecraft:spruce_slab");
		s.addProperty("gable_fill", "minecraft:oak_planks");
		s.addProperty("door", "minecraft:oak_door");
		for (String none : new String[] {"porch", "chimney", "door_off_center", "wing", "loft", "tower"}) {
			s.addProperty(none, 0);
		}

		roofs(s, "gable");
		return s;
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
				JsonArray array = new JsonArray();
				array.add(range[0]);
				array.add(range[1]);
				s.add(key, array);
			} else {
				s.addProperty(key, (String)value);
			}
		}

		return s;
	}

	private static List<Part> parts() {
		return List.of(
			new Part("Walls, door,", "windows, gable", with(), "gable"),
			new Part("Roof: gable", "along z", with("roof", "gable_z"), "gable_z"),
			new Part("Roof: hip", "", with("roof", "hip"), "hip"),
			new Part("Roof: shed", "", with("roof", "shed"), "shed"),
			new Part("Roof: flat", "with parapet", with("roof", "flat", "parapet", "minecraft:cobblestone_wall"), "flat"),
			new Part("Base row", "bottom of walls", with("base", "minecraft:cobblestone"), null),
			new Part("Frame", "posts every 2", with("frame", "minecraft:spruce_log", "posts_every", 2), null),
			new Part("Beam", "log on wall tops", with("frame", "minecraft:spruce_log", "beam", true), null),
			new Part("Two floors", "upper infill", with("two_floors", 1, "upper_infill", "minecraft:spruce_planks"), "two floors"),
			new Part("Window sills", "", with("sill", "minecraft:spruce_stairs"), "sills"),
			new Part("Tall windows", "walls 4 high", with("wall_height", 4, "tall_windows", 1), "tall windows"),
			new Part("Porch", "", with("porch", 1, "porch_floor", "minecraft:spruce_slab", "porch_post", "minecraft:oak_fence"), "porch"),
			new Part("Chimney", "", with("chimney", 1, "chimney_block", "minecraft:cobblestone"), "chimney"),
			new Part("Door", "off centre", with("door_off_center", 1), "off-centre door"),
			new Part("Loft", "gable 7+ deep", with("depth", 7, "loft", 1), "loft"),
			new Part("Corner tower", "", with("tower", 1), "tower"),
			new Part("Wing (L)", "", with("wing", 1, "wing_depth", new int[] {4, 4}), "wing"),
			new Part("Attic windows", "gable 7+ across", with("depth", 8), "gable"));
	}

	/** A plan from this style with this feature (draws again until it has it), or null. */
	@Nullable
	private static HousePlan draw(String id, HouseStyle style, @Nullable String feature) {
		for (long seed = 1; seed <= 400; seed++) {
			HousePlan plan = PlanGenerator.generate(id, style, RandomSource.create(seed * 7919L));
			if (plan != null && (feature == null || PlanGenerator.lastFeatures.stream().anyMatch(f -> f.equals(feature) || f.endsWith(feature)))) {
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
			if (plan.id.startsWith(GeneratedPlans.PREFIX) || plan.source.equals("generated")) {
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
		return Math.max(16, plan.size.getX() + GAP);
	}
}
