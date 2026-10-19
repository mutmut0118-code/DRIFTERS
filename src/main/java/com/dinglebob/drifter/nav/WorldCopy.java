package com.dinglebob.drifter.nav;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.world.PlayerPlacedBlocks;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * A frozen copy of the world round the Drifters, for path searches on other threads (milestone 16, performance: "1000
 * Drifters"; searches had a 2 ms share of the server thread between all of them, and with a crowd each waited its turn
 * and stood about). Chunk sections are copied (their block palettes, as the game copies them itself), with which blocks
 * have a block entity and which were placed by a player or built by a Drifter, and each column's height of solid ground
 * from above. One copy for everybody: Drifters near each other read the same sections.
 *
 * <p>Kept up to date on the server thread: a block change marks its section (LevelChunk#setBlockState), and at the end of
 * the tick the marked sections are copied again. A search reads whatever copy is there when it looks, so it sees the
 * world as it was a tick or so ago, as any search did that ran over several ticks. Copies nobody has used for a minute,
 * and those of unloaded chunks, are dropped.
 */
public final class WorldCopy {
	private WorldCopy() {
	}

	/** Columns nobody's search has used for this long (ticks) are dropped. */
	private static final long FORGET_AFTER = 20 * 60;
	private static final int FORGET_EVERY = 200;

	/** A copied section: frozen, read by any thread. */
	static final class Section {
		final PalettedContainer<BlockState> states;
		final boolean onlyAir;
		/** 4096-bit sets (by x << 8 | y << 4 | z), or null for none. */
		@Nullable
		final long[] blockEntities;
		@Nullable
		final long[] placed;
		@Nullable
		final long[] built;

		Section(PalettedContainer<BlockState> states, boolean onlyAir, @Nullable long[] blockEntities, @Nullable long[] placed, @Nullable long[] built) {
			this.states = states;
			this.onlyAir = onlyAir;
			this.blockEntities = blockEntities;
			this.placed = placed;
			this.built = built;
		}
	}

	/** A copied chunk column: its sections, copied as searches need them, and its ground heights. */
	static final class Column {
		final AtomicReferenceArray<Section> sections;
		/** Per x << 4 | z: the lowest y with nothing solid above it (the ocean-floor heightmap). */
		volatile int[] ground;
		volatile long lastUsed;

		Column(int sectionCount) {
			this.sections = new AtomicReferenceArray<>(sectionCount);
		}
	}

	/** One level's copies. */
	static final class LevelCopy {
		final Map<Long, Column> columns = new ConcurrentHashMap<>();
		/** Sections changed this tick (section keys), copied again at its end. */
		final it.unimi.dsi.fastutil.longs.LongOpenHashSet dirty = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		final int minSection;
		final int sectionCount;
		final int minBuildHeight;
		final int height;

		LevelCopy(Level level) {
			this.minSection = level.getMinSection();
			this.sectionCount = level.getSectionsCount();
			this.minBuildHeight = level.getMinBuildHeight();
			this.height = level.getHeight();
		}
	}

	private static final Map<ResourceKey<Level>, LevelCopy> levels = new ConcurrentHashMap<>();

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerLevel level : server.getAllLevels()) {
				LevelCopy copy = levels.get(level.dimension());
				if (copy == null) {
					continue;
				}

				refresh(level, copy);
				if (server.getTickCount() % FORGET_EVERY == 0) {
					long now = level.getGameTime();
					copy.columns.values().removeIf(column -> now - column.lastUsed > FORGET_AFTER);
				}
			}
		});
		ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			LevelCopy copy = levels.get(level.dimension());
			if (copy != null) {
				copy.columns.remove(chunk.getPos().toLong());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> levels.clear());
	}

	/** A block in this level changed (or its placed/built marks did): its section is copied again at the end of the tick. */
	public static void changed(Level level, BlockPos pos) {
		if (level.isClientSide()) {
			return;
		}

		LevelCopy copy = levels.get(level.dimension());
		if (copy == null || !copy.columns.containsKey(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4))) {
			return;
		}

		// (Locked: a block can be set off the server thread by mods that do work on other threads.)
		synchronized (copy.dirty) {
			copy.dirty.add(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
		}
	}

	private static void refresh(ServerLevel level, LevelCopy copy) {
		long[] keys;
		synchronized (copy.dirty) {
			if (copy.dirty.isEmpty()) {
				return;
			}

			keys = copy.dirty.toLongArray();
			copy.dirty.clear();
		}

		for (long key : keys) {
			int cx = SectionPos.x(key);
			int cz = SectionPos.z(key);
			Column column = copy.columns.get(ChunkPos.asLong(cx, cz));
			if (column == null) {
				continue;
			}

			LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
			if (chunk == null) {
				copy.columns.remove(ChunkPos.asLong(cx, cz));
				continue;
			}

			int index = SectionPos.y(key) - copy.minSection;
			if (index >= 0 && index < copy.sectionCount && column.sections.get(index) != null) {
				column.sections.set(index, copySection(chunk, index, copy));
			}

			column.ground = ground(chunk);
		}
	}

	/**
	 * Before a search on another thread: everything in this box copied (server thread). Sections already copied cost a
	 * lookup; most are, with Drifters about.
	 */
	public static void ensure(ServerLevel level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		LevelCopy copy = levels.computeIfAbsent(level.dimension(), key -> new LevelCopy(level));
		long now = level.getGameTime();
		// Changed already this tick in the box (a block it just placed to climb out of a current): copied now, not at the end
		// of the tick, or the search would plan on the world as it was before (found in testing: stuck in a current,
		// planning again and again from under the block it had just put down).
		synchronized (copy.dirty) {
			if (!copy.dirty.isEmpty()) {
				for (var it = copy.dirty.iterator(); it.hasNext(); ) {
					long key = it.nextLong();
					int sx = SectionPos.x(key);
					int sy = SectionPos.y(key);
					int sz = SectionPos.z(key);
					if (sx < minX >> 4 || sx > maxX >> 4 || sz < minZ >> 4 || sz > maxZ >> 4 || sy < minY >> 4 || sy > maxY >> 4) {
						continue;
					}

					Column column = copy.columns.get(ChunkPos.asLong(sx, sz));
					LevelChunk chunk = level.getChunkSource().getChunkNow(sx, sz);
					int index = sy - copy.minSection;
					if (column != null && chunk != null && index >= 0 && index < copy.sectionCount && column.sections.get(index) != null) {
						column.sections.set(index, copySection(chunk, index, copy));
						column.ground = ground(chunk);
					}

					it.remove();
				}
			}
		}

		int minIndex = Math.max(0, (minY >> 4) - copy.minSection);
		int maxIndex = Math.min(copy.sectionCount - 1, (maxY >> 4) - copy.minSection);
		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
				LevelChunk chunk = null;
				long chunkKey = ChunkPos.asLong(cx, cz);
				Column column = copy.columns.get(chunkKey);
				if (column == null) {
					chunk = level.getChunkSource().getChunkNow(cx, cz);
					if (chunk == null) {
						continue;
					}

					column = new Column(copy.sectionCount);
					column.ground = ground(chunk);
					copy.columns.put(chunkKey, column);
				}

				column.lastUsed = now;
				for (int index = minIndex; index <= maxIndex; index++) {
					if (column.sections.get(index) != null) {
						continue;
					}

					if (chunk == null) {
						chunk = level.getChunkSource().getChunkNow(cx, cz);
						if (chunk == null) {
							break;
						}
					}

					column.sections.set(index, copySection(chunk, index, copy));
				}
			}
		}
	}

	private static Section copySection(LevelChunk chunk, int index, LevelCopy copy) {
		LevelChunkSection section = chunk.getSection(index);
		int baseY = (copy.minSection + index) << 4;
		long[] blockEntities = null;
		for (BlockPos pos : chunk.getBlockEntitiesPos()) {
			if (pos.getY() >= baseY && pos.getY() < baseY + 16) {
				if (blockEntities == null) {
					blockEntities = new long[64];
				}

				set(blockEntities, pos);
			}
		}

		return new Section(section.getStates().copy(), section.hasOnlyAir(), blockEntities, marks(chunk, PlayerPlacedBlocks.PLACED, baseY),
			marks(chunk, PlayerPlacedBlocks.BUILT, baseY));
	}

	@Nullable
	private static long[] marks(LevelChunk chunk, net.fabricmc.fabric.api.attachment.v1.AttachmentType<List<Long>> type, int baseY) {
		List<Long> list = chunk.getAttached(type);
		if (list == null || list.isEmpty()) {
			return null;
		}

		long[] bits = null;
		for (Long key : list) {
			int y = BlockPos.getY(key);
			if (y >= baseY && y < baseY + 16) {
				if (bits == null) {
					bits = new long[64];
				}

				set(bits, BlockPos.of(key));
			}
		}

		return bits;
	}

	private static int[] ground(LevelChunk chunk) {
		int[] ground = new int[256];
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				ground[x << 4 | z] = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
			}
		}

		return ground;
	}

	private static int bit(int x, int y, int z) {
		return (x & 15) << 8 | (y & 15) << 4 | z & 15;
	}

	private static void set(long[] bits, BlockPos pos) {
		int i = bit(pos.getX(), pos.getY(), pos.getZ());
		bits[i >> 6] |= 1L << (i & 63);
	}

	private static boolean get(@Nullable long[] bits, BlockPos pos) {
		if (bits == null) {
			return false;
		}

		int i = bit(pos.getX(), pos.getY(), pos.getZ());
		return (bits[i >> 6] & 1L << (i & 63)) != 0;
	}

	/** A search's view of the copy (any thread; one view per search: it keeps the last column it read). */
	public static NavWorld view(ServerLevel level) {
		return new View(levels.computeIfAbsent(level.dimension(), key -> new LevelCopy(level)));
	}

	/** Copied columns and sections, for `/drifter perf`. */
	public static String report() {
		int columns = 0;
		int sections = 0;
		for (LevelCopy copy : levels.values()) {
			for (Column column : copy.columns.values()) {
				columns++;
				for (int i = 0; i < column.sections.length(); i++) {
					if (column.sections.get(i) != null) {
						sections++;
					}
				}
			}
		}

		return columns + " chunk columns, " + sections + " sections";
	}

	private static final class View implements NavWorld {
		private final LevelCopy copy;
		private long lastKey = Long.MIN_VALUE;
		@Nullable
		private Column lastColumn;

		View(LevelCopy copy) {
			this.copy = copy;
		}

		@Nullable
		private Column column(int cx, int cz) {
			long key = ChunkPos.asLong(cx, cz);
			if (key != this.lastKey) {
				this.lastColumn = this.copy.columns.get(key);
				this.lastKey = key;
			}

			return this.lastColumn;
		}

		@Nullable
		private Section section(BlockPos pos) {
			Column column = this.column(pos.getX() >> 4, pos.getZ() >> 4);
			if (column == null) {
				return null;
			}

			int index = (pos.getY() >> 4) - this.copy.minSection;
			return index < 0 || index >= this.copy.sectionCount ? null : column.sections.get(index);
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			Section section = this.section(pos);
			return section == null ? Blocks.VOID_AIR.defaultBlockState() : section.states.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return this.getBlockState(pos).getFluidState();
		}

		/** (Never a live block entity off the server thread: shapes that ask get their plain form.) */
		@Nullable
		@Override
		public BlockEntity getBlockEntity(BlockPos pos) {
			return null;
		}

		@Override
		public int getHeight() {
			return this.copy.height;
		}

		@Override
		public int getMinBuildHeight() {
			return this.copy.minBuildHeight;
		}

		@Override
		public boolean loaded(BlockPos pos) {
			return this.section(pos) != null;
		}

		@Override
		public boolean hasBlockEntityAt(BlockPos pos) {
			Section section = this.section(pos);
			return section != null && get(section.blockEntities, pos);
		}

		@Override
		public boolean playerPlaced(BlockPos pos) {
			Section section = this.section(pos);
			return section != null && get(section.placed, pos) && !this.getBlockState(pos).isAir();
		}

		@Override
		public boolean drifterBuilt(BlockPos pos) {
			Section section = this.section(pos);
			return section != null && get(section.built, pos) && !this.getBlockState(pos).isAir();
		}

		@Override
		public boolean openAbove(BlockPos pos) {
			Column column = this.column(pos.getX() >> 4, pos.getZ() >> 4);
			int[] ground = column == null ? null : column.ground;
			return ground != null && pos.getY() >= ground[(pos.getX() & 15) << 4 | pos.getZ() & 15];
		}

		@Override
		public boolean sectionMayHave(int sx, int sy, int sz, Predicate<BlockState> test) {
			Column column = this.column(sx, sz);
			int index = sy - this.copy.minSection;
			if (column == null || index < 0 || index >= this.copy.sectionCount) {
				return false;
			}

			Section section = column.sections.get(index);
			return section != null && !section.onlyAir && section.states.maybeHas(test);
		}
	}
}
