package com.dinglebob.drifter.brain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.hands.Tools;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;

/**
 * What it keeps on it and what goes in the chest (DESIGN.md section 10.18, "Emergency kit" and "Going home"). It keeps
 * its working kit: its best tool of each kind and its weapon, a shield, buckets, a crafting table, food, torches, a
 * stack of building blocks, some wood, sticks and coal. Everything else (ores, ingots, spare tools and armor, mob
 * drops, the extra stone) goes home.
 * TODO(milestone 8 later steps): junk policy (throwing diorite and the like), sorting chests by category.
 */
public final class Storage {
	public static final int KEEP_FOOD = 16;
	public static final int KEEP_TORCHES = 32;
	public static final int KEEP_BUILDING_BLOCKS = 64;
	public static final int KEEP_WOOD = 16;
	public static final int KEEP_STICKS = 16;
	public static final int KEEP_COAL = 16;
	/** Emeralds and coins it keeps on it (the rest goes in the chest). */
	public static final int KEEP_MONEY = 64;
	/** Free main-inventory slots it likes to have; fewer than this and storing gets urgent. */
	public static final int FULL_FREE_SLOTS = 4;

	private Storage() {
	}

	/** Gravel and sand it keeps beyond its building blocks (for... not much); more is junk. */
	public static final int KEEP_LOOSE = 16;

	/**
	 * Junk (section 10.18, "Junk policy: most throw diorite, granite, andesite, tuff and extra gravel. Some keep one stack
	 * of dirt or cobblestone for bridging. Hoarders keep everything"): stacks it tosses out when it needs the room, and
	 * stops picking up when it's nearly full. Slot -> how many to toss.
	 */
	public static Map<Integer, Integer> junk(DrifterEntity drifter) {
		Map<Integer, Integer> toss = new HashMap<>();
		if (drifter.getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.HOARDER) {
			return toss;
		}

		DrifterInventory inventory = drifter.getInventory();
		boolean keepsDirt = drifter.getPersonality().habits().keepsDirtStack;
		int cobble = inventory.count(stack -> stack.is(Items.COBBLESTONE) || stack.is(Items.COBBLED_DEEPSLATE));
		Map<String, Integer> kept = new HashMap<>();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			String kind;
			int limit;
			if (isWorthlessStone(stack) && !houseStone(drifter, stack) || stack.is(Items.POISONOUS_POTATO)) {
				kind = "worthless";
				limit = 0;
			} else if ((stack.is(Items.GRAVEL) || stack.is(Items.SAND) || stack.is(Items.RED_SAND)) && !houseStone(drifter, stack)) {
				kind = "loose";
				limit = KEEP_LOOSE;
			} else if (stack.is(Items.DIRT) || stack.is(Items.COARSE_DIRT) || stack.is(Items.ROOTED_DIRT)) {
				// One stack of dirt if that's its habit, or while it has no cobblestone to build with.
				kind = "dirt";
				limit = keepsDirt || cobble < KEEP_BUILDING_BLOCKS ? KEEP_BUILDING_BLOCKS : 0;
			} else if (stack.is(Items.COBBLESTONE) || stack.is(Items.COBBLED_DEEPSLATE)) {
				// Two stacks for building and plugging holes; more is dead weight down a mine (found in testing: deep trips
				// came back "pockets full" of cobbled deepslate without the ore).
				kind = "cobble";
				limit = KEEP_BUILDING_BLOCKS * 2;
			} else if (stack.is(Items.ROTTEN_FLESH) && Food.carriedNutrition(drifter) >= 12) {
				kind = "flesh";
				limit = 0;
			} else {
				continue;
			}

			int already = kept.getOrDefault(kind, 0);
			int keep = Math.max(0, Math.min(stack.getCount(), limit - already));
			kept.put(kind, already + keep);
			if (stack.getCount() > keep) {
				toss.put(slot, stack.getCount() - keep);
			}
		}

