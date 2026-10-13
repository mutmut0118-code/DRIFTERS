package com.dinglebob.drifter.command;

import java.util.Arrays;
import java.util.Locale;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.mannerism.Mannerisms;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.personality.Mood;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * Personality commands (DESIGN.md sections 16, 17 and 25): see who the nearest Drifter is, give it a new personality
 * (optionally of one archetype), force a mood, or play a tic to see what it looks like.
 */
public final class PersonalityCommands {
	private PersonalityCommands() {
	}

	public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("personality")
			.executes(PersonalityCommands::show)
			.then(Commands.literal("reroll")
				.executes(ctx -> reroll(ctx, null))
				.then(Commands.argument("archetype", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Archetype.values()).map(Archetype::id), builder))
					.executes(ctx -> reroll(ctx, StringArgumentType.getString(ctx, "archetype"))))));
		root.then(Commands.literal("mood")
			.executes(PersonalityCommands::showMood)
			.then(Commands.argument("mood", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Mood.values()).map(Mood::id), builder))
				.executes(PersonalityCommands::setMood)));
		root.then(Commands.literal("night")
			.executes(PersonalityCommands::showNight)
			.then(Commands.argument("plan", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"shelter", "hunt", "mine"}, builder))
				.executes(PersonalityCommands::setNight)));
		root.then(Commands.literal("tic")
			.then(Commands.argument("tic", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Mannerisms.Kind.values()).map(PersonalityCommands::ticId), builder))
				.executes(PersonalityCommands::tic)));
	}

	private static String ticId(Mannerisms.Kind kind) {
		return kind.name().toLowerCase(Locale.ROOT);
	}

	private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + ":"), false);
		for (String line : drifter.getPersonality().describe()) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}

		return 1;
	}

	private static int reroll(CommandContext<CommandSourceStack> ctx, String archetypeId) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		Archetype archetype = null;
		if (archetypeId != null) {
			archetype = Arrays.stream(Archetype.values()).filter(a -> a.id().equals(archetypeId)).findFirst().orElseThrow(() ->
				new SimpleCommandExceptionType(Component.literal("Unknown archetype '" + archetypeId + "'")).create());
		}

		drifter.getPersonality().reroll(drifter.getRandom(), archetype);
		ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + " is now a " + drifter.getPersonality().archetype().displayName()), true);
		return show(ctx);
	}

	private static int showMood(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		StringBuilder pressures = new StringBuilder();
		for (Mood mood : Mood.values()) {
			double value = drifter.getPersonality().mood().pressure(mood);
			if (value > 0.0) {
				pressures.append(String.format(Locale.ROOT, " %s %.2f", mood.id(), value));
			}
		}

		String line = DrifterCommands.label(drifter) + " feels " + drifter.getPersonality().mood().describe()
			+ (pressures.isEmpty() ? "" : " (pressures:" + pressures + ")");
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	private static int setMood(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		String id = StringArgumentType.getString(ctx, "mood");
		Mood mood = Arrays.stream(Mood.values()).filter(m -> m.id().equals(id)).findFirst().orElseThrow(() ->
			new SimpleCommandExceptionType(Component.literal("Unknown mood '" + id + "'")).create());
		drifter.getPersonality().mood().force(mood);
		ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + " now feels " + mood.id()
			+ " (it fades on its own like a real mood)"), true);
		return 1;
	}

	private static int showNight(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		Schedule.NightStyle style = drifter.getDrifterBrain().nightStyle();
		String line = DrifterCommands.label(drifter) + ": tonight it " + style.describe() + " (it would pick: "
			+ Schedule.chooseNightStyle(drifter).describe() + "; fit to hunt: " + (Schedule.fitToHunt(drifter) ? "yes" : "no") + ")";
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	private static int setNight(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		String id = StringArgumentType.getString(ctx, "plan");
		Schedule.NightStyle style = Arrays.stream(Schedule.NightStyle.values()).filter(s -> s.name().toLowerCase(Locale.ROOT).equals(id)).findFirst()
			.orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("Unknown plan '" + id + "' (shelter, hunt, mine)")).create());
		drifter.getDrifterBrain().forceNightStyle(style);
		ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + ": tonight it " + style.describe()), true);
		return 1;
	}

	private static int tic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		String id = StringArgumentType.getString(ctx, "tic");
		Mannerisms.Kind kind = Arrays.stream(Mannerisms.Kind.values()).filter(k -> ticId(k).equals(id)).findFirst().orElseThrow(() ->
			new SimpleCommandExceptionType(Component.literal("Unknown tic '" + id + "'")).create());
		if (!drifter.getMannerisms().force(kind)) {
			throw new SimpleCommandExceptionType(Component.literal("It can't do that one right now (a shield raise needs a shield in its offhand)")).create();
		}

		ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + " does: " + kind.id()), false);
		return 1;
	}
}
