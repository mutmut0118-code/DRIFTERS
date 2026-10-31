package com.dinglebob.drifter.brain.activity;

import java.util.ArrayList;
import java.util.List;

import com.dinglebob.drifter.brain.Planner;
import com.dinglebob.drifter.compat.FarmersDelight;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.executor.task.UseOnBlockTask;
import com.dinglebob.drifter.executor.task.WaitTask;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Cooking a meal in the pot (Farmer's Delight, optional), the way a player does: at the pot on the kitchen's stove (lit
 * first with flint and steel if it went out), it opens the pot's window and puts each ingredient in a slot of its own, one per
 * batch, and the bowls in theirs; closes it and waits by the stove while it cooks (about ten seconds a batch: the bowls
 * serve each meal as it's done); then opens it again and takes the meals out. Leftovers the pot drops (a milk bucket's
 * bucket) it picks up.
 */
public class PotCookActivity extends KitchenActivity {
	private enum Phase {
		CLEAR, LOAD, COOK, TAKE, TIDY, DONE
	}

	@org.jetbrains.annotations.Nullable
	private final Planner.PotCook cook;
	private final net.minecraft.world.item.Item meal;
	/** Going back for a batch it left cooking: where the pot is (null: its kitchen's pot). */
	@org.jetbrains.annotations.Nullable
	private final BlockPos collectFrom;
	private Phase phase = Phase.CLEAR;
	private int batches;
	private int lights;
	private boolean waitedAtPot;

	public PotCookActivity(DrifterEntity drifter, Planner.PotCook cook) {
		super(drifter);
		this.cook = cook;
		this.meal = cook.meal();
		this.collectFrom = null;
	}

	/** Back for a batch it left in the pot (called away while it cooked). */
	public PotCookActivity(DrifterEntity drifter, com.dinglebob.drifter.brain.Brain.PotBatch batch) {
		super(drifter);
		this.cook = null;
		this.meal = batch.meal();
		this.collectFrom = batch.pot();
		this.phase = Phase.TAKE;
	}

	@Override
	public String describe() {
		return this.cook != null ? this.cook.describe() : "take the " + Planner.name(this.meal) + " out of the cooking pot";
	}

	/** Done (the meals out, or given up): the goal holds it till then. */
	public boolean finished() {
		return this.phase == Phase.DONE;
	}

	@Override
	protected int timeoutTicks() {
		return this.cook == null ? 20 * 60 : 20 * 60 + Math.max(this.cook.batches(), MOST_BATCHES) * this.cook.cookTicks() * 2;
	}

	/** Most batches it cooks at a go when it has the makings of more than it planned. */
	private static final int MOST_BATCHES = 3;

	private Container pot(BlockPos pos) {
		return FarmersDelight.pot(this.drifter.level(), pos);
	}

	@Override
	protected void stepDone(com.dinglebob.drifter.executor.Task step, Status status) {
		// (A wait that ran out: it takes out what's done all the same.)
		if (!(step instanceof WaitTask)) {
			super.stepDone(step, status);
		}
	}

	/** Batches it carries one of each ingredient (and a bowl) for, up to the plan's: worked out a batch at a time. */
	private int batchesCarried() {
		var inventory = this.drifter.getInventory();
		int[] left = new int[com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE];
		for (int slot = 0; slot < left.length; slot++) {
			left[slot] = inventory.getItem(slot).getCount();
		}

		int batches = 0;
		while (batches < Math.max(this.cook.batches(), MOST_BATCHES)) {
			List<java.util.function.Predicate<ItemStack>> wanted = new ArrayList<>(this.cook.inputs());
			if (this.cook.container() != null) {
				var container = this.cook.container();
				wanted.add(stack -> stack.is(container));
			}

			for (var want : wanted) {
				int found = -1;
				for (int slot = 0; slot < left.length && found < 0; slot++) {
					if (left[slot] > 0 && want.test(inventory.getItem(slot))) {
						found = slot;
					}
				}

				if (found < 0) {
					return batches;
				}

				left[found]--;
			}

			batches++;
		}

		return batches;
	}

	@Override
	protected Status next() {
		if (this.stepFailure != null) {
			return this.fail(this.stepFailure);
		}

		if (this.phase == Phase.DONE) {
			return Status.SUCCESS;
		}

		BlockPos pos = this.collectFrom != null ? this.collectFrom : Planner.usablePot(this.drifter);
		Container pot = pos == null ? null : this.pot(pos);
		if (pos == null || pot == null) {
			this.drifter.getDrifterBrain().setPotBatch(null);
			this.phase = Phase.DONE;
			return this.fail("knows no cooking pot on a stove");
		}

		Status walk = this.reach(pos, "cooking pot");
		if (walk != null) {
			return walk;
		}

		// The stove went out (water, a shovel): light it, as a player does, with flint and steel.
		if (!FarmersDelight.heatedFrom(this.drifter.level(), pos) && this.phase != Phase.TAKE && this.phase != Phase.TIDY) {
			if (!FarmersDelight.unlitStove(this.drifter.level().getBlockState(pos.below())) || this.lights++ >= 2
				|| this.drifter.getInventory().count(stack -> stack.is(Items.FLINT_AND_STEEL)) == 0) {
				return this.fail("the pot has no fire under it");
			}

			BlockPos stove = pos.below();
			Direction face = Direction.getNearest(this.drifter.getX() - stove.getX() - 0.5, 0.0, this.drifter.getZ() - stove.getZ() - 0.5);
			this.think("lighting the stove");
			return this.begin(new SequenceTask(this.drifter, "light the stove", List.of(
				new SelectItemTask(this.drifter, stack -> stack.is(Items.FLINT_AND_STEEL), "flint and steel"),
				UseOnBlockTask.useBlock(this.drifter, stove, face, "light the stove"))));
		}

		switch (this.phase) {
			case CLEAR -> {
				this.phase = Phase.LOAD;
				// Served meals left in it (its own from before, or a housemate's): taken out first, they'd block the slot.
				if (!pot.getItem(FarmersDelight.POT_OUTPUT).isEmpty()) {
					return this.begin(new ContainerTask(this.drifter, pos, ContainerTask.Mode.TAKE, stack -> true, "what's served", Integer.MAX_VALUE,
						new int[] {FarmersDelight.POT_OUTPUT}).through(FarmersDelight::pot));
				}

				return this.next();
			}
			case LOAD -> {
				ItemStack cooking = pot.getItem(FarmersDelight.POT_MEAL);
				if (!cooking.isEmpty() && !cooking.is(this.meal)) {
					return this.fail("the pot has " + Planner.name(cooking.getItem()) + " in it");
				}

				for (int slot = 0; slot < FarmersDelight.POT_INPUTS; slot++) {
					ItemStack in = pot.getItem(slot);
					if (!in.isEmpty() && (slot >= this.cook.inputs().size() || !this.cook.inputs().get(slot).test(in))) {
						return this.fail("the pot has other things in it");
					}
				}

				// As many batches as it has everything for.
				this.batches = this.batchesCarried();

				if (this.batches <= 0) {
					return this.fail("hasn't got everything for " + Planner.name(this.cook.meal()));
				}

				List<ContainerTask.Load> loads = new ArrayList<>();
				for (int i = 0; i < this.cook.inputs().size(); i++) {
					loads.add(new ContainerTask.Load(i, this.cook.inputs().get(i), Math.max(0, this.batches - pot.getItem(i).getCount())));
				}

				if (this.cook.container() != null) {
					var container = this.cook.container();
					loads.add(new ContainerTask.Load(FarmersDelight.POT_CONTAINER, stack -> stack.is(container), this.batches));
				}

				this.phase = Phase.COOK;
				// It remembers the batch: called away, it comes back for it.
				this.drifter.getDrifterBrain().setPotBatch(new com.dinglebob.drifter.brain.Brain.PotBatch(pos, this.meal, this.batches,
					this.now() + (long)this.batches * this.cook.cookTicks() + 40));
				this.think("cooking " + this.batches + " " + Planner.name(this.cook.meal()) + " (" + String.join(", ", this.cook.inputLabels()) + ")");
				return this.begin(ContainerTask.load(this.drifter, pos, FarmersDelight::pot, loads, Planner.name(this.cook.meal())));
			}
			case COOK -> {
				this.phase = Phase.TAKE;
				// It stays by the stove: done when every batch is cooked (the ingredients used up) and served, or (no bowls
				// needed) out in the served slot.
				int wait = this.batches * this.cook.cookTicks() + 60;
				return this.begin(new WaitTask(this.drifter, Planner.name(this.cook.meal()) + " to cook", () -> {
					Container now = this.pot(pos);
					if (now == null) {
						return true;
					}

					boolean inputsLeft = false;
					for (int slot = 0; slot < FarmersDelight.POT_INPUTS; slot++) {
						inputsLeft |= !now.getItem(slot).isEmpty();
					}

					return !inputsLeft && now.getItem(FarmersDelight.POT_MEAL).isEmpty();
				}, wait));
			}
			case TAKE -> {
				this.phase = Phase.TIDY;
				boolean stillCooking = !pot.getItem(FarmersDelight.POT_MEAL).isEmpty();
				for (int slot = 0; slot < FarmersDelight.POT_INPUTS; slot++) {
					stillCooking |= !pot.getItem(slot).isEmpty();
				}

				if (stillCooking && !this.waitedAtPot && FarmersDelight.heatedFrom(this.drifter.level(), pos)) {
					// Back before it's done: it waits by the stove for it.
					this.waitedAtPot = true;
					this.phase = Phase.TAKE;
					return this.begin(new WaitTask(this.drifter, Planner.name(this.meal) + " to finish", () -> {
						Container now = this.pot(pos);
						return now == null || !now.getItem(FarmersDelight.POT_OUTPUT).isEmpty() && now.getItem(FarmersDelight.POT_MEAL).isEmpty();
					}, 20 * 30));
				}

				if (pot.getItem(FarmersDelight.POT_OUTPUT).isEmpty()) {
					// Nothing served (no bowls in it, or someone took them): done here.
					this.drifter.getDrifterBrain().setPotBatch(null);
					this.phase = Phase.DONE;
					return this.cook == null ? Status.SUCCESS : this.fail("nothing came out of the pot");
				}

				return this.begin(new ContainerTask(this.drifter, pos, ContainerTask.Mode.TAKE, stack -> stack.is(this.meal), Planner.name(this.meal),
					Integer.MAX_VALUE, new int[] {FarmersDelight.POT_OUTPUT}).through(FarmersDelight::pot));
			}
			case TIDY -> {
				this.phase = Phase.DONE;
				this.drifter.getDrifterBrain().setPotBatch(null);
				com.dinglebob.drifter.brain.Kitchen.rethink(this.drifter);
				int made = this.drifter.getInventory().count(stack -> stack.is(this.meal));
				this.think("cooked " + Planner.name(this.meal) + " (" + made + " on it now)");
				this.drifter.getDrifterBrain().noteCooked(this.meal, this.now());
				// A bucket from milk, a bottle: what the pot threw out beside it.
				return this.begin(new CollectItemsTask(this.drifter, 2.5));
			}
			default -> {
				return Status.SUCCESS;
			}
		}
	}
}
