package com.dinglebob.drifter.api;

import java.util.List;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;

/**
 * The work a town's people share (set by Drifter: Civilizations with {@link DrifterApi#setTownWork}; not saved, set again
 * while it's in a town): its roads made into path with a shovel, its ground kept lit, the ground round each one's house
 * eased into the land. Each block of it done by a Drifter, with what it has, at a player's pace.
 */
public interface TownWork {
	/** The columns (x and z; the y is ignored) of the town's roads, the ones near the middle first. */
	List<BlockPos> roads(DrifterEntity drifter);

	/** This column is the town's own ground (kept lit at night): its roads and round its houses, not the woods round it. */
	boolean townGround(int x, int z);

	/** The town's middle (its plaza). */
	BlockPos center();

	/** How far from the middle its ground goes. */
	int radius();
}
