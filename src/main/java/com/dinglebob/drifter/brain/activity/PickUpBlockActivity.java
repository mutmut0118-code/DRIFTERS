package com.dinglebob.drifter.brain.activity;

import java.util.List;
import java.util.Set;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Breaks a block of its own and picks up the drop (a crafting table it's done with). */
public class PickUpBlockActivity extends Activity {
	private final BlockPos pos;
	private final String label;
	private boolean done;
	private int walks;

	public PickUpBlockActivity(DrifterEntity drifter, BlockPos pos, String label) {
		super(drifter);
		this.pos = pos;
		this.label = label;
	}

	@Override
	public String describe() {
		return "pick up " + this.label;
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 30;
	}

	@Override
	protected Status next() {
		if (this.done || this.drifter.level().getBlockState(this.pos).isAir()) {
			return Status.SUCCESS;
		}

		if (Reach.canReach(this.drifter, this.pos)) {
			this.done = true;
			return this.begin(new SequenceTask(this.drifter, "pick up " + this.label, List.of(this.dig(this.pos), new CollectItemsTask(this.drifter, 3.0))));
		}

		BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, this.pos, Set.of());
		if (spot == null || this.walks++ > 2) {
			return this.fail("can't get to " + this.label);
		}

		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk over to " + this.label));
	}
}
