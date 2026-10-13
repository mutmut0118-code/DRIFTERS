package com.dinglebob.drifter.executor;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.network.chat.Component;

/**
 * Runs one top-level task at a time for a Drifter. Starting a new task cancels the old one.
 * Until the brain exists (milestone 5), tasks are started by debug commands, which pass a feedback sink
 * so the result is reported in chat.
 */
public class TaskRunner {
	private final DrifterEntity drifter;
	@Nullable
	private Task current;
	@Nullable
	private Consumer<Component> feedback;

	public TaskRunner(DrifterEntity drifter) {
		this.drifter = drifter;
	}

	public void start(Task task, @Nullable Consumer<Component> feedback) {
		this.cancel("replaced by: " + task.describe());
		this.current = task;
		this.feedback = feedback;
	}

	public boolean cancel(String why) {
		if (this.current == null) {
			return false;
		}

		Task task = this.current;
		this.current = null;
		task.stop();
		this.report("Stopped " + task.describe() + " (" + why + ")");
		return true;
	}

	@Nullable
	public Task current() {
		return this.current;
	}

	public void tick() {
		if (this.current == null) {
			return;
		}

		Task task = this.current;
		Task.Status status = task.tick();
		if (status == Task.Status.RUNNING || this.current != task) {
			return;
		}

		this.current = null;
		task.stop();
		if (status == Task.Status.SUCCESS) {
			this.report("Done: " + task.describe());
		} else {
			this.report("Failed: " + task.describe() + " (" + task.failureReason() + ")");
		}
	}

	private void report(String message) {
		if (this.feedback != null) {
			this.feedback.accept(Component.literal("[" + this.drifter.getName().getString() + "] " + message));
		}
	}
}
