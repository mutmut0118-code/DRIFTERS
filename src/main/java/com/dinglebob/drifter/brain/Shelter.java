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

	/** In its finished hut with the door shut (section 11 tier 3): mobs can't get at it. */
	public static boolean inClosedHut(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		if (home == null || !home.hutBuilt || !home.near(drifter, 8.0)) {
			return false;
		}

		com.dinglebob.drifter.building.StarterHut hut = com.dinglebob.drifter.building.StarterHut.of(home);
		return hut.isInside(drifter.blockPosition()) && doorShut(drifter.level(), hut);
	}

	public static boolean doorShut(Level level, com.dinglebob.drifter.building.StarterHut hut) {
		net.minecraft.world.level.block.state.BlockState door = level.getBlockState(hut.doorPos());
		if (door.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
			return !door.getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
		}

		return solid(level, hut.doorPos()) && solid(level, hut.doorPos().above());
	}

	/** Somewhere mobs can't reach it: dug in and sealed, or in its hut with the door shut. */
	public static boolean isSheltered(DrifterEntity drifter) {
		return isSealedIn(drifter) || inClosedHut(drifter);
	}

	private static boolean solid(Level level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
	}
}
