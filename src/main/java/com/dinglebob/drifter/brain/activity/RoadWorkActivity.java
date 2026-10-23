package com.dinglebob.drifter.brain.activity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.executor.task.UseOnBlockTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Work on its town's roads (milestone 22, a town founded by its people; user, 2026-10-23: "give founded towns the side
 * lanes"): along the roads its town laid out, from the middle outward, the ground made into path with a shovel (a
 * right-click, as a player makes one), the grass and flowers on it cut first. Only ground it has seen; water, trees and
 * anyone's build it leaves (the road goes round or stops there). A stretch a visit.
 */
public class RoadWorkActivity extends Activity {
	/** Blocks of road a visit. */
	private static final int PER_VISIT = 24;
	/** How far along the road list it looks for the next block to do. */
	private static final int LOOK_AHEAD = 400;

	private final Set<Long> tried = new HashSet<>();
	private final Map<BlockPos, Integer> walks = new HashMap<>();
	private int done;

	public RoadWorkActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "work on the town's roads";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 120;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		return false;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof SequenceTask && status == Status.SUCCESS) {
			this.done++;
		}
	}

	@Override
	protected Status next() {
		TownWork work = this.drifter.getTownWork();
		if (work == null || this.done >= PER_VISIT || this.drifter.getInventory().count(stack -> stack.is(ItemTags.SHOVELS)) == 0) {
			return Status.SUCCESS;
		}

		BlockPos top = next(this.drifter, work, this.tried);
		if (top == null) {
			return Status.SUCCESS;
		}

		Level level = this.drifter.level();
		// Grass or a flower on it: cut first.
		BlockPos over = top.above();
		if (!level.getBlockState(over).isAir()) {
			if (!Reach.canReach(this.drifter, over)) {
				return this.walkTo(top);
			}

			return this.begin(this.dig(over));
		}

		Direction face = null;
		for (Direction side : Direction.values()) {
			if (face == null && Reach.aimPointFrom(this.drifter, this.drifter.getEyePosition(), top, side).isPresent()) {
				face = side;
			}
		}

		if (face == null) {
			return this.walkTo(top);
		}

		this.tried.add(top.asLong());
		return this.begin(new SequenceTask(this.drifter, "make a road", List.of(new SelectItemTask(this.drifter, stack -> stack.is(ItemTags.SHOVELS), "a shovel"),
			new UseOnBlockTask(this.drifter, top, face, "make a road"))));
	}

	private Status walkTo(BlockPos top) {
		if (this.walks.merge(top, 1, Integer::sum) > 2) {
			this.tried.add(top.asLong());
			return Status.RUNNING;
		}

		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(top.above()), 2.5, "walk along the road"));
	}

	/** The next block of its town's road to make into path (the top of the ground there), or null if it knows of none. */
	@Nullable
	public static BlockPos next(DrifterEntity drifter, TownWork work, Set<Long> skip) {
		Level level = drifter.level();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		int looked = 0;
		for (BlockPos column : work.roads(drifter)) {
			if (!level.isLoaded(column)) {
				continue;
			}

			BlockPos top = new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1,
				column.getZ());
			if (skip.contains(top.asLong()) || !pathable(level, top) || !drifter.getSenses().terrain().isKnown(top.above())) {
				continue;
			}

			// The nearest of the next stretch (it works along, not back and forth).
			double distance = drifter.position().distanceToSqr(Vec3.atCenterOf(top));
			if (distance < bestDistance) {
				bestDistance = distance;
				best = top;
			}

			if (++looked >= 12) {
				break;
			}
		}

		return best;
	}

	/** Ground a shovel makes into path, with nothing over it but air or a plant (and nobody's build). */
	private static boolean pathable(Level level, BlockPos top) {
		BlockState state = level.getBlockState(top);
		BlockState over = level.getBlockState(top.above());
		return (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
			|| state.is(Blocks.ROOTED_DIRT)) && (over.isAir() || over.canBeReplaced() && over.getFluidState().isEmpty())
			&& !com.dinglebob.drifter.world.PlayerPlacedBlocks.isBuilt(level, top);
	}
}
