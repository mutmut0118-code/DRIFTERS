package com.dinglebob.drifter.encounter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;

/**
 * An encounter part's steps, in order, each made when its turn comes (so it reads the world then; a step that's
 * nothing to do now gives null and is skipped). A step that fails ends it.
 */
final class Script extends Activity {
	private final String what;
	private final int timeout;
	private final List<Supplier<Task>> steps = new ArrayList<>();
	private int next;
	private boolean failed;

	Script(DrifterEntity drifter, String what, int timeout) {
		super(drifter);
		this.what = what;
		this.timeout = timeout;
	}

	Script then(Supplier<Task> step) {
		this.steps.add(step);
		return this;
	}

	/** A step that just does something at once (says a line, hands something over). */
	Script now(Runnable action) {
		this.steps.add(() -> {
			action.run();
			return null;
		});
		return this;
	}

	@Override
	public String describe() {
		return this.what;
	}

	/** Its time is up: it simply ends (re-planned at once if its part still wants doing), not a failure. */
	@Override
	protected boolean finishedEarly() {
		return this.ticksRunning() >= this.timeout;
	}

	@Override
	protected int timeoutTicks() {
		return this.timeout + 20 * 10;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (status == Status.FAILURE) {
			this.failed = true;
		}
	}

	@Override
	protected Status next() {
		if (this.failed) {
			return Status.SUCCESS;
		}

		while (this.next < this.steps.size()) {
			Task task = this.steps.get(this.next++).get();
			if (task != null) {
				return this.begin(task);
			}
		}

		return Status.SUCCESS;
	}
}
