package com.dinglebob.drifter.hands;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/**
 * Crafting through the game's own recipe manager (DESIGN.md sections 2 and 10.2), so modded recipes work too.
 * The 2x2 inventory grid is used when the recipe fits it; anything bigger needs a crafting table in reach.
 *
 * <p>Ingredients are laid out in the recipe's own grid shape and the recipe is asked whether it matches, then
 * assembled exactly like the crafting menu does (result, plus remaining items such as empty buckets).
 * TODO(milestone 5/7): limit known recipes by the Knowledge trait (section 8).
 */
public final class Crafting {
	public static final int INVENTORY_GRID = 2;
	public static final int TABLE_GRID = 3;

	/** A recipe plus which inventory slot supplies each grid cell (-1 for an empty cell). */
	public record Plan(RecipeHolder<CraftingRecipe> recipe, int width, int height, int[] cellSlots) {
		public boolean needsTable() {
			return !this.recipe.value().canCraftInDimensions(INVENTORY_GRID, INVENTORY_GRID);
		}
	}

	private Crafting() {
	}

	/** Finds a craftable recipe whose result matches, from what is in the main inventory right now. */
	public static Optional<Plan> find(DrifterEntity drifter, Predicate<ItemStack> wantedResult, boolean tableAvailable) {
		Level level = drifter.level();
		int grid = tableAvailable ? TABLE_GRID : INVENTORY_GRID;
		for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
			CraftingRecipe recipe = holder.value();
			if (recipe.isSpecial() || !recipe.canCraftInDimensions(grid, grid)) {
				continue;
			}

			ItemStack result = recipe.getResultItem(level.registryAccess());
			if (result.isEmpty() || !wantedResult.test(result)) {
				continue;
			}

			Optional<Plan> plan = plan(drifter, holder);
			if (plan.isPresent()) {
				return plan;
			}
		}

		return Optional.empty();
	}

	/** Assigns an inventory slot to every ingredient cell, or empty if something is missing. */
	public static Optional<Plan> plan(DrifterEntity drifter, RecipeHolder<CraftingRecipe> holder) {
		CraftingRecipe recipe = holder.value();
		NonNullList<Ingredient> ingredients = recipe.getIngredients();
		if (ingredients.isEmpty()) {
			return Optional.empty();
		}

		int width;
		int height;
		if (recipe instanceof ShapedRecipe shaped) {
			width = shaped.getWidth();
			height = shaped.getHeight();
		} else {
			width = ingredients.size();
			height = 1;
		}

		DrifterInventory inventory = drifter.getInventory();
		int[] available = new int[DrifterInventory.MAIN_SIZE];
		for (int slot = 0; slot < available.length; slot++) {
			available[slot] = inventory.getItem(slot).getCount();
		}

		int[] cellSlots = new int[ingredients.size()];
		for (int cell = 0; cell < ingredients.size(); cell++) {
			Ingredient ingredient = ingredients.get(cell);
			if (ingredient.isEmpty()) {
				cellSlots[cell] = -1;
				continue;
			}

			int found = -1;
			for (int slot = 0; slot < available.length; slot++) {
				if (available[slot] > 0 && ingredient.test(inventory.getItem(slot))) {
					found = slot;
					break;
				}
			}

			if (found < 0) {
				return Optional.empty();
			}

			available[found]--;
			cellSlots[cell] = found;
		}

		return Optional.of(new Plan(holder, width, height, cellSlots));
	}

	/**
	 * Crafts the recipe once from the inventory: takes one item per cell, gives the result and any remaining
	 * items. Anything that doesn't fit is dropped, like a full player inventory.
	 *
	 * @return the crafted result (a copy, for reporting), or empty if the ingredients no longer match
	 */
	public static ItemStack craftOnce(DrifterEntity drifter, RecipeHolder<CraftingRecipe> holder) {
		Optional<Plan> maybePlan = plan(drifter, holder);
		if (maybePlan.isEmpty()) {
			return ItemStack.EMPTY;
		}

		Plan plan = maybePlan.get();
		DrifterInventory inventory = drifter.getInventory();
		List<ItemStack> cells = new ArrayList<>(plan.cellSlots().length);
		for (int slot : plan.cellSlots()) {
			cells.add(slot < 0 ? ItemStack.EMPTY : inventory.getItem(slot).copyWithCount(1));
		}

		Level level = drifter.level();
		CraftingInput input = CraftingInput.of(plan.width(), plan.height(), cells);
		CraftingRecipe recipe = holder.value();
		if (!recipe.matches(input, level)) {
			return ItemStack.EMPTY;
		}

		ItemStack result = recipe.assemble(input, level.registryAccess());
		NonNullList<ItemStack> remaining = recipe.getRemainingItems(input);
		for (int slot : plan.cellSlots()) {
			if (slot >= 0) {
				inventory.getItem(slot).shrink(1);
			}
		}

		ItemStack report = result.copy();
		result.onCraftedBySystem(level);
		give(drifter, result);
		for (ItemStack leftover : remaining) {
			give(drifter, leftover);
		}

		return report;
	}

	private static void give(DrifterEntity drifter, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}

		drifter.getInventory().add(stack);
		if (!stack.isEmpty()) {
			drifter.dropScattered(stack);
		}
	}
}
