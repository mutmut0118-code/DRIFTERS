package com.dinglebob.drifter.brain.activity;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.mining.Caver;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;

import net.minecraft.core.BlockPos;

/**
 * Delving an underground structure (user's request, 2026-10-17: "i would love to be in a mineshaft, and run into a
 * drifter looting a chest"). A well-armed Drifter that came down to loot it: it works its way through the tunnels the
 * way a caving trip does (only where it has looked, lighting its way, looting every chest minecart it sees), fights
 * what it meets, and once it has been through (or after a day or so down there) it's an ordinary Drifter again.
 */
public class DelveActivity extends Activity {
	/** Walks to new spots per stretch; the goal starts another while it's still delving. */
	private static final int LEGS = 24;

	private final Caver caver;

	public DelveActivity(DrifterEntity drifter) {
		super(drifter);
		BlockPos from = drifter.delveFrom() != null ? drifter.delveFrom() : drifter.blockPosition();
		// It keeps to the structure's depth: the frontier search leans toward where it went in.
		this.caver = new Caver(drifter, drifter.blockPosition(), from.getY(), LEGS);
	}

	@Override
	public String describe() {
		return "delve the mineshaft";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 6;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		return false;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		this.caver.stepDone(step, status);
		this.relay();
	}

	@Override
	protected Status next() {
		Task step = this.caver.next();
		this.relay();
		if (step != null) {
			return this.begin(step);
		}

		// Nowhere new to go from here: it has been through the place.
		this.drifter.stopDelving();
		this.think("been through this mineshaft; time to move on");
		return Status.SUCCESS;
	}

	private void relay() {
		String thought = this.caver.takeThought();
		if (thought != null) {
			this.think(thought);
		}
	}

	/** The goal: while it's delving, this is what it's doing (fights, fleeing, eating and sleep come first). */
	public static final class Goal extends Goals.Goal {
		@Override
		public String id() {
			return "delve";
		}

		@Override
		public String name(Goals.Context context) {
			return "delve the mineshaft";
		}

		@Override
		public double urgency(Goals.Context context) {
			return context.drifter().delving() ? 0.75 : 0.0;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public double risk(Goals.Context context) {
			return 1.0;
		}

		@Override
		public Activity start(Goals.Context context) {
			return new DelveActivity(context.drifter());
		}
	}
}
