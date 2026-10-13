package com.dinglebob.drifter.brain;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Is it somewhere mobs can't get at it (section 11, shelter tiers)? */
public final class Shelter {
	private Shelter() {
	}

	/** Walled in on every side and covered: the body's two blocks are surrounded by solid blocks. */
	public static boolean isSealedIn(DrifterEntity drifter) {
		Level level = drifter.level();
		BlockPos feet = drifter.blockPosition();
		BlockPos head = feet.above();
		if (!solid(level, head.above())) {
			return false;
		}

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (!solid(level, feet.relative(direction)) || !solid(level, head.relative(direction))) {
				return false;
			}
		}

		return true;
	}

	private static boolean solid(Level level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
	}
}
