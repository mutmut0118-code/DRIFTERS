package com.dinglebob.drifter.hands;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;

import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;

/**
 * Shields and bows of any kind (milestone 19, mod compat): Spartan Shields' metal and tower shields are shields, Simply
 * Bows' unique bows are bows, as the game treats them (they're its own shield and bow items underneath). Which of them is
 * the better one to hold.
 */
public final class Shields {
	private Shields() {
	}

	public static boolean isShield(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof ShieldItem;
	}

	/** How good a shield is: the wear it has left (the metal ones last far longer), enchanted ones a bit better. */
	public static double score(ItemStack stack) {
		double left = stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Items.SHIELD.getDefaultInstance().getMaxDamage();
		return left + (stack.isEnchanted() ? 200.0 : 0.0);
	}

	/** Its best shield in the main inventory, or -1. */
	public static int bestSlot(DrifterEntity drifter) {
		DrifterInventory inventory = drifter.getInventory();
		int best = -1;
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (isShield(stack) && (best < 0 || score(stack) > score(inventory.getItem(best)))) {
				best = slot;
			}
		}

		return best;
	}

	public static boolean isBow(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof BowItem;
	}

	/** How good a bow is: a modded unique bow over a plain one, enchantments, then the wear left. */
	public static double bowScore(ItemStack stack) {
		double score = stack.getItem().getClass() != BowItem.class ? 1000.0 : 0.0;
		score += 100.0 * stack.getEnchantments().size();
		return score + (stack.isDamageableItem() ? 1.0 - stack.getDamageValue() / (double)stack.getMaxDamage() : 1.0);
	}

	/** Its best bow in the main inventory, or -1. */
	public static int bestBowSlot(DrifterEntity drifter) {
		DrifterInventory inventory = drifter.getInventory();
		int best = -1;
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (isBow(stack) && (best < 0 || bowScore(stack) > bowScore(inventory.getItem(best)))) {
				best = slot;
			}
		}

		return best;
	}
}
