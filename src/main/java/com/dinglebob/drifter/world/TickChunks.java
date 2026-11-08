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

	/**
	 * False only when no block in the box can match: each chunk section's palette is looked through instead of every block
	 * (milestone 16 scale pass, 2026-11-08: the lava check read two dozen blocks round every Drifter every tick). True when
	 * one might, or a chunk isn't loaded (or off the server thread): then look block by block as before.
	 */
	public static boolean mayHave(Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
		java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> test) {
		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
				LevelChunk chunk = get(level, cx, cz);
				if (chunk == null) {
					return true;
				}

				for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
					int index = chunk.getSectionIndexFromSectionY(sy);
					if (index < 0 || index >= chunk.getSectionsCount()) {
						continue;
					}

					var section = chunk.getSection(index);
					if (!section.hasOnlyAir() && section.maybeHas(test)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * {@link #mayHave} for lava, each section's answer kept for the tick and shared (a thousand Drifters' lava checks went
	 * through the same few hundred sections' palettes every tick; milestone 16 scale pass, 2026-11-08).
	 */
	public static boolean mayHaveLava(Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()) {
			return mayHave(level, minX, minY, minZ, maxX, maxY, maxZ, LAVA);
		}

		long now = server.getServer().getTickCount();
		if (now != lavaTick || level != lavaLevel) {
			lavaTick = now;
			lavaLevel = level;
			lavaSections.clear();
		}

		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
				for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
					long key = net.minecraft.core.SectionPos.asLong(cx, sy, cz);
					byte known = lavaSections.get(key);
					if (known == 0) {
						known = mayHave(level, cx << 4, sy << 4, cz << 4, cx << 4, sy << 4, cz << 4, LAVA) ? (byte)1 : (byte)-1;
						lavaSections.put(key, known);
					}

					if (known > 0) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private static final java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> LAVA =
		state -> state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
	private static long lavaTick = Long.MIN_VALUE;
	@Nullable
	private static Level lavaLevel;
	private static final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap lavaSections = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
}
