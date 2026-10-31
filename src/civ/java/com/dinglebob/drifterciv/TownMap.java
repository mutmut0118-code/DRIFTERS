package com.dinglebob.drifterciv;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Debug: a town seen from above, as a picture (`/drifterciv town map`): the colours a map gives each block, shaded by the
 * land's rise and fall, its roads and paths picked out. To look over a town's layout without flying over it (milestone 23,
 * the town shapes: the user, 2026-10-30).
 */
final class TownMap {
	private TownMap() {
	}

	/** Pixels a block. */
	private static final int SCALE = 3;
	private static final int ROAD = 0xE8C77A;

	/** The land this far round {@code center} drawn into {@code town-maps/<name>.png} in the server's folder; where it went. */
	static Path draw(ServerLevel level, BlockPos center, int radius, String name) throws IOException {
		int scale = radius > 200 ? 1 : SCALE;
		int size = radius * 2 + 1;
		BufferedImage image = new BufferedImage(size * scale, size * scale, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				int x = center.getX() - radius + i;
				int z = center.getZ() - radius + j;
				int colour = colour(level, x, z, radius <= 200);
				for (int a = 0; a < scale; a++) {
					for (int b = 0; b < scale; b++) {
						image.setRGB(i * scale + a, j * scale + b, colour);
					}
				}
			}
		}

		Path folder = level.getServer().getServerDirectory().resolve("town-maps");
		Files.createDirectories(folder);
		Path file = folder.resolve(name.replaceAll("[^A-Za-z0-9_-]", "_") + ".png");
		ImageIO.write(image, "png", file.toFile());
		return file.toAbsolutePath();
	}

	private static int colour(ServerLevel level, int x, int z, boolean load) {
		// (Loaded, or made, if need be, for a town's map; a big one only what's loaded: making 600 blocks of new land in one
		// tick stopped the server.)
		if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
			if (!load) {
				return 0x101010;
			}

			level.getChunk(x >> 4, z >> 4);
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		BlockPos pos = new BlockPos(x, top, z);
		BlockState state = level.getBlockState(pos);
		int colour = state.is(Blocks.DIRT_PATH) || state.is(Blocks.SMOOTH_SANDSTONE) && top <= level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) ? ROAD
			: state.getMapColor(level, pos).col;
		// (Shaded as a map is: lighter where the land rises to the north, darker where it falls.)
		int north = level.getChunkSource().getChunkNow(x >> 4, (z - 1) >> 4) == null ? top : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z - 1) - 1;
		double shade = top > north ? 1.12 : top < north ? 0.82 : 1.0;
		int r = Math.min(255, (int)(((colour >> 16) & 255) * shade));
		int g = Math.min(255, (int)(((colour >> 8) & 255) * shade));
		int b = Math.min(255, (int)((colour & 255) * shade));
		return r << 16 | g << 8 | b;
	}
}
