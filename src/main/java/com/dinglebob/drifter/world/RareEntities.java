package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The few kinds of entity a Drifter's reflexes watch for every tick (projectiles, lit TNT, falling blocks), kept in a list
 * per level as they come and go (milestone 16: every Drifter searched the world round it for each of these every tick,
 * and there are seldom any). Asking is then a look through that short list, or nothing at all when there are none.
 */
public final class RareEntities {
	private RareEntities() {
	}

	private static final Map<ResourceKey<Level>, Set<Entity>> levels = new HashMap<>();

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (watched(entity)) {
				levels.computeIfAbsent(level.dimension(), key -> new LinkedHashSet<>()).add(entity);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (watched(entity)) {
				Set<Entity> set = levels.get(level.dimension());
				if (set != null) {
					set.remove(entity);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> levels.clear());
	}

	private static boolean watched(Entity entity) {
		return entity instanceof Projectile || entity instanceof PrimedTnt || entity instanceof FallingBlockEntity;
	}

	/** As Level#getEntitiesOfClass, for one of the watched kinds (server side). */
	public static <T extends Entity> List<T> near(Level level, Class<T> type, AABB box, Predicate<? super T> test) {
		Set<Entity> set = levels.get(level.dimension());
		if (set == null || set.isEmpty() || level.isClientSide()) {
			// (A list the caller may sort, as getEntitiesOfClass gives: an unmodifiable empty one crashed a reflex that sorts.)
			return level.isClientSide() ? level.getEntitiesOfClass(type, box, test) : new ArrayList<>(0);
		}

		List<T> found = new ArrayList<>(2);
		for (Entity entity : set) {
			if (type.isInstance(entity) && !entity.isRemoved() && entity.getBoundingBox().intersects(box)) {
				T typed = type.cast(entity);
				if (test.test(typed)) {
					found.add(typed);
				}
			}
		}

		return found;
	}
}
