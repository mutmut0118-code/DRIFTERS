package com.dinglebob.drifter.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dinglebob.drifter.perception.ExplosionHearing;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Explosions carry 64 blocks for Drifter hearing (DESIGN.md section 5); vanilla game events stop at 16. And a house it blows
 * a hole in is mended ({@link com.dinglebob.drifter.building.HouseDamage}), seen before the blocks go.
 */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {
	@Shadow
	@Final
	private Level level;

	@Shadow
	public abstract Vec3 center();

	@Shadow
	public abstract List<BlockPos> getToBlow();

	@Shadow
	public abstract Entity getDirectSourceEntity();

	@Shadow
	public abstract boolean interactsWithBlocks();

	@Inject(method = "finalizeExplosion", at = @At("HEAD"))
	private void drifter$hearExplosion(boolean spawnParticles, CallbackInfo ci) {
		if (this.level instanceof ServerLevel serverLevel) {
			ExplosionHearing.onExplosion(serverLevel, this.center());
			if (this.interactsWithBlocks()) {
				try {
					com.dinglebob.drifter.building.HouseDamage.exploded(serverLevel, this.center(), this.getToBlow(), this.getDirectSourceEntity());
				} catch (RuntimeException e) {
					com.dinglebob.drifter.Drifter.LOGGER.error("[Drifter] couldn't look at what an explosion broke", e);
				}
			}
		}
	}
}
