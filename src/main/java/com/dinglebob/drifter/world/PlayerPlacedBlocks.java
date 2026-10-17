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

	/**
	 * Blocks Drifters built (user, 2026-10-17: "people mining their own hut/house to bridge somewhere or pillar, but there
	 * is door right there"): what they placed themselves, and the huts and houses world generation and catch-up put up.
	 * They don't dig these for a route or take them for blocks either (their own scaffolding aside), so they use the door.
	 */
	public static final AttachmentType<List<Long>> BUILT = AttachmentRegistry.createPersistent(Drifter.id("drifter_built"),
		Codec.LONG.listOf());

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

	/** A Drifter built this block (one place). */
	public static void recordBuilt(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
		if (level.isClientSide()) {
			return;
		}

		var chunk = level.getChunk(pos);
		List<Long> current = chunk.getAttachedOrElse(BUILT, List.of());
		long key = pos.asLong();
		if (!current.contains(key)) {
			List<Long> updated = new ArrayList<>(current);
			updated.add(key);
			chunk.setAttached(BUILT, updated);
		}
	}

	/** Every non-air block in this box is a Drifter's build (a hut or house it put up). */
	public static void recordBuiltBox(net.minecraft.world.level.LevelAccessor level, net.minecraft.world.level.levelgen.structure.BoundingBox box) {
		if (level.isClientSide()) {
			return;
		}

		java.util.Map<Long, it.unimi.dsi.fastutil.longs.LongArrayList> byChunk = new java.util.HashMap<>();
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!level.getBlockState(pos).isAir()) {
				byChunk.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4),
					k -> new it.unimi.dsi.fastutil.longs.LongArrayList()).add(pos.asLong());
			}
		}

		for (var entry : byChunk.entrySet()) {
			var chunk = level.getChunk(net.minecraft.world.level.ChunkPos.getX(entry.getKey()), net.minecraft.world.level.ChunkPos.getZ(entry.getKey()));
			List<Long> current = chunk.getAttachedOrElse(BUILT, List.of());
			java.util.LinkedHashSet<Long> merged = new java.util.LinkedHashSet<>(current);
			entry.getValue().forEach(merged::add);
			chunk.setAttached(BUILT, new ArrayList<>(merged));
		}
	}

	/** A player placed it, or a Drifter built it: not to be dug or taken. */
	public static boolean isBuilt(Level level, BlockPos pos) {
		return isPlayerPlaced(level, pos) || isDrifterBuilt(level, pos);
	}

	public static boolean isDrifterBuilt(Level level, BlockPos pos) {
		return contains(level, pos, BUILT, BUILT_LOOKUPS);
	}

	/** True if a player placed the block that's there now (stale entries for blocks since removed are ignored). */
	public static boolean isPlayerPlaced(Level level, BlockPos pos) {
		return contains(level, pos, PLACED, LOOKUPS);
	}

	private static boolean contains(Level level, BlockPos pos, AttachmentType<List<Long>> type, java.util.Map<LevelChunk, Lookup> lookups) {
		if (!level.hasChunkAt(pos) || level.getBlockState(pos).isAir()) {
			return false;
		}

		LevelChunk chunk = level.getChunkAt(pos);
		List<Long> placed = chunk.getAttached(type);
		if (placed == null || placed.isEmpty()) {
			return false;
		}

		// A hash set of the chunk's list, made once per version of the list (the list is replaced on every change): looking
		// through a big base's thousands of entries for every block the pathfinder weighed was slow. Performance 2026-10-17.
		Lookup lookup = lookups.get(chunk);
		if (lookup == null || lookup.source != placed) {
			it.unimi.dsi.fastutil.longs.LongOpenHashSet set = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(placed.size());
			for (Long key : placed) {
				set.add(key.longValue());
			}

			lookup = new Lookup(placed, set);
			lookups.put(chunk, lookup);
		}

		return lookup.set.contains(pos.asLong());
	}

	private record Lookup(List<Long> source, it.unimi.dsi.fastutil.longs.LongOpenHashSet set) {
	}

	/** Per loaded chunk (forgotten with the chunk). */
	private static final java.util.Map<LevelChunk, Lookup> LOOKUPS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
	private static final java.util.Map<LevelChunk, Lookup> BUILT_LOOKUPS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
}
