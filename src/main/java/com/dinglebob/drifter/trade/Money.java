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

	public static final boolean COINS = FabricLoader.getInstance().isModLoaded("numismatic-overhaul");
	private static final String NAMESPACE = "numismatic-overhaul";
	public static final long SILVER = 100;
	public static final long GOLD = 10000;
	/** What a bronze coin is worth in {@code ItemValues} points (an emerald is 6, about 50 bronze at the mod's prices). */
	public static final double BRONZE_POINTS = 0.12;

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
