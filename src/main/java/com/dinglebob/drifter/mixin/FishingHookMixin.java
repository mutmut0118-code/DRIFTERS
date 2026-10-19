package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.FakePlayerBridge;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.projectile.FishingHook;

/**
 * A Drifter's bobber (cast by its helper player, which clients never see) goes out to clients with the Drifter as its
 * owner, so they can draw it and its line from the Drifter's hand.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
	@Inject(method = "getAddEntityPacket", at = @At("HEAD"), cancellable = true)
	private void drifter$drifterOwner(ServerEntity serverEntity, CallbackInfoReturnable<Packet<ClientGamePacketListener>> cir) {
		FishingHook self = (FishingHook)(Object)this;
		DrifterEntity drifter = FakePlayerBridge.ownerOf(self.getOwner());
		if (drifter != null) {
			cir.setReturnValue(new ClientboundAddEntityPacket(self, serverEntity, drifter.getId()));
		}
	}
}
