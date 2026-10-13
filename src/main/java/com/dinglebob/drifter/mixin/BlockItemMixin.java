package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.world.PlayerPlacedBlocks;

import net.fabricmc.fabric.api.entity.FakePlayer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Records blocks placed by real players (not fake players, so not Drifters) for {@link PlayerPlacedBlocks}. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(method = "placeBlock", at = @At("RETURN"))
	private void drifter$recordPlayerPlaced(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && context.getPlayer() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
			PlayerPlacedBlocks.recordPlaced(context.getLevel(), context.getClickedPos());
		}
	}
}
