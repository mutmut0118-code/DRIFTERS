package com.dinglebob.drifter.brain.activity;

import java.util.Locale;
import java.util.Set;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/**
 * Taking something it stored out of its chest at home (the planner's "fetch" step, section 9: stage and plans count home
 * storage). If the chest turns out not to have it (someone took it), opening it updates what it knows and it plans again.
 */
public class FetchActivity extends Activity {
	private final BlockPos chest;
	private final Item item;
	private final int count;
	private boolean walked;
	private boolean done;

	public FetchActivity(DrifterEntity drifter, BlockPos chest, Item item, int count) {
		super(drifter);
		this.chest = chest;
		this.item = item;
		this.count = count;
	}

	@Override
	public String describe() {
		return "fetch " + this.count + " " + this.item.getDescription().getString().toLowerCase(Locale.ROOT) + " from its chest";
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

	/** It has walked over toward the chest to see round it. */
	private boolean approached;

	@Override
	protected Status next() {
		if (this.done) {
			return Status.SUCCESS;
		}

		if (!Reach.canReach(this.drifter, this.chest)) {
			BlockPos spot = this.walked ? null : KnownBlocks.standingSpotFor(this.drifter, this.chest, Set.of());
			if (spot == null) {
				// From across town it hasn't seen the floor by the chest yet (found in testing: a town's store it had never been
				// in, "can't get to its chest" from 40 blocks off): over there first, then it sees where to stand.
				if (!this.approached && this.drifter.position().distanceTo(Vec3.atCenterOf(this.chest)) > 4.0) {
					this.approached = true;
					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.chest), 3.0, "go over to the chest"));
				}

				return this.fail("can't get to its chest");
			}

			this.walked = true;
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk to the chest"));
		}

		return this.begin(new ContainerTask(this.drifter, this.chest, ContainerTask.Mode.TAKE, stack -> stack.is(this.item),
			this.item.getDescription().getString().toLowerCase(Locale.ROOT), this.count, null));
	}
}
