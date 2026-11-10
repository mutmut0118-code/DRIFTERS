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

	/** It has a shield: held in its off hand or carried. */
	public static boolean has(DrifterEntity drifter) {
		return isShield(drifter.getOffhandItem()) || bestSlot(drifter) >= 0;
	}

	/**
	 * Shields made without iron, if a mod adds them (Spartan Shields): a crude wooden shield (four planks and a stick), and
	 * a stone braced one (that and four cobblestone). Each list is empty without the mod; looked up when asked (mods add
	 * their items after this may first be read).
	 */
	public static final java.util.List<net.minecraft.world.item.Item> CRUDE_SHIELD = modItems("spartanshields:wooden_basic_shield");
	public static final java.util.List<net.minecraft.world.item.Item> STONE_SHIELD = modItems("spartanshields:stone_basic_shield");

	private static java.util.List<net.minecraft.world.item.Item> modItems(String... ids) {
		return new java.util.AbstractList<>() {
			private java.util.List<net.minecraft.world.item.Item> found() {
				java.util.List<net.minecraft.world.item.Item> items = new java.util.ArrayList<>();
				for (String id : ids) {
					net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.parse(id)).ifPresent(items::add);
				}

				return items;
			}

			@Override
			public net.minecraft.world.item.Item get(int index) {
				return this.found().get(index);
			}

			@Override
			public int size() {
				return this.found().size();
			}
		};
	}

	/** A shield past the crude wooden one (stone braced, the game's own, any metal one). */
	public static boolean hasBetterThanCrude(DrifterEntity drifter) {
		java.util.List<net.minecraft.world.item.Item> crude = CRUDE_SHIELD;
		return com.dinglebob.drifter.brain.Strategist.has(drifter, stack -> isShield(stack) && !crude.contains(stack.getItem()));
	}

	/** A shield at least as strong as the game's own (iron age: it lasts as long or longer). */
	public static boolean hasIronAge(DrifterEntity drifter) {
		int vanilla = Items.SHIELD.getDefaultInstance().getMaxDamage();
		return com.dinglebob.drifter.brain.Strategist.has(drifter, stack -> isShield(stack) && stack.getMaxDamage() >= vanilla);
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

	/** A bow or a crossbow, any mod's (what it can shoot arrows from: the shooting task uses either). */
	public static boolean isLauncher(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof BowItem || stack.getItem() instanceof net.minecraft.world.item.CrossbowItem);
	}

	/** How much this Drifter likes a bow or crossbow: its value (enchantments, rarity, own release, wear) with its taste in it. */
	public static double bowScore(DrifterEntity drifter, ItemStack stack) {
		return com.dinglebob.drifter.gear.GearTaste.liking(drifter, stack, com.dinglebob.drifter.gear.GearValue.ranged(stack));
	}

	/** Its best bow or crossbow to shoot with now in the main inventory, or -1. */
	public static int bestBowSlot(DrifterEntity drifter) {
		return bestBowSlot(drifter, null);
	}

	/**
	 * The same, against this target (null: none in mind): how well each launcher's powers suit it, as it believes, multiplies
	 * its score ({@code Powers.fit}: an explosive bow loses to a plain one up close or near friends). Only ones it could
	 * shoot now (Archery#canShoot: with no arrows, only a loaded crossbow; never one it gave up on).
	 */
	public static int bestBowSlot(DrifterEntity drifter, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity target) {
		DrifterInventory inventory = drifter.getInventory();
		boolean ammo = !Archery.ammo(drifter).isEmpty();
		int best = -1;
		double bestScore = 0.0;
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (Archery.canShoot(drifter, stack, ammo)) {
				double score = bowScore(drifter, stack);
				// (A score below zero would be raised by a penalty: one is scaled the other way.)
				double fit = com.dinglebob.drifter.gear.Powers.fit(drifter, stack, target);
				score = score >= 0.0 ? score * fit : score * (2.0 - fit);
				if (best < 0 || score > bestScore) {
					best = slot;
					bestScore = score;
				}
			}
		}

		return best;
	}
}
