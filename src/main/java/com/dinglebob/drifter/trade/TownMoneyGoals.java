package com.dinglebob.drifter.trade;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.ShopKeeper;
import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.ContainerTask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A town's money (milestone 22 step 4; the user, 2026-10-28: "Town treasury"): the stall keeper puts its takings in the
 * town's till (a chest in its store), keeping some on hand to buy with; the town's people take the wages the town owes them
 * for its work out of the till, as far as there's money in it. Money goes round: wages, buying at the stall, the till,
 * wages. None is ever made: only what's in the till is paid.
 */
public final class TownMoneyGoals {
	private TownMoneyGoals() {
	}

	/** Each money item and its worth, in points (an emerald is 6). */
	private static boolean money(ItemStack stack) {
		return stack.is(Items.EMERALD) || Money.isCoin(stack);
	}

	private static double carried(DrifterEntity drifter) {
		double points = 0.0;
		for (int slot = 0; slot < drifter.getInventory().getContainerSize(); slot++) {
			ItemStack stack = drifter.getInventory().getItem(slot);
			if (money(stack)) {
				points += Money.worth(stack);
			}
		}

		return points;
	}

	/** Its wages: when the town owes it a piece of money's worth or more, it goes to the till and takes what it's owed. */
	public static final class Wages extends Goals.Goal {
		/** It goes for its wages from this much owed (points: an emerald). */
		private static final double ENOUGH = 6.0;
		/** After a trip, not again for this long (an empty till: it'll be a while). */
		private static final long AGAIN = 20L * 60 * 5;
		private long triedAt = Long.MIN_VALUE / 2;

		@Override
		public String id() {
			return "wages";
		}

		@Override
		public String name(Goals.Context context) {
			return "collect its wages";
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			TownWork town = drifter.getTownWork();
			BlockPos till = town == null ? null : town.till();
			if (till == null || Schedule.isNight(drifter) || context.threat().urgent() || drifter.level().getGameTime() - this.triedAt < AGAIN
				|| town.owed(drifter) < ENOUGH || !drifter.blockPosition().closerThan(till, 96.0)) {
				return 0.0;
			}

			return 0.42;
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
			TownWork town = drifter.getTownWork();
			BlockPos till = town == null ? null : town.till();
			if (till == null) {
				return null;
			}

			this.triedAt = drifter.level().getGameTime();
			double owed = town.owed(drifter);
			ItemStack wage = Money.priceStack(owed);
			// (No more than it's owed: the stack worth about that, rounded down.)
			int count = Math.max(1, (int)Math.floor(owed / Math.max(0.01, Money.worth(wage.copyWithCount(1)))));
			double before = carried(drifter);
			return new ChestErrand(drifter, till, ContainerTask.Mode.TAKE, stack -> stack.is(wage.getItem()), "its wages", count) {
				@Override
				protected Status next() {
					Status status = super.next();
					if (status != Status.RUNNING) {
						double got = carried(this.drifter) - before;
						if (got > 0.0) {
							town.paid(this.drifter, got);
							this.think("took " + Math.round(got) + " points of wages from the town's till");
						} else {
							this.think("the town's till had nothing for its wages");
						}
					}

					return status;
				}
			};
		}
	}

	/** Its tax (step 5, a leader's law): it takes what it owes the town to the till, as far as it has the money. */
	public static final class Tax extends Goals.Goal {
		private static final long AGAIN = 20L * 60 * 4;
		private long triedAt = Long.MIN_VALUE / 2;

		@Override
		public String id() {
			return "tax";
		}

