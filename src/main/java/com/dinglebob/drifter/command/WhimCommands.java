package com.dinglebob.drifter.command;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.whim.Whim;
import com.dinglebob.drifter.whim.WhimGoal;
import com.dinglebob.drifter.whim.Whims;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * Whim commands (milestone 13): {@code /drifter whims} lists the whims the nearest Drifter would go for now (with how
 * much, and when it last did each), and {@code /drifter whim <id>} has it do one now (debug).
 */
public final class WhimCommands {
	private WhimCommands() {
	}

	public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("whims").executes(WhimCommands::list));
		root.then(Commands.literal("whim").then(Commands.argument("id", StringArgumentType.word())
			.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Whims.all().stream().map(Whim::id), builder))
			.executes(WhimCommands::force)));
	}

	private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		long now = drifter.level().getGameTime();
		var state = WhimGoal.state(drifter);
		List<Whim> all = Whims.all().stream().sorted(Comparator.comparingDouble((Whim w) -> -WhimGoal.weight(drifter, w))).toList();
		say(ctx, String.format(Locale.ROOT, "%s: %d whims known, %d done so far (%s ago last); %d settled at once with nobody near (all Drifters, since the start)",
			DrifterCommands.label(drifter), all.size(), state.done, state.done == 0 ? "never" : ((now - state.lastAny) / 20) + " s", WhimGoal.settled));
		// How long each one's check against the world takes (performance; the slowest few).
		List<String> slow = all.stream().map(w -> {
			long t = System.nanoTime();
			WhimGoal.whyNot(drifter, w, null);
			return Map.entry(w.id(), System.nanoTime() - t);
		}).sorted((x, y) -> Long.compare(y.getValue(), x.getValue())).limit(5)
			.map(e -> String.format(Locale.ROOT, "%s %.2f ms", e.getKey(), e.getValue() / 1e6)).toList();
		say(ctx, "Slowest checks: " + String.join(", ", slow));
		int shown = 0;
		for (Whim whim : all) {
			double weight = WhimGoal.weight(drifter, whim);
			String whyNot = weight > 0.0 ? WhimGoal.whyNot(drifter, whim, null) : "never";
			boolean fits = whyNot == null;
			long last = state.lastDone(whim.id());
			say(ctx, String.format(Locale.ROOT, "  %s %s: weight %.2f%s%s", fits ? "+" : "-", whim.id(), weight, fits ? "" : " (" + whyNot + ")",
				last > Long.MIN_VALUE / 4 ? ", last " + ((now - last) / 1200) + " min ago" : ""));
			if (++shown >= 30) {
				break;
			}
		}

		return all.size();
	}

	private static int force(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		String id = StringArgumentType.getString(ctx, "id");
		Whim whim = Whims.byId(id);
		if (whim == null) {
			throw new SimpleCommandExceptionType(Component.literal("No whim called " + id)).create();
		}

		if (!(drifter.getDrifterBrain().goal("whim") instanceof WhimGoal goal)) {
			throw new SimpleCommandExceptionType(Component.literal("It has no whim goal")).create();
		}

		String whyNot = WhimGoal.whyNot(drifter, whim, null);
		if (whyNot != null) {
			say(ctx, DrifterCommands.label(drifter) + " can't do that one right now: " + whyNot);
			return 0;
		}

		goal.force(whim);
		drifter.getDrifterBrain().force("whim");
		say(ctx, DrifterCommands.label(drifter) + " now does: " + whim.thought());
		return 1;
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
	}
}
