package com.dinglebob.drifter.brain.activity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Takes down the pillars and bridges it left round home (user's request: "radish unintentionally made an andesite
 * tree"): blocks it put down only to get somewhere, top first, the ones it can reach. Tidy Drifters do it, further out
 * the tidier they are (25 to 40 blocks); messy ones leave them.
 */
public class TidyScaffoldActivity extends Activity {
	/** Tidiness under which it leaves its scaffolding standing. */
	public static final double TIDY_ENOUGH = 0.4;
	private static final int PER_VISIT = 24;

	private final Set<BlockPos> skipped = new HashSet<>();
	private final java.util.Map<BlockPos, Integer> walks = new java.util.HashMap<>();
	private int removed;

	public TidyScaffoldActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "take down its old pillars and bridges";
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
			this.removed++;
		}
	}

	@Override
	protected Status next() {
		Home home = this.drifter.getDrifterBrain().home();
		if (home == null || this.removed >= PER_VISIT) {
			return this.finish();
		}

		BlockPos block = next(this.drifter, home, this.skipped);
		if (block == null) {
			return this.finish();
		}

		if (Reach.canReach(this.drifter, block)) {
			if (this.removed == 0 && this.skipped.isEmpty()) {
				this.think("taking down a pillar or bridge it left by home");
			}

			this.skipped.add(block);
			this.drifter.forgetScaffold(block);
			return this.begin(new SequenceTask(this.drifter, "take down a block it left", List.of(this.dig(block), new CollectItemsTask(this.drifter, 3.0))));
		}

		BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, block, Set.of());
		if (spot == null || this.walks.merge(block, 1, Integer::sum) > 2) {
			this.skipped.add(block);
			return Status.RUNNING;
		}

		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk over to a block it left", NavigateTask.Pace.STROLL));
	}

	private Status finish() {
		if (this.removed > 0) {
			this.think("took down " + this.removed + " blocks it had left round home");
		}

		return Status.SUCCESS;
	}

	/** How many of its scaffold blocks are left standing round home (for how keen it is to tidy). */
	public static int countLeft(DrifterEntity drifter, Home home) {
		int radius = radius(drifter);
		if (radius == 0) {
			return 0;
		}

		int count = 0;
		for (BlockPos pos : drifter.scaffold()) {
			if (home.center.closerThan(pos, radius) && stillScaffold(drifter, home, pos)) {
				count++;
			}
		}

		return count;
	}

	/** How far round home it tidies (25 to 40 blocks by tidiness), or 0 if it doesn't. */
	public static int radius(DrifterEntity drifter) {
		double tidiness = drifter.getPersonality().get(Trait.TIDINESS);
		if (tidiness < TIDY_ENOUGH) {
			return 0;
		}

		return 25 + (int)Math.round(15 * (tidiness - TIDY_ENOUGH) / (1.0 - TIDY_ENOUGH));
	}

	/**
	 * The next of its scaffold blocks round home to take down, nearest first, only from the top (none of its scaffolding
	 * left on it, so nothing is left floating), never the one under its feet. Null if there's none.
	 */
	@Nullable
	public static BlockPos next(DrifterEntity drifter, Home home, Set<BlockPos> skip) {
		int radius = radius(drifter);
		if (radius == 0) {
			return null;
		}

		BlockPos feet = drifter.blockPosition();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : drifter.scaffold()) {
			if (skip.contains(pos) || !home.center.closerThan(pos, radius)) {
				continue;
			}

			if (!stillScaffold(drifter, home, pos)) {
				drifter.forgetScaffold(pos);
				continue;
			}

			if (pos.equals(feet.below()) || drifter.isScaffold(pos.above()) && stillScaffold(drifter, home, pos.above())) {
				continue;
			}

			double distance = pos.distSqr(feet);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = pos;
			}
		}

		return best;
	}

	/** Still the building block it put there, and not part of something it built since. */
	private static boolean stillScaffold(DrifterEntity drifter, Home home, BlockPos pos) {
		BlockState state = drifter.level().getBlockState(pos);
		// Out in the open only: pillars and bridges down its mines and caves aren't an eyesore (and mostly out of reach).
		return !state.isAir() && drifter.level().canSeeSky(pos.above()) && com.dinglebob.drifter.hands.Tools.BUILDING_BLOCK.test(new net.minecraft.world.item.ItemStack(state.getBlock().asItem()))
			&& !home.inOwnBuilding(pos, 1) && drifter.level().getFluidState(pos).isEmpty();
	}
}
