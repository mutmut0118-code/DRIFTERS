package com.dinglebob.drifter.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.ShopKeeper;
import com.dinglebob.drifter.api.TradeRun;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.brain.activity.FetchActivity;
import com.dinglebob.drifter.brain.activity.StoreActivity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.WaitTask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A trade run (milestone 22 step 4; the user, 2026-10-28: "A trader walks"; {@link TradeRun}, set by Drifter:
 * Civilizations): it packs what its town has plenty of from the store, and a little of the town's money from the till, walks
 * the road to the other town's stall, sells there what that town's keeper buys, buys what its own town is short of, walks
 * home, and puts it all away (the money in the till). Bandits on the road are bandits on the road. A fight or night on the
 * way and it deals with that first, then goes on.
 */
public final class TradeRunGoal extends Goals.Goal {
	/** Close enough to a stall to trade across its counter. */
	private static final double COUNTER = 3.2;
	/** Money it takes from the till for buying (emeralds). */
	private static final int PURSE = 6;
	/** How long it waits at the other stall for its keeper (ticks). */
	private static final long WAITS = 20L * 60 * 2;
	/** Most bundles of one thing it buys there. */
	private static final int MOST = 4;

	@Override
	public String id() {
		return "trade_run";
	}

	@Override
	public String name(Goals.Context context) {
		TradeRun run = context.drifter().getTradeRun();
		return run == null ? "go trading" : "go trading in " + run.thereName();
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		// (Its errand for the town: above its own chores, below staying alive, eating and sleeping.)
		return drifter.getTradeRun() == null || context.threat().urgent() || Schedule.isNight(drifter) ? 0.0 : 0.7;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 30;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		TradeRun run = drifter.getTradeRun();
		return run == null ? null : new Run(drifter, run);
	}

	/** The trip, phase by phase (it picks up where it was after anything that came first). */
	private static final class Run extends Activity {
		private final TradeRun run;
		private final List<Item> packed = new ArrayList<>();
		private boolean purse;
		private boolean setOut;
		private boolean outbound = true;
		private boolean traded;
		private boolean stored;
		private boolean banked;
		private long waitingSince = -1;
		private int walks;

		Run(DrifterEntity drifter, TradeRun run) {
			super(drifter);
			this.run = run;
		}

		@Override
		public String describe() {
			return this.outbound ? "take goods to " + this.run.thereName() : "bring goods home from " + this.run.thereName();
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60 * 15;
		}

		@Override
		protected boolean countsAsFailure(Task step) {
			return false;
		}

		@Override
		protected Status next() {
			TradeRun state = this.drifter.getTradeRun();
			if (state != this.run) {
				return Status.SUCCESS;
			}

			Home home = this.drifter.getDrifterBrain().home();
			// 1. Packing: the cargo from the store, a purse from the till.
			if (this.outbound && !this.traded) {
				for (Map.Entry<Item, Integer> load : this.run.cargo().entrySet()) {
					Item item = load.getKey();
					int have = this.drifter.getInventory().count(stack -> stack.is(item));
					BlockPos chest = home == null ? null : home.chestWith(item);
					if (have < load.getValue() && chest != null && !this.packed.contains(item)) {
						this.packed.add(item);
						return this.begin(new FetchActivity(this.drifter, chest, item, load.getValue() - have));
					}
				}

				if (!this.purse && this.run.till() != null) {
					this.purse = true;
					return this.begin(new ChestErrand(this.drifter, this.run.till(), ContainerTask.Mode.TAKE, stack -> stack.is(Items.EMERALD) || Money.isCoin(stack),
						"the town's purse", PURSE));
				}

				if (!this.setOut) {
					this.setOut = true;
					this.run.news(this.drifter, "set out");
					this.think("setting out to trade in " + this.run.thereName());
				}

				// 2. There.
				if (this.drifter.position().distanceTo(Vec3.atBottomCenterOf(this.run.there())) > 16.0) {
					if (this.walks++ > 30) {
						return this.giveUp("couldn't find the way to " + this.run.thereName());
					}

					this.think("on the road to " + this.run.thereName() + ", " + (int)this.drifter.position().distanceTo(Vec3.atBottomCenterOf(this.run.there()))
						+ " blocks to go");

					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.run.there()), 8.0, "walk to " + this.run.thereName()));
				}

				// 3. Trading at the stall, when its keeper's there (a while, then home with what it has).
				DrifterEntity keeper = this.keeper();
				if (keeper == null) {
					long now = this.now();
					if (this.waitingSince < 0) {
						this.waitingSince = now;
					}

					if (now - this.waitingSince > WAITS) {
						this.run.news(this.drifter, "found nobody at the stall in " + this.run.thereName());
						this.traded = true;
						this.outbound = false;
						this.walks = 0;
						return Status.RUNNING;
					}

					return this.begin(new WaitTask(this.drifter, "wait for the stall to open", () -> this.keeper() != null, 20 * 10));
				}

