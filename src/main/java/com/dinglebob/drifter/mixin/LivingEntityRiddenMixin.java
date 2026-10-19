package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.skills.DrifterRiding;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;

/** A horse a Drifter steers moves by the Drifter's keys, as a ridden horse moves by a player's (DrifterRiding). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityRiddenMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void drifter$riddenByDrifter(Vec3 input, CallbackInfo ci) {
		if (!DrifterRiding.riding() && (Object)this instanceof AbstractHorse horse && horse.isAlive()
			&& horse.getControllingPassenger() instanceof DrifterEntity rider) {
			ci.cancel();
			DrifterRiding.travel(horse, rider);
		}
	}
}
