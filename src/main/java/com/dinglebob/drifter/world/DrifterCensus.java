package com.dinglebob.drifter.world;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;

/**
 * How many Drifters are loaded and roughly where (user, 2026-10-17: "in a few minutes it went from 10 to 60 drifters ...
 * should we set a max?"). World generation asks it before making one: past the cap (setting "Most Drifters loaded")
 * none are made, and a single one isn't put where three are already within 64 blocks. Safe to ask from world-generation
 * threads: counts are atomic and positions are a snapshot taken every two seconds. Debug and spawning only: nothing a
 * Drifter decides reads this.
 */
public final class DrifterCensus {
	private DrifterCensus() {
	}

	private static final int SNAPSHOT_TICKS = 40;
	/** A lone new one isn't put where this many are already this close. */
	private static final int CROWD = 3;
	private static final double CROWD_RANGE = 64.0;

	private static final Map<UUID, DrifterEntity> loaded = new ConcurrentHashMap<>();
	/** Made since the last snapshot (world generation adds them before they count as loaded). */
	private static final AtomicInteger recent = new AtomicInteger();
	/** x, z of every loaded Drifter (all dimensions together: a rough guide). */
	private static volatile long[] spots = new long[0];

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof DrifterEntity drifter) {
				loaded.put(drifter.getUUID(), drifter);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof DrifterEntity drifter) {
				loaded.remove(drifter.getUUID());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			loaded.clear();
			recent.set(0);
			spots = new long[0];
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SNAPSHOT_TICKS != 0) {
				return;
			}

			long[] now = new long[loaded.size()];
			int i = 0;
			for (DrifterEntity drifter : loaded.values()) {
				if (i < now.length && !drifter.isRemoved()) {
					now[i++] = BlockPos.asLong(drifter.getBlockX(), 0, drifter.getBlockZ());
				}
			}

			spots = java.util.Arrays.copyOf(now, i);
			recent.set(0);
		});
	}

	/** Loaded now (plus any just made). */
	public static int count() {
		return loaded.size() + recent.get();
	}

	/**
	 * Room for this many more here: under the cap, and (for a lone one) not where three are already close. Counts them as
	 * made when it says yes.
	 */
	public static boolean roomFor(BlockPos at, int how) {
		if (count() + how > DrifterConfig.spawn(DrifterConfig.Spawn.MAX_DRIFTERS)) {
			return false;
		}

		if (how == 1) {
			int near = 0;
			for (long spot : spots) {
				double dx = BlockPos.getX(spot) - at.getX();
				double dz = BlockPos.getZ(spot) - at.getZ();
				if (dx * dx + dz * dz < CROWD_RANGE * CROWD_RANGE && ++near >= CROWD) {
					return false;
				}
			}
		}

		recent.addAndGet(how);
		return true;
	}
}
