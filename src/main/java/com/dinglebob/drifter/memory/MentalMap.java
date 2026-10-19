package com.dinglebob.drifter.memory;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;

/**
 * The coarse map of the world the Drifter carries in its head (DESIGN.md section 6, "Mental map"): one tile per
 * chunk, recording whether it has looked at it, its biome, and whether it saw water or lava there.
 * Danger and resource scores are counted from the memories in the tile.
 * TODO(milestone 4): known roads with lower path cost; "mined out" marking.
 */
public class MentalMap {
	public static final int MAX_TILES = 4096;

	public static class Tile {
		public String biome = "";
		public boolean water;
		public boolean lava;
		public long lastSeen;

		CompoundTag save(long chunkKey) {
			CompoundTag tag = new CompoundTag();
			tag.putLong("Chunk", chunkKey);
			tag.putString("Biome", this.biome);
			tag.putBoolean("Water", this.water);
			tag.putBoolean("Lava", this.lava);
			tag.putLong("Seen", this.lastSeen);
			return tag;
		}
	}

	/**
	 * Oldest seen first (each one seen moves to the back), so the one to forget is always the first (milestone 16: once full,
	 * finding the oldest went through all 4,096 for every block an eye ray hit).
	 */
	private final it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<Tile> tiles = new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<>();

	public Tile explore(ChunkPos chunk, long now) {
		long key = chunk.toLong();
		Tile tile = this.tiles.getAndMoveToLast(key);
		if (tile == null) {
			tile = new Tile();
			this.tiles.putAndMoveToLast(key, tile);
			while (this.tiles.size() > MAX_TILES) {
				this.tiles.removeFirst();
			}
		}

		tile.lastSeen = now;
		return tile;
	}

	@Nullable
	public Tile get(ChunkPos chunk) {
		return this.tiles.get(chunk.toLong());
	}

	public int exploredCount() {
		return this.tiles.size();
	}

	ListTag save() {
		ListTag list = new ListTag();
		this.tiles.forEach((key, tile) -> list.add(tile.save(key)));
		return list;
	}

	void load(ListTag list) {
		this.tiles.clear();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag tag = list.getCompound(i);
			Tile tile = new Tile();
			tile.biome = tag.getString("Biome");
			tile.water = tag.getBoolean("Water");
			tile.lava = tag.getBoolean("Lava");
			tile.lastSeen = tag.getLong("Seen");
			this.tiles.put(tag.getLong("Chunk"), tile);
		}
	}

	void clear() {
		this.tiles.clear();
	}
}
