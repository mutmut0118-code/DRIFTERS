package com.dinglebob.drifter.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * The renderer registered for the Drifter entity type. Like the vanilla player renderers (one per arm type),
 * it keeps a wide-arm and a slim-arm renderer and hands each Drifter to the one matching its skin.
 */
public class DrifterRenderer extends EntityRenderer<DrifterEntity> {
	private final DrifterModelRenderer wide;
	private final DrifterModelRenderer slim;

	public DrifterRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.wide = new DrifterModelRenderer(context, false);
		this.slim = new DrifterModelRenderer(context, true);
		this.shadowRadius = 0.5F;
	}

	private DrifterModelRenderer pick(DrifterEntity drifter) {
		return drifter.hasSlimArms() ? this.slim : this.wide;
	}

	@Override
	public void render(DrifterEntity drifter, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		this.pick(drifter).render(drifter, yaw, partialTick, poseStack, buffers, light);
	}

	@Override
	public boolean shouldRender(DrifterEntity drifter, Frustum frustum, double camX, double camY, double camZ) {
		return this.pick(drifter).shouldRender(drifter, frustum, camX, camY, camZ);
	}

	@Override
	public Vec3 getRenderOffset(DrifterEntity drifter, float partialTick) {
		return this.pick(drifter).getRenderOffset(drifter, partialTick);
	}

	@Override
	public ResourceLocation getTextureLocation(DrifterEntity drifter) {
		return this.pick(drifter).getTextureLocation(drifter);
	}
}
