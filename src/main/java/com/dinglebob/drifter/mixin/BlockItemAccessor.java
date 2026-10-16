package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** BlockItem#getPlacementState: what an item would place for a click (a torch item: a torch, or a wall torch). */
@Mixin(BlockItem.class)
public interface BlockItemAccessor {
	@Invoker("getPlacementState")
	BlockState drifter$getPlacementState(BlockPlaceContext context);
}
