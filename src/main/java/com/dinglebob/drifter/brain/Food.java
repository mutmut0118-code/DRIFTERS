package com.dinglebob.drifter.brain;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Food choices (DESIGN.md section 10.5, "Eating policy"). */
public final class Food {
	/** Eats when food drops to this (a habit fingerprint later, 12 to 17). TODO(milestone 7): per-Drifter. */
	public static final int EAT_THRESHOLD = 14;
	/** Below this, it eats anything, rotten flesh included. */
	public static final int DESPERATE = 4;

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

	/** Hunger points of food it carries (what it would eat). */
	public static int carriedNutrition(DrifterEntity drifter) {
		int total = 0;
		DrifterInventory inventory = drifter.getInventory();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			FoodProperties food = of(stack);
			if (food != null && !isBad(stack)) {
				total += food.nutrition() * stack.getCount();
			}
		}

		return total;
	}

	/**
	 * The food to eat now: the one that fills without waste (no steak at 19 food), cooked before raw, bad food only when
	 * desperate. Null if it has nothing it would eat.
	 */
	@Nullable
	public static ItemStack choose(DrifterEntity drifter) {
		int food = drifter.getFoodData().getFoodLevel();
		int room = 20 - food;
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

			int nutrition = properties.nutrition();
			double waste = Math.max(0, nutrition - room);
			double score = Math.min(nutrition, room) - waste * 1.5 + properties.saturation() * 0.5 - (bad ? 20 : 0);
			if (score > bestScore) {
				bestScore = score;
				best = stack;
			}
		}

		return best;
	}
}
