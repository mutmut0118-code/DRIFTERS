package com.dinglebob.drifter.compatmixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.world.entity.animal.horse.AbstractHorse;

/**
 * Icy's Better Horses (optional, horse culture task 8): its cart moves any rider on its horse that isn't a player into the
 * cart, or sets it down (its "bench cargo": a mob led on a cart rides in it), so only a player could drive a cart. A Drifter
 * in the saddle of its own horse is its driver, as a player is: the cart leaves it there. Everything else of Icy's cart is
 * untouched. Applied only when Icy's is loaded (CompatMixinPlugin); if this Icy's doesn't have the method, nothing changes.
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.entity.HorseCartEntity", remap = false)
public abstract class IcyCartMixin {
	@Inject(method = "shiftBenchCargo", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void drifter$driverStaysOn(AbstractHorse horse, CallbackInfo ci) {
		if (horse != null && horse.getFirstPassenger() instanceof DrifterEntity) {
			ci.cancel();
		}
	}
}
