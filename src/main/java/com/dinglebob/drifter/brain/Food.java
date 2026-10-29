package com.dinglebob.drifter.brain;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Food choices (DESIGN.md section 10.5, "Eating policy"). */
public final class Food {
	/** Eats when food drops to this: its habit (section 17 habit fingerprint, 12 to 17). */
	public static int eatThreshold(DrifterEntity drifter) {
		// (Warier when dying would cost more: it keeps its food up, as a hardcore player does.)
		return Math.min(17, drifter.getPersonality().habits().eatThreshold + (int)Math.round(4.0 * Stakes.wariness(drifter)));
	}

	/** Below the eat threshold, eating beats routine work (progress scores 0.5). */
	public static final double AT_THRESHOLD_URGENCY = 0.6;
	/** Below this, it eats anything, rotten flesh included. */
	public static final int DESPERATE = 4;
	/**
	 * At or below this it eats raw food rather than wait for the furnace: it can't sprint any more (the player rule:
	 * sprinting needs food above 6). Above it, raw food that cooks waits to be cooked (section 10.5, "only when it
	 * can't cook"; user's rule).
	 */
	public static final int RAW_OK = 6;
	/** Cobblestone (or blackstone, cobbled deepslate) for a furnace. */
	private static final int FURNACE_STONE = 8;

	private Food() {
	}

	@Nullable
	public static FoodProperties of(ItemStack stack) {
		return stack.get(DataComponents.FOOD);
	}

	/** Food it would rather not eat: makes you sick or poisons you. */
	public static boolean isBad(ItemStack stack) {
		return stack.is(Items.ROTTEN_FLESH) || stack.is(Items.SPIDER_EYE) || stack.is(Items.POISONOUS_POTATO) || stack.is(Items.PUFFERFISH)
			|| stack.is(Items.CHICKEN) || stack.is(Items.SUSPICIOUS_STEW);
	}

	/** Raw food that a furnace or smoker turns into better food (beef, porkchop, chicken, potatoes, fish...). */
	public static boolean isCookable(Level level, ItemStack stack) {
		return !stack.isEmpty() && Planner.cookedForm(level, stack.getItem()) != null;
	}

	/**
	 * Hunger points of food it carries (what it would eat). Raw food that cooks counts as what it will be once cooked:
	 * it plans to cook it (the cook goal).
	 */
	public static int carriedNutrition(DrifterEntity drifter) {
		int total = 0;
		DrifterInventory inventory = drifter.getInventory();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			Item cooked = Planner.cookedForm(drifter.level(), stack.getItem());
			ItemStack eaten = cooked == null ? stack : new ItemStack(cooked);
			FoodProperties food = of(eaten);
			if (food != null && !isBad(eaten)) {
				total += food.nutrition() * stack.getCount();
			}
		}

		return total;
	}

	/**
	 * It could get its raw food cooked soon: it knows a furnace or smoker nearby, carries one, or carries the stone to
	 * make one. If not, raw food is "all there is" and it eats it when hungry.
	 */
	public static boolean canCook(DrifterEntity drifter) {
		if (Stations.nearest(drifter, Blocks.FURNACE, Stations.USE_RANGE) != null || Stations.nearest(drifter, Blocks.SMOKER, Stations.USE_RANGE) != null) {
			return true;
		}

		DrifterInventory inventory = drifter.getInventory();
		return inventory.count(stack -> stack.is(Items.FURNACE) || stack.is(Items.SMOKER)) > 0
			|| inventory.count(stack -> stack.is(ItemTags.STONE_CRAFTING_MATERIALS)) >= FURNACE_STONE;
	}

	/**
	 * The food to eat now: the one that fills without waste (no steak at 19 food), cooked before raw, bad food only when
	 * desperate. Raw food that cooks only when it's starving (food 6 or less) or has no way to cook it. Null if it has
	 * nothing it would eat now.
	 */
	@Nullable
	public static ItemStack choose(DrifterEntity drifter) {
		int food = drifter.getFoodData().getFoodLevel();
		int room = 20 - food;
		Level level = drifter.level();
		Boolean rawOk = food <= RAW_OK ? Boolean.TRUE : null;
		ItemStack best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		DrifterInventory inventory = drifter.getInventory();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			FoodProperties properties = of(stack);
			if (properties == null || !drifter.canEat(properties.canAlwaysEat())) {
				continue;
			}

			boolean bad = isBad(stack);
			if (bad && food > DESPERATE) {
				continue;
			}

			boolean raw = isCookable(level, stack);
			if (raw) {
				if (rawOk == null) {
					rawOk = !canCook(drifter);
				}

				if (!rawOk) {
					continue;
				}
			}

			int nutrition = properties.nutrition();
			double waste = Math.max(0, nutrition - room);
			double score = Math.min(nutrition, room) - waste * 1.5 + properties.saturation() * 0.5 - (bad ? 20 : 0) - (raw ? 3 : 0);
			if (score > bestScore) {
				bestScore = score;
				best = stack;
			}
		}

		return best;
	}
}
