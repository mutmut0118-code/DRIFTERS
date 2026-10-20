package com.dinglebob.drifter.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Small checks for the mods Drifters know about without depending on them (milestone 19): by registry id, so nothing
 * here needs the mod to be installed.
 */
public final class ModCompat {
	/** Guard Villagers' guard. */
	private static final ResourceLocation GUARD = ResourceLocation.fromNamespaceAndPath("guardvillagers", "guard");

	private ModCompat() {
	}

	/** A village guard (Guard Villagers): the village's defender, like an iron golem. */
	public static boolean isGuard(Entity entity) {
		return is(entity.getType(), GUARD);
	}

	private static boolean is(EntityType<?> type, ResourceLocation id) {
		return id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
	}
}
