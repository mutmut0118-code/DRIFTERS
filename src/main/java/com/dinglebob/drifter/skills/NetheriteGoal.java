package com.dinglebob.drifter.skills;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownWorld;
import com.dinglebob.drifter.brain.Planner;
import com.dinglebob.drifter.brain.Stations;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.brain.activity.CraftActivity;
import com.dinglebob.drifter.brain.activity.PlaceStationActivity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SmithTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Netherite at home (DESIGN.md section 19 "Netherite"): with a template and debris or scrap enough for an ingot, it makes
 * the ingot (the planner's usual chain: the debris smelted to scrap, gold, four and four at a crafting table), makes a
 * second template first if it has the diamonds and more to upgrade, gets a smithing table, and upgrades its gear in order
 * (the weapon, the pickaxe, the chestplate, then the rest).
 */
public final class NetheriteGoal extends Goals.Goal {
	@Override
	public String id() {
		return "netherite";
	}

	@Override
	public String name(Goals.Context context) {
		return "upgrade its gear to netherite";
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		if (context.threat().urgent() || drifter.inCombat() || com.dinglebob.drifter.world.Battle.inBattle(drifter) || drifter.level().dimension() != Level.OVERWORLD
			|| drifter.getDrifterBrain().failedRecently(this.id())) {
			return 0.0;
		}

		Home home = drifter.getDrifterBrain().home();
		if (home == null || !home.near(drifter, Stations.HOME_RANGE) || count(drifter, Netherite.TEMPLATE) == 0
			|| Netherite.scrapWorth(drifter) < 4 || Netherite.upgradeable(drifter).isEmpty()) {
			return 0.0;
		}

		return 0.5;
	}

	private static int count(DrifterEntity drifter, Item item) {
		return drifter.getInventory().count(stack -> stack.is(item));
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 30;
	}

	@Nullable
	@Override
	public Activity start(Goals.Context context) {
		return new Upgrade(context.drifter());
	}

	/** The ingot, a second template, the table, then the upgrade. */
	private static final class Upgrade extends Activity {
		/** Planned steps (smelting, crafting, fetching) it takes in one go before it lets other things have a turn. */
		private static final int MAX_STEPS = 10;
		private int steps;
		private boolean duplicated;
		private boolean placedTable;
		private boolean walked;
		private boolean smithed;

		Upgrade(DrifterEntity drifter) {
			super(drifter);
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "going to make netherite gear");
		}

		@Override
		public String describe() {
			return "upgrade its gear to netherite";
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60 * 6;
		}

		@Override
		protected void stepDone(Task step, Status status) {
			if (step instanceof SmithTask smith && status == Status.SUCCESS) {
				this.think("made " + smith.made() + " at the smithing table");
			}
		}

		@Override
		protected Status next() {
			if (this.smithed) {
				return Status.SUCCESS;
			}

			// The ingot: scrap from the debris, then four scrap and four gold.
			if (count(this.drifter, Items.NETHERITE_INGOT) == 0) {
				return this.planFor(Items.NETHERITE_INGOT, "a netherite ingot");
			}

			// A second template from the first (seven diamonds and a netherrack) while there's more to upgrade.
			if (!this.duplicated && count(this.drifter, Netherite.TEMPLATE) == 1 && Netherite.upgradeable(this.drifter).size() >= 2
				&& count(this.drifter, Items.DIAMOND) >= 7 && count(this.drifter, Items.NETHERRACK) > 0) {
				this.duplicated = true;
				return this.begin(new CraftActivity(this.drifter, Netherite.TEMPLATE, 1));
			}

			BlockPos table = Stations.usable(this.drifter, Blocks.SMITHING_TABLE);
			if (table == null) {
				if (count(this.drifter, Items.SMITHING_TABLE) > 0) {
					if (this.placedTable) {
						return this.fail("couldn't set up a smithing table");
					}

					this.placedTable = true;
					return this.begin(new PlaceStationActivity(this.drifter, Items.SMITHING_TABLE));
				}

				return this.planFor(Items.SMITHING_TABLE, "a smithing table");
			}

			if (!Reach.canReach(this.drifter, table)) {
				if (this.walked) {
					return this.fail("can't get to the smithing table");
				}

				this.walked = true;
				return this.begin(new NavigateTask(this.drifter, Vec3.atCenterOf(table), 2.5, "walk over to the smithing table"));
			}

			var upgradeable = Netherite.upgradeable(this.drifter);
			if (upgradeable.isEmpty()) {
				return Status.SUCCESS;
			}

			this.smithed = true;
			return this.begin(new SmithTask(this.drifter, table, upgradeable.get(0)));
		}

		/** The next step of the planner's way to get this (a furnace still busy: done for now, back later). */
		private Status planFor(Item item, String label) {
			if (++this.steps > MAX_STEPS) {
				return Status.SUCCESS;
			}

			Planner.Plan plan = Planner.plan(this.drifter, KnownWorld.scan(this.drifter), item, 1);
			Planner.Action next = plan.next();
			if (!plan.feasible() || next == null || next instanceof Planner.Explore) {
				return this.fail("can't make " + label + " yet");
			}

			if (next instanceof Planner.Collect collect && !collect.job().ready(this.drifter.level().getGameTime())) {
				this.think("waiting on the furnace for " + label);
				return Status.SUCCESS;
			}

			return this.begin(Goals.activityFor(this.drifter, next));
		}
	}
}
