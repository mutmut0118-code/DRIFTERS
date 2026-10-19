package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.nav.WorldCopy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** A block change marks its section in the pathfinder's world copy, to be copied again at the end of the tick. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
	@Shadow
	@Final
	Level level;

	@Inject(method = "setBlockState", at = @At("HEAD"))
	private void drifter$markCopy(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
		WorldCopy.changed(this.level, pos);
	}
}
