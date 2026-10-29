package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.api.DrifterApi;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;

/** Villages replaced: no wandering trader either (milestone 23, A1; a travelling Drifter merchant takes its place, A7). */
@Mixin(WanderingTraderSpawner.class)
public abstract class WanderingTraderSpawnerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void drifter$noTrader(ServerLevel level, boolean spawnEnemies, boolean spawnFriendlies, CallbackInfoReturnable<Integer> cir) {
		if (DrifterApi.villagesReplaced()) {
			cir.setReturnValue(0);
		}
	}
}
