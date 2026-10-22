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
			// Towns: the nearest, all of them, and (debug, for testing) one made at once.
			.then(Commands.literal("town")
				.executes(DrifterCivilizations::town)
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
				return 1;
			}))
			.then(Commands.literal("towns").executes(ctx -> {
				var towns = Towns.all();
				ctx.getSource().sendSuccess(() -> Component.literal(towns.isEmpty() ? "No towns yet (/drifterciv town spawn makes one, for testing)"
					: towns.size() + (towns.size() == 1 ? " town:" : " towns:")), false);
				for (Town town : towns) {
					ctx.getSource().sendSuccess(() -> Component.literal("  " + town.name + " at " + town.center.getX() + ", " + town.center.getZ() + ", "
						+ town.plots.size() + " households, founded day " + town.foundedDay + (town.spawned ? " (made ready)" : "")), false);
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
			var record = plot.owner == null ? null : DrifterApi.record(plot.owner);
			if (record != null) {
				people.add(record.name() + (record.status() == com.dinglebob.drifter.api.DrifterRecord.Status.ALIVE ? "" : " (" + record.status().name().toLowerCase(java.util.Locale.ROOT) + ")"));
				if (record.status() == com.dinglebob.drifter.api.DrifterRecord.Status.ALIVE) {
					alive++;
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
			+ " (" + age + (age == 1 ? " day" : " days") + " ago), " + town.plots.size() + " households, " + alivePeople + " people alive; the store has "
			+ stacks + " stacks in " + town.store.size() + " chests"), false);
		source.sendSuccess(() -> Component.literal("  " + String.join(", ", people)), false);
		// A town founded by its people: who has a plot, whose house is up.
		var group = town.spawned || town.group == null ? null : DrifterApi.groupById(town.group);
		if (group != null) {
			List<String> households = new java.util.ArrayList<>();
			for (java.util.UUID member : group.members) {
				Town.Plot plot = town.plots.stream().filter(p -> member.equals(p.owner)).findFirst().orElse(null);
				String state = plot == null ? (town.plots.isEmpty() || !town.plots.get(0).built ? "building the first house" : "no plot yet")
					: (plot.built ? "house built" : "building") + " (" + plot.planId + " at " + plot.origin.toShortString() + ")";
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
			String text = (entry.day() != day ? "Day " + entry.day() + "\n" : "") + entry.text() + "\n";
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
				if (!text.startsWith("Day ")) {
					text = "Day " + entry.day() + "\n" + text;
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
