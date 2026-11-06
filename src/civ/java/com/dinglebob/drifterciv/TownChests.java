package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * What a town's chests hold when it's made (the user, 2026-11-06: "we should revamp chest inventories, for more various
 * items, materials, and food given that farmer delights is added, make em more filled most of the chests are empty with 3
 * item stacks being the most"): a household's chests nine to eighteen stacks each, from a few pools (food, its pantry of
 * Farmer's Delight's crops and dishes when that's installed, building stuff, valuables, farming, odds and ends) leaning to
 * what its kind of household would keep; the store's, fuller still, by what each chest is for. Things a mod doesn't have
 * (Farmer's Delight not installed) are left out.
 */
final class TownChests {
	private TownChests() {
	}

	/** An item that may go in, how many (from, to), and how likely next to the others in its pool. */
	private record Entry(String id, int min, int max, double weight) {
	}

	private static Entry e(String id, int min, int max, double weight) {
		return new Entry(id, min, max, weight);
	}

	static final List<Entry> FOOD = List.of(e("minecraft:bread", 6, 24, 4), e("minecraft:baked_potato", 6, 20, 3), e("minecraft:cooked_beef", 4, 16, 3),
		e("minecraft:cooked_porkchop", 4, 16, 3), e("minecraft:cooked_chicken", 4, 16, 3), e("minecraft:cooked_mutton", 4, 12, 2),
		e("minecraft:cooked_cod", 4, 12, 2), e("minecraft:cooked_salmon", 4, 12, 2), e("minecraft:apple", 3, 12, 3), e("minecraft:carrot", 6, 24, 3),
		e("minecraft:potato", 6, 24, 3), e("minecraft:beetroot", 4, 16, 2), e("minecraft:pumpkin_pie", 2, 8, 1.5), e("minecraft:cookie", 4, 16, 1.5),
		e("minecraft:melon_slice", 6, 20, 1.5), e("minecraft:honey_bottle", 1, 4, 1), e("minecraft:mushroom_stew", 1, 1, 1), e("minecraft:dried_kelp", 6, 24, 1),
		e("minecraft:sweet_berries", 6, 20, 1.5), e("minecraft:egg", 3, 12, 2), e("minecraft:sugar", 4, 16, 1), e("minecraft:milk_bucket", 1, 1, 0.5),
		e("minecraft:beef", 3, 10, 1.5), e("minecraft:porkchop", 3, 10, 1.5), e("minecraft:chicken", 3, 10, 1.5), e("minecraft:cod", 3, 10, 1));

	/** Farmer's Delight's crops, cuts and dishes (only when it's installed). */
	static final List<Entry> PANTRY = List.of(e("farmersdelight:cabbage", 4, 16, 3), e("farmersdelight:tomato", 4, 16, 3),
		e("farmersdelight:onion", 4, 16, 3), e("farmersdelight:rice", 6, 24, 2.5), e("farmersdelight:cabbage_leaf", 4, 12, 1.5),
		e("farmersdelight:minced_beef", 2, 8, 1.5), e("farmersdelight:bacon", 2, 8, 1.5), e("farmersdelight:chicken_cuts", 2, 8, 1.5),
		e("farmersdelight:cod_slice", 2, 8, 1), e("farmersdelight:ham", 1, 2, 0.6), e("farmersdelight:wheat_dough", 2, 8, 1.2),
		e("farmersdelight:raw_pasta", 2, 8, 1), e("farmersdelight:pie_crust", 1, 3, 0.8), e("farmersdelight:cooked_rice", 2, 6, 1.2),
		e("farmersdelight:fried_egg", 2, 8, 1.2), e("farmersdelight:cooked_bacon", 2, 8, 1.2), e("farmersdelight:beef_stew", 1, 3, 1.2),
		e("farmersdelight:chicken_soup", 1, 3, 1.2), e("farmersdelight:vegetable_soup", 1, 3, 1.2), e("farmersdelight:fish_stew", 1, 3, 0.8),
		e("farmersdelight:pumpkin_soup", 1, 3, 0.8), e("farmersdelight:fried_rice", 1, 3, 1), e("farmersdelight:mushroom_rice", 1, 3, 0.8),
		e("farmersdelight:pasta_with_meatballs", 1, 2, 0.6), e("farmersdelight:steak_and_potatoes", 1, 2, 0.6), e("farmersdelight:ratatouille", 1, 2, 0.6),
		e("farmersdelight:bacon_and_eggs", 1, 3, 0.8), e("farmersdelight:egg_sandwich", 1, 4, 1), e("farmersdelight:chicken_sandwich", 1, 4, 0.8),
		e("farmersdelight:hamburger", 1, 3, 0.8), e("farmersdelight:mutton_wrap", 1, 3, 0.6), e("farmersdelight:dumplings", 2, 6, 0.8),
		e("farmersdelight:stuffed_potato", 1, 4, 0.8), e("farmersdelight:fruit_salad", 1, 3, 0.8), e("farmersdelight:mixed_salad", 1, 3, 0.8),
		e("farmersdelight:apple_pie", 1, 1, 0.4), e("farmersdelight:sweet_berry_cookie", 4, 12, 1), e("farmersdelight:honey_cookie", 4, 12, 0.8),
		e("farmersdelight:cabbage_seeds", 4, 12, 1), e("farmersdelight:tomato_seeds", 4, 12, 1), e("farmersdelight:straw", 4, 16, 1),
		e("farmersdelight:rope", 4, 16, 0.8), e("farmersdelight:canvas", 2, 6, 0.6));

	static final List<Entry> BUILDING = List.of(e("minecraft:cobblestone", 24, 64, 4), e("minecraft:stone", 16, 48, 2), e("minecraft:oak_log", 12, 32, 3),
		e("minecraft:spruce_log", 12, 32, 2), e("minecraft:birch_log", 8, 24, 1.5), e("minecraft:oak_planks", 24, 64, 3),
		e("minecraft:spruce_planks", 16, 48, 2), e("minecraft:stick", 12, 32, 2), e("minecraft:glass", 8, 24, 1.5), e("minecraft:bricks", 8, 32, 1),
		e("minecraft:clay_ball", 8, 24, 1), e("minecraft:sand", 8, 32, 1), e("minecraft:gravel", 8, 24, 1), e("minecraft:dirt", 16, 48, 1.5),
		e("minecraft:stone_bricks", 16, 48, 1), e("minecraft:cobblestone_stairs", 4, 16, 0.8), e("minecraft:oak_slab", 6, 16, 0.8),
		e("minecraft:ladder", 4, 12, 0.8), e("minecraft:oak_fence", 4, 12, 0.8), e("minecraft:white_wool", 2, 8, 1), e("minecraft:andesite", 8, 32, 1),
		e("minecraft:deepslate", 8, 32, 0.6), e("minecraft:hay_block", 1, 4, 0.8));

	static final List<Entry> VALUABLES = List.of(e("minecraft:iron_ingot", 4, 18, 4), e("minecraft:raw_iron", 4, 16, 2), e("minecraft:gold_ingot", 2, 8, 1.5),
		e("minecraft:gold_nugget", 6, 24, 1.5), e("minecraft:copper_ingot", 6, 24, 2), e("minecraft:raw_copper", 6, 20, 1), e("minecraft:coal", 12, 48, 4),
		e("minecraft:charcoal", 8, 24, 1.5), e("minecraft:redstone", 8, 32, 1.5), e("minecraft:lapis_lazuli", 4, 16, 1), e("minecraft:emerald", 1, 6, 1),
		e("minecraft:diamond", 1, 2, 0.25), e("minecraft:amethyst_shard", 2, 8, 0.6), e("minecraft:quartz", 4, 12, 0.4));

	static final List<Entry> FARMING = List.of(e("minecraft:wheat_seeds", 12, 48, 4), e("minecraft:wheat", 8, 32, 3), e("minecraft:beetroot_seeds", 6, 24, 2),
		e("minecraft:melon_seeds", 2, 8, 1), e("minecraft:pumpkin_seeds", 2, 8, 1), e("minecraft:bone_meal", 6, 24, 2), e("minecraft:oak_sapling", 2, 8, 1.5),
		e("minecraft:spruce_sapling", 2, 6, 1), e("minecraft:sugar_cane", 6, 24, 1.5), e("minecraft:leather", 3, 12, 1.5), e("minecraft:feather", 4, 16, 1.5),
		e("minecraft:white_wool", 4, 12, 1), e("minecraft:pumpkin", 1, 6, 1), e("minecraft:melon", 1, 4, 0.6), e("minecraft:bamboo", 6, 24, 0.6));

	static final List<Entry> ODDS = List.of(e("minecraft:torch", 12, 48, 4), e("minecraft:string", 4, 16, 2), e("minecraft:flint", 4, 12, 1.5),
		e("minecraft:bone", 4, 16, 1.5), e("minecraft:arrow", 8, 32, 1.5), e("minecraft:paper", 4, 16, 1.5), e("minecraft:book", 1, 6, 1.2),
		e("minecraft:candle", 2, 6, 0.8), e("minecraft:flower_pot", 1, 3, 0.6), e("minecraft:bucket", 1, 1, 1), e("minecraft:shears", 1, 1, 0.6),
		e("minecraft:fishing_rod", 1, 1, 0.6), e("minecraft:bowl", 2, 8, 1), e("minecraft:glass_bottle", 2, 8, 0.8), e("minecraft:lead", 1, 2, 0.5),
		e("minecraft:name_tag", 1, 1, 0.15), e("minecraft:saddle", 1, 1, 0.2), e("minecraft:poppy", 1, 6, 0.8), e("minecraft:dandelion", 1, 6, 0.8),
		e("minecraft:red_dye", 2, 8, 0.6), e("minecraft:blue_dye", 2, 8, 0.4), e("minecraft:ink_sac", 2, 6, 0.6), e("minecraft:gunpowder", 2, 8, 0.6),
		e("minecraft:slime_ball", 1, 4, 0.3), e("minecraft:map", 1, 1, 0.3), e("minecraft:compass", 1, 1, 0.2), e("minecraft:clock", 1, 1, 0.15));

	/** A household's kinds of things, by how much of each it keeps (the pools above, in order: food, pantry, building, valuables, farming, odds). */
	private static final double[][] HOUSEHOLDS = {
		// a farmer's: food and the fields
		{4, 3, 1, 1, 4, 1},
		// a builder's: stone and wood
		{2, 1, 5, 1.5, 1, 1.5},
		// a smith's: ore, coal, ingots
		{2, 1, 2, 5, 0.5, 1.5},
		// a cook's: the pantry
		{4, 5, 1, 1, 2, 1},
		// a hunter's or fisher's: meat and fish, arrows and string
		{5, 2, 1, 1, 1.5, 3},
		// a well-off one's: a bit of everything, more of the good stuff
		{3, 2, 2, 3, 1.5, 2}};

	/** A household's chests: each 9 to 18 stacks, by a kind of household picked for it. */
	static void household(net.minecraft.server.level.ServerLevel level, List<ChestBlockEntity> chests, RandomSource random) {
		double[] mix = HOUSEHOLDS[random.nextInt(HOUSEHOLDS.length)];
		for (ChestBlockEntity chest : chests) {
			fill(chest, mix, 9 + random.nextInt(10), random);
		}
	}

	/** One of the town store's chests, by what it's for (0 wood, 1 stone, 2 ore and coal, 3 food, then the rest a bit of everything): full-ish. */
	static void store(ChestBlockEntity chest, int which, RandomSource random) {
		double[] mix = switch (which) {
			case 0, 1 -> new double[] {0, 0, 6, 0.5, 0.5, 0.5};
			case 2 -> new double[] {0, 0, 0.5, 6, 0, 1};
			case 3 -> new double[] {5, 4, 0, 0, 2, 0};
			case 4 -> new double[] {1, 1, 1, 1, 4, 1};
			default -> new double[] {2, 2, 2, 2, 2, 3};
		};
		fill(chest, mix, 14 + random.nextInt(9), random);
	}

	private static final List<List<Entry>> POOLS = List.of(FOOD, PANTRY, BUILDING, VALUABLES, FARMING, ODDS);

	/** {@code stacks} stacks into the chest's empty slots, at random, each from a pool picked by {@code mix}. */
	private static void fill(ChestBlockEntity chest, double[] mix, int stacks, RandomSource random) {
		List<Integer> free = new ArrayList<>();
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			if (chest.getItem(slot).isEmpty()) {
				free.add(slot);
			}
		}

		double total = 0;
		for (double w : mix) {
			total += w;
		}

		for (int n = 0; n < stacks && !free.isEmpty() && total > 0; n++) {
			double roll = random.nextDouble() * total;
			int pool = 0;
			while (pool < mix.length - 1 && (roll -= mix[pool]) > 0) {
				pool++;
			}

			ItemStack stack = pick(POOLS.get(pool), random);
			if (stack.isEmpty()) {
				// (Farmer's Delight not installed: its pantry's food from the plain list.)
				stack = pick(FOOD, random);
			}

			if (!stack.isEmpty()) {
				chest.setItem(free.remove(random.nextInt(free.size())), stack);
			}
		}
	}

	/** One stack from a pool, by weight, of something this game has (empty if none). */
	private static ItemStack pick(List<Entry> pool, RandomSource random) {
		double total = 0;
		List<Entry> there = new ArrayList<>();
		for (Entry entry : pool) {
			if (item(entry.id) != null) {
				there.add(entry);
				total += entry.weight;
			}
		}

		double roll = random.nextDouble() * total;
		for (Entry entry : there) {
			if ((roll -= entry.weight) <= 0) {
				Item item = item(entry.id);
				int count = Math.min(item.getDefaultMaxStackSize(), entry.min + random.nextInt(entry.max - entry.min + 1));
				return new ItemStack(item, count);
			}
		}

		return ItemStack.EMPTY;
	}

	private static Item item(String id) {
		var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id));
		return item.isPresent() && item.get() != Items.AIR ? item.get() : null;
	}
}
