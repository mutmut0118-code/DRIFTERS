package com.dinglebob.drifter.building;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * Decorating its house once it's built and furnished (the user, 2026-10-30: "rugs and other decoration"): a rug of carpet
 * on the open floor of the main room, and a painting on a long blank wall, as a player makes a house a home. Only its own
 * house it knows inside out; nothing a player put down is touched (carpet and a painting go on empty floor and wall).
 */
public final class Decor {
	private Decor() {
	}

	/** Rugs it tries, biggest first (width and depth). */
	private static final int[][] RUGS = {{3, 3}, {3, 2}, {2, 3}, {2, 2}};
	/** Blank wall either side of a painting's spot (it picks the longest). */
	private static final int BLANK = 1;

	/** Where its rug goes: open floor in its house, away from the doors, the main room first. Null if it has one or there's no room. */
	@Nullable
	public static List<BlockPos> rug(Level level, Home home) {
		return rug(level, home, 9);
	}

	/** The same, no bigger than {@code most} carpets (what it can make from the wool it has; 2 by 2 at least). */
	@Nullable
	public static List<BlockPos> rug(Level level, Home home, int most) {
		BoundingBox box = home.planBox();
		if (box == null || hasCarpet(level, box)) {
			return null;
		}

		int y = floorY(level, home, box);
		double cx = (box.minX() + box.maxX()) / 2.0;
		double cz = (box.minZ() + box.maxZ()) / 2.0;
		for (int[] size : RUGS) {
			if (size[0] * size[1] > Math.max(4, most)) {
				continue;
			}

			List<BlockPos> best = null;
			double bestDistance = Double.MAX_VALUE;
			for (int x = box.minX(); x + size[0] - 1 <= box.maxX(); x++) {
				for (int z = box.minZ(); z + size[1] - 1 <= box.maxZ(); z++) {
					List<BlockPos> cells = new ArrayList<>();
					boolean fits = true;
					for (int dx = 0; dx < size[0] && fits; dx++) {
						for (int dz = 0; dz < size[1] && fits; dz++) {
							BlockPos cell = new BlockPos(x + dx, y, z + dz);
							fits = openFloor(level, home, cell);
							cells.add(cell);
						}
					}

					double middleX = x + (size[0] - 1) / 2.0;
					double middleZ = z + (size[1] - 1) / 2.0;
					double distance = (middleX - cx) * (middleX - cx) + (middleZ - cz) * (middleZ - cz);
					if (fits && distance < bestDistance) {
						best = cells;
						bestDistance = distance;
					}
				}
			}

			if (best != null) {
				return best;
			}
		}

		return null;
	}

	/** A spot for a painting: a wall block at eye height with blank wall either side and open room in front. Null if it has one. */
	@Nullable
	public static Wall painting(Level level, Home home) {
		BoundingBox box = home.planBox();
		if (box == null || !level.getEntitiesOfClass(Painting.class, AABB.of(box).inflate(1.0)).isEmpty()) {
			return null;
		}

		int y = floorY(level, home, box) + 1;
		Wall best = null;
		int bestRun = 0;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				BlockPos wall = new BlockPos(x, y, z);
				if (!blank(level, wall)) {
					continue;
				}

				for (Direction face : Direction.Plane.HORIZONTAL) {
					BlockPos front = wall.relative(face);
					if (!openFloor(level, home, front.below()) || !level.getBlockState(front.above()).isAir()) {
						continue;
					}

					// The run of blank wall with open room in front, along the wall both ways.
					Direction along = face.getClockWise();
					int run = 1;
					for (int sign : new int[] {1, -1}) {
						for (int step = 1; step <= 3; step++) {
							BlockPos next = wall.relative(along, step * sign);
							if (!blank(level, next) || !level.getBlockState(next.relative(face)).isAir()) {
								break;
							}

							run++;
						}
					}

					if (run >= 1 + 2 * BLANK && run > bestRun) {
						best = new Wall(wall, face);
						bestRun = run;
					}
				}
			}
		}

		return best;
	}

	/** A wall block and the face of it into the room. */
	public record Wall(BlockPos wall, Direction face) {
	}

	/** The floor it lives on: its bed's, else the plan's ground floor. */
	private static int floorY(Level level, Home home, BoundingBox box) {
		BlockPos bed = home.planBed(level);
		return bed != null ? bed.getY() : box.minY() + 1;
	}

	private static boolean hasCarpet(Level level, BoundingBox box) {
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockState(pos).is(BlockTags.WOOL_CARPETS)) {
				return true;
			}
		}

		return false;
	}

	/** Empty, on a solid floor, in its house under a roof, with headroom, and not by a door (nothing to trip over in a doorway). */
	private static boolean openFloor(Level level, Home home, BlockPos cell) {
		if (!level.getBlockState(cell).isAir() || !level.getBlockState(cell.above()).isAir() || !home.inOwnBuilding(cell, 0)
			|| !level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)) {
			return false;
		}

		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (level.getBlockState(cell.relative(side)).getBlock() instanceof DoorBlock) {
				return false;
			}
		}

		for (int up = 2; up <= 6; up++) {
			if (!level.getBlockState(cell.above(up)).isAir()) {
				return true;
			}
		}

		return false;
	}

	/** A plain wall block (not glass, a door, a chest or a post's end), and the same over it (room for a painting). */
	private static boolean blank(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		BlockState over = level.getBlockState(pos.above());
		return state.isCollisionShapeFullBlock(level, pos) && over.isCollisionShapeFullBlock(level, pos.above()) && level.getBlockEntity(pos) == null
			&& !state.is(net.minecraft.tags.BlockTags.IMPERMEABLE) && !over.is(net.minecraft.tags.BlockTags.IMPERMEABLE);
	}
}
