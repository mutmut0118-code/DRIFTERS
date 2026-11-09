package com.dinglebob.drifter.command;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.gear.GearLore;
import com.dinglebob.drifter.gear.RecipeIndex;
import com.dinglebob.drifter.gear.GearKinds;
import com.dinglebob.drifter.gear.GearValue;
import com.dinglebob.drifter.social.ItemValues;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Gear commands (any weapon, armor and trinket project): {@code /drifter gear item <item>} says how Drifter reads one
 * item (its kind, melee, ranged and armor value, rarity, cost and trade points), for checking modded gear.
 */
public final class GearCommands {
	private GearCommands() {
	}

	public static void register(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {
		root.then(Commands.literal("gear")
			.then(Commands.literal("item")
				.then(Commands.argument("item", ItemArgument.item(context)).executes(GearCommands::item)))
			.then(Commands.literal("learn")
				.then(Commands.argument("item", ItemArgument.item(context)).executes(GearCommands::learn)))
			.then(Commands.literal("forget").executes(GearCommands::forget))
			.executes(GearCommands::list));
	}

	private static int item(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ItemStack stack = ItemArgument.getItem(ctx, "item").createItemStack(1, false);
		CommandSourceStack source = ctx.getSource();
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		GearKinds.Kind kind = GearKinds.of(stack);
		EquipmentSlot slot = GearValue.armorSlot(stack);
		EquipmentSlot armorSlot = slot != null ? slot : GearValue.slotOf(stack);
		double melee;
		try {
			DrifterEntity drifter = DrifterCommands.nearest(source);
			melee = GearValue.melee(drifter, stack);
		} catch (CommandSyntaxException e) {
			melee = GearValue.dps(stack);
		}

		double armor = armorSlot == null ? 0.0 : GearValue.armor(stack, armorSlot);
		String line = String.format(Locale.ROOT, "[gear] %s: kind %s (%s), melee %.1f, ranged %.1f, armor %.1f (%s), rarity %d, cost %.1f, points %.1f",
			id, kind == null ? "none" : kind.id(), kind == null ? "none" : kind.use().name(), melee, GearValue.ranged(stack), armor,
			slot == null ? "none" : slot.getName(), GearValue.rarity(stack), GearValue.cost(source.getLevel(), stack.getItem()), ItemValues.each(stack))
			+ ", key " + describeKey(source, stack) + knowsPart(source, stack);
		source.sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	private static String describeKey(CommandSourceStack source, ItemStack stack) {
		Ingredient key = RecipeIndex.key(source.getLevel(), stack.getItem());
		if (key == null) {
			return "none";
		}

		ItemStack first = RecipeIndex.first(key);
		return first.isEmpty() ? "none" : BuiltInRegistries.ITEM.getKey(first.getItem()).toString();
	}

	private static String knowsPart(CommandSourceStack source, ItemStack stack) {
		try {
			DrifterEntity drifter = DrifterCommands.nearest(source);
			GearLore.How how = drifter.getGearLore().how(drifter, stack.getItem());
			String name = drifter.getName().getString();
			return "; " + name + " knows it: " + (how == null ? "no" : "yes (" + how.id() + ")");
		} catch (CommandSyntaxException e) {
			return "";
		}
	}

	private static int learn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		ItemStack stack = ItemArgument.getItem(ctx, "item").createItemStack(1, false);
		boolean isNew = drifter.getGearLore().learn(drifter, stack.getItem(), GearLore.How.TOLD, "told");
		String line = drifter.getName().getString() + (isNew ? " learned of " : " already knew ") + GearLore.nameOf(stack.getItem());
		ctx.getSource().sendSuccess(() -> Component.literal("[gear] " + line), false);
		return 1;
	}

	private static int forget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		drifter.getGearLore().forget();
		ctx.getSource().sendSuccess(() -> Component.literal("[gear] " + drifter.getName().getString() + " forgot what it learned of modded items"), false);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		List<Item> items = drifter.getGearLore().learnedItems();
		StringBuilder ids = new StringBuilder();
		for (int i = 0; i < Math.min(12, items.size()); i++) {
			ids.append(i == 0 ? "" : ", ").append(BuiltInRegistries.ITEM.getKey(items.get(i)));
		}

		String line = "[gear] " + drifter.getName().getString() + ": knows " + items.size() + " modded items (" + ids + "); know every recipe "
			+ (DrifterConfig.knowEveryRecipe() ? "on" : "off");
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}
}
