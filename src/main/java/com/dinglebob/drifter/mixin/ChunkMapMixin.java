package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.world.DrifterSimulation;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Hybrid offscreen simulation (DESIGN.md section 22): a chunk near a simulated Drifter counts as "near a player" for
 * random ticks, so crops grow, saplings sprout and leaves decay round it as round a player. (Natural mob spawning still
 * needs a real player within 128 blocks: vanilla checks that again when it places a mob.)
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
	@Shadow
	@Final
	ServerLevel level;

	@Inject(method = "anyPlayerCloseEnoughForSpawning", at = @At("HEAD"), cancellable = true)
	private void drifter$nearSimulatedDrifter(ChunkPos chunkPos, CallbackInfoReturnable<Boolean> cir) {
		if (DrifterSimulation.nearSimulated(this.level.dimension(), chunkPos)) {
			cir.setReturnValue(true);
		}
	}
}
