package com.dinglebob.drifter.trade;

import com.dinglebob.drifter.api.ShopKeeper;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.world.item.ItemStack;

/**
 * A trade across a stall's counter between a Drifter and the keeper (milestone 22 step 4), as the trading screen does it for a
 * player: the customer's side ({@code offer.pay()}) out of its pockets into the keeper's, the keeper's side ({@code
 * offer.goods()}) out of the keeper's into the customer's; each time only while both still have their side.
 */
final class Counter {
	private Counter() {
	}

	/** Up to {@code times} of this offer; how many were made. */
	static int trade(DrifterEntity customer, DrifterEntity keeper, ShopKeeper.ShopOffer offer, int times) {
		int made = 0;
		ItemStack pay = offer.pay();
		ItemStack goods = offer.goods();
		for (int i = 0; i < times; i++) {
			if (customer.getInventory().count(stack -> ItemStack.isSameItemSameComponents(stack, pay)) < pay.getCount()
				|| keeper.getInventory().count(stack -> ItemStack.isSameItemSameComponents(stack, goods)) < goods.getCount()) {
				break;
			}

			DrifterMerchant.take(customer.getInventory(), pay);
			DrifterMerchant.give(keeper, pay.copy());
			DrifterMerchant.take(keeper.getInventory(), goods);
			DrifterMerchant.give(customer, goods.copy());
			made++;
		}

		if (made > 0 && keeper.getShop() != null) {
			keeper.getShop().soldTo(keeper, customer, offer, made);
		}

		return made;
	}

	static String describe(ItemStack stack, int times) {
		return stack.getCount() * times + " " + stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
	}
}
