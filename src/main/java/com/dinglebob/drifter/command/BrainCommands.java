package com.dinglebob.drifter.command;

import java.util.Locale;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import com.dinglebob.drifter.brain.Brain;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Strategist;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * Brain commands (DESIGN.md section 25, "Commands"): status, switching the brain on and off, and forcing a goal.
 */
public final class BrainCommands {
	private BrainCommands() {
	}

	public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("brain")
			.executes(BrainCommands::status)
			.then(Commands.literal("on").executes(ctx -> toggle(ctx, true)))
			.then(Commands.literal("off").executes(ctx -> toggle(ctx, false)))
			.then(Commands.literal("goal")
				.then(Commands.argument("goal", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"fight", "flee", "equip", "eat", "food", "cook", "collect", "shelter", "nighthunt", "makeroom", "settle", "hut", "store", "progress", "explore", "idle"}, builder))
					.executes(BrainCommands::force))));
		root.then(Commands.literal("hut").executes(BrainCommands::showHut));
		root.then(Commands.literal("home")
			.executes(BrainCommands::showHome)
			.then(Commands.literal("here").executes(ctx -> {
				DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
				drifter.getDrifterBrain().setHome(new com.dinglebob.drifter.brain.Home(drifter.blockPosition(), drifter.level().dimension().location(),
					drifter.level().getGameTime()));
				ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + " now lives here (it still needs a chest)"), true);
				return 1;
			}))
			.then(Commands.literal("clear").executes(ctx -> {
				DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
				drifter.getDrifterBrain().setHome(null);
				ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + " forgot its home"), true);
				return 1;
			})));
	}

	/**
	 * The hut as a little map, one layer per line from the floor up (debug): # wall or solid, . air, D door, C chest,
	 * T table, F furnace, i torch, @ the Drifter.
	 */
	private static int showHut(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		com.dinglebob.drifter.brain.Home home = drifter.getDrifterBrain().home();
		if (home == null) {
			throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("No home yet")).create();
		}

		com.dinglebob.drifter.building.StarterHut hut = com.dinglebob.drifter.building.StarterHut.of(home);
		net.minecraft.world.level.Level level = drifter.level();
		java.util.List<String> lines = new java.util.ArrayList<>();
		lines.add(String.format(Locale.ROOT, "%s's hut at %s, door %s, %s, %d blocks missing", DrifterCommands.label(drifter),
			hut.center.toShortString(), hut.door.getName(), home.hutBuilt ? "finished" : "not finished", hut.missingBlocks(level)));
		int half = com.dinglebob.drifter.building.StarterHut.HALF;
		for (int dy = -1; dy <= com.dinglebob.drifter.building.StarterHut.ROOF_LAYER; dy++) {
			StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "y%+d:", dy));
			for (int dz = -half; dz <= half; dz++) {
				line.append(' ');
				for (int dx = -half; dx <= half; dx++) {
					net.minecraft.core.BlockPos pos = hut.center.offset(dx, dy, dz);
					net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
					char c;
					if (drifter.blockPosition().equals(pos)) {
						c = '@';
					} else if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
						c = 'D';
					} else if (com.dinglebob.drifter.brain.Home.isStorageBlock(state)) {
						c = 'C';
					} else if (state.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)) {
						c = 'T';
					} else if (state.is(net.minecraft.world.level.block.Blocks.FURNACE)) {
						c = 'F';
					} else if (state.is(net.minecraft.world.level.block.Blocks.TORCH) || state.is(net.minecraft.world.level.block.Blocks.WALL_TORCH)) {
						c = 'i';
					} else if (com.dinglebob.drifter.building.StarterHut.filled(level, pos)) {
						c = '#';
					} else {
						c = '.';
					}

					line.append(c);
				}
			}

			lines.add(line.toString());
		}

		lines.add("(each row of 5 is one z line, west to east; rows go north to south)");
		for (String line : lines) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}

		return 1;
	}

	private static int showHome(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		String line = homeLine(drifter);
		ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + ": " + (line == null ? "no home yet" : line)), false);
		return 1;
	}

	/** One line about its home, or null. */
	@org.jetbrains.annotations.Nullable
	public static String homeLine(DrifterEntity drifter) {
		com.dinglebob.drifter.brain.Home home = drifter.getDrifterBrain().home();
		if (home == null) {
			return null;
		}

		StringBuilder stored = new StringBuilder();
		home.stored().entrySet().stream().sorted(java.util.Map.Entry.<net.minecraft.world.item.Item, Integer>comparingByValue().reversed()).limit(6)
			.forEach(entry -> stored.append(stored.isEmpty() ? "" : ", ").append(entry.getValue()).append(' ')
				.append(entry.getKey().getDescription().getString().toLowerCase(Locale.ROOT)));
		return String.format(Locale.ROOT, "Home at %s, %d blocks away, %d chest%s (%.0f%% full)%s", home.center.toShortString(),
			(int)Math.sqrt(drifter.blockPosition().distSqr(home.center)), home.chests().size(), home.chests().size() == 1 ? "" : "s",
			home.fullness() * 100.0, stored.isEmpty() ? "" : ": " + stored)
			+ (home.mineBottom == null ? "" : String.format(Locale.ROOT, "; mine down to %s, %d trip%s", home.mineBottom.toShortString(),
				home.mineTrips, home.mineTrips == 1 ? "" : "s"))
			+ farmSummary(drifter, home) + penSummary(drifter, home)
			+ (home.houseBed == null ? "" : "; lives in a village house (bed at " + home.houseBed.toShortString() + ")");
	}

	private static String farmSummary(DrifterEntity drifter, com.dinglebob.drifter.brain.Home home) {
		if (home.farm == null) {
			return "";
		}

		var level = drifter.level();
		int farmland = 0;
		int planted = 0;
		int ripe = 0;
		for (net.minecraft.core.BlockPos soil : com.dinglebob.drifter.brain.activity.FarmActivity.cells(home.farm)) {
			if (level.getBlockState(soil).is(net.minecraft.world.level.block.Blocks.FARMLAND)) {
				farmland++;
			}

			var crop = level.getBlockState(soil.above());
			if (crop.getBlock() instanceof net.minecraft.world.level.block.CropBlock wheat) {
				planted++;
				if (wheat.isMaxAge(crop)) {
					ripe++;
				}
			}
		}

		return String.format(Locale.ROOT, "; farm at %s: %d farmland, %d planted, %d ripe, water %s", home.farm.toShortString(), farmland, planted, ripe,
			com.dinglebob.drifter.brain.activity.FarmActivity.isWaterSource(level, home.farm.below()) ? "yes" : "no");
	}

	private static String penSummary(DrifterEntity drifter, com.dinglebob.drifter.brain.Home home) {
		if (home.pen == null) {
			return "";
		}

		return String.format(Locale.ROOT, "; pen at %s with %d animals", home.pen.toShortString(),
			com.dinglebob.drifter.brain.activity.PenActivity.animalsInside(drifter.level(), home.pen).size());
	}

	private static int toggle(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		drifter.getDrifterBrain().setEnabled(on);
		if (on && drifter.getTasks().current() != null) {
			drifter.getTasks().cancel("the brain took over");
		}

		ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + "'s brain is " + (on ? "on: it plays on its own" : "off")), true);
		return 1;
	}

	private static int force(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		String id = StringArgumentType.getString(ctx, "goal");
		if (!drifter.getDrifterBrain().force(id)) {
			throw new SimpleCommandExceptionType(Component.literal("Couldn't start '" + id + "' (goals: "
				+ String.join(", ", drifter.getDrifterBrain().goalIds()) + "; it may have nothing to do for it)")).create();
		}

		ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + " now does: " + id), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		for (String line : describe(drifter)) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}

		return 1;
	}

	/** The brain's state in plain words, for the status command and the overlay. */
	public static java.util.List<String> describe(DrifterEntity drifter) {
		Brain brain = drifter.getDrifterBrain();
		java.util.List<String> lines = new java.util.ArrayList<>();
		int stage = Strategist.stage(drifter);
		Strategist.Target target = Strategist.nextTarget(drifter);
		lines.add(String.format(Locale.ROOT, "%s: brain %s, stage %d (%s), next target: %s, level %d", DrifterCommands.label(drifter),
			brain.isEnabled() ? "on" : "off", stage, Strategist.stageName(stage), target == null ? "none" : target.label(),
			drifter.experienceLevel()));
		Goals.Context context = brain.lastContext();
		if (brain.isEnabled() && brain.currentGoal() != null && context != null) {
			Activity activity = brain.activity();
			lines.add("Goal: " + brain.currentGoal().name(context) + (activity == null ? "" : " -> " + activity.describe() + " -> " + activity.currentStep()));
			StringBuilder scores = new StringBuilder("Scores:");
			brain.lastScores().stream().limit(4).forEach(s -> scores.append(String.format(Locale.ROOT, " %s %.2f%s,", s.name(), s.score(),
				s.urgent() ? "!" : "")));
			lines.add(scores.substring(0, scores.length() - 1));
			if (!brain.lastSteps().isEmpty()) {
				lines.add("Plan: " + String.join(" > ", brain.lastSteps().subList(0, Math.min(5, brain.lastSteps().size())))
					+ (brain.lastSteps().size() > 5 ? " > ..." : ""));
			}

			lines.add("Needs: " + context.needs().describe());
		}

		String home = homeLine(drifter);
		if (home != null) {
			lines.add(home);
		}

		com.dinglebob.drifter.brain.Threats.Assessment threat = com.dinglebob.drifter.brain.Threats.assess(drifter);
		if (threat.decision() != com.dinglebob.drifter.brain.Threats.Decision.NONE) {
			lines.add("Threat: " + threat.describe());
		}

		String reflex = drifter.getReflexes().describe();
		if (!reflex.isEmpty()) {
			lines.add(reflex);
		}

		return lines;
	}
}
