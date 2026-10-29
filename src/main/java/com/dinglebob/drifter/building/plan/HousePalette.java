package com.dinglebob.drifter.building.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * A plan's materials made the Drifter's own (user's idea, 2026-10-16: "it can use logs that are around it, so it's not a
 * specific spruce house... it generalizes it and creates variety"). Wood is a family (logs, planks, stairs, slabs,
 * fences, doors, trapdoors...): the plan's main wood becomes the wood it has or sees most round it, and a second wood the
 * plan uses for contrast becomes a second wood it knows (or stays). Cobblestone and cobbled deepslate swap the same way.
 * Everything else stays as the plan has it. Block properties (which way a stair faces) carry over.
 */
public final class HousePalette {
	/** Overworld woods, in the order players usually have them. */
	public static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry");
	/** How dark each wood's planks are (1 light to 7 dark): a frame should stand out from its wall (guidebook: "pair dark with light"). */
	private static final Map<String, Integer> DARKNESS = Map.of("birch", 1, "cherry", 2, "oak", 3, "jungle", 4, "acacia", 4, "mangrove", 5,
		"spruce", 6, "dark_oak", 7);
	/** A second wood that stands out against each (when it can be any). */
	private static final Map<String, String> PARTNER = Map.of("oak", "spruce", "birch", "spruce", "spruce", "oak", "dark_oak", "oak",
		"jungle", "dark_oak", "acacia", "dark_oak", "mangrove", "dark_oak", "cherry", "dark_oak");
	/** Parts of a wood family, as their ids end (the stripped ones start with "stripped_"). */
	private static final List<String> PARTS = List.of("_log", "_wood", "_planks", "_stairs", "_slab", "_fence_gate", "_fence", "_door",
		"_trapdoor", "_button", "_pressure_plate", "_wall_hanging_sign", "_hanging_sign", "_wall_sign", "_sign");

	private final Map<String, String> woods;
	private final boolean deepslate;

	private HousePalette(Map<String, String> woods, boolean deepslate) {
		this.woods = woods;
		this.deepslate = deepslate;
	}

	/** A saved palette (the one it picked when it chose the plan). */
	public static HousePalette of(Map<String, String> woods, boolean deepslate) {
		return new HousePalette(Map.copyOf(woods), deepslate);
	}

	public Map<String, String> woods() {
		return this.woods;
	}

	public boolean deepslate() {
		return this.deepslate;
	}

	/** As the plan is: nothing swapped. */
	public static HousePalette asDrawn() {
		return new HousePalette(Map.of(), false);
	}

	/**
	 * This Drifter's palette for this plan: the plan's woods by how much it uses them, matched to the woods it has (logs and
	 * planks on it and in its chests) and sees round home, most first; deepslate for cobblestone if it has more of that.
	 */
	/** The plan's main wood as this one, its other woods one that stands out against it (world-gen houses: the wood that grows round them). */
	/**
	 * Its stone, kept with its woods (so a home's saved palette has it): what cobblestone (and its stairs, slabs and walls)
	 * becomes, e.g. "sandstone" (a desert town: milestone 23 A3).
	 */
	public static final String STONE = "@stone";

	public static HousePalette forWood(HousePlan plan, String wood) {
		return forWood(plan, wood, null);
	}

	public static HousePalette forWood(HousePlan plan, String wood, @org.jetbrains.annotations.Nullable String stone) {
		Map<String, String> map = new HashMap<>();
		if (stone != null) {
			map.put(STONE, stone);
		}

		List<String> planWoods = woodsByUse(plan);
		for (int i = 0; i < planWoods.size(); i++) {
			map.put(planWoods.get(i), i == 0 ? wood : PARTNER.getOrDefault(wood, wood));
		}

		return new HousePalette(map, false);
	}

	public static HousePalette forDrifter(DrifterEntity drifter, HousePlan plan) {
		List<String> planWoods = woodsByUse(plan);
		List<String> theirs = woodsAround(drifter);
		Map<String, String> map = new HashMap<>();
		for (int i = 0; i < planWoods.size(); i++) {
			String to = i < theirs.size() ? theirs.get(i) : theirs.isEmpty() ? planWoods.get(i) : theirs.get(0);
			map.put(planWoods.get(i), to);
		}

		// The second wood (the frame, the roof) the one it knows that stands out most against the first, of its three most
		// at hand (two woods alike, oak and jungle say, make a flat wall). One wood only: the house is all that wood.
		if (planWoods.size() >= 2 && theirs.size() >= 2) {
			String main = theirs.get(0);
			String best = theirs.get(1);
			for (String candidate : theirs.subList(1, Math.min(3, theirs.size()))) {
				if (contrast(main, candidate) > contrast(main, best)) {
					best = candidate;
				}
			}

			map.put(planWoods.get(1), best);
			if (planWoods.size() >= 3 && map.get(planWoods.get(2)).equals(best)) {
				map.put(planWoods.get(2), theirs.get(1).equals(best) ? theirs.get(Math.min(2, theirs.size() - 1)) : theirs.get(1));
			}
		}

		int cobble = count(drifter, net.minecraft.world.item.Items.COBBLESTONE);
		int deep = count(drifter, net.minecraft.world.item.Items.COBBLED_DEEPSLATE);
		return new HousePalette(map, deep > cobble);
	}

