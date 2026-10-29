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
			BlockState state = palette.apply(cell.state().rotate(rotation));
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

	/**
	 * The sides of its box its front doors are on: each door from which one walks straight out of the house at door height
	 * (nothing solid in the way to the box's edge), its side the way out. A door between two rooms isn't one (found in a
	 * city: a farmhouse's inner door taken for its front, its path and the walk to it through the house).
	 */
	public static Set<Direction> doorSides(PlacedPlan placed) {
		Set<Direction> sides = EnumSet.noneOf(Direction.class);
		Set<BlockPos> solid = solid(placed);
		for (PlacedPlan.Cell cell : placed.build) {
			if (cell.state().getBlock() instanceof DoorBlock) {
				Direction out = wayOut(placed, solid, cell.pos());
				if (out != null) {
					sides.add(out);
				}
			}
		}

		return sides;
	}

	/** The front door's lower half (one whose way out is {@code side}, else any front door, else any door), or null. */
	@Nullable
	public static BlockPos door(PlacedPlan placed, @Nullable Direction side) {
		BlockPos any = null;
		BlockPos front = null;
		Set<BlockPos> solid = solid(placed);
		for (PlacedPlan.Cell cell : placed.build) {
			if (!(cell.state().getBlock() instanceof DoorBlock) || cell.state().getValue(DoorBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
				continue;
			}

			BlockPos p = cell.pos();
			any = any == null ? p : any;
			Direction out = wayOut(placed, solid, p);
			if (out != null && (side == null || out == side)) {
				return p;
			}

			front = front == null && out != null ? p : front;
		}

		return front != null ? front : any;
	}

	/** The plan's cells something can't walk through. */
	private static Set<BlockPos> solid(PlacedPlan placed) {
		Set<BlockPos> solid = new java.util.HashSet<>();
		for (PlacedPlan.Cell cell : placed.build) {
			if (!cell.state().isAir() && !(cell.state().getBlock() instanceof DoorBlock) && cell.state().blocksMotion()) {
				solid.add(cell.pos());
			}
		}

		return solid;
	}

	/** Which way one walks straight out of the house from this door (feet and head clear to the box's edge), or null. */
	@Nullable
	private static Direction wayOut(PlacedPlan placed, Set<BlockPos> solid, BlockPos door) {
		var box = placed.box();
		BlockPos feet = door;
		for (Direction way : Direction.Plane.HORIZONTAL) {
			BlockPos at = feet.relative(way);
			boolean clear = true;
			while (box.isInside(at) && clear) {
				clear = !solid.contains(at) && !solid.contains(at.above());
				at = at.relative(way);
			}

			if (clear) {
				return way;
			}
		}

		return null;
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
