package com.dinglebob.drifter.api;

import java.util.Map;
import java.util.Set;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

/**
 * A trade run to another town (milestone 22 step 4; the user, 2026-10-28: "A trader walks"), set by Drifter:
 * Civilizations with {@link DrifterApi#setTradeRun}: it packs {@code cargo} from its home's chests (and a little of the
 * town's money from its till for buying), walks to {@code there} (the other town's stall), sells what that stall's keeper
 * buys and buys what its own town is short of ({@code wanted}), walks back to {@code home} and puts it all away, the money in
 * the till. Every item real, every step walked. Not saved: a run under way when the world closes is over.
 */
public interface TradeRun {
	/** The other town's stall (where its keeper stands), and its name. */
	BlockPos there();

	String thereName();

	/** Home: its own town's middle, and its till (or null). */
	BlockPos home();

	@org.jetbrains.annotations.Nullable
	BlockPos till();

	/** What it takes to sell (item and how many at most). */
	Map<Item, Integer> cargo();

	/** What its own town is short of (it buys these there). */
	Set<Item> wanted();

	/** What it saw the other stall buy and sell (the town remembers, and sends what sold next time). */
	default void saw(DrifterEntity trader, Set<Item> theyBuy, Set<Item> theySell) {
	}

	/** How it's going ("set out", "traded: ...", "back home", "gave up: ..."), for the town's chronicle. */
	default void news(DrifterEntity trader, String what) {
	}
}
