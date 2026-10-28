package com.dinglebob.drifter.trade;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.ShopKeeper;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * A Drifter's shop as the villager trading screen (milestone 22 step 4): the offers its {@link ShopKeeper} makes from
 * what it carries. The screen hands the player a copy of what was bought and takes the payment away, so each trade here
 * moves the real things: the goods out of the Drifter's pockets, the payment into them (no free items, DESIGN.md
 * section 1). It can offer only what it has: each offer's uses are how many times it can.
 */
public final class DrifterMerchant implements Merchant {
	private final DrifterEntity keeper;
	private final ShopKeeper shop;
	private final MerchantOffers offers = new MerchantOffers();
	private final Map<MerchantOffer, ShopKeeper.ShopOffer> made = new IdentityHashMap<>();
	@Nullable
	private Player trading;

	private DrifterMerchant(DrifterEntity keeper, ShopKeeper shop) {
		this.keeper = keeper;
		this.shop = shop;
	}

	/** The trading screen for this player, if it has anything to trade. False if not (nothing on offer). */
	public static boolean open(DrifterEntity keeper, ShopKeeper shop, Player player) {
		DrifterMerchant merchant = new DrifterMerchant(keeper, shop);
		List<ShopKeeper.ShopOffer> offers = shop.offers(keeper, player);
		for (ShopKeeper.ShopOffer offer : offers) {
			if (offer.pay().isEmpty() || offer.goods().isEmpty() || offer.times() <= 0) {
				continue;
			}

			MerchantOffer merchantOffer = new MerchantOffer(cost(offer.pay()), offer.payB().isEmpty() ? Optional.empty() : Optional.of(cost(offer.payB())),
				offer.goods().copy(), offer.times(), 0, 0.0F);
			merchant.offers.add(merchantOffer);
			merchant.made.put(merchantOffer, offer);
		}

		if (merchant.offers.isEmpty()) {
			return false;
		}

		merchant.setTradingPlayer(player);
		merchant.openTradingScreen(player, keeper.getDisplayName(), 1);
		return true;
	}

	private static ItemCost cost(ItemStack stack) {
		return new ItemCost(stack.getItem(), stack.getCount());
	}

	@Override
	public void setTradingPlayer(@Nullable Player player) {
		this.trading = player;
	}

	@Nullable
	@Override
	public Player getTradingPlayer() {
		return this.trading;
	}

	@Override
	public MerchantOffers getOffers() {
		return this.offers;
	}

	@Override
	public void overrideOffers(MerchantOffers offers) {
	}

	@Override
	public void notifyTrade(MerchantOffer offer) {
		offer.increaseUses();
		ShopKeeper.ShopOffer shopOffer = this.made.get(offer);
		if (shopOffer == null) {
			return;
		}

		// The goods out of its pockets (the screen gave the player a copy), the payment into them (the screen took it).
		take(this.keeper.getInventory(), shopOffer.goods());
		give(this.keeper, shopOffer.pay());
		give(this.keeper, shopOffer.payB());
		this.keeper.getMemory().thoughts().add(this.keeper.level().getGameTime(), "sold " + describe(shopOffer.goods()) + " to "
			+ (this.trading != null ? this.trading.getGameProfile().getName() : "someone") + " for " + describe(shopOffer.pay())
			+ (shopOffer.payB().isEmpty() ? "" : " and " + describe(shopOffer.payB())));
		if (this.trading != null) {
			this.shop.traded(this.keeper, this.trading, shopOffer);
		}
	}

	private static String describe(ItemStack stack) {
		return stack.getCount() + " " + stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
	}

	/** Takes this many of this item out of its pockets (as far as it has them). */
	static void take(DrifterInventory inventory, ItemStack what) {
		int left = what.getCount();
		for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (ItemStack.isSameItemSameComponents(stack, what)) {
				int taken = Math.min(left, stack.getCount());
				inventory.removeItem(slot, taken);
				left -= taken;
			}
		}
	}

	/** Into its pockets, or at its feet if they're full. */
	static void give(DrifterEntity keeper, ItemStack what) {
		if (what.isEmpty()) {
			return;
		}

		ItemStack stack = what.copy();
		if (!keeper.getInventory().add(stack) && !stack.isEmpty()) {
			keeper.spawnAtLocation(stack);
		}
	}

	@Override
	public void notifyTradeUpdated(ItemStack stack) {
	}

	@Override
	public int getVillagerXp() {
		return 0;
	}

	@Override
	public void overrideXp(int xp) {
	}

	@Override
	public boolean showProgressBar() {
		return false;
	}

	@Override
	public SoundEvent getNotifyTradeSound() {
		return SoundEvents.VILLAGER_YES;
	}

	@Override
	public boolean isClientSide() {
		return this.keeper.level().isClientSide();
	}

	/** Its name and line for a player who right-clicked with nothing on offer. */
	public static Component nothing(DrifterEntity keeper) {
		String line = com.dinglebob.drifter.social.Lines.pick(keeper, "shop.nothing");
		return Component.literal("<" + keeper.getName().getString() + "> " + (line != null ? line : "nothing to sell right now, come back later"));
	}
}
