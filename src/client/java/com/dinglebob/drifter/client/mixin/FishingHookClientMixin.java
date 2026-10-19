package com.dinglebob.drifter.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dinglebob.drifter.client.render.DrifterFishers;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.projectile.FishingHook;

/**
 * A bobber cast by a Drifter: its owner on this side is the Drifter's stand-in player (DrifterFishers), so the bobber
 * isn't thrown away for having no player and vanilla draws it and its line from the Drifter's hand.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookClientMixin {
	@Inject(method = "recreateFromPacket", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/projectile/FishingHook;getPlayerOwner()Lnet/minecraft/world/entity/player/Player;"), cancellable = true)
	private void drifter$drifterOwner(ClientboundAddEntityPacket packet, CallbackInfo ci) {
		FishingHook self = (FishingHook)(Object)this;
		if (self.level().getEntity(packet.getData()) instanceof DrifterEntity drifter) {
			self.setOwner(DrifterFishers.standIn(drifter));
			ci.cancel();
		}
	}
}
