package com.dinglebob.drifter.brain.activity;

import java.util.Set;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Checking one of its chests after it saw (or heard) someone at it (user's idea, 2026-10-16): it walks over, opens it and
 * looks. Opening it is what tells it whether anything's gone (Senses compares with what it remembers putting there), and
 * whether it caught them in the act.
 */
public class CheckChestActivity extends Activity {
	private final BlockPos chest;
	private boolean walked;
	private boolean done;

	public CheckChestActivity(DrifterEntity drifter, BlockPos chest) {
		super(drifter);
		this.chest = chest;
	}

	@Override
	public String describe() {
		return "check its chest";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof ContainerTask) {
			this.done = true;
		}
	}

	@Override
	protected Status next() {
		if (this.done) {
			return Status.SUCCESS;
		}

		if (!Reach.canReach(this.drifter, this.chest)) {
			BlockPos spot = this.walked ? null : KnownBlocks.standingSpotFor(this.drifter, this.chest, Set.of());
			if (spot == null) {
				return this.fail("can't get to its chest");
			}

			this.walked = true;
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk to the chest"));
		}

		this.think("someone was at its chest: checking it");
		return this.begin(new ContainerTask(this.drifter, this.chest, ContainerTask.Mode.TAKE, stack -> false, "nothing", 0, null));
	}
}
