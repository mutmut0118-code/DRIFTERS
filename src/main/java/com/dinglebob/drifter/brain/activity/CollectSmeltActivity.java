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
	private boolean approached;
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

	/** Trips back that failed before it gives a furnace up. */
	private static final int TRIPS = 3;

	@Override
	protected void onStop() {
		// A furnace it can't get back to (found in desert tests: one left at the bottom of a shaft through the sand, the
		// stone in it never collected, the house never built): after a few tries it gives it up and smelts again elsewhere,
		// as a player would rather than dig all the way back.
		String why = this.failureReason();
		if (!this.took && why != null && !why.startsWith("no room") && !why.startsWith("the furnace is gone") && ++this.job.failedTrips >= TRIPS) {
			this.drifter.getDrifterBrain().removeFurnaceJob(this.job);
			com.dinglebob.drifter.brain.Stations.giveUp(this.drifter, this.drifter.level().getBlockState(this.job.furnace).getBlock(), this.job.furnace);
			this.think("can't get back to the furnace at " + this.job.furnace.toShortString() + ": leaving what's in it");
		}

		super.onStop();
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
				this.job.readyAt = this.now() + (long)left * SmeltActivity.ticksPerItem(this.drifter.level(), furnace) + 20;
			} else {
				this.drifter.getDrifterBrain().removeFurnaceJob(this.job);
			}

			return Status.SUCCESS;
		}

		if (!Reach.canReach(this.drifter, furnace)) {
			BlockPos spot = this.walked ? null : KnownBlocks.standingSpotFor(this.drifter, furnace, Set.of());
			if (spot == null && !this.approached && this.drifter.blockPosition().distSqr(furnace) > 4 * 4) {
				// It doesn't know the ground by the furnace from here (a ravine, a long way off): it heads that way to look,
				// as the crafting and smelting steps do (found in a user's test: it failed at once, over and over, in a ravine).
				this.approached = true;
				return this.begin(new NavigateTask(this.drifter, Vec3.atCenterOf(furnace), 3.0, "walk back toward the furnace"));
			}

			if (spot == null) {
				return this.fail("can't get to the furnace");
			}

			this.walked = true;
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk back to the furnace"));
		}

		// A look inside first (found in testing: a job whose furnace had nothing to take pulled it back 37 times).
		Container container = Containers.get(this.drifter.level(), furnace);
		if (container != null && container.getItem(SmeltActivity.FURNACE_RESULT).isEmpty()) {
			int left = container.getItem(SmeltActivity.FURNACE_INPUT).getCount();
			var state = this.drifter.level().getBlockState(furnace);
			boolean burning = state.hasProperty(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT)
				&& state.getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT);
			if (left == 0) {
				this.drifter.getDrifterBrain().removeFurnaceJob(this.job);
				this.think("nothing in the furnace any more");
				return Status.SUCCESS;
			}

			if (!burning && container.getItem(SmeltActivity.FURNACE_FUEL).isEmpty()) {
				// Out of fuel with things still in: it takes them back (the planner smelts them again with fuel).
				this.think("the furnace ran out of fuel: taking the rest back");
				this.took = true;
				return this.begin(new ContainerTask(this.drifter, furnace, ContainerTask.Mode.TAKE, stack -> true, "what's left", Integer.MAX_VALUE,
					new int[]{SmeltActivity.FURNACE_INPUT}));
			}

			this.job.readyAt = this.now() + (long)left * SmeltActivity.ticksPerItem(this.drifter.level(), furnace) + 20;
			return Status.SUCCESS;
		}

		if (com.dinglebob.drifter.brain.Storage.freeSlots(this.drifter) == 0) {
			this.job.readyAt = this.now() + 20 * 60;
			return this.fail("no room for what's done");
		}

		return this.begin(new ContainerTask(this.drifter, furnace, ContainerTask.Mode.TAKE, stack -> true, "what's done", Integer.MAX_VALUE,
			new int[]{SmeltActivity.FURNACE_RESULT}));
	}
}
