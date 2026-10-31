package com.dinglebob.drifter.client.mixin;

import com.dinglebob.drifter.client.render.DrifterTouchNMotion;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Drifter picked an item up: Touch'n Motion's "into the pocket" for its stand-in, as for a player (DrifterTouchNMotion). */
@Mixin(ClientPacketListener.class)
public abstract class TakeItemMixin {
	@Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
	private void drifter$pickedUp(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		// The handler runs twice: on the network thread it only hands itself over to the game's.
		if (!minecraft.isSameThread() || minecraft.level == null) {
			return;
		}

		Entity taken = minecraft.level.getEntity(packet.getItemId());
		if (taken != null && !(taken instanceof ExperienceOrb) && minecraft.level.getEntity(packet.getPlayerId()) instanceof DrifterEntity drifter) {
			DrifterTouchNMotion.pickedUp(drifter);
		}
	}
}
