package com.dinglebob.drifter.command;

import java.util.Locale;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.dinglebob.drifter.entity.DrifterEntity;
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
import net.minecraft.world.item.ItemStack;

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
				.then(Commands.argument("item", ItemArgument.item(context)).executes(GearCommands::item))));
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
			slot == null ? "none" : slot.getName(), GearValue.rarity(stack), GearValue.cost(source.getLevel(), stack.getItem()), ItemValues.each(stack));
		source.sendSuccess(() -> Component.literal(line), false);
		return 1;
	}
}
