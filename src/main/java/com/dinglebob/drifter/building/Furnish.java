package com.dinglebob.drifter.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Putting a chest or a station down the way a player's would end up (user, 2026-10-17: "double chests dont work, and some
 * chests are facing the wall"), for the places that set blocks directly (world generation, catch-up): facing the open
 * floor with its back to the wall, and a chest beside a single chest facing the same way joined into a double chest, as
 * placing it by hand does (ChestBlock#getStateForPlacement).
 */
public final class Furnish {
	private Furnish() {
	}

	/** A chest here, facing into the room, joined to a single chest beside it if they line up. */
	public static void chest(LevelAccessor level, BlockPos pos, int flags) {
		Direction facing = facing(level, pos);
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
		// Vanilla: a partner on the clockwise side makes this the left half, on the other side the right half.
		for (boolean clockwise : new boolean[] {true, false}) {
			BlockPos side = pos.relative(clockwise ? facing.getClockWise() : facing.getCounterClockWise());
			BlockState there = level.getBlockState(side);
			if (there.is(Blocks.CHEST) && there.getValue(ChestBlock.FACING) == facing && there.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
				level.setBlock(pos, chest.setValue(ChestBlock.TYPE, clockwise ? ChestType.LEFT : ChestType.RIGHT), flags);
				level.setBlock(side, there.setValue(ChestBlock.TYPE, clockwise ? ChestType.RIGHT : ChestType.LEFT), flags);
				return;
			}
		}

		level.setBlock(pos, chest, flags);
	}

	/** A station (furnace, crafting table...) here, turned to face into the room if it has a facing. */
	public static void station(LevelAccessor level, BlockPos pos, BlockState state, int flags) {
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing(level, pos));
		}

		level.setBlock(pos, state, flags);
	}

	/**
	 * Which way something here should face: toward open floor, with a wall behind it (and toward the most open side if
	 * it's in a corner).
	 */
	public static Direction facing(LevelAccessor level, BlockPos pos) {
		Direction best = Direction.NORTH;
		int bestScore = Integer.MIN_VALUE;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos front = pos.relative(side);
			BlockPos back = pos.relative(side.getOpposite());
			int score = 0;
			if (level.getBlockState(front).getCollisionShape(level, front).isEmpty()
				&& level.getBlockState(front.above()).getCollisionShape(level, front.above()).isEmpty()) {
				score += 4;
				// Room to stand in front of it, not just a gap.
				if (level.getBlockState(front.below()).isFaceSturdy(level, front.below(), Direction.UP)) {
					score += 2;
				}
			}

			if (!level.getBlockState(back).getCollisionShape(level, back).isEmpty()) {
				score += 3;
			}

			if (score > bestScore) {
				bestScore = score;
				best = side;
			}
		}

		return best;
	}
}
