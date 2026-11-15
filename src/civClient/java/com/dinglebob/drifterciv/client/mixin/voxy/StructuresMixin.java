package com.dinglebob.drifterciv.client.mixin.voxy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.api.DrifterApi;

import mobius.voxyseedgen.terrain.Structures;

/**
 * No villages in Voxy's far view where Drifter towns stand in for villages (optional: applied only with Voxy SeedGen
 * 0.3.x there). Voxy SeedGen works villages' spots out from the seed and draws stand-in houses there; it can't see that
 * none is ever placed in such a world, so far villages showed that weren't there (the user, 2026-11-15: "villages are
 * shown in voxy").
 */
@Pseudo
@Mixin(targets = "mobius.voxyseedgen.terrain.Structures", remap = false)
public class StructuresMixin {
	@Inject(method = "carriesStructure", at = @At("HEAD"), cancellable = true, remap = false)
	private static void drifterciv$noVillages(long seed, Structures.Set set, int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> info) {
		if (set != null && set.name().equals("VILLAGE") && DrifterApi.villagesReplaced()) {
			info.setReturnValue(false);
		}
	}
}
