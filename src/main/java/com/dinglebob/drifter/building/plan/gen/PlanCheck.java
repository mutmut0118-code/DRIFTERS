package com.dinglebob.drifter.building.plan.gen;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.BuildPlanActivity;
import com.dinglebob.drifter.building.plan.HousePalette;
import com.dinglebob.drifter.building.plan.HousePlan;
import com.dinglebob.drifter.building.plan.PlacedPlan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Is a generated plan a sound house a Drifter can build and live in (milestone 15)? Every block reaches the floor through
 * blocks touching face to face (nothing floating: a player places each against one already there), a door, and room for
 * a bed on the lowest floor under the roof (the same test the builder uses). Plans that fail are thrown away.
 */
public final class PlanCheck {
	private PlanCheck() {
	}

	/** Null if the plan is sound, else why not. */
	@Nullable
	public static String problem(HousePlan plan) {
		Map<BlockPos, BlockState> cells = new HashMap<>();
		boolean door = false;
		for (HousePlan.Cell cell : plan.cells) {
			cells.put(cell.pos(), cell.state());
			door |= cell.state().getBlock() instanceof DoorBlock;
		}

		if (!door) {
			return "no door";
		}

		// Everything connected to the bottom layer (the floor, which sits on the ground).
		Set<BlockPos> reached = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos pos : cells.keySet()) {
			if (pos.getY() == 0) {
				reached.add(pos);
				queue.add(pos);
			}
		}

		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			for (Direction side : Direction.values()) {
				BlockPos next = pos.relative(side);
				if (cells.containsKey(next) && reached.add(next)) {
					queue.add(next);
				}
			}
		}

		if (reached.size() < cells.size()) {
			BlockPos example = cells.keySet().stream().filter(pos -> !reached.contains(pos)).findFirst().orElse(BlockPos.ZERO);
			return (cells.size() - reached.size()) + " floating blocks (" + cells.get(example).getBlock().getName().getString() + " at "
				+ example.toShortString() + " of " + plan.size.toShortString() + ")";
		}

		PlacedPlan placed = new PlacedPlan(plan, BlockPos.ZERO, Rotation.NONE, HousePalette.asDrawn());
		Map<BlockPos, BlockState> want = placed.buildMap();
		if (BuildPlanActivity.bedSpot(new PlanWorld(want), placed, want) == null) {
			return "no room for a bed under the roof";
		}

		return null;
	}

	/** The plan standing on flat stone, nothing else round it. */
	private record PlanWorld(Map<BlockPos, BlockState> blocks) implements BlockGetter {
		@Override
		@Nullable
		public BlockEntity getBlockEntity(BlockPos pos) {
			return null;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			BlockState state = this.blocks.get(pos);
			return state != null ? state : pos.getY() < 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return this.getBlockState(pos).getFluidState();
		}

		@Override
		public int getHeight() {
			return 384;
		}

		@Override
		public int getMinBuildHeight() {
			return -64;
		}
	}
}
