package com.dinglebob.drifter.skills;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.EnchantTask;
import com.dinglebob.drifter.executor.task.NavigateTask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * A town's enchanter making enchanted books to sell (milestone 23 B6: what villagers gave, and where it's had now:
 * "Mending, enchanted books: enchanters' books (enchanting table rolls)"): with a book, lapis and a level or more, at an
 * enchanting table it knows, it enchants the book (the table's own roll, its own levels and lapis), till it has a few put
 * by. Its shop sells them.
 */
public final class EnchantBooksGoal extends Goals.Goal {
	/** It keeps this many enchanted books about (carried and at home) at most. */
	private static final int KEEPS = 4;
	/** After a try that didn't come off (out of levels or lapis, the table out of reach), it leaves it this long. */
	private static final long REST = 20L * 60 * 5;
	private long restUntil;

	@Override
	public String id() {
		return "enchant_books";
	}

	@Override
	public String name(Goals.Context context) {
		return "enchant books to sell";
	}

	@Nullable
	private static BlockPos table(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		if (home != null && home.enchantingTable(drifter.level()) != null) {
			return home.enchantingTable(drifter.level());
		}

		var found = KnownBlocks.find(drifter, 64, state -> state.is(Blocks.ENCHANTING_TABLE), 1);
		return found.isEmpty() ? null : found.get(0);
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		TownWork town = drifter.getTownWork();
		if (town == null || !town.enchantsBooks(drifter) || Schedule.isNight(drifter) || context.threat().urgent() || drifter.experienceLevel() < 1
			|| drifter.getInventory().count(stack -> stack.is(Items.BOOK)) == 0 || drifter.getInventory().count(stack -> stack.is(Items.LAPIS_LAZULI)) == 0) {
			return 0.0;
		}

		if (drifter.level().getGameTime() < this.restUntil) {
			return 0.0;
		}

		Home home = drifter.getDrifterBrain().home();
		int have = drifter.getInventory().count(stack -> stack.is(Items.ENCHANTED_BOOK)) + (home == null ? 0 : home.stored(Items.ENCHANTED_BOOK));
		return have >= KEEPS || table(drifter) == null ? 0.0 : 0.52;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 20;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		BlockPos table = table(drifter);
		if (table == null) {
			return null;
		}

		int before = drifter.getInventory().count(stack -> stack.is(Items.ENCHANTED_BOOK));
		EnchantBooksGoal goal = this;
		return new Activity(drifter) {
			private int step;

			@Override
			public String describe() {
				return "enchant books to sell";
			}

			@Override
			protected void onStop() {
				// (No new book out of it: a rest before the next try.)
				if (this.drifter.getInventory().count(stack -> stack.is(Items.ENCHANTED_BOOK)) <= before) {
					goal.restUntil = this.drifter.level().getGameTime() + REST;
				}
			}

			@Override
			protected int timeoutTicks() {
				return 20 * 60;
			}

			@Override
			protected Status next() {
				return switch (this.step++) {
					case 0 -> {
						if (com.dinglebob.drifter.hands.Reach.canReach(this.drifter, table)) {
							yield Status.RUNNING;
						}

						BlockPos stand = KnownBlocks.standingSpotFor(this.drifter, table, java.util.Set.of());
						yield stand == null ? Status.FAILURE : this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(stand), 0.6, "go to the enchanting table"));
					}
					case 1 -> this.begin(new EnchantTask(this.drifter, table, stack -> stack.is(Items.BOOK), "enchant a book"));
					default -> Status.SUCCESS;
				};
			}
		};
	}
}
