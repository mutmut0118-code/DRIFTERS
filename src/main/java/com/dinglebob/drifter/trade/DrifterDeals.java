package com.dinglebob.drifter.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Storage;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.Steering;
import com.dinglebob.drifter.personality.Trait;
import com.dinglebob.drifter.social.ChatLines;
import com.dinglebob.drifter.social.DrifterBonds;
import com.dinglebob.drifter.social.ItemValues;
import com.dinglebob.drifter.social.Relationship;
import com.dinglebob.drifter.social.SocialWatcher;
import com.dinglebob.drifter.world.Battle;
import com.dinglebob.drifter.world.DrifterGroups;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Drifters trading with each other (milestone 22 groundwork, docs/CIVILIZATION.md: money and markets need it). Two
 * Drifters that meet and don't dislike each other: now and then one asks the other for something it needs that the other
 * can spare (they talk, as players would in chat: "got any iron to spare?"), and offers something of about the same worth
 * from what it can spare that the other wants, or emeralds. A greedy one asks more, friends less; a friend gives a little
 * thing for nothing (and is owed for it, which comes off next time). Agreed, both stop what they're doing, walk up to
 * each other and toss their side over (the real items, thrown as a player throws them, for that one only), and each picks
 * up what it was given.
 */
public final class DrifterDeals {
	private DrifterDeals() {
	}

	/** It asks the same Drifter again after this long (five minutes). */
	private static final long ASK_AGAIN = 20L * 60 * 5;
	/** It keeps its things from a Drifter it trusts this little (as with news). */
	private static final double DISLIKES = -15.0;
	/** Wanted less than this, it doesn't ask. */
	private static final double MIN_WANT = 0.25;
	/** They get together within this long of agreeing, or it's off. */
	private static final long MEET_WITHIN = 20L * 60;
	/** The swap takes at most this long once they're together. */
	private static final long SWAP_WITHIN = 20L * 30;
	/** Close enough to toss things over. */
	private static final double TOSS_RANGE = 2.8;
	/** A friend (or group mate) gives what's worth this little for nothing. */
	private static final double GIFT_UP_TO = 16.0;
	/** A friend's (or group mate's) price. */
	private static final double FRIEND_PRICE = 0.75;
	/** Paid in goods worth up to this much over the price (things don't come in fractions). */
	private static final double OVERPAY = 1.6;
	private static final double EMERALD_WORTH = 6.0;
	/** Trust a fair trade earns both ways, and a gift the giver. */
	private static final double FAIR_TRUST = 3.0;
	private static final double GIFT_TRUST = 6.0;

	// What it wants for its own needs (besides better gear and food: Trading.want).
	private static final double WANT_IRON = 0.4;
	private static final double WANT_TORCHES = 0.35;
	private static final double WANT_COAL = 0.3;
	private static final double WANT_WOOD = 0.25;

