package com.dinglebob.drifter.brain.activity;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.Stations;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.CraftTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.hands.Crafting;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Crafting (DESIGN.md section 10.2): in the 2x2 grid when the recipe fits, otherwise at a crafting table it knows
 * nearby, walking back to it if needed. The planner makes sure a table exists first.
 */
public class CraftActivity extends Activity {
	private final Item item;
	private final int times;
	private final String label;
	private boolean walked;
	private boolean approached;
	private boolean crafted;
	@org.jetbrains.annotations.Nullable
	private String stepFailure;

	public CraftActivity(DrifterEntity drifter, Item item, int times) {
		super(drifter);
		this.item = item;
		this.times = times;
		this.label = item.getDescription().getString().toLowerCase(java.util.Locale.ROOT);
	}

	@Override
	public String describe() {
		return "craft " + this.label + (this.times > 1 ? " x" + this.times : "");
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 90;
	}

	@Override
	protected void stepDone(com.dinglebob.drifter.executor.Task step, Status status) {
		if (status == Status.FAILURE) {
			this.stepFailure = step.describe() + ": " + step.failureReason();
		}
	}

	@Override
	protected Status next() {
		if (this.stepFailure != null) {
			return this.fail(this.stepFailure);
		}

		if (this.crafted) {
			return Status.SUCCESS;
		}

		if (Crafting.find(this.drifter, stack -> stack.is(this.item), false).isPresent()) {
			this.crafted = true;
			return this.begin(new CraftTask(this.drifter, stack -> stack.is(this.item), this.label, this.times, null));
		}

		BlockPos table = Stations.usable(this.drifter, Blocks.CRAFTING_TABLE);
		if (table == null) {
			return this.fail("needs a crafting table");
		}

		if (!Reach.canReach(this.drifter, table)) {
			if (this.walked) {
				return this.fail("can't get within reach of the crafting table");
			}

			@Nullable BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, table, Set.of());
			if (spot == null && !this.approached && this.drifter.blockPosition().distSqr(table) > 4 * 4) {
				// It doesn't remember the ground round it well enough from here (its home, seen hours ago): go and look.
				this.approached = true;
				return this.begin(new NavigateTask(this.drifter, Vec3.atCenterOf(table), 3.0, "walk over to the crafting table"));
			}

			if (spot == null) {
				return this.fail("can't find a spot by the crafting table at " + table.toShortString());
			}

			this.walked = true;
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk to the crafting table"));
		}

		this.crafted = true;
		return this.begin(new CraftTask(this.drifter, stack -> stack.is(this.item), this.label, this.times, table));
	}
}
