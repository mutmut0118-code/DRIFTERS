package com.dinglebob.drifter.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import com.dinglebob.drifter.building.plan.GrabCraftImport;
import com.dinglebob.drifter.building.plan.HousePalette;
import com.dinglebob.drifter.building.plan.HousePlan;
import com.dinglebob.drifter.building.plan.HousePlans;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/**
 * House plans (user's decision, 2026-10-16): {@code /drifter houses} lists them; {@code /drifter house info <plan>} shows
 * a plan and what the nearest Drifter would build it from; {@code /drifter house keep <world plan>} copies a structure
 * block save to the personal folder (for every world); {@code /drifter house import <name>} turns a GrabCraft blueprint in
 * config/drifter/imports into a personal plan; {@code /drifter house paste <plan>} (debug) places it at once, in the
 * nearest Drifter's materials, to look at.
 */
public final class HouseCommands {
	private HouseCommands() {
	}

	public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("houses").executes(HouseCommands::list));
		root.then(Commands.literal("house")
			.then(Commands.literal("info").then(plan().executes(HouseCommands::info)))
			.then(Commands.literal("keep").then(plan().executes(HouseCommands::keep)))
			.then(Commands.literal("paste").then(plan().executes(ctx -> paste(ctx, false))
				.then(Commands.literal("asdrawn").executes(ctx -> paste(ctx, true)))))
			.then(Commands.literal("import").then(Commands.argument("name", StringArgumentType.word())
				.executes(ctx -> importGrabCraft(ctx, true))
				.then(Commands.literal("noflip").executes(ctx -> importGrabCraft(ctx, false))))));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> plan() {
		return Commands.argument("plan", StringArgumentType.greedyString())
			.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(HousePlans.names(ctx.getSource().getServer()), builder));
	}

	private static HousePlan plan(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String id = StringArgumentType.getString(ctx, "plan").replace(" asdrawn", "");
		HousePlan plan = HousePlans.get(ctx.getSource().getServer(), id);
		if (plan == null) {
			throw new SimpleCommandExceptionType(Component.literal("No house plan called " + id + " (see /drifter houses)")).create();
		}

		return plan;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		Map<String, HousePlan> plans = HousePlans.all(ctx.getSource().getServer());
		if (plans.isEmpty()) {
			say(ctx, "No house plans yet. Save one with a structure block named " + HousePlans.WORLD_PREFIX + "<name>, or put .nbt files in "
				+ HousePlans.personalFolder());
		}

		for (HousePlan plan : plans.values()) {
			say(ctx, String.format(Locale.ROOT, "%s (%s): %d x %d x %d, %d blocks", plan.id, plan.source, plan.size.getX(), plan.size.getZ(),
				plan.size.getY(), plan.blockCount()));
		}

		return plans.size();
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		HousePlan plan = plan(ctx);
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		HousePalette palette = HousePalette.forDrifter(drifter, plan);
		say(ctx, String.format(Locale.ROOT, "%s: %d wide, %d deep, %d tall, %d blocks", plan.id, plan.size.getX(), plan.size.getZ(),
			plan.size.getY(), plan.blockCount()));
		say(ctx, DrifterCommands.label(drifter) + " would build it in: " + palette.describe());
		Map<Item, Integer> needs = plan.materials(palette.apply(plan));
		say(ctx, "It takes: " + String.join(", ", needs.entrySet().stream()
			.sorted((a, b) -> b.getValue() - a.getValue())
			.map(e -> e.getValue() + " " + e.getKey().getDescription().getString().toLowerCase(Locale.ROOT)).toList()));
		return 1;
	}

	private static int keep(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String id = StringArgumentType.getString(ctx, "plan");
		if (!id.startsWith("world:")) {
			throw new SimpleCommandExceptionType(Component.literal("Only plans saved in this world (world:...) need keeping")).create();
		}

		var server = ctx.getSource().getServer();
		var template = server.getStructureManager().get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(id.substring("world:".length())));
		if (template.isEmpty()) {
			throw new SimpleCommandExceptionType(Component.literal("No structure " + id)).create();
		}

		try {
			var file = HousePlans.savePersonal(id.substring("world:".length() + HousePlans.WORLD_PREFIX.length()),
				template.get().save(new net.minecraft.nbt.CompoundTag()));
			say(ctx, "Kept for every world: " + file);
			return 1;
		} catch (java.io.IOException e) {
			throw new SimpleCommandExceptionType(Component.literal("Couldn't save: " + e.getMessage())).create();
		}
	}

	private static int importGrabCraft(CommandContext<CommandSourceStack> ctx, boolean flip) throws CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, "name");
		var file = HousePlans.personalFolder().getParent().resolve("imports").resolve(name + ".json");
		if (!java.nio.file.Files.exists(file)) {
			throw new SimpleCommandExceptionType(Component.literal("No " + file + " (a GrabCraft blueprint's block data)")).create();
		}

		try {
			var blocks = ctx.getSource().getServer().registryAccess().lookupOrThrow(Registries.BLOCK);
			var saved = HousePlans.savePersonal(name, GrabCraftImport.read(file, blocks, flip));
			say(ctx, "Imported " + name + " as personal:" + name + " (" + saved + ")");
			return 1;
		} catch (java.io.IOException | RuntimeException e) {
			throw new SimpleCommandExceptionType(Component.literal("Couldn't import: " + e)).create();
		}
	}

	/** Debug: the plan placed at once where the command runs (south-east of it), in the nearest Drifter's materials. */
	private static int paste(CommandContext<CommandSourceStack> ctx, boolean asDrawn) throws CommandSyntaxException {
		HousePlan plan = plan(ctx);
		HousePalette palette = asDrawn ? HousePalette.asDrawn() : HousePalette.forDrifter(DrifterCommands.nearest(ctx.getSource()), plan);
		BlockPos origin = BlockPos.containing(ctx.getSource().getPosition());
		var level = ctx.getSource().getLevel();
		List<HousePlan.Cell> cells = palette.apply(plan);
		for (HousePlan.Cell cell : cells) {
			level.setBlock(origin.offset(cell.pos()), cell.state(), 2);
		}

		say(ctx, "Pasted " + plan.id + " at " + origin.toShortString() + " (" + palette.describe() + ")");
		return 1;
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
	}
}
