package com.dinglebob.drifter.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.api.DrifterApi;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Villages replaced (milestone 23, docs/LIVED_WORLD.md A1): with an addon making Drifter towns where villages would be, a
 * village (anything tagged #minecraft:village, other mods' too) finds nowhere to go: not in new chunks, and not for
 * /locate or a map either, which says so at once rather than laying out a village at every spot it looks at (found in
 * testing: /locate for villages took over a minute and the server shut itself down).
 */
@Mixin(Structure.class)
public abstract class StructureMixin {
	@Inject(method = "findValidGenerationPoint", at = @At("HEAD"), cancellable = true)
	private void drifter$noVillages(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
		if (DrifterApi.villagesReplaced() && context.registryAccess().registryOrThrow(Registries.STRUCTURE).wrapAsHolder((Structure)(Object)this)
			.is(StructureTags.VILLAGE)) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
