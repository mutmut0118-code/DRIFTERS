package com.dinglebob.drifter.client.render;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;

/**
 * A Drifter's stand-in player for Touch'n Motion ({@link DrifterTouchNMotion}), on the client only. It is in the client
 * world (the addon finds players there, by id and by UUID), but it is never drawn, never moves or thinks by itself, can't
 * be aimed at, hit or pushed: everything it is, it copies from its Drifter (where it is, which way it looks, its pose and
 * flags, what it holds and wears, its swing, the item in use). The addon's poses for it go onto the Drifter's model.
 */
public final class DrifterStandIn extends RemotePlayer {
	/** Client-made ids: below every id the server hands out (those count up from 0). */
	private static final int ID_BASE = Integer.MIN_VALUE;
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

	final DrifterEntity drifter;
	/** The client tick it was last drawn on (it goes after a while undrawn). */
	int drawnAt;

	DrifterStandIn(ClientLevel level, DrifterEntity drifter) {
		super(level, new GameProfile(UUID.nameUUIDFromBytes(("drifter-touchnmotion:" + drifter.getUUID()).getBytes(StandardCharsets.UTF_8)),
			name(drifter)));
		this.drifter = drifter;
		this.setId(ID_BASE + drifter.getId());
		this.copy();
	}

	private static String name(DrifterEntity drifter) {
		String name = drifter.getName().getString();
		return name.length() > 16 ? name.substring(0, 16) : name;
	}

	/** Everything the addon reads off a player, from the Drifter. */
	void copy() {
		DrifterEntity drifter = this.drifter;
		this.xo = drifter.xo;
		this.yo = drifter.yo;
		this.zo = drifter.zo;
		this.xOld = drifter.xOld;
		this.yOld = drifter.yOld;
		this.zOld = drifter.zOld;
		this.setPos(drifter.getX(), drifter.getY(), drifter.getZ());
		this.setDeltaMovement(drifter.getDeltaMovement());
		this.setOnGround(drifter.onGround());
		this.fallDistance = drifter.fallDistance;
		this.yRotO = drifter.yRotO;
		this.xRotO = drifter.xRotO;
		this.setYRot(drifter.getYRot());
		this.setXRot(drifter.getXRot());
		this.yBodyRotO = drifter.yBodyRotO;
		this.yBodyRot = drifter.yBodyRot;
		this.yHeadRotO = drifter.yHeadRotO;
		this.yHeadRot = drifter.yHeadRot;
		this.walkDistO = drifter.walkDistO;
		this.walkDist = drifter.walkDist;
		this.tickCount = drifter.tickCount;
		this.wasTouchingWater = drifter.isInWater();
		this.wasEyeInWater = drifter.isUnderWater();
		this.isInPowderSnow = drifter.isInPowderSnow;
		this.wasInPowderSnow = drifter.wasInPowderSnow;
		// What it holds and wears, before the flags (the item in use is read from the hand when the flag comes on).
		Inventory inventory = this.getInventory();
		inventory.selected = 0;
		inventory.items.set(0, drifter.getMainHandItem());
		inventory.offhand.set(0, drifter.getOffhandItem());
		for (EquipmentSlot slot : ARMOR) {
			inventory.armor.set(slot.getIndex(), drifter.getItemBySlot(slot));
		}

		this.setMainArm(drifter.getMainArm());
		// Crouching, sprinting, swimming, gliding (the shared flags), and the item in use (the living flags).
		this.entityData.set(DATA_SHARED_FLAGS_ID, drifter.getEntityData().get(DATA_SHARED_FLAGS_ID));
		this.entityData.set(DATA_LIVING_ENTITY_FLAGS, drifter.getEntityData().get(DATA_LIVING_ENTITY_FLAGS));
		this.useItemRemaining = drifter.getUseItemRemainingTicks();
		this.setPose(drifter.getPose());
		drifter.getSleepingPos().ifPresentOrElse(this::setSleepingPos, this::clearSleepingPos);
		this.swinging = drifter.swinging;
		this.swingingArm = drifter.swingingArm;
		this.swingTime = drifter.swingTime;
		this.attackAnim = drifter.attackAnim;
		this.oAttackAnim = drifter.oAttackAnim;
		this.hurtTime = drifter.hurtTime;
		this.hurtDuration = drifter.hurtDuration;
	}

	/** Once a tick: the copy, and the walk as a player's is worked out (from how far it went). */
	void copyTick() {
		this.copy();
		this.calculateEntityAnimation(false);
	}

	@Override
	public void tick() {
		// Copied from the Drifter after the world's tick (DrifterTouchNMotion), never moved by itself.
	}

	@Override
	public void aiStep() {
	}

	@Override
	public boolean shouldRender(double x, double y, double z) {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canSpawnSprintParticle() {
		return false;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}
}
