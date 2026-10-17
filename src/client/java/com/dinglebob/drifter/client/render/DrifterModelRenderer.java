package com.dinglebob.drifter.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.BeeStingerLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.SpinAttackEffectLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;

/**
 * Renders a Drifter with one arm type. This is vanilla PlayerRenderer adapted to a mob: it uses the vanilla
 * {@link PlayerModel} baked from the vanilla player model layers ({@link ModelLayers#PLAYER} or
 * {@link ModelLayers#PLAYER_SLIM}), so player resource packs and EMF have the best chance of applying
 * (DESIGN.md section 2, "Resource pack compatibility"). Same 0.9375 scale, crouch offset, swim rotation,
 * arm poses and layers as a player, minus the player-only cape, ears and shoulder parrots.
 */
public class DrifterModelRenderer extends MobRenderer<DrifterEntity, PlayerModel<DrifterEntity>> {
	private static final float PLAYER_SCALE = 0.9375F;

	public DrifterModelRenderer(EntityRendererProvider.Context context, boolean slim) {
		super(context, new SwingingModel(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5F);
		this.addLayer(new HumanoidArmorLayer<>(
			this,
			new HumanoidArmorModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
			new HumanoidArmorModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
			context.getModelManager()
		));
		this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
		this.addLayer(new ArrowLayer<>(context, this));
		this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
		this.addLayer(new ElytraLayer<>(this, context.getModelSet()));
		this.addLayer(new SpinAttackEffectLayer<>(this, context.getModelSet()));
		this.addLayer(new BeeStingerLayer<>(this));
	}

	/** The vanilla player model (same layers, so EMF and resource packs still see a player model), plus Better Combat swings. */
	private static final class SwingingModel extends PlayerModel<DrifterEntity> {
		SwingingModel(net.minecraft.client.model.geom.ModelPart root, boolean slim) {
			super(root, slim);
		}

		@Override
		public void setupAnim(DrifterEntity drifter, float limbSwing, float limbSwingAmount, float ageInTicks, float headYaw, float headPitch) {
			super.setupAnim(drifter, limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch);
			DrifterSwingAnimations.apply(this, drifter, ageInTicks, limbSwingAmount);
		}
	}

	@Override
	public void render(DrifterEntity drifter, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		this.setModelProperties(drifter, partialTick);
		super.render(drifter, yaw, partialTick, poseStack, buffers, light);
	}

	@Override
	public Vec3 getRenderOffset(DrifterEntity drifter, float partialTick) {
		return drifter.isCrouching() ? new Vec3(0.0, drifter.getScale() * -2.0F / 16.0, 0.0) : super.getRenderOffset(drifter, partialTick);
	}

	@Override
	public ResourceLocation getTextureLocation(DrifterEntity drifter) {
		return drifter.getSkinTexture();
	}

	@Override
	protected void scale(DrifterEntity drifter, PoseStack poseStack, float partialTick) {
		poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
	}

	private void setModelProperties(DrifterEntity drifter, float partialTick) {
		PlayerModel<DrifterEntity> model = this.getModel();
		// All skin overlay layers on; Drifters have no skin-customization settings (yet).
		model.setAllVisible(true);
		// Tucked in through a roll.
		model.crouching = drifter.isCrouching() || drifter.rollProgress(partialTick) >= 0.0F;
		HumanoidModel.ArmPose mainPose = getArmPose(drifter, InteractionHand.MAIN_HAND);
		HumanoidModel.ArmPose offPose = getArmPose(drifter, InteractionHand.OFF_HAND);
		if (mainPose.isTwoHanded()) {
			offPose = drifter.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		}

		if (drifter.getMainArm() == HumanoidArm.RIGHT) {
			model.rightArmPose = mainPose;
			model.leftArmPose = offPose;
		} else {
			model.rightArmPose = offPose;
			model.leftArmPose = mainPose;
		}
	}

	/** PlayerRenderer#getArmPose, for a Drifter: bow draw, crossbow, shield, trident, spyglass, horn and brush poses. */
	private static HumanoidModel.ArmPose getArmPose(DrifterEntity drifter, InteractionHand hand) {
		ItemStack stack = drifter.getItemInHand(hand);
		if (stack.isEmpty()) {
			return HumanoidModel.ArmPose.EMPTY;
		}

		if (drifter.getUsedItemHand() == hand && drifter.getUseItemRemainingTicks() > 0) {
			UseAnim anim = stack.getUseAnimation();
			switch (anim) {
				case BLOCK:
					return HumanoidModel.ArmPose.BLOCK;
				case BOW:
					return HumanoidModel.ArmPose.BOW_AND_ARROW;
				case SPEAR:
					return HumanoidModel.ArmPose.THROW_SPEAR;
				case CROSSBOW:
					return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
				case SPYGLASS:
					return HumanoidModel.ArmPose.SPYGLASS;
				case TOOT_HORN:
					return HumanoidModel.ArmPose.TOOT_HORN;
				case BRUSH:
					return HumanoidModel.ArmPose.BRUSH;
				default:
					break;
			}
		} else if (!drifter.swinging && stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack)) {
			return HumanoidModel.ArmPose.CROSSBOW_HOLD;
		}

		return HumanoidModel.ArmPose.ITEM;
	}

	/** PlayerRenderer#setupRotations: tilts the body forward while swimming. TODO(late milestone): elytra glide tilt. */
	@Override
	protected void setupRotations(DrifterEntity drifter, PoseStack poseStack, float bob, float bodyYaw, float partialTick, float scale) {
		float swimAmount = drifter.getSwimAmount(partialTick);
		float viewPitch = drifter.getViewXRot(partialTick);
		super.setupRotations(drifter, poseStack, bob, bodyYaw, partialTick, scale);
		// A Better Combat swing's whole-body movement (the twist into a slash, the lean into a stab).
		DrifterSwingAnimations.applyBody(drifter, poseStack, partialTick);
		// A roll (Combat Roll's dodge): a forward somersault the way it rolls, round the middle of its body.
		float roll = drifter.rollProgress(partialTick);
		if (roll >= 0.0F) {
			float turn = Mth.wrapDegrees(drifter.rollYaw() - bodyYaw);
			float eased = roll * roll * (3.0F - 2.0F * roll);
			poseStack.translate(0.0F, 0.6F, 0.0F);
			poseStack.mulPose(Axis.YP.rotationDegrees(-turn));
			poseStack.mulPose(Axis.XP.rotationDegrees(-360.0F * eased));
			poseStack.mulPose(Axis.YP.rotationDegrees(turn));
			poseStack.translate(0.0F, -0.6F, 0.0F);
		}

		if (swimAmount > 0.0F && !drifter.isFallFlying()) {
			float target = drifter.isInWater() ? -90.0F - viewPitch : -90.0F;
			poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(swimAmount, 0.0F, target)));
			if (drifter.isVisuallySwimming()) {
				poseStack.translate(0.0F, -1.0F, 0.3F);
			}
		}
	}
}
