package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterRecord;
import com.dinglebob.drifter.api.ShopKeeper;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.Rotation;

/**
 * A town's market (docs/CIVILIZATION.md part 2, money and trade; milestone 22 step 4): a stall its people build once the
 * store is up (a counter, the keeper behind it), and a keeper (the most trader-like of them: greedy and sociable) who
 * keeps it by day. A player who right-clicks the keeper gets the trading screen: it sells what the town has plenty of
 * (it takes it from the store in the morning) and buys what the town is short of, with money it has (emeralds, or
 * Numismatic Overhaul's coins when that mod is here). Prices from supply and demand: an item's worth, dearer when the
 * town has little of it, cheaper when it has plenty (iron cheap in a mining town). Every trade real (Drifter's
 * DrifterMerchant): what it sells comes out of its pockets, what it's paid goes in.
 */
public final class Market {
	private Market() {
	}

	/** The town build's name for the stall. */
	static final String STALL = "stall";
	/** It sells kinds it has at least this many of in the store, this many kinds at most. */
	private static final int PLENTY = 32;
	private static final int KINDS = 6;
	/** It buys what the town has fewer than this of. */
	private static final int SHORT = 32;
	/** A player selling it something gets this much of the price it sells for. */
	private static final double BUYS_AT = 0.8;
	/** What it buys from players (when the town is short of it): food, iron, coal, wood, stone, wool, leather, light. */
	private static final List<Item> WANTED = List.of(Items.BREAD, Items.WHEAT, Items.IRON_INGOT, Items.COAL, Items.OAK_LOG, Items.SPRUCE_LOG,
		Items.BIRCH_LOG, Items.COBBLESTONE, Items.WHITE_WOOL, Items.LEATHER, Items.TORCH);

	// ---------------------------------------------------------------------------------------------
	// The stall
	// ---------------------------------------------------------------------------------------------

	/** The stall: begun once the store's up, built by them all; then the keeper. True if anything changed. */
	static boolean stall(MinecraftServer server, Town town, DrifterGroups.Group group) {
		if (!town.storeBuilt) {
			return false;
		}

		if (town.stallBuilt) {
			for (UUID id : group.members) {
				DrifterEntity member = TownFounding.loaded(server, id);
				var build = member == null ? null : DrifterApi.townBuildOf(member);
				if (build != null && build.label.equals(STALL)) {
					DrifterApi.endTownBuild(member);
				}
			}

			return keeper(server, town, group);
		}

		boolean changed = false;
		if (town.stallPlot == null) {
			DrifterEntity leader = TownFounding.loaded(server, group.leader);
			if (leader == null) {
				return false;
			}

			DrifterApi.HousePlanInfo info = DrifterApi.housePlans(server).stream().filter(plan -> plan.id().equals(DrifterApi.stallPlan())).findFirst()
				.orElse(new DrifterApi.HousePlanInfo(DrifterApi.stallPlan(), 5, 4, 4, false));
			// (Its counter to the road: its door, at the back, away from it.)
			Town.Plot plot = TownFounding.findPlotByAnyone(server, town, group, List.of(info), true);
			if (plot == null) {
				return false;
			}

			plot.owner = null;
			town.stallPlot = plot;
			List<DrifterEntity> builders = new ArrayList<>();
			for (UUID id : group.members) {
				DrifterEntity member = TownFounding.loaded(server, id);
				if (member != null && member != leader && DrifterApi.townBuildOf(member) == null) {
					builders.add(member);
				}
			}

			builders.add(0, leader);
			DrifterApi.townBuild(builders, plot.planId, plot.origin, plot.rotation, STALL);
			Chronicle.add(server, "town", "The people of " + town.name + " began building a market stall");
			changed = true;
		}

		// Those not here when it was begun join in, in the same woods (once they're done with any other town build).
		Home.Build any = null;
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			var build = member == null ? null : DrifterApi.townBuildOf(member);
			if (build != null && build.label.equals(STALL)) {
				any = build;
			}
		}

		if (any != null) {
			for (UUID id : group.members) {
				DrifterEntity member = TownFounding.loaded(server, id);
				if (member != null && DrifterApi.townBuildOf(member) == null) {
					DrifterApi.joinTownBuild(member, any.planId, any.origin, Rotation.valueOf(any.rotation), STALL, any.woods, any.deepslate);
				}
			}
		}

