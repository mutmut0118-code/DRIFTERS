package com.dinglebob.drifter.brain.activity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.executor.task.UseItemTask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Filling buckets with water (for a cooking pot's recipe, Farmer's Delight): to the nearest water it has seen, a bucket in
 * hand, a right-click at the surface, as the farm fills its bucket.
 */
public class FillBucketActivity extends Activity {
	private final int count;
	private final int start;
	private final Set<BlockPos> tried = new HashSet<>();
	private int walks;
	private int fills;

	public FillBucketActivity(DrifterEntity drifter, int count) {
		super(drifter);
		this.count = count;
		this.start = drifter.getInventory().count(stack -> stack.is(Items.WATER_BUCKET));
	}

	@Override
	public String describe() {
		return "fill " + (this.count == 1 ? "a bucket" : this.count + " buckets") + " with water";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 90;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (status == Status.FAILURE && step instanceof SequenceTask) {
			this.walks++;
		}
	}

	@Override
	protected Status next() {
		int filled = this.drifter.getInventory().count(stack -> stack.is(Items.WATER_BUCKET)) - this.start;
		if (filled >= this.count) {
			return Status.SUCCESS;
		}

		if (this.drifter.getInventory().count(stack -> stack.is(Items.BUCKET)) == 0) {
			return filled > 0 ? Status.SUCCESS : this.fail("has no empty bucket");
		}

		BlockPos water = FarmActivity.nearestWater(this.drifter, 48, this.tried);
		if (water == null || this.walks > 4) {
			return this.fail("knows no water to fill a bucket at");
		}

		if (this.drifter.getEyePosition().distanceTo(Vec3.atCenterOf(water)) > this.drifter.blockInteractionRange() - 0.5) {
			BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, water, Set.of());
			if (spot == null) {
				this.tried.add(water);
				return this.begin(new NavigateTask(this.drifter, Vec3.atCenterOf(water), 3.0, "walk over to the water"));
			}

			this.walks++;
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk to the water"));
		}

		if (this.fills++ > this.count + 2) {
			return filled > 0 ? Status.SUCCESS : this.fail("the bucket won't fill there");
		}

		this.think("filling a bucket with water");
		return this.begin(new SequenceTask(this.drifter, "fill the bucket", List.of(
			new SelectItemTask(this.drifter, stack -> stack.is(Items.BUCKET), "a bucket"),
			new LookAtTask(this.drifter, Vec3.atCenterOf(water).add(0.0, 0.4, 0.0), 4),
			new UseItemTask(this.drifter))));
	}
}
