package com.dinglebob.drifter.building.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A plan put down in the world: turned, in its builder's materials, at its corner. Its cells split into ground (grass and
 * dirt in a plan are the ground there, user's rule 2026-10-16: never placed as blocks, only made sure of) and blocks to
 * build, in build order: the lowest first, solid blocks before what hangs on them (doors, torches, panes, trapdoors), the
 * upper half of a door and the head of a bed left out (they come with their other half).
 */
public final class PlacedPlan {
	public record Cell(BlockPos pos, BlockState state) {
	}

	public final HousePlan plan;
	public final BlockPos origin;
	public final Rotation rotation;
	/** Width (x) and depth (z) once turned, and height. */
	public final int sizeX;
	public final int sizeZ;
	public final int sizeY;
	public final List<Cell> build;
	public final List<BlockPos> ground;

	/** Blocks that are the ground, not something built. */
	private static final Set<Block> GROUND = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.MYCELIUM,
		Blocks.ROOTED_DIRT, Blocks.MUD, Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL);

	public PlacedPlan(HousePlan plan, BlockPos origin, Rotation rotation, HousePalette palette) {
		this.plan = plan;
		this.origin = origin.immutable();
		this.rotation = rotation;
		boolean turned = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
		this.sizeX = turned ? plan.size.getZ() : plan.size.getX();
		this.sizeZ = turned ? plan.size.getX() : plan.size.getZ();
		this.sizeY = plan.size.getY();
		// Turned about the plan's corner, then shifted back so the turned plan still starts at the corner.
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		for (int[] corner : new int[][] {{0, 0}, {plan.size.getX() - 1, 0}, {0, plan.size.getZ() - 1}, {plan.size.getX() - 1, plan.size.getZ() - 1}}) {
			BlockPos t = StructureTemplate.transform(new BlockPos(corner[0], 0, corner[1]), net.minecraft.world.level.block.Mirror.NONE, rotation, BlockPos.ZERO);
			minX = Math.min(minX, t.getX());
			minZ = Math.min(minZ, t.getZ());
		}

		List<Cell> build = new ArrayList<>();
		List<BlockPos> ground = new ArrayList<>();
		for (HousePlan.Cell cell : plan.cells) {
			BlockPos t = StructureTemplate.transform(cell.pos(), net.minecraft.world.level.block.Mirror.NONE, rotation, BlockPos.ZERO);
			BlockPos world = origin.offset(t.getX() - minX, t.getY(), t.getZ() - minZ);
			BlockState state = palette.apply(cell.state().rotate(rotation));
			if (isGround(state)) {
				ground.add(world);
				continue;
			}

			if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
				|| state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
				continue;
			}

			build.add(new Cell(world, state));
		}

		build.sort(Comparator.comparingInt((Cell c) -> c.pos().getY()).thenComparingInt(c -> hangs(c.state()) ? 1 : 0)
			.thenComparingInt(c -> c.pos().getX()).thenComparingInt(c -> c.pos().getZ()));
		this.build = List.copyOf(build);
		this.ground = List.copyOf(ground);
	}

	/** Grass, dirt and the like in a plan: the ground there, never placed as a block (user's rule, 2026-10-16). */
	public static boolean isGround(BlockState state) {
		return GROUND.contains(state.getBlock());
	}

	/** Something that hangs on or stands in what's built round it (placed after the solid blocks of its layer). */
	static boolean hangs(BlockState state) {
		Block block = state.getBlock();
		return block instanceof net.minecraft.world.level.block.DoorBlock || block instanceof net.minecraft.world.level.block.TorchBlock
			|| block instanceof net.minecraft.world.level.block.TrapDoorBlock || block instanceof net.minecraft.world.level.block.IronBarsBlock
			|| block instanceof net.minecraft.world.level.block.SignBlock || block instanceof net.minecraft.world.level.block.LadderBlock
			|| block instanceof net.minecraft.world.level.block.ButtonBlock || block instanceof net.minecraft.world.level.block.LanternBlock
			|| block instanceof net.minecraft.world.level.block.BedBlock || block instanceof net.minecraft.world.level.block.CarpetBlock
			|| block instanceof net.minecraft.world.level.block.FlowerPotBlock || block instanceof net.minecraft.world.level.block.BasePressurePlateBlock;
	}

	/** It's inside the plan's box (with a margin). */
	public boolean contains(BlockPos pos, int margin) {
		return pos.getX() >= this.origin.getX() - margin && pos.getX() < this.origin.getX() + this.sizeX + margin
			&& pos.getZ() >= this.origin.getZ() - margin && pos.getZ() < this.origin.getZ() + this.sizeZ + margin
			&& pos.getY() >= this.origin.getY() - 1 - margin && pos.getY() < this.origin.getY() + this.sizeY + margin;
	}

	/** Every column of its footprint, at its lowest layer. */
	public List<BlockPos> footprint() {
		List<BlockPos> columns = new ArrayList<>();
		for (int dx = 0; dx < this.sizeX; dx++) {
			for (int dz = 0; dz < this.sizeZ; dz++) {
				columns.add(this.origin.offset(dx, 0, dz));
			}
		}

		return columns;
	}

	/** What the plan has at this spot: a block to build, ground, or (null) nothing (air). */
	public java.util.Map<BlockPos, BlockState> buildMap() {
		java.util.Map<BlockPos, BlockState> map = new java.util.HashMap<>();
		this.build.forEach(cell -> map.put(cell.pos(), cell.state()));
		return map;
	}

	public static PlacedPlan of(HousePlan plan, com.dinglebob.drifter.brain.Home home) {
		return new PlacedPlan(plan, home.planOrigin, Rotation.valueOf(home.planRotation), HousePalette.of(home.planWoods, home.planDeepslate));
	}
}
