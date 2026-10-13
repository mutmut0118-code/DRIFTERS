package com.dinglebob.drifter.hands;

import java.util.function.Predicate;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Tool choice and throwaway building blocks for digging, bridging and pillaring. */
public final class Tools {
	/** MineTask's pause after each full break, counted into dig time estimates. */
	private static final float BREAK_DELAY_SECONDS = 0.25F;

	private Tools() {
	}

	/** Break progress per tick if this stack were in hand (the player formula, minus held-only enchantment bonuses). */
	public static float destroyProgressWith(DrifterEntity drifter, ItemStack stack, BlockState state, BlockGetter level, BlockPos pos) {
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0F) {
			return 0.0F;
		}

		float speed = stack.getDestroySpeed(state);
		boolean correct = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
		if (!drifter.onGround()) {
			speed /= 5.0F;
		}

		return speed / Math.max(hardness, 1.0E-4F) / (correct ? 30 : 100);
	}

	/** The main-inventory slot whose item breaks this block fastest, or -1 if bare hands are as good as anything. */
	public static int bestToolSlot(DrifterEntity drifter, BlockState state, BlockGetter level, BlockPos pos) {
		DrifterInventory inventory = drifter.getInventory();
		float best = destroyProgressWith(drifter, ItemStack.EMPTY, state, level, pos);
		int bestSlot = -1;
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || stack.getItem() instanceof BlockItem) {
				continue;
			}

			float progress = destroyProgressWith(drifter, stack, state, level, pos);
			if (progress > best * 1.01F) {
				best = progress;
				bestSlot = slot;
			}
		}

		return bestSlot;
	}

	/** Seconds to break this block with the best tool it carries, or infinity if it can't. */
	public static float digSeconds(DrifterEntity drifter, BlockState state, BlockGetter level, BlockPos pos) {
		int slot = bestToolSlot(drifter, state, level, pos);
		ItemStack tool = slot < 0 ? ItemStack.EMPTY : drifter.getInventory().getItem(slot);
		float progress = destroyProgressWith(drifter, tool, state, level, pos);
		if (progress <= 0.0F) {
			return Float.POSITIVE_INFINITY;
		}

		return progress >= 1.0F ? 0.05F : (float)Math.ceil(1.0F / progress) / 20.0F + BREAK_DELAY_SECONDS;
	}

	/**
	 * Cheap, plain full blocks a player happily throws away to bridge or pillar (section 10.18 junk policy):
	 * dirt, cobblestone and the like. Never valuables, ores, logs or blocks with a use.
	 */
	public static final Predicate<ItemStack> BUILDING_BLOCK = stack -> {
		if (!(stack.getItem() instanceof BlockItem blockItem)) {
			return false;
		}

		Block block = blockItem.getBlock();
		if (stack.is(Items.DIRT) || stack.is(Items.COARSE_DIRT) || stack.is(Items.COBBLESTONE) || stack.is(Items.COBBLED_DEEPSLATE)
			|| stack.is(Items.NETHERRACK) || stack.is(Items.STONE) || stack.is(Items.ANDESITE) || stack.is(Items.DIORITE)
			|| stack.is(Items.GRANITE) || stack.is(Items.TUFF) || stack.is(Items.DEEPSLATE) || stack.is(Items.BLACKSTONE)
			|| stack.is(Items.END_STONE) || stack.is(Items.SANDSTONE)) {
			return true;
		}

		return block.defaultBlockState().is(BlockTags.PLANKS);
	};

	public static int buildingBlockCount(DrifterEntity drifter) {
		return drifter.getInventory().count(BUILDING_BLOCK);
	}
}
