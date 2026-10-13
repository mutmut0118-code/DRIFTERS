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
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"fight", "flee", "equip", "eat", "food", "collect", "shelter", "progress", "explore", "idle"}, builder))
					.executes(BrainCommands::force))));
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
		lines.add(String.format(Locale.ROOT, "%s: brain %s, stage %d (%s), next target: %s", DrifterCommands.label(drifter),
			brain.isEnabled() ? "on" : "off", stage, Strategist.stageName(stage), target == null ? "none" : target.label()));
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
