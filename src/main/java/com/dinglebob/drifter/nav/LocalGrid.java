package com.dinglebob.drifter.nav;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;

/**
 * Per-block values for one path search, stored the way a game engine stores a voxel grid: plain arrays of 16x16x16
 * sections round where the search starts, indexed straight from the block's coordinates (no hashing). Only the sections
 * a search touches are allocated. Anything outside the grid falls back to a hash map.
 *
 * <p>Performance, 2026-10-17 (user: "rockstar games level of optimizing"): in a 30-Drifter profile the pathfinder's
 * hash-map lookups were the single biggest cost on the server thread.
 */
final class LocalGrid<T> {
	private static final int SECTION = 16;

	private final int minX;
	private final int minY;
	private final int minZ;
	/** Sections along each axis. */
	private final int span;
	private final int extent;
	private final Object[][] sections;
	@Nullable
	private Long2ObjectOpenHashMap<T> outside;

	LocalGrid(BlockPos center, int radius) {
		this.span = (2 * radius + SECTION) / SECTION + 1;
		this.extent = this.span * SECTION;
		int half = this.extent / 2;
		this.minX = center.getX() - half;
		this.minY = center.getY() - half;
		this.minZ = center.getZ() - half;
		this.sections = new Object[this.span * this.span * this.span][];
	}

	@Nullable
	@SuppressWarnings("unchecked")
	T get(BlockPos pos) {
		int x = pos.getX() - this.minX;
		int y = pos.getY() - this.minY;
		int z = pos.getZ() - this.minZ;
		if (x < 0 || y < 0 || z < 0 || x >= this.extent || y >= this.extent || z >= this.extent) {
			return this.outside == null ? null : this.outside.get(pos.asLong());
		}

		Object[] section = this.sections[((x >> 4) * this.span + (y >> 4)) * this.span + (z >> 4)];
		return section == null ? null : (T)section[(x & 15) << 8 | (y & 15) << 4 | z & 15];
	}

	void put(BlockPos pos, T value) {
		int x = pos.getX() - this.minX;
		int y = pos.getY() - this.minY;
		int z = pos.getZ() - this.minZ;
		if (x < 0 || y < 0 || z < 0 || x >= this.extent || y >= this.extent || z >= this.extent) {
			if (this.outside == null) {
				this.outside = new Long2ObjectOpenHashMap<>();
			}

			this.outside.put(pos.asLong(), value);
			return;
		}

		int index = ((x >> 4) * this.span + (y >> 4)) * this.span + (z >> 4);
		Object[] section = this.sections[index];
		if (section == null) {
			section = new Object[SECTION * SECTION * SECTION];
			this.sections[index] = section;
		}

		section[(x & 15) << 8 | (y & 15) << 4 | z & 15] = value;
	}
}
