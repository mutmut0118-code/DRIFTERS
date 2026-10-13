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
public class TerrainKnowledge {
	/** Chunk sections (16x16x16) remembered at once; the least recently touched are forgotten first. */
	public static final int MAX_SECTIONS = 512;

	/** Enough sections to cover the path range around it (it grows with `/drifter config path`). */
	private static int maxSections() {
		int across = 2 * com.dinglebob.drifter.DrifterConfig.pathRange() / 16 + 2;
		return Math.max(MAX_SECTIONS, across * across * 6);
	}

	private final Map<Long, BitSet> sections = new LinkedHashMap<>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, BitSet> eldest) {
			return this.size() > maxSections();
		}
	};

	private static int index(int x, int y, int z) {
		return (x & 15) << 8 | (y & 15) << 4 | z & 15;
	}

	public void mark(int x, int y, int z) {
		long key = SectionPos.asLong(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(y), SectionPos.blockToSectionCoord(z));
		this.sections.computeIfAbsent(key, k -> new BitSet(4096)).set(index(x, y, z));
	}

	public void mark(BlockPos pos) {
		this.mark(pos.getX(), pos.getY(), pos.getZ());
	}

	public boolean isKnown(BlockPos pos) {
		long key = SectionPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getY()),
			SectionPos.blockToSectionCoord(pos.getZ()));
		BitSet bits = this.sections.get(key);
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
					// Plain get without touching the access order isn't available on LinkedHashMap; a scan touching order is harmless.
					BitSet bits = this.sections.get(SectionPos.asLong(sx, sy, sz));
					if (bits == null) {
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

	public void clear() {
		this.sections.clear();
	}
}
