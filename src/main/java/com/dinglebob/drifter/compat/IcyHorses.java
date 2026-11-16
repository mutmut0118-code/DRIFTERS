package com.dinglebob.drifter.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.Item;

/**
 * Icy's Better Horses, when it's installed (horse culture: docs/superpowers/specs/2026-11-16-horse-culture-design.md): its
 * owners, bond, homes, breeds, gaits and carts, reached by reflection only (nothing needs its jar to build or run). Every
 * call is safe without it: a default comes back and nothing happens. Its names checked against version 2.1.8.
 */
public final class IcyHorses {
	private IcyHorses() {
	}

	public static final String MOD_ID = "icys-better-horses";
	/** Icy's gait gears (its {@code BhGears}): none, walk, trot, canter, gallop. */
	public static final int NO_GEAR = 0;
	public static final int WALK = 1;
	public static final int TROT = 2;
	public static final int CANTER = 3;
	public static final int GALLOP = 4;

	private static volatile Boolean present;
	private static Class<?> data;
	private static Method getOwner;
	private static Method setOwner;
	private static Method getBond;
	private static Method setBond;
	private static Method setHome;
	private static Method getBreed;
	private static Method getGear;
	private static Method setGear;
	private static Method getCart;
	private static Method bonding;
	private static Method riddenSpeed;

	/** Icy's is loaded and its classes are what Drifter expects. */
	public static boolean present() {
		Boolean known = present;
		if (known != null) {
			return known;
		}

		synchronized (IcyHorses.class) {
			if (present == null) {
				present = load();
			}

			return present;
		}
	}

	private static boolean load() {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			return false;
		}

		try {
			data = Class.forName("icy.betterhorses.net.IHorseData");
			getOwner = data.getMethod("bh_getOwner");
			setOwner = data.getMethod("bh_setOwner", UUID.class);
			getBond = data.getMethod("bh_getBond");
			setBond = data.getMethod("bh_setBond", int.class);
			setHome = data.getMethod("bh_setHome", BlockPos.class);
			getBreed = data.getMethod("bh_getBreed");
			getGear = data.getMethod("bh_getGaitGear");
			setGear = data.getMethod("bh_setGaitGear", int.class);
			getCart = data.getMethod("bh_getCartEntity");
			bonding = data.getMethod("bh_bonding");
			riddenSpeed = Class.forName("icy.betterhorses.net.BhGears").getMethod("riddenSpeed", int.class, float.class);
			return true;
		} catch (ReflectiveOperationException | LinkageError e) {
			com.dinglebob.drifter.Drifter.LOGGER.warn("[icy] Icy's Better Horses is installed but not as expected; Drifter leaves it be: {}", e.toString());
			return false;
		}
	}

	private static boolean ours(AbstractHorse horse) {
		return present() && data.isInstance(horse);
	}

	/** (The method asked for after {@link #present()} has looked them up: read first, the handles were still null.) */
	@Nullable
	private static Object call(java.util.function.Supplier<Method> which, AbstractHorse horse, Object... args) {
		if (!ours(horse)) {
			return null;
		}

		Method method = which.get();
		try {
			return method.invoke(horse, args);
		} catch (ReflectiveOperationException | RuntimeException e) {
			com.dinglebob.drifter.Drifter.LOGGER.debug("[icy] {} failed: {}", method.getName(), e.toString());
			return null;
		}
	}

	/** Its owner in Icy's terms, or null. */
	@Nullable
	public static UUID owner(AbstractHorse horse) {
		return call(() -> getOwner, horse) instanceof UUID id ? id : null;
	}

	public static void setOwner(AbstractHorse horse, UUID owner) {
		call(() -> setOwner, horse, owner);
	}

	/** Its bond with its owner (Icy's number; 0 without Icy's). */
	public static int bond(AbstractHorse horse) {
		return call(() -> getBond, horse) instanceof Integer bond ? bond : 0;
	}

	public static void addBond(AbstractHorse horse, int amount) {
		if (ours(horse)) {
			call(() -> setBond, horse, Math.max(0, bond(horse) + amount));
		}
	}

	/** Its home (where Icy's sends it back to): its stall. */
	public static void setHome(AbstractHorse horse, BlockPos home) {
		call(() -> setHome, horse, home.immutable());
	}

	/** Its breed's id ({@code arabian}, {@code thoroughbred}...), or null. */
	@Nullable
	public static String breed(AbstractHorse horse) {
		Object breed = call(() -> getBreed, horse);
		return breed == null ? null : breed.toString().toLowerCase(java.util.Locale.ROOT);
	}

	/** Its gait gear (0 none, 1 walk, 2 trot, 3 canter, 4 gallop). */
	public static int gait(AbstractHorse horse) {
		return call(() -> getGear, horse) instanceof Integer gear ? gear : NO_GEAR;
	}

	public static void setGait(AbstractHorse horse, int gear) {
		if (gait(horse) != gear) {
			call(() -> setGear, horse, gear);
		}
	}

	/**
	 * The speed Icy's gives a horse of this speed in this gear, as for a player rider (Icy's applies it only for players:
	 * a Drifter's riding uses it itself, so the gait's animation follows); the speed unchanged without Icy's.
	 */
	public static float gearSpeed(int gear, float speed) {
		if (!present() || gear == NO_GEAR) {
			return speed;
		}

		try {
			return (Float)riddenSpeed.invoke(null, gear, speed);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return speed;
		}
	}

	/** The cart it pulls, or null. */
	@Nullable
	public static Entity cart(AbstractHorse horse) {
		return call(() -> getCart, horse) instanceof Entity cart ? cart : null;
	}

	/** The treat it likes best, once its owner has found it (Icy's bonding), or null. */
	@Nullable
	public static Item favouriteTreat(AbstractHorse horse) {
		Object feature = call(() -> bonding, horse);
		if (feature == null) {
			return null;
		}

		try {
			Field known = feature.getClass().getDeclaredField("treatKnown");
			Field treat = feature.getClass().getDeclaredField("treat");
			known.setAccessible(true);
			treat.setAccessible(true);
			return Boolean.TRUE.equals(known.get(feature)) && treat.get(feature) instanceof ResourceLocation id ? BuiltInRegistries.ITEM.get(id) : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** A horse of one of Icy's breeds ({@code arabian} and so on) made here, or null (without Icy's, or no such breed). */
	@Nullable
	public static AbstractHorse spawn(ServerLevel level, String breed, BlockPos at) {
		if (!present()) {
			return null;
		}

		var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath(MOD_ID, breed + "_horse")).orElse(null);
		if (type == null) {
			return null;
		}

		Entity made = ((EntityType<?>)type).create(level);
		if (!(made instanceof AbstractHorse horse)) {
			return null;
		}

		horse.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		horse.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null);
		return horse;
	}
}
