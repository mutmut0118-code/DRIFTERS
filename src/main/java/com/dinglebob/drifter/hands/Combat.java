package com.dinglebob.drifter.hands;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.mixin.LivingEntityAccessor;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Player melee (DESIGN.md section 1 "Player reach", section 3 "Weapons and timing"): hits only what is under its
 * crosshair within the entity interaction range (3 blocks), nothing through walls, with the player attack cooldown.
 * The hit itself is a real player attack by the fake-player helper, so damage, crits, knockback, sweeping and tool wear
 * follow vanilla.
 */
public final class Combat {
	private Combat() {
	}

	/** Ticks for a full-strength swing with what it holds (Player#getCurrentItemAttackStrengthDelay). */
	public static float attackDelay(DrifterEntity drifter) {
		return (float)(1.0 / drifter.getAttributeValue(Attributes.ATTACK_SPEED) * 20.0);
	}

	/** Player#getAttackStrengthScale. */
	public static float attackStrength(DrifterEntity drifter) {
		return Math.min(1.0F, (drifter.ticksSinceAttack() + 0.5F) / attackDelay(drifter));
	}

	/** Is this entity under its crosshair, within reach, with no block in the way? */
	public static boolean canHit(DrifterEntity drifter, Entity target) {
		Vec3 eye = drifter.getEyePosition();
		double range = drifter.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		Vec3 end = eye.add(drifter.getViewVector(1.0F).scale(range));
		AABB box = target.getBoundingBox().inflate(target.getPickRadius());
		Optional<Vec3> hit = box.contains(eye) ? Optional.of(eye) : box.clip(eye, end);
		if (hit.isEmpty() || eye.distanceToSqr(hit.get()) > range * range) {
			return false;
		}

		return drifter.level().clip(new ClipContext(eye, hit.get(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, drifter)).getType()
			== HitResult.Type.MISS;
	}

	/** Swings at the target as a player would. Returns false if it isn't under the crosshair in reach. */
	public static boolean attack(DrifterEntity drifter, Entity target) {
		if (!canHit(drifter, target)) {
			return false;
		}

		int sinceAttack = drifter.ticksSinceAttack();
		boolean wasSprinting = drifter.isSprinting();
		boolean stillSprinting = FakePlayerBridge.act(drifter, fake -> {
			// The fake player isn't ticked, so it doesn't carry the held item's attack damage and speed modifiers or the
			// attack cooldown; put them on for the swing.
			List<AttributeInstance> touched = new ArrayList<>();
			List<AttributeModifier> added = new ArrayList<>();
			drifter.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
				AttributeInstance instance = fake.getAttribute(attribute);
				if (instance != null && !instance.hasModifier(modifier.id())) {
					instance.addTransientModifier(modifier);
					touched.add(instance);
					added.add(modifier);
				}
			});
			((LivingEntityAccessor)fake).drifter$setAttackStrengthTicker(sinceAttack);
			fake.setSprinting(wasSprinting);
			fake.fallDistance = drifter.fallDistance;
			drifter.setOwnSwing(true);
			try {
				fake.attack(target);
			} finally {
				drifter.setOwnSwing(false);
				for (int i = 0; i < touched.size(); i++) {
					touched.get(i).removeModifier(added.get(i).id());
				}
			}

			boolean sprinting = fake.isSprinting();
			fake.setSprinting(false);
			fake.fallDistance = 0.0F;
			return sprinting;
		});

		// A sprint hit stops the sprint, as for a player.
		if (wasSprinting && !stillSprinting) {
			drifter.setSprinting(false);
		}

		drifter.swing(InteractionHand.MAIN_HAND);
		drifter.resetAttackTimer();
		return true;
	}
}
