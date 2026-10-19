package com.dinglebob.drifter.nav;

import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.world.PlayerPlacedBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * What the pathfinder reads of the world, and nothing else (milestone 16, performance): either the live world (on the
 * server thread) or a frozen copy of it ({@link WorldCopy}) that a path search on another thread can read safely. A
 * {@link BlockGetter} as well, so block shapes and break speeds work on either. Never the Drifter's knowledge: that's
 * {@link com.dinglebob.drifter.perception.KnownTerrain}.
 */
public interface NavWorld extends BlockGetter {
	/** The chunk is there to read (an unloaded one is never walked into). */
	boolean loaded(BlockPos pos);

	/** A block entity is there (a chest, a furnace): never dug through. */
	boolean hasBlockEntityAt(BlockPos pos);

	/** A player placed the block that's there now. */
	boolean playerPlaced(BlockPos pos);

	/** A Drifter built the block that's there now. */
	boolean drifterBuilt(BlockPos pos);

	/** Nothing solid overhead all the way up (water doesn't count): somewhere to come up for air. */
	boolean openAbove(BlockPos pos);

	/** False only if this section (section coordinates) certainly has no block passing {@code test} (from its palette). */
	boolean sectionMayHave(int sx, int sy, int sz, Predicate<BlockState> test);

	/** The live world, read on the server thread. */
	static NavWorld live(Level level) {
		return new Live(level);
	}

	/** The live world through its chunks, keeping the last chunk read (reads come in runs close together). */
	final class Live implements NavWorld {
		private final Level level;
		private long lastChunkKey = Long.MIN_VALUE;
		private long lastChunkTick = Long.MIN_VALUE;
		@Nullable
		private LevelChunk lastChunk;

		Live(Level level) {
			this.level = level;
		}

		@Nullable
		private LevelChunk chunk(int cx, int cz) {
			long key = ChunkPos.asLong(cx, cz);
			long now = this.level.getGameTime();
			// (Looked up again each tick: a chunk unloaded and loaded again is a new object.)
			if (key != this.lastChunkKey || now != this.lastChunkTick) {
				this.lastChunk = this.level.getChunkSource().getChunkNow(cx, cz);
				this.lastChunkKey = key;
				this.lastChunkTick = now;
			}

			return this.lastChunk;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			LevelChunk chunk = this.chunk(pos.getX() >> 4, pos.getZ() >> 4);
			return chunk == null ? Blocks.VOID_AIR.defaultBlockState() : chunk.getBlockState(pos);
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return this.getBlockState(pos).getFluidState();
		}

		@Nullable
		@Override
		public BlockEntity getBlockEntity(BlockPos pos) {
			return this.level.getBlockEntity(pos);
		}

		@Override
		public int getHeight() {
			return this.level.getHeight();
		}

		@Override
		public int getMinBuildHeight() {
			return this.level.getMinBuildHeight();
		}

		@Override
		public boolean loaded(BlockPos pos) {
			return this.level.isLoaded(pos);
		}

		@Override
		public boolean hasBlockEntityAt(BlockPos pos) {
			return this.level.getBlockEntity(pos) != null;
		}

		@Override
		public boolean playerPlaced(BlockPos pos) {
			return PlayerPlacedBlocks.isPlayerPlaced(this.level, pos);
		}

		@Override
		public boolean drifterBuilt(BlockPos pos) {
			return PlayerPlacedBlocks.isDrifterBuilt(this.level, pos);
		}

		@Override
		public boolean openAbove(BlockPos pos) {
			LevelChunk chunk = this.chunk(pos.getX() >> 4, pos.getZ() >> 4);
			return chunk != null && pos.getY() >= chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, pos.getX() & 15, pos.getZ() & 15);
		}

		@Override
		public boolean sectionMayHave(int sx, int sy, int sz, Predicate<BlockState> test) {
			return com.dinglebob.drifter.perception.TerrainKnowledge.SectionFilter.palette(this.level, test).mayHave(sx, sy, sz);
		}
	}
}
