package com.dinglebob.drifter.world.gen;

import com.mojang.serialization.Codec;

import com.dinglebob.drifter.DrifterConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * An old strip mine (user's idea, 2026-10-16: a world already lived in): a 1x2 tunnel through the deep rock with branches
 * off it every three blocks and a torch every eight, the way players dig for diamonds, left behind. Only through solid
 * stone or deepslate: it stops before it would open into a cave, water or lava. Kept within its own chunk and the next,
 * which world generation allows.
 */
public class StripMineFeature extends Feature<NoneFeatureConfiguration> {
	private static final int MAIN_LENGTH = 14;
	private static final int BRANCH_EVERY = 3;
	private static final int BRANCH_LENGTH = 5;
	private static final int TORCH_EVERY = 8;

	public StripMineFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		if (!DrifterConfig.worldgenMines()) {
			return false;
		}

		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos start = context.origin();
		Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		if (!rock(level, start) || !rock(level, start.above())) {
			return false;
		}

		int dug = 0;
		BlockPos cursor = start;
		for (int i = 0; i < MAIN_LENGTH; i++) {
			if (!this.dig(level, cursor)) {
				break;
			}

			dug++;
			if (i % TORCH_EVERY == 0) {
				this.torch(level, cursor, along.getClockWise());
			}

			if (i > 0 && i % BRANCH_EVERY == 0) {
				for (Direction side : new Direction[] {along.getClockWise(), along.getCounterClockWise()}) {
					BlockPos branch = cursor;
					for (int b = 0; b < BRANCH_LENGTH; b++) {
						branch = branch.relative(side);
						if (!this.dig(level, branch)) {
							break;
						}
					}
				}
			}

			cursor = cursor.relative(along);
		}

		if (dug > 0) {
			com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter] world gen: an old strip mine at {}", start.toShortString());
		}

		return dug > 0;
	}

	/** A 1x2 opening here, if the rock round it is whole (no air, water or lava next to it). */
	private boolean dig(WorldGenLevel level, BlockPos feet) {
		for (BlockPos cell : new BlockPos[] {feet, feet.above()}) {
			if (!rock(level, cell)) {
				return false;
			}

			for (Direction side : Direction.values()) {
				if (!level.getBlockState(cell.relative(side)).getFluidState().isEmpty()) {
					return false;
				}
			}
		}

		level.setBlock(feet, Blocks.CAVE_AIR.defaultBlockState(), 2);
		level.setBlock(feet.above(), Blocks.CAVE_AIR.defaultBlockState(), 2);
		return true;
	}

	private void torch(WorldGenLevel level, BlockPos feet, Direction wallSide) {
		BlockPos wall = feet.above().relative(wallSide);
		if (rock(level, wall)) {
			level.setBlock(feet.above(), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, wallSide.getOpposite()), 2);
		}
	}

	private static boolean rock(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES) || state.is(BlockTags.STONE_ORE_REPLACEABLES);
	}
}
