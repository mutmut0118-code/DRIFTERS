package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A mob spawner wakes for a Drifter as it does for a player, within the spawner's own player range (16 blocks). Without
 * this, blazes never came while it waited by a fortress's spawner (found in testing: ten minutes by it, no blaze), and a
 * dungeon's spawner left a Drifter mining beside it alone, which a player never gets.
 */
@Mixin(BaseSpawner.class)
public abstract class BaseSpawnerMixin {
	@Shadow
	private int requiredPlayerRange;

	@Inject(method = "isNearPlayer", at = @At("RETURN"), cancellable = true)
	private void drifter$nearDrifter(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() || level.isClientSide()) {
			return;
		}

		Vec3 centre = Vec3.atCenterOf(pos);
		double range = this.requiredPlayerRange;
		if (!level.getEntitiesOfClass(DrifterEntity.class, new AABB(centre, centre).inflate(range),
			drifter -> drifter.isAlive() && drifter.distanceToSqr(centre) < range * range).isEmpty()) {
			cir.setReturnValue(true);
		}
	}
}
