package com.dinglebob.drifter.api;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A Drifter keeping a shop (a town's market stall, milestone 22 step 4; {@link DrifterApi#setShop}): where it keeps it,
 * what it stocks up on, and what it would trade with a player who right-clicks it (the villager trading screen). Every
 * trade is of real things: what it gives comes out of its pockets, what it's paid goes into them (nothing appears from
 * nowhere, as with a villager), so it only offers what it has, and buys only with money it has.
 */
public interface ShopKeeper {
	/** Where it stands to keep shop (behind the counter), or null if it has nowhere to. */
	@Nullable
	BlockPos stand(DrifterEntity keeper);

	/**
	 * Its open hours: it's at its stand then (a town's stall: morning to mid-afternoon; a smith at its door, mornings only,
	 * so it has the rest of the day to make what it sells).
	 */
	default boolean open(DrifterEntity keeper) {
		return com.dinglebob.drifter.trade.StallGoal.open(keeper);
	}

	/** What it would trade with this player now: from what it carries. */
	List<ShopOffer> offers(DrifterEntity keeper, Player player);

	/** What to take from its home's chests (the town's store) to sell before it opens up: item and how many. */
	default Map<Item, Integer> stock(DrifterEntity keeper) {
		return Map.of();
	}

	/** A trade was made. */
	default void traded(DrifterEntity keeper, Player player, ShopOffer offer) {
	}

	/** It sold {@code times} of this offer to another Drifter at its counter (step 4: Drifters buy with their wages). */
	default void soldTo(DrifterEntity keeper, DrifterEntity buyer, ShopOffer offer, int times) {
	}

	/** Where it puts its takings (the town's till, a chest), or null: it keeps them. */
	@Nullable
	default BlockPos till(DrifterEntity keeper) {
		return null;
	}

	/** How much money it keeps on hand to buy with (emeralds' worth); the rest goes to the till. */
	default int keepOnHand() {
		return 8;
	}

	/**
	 * One trade: the player gives {@code pay} (and {@code payB}, or empty) for {@code goods}, up to {@code times} times.
	 */
	record ShopOffer(ItemStack pay, ItemStack payB, ItemStack goods, int times) {
	}
}
