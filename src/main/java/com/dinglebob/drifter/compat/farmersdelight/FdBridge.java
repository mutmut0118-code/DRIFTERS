package com.dinglebob.drifter.compat.farmersdelight;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.compat.FarmersDelight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;
import vectorwing.farmersdelight.refabricated.inventory.ItemStackHandler;

/**
 * The part of the Farmer's Delight support that uses the mod's own classes ({@link FarmersDelight} is the rest). Only
 * loaded when the mod is installed: everything reaches it through {@link FarmersDelight}, which checks first.
 */
public final class FdBridge {
	private FdBridge() {
	}

	private static RecipeManager potIndexedFor;
	private static List<FarmersDelight.PotRecipe> potRecipes = List.of();
	private static RecipeManager cutIndexedFor;
	private static List<FarmersDelight.CutRecipe> cutRecipes = List.of();

	/** The cooking pot recipes, as the planner reads them (kept until the recipes reload). */
	public static synchronized List<FarmersDelight.PotRecipe> potRecipes(Level level) {
		RecipeManager manager = level.getRecipeManager();
		if (manager != potIndexedFor) {
			List<FarmersDelight.PotRecipe> list = new ArrayList<>();
			for (RecipeHolder<CookingPotRecipe> holder : manager.getAllRecipesFor(ModRecipeTypes.COOKING.get())) {
				CookingPotRecipe recipe = holder.value();
				ItemStack result = recipe.getResultItem(level.registryAccess());
				List<Ingredient> inputs = recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
				if (!result.isEmpty() && !inputs.isEmpty() && inputs.size() <= FarmersDelight.POT_INPUTS) {
					list.add(new FarmersDelight.PotRecipe(inputs, result.copy(), recipe.getOutputContainer().copy(), recipe.getCookTime()));
				}
			}

			potRecipes = List.copyOf(list);
			potIndexedFor = manager;
		}

		return potRecipes;
	}

	/** The cutting board recipes (only the results that always come off; a chance of more is a bonus). */
	public static synchronized List<FarmersDelight.CutRecipe> cutRecipes(Level level) {
		RecipeManager manager = level.getRecipeManager();
		if (manager != cutIndexedFor) {
			List<FarmersDelight.CutRecipe> list = new ArrayList<>();
			for (RecipeHolder<CuttingBoardRecipe> holder : manager.getAllRecipesFor(ModRecipeTypes.CUTTING.get())) {
				CuttingBoardRecipe recipe = holder.value();
				List<ItemStack> sure = new ArrayList<>();
				recipe.getRollableResults().forEach(result -> {
					if (result.chance() >= 1.0F && !result.stack().isEmpty()) {
						sure.add(result.stack().copy());
					}
				});
				if (!sure.isEmpty() && !recipe.getIngredients().isEmpty()) {
					list.add(new FarmersDelight.CutRecipe(recipe.getIngredients().get(0), recipe.getTool(), List.copyOf(sure)));
				}
			}

			cutRecipes = List.copyOf(list);
			cutIndexedFor = manager;
		}

		return cutRecipes;
	}

	@Nullable
	public static Container pot(Level level, BlockPos pos) {
		BlockEntity entity = level.getBlockEntity(pos);
		return entity instanceof CookingPotBlockEntity pot ? new PotWindow(pot) : null;
	}

	public static ItemStack onBoard(Level level, BlockPos pos) {
		BlockEntity entity = level.getBlockEntity(pos);
		return entity instanceof CuttingBoardBlockEntity board ? board.getStoredItem() : ItemStack.EMPTY;
	}

	public static ItemStack inSkillet(Level level, BlockPos pos) {
		BlockEntity entity = level.getBlockEntity(pos);
		return entity instanceof SkilletBlockEntity skillet ? skillet.getStoredStack() : ItemStack.EMPTY;
	}

	/** The pot's window: its nine slots as a container (the same stacks, so a change shows in the pot at once). */
	private record PotWindow(CookingPotBlockEntity pot) implements Container {
		private ItemStackHandler slots() {
			return this.pot.getInventory();
		}

		@Override
		public int getContainerSize() {
			return this.slots().getSlotCount();
		}

		@Override
		public boolean isEmpty() {
			for (int slot = 0; slot < this.getContainerSize(); slot++) {
				if (!this.getItem(slot).isEmpty()) {
					return false;
				}
			}

			return true;
		}

		@Override
		public ItemStack getItem(int slot) {
			return this.slots().getStackInSlot(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int amount) {
			ItemStack stack = this.getItem(slot);
			ItemStack taken = stack.split(amount);
			this.setChanged();
			return taken;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			ItemStack stack = this.getItem(slot);
			this.slots().setStackInSlot(slot, ItemStack.EMPTY);
			return stack;
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			this.slots().setStackInSlot(slot, stack);
		}

		/** The meal's own slot and the served slot only take what the pot puts there (the window's rule). */
		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return slot < FarmersDelight.POT_INPUTS || slot == FarmersDelight.POT_CONTAINER;
		}

		@Override
		public void setChanged() {
			this.pot.setChanged();
			Level level = this.pot.getLevel();
			if (level != null) {
				level.sendBlockUpdated(this.pot.getBlockPos(), this.pot.getBlockState(), this.pot.getBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		@Override
		public boolean stillValid(Player player) {
			return !this.pot.isRemoved();
		}

		@Override
		public void clearContent() {
			for (int slot = 0; slot < this.getContainerSize(); slot++) {
				this.slots().setStackInSlot(slot, ItemStack.EMPTY);
			}
		}
	}
}
