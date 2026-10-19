package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

/** A saddled horse with a Drifter on it is steered by the Drifter, as it is by a player. */
@Mixin(AbstractHorse.class)
public abstract class AbstractHorseMixin {
	@Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
	private void drifter$drifterRider(CallbackInfoReturnable<LivingEntity> cir) {
		AbstractHorse self = (AbstractHorse)(Object)this;
		if (self.isSaddled() && self.getFirstPassenger() instanceof DrifterEntity drifter) {
			cir.setReturnValue(drifter);
		}
	}
}
