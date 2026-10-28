package com.dinglebob.drifter.trade;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.dinglebob.drifter.api.ShopKeeper;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.brain.activity.FetchActivity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.WaitTask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/**
 * Keeping a shop (milestone 22 step 4; a Drifter with a {@link ShopKeeper}, set by Drifter: Civilizations): in its open
 * hours (morning to mid-afternoon, by its own sense of the time), it takes what it means to sell from its home's chests
 * (the town's store), goes to its stall and stays there, for whoever comes by to trade (right-click: the trading
 * screen). The rest of the day it goes about its life.
 */
public final class StallGoal extends Goals.Goal {
	/** Open from this time of day to that (game ticks: 1000 is early morning, 9000 mid-afternoon). */
	private static final long OPENS = 1000L;
	private static final long CLOSES = 9000L;
	/** Farthest from its stall it goes to keep it. */
	private static final double RANGE = 128.0;

	public static boolean open(DrifterEntity drifter) {
		long time = Schedule.believedTime(drifter) % 24000L;
		return time >= OPENS && time < CLOSES;
	}

	@Override
	public String id() {
		return "stall";
	}

	@Override
	public String name(Goals.Context context) {
		return "keep its stall";
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		ShopKeeper shop = drifter.getShop();
		BlockPos stand = shop == null ? null : shop.stand(drifter);
		// (Its job in its open hours: above its own chores, below staying alive and eating.)
		return stand != null && open(drifter) && drifter.blockPosition().closerThan(stand, RANGE) ? 0.72 : 0.0;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 60;
	}

	@Override
	public Activity start(Goals.Context context) {
		return new Activity(context.drifter()) {
			private final Set<Item> fetched = new HashSet<>();

			@Override
			public String describe() {
				return "keep its stall";
			}

			@Override
			protected int timeoutTicks() {
				return 20 * 60 * 10;
			}

			@Override
			protected boolean countsAsFailure(com.dinglebob.drifter.executor.Task step) {
				return false;
			}

			@Override
			protected Status next() {
				ShopKeeper shop = this.drifter.getShop();
				BlockPos stand = shop == null ? null : shop.stand(this.drifter);
				if (stand == null || !open(this.drifter)) {
					return Status.SUCCESS;
				}

				// Stocked up first: what it means to sell and is short of, from its home's chests.
				Home home = this.drifter.getDrifterBrain().home();
				if (home != null) {
					for (Map.Entry<Item, Integer> wanted : shop.stock(this.drifter).entrySet()) {
						Item item = wanted.getKey();
						int have = this.drifter.getInventory().count(stack -> stack.is(item));
						BlockPos chest = home.chestWith(item);
						if (have * 2 < wanted.getValue() && chest != null && this.fetched.add(item)) {
							return this.begin(new FetchActivity(this.drifter, chest, item, wanted.getValue() - have));
						}
					}
				}

				// At its stall: there till closing, for whoever comes by.
				if (this.drifter.position().distanceTo(Vec3.atBottomCenterOf(stand)) > 1.2) {
					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(stand), 0.6, "go to its stall"));
				}

				return this.begin(new WaitTask(this.drifter, "keep its stall", () -> !open(this.drifter), 20 * 30));
			}
		};
	}
}
