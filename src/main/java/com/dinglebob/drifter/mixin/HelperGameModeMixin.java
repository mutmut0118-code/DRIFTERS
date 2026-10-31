package com.dinglebob.drifter.mixin;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.hands.HandSignals;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Drifter's helper uses a block (every door, button, chest, seed, block put down): told to those watching (HandSignals). */
@Mixin(ServerPlayerGameMode.class)
public abstract class HelperGameModeMixin {
	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void drifter$usedOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hit,
		CallbackInfoReturnable<InteractionResult> cir) {
		DrifterEntity drifter = player instanceof net.fabricmc.fabric.api.entity.FakePlayer ? FakePlayerBridge.ownerOf(player) : null;
		if (drifter != null) {
			HandSignals.usedOn(drifter, hit, stack, hand);
		}
	}
}
