package com.dinglebob.drifter.hands;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.mixin.LivingEntityAccessor;

import net.minecraft.world.InteractionHand;
import net.fabricmc.fabric.api.entity.FakePlayer;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
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

	/** Damage per hit and swings per second an item gives in the main hand (base player values plus its modifiers). */
	public record WeaponStats(double damage, double speed) {
		public double perSecond() {
			return this.damage * this.speed;
		}
	}

	public static WeaponStats weaponStats(DrifterEntity drifter, ItemStack stack) {
		double[] damage = {drifter.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)};
		double[] speed = {drifter.getAttributeBaseValue(Attributes.ATTACK_SPEED)};
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (modifier.operation() != AttributeModifier.Operation.ADD_VALUE) {
				return;
			}

			if (attribute.is(Attributes.ATTACK_DAMAGE)) {
				damage[0] += modifier.amount();
			} else if (attribute.is(Attributes.ATTACK_SPEED)) {
				speed[0] += modifier.amount();
			}
		});
		return new WeaponStats(damage[0], Math.max(0.1, speed[0]));
	}

	/**
	 * The inventory slot of its best melee weapon (most damage per second, so a sword beats an axe and anything beats a
	 * block of planks), or -1 if nothing beats an empty hand.
	 */
	/** A hotbar slot to hold while blocking (right-click uses the main hand first): its best weapon if that's on the
	 * hotbar, else anything with no use of its own, or -1. */
	public static int freeHandSlot(DrifterEntity drifter) {
		int weapon = bestWeaponSlot(drifter);
		if (weapon >= 0 && weapon < com.dinglebob.drifter.entity.DrifterInventory.HOTBAR_SIZE) {
			return weapon;
		}

		for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.HOTBAR_SIZE; slot++) {
			if (drifter.getInventory().getItem(slot).getUseAnimation() == net.minecraft.world.item.UseAnim.NONE) {
				return slot;
			}
		}

		return -1;
	}

	public static int bestWeaponSlot(DrifterEntity drifter) {
		DrifterInventory inventory = drifter.getInventory();
		int best = -1;
		double bestScore = weaponStats(drifter, ItemStack.EMPTY).perSecond() + 0.01;
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			double score = weaponStats(drifter, stack).perSecond();
			if (score > bestScore) {
				bestScore = score;
				best = slot;
			}
		}

		return best;
	}

	/** Ticks for a full-strength swing with what it holds (Player#getCurrentItemAttackStrengthDelay). */
	public static float attackDelay(DrifterEntity drifter) {
		return (float)(1.0 / drifter.getAttributeValue(Attributes.ATTACK_SPEED) * 20.0);
	}

	/** Player#getAttackStrengthScale. */
	public static float attackStrength(DrifterEntity drifter) {
		return Math.min(1.0F, (drifter.ticksSinceAttack() + 0.5F) / attackDelay(drifter));
	}

	/** Points a mob's revenge and anger at the Drifter instead of the fake player that made the hit. */
	private static void redirectAnger(DrifterEntity drifter, Entity target) {
		if (!(target instanceof LivingEntity living) || !living.isAlive()) {
			return;
		}

		if (living.getLastHurtByMob() instanceof FakePlayer) {
			living.setLastHurtByMob(drifter);
		}

		if (living instanceof Mob mob && mob.getTarget() instanceof FakePlayer) {
			mob.setTarget(drifter);
		}

		if (living instanceof NeutralMob neutral && neutral.getPersistentAngerTarget() != null
			&& neutral.getPersistentAngerTarget().equals(FakePlayerBridge.helperId(drifter))) {
			neutral.setPersistentAngerTarget(drifter.getUUID());
		}

		// Zombies call their friends against the attacker: point those at the Drifter too.
		for (Mob nearby : drifter.level().getEntitiesOfClass(Mob.class, living.getBoundingBox().inflate(32.0), mob -> mob.getTarget() instanceof FakePlayer)) {
			nearby.setTarget(drifter);
		}
	}

	/** Its attack reach: the player's, or with Better Combat installed, the reach of the weapon in its hand. */
	public static double reach(DrifterEntity drifter) {
		double base = drifter.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		return com.dinglebob.drifter.compat.BetterCombatCompat.range(drifter.getMainHandItem(), base);
	}

	/** Is this entity under its crosshair, within reach, with no block in the way? */
	public static boolean canHit(DrifterEntity drifter, Entity target) {
		Vec3 eye = drifter.getEyePosition();
		double range = reach(drifter);
		Vec3 end = eye.add(drifter.getViewVector(1.0F).scale(range));
		AABB box = target.getBoundingBox().inflate(target.getPickRadius());
		Optional<Vec3> hit = box.contains(eye) ? Optional.of(eye) : box.clip(eye, end);
		if (hit.isEmpty() || eye.distanceToSqr(hit.get()) > range * range) {
			return false;
		}

		return drifter.level().clip(new ClipContext(eye, hit.get(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, drifter)).getType()
			== HitResult.Type.MISS;
	}

	/**
	 * Other hostiles inside this Better Combat swing (its reach, and its arc: wide for a horizontal sweep, narrow for an
	 * overhead or a thrust), in clear sight.
	 */
	private static List<Entity> alsoInArc(DrifterEntity drifter, Entity target, com.dinglebob.drifter.compat.BetterCombatCompat.Swing swing) {
		double range = reach(drifter);
		Vec3 eye = drifter.getEyePosition();
		Vec3 look = drifter.getViewVector(1.0F);
		double half = switch (swing.hitbox()) {
			case "HORIZONTAL_PLANE" -> Math.max(15.0, swing.angle() / 2.0);
			case "VERTICAL_PLANE" -> 15.0;
			default -> 10.0;
		};
		List<Entity> hits = new ArrayList<>();
		for (Entity other : drifter.level().getEntities(drifter, drifter.getBoundingBox().inflate(range + 1.0), Entity::isAlive)) {
			if (other == target || !(other instanceof net.minecraft.world.entity.LivingEntity)
				|| !(other instanceof net.minecraft.world.entity.monster.Enemy || other instanceof Mob mob && mob.getTarget() == drifter)) {
				continue;
			}

			Vec3 to = other.getBoundingBox().getCenter().subtract(eye);
			Vec3 closest = other.getBoundingBox().getCenter();
			if (eye.distanceTo(closest) > range + other.getBbWidth() / 2.0) {
				continue;
			}

			double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, to.normalize().dot(look)))));
			if (angle > half || drifter.level().clip(new ClipContext(eye, closest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, drifter))
				.getType() != HitResult.Type.MISS) {
				continue;
			}

			hits.add(other);
		}

		return hits;
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
			// Better Combat: this swing is the next attack of the weapon's combo (its own damage, arc and sound).
			com.dinglebob.drifter.compat.BetterCombatCompat.Swing swing = com.dinglebob.drifter.compat.BetterCombatCompat.swing(fake,
				drifter.comboFor(sinceAttack));
			try {
				fake.attack(target);
				if (swing != null) {
					// Its swing animation on watching clients (Better Combat's own, for this attack of the combo).
					drifter.showSwing(swing.animation(), attackDelay(drifter), drifter.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT);
					com.dinglebob.drifter.compat.BetterCombatCompat.playSwingSound((net.minecraft.server.level.ServerLevel)drifter.level(), drifter, swing);
					// Everything else in the swing's arc it would hit too, as Better Combat hits for a player (hostiles only:
					// it doesn't clip a friend or an animal on the way past).
					List<Entity> others = alsoInArc(drifter, target, swing);
					for (Entity other : others) {
						((LivingEntityAccessor)fake).drifter$setAttackStrengthTicker(sinceAttack);
						fake.attack(other);
						redirectAnger(drifter, other);
					}

					if (!others.isEmpty()) {
						drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "caught " + (others.size() + 1) + " in one swing");
					}
				}
			} finally {
				if (swing != null) {
					com.dinglebob.drifter.compat.BetterCombatCompat.clearCombo(fake);
					drifter.nextCombo();
				}

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

		// The mob knows who hit it: the Drifter, not its invisible helper (found in review: zombies went after the helper
		// standing where the Drifter last swung, which made every fight free). Kill credit stays with the helper, as a
		// player's, for drops and experience.
		redirectAnger(drifter, target);

		// A sprint hit stops the sprint and costs momentum, as for a player (Player#attack).
		if (wasSprinting && !stillSprinting) {
			drifter.setSprinting(false);
			drifter.setDeltaMovement(drifter.getDeltaMovement().multiply(0.6, 1.0, 0.6));
		}

		drifter.swing(InteractionHand.MAIN_HAND);
		drifter.resetAttackTimer();
		return true;
	}
}
