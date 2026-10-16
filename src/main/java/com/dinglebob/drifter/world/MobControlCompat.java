package com.dinglebob.drifter.world;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Mob;

/**
 * Optional Mob Control support (DESIGN.md section 21 "Fitting into the modpack's factions"; milestone 12 step 4). Mob
 * Control is detected at runtime and never required. It rebuilds the targets of every mob it has a rule for: "player"
 * in a rule's attack list then means real players only, so Drifters would be left alone. A Drifter counts as a player
 * there (the same rule as everywhere else): for mobs Mob Control governs, the "attack the nearest Drifter" goal mirrors
 * their player goal without its player-only check. Adding "drifter:drifter" to a rule's attack list also works.
 */
public final class MobControlCompat {
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("mobcontrol");
	@Nullable
	private static final Method GET_RULE = find();

	private MobControlCompat() {
	}

	@Nullable
	private static Method find() {
		if (!LOADED) {
			return null;
		}

		try {
			Method method = Class.forName("com.crackerjackbox.mobcontrol.iface.IMob").getMethod("mobControl$getRule");
			Drifter.LOGGER.info("[Drifter] Mob Control found: mobs its rules set to attack players attack Drifters too");
			return method;
		} catch (ReflectiveOperationException | LinkageError e) {
			Drifter.LOGGER.warn("[Drifter] Mob Control found, but not the version Drifter knows; add \"drifter:drifter\" to its attack lists by hand ({})",
				e.toString());
			return null;
		}
	}

	/**
	 * The mob's Mob Control rule lets it attack players: Drifters too. Mob Control cancels any target whose type isn't in
	 * the rule's allow-attack list (and "player" there means the player entity type only), so "drifter:drifter" is added
	 * to the rule's list in memory (never to the config file).
	 */
	@SuppressWarnings("unchecked")
	public static void includeDrifters(Mob mob) {
		if (GET_RULE == null) {
			return;
		}

		try {
			Object rule = GET_RULE.invoke(mob);
			if (rule == null) {
				return;
			}

			Object set = rule.getClass().getField("set").get(rule);
			if (set == null) {
				return;
			}

			for (String list : new String[] {"allowAttack", "attack"}) {
				Object value = set.getClass().getField(list).get(set);
				if (value instanceof java.util.List<?> names && names.contains("player") && !names.contains(DRIFTER)) {
					((java.util.List<Object>)names).add(DRIFTER);
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			if (!warned) {
				warned = true;
				Drifter.LOGGER.warn("[Drifter] couldn't add Drifters to a Mob Control rule; add \"drifter:drifter\" to its attack lists by hand ({})", e.toString());
			}
		}
	}

	private static final String DRIFTER = "drifter:drifter";
	private static boolean warned;

	/** Mob Control has a rule for this mob (and so made its targets). */
	public static boolean governs(Mob mob) {
		if (GET_RULE == null) {
			return false;
		}

		try {
			return GET_RULE.invoke(mob) != null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}
}
