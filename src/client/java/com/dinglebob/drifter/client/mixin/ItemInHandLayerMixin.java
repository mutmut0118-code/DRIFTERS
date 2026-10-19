package com.dinglebob.drifter.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dinglebob.drifter.client.render.DrifterSwingAnimations;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A Drifter's weapon follows the animation playing on it (a Better Combat swing turns the blade, a pose holds it), as
 * the player animation library does for a player's held item.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
	private void drifter$animatedItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack poseStack,
		MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (entity instanceof DrifterEntity drifter) {
			DrifterSwingAnimations.applyItem(drifter, arm, poseStack);
		}
	}
}
