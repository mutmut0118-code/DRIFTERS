package com.dinglebob.drifter.perception;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Which blocks the Drifter has actually seen (working memory for the pathfinder, DESIGN.md sections 5 and 12).
 * Filled by the same eye rays that notice blocks: every block a ray passes through (air it saw across) and the block
 * it stops on are marked. Mining reveals neighbours, and it always knows the few blocks right around its body.
 *
 * <p>The pathfinder reads the real terrain only for blocks marked here; everything else is unknown and "assumed
 * passable at a moderate cost" (section 12), so it can't plan through a cave or around lava it never saw.
 * Not saved: it fills again within seconds of looking around.
 */
public class TerrainKnowledge implements KnownTerrain {
	/**
	 * A frozen copy of what it knows within {@code radius} (a cube) of {@code center}, for a path search on another thread
	 * (milestone 16): the sections' bits copied, so its eyes can go on marking the live ones.
	 */
	public KnownTerrain copyAround(BlockPos center, int radius) {
		int minSx = SectionPos.blockToSectionCoord(center.getX() - radius);
		int maxSx = SectionPos.blockToSectionCoord(center.getX() + radius);
		int minSy = SectionPos.blockToSectionCoord(center.getY() - radius);
		int maxSy = SectionPos.blockToSectionCoord(center.getY() + radius);
		int minSz = SectionPos.blockToSectionCoord(center.getZ() - radius);
		int maxSz = SectionPos.blockToSectionCoord(center.getZ() + radius);
		it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<BitSet> copy = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
		for (var entry : this.sections.long2ObjectEntrySet()) {
			long key = entry.getLongKey();
			int sx = SectionPos.x(key);
			int sy = SectionPos.y(key);
			int sz = SectionPos.z(key);
			if (sx >= minSx && sx <= maxSx && sy >= minSy && sy <= maxSy && sz >= minSz && sz <= maxSz) {
				copy.put(key, (BitSet)entry.getValue().clone());
			}
		}

		return new KnownTerrain() {
			private long lastKey = Long.MIN_VALUE;
			@org.jetbrains.annotations.Nullable
			private BitSet lastBits;

			@Override
			public boolean isKnown(BlockPos pos) {
				long key = SectionPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getY()),
					SectionPos.blockToSectionCoord(pos.getZ()));
				if (key != this.lastKey) {
					this.lastKey = key;
					this.lastBits = copy.get(key);
				}

				return this.lastBits != null && this.lastBits.get(index(pos.getX(), pos.getY(), pos.getZ()));
			}
		};
	}

	/** Chunk sections (16x16x16) remembered at once; the least recently touched are forgotten first. */
	public static final int MAX_SECTIONS = 512;

	/** Enough sections to cover the path range around it (it grows with `/drifter config path`). */
	private static int maxSections() {
		int across = 2 * com.dinglebob.drifter.DrifterConfig.pathRange() / 16 + 2;
		return Math.max(MAX_SECTIONS, across * across * 6);
	}

	/**
	 * Sections by key, oldest-touched first (performance, 2026-10-17: a boxed-Long map was a third of the server
	 * thread's time in a 30-Drifter profile, with all the pathfinder's lookups going through it).
	 */
	private final it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<BitSet> sections = new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<>();
	/** The section looked up last (lookups come in runs in one place). */
	private long lastKey = Long.MIN_VALUE;
	@org.jetbrains.annotations.Nullable
	private BitSet lastBits;

	@org.jetbrains.annotations.Nullable
	private BitSet section(long key) {
		if (key == this.lastKey) {
			return this.lastBits;
		}

		BitSet bits = this.sections.get(key);
		this.lastKey = key;
		this.lastBits = bits;
		return bits;
	}

	private static int index(int x, int y, int z) {
		return (x & 15) << 8 | (y & 15) << 4 | z & 15;
	}

	public void mark(int x, int y, int z) {
		long key = SectionPos.asLong(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(y), SectionPos.blockToSectionCoord(z));
		BitSet bits = this.section(key);
		if (bits == null) {
			bits = new BitSet(4096);
			this.sections.putAndMoveToLast(key, bits);
			while (this.sections.size() > maxSections()) {
				this.sections.removeFirst();
			}

			this.lastKey = key;
			this.lastBits = bits;
		} else if (this.sections.lastLongKey() != key) {
			// Touched: to the back of the line for forgetting.
			this.sections.getAndMoveToLast(key);
		}

		bits.set(index(x, y, z));
	}

	public void mark(BlockPos pos) {
		this.mark(pos.getX(), pos.getY(), pos.getZ());
	}

	@Override
	public boolean isKnown(BlockPos pos) {
		long key = SectionPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getY()),
			SectionPos.blockToSectionCoord(pos.getZ()));
		BitSet bits = this.section(key);
		return bits != null && bits.get(index(pos.getX(), pos.getY(), pos.getZ()));
	}

	/** Marks every block a sight line passes through, up to and including the block it ended on (3D DDA walk). */
	public void markRay(Vec3 from, Vec3 to) {
		int x = Mth.floor(from.x);
		int y = Mth.floor(from.y);
		int z = Mth.floor(from.z);
		int endX = Mth.floor(to.x);
		int endY = Mth.floor(to.y);
		int endZ = Mth.floor(to.z);
		Vec3 d = to.subtract(from);
		int stepX = (int)Math.signum(d.x);
		int stepY = (int)Math.signum(d.y);
		int stepZ = (int)Math.signum(d.z);
		double tDeltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / d.x);
		double tDeltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / d.y);
		double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / d.z);
		double tMaxX = stepX == 0 ? Double.MAX_VALUE : tDeltaX * (stepX > 0 ? x + 1 - from.x : from.x - x);
		double tMaxY = stepY == 0 ? Double.MAX_VALUE : tDeltaY * (stepY > 0 ? y + 1 - from.y : from.y - y);
		double tMaxZ = stepZ == 0 ? Double.MAX_VALUE : tDeltaZ * (stepZ > 0 ? z + 1 - from.z : from.z - z);
		for (int guard = 0; guard < 256; guard++) {
			this.mark(x, y, z);
			if (x == endX && y == endY && z == endZ) {
				return;
			}

			if (tMaxX < tMaxY && tMaxX < tMaxZ) {
				x += stepX;
				tMaxX += tDeltaX;
			} else if (tMaxY < tMaxZ) {
				y += stepY;
				tMaxY += tDeltaY;
			} else {
				z += stepZ;
				tMaxZ += tDeltaZ;
			}
		}
	}

	/** It always knows the blocks right around its body: touch and the edge of its vision. */
	public void markAround(BlockPos feet, int radius, int below, int above) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -below; dy <= above; dy++) {
					this.mark(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
				}
			}
		}
	}

	/**
	 * Calls {@code action} for every known block within {@code radius} (a cube) of {@code center}. The position passed
	 * is reused between calls: copy it with {@code immutable()} to keep it.
	 */
	public void forEachKnown(BlockPos center, int radius, java.util.function.Consumer<BlockPos.MutableBlockPos> action) {
		this.forEachKnown(center, radius, null, action);
	}

	/**
	 * The same, skipping whole sections {@code sections} turns down (by section coordinates): a scan for something rare
	 * asks the chunk's palette first whether a section can have it at all (performance, 2026-10-17).
	 */
	public void forEachKnown(BlockPos center, int radius, @org.jetbrains.annotations.Nullable SectionFilter sections,
		java.util.function.Consumer<BlockPos.MutableBlockPos> action) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minSx = SectionPos.blockToSectionCoord(center.getX() - radius);
		int maxSx = SectionPos.blockToSectionCoord(center.getX() + radius);
		int minSy = SectionPos.blockToSectionCoord(center.getY() - radius);
		int maxSy = SectionPos.blockToSectionCoord(center.getY() + radius);
		int minSz = SectionPos.blockToSectionCoord(center.getZ() - radius);
		int maxSz = SectionPos.blockToSectionCoord(center.getZ() + radius);
		for (int sx = minSx; sx <= maxSx; sx++) {
			for (int sy = minSy; sy <= maxSy; sy++) {
				for (int sz = minSz; sz <= maxSz; sz++) {
					BitSet bits = this.sections.get(SectionPos.asLong(sx, sy, sz));
					if (bits == null || sections != null && !sections.mayHave(sx, sy, sz)) {
						continue;
					}

					for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
						int x = (sx << 4) + (i >> 8 & 15);
						int y = (sy << 4) + (i >> 4 & 15);
						int z = (sz << 4) + (i & 15);
						if (Math.abs(x - center.getX()) <= radius && Math.abs(y - center.getY()) <= radius && Math.abs(z - center.getZ()) <= radius) {
							action.accept(pos.set(x, y, z));
						}
					}
				}
			}
		}
	}

	/** A known block and what's there now (read straight from its chunk section). */
	@FunctionalInterface
	public interface KnownState {
		void accept(BlockPos.MutableBlockPos pos, net.minecraft.world.level.block.state.BlockState state);
	}

	/**
	 * As {@link #forEachKnown(BlockPos, int, SectionFilter, java.util.function.Consumer)}, with the block that's there now,
	 * read from each chunk section directly instead of through the level for every block (milestone 16: the level lookup
	 * per block was most of a brain decision's time), skipping {@code skip} states (air, kinds already found) before
	 * anything else. Only loaded chunks.
	 */
	public void forEachKnownState(net.minecraft.world.level.Level level, BlockPos center, int radius, @org.jetbrains.annotations.Nullable SectionFilter sections,
		java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> skip, KnownState action) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minSx = SectionPos.blockToSectionCoord(center.getX() - radius);
		int maxSx = SectionPos.blockToSectionCoord(center.getX() + radius);
		int minSy = SectionPos.blockToSectionCoord(center.getY() - radius);
		int maxSy = SectionPos.blockToSectionCoord(center.getY() + radius);
		int minSz = SectionPos.blockToSectionCoord(center.getZ() - radius);
		int maxSz = SectionPos.blockToSectionCoord(center.getZ() + radius);
		for (int sx = minSx; sx <= maxSx; sx++) {
			for (int sz = minSz; sz <= maxSz; sz++) {
				net.minecraft.world.level.chunk.LevelChunk chunk = null;
				boolean looked = false;
				for (int sy = minSy; sy <= maxSy; sy++) {
					BitSet bits = this.sections.get(SectionPos.asLong(sx, sy, sz));
					if (bits == null || sections != null && !sections.mayHave(sx, sy, sz)) {
						continue;
					}

					if (!looked) {
						looked = true;
						chunk = level.getChunkSource().getChunkNow(sx, sz);
					}

					int index = level.getSectionIndexFromSectionY(sy);
					if (chunk == null || index < 0 || index >= chunk.getSections().length) {
						continue;
					}

					var states = chunk.getSection(index).getStates();
					for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
						int lx = i >> 8 & 15;
						int ly = i >> 4 & 15;
						int lz = i & 15;
						var state = states.get(lx, ly, lz);
						if (skip.test(state)) {
							continue;
						}

						int x = (sx << 4) + lx;
						int y = (sy << 4) + ly;
						int z = (sz << 4) + lz;
						if (Math.abs(x - center.getX()) <= radius && Math.abs(y - center.getY()) <= radius && Math.abs(z - center.getZ()) <= radius) {
							action.accept(pos.set(x, y, z), state);
						}
					}
				}
			}
		}
	}

	/**
	 * The highest known block within {@code radius} that passes {@code test}, or null: searched from the top down, a layer
	 * at a time, stopping at the first layer that has one (a "highest point" search used to test every block it knew in a
	 * 64-block radius; 200 ms in one tick). Performance, 2026-10-17.
	 */
	@org.jetbrains.annotations.Nullable
	public BlockPos highestKnown(BlockPos center, int radius, java.util.function.Predicate<BlockPos> test) {
		int minSx = SectionPos.blockToSectionCoord(center.getX() - radius);
		int maxSx = SectionPos.blockToSectionCoord(center.getX() + radius);
		int minSy = SectionPos.blockToSectionCoord(center.getY() - radius);
		int maxSy = SectionPos.blockToSectionCoord(center.getY() + radius);
		int minSz = SectionPos.blockToSectionCoord(center.getZ() - radius);
		int maxSz = SectionPos.blockToSectionCoord(center.getZ() + radius);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int sy = maxSy; sy >= minSy; sy--) {
			for (int ly = 15; ly >= 0; ly--) {
				int y = (sy << 4) + ly;
				if (Math.abs(y - center.getY()) > radius) {
					continue;
				}

				for (int sx = minSx; sx <= maxSx; sx++) {
					for (int sz = minSz; sz <= maxSz; sz++) {
						BitSet bits = this.sections.get(SectionPos.asLong(sx, sy, sz));
						if (bits == null) {
							continue;
						}

						for (int lx = 0; lx < 16; lx++) {
							for (int lz = 0; lz < 16; lz++) {
								if (!bits.get(lx << 8 | ly << 4 | lz)) {
									continue;
								}

								int x = (sx << 4) + lx;
								int z = (sz << 4) + lz;
								if (Math.abs(x - center.getX()) <= radius && Math.abs(z - center.getZ()) <= radius && test.test(pos.set(x, y, z))) {
									return pos.immutable();
								}
							}
						}
					}
				}
			}
		}

		return null;
	}

	/**
	 * The known block nearest {@code center} (within {@code radius}, the same box as forEachKnown) that passes
	 * {@code test}, or null. Sections are searched nearest first and the search stops once no section left could hold
	 * anything nearer: a "nearest spot" scan used to test every block it knew. Performance, 2026-10-17.
	 */
	@org.jetbrains.annotations.Nullable
	public BlockPos nearestKnown(BlockPos center, int radius, @org.jetbrains.annotations.Nullable SectionFilter sections,
		java.util.function.Predicate<BlockPos> test) {
		int cx = center.getX();
		int cy = center.getY();
		int cz = center.getZ();
		java.util.List<long[]> order = new java.util.ArrayList<>();
		for (int sx = SectionPos.blockToSectionCoord(cx - radius); sx <= SectionPos.blockToSectionCoord(cx + radius); sx++) {
			for (int sy = SectionPos.blockToSectionCoord(cy - radius); sy <= SectionPos.blockToSectionCoord(cy + radius); sy++) {
				for (int sz = SectionPos.blockToSectionCoord(cz - radius); sz <= SectionPos.blockToSectionCoord(cz + radius); sz++) {
					long key = SectionPos.asLong(sx, sy, sz);
					if (!this.sections.containsKey(key)) {
						continue;
					}

					// Squared distance from the center to the nearest point of this section.
					long dx = Math.max(0, Math.max((sx << 4) - cx, cx - ((sx << 4) + 15)));
					long dy = Math.max(0, Math.max((sy << 4) - cy, cy - ((sy << 4) + 15)));
					long dz = Math.max(0, Math.max((sz << 4) - cz, cz - ((sz << 4) + 15)));
					order.add(new long[] {dx * dx + dy * dy + dz * dz, key, sx, sy, sz});
				}
			}
		}

		order.sort(java.util.Comparator.comparingLong(entry -> entry[0]));
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos best = null;
		long bestDistance = Long.MAX_VALUE;
		for (long[] entry : order) {
			if (entry[0] >= bestDistance) {
				break;
			}

			int sx = (int)entry[2];
			int sy = (int)entry[3];
			int sz = (int)entry[4];
			if (sections != null && !sections.mayHave(sx, sy, sz)) {
				continue;
			}

			BitSet bits = this.sections.get(entry[1]);
			for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
				int x = (sx << 4) + (i >> 8 & 15);
				int y = (sy << 4) + (i >> 4 & 15);
				int z = (sz << 4) + (i & 15);
				if (Math.abs(x - cx) > radius || Math.abs(y - cy) > radius || Math.abs(z - cz) > radius) {
					continue;
				}

				long d = (long)(x - cx) * (x - cx) + (long)(y - cy) * (y - cy) + (long)(z - cz) * (z - cz);
				if (d < bestDistance && test.test(pos.set(x, y, z))) {
					bestDistance = d;
					best = pos.immutable();
				}
			}
		}

		return best;
	}

	/** Some known block within {@code radius} that passes {@code test} (the first found, in no particular order), or null. */
	@org.jetbrains.annotations.Nullable
	public BlockPos anyKnown(BlockPos center, int radius, @org.jetbrains.annotations.Nullable SectionFilter sections,
		java.util.function.Predicate<BlockPos> test) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int sx = SectionPos.blockToSectionCoord(center.getX() - radius); sx <= SectionPos.blockToSectionCoord(center.getX() + radius); sx++) {
			for (int sy = SectionPos.blockToSectionCoord(center.getY() - radius); sy <= SectionPos.blockToSectionCoord(center.getY() + radius); sy++) {
				for (int sz = SectionPos.blockToSectionCoord(center.getZ() - radius); sz <= SectionPos.blockToSectionCoord(center.getZ() + radius); sz++) {
					BitSet bits = this.sections.get(SectionPos.asLong(sx, sy, sz));
					if (bits == null || sections != null && !sections.mayHave(sx, sy, sz)) {
						continue;
					}

					for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
						int x = (sx << 4) + (i >> 8 & 15);
						int y = (sy << 4) + (i >> 4 & 15);
						int z = (sz << 4) + (i & 15);
						if (Math.abs(x - center.getX()) <= radius && Math.abs(y - center.getY()) <= radius && Math.abs(z - center.getZ()) <= radius
							&& test.test(pos.set(x, y, z))) {
							return pos.immutable();
						}
					}
				}
			}
		}

		return null;
	}

	private static final java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> LAVA =
		state -> state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);

	/**
	 * There could be lava somewhere in this box (false: certainly none, from the sections' own palettes, without looking at
	 * a single block). Box loops looking for lava ask this first (performance, 2026-10-17).
	 */
	public static boolean lavaPossible(net.minecraft.world.level.Level level, BlockPos min, BlockPos max) {
		SectionFilter palette = SectionFilter.palette(level, LAVA);
		for (int sx = min.getX() >> 4; sx <= max.getX() >> 4; sx++) {
			for (int sy = min.getY() >> 4; sy <= max.getY() >> 4; sy++) {
				for (int sz = min.getZ() >> 4; sz <= max.getZ() >> 4; sz++) {
					if (palette.mayHave(sx, sy, sz)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/** Whether a section (by section coordinates) can have what a scan looks for. */
	@FunctionalInterface
	public interface SectionFilter {
		boolean mayHave(int sx, int sy, int sz);

		/** From the level's own palettes: false only if the section certainly has no block matching {@code test}. */
		static SectionFilter palette(net.minecraft.world.level.Level level, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> test) {
			return (sx, sy, sz) -> {
				if (!level.hasChunk(sx, sz)) {
					return false;
				}

				int index = level.getSectionIndexFromSectionY(sy);
				var chunk = level.getChunk(sx, sz);
				if (index < 0 || index >= chunk.getSections().length) {
					return false;
				}

				var section = chunk.getSection(index);
				return !section.hasOnlyAir() && section.getStates().maybeHas(test);
			};
		}
	}

	public void clear() {
		this.sections.clear();
		this.lastKey = Long.MIN_VALUE;
		this.lastBits = null;
	}
}
