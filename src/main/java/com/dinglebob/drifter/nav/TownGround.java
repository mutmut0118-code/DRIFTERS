package com.dinglebob.drifter.nav;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * Which columns round here are its town's ground, copied for a route worked out on a path thread (the town's own check
 * isn't safe off the server thread). Kept per town, a chunk at a time, and made again every few minutes (towns grow).
 * Routes cost more where they'd pillar or bridge on it (the user's screenshots, 2026-10-30: lone cobblestone blocks on
 * town roads, left by Drifters stepping up a ledge on an errand instead of walking round).
 */
public final class TownGround {
	private static final long FRESH = 20L * 60 * 5;

	private record Chunk(long[] bits, long made) {
	}

	/** Server thread only. */
	private static final Map<TownWork, Map<Long, Chunk>> CACHE = new WeakHashMap<>();

	private final Map<Long, long[]> chunks;

	private TownGround(Map<Long, long[]> chunks) {
		this.chunks = chunks;
	}

	/** Its town's ground within {@code radius} of {@code center}, or null if it has no town (server thread). */
	@Nullable
	public static TownGround around(DrifterEntity drifter, BlockPos center, int radius) {
		TownWork town = drifter.getTownWork();
		if (town == null) {
			return null;
		}

		long now = drifter.level().getGameTime();
		Map<Long, Chunk> cache = CACHE.computeIfAbsent(town, key -> new HashMap<>());
		Map<Long, long[]> out = new HashMap<>();
		for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
			for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
				long key = ChunkPos.asLong(cx, cz);
				Chunk chunk = cache.get(key);
				if (chunk == null || now - chunk.made() > FRESH || now < chunk.made()) {
					long[] bits = new long[4];
					for (int i = 0; i < 256; i++) {
						if (town.townGround((cx << 4) + (i & 15), (cz << 4) + (i >> 4))) {
							bits[i >> 6] |= 1L << (i & 63);
						}
					}

					chunk = new Chunk(bits, now);
					cache.put(key, chunk);
				}

				out.put(key, chunk.bits());
			}
		}

		return new TownGround(out);
	}

	/** This column is the town's ground (any thread). */
	public boolean contains(int x, int z) {
		long[] bits = this.chunks.get(ChunkPos.asLong(x >> 4, z >> 4));
		if (bits == null) {
			return false;
		}

		int i = ((z & 15) << 4) | (x & 15);
		return (bits[i >> 6] >>> (i & 63) & 1L) != 0;
	}
}
