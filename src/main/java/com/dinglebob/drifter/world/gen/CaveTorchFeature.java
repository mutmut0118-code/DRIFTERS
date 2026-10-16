package com.dinglebob.drifter.world.gen;

import com.mojang.serialization.Codec;

import com.dinglebob.drifter.DrifterConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Torches left in a cave (user's idea, 2026-10-16): a few on the walls near where it's placed, the way someone who
 * explored the cave before lit their way. Only on bare cave walls, on air that's actually a cave.
 */
public class CaveTorchFeature extends Feature<NoneFeatureConfiguration> {
	private static final int TRIES = 24;
	private static final int SPREAD = 6;
	private static final int MAX_TORCHES = 3;

	public CaveTorchFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		if (!DrifterConfig.worldgenCaveTorches()) {
			return false;
		}

		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		int placed = 0;
		for (int i = 0; i < TRIES && placed < MAX_TORCHES; i++) {
			BlockPos pos = context.origin().offset(random.nextInt(SPREAD * 2 + 1) - SPREAD, random.nextInt(5) - 2, random.nextInt(SPREAD * 2 + 1) - SPREAD);
			if (!level.getBlockState(pos).is(Blocks.CAVE_AIR) || level.canSeeSkyFromBelowWater(pos)) {
				continue;
			}

			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos wall = pos.relative(side);
				if (level.getBlockState(wall).isSolidRender(level, wall) && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
					level.setBlock(pos, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, side.getOpposite()), 2);
					placed++;
					break;
				}
			}
		}

		return placed > 0;
	}
}
