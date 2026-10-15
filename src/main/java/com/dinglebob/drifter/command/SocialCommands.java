package com.dinglebob.drifter.command;

import java.util.Arrays;
import java.util.Locale;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.social.Disposition;
import com.dinglebob.drifter.social.Relationship;
import com.dinglebob.drifter.social.Relationships;
import com.dinglebob.drifter.social.SocialEvent;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Milestone 9 debug commands (section 22: "set Trust toward a player"): what the nearest Drifter thinks of players, and
 * setting numbers or applying a section 14 event by hand to check the table.
 *
 * <pre>
 * /drifter social                                   its disposition and everyone it knows
 * /drifter social show &lt;player&gt;                     one relationship in full
 * /drifter social set &lt;player&gt; trust|fear|familiarity &lt;value&gt;
 * /drifter social event &lt;player&gt; &lt;event&gt; [amount]   apply a table event (amount: gift value, damage, gear gap)
 * /drifter social disposition &lt;disposition&gt;
 * </pre>
 */
public final class SocialCommands {
	private SocialCommands() {
	}

	public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("social")
			.executes(SocialCommands::list)
			.then(Commands.literal("show").then(Commands.argument("player", EntityArgument.player()).executes(SocialCommands::show)))
			.then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
				.then(Commands.argument("what", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"trust", "fear", "familiarity"}, builder))
					.then(Commands.argument("value", DoubleArgumentType.doubleArg(-100, 100)).executes(SocialCommands::set)))))
			.then(Commands.literal("event").then(Commands.argument("player", EntityArgument.player())
				.then(Commands.argument("event", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(SocialEvent.values()).map(SocialEvent::id), builder))
					.executes(ctx -> event(ctx, 0.0))
					.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0, 100))
						.executes(ctx -> event(ctx, DoubleArgumentType.getDouble(ctx, "amount")))))))
			.then(Commands.literal("disposition").then(Commands.argument("disposition", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Disposition.values()).map(Disposition::id), builder))
				.executes(SocialCommands::disposition))));
	}

	private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		Relationships social = drifter.getSocial();
		say(ctx, String.format(Locale.ROOT, "%s: %s, stranger baseline trust %.0f", DrifterCommands.label(drifter), social.disposition().label(),
			social.strangerBaseline()));
		if (social.all().isEmpty()) {
			say(ctx, "  knows no players yet");
		}

		for (Relationship r : social.all()) {
			say(ctx, "  " + summary(r));
		}

		return social.all().size();
	}

	private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Relationship r = drifter.getSocial().get(player.getUUID());
		if (r == null) {
			say(ctx, DrifterCommands.label(drifter) + " hasn't met " + player.getGameProfile().getName());
			return 0;
		}

		say(ctx, summary(r));
		say(ctx, String.format(Locale.ROOT, "  strength estimate %.2f, gear fear %d, wronged it: %s, last seen %s, base %s", r.strength, r.gearFear,
			r.wronged ? "yes" : "no", r.lastSeenPos == null ? "never" : r.lastSeenPos.toShortString(),
			r.knownBase == null ? "unknown" : r.knownBase.toShortString()));
		for (Relationship.Grudge g : r.grudges) {
			say(ctx, String.format(Locale.ROOT, "  grudge: %s, severity %.0f%s", g.event().what, g.severity(), g.forever() ? " (never forgets)" : ""));
		}

		for (Relationship.Debt d : r.debts) {
			say(ctx, "  owes them: " + d.what() + " (" + d.value() + ")");
		}

		return 1;
	}

	private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Relationship r = drifter.getSocial().getOrMeet(player.getUUID(), player.getGameProfile().getName());
		double value = DoubleArgumentType.getDouble(ctx, "value");
		switch (StringArgumentType.getString(ctx, "what")) {
			case "trust" -> r.trust = value;
			case "fear" -> r.fear = Math.max(0.0, value);
			case "familiarity" -> r.familiarity = Math.max(0.0, value);
			default -> throw new SimpleCommandExceptionType(Component.literal("trust, fear or familiarity")).create();
		}

		say(ctx, summary(r));
		return 1;
	}

	private static int event(CommandContext<CommandSourceStack> ctx, double amount) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String id = StringArgumentType.getString(ctx, "event");
		SocialEvent event = Arrays.stream(SocialEvent.values()).filter(e -> e.id().equals(id)).findFirst()
			.orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("No such event: " + id)).create());
		double change = drifter.getSocial().record(player.getUUID(), player.getGameProfile().getName(), event, amount);
		Relationship r = drifter.getSocial().get(player.getUUID());
		say(ctx, String.format(Locale.ROOT, "%s (trust %+.0f): %s", event.what, change, summary(r)));
		return 1;
	}

	private static int disposition(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		Disposition disposition = Disposition.byId(StringArgumentType.getString(ctx, "disposition"));
		drifter.getSocial().setDisposition(disposition);
		say(ctx, DrifterCommands.label(drifter) + " is now " + disposition.label());
		return 1;
	}

	private static String summary(Relationship r) {
		return String.format(Locale.ROOT, "%s: trust %.0f, fear %.0f, familiarity %.0f, %d grudge%s", r.name, r.trust, r.fear, r.familiarity,
			r.grudges.size(), r.grudges.size() == 1 ? "" : "s");
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
	}
}
