package com.dinglebob.drifter.brain.activity;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.WaitTask;

import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinArmPose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Bartering with piglins (section 19 piglin etiquette, milestone 11 step 6), the player's way: wearing its gold piece, it
 * walks up to a calm piglin, tosses it a gold ingot, waits while the piglin admires it (six seconds), and picks up what the
 * piglin throws back (ender pearls, fire resistance potions, string, obsidian...). A few at a time.
 */
public class BarterActivity extends Activity {
	/** Ingots it trades away in one go. */
	public static final int TRADES = 6;
	/** A piglin admires gold for six seconds, then throws its trade: a little longer. */
	private static final int ADMIRE_WAIT_TICKS = 20 * 12;
	private static final double TOSS_RANGE = 3.0;
	private static final double SEARCH_RANGE = 16.0;

	private int trades;
	private boolean tossed;
	@Nullable
	private Piglin piglin;

	public BarterActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "barter with a piglin (" + this.trades + " gold given)";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 120;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		return false;
	}

	@Override
	protected Status next() {
		// What the last one threw back: picked up first.
		if (this.tossed) {
			this.tossed = false;
			return this.begin(new CollectItemsTask(this.drifter, 6.0));
		}

		if (this.trades >= TRADES || gold(this.drifter) == 0) {
			this.think("bartered " + this.trades + " gold with the piglins");
			return Status.SUCCESS;
		}

		if (this.piglin == null || !willing(this.drifter, this.piglin)) {
			this.piglin = calmPiglin(this.drifter);
			if (this.piglin == null) {
				return this.trades > 0 ? Status.SUCCESS : this.fail("no piglin to barter with");
			}
		}

		if (this.drifter.distanceTo(this.piglin) > TOSS_RANGE) {
			return this.begin(new NavigateTask(this.drifter, this.piglin.position(), TOSS_RANGE - 0.5, "walk up to a piglin"));
		}

		// Facing it, a gold ingot tossed at its feet (Q on one ingot).
		if (!this.facing) {
			this.facing = true;
			return this.begin(new LookAtTask(this.drifter, this.piglin.position().add(0.0, 0.3, 0.0), 6));
		}

		this.facing = false;
		ItemStack ingot = takeGold(this.drifter);
		if (ingot.isEmpty()) {
			return Status.SUCCESS;
		}

		this.drifter.tossForward(ingot);
		this.drifter.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		this.trades++;
		this.tossed = true;
		if (this.trades == 1) {
			this.think("tossing a piglin some gold to barter");
		}

		// (The ingot can't be picked up for two seconds, by a piglin either; then six of admiring.)
		Piglin admirer = this.piglin;
		this.waited = 0;
		return this.begin(new WaitTask(this.drifter, "the piglin's trade", () -> !admirer.isAlive()
			|| this.waited++ > 60 && admirer.getArmPose() != PiglinArmPose.ADMIRING_ITEM && admirer.getOffhandItem().isEmpty(),
			ADMIRE_WAIT_TICKS));
	}

	private boolean facing;
	private int waited;

	/** Gold ingots it carries. */
	public static int gold(DrifterEntity drifter) {
		return drifter.getInventory().count(stack -> stack.is(Items.GOLD_INGOT));
	}

	private static ItemStack takeGold(DrifterEntity drifter) {
		var inventory = drifter.getInventory();
		for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Items.GOLD_INGOT)) {
				return stack.split(1);
			}
		}

		return ItemStack.EMPTY;
	}

	/** A grown piglin it can see, near, that isn't after it or busy admiring something. */
	@Nullable
	public static Piglin calmPiglin(DrifterEntity drifter) {
		Piglin best = null;
		double bestDistance = Double.MAX_VALUE;
		for (var awareness : drifter.getSenses().tracked()) {
			if (awareness.seenWithin(drifter.level().getGameTime(), 20 * 5) && awareness.entity instanceof Piglin candidate && willing(drifter, candidate)) {
				double distance = candidate.distanceTo(drifter);
				if (distance < SEARCH_RANGE && distance < bestDistance) {
					bestDistance = distance;
					best = candidate;
				}
			}
		}

		return best;
	}

	private static boolean willing(DrifterEntity drifter, Piglin piglin) {
		return piglin.isAlive() && piglin.isAdult() && piglin.getTarget() != drifter && !piglin.isAggressive()
			&& piglin.getArmPose() != PiglinArmPose.ADMIRING_ITEM && piglin.distanceTo(drifter) < SEARCH_RANGE;
	}
}
