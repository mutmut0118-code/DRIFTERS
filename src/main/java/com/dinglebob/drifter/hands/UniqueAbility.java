package com.dinglebob.drifter.hands;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonObject;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.brain.Weapons;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/**
 * A unique weapon's ability in a fight (DESIGN.md section 21: "weapons with special abilities need a small per-item
 * data entry saying when to trigger the ability"; user, 2026-10-17: uniques found in loot, abilities used). The entry is
 * the weapon file's {@code ability} object:
 * <ul>
 *   <li>{@code "when": "crowd"}: {@code count} enemies or more within {@code range};</li>
 *   <li>{@code "when": "target"}: what it fights is between {@code min} and {@code max} blocks off, in sight;</li>
 *   <li>{@code "when": "hurt"}: its health is at {@code health} (a fraction) or less, mid-fight;</li>
 *   <li>{@code "when": "fight"}: any time what it fights is within {@code max} (default 6);</li>
 *   <li>{@code "hold"}: ticks it stands and aims first (a charged ability's wind-up); 0 is at once.</li>
 * </ul>
 * The ability itself goes through Simply Swords' own API for a mob using a weapon's ability (its activation source
 * MOB): the Drifter is the one using it, so what the ability does to its user (a heal, a buff, a dash) happens to the
 * Drifter, and Simply Swords keeps its cooldown. Read through reflection; without Simply Swords (or if its API changes)
 * there are no abilities and the weapons are swung as plain ones.
 */
public final class UniqueAbility {
	private UniqueAbility() {
	}

	/** Between tries of an ability (a player doesn't spam a key that isn't ready). */
	private static final int RETRY_TICKS = 30;

	private static boolean ready;
	private static boolean failed;
	private static Method contextOf;
	private static Method canActivate;
	private static Method tryActivate;
	private static Object mobSource;

	private static boolean active() {
		if (ready || failed) {
			return ready;
		}

		failed = true;
		if (!FabricLoader.getInstance().isModLoaded("simplyswords")) {
			return false;
		}

		try {
			Class<?> context = Class.forName("net.sweenus.simplyswords.api.WeaponAbilityContext");
			Class<?> source = Class.forName("net.sweenus.simplyswords.api.WeaponAbilityActivationSource");
			Class<?> api = Class.forName("net.sweenus.simplyswords.api.SimplySwordsAPI");
			contextOf = context.getMethod("of", ServerLevel.class, ItemStack.class, LivingEntity.class, ServerPlayer.class, LivingEntity.class,
				InteractionHand.class, source);
			canActivate = api.getMethod("canActivateWeaponAbility", context);
			tryActivate = api.getMethod("tryActivateWeaponAbility", context);
			mobSource = source.getField("MOB").get(null);
			ready = true;
			failed = false;
			Drifter.LOGGER.info("[Drifter] Simply Swords found: Drifters use their unique weapons' abilities");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			Drifter.LOGGER.warn("[Drifter] Simply Swords' ability API didn't match ({}); Drifters swing unique weapons as plain ones", e.toString());
		}

		return ready;
	}

	/**
	 * Each attack tick: uses the held unique's ability if its entry says now. True while it winds up a charged one (the
	 * attack waits, aiming), false otherwise.
	 */
	public static boolean tick(DrifterEntity drifter, LivingEntity target, double distance) {
		ItemStack held = drifter.getMainHandItem();
		JsonObject ability = abilityOf(held);
		if (ability == null || !(drifter.level() instanceof ServerLevel level) || !active()) {
			return false;
		}

		long now = level.getGameTime();
		int hold = ability.has("hold") ? ability.get("hold").getAsInt() : 0;
		int windUp = drifter.abilityHold();
		if (windUp > 0) {
			drifter.setAbilityHold(windUp + 1);
			if (windUp + 1 < hold && sees(drifter, target)) {
				return true;
			}

			drifter.setAbilityHold(0);
			drifter.setAbilityReadyAt(now + RETRY_TICKS);
			activate(drifter, level, held, target);
			return false;
		}

		if (now < drifter.abilityReadyAt() || !wanted(drifter, target, distance, ability)) {
			return false;
		}

		drifter.setAbilityReadyAt(now + RETRY_TICKS);
		Object context = context(level, held, drifter, target);
		if (context == null || !call(canActivate, context)) {
			return false;
		}

		if (hold > 0) {
			drifter.setAbilityHold(1);
			return true;
		}

		activate(drifter, level, held, target);
		return false;
	}

	private static void activate(DrifterEntity drifter, ServerLevel level, ItemStack held, LivingEntity target) {
		Object context = context(level, held, drifter, target);
		if (context != null && call(tryActivate, context)) {
			drifter.getMemory().thoughts().add(level.getGameTime(), "used " + held.getHoverName().getString() + "'s ability on the "
				+ target.getName().getString());
		}
	}

	@Nullable
	private static Object context(ServerLevel level, ItemStack stack, DrifterEntity drifter, LivingEntity target) {
		try {
			return contextOf.invoke(null, level, stack, drifter, null, target, InteractionHand.MAIN_HAND, mobSource);
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
			return null;
		}
	}

	private static boolean call(Method method, Object context) {
		try {
			return (boolean)method.invoke(null, context);
		} catch (java.lang.reflect.InvocationTargetException e) {
			// The ability itself threw: that one weapon is skipped this time, the rest keep working.
			Drifter.LOGGER.warn("[Drifter] A weapon ability failed: {}", e.getCause() == null ? e.toString() : e.getCause().toString());
			return false;
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
			return false;
		}
	}

	private static void fail(Throwable e) {
		ready = false;
		failed = true;
		Drifter.LOGGER.warn("[Drifter] Simply Swords' ability API didn't match ({}); Drifters swing unique weapons as plain ones", e.toString());
	}

	private static boolean wanted(DrifterEntity drifter, LivingEntity target, double distance, JsonObject ability) {
		String when = ability.has("when") ? ability.get("when").getAsString() : "fight";
		double max = ability.has("max") ? ability.get("max").getAsDouble() : 6.0;
		return switch (when) {
			case "crowd" -> enemiesWithin(drifter, ability.has("range") ? ability.get("range").getAsDouble() : 5.0)
				>= (ability.has("count") ? ability.get("count").getAsInt() : 2);
			case "target" -> distance >= (ability.has("min") ? ability.get("min").getAsDouble() : 0.0) && distance <= max && sees(drifter, target);
			case "hurt" -> drifter.getHealth() <= drifter.getMaxHealth() * (ability.has("health") ? ability.get("health").getAsDouble() : 0.5)
				&& distance <= max;
			default -> distance <= max && sees(drifter, target);
		};
	}

	/** Hostiles near it that it can see (anything Enemy, or a mob after it). */
	private static int enemiesWithin(DrifterEntity drifter, double range) {
		return drifter.level().getEntitiesOfClass(LivingEntity.class, drifter.getBoundingBox().inflate(range), e -> e != drifter && e.isAlive()
			&& (e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == drifter) && e.distanceTo(drifter) <= range && sees(drifter, e)).size();
	}

	private static boolean sees(DrifterEntity drifter, LivingEntity other) {
		return drifter.level().clip(new ClipContext(drifter.getEyePosition(), other.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
			drifter)).getType() == HitResult.Type.MISS;
	}

	@Nullable
	public static JsonObject abilityOf(ItemStack stack) {
		Weapons.Unique unique = Weapons.unique(stack);
		return unique == null ? null : unique.ability();
	}
}