	private static int contrast(String a, String b) {
		return Math.abs(DARKNESS.getOrDefault(a, 4) - DARKNESS.getOrDefault(b, 4));
	}

	/** The plan's woods, the most used first. */
	static List<String> woodsByUse(HousePlan plan) {
		Map<String, Integer> uses = new LinkedHashMap<>();
		for (HousePlan.Cell cell : plan.cells) {
			String wood = woodOf(BuiltInRegistries.BLOCK.getKey(cell.state().getBlock()).getPath());
			if (wood != null) {
				uses.merge(wood, 1, Integer::sum);
			}
		}

		List<String> order = new ArrayList<>(uses.keySet());
		order.sort((a, b) -> uses.get(b) - uses.get(a));
		return order;
	}

	/** Woods it has (on it and stored at home) and sees round it, the most first. */
	public static List<String> woodsAround(DrifterEntity drifter) {
		Map<String, Integer> score = new LinkedHashMap<>();
		var inventory = drifter.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			var stack = inventory.getItem(slot);
			if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS)) {
				String wood = woodOf(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
				if (wood != null) {
					score.merge(wood, stack.getCount() * (stack.is(ItemTags.LOGS) ? 4 : 1), Integer::sum);
				}
			}
		}

		Home home = drifter.getDrifterBrain().home();
		if (home != null) {
			home.stored().forEach((item, count) -> {
				String wood = woodOf(BuiltInRegistries.ITEM.getKey(item).getPath());
				if (wood != null && (new net.minecraft.world.item.ItemStack(item).is(ItemTags.LOGS) || new net.minecraft.world.item.ItemStack(item).is(ItemTags.PLANKS))) {
					score.merge(wood, count, Integer::sum);
				}
			});
		}

		// Trees it knows round here: a few points a log.
		for (var pos : KnownBlocks.find(drifter, 48, state -> state.is(BlockTags.LOGS), 64)) {
			String wood = woodOf(BuiltInRegistries.BLOCK.getKey(drifter.level().getBlockState(pos).getBlock()).getPath());
			if (wood != null) {
				score.merge(wood, 3, Integer::sum);
			}
		}

		List<String> order = new ArrayList<>(score.keySet());
		order.sort((a, b) -> score.get(b) - score.get(a));
		return order;
	}

	/** Which overworld wood an id belongs to ("spruce_stairs" → spruce), or null. */
	@Nullable
	public static String woodOf(String path) {
		String p = path.startsWith("stripped_") ? path.substring("stripped_".length()) : path;
		for (String wood : WOODS) {
			if (!p.startsWith(wood + "_")) {
				continue;
			}

			String rest = p.substring(wood.length());
			for (String part : PARTS) {
				if (rest.equals(part)) {
					return wood;
				}
			}
		}

		return null;
	}

	private static int count(DrifterEntity drifter, Item item) {
		Home home = drifter.getDrifterBrain().home();
		return drifter.getInventory().count(stack -> stack.is(item)) + (home == null ? 0 : home.stored().getOrDefault(item, 0));
	}

	/** The block this palette puts where the plan has {@code state}, with the same facing, half and the rest. */
	public BlockState apply(BlockState state) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		String path = id.getPath();
		String swapped = path;
		String wood = woodOf(path);
		// Glass panes go in as glass blocks (user, 2026-10-17: "none of the panes connect to the wall": a lone pane in a
		// one-block window is a thin post).
		if (path.endsWith("glass_pane")) {
			swapped = path.substring(0, path.length() - "_pane".length());
		} else if (wood != null && this.woods.containsKey(wood) && !this.woods.get(wood).equals(wood)) {
			String to = this.woods.get(wood);
			swapped = path.startsWith("stripped_") ? "stripped_" + to + path.substring(("stripped_" + wood).length()) : to + path.substring(wood.length());
		} else if (this.woods.containsKey(STONE) && path.startsWith("cobblestone")) {
			swapped = this.woods.get(STONE) + path.substring("cobblestone".length());
		} else if (this.deepslate && path.startsWith("cobblestone")) {
			swapped = "cobbled_deepslate" + path.substring("cobblestone".length());
		}

		if (swapped.equals(path)) {
			return state;
		}

		Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), swapped));
		if (block == Blocks.AIR) {
			return state;
		}

		BlockState out = block.defaultBlockState();
		for (Property<?> property : state.getProperties()) {
			out = copy(state, out, property);
		}

		return out;
	}

	private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
		return to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
	}

	/** The plan's cells in this palette. */
	public List<HousePlan.Cell> apply(HousePlan plan) {
		List<HousePlan.Cell> out = new ArrayList<>(plan.cells.size());
		for (HousePlan.Cell cell : plan.cells) {
			out.add(new HousePlan.Cell(cell.pos(), this.apply(cell.state())));
		}

		return out;
	}

	public String describe() {
		if (this.woods.isEmpty() && !this.deepslate) {
			return "as drawn";
		}

		List<String> parts = new ArrayList<>();
		this.woods.forEach((from, to) -> parts.add(from.equals(STONE) ? "cobblestone → " + to : from.equals(to) ? from + " stays" : from + " → " + to));
		if (this.deepslate) {
			parts.add("cobblestone → cobbled deepslate");
		}

		return String.join(", ", parts);
	}
}
