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
			.then(Commands.literal("status").executes(HouseCommands::status))
			// Debug: the house as a map, layer by layer.
			.then(Commands.literal("map").executes(HouseCommands::map))
			// Debug: blocks in every loaded Drifter's house that aren't in its plan (stray cobblestone and the like), to the log.
			.then(Commands.literal("strays").executes(HouseCommands::strays))
			// Debug: a plan's own cells, layer by layer, to the log (one letter a block, the key after each layer).
			.then(Commands.literal("plandump").then(plan().executes(ctx -> {
				HousePlan plan = plan(ctx);
				java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> cells = new java.util.HashMap<>();
				plan.cells.forEach(cell -> cells.put(cell.pos(), cell.state()));
				String letters = "#abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
				for (int y = 0; y < plan.size.getY(); y++) {
					java.util.Map<String, Character> key = new java.util.LinkedHashMap<>();
					StringBuilder out = new StringBuilder("[plandump] " + plan.id + " y" + y + " (rows z, x across)");
					for (int z = 0; z < plan.size.getZ(); z++) {
						out.append(System.lineSeparator()).append("  ");
						for (int x = 0; x < plan.size.getX(); x++) {
							var state = cells.get(new BlockPos(x, y, z));
							out.append(state == null ? '.' : key.computeIfAbsent(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(),
								k -> letters.charAt(Math.min(key.size(), letters.length() - 1))));
						}
					}

					out.append(System.lineSeparator()).append("  key: ").append(key);
					com.dinglebob.drifter.Drifter.LOGGER.info(out.toString());
				}

				return 1;
			})))
			.then(Commands.literal("info").then(plan().executes(HouseCommands::info)))
			.then(Commands.literal("keep").then(plan().executes(HouseCommands::keep)))
			.then(Commands.literal("layout").then(Commands.argument("bed", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
				.then(Commands.argument("chests", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 12)).executes(HouseCommands::layout))))
			.then(Commands.literal("choose").then(plan().executes(HouseCommands::choose)))
			// Debug: where the house it's chosen goes (its corner), not turned, in its woods: for testing a build at once.
			.then(Commands.literal("site").then(Commands.argument("corner", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
				.executes(HouseCommands::site)))
			// Debug: the house at its site counts as built and lived in (its bed is the one inside), e.g. after a paste.
			.then(Commands.literal("done").executes(HouseCommands::done))
			.then(Commands.literal("save").then(Commands.argument("name", StringArgumentType.word())
				.then(Commands.argument("from", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
					.then(Commands.argument("to", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
						.executes(HouseCommands::save)))))
			// Debug: every part of a generated house on its own, a house of each style, the hand-made plans, in the generator's
			// own materials, with signs (to look over and edit by hand; the user, 2026-10-21).
			.then(Commands.literal("parts").executes(ctx -> {
				String result = com.dinglebob.drifter.building.plan.gen.PartsExhibit.build(ctx.getSource().getLevel(),
					BlockPos.containing(ctx.getSource().getPosition()));
				say(ctx, "House parts: " + result);
				return 1;
			}))
			// A row of generated plans, spread over the styles, built as drawn from here along +x (to look them over in-game).
			.then(Commands.literal("showcase").then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
				.executes(HouseCommands::showcase)))
			// Debug (milestone 15): a sample of the generated plans, spread over the styles, as structure files for pictures.
			.then(Commands.literal("export").then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1000))
				.executes(HouseCommands::export)))
			.then(Commands.literal("paste").then(plan().executes(ctx -> paste(ctx, false))
				.then(Commands.literal("asdrawn").executes(ctx -> paste(ctx, true)))))
			.then(Commands.literal("import").then(Commands.argument("name", StringArgumentType.word())
				.executes(ctx -> importGrabCraft(ctx, true))
				.then(Commands.literal("noflip").executes(ctx -> importGrabCraft(ctx, false))))));
	}

	private static int showcase(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
		int count = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count");
		var plans = new java.util.ArrayList<>(com.dinglebob.drifter.building.plan.gen.GeneratedPlans.all(ctx.getSource().getServer()).values());
		if (plans.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("No generated house plans (the setting is 0, or no styles)"));
			return 0;
		}

		var level = ctx.getSource().getLevel();
		BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
		int step = Math.max(1, plans.size() / count);
		StringBuilder names = new StringBuilder();
		int x = 0;
		for (int i = 0, made = 0; i < plans.size() && made < count; i += step, made++) {
			HousePlan plan = plans.get(i);
			for (HousePlan.Cell cell : plan.cells) {
				level.setBlock(at.offset(x, 0, 0).offset(cell.pos()), cell.state(), 2);
			}

			names.append(made == 0 ? "" : ", ").append(plan.id.substring(plan.id.indexOf(':') + 1)).append(" at ").append(at.getX() + x).append(" ")
				.append(com.dinglebob.drifter.building.plan.gen.GeneratedPlans.features(plan.id));
			x += Math.max(16, plan.size.getX() + 3);
		}

		ctx.getSource().sendSuccess(() -> Component.literal("Built " + names), false);
		return 1;
	}

	private static int export(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
		int count = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count");
		var plans = new java.util.ArrayList<>(com.dinglebob.drifter.building.plan.gen.GeneratedPlans.all(ctx.getSource().getServer()).values());
		java.nio.file.Path folder = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("drifter").resolve("house_exports");
		int written = 0;
		try {
			java.nio.file.Files.createDirectories(folder);
			int step = Math.max(1, plans.size() / Math.max(1, count));
			for (int i = 0; i < plans.size() && written < count; i += step) {
				HousePlan plan = plans.get(i);
				net.minecraft.nbt.NbtIo.writeCompressed(plan.toStructure(), folder.resolve(plan.id.replace(':', '_') + ".nbt"));
				written++;
			}
		} catch (java.io.IOException e) {
			ctx.getSource().sendFailure(Component.literal("Couldn't write: " + e.getMessage()));
			return 0;
		}

		int total = written;
		ctx.getSource().sendSuccess(() -> Component.literal("Wrote " + total + " of " + plans.size() + " generated plans to " + folder), false);
		return total;
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> plan() {
		return Commands.argument("plan", StringArgumentType.greedyString())
			.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(HousePlans.names(ctx.getSource().getServer()), builder));
	}

	private static HousePlan plan(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String id = StringArgumentType.getString(ctx, "plan").replace(" asdrawn", "");
		HousePlan plan = HousePlans.get(ctx.getSource().getServer(), id);
		// "house:<style>": the first generated plan of that style (their numbers depend on the world).
		if (plan == null && id.startsWith(com.dinglebob.drifter.building.plan.gen.GeneratedPlans.PREFIX)) {
			plan = com.dinglebob.drifter.building.plan.gen.GeneratedPlans.all(ctx.getSource().getServer()).values().stream()
				.filter(p -> p.id.startsWith(id + "_")).findFirst().orElse(null);
		}

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

	/** The nearest Drifter's house from a plan: which, where, how far along. */
	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		var home = drifter.getDrifterBrain().home();
		if (home == null || home.planId == null || home.planOrigin == null) {
			say(ctx, DrifterCommands.label(drifter) + " hasn't picked a house plan yet");
			return 0;
		}

		HousePlan plan = HousePlans.get(ctx.getSource().getServer(), home.planId);
		if (plan == null) {
			say(ctx, DrifterCommands.label(drifter) + "'s plan " + home.planId + " is gone");
			return 0;
		}

		var placed = com.dinglebob.drifter.building.plan.PlacedPlan.of(plan, home);
		var level = drifter.level();
		long done = placed.build.stream().filter(c -> com.dinglebob.drifter.hands.Placement.matches(level.getBlockState(c.pos()), c.state())).count();
		BlockPos far = home.planOrigin.offset(placed.sizeX - 1, placed.sizeY - 1, placed.sizeZ - 1);
		BlockPos bed = home.planBed(level);
		say(ctx, String.format(Locale.ROOT, "%s's house: %s from %s to %s, turned %s, in %s; %d of %d blocks placed%s%s", DrifterCommands.label(drifter),
			home.planId, home.planOrigin.toShortString(), far.toShortString(), home.planRotation.toLowerCase(Locale.ROOT),
			com.dinglebob.drifter.building.plan.HousePalette.of(home.planWoods, home.planDeepslate).describe(), done, placed.build.size(),
			home.planDone ? " (finished)" : "", bed != null ? "; lives in it (bed at " + bed.toShortString() + ")" : ""));
		List<String> missing = placed.build.stream().filter(c -> !com.dinglebob.drifter.hands.Placement.matches(level.getBlockState(c.pos()), c.state()))
			.limit(8).map(c -> c.state().getBlock().getName().getString().toLowerCase(Locale.ROOT) + " at " + c.pos().toShortString()
				+ (level.getBlockState(c.pos()).isAir() ? "" : " (" + level.getBlockState(c.pos()).getBlock().getName().getString().toLowerCase(Locale.ROOT) + " there)")
				+ (com.dinglebob.drifter.hands.Placement.ways(drifter, c.pos(), com.dinglebob.drifter.hands.Placement.step(level, c.pos(), c.state())).isEmpty()
					? " (no way to place it: " + whyNoWay(level, c.pos()) + ")" : "")).toList();
		if (!missing.isEmpty() && done > placed.build.size() / 2) {
			say(ctx, "Still missing: " + String.join(", ", missing) + (placed.build.size() - done > 8 ? ", ..." : ""));
		}
		return 1;
	}

	/**
	 * The nearest Drifter's house, one layer per line from two under its floor up (debug): # placed as planned, . still to
	 * place, X something else where a block goes, G ground in place, o a hole in its ground, @ the Drifter, - nothing
	 * planned (open), = nothing planned but a solid block there.
	 */
	private static int map(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		var home = drifter.getDrifterBrain().home();
		HousePlan plan = home == null || home.planId == null || home.planOrigin == null ? null : HousePlans.get(ctx.getSource().getServer(), home.planId);
		if (plan == null) {
			say(ctx, DrifterCommands.label(drifter) + " has no house plan with a spot");
			return 0;
		}

		var placed = com.dinglebob.drifter.building.plan.PlacedPlan.of(plan, home);
		var level = drifter.level();
		java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> want = placed.buildMap();
		java.util.Set<BlockPos> ground = new java.util.HashSet<>(placed.ground);
		BlockPos feet = drifter.blockPosition();
		say(ctx, DrifterCommands.label(drifter) + "'s house " + home.planId + " at " + home.planOrigin.toShortString() + " (rows along x, north to south)");
		for (int y = -2; y < placed.sizeY; y++) {
			StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "y%+d:", y));
			for (int z = 0; z < placed.sizeZ; z++) {
				line.append(' ');
				for (int x = 0; x < placed.sizeX; x++) {
					BlockPos pos = home.planOrigin.offset(x, y, z);
					var there = level.getBlockState(pos);
					var planned = want.get(pos);
					char c;
					if (pos.equals(feet)) {
						c = '@';
					} else if (planned != null) {
						c = com.dinglebob.drifter.hands.Placement.matches(there, planned) ? '#' : there.isAir() || there.canBeReplaced() ? '.' : 'X';
					} else if (ground.contains(pos)) {
						c = there.isAir() || there.canBeReplaced() ? 'o' : 'G';
					} else {
						c = there.isAir() || there.canBeReplaced() ? '-' : '=';
					}

					line.append(c);
				}
			}

			say(ctx, line.toString());
		}

		return 1;
	}

	private static int strays(CommandContext<CommandSourceStack> ctx) {
		var level = ctx.getSource().getLevel();
		java.util.Set<String> seen = new java.util.HashSet<>();
		int total = 0;
		for (DrifterEntity drifter : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(DrifterEntity.class), DrifterEntity::isAlive)) {
			var home = drifter.getDrifterBrain().home();
			HousePlan plan = home == null || home.planId == null || home.planOrigin == null ? null : HousePlans.get(ctx.getSource().getServer(), home.planId);
			if (plan == null || !seen.add(home.planId + "@" + home.planOrigin.toShortString())) {
				continue;
			}

			var placed = com.dinglebob.drifter.building.plan.PlacedPlan.of(plan, home);
			java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> want = placed.buildMap();
			java.util.Set<BlockPos> ground = new java.util.HashSet<>(placed.ground);
			java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
			java.util.List<String> where = new java.util.ArrayList<>();
			for (int y = 1; y < placed.sizeY; y++) {
				for (int z = 0; z < placed.sizeZ; z++) {
					for (int x = 0; x < placed.sizeX; x++) {
						BlockPos pos = home.planOrigin.offset(x, y, z);
						var there = level.getBlockState(pos);
						// (Furniture isn't in plans: beds, chests, stations, doors.)
						if (want.containsKey(pos) || ground.contains(pos) || there.isAir() || there.canBeReplaced()
							|| there.getCollisionShape(level, pos).isEmpty() || level.getBlockEntity(pos) != null
							|| there.is(net.minecraft.tags.BlockTags.DOORS) || there.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)) {
							continue;
						}

						String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(there.getBlock()).getPath();
						kinds.merge(id, 1, Integer::sum);
						if (where.size() < 12) {
							where.add(id + " " + pos.toShortString());
						}
					}
				}
			}

			int count = kinds.values().stream().mapToInt(Integer::intValue).sum();
			total += count;
			com.dinglebob.drifter.Drifter.LOGGER.info("[strays] {} at {} ({}): {} not in the plan {} e.g. {}", home.planId, home.planOrigin.toShortString(),
				drifter.getName().getString(), count, kinds, where);
		}

		int all = total;
		say(ctx, all + " blocks in Drifter houses that aren't in their plans (the server log lists them: [strays])");
		return all;
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

	/**
	 * Debug: furnishes the room round a bed the way a Drifter living there would (that many chests, then a crafting table
	 * and a furnace), placed at once, and says where each went.
	 */
	private static int layout(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		var level = ctx.getSource().getLevel();
		BlockPos bed = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "bed");
		int chests = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "chests");
		List<String> placed = new java.util.ArrayList<>();
		List<net.minecraft.world.level.block.state.BlockState> things = new java.util.ArrayList<>();
		for (int i = 0; i < chests; i++) {
			things.add(net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
		}

		things.add(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState());
		things.add(net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
		for (var thing : things) {
			var room = com.dinglebob.drifter.building.Interior.of(level, bed);
			if (room == null) {
				say(ctx, "No walled-in room round " + bed.toShortString());
				return 0;
			}

			boolean chest = thing.is(net.minecraft.world.level.block.Blocks.CHEST);
			BlockPos spot = room.spot(chest ? com.dinglebob.drifter.building.Interior.Kind.CHEST : com.dinglebob.drifter.building.Interior.Kind.STATION,
				java.util.Set.of());
			if (spot == null) {
				placed.add(thing.getBlock().getName().getString() + ": no room");
				continue;
			}

			level.setBlock(spot, thing, 3);
			placed.add(thing.getBlock().getName().getString() + " " + spot.toShortString());
		}

		say(ctx, "Layout: " + String.join(", ", placed));
		return 1;
	}

	/** Debug: the nearest Drifter's next house will be this plan (it picks a spot for it when it starts building). */
	private static int choose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		HousePlan plan = plan(ctx);
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		var home = drifter.getDrifterBrain().home();
		if (home == null) {
			say(ctx, DrifterCommands.label(drifter) + " has no home");
			return 0;
		}

		home.planId = plan.id;
		home.planOrigin = null;
		home.planDone = false;
		home.planBed = null;
		say(ctx, DrifterCommands.label(drifter) + " will build " + plan.id + " next");
		return 1;
	}

	private static int site(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		var home = drifter.getDrifterBrain().home();
		HousePlan plan = home == null || home.planId == null ? null : HousePlans.get(ctx.getSource().getServer(), home.planId);
		if (plan == null) {
			say(ctx, DrifterCommands.label(drifter) + " has no house plan chosen (/drifter house choose <plan> first)");
			return 0;
		}

		var palette = com.dinglebob.drifter.building.plan.HousePalette.forDrifter(drifter, plan);
		home.planOrigin = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "corner");
		home.planRotation = "NONE";
		home.planWoods.clear();
		home.planWoods.putAll(palette.woods());
		home.planDeepslate = palette.deepslate();
		home.planDone = false;
		home.planBed = null;
		home.groundEased = false;
		home.setPlanBox(com.dinglebob.drifter.building.plan.PlacedPlan.of(plan, home).box());
		// (Debug: this house now, as a town's people build theirs, no hut first.)
		home.noHut = true;
		say(ctx, DrifterCommands.label(drifter) + " will build " + plan.id + " from " + home.planOrigin.toShortString() + ", in " + palette.describe());
		return 1;
	}

	private static int done(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		var home = drifter.getDrifterBrain().home();
		HousePlan plan = home == null || home.planId == null || home.planOrigin == null ? null : HousePlans.get(ctx.getSource().getServer(), home.planId);
		if (plan == null) {
			say(ctx, DrifterCommands.label(drifter) + " has no house at a site (/drifter house choose, then /drifter house site)");
			return 0;
		}

		var placed = com.dinglebob.drifter.building.plan.PlacedPlan.of(plan, home);
		var level = drifter.level();
		home.planDone = true;
		home.planBed = null;
		for (BlockPos pos : BlockPos.betweenClosed(placed.box().minX(), placed.box().minY(), placed.box().minZ(), placed.box().maxX(), placed.box().maxY(),
			placed.box().maxZ())) {
			var state = level.getBlockState(pos);
			if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock
				&& state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
				home.planBed = pos.immutable();
				break;
			}
		}

		BlockPos bed = home.planBed;
		say(ctx, DrifterCommands.label(drifter) + "'s house counts as built" + (bed != null ? ", its bed at " + bed.toShortString() : " (no bed inside)"));
		return 1;
	}

	/** Debug: what's round a cell that has no way to be placed (its neighbours, and anything standing in it). */
	private static String whyNoWay(net.minecraft.world.level.Level level, BlockPos pos) {
		List<String> parts = new java.util.ArrayList<>();
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
			var state = level.getBlockState(pos.relative(d));
			parts.add(d.getName().charAt(0) + "=" + (state.isAir() ? "air" : state.getBlock().getName().getString().toLowerCase(Locale.ROOT)));
		}

		var inside = level.getEntities((net.minecraft.world.entity.Entity)null, new net.minecraft.world.phys.AABB(pos));
		if (!inside.isEmpty()) {
			parts.add("entity " + inside.get(0).getName().getString());
		}

		return String.join(" ", parts);
	}

	/** Most a plan may measure each way (a structure block's limit). */
	private static final int MAX_SIZE = 48;

	/**
	 * Saves the blocks between two corners as a personal plan, for every world (the user's own builds, or one pasted with
	 * the BuildPaste mod; user's request 2026-10-16), without a structure block.
	 */
	private static int save(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, "name").toLowerCase(Locale.ROOT);
		BlockPos a = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "from");
		BlockPos b = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "to");
		BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
		net.minecraft.core.Vec3i size = new net.minecraft.core.Vec3i(Math.abs(a.getX() - b.getX()) + 1, Math.abs(a.getY() - b.getY()) + 1,
			Math.abs(a.getZ() - b.getZ()) + 1);
		if (size.getX() > MAX_SIZE || size.getY() > MAX_SIZE || size.getZ() > MAX_SIZE) {
			throw new SimpleCommandExceptionType(Component.literal("Too big: at most " + MAX_SIZE + " blocks each way")).create();
		}

		// Trimmed to what's built in it (a box picked a little big, or reaching into the ground, doesn't make the plan bigger;
		// grass and dirt inside it stay, as the plan's ground).
		var level = ctx.getSource().getLevel();
		BlockPos lo = null;
		BlockPos hi = null;
		for (BlockPos pos : BlockPos.betweenClosed(min, min.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			var state = level.getBlockState(pos);
			if (!state.isAir() && !com.dinglebob.drifter.building.plan.PlacedPlan.isGround(state) && !state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
				&& !state.is(net.minecraft.world.level.block.Blocks.BEDROCK) && level.getFluidState(pos).isEmpty()
				&& !state.is(net.minecraft.tags.BlockTags.REPLACEABLE)) {
				lo = lo == null ? pos.immutable() : new BlockPos(Math.min(lo.getX(), pos.getX()), Math.min(lo.getY(), pos.getY()), Math.min(lo.getZ(), pos.getZ()));
				hi = hi == null ? pos.immutable() : new BlockPos(Math.max(hi.getX(), pos.getX()), Math.max(hi.getY(), pos.getY()), Math.max(hi.getZ(), pos.getZ()));
			}
		}

		if (lo == null) {
			throw new SimpleCommandExceptionType(Component.literal("There's nothing between those corners")).create();
		}

		min = lo;
		size = new net.minecraft.core.Vec3i(hi.getX() - lo.getX() + 1, hi.getY() - lo.getY() + 1, hi.getZ() - lo.getZ() + 1);
		var template = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
		template.fillFromWorld(level, min, size, false, null);
		try {
			var file = HousePlans.savePersonal(name, template.save(new net.minecraft.nbt.CompoundTag()));
			HousePlan plan = HousePlans.get(ctx.getSource().getServer(), "personal:" + name);
			say(ctx, String.format(Locale.ROOT, "Saved personal:%s (%d x %d x %d, %d blocks once unfurnished) for every world: %s", name,
				size.getX(), size.getY(), size.getZ(), plan == null ? 0 : plan.blockCount(), file));
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