		@Override
		public String name(Goals.Context context) {
			return "pay its tax";
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			TownWork town = drifter.getTownWork();
			BlockPos till = town == null ? null : town.till();
			if (till == null || Schedule.isNight(drifter) || context.threat().urgent() || drifter.level().getGameTime() - this.triedAt < AGAIN
				|| town.taxOwed(drifter) <= 0 || drifter.getInventory().count(stack -> stack.is(Items.EMERALD)) == 0 || !drifter.blockPosition().closerThan(till, 96.0)) {
				return 0.0;
			}

			// (A chore it puts off, the greedy more; the more it owes, the less it can.)
			return Math.min(0.7, 0.4 + 0.08 * town.taxOwed(drifter) - 0.1 * drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.GREED));
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
			TownWork town = drifter.getTownWork();
			BlockPos till = town == null ? null : town.till();
			if (till == null) {
				return null;
			}

			this.triedAt = drifter.level().getGameTime();
			int owed = town.taxOwed(drifter);
			int pays = Math.min(owed, drifter.getInventory().count(stack -> stack.is(Items.EMERALD)));
			int before = drifter.getInventory().count(stack -> stack.is(Items.EMERALD));
			return new ChestErrand(drifter, till, ContainerTask.Mode.PUT, stack -> stack.is(Items.EMERALD), "its tax", pays) {
				@Override
				protected Status next() {
					Status status = super.next();
					if (status != Status.RUNNING) {
						int paid = before - this.drifter.getInventory().count(stack -> stack.is(Items.EMERALD));
						if (paid > 0) {
							town.taxPaid(this.drifter, paid);
							this.think("paid " + paid + " emerald" + (paid == 1 ? "" : "s") + " of tax into the town's till");
						}
					}

					return status;
				}
			};
		}
	}

	/** The keeper banks its takings: what it has above what it keeps on hand goes into the till, after closing. */
	public static final class Bank extends Goals.Goal {
		/** It banks when it has this much more than it keeps on hand (points: two emeralds). */
		private static final double SPARE = 12.0;

		@Override
		public String id() {
			return "bank";
		}

		@Override
		public String name(Goals.Context context) {
			return "put the stall's takings in the till";
		}

		@Nullable
		private static BlockPos till(DrifterEntity drifter) {
			ShopKeeper shop = drifter.getShop();
			return shop == null ? null : shop.till(drifter);
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			ShopKeeper shop = drifter.getShop();
			BlockPos till = till(drifter);
			if (till == null || StallGoal.open(drifter) || Schedule.isNight(drifter) || context.threat().urgent()
				|| !drifter.blockPosition().closerThan(till, 96.0)) {
				return 0.0;
			}

			return carried(drifter) - shop.keepOnHand() * 6.0 > SPARE ? 0.5 : 0.0;
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
			ShopKeeper shop = drifter.getShop();
			BlockPos till = till(drifter);
			if (shop == null || till == null) {
				return null;
			}

			// The most common money it carries, all but what it keeps on hand.
			ItemStack kind = ItemStack.EMPTY;
			int most = 0;
			for (int slot = 0; slot < drifter.getInventory().getContainerSize(); slot++) {
				ItemStack stack = drifter.getInventory().getItem(slot);
				if (money(stack)) {
					int count = drifter.getInventory().count(other -> other.is(stack.getItem()));
					if (count > most) {
						most = count;
						kind = stack;
					}
				}
			}

			if (kind.isEmpty()) {
				return null;
			}

			double each = Money.worth(kind.copyWithCount(1));
			int keep = (int)Math.ceil(shop.keepOnHand() * 6.0 / Math.max(0.01, each));
			int put = most - Math.max(0, keep - (int)((carried(drifter) - most * each) / Math.max(0.01, each)));
			if (put <= 0) {
				return null;
			}

			ItemStack banked = kind.copy();
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "taking the stall's takings to the till: " + put + " "
				+ banked.getHoverName().getString().toLowerCase(java.util.Locale.ROOT));
			return new ChestErrand(drifter, till, ContainerTask.Mode.PUT, stack -> stack.is(banked.getItem()), "the stall's takings", put);
		}
	}

	/** (For tests and the overlay.) Its money, in points. */
	public static double moneyOf(DrifterEntity drifter) {
		return carried(drifter);
	}
}
