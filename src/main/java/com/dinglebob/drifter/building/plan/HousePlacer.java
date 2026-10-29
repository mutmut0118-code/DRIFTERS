package com.dinglebob.drifter.building.plan;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A house from a plan put up all at once (milestone 22: a town made ready for testing, `/drifterciv town spawn`; and the
 * `/drifter house paste` debug command). Not something a Drifter does: Drifters build block by block
 * ({@code BuildPlanActivity}).
 */
public final class HousePlacer {
	private HousePlacer() {
	}

	/** Every block of the plan, turned and in this palette, its corner at {@code origin}; the house as placed. */
	public static PlacedPlan place(ServerLevel level, HousePlan plan, BlockPos origin, Rotation rotation, HousePalette palette) {
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		for (int[] corner : new int[][] {{0, 0}, {plan.size.getX() - 1, 0}, {0, plan.size.getZ() - 1}, {plan.size.getX() - 1, plan.size.getZ() - 1}}) {
			BlockPos t = StructureTemplate.transform(new BlockPos(corner[0], 0, corner[1]), net.minecraft.world.level.block.Mirror.NONE, rotation, BlockPos.ZERO);
			minX = Math.min(minX, t.getX());
			minZ = Math.min(minZ, t.getZ());
		}

		// The solid blocks first, then what hangs on them (doors, torches, beds), so nothing pops off.
		List<BlockPos> hanging = new ArrayList<>();
		List<BlockState> hangingStates = new ArrayList<>();
		for (HousePlan.Cell cell : plan.cells) {
			BlockPos t = StructureTemplate.transform(cell.pos(), net.minecraft.world.level.block.Mirror.NONE, rotation, BlockPos.ZERO);
			BlockPos world = origin.offset(t.getX() - minX, t.getY(), t.getZ() - minZ);
			BlockState state = palette.apply(cell.state().rotate(rotation), cell.pos());
			if (PlacedPlan.hangs(state)) {
				hanging.add(world);
				hangingStates.add(state);
			} else {
				level.setBlock(world, state, Block.UPDATE_CLIENTS);
				// (Grass with the house on it is earth: user, 2026-10-23, "grass blocks not turning into dirt when there is a house
				// on top of it".)
				BlockState under = level.getBlockState(world.below());
				if (!state.isAir() && (under.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) || under.is(net.minecraft.world.level.block.Blocks.PODZOL)
					|| under.is(net.minecraft.world.level.block.Blocks.MYCELIUM))) {
					level.setBlock(world.below(), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}

		for (int i = 0; i < hanging.size(); i++) {
			level.setBlock(hanging.get(i), hangingStates.get(i), Block.UPDATE_CLIENTS);
		}

		PlacedPlan placed = new PlacedPlan(plan, origin, rotation, palette);
		// (The trees it cut through: no trunks or leaves left hanging.)
		com.dinglebob.drifter.world.gen.Felling.tidy(level, placed.box(), placed.box());
		return placed;
	}

	/** The sides of its box its doors are on, turned this way (a house's front). */
	public static Set<Direction> doorSides(PlacedPlan placed) {
		Set<Direction> sides = EnumSet.noneOf(Direction.class);
		var box = placed.box();
		for (PlacedPlan.Cell cell : placed.build) {
			if (!(cell.state().getBlock() instanceof DoorBlock)) {
				continue;
			}

			BlockPos p = cell.pos();
			int west = p.getX() - box.minX();
			int east = box.maxX() - p.getX();
			int north = p.getZ() - box.minZ();
			int south = box.maxZ() - p.getZ();
			int nearest = Math.min(Math.min(west, east), Math.min(north, south));
			if (nearest == west) {
				sides.add(Direction.WEST);
			} else if (nearest == east) {
				sides.add(Direction.EAST);
			} else if (nearest == north) {
				sides.add(Direction.NORTH);
			} else {
				sides.add(Direction.SOUTH);
			}
		}

		return sides;
	}

	/** The front door's lower half (the one on {@code side}, else any), or null. */
	@Nullable
	public static BlockPos door(PlacedPlan placed, @Nullable Direction side) {
		BlockPos any = null;
		var box = placed.box();
		for (PlacedPlan.Cell cell : placed.build) {
			if (cell.state().getBlock() instanceof DoorBlock) {
				BlockPos p = cell.pos();
				any = any == null ? p : any;
				boolean onSide = side == null || switch (side) {
					case WEST -> p.getX() - box.minX() <= 1;
					case EAST -> box.maxX() - p.getX() <= 1;
					case NORTH -> p.getZ() - box.minZ() <= 1;
					case SOUTH -> box.maxZ() - p.getZ() <= 1;
					default -> false;
				};
				if (onSide) {
					return p;
				}
			}
		}

		return any;
	}

	/** The head of a bed inside the house as it stands, or null. */
	@Nullable
	public static BlockPos bedIn(ServerLevel level, PlacedPlan placed) {
		var box = placed.box();
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
				return pos.immutable();
			}
		}

		return null;
	}
}
