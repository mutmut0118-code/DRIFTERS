package com.dinglebob.drifter.whim;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.KnownWorld;
import com.dinglebob.drifter.brain.Planner;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Making what a whim needs first (milestone 13 step 2): a whim that needs a sign, a campfire or a flower pot it doesn't
 * carry can still come up if it can make one from what it has and knows round here (the whim's {@code "make"} param maps
 * a need to the item it crafts for it). Never a mining trip or a long search for it: it's a whim.
 */
final class WhimPrep {
	private WhimPrep() {
	}

	/** The item it would make for this need, or null if the whim doesn't say. */
	@Nullable
	static Item makes(Whim whim, Whim.Need need) {
		Map<String, String> make = whim.paramMap("make");
		String id = make.get(need.item());
		return id == null ? null : BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
	}

	/** It can make this need from what it has round here: a plan with no trips and no searching. */
	static boolean canMake(DrifterEntity drifter, Whim whim, Whim.Need need, @Nullable KnownWorld world) {
		Item item = makes(whim, need);
		if (item == null) {
			return false;
		}

		Planner.Plan plan = Planner.plan(drifter, world != null ? world : KnownWorld.scan(drifter), item, need.count());
		return plan.feasible() && quick(plan);
	}

	/** Crafting, smelting and fetching from its chest only. */
	static boolean quick(Planner.Plan plan) {
		return plan.actions().stream().noneMatch(action -> action instanceof Planner.Mine || action instanceof Planner.Explore
			|| action instanceof Planner.Hunt || action instanceof Planner.MakeObsidian);
	}

	/** First the making (a step of the plan at a time, planning again after each), then the whim itself. */
	static final class MakeThen extends Activity {
		private final Whim whim;
		private final WhimAction action;
		private int steps;
		private boolean started;

		MakeThen(DrifterEntity drifter, Whim whim, WhimAction action) {
			super(drifter);
			this.whim = whim;
			this.action = action;
		}

		@Override
		public String describe() {
			return this.whim.thought();
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60 * 5;
		}

		@Override
		protected Status next() {
			if (this.started) {
				return Status.SUCCESS;
			}

			for (Whim.Need need : this.whim.needs()) {
				if (WhimGoal.count(this.drifter, need.item()) >= need.count()) {
					continue;
				}

				Item item = makes(this.whim, need);
				if (item == null || ++this.steps > 10) {
					return this.fail("couldn't make what it needed for it");
				}

				Planner.Plan plan = Planner.plan(this.drifter, KnownWorld.scan(this.drifter), item, need.count());
				if (!plan.feasible() || !quick(plan) || plan.next() == null) {
					return this.fail("can't make a " + item.getDescription().getString().toLowerCase(java.util.Locale.ROOT) + " for it");
				}

				return this.begin(Goals.activityFor(this.drifter, plan.next()));
			}

			if (!this.action.possible(this.drifter, this.whim)) {
				return this.fail("not possible after all");
			}

			Activity activity = this.action.start(this.drifter, this.whim);
			if (activity == null) {
				return Status.SUCCESS;
			}

			this.started = true;
			return this.begin(activity);
		}

		@Override
		protected boolean countsAsFailure(Task step) {
			return true;
		}
	}
}
