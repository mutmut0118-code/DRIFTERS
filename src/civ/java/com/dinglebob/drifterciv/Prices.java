package com.dinglebob.drifterciv;

import java.util.Map;

import com.dinglebob.drifter.api.DrifterApi;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * One scale for what the towns pay and ask (the economy balanced, 0.23.68; the user, 2026-11-14: "just correct the whole
 * economy to where its balanced. i feel like doing one board quest makes me mega rich with trading, since most things
 * cost one silver coin"). The stalls follow Numismatic Overhaul's own prices where it has one (the user's rule, 0.23.50),
 * so everything else is measured against them: goods asked for (a notice, a town's shortage) pay a little over what a
 * stall would give for them and never as much as a stall would sell them for (no profit in buying them to hand in), and a
 * job's pay is reckoned against the price of food (a bread is about 18 bronze, a steak 75): an errand is a few silver,
 * a long one tens, a dangerous one a gold or so at most. Without that mod the same sums in points, as whole emeralds.
 * Points: an emerald is 6, a silver coin 0.6, a bronze 0.006.
 */
final class Prices {
	private Prices() {
	}

	/** A silver coin in points. */
	static final double SILVER = 0.6;

	// The pay for work, in silver.
	/** Carry word: this, and this more for each 300 blocks, at most this. */
	static final double WORD = 3 * SILVER;
	static final double WORD_PER = 2 * SILVER;
	static final double WORD_EVERY = 300.0;
	static final double WORD_MOST = 30 * SILVER;
	/** Find someone to live in a town; deal with a bandit (a stranger, someone liked, a friend or one of their own). */
	static final double[] FIND = {15 * SILVER, 15 * SILVER, 25 * SILVER};
	static final double[] DEAL = {30 * SILVER, 30 * SILVER, 50 * SILVER};
	/** A flower on a grave (thanks, or something in kind). */
	static final double DEAD = 1 * SILVER;
	/** A reply brought back adds this. */
	static final double REPLY = 1 * SILVER;
	/** A horse from a town's stable (horse culture), saddled. */
	static final double HORSE = 20 * SILVER;
	/** A guard hired for a day. */
	static final double GUARD_DAY = 7 * SILVER;
	/** A job worth this much or more is a big one (a prize besides: Rewards). */
	static final double BIG = 25 * SILVER;
	/** The least a notice pays. */
	static final double NOTICE_LEAST = 1 * SILVER;

	// Goods asked for.
	/** Over what a stall pays: a notice; a town short of something; at war. */
	static final double NOTICE = 1.5;
	static final double SHORT = 1.3;
	static final double WAR = 1.6;
	/**
	 * Never more than this share of a stall's listed price for one: the cheapest a stall ever sells it (well stocked, not
	 * selling, a town that makes it: 0.5 x 0.8 x 0.8 of the list). Found in review: at 0.9, a stocked stall's leather bought to
	 * hand in to a notice paid.
	 */
	static final double UNDER_SELLING = 0.32;
	/** Food a stall only buys sells at this many times what it pays (as Market). */
	private static final double FOOD_SELL_OF_BUY = 1.8;
	/** A stall's selling price by worth, for what the mod doesn't list (as Market: 1.6 its worth; food a tenth of that with coins). */
	private static final double SELLS_AT = 1.6;
	private static final double COIN_FOOD = 0.1;
	/** With coins, a thing with only one of its prices listed: the other from it. */
	private static final double BUY_OF_SELL = 0.4;
	private static final double SELL_OF_BUY = 3.0;
	/** With coins, a thing the mod doesn't list: how hard it is to get (points, vanilla's scale) times this (the mod's prices
	 * run about a quarter of vanilla's emerald trades at a thousand bronze an emerald). */
	private static final double UNLISTED = 0.25;

	/**
	 * How hard one is to come by, in points (vanilla's villagers: 20 wheat, 18 wool, 15 coal, 6 leather, 4 iron to an
	 * emerald...), whatever it would sell for.
	 */
	private static final Map<Item, Double> EFFORT = Map.ofEntries(Map.entry(Items.WHEAT, 0.3), Map.entry(Items.COBBLESTONE, 0.1), Map.entry(Items.DIRT, 0.05),
		Map.entry(Items.SAND, 0.08), Map.entry(Items.OAK_LOG, 0.15), Map.entry(Items.SPRUCE_LOG, 0.15), Map.entry(Items.BIRCH_LOG, 0.15),
		Map.entry(Items.GLASS, 0.35), Map.entry(Items.PAPER, 0.25), Map.entry(Items.BREAD, 0.5), Map.entry(Items.STONE_BRICKS, 0.35), Map.entry(Items.TORCH, 0.2),
		Map.entry(Items.WHITE_WOOL, 0.33), Map.entry(Items.STRING, 0.3), Map.entry(Items.COAL, 0.4), Map.entry(Items.FEATHER, 0.25), Map.entry(Items.BONE, 0.3),
		Map.entry(Items.GUNPOWDER, 0.5), Map.entry(Items.LEATHER, 1.0), Map.entry(Items.FLINT, 0.23), Map.entry(Items.IRON_INGOT, 1.5),
		Map.entry(Items.GOLD_INGOT, 2.0), Map.entry(Items.COPPER_INGOT, 0.3), Map.entry(Items.REDSTONE, 0.4), Map.entry(Items.LAPIS_LAZULI, 0.5),
		Map.entry(Items.DIAMOND, 6.0), Map.entry(Items.ENDER_PEARL, 4.0), Map.entry(Items.BLAZE_ROD, 3.0), Map.entry(Items.GHAST_TEAR, 6.0),
		Map.entry(Items.OBSIDIAN, 1.0), Map.entry(Items.SLIME_BALL, 1.0), Map.entry(Items.PHANTOM_MEMBRANE, 2.0), Map.entry(Items.EMERALD, 6.0));

	/** What a stall gives for one, in points (the mod's own price, else from how hard it is to get). */
	static double buys(Item item) {
		Double buy = DrifterApi.listedPoints(item, false);
		Double sell = DrifterApi.listedPoints(item, true);
		if (buy != null) {
			return buy;
		}

		if (sell != null) {
			return sell * BUY_OF_SELL;
		}

		return effort(item) * (DrifterApi.coins() ? UNLISTED : 1.0);
	}

	/** What a stall sells one for at its list price, in points: the mod's own, else from what it pays, else by its worth (as Market). */
	static double sells(Item item) {
		Double sell = DrifterApi.listedPoints(item, true);
		Double buy = DrifterApi.listedPoints(item, false);
		boolean food = new ItemStack(item).has(net.minecraft.core.component.DataComponents.FOOD);
		if (sell != null) {
			return sell;
		}

		if (buy != null) {
			return buy * (food ? FOOD_SELL_OF_BUY : SELL_OF_BUY);
		}

		// (Found in review: with no listed price there was no cap at all, so without the coin mod a notice paid many times a stall.)
		return DrifterApi.worth(new ItemStack(item)) * SELLS_AT * (DrifterApi.coins() && food ? COIN_FOOD : 1.0);
	}

	private static double effort(Item item) {
		Double known = EFFORT.get(item);
		return known != null ? known : Math.max(0.15, DrifterApi.worth(new ItemStack(item)));
	}

	/**
	 * What asking for {@code count} of this pays (points): over what a stall gives, {@code premium} times ({@code far} more
	 * for a journey), but never as much as a stall sells them for, and not less than {@code least}.
	 */
	static double goods(Item item, int count, double premium, double far, double least) {
		// (The least first, then the cap: found in review, the least after it made 8 wheat fill a silver's notice.)
		double pay = Math.max(least, buys(item) * premium * count);
		double sell = sells(item);
		// (A journey's more on top of the cap: the town it's for is short of them, so its stall hasn't them to buy.)
		return (sell > 0 ? Math.min(pay, sell * UNDER_SELLING * count) : pay) * far;
	}

	/** Carrying word this far (blocks), half for a stranger ({@code level} 0). */
	static double word(double far, int level) {
		double pay = Math.min(WORD_MOST, WORD + WORD_PER * Math.floor(far / WORD_EVERY));
		return level == 0 ? Math.max(SILVER, pay / 2) : pay;
	}

	/** Debug: `/drifterciv economy`, the scale's sums in the log and chat (to check it's balanced). */
	static void register() {
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
			net.minecraft.commands.Commands.literal("drifterciv").then(net.minecraft.commands.Commands.literal("economy").requires(source -> source.hasPermission(2))
				.executes(ctx -> {
					java.util.List<String> lines = new java.util.ArrayList<>();
					for (Item item : java.util.List.of(Items.COBBLESTONE, Items.OAK_LOG, Items.WHEAT, Items.WHITE_WOOL, Items.GLASS, Items.PAPER, Items.LEATHER,
						Items.IRON_INGOT, Items.BREAD, Items.DIAMOND)) {
						lines.add(new ItemStack(item).getHoverName().getString() + ": stall gives " + say(buys(item)) + ", sells " + say(sells(item)) + "; a notice for 32: "
							+ say(goods(item, 32, NOTICE, 1.0, NOTICE_LEAST)) + " (far: " + say(goods(item, 32, NOTICE, 1.8, NOTICE_LEAST)) + ")");
					}

					lines.add("word: 300 blocks " + say(word(300, 1)) + ", 900 " + say(word(900, 1)) + ", 1500 " + say(word(1500, 1)) + ", a stranger 900 " + say(word(900, 0)));
					lines.add("find " + say(FIND[1]) + ", deal " + say(DEAL[1]) + " (a friend " + say(DEAL[2]) + "), a guard a day " + say(GUARD_DAY) + ", big from " + say(BIG));
					lines.forEach(line -> DrifterCivilizations.LOGGER.info("[economy] {}", line));
					ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(String.join("\n", lines)), false);
					return 1;
				}))));
	}

	/** A sum in words ("35 silver", "1 gold and 5 silver"; without coins, emeralds). */
	static String say(double points) {
		return DrifterApi.moneyWordsPoints(points);
	}
}
