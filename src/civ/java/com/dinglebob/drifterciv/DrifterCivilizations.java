package com.dinglebob.drifterciv;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.api.DrifterEvents;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * Drifter: Civilizations (milestone 22, docs/CIVILIZATION.md): an addon for Drifter, in the same repository (the user,
 * 2026-10-21: "same repo, call it Drifter: Civilizations, go ahead"). It reaches into Drifter only through
 * {@code com.dinglebob.drifter.api} (docs/API.md), and asks for exactly the Drifter it was built with.
 *
 * <p>So far: the chronicle (what happened, day by day) and "one island" mode (a crowd of new Drifters starting with nothing
 * in one place). Towns come next.
 */
public final class DrifterCivilizations implements ModInitializer {
	public static final String MOD_ID = "drifter_civ";
	public static final Logger LOGGER = LoggerFactory.getLogger("Drifter: Civilizations");

	/** Drifter deaths heard of through the API this run (the door working end to end). */
	private static int deathsHeard;
	/** News Drifters passed on to each other this run. */
	private static int newsTold;
	/** Trades between Drifters this run. */
	private static int trades;
	/** Lines of the chronicle a command shows at once. */
	private static final int PAGE = 15;
	@Nullable
	private static MinecraftServer server;

	@Override
	public void onInitialize() {
		CivConfig.load();
		ServerLifecycleEvents.SERVER_STARTING.register(started -> server = started);
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> server = null);
		DrifterEvents.DIED.register((drifter, source, comesBack) -> deathsHeard++);
		DrifterEvents.HEARD_NEWS.register((drifter, news, from) -> newsTold++);
		DrifterEvents.TRADED.register((asker, giver, paid, got) -> trades++);
		Chronicle.register();
		Island.register();
		Towns.register();
		TownFounding.register();
		Justice.register();
		// An autosave on walking into a town, with Quicksave (0.23.37).
		Arrivals.register();
		Diplomacy.register();
		TownSites.register();
		WorldPlan.register();
		Highways.register();
		WorldBuild.register();
		TownRaids.register();
		Trades.register();
		DrifterApi.setRoutes(Routes::between);
		DrifterApi.setRoadPoints(Routes::near);
		DrifterApi.setTownLooters(TownLife::scavengers);
		TownTraditions.register();
		TownNotices.register();
		TownServices.register();
		BloodGoods.register();
		Orders.register();
		Friends.register();
		FriendTalk.register();
		FriendPerks.register();
		FriendGoals.register();
		FriendCommands.register();
		Journal.register();
		Quests.register();
		Citizens.register();
		TownAway.register();
		Annals.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(Commands.literal("drifterciv")
			.executes(ctx -> {
				ctx.getSource().sendSuccess(() -> Component.literal("Drifter: Civilizations " + version() + " on Drifter " + DrifterApi.version()
					+ " (API " + DrifterApi.API_VERSION + "). This run: Drifter deaths heard of " + deathsHeard + ", news passed between Drifters "
					+ newsTold + ", trades between Drifters " + trades + ". /drifterciv chronicle, /drifterciv island"), false);
				return 1;
			})
			// The chronicle: the latest, a day, a page, as a book; the morning summary on or off.
			.then(Commands.literal("chronicle")
				.executes(ctx -> chronicle(ctx, Chronicle.all(), "The chronicle (latest)", true))
				.then(Commands.literal("day").then(Commands.argument("day", LongArgumentType.longArg(1)).executes(ctx -> {
					long day = LongArgumentType.getLong(ctx, "day");
					return chronicle(ctx, Chronicle.day(day), "Day " + day, false);
				})))
				.then(Commands.literal("book").requires(source -> source.hasPermission(2)).executes(DrifterCivilizations::book))
				.then(Commands.literal("summary").requires(source -> source.hasPermission(2))
					.then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
						boolean on = BoolArgumentType.getBool(ctx, "on");
						Chronicle.setSummary(on);
						ctx.getSource().sendSuccess(() -> Component.literal("Each morning, the day before in chat: " + (on ? "on" : "off")), true);
						return 1;
					}))))
			// The civilized world (W4): the nearest city, town, hamlet or ruin of the world plan, made or not, and where; the plan
			// as a whole (debug: build the roads round here now).
			.then(Commands.literal("locate")
				.then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
					.suggests((ctx, builder) -> builder.suggest("city").suggest("town").suggest("hamlet").suggest("ruin").suggest("any").buildFuture())
					.executes(ctx -> {
						String kind = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind");
						BlockPos from = BlockPos.containing(ctx.getSource().getPosition());
						WorldPlan.Place place = WorldPlan.nearest(from, kind.equals("any") ? null : kind);
						if (place == null) {
							ctx.getSource().sendFailure(Component.literal(WorldPlan.planned ? WorldPlan.ready ? "None in this world" : "The world plan is still being made"
								: "This world has no plan (made before it, or with \"A world of nations\" off): /locate structure drifter_civ:town"));
							return 0;
						}

						WorldPlan.PlannedNation nation = WorldPlan.nation(place.nation);
						int far = (int)Math.round(Math.hypot(place.x - from.getX(), place.z - from.getZ()));
						ctx.getSource().sendSuccess(() -> Component.literal(place.name + " (a " + place.kind + (nation != null ? ", of " + nation.name() : place.ruin()
							? "" : ", in no nation") + ") is at " + place.x + ", " + place.z + ", " + far + " blocks away" + (place.made ? "" : " (not seen yet)")), false);
						return 1;
					})))
			// (Debug: the way along the roads from here to there, as the Drifters going far take it.)
			.then(Commands.literal("route")
				.then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
					.then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer()).executes(ctx -> {
						BlockPos from = BlockPos.containing(ctx.getSource().getPosition());
						BlockPos to = new BlockPos(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"), from.getY(),
							com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z"));
						String way = Routes.describe(from, to);
						ctx.getSource().sendSuccess(() -> Component.literal("To " + to.getX() + ", " + to.getZ() + ": " + way), false);
						return 1;
					}))))
			.then(Commands.literal("plan")
				.executes(ctx -> {
					if (!WorldPlan.ready) {
						ctx.getSource().sendFailure(Component.literal(WorldPlan.planned ? "The world plan is still being made" : "This world has no plan"));
						return 0;
					}

					for (WorldPlan.PlannedNation nation : WorldPlan.nations) {
						List<String> towns = WorldPlan.places.stream().filter(p -> nation.id().equals(p.nation)).map(p -> p.name + " (" + (p.ruin() ? "ruin" : p.kind) + " at " + p.x + ", "
							+ p.z + (p.made ? ", made" : "") + ")").toList();
						ctx.getSource().sendSuccess(() -> Component.literal(nation.name() + " (" + nation.colour().getName() + "): " + String.join("; ", towns)), false);
					}

					List<String> free = WorldPlan.places.stream().filter(p -> p.nation == null).map(p -> p.name + " (" + (p.ruin() ? "ruin" : p.kind) + " at " + p.x + ", " + p.z
						+ ")").toList();
					ctx.getSource().sendSuccess(() -> Component.literal("In no nation: " + String.join("; ", free)), false);
					ctx.getSource().sendSuccess(() -> Component.literal("Roads: " + Highways.describe()), false);
					if (WorldPlan.history != null) {
						long since = WorldPlan.history.day - History.DAYS;
						ctx.getSource().sendSuccess(() -> Component.literal("Its past: " + History.DAYS + " days before day 1, " + since
							+ " since (the places nobody's near living by their summary)"), false);
					}
					return 1;
				})
				.then(Commands.literal("built").requires(source -> source.hasPermission(2)).executes(ctx -> {
					// Debug: within 128 blocks, the columns counted as built on, by the block that decides it.
					BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
					var level = ctx.getSource().getLevel();
					java.util.Map<String, Integer> by = new java.util.TreeMap<>();
					for (int x = at.getX() - 128; x <= at.getX() + 128; x++) {
						for (int z = at.getZ() - 128; z <= at.getZ() + 128; z++) {
							if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null) {
								var state = TownBuilder.builtBy(level, x, z);
								if (state != null) {
									by.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 1, Integer::sum);
								}
							}
						}
					}

					LOGGER.info("[built] round {}: {}", at.toShortString(), by);
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + by), false);
					return 1;
				}))
				// Debug: logs round here with no leaves on them (bare trunks a road or a town left: the user's picture, 2026-11-08,
				// "floating thing"), each piece: where, how many logs, how many stand on the ground, whether it's someone's build.
				.then(Commands.literal("bare").requires(source -> source.hasPermission(2)).executes(ctx -> {
					var level = ctx.getSource().getLevel();
					BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
					int r = 96;
					java.util.Set<BlockPos> seen = new java.util.HashSet<>();
					int found = 0;
					for (BlockPos pos : BlockPos.betweenClosed(at.offset(-r, -40, -r), at.offset(r, 60, r))) {
						if (seen.contains(pos) || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
							|| !level.getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS)) {
							continue;
						}

						java.util.Set<BlockPos> piece = new java.util.HashSet<>();
						java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
						piece.add(pos.immutable());
						queue.add(pos.immutable());
						while (!queue.isEmpty() && piece.size() < 600) {
							BlockPos p = queue.poll();
							for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
								if (!piece.contains(n) && level.getChunkSource().getChunkNow(n.getX() >> 4, n.getZ() >> 4) != null
									&& level.getBlockState(n).is(net.minecraft.tags.BlockTags.LOGS)) {
									piece.add(n.immutable());
									queue.add(n.immutable());
								}
							}
						}

						seen.addAll(piece);
						int leaves = 0;
						int grounded = 0;
						boolean built = false;
						for (BlockPos log : piece) {
							built |= DrifterApi.isBuilt(level, log);
							var under = level.getBlockState(log.below());
							grounded += !piece.contains(log.below()) && !under.isAir() && !under.is(net.minecraft.tags.BlockTags.LEAVES) ? 1 : 0;
							for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
								var next = level.getBlockState(log.relative(side));
								leaves += next.is(net.minecraft.tags.BlockTags.LEAVES) ? 1 : 0;
							}
						}

						if (leaves == 0 && !built && piece.size() >= 2) {
							found++;
							int minY = piece.stream().mapToInt(BlockPos::getY).min().orElse(0);
							int maxY = piece.stream().mapToInt(BlockPos::getY).max().orElse(0);
							String block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
							String line = "[bare] " + piece.size() + " " + block + " at " + pos.toShortString() + " (y " + minY + " to " + maxY + ", " + grounded
								+ " on the ground, under them: " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(pos.getX(),
								minY - 1, pos.getZ())).getBlock()) + ")";
							LOGGER.info(line);
						}
					}

					int all = found;
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + all + " bare pieces of trunk within " + r + " blocks (in the log)"), false);
					return all;
				}))
				// Debug: the land surveyed from here as a new world's plan would (0.23.31: the continent the nations go on).
				.then(Commands.literal("land").requires(source -> source.hasPermission(2)).executes(ctx -> {
					BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
					String found = WorldPlan.survey(ctx.getSource().getLevel(), at);
					LOGGER.info("[plan] the land round {}: {}", at.toShortString(), found);
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + found), false);
					return 1;
				}))
				.then(Commands.literal("height").requires(source -> source.hasPermission(2)).executes(ctx -> {
					// Debug: the land's height here as the plan reads it, and as it's generated.
					BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
					var level = ctx.getSource().getLevel();
					int plan = WorldPlan.realHeight(level, at.getX(), at.getZ());
					level.getChunk(at);
					int floor = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, at.getX(), at.getZ()) - 1;
					int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, at.getX(), at.getZ()) - 1;
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: at " + at.getX() + ", " + at.getZ() + " the plan reads the land at " + plan
						+ "; generated, its floor at " + floor + " and its top at " + top), false);
					return 1;
				}))
				// (Debug: the lamps built round here saved as structure files, for the road lamps; the user built them.)
				.then(Commands.literal("grablamps").requires(source -> source.hasPermission(2))
					.then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(4, 128)).executes(ctx -> {
						int radius = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius");
						try {
							int count = RoadLamps.grab(ctx.getSource().getLevel(), BlockPos.containing(ctx.getSource().getPosition()), radius);
							ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + count + " lamps saved to lamps_out"), true);
							return count;
						} catch (java.io.IOException e) {
							ctx.getSource().sendFailure(Component.literal("Couldn't save the lamps: " + e.getMessage()));
							return 0;
						}
					})))
				// (Debug: a road between towns from here the way the player looks, bending a little, in stretches as they're built.)
				.then(Commands.literal("roaddemo").requires(source -> source.hasPermission(2))
					.then(Commands.argument("length", com.mojang.brigadier.arguments.IntegerArgumentType.integer(16, 400)).executes(ctx -> {
						int length = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "length");
						int stretches = Highways.demo(ctx.getSource().getLevel(), ctx.getSource().getPosition(), ctx.getSource().getRotation().y, length, 0);
						ctx.getSource().sendSuccess(() -> Component.literal("Debug: a road " + length + " blocks long from here, in " + stretches + " stretches"), true);
						return 1;
					}).then(Commands.literal("straight").executes(ctx -> {
						int length = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "length");
						int stretches = Highways.demo(ctx.getSource().getLevel(), ctx.getSource().getPosition(), ctx.getSource().getRotation().y, length, 1);
						ctx.getSource().sendSuccess(() -> Component.literal("Debug: a straight road " + length + " blocks long from here, in " + stretches + " stretches"), true);
						return 1;
					})).then(Commands.literal("corner").executes(ctx -> {
						int length = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "length");
						int stretches = Highways.demo(ctx.getSource().getLevel(), ctx.getSource().getPosition(), ctx.getSource().getRotation().y, length, 2);
						ctx.getSource().sendSuccess(() -> Component.literal("Debug: a road " + length + " blocks long from here with a square corner halfway, in " + stretches + " stretches"), true);
						return 1;
					}))))
				.then(Commands.literal("roads").requires(source -> source.hasPermission(2)).executes(ctx -> {
					Highways.forcedNear = BlockPos.containing(ctx.getSource().getPosition());
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: roads within 1000 blocks of here are built now, a stretch at a time, player or not"),
						true);
					return 1;
				})))
			// The world's nations (step 6, part 3): each one's capital, towns, tribute and banners; debug: tribute due now.
			.then(Commands.literal("nations")
				.executes(ctx -> {
					List<String> lines = Nations.describe(ctx.getSource().getServer());
					if (lines.isEmpty()) {
						ctx.getSource().sendSuccess(() -> Component.literal("No nations yet"), false);
					}

					lines.forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(line), false));
					return lines.size();
				})
				.then(Commands.literal("tribute").requires(source -> source.hasPermission(2)).executes(ctx -> {
					Nations.debugTribute(ctx.getSource().getServer());
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: every nation's tribute falls due now"), true);
					return 1;
				}))
				// Debug: the named town joins the nearest one's nation at once (a new one, the nearest its capital), no envoy.
				.then(Commands.literal("join").requires(source -> source.hasPermission(2))
					.then(Commands.argument("other", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((c, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Towns.all().stream().map(t -> t.name), builder))
						.executes(ctx -> {
							Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
							Town other = named(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "other"));
							if (town == null || other == null || other == town || Nations.of(other) != null) {
								ctx.getSource().sendFailure(Component.literal("No such other town, or it's in a nation already"));
								return 0;
							}

							var nation = Nations.join(ctx.getSource().getServer(), town, other, false);
							ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + other.name + " is in " + nation.name + " now"), true);
							return 1;
						})))
				// Debug: the named town goes free of its nation at once (as a ruler letting it go), no envoy.
				.then(Commands.literal("free").requires(source -> source.hasPermission(2))
					.then(Commands.argument("town", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((c, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Towns.all().stream().map(t -> t.name), builder))
						.executes(ctx -> {
							Town town = named(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "town"));
							var nation = town == null ? null : Nations.of(town);
							if (nation == null || nation.capital.equals(town.id)) {
								ctx.getSource().sendFailure(Component.literal("No such town in a nation (or it's the capital)"));
								return 0;
							}

							Nations.leave(ctx.getSource().getServer(), town, "left " + nation.name + ", free again (debug)");
							ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + town.name + " is free of " + nation.name + " now"), true);
							return 1;
						}))))
			// The nearest town's dealings with the others it knows of (step 6, part 1); debug: a standing set, an envoy sent now.
			.then(Commands.literal("relations")
				.executes(DrifterCivilizations::relations)
				.then(Commands.literal("set").requires(source -> source.hasPermission(2))
					.then(Commands.argument("other", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((c, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Towns.all().stream().map(t -> t.name), builder))
						.then(Commands.argument("standing", IntegerArgumentType.integer(-100, 100)).executes(ctx -> {
							Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
							Town other = named(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "other"));
							if (town == null || other == null || other == town) {
								ctx.getSource().sendFailure(Component.literal("No such other town"));
								return 0;
							}

							var r = Diplomacy.meet(ctx.getSource().getServer(), town, other, 0.0, null);
							r.standing = IntegerArgumentType.getInteger(ctx, "standing");
							Towns.save(ctx.getSource().getServer());
							ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + town.name + " and " + other.name + ": " + Diplomacy.word(r.standing)
								+ " (" + Math.round(r.standing) + ")"), true);
							return 1;
						}))))
				// Debug: war declared at once (no envoy), or a white peace made at once.
				.then(Commands.literal("war").requires(source -> source.hasPermission(2))
					.then(Commands.argument("other", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((c, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Towns.all().stream().map(t -> t.name), builder))
						.executes(ctx -> warOrPeace(ctx, true))))
				.then(Commands.literal("peace").requires(source -> source.hasPermission(2))
					.then(Commands.argument("other", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((c, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Towns.all().stream().map(t -> t.name), builder))
						.executes(ctx -> warOrPeace(ctx, false))))
				.then(Commands.literal("envoy").requires(source -> source.hasPermission(2))
					.then(Commands.argument("other", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((c, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Towns.all().stream().map(t -> t.name), builder))
						.then(Commands.argument("what", com.mojang.brigadier.arguments.StringArgumentType.word())
							.suggests((c, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("trade", "peace", "alliance", "insult", "war", "terms",
								"demand", "union", "submit", "independence"), builder))
							.executes(ctx -> {
								Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
								Town other = named(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "other"));
								var group = town == null || town.group == null ? null : DrifterApi.groupById(town.group);
								String what = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "what").toUpperCase(java.util.Locale.ROOT);
								if (group == null || other == null || other == town || !List.of("TRADE", "PEACE", "ALLIANCE", "INSULT", "WAR", "TERMS", "DEMAND", "UNION", "SUBMIT",
									"INDEPENDENCE").contains(what)) {
									ctx.getSource().sendFailure(Component.literal("No such other town (or not trade, peace, alliance, insult, war, terms, demand, union, submit or"
										+ " independence)"));
									return 0;
								}

								boolean sent = Diplomacy.send(ctx.getSource().getServer(), town, group, other, what);
								ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + (sent ? town.name + " sent an envoy to " + other.name
									+ " (see the chronicle)" : "nobody to send")), true);
								return sent ? 1 : 0;
							})))))
			// A town's notice turned in at a Bountiful board: what its paper's reward runs (TownBounties), "<notice> <item> <count>
			// <player>"; the town pays them, and takes the things.
			.then(Commands.literal("bountiful").requires(source -> source.hasPermission(2))
				.then(Commands.argument("paper", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
					String[] parts = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "paper").trim().split("\\s+");
					try {
						var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.parse(parts[1]));
						if (parts.length < 4 || item.isEmpty()) {
							return 0;
						}

						return TownBounties.cashIn(ctx.getSource().getServer(), java.util.UUID.fromString(parts[0]), item.get(), Integer.parseInt(parts[2]), parts[3]) ? 1 : 0;
					} catch (RuntimeException e) {
						ctx.getSource().sendFailure(Component.literal("drifterciv bountiful <notice> <item> <count> <player>"));
						return 0;
					}
				})))
			// Towns: the nearest, all of them, and (debug, for testing) one made at once.
			.then(Commands.literal("town")
				.executes(DrifterCivilizations::town)
				// The nearest town's uniform (API 6): its set, its trim (or its nation's), what its guards and leader wear; debug: its set
				// changed (no one re-dressed: a stale or unknown one is worn as trimmed iron by those dressed in it).
				.then(Commands.literal("uniform")
					.executes(ctx -> uniform(ctx, null))
					.then(Commands.literal("set").requires(source -> source.hasPermission(2))
						.then(Commands.argument("set", net.minecraft.commands.arguments.ResourceLocationArgument.id())
							.executes(ctx -> uniform(ctx, net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "set").toString())))))
				// Debug: the nearest town's market day, or Founding Day, today (milestone 23 C4, C6).
				.then(Commands.literal("market").requires(source -> source.hasPermission(2)).executes(ctx -> {
					Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
					if (town == null) {
						return 0;
					}

					town.forcedMarketDay = Chronicle.day(ctx.getSource().getServer());
					town.eventDay = -1;
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: market day in " + town.name + " today" + (town.stallBuilt ? "" : " (it has no stall up!)")),
						false);
					return 1;
				}))
				.then(Commands.literal("monument").requires(source -> source.hasPermission(2)).executes(ctx -> {
					Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
					if (town == null) {
						return 0;
					}

					town.monumentFor = "In memory of|those who|built|" + town.name;
					town.monumentDay = -100;
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: a monument due in " + town.name + " (its people begin it at the next check)"), false);
					return 1;
				}))
				.then(Commands.literal("festival").requires(source -> source.hasPermission(2)).executes(ctx -> {
					Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
					if (town == null) {
						return 0;
					}

					long today = Chronicle.day(ctx.getSource().getServer());
					long years = Math.max(1, (today - town.bornDay) / TownCulture.YEAR);
					town.bornDay = today - years * TownCulture.YEAR;
					town.festivalDay = -1;
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: Founding Day in " + town.name + " today"), false);
					return 1;
				}))
				// Debug: comings and goings (milestone 23 C3) at the nearest town now.
				.then(Commands.literal("leave").requires(source -> source.hasPermission(2)).executes(ctx -> {
					Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
					String what = town == null ? "no town" : TownLife.debugLeave(ctx.getSource().getServer(), town);
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + what), false);
					return 1;
				}))
				.then(Commands.literal("newcomer").requires(source -> source.hasPermission(2)).executes(ctx -> {
					Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
					String what = town == null ? "no town" : TownLife.debugArrive(ctx.getSource().getServer(), town);
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + what), false);
					return 1;
				}))
				// Debug: every town house's blocks gone (by layer), logged.
				.then(Commands.literal("damage").requires(source -> source.hasPermission(2)).executes(ctx -> {
					var level = ctx.getSource().getLevel();
					int damaged = 0;
					for (Town town : Towns.all()) {
						for (Town.Plot plot : town.plots) {
							int[] missing = DrifterApi.missingByLayer(level, plot.planId, plot.origin, plot.rotation);
							if (missing == null || level.getChunkSource().getChunkNow(plot.origin.getX() >> 4, plot.origin.getZ() >> 4) == null) {
								continue;
							}

							int sum = java.util.Arrays.stream(missing).sum();
							if (sum > 8) {
								damaged++;
								LOGGER.info("[damage] {} {} at {} built {}: {} gone, by layer {}", town.name, plot.planId, plot.origin.toShortString(), plot.built, sum,
									java.util.Arrays.toString(missing));
							}
						}
					}

					int count = damaged;
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + count + " town houses with blocks gone (in the log)"), false);
					return 1;
				}))
				// Debug: to the nearest town's plaza (testing world-generated towns).
				.then(Commands.literal("go").requires(source -> source.hasPermission(2)).executes(ctx -> {
					var player = ctx.getSource().getPlayerOrException();
					Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), player.blockPosition());
					if (town == null) {
						ctx.getSource().sendFailure(Component.literal("No town made yet"));
						return 0;
					}

					player.teleportTo(ctx.getSource().getLevel(), town.center.getX() + 0.5, town.center.getY() + 1, town.center.getZ() + 0.5, player.getYRot(),
						player.getXRot());
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: to " + town.name), true);
					return 1;
				}))
				// Where world generation put towns not made yet (milestone 23 A1: made when a player comes near), nearest first.
				.then(Commands.literal("sites").executes(ctx -> {
					BlockPos from = BlockPos.containing(ctx.getSource().getPosition());
					var pending = TownSites.pending(ctx.getSource().getLevel().dimension().location().toString(), from);
					String making = TownSites.making();
					ctx.getSource().sendSuccess(() -> Component.literal(pending.size() + " town spots not made yet" + (making != null ? "; making one at " + making : "")
						+ ". /locate structure drifter_civ:town finds them too"), false);
					for (var site : pending.subList(0, Math.min(8, pending.size()))) {
						ctx.getSource().sendSuccess(() -> Component.literal("  " + site.x + ", " + site.z + " (" + Math.round(Math.hypot(site.x - from.getX(),
							site.z - from.getZ())) + " blocks away, " + site.households + " households" + TownSites.progress(ctx.getSource().getLevel(), site) + ")"), false);
					}

					return pending.size();
				})
					// Debug: the nearest made now, a step at a time as usual, player near or not.
					.then(Commands.literal("make").requires(source -> source.hasPermission(2)).executes(ctx -> {
						var site = TownSites.makeNearest(ctx.getSource().getLevel(), BlockPos.containing(ctx.getSource().getPosition()));
						if (site == null) {
							ctx.getSource().sendFailure(Component.literal("No town spot near (or villages aren't replaced: the setting)"));
							return 0;
						}

						ctx.getSource().sendSuccess(() -> Component.literal("Debug: making the town at " + site.x + ", " + site.z + " now"), true);
						return 1;
					})))
				// Debug: the player running it did this in the nearest town, seen by the nearest of its people (theft, farms, murder).
				.then(Commands.literal("crime").requires(source -> source.hasPermission(2))
					.then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((c, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("theft", "farms", "murder", "massacre", "pardon"), builder))
						.executes(ctx -> {
							var player = ctx.getSource().getPlayerOrException();
							Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), player.blockPosition());
							String kind = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind");
							if (town == null || !Justice.debugCrime(ctx.getSource().getServer(), town, player, kind)) {
								ctx.getSource().sendFailure(Component.literal("No town (or none of its people) near you, or not a crime: theft, farms, murder, massacre (pardon: forgiven)"));
								return 0;
							}

							ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + kind + " in " + town.name + " by " + player.getGameProfile().getName()), true);
							return 1;
						})))
				// Debug: the nearest town holds its election now (those standing chosen, the vote at once).
				.then(Commands.literal("elect").requires(source -> source.hasPermission(2)).executes(ctx -> {
					Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
					if (town == null) {
						ctx.getSource().sendFailure(Component.literal("No town here"));
						return 0;
					}

					var server = ctx.getSource().getServer();
					town.nextElection = Chronicle.day(server);
					town.candidates.clear();
					Politics.check(server, town);
					long time = server.overworld().getDayTime() % 24000L;
					if (time < 1000L) {
						ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + town.name + " votes at 1000 (morning)"), true);
					}

					Politics.check(server, town);
					Towns.save(server);
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + town.name + " held its election (/drifterciv chronicle)"), true);
					return 1;
				}))
				// Debug: the nearest of the nearest town's people (not its leader) is its crook now, and steals or picks a fight soon.
				.then(Commands.literal("crook").requires(source -> source.hasPermission(2)).executes(ctx -> {
					Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
					String name = town == null ? null : Justice.debugCrook(ctx.getSource().getServer(), town, BlockPos.containing(ctx.getSource().getPosition()));
					if (name == null) {
						ctx.getSource().sendFailure(Component.literal("No town (with people loaded) here"));
						return 0;
					}

					ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + name + " is " + town.name + "'s crook (at it in a few seconds)"), true);
					return 1;
				})
					// Debug: the town throws its crook out now (as if caught the third time).
					.then(Commands.literal("out").executes(ctx -> {
						Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
						String name = town == null ? null : Justice.debugThrowOut(ctx.getSource().getServer(), town);
						if (name == null) {
							ctx.getSource().sendFailure(Component.literal("No town with a crook (loaded) here"));
							return 0;
						}

						ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + town.name + " threw " + name + " out"), true);
						return 1;
					})))
				// Debug: the nearest town's people think this much less of its leader (to see unrest come of it).
				.then(Commands.literal("sour").requires(source -> source.hasPermission(2))
					.then(Commands.argument("points", IntegerArgumentType.integer(1, 200)).executes(ctx -> {
						Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
						var group = town == null || town.group == null ? null : DrifterApi.groupById(town.group);
						if (group == null || town.leader == null) {
							ctx.getSource().sendFailure(Component.literal("No town (with a leader) here"));
							return 0;
						}

						int points = IntegerArgumentType.getInteger(ctx, "points");
						group.members.stream().filter(id -> !id.equals(town.leader)).forEach(id -> Unrest.change(town, id, -points));
						Towns.save(ctx.getSource().getServer());
						ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + Unrest.status(town, group)), true);
						return 1;
					})))
				// Debug: the nearest town's unrest today, at once (now), or one of its people plotting against the leader (plot).
				.then(Commands.literal("unrest").requires(source -> source.hasPermission(2))
					.then(Commands.argument("what", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((c, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("now", "plot"), builder))
						.executes(ctx -> {
							Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
							var group = town == null || town.group == null ? null : DrifterApi.groupById(town.group);
							if (group == null || town.leader == null) {
								ctx.getSource().sendFailure(Component.literal("No town (with a leader) here"));
								return 0;
							}

							var server = ctx.getSource().getServer();
							String what = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "what");
							String result = "plot".equals(what) ? Unrest.debugPlot(server, town, group) : Unrest.today(server, town, group, Chronicle.day(server), true);
							Towns.save(server);
							ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + town.name + ": " + result), true);
							return 1;
						})))
				// Debug: the nearest town owes each of its people this many emeralds' worth of wages (to see them collected).
				.then(Commands.literal("owe").requires(source -> source.hasPermission(2))
					.then(Commands.argument("emeralds", IntegerArgumentType.integer(1, 10)).executes(ctx -> {
						Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
						var group = town == null || town.group == null ? null : DrifterApi.groupById(town.group);
						if (group == null) {
							ctx.getSource().sendFailure(Component.literal("No town here"));
							return 0;
						}

						int emeralds = IntegerArgumentType.getInteger(ctx, "emeralds");
						group.members.forEach(id -> town.owed.merge(id, emeralds * 6.0, Double::sum));
						Towns.save(ctx.getSource().getServer());
						ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + town.name + " owes each of its people " + emeralds + " emeralds' worth"), true);
						return 1;
					})))
				// Debug: the nearest founded town's store, then its stall, put up at once where its people were building it (to
				// test what comes after without waiting days).
				.then(Commands.literal("finish").requires(source -> source.hasPermission(2)).executes(ctx -> {
					Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
					var server = ctx.getSource().getServer();
					Town.Plot plot = town == null || town.spawned ? null : !town.storeBuilt ? town.storePlot : !town.stallBuilt ? town.stallPlot : null;
					if (plot == null) {
						ctx.getSource().sendFailure(Component.literal("No store or stall being built in a town founded by its people here"));
						return 0;
					}

					DrifterApi.placeHouse(ctx.getSource().getLevel(), plot.planId, plot.origin, plot.rotation, "oak", null);
					if (!town.storeBuilt) {
						TownFounding.storeBuilt(server, town);
					} else {
						Market.stallBuilt(server, town);
					}

					Towns.save(server);
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + town.name + "'s " + (town.stallBuilt ? "stall" : "store") + " put up at "
						+ plot.origin.toShortString()), true);
					return 1;
				}))
				// Debug: the nearest founded town's store and stall count as up (not built), so its plaza comes next.
				.then(Commands.literal("plaza").requires(source -> source.hasPermission(2)).executes(ctx -> {
					Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
					if (town == null || town.spawned) {
						ctx.getSource().sendFailure(Component.literal("No town founded by its people here"));
						return 0;
					}

					town.storeBuilt = true;
					town.stallBuilt = true;
					Towns.save(ctx.getSource().getServer());
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + town.name + "'s store and stall count as up; its plaza is next"), true);
					return 1;
				}))
				// Debug: a city made here at once (40 to 80 households, a hundred people at most).
				.then(Commands.literal("city").requires(source -> source.hasPermission(2))
					.then(Commands.argument("households", IntegerArgumentType.integer(TownBuilder.CITY, 80)).executes(ctx -> {
						var source = ctx.getSource();
						TownBuilder.Result result = TownBuilder.city(source.getLevel(), BlockPos.containing(source.getPosition()),
							IntegerArgumentType.getInteger(ctx, "households"));
						source.sendSuccess(() -> Component.literal("Debug: " + result.message()), true);
						return 1;
					})))
				// Debug: a raid on the nearest town, as if you'd walked in with Bad Omen.
				.then(Commands.literal("raid").requires(source -> source.hasPermission(2)).executes(ctx -> {
					boolean in = TownRaids.debugRaid(ctx.getSource().getPlayerOrException());
					ctx.getSource().sendSuccess(() -> Component.literal(in ? "Debug: Bad Omen given; a raid's coming" : "Debug: Bad Omen given (walk into a town)"), true);
					return 1;
				}))
				// Debug: how many are at the nearest town's store now (0.23.30: the rush to it when a town is made).
				// Debug (0.23.35, crime tests): the one running it is put beside the nearest town's bell, pen, a horse of its
				// people's or its leader; the animal, horse or leader there gets a tag for selectors (town_animal, town_horse,
				// town_leader).
				.then(Commands.literal("spot").requires(source -> source.hasPermission(2))
					.then(Commands.argument("what", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
						var source = ctx.getSource();
						String what = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "what");
						var level = source.getLevel();
						Town town = Towns.nearest(level.dimension().location().toString(), BlockPos.containing(source.getPosition()));
						var group = town == null || town.group == null ? null : com.dinglebob.drifter.api.DrifterApi.groupById(town.group);
						if (town == null || group == null || source.getEntity() == null) {
							source.sendFailure(Component.literal("No town near (or no one to put there)"));
							return 0;
						}

						net.minecraft.world.entity.Entity thing = null;
						BlockPos at = null;
						switch (what) {
							case "bell" -> at = TownBells.bell(town, level);
							// (The middle of the nearest other town: a second one to test in.)
							case "town2" -> {
								Town other = Towns.all().stream().filter(t -> t != town && t.dimension.equals(town.dimension))
									.min(java.util.Comparator.comparingDouble(t -> t.center.distSqr(town.center))).orElse(null);
								if (other != null) {
									// (Loaded first: an unloaded chunk's heightmap is the bottom of the world.)
									level.getChunk(other.center.getX() >> 4, other.center.getZ() >> 4);
									at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, other.center);
								}
							}
							case "leader" -> thing = town.leader == null ? null : TownFounding.loaded(source.getServer(), town.leader);
							case "pen" -> thing = town.pen == null ? null : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,
								new net.minecraft.world.phys.AABB(town.pen).inflate(10.0), a -> a.isAlive() && !(a instanceof net.minecraft.world.entity.OwnableEntity o
									&& o.getOwnerUUID() != null)).stream().findFirst().orElse(null);
							case "horse" -> thing = level.getEntitiesOfClass(net.minecraft.world.entity.animal.horse.AbstractHorse.class,
								new net.minecraft.world.phys.AABB(town.center).inflate(160.0), h -> h.isAlive() && h.getOwnerUUID() != null
									&& group.members.contains(h.getOwnerUUID())).stream().findFirst().orElse(null);
							default -> {
							}
						}

						if (thing != null) {
							thing.addTag(switch (what) {
								case "leader" -> "town_leader";
								case "pen" -> "town_animal";
								default -> "town_horse";
							});
							at = thing.blockPosition();
						}

						if (at == null) {
							source.sendFailure(Component.literal("No " + what + " found in " + town.name));
							return 0;
						}

						BlockPos stand = at.offset(2, 0, 0);
						for (BlockPos near : new BlockPos[] {at.offset(2, 0, 0), at.offset(-2, 0, 0), at.offset(0, 0, 2), at.offset(0, 0, -2), at.offset(2, 1, 0),
							at.offset(-2, 1, 0), at.offset(0, 1, 2), at.offset(0, 1, -2)}) {
							if (level.getBlockState(near).getCollisionShape(level, near).isEmpty() && level.getBlockState(near.above()).getCollisionShape(level, near.above()).isEmpty()
								&& !level.getBlockState(near.below()).getCollisionShape(level, near.below()).isEmpty()) {
								stand = near;
								break;
							}
						}
						source.getEntity().teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
						String said = town.name + ": " + what + " at " + at.toShortString() + (thing == null ? "" : " (" + thing.getName().getString() + ")");
						source.sendSuccess(() -> Component.literal(said), false);
						return 1;
					})))
				// Debug (0.23.40): who of the nearest town is out tonight and till when (night life), to the log and a summary.
				.then(Commands.literal("night").requires(source -> source.hasPermission(2)).executes(ctx -> {
					var source = ctx.getSource();
					Town town = Towns.nearest(source.getLevel().dimension().location().toString(), BlockPos.containing(source.getPosition()));
					var group = town == null || town.group == null ? null : com.dinglebob.drifter.api.DrifterApi.groupById(town.group);
					if (group == null) {
						source.sendFailure(Component.literal("No town near"));
						return 0;
					}

					int people = 0;
					int out = 0;
					int late = 0;
					for (java.util.UUID id : group.members) {
						DrifterEntity member = TownFounding.loaded(source.getServer(), id);
						if (member == null) {
							continue;
						}

						people++;
						long bed = TownDay.bedtime(town, member);
						if (bed > 0) {
							out++;
							late += bed >= 18000L ? 1 : 0;
						}

						DrifterCivilizations.LOGGER.info("[night] {}: {} (style {})", member.getName().getString(), bed > 0 ? "out till " + bed : "in with the sun",
							member.getDrifterBrain().nightStyle());
					}

					String said = town.name + (Towns.city(town) ? " (a city)" : "") + ": " + out + " of " + people + " out tonight, " + late + " past midnight";
					DrifterCivilizations.LOGGER.info("[night] {}", said);
					source.sendSuccess(() -> Component.literal(said), false);
					return 1;
				}))
				// Debug (0.23.40): the nearest town's pen, its fence line column by column (the blocks over the ground), to the log.
				.then(Commands.literal("pendump").requires(source -> source.hasPermission(2)).executes(ctx -> {
					var source = ctx.getSource();
					Town town = Towns.nearest(source.getLevel().dimension().location().toString(), BlockPos.containing(source.getPosition()));
					if (town == null || town.pen == null) {
						source.sendFailure(Component.literal("No town pen near"));
						return 0;
					}

					DrifterCivilizations.LOGGER.info("[pendump] {}: pen at {}", town.name, town.pen.toShortString());
					for (BlockPos column : TownBuilder.penRingFor(town.pen)) {
						StringBuilder line = new StringBuilder();
						for (int y = town.pen.getY() - 3; y <= town.pen.getY() + 5; y++) {
							var state = source.getLevel().getBlockState(column.atY(y));
							String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
							if (!state.isAir()) {
								line.append(y).append('=').append(id.replace("_fence_gate", "GATE").replace("_fence", "FENCE"))
									.append(state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock ? (state.getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN) ? "(open)" : "(shut)") : "").append(' ');
							}
						}

						DrifterCivilizations.LOGGER.info("[pendump] {},{}: {}", column.getX(), column.getZ(), line);
					}

					// (Inside it too: whatever's over its floor.)
					for (int x = town.pen.getX() - 4; x <= town.pen.getX() + 4; x++) {
						StringBuilder line = new StringBuilder();
						for (int z = town.pen.getZ() - 4; z <= town.pen.getZ() + 4; z++) {
							for (int y = town.pen.getY() - 1; y <= town.pen.getY() + 2; y++) {
								var state = source.getLevel().getBlockState(new BlockPos(x, y, z));
								if (!state.isAir() && !state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) && !state.is(net.minecraft.world.level.block.Blocks.DIRT)) {
									line.append(x).append(',').append(y).append(',').append(z).append('=')
										.append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()).append(' ');
								}
							}
						}

						if (line.length() > 0) {
							DrifterCivilizations.LOGGER.info("[pendump] inside x {}: {}", x, line);
						}
					}

					source.sendSuccess(() -> Component.literal("Pen dumped to the log"), false);
					return 1;
				}))
				.then(Commands.literal("crowd").requires(source -> source.hasPermission(2)).executes(ctx -> {
					var source = ctx.getSource();
					Town town = Towns.nearest(source.getLevel().dimension().location().toString(), BlockPos.containing(source.getPosition()));
					if (town == null || town.store.isEmpty()) {
						source.sendFailure(Component.literal("No town with a store"));
						return 0;
					}

					// (Inside it: the room its chests stand round, not the plaza, the stall or the fields outside its walls.)
					int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
					for (BlockPos chest : town.store) {
						x0 = Math.min(x0, chest.getX());
						y0 = Math.min(y0, chest.getY());
						z0 = Math.min(z0, chest.getZ());
						x1 = Math.max(x1, chest.getX());
						y1 = Math.max(y1, chest.getY());
						z1 = Math.max(z1, chest.getZ());
					}

					// (Standing at one of its chests: the block beside it, not the plaza or the far side of its wall.)
					java.util.Set<DrifterEntity> there = new java.util.HashSet<>();
					for (BlockPos chest : town.store) {
						there.addAll(source.getLevel().getEntitiesOfClass(DrifterEntity.class, new net.minecraft.world.phys.AABB(chest).inflate(1.0, 0.5, 1.0)));
					}

					String box = (x1 - x0 + 1) + "x" + (z1 - z0 + 1) + " (" + town.store.size() + " chests)";
					String names = there.stream().map(one -> one.getName().getString()).sorted().collect(java.util.stream.Collectors.joining(", "));
					source.sendSuccess(() -> Component.literal("[crowd] " + town.name + ": " + there.size() + " at the store (" + names + ") " + box), false);
					return there.size();
				}))
				// Debug: the nearest town's alarm bell rung (0.23.30), the trouble where you stand, for two minutes; or stopped.
				.then(Commands.literal("alarm").requires(source -> source.hasPermission(2)).executes(ctx -> {
					var source = ctx.getSource();
					Town town = Towns.nearest(source.getLevel().dimension().location().toString(), BlockPos.containing(source.getPosition()));
					if (town == null) {
						source.sendFailure(Component.literal("No town"));
						return 0;
					}

					BlockPos at = source.getLevel().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
						BlockPos.containing(source.getPosition()));
					TownBells.alarm(source.getServer(), town, "a test", java.util.List.of("ALARM! (a test)", "the bell! everyone, a test!"), () -> at, () -> true,
						20L * 60 * 2);
					source.sendSuccess(() -> Component.literal("Debug: " + town.name + "'s alarm raised (two minutes, the trouble here)"), true);
					return 1;
				}).then(Commands.literal("stop").executes(ctx -> {
					var source = ctx.getSource();
					Town town = Towns.nearest(source.getLevel().dimension().location().toString(), BlockPos.containing(source.getPosition()));
					boolean was = town != null && TownBells.stopAlarm(town);
					source.sendSuccess(() -> Component.literal(was ? "Debug: " + town.name + "'s alarm stopped" : "Debug: no alarm on"), true);
					return 1;
				})))
				// Debug: the ruins of a town that failed, made here at once (as world generation now and then makes one).
				.then(Commands.literal("ruin").requires(source -> source.hasPermission(2)).executes(ctx -> {
					var source = ctx.getSource();
					TownBuilder.Result result = TownBuilder.ruin(source.getLevel(), BlockPos.containing(source.getPosition()));
					source.sendSuccess(() -> Component.literal("Debug: " + result.message()), true);
					return 1;
				}))
				// Debug: the nearest town looked over for what the user found wrong (0.23.29): torches in its mine, beds in the way,
				// workshops out of doors, houses on its kept ground.
				.then(Commands.literal("check").requires(source -> source.hasPermission(2)).executes(ctx -> {
					Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
					if (town == null) {
						ctx.getSource().sendFailure(Component.literal("No town"));
						return 0;
					}

					String report = TownMine.check(ctx.getSource().getLevel(), town);
					ctx.getSource().sendSuccess(() -> Component.literal(town.name + ": " + report), false);
					DrifterCivilizations.LOGGER.info("[check] {}: {}", town.name, report);
					return 1;
				}))
				// Debug: to the nearest town's mine, its mouth or its hub at the bottom (facing down its stairs, or along its corridor).
				.then(Commands.literal("goto").requires(source -> source.hasPermission(2))
					.then(Commands.argument("place", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((c, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("mine", "hub"), builder))
						.executes(ctx -> {
							var player = ctx.getSource().getPlayerOrException();
							Town town = Towns.nearest(player.level().dimension().location().toString(), player.blockPosition());
							String place = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "place");
							BlockPos to = town == null ? null : place.equals("hub") ? town.mineBottom : place.equals("mine") ? town.mine : null;
							if (to == null || town.mineWay == null) {
								ctx.getSource().sendFailure(Component.literal("The nearest town has no mine (places: mine, hub)"));
								return 0;
							}

							float yaw = town.mineWay.toYRot();
							player.teleportTo(player.serverLevel(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, yaw, place.equals("hub") ? 10.0F : 35.0F);
							ctx.getSource().sendSuccess(() -> Component.literal("Debug: at " + town.name + "'s " + place + " (" + to.toShortString() + ")"), true);
							return 1;
						})))
				// Debug (with Bountiful): the nearest town's bounty board right-clicked with what's in your hand, from where you are
				// (an empty hand opens it, a paper is turned in); "paper": its first notice's paper from the board, in your hand.
				.then(Commands.literal("bounty").requires(source -> source.hasPermission(2)).executes(ctx -> {
					var player = ctx.getSource().getPlayerOrException();
					Town town = Towns.nearest(player.level().dimension().location().toString(), player.blockPosition());
					if (town == null || !TownBounties.click(player, town)) {
						ctx.getSource().sendFailure(Component.literal("The nearest town has no bounty board (or Bountiful isn't here)"));
						return 0;
					}

					return 1;
				}).then(Commands.literal("paper").executes(ctx -> {
					var player = ctx.getSource().getPlayerOrException();
					Town town = Towns.nearest(player.level().dimension().location().toString(), player.blockPosition());
					ItemStack paper = town == null ? ItemStack.EMPTY : TownBounties.paperOnBoard(ctx.getSource().getServer(), town);
					if (paper.isEmpty()) {
						ctx.getSource().sendFailure(Component.literal("No notice on the nearest town's bounty board"));
						return 0;
					}

					player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, paper);
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + town.name + "'s notice paper in your hand"), true);
					return 1;
				})).then(Commands.literal("goods").executes(ctx -> {
					// Debug: what the nearest town's first notice asks for, given you.
					var player = ctx.getSource().getPlayerOrException();
					Town town = Towns.nearest(player.level().dimension().location().toString(), player.blockPosition());
					var notice = town == null || town.notices.isEmpty() ? null : town.notices.get(0);
					if (notice == null) {
						ctx.getSource().sendFailure(Component.literal("No notice up in the nearest town"));
						return 0;
					}

					player.getInventory().placeItemBackInInventory(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
						net.minecraft.resources.ResourceLocation.parse(notice.item)), notice.count));
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: given what " + town.name + "'s notice asks for (" + notice.count + " " + notice.item + ")"), true);
					return 1;
				})))
				// Debug: the nearest of the nearest town's store chests opened, as if you'd right-clicked it (a real click: whoever
				// sees you at it sees you); "take": the first few stacks taken out of the chest you have open, and it shut.
				.then(Commands.literal("steal").requires(source -> source.hasPermission(2)).executes(ctx -> {
					var player = ctx.getSource().getPlayerOrException();
					Town town = Towns.nearest(player.level().dimension().location().toString(), player.blockPosition());
					BlockPos chest = town == null ? null : town.store.stream().min(java.util.Comparator.comparingDouble(pos -> pos.distSqr(player.blockPosition())))
						.orElse(null);
					if (chest == null) {
						ctx.getSource().sendFailure(Component.literal("The nearest town has no store chests"));
						return 0;
					}

					// (Out of reach, the chest shuts again at once: in front of it first, facing it.)
					if (player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(chest)) > 4.0) {
						var state = player.serverLevel().getBlockState(chest);
						net.minecraft.core.Direction facing = state.hasProperty(net.minecraft.world.level.block.ChestBlock.FACING)
							? state.getValue(net.minecraft.world.level.block.ChestBlock.FACING) : net.minecraft.core.Direction.NORTH;
						BlockPos front = chest.relative(facing);
						player.teleportTo(player.serverLevel(), front.getX() + 0.5, front.getY(), front.getZ() + 0.5, facing.getOpposite().toYRot(), 30.0F);
					}

					var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(chest), net.minecraft.core.Direction.UP, chest, false);
					player.gameMode.useItemOn(player, player.serverLevel(), ItemStack.EMPTY, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: opened " + town.name + "'s store chest at " + chest.toShortString()), true);
					return 1;
				}).then(Commands.literal("take").executes(ctx -> {
					var player = ctx.getSource().getPlayerOrException();
					if (!(player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu menu)) {
						ctx.getSource().sendFailure(Component.literal("No chest open"));
						return 0;
					}

					int took = 0;
					for (int slot = 0; slot < menu.getContainer().getContainerSize() && took < 3; slot++) {
						if (!menu.getContainer().getItem(slot).isEmpty()) {
							menu.quickMoveStack(player, slot);
							took++;
						}
					}

					player.closeContainer();
					int stacks = took;
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: took " + stacks + " stacks and shut the chest"), true);
					return 1;
				})))
				// Debug: what's in your hand to the nearest Drifter, as if you'd thrown it to it (a job, a notice; B5, D5).
				.then(Commands.literal("fill").requires(source -> source.hasPermission(2)).executes(ctx -> {
					// Debug: the nearest town's first notice filled, as if you'd brought its poster what it asked for.
					var player = ctx.getSource().getPlayerOrException();
					Town town = Towns.nearest(player.level().dimension().location().toString(), player.blockPosition());
					var notice = town == null || town.notices.isEmpty() ? null : town.notices.get(0);
					DrifterEntity poster = notice == null ? null : TownFounding.loaded(ctx.getSource().getServer(), notice.poster);
					if (poster == null) {
						ctx.getSource().sendFailure(Component.literal("No notice up in the nearest town (or its poster isn't loaded)"));
						return 0;
					}

					player.teleportTo(poster.getX() + 1.5, poster.getY(), poster.getZ());
					ItemStack goods = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(notice.item)),
						notice.count);
					poster.getInventory().add(goods.copy());
					com.dinglebob.drifter.api.DrifterEvents.RECEIVED.invoker().onReceived(poster, player, goods);
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: brought " + notice.posterName + " what its notice asked for"), true);
					return 1;
				}))
				.then(Commands.literal("hand").requires(source -> source.hasPermission(2)).then(Commands.argument("line", com.mojang.brigadier.arguments.StringArgumentType.word())
					.executes(ctx -> {
						// Debug: to the nearest town's Drifter of that line (enchanter, smith...), then what's in your hand to it.
						var player = ctx.getSource().getPlayerOrException();
						Town town = Towns.nearest(player.level().dimension().location().toString(), player.blockPosition());
						String line = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "line");
						DrifterEntity found = null;
						if (town != null) {
							for (var entry : town.lines.entrySet()) {
								DrifterEntity one = entry.getValue().equals(line) ? TownFounding.loaded(ctx.getSource().getServer(), entry.getKey()) : null;
								if (one != null) {
									found = one;
									break;
								}
							}
						}

						if (found == null) {
							ctx.getSource().sendFailure(Component.literal("No " + line + " in the nearest town"));
							return 0;
						}

						player.teleportTo(found.getX() + 1.5, found.getY(), found.getZ());
						return handOver(ctx.getSource(), player, found);
					})))
				.then(Commands.literal("hand").requires(source -> source.hasPermission(2)).executes(ctx -> {
					var player = ctx.getSource().getPlayerOrException();
					ItemStack held = player.getMainHandItem().copy();
					DrifterEntity nearest = null;
					for (DrifterEntity one : player.level().getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(8.0), DrifterEntity::isAlive)) {
						if (nearest == null || one.distanceTo(player) < nearest.distanceTo(player)) {
							nearest = one;
						}
					}

					if (held.isEmpty() || nearest == null) {
						ctx.getSource().sendFailure(Component.literal("Nothing in your hand, or no Drifter within 8 blocks"));
						return 0;
					}

					player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
					nearest.getInventory().add(held.copy());
					com.dinglebob.drifter.api.DrifterEvents.RECEIVED.invoker().onReceived(nearest, player, held);
					String to = nearest.getName().getString();
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: handed " + held.getCount() + " " + held.getHoverName().getString() + " to " + to), true);
					return 1;
				}))
				// The nearest town's house storage: how many chests, barrels and cabinets, how many empty, how full on average.
				.then(Commands.literal("chests").executes(ctx -> {
					var source = ctx.getSource();
					Town town = Towns.nearest(source.getLevel().dimension().location().toString(), BlockPos.containing(source.getPosition()));
					if (town == null) {
						source.sendFailure(Component.literal("No town here"));
						return 0;
					}

					int containers = 0;
					int empty = 0;
					int stacks = 0;
					int valuables = 0;
					int cabinets = 0;
					int kinds = 0;
					for (Town.Plot plot : town.plots) {
						if (!plot.built || plot.sizeX == 0) {
							continue;
						}

						int x1 = plot.origin.getX() + Math.max(plot.sizeX, plot.sizeZ);
						int z1 = plot.origin.getZ() + Math.max(plot.sizeX, plot.sizeZ);
						for (BlockPos pos : BlockPos.betweenClosed(plot.origin.getX() - 2, plot.origin.getY() - 3, plot.origin.getZ() - 2, x1 + 2, plot.origin.getY() + 14, z1 + 2)) {
							var entity = source.getLevel().getBlockEntity(pos);
							if (entity instanceof net.minecraft.world.Container pantry && net.minecraft.core.registries.BuiltInRegistries.BLOCK
								.getKey(source.getLevel().getBlockState(pos).getBlock()).getPath().contains("cabinet")) {
								cabinets++;
								java.util.Set<net.minecraft.world.item.Item> items = new java.util.HashSet<>();
								for (int slot = 0; slot < pantry.getContainerSize(); slot++) {
									if (!pantry.getItem(slot).isEmpty()) {
										items.add(pantry.getItem(slot).getItem());
									}
								}

								kinds += items.size();
							}

							if (entity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity || entity instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity) {
								var container = (net.minecraft.world.Container)entity;
								containers++;
								int here = 0;
								for (int slot = 0; slot < container.getContainerSize(); slot++) {
									var stack = container.getItem(slot);
									if (!stack.isEmpty()) {
										here++;
										if (DrifterApi.worth(stack) >= 3.0) {
											valuables++;
										}
									}
								}

								stacks += here;
								if (here == 0) {
									empty++;
								}
							}
						}
					}

					int all = containers;
					int none = empty;
					int filled = stacks;
					int worth = valuables;
					int pantries = cabinets;
					int foods = kinds;
					source.sendSuccess(() -> Component.literal(town.name + "'s houses: " + all + " chests and barrels (each half of a double chest counted), " + none
						+ " empty, " + (all == 0 ? 0 : filled / all) + " stacks each on average, " + worth + " stacks of valuables (gold, gems, money); "
						+ pantries + " kitchen cabinets, " + (pantries == 0 ? 0 : foods / pantries) + " kinds of ingredient each"), false);
					return all;
				}))
				// Repair: tops of tall plants (and doors) floating with no foot under them near here, taken off (town and road
				// building before 0.23.39 left them; Thai Delight's lime crashed the game on one).
				.then(Commands.literal("halves").requires(source -> source.hasPermission(2)).executes(ctx -> strayHalves(ctx, 64))
					.then(Commands.argument("radius", IntegerArgumentType.integer(4, 160)).executes(ctx -> strayHalves(ctx, IntegerArgumentType.getInteger(ctx,
						"radius")))))
				// Debug: the nearest town seen from above, saved as a picture in the server's town-maps folder.
				.then(Commands.literal("map").requires(source -> source.hasPermission(2)).executes(ctx -> townMap(ctx, 0))
					.then(Commands.argument("radius", IntegerArgumentType.integer(16, 1024)).executes(ctx -> townMap(ctx, IntegerArgumentType.getInteger(ctx,
						"radius")))))
				// Debug: the shape every town made from now on has (crossroads, main_street, winding, river, harbour, hill, ring, grid), or "any" (each its own).
				.then(Commands.literal("shape").requires(source -> source.hasPermission(2))
					.then(Commands.argument("shape", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((ctx, builder) -> {
							for (TownBuilder.Shape shape : TownBuilder.Shape.values()) {
								builder.suggest(shape.id);
							}

							return builder.suggest("any").buildFuture();
						})
						.executes(ctx -> {
							String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "shape");
							TownBuilder.Shape shape = TownBuilder.Shape.byId(id);
							if (shape == null && !id.equals("any")) {
								ctx.getSource().sendFailure(Component.literal("No such shape: crossroads, main_street, winding, river, harbour, hill, ring, grid or any"));
								return 0;
							}

							TownBuilder.forcedShape = shape;
							ctx.getSource().sendSuccess(() -> Component.literal("Debug: towns made from now on: " + (shape == null ? "each its own shape"
								: shape.id + " (" + shape.what + ")") + " (till the server restarts)"), true);
							return 1;
						})))
				.then(Commands.literal("spawn").requires(source -> source.hasPermission(2))
					.executes(ctx -> spawnTown(ctx, TownBuilder.DEFAULT_HOUSEHOLDS, null))
					.then(Commands.argument("households", IntegerArgumentType.integer(2, TownBuilder.MAX_HOUSEHOLDS))
						.executes(ctx -> spawnTown(ctx, IntegerArgumentType.getInteger(ctx, "households"), null))
						.then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
							.executes(ctx -> spawnTown(ctx, IntegerArgumentType.getInteger(ctx, "households"),
								com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name")))))))
			.then(Commands.literal("shop").executes(ctx -> {
				// (Debug: the nearest town's keeper's trading screen, as if right-clicked.)
				var player = ctx.getSource().getPlayerOrException();
				Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
				var keeper = town == null || town.keeper == null ? null : TownFounding.loaded(ctx.getSource().getServer(), town.keeper);
				if (keeper == null || !DrifterApi.openShop(keeper, player)) {
					ctx.getSource().sendFailure(Component.literal("No market stall open here (or nothing on offer)"));
					return 0;
				}

				return 1;
			}))
			.then(Commands.literal("prices").executes(ctx -> {
				Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
				if (town == null) {
					ctx.getSource().sendFailure(Component.literal("No town here yet"));
					return 0;
				}

				Market.prices(ctx.getSource().getServer(), town).forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(line), false));
				Trades.shops(ctx.getSource().getServer(), town).forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(line), false));
				return 1;
			}))
			.then(Commands.literal("towns").executes(ctx -> {
				var towns = Towns.all();
				ctx.getSource().sendSuccess(() -> Component.literal(towns.isEmpty() ? "No towns yet (/drifterciv town spawn makes one, for testing)"
					: towns.size() + (towns.size() == 1 ? " town:" : " towns:")), false);
				for (Town town : towns) {
					ctx.getSource().sendSuccess(() -> Component.literal("  " + town.name + " at " + town.center.getX() + ", " + town.center.getZ() + ", "
						+ town.plots.size() + " houses, founded day " + town.foundedDay + (town.spawned ? " (made ready)" : "")), false);
				}

				return towns.size();
			}))
			// "One island" mode.
			.then(Commands.literal("island")
				.executes(ctx -> {
					Island.status(ctx.getSource().getServer()).forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(line), false));
					return 1;
				})
				.then(Commands.literal("start").requires(source -> source.hasPermission(2))
					.executes(ctx -> island(ctx, Island.DEFAULT_COUNT, Island.DEFAULT_RADIUS))
					.then(Commands.argument("count", IntegerArgumentType.integer(1, Island.MAX_COUNT))
						.executes(ctx -> island(ctx, IntegerArgumentType.getInteger(ctx, "count"), Island.DEFAULT_RADIUS))
						.then(Commands.argument("size", IntegerArgumentType.integer(16, Island.MAX_RADIUS))
							.executes(ctx -> island(ctx, IntegerArgumentType.getInteger(ctx, "count"), IntegerArgumentType.getInteger(ctx, "size"))))))
				.then(Commands.literal("stop").requires(source -> source.hasPermission(2)).executes(ctx -> {
					boolean stopped = Island.stop(ctx.getSource().getServer());
					ctx.getSource().sendSuccess(() -> Component.literal(stopped ? "The island is left to itself (its land unloads as usual)"
						: "No island going"), true);
					return stopped ? 1 : 0;
				})))));
		LOGGER.info("Drifter: Civilizations {} loaded, on Drifter {} (API {})", version(), DrifterApi.version(), DrifterApi.API_VERSION);
	}

	/** Debug: the nearest town (or, with a radius, the land round here) from above, as a picture in the server's town-maps folder. */
	private static int strayHalves(CommandContext<CommandSourceStack> ctx, int radius) {
		long start = System.nanoTime();
		int found = TownBuilder.strayHalves(ctx.getSource().getLevel(), BlockPos.containing(ctx.getSource().getPosition()), radius);
		DrifterCivilizations.LOGGER.info("[town] floating plant tops within {}: {} taken off in {} ms", radius, found, (System.nanoTime() - start) / 1_000_000);
		ctx.getSource().sendSuccess(() -> Component.literal(found == 0 ? "No floating plant tops within " + radius + " blocks."
			: "Took off " + found + " floating plant top" + (found == 1 ? "" : "s") + " within " + radius + " blocks."), true);
		return found;
	}

	private static int townMap(CommandContext<CommandSourceStack> ctx, int radius) {
		var source = ctx.getSource();
		Town town = Towns.nearest(source.getLevel().dimension().location().toString(), BlockPos.containing(source.getPosition()));
		if (town == null && radius == 0) {
			source.sendFailure(Component.literal("No town near"));
			return 0;
		}

		try {
			BlockPos center = radius > 0 ? BlockPos.containing(source.getPosition()) : town.center;
			int size = radius > 0 ? radius : town.plots.size() >= TownBuilder.CITY ? TownBuilder.CITY_REACH + 8 : 112;
			String name = radius > 0 ? "land_" + center.getX() + "_" + center.getZ() : town.name;
			java.nio.file.Path file = TownMap.draw(source.getLevel(), center, size, name);
			source.sendSuccess(() -> Component.literal("Debug: a map saved to " + file), true);
			return 1;
		} catch (java.io.IOException e) {
			source.sendFailure(Component.literal("Couldn't save the map: " + e.getMessage()));
			return 0;
		}
	}

	private static int spawnTown(CommandContext<CommandSourceStack> ctx, int households, @Nullable String name) {
		CommandSourceStack source = ctx.getSource();
		long started = System.currentTimeMillis();
		TownBuilder.Result result = TownBuilder.spawn(source.getLevel(), BlockPos.containing(source.getPosition()), households, name);
		long seconds = (System.currentTimeMillis() - started) / 1000L;
		if (result.town() == null) {
			source.sendFailure(Component.literal(result.message()));
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Made the town of " + result.message() + " (" + seconds + " s). /drifterciv town tells about it"), true);
		return result.town().plots.size();
	}

	/** The nearest town's uniform (its set changed first, debug, if {@code set} isn't null). */
	private static int uniform(CommandContext<CommandSourceStack> ctx, @Nullable String set) {
		Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
		if (town == null) {
			ctx.getSource().sendFailure(Component.literal("No town here"));
			return 0;
		}

		if (set != null) {
			town.uniformSet = set;
		}

		List<String> lines = TownUniforms.describe(ctx.getSource().getServer(), town);
		// (Its set chosen just now, a town from before uniforms: kept.)
		Towns.save(ctx.getSource().getServer());
		lines.forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(line), false));
		return lines.size();
	}

	/** The nearest town: its name, when it was founded, its people, its store. */
	/** The nearest town's dealings: each town it knows of, how they stand, their pacts. */
	private static int relations(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
		Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
		if (town == null) {
			ctx.getSource().sendFailure(Component.literal("No town here"));
			return 0;
		}

		var all = Diplomacy.of(town);
		ctx.getSource().sendSuccess(() -> Component.literal(town.name + (all.isEmpty() ? " knows of no other town yet" : " and the towns it knows of:")), false);
		for (var r : all) {
			Town other = Diplomacy.byId(r.other(town.id).toString());
			if (other == null) {
				continue;
			}

			String pacts = r.pacts.isEmpty() ? "no pacts" : String.join(", ", r.pacts.stream().map(p -> p.words).toList());
			long day = Chronicle.day(ctx.getSource().getServer());
			String war = r.war ? "AT WAR since day " + r.warSinceDay + " (dead: " + town.name + " " + r.losses(town.id) + ", " + other.name + " "
				+ r.losses(other.id) + ")" : r.truceUntilDay > day ? "truce till day " + r.truceUntilDay : null;
			String owed = r.owedBy == null || r.owed + r.inTransit == 0 ? null : (r.owedBy.equals(town.id) ? "owes " : "is owed ") + (r.owed + r.inTransit)
				+ " emeralds";
			ctx.getSource().sendSuccess(() -> Component.literal("  " + other.name + ": " + Diplomacy.word(r.standing) + " (" + Math.round(r.standing) + "), "
				+ (war != null ? war + ", " : "") + pacts + (owed != null ? ", " + owed : "") + (r.insults > 0 ? ", " + r.insults + " insult"
				+ (r.insults == 1 ? "" : "s") : "")), false);
		}

		return 1;
	}

	/** Debug: the nearest town at war with the named one now, or at a white peace now. */
	private static int warOrPeace(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx, boolean war) {
		var server = ctx.getSource().getServer();
		Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), BlockPos.containing(ctx.getSource().getPosition()));
		Town other = named(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "other"));
		if (town == null || other == null || other == town) {
			ctx.getSource().sendFailure(Component.literal("No such other town"));
			return 0;
		}

		var r = Diplomacy.meet(server, town, other, 0.0, null);
		if (war == r.war) {
			ctx.getSource().sendFailure(Component.literal(war ? "Already at war" : "Not at war"));
			return 0;
		}

		if (war) {
			Diplomacy.war(server, town, other, r);
		} else {
			Diplomacy.peace(server, town, other, r, null, 0);
		}

		ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + town.name + " and " + other.name + (war ? ": at war" : ": at peace")
			+ " (see the chronicle)"), true);
		return 1;
	}

	@org.jetbrains.annotations.Nullable
	private static Town named(String name) {
		return Towns.all().stream().filter(t -> t.name.equalsIgnoreCase(name)).findFirst().orElse(null);
	}

	/** (Debug.) What's in the player's hand to this Drifter, as if thrown to it. */
	private static int handOver(CommandSourceStack source, net.minecraft.server.level.ServerPlayer player, DrifterEntity drifter) {
		ItemStack held = player.getMainHandItem().copy();
		if (held.isEmpty()) {
			source.sendFailure(Component.literal("Nothing in your hand"));
			return 0;
		}

		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		drifter.getInventory().add(held.copy());
		com.dinglebob.drifter.api.DrifterEvents.RECEIVED.invoker().onReceived(drifter, player, held);
		String to = drifter.getName().getString();
		source.sendSuccess(() -> Component.literal("Debug: handed " + held.getCount() + " " + held.getHoverName().getString() + " to " + to), true);
		return 1;
	}

	private static int town(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		Town town = Towns.nearest(source.getLevel().dimension().location().toString(), BlockPos.containing(source.getPosition()));
		if (town == null) {
			source.sendSuccess(() -> Component.literal("No town here yet (/drifterciv town spawn makes one, for testing)"), false);
			return 0;
		}

		int alive = 0;
		List<String> people = new java.util.ArrayList<>();
		for (Town.Plot plot : town.plots) {
			List<java.util.UUID> living = new java.util.ArrayList<>();
			if (plot.owner != null) {
				living.add(plot.owner);
			}

			living.addAll(plot.residents);
			for (java.util.UUID one : living) {
				var record = DrifterApi.record(one);
				if (record != null) {
					people.add(record.name() + (record.status() == com.dinglebob.drifter.api.DrifterRecord.Status.ALIVE ? "" : " (" + record.status().name().toLowerCase(java.util.Locale.ROOT) + ")"));
					if (record.status() == com.dinglebob.drifter.api.DrifterRecord.Status.ALIVE) {
						alive++;
					}
				}
			}
		}

		int stocked = 0;
		for (BlockPos chest : town.store) {
			if (source.getLevel().getBlockEntity(chest) instanceof net.minecraft.world.Container container) {
				for (int slot = 0; slot < container.getContainerSize(); slot++) {
					stocked += container.getItem(slot).isEmpty() ? 0 : 1;
				}
			}
		}

		int alivePeople = alive;
		int stacks = stocked;
		long age = Chronicle.day(source.getServer()) - town.foundedDay;
		source.sendSuccess(() -> Component.literal(town.name + (town.spawned ? " (made ready, for testing)" : "") + ": founded day " + town.foundedDay
			+ " (" + age + (age == 1 ? " day" : " days") + " ago), " + town.plots.size() + " houses, " + alivePeople + " people alive; the store has "
			+ stacks + " stacks in " + town.store.size() + " chests"), false);
		source.sendSuccess(() -> Component.literal("  " + String.join(", ", people)), false);
		TownBuilder.Shape shape = TownBuilder.Shape.byId(town.shape);
		if (shape != null) {
			source.sendSuccess(() -> Component.literal("  Shape: " + shape.id.replace('_', ' ') + " (" + shape.what + ")"), false);
		}

		source.sendSuccess(() -> Component.literal("  Work: " + Trades.describe(town)), false);
		// Its life and culture (milestone 23, phase C): its character, market day, festivals, the latest comings and goings.
		long today = Chronicle.day(source.getServer());
		String character = TownCulture.character(source.getServer(), town);
		long toMarket = -1;
		for (long d = 0; d < TownEvents.MARKET_EVERY && town.stallBuilt; d++) {
			if (TownEvents.marketDay(town, today + d)) {
				toMarket = d;
				break;
			}
		}

		long toFestival = 0;
		while (toFestival < TownCulture.YEAR && TownCulture.festival(town, today + toFestival) == null) {
			toFestival++;
		}

		String festival = TownCulture.festival(town, today + toFestival);
		long market = toMarket;
		long festivalIn = toFestival;
		source.sendSuccess(() -> Component.literal("  Life: " + character + "; market day " + (market < 0 ? "none (no stall)" : market == 0 ? "today" : "in "
			+ market + (market == 1 ? " day" : " days")) + "; " + (festival == null ? "no festival" : TownCulture.name(festival) + (festivalIn == 0 ? " today"
			: " in " + festivalIn + " days")) + (town.shrine != null ? "; a shrine" : "") + (town.fire != null ? "; a gathering fire" : "")
			+ (town.newest != null ? "; newest: " + town.newest : "") + (town.lastLeft != null ? "; last to leave: " + town.lastLeft : "")
			+ (town.traditions.isEmpty() ? "" : "; customs: " + String.join(", ", TownTraditions.describe(town)))), false);
		// Where you stand with it (D1), and its notices (D5).
		if (source.getEntity() instanceof net.minecraft.world.entity.player.Player you) {
			String standing = TownReputation.describe(source.getServer(), town, you);
			source.sendSuccess(() -> Component.literal("  " + standing), false);
			String yours = Friends.inTown(town, you);
			if (yours != null) {
				source.sendSuccess(() -> Component.literal("  " + yours), false);
			}
		}

		List<String> posted = TownNotices.lines(town);
		if (!posted.isEmpty()) {
			source.sendSuccess(() -> Component.literal("  Notices: " + String.join(" | ", posted)), false);
		}

		// Its politics (step 5): the leader, the laws, the election.
		var politicsGroup = town.group == null ? null : DrifterApi.groupById(town.group);
		if (politicsGroup != null && town.leader != null) {
			String leaderName = politicsGroup.names.getOrDefault(town.leader, "?");
			String standing = town.candidates.size() > 1 ? "; standing: " + String.join(", ", town.candidates.stream()
				.map(id -> politicsGroup.names.getOrDefault(id, "?")).toList()) : "";
			String last = town.lastVotes.isEmpty() ? "" : "; last vote: " + String.join(", ", town.lastVotes.entrySet().stream()
				.map(entry -> entry.getKey() + " " + entry.getValue()).toList());
			source.sendSuccess(() -> Component.literal("  Leader: " + leaderName + " (since day " + town.leaderSince + "); laws: " + Politics.lawsLong(town)
				+ "; next election day " + town.nextElection + standing + last), false);
			List<String> wanted = new java.util.ArrayList<>();
			town.crimes.values().forEach(c -> {
				if (c.exiledUntil > 0) {
					wanted.add(c.name + " exiled till day " + c.exiledUntil);
				} else if (c.fine > 0) {
					wanted.add(c.name + " owes a fine of " + c.fine + " (" + c.crime + ")");
				}
			});
			if (!wanted.isEmpty()) {
				source.sendSuccess(() -> Component.literal("  Law: " + String.join("; ", wanted)), false);
			}

			source.sendSuccess(() -> Component.literal("  " + Unrest.status(town, politicsGroup)), false);
		}
		// A town founded by its people: who has a plot, whose house is up.
		var group = town.spawned || town.group == null ? null : DrifterApi.groupById(town.group);
		if (group != null) {
			List<String> households = new java.util.ArrayList<>();
			for (java.util.UUID member : group.members) {
				Town.Plot plot = town.plots.stream().filter(p -> member.equals(p.owner)).findFirst().orElse(null);
				Town.Plot lodging = town.plots.stream().filter(p -> p.residents.contains(member)).findFirst().orElse(null);
				String state = lodging != null ? "a room in " + group.names.getOrDefault(lodging.owner, "someone") + "'s house"
					: plot == null ? (town.plots.isEmpty() || !town.plots.get(0).built ? "building the first house" : "no plot yet")
					: (plot.built ? "house built" : "building") + " (" + plot.planId + " at " + plot.origin.toShortString() + ")"
						+ (plot.room > 1 ? ", room for " + plot.room + " households" : "");
				households.add(group.names.getOrDefault(member, "?") + (member.equals(group.leader) ? " (leader)" : "") + ": " + state);
			}

			source.sendSuccess(() -> Component.literal("  Households: " + String.join("; ", households)), false);
			String store = town.storeBuilt ? "built (" + town.store.size() + " chests)"
				: town.storePlot == null ? "not begun (once half the households have their houses)" : "being built at " + town.storePlot.origin.toShortString();
			source.sendSuccess(() -> Component.literal("  Store: " + store), false);
			String stall = town.stallBuilt ? "built" + (town.keeper != null ? ", kept by " + group.names.getOrDefault(town.keeper, "?") : "")
				+ (town.trades > 0 ? " (" + town.trades + " trades)" : "") : town.stallPlot == null ? "not begun (after the store)"
				: "being built at " + town.stallPlot.origin.toShortString();
			source.sendSuccess(() -> Component.literal("  Market stall: " + stall + " (/drifterciv prices)"), false);
			source.sendSuccess(() -> Component.literal("  Roads: four from the plaza and " + town.lanes.size() + " side lane" + (town.lanes.size() == 1 ? "" : "s")),
				false);
			List<String> plaza = new java.util.ArrayList<>();
			for (Town.Plot piece : town.plaza) {
				String what = piece.planId.equals(DrifterApi.wellPlan()) ? "well" : piece.planId.equals(DrifterApi.boardPlan()) ? "notice board" : "lamp post";
				plaza.add(what + (piece.sizeX == 0 ? ": no room" : piece.built ? " built at " + piece.origin.toShortString() : " being built at "
					+ piece.origin.toShortString()) + (what.equals("well") && piece.built ? (town.wellFilled ? ", with water" : ", waiting for water") : ""));
			}

			source.sendSuccess(() -> Component.literal("  Plaza: " + (plaza.isEmpty() ? "nothing yet (after the market stall, and the leader's old hut down)"
				: String.join("; ", plaza))), false);
			// Its money: the till, what it owes in wages and has paid, its stall's sales to its people.
			double inTill = 0.0;
			if (town.till() != null && source.getLevel().getBlockEntity(town.till()) instanceof net.minecraft.world.Container till) {
				for (int slot = 0; slot < till.getContainerSize(); slot++) {
					inTill += DrifterApi.moneyWorth(till.getItem(slot));
				}
			}

			double money = inTill;
			source.sendSuccess(() -> Component.literal("  Money: " + (town.till() == null ? "no till yet (the store's first chest)" : String.format(java.util.Locale.ROOT,
				"%.1f emeralds' worth in the till at %s", money / 6.0, town.till().toShortString())) + String.format(java.util.Locale.ROOT,
				"; wages owed %.1f, paid %.1f (emeralds' worth); %d sales to Drifters", Wages.owedAll(town) / 6.0, town.wagesPaid / 6.0, town.drifterTrades)), false);
		}

		return alivePeople;
	}

	@Nullable
	public static MinecraftServer server() {
		return server;
	}

	private static int island(CommandContext<CommandSourceStack> ctx, int count, int size) {
		CommandSourceStack source = ctx.getSource();
		long started = System.currentTimeMillis();
		int put = Island.start(source.getLevel(), BlockPos.containing(source.getPosition()), count, size);
		if (put < 0) {
			source.sendFailure(Component.literal("An island is going already (/drifterciv island stop first)"));
			return 0;
		}

		long seconds = (System.currentTimeMillis() - started) / 1000L;
		source.sendSuccess(() -> Component.literal(put + " Drifters woke up here with nothing, keeping within " + size + " blocks (" + seconds
			+ " s). Its land stays loaded. /tick sprint 24000 runs a day; /drifterciv chronicle tells what happened"), true);
		return put;
	}

	private static int chronicle(CommandContext<CommandSourceStack> ctx, List<Chronicle.Entry> entries, String title, boolean latest) {
		CommandSourceStack source = ctx.getSource();
		if (entries.isEmpty()) {
			source.sendSuccess(() -> Component.literal(title + ": nothing yet"), false);
			return 0;
		}

		List<Chronicle.Entry> shown = latest ? entries.subList(Math.max(0, entries.size() - PAGE), entries.size()) : entries;
		source.sendSuccess(() -> Component.literal(title + " (" + entries.size() + (entries.size() == 1 ? " line" : " lines") + "):"), false);
		for (Chronicle.Entry entry : shown) {
			source.sendSuccess(() -> Component.literal("  " + Chronicle.line(entry)), false);
		}

		return entries.size();
	}

	/** The whole chronicle as a written book (as many pages as it takes, up to a book's 100). */
	private static int book(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		List<Filterable<Component>> pages = new java.util.ArrayList<>();
		StringBuilder page = new StringBuilder();
		int lines = 0;
		long day = -1;
		for (Chronicle.Entry entry : Chronicle.all()) {
			String text = (entry.day() != day ? Chronicle.dayName(entry.day()) + "\n" : "") + entry.text() + "\n";
			day = entry.day();
			// About 14 lines a page; a long line takes two or three.
			int takes = 1 + text.length() / 19;
			if (lines + takes > 13 && page.length() > 0) {
				pages.add(Filterable.passThrough(Component.literal(page.toString())));
				page.setLength(0);
				lines = 0;
				if (pages.size() >= 100) {
					break;
				}

				// (A new page names its day again.)
				if (!text.startsWith(Chronicle.dayName(entry.day()))) {
					text = Chronicle.dayName(entry.day()) + "\n" + text;
				}
			}

			page.append(text);
			lines += takes;
		}

		if (page.length() > 0 && pages.size() < 100) {
			pages.add(Filterable.passThrough(Component.literal(page.toString())));
		}

		if (pages.isEmpty()) {
			pages.add(Filterable.passThrough(Component.literal("Nothing has happened yet.").withStyle(Style.EMPTY)));
		}

		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("The Chronicle"), "the world", 0, pages, true));
		if (!player.getInventory().add(book)) {
			player.drop(book, false);
		}

		int count = pages.size();
		ctx.getSource().sendSuccess(() -> Component.literal("The chronicle, " + count + (count == 1 ? " page" : " pages")), false);
		return count;
	}

	public static String version() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?");
	}
}
