package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dinglebob.drifter.perception.ExplosionHearing;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Explosions carry 64 blocks for Drifter hearing (DESIGN.md section 5); vanilla game events stop at 16. */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {
	@Shadow
	@Final
	private Level level;

	@Shadow
	public abstract Vec3 center();

	@Inject(method = "finalizeExplosion", at = @At("HEAD"))
	private void drifter$hearExplosion(boolean spawnParticles, CallbackInfo ci) {
		if (this.level instanceof ServerLevel serverLevel) {
			ExplosionHearing.onExplosion(serverLevel, this.center());
		}
	}
}
