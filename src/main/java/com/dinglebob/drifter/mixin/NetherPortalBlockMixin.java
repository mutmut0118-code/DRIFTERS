package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.NetherPortalBlock;

/**
 * A Drifter waits in a Nether portal as long as a player does (4 s by the game rule; mobs go through at once), section 1
 * "actions take player time".
 */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {
	@Inject(method = "getPortalTransitionTime", at = @At("HEAD"), cancellable = true)
	private void drifter$playerWait(ServerLevel level, Entity entity, CallbackInfoReturnable<Integer> cir) {
		if (entity instanceof DrifterEntity) {
			cir.setReturnValue(Math.max(1, level.getGameRules().getInt(GameRules.RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY)));
		}
	}
}
