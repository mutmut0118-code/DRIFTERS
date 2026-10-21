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
