package com.dinglebob.drifter.executor;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

/**
 * One executor task (DESIGN.md section 8, layer 4): a small state machine that runs every tick and reports
 * running, success or failure. Every task has a timeout of roughly 3x its expected time.
 *
 * <p>TODO(milestone 7): tasks declare which body parts (head, hands, legs) they use, so mannerisms only use free ones.
 */
public abstract class Task {
	public enum Status {
		RUNNING, SUCCESS, FAILURE
	}

	protected final DrifterEntity drifter;
	private int ticks;
	@Nullable
	private String failureReason;
	private boolean started;
	private boolean stopped;

	protected Task(DrifterEntity drifter) {
		this.drifter = drifter;
	}

	/** Short plain-words description, e.g. "mine oak log at 10 64 -3". */
	public abstract String describe();

	/** Ticks before the task gives up. Override with about 3x the expected time. */
	protected int timeoutTicks() {
		return 20 * 60;
	}

	protected void onStart() {
	}

	protected abstract Status onTick();

	/** Cleanup. Called exactly once when the task finishes, fails, times out or is cancelled. */
	protected void onStop() {
	}

	public final Status tick() {
		if (!this.started) {
			this.started = true;
			this.onStart();
			if (this.failureReason != null) {
				return Status.FAILURE;
			}
		}

		if (++this.ticks > this.timeoutTicks()) {
			return this.fail("timed out after " + this.timeoutTicks() / 20 + " s");
		}

		return this.onTick();
	}

	public final void stop() {
		if (this.started && !this.stopped) {
			this.stopped = true;
			this.onStop();
		}
	}

	protected int ticksRunning() {
		return this.ticks;
	}

	/** Marks the task failed. Can be called from onStart (the task then fails on its first tick). */
	protected Status fail(String reason) {
		this.failureReason = reason;
		return Status.FAILURE;
	}

	@Nullable
	public String failureReason() {
		return this.failureReason;
	}
}
