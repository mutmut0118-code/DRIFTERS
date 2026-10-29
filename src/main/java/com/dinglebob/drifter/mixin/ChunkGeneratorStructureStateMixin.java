package com.dinglebob.drifter.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.api.DrifterApi;

import net.minecraft.core.Holder;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * Villages replaced (milestone 23, A1): a village has nowhere it's placed, so /locate, explorer maps and other mods looking
 * for one give up at once (found in testing: with villages only stopped where they'd start, /locate looked at every spot
 * one could have been for a minute, and the server shut itself down).
 */
@Mixin(ChunkGeneratorStructureState.class)
public abstract class ChunkGeneratorStructureStateMixin {
	@Inject(method = "getPlacementsForStructure", at = @At("HEAD"), cancellable = true)
	private void drifter$noVillages(Holder<Structure> structure, CallbackInfoReturnable<List<StructurePlacement>> cir) {
		if (DrifterApi.villagesReplaced() && structure.is(StructureTags.VILLAGE)) {
			cir.setReturnValue(List.of());
		}
	}
}
