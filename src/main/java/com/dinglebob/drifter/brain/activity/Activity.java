package com.dinglebob.drifter.brain.activity;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Blacklist;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.MineTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.executor.task.WaitTask;
import com.dinglebob.drifter.hands.Tools;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a goal does (DESIGN.md section 10): a task that keeps choosing its next small step (walk there, mine that,
 * craft this) from the current state, runs it as a child task, and repeats until it is done or keeps failing. Choosing
 * steps afresh each time is the "replan from the current state" rule of section 8.
 */
public abstract class Activity extends Task {
	/** Failed steps before the activity gives up (the selector then picks something else). */
	public static final int MAX_FAILURES = 6;

	@Nullable
	private Task child;

	/** The step it's on, or null between steps. */
	@org.jetbrains.annotations.Nullable
	protected Task current() {
		return this.child;
	}
	private int failures;
	@Nullable
	private String lastFailure;

	protected Activity(DrifterEntity drifter) {
		super(drifter);
	}

	/** Picks and starts the next step with {@link #begin}, or finishes with SUCCESS or {@link #fail}. */
	protected abstract Status next();

	/** Whether a failed step counts toward giving up (a wait that simply ran out of time doesn't). */
	protected boolean countsAsFailure(Task step) {
		return true;
	}

	/** Called when a step finishes, before {@link #next()}. */
	protected void stepDone(Task step, Status status) {
	}

	protected final Status begin(Task step) {
		this.child = step;
		return Status.RUNNING;
	}

	/** Checked every tick: true drops the current step so {@link #next()} picks again at once. */
	protected boolean rethink() {
		return false;
	}

	/** Checked every tick: true ends the activity successfully at once (it found what it was looking for). */
	protected boolean finishedEarly() {
		return false;
	}

	/** How deep activities are nested right now (server thread only). */
	private static int depth;
	/** Deeper than this is a loop of activities starting each other: the innermost fails instead of crashing the server. */
	private static final int MAX_DEPTH = 24;

	@Override
	protected final Status onTick() {
		if (depth >= MAX_DEPTH) {
			return this.fail("activities nested too deep (a loop)");
		}

		depth++;
		try {
			return this.tickInside();
		} finally {
			depth--;
		}
	}

	private Status tickInside() {
		if (this.finishedEarly()) {
			if (this.child != null) {
				this.child.stop();
				this.child = null;
			}

			return Status.SUCCESS;
		}

		if (this.child != null && this.rethink()) {
			this.child.stop();
			this.child = null;
		}

		if (this.child != null) {
			Status status = this.child.tick();
			if (status == Status.RUNNING) {
				return Status.RUNNING;
			}

			Task finished = this.child;
			this.child = null;
			finished.stop();
			if (status == Status.FAILURE && this.countsAsFailure(finished)) {
				this.lastFailure = finished.describe() + ": " + finished.failureReason();
				this.think("couldn't " + this.lastFailure);
				if (++this.failures > MAX_FAILURES) {
					return this.fail("too many failed steps (last: " + this.lastFailure + ")");
				}
			}

			this.stepDone(finished, status);
		}

		return this.next();
	}

	@Override
	protected void onStop() {
		if (this.child != null) {
			this.child.stop();
			this.child = null;
		}
	}

	/**
	 * A safe point to switch goals (section 7, "Atomic actions finish first"): between steps, or while only walking or
	 * waiting. A block half-broken, a craft or a bite of food finishes first.
	 */
	public boolean atCheckpoint() {
		// A job running another job (setting up home -> gathering logs): safe when that one is.
		return this.child == null || this.child instanceof NavigateTask navigate && navigate.isSafeToInterrupt() || this.child instanceof WaitTask
			|| this.child instanceof Activity activity && activity.atCheckpoint();
	}

	@Override
	public java.util.Set<BodyPart> freeParts() {
		return this.child == null ? NOTHING_FREE : this.child.freeParts();
	}

	@Override
	public Task leaf() {
		return this.child == null ? this : this.child.leaf();
	}

	/** The step running now, if any. */
	@Nullable
	protected Task child() {
		return this.child;
	}

	/** What it's doing right now, for the overlay. */
	public String currentStep() {
		return this.child == null ? "thinking" : this.child.describe();
	}

	protected int failures() {
		return this.failures;
	}

	protected long now() {
		return this.drifter.level().getGameTime();
	}

	protected Blacklist blacklist() {
		return this.drifter.getDrifterBrain().blacklist();
	}

	protected void think(String thought) {
		this.drifter.getMemory().thoughts().add(this.now(), thought);
	}

	/** Takes out the best tool it has for the block, then mines it. */
	protected Task dig(BlockPos pos) {
		return digTask(this.drifter, pos);
	}

	/** Takes out the best tool it has for the block, then mines it (for helpers that hand tasks to an activity). */
	public static Task digTask(DrifterEntity drifter, BlockPos pos) {
		BlockState state = drifter.level().getBlockState(pos);
		String label = "dig " + state.getBlock().getName().getString().toLowerCase(java.util.Locale.ROOT);
		java.util.List<Task> steps = new java.util.ArrayList<>();
		// Snow or grass on top of it, looking down at it: that's what the crosshair hits, so it goes first, like a player
		// (found by the user: odd behaviour in snowy biomes).
		BlockPos above = pos.above();
		if (drifter.getEyeY() > pos.getY() + 1 && com.dinglebob.drifter.executor.task.PlaceBlockTask.isClickableReplaceable(drifter.level(), above)) {
			steps.add(new MineTask(drifter, above));
		}

		int slot = Tools.bestToolSlot(drifter, state, drifter.level(), pos);
		// Plain rock with a stone pickaxe along: that one, keeping the better pickaxe for ore that needs it (found in
		// testing: a staircase down to the diamonds wore out its iron pickaxe before it got there). Section 10.3: thrifty
		// players don't use their good pickaxe on plain stone; only the most efficient don't care.
		int cheap = drifter.getInventory().findSlot(stack -> stack.is(net.minecraft.world.item.Items.STONE_PICKAXE), 0,
			com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE);
		if (cheap >= 0 && state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
			&& drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.EFFICIENCY) < 0.8) {
			slot = cheap;
		}

		if (slot >= 0) {
			ItemStack tool = drifter.getInventory().getItem(slot);
			steps.add(new SelectItemTask(drifter, stack -> stack == tool, tool.getHoverName().getString()));
		}

		steps.add(new MineTask(drifter, pos));
		return steps.size() == 1 ? steps.get(0) : new SequenceTask(drifter, label, steps);
	}
}
