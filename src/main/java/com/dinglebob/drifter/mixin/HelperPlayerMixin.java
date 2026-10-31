package com.dinglebob.drifter.mixin;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.hands.HandSignals;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Drifter's helper uses or hits a creature (feeding, milking, shearing, an armour stand, a blow): told to those watching. */
@Mixin(Player.class)
public abstract class HelperPlayerMixin {
	@Inject(method = "interactOn", at = @At("HEAD"))
	private void drifter$usedOn(Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		Player self = (Player)(Object)this;
		DrifterEntity drifter = self instanceof net.fabricmc.fabric.api.entity.FakePlayer && !self.level().isClientSide() ? FakePlayerBridge.ownerOf(self) : null;
		if (drifter != null) {
			HandSignals.usedOn(drifter, target, self.getItemInHand(hand), hand);
		}
	}

	@Inject(method = "attack", at = @At("HEAD"))
	private void drifter$attacked(Entity target, CallbackInfo ci) {
		Player self = (Player)(Object)this;
		DrifterEntity drifter = self instanceof net.fabricmc.fabric.api.entity.FakePlayer && !self.level().isClientSide() ? FakePlayerBridge.ownerOf(self) : null;
		if (drifter != null) {
			HandSignals.attacked(drifter, target);
		}
	}
}
