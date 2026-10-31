package com.dinglebob.drifter.brain.activity;

import java.util.List;

import com.dinglebob.drifter.brain.Planner;
import com.dinglebob.drifter.brain.Stations;
import com.dinglebob.drifter.compat.FarmersDelight;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.BoardTask;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.executor.task.WaitTask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/**
 * Cutting on the board (Farmer's Delight, optional; how a player does it: "place an item on it by right-clicking, then
 * right-click again with a knife"): walks to the cutting board, puts the pieces on, cuts them one by one, and picks up what
 * pops off.
 */
public class CutActivity extends KitchenActivity {
	private final Planner.Cut cut;
	private boolean started;

	public CutActivity(DrifterEntity drifter, Planner.Cut cut) {
		super(drifter);
		this.cut = cut;
	}

	@Override
	public String describe() {
		return this.cut.describe();
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 90;
	}

	@Override
	protected Status next() {
		if (this.stepFailure != null) {
			return this.fail(this.stepFailure);
		}

		if (this.started) {
			return Status.SUCCESS;
		}

		Block block = FarmersDelight.block("cutting_board");
		BlockPos board = block == null ? null : Stations.usable(this.drifter, block);
		if (board == null) {
			return this.fail("needs a cutting board");
		}

		// Someone else's things on it: not its to take (it waits for a clear board).
		if (!FarmersDelight.onBoard(this.drifter.level(), board).isEmpty()) {
			return this.fail("the cutting board has something on it");
		}

		Status walk = this.reach(board, "cutting board");
		if (walk != null) {
			return walk;
		}

		int have = this.drifter.getInventory().count(this.cut.input());
		int times = Math.min(this.cut.times(), have);
		if (times <= 0) {
			return this.fail("has no " + this.cut.inputLabel() + " to cut");
		}

		this.started = true;
		this.think("cutting " + times + " " + this.cut.inputLabel() + " on the board");
		return this.begin(new SequenceTask(this.drifter, this.describe(), List.of(
			new SelectItemTask(this.drifter, this.cut.input(), this.cut.inputLabel()),
			BoardTask.put(this.drifter, board, times, this.cut.input(), this.cut.inputLabel()),
			new SelectItemTask(this.drifter, this.cut.tool(), this.cut.toolLabel()),
			BoardTask.cut(this.drifter, board, times, this.cut.tool(), this.cut.toolLabel()),
			// What came off lands beside the board: most it picks up standing there, the rest it steps over to.
			new WaitTask(this.drifter, "the pieces to land", landed(), 40),
			new CollectItemsTask(this.drifter, 3.0))));
	}

	/** A moment for the pieces to land (most it picks up where it stands). */
	private static java.util.function.BooleanSupplier landed() {
		int[] ticks = {0};
		return () -> ++ticks[0] >= 15;
	}
}
