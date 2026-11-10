package com.dinglebob.drifter.compatmixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.world.entity.LivingEntity;

/**
 * Trinkets (optional, R40): its end-of-tick scan of every entity with trinket slots (a copy of each slot, a name built per slot
 * and two new maps, every tick) asks {@code TrinketsApi.getTrinketComponent} first and does nothing when there is none. For a
 * Drifter wearing no trinkets, which Trinkets has already seen empty, that one call answers "none" during the scan's moment
 * at the end of its tick ({@link DrifterEntity#skipTrinketScan}); any other time, and for every other entity, it is untouched.
 * Applied only when Trinkets is loaded (CompatMixinPlugin); if this Trinkets doesn't have the method, nothing is skipped.
 */
@Pseudo
@Mixin(targets = "dev.emi.trinkets.api.TrinketsApi", remap = false)
public abstract class TrinketsApiMixin {
	@Inject(method = "getTrinketComponent", at = @At("HEAD"), cancellable = true, remap = false)
	private static void drifter$skipEmptyScan(LivingEntity entity, CallbackInfoReturnable<Optional<?>> cir) {
		if (entity instanceof DrifterEntity drifter && drifter.skipTrinketScan()) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
