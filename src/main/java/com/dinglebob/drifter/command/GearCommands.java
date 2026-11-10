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
import com.dinglebob.drifter.gear.Powers;
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
 * item (its kind, melee, ranged and armor value, rarity, cost and trade points), for checking modded gear; with the nearest
 * Drifter's power beliefs about it and what the item's text hints at; {@code /drifter gear powers} reads every gear
 * item's text at once (how many hint at a power, how many could not be read).
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
			.then(Commands.literal("powers").executes(GearCommands::powers))
			.then(Commands.literal("sense")
				.then(Commands.argument("item", ItemArgument.item(context)).executes(GearCommands::sense)))
			.then(Commands.literal("tip").executes(GearCommands::tip))
			.then(Commands.literal("trinkets").executes(GearCommands::trinkets))
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
			+ trinketPart(source, stack) + ", key " + describeKey(source, stack) + knowsPart(source, stack) + powersPart(source, stack);
		source.sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	/** ", trinket 21.0" (its value to the nearest Drifter), ", trinket yes" (no Drifter near), or nothing if it isn't one. */
	private static String trinketPart(CommandSourceStack source, ItemStack stack) {
		if (!com.dinglebob.drifter.compat.TrinketsCompat.isTrinket(stack)) {
			return "";
		}

		try {
			DrifterEntity drifter = DrifterCommands.nearest(source);
			return String.format(Locale.ROOT, ", trinket %.1f", GearValue.trinket(drifter, stack));
		} catch (CommandSyntaxException e) {
			return ", trinket yes";
		}
	}

	/**
	 * Debug: the nearest Drifter's trinket slots and what is in each, with its value there ("[gear] Trink's trinkets:
	 * chest/necklace artifacts:cross_necklace (21.0), hand/ring empty, ...").
	 */
	private static int trinkets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		if (!com.dinglebob.drifter.compat.TrinketsCompat.installed()) {
			source.sendSuccess(() -> Component.literal("[gear] Trinkets isn't installed"), false);
			return 0;
		}

		if (!com.dinglebob.drifter.compat.TrinketsCompat.active()) {
			source.sendSuccess(() -> Component.literal("[gear] Trinkets' API didn't match: Drifters don't wear trinkets"), false);
			return 0;
		}

		DrifterEntity drifter = DrifterCommands.nearest(source);
		List<com.dinglebob.drifter.compat.TrinketsCompat.Slot> slots = com.dinglebob.drifter.compat.TrinketsCompat.slots(drifter);
		StringBuilder line = new StringBuilder("[gear] " + drifter.getName().getString() + "'s trinkets (" + slots.size() + " slots):");
		for (int i = 0; i < slots.size(); i++) {
			com.dinglebob.drifter.compat.TrinketsCompat.Slot slot = slots.get(i);
			ItemStack worn = slot.worn();
			line.append(i == 0 ? " " : ", ").append(slot.id()).append(slot.index() > 0 ? "#" + slot.index() : "").append(' ');
			if (worn.isEmpty()) {
				line.append("empty");
			} else {
				line.append(BuiltInRegistries.ITEM.getKey(worn.getItem())).append(String.format(Locale.ROOT, " (%.1f)", GearValue.trinketIn(drifter, slot, worn)));
			}
		}

		if (slots.isEmpty()) {
			line.append(" none (Trinkets gave it no slots)");
		}

		line.append("; carries a better one: ").append(com.dinglebob.drifter.executor.task.WearTrinketsTask.hasUpgrade(drifter) ? "yes" : "no");

		source.sendSuccess(() -> Component.literal(line.toString()), false);
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

	/**
	 * "; powers: explosive 0.75 (TEXT), fire (ENCHANT), explosive (LORE); text guess: explosive": the nearest Drifter's beliefs
	 * about the item, then what this stack does of its own for it (its enchantments; its lore or name if it reads text), then
	 * what the text alone hints (the item's and this stack's, whoever reads it).
	 */
	private static String powersPart(CommandSourceStack source, ItemStack stack) {
		StringBuilder out = new StringBuilder();
		try {
			DrifterEntity drifter = DrifterCommands.nearest(source);
			List<String> parts = new java.util.ArrayList<>();
			drifter.getGearLore().beliefs(stack.getItem()).forEach((power, belief) -> parts.add(String.format(Locale.ROOT, "%s %.2f (%s)", power.id(),
				belief.confidence(), belief.source().name())));
			Powers.stackOwn(stack, Powers.readsText(drifter)).forEach((power, own) -> parts.add(power.id() + " (" + own.name() + ")"));
			out.append("; powers: ").append(parts.isEmpty() ? "none" : String.join(", ", parts));
		} catch (CommandSyntaxException e) {
			// no Drifter near: the text guess alone
		}

		java.util.Set<Powers.Power> guess = java.util.EnumSet.noneOf(Powers.Power.class);
		guess.addAll(Powers.guess(source.getLevel(), stack));
		guess.addAll(Powers.written(stack));
		out.append("; text guess: ");
		if (guess.isEmpty()) {
			out.append("none");
		}

		boolean first = true;
		for (Powers.Power power : guess) {
			out.append(first ? "" : ", ").append(power.id());
			first = false;
		}

		return out.toString();
	}

	/**
	 * Debug: would the nearest Drifter use this item's powers on the nearest hostile it sees (or with none in mind), and how
	 * well they suit it: "[gear] Vet, Bow vs Zombie: may use no (would blow up Pal), fit 0.20; believes explosive".
	 */
	private static int sense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		ItemStack stack = ItemArgument.getItem(ctx, "item").createItemStack(1, false);
		net.minecraft.world.entity.LivingEntity target = null;
		double best = Double.MAX_VALUE;
		for (com.dinglebob.drifter.perception.Awareness seen : drifter.getSenses().tracked()) {
			if (seen.visibleNow && seen.entity instanceof net.minecraft.world.entity.monster.Enemy && seen.entity instanceof net.minecraft.world.entity.LivingEntity living
				&& living.isAlive() && living.distanceToSqr(drifter) < best) {
				best = living.distanceToSqr(drifter);
				target = living;
			}
		}

		String why = Powers.whyNot(drifter, stack, target);
		List<String> believed = new ArrayList<>();
		Powers.believed(drifter, stack).forEach(power -> believed.add(power.id()));
		String line = String.format(Locale.ROOT, "[gear] %s, %s vs %s: may use %s, fit %.2f; believes %s", drifter.getName().getString(),
			stack.getHoverName().getString(), target == null ? "nothing" : target.getName().getString() + String.format(Locale.ROOT, " (%.1f off)", Math.sqrt(best)),
			why == null ? "yes" : "no (" + Powers.heldFireWords(drifter, stack, why) + ")", Powers.fit(drifter, stack, target),
			believed.isEmpty() ? "nothing" : String.join(", ", believed));
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	/** Debug: the tip the nearest Drifter could pass on in talk (to anyone), and the gear it has new today. */
	private static int tip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		Powers.Tip tip = Powers.tip(drifter, null);
		Item fresh = drifter.getGearLore().newGear(drifter.level().getGameTime());
		String line = "[gear] " + drifter.getName().getString() + "'s tip: " + (tip == null ? "none" : tip.name() + " " + tip.words()
			+ String.format(Locale.ROOT, " (%s %.2f)", tip.power().id(), tip.confidence())) + "; new gear: " + (fresh == null ? "none" : GearLore.nameOf(fresh));
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	/**
	 * Debug: reads the hover text of every gear item in the game (as a knowledgeable Drifter would, one by one) and says how
	 * many hint at each power and how many could not be read (client-only hover text: no guess, logged once at debug level).
	 */
	private static int powers(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		java.util.Map<Powers.Power, Integer> counts = new java.util.EnumMap<>(Powers.Power.class);
		int gear = 0;
		int guessed = 0;
		for (net.minecraft.world.item.Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			if (stack.isEmpty() || !GearValue.isGear(stack)) {
				continue;
			}

			gear++;
			java.util.Set<Powers.Power> guess = Powers.guess(source.getLevel(), stack);
			if (!guess.isEmpty()) {
				guessed++;
			}

			for (Powers.Power power : guess) {
				counts.merge(power, 1, Integer::sum);
			}
		}

		StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "[gear] text guesses: %d gear items, %d with a guess, %d unreadable", gear, guessed,
			Powers.unreadable()));
		counts.forEach((power, count) -> line.append(", ").append(power.id()).append(' ').append(count));
		source.sendSuccess(() -> Component.literal(line.toString()), false);
		return 1;
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
