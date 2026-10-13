package com.dinglebob.drifter.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;

import net.minecraft.world.entity.LivingEntity;

/**
 * Lithium (optional, detected at runtime, never required) skips a mob's "has its equipment changed?" check unless its
 * vanilla equipment lists changed, and makes an exception only for players, whose equipment lives in an inventory.
 * A Drifter's equipment lives in its player-style inventory too, so without this a hotbar switch never reached clients
 * (found in testing: it mined with a pickaxe while showing a log) and armor and weapon attribute changes were skipped.
 * Like Lithium does for players, every tick the Drifter is marked as possibly changed.
 */
public final class LithiumCompat {
	private static final String TRACKING_ENTITY = "net.caffeinemc.mods.lithium.common.entity.EquipmentEntity$EquipmentTrackingEntity";
	@Nullable
	private static final MethodHandle MARK_CHANGED = find();

	private LithiumCompat() {
	}

	@Nullable
	private static MethodHandle find() {
		try {
			Class<?> type = Class.forName(TRACKING_ENTITY);
			MethodHandle handle = MethodHandles.publicLookup().findVirtual(type, "lithium$onEquipmentChanged", MethodType.methodType(void.class));
			Drifter.LOGGER.info("Lithium found: Drifters mark their equipment as changed every tick, like players");
			return handle.asType(MethodType.methodType(void.class, Object.class));
		} catch (ClassNotFoundException e) {
			return null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			Drifter.LOGGER.warn("Lithium found but its equipment tracking couldn't be hooked; Drifter held items may not show correctly", e);
			return null;
		}
	}

	/** Makes the next equipment check run in full, as vanilla does. */
	public static void markEquipmentChanged(LivingEntity entity) {
		if (MARK_CHANGED == null) {
			return;
		}

		try {
			MARK_CHANGED.invokeExact((Object)entity);
		} catch (ClassCastException e) {
			// This entity wasn't given Lithium's tracking (an option turned off): vanilla checks it every tick anyway.
		} catch (Throwable e) {
			throw new IllegalStateException("Lithium equipment hook failed", e);
		}
	}
}
