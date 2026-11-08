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

			if (SOLID.get(entity.getClass())) {
				solids.computeIfAbsent(level.dimension(), key -> new LinkedHashSet<>()).add(entity);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (watched(entity)) {
				Set<Entity> set = levels.get(level.dimension());
				if (set != null) {
					set.remove(entity);
				}
			}

			if (SOLID.get(entity.getClass())) {
				Set<Entity> set = solids.get(level.dimension());
				if (set != null) {
					set.remove(entity);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			levels.clear();
			solids.clear();
		});
		com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter] solid entities known by {} (boats {}, Drifters {})", COLLIDABLE_NAME,
			SOLID.get(net.minecraft.world.entity.vehicle.Boat.class), SOLID.get(com.dinglebob.drifter.entity.DrifterEntity.class));
	}

	/**
	 * Loaded entities of a kind that can be bumped into (a boat, a minecart, a shulker: any whose class says it can be
	 * collided with, modded ones too), per level. Moving, a Drifter asks the game for the solid entities in its way, and
	 * the game looks through every entity round it to find them: in a packed army, hundreds a tick each, to find none
	 * (milestone 16 scale pass, 2026-11-08: a twelfth of a 500v500's time). With none of these near, the answer is none.
	 */
	private static final Map<ResourceKey<Level>, Set<Entity>> solids = new HashMap<>();

	/** The game's own name for Entity#canBeCollidedWith at run time (it differs between the dev setup and a real game). */
	private interface SerialPredicate extends java.util.function.Predicate<Entity>, java.io.Serializable {
	}

	@org.jetbrains.annotations.Nullable
	private static final String COLLIDABLE_NAME = collidableName();

	@org.jetbrains.annotations.Nullable
	private static String collidableName() {
		try {
			SerialPredicate reference = Entity::canBeCollidedWith;
			java.lang.reflect.Method replace = reference.getClass().getDeclaredMethod("writeReplace");
			replace.setAccessible(true);
			return ((java.lang.invoke.SerializedLambda)replace.invoke(reference)).getImplMethodName();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** This kind of entity says (in its own class or one between it and Entity) whether it can be collided with. */
	private static final ClassValue<Boolean> SOLID = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			if (COLLIDABLE_NAME == null) {
				return true;
			}

			try {
				return type.getMethod(COLLIDABLE_NAME).getDeclaringClass() != Entity.class;
			} catch (NoSuchMethodException | RuntimeException e) {
				return true;
			}
		}
	};

	/**
	 * No entity that could be solid is loaded in this box (server side): the game's search for solid entities there would
	 * find none. False whenever it can't be sure (the name lookup failed: then every kind counts as maybe solid).
	 */
	public static boolean noSolidsIn(Level level, AABB box) {
		if (level.isClientSide() || COLLIDABLE_NAME == null) {
			return false;
		}

		Set<Entity> set = solids.get(level.dimension());
		if (set == null || set.isEmpty()) {
			return true;
		}

		for (Entity entity : set) {
			if (!entity.isRemoved() && entity.getBoundingBox().intersects(box)) {
				return false;
			}
		}

		return true;
	}

	/** A kind of entity that may be solid (boats, minecarts, shulkers...: its class says whether it can be collided with). */
	public static boolean solidKind(Entity entity) {
		return SOLID.get(entity.getClass());
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
