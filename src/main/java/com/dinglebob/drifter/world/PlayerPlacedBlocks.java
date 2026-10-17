package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import com.dinglebob.drifter.Drifter;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Where real players placed blocks (DESIGN.md section 11, "Respecting other people's builds"). The Drifter can't tell
 * a natural block from a player's build by looking, so the mod keeps a small per-chunk list, saved with the chunk
 * (section 20, "Player-placed blocks: per-chunk attachment"). A Drifter never digs through these.
 * TODO(milestone 9): allow it for Drifters hostile to that player when player griefing is enabled in config.
 */
public final class PlayerPlacedBlocks {
	public static final AttachmentType<List<Long>> PLACED = AttachmentRegistry.createPersistent(Drifter.id("player_placed_blocks"),
		Codec.LONG.listOf());

	private PlayerPlacedBlocks() {
	}

	/** Forces class loading so the attachment type is registered during mod init. */
	public static void register() {
	}

	public static void recordPlaced(Level level, BlockPos pos) {
		if (level.isClientSide || !level.hasChunkAt(pos)) {
			return;
		}

		LevelChunk chunk = level.getChunkAt(pos);
		List<Long> current = chunk.getAttachedOrElse(PLACED, List.of());
		long key = pos.asLong();
		if (current.contains(key)) {
			return;
		}

		List<Long> updated = new ArrayList<>(current);
		updated.add(key);
		chunk.setAttached(PLACED, updated);
	}

	/** True if a player placed the block that's there now (stale entries for blocks since removed are ignored). */
	public static boolean isPlayerPlaced(Level level, BlockPos pos) {
		if (!level.hasChunkAt(pos) || level.getBlockState(pos).isAir()) {
			return false;
		}

		LevelChunk chunk = level.getChunkAt(pos);
		List<Long> placed = chunk.getAttached(PLACED);
		if (placed == null || placed.isEmpty()) {
			return false;
		}

		// A hash set of the chunk's list, made once per version of the list (the list is replaced on every change): looking
		// through a big base's thousands of entries for every block the pathfinder weighed was slow. Performance 2026-10-17.
		Lookup lookup = LOOKUPS.get(chunk);
		if (lookup == null || lookup.source != placed) {
			it.unimi.dsi.fastutil.longs.LongOpenHashSet set = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(placed.size());
			for (Long key : placed) {
				set.add(key.longValue());
			}

			lookup = new Lookup(placed, set);
			LOOKUPS.put(chunk, lookup);
		}

		return lookup.set.contains(pos.asLong());
	}

	private record Lookup(List<Long> source, it.unimi.dsi.fastutil.longs.LongOpenHashSet set) {
	}

	/** Per loaded chunk (forgotten with the chunk). */
	private static final java.util.Map<LevelChunk, Lookup> LOOKUPS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
}
