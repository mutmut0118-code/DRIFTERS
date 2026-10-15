package com.dinglebob.drifter.social;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;

/**
 * A simple item value table (DESIGN.md section 14: gifts "by value", barter "about equally", gear for the strength
 * estimate). Rough player-economy worth in "points": an iron ingot is 2, a diamond 10. Kept in one place so it can move to
 * a data file (section 25).
 */
public final class ItemValues {
	private ItemValues() {
	}

	/** Points for one of this item. */
	public static double each(ItemStack stack) {
		Item item = stack.getItem();
		double value;
		if (item == Items.NETHERITE_INGOT || item == Items.NETHERITE_SCRAP) {
			value = 40.0;
		} else if (item == Items.DIAMOND || item == Items.DIAMOND_BLOCK) {
			value = item == Items.DIAMOND_BLOCK ? 90.0 : 10.0;
		} else if (item == Items.EMERALD) {
			value = 6.0;
		} else if (item == Items.GOLD_INGOT || item == Items.RAW_GOLD) {
			value = 3.0;
		} else if (item == Items.IRON_INGOT || item == Items.RAW_IRON) {
			value = 2.0;
		} else if (item == Items.ENDER_PEARL || item == Items.GOLDEN_APPLE) {
			value = 8.0;
		} else if (item == Items.LAPIS_LAZULI || item == Items.REDSTONE || item == Items.COAL || item == Items.COPPER_INGOT) {
			value = 0.5;
		} else if (item instanceof ArmorItem armor) {
			value = armorTier(armor) * 4.0;
		} else if (item instanceof TieredItem tiered) {
			value = toolTier(tiered) * 3.0;
		} else if (stack.has(DataComponents.FOOD)) {
			value = stack.get(DataComponents.FOOD).nutrition() * 0.25;
		} else if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS)) {
			value = 0.1;
		} else {
			value = 0.05;
		}

		if (stack.isEnchanted()) {
			value += 10.0;
		}

		return value;
	}

	/** Points for the whole stack. */
	public static double of(ItemStack stack) {
		return each(stack) * stack.getCount();
	}

	/** Worth a grudge if taken from its chest (section 14: "valuables"). */
	public static boolean valuable(ItemStack stack) {
		return each(stack) >= 2.0;
	}

	/** 0 (none) to 5 (netherite) for a piece of armor. */
	public static double armorTier(ArmorItem armor) {
		var material = armor.getMaterial();
		if (material.is(ArmorMaterials.NETHERITE)) {
			return 5.0;
		} else if (material.is(ArmorMaterials.DIAMOND)) {
			return 4.0;
		} else if (material.is(ArmorMaterials.IRON)) {
			return 3.0;
		} else if (material.is(ArmorMaterials.CHAIN) || material.is(ArmorMaterials.TURTLE)) {
			return 2.0;
		} else if (material.is(ArmorMaterials.GOLD)) {
			return 1.5;
		}

		return 1.0;
	}

	/** 0 (wood) to 5 (netherite) for a tool or weapon. */
	public static double toolTier(TieredItem tool) {
		var tier = tool.getTier();
		if (tier == Tiers.NETHERITE) {
			return 5.0;
		} else if (tier == Tiers.DIAMOND) {
			return 4.0;
		} else if (tier == Tiers.IRON) {
			return 3.0;
		} else if (tier == Tiers.STONE) {
			return 2.0;
		} else if (tier == Tiers.GOLD) {
			return 1.5;
		}

		return 1.0;
	}
}
