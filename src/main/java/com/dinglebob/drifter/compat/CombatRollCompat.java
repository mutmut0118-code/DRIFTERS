package com.dinglebob.drifter.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Combat Roll, when it's installed (DESIGN.md section 21: "the same dodge roll as Combat Roll"; optional, detected at
 * runtime). A Drifter rolls as a player does: a quick burst along the ground, Combat Roll's own distance; the
 * invulnerability ticks Combat Roll gives a roll (its server side, which works on any living thing); its cooldown, the
 * hunger it costs and the food it needs, from Combat Roll's own server settings. Read through reflection; if anything
 * doesn't match, it switches itself off and the Drifter just sidesteps.
 */
public final class CombatRollCompat {
	private CombatRollCompat() {
	}

	private static boolean ready;
	private static boolean failed;
	/** CombatRollMod.config, read fresh each time (it's replaced when the server's settings reload). */
	private static Field configField;
	private static Field invulnerableTicks;
	private static Field cooldownSeconds;
	private static Field extraDistance;
	private static Field foodRequired;
	private static Field exhaustion;
	private static Field airborne;
	private static Field duration;
	private static Method setInvulnerable;
	private static double baseDistance = 3.0;
	/**
	 * Combat Roll's attributes (count, recharge, distance; its enchantments Multi-Roll, Acrobat and Longfooted work through
	 * them). It adds them to the player's attributes, which a Drifter's are, so a Drifter has them and its armour's
	 * enchantments count, as for a player.
	 */
	@org.jetbrains.annotations.Nullable
	private static net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> countAttribute;
	@org.jetbrains.annotations.Nullable
	private static net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> rechargeAttribute;
	@org.jetbrains.annotations.Nullable
	private static net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> distanceAttribute;
	/** Ground friction slows a roll's push: the push that carries it about this far. */
	private static final double PUSH_PER_BLOCK = 0.45;

	public static boolean active() {
		if (ready || failed) {
			return ready;
		}

		if (!FabricLoader.getInstance().isModLoaded("combat_roll")) {
			failed = true;
			return false;
		}

		try {
			Class<?> mod = Class.forName("net.combat_roll.CombatRollMod");
			configField = mod.getField("config");
			Class<?> settings = Class.forName("net.combat_roll.config.ServerConfig");
			invulnerableTicks = settings.getField("invulnerable_ticks_upon_roll");
			cooldownSeconds = settings.getField("roll_cooldown");
			extraDistance = settings.getField("additional_roll_distance");
			foodRequired = settings.getField("food_level_required");
			exhaustion = settings.getField("exhaust_on_roll");
			airborne = settings.getField("allow_rolling_while_airborn");
			duration = settings.getField("roll_duration");
			setInvulnerable = Class.forName("net.combat_roll.api.RollInvulnerable").getMethod("setRollInvulnerableTicks", int.class);
			Class<?> attributes = Class.forName("net.combat_roll.api.CombatRoll$Attributes");
			Object distance = attributes.getField("DISTANCE").get(null);
			baseDistance = distance.getClass().getField("baseValue").getDouble(distance);
			distanceAttribute = holder(distance);
			rechargeAttribute = holder(attributes.getField("RECHARGE").get(null));
			countAttribute = holder(attributes.getField("COUNT").get(null));
			ready = configField.get(null) != null;
			if (ready) {
				Drifter.LOGGER.info("[Drifter] Combat Roll found: Drifters roll out of the way as players do");
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
		}

		if (!ready) {
			failed = true;
		}

		return ready;
	}

	private static void fail(Throwable e) {
		ready = false;
		failed = true;
		Drifter.LOGGER.warn("[Drifter] Combat Roll is installed but didn't match ({}); Drifters sidestep instead", e.toString());
	}

	private static Object config() throws ReflectiveOperationException {
		return configField.get(null);
	}

	@SuppressWarnings("unchecked")
	@org.jetbrains.annotations.Nullable
	private static net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder(Object entry) throws ReflectiveOperationException {
		Object holder = entry.getClass().getField("entry").get(entry);
		return holder instanceof net.minecraft.core.Holder<?> h ? (net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>)h : null;
	}

	/** One of its Combat Roll attributes (with what its gear's enchantments add), or {@code fallback} if it hasn't got it. */
	private static double attribute(DrifterEntity drifter, @org.jetbrains.annotations.Nullable net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder,
		double fallback) {
		return holder != null && drifter.getAttributes().hasAttribute(holder) ? drifter.getAttributeValue(holder) : fallback;
	}

	/**
	 * Its rolls ready now, as Combat Roll keeps them for a player (RollManager): up to its count, each used one coming back
	 * after the cooldown, sooner with more recharge (Acrobat). A Drifter starts with all of them.
	 */
	public static int rollsReady(DrifterEntity drifter) {
		if (!active()) {
			return 0;
		}

		try {
			long now = drifter.level().getGameTime();
			int max = Math.max(0, (int)attribute(drifter, countAttribute, 1.0));
			float perTick = (float)(attribute(drifter, rechargeAttribute, 20.0) / 20.0);
			int length = Math.max(1, Math.round(cooldownSeconds.getFloat(config()) * 20.0F));
			int charges = drifter.rollCharges();
			float progress = drifter.rollProgress();
			if (charges < 0) {
				charges = max;
				progress = 0.0F;
			} else if (charges < max) {
				progress += Math.max(0L, now - drifter.rollUpdatedAt()) * perTick;
				while (progress >= length && charges < max) {
					charges++;
					progress -= length;
				}
			}

			if (charges >= max) {
				charges = max;
				progress = 0.0F;
			}

			drifter.setRollState(charges, progress, now);
			return charges;
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
			return 0;
		}
	}

	/** It could roll right now (a roll ready, fed enough, on the ground unless the server allows otherwise). */
	public static boolean canRoll(DrifterEntity drifter) {
		if (!active()) {
			return false;
		}

		try {
			return rollsReady(drifter) > 0 && (drifter.onGround() || airborne.getBoolean(config()))
				&& drifter.getFoodData().getFoodLevel() >= foodRequired.getFloat(config()) && !drifter.isInWater() && !drifter.isPassenger();
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
			return false;
		}
	}

	/** Rolls this way (along the ground) if it can; true if it did. */
	public static boolean roll(DrifterEntity drifter, Vec3 direction) {
		Vec3 flat = new Vec3(direction.x, 0.0, direction.z);
		if (!canRoll(drifter) || flat.lengthSqr() < 1.0E-6) {
			return false;
		}

		try {
			double distance = attribute(drifter, distanceAttribute, baseDistance) + extraDistance.getFloat(config());
			Vec3 push = flat.normalize().scale(distance * PUSH_PER_BLOCK / 2.0);
			drifter.setDeltaMovement(push.x, Math.max(0.0, drifter.getDeltaMovement().y), push.z);
			drifter.hurtMarked = true;
			// Its roll animation (the one Combat Roll plays is for players only), the way it rolls.
			drifter.showRoll((float)Math.toDegrees(Math.atan2(-flat.x, flat.z)), duration.getInt(config()));
			setInvulnerable.invoke(drifter, invulnerableTicks.getInt(config()));
			drifter.getFoodData().addExhaustion(exhaustion.getFloat(config()));
			drifter.setRollState(rollsReady(drifter) - 1, drifter.rollProgress(), drifter.level().getGameTime());
			var sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("combat_roll", "roll"));
			if (sound != null) {
				drifter.level().playSound(null, drifter.getX(), drifter.getY(), drifter.getZ(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
			}

			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "rolled out of the way");
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
			return false;
		}
	}
}
