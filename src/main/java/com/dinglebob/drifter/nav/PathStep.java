package com.dinglebob.drifter.nav;

import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * One step of a path: move to {@code to} (feet position) by {@code type}, first digging {@code digs} (in order)
 * if they aren't already clear. {@code unknownCells} counts cells it hadn't seen when planning (assumed passable).
 * {@code jump}: for a gap jump, how it takes it (planned with the physics; the walker does just that).
 */
public record PathStep(MoveType type, BlockPos from, BlockPos to, List<BlockPos> digs, int unknownCells, @org.jetbrains.annotations.Nullable JumpPhysics.Mode jump) {
	public PathStep(MoveType type, BlockPos from, BlockPos to, List<BlockPos> digs, int unknownCells) {
		this(type, from, to, digs, unknownCells, null);
	}
}
