package com.dinglebob.drifter.brain.activity;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Out of the water, now (found in testing: it kept running out of air in a flooded cave and going back under for what
 * it was doing until it drowned). To the nearest dry ground it has seen, or back to where it last stood on dry ground.
 */
public class GetOutOfWaterActivity extends Activity {
	private int tries;

	public GetOutOfWaterActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "get out of the water";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60;
	}

	@Override
	protected Status next() {
		if (!this.drifter.isInWater() && this.drifter.onGround()) {
			this.think("out of the water");
			return Status.SUCCESS;
		}

		if (++this.tries > 3) {
			return this.fail("can't find a way out of the water");
		}

		// Where it last stood on dry ground first (the edge it came in from, usually), then the nearest dry ground it knows.
		BlockPos last = this.drifter.lastDryGround();
		BlockPos dry = last != null && this.tries == 1 && last.distSqr(this.drifter.blockPosition()) < 24 * 24 ? last
			: KnownBlocks.nearestDryGround(this.drifter, 24, 0.0, 1);
		if (dry == null) {
			dry = last;
		}

		if (dry == null) {
			return this.fail("knows no dry ground to get to");
		}

		this.think("nearly drowned: getting out of the water");
		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(dry), 0.5, "get out of the water"));
	}
}
