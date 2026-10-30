package com.dinglebob.drifterciv;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dinglebob.drifter.api.DrifterApi;
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
		Diplomacy.register();
		TownSites.register();
		TownRaids.register();
		Trades.register();
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
			// Towns: the nearest, all of them, and (debug, for testing) one made at once.
			.then(Commands.literal("town")
				.executes(DrifterCivilizations::town)
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
							site.z - from.getZ())) + " blocks away, " + site.households + " households)"), false);
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
						.suggests((c, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("theft", "farms", "murder"), builder))
						.executes(ctx -> {
							var player = ctx.getSource().getPlayerOrException();
							Town town = Towns.nearest(ctx.getSource().getLevel().dimension().location().toString(), player.blockPosition());
							String kind = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind");
							if (town == null || !Justice.debugCrime(ctx.getSource().getServer(), town, player, kind)) {
								ctx.getSource().sendFailure(Component.literal("No town (or none of its people) near you, or not a crime: theft, farms, murder"));
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
				// Debug: the ruins of a town that failed, made here at once (as world generation now and then makes one).
				.then(Commands.literal("ruin").requires(source -> source.hasPermission(2)).executes(ctx -> {
					var source = ctx.getSource();
					TownBuilder.Result result = TownBuilder.ruin(source.getLevel(), BlockPos.containing(source.getPosition()));
					source.sendSuccess(() -> Component.literal("Debug: " + result.message()), true);
					return 1;
				}))
				// Debug: the nearest town seen from above, saved as a picture in the server's town-maps folder.
				.then(Commands.literal("map").requires(source -> source.hasPermission(2)).executes(ctx -> {
					var source = ctx.getSource();
					Town town = Towns.nearest(source.getLevel().dimension().location().toString(), BlockPos.containing(source.getPosition()));
					if (town == null) {
						source.sendFailure(Component.literal("No town near"));
						return 0;
					}

					try {
						int radius = town.plots.size() >= TownBuilder.CITY ? TownBuilder.CITY_REACH + 8 : 112;
						java.nio.file.Path file = TownMap.draw(source.getLevel(), town.center, radius, town.name);
						source.sendSuccess(() -> Component.literal("Debug: a map of " + town.name + " saved to " + file), true);
						return 1;
					} catch (java.io.IOException e) {
						source.sendFailure(Component.literal("Couldn't save the map: " + e.getMessage()));
						return 0;
					}
				}))
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
