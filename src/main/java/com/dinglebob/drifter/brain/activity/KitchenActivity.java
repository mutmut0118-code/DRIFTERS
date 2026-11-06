package com.dinglebob.drifter.brain.activity;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * What the kitchen jobs share (Farmer's Delight, optional): getting within reach of the board or the pot, as the smelting
 * does for a furnace, and saying once why a step went wrong.
 */
abstract class KitchenActivity extends Activity {
	private int walks;
	private boolean approached;
	@Nullable
	protected String stepFailure;

	protected KitchenActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (status == Status.FAILURE) {
			this.stepFailure = step.describe() + ": " + step.failureReason();
		}
	}

	/** A walk to within reach of it, or null if it's in reach already; a failure status if it can't get there. */
	@Nullable
	protected Status reach(BlockPos pos, String what) {
		if (Reach.canReach(this.drifter, pos)) {
			return null;
		}

		BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, pos, Set.of(pos));
		if (spot == null && !this.approached && this.drifter.blockPosition().distSqr(pos) > 4 * 4) {
			// It doesn't remember the floor round it well enough from here: go and look.
			this.approached = true;
			return this.begin(new NavigateTask(this.drifter, Vec3.atCenterOf(pos), 3.0, "walk over to the " + what));
		}

		if (spot == null || this.walks++ >= 2) {
			return this.fail("can't get within reach of the " + what);
		}

		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk to the " + what));
	}
}
