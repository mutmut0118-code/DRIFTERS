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
 * Farmer's Delight's crops and dishes when that's installed, building stuff, ore, farming, odds and ends) leaning to what
 * its kind of household would keep, and its own valuables (gold, gems, its purse); the store's, fuller still, by what each
 * chest is for, never valuables. Things a mod doesn't have
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

	/** Ore, coal and ingots of the everyday metals (what the store keeps for everyone). */
	static final List<Entry> ORE = List.of(e("minecraft:iron_ingot", 4, 18, 4), e("minecraft:raw_iron", 4, 16, 2), e("minecraft:copper_ingot", 6, 24, 2),
		e("minecraft:raw_copper", 6, 20, 1), e("minecraft:coal", 12, 48, 4), e("minecraft:charcoal", 8, 24, 1.5), e("minecraft:redstone", 8, 32, 1.5));

	/**
	 * A household's own valuables, never the store's (the user, 2026-11-06: "valuable stuff like gems, or coin purses, or
	 * any related to the sort, should be in households instead of shared chests"): gold, gems, Numismatic Overhaul's coins
	 * and money bag when it's there.
	 */
	static final List<Entry> TREASURE = List.of(e("minecraft:gold_ingot", 2, 8, 2), e("minecraft:gold_nugget", 6, 24, 2), e("minecraft:emerald", 1, 6, 2),
		e("minecraft:diamond", 1, 2, 0.4), e("minecraft:lapis_lazuli", 4, 16, 1.2), e("minecraft:amethyst_shard", 2, 8, 1), e("minecraft:quartz", 4, 12, 0.5),
		e("minecraft:raw_gold", 2, 6, 0.6), e("minecraft:golden_apple", 1, 1, 0.2), e("numismatic-overhaul:silver_coin", 2, 12, 1.5),
		e("numismatic-overhaul:bronze_coin", 8, 48, 1.5), e("numismatic-overhaul:gold_coin", 1, 1, 0.1), e("numismatic-overhaul:money_bag", 1, 1, 0.5));

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

	/**
	 * A kitchen cabinet's pantry (the user, 2026-11-06: "a wide array of ingredients in the cabinet, to make various amounts
	 * of farmers delight foods"): what the cooking pot's dishes are made of, a little of a lot of things: Farmer's Delight's
	 * crops, cuts, dough, pasta, milk and sauce (when it's installed), the vegetables, mushrooms, eggs, meat and fish its
	 * dishes call for, and bowls to serve them in.
	 */
	static final List<Entry> INGREDIENTS = List.of(e("farmersdelight:cabbage", 3, 10, 3), e("farmersdelight:tomato", 3, 10, 3),
		e("farmersdelight:onion", 3, 10, 3), e("farmersdelight:rice", 4, 16, 3), e("farmersdelight:cabbage_leaf", 2, 8, 1.5),
		e("farmersdelight:minced_beef", 2, 6, 2), e("farmersdelight:beef_patty", 1, 4, 1), e("farmersdelight:chicken_cuts", 2, 6, 2),
		e("farmersdelight:bacon", 2, 6, 2), e("farmersdelight:mutton_chops", 2, 6, 1.5), e("farmersdelight:cod_slice", 2, 6, 1.5),
		e("farmersdelight:salmon_slice", 2, 6, 1.5), e("farmersdelight:ham", 1, 2, 0.6), e("farmersdelight:wheat_dough", 2, 8, 2),
		e("farmersdelight:raw_pasta", 2, 8, 2), e("farmersdelight:pie_crust", 1, 3, 1), e("farmersdelight:milk_bottle", 2, 6, 2),
		e("farmersdelight:tomato_sauce", 1, 4, 1.5), e("farmersdelight:pumpkin_slice", 2, 8, 1.5), e("minecraft:carrot", 4, 16, 3),
		e("minecraft:potato", 4, 16, 3), e("minecraft:beetroot", 3, 12, 2), e("minecraft:brown_mushroom", 2, 8, 2.5), e("minecraft:red_mushroom", 2, 8, 2),
		e("minecraft:egg", 3, 12, 2.5), e("minecraft:sugar", 3, 12, 1.5), e("minecraft:cocoa_beans", 2, 8, 1), e("minecraft:apple", 2, 8, 1.5),
		e("minecraft:sweet_berries", 4, 12, 1), e("minecraft:glow_berries", 2, 8, 0.6), e("minecraft:dried_kelp", 4, 12, 1), e("minecraft:beef", 2, 6, 1.5),
		e("minecraft:porkchop", 2, 6, 1.5), e("minecraft:chicken", 2, 6, 1.5), e("minecraft:mutton", 2, 6, 1), e("minecraft:cod", 2, 6, 1),
		e("minecraft:salmon", 2, 6, 1), e("minecraft:rabbit", 1, 4, 0.6), e("minecraft:bread", 2, 8, 1), e("minecraft:bone", 2, 6, 0.5),
		e("minecraft:wheat", 4, 16, 1), e("minecraft:honey_bottle", 1, 3, 0.5));

	/** A cabinet's pantry: 14 to 22 ingredients, each a different one, and a stack of bowls. */
	static void cabinet(net.minecraft.world.Container cabinet, boolean tidy, RandomSource random) {
		List<ItemStack> stacks = new ArrayList<>();
		java.util.Set<Item> had = new java.util.HashSet<>();
		int want = Math.min(cabinet.getContainerSize() - 1, 14 + random.nextInt(9));
		for (int tries = 0; stacks.size() < want && tries < want * 4; tries++) {
			ItemStack stack = pick(INGREDIENTS, random);
			if (!stack.isEmpty() && had.add(stack.getItem())) {
				stacks.add(stack);
			}
		}

		Item bowl = item("minecraft:bowl");
		if (bowl != null) {
			stacks.add(new ItemStack(bowl, 4 + random.nextInt(9)));
		}

		lay(cabinet, stacks, tidy, random);
	}

	/**
	 * The stacks into the container: a tidy one's from the first slot on, alike together, sorted; else wherever there's a
	 * free slot.
	 */
	private static void lay(net.minecraft.world.Container chest, List<ItemStack> stacks, boolean tidy, RandomSource random) {
		if (tidy) {
			stacks = merged(stacks);
			stacks.sort(java.util.Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
			int slot = 0;
			for (ItemStack stack : stacks) {
				while (slot < chest.getContainerSize() && !chest.getItem(slot).isEmpty()) {
					slot++;
				}

				if (slot >= chest.getContainerSize()) {
					return;
				}

				chest.setItem(slot++, stack);
			}

			return;
		}

		for (ItemStack stack : stacks) {
			put(chest, stack, random);
		}
	}

	/** Like stacks put together (up to a full stack each). */
	private static List<ItemStack> merged(List<ItemStack> stacks) {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack stack : stacks) {
			ItemStack rest = stack.copy();
			for (ItemStack into : out) {
				if (ItemStack.isSameItemSameComponents(into, rest) && into.getCount() < into.getMaxStackSize()) {
					int moved = Math.min(rest.getCount(), into.getMaxStackSize() - into.getCount());
					into.grow(moved);
					rest.shrink(moved);
				}

				if (rest.isEmpty()) {
					break;
				}
			}

			if (!rest.isEmpty()) {
				out.add(rest);
			}
		}

		return out;
	}

	/** A household's kinds of things, by how much of each it keeps (the pools, in order: food, pantry, building, ore, farming, odds, treasure). */
	private static final double[][] HOUSEHOLDS = {
		// a farmer's: food and the fields
		{4, 3, 1, 1, 4, 1, 0.6},
		// a builder's: stone and wood
		{2, 1, 5, 1.5, 1, 1.5, 0.6},
		// a smith's: ore, coal, ingots
		{2, 1, 2, 5, 0.5, 1.5, 1},
		// a cook's: the pantry
		{4, 5, 1, 1, 2, 1, 0.6},
		// a hunter's or fisher's: meat and fish, arrows and string
		{5, 2, 1, 1, 1.5, 3, 0.6},
		// a well-off one's: a bit of everything, more of the good stuff
		{3, 2, 2, 2, 1.5, 2, 2.5}};

	/**
	 * A household's chests: each 9 to 18 stacks, by a kind of household picked for it, kept as its people keep things (the
	 * user, 2026-11-06: "depending on the personality they could be organized or not"): a tidy household (its tidiness from
	 * 0.6) keeps one kind of thing a chest (food, building stuff, ore...), alike stacked together, sorted, its valuables in
	 * one chest of their own; a middling one's chests each lean to a kind; a messy one's (under 0.35) a jumble, anywhere.
	 * Its kitchen cabinets hold its pantry ({@link #cabinet}).
	 */
	static void household(net.minecraft.server.level.ServerLevel level, List<? extends net.minecraft.world.Container> chests,
		List<? extends net.minecraft.world.Container> cabinets, double tidiness, RandomSource random) {
		double[] mix = HOUSEHOLDS[random.nextInt(HOUSEHOLDS.length)];
		boolean tidy = tidiness >= 0.6;
		boolean messy = tidiness < 0.35;
		for (net.minecraft.world.Container cabinet : cabinets) {
			cabinet(cabinet, !messy, random);
		}

		// (Which kinds it keeps most of, for a tidy one's chests: one kind each, round them.)
		List<Integer> kinds = new ArrayList<>();
		for (int k = 0; k < 6; k++) {
			if (mix[k] >= 1.0) {
				kinds.add(k);
			}
		}

		kinds.sort(java.util.Comparator.comparingDouble((Integer k) -> -mix[k]));
		for (int c = 0; c < chests.size(); c++) {
			net.minecraft.world.Container chest = chests.get(c);
			int stacks = Math.min(chest.getContainerSize() - 2, 9 + random.nextInt(10));
			double[] here = mix.clone();
			here[6] = 0;
			if (tidy && !kinds.isEmpty()) {
				int kind = kinds.get(c % kinds.size());
				java.util.Arrays.fill(here, 0);
				here[kind] = 1;
			} else if (!messy && !kinds.isEmpty()) {
				here[kinds.get(random.nextInt(kinds.size()))] *= 4;
			}

			List<ItemStack> laid = new ArrayList<>();
			for (int n = 0; n < stacks; n++) {
				ItemStack stack = pickFrom(here, random);
				if (!stack.isEmpty()) {
					laid.add(stack);
				}
			}

			lay(chest, laid, tidy, random);
		}

		if (chests.isEmpty()) {
			return;
		}

		// The everyday things every household keeps by it, as a player keeps them at home: planks, cobblestone, sticks,
		// torches, coal (found by the user: a town just made, half its people off to the store at once for a few planks or
		// some cobblestone their own chests didn't have).
		for (ItemStack basic : List.of(new ItemStack(Items.OAK_PLANKS, 16 + random.nextInt(25)), new ItemStack(Items.COBBLESTONE, 16 + random.nextInt(33)),
			new ItemStack(Items.STICK, 8 + random.nextInt(17)), new ItemStack(Items.TORCH, 8 + random.nextInt(9)), new ItemStack(Items.COAL, 6 + random.nextInt(11)))) {
			put(chests.get(tidy ? 0 : random.nextInt(chests.size())), basic, random);
		}

		// Its own valuables, put away somewhere in the house: a stack or two of them, and its purse (its money). A tidy
		// household's all in one chest (the last), together.

		List<ItemStack> valuables = new ArrayList<>();
		int treasures = 1 + random.nextInt(mix[6] > 2 ? 3 : 2);
		for (int i = 0; i < treasures; i++) {
			valuables.add(pick(TREASURE, random));
		}

		valuables.add(com.dinglebob.drifter.api.DrifterApi.money(6 + random.nextInt(mix[6] > 2 ? 90 : 40)));
		valuables.removeIf(ItemStack::isEmpty);
		if (tidy) {
			lay(chests.get(chests.size() - 1), valuables, true, random);
		} else {
			for (ItemStack stack : valuables) {
				put(chests.get(random.nextInt(chests.size())), stack, random);
			}
		}
	}

	/** One stack from a pool picked by {@code mix} (the pantry's plain food if Farmer's Delight isn't there). */
	private static ItemStack pickFrom(double[] mix, RandomSource random) {
		double total = 0;
		for (double w : mix) {
			total += w;
		}

		if (total <= 0) {
			return ItemStack.EMPTY;
		}

		double roll = random.nextDouble() * total;
		int pool = 0;
		while (pool < mix.length - 1 && (roll -= mix[pool]) > 0) {
			pool++;
		}

		ItemStack stack = pick(POOLS.get(pool), random);
		return stack.isEmpty() ? pick(FOOD, random) : stack;
	}

	/** One stack into a free slot of it (at random), if there's room. */
	private static void put(net.minecraft.world.Container chest, ItemStack stack, RandomSource random) {
		if (stack.isEmpty()) {
			return;
		}

		List<Integer> free = new ArrayList<>();
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			if (chest.getItem(slot).isEmpty()) {
				free.add(slot);
			}
		}

		if (!free.isEmpty()) {
			chest.setItem(free.get(random.nextInt(free.size())), stack);
		}
	}

	/** One of the town store's chests, by what it's for (0 wood, 1 stone, 2 ore and coal, 3 food, then the rest a bit of everything): full-ish. */
	static void store(ChestBlockEntity chest, int which, RandomSource random) {
		// (Never its valuables: those are its households', at home.)
		double[] mix = switch (which) {
			case 0, 1 -> new double[] {0, 0, 6, 0.5, 0.5, 0.5, 0};
			case 2 -> new double[] {0, 0, 0.5, 6, 0, 1, 0};
			case 3 -> new double[] {5, 4, 0, 0, 2, 0, 0};
			case 4 -> new double[] {1, 1, 1, 1, 4, 1, 0};
			default -> new double[] {2, 2, 2, 2, 2, 3, 0};
		};
		fill(chest, mix, 14 + random.nextInt(9), random);
	}

	private static final List<List<Entry>> POOLS = List.of(FOOD, PANTRY, BUILDING, ORE, FARMING, ODDS, TREASURE);

	/** {@code stacks} stacks into the chest's empty slots, at random, each from a pool picked by {@code mix}. */
	private static void fill(net.minecraft.world.Container chest, double[] mix, int stacks, RandomSource random) {
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
