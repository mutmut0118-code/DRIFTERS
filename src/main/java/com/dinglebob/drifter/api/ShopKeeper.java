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

	/** What it would trade with this player now: from what it carries. */
	List<ShopOffer> offers(DrifterEntity keeper, Player player);

	/** What to take from its home's chests (the town's store) to sell before it opens up: item and how many. */
	default Map<Item, Integer> stock(DrifterEntity keeper) {
		return Map.of();
	}

	/** A trade was made. */
	default void traded(DrifterEntity keeper, Player player, ShopOffer offer) {
	}

	/**
	 * One trade: the player gives {@code pay} (and {@code payB}, or empty) for {@code goods}, up to {@code times} times.
	 */
	record ShopOffer(ItemStack pay, ItemStack payB, ItemStack goods, int times) {
	}
}
