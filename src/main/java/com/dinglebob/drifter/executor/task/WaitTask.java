package com.dinglebob.drifter.executor.task;

import java.util.function.BooleanSupplier;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;

/** Wait (DESIGN.md section 8): waits until a condition holds (a furnace finishing, daylight), with a timeout. */
public class WaitTask extends Task {
	private final String label;
	private final BooleanSupplier done;
	private final int timeout;

	public WaitTask(DrifterEntity drifter, String label, BooleanSupplier done, int timeoutTicks) {
		super(drifter);
		this.label = label;
		this.done = done;
		this.timeout = timeoutTicks;
	}

	@Override
	public String describe() {
		return "wait for " + this.label;
	}

	@Override
	protected int timeoutTicks() {
		return this.timeout;
	}

	@Override
	protected Status onTick() {
		return this.done.getAsBoolean() ? Status.SUCCESS : Status.RUNNING;
	}
}
