package com.dinglebob.drifter.compat;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Better Combat, when it's installed (DESIGN.md section 21: optional, detected at runtime, never required). Read through
 * reflection, so the mod builds and runs without it: a weapon's reach, whether it's two-handed, and its combo of attacks
 * (each with its own hitbox shape, swing arc and damage multiplier, and the weapon's swing sound). Better Combat's own
 * player code then does the rest for the Drifter's stand-in player (the combo's damage multiplier, no vanilla sweeping).
 * If anything about it changes in an update, this switches itself off and the Drifter fights the vanilla way.
 */
public final class BetterCombatCompat {
	private BetterCombatCompat() {
	}

	/** One attack of a weapon's combo. */
	public record Swing(String hitbox, double angle, double damageMultiplier, @Nullable Object sound, String animation, double upswing) {
	}

	private static boolean ready;
	private static boolean failed;
	private static Method getAttributes;
	private static Method combineAttackRange;
	private static Method isTwoHanded;
	private static Method getCurrentAttack;
	private static Method handAttack;
	private static Method attackHitbox;
	private static Method attackAngle;
	private static Method attackDamage;
	private static Method attackSound;
	private static Method attackAnimation;
	private static Method attackUpswing;
	private static Method setComboCount;
	private static Method playSound;

	public static boolean active() {
		if (ready || failed) {
			return ready;
		}

		if (!FabricLoader.getInstance().isModLoaded("bettercombat")) {
			failed = true;
			return false;
		}

		try {
			Class<?> registry = Class.forName("net.bettercombat.logic.WeaponRegistry");
			Class<?> attributes = Class.forName("net.bettercombat.api.WeaponAttributes");
			Class<?> attack = Class.forName("net.bettercombat.api.WeaponAttributes$Attack");
			Class<?> helper = Class.forName("net.bettercombat.logic.PlayerAttackHelper");
			Class<?> hand = Class.forName("net.bettercombat.api.AttackHand");
			Class<?> properties = Class.forName("net.bettercombat.logic.PlayerAttackProperties");
			Class<?> sound = Class.forName("net.bettercombat.api.WeaponAttributes$Sound");
			getAttributes = registry.getMethod("getAttributes", ItemStack.class);
			combineAttackRange = helper.getMethod("combineAttackRange", attributes, double.class);
			isTwoHanded = attributes.getMethod("isTwoHanded");
			getCurrentAttack = helper.getMethod("getCurrentAttack", Player.class, int.class);
			handAttack = hand.getMethod("attack");
			attackHitbox = attack.getMethod("hitbox");
			attackAngle = attack.getMethod("angle");
			attackDamage = attack.getMethod("damageMultiplier");
			attackSound = attack.getMethod("swingSound");
			attackAnimation = attack.getMethod("animation");
			attackUpswing = attack.getMethod("upswing");
			setComboCount = properties.getMethod("setComboCount", int.class);
			playSound = Class.forName("net.bettercombat.utils.SoundHelper").getMethod("playSound", ServerLevel.class, Entity.class, sound);
			ready = true;
			Drifter.LOGGER.info("[Drifter] Better Combat found: Drifters fight with its weapon reach and combos");
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
		}

		return ready;
	}

	private static void fail(Throwable e) {
		ready = false;
		failed = true;
		Drifter.LOGGER.warn("[Drifter] Better Combat is installed but its API didn't match ({}); Drifters fight the vanilla way", e.toString());
	}

	@Nullable
	private static Object attributes(ItemStack stack) {
		if (!active() || stack.isEmpty()) {
			return null;
		}

		try {
			return getAttributes.invoke(null, stack);
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
			return null;
		}
	}

	/** It has Better Combat attributes (a weapon it knows). */
	public static boolean isWeapon(ItemStack stack) {
		return attributes(stack) != null;
	}

	/** Its attack reach with this in hand (the base reach if it isn't one of Better Combat's weapons). */
	public static double range(ItemStack stack, double base) {
		Object attributes = attributes(stack);
		if (attributes == null) {
			return base;
		}

		try {
			return (double)combineAttackRange.invoke(null, attributes, base);
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
			return base;
		}
	}

	/** A two-handed weapon (no shield in the other hand while it's out). */
	public static boolean twoHanded(ItemStack stack) {
		Object attributes = attributes(stack);
		if (attributes == null) {
			return false;
		}

		try {
			return (boolean)isTwoHanded.invoke(attributes);
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
			return false;
		}
	}

	/**
	 * The attack of its combo this swing is (Better Combat's own choice, for the player holding the weapon), with the
	 * player's combo count set to it so Better Combat applies that attack's damage. Null: not a Better Combat weapon.
	 */
	@Nullable
	public static Swing swing(Player player, int combo) {
		if (attributes(player.getMainHandItem()) == null) {
			return null;
		}

		try {
			setComboCount.invoke(player, combo);
			Object hand = getCurrentAttack.invoke(null, player, combo);
			if (hand == null) {
				return null;
			}

			Object attack = handAttack.invoke(hand);
			Object hitbox = attackHitbox.invoke(attack);
			return new Swing(hitbox == null ? "FORWARD_BOX" : ((Enum<?>)hitbox).name(), (double)attackAngle.invoke(attack),
				(double)attackDamage.invoke(attack), attackSound.invoke(attack), String.valueOf(attackAnimation.invoke(attack)),
				(double)attackUpswing.invoke(attack));
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
			return null;
		}
	}

	/** Back to no combo (after the swing). */
	public static void clearCombo(Player player) {
		if (!ready) {
			return;
		}

		try {
			setComboCount.invoke(player, 0);
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
		}
	}

	/** The weapon's own swing sound, as Better Combat plays it for a player. */
	public static void playSwingSound(ServerLevel level, Entity source, Swing swing) {
		if (!ready || swing.sound() == null) {
			return;
		}

		try {
			playSound.invoke(null, level, source, swing.sound());
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
		}
	}
}