		return changed;
	}

	static void stallBuilt(@Nullable MinecraftServer server, Town town) {
		if (server == null || town.stallPlot == null) {
			return;
		}

		town.stallBuilt = true;
		Chronicle.add(server, "town", "The market stall of " + town.name + " was finished");
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group != null) {
			stall(server, town, group);
		}

		Towns.save(server);
	}

	// ---------------------------------------------------------------------------------------------
	// The keeper
	// ---------------------------------------------------------------------------------------------

	/** The most trader-like member keeps the stall (another if it's gone for good); its shop set each check. */
	static boolean keeper(MinecraftServer server, Town town, DrifterGroups.Group group) {
		boolean changed = false;
		DrifterRecord record = town.keeper == null ? null : DrifterApi.record(town.keeper);
		// (Not its crook: the user, 2026-10-28, "the crook is the shopkeeper". One who turned crook gives the stall up.)
		boolean crook = town.keeper != null && town.crooks.contains(town.keeper);
		if (town.keeper == null || crook || !group.members.contains(town.keeper) || record != null && record.status() == DrifterRecord.Status.GONE) {
			DrifterEntity old = crook ? TownFounding.loaded(server, town.keeper) : null;
			if (old != null) {
				DrifterApi.setShop(old, null);
			}

			DrifterEntity best = null;
			double bestScore = -1.0;
			for (UUID id : group.members) {
				DrifterEntity member = TownFounding.loaded(server, id);
				if (member != null && !town.crooks.contains(id)) {
					double score = DrifterApi.trait(member, "greed") + DrifterApi.trait(member, "sociability");
					if (score > bestScore) {
						best = member;
						bestScore = score;
					}
				}
			}

			if (best == null) {
				return false;
			}

			town.keeper = best.getUUID();
			DrifterApi.think(best, "is to keep the market stall of " + town.name);
			Chronicle.add(server, "town", best.getName().getString() + " keeps the market stall in " + town.name);
			changed = true;
		}

		DrifterEntity keeper = TownFounding.loaded(server, town.keeper);
		if (keeper != null && !(keeper.getShop() instanceof TownShop shop && shop.town == town)) {
			DrifterApi.setShop(keeper, new TownShop(town));
		}

		return changed;
	}

	// ---------------------------------------------------------------------------------------------
	// The shop and its prices
	// ---------------------------------------------------------------------------------------------

	/** The keeper's shop: the town's stall, its stock and prices. */
	static final class TownShop implements ShopKeeper {
		final Town town;

		TownShop(Town town) {
			this.town = town;
		}

		@Nullable
		@Override
		public BlockPos stand(DrifterEntity keeper) {
			MinecraftServer server = keeper.getServer();
			return server == null || this.town.stallPlot == null || !this.town.stallBuilt ? null
				: DrifterApi.stallStand(server, this.town.stallPlot.origin, this.town.stallPlot.rotation);
		}

		/** What it takes from the store to sell: the kinds the town has plenty of (as it knows its chests). */
		@Override
		public Map<Item, Integer> stock(DrifterEntity keeper) {
			Map<Item, Integer> stock = new LinkedHashMap<>();
			Home home = DrifterApi.home(keeper);
			if (home == null) {
				return stock;
			}

			// What its people buy first (food, torches, iron: step 4), then what there's most of.
			home.stored().entrySet().stream().filter(entry -> entry.getValue() >= plenty(entry.getKey()) && sellable(entry.getKey()))
				.sorted(java.util.Comparator.<Map.Entry<Item, Integer>>comparingInt(entry -> plenty(entry.getKey()) < PLENTY ? 0 : 1)
					.thenComparing(Map.Entry.<Item, Integer>comparingByValue().reversed())).limit(KINDS)
				.forEach(entry -> stock.put(entry.getKey(), Math.min(64, entry.getValue() / 2)));
			return stock;
		}

		@Override
		public List<ShopOffer> offers(DrifterEntity keeper, Player player) {
			List<ShopOffer> offers = new ArrayList<>();
			// Selling: what it took from the store to sell, as much as it carries.
			for (Item item : this.stock(keeper).keySet()) {
				int carried = keeper.getInventory().count(stack -> stack.is(item));
				ShopOffer offer = trade(item, price(keeper, item), carried, true);
				if (offer != null) {
					offers.add(offer);
				}
			}

			// Buying: what the town is short of, with the money it has.
			for (Item item : WANTED) {
				if (supply(keeper, item) >= SHORT) {
					continue;
				}

				ShopOffer offer = trade(item, price(keeper, item) * BUYS_AT, 0, false);
				if (offer != null) {
					ItemStack money = offer.goods();
					int has = keeper.getInventory().count(stack -> stack.is(money.getItem()));
					int times = has / money.getCount();
					if (times > 0) {
						offers.add(new ShopOffer(offer.pay(), ItemStack.EMPTY, money, times));
					}
				}
			}

			return offers;
		}

		@Override
		public void traded(DrifterEntity keeper, Player player, ShopOffer offer) {
			this.town.trades++;
		}

		@Override
		public void soldTo(DrifterEntity keeper, DrifterEntity buyer, ShopOffer offer, int times) {
			this.town.trades++;
			this.town.drifterTrades++;
		}

		/** Its takings go to the town's till (step 4: the money its wages are paid from). */
		@Nullable
		@Override
		public BlockPos till(DrifterEntity keeper) {
			return this.town.till();
		}
	}

	/**
	 * How many the store must have before it sells some: food, iron and torches go sooner (its people buy those with their
	 * wages, step 4: a town shouldn't need 32 loaves before it sells one).
	 */
	private static int plenty(Item item) {
		ItemStack stack = new ItemStack(item);
		return stack.has(net.minecraft.core.component.DataComponents.FOOD) || item == Items.IRON_INGOT ? 12 : item == Items.TORCH ? 16 : PLENTY;
	}

	/** What a stall buys (when its town is short of it), what traders carry between towns. */
	static List<Item> wanted() {
		return WANTED;
	}

	/** Not its tools, weapons or armour, nor money, nor things that are part of its house. */
	private static boolean sellable(Item item) {
		ItemStack stack = new ItemStack(item);
		return !(item instanceof TieredItem) && !(item instanceof ArmorItem) && DrifterApi.moneyWorth(stack) <= 0.0 && item != Items.CHEST
			&& item != Items.CRAFTING_TABLE && item != Items.FURNACE && !stack.is(net.minecraft.tags.ItemTags.BEDS);
	}

	/** How much of it the town has (as the keeper knows its chests, and in its pockets). */
	private static int supply(DrifterEntity keeper, Item item) {
		Home home = DrifterApi.home(keeper);
		return (home == null ? 0 : home.stored(item)) + keeper.getInventory().count(stack -> stack.is(item));
	}

	/** One's price in points: its worth, dearer the less the town has (up to 1.6 times), cheaper the more (down to half). */
	static double price(DrifterEntity keeper, Item item) {
		int supply = supply(keeper, item);
		double factor = Math.max(0.5, Math.min(1.6, 1.6 - 1.1 * supply / (supply + 48.0)));
		return Math.max(0.05, DrifterApi.worth(new ItemStack(item)) * factor);
	}

	/**
	 * A trade at {@code each} points apiece: enough of them together to cost one piece of money at least (16 bread for an
	 * emerald), in money of about the worth. Selling ({@code sells}): the player pays money for the goods, as many times as
	 * it has them; buying: the player gives the goods for money (its times set by the caller).
	 */
	@Nullable
	static ShopKeeper.ShopOffer trade(Item item, double each, int carried, boolean sells) {
		double smallest = DrifterApi.moneyWorth(DrifterApi.money(0.0));
		int bundle = (int)Math.max(1, Math.min(64, Math.ceil(smallest / each)));
		ItemStack money = DrifterApi.money(each * bundle);
		ItemStack goods = new ItemStack(item, Math.min(bundle, item.getDefaultMaxStackSize()));
		if (sells) {
			int times = carried / goods.getCount();
			return times > 0 ? new ShopKeeper.ShopOffer(money, ItemStack.EMPTY, goods, times) : null;
		}

		return new ShopKeeper.ShopOffer(goods, ItemStack.EMPTY, money, 1);
	}

	/** Today's prices at this town's stall, for `/drifterciv prices` (what it sells and buys, and for how much). */
	static List<String> prices(MinecraftServer server, Town town) {
		List<String> lines = new ArrayList<>();
		DrifterEntity keeper = town.keeper == null ? null : TownFounding.loaded(server, town.keeper);
		if (keeper == null || !(keeper.getShop() instanceof TownShop shop)) {
			lines.add(town.name + " has no market stall open yet");
			return lines;
		}

		lines.add(town.name + "'s stall, kept by " + keeper.getName().getString() + (town.trades > 0 ? " (" + town.trades + " trades so far)" : ""));
		Map<Item, Integer> stock = shop.stock(keeper);
		lines.add("  Stocks up on: " + (stock.isEmpty() ? "nothing (the store has nothing it has plenty of)" : String.join(", ", stock.entrySet().stream()
			.map(entry -> entry.getValue() + " " + new ItemStack(entry.getKey()).getHoverName().getString().toLowerCase(java.util.Locale.ROOT)).toList())));
		for (ShopKeeper.ShopOffer offer : shop.offers(keeper, null)) {
			lines.add("  " + describe(offer.goods()) + " for " + describe(offer.pay()) + " (" + offer.times() + " times)");
		}

		return lines;
	}

	static String describe(ItemStack stack) {
		return stack.getCount() + " " + stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
	}

}
