package com.dinglebob.drifter.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.gear.GearLore;
import com.dinglebob.drifter.gear.RecipeIndex;
import com.dinglebob.drifter.gear.GearKinds;
import com.dinglebob.drifter.gear.GearTaste;
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
			.then(Commands.literal("style")
				.then(Commands.argument("style", StringArgumentType.word())
					.suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("practical", "flashy", "loyal", "follower", "clear"), builder))
					.executes(GearCommands::style)))
			.then(Commands.literal("friend")
				.then(Commands.argument("kind", StringArgumentType.word()).executes(GearCommands::friend)))
			.then(Commands.literal("plans").executes(GearCommands::plans))
			.then(Commands.literal("pick")
				.then(Commands.literal("weapon").executes(ctx -> pick(ctx, true)))
				.then(Commands.literal("armor").executes(ctx -> pick(ctx, false))))
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
		com.dinglebob.drifter.brain.GearPlans.refresh(drifter);
		String line = drifter.getName().getString() + (isNew ? " learned of " : " already knew ") + GearLore.nameOf(stack.getItem());
		ctx.getSource().sendSuccess(() -> Component.literal("[gear] " + line), false);
		return 1;
	}

	private static int forget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		drifter.getGearLore().forget();
		com.dinglebob.drifter.brain.GearPlans.refresh(drifter);
		ctx.getSource().sendSuccess(() -> Component.literal("[gear] " + drifter.getName().getString() + " forgot what it learned of modded items"), false);
		return 1;
	}

	/** Debug: pretend it has seen friends with this kind of weapon (or armor set key) five times. */
	private static int friend(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		String kind = StringArgumentType.getString(ctx, "kind");
		for (int i = 0; i < 5; i++) {
			drifter.getGearLore().noteFriendGear(kind);
		}

		String line = String.format(Locale.ROOT, "[gear] %s noted friends with %s (share %.2f)", drifter.getName().getString(), kind, drifter.getGearLore().friendShare(kind));
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	/** Debug: pretend a style (not saved); "clear" returns to the seeded draw. */
	private static int style(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		String word = StringArgumentType.getString(ctx, "style").toLowerCase(Locale.ROOT);
		GearTaste.Style style = GearTaste.Style.byId(word);
		if (style == null && !word.equals("clear")) {
			ctx.getSource().sendFailure(Component.literal("[gear] style is practical, flashy, loyal, follower or clear"));
			return 0;
		}

		drifter.getGearLore().setStyleOverride(style);
		com.dinglebob.drifter.brain.GearPlans.refresh(drifter);
		String line = "[gear] " + drifter.getName().getString() + " style " + GearTaste.style(drifter).id() + (style == null ? " (seeded draw)" : " (debug override)");
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	/** Debug: what it works toward at iron and diamond (GearPlans): the weapon and each armor slot. */
	private static int plans(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		CommandSourceStack source = ctx.getSource();
		String name = drifter.getName().getString();
		for (com.dinglebob.drifter.brain.Weapons.Tier tier : List.of(com.dinglebob.drifter.brain.Weapons.Tier.IRON, com.dinglebob.drifter.brain.Weapons.Tier.DIAMOND)) {
			com.dinglebob.drifter.brain.Strategist.Target weapon = com.dinglebob.drifter.brain.GearPlans.weaponTarget(drifter, tier);
			String line = "[gear] plan weapon at " + tier.id() + ": " + (weapon == null ? "none" : BuiltInRegistries.ITEM.getKey(weapon.items().get(0)))
				+ " (" + name + ", style " + GearTaste.style(drifter).id() + ")";
			source.sendSuccess(() -> Component.literal(line), false);
			List<com.dinglebob.drifter.brain.Strategist.Target> armor = com.dinglebob.drifter.brain.GearPlans.armorTargets(drifter, tier);
			for (int i = 0; i < armor.size(); i++) {
				String part = "[gear] plan " + com.dinglebob.drifter.brain.GearPlans.SLOTS[i].getName() + " at " + tier.id() + ": "
					+ BuiltInRegistries.ITEM.getKey(armor.get(i).items().get(0));
				source.sendSuccess(() -> Component.literal(part), false);
			}
		}

		return 1;
	}

	/** Debug: what it would choose among what it carries, with each candidate's value and liking. */
	private static int pick(CommandContext<CommandSourceStack> ctx, boolean weapon) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		CommandSourceStack source = ctx.getSource();
		if (weapon) {
			List<ItemStack> options = new ArrayList<>();
			for (int slot = 0; slot < drifter.getInventory().getContainerSize(); slot++) {
				ItemStack stack = drifter.getInventory().getItem(slot);
				GearKinds.Kind kind = GearKinds.of(stack);
				if (kind != null && kind.use() == GearKinds.Use.MELEE) {
					options.add(stack);
				}
			}

			report(source, drifter, "weapon", options, null);
		} else {
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				List<ItemStack> options = new ArrayList<>();
				for (int i = 0; i < drifter.getInventory().getContainerSize(); i++) {
					ItemStack stack = drifter.getInventory().getItem(i);
					if (GearValue.wornSlot(drifter, stack) == slot) {
						options.add(stack);
					}
				}

				ItemStack worn = drifter.getItemBySlot(slot);
				if (!worn.isEmpty() && GearValue.armor(worn, slot) > 0.0) {
					options.add(worn);
				}

				report(source, drifter, slot.getName(), options, slot);
			}
		}

		return 1;
	}

	private static void report(CommandSourceStack source, DrifterEntity drifter, String what, List<ItemStack> options, EquipmentSlot slot) {
		java.util.function.ToDoubleFunction<ItemStack> value = stack -> slot == null ? GearValue.melee(drifter, stack) : GearValue.armor(stack, slot);
		for (ItemStack stack : options) {
			double v = value.applyAsDouble(stack);
			String line = String.format(Locale.ROOT, "[gear] pick %s value %.2f liking %.2f", BuiltInRegistries.ITEM.getKey(stack.getItem()), v, GearTaste.liking(drifter, stack, v));
			source.sendSuccess(() -> Component.literal(line), false);
		}

		ItemStack chosen = GearTaste.pick(drifter, options, value, stack -> stack);
		String line = "[gear] picked " + (chosen == null ? "none" : BuiltInRegistries.ITEM.getKey(chosen.getItem()).toString()) + " for " + what
			+ " (" + drifter.getName().getString() + ", style " + GearTaste.style(drifter).id() + ")";
		source.sendSuccess(() -> Component.literal(line), false);
	}

	private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		List<Item> items = drifter.getGearLore().learnedItems();
		StringBuilder ids = new StringBuilder();
		for (int i = 0; i < Math.min(12, items.size()); i++) {
			ids.append(i == 0 ? "" : ", ").append(BuiltInRegistries.ITEM.getKey(items.get(i)));
		}

		String line = "[gear] " + drifter.getName().getString() + ": knows " + items.size() + " modded items (" + ids + "); know every recipe "
			+ (DrifterConfig.knowEveryRecipe() ? "on" : "off")
			+ "; style " + GearTaste.style(drifter).id() + " (loyal to " + (drifter.getGearLore().loyalKind() == null ? "none" : drifter.getGearLore().loyalKind()) + ")";
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}
}