				if (this.drifter.distanceTo(keeper) > COUNTER) {
					return this.begin(new NavigateTask(this.drifter, keeper.position(), COUNTER - 0.8, "walk up to the stall in " + this.run.thereName()));
				}

				this.trade(keeper);
				this.traded = true;
				this.outbound = false;
				this.walks = 0;
				return this.begin(new LookAtTask(this.drifter, keeper.getEyePosition(), 20));
			}

			// 4. Home.
			if (this.drifter.position().distanceTo(Vec3.atBottomCenterOf(this.run.home())) > 24.0) {
				if (this.walks++ > 30) {
					return this.giveUp("couldn't find the way home");
				}

				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.run.home()), 12.0, "walk home"));
			}

			// 5. The money in the till, the goods put away.
			if (!this.banked && this.run.till() != null) {
				this.banked = true;
				int money = this.drifter.getInventory().count(stack -> stack.is(Items.EMERALD) || Money.isCoin(stack));
				if (money > 0) {
					return this.begin(new ChestErrand(this.drifter, this.run.till(), ContainerTask.Mode.PUT, stack -> stack.is(Items.EMERALD) || Money.isCoin(stack),
						"the trip's money", money));
				}
			}

			if (!this.stored) {
				this.stored = true;
				return this.begin(new StoreActivity(this.drifter));
			}

			this.run.news(this.drifter, "back home");
			this.think("back from trading in " + this.run.thereName());
			this.drifter.setTradeRun(null);
			return Status.SUCCESS;
		}

		private Status giveUp(String why) {
			this.run.news(this.drifter, "gave up: " + why);
			this.drifter.setTradeRun(null);
			return this.fail(why);
		}

		/** The other town's keeper, at its stall and open, or null. */
		@Nullable
		private DrifterEntity keeper() {
			for (DrifterEntity other : this.drifter.level().getEntitiesOfClass(DrifterEntity.class, this.drifter.getBoundingBox().inflate(24.0),
				one -> one != this.drifter && one.isAlive() && one.getShop() != null)) {
				BlockPos stand = other.getShop().stand(other);
				if (stand != null && stand.closerThan(this.run.there(), 4.0) && StallGoal.open(other)
					&& other.position().distanceTo(Vec3.atBottomCenterOf(stand)) <= 1.5) {
					return other;
				}
			}

			return null;
		}

		/** Sells what the keeper buys, then buys what home is short of. */
		private void trade(DrifterEntity keeper) {
			ShopKeeper shop = keeper.getShop();
			// (What this stall buys and sells, seen with its own eyes: for its town's next run.)
			java.util.Set<Item> theyBuy = new java.util.HashSet<>();
			java.util.Set<Item> theySell = new java.util.HashSet<>();
			for (ShopKeeper.ShopOffer offer : shop.offers(keeper, null)) {
				if (offer.goods().is(Items.EMERALD) || Money.isCoin(offer.goods())) {
					theyBuy.add(offer.pay().getItem());
				} else {
					theySell.add(offer.goods().getItem());
				}
			}

			this.run.saw(this.drifter, theyBuy, theySell);
			List<String> sold = new ArrayList<>();
			List<String> bought = new ArrayList<>();
			// Selling: the keeper's offers that give money for goods it carries.
			for (ShopKeeper.ShopOffer offer : shop.offers(keeper, null)) {
				boolean buysGoods = offer.goods().is(Items.EMERALD) || Money.isCoin(offer.goods());
				if (buysGoods && this.run.cargo().containsKey(offer.pay().getItem())) {
					int made = Counter.trade(this.drifter, keeper, offer, offer.times());
					if (made > 0) {
						sold.add(Counter.describe(offer.pay(), made));
					}
				}
			}

			// Buying: what home is short of, with the money it has.
			for (ShopKeeper.ShopOffer offer : shop.offers(keeper, null)) {
				boolean sells = offer.pay().is(Items.EMERALD) || Money.isCoin(offer.pay());
				if (sells && this.run.wanted().contains(offer.goods().getItem())) {
					int made = Counter.trade(this.drifter, keeper, offer, Math.min(MOST, offer.times()));
					if (made > 0) {
						bought.add(Counter.describe(offer.goods(), made));
					}
				}
			}

			String said = (sold.isEmpty() ? "sold nothing" : "sold " + String.join(", ", sold)) + (bought.isEmpty() ? "" : "; bought " + String.join(", ", bought));
			this.think("traded in " + this.run.thereName() + " with " + keeper.getName().getString() + ": " + said);
			keeper.getMemory().thoughts().add(keeper.level().getGameTime(), "traded with " + this.drifter.getName().getString() + ", come from afar: " + said);
			this.run.news(this.drifter, "traded: " + said);
			com.dinglebob.drifter.social.ChatLines.speak(this.drifter, com.dinglebob.drifter.social.Lines.pick(this.drifter, "shop.trader", "who",
				keeper.getName().getString(), "town", this.run.thereName()));
		}
	}
}
