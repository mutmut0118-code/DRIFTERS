package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Every body in a level (living things, and the solid kinds: boats, minecarts; and apart from them, dropped items and
 * experience orbs), dropped once a tick into columns 8 blocks
 * across, for the questions a crowd asks over and over: who's round me (Senses), who am I pressed against (pushing), is a
 * friend in my arrow's path (Archery). Milestone 16 crowd pass, 2026-11-08: in a 500v500 every soldier asked the game's
 * entity sections, 16 blocks a side and a hundred soldiers deep, several times a tick (a ninth of the server's time).
 *
 * <p>Built at the first question of a tick, from where everyone stood then; each answer tests the body's box as it is now,
 * with a margin for a tick's movement. A body that came into the world during the tick is in from the next one (a 50 ms
 * glance later). Server thread only: elsewhere (and for very wide boxes) {@link #bodies} answers null and the caller asks
 * the game as before.
 */
public final class BodyGrid {
	private BodyGrid() {
	}

	/** Columns are 1 << SHIFT blocks across. */
	private static final int SHIFT = 3;
	/** Half the widest everyday body (a ghast is 4 across) and a tick's movement and more: looked for this far outside the box. */
	private static final double MARGIN = 3.0;
	/** Boxes wider than this ask the game (the far sight's: a few hundred columns would cost more than the sections). */
	public static final double MAX_BOX = 140.0;

	private static final class LevelGrid {
		long tick = Long.MIN_VALUE;
		final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ArrayList<Entity>> cells = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
		/** Dropped items and experience orbs, the same way. */
		final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ArrayList<Entity>> loose = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
		/** Emptied lists from the last build, used again. */
		final ArrayList<ArrayList<Entity>> spare = new ArrayList<>();
	}

	private static final Map<ResourceKey<Level>, LevelGrid> levels = new HashMap<>();

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> levels.clear());
	}

	@Nullable
	private static LevelGrid grid(Level level) {
		if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()) {
			return null;
		}

		LevelGrid grid = levels.computeIfAbsent(level.dimension(), key -> new LevelGrid());
		long now = server.getServer().getTickCount();
		if (grid.tick != now) {
			grid.tick = now;
			release(grid, grid.cells);
			release(grid, grid.loose);
			for (Entity entity : server.getAllEntities()) {
				if (entity == null || entity.isRemoved()) {
					continue;
				}

				var map = entity instanceof LivingEntity || RareEntities.solidKind(entity) ? grid.cells
					: entity instanceof net.minecraft.world.entity.item.ItemEntity || entity instanceof net.minecraft.world.entity.ExperienceOrb ? grid.loose : null;
				if (map == null) {
					continue;
				}

				long key = key(Mth.floor(entity.getX()) >> SHIFT, Mth.floor(entity.getZ()) >> SHIFT);
				ArrayList<Entity> list = map.get(key);
				if (list == null) {
					list = grid.spare.isEmpty() ? new ArrayList<>(8) : grid.spare.remove(grid.spare.size() - 1);
					map.put(key, list);
				}

				list.add(entity);
			}
		}

		return grid;
	}

	private static void release(LevelGrid grid, it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ArrayList<Entity>> map) {
		for (ArrayList<Entity> list : map.values()) {
			list.clear();
			if (grid.spare.size() < 4096) {
				grid.spare.add(list);
			}
		}

		map.clear();
	}

	private static long key(int cx, int cz) {
		return (long)cx << 32 | cz & 0xFFFFFFFFL;
	}

	/**
	 * As Level#getEntities(except, box, test) for bodies (living things, boats, minecarts and the like): those whose box
	 * meets {@code box}. Null when this can't answer (off the server thread, or a box wider than {@link #MAX_BOX}): ask the
	 * game then.
	 */
	@Nullable
	public static List<Entity> bodies(Level level, @Nullable Entity except, AABB box, Predicate<? super Entity> test) {
		if (box.getXsize() > MAX_BOX || box.getZsize() > MAX_BOX) {
			return null;
		}

		LevelGrid grid = grid(level);
		return grid == null ? null : find(grid.cells, except, box, test);
	}

	/**
	 * As {@link #bodies}, for dropped items and experience orbs (picking up, noticing loot: a battlefield's dropped gear lies
	 * among the soldiers).
	 */
	@Nullable
	@SuppressWarnings("unchecked")
	public static <T extends Entity> List<T> looseOf(Level level, Class<T> type, AABB box, Predicate<? super T> test) {
		if (box.getXsize() > MAX_BOX || box.getZsize() > MAX_BOX) {
			return null;
		}

		LevelGrid grid = grid(level);
		return grid == null ? null : (List<T>)(List<?>)find(grid.loose, null, box, entity -> type.isInstance(entity) && test.test((T)entity));
	}

	private static List<Entity> find(it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ArrayList<Entity>> cells, @Nullable Entity except, AABB box,
		Predicate<? super Entity> test) {
		List<Entity> found = new ArrayList<>();
		int minX = Mth.floor(box.minX - MARGIN) >> SHIFT;
		int maxX = Mth.floor(box.maxX + MARGIN) >> SHIFT;
		int minZ = Mth.floor(box.minZ - MARGIN) >> SHIFT;
		int maxZ = Mth.floor(box.maxZ + MARGIN) >> SHIFT;
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				ArrayList<Entity> list = cells.get(key(cx, cz));
				if (list == null) {
					continue;
				}

				for (int i = 0, n = list.size(); i < n; i++) {
					Entity entity = list.get(i);
					if (entity != except && !entity.isRemoved() && entity.getBoundingBox().intersects(box) && test.test(entity)) {
						found.add(entity);
					}
				}
			}
		}

		return found;
	}

	/** {@link #bodies}, of one class. */
	@Nullable
	@SuppressWarnings("unchecked")
	public static <T extends Entity> List<T> bodiesOf(Level level, Class<T> type, AABB box, Predicate<? super T> test) {
		return (List<T>)(List<?>)bodies(level, null, box, entity -> type.isInstance(entity) && test.test((T)entity));
	}
}
