package com.dinglebob.drifter.world;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Loaded chunks by position, for the server thread, kept for the tick and shared by every Drifter (milestone 16: eye rays,
 * scans of what it knows and the like asked the server for the same chunks over and over, a thousand Drifters among a few
 * hundred chunks, and each ask goes through the chunk cache's small cache, its holder map and a future). A chunk loaded or
 * unloaded during the tick is seen as it was at its start, as anything that read it then would have.
 */
public final class TickChunks {
	private TickChunks() {
	}

	private static final class LevelChunks {
		long tick = Long.MIN_VALUE;
		final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<LevelChunk> chunks = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
		/** Asked about and not loaded. */
		final it.unimi.dsi.fastutil.longs.LongOpenHashSet missing = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
	}

	private static final java.util.Map<net.minecraft.resources.ResourceKey<Level>, LevelChunks> levels = new java.util.HashMap<>();

	/** The world closed: nothing kept of it. */
	public static void clear() {
		levels.clear();
	}

	/** The loaded chunk there, or null (server thread only; anything else gets null, as getChunkNow gives). */
	@Nullable
	public static LevelChunk get(Level level, int cx, int cz) {
		if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()) {
			return null;
		}

		LevelChunks known = levels.computeIfAbsent(level.dimension(), key -> new LevelChunks());
		// (By the server's own tick count: it goes on while the game is frozen.)
		long now = server.getServer().getTickCount();
		if (known.tick != now) {
			known.tick = now;
			known.chunks.clear();
			known.missing.clear();
		}

		long key = ChunkPos.asLong(cx, cz);
		LevelChunk chunk = known.chunks.get(key);
		if (chunk != null || known.missing.contains(key)) {
			return chunk;
		}

		chunk = server.getChunkSource().getChunkNow(cx, cz);
		if (chunk == null) {
			known.missing.add(key);
		} else {
			known.chunks.put(key, chunk);
		}

		return chunk;
	}
}
