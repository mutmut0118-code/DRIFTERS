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
		// Where Drifters live in this world: the nearest lived-in village, or Bandit-held pillager outpost (user's request).
		// Debug: a Bandit gang that lives here as if it held an outpost (raids, griefing).
		// Debug: a lone wanderer here (fresh, day one, or passing through), as world generation makes them.
		root.then(Commands.literal("wanderer").then(Commands.argument("kind", StringArgumentType.word())
			.suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("fresh", "dayone", "passing", "delver"), builder))
			.executes(ctx -> {
				String kind = StringArgumentType.getString(ctx, "kind");
				if (kind.equals("delver")) {
					// Debug: a mineshaft delver here, as if it were looting the nearest chest minecart.
					var level = ctx.getSource().getLevel();
					var delver = com.dinglebob.drifter.world.gen.Delvers.spawn(level, net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()),
						level.getRandom());
					ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(delver == null ? "couldn't"
						: "spawned a delver, " + delver.getSocial().disposition().label() + " (careful: most aren't friendly)"), true);
					return delver == null ? 0 : 1;
				}

				int which = kind.equals("fresh") ? 0 : kind.equals("dayone") ? 1 : 2;
				String made = com.dinglebob.drifter.world.gen.WandererFeature.spawn(ctx.getSource().getLevel(),
					net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()), ctx.getSource().getLevel().getRandom(), which);
				ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(made == null ? "couldn't" : "spawned " + made + " wanderer"), true);
				return made == null ? 0 : 1;
			})));
		root.then(Commands.literal("banditgang").executes(ctx -> {
			int n = com.dinglebob.drifter.world.gen.LivedInStructures.gangAt(ctx.getSource().getLevel(), net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
			ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(n + " Bandits now hold this spot (careful: they're hostile)"), true);
			return n;
		}));
		// Debug: the nearest Bandit gang learns of a base right here (and may raid it soon).
		root.then(Commands.literal("banditmark").executes(ctx -> {
			DrifterEntity bandit = null;
			double best = Double.MAX_VALUE;
			for (DrifterEntity d : ctx.getSource().getLevel().getEntitiesOfClass(DrifterEntity.class, new net.minecraft.world.phys.AABB(net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition())).inflate(400.0))) {
				double dist = d.distanceToSqr(ctx.getSource().getPosition());
				if (com.dinglebob.drifter.social.BanditGoals.griefer(d) && dist < best) {
					best = dist;
					bandit = d;
				}
			}

			if (bandit == null) {
				ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("No outpost Bandit within 400 blocks"));
				return 0;
			}

			com.dinglebob.drifter.social.BanditGoals.debugMark(bandit, net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
			String name = bandit.getName().getString();
			ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(name + "'s gang now knows of a base here and will raid it soon"), true);
			return 1;
		}));
		root.then(Commands.literal("locate").then(Commands.argument("what", StringArgumentType.word())
			.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"village", "outpost", "mineshaft"}, builder))
			.executes(ctx -> {
				String what = StringArgumentType.getString(ctx, "what");
				if (what.equals("mineshaft")) {
					return mineshaft(ctx);
				}

				if (!what.equals("village") && !what.equals("outpost")) {
					throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("village or outpost")).create();
				}

				var level = ctx.getSource().getServer().overworld();
				boolean on = what.equals("village") ? com.dinglebob.drifter.DrifterConfig.worldgenVillages() : com.dinglebob.drifter.DrifterConfig.worldgenOutposts();
				if (!on) {
					say(ctx, (what.equals("village") ? "Drifter villages" : "Bandit outposts") + " are turned off in the settings");
					return 0;
				}

				var from = net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition());
				var found = com.dinglebob.drifter.world.gen.LivedInStructures.locate(level, what, from, what.equals("village") ? 16 : 24);
				if (found == null) {
					say(ctx, "No " + (what.equals("village") ? "Drifter village" : "Bandit outpost") + " within a few thousand blocks");
					return 0;
				}

				int x = found.pos().getX();
				int z = found.pos().getZ();
				int distance = (int)Math.round(Math.sqrt(Math.pow(x - from.getX(), 2) + Math.pow(z - from.getZ(), 2)));
				var coords = net.minecraft.network.chat.ComponentUtils.wrapInSquareBrackets(Component.literal(x + ", ~, " + z))
					.withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GREEN)
						.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + x + " ~ " + z))
						.withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.coordinates.tooltip"))));
				Component text = Component.literal("The nearest " + (what.equals("village") ? "village with Drifters living in it" : "outpost held by Bandits") + " is at ")
					.append(coords).append(Component.literal(" (" + distance + " blocks away; they move in when it's first loaded)"));
				ctx.getSource().sendSuccess(() -> text, false);
				return 1;
			})));
		// Team wars (section 21): every feud between groups, and (debug) the tension between two Drifters' groups set outright.
		root.then(Commands.literal("wars").executes(ctx -> {
			var feuds = com.dinglebob.drifter.world.DrifterWars.all();
			if (feuds.isEmpty()) {
				say(ctx, "No feuds between Drifter groups");
			}

			long now = ctx.getSource().getLevel().getGameTime();
			for (var feud : feuds) {
				var raid = com.dinglebob.drifter.world.DrifterWars.raidOf(feud.a);
				if (raid == null) {
					raid = com.dinglebob.drifter.world.DrifterWars.raidOf(feud.b);
				}

				String truce = now < feud.truceUntil ? String.format(Locale.ROOT, ", truce for %d more min", (feud.truceUntil - now) / 1200) : "";
				String raiding = raid == null ? "" : ", " + com.dinglebob.drifter.world.DrifterWars.name(raid.attackers) + " raiding (" + raid.phase.name().toLowerCase(Locale.ROOT) + ")";
				say(ctx, String.format(Locale.ROOT, "%s vs %s: %s, tension %d, losses %d / %d%s%s", com.dinglebob.drifter.world.DrifterWars.name(feud.a),
					com.dinglebob.drifter.world.DrifterWars.name(feud.b), com.dinglebob.drifter.world.DrifterWars.levelName(feud.tension),
					Math.round(feud.tension), feud.lossesA, feud.lossesB, truce, raiding));
			}

			return feuds.size();
		}));
		root.then(Commands.literal("war").then(Commands.argument("one", StringArgumentType.word()).then(Commands.argument("other", StringArgumentType.word())
			.then(Commands.argument("tension", DoubleArgumentType.doubleArg(0, 100)).executes(ctx -> {
				DrifterEntity one = named(ctx, StringArgumentType.getString(ctx, "one"));
				DrifterEntity other = named(ctx, StringArgumentType.getString(ctx, "other"));
				var a = com.dinglebob.drifter.world.DrifterGroups.of(one.getUUID());
				var b = com.dinglebob.drifter.world.DrifterGroups.of(other.getUUID());
				if (a == null || b == null || a == b) {
					throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Both need to be in groups, and not the same one")).create();
				}

				double tension = DoubleArgumentType.getDouble(ctx, "tension");
				com.dinglebob.drifter.world.DrifterWars.setTension(a.id, b.id, tension, one.level().getGameTime());
				ctx.getSource().sendSuccess(() -> Component.literal(a.name + " vs " + b.name + ": tension " + Math.round(tension) + " ("
					+ com.dinglebob.drifter.world.DrifterWars.levelName(tension) + ")"), true);
				return 1;
			})))));
		// Debug: the nearest one teams up with the named one at once (friends both ways, one group).
		root.then(Commands.literal("group").then(Commands.argument("other", StringArgumentType.word()).executes(ctx -> {
			DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
			DrifterEntity other = named(ctx, StringArgumentType.getString(ctx, "other"));
			if (other == drifter) {
				throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("That's " + DrifterCommands.label(drifter) + " itself")).create();
			}

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

	/**
	 * Debug: whoever ran it (a player, or any entity with "execute as") to the first room of the nearest mineshaft a delver
	 * would come to (vanilla's or Hopo's), on its floor (to meet a delver: walk into the tunnels).
	 */
	private static int mineshaft(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx)
		throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = ctx.getSource().getEntityOrException();
		var level = ctx.getSource().getServer().overworld();
		var from = player.blockPosition();
		var found = level.findNearestMapStructure(com.dinglebob.drifter.world.gen.Delvers.MINESHAFTS, from, 64, false);
		com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter] nearest mineshaft to {}: {}", from.toShortString(), found);
		if (found == null) {
			say(ctx, "No mineshaft found near here");
			return 0;
		}

		var structures = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
		var chunk = level.getChunk(found.getX() >> 4, found.getZ() >> 4);
		for (var holder : structures.getTagOrEmpty(com.dinglebob.drifter.world.gen.Delvers.MINESHAFTS)) {
			var structure = holder.value();
			var start = level.structureManager().getStartForStructure(net.minecraft.core.SectionPos.of(found), structure, chunk);
			if (start == null || !start.isValid() || start.getPieces().isEmpty()) {
				continue;
			}

			// The floor of a room, as near its middle as there is one (a jigsaw room's middle can be a pit or a wall).
			for (var piece : start.getPieces()) {
				var room = piece.getBoundingBox();
				var center = room.getCenter();
				net.minecraft.core.BlockPos best = null;
				for (var pos : net.minecraft.core.BlockPos.betweenClosed(room.minX(), room.minY() + 1, room.minZ(), room.maxX(), room.maxY() - 1, room.maxZ())) {
					if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
						&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP) && level.getFluidState(pos).isEmpty()
						&& (best == null || pos.distSqr(center) < best.distSqr(center))) {
						best = pos.immutable();
					}
				}

				if (best == null) {
					continue;
				}

				var stand = best;
				String name = structures.getKey(structure).toString();
				player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot());
				com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter] {} to the mineshaft ({}) room at {}", player.getName().getString(), name, stand.toShortString());
				say(ctx, "In the mineshaft (" + name + ") at " + stand.getX() + ", " + stand.getY() + ", " + stand.getZ());
				return 1;
			}
		}

		say(ctx, "Found a mineshaft near " + found.toShortString() + " but couldn't get into it");
		return 0;
	}
}
