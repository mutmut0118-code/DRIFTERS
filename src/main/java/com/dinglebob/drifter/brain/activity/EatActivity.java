package com.dinglebob.drifter.brain.activity;

import java.util.List;

import com.dinglebob.drifter.brain.Food;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.executor.task.UseItemTask;

import net.minecraft.world.item.ItemStack;

/** Eats until full enough (section 10.5), picking food that fills without waste. Eating takes 1.6 s a bite. */
public class EatActivity extends Activity {
	/** Stops eating at this food level. */
	public static final int FULL_ENOUGH = 18;
	private int bites;

	public EatActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "eat";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 30;
	}

	@Override
	public boolean atCheckpoint() {
		// A bite in progress finishes first.
		return this.bites == 0 || super.atCheckpoint();
	}

	@Override
	protected Status next() {
		if (this.drifter.getFoodData().getFoodLevel() >= FULL_ENOUGH || this.bites >= 4) {
			return this.bites > 0 ? Status.SUCCESS : this.fail("isn't hungry");
		}

		ItemStack food = Food.choose(this.drifter);
		if (food == null) {
			return this.bites > 0 ? Status.SUCCESS : this.fail("has nothing it wants to eat");
		}

		this.bites++;
		ItemStack chosen = food;
		return this.begin(new SequenceTask(this.drifter, "eat " + chosen.getHoverName().getString().toLowerCase(java.util.Locale.ROOT), List.of(
			new SelectItemTask(this.drifter, stack -> stack == chosen, chosen.getHoverName().getString()),
			new UseItemTask(this.drifter))));
	}
}
