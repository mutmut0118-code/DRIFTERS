package com.dinglebob.drifter.brain;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Its town's ground (a Drifter living in a town: {@code TownWork}), kept free of holes: mine stairs and digging down for
 * stone start outside it (found by the user: 1x1 shafts and plugged holes all over a town's grass, and cobblestone left
 * about).
 */
public final class TownEdge {
	private TownEdge() {
	}

	/** Blocks past the edge of its town's ground a dig starts. */
	private static final int PAST = 4;

	/** This column is its town's ground (no digging into it). */
	public static boolean inTown(DrifterEntity drifter, BlockPos pos) {
		var town = drifter.getTownWork();
		return town != null && town.townGround(pos.getX(), pos.getZ());
	}

	/** The ground just outside its town that way from the middle (the top block's air above it), or null if not in a town. */
	@Nullable
	public static BlockPos outside(DrifterEntity drifter, Direction way) {
		var town = drifter.getTownWork();
		if (town == null) {
			return null;
		}

		BlockPos edge = town.center().relative(way, town.radius() + PAST);
		for (int more = 0; more < 32 && town.townGround(edge.getX(), edge.getZ()); more += 4) {
			edge = edge.relative(way, 4);
		}

		if (!drifter.level().isLoaded(edge)) {
			return null;
		}

		return drifter.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, edge);
	}

	/** The way out of its town from here (from the middle toward it). */
	public static Direction wayOut(DrifterEntity drifter) {
		var town = drifter.getTownWork();
		if (town == null) {
			return drifter.getDirection();
		}

		BlockPos middle = town.center();
		int dx = drifter.getBlockX() - middle.getX();
		int dz = drifter.getBlockZ() - middle.getZ();
		return Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
	}
}
