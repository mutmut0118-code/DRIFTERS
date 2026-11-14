package com.dinglebob.drifterciv.client.mixin.voxy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dinglebob.drifterciv.client.FarVoxy;

import mobius.voxyseedgen.terrain.ColumnSpans;

/**
 * Paper towns in Voxy (optional: applied only with Voxy SeedGen 0.3.x there, {@code FarVoxyPlugin}): a column of far land
 * Voxy SeedGen is about to write into Voxy (its coarser levels) has the sketched towns and roads painted into it first.
 */
@Pseudo
@Mixin(targets = "mobius.voxyseedgen.voxy.LadderWriter", remap = false)
public class LadderWriterMixin {
	@Inject(method = "column", at = @At("HEAD"), remap = false)
	private void drifterciv$paint(int worldX, int worldZ, int stride, ColumnSpans spans, CallbackInfo info) {
		FarVoxy.paint(worldX, worldZ, stride, spans);
	}
}
