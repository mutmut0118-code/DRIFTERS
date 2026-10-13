package com.dinglebob.drifter.hands;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Direct container access (DESIGN.md section 2: "Direct container access within reach; play the chest lid
 * open/close animation"). Only containers a player could open: a blocked chest (a block or cat on top) can't be
 * opened, and ender chests are never touched here (section 3: the Drifter's own ender chest comes later).
 */
public final class Containers {
	private Containers() {
	}

	/** The container at this position as a player would see it (a double chest is one 54-slot container), or null. */
	@Nullable
	public static Container get(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof EnderChestBlock) {
			return null;
		}

		if (state.getBlock() instanceof ChestBlock chest) {
			return ChestBlock.getContainer(chest, state, level, pos, false);
		}

		BlockEntity blockEntity = level.getBlockEntity(pos);
		return blockEntity instanceof Container container ? container : null;
	}

	/** Lid and sound when the Drifter opens a container, like a player opening its menu. */
	public static void playOpen(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock) {
			level.blockEvent(pos, state.getBlock(), 1, ChestBlockEntity.getOpenCount(level, pos) + 1);
			playSound(level, pos, SoundEvents.CHEST_OPEN);
		} else if (state.getBlock() instanceof BarrelBlock && state.hasProperty(BarrelBlock.OPEN)) {
			level.setBlock(pos, state.setValue(BarrelBlock.OPEN, true), 3);
			playSound(level, pos, SoundEvents.BARREL_OPEN);
		}
	}

	/**
	 * Lid and sound when the Drifter closes it. Keeps a chest open if real players still have it open.
	 * TODO: trapped chests should also pulse redstone while the Drifter has them open.
	 */
	public static void playClose(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock) {
			int playersStillOpen = ChestBlockEntity.getOpenCount(level, pos);
			level.blockEvent(pos, state.getBlock(), 1, playersStillOpen);
			if (playersStillOpen == 0) {
				playSound(level, pos, SoundEvents.CHEST_CLOSE);
			}
		} else if (state.getBlock() instanceof BarrelBlock && state.hasProperty(BarrelBlock.OPEN)) {
			level.setBlock(pos, state.setValue(BarrelBlock.OPEN, false), 3);
			playSound(level, pos, SoundEvents.BARREL_CLOSE);
		}
	}

	private static void playSound(Level level, BlockPos pos, SoundEvent sound) {
		level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
	}
}
