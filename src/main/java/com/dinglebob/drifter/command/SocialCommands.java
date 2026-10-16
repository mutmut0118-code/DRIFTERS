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
		// Section 25 manhunt test mode: /drifter manhunt start <player> [nether|dragon], /drifter manhunt stop.
		root.then(Commands.literal("manhunt")
				.executes(ctx -> {
					say(ctx, com.dinglebob.drifter.social.Manhunt.status(ctx.getSource().getServer()));
					return 1;
				})
				.then(Commands.literal("stop").executes(ctx -> {
					com.dinglebob.drifter.social.Manhunt.stop(ctx.getSource().getServer(), "stopped by command");
					return 1;
				}))
				.then(Commands.literal("start").then(Commands.argument("runner", net.minecraft.commands.arguments.EntityArgument.player())
					.executes(ctx -> manhunt(ctx, com.dinglebob.drifter.social.Manhunt.Goal.NETHER))
					.then(Commands.literal("nether").executes(ctx -> manhunt(ctx, com.dinglebob.drifter.social.Manhunt.Goal.NETHER)))
					.then(Commands.literal("dragon").executes(ctx -> manhunt(ctx, com.dinglebob.drifter.social.Manhunt.Goal.DRAGON))))));
		// (Debug, milestone 12: the nearest Drifter's trust in another Drifter, by name; it meets it first if need be.)
		root.then(Commands.literal("bond").then(Commands.argument("other", StringArgumentType.word())
			.then(Commands.argument("trust", DoubleArgumentType.doubleArg(-100, 100)).executes(ctx -> {
				DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
				String name = StringArgumentType.getString(ctx, "other");
				DrifterEntity other = null;
				for (var level : ctx.getSource().getServer().getAllLevels()) {
					for (DrifterEntity d : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(DrifterEntity.class), DrifterEntity::isAlive)) {
						if (d.getName().getString().equalsIgnoreCase(name)) {
							other = d;
						}
					}
				}

				if (other == null) {
					throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("No Drifter called " + name)).create();
				}

				double trust = DoubleArgumentType.getDouble(ctx, "trust");
				drifter.getBonds().setTrust(other, trust);
				String label = DrifterCommands.label(drifter);
				ctx.getSource().sendSuccess(() -> Component.literal(label + " now trusts " + name + " " + Math.round(trust)), true);
				return 1;
			}))));
		// Debug: a thief empties the valuables out of the nearest one's home chest (they vanish), and it noticed someone there.
		root.then(Commands.literal("rob").executes(ctx -> {
			DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
			var home = drifter.getDrifterBrain().home();
			if (home == null || !home.hasChest()) {
				throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("It has no chest at home")).create();
			}

			int taken = 0;
			net.minecraft.core.BlockPos robbed = null;
			for (net.minecraft.core.BlockPos chest : home.chests()) {
				var container = com.dinglebob.drifter.hands.Containers.get(drifter.level(), chest);
				if (container == null) {
					continue;
				}

				for (int slot = 0; slot < container.getContainerSize(); slot++) {
					if (com.dinglebob.drifter.social.ItemValues.valuable(container.getItem(slot))) {
						taken += container.getItem(slot).getCount();
						container.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
						robbed = chest;
					}
				}
			}

			if (robbed != null) {
				home.checkChest = robbed;
				home.checkChestSince = drifter.level().getGameTime();
			}

			int count = taken;
			ctx.getSource().sendSuccess(() -> Component.literal("took " + count + " valuable items from " + DrifterCommands.label(drifter) + "'s chests"), true);
			return 1;
		}));
		// Debug: the nearest one teams up with the named one at once (friends both ways, one group).
		root.then(Commands.literal("group").then(Commands.argument("other", StringArgumentType.word()).executes(ctx -> {
			DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
			DrifterEntity other = named(ctx, StringArgumentType.getString(ctx, "other"));
			drifter.getBonds().teamUp(other);
			var group = com.dinglebob.drifter.world.DrifterGroups.of(drifter.getUUID());
			String text = DrifterCommands.label(drifter) + (group == null ? " couldn't team up (the other's group is full?)" : " is in \"" + group.name + "\"");
			ctx.getSource().sendSuccess(() -> Component.literal(text), true);
			return 1;
		})));
		root.then(Commands.literal("social")
			.executes(SocialCommands::list)
			.then(Commands.literal("show").then(Commands.argument("player", StringArgumentType.word()).suggests(SocialCommands::players).executes(SocialCommands::show)))
			.then(Commands.literal("set").then(Commands.argument("player", StringArgumentType.word()).suggests(SocialCommands::players)
				.then(Commands.argument("what", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"trust", "fear", "familiarity"}, builder))
					.then(Commands.argument("value", DoubleArgumentType.doubleArg(-100, 100)).executes(SocialCommands::set)))))
			.then(Commands.literal("event").then(Commands.argument("player", StringArgumentType.word()).suggests(SocialCommands::players)
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
			say(ctx, "  " + summary(r) + modeNow(drifter, r));
		}

		// Its group (milestone 12).
		var group = com.dinglebob.drifter.world.DrifterGroups.of(drifter.getUUID());
		if (group != null) {
			say(ctx, "  In \"" + group.name + "\" (led by " + group.names.getOrDefault(group.leader, "?") + "): " + String.join(", ", group.names.values()));
		}

		// Other Drifters it knows (milestone 12).
		var bonds = drifter.getBonds().all();
		if (!bonds.isEmpty()) {
			say(ctx, "  Drifters it knows:");
			for (Relationship r : bonds) {
				say(ctx, "    " + summary(r) + " (" + com.dinglebob.drifter.social.DrifterBonds.attitude(r) + ")");
			}
		}

		return social.all().size() + bonds.size();
	}

	/** A loaded Drifter by name. */
	static DrifterEntity named(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
		for (var level : ctx.getSource().getServer().getAllLevels()) {
			for (DrifterEntity d : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(DrifterEntity.class), DrifterEntity::isAlive)) {
				if (d.getName().getString().equalsIgnoreCase(name)) {
					return d;
				}
			}
		}

		throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("No Drifter called " + name)).create();
	}

	private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		Who player = who(ctx);
		Relationship r = drifter.getSocial().get(player.id());
		if (r == null) {
			say(ctx, DrifterCommands.label(drifter) + " hasn't met " + player.name());
			return 0;
		}

		say(ctx, summary(r) + modeNow(drifter, r));
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
		Who player = who(ctx);
		Relationship r = drifter.getSocial().getOrMeet(player.id(), player.name());
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
		Who player = who(ctx);
		String id = StringArgumentType.getString(ctx, "event");
		SocialEvent event = Arrays.stream(SocialEvent.values()).filter(e -> e.id().equals(id)).findFirst()
			.orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("No such event: " + id)).create());
		double change = drifter.getSocial().record(player.id(), player.name(), event, amount);
		Relationship r = drifter.getSocial().get(player.id());
		say(ctx, String.format(Locale.ROOT, "%s (trust %+.0f): %s", event.what, change, summary(r)));
		return 1;
	}

	/** Section 25 manhunt test mode, hunter role: the nearest Drifter hunts this player. */
	private static int manhunt(CommandContext<CommandSourceStack> ctx, com.dinglebob.drifter.social.Manhunt.Goal goal) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		ServerPlayer runner = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "runner");
		com.dinglebob.drifter.social.Manhunt.start(drifter, runner, goal);
		return 1;
	}

	private static int disposition(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		Disposition disposition = Disposition.byId(StringArgumentType.getString(ctx, "disposition"));
		drifter.getSocial().setDisposition(disposition);
		say(ctx, DrifterCommands.label(drifter) + " is now " + disposition.label());
		return 1;
	}

	/** A player by name: an online one, or (for tests without players) the id an offline server would give that name. */
	private record Who(java.util.UUID id, String name) {
	}

	private static Who who(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "player");
		ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
		return online != null ? new Who(online.getUUID(), online.getGameProfile().getName())
			: new Who(net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(name), name);
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> players(CommandContext<CommandSourceStack> ctx,
		com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder);
	}

	/** Its mode toward this player now, if they're online and in its world. */
	private static String modeNow(DrifterEntity drifter, Relationship r) {
		net.minecraft.world.entity.player.Player player = drifter.level().getPlayerByUUID(r.player);
		return player == null ? "" : ", now " + com.dinglebob.drifter.social.Mode.toward(drifter, player).id().replace('_', ' ');
	}

	private static String summary(Relationship r) {
		return String.format(Locale.ROOT, "%s: trust %.0f, fear %.0f, familiarity %.0f, %d grudge%s", r.name, r.trust, r.fear, r.familiarity,
			r.grudges.size(), r.grudges.size() == 1 ? "" : "s");
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
	}
}
