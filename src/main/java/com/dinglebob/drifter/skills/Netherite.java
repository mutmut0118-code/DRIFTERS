package com.dinglebob.drifter.skills;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.dinglebob.drifter.brain.Weapons;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

/**
 * Netherite (DESIGN.md section 19 "Netherite"): ancient debris from deep in the Nether, smelted to scrap, four scrap and
 * four gold to an ingot, and at a smithing table an upgrade template, a diamond piece and an ingot make the netherite
 * piece (the enchantments kept). Upgrade order: the weapon, the pickaxe, the chestplate, then the rest. The first template
 * only comes from a bastion's chests; seven diamonds and a netherrack make a second from one.
 */
public final class Netherite {
	public static final Item TEMPLATE = Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
	/** Ancient debris is most common around here (8 to 22). */
	public static final int DEBRIS_Y = 15;
	/** Netherite it goes after at a time: enough for this many upgrades. */
	private static final int UPGRADES_AHEAD = 2;

	private Netherite() {
	}

	/** The smithing recipe that makes this into netherite (the game's own, modded diamond weapons too), if there is one. */
	public static Optional<RecipeHolder<SmithingRecipe>> recipe(Level level, ItemStack base) {
		if (base.isEmpty()) {
			return Optional.empty();
		}

		SmithingRecipeInput input = new SmithingRecipeInput(new ItemStack(TEMPLATE), base, new ItemStack(Items.NETHERITE_INGOT));
		return level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, level);
	}

	/** Its gear that has a netherite version, as inventory slots (worn armour included), in the order it upgrades them. */
	public static List<Integer> upgradeable(DrifterEntity drifter) {
		List<Integer> slots = new ArrayList<>();
		var inventory = drifter.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && !stack.is(ItemTags.HOES) && recipe(drifter.level(), stack).isPresent()) {
				slots.add(slot);
			}
		}

		slots.sort(Comparator.comparingInt(slot -> rank(inventory.getItem(slot))));
		return slots;
	}

	/** Weapon, pickaxe, chestplate, leggings, helmet, boots, axe, shovel (section 19). */
	private static int rank(ItemStack stack) {
		if (stack.getItem() instanceof ArmorItem armor) {
			return switch (armor.getType()) {
				case CHESTPLATE -> 2;
				case LEGGINGS -> 3;
				case HELMET -> 4;
				default -> 5;
			};
		}

		if (stack.is(ItemTags.PICKAXES)) {
			return 1;
		}

		if (stack.is(ItemTags.AXES)) {
			return 6;
		}

		return Weapons.isWeapon(stack) ? 0 : 7;
	}

	private static int count(DrifterEntity drifter, Item item) {
		return drifter.getInventory().count(stack -> stack.is(item));
	}

	/** Templates it has, and the one it could make from a template (seven diamonds and a netherrack). */
	public static int templates(DrifterEntity drifter) {
		int templates = count(drifter, TEMPLATE);
		return templates == 0 ? 0 : templates + (count(drifter, Items.DIAMOND) >= 7 && count(drifter, Items.NETHERRACK) > 0 ? 1 : 0);
	}

	/** Netherite it has toward upgrades, in scrap's worth (an ingot is four). */
	public static int scrapWorth(DrifterEntity drifter) {
		return 4 * count(drifter, Items.NETHERITE_INGOT) + count(drifter, Items.NETHERITE_SCRAP) + count(drifter, Items.ANCIENT_DEBRIS);
	}

	/** Upgrades it could do now or soon: gear with a netherite version, as many as it has templates for (two at most ahead). */
	public static int upgradesWanted(DrifterEntity drifter) {
		return Math.min(UPGRADES_AHEAD, Math.min(upgradeable(drifter).size(), templates(drifter)));
	}

	/** Ancient debris it still wants to find (none without a template: netherite is no use without one). */
	public static int debrisWanted(DrifterEntity drifter) {
		return Math.max(0, 4 * upgradesWanted(drifter) - scrapWorth(drifter));
	}

	/** It knows to dig for debris: a diamond (or netherite) pickaxe on it. */
	public static boolean canDig(DrifterEntity drifter) {
		return drifter.getInventory().count(stack -> stack.is(Items.DIAMOND_PICKAXE) || stack.is(Items.NETHERITE_PICKAXE)) > 0;
	}
}