		return toss;
	}

	/** Would it rather not pick this up when it's short of room? */
	public static boolean isJunk(DrifterEntity drifter, ItemStack stack) {
		return drifter.getPersonality().archetype() != com.dinglebob.drifter.personality.Archetype.HOARDER
			&& (isWorthlessStone(stack) && !houseStone(drifter, stack) || stack.is(Items.POISONOUS_POTATO) || stack.is(Items.GRAVEL) || stack.is(Items.ROTTEN_FLESH));
	}

	/**
	 * A stone its house is being built of (andesite walls, tuff, the sand for sandstone: HousePalette), which isn't junk to
	 * it then (found in testing: a builder threw out the andesite for its walls, then went looking for more).
	 */
	private static boolean houseStone(DrifterEntity drifter, ItemStack stack) {
		Home home = drifter.getDrifterBrain().home();
		if (home == null) {
			return false;
		}

		for (Map<String, String> woods : home.project == null ? List.of(home.planWoods) : List.of(home.planWoods, home.project.woods)) {
			for (String key : new String[] {com.dinglebob.drifter.building.plan.HousePalette.STONE_KEY, com.dinglebob.drifter.building.plan.HousePalette.TRIM_KEY}) {
				var stone = com.dinglebob.drifter.building.plan.HousePalette.stoneNamed(woods.get(key));
				if (stone != null && (stack.is(stone.raw()) || stone.madeFrom() != null && stack.is(stone.madeFrom().asItem()))) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean isWorthlessStone(ItemStack stack) {
		return stack.is(Items.ANDESITE) || stack.is(Items.DIORITE) || stack.is(Items.GRANITE) || stack.is(Items.TUFF) || stack.is(Items.CALCITE);
	}

	/** For each main-inventory slot: true if that stack (or part of it) should go into storage. */
	public static Map<Integer, Integer> toDeposit(DrifterEntity drifter) {
		DrifterInventory inventory = drifter.getInventory();
		Map<String, Integer> kept = new HashMap<>();
		Map<String, Integer> bestToolSlot = new HashMap<>();
		// First pass: the best tool of each kind stays.
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			String kind = toolKind(stack);
			if (kind == null) {
				continue;
			}

			Integer current = bestToolSlot.get(kind);
			if (current == null || toolScore(stack) > toolScore(inventory.getItem(current))) {
				bestToolSlot.put(kind, slot);
			}
		}

		// Section 10.17: it keeps a spare pickaxe on it too (the next best one).
		Integer bestPickaxe = bestToolSlot.get("pickaxe");
		int sparePickaxe = -1;
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(ItemTags.PICKAXES) && bestPickaxe != null && slot != bestPickaxe
				&& (sparePickaxe < 0 || toolScore(stack) > toolScore(inventory.getItem(sparePickaxe)))) {
				sparePickaxe = slot;
			}
		}

		Map<Integer, Integer> deposit = new HashMap<>();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			String kind = toolKind(stack);
			if (kind != null) {
				if (bestToolSlot.get(kind) != slot && slot != sparePickaxe) {
					deposit.put(slot, stack.getCount());
				}

				continue;
			}

			String category = category(drifter, stack);
			int limit = keepLimit(category);
			int already = kept.getOrDefault(category, 0);
			int keep = Math.max(0, Math.min(stack.getCount(), limit - already));
			kept.put(category, already + keep);
			if (stack.getCount() > keep) {
				deposit.put(slot, stack.getCount() - keep);
			}
		}

		return deposit;
	}

	/**
	 * What it could give away (slot -> how many): what it would put away anyway, and of the common things it has plenty
	 * of (logs, planks, cobblestone, coal, food) all but a working stack (found by the user: early on it had nothing
	 * "spare" and never paid anyone back). What it pays a player back from, and trades with other Drifters.
	 */
	public static Map<Integer, Integer> spare(DrifterEntity drifter) {
		Map<Integer, Integer> spare = new HashMap<>(toDeposit(drifter));
		DrifterInventory inventory = drifter.getInventory();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			int keep = stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS) || stack.is(Items.COBBLESTONE) || stack.is(Items.COAL) ? 16
				: stack.has(net.minecraft.core.component.DataComponents.FOOD) ? 8 : -1;
			if (keep >= 0) {
				int total = inventory.count(other -> other.is(stack.getItem()));
				int extra = Math.min(stack.getCount(), total - keep);
				if (extra > 0) {
					spare.merge(slot, extra, Math::max);
				}
			}
		}

		return spare;
	}

	/** How many items it would put away right now. */
	public static int depositCount(DrifterEntity drifter) {
		return toDeposit(drifter).values().stream().mapToInt(Integer::intValue).sum();
	}

	public static int freeSlots(DrifterEntity drifter) {
		int free = 0;
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			if (drifter.getInventory().getItem(slot).isEmpty()) {
				free++;
			}
		}

		return free;
	}

	private static String toolKind(ItemStack stack) {
		if (stack.is(ItemTags.PICKAXES)) {
			return "pickaxe";
		} else if (stack.is(ItemTags.AXES)) {
			return "axe";
		} else if (stack.is(ItemTags.SHOVELS)) {
			return "shovel";
		} else if (Weapons.isWeapon(stack)) {
			return "sword";
		} else if (stack.is(ItemTags.HOES)) {
			return "hoe";
		} else if (com.dinglebob.drifter.hands.Shields.isShield(stack)) {
			return "shield";
		} else if (stack.is(Items.SHEARS)) {
			return "shears";
		} else if (stack.is(Items.FLINT_AND_STEEL)) {
			return "flint and steel";
		} else if (com.dinglebob.drifter.hands.Shields.isBow(stack)) {
			return "bow";
		} else if (stack.is(Items.FISHING_ROD)) {
			return "fishing rod";
		} else if (com.dinglebob.drifter.executor.task.BoatTask.isBoat(stack)) {
			return "boat";
		}

		return null;
	}

	private static double toolScore(ItemStack stack) {
		// (Shields by the wear they have left; bows, a modded unique one first.)
		if (com.dinglebob.drifter.hands.Shields.isShield(stack)) {
			return com.dinglebob.drifter.hands.Shields.score(stack) / 100.0;
		}

		if (com.dinglebob.drifter.hands.Shields.isBow(stack)) {
			return com.dinglebob.drifter.hands.Shields.bowScore(stack);
		}

		double tier = stack.getItem() instanceof TieredItem tiered ? tiered.getTier().getSpeed() + tiered.getTier().getAttackDamageBonus() : 1.0;
		double durability = stack.isDamageableItem() ? 1.0 - stack.getDamageValue() / (double)stack.getMaxDamage() : 1.0;
		return tier + durability * 0.5 + (stack.isEnchanted() ? 3.0 : 0.0);
	}

	private static String category(DrifterEntity drifter, ItemStack stack) {
		// A bed it hasn't put in its hut yet stays on it (found in testing: it went into the chest and never into the hut).
		if (stack.is(net.minecraft.tags.ItemTags.BEDS)) {
			return "bed";
		}

		if (stack.is(Items.EMERALD) || com.dinglebob.drifter.trade.Money.isCoin(stack)) {
			// Money it keeps on it, as a player keeps a purse: for a villager or another Drifter met on the way (milestone 22).
			return "money";
		}

		if (stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET)) {
			return "bucket";
		} else if (stack.is(Items.CRAFTING_TABLE)) {
			return "table";
		} else if (stack.is(ItemTags.BANNERS) || stack.is(Items.SMOKER) || stack.is(Items.BLAST_FURNACE)) {
			// The group's banner, a smoker or blast furnace for the hut: kept until it's put up (it's what it made them for).
			return "to put up";
		} else if (stack.is(Items.ENCHANTING_TABLE)) {
			// Kept until it's set up near home (milestone 8 step 7).
			return "enchanting table";
		} else if (stack.is(Items.BOOKSHELF)) {
			// For the ring round its enchanting table.
			return "bookshelf";
		} else if (stack.is(Items.GLASS) || stack.is(Items.GLASS_PANE)) {
			// For the house's windows.
			return "glass";
		} else if (stack.is(Items.WHEAT_SEEDS)) {
			// The farm's seeds (found in testing: they went in the chest and the farm stood empty).
			return "seeds";
		} else if (stack.is(Items.WHEAT)) {
			// For leading and breeding the pen's animals (and bread).
			return "wheat";
		} else if (stack.is(Items.LAPIS_LAZULI)) {
			// What it enchants with: on it, for when it has the levels.
			return "lapis";
		} else if (stack.is(Items.FURNACE)) {
			// Kept until it's set up at home (the hut's furnace); a spare one goes in the chest.
			return "furnace";
		} else if (stack.is(Items.TORCH)) {
			return "torch";
		} else if (stack.is(Items.ANCIENT_DEBRIS) || stack.is(Items.NETHERITE_SCRAP) || stack.is(Items.NETHERITE_INGOT)
			|| stack.is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)) {
			// Its netherite on the way to an upgrade (milestone 18): with it, for the smithing table.
			return "netherite";
		} else if (com.dinglebob.drifter.skills.Potions.isAnyPotion(stack) || stack.is(Items.GLASS_BOTTLE) || stack.is(Items.NETHER_WART)
			|| stack.is(Items.BLAZE_POWDER) || stack.is(Items.MAGMA_CREAM) || stack.is(Items.GLISTERING_MELON_SLICE) || stack.is(Items.GOLDEN_CARROT)) {
			// Potions and what it brews with (milestone 18).
			return "brewing";
		} else if (Food.of(stack) != null && !Food.isBad(stack)) {
			return "food";
		} else if (Tools.BUILDING_BLOCK.test(stack)) {
			return "building";
		} else if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS)) {
			return "wood";
		} else if (stack.is(Items.STICK)) {
			return "stick";
		} else if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
			return "coal";
		} else if (stack.getItem() instanceof ArmorItem) {
			// Worn armor isn't in the main inventory; spare pieces go home.
			return "spare armor";
		}

		return "other";
	}

	private static int keepLimit(String category) {
		return switch (category) {
			case "bucket" -> 2;
			case "table", "furnace", "bed", "enchanting table" -> 1;
			case "to put up" -> 3;
			case "lapis" -> 64;
			case "money" -> KEEP_MONEY;
			case "netherite" -> 64;
			case "brewing" -> 32;
			case "seeds" -> 32;
			case "glass" -> 16;
			case "bookshelf" -> 15;
			case "wheat" -> 16;
			case "torch" -> KEEP_TORCHES;
			case "food" -> KEEP_FOOD;
			case "building" -> KEEP_BUILDING_BLOCKS;
			case "wood" -> KEEP_WOOD;
			case "stick" -> KEEP_STICKS;
			case "coal" -> KEEP_COAL;
			default -> 0;
		};
	}
}
