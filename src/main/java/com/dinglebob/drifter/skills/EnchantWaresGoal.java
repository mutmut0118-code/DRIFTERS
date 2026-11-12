package com.dinglebob.drifter.skills;

import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.EnchantTask;
import com.dinglebob.drifter.executor.task.NavigateTask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * A trade's piece enchanted to sell dearer (the user, 2026-11-12: what a shop sells is better than what falls off a body):
 * its town says which of what it carries ({@link TownWork#wareToEnchant}: a smith's piece, now and then); with lapis and
 * levels to spare, at an enchanting table it knows of (its own, or one it has seen: the town's enchanter's), it enchants it
 * as a player would: the table's own roll, its own levels and lapis. Nothing from nowhere.
 */
public final class EnchantWaresGoal extends Goals.Goal {
	/** It keeps this many levels for its other work (a repair takes them). */
	private static final int KEEPS_LEVELS = 3;
	/** Under keeping its stall and a job taken, over making more to sell: its town says when (one every couple of days). */
	private static final double URGENCY = 0.72;

	@Override
	public String id() {
		return "enchant_wares";
	}

	@Override
	public String name(Goals.Context context) {
		return "enchant something to sell";
	}

	@Nullable
	private static BlockPos table(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		if (home != null && home.enchantingTable(drifter.level()) != null) {
			return home.enchantingTable(drifter.level());
		}

		var found = KnownBlocks.find(drifter, 128, state -> state.is(Blocks.ENCHANTING_TABLE), 1);
		return found.isEmpty() ? null : found.get(0);
	}

	@Nullable
	private static Predicate<ItemStack> ware(DrifterEntity drifter) {
		TownWork town = drifter.getTownWork();
		return town == null ? null : town.wareToEnchant(drifter);
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		if (drifter.getTownWork() == null || Schedule.isNight(drifter) || context.threat().urgent() || drifter.experienceLevel() <= KEEPS_LEVELS
			|| drifter.getInventory().count(stack -> stack.is(Items.LAPIS_LAZULI)) == 0) {
			return 0.0;
		}

		Predicate<ItemStack> ware = ware(drifter);
		return ware == null || drifter.getInventory().count(ware) == 0 || table(drifter) == null ? 0.0 : URGENCY;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		// (The table may be across the town: long enough to get there. Found in testing: it turned back halfway.)
		return 20 * 90;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		BlockPos table = table(drifter);
		Predicate<ItemStack> ware = ware(drifter);
		TownWork town = drifter.getTownWork();
		if (table == null || ware == null || town == null) {
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), table == null ? "knows no enchanting table near to enchant something to sell at"
				: "has nothing to enchant to sell just now");
			return null;
		}

		return new Activity(drifter) {
			private int step;
			private boolean done;
			private boolean told;

			@Override
			public String describe() {
				return "enchant something to sell";
			}

			@Override
			protected void stepDone(Task task, Status status) {
				if (task instanceof EnchantTask enchant && status == Status.SUCCESS) {
					this.done = true;
					this.think(enchant.result());
				}
			}

			@Override
			protected void onStop() {
				super.onStop();
				if (!this.told) {
					this.told = true;
					town.wareEnchanted(this.drifter, this.done);
				}
			}

			@Override
			protected int timeoutTicks() {
				return 20 * 60 * 3;
			}

			@Override
			protected Status next() {
				return switch (this.step++) {
					case 0 -> {
						if (com.dinglebob.drifter.hands.Reach.canReach(this.drifter, table)) {
							yield Status.RUNNING;
						}

						BlockPos stand = KnownBlocks.standingSpotFor(this.drifter, table, java.util.Set.of());
						// (A table it knows of but has never stood at, the town's enchanter's: up to it, as near as it gets.)
						yield this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(stand != null ? stand : table), stand != null ? 0.6 : 1.6,
							"go to the enchanting table"));
					}
					case 1 -> this.begin(new EnchantTask(this.drifter, table, stack -> ware.test(stack) && !stack.isEnchanted(), "something to sell"));
					default -> Status.SUCCESS;
				};
			}
		};
	}
}
