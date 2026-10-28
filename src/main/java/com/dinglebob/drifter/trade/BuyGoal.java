package com.dinglebob.drifter.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.ShopKeeper;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.WaitTask;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Buying at a stall (milestone 22 step 4; the user, 2026-10-28: "What they're short of"): a Drifter short of something a
 * stall it knows sells (food when it's hungry with little on it, torches, iron when it has no iron pickaxe, wood for the
 * house it's building), and with the money for it, walks up to the keeper behind the counter and buys it, as a player at
 * the trading screen: its money into the keeper's pockets, the goods out of them into its own. Quicker than gathering it
 * when it has wages to spend; the takings go to the town's till.
 */
public final class BuyGoal extends Goals.Goal {
	/** How far it goes to a stall. */
	private static final double RANGE = 64.0;
	/** Close enough to the keeper to trade (across the counter). */
	private static final double COUNTER = 3.2;
	/** After a visit, not again for this long. */
	private static final long AGAIN = 20L * 60 * 4;
	/** Most bundles it buys of one thing a visit. */
	private static final int MOST = 3;

	private long visitedAt = Long.MIN_VALUE / 2;

	/** Something it's short of: what, and how many it wants. */
	private record Need(String what, Predicate<ItemStack> is, int want) {
	}

	/** A purchase: from whom, which offer, how many times. */
	private record Purchase(DrifterEntity keeper, ShopKeeper.ShopOffer offer, int times, Need need) {
	}

	@Override
	public String id() {
		return "buy";
	}

	@Override
	public String name(Goals.Context context) {
		return "buy something at the stall";
	}

	/** What it's short of now. */
	private static List<Need> needs(DrifterEntity drifter) {
		List<Need> needs = new ArrayList<>();
		var inventory = drifter.getInventory();
		Home home = drifter.getDrifterBrain().home();
		int food = inventory.count(stack -> stack.has(DataComponents.FOOD));
		if (food < 4 && drifter.getFoodData().getFoodLevel() <= 16) {
			needs.add(new Need("food", stack -> stack.has(DataComponents.FOOD), 8));
		}

		if (inventory.count(stack -> stack.is(Items.TORCH)) < 8 && (home == null || home.stored(Items.TORCH) < 8)) {
			needs.add(new Need("torches", stack -> stack.is(Items.TORCH), 16));
		}

		boolean ironPick = inventory.count(stack -> stack.is(Items.IRON_PICKAXE) || stack.is(Items.DIAMOND_PICKAXE) || stack.is(Items.NETHERITE_PICKAXE)) > 0;
		if (!ironPick && inventory.count(stack -> stack.is(Items.IRON_INGOT)) < 3) {
			needs.add(new Need("iron", stack -> stack.is(Items.IRON_INGOT), 3));
		}

		if (home != null && home.planId != null && !home.planDone && inventory.count(stack -> stack.is(ItemTags.LOGS)) < 16) {
			needs.add(new Need("wood", stack -> stack.is(ItemTags.LOGS), 16));
		}

		return needs;
	}

	/** The best purchase it can make now at an open stall it knows, or null. */
	@Nullable
	private static Purchase purchase(DrifterEntity drifter) {
		List<Need> needs = needs(drifter);
		if (needs.isEmpty()) {
			return null;
		}

		for (DrifterEntity keeper : drifter.level().getEntitiesOfClass(DrifterEntity.class, drifter.getBoundingBox().inflate(RANGE),
			other -> other != drifter && other.isAlive() && other.getShop() != null)) {
			ShopKeeper shop = keeper.getShop();
			var stand = shop.stand(keeper);
			// Open, at its stall, and one it knows of (seen the keeper, seen the stall's spot, or its own town's).
			var seen = drifter.getSenses().awarenessOf(keeper);
			boolean knows = seen != null && seen.identified || stand != null && drifter.getSenses().terrain().isKnown(stand.below())
				|| com.dinglebob.drifter.world.DrifterGroups.of(drifter.getUUID()) != null
				&& com.dinglebob.drifter.world.DrifterGroups.of(drifter.getUUID()) == com.dinglebob.drifter.world.DrifterGroups.of(keeper.getUUID());
			if (stand == null || !StallGoal.open(keeper) || keeper.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(stand)) > 1.5 || !knows) {
				continue;
			}

			for (Need need : needs) {
				for (ShopKeeper.ShopOffer offer : shop.offers(keeper, null)) {
					if (!need.is().test(offer.goods()) || !(offer.pay().is(Items.EMERALD) || Money.isCoin(offer.pay())) || offer.times() <= 0) {
						continue;
					}

					int afford = drifter.getInventory().count(stack -> stack.is(offer.pay().getItem())) / Math.max(1, offer.pay().getCount());
					if (afford <= 0 && Money.isCoin(offer.pay())) {
						Money.makeChange(drifter, offer.pay().getItem(), offer.pay().getCount());
						afford = drifter.getInventory().count(stack -> stack.is(offer.pay().getItem())) / Math.max(1, offer.pay().getCount());
					}

					int wanted = (int)Math.ceil(need.want() / (double)Math.max(1, offer.goods().getCount()));
					int times = Math.min(Math.min(afford, offer.times()), Math.min(wanted, MOST));
					if (times > 0) {
						return new Purchase(keeper, offer, times, need);
					}
				}
			}
		}

		return null;
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		if (drifter.getShop() != null || Schedule.isNight(drifter) || context.threat().urgent() || drifter.level().getGameTime() - this.visitedAt < AGAIN) {
			return 0.0;
		}

		// (Hungry with little food: buying it beats a hunt.)
		Purchase purchase = purchase(drifter);
		return purchase == null ? 0.0 : purchase.need().what().equals("food") && drifter.getFoodData().getFoodLevel() <= 10 ? 0.62 : 0.4;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 15;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		Purchase purchase = purchase(drifter);
		if (purchase == null) {
			return null;
		}

		this.visitedAt = drifter.level().getGameTime();
		DrifterEntity keeper = purchase.keeper();
		String name = keeper.getName().getString();
		return new Activity(drifter) {
			private boolean asked;
			private boolean done;

			@Override
			public String describe() {
				return "buy " + purchase.need().what() + " from " + name;
			}

			@Override
			protected int timeoutTicks() {
				return 20 * 90;
			}

			@Override
			protected Status next() {
				if (this.done || !keeper.isAlive() || keeper.getShop() == null) {
					return Status.SUCCESS;
				}

				if (this.drifter.distanceTo(keeper) > COUNTER) {
					return this.begin(new NavigateTask(this.drifter, keeper.position(), COUNTER - 0.8, "walk up to " + name + "'s stall", NavigateTask.Pace.STROLL));
				}

				// Up to the counter: a word, a moment (as a player looks over the trades), then the trade.
				if (!this.asked) {
					this.asked = true;
					com.dinglebob.drifter.social.ChatLines.speak(this.drifter, com.dinglebob.drifter.social.Lines.pick(this.drifter, "shop.buy", "who", name,
						"what", purchase.offer().goods().getHoverName().getString().toLowerCase(java.util.Locale.ROOT)));
					return this.begin(new LookAtTask(this.drifter, keeper.getEyePosition(), 30));
				}

				this.done = true;
				int bought = 0;
				for (int i = 0; i < purchase.times(); i++) {
					ItemStack pay = purchase.offer().pay();
					ItemStack goods = purchase.offer().goods();
					if (this.drifter.getInventory().count(stack -> stack.is(pay.getItem())) < pay.getCount()
						|| keeper.getInventory().count(stack -> ItemStack.isSameItemSameComponents(stack, goods)) < goods.getCount()) {
						break;
					}

					DrifterMerchant.take(this.drifter.getInventory(), pay);
					DrifterMerchant.give(keeper, pay.copy());
					DrifterMerchant.take(keeper.getInventory(), goods);
					DrifterMerchant.give(this.drifter, goods.copy());
					bought++;
				}

				if (bought > 0) {
					ItemStack goods = purchase.offer().goods();
					ItemStack pay = purchase.offer().pay();
					String what = goods.getCount() * bought + " " + goods.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
					String price = pay.getCount() * bought + " " + pay.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
					this.think("bought " + what + " from " + name + " for " + price);
					keeper.getMemory().thoughts().add(keeper.level().getGameTime(), "sold " + what + " to " + this.drifter.getName().getString() + " for " + price);
					keeper.getShop().soldTo(keeper, this.drifter, purchase.offer(), bought);
					com.dinglebob.drifter.social.ChatLines.speak(keeper, com.dinglebob.drifter.social.Lines.pick(keeper, "shop.sold", "who",
						this.drifter.getName().getString()));
				}

				return this.begin(new WaitTask(this.drifter, "pocket it", () -> false, 10));
			}
		};
	}
}
