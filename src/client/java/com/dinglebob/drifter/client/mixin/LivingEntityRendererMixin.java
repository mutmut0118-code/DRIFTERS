package com.dinglebob.drifter.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import com.dinglebob.drifter.client.render.DrifterTouchNMotion;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right before a Drifter's model is posed, on the pose stack as it is there: Touch'n Motion's turn (DrifterTouchNMotion). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
	private void drifter$touchNMotion(LivingEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light,
		CallbackInfo ci) {
		if (entity instanceof DrifterEntity drifter) {
			DrifterTouchNMotion.beforeAnimating(drifter, poseStack, partialTick);
		}
	}
}
