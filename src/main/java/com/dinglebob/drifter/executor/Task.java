package com.dinglebob.drifter.executor;

import java.util.EnumSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

/**
 * One executor task (DESIGN.md section 8, layer 4): a small state machine that runs every tick and reports
 * running, success or failure. Every task has a timeout of roughly 3x its expected time.
 *
 * <p>Tasks say which body parts (head, hands, legs) they leave free right now (section 8 "body-part claims"), so the
 * mannerism layer (section 16.6) only uses free ones. By default a task claims the whole body.
 */
public abstract class Task {
	public enum Status {
		RUNNING, SUCCESS, FAILURE
	}

	/** Body parts a task can leave free for mannerisms (section 8). */
	public enum BodyPart {
		/** The camera (head yaw and pitch). */
		HEAD,
		/** The hotbar, swinging, and the offhand. */
		HANDS,
		/** The movement keys. */
		LEGS
	}

	public static final Set<BodyPart> NOTHING_FREE = EnumSet.noneOf(BodyPart.class);
	public static final Set<BodyPart> ALL_FREE = EnumSet.allOf(BodyPart.class);

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

	/** Body parts this task leaves free right now (section 8). Default: none, the task has the whole body. */
	public Set<BodyPart> freeParts() {
		return NOTHING_FREE;
	}

	/** The innermost task actually running (a composite task's current step). */
	public Task leaf() {
		return this;
	}

	public int ticksRunning() {
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
