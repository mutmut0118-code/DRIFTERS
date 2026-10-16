package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Sneaking on a ladder or vines holds a Drifter in place, as it does a player (vanilla only lets players do it: found in a
 * Nether test, its route was cut off halfway up twisting vines, it crouched to hold on, slid down and into the lava).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityClimbMixin {
	@Inject(method = "handleOnClimbable", at = @At("RETURN"), cancellable = true)
	private void drifter$holdOnLikeAPlayer(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
		LivingEntity self = (LivingEntity)(Object)this;
		Vec3 result = cir.getReturnValue();
		if (self instanceof DrifterEntity && result.y < 0.0 && self.onClimbable() && self.isSuppressingSlidingDownLadder()
			&& !self.getInBlockState().is(Blocks.SCAFFOLDING)) {
			cir.setReturnValue(new Vec3(result.x, 0.0, result.z));
		}
	}
}
