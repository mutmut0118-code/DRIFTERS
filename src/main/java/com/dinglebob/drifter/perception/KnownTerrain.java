package com.dinglebob.drifter.perception;

import net.minecraft.core.BlockPos;

/**
 * Which blocks the Drifter has seen: its live {@link TerrainKnowledge}, or a frozen copy of part of it for a path search
 * on another thread (milestone 16).
 */
public interface KnownTerrain {
	boolean isKnown(BlockPos pos);
}
