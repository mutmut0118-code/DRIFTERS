package com.dinglebob.drifter.brain;

import java.util.HashMap;
import java.util.HashSet;
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
			if (isWorthlessStone(stack) || stack.is(Items.POISONOUS_POTATO)) {
				kind = "worthless";
				limit = 0;
			} else if (stack.is(Items.GRAVEL) || stack.is(Items.SAND) || stack.is(Items.RED_SAND)) {
				kind = "loose";
				limit = KEEP_LOOSE;
			} else if (stack.is(Items.DIRT) || stack.is(Items.COARSE_DIRT) || stack.is(Items.ROOTED_DIRT)) {
				// One stack of dirt if that's its habit, or while it has no cobblestone to build with.
				kind = "dirt";
				limit = keepsDirt || cobble < KEEP_BUILDING_BLOCKS ? KEEP_BUILDING_BLOCKS : 0;
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
			&& (isWorthlessStone(stack) || stack.is(Items.POISONOUS_POTATO) || stack.is(Items.GRAVEL) || stack.is(Items.ROTTEN_FLESH));
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

		Map<Integer, Integer> deposit = new HashMap<>();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			String kind = toolKind(stack);
			if (kind != null) {
				if (bestToolSlot.get(kind) != slot) {
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
		} else if (stack.is(ItemTags.SWORDS)) {
			return "sword";
		} else if (stack.is(ItemTags.HOES)) {
			return "hoe";
		} else if (stack.is(Items.SHIELD)) {
			return "shield";
		} else if (stack.is(Items.SHEARS)) {
			return "shears";
		} else if (stack.is(Items.FLINT_AND_STEEL)) {
			return "flint and steel";
		} else if (stack.is(Items.BOW)) {
			return "bow";
		} else if (stack.is(Items.FISHING_ROD)) {
			return "fishing rod";
		}

		return null;
	}

	private static double toolScore(ItemStack stack) {
		double tier = stack.getItem() instanceof TieredItem tiered ? tiered.getTier().getSpeed() + tiered.getTier().getAttackDamageBonus() : 1.0;
		double durability = stack.isDamageableItem() ? 1.0 - stack.getDamageValue() / (double)stack.getMaxDamage() : 1.0;
		return tier + durability * 0.5 + (stack.isEnchanted() ? 3.0 : 0.0);
	}

	private static String category(DrifterEntity drifter, ItemStack stack) {
		if (stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET)) {
			return "bucket";
		} else if (stack.is(Items.CRAFTING_TABLE)) {
			return "table";
		} else if (stack.is(Items.TORCH)) {
			return "torch";
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
			case "table" -> 1;
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
