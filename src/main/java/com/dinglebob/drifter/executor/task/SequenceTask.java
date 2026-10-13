package com.dinglebob.drifter.executor.task;

import java.util.ArrayList;
import java.util.List;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;

/** Runs tasks one after another; fails as soon as one fails. */
public class SequenceTask extends Task {
	private final String label;
	private final List<Task> steps;
	private int index;

	public SequenceTask(DrifterEntity drifter, String label, List<Task> steps) {
		super(drifter);
		this.label = label;
		this.steps = new ArrayList<>(steps);
	}

	@Override
	public String describe() {
		return this.label;
	}

	@Override
	protected int timeoutTicks() {
		return Integer.MAX_VALUE;
	}

	@Override
	protected Status onTick() {
		if (this.index >= this.steps.size()) {
			return Status.SUCCESS;
		}

		Task step = this.steps.get(this.index);
		Status status = step.tick();
		if (status == Status.RUNNING) {
			return Status.RUNNING;
		}

		step.stop();
		if (status == Status.FAILURE) {
			return this.fail(step.describe() + ": " + step.failureReason());
		}

		this.index++;
		return this.index >= this.steps.size() ? Status.SUCCESS : Status.RUNNING;
	}

	@Override
	public java.util.Set<BodyPart> freeParts() {
		return this.index < this.steps.size() ? this.steps.get(this.index).freeParts() : NOTHING_FREE;
	}

	@Override
	public Task leaf() {
		return this.index < this.steps.size() ? this.steps.get(this.index).leaf() : this;
	}

	@Override
	protected void onStop() {
		if (this.index < this.steps.size()) {
			this.steps.get(this.index).stop();
		}
	}
}