	/** Deals agreed and not done yet, by each of the two. */
	private static final Map<UUID, Deal> DEALS = new HashMap<>();

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DEALS.clear());
	}

	/** Its deal under way, or null. */
	@Nullable
	public static Deal of(DrifterEntity drifter) {
		return DEALS.get(drifter.getUUID());
	}

	/** One trade between two Drifters: {@code asker} gives {@code pay} (nothing: a gift) for {@code goods}. */
	public static final class Deal {
		public final DrifterEntity asker;
		public final DrifterEntity giver;
		public final ItemStack pay;
		public final ItemStack goods;
		/** The giver owed the asker this much (a gift before): it's this, or part of it, paid back. */
		final int owed;
		final long made;
		long goAt = -1;
		boolean askerReady;
		boolean giverReady;
		boolean askerTossed;
		boolean giverTossed;
		boolean askerGot;
		boolean giverGot;
		boolean closed;
		boolean completed;
		final List<ItemEntity> fromAsker = new ArrayList<>();
		final List<ItemEntity> fromGiver = new ArrayList<>();

		Deal(DrifterEntity asker, DrifterEntity giver, ItemStack pay, ItemStack goods, int owed, long made) {
			this.asker = asker;
			this.giver = giver;
			this.pay = pay;
			this.goods = goods;
			this.owed = owed;
			this.made = made;
		}

		DrifterEntity partner(DrifterEntity me) {
			return me == this.asker ? this.giver : this.asker;
		}

		ItemStack gives(DrifterEntity me) {
			return me == this.asker ? this.pay : this.goods;
		}

		List<ItemEntity> tossedBy(DrifterEntity me) {
			return me == this.asker ? this.fromAsker : this.fromGiver;
		}

		boolean tossed(DrifterEntity me) {
			return me == this.asker ? this.askerTossed : this.giverTossed;
		}

		boolean gift() {
			return this.pay.isEmpty();
		}

		/** In words, from the asker's side ("3 bread for an iron sword"). */
		public String describe() {
			return this.gift() ? DrifterDeals.describe(this.goods) + (this.owed > 0 ? " (paying it back)" : " (a gift)") : DrifterDeals.describe(this.pay) + " for " + DrifterDeals.describe(this.goods);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Asking
	// ---------------------------------------------------------------------------------------------

	/**
	 * {@code asker} is close to {@code other} (in sight, within talking range; called by its bonds): now and then it asks
	 * for what it needs. {@code bond} is its view of the other.
	 */
	public static void meet(DrifterEntity asker, DrifterEntity other, Relationship bond) {
		long now = asker.level().getGameTime();
		if (now - bond.tradeAskedAt < ASK_AGAIN || bond.trust <= DISLIKES || DEALS.containsKey(asker.getUUID()) || DEALS.containsKey(other.getUUID())
			|| !free(asker) || !free(other)) {
			return;
		}

		Relationship back = other.getBonds().get(asker.getUUID());
		if (back == null || back.trust <= DISLIKES) {
			return;
		}

		bond.tradeAskedAt = now;
		ask(asker, other, back);
	}

	/** Free to trade: not fighting, not in a battle, not one of an encounter's. */
	private static boolean free(DrifterEntity drifter) {
		return drifter.isAlive() && !drifter.isTemporary() && !drifter.inCombat() && !Battle.inBattle(drifter) && !drifter.isSleeping()
			&& drifter.getDrifterBrain().pursuit() == null;
	}

	/**
	 * Asks {@code other} for the thing it wants most that the other can spare, and agrees a price (by the other's view of
	 * it, {@code back}). Returns the deal (already under way), or null.
	 */
	@Nullable
	public static Deal ask(DrifterEntity asker, DrifterEntity other, Relationship back) {
		long now = asker.level().getGameTime();
		DrifterInventory theirs = other.getInventory();
		int askSlot = -1;
		int askCount = 0;
		double bestWant = MIN_WANT;
		for (Map.Entry<Integer, Integer> entry : Storage.spare(other).entrySet()) {
			ItemStack stack = theirs.getItem(entry.getKey());
			if (stack.isEmpty()) {
				continue;
			}

			double want = want(asker, stack);
			if (want >= bestWant) {
				bestWant = want;
				askSlot = entry.getKey();
				askCount = Math.min(entry.getValue(), wanted(asker, stack));
			}
		}

		if (askSlot < 0 || askCount <= 0) {
			return null;
		}

		ItemStack goods = theirs.getItem(askSlot).copyWithCount(askCount);
		String name = other.getName().getString();
		double value = ItemValues.of(goods);
		boolean friends = back.trust >= DrifterBonds.FRIEND || sameGroup(asker, other);
		// What it asks: the worth, more if it's greedy, less for a friend, less what it owes from before.
		int owed = back.debts.stream().mapToInt(Relationship.Debt::value).sum();
		double price = Math.max(0.0, value * (1.0 + 0.6 * (other.getPersonality().get(Trait.GREED) - 0.5)) * (friends ? FRIEND_PRICE : 1.0) - owed);
		ItemStack pay = price < 0.5 ? ItemStack.EMPTY : payment(asker, other, goods, price);
		if (pay == null && friends && value <= GIFT_UP_TO) {
			pay = ItemStack.EMPTY;
		}

		if (pay == null) {
			think(asker, "asked " + name + " for " + describe(goods) + ": nothing it could pay with");
			return null;
		}

		Deal deal = new Deal(asker, other, pay, goods, owed, now);
		DEALS.put(asker.getUUID(), deal);
		DEALS.put(other.getUUID(), deal);
		think(asker, "asked " + name + " for " + describe(goods) + (pay.isEmpty() ? "" : ", offering " + describe(pay)) + ": agreed");
		think(other, asker.getName().getString() + " asked for " + describe(goods) + (pay.isEmpty() ? (owed > 0 ? ": paying back what it owes" : ": a gift")
			: " for " + describe(pay) + ": agreed"));
		if (playerNear(asker)) {
			ChatLines.sayLine(asker, "got any " + plural(goods) + " to spare?" + (pay.isEmpty() ? "" : " I'll give you " + describe(pay)));
			ChatLines.sayLine(other, pay.isEmpty() ? "sure, have " + describe(goods) : "deal");
		}

		return deal;
	}

	/** What it pays with: something it can spare that the other wants, about the price; or emeralds; null: nothing. */
	@Nullable
	private static ItemStack payment(DrifterEntity asker, DrifterEntity other, ItemStack goods, double price) {
		DrifterInventory mine = asker.getInventory();
		ItemStack best = null;
		double bestWant = 0.0;
		for (Map.Entry<Integer, Integer> entry : Storage.spare(asker).entrySet()) {
			ItemStack stack = mine.getItem(entry.getKey());
			if (stack.isEmpty() || ItemStack.isSameItem(stack, goods) || stack.is(Items.EMERALD)) {
				continue;
			}

			double want = want(other, stack);
			double each = ItemValues.each(stack);
			int count = (int)Math.ceil(price / each);
			if (want <= bestWant || count > entry.getValue() || count > wanted(other, stack) * 2 || count * each > price * OVERPAY + 1.0) {
				continue;
			}

			bestWant = want;
			best = stack.copyWithCount(count);
		}

		if (best != null) {
			return best;
		}

		int emeralds = (int)Math.max(1, Math.round(price / EMERALD_WORTH));
		return mine.count(stack -> stack.is(Items.EMERALD)) >= emeralds && emeralds * EMERALD_WORTH <= price * OVERPAY + 3.0
			? new ItemStack(Items.EMERALD, emeralds) : null;
	}

	/** How much it wants this for itself (0 to 1): better gear, food when short, iron with no iron pickaxe, torches, coal, wood. */
	public static double want(DrifterEntity drifter, ItemStack stack) {
		if (stack.is(Items.ROTTEN_FLESH) || stack.is(Items.SPIDER_EYE) || stack.is(Items.POISONOUS_POTATO)) {
			return 0.0;
		}

		double gear = Trading.want(drifter, stack);
		if (gear > 0.0) {
			return gear;
		}

		DrifterInventory inventory = drifter.getInventory();
		int torches = inventory.count(s -> s.is(Items.TORCH));
		if (stack.is(Items.IRON_INGOT)) {
			boolean ironPick = inventory.count(s -> s.is(ItemTags.PICKAXES) && s.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState())) > 0;
			return !ironPick && inventory.count(s -> s.is(Items.IRON_INGOT)) < 3 ? WANT_IRON : 0.0;
		}

		if (stack.is(Items.TORCH)) {
			return torches < 8 ? WANT_TORCHES : 0.0;
		}

		if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
			return torches < 8 && inventory.count(s -> s.is(Items.COAL) || s.is(Items.CHARCOAL)) < 2 ? WANT_COAL : 0.0;
		}

		if (stack.is(ItemTags.LOGS)) {
			return inventory.count(s -> s.is(ItemTags.LOGS)) < 4 && inventory.count(s -> s.is(ItemTags.PLANKS)) < 8 ? WANT_WOOD : 0.0;
		}

		return 0.0;
	}

	/** How many of this it wants. */
	static int wanted(DrifterEntity drifter, ItemStack stack) {
		DrifterInventory inventory = drifter.getInventory();
		if (stack.has(DataComponents.FOOD)) {
			return Math.max(1, Trading.FOOD_SHORT - inventory.count(s -> s.has(DataComponents.FOOD) && !s.is(Items.ROTTEN_FLESH) && !s.is(Items.SPIDER_EYE)));
		} else if (stack.is(Items.IRON_INGOT)) {
			return Math.max(1, 3 - inventory.count(s -> s.is(Items.IRON_INGOT)));
		} else if (stack.is(Items.TORCH)) {
			return Math.max(1, 16 - inventory.count(s -> s.is(Items.TORCH)));
		} else if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
			return 4;
		} else if (stack.is(ItemTags.LOGS)) {
			return 8;
		}

		return 1;
	}

	private static boolean sameGroup(DrifterEntity a, DrifterEntity b) {
		var group = DrifterGroups.of(a.getUUID());
		return group != null && group == DrifterGroups.of(b.getUUID());
	}

	// ---------------------------------------------------------------------------------------------
	// Ending
	// ---------------------------------------------------------------------------------------------

	private static void end(Deal deal, @Nullable String why) {
		if (deal.closed) {
			return;
		}

		deal.closed = true;
		DEALS.remove(deal.asker.getUUID(), deal);
		DEALS.remove(deal.giver.getUUID(), deal);
		if (why != null) {
			think(deal.asker, "the deal with " + deal.giver.getName().getString() + " fell through (" + why + ")");
			think(deal.giver, "the deal with " + deal.asker.getName().getString() + " fell through (" + why + ")");
		}
	}

	/** Both have what they were given: trust for a fair deal, a debt for a gift, the event. */
	private static void complete(Deal deal) {
		deal.completed = true;
		end(deal, null);
		String asker = deal.asker.getName().getString();
		String giver = deal.giver.getName().getString();
		Relationship mine = deal.asker.getBonds().get(deal.giver.getUUID());
		Relationship theirs = deal.giver.getBonds().get(deal.asker.getUUID());
		long now = deal.asker.level().getGameTime();
		if (deal.gift()) {
			think(deal.asker, giver + " gave it " + describe(deal.goods) + (deal.owed > 0 ? " (paying it back)" : " (a gift)"));
			think(deal.giver, "gave " + asker + " " + describe(deal.goods));
			if (mine != null) {
				mine.addTrust(GIFT_TRUST);
				// A gift is owed back (unless it was the giver paying back).
				if (deal.owed <= 0) {
					mine.debts.add(new Relationship.Debt(describe(deal.goods), (int)Math.max(1, Math.round(ItemValues.of(deal.goods))), now));
				}
			}
		} else {
			think(deal.asker, "traded " + describe(deal.pay) + " to " + giver + " for " + describe(deal.goods));
			think(deal.giver, "traded " + describe(deal.goods) + " to " + asker + " for " + describe(deal.pay));
			if (mine != null) {
				mine.addTrust(FAIR_TRUST);
			}

			if (theirs != null) {
				theirs.addTrust(FAIR_TRUST);
			}
		}

		// What the giver owed the asker came off the price: cleared.
		if (deal.owed > 0 && theirs != null) {
			theirs.debts.clear();
		}

		DrifterEvents.TRADED.invoker().onTraded(deal.asker, deal.giver, deal.pay.copy(), deal.goods.copy());
	}

	// ---------------------------------------------------------------------------------------------
	// Doing it
	// ---------------------------------------------------------------------------------------------

	/** Its part in a deal: drop what it's doing, go to the other, swap. Both have this goal. */
	public static final class Goal extends Goals.Goal {
		@Override
		public String id() {
			return "drifterdeal";
		}

		@Override
		public String name(Goals.Context context) {
			Deal deal = DEALS.get(context.drifter().getUUID());
			return deal == null ? "trade with a Drifter" : "trade with " + deal.partner(context.drifter()).getName().getString();
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			Deal deal = DEALS.get(drifter.getUUID());
			if (deal == null) {
				return 0.0;
			}

			long now = drifter.level().getGameTime();
			DrifterEntity partner = deal.partner(drifter);
			if (!partner.isAlive() || partner.isRemoved() || partner.level() != drifter.level()) {
				end(deal, partner.getName().getString() + " is gone");
				return 0.0;
			}

			if (deal.goAt < 0 ? now - deal.made > MEET_WITHIN : now - deal.goAt > SWAP_WITHIN) {
				end(deal, "took too long");
				return 0.0;
			}

			// (Its part done, it gets on with things; the other finishes the deal.)
			return context.threat().urgent() || drifter.inCombat() || (drifter == deal.asker ? deal.askerGot : deal.giverGot) ? 0.0 : 0.75;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public boolean urgent(Goals.Context context) {
			// (Someone's waiting.)
			return true;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 5;
		}

		@Override
		public Activity start(Goals.Context context) {
			Deal deal = DEALS.get(context.drifter().getUUID());
			return deal == null ? null : new Swap(context.drifter(), deal);
		}
	}

	private static final class Swap extends Activity {
		private final Deal deal;
		private final DrifterEntity partner;
		private long tossAt = -1;
		/** What it's going to pick up. */
		@Nullable
		private ItemEntity chasing;

		Swap(DrifterEntity drifter, Deal deal) {
			super(drifter);
			this.deal = deal;
			this.partner = deal.partner(drifter);
		}

		@Override
		public String describe() {
			return "trade with " + this.partner.getName().getString();
		}

		@Override
		protected boolean rethink() {
			// Picked up on the way (or the deal's off): no need to walk on.
			return this.chasing != null && this.chasing.isRemoved() || this.deal.closed;
		}

		@Override
		protected Status next() {
			Deal deal = this.deal;
			if (deal.closed) {
				return deal.completed ? Status.SUCCESS : this.fail("the deal fell through");
			}

			DrifterEntity me = this.drifter;
			boolean asker = me == deal.asker;
			// 1. Get together.
			if (deal.goAt < 0) {
				if (me.distanceTo(this.partner) > TOSS_RANGE) {
					if (asker) {
						deal.askerReady = false;
					} else {
						deal.giverReady = false;
					}

					return this.begin(new NavigateTask(me, this.partner.position(), 2.0, "walk over to " + this.partner.getName().getString(),
						NavigateTask.Pace.STROLL));
				}

				Steering.stopMoving(me);
				me.getCamera().lookAt(this.partner.getEyePosition(), CameraModel.TurnSpeed.NORMAL);
				if (asker) {
					deal.askerReady = true;
				} else {
					deal.giverReady = true;
				}

				if (deal.askerReady && deal.giverReady) {
					// Still has it all? (It may have eaten the bread on the way.)
					if (!has(deal.asker, deal.pay) || !has(deal.giver, deal.goods)) {
						end(deal, "something was missing");
						return this.fail("the deal fell through");
					}

					deal.goAt = this.now();
				}

				return Status.RUNNING;
			}

			// 2. Toss its side over (looking at their feet, as a player would).
			if (!deal.tossed(me)) {
				Steering.stopMoving(me);
				me.getCamera().lookAt(this.partner.position().add(0.0, 0.3, 0.0), CameraModel.TurnSpeed.NORMAL);
				if (this.tossAt < 0) {
					this.tossAt = this.now() + 8;
				}

				if (this.now() < this.tossAt) {
					return Status.RUNNING;
				}

				ItemStack gives = deal.gives(me);
				if (!gives.isEmpty()) {
					int left = gives.getCount();
					DrifterInventory inventory = me.getInventory();
					for (int slot = 0; slot < DrifterInventory.MAIN_SIZE && left > 0; slot++) {
						ItemStack stack = inventory.getItem(slot);
						if (ItemStack.isSameItemSameComponents(stack, gives)) {
							ItemStack piece = stack.split(Math.min(left, stack.getCount()));
							left -= piece.getCount();
							ItemEntity item = me.tossForward(piece);
							item.setTarget(this.partner.getUUID());
							deal.tossedBy(me).add(item);
						}
					}
				}

				if (asker) {
					deal.askerTossed = true;
				} else {
					deal.giverTossed = true;
				}

				if (!asker && playerNear(me)) {
					ChatLines.sayLine(me, deal.gift() ? "here you go" : "pleasure doing business");
				}

				return Status.RUNNING;
			}

			// 3. Pick up what it was given.
			if (!deal.tossed(this.partner)) {
				me.getCamera().lookAt(this.partner.getEyePosition(), CameraModel.TurnSpeed.NORMAL);
				return Status.RUNNING;
			}

			ItemEntity nearest = null;
			for (ItemEntity item : deal.tossedBy(this.partner)) {
				if (!item.isRemoved() && (nearest == null || item.distanceTo(me) < nearest.distanceTo(me))) {
					nearest = item;
				}
			}

			this.chasing = nearest;
			if (nearest != null) {
				// A step or two away: straight there (it's picked up within a block); further, or up or down: the way there.
				Vec3 to = nearest.position().subtract(me.position());
				if (to.horizontalDistance() > 0.8 && (to.horizontalDistance() > 3.0 || Math.abs(to.y) > 1.0
					|| !Steering.safeStep(me, new Vec3(to.x, 0.0, to.z).normalize()))) {
					return this.begin(new NavigateTask(me, nearest.position(), 0.9, "pick up what " + this.partner.getName().getString() + " gave it",
						NavigateTask.Pace.STROLL));
				}

				me.getCamera().lookAt(nearest.position(), CameraModel.TurnSpeed.NORMAL);
				if (to.horizontalDistance() > 0.8) {
					Steering.pressToward(me, nearest.position());
				} else {
					Steering.stopMoving(me);
				}

				return Status.RUNNING;
			}

			if (asker) {
				deal.askerGot = true;
			} else {
				deal.giverGot = true;
			}

			if (deal.askerGot && deal.giverGot) {
				complete(deal);
			}

			return Status.SUCCESS;
		}
	}

	private static boolean has(DrifterEntity drifter, ItemStack stack) {
		return stack.isEmpty() || drifter.getInventory().count(s -> ItemStack.isSameItemSameComponents(s, stack)) >= stack.getCount();
	}

	// ---------------------------------------------------------------------------------------------
	// Words
	// ---------------------------------------------------------------------------------------------

	/** "3 bread", "an iron sword". */
	public static String describe(ItemStack stack) {
		String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
		if (stack.getCount() == 1) {
			return ("aeiou".indexOf(name.isEmpty() ? 'x' : name.charAt(0)) >= 0 ? "an " : "a ") + name;
		}

		return stack.getCount() + " " + name;
	}

	/** "iron ingots"-ish, for a question. */
	private static String plural(ItemStack stack) {
		String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
		return stack.getCount() > 1 && !name.endsWith("s") && !stack.has(DataComponents.FOOD) ? name + "s" : name;
	}

	private static boolean playerNear(DrifterEntity drifter) {
		Player player = drifter.level().getNearestPlayer(drifter, 16.0);
		return player != null && SocialWatcher.isRealPlayer(player);
	}

	private static void think(DrifterEntity drifter, String thought) {
		drifter.getMemory().thoughts().add(drifter.level().getGameTime(), thought);
	}
}
