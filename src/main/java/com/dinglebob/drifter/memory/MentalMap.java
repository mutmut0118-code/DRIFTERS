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

	private final Map<Long, Tile> tiles = new LinkedHashMap<>();

	public Tile explore(ChunkPos chunk, long now) {
		Tile tile = this.tiles.computeIfAbsent(chunk.toLong(), key -> new Tile());
		tile.lastSeen = now;
		if (this.tiles.size() > MAX_TILES) {
			this.tiles.entrySet().stream()
				.min(Comparator.comparingLong(entry -> entry.getValue().lastSeen))
				.ifPresent(oldest -> this.tiles.remove(oldest.getKey()));
		}

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
