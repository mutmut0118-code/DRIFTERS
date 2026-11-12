package com.dinglebob.drifter.trade;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Money (milestone 22, the user 2026-10-21: "incorporate numismatic overhaul"): emeralds, and with Numismatic Overhaul
 * (optional, found at runtime) its coins: bronze, silver (100 bronze) and gold (100 silver). With that mod villagers price
 * everything in coins, so a Drifter pays and is paid in coins too. Coins it carries are plain items (the mod's purse is a
 * player's, filled by right-clicking coins); like a player using the purse, it can change coins of one size for another of
 * the same worth when it pays.
 */
public final class Money {
	private Money() {
	}

	/**
	 * Debug only (the environment variable DRIFTER_COINS_TEST=1, a development game): three plain items stand in for the
	 * coins (a brick for bronze, a nether brick for silver, a prismarine shard for gold), so money in coins can be tested
	 * where Numismatic Overhaul itself can't be loaded (it doesn't remap into a development game).
	 */
	private static final boolean STAND_INS = "1".equals(System.getenv("DRIFTER_COINS_TEST")) && FabricLoader.getInstance().isDevelopmentEnvironment();
	public static final boolean COINS = FabricLoader.getInstance().isModLoaded("numismatic-overhaul") || STAND_INS;
	private static final String NAMESPACE = "numismatic-overhaul";
	public static final long SILVER = 100;
	public static final long GOLD = 10000;
	/**
	 * Bronze an emerald's worth comes to (the user, 2026-11-12: "think of bronze more like pennies, silver coins as dollars
	 * 10s or 20s, and gold coins as like 100 bills ... its very easy to get bronze coins, making it extremely easy to trade
	 * with drifters"): as the mod itself has it. Its villagers buy an emerald for 1,000 bronze and a diamond for 3,000, sell
	 * an iron chestplate at 3,200 and a diamond axe at 10,000; a pillager drops about 75, an outpost's chest holds 1,500 to
	 * 4,000. (Till 0.23.49 an emerald's worth was 50 bronze: one pillager's drop bought an emerald and a half of goods.)
	 */
	public static final long EMERALD_BRONZE = 1000;
	/** What an emerald is worth in {@code ItemValues} points. */
	public static final double EMERALD_POINTS = 6.0;
	/** What a bronze coin is worth in {@code ItemValues} points. */
	public static final double BRONZE_POINTS = EMERALD_POINTS / EMERALD_BRONZE;

	/** A price or a sum: in coins (bronze), or in emeralds. */
	public record Price(boolean coins, long amount) {
		public String describe() {
			if (!this.coins) {
				return this.amount + (this.amount == 1 ? " emerald" : " emeralds");
			}

			if (this.amount >= GOLD && this.amount % GOLD == 0) {
				return this.amount / GOLD + " gold";
			}

			return this.amount >= SILVER && this.amount % SILVER == 0 ? this.amount / SILVER + " silver" : this.amount + " bronze";
		}
	}

	@Nullable
	private static Item coin(String name) {
		if (!COINS) {
			return null;
		}

		if (STAND_INS) {
			return name.startsWith("bronze") ? Items.BRICK : name.startsWith("silver") ? Items.NETHER_BRICK : Items.PRISMARINE_SHARD;
		}

		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NAMESPACE, name));
		return item == Items.AIR ? null : item;
	}

	private static Item bronze;
	private static Item silver;
	private static Item gold;
	private static boolean looked;

	private static void look() {
		if (!looked) {
			looked = true;
			bronze = coin("bronze_coin");
			silver = coin("silver_coin");
			gold = coin("gold_coin");
		}
	}

	/** Bronze one of these is worth (0: not a coin). */
	public static long coinValue(Item item) {
		look();
		return item == null || item == Items.AIR ? 0 : item == bronze ? 1 : item == silver ? SILVER : item == gold ? GOLD : 0;
	}

	public static boolean isCoin(ItemStack stack) {
		return coinValue(stack.getItem()) > 0;
	}

	/** Emeralds or coins. */
	public static boolean isMoney(String id) {
		return id.equals("minecraft:emerald") || COINS && coinValue(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))) > 0;
	}

	/** {@code count} of this item as a price, or null if it isn't money. */
	@Nullable
	public static Price price(String id, int count) {
		if (id.equals("minecraft:emerald")) {
			return new Price(false, count);
		}

		long each = COINS && !id.isEmpty() ? coinValue(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))) : 0;
		return each > 0 ? new Price(true, each * count) : null;
	}

	/** What it carries in coins (bronze), or in emeralds. */
	public static long carried(DrifterEntity drifter, boolean coins) {
		DrifterInventory inventory = drifter.getInventory();
		long total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			total += coins ? coinValue(stack.getItem()) * stack.getCount() : stack.is(Items.EMERALD) ? stack.getCount() : 0;
		}

		return total;
	}

	/** What it carries in whole emeralds' worth: its emeralds, and with Numismatic Overhaul its coins. */
	public static int carriedEmeralds(DrifterEntity drifter) {
		return (int)(carried(drifter, false) + coins(drifter) / EMERALD_BRONZE);
	}

	/** This many emeralds' worth into its pockets: coins with Numismatic Overhaul, else emeralds (an encounter's purse, a kit's). */
	public static void addTo(DrifterInventory inventory, long emeralds) {
		for (ItemStack stack : stacks(emeralds)) {
			inventory.add(stack);
		}
	}

	private static final java.util.regex.Pattern EMERALDS = java.util.regex.Pattern.compile("\\b(\\d{1,6}) emeralds?\\b");

	/** Words that name a sum in emeralds ("6 emeralds"), said in coins when Numismatic Overhaul is here ("60 silver"). */
	public static String inCoins(String text) {
		if (!COINS || text == null || text.indexOf("emerald") < 0) {
			return text;
		}

		java.util.regex.Matcher found = EMERALDS.matcher(text);
		StringBuilder out = new StringBuilder();
		while (found.find()) {
			found.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(words(Long.parseLong(found.group(1)))));
		}

		found.appendTail(out);
		return out.toString();
	}

	/** The silver coin (null without Numismatic Overhaul). */
	@Nullable
	public static Item silverCoin() {
		look();
		return silver;
	}

	/** Its coins, in bronze. */
	public static long coins(DrifterEntity drifter) {
		return COINS ? carried(drifter, true) : 0;
	}

	/** One coin's worth in {@code ItemValues} points. */
	public static double points(ItemStack stack) {
		return coinValue(stack.getItem()) * BRONZE_POINTS;
	}

	/**
	 * Before paying {@code count} of the coin {@code wanted} out of {@code inventory} (its helper's, at the moment it trades):
	 * if it hasn't that many of that coin but its coins are worth enough, it changes them, as a player does with the purse:
	 * all its coins in, that many of that coin out, the rest back as gold, silver and bronze. Nothing gained or lost.
	 */
	public static void makeChange(Inventory inventory, Item wanted, int count) {
		long each = coinValue(wanted);
		if (each <= 0) {
			return;
		}

		long have = 0;
		long ofIt = 0;
		for (ItemStack stack : inventory.items) {
			long value = coinValue(stack.getItem());
			have += value * stack.getCount();
			if (stack.is(wanted)) {
				ofIt += stack.getCount();
			}
		}

		long need = each * count;
		if (ofIt >= count || have < need) {
			return;
		}

		for (ItemStack stack : inventory.items) {
			if (coinValue(stack.getItem()) > 0) {
				stack.setCount(0);
			}
		}

		give(inventory, wanted, count);
		long rest = have - need;
		look();
		for (Item coin : new Item[] {gold, silver, bronze}) {
			long value = coinValue(coin);
			if (coin != null && rest >= value) {
				give(inventory, coin, rest / value);
				rest %= value;
			}
		}
	}

	private static void give(Inventory inventory, Item coin, long count) {
		while (count > 0) {
			int now = (int)Math.min(count, coin.getDefaultMaxStackSize());
			ItemStack stack = new ItemStack(coin, now);
			if (!inventory.add(stack) && !stack.isEmpty()) {
				inventory.player.drop(stack, false);
			}

			count -= now;
		}
	}

	/**
	 * What it pays a Drifter about {@code points} worth with: coins if it has them (one size, the nearest), else emeralds;
	 * null if it has too little. Coins are changed first if need be.
	 */
	@Nullable
	public static ItemStack pay(DrifterEntity drifter, double points, double overpay) {
		if (coins(drifter) > 0) {
			long bronzeNeeded = Math.max(1, (long)Math.ceil(points / BRONZE_POINTS));
			look();
			Item coin = bronzeNeeded >= GOLD && gold != null ? gold : bronzeNeeded >= SILVER && silver != null ? silver : bronze;
			long count = Math.max(1, Math.round(bronzeNeeded / (double)coinValue(coin)));
			if (coin != null && count <= coin.getDefaultMaxStackSize() && coins(drifter) >= count * coinValue(coin)
				&& count * coinValue(coin) * BRONZE_POINTS <= points * overpay + 1.0) {
				return new ItemStack(coin, (int)count);
			}
		}

		int emeralds = (int)Math.max(1, Math.round(points / 6.0));
		return carried(drifter, false) >= emeralds && emeralds * 6.0 <= points * overpay + 3.0 ? new ItemStack(Items.EMERALD, emeralds) : null;
	}

	/**
	 * About {@code points} worth of money as one stack, for a price (a shop's): coins with Numismatic Overhaul (the size that
	 * keeps the count to 64 or less), else emeralds (one at least).
	 */
	public static ItemStack priceStack(double points) {
		look();
		if (COINS && bronze != null) {
			long bronzeWorth = Math.max(1, Math.round(points / BRONZE_POINTS));
			Item coin = bronzeWorth <= 64 || silver == null ? bronze : bronzeWorth <= 64 * SILVER || gold == null ? silver : gold;
			return new ItemStack(coin, (int)Math.max(1, Math.min(64, Math.round(bronzeWorth / (double)coinValue(coin)))));
		}

		return new ItemStack(Items.EMERALD, (int)Math.max(1, Math.min(64, Math.round(points / 6.0))));
	}

	// ----- Sums in emeralds' worth (a town's till, a tax, a fine, a tribute: counted in emeralds since before coins) -----

	/** This many emeralds' worth, in words: "3 emeralds"; with coins, "30 silver", "1 gold and 20 silver", "250 bronze". */
	public static String words(long emeralds) {
		if (!COINS) {
			return emeralds + (emeralds == 1 ? " emerald" : " emeralds");
		}

		long bronzeWorth = emeralds * EMERALD_BRONZE;
		long g = bronzeWorth / GOLD;
		long s = bronzeWorth % GOLD / SILVER;
		long b = bronzeWorth % SILVER;
		java.util.List<String> parts = new java.util.ArrayList<>();
		if (g > 0) {
			parts.add(g + " gold");
		}

		if (s > 0) {
			parts.add(s + " silver");
		}

		if (b > 0 || parts.isEmpty()) {
			parts.add(b + " bronze");
		}

		return String.join(" and ", parts);
	}

	/** This many emeralds' worth as stacks to hand over: coins (the fewest) with Numismatic Overhaul, else emeralds. */
	public static java.util.List<ItemStack> stacks(long emeralds) {
		java.util.List<ItemStack> out = new java.util.ArrayList<>();
		look();
		if (COINS && bronze != null) {
			long rest = emeralds * EMERALD_BRONZE;
			for (Item coin : new Item[] {gold, silver, bronze}) {
				long value = coinValue(coin);
				long count = coin == null || value <= 0 ? 0 : rest / value;
				rest -= count * value;
				while (count > 0) {
					int now = (int)Math.min(count, coin.getDefaultMaxStackSize());
					out.add(new ItemStack(coin, now));
					count -= now;
				}
			}

			return out;
		}

		while (emeralds > 0) {
			int now = (int)Math.min(emeralds, 64);
			out.add(new ItemStack(Items.EMERALD, now));
			emeralds -= now;
		}

		return out;
	}

	/** The money in a chest, in whole emeralds' worth: its emeralds, and with Numismatic Overhaul its coins. */
	public static int held(net.minecraft.world.Container chest) {
		long bronzeWorth = 0;
		int emeralds = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack stack = chest.getItem(i);
			if (stack.is(Items.EMERALD)) {
				emeralds += stack.getCount();
			} else if (COINS) {
				bronzeWorth += coinValue(stack.getItem()) * stack.getCount();
			}
		}

		return emeralds + (int)(bronzeWorth / EMERALD_BRONZE);
	}

	/**
	 * Up to this many emeralds' worth taken out of a chest: its coins first (changed as need be: what's over goes back in
	 * as the fewest coins), then its emeralds. How many emeralds' worth came out. Nothing made, nothing lost.
	 */
	public static int take(net.minecraft.world.Container chest, int emeralds) {
		int taken = 0;
		if (COINS && emeralds > 0) {
			long have = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				have += coinValue(chest.getItem(i).getItem()) * chest.getItem(i).getCount();
			}

			int fromCoins = (int)Math.min(emeralds, have / EMERALD_BRONZE);
			if (fromCoins > 0) {
				for (int i = 0; i < chest.getContainerSize(); i++) {
					if (isCoin(chest.getItem(i))) {
						chest.setItem(i, ItemStack.EMPTY);
					}
				}

				long rest = have - fromCoins * EMERALD_BRONZE;
				look();
				for (Item coin : new Item[] {gold, silver, bronze}) {
					long value = coinValue(coin);
					long count = coin == null || value <= 0 ? 0 : rest / value;
					rest -= count * value;
					add(chest, coin, count);
				}

				taken += fromCoins;
			}
		}

		for (int i = 0; i < chest.getContainerSize() && taken < emeralds; i++) {
			ItemStack stack = chest.getItem(i);
			if (stack.is(Items.EMERALD)) {
				int take = Math.min(stack.getCount(), emeralds - taken);
				stack.shrink(take);
				taken += take;
			}
		}

		if (taken > 0) {
			chest.setChanged();
		}

		return taken;
	}

	/** Up to this many emeralds' worth put into a chest (coins with Numismatic Overhaul, else emeralds): how many went in. */
	public static int put(net.minecraft.world.Container chest, int emeralds) {
		int done = 0;
		for (int n = 0; n < emeralds; n++) {
			// (An emerald's worth at a time, so what's put in is always whole: a full chest takes what it can.)
			java.util.List<ItemStack> one = stacks(1);
			boolean room = true;
			java.util.List<int[]> placed = new java.util.ArrayList<>();
			for (ItemStack stack : one) {
				long left = add(chest, stack.getItem(), stack.getCount());
				placed.add(new int[] {stack.getCount() - (int)left});
				if (left > 0) {
					room = false;
					// (No room for all of it: what went in of this one comes out again.)
					for (int k = 0; k < placed.size(); k++) {
						remove(chest, one.get(k).getItem(), placed.get(k)[0]);
					}

					break;
				}
			}

			if (!room) {
				break;
			}

			done++;
		}

		if (done > 0) {
			chest.setChanged();
		}

		return done;
	}

	/** Adds this many of an item to a chest (stacking, then empty slots): how many found no room. */
	private static long add(net.minecraft.world.Container chest, @Nullable Item item, long count) {
		if (item == null) {
			return count;
		}

		for (int i = 0; i < chest.getContainerSize() && count > 0; i++) {
			ItemStack stack = chest.getItem(i);
			if (stack.is(item) && stack.getCount() < stack.getMaxStackSize()) {
				int now = (int)Math.min(count, stack.getMaxStackSize() - stack.getCount());
				stack.grow(now);
				count -= now;
			}
		}

		for (int i = 0; i < chest.getContainerSize() && count > 0; i++) {
			if (chest.getItem(i).isEmpty()) {
				int now = (int)Math.min(count, item.getDefaultMaxStackSize());
				chest.setItem(i, new ItemStack(item, now));
				count -= now;
			}
		}

		return count;
	}

	private static void remove(net.minecraft.world.Container chest, Item item, int count) {
		for (int i = 0; i < chest.getContainerSize() && count > 0; i++) {
			ItemStack stack = chest.getItem(i);
			if (stack.is(item)) {
				int now = Math.min(count, stack.getCount());
				stack.shrink(now);
				count -= now;
			}
		}
	}

	/** What a stack of money is worth in {@code ItemValues} points (0 if it isn't money). */
	public static double worth(ItemStack money) {
		return money.is(Items.EMERALD) ? 6.0 * money.getCount() : coinValue(money.getItem()) * BRONZE_POINTS * money.getCount();
	}

	/** Changes its own coins (in its pockets) so it has {@code count} of {@code coin}, if they're worth enough. */
	public static void makeChange(DrifterEntity drifter, Item coin, int count) {
		long each = coinValue(coin);
		DrifterInventory inventory = drifter.getInventory();
		if (each <= 0 || inventory.count(stack -> stack.is(coin)) >= count || coins(drifter) < each * count) {
			return;
		}

		long have = coins(drifter);
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (isCoin(inventory.getItem(slot))) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}

		long rest = have - each * count;
		addCoins(drifter, coin, count);
		look();
		for (Item c : new Item[] {gold, silver, bronze}) {
			long value = coinValue(c);
			if (c != null && rest >= value) {
				addCoins(drifter, c, rest / value);
				rest %= value;
			}
		}
	}

	private static void addCoins(DrifterEntity drifter, Item coin, long count) {
		while (count > 0) {
			int now = (int)Math.min(count, coin.getDefaultMaxStackSize());
			ItemStack stack = new ItemStack(coin, now);
			if (!drifter.getInventory().add(stack) && !stack.isEmpty()) {
				drifter.tossForward(stack, false);
			}

			count -= now;
		}
	}
}
