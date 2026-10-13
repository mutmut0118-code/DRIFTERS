package com.dinglebob.drifter.brain.activity;

import java.util.Set;

import com.dinglebob.drifter.brain.FurnaceJob;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.hands.Containers;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Coming back to a furnace it left cooking (section 10.4) and taking out what's done. If some input is still cooking
 * (it came back early), it notes when to come back again.
 */
public class CollectSmeltActivity extends Activity {
	private final FurnaceJob job;
	private boolean walked;
	private boolean took;

	public CollectSmeltActivity(DrifterEntity drifter, FurnaceJob job) {
		super(drifter);
		this.job = job;
	}

	@Override
	public String describe() {
		return "collect " + this.job.output.getDescription().getString().toLowerCase(java.util.Locale.ROOT) + " from the furnace";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 90;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof ContainerTask && status == Status.SUCCESS) {
			this.took = true;
		}
	}

	@Override
	protected Status next() {
		BlockPos furnace = this.job.furnace;
		if (!(this.drifter.level().getBlockEntity(furnace) instanceof AbstractFurnaceBlockEntity)) {
			this.drifter.getDrifterBrain().removeFurnaceJob(this.job);
			return this.fail("the furnace is gone");
		}

		if (this.took) {
			// Anything still cooking? Then come back later for the rest.
			Container container = Containers.get(this.drifter.level(), furnace);
			int left = container == null ? 0 : container.getItem(SmeltActivity.FURNACE_INPUT).getCount();
			if (left > 0) {
				this.job.count = left;
				this.job.readyAt = this.now() + (long)left * SmeltActivity.TICKS_PER_ITEM + 20;
			} else {
				this.drifter.getDrifterBrain().removeFurnaceJob(this.job);
			}

			return Status.SUCCESS;
		}

		if (!Reach.canReach(this.drifter, furnace)) {
			BlockPos spot = this.walked ? null : KnownBlocks.standingSpotFor(this.drifter, furnace, Set.of());
			if (spot == null) {
				return this.fail("can't get to the furnace");
			}

			this.walked = true;
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk back to the furnace"));
		}

		return this.begin(new ContainerTask(this.drifter, furnace, ContainerTask.Mode.TAKE, stack -> true, "what's done", Integer.MAX_VALUE,
			new int[]{SmeltActivity.FURNACE_RESULT}));
	}
}
