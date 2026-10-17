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
			Object distance = Class.forName("net.combat_roll.api.CombatRoll$Attributes").getField("DISTANCE").get(null);
			baseDistance = distance.getClass().getField("baseValue").getDouble(distance);
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

	/** It could roll right now (off cooldown, fed enough, on the ground unless the server allows otherwise). */
	public static boolean canRoll(DrifterEntity drifter) {
		if (!active()) {
			return false;
		}

		try {
			return drifter.level().getGameTime() >= drifter.rollReadyAt() && (drifter.onGround() || airborne.getBoolean(config()))
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
			double distance = baseDistance + extraDistance.getFloat(config());
			Vec3 push = flat.normalize().scale(distance * PUSH_PER_BLOCK / 2.0);
			drifter.setDeltaMovement(push.x, Math.max(0.0, drifter.getDeltaMovement().y), push.z);
			drifter.hurtMarked = true;
			// Its roll animation (the one Combat Roll plays is for players only), the way it rolls.
			drifter.showRoll((float)Math.toDegrees(Math.atan2(-flat.x, flat.z)), duration.getInt(config()));
			setInvulnerable.invoke(drifter, invulnerableTicks.getInt(config()));
			drifter.getFoodData().addExhaustion(exhaustion.getFloat(config()));
			drifter.setRollReadyAt(drifter.level().getGameTime() + Math.round(cooldownSeconds.getFloat(config()) * 20.0F));
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
