package com.dinglebob.drifter.trade;

import java.util.Set;
import java.util.function.Predicate;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * A trip to a chest that isn't (only) its own to put something in or take something out (a town's till, milestone 22 step
 * 4): walks over (to near it first if it hasn't seen round it), opens it, moves up to {@code count} of what matches, and
 * closes it, as a player does. What it moved shows in its pockets after.
 */
public class ChestErrand extends Activity {
	private final BlockPos chest;
	private final ContainerTask.Mode mode;
	private final Predicate<ItemStack> what;
	private final String label;
	private final int count;
	private boolean walked;
	private boolean approached;
	private boolean done;

	public ChestErrand(DrifterEntity drifter, BlockPos chest, ContainerTask.Mode mode, Predicate<ItemStack> what, String label, int count) {
		super(drifter);
		this.chest = chest.immutable();
		this.mode = mode;
		this.what = what;
		this.label = label;
		this.count = count;
	}

	@Override
	public String describe() {
		return (this.mode == ContainerTask.Mode.PUT ? "put " : "take ") + this.label + (this.mode == ContainerTask.Mode.PUT ? " into " : " out of ")
			+ "the chest at " + this.chest.toShortString();
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 3;
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
				if (!this.approached && this.drifter.position().distanceTo(Vec3.atCenterOf(this.chest)) > 4.0) {
					this.approached = true;
					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.chest), 3.0, "go over to the chest"));
				}

				return this.fail("can't get to the chest");
			}

			this.walked = true;
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk to the chest"));
		}

		return this.begin(new ContainerTask(this.drifter, this.chest, this.mode, this.what, this.label, this.count, null));
	}
}
