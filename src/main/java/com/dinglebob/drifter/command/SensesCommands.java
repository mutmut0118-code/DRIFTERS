package com.dinglebob.drifter.command;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.dinglebob.drifter.debug.overlay.DebugOverlayServer;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.memory.DrifterMemory;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.memory.ThoughtLog;
import com.dinglebob.drifter.perception.Awareness;
import com.dinglebob.drifter.perception.HeardSound;
import com.dinglebob.drifter.perception.Senses;
import com.dinglebob.drifter.perception.TimeSense;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Milestone 3 (Senses and memory) debug commands: the overlay, and what the nearest Drifter knows, in chat. */
final class SensesCommands {
	private static final int MEMORY_LIST_LIMIT = 15;
	private static final int THOUGHT_LIST_LIMIT = 15;

	private SensesCommands() {
	}

	/** Nothing over it up to the top of the column but leaves and more of the same (a pillar), for the cobblestone scan. */
	private static boolean openOver(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, int top) {
		for (int y = pos.getY() + 1; y < top; y++) {
			net.minecraft.core.BlockPos up = new net.minecraft.core.BlockPos(pos.getX(), y, pos.getZ());
			var over = level.getBlockState(up);
			if (!over.getCollisionShape(level, up).isEmpty() && !over.is(net.minecraft.tags.BlockTags.LEAVES) && !over.is(level.getBlockState(pos).getBlock())) {
				return false;
			}
		}

		return true;
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("debug")
			.then(Commands.literal("on").executes(ctx -> debug(ctx, true)))
			.then(Commands.literal("off").executes(ctx -> debug(ctx, false))));

		root.then(Commands.literal("memory")
			.executes(SensesCommands::memory)
			.then(Commands.literal("clear").executes(ctx -> {
				DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
				drifter.getMemory().clear();
				ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + " forgot everything"), true);
				return 1;
			})));

		root.then(Commands.literal("thoughts").executes(SensesCommands::thoughts)
			.then(Commands.literal("log").then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(ctx -> {
				boolean on = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on");
				com.dinglebob.drifter.memory.ThoughtLog.echo = on;
				com.dinglebob.drifter.DrifterConfig.setLogThoughts(on);
				ctx.getSource().sendSuccess(() -> Component.literal("Drifter thoughts " + (on ? "now go to the server log (debug)" : "no longer go to the server log")), true);
				return 1;
			}))));
		root.then(Commands.literal("senses").executes(SensesCommands::senses));
		// Debug: cobblestone out in the open round here (stray scaffolding, steps), not part of anything built, to the log.
		root.then(Commands.literal("cobblescan").then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 256)).executes(ctx -> {
			int radius = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius");
			var level = ctx.getSource().getLevel();
			net.minecraft.core.BlockPos center = net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition());
			java.util.List<String> found = new java.util.ArrayList<>();
			// Tagged: T on a town's ground, then whose scaffolding it is (a Drifter remembers what it put down to get somewhere).
			java.util.List<com.dinglebob.drifter.entity.DrifterEntity> drifters = new java.util.ArrayList<>(level.getEntities(
				net.minecraft.world.level.entity.EntityTypeTest.forClass(com.dinglebob.drifter.entity.DrifterEntity.class), d -> true));
			com.dinglebob.drifter.api.TownWork town = drifters.stream().map(com.dinglebob.drifter.entity.DrifterEntity::getTownWork)
				.filter(java.util.Objects::nonNull).findFirst().orElse(null);
			int[] onTown = {0};
			for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
				for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
					if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
						continue;
					}

					int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					for (int y = top - 16; y < top; y++) {
						net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
						var state = level.getBlockState(pos);
						if ((state.is(net.minecraft.world.level.block.Blocks.COBBLESTONE) || state.is(net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE))
							&& !com.dinglebob.drifter.world.PlayerPlacedBlocks.isDrifterBuilt(level, pos) && openOver(level, pos, top)) {
							String tag = "";
							if (town != null && town.townGround(x, z)) {
								tag += " T";
								onTown[0]++;
							}

							for (var d : drifters) {
								if (d.isScaffold(pos)) {
									tag += " S:" + d.getName().getString();
								}
							}

							found.add(x + " " + y + " " + z + tag);
						}
					}
				}
			}

			com.dinglebob.drifter.Drifter.LOGGER.info("[cobblescan] {} in the open within {} ({} on town ground): {}", found.size(), radius, onTown[0], found);
			ctx.getSource().sendSuccess(() -> Component.literal(found.size() + " cobblestone blocks in the open within " + radius + " (the log lists them: [cobblescan])"), false);
			return found.size();
		})));
		// Debug: a box of blocks, layer by layer, to the server log (one letter a block, the key after each layer).
		root.then(Commands.literal("dumpbox").then(Commands.argument("from", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
			.then(Commands.argument("to", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos()).executes(ctx -> {
				net.minecraft.core.BlockPos a = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "from");
				net.minecraft.core.BlockPos b = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "to");
				var level = ctx.getSource().getLevel();
				String letters = "#abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
				for (int y = Math.min(a.getY(), b.getY()); y <= Math.max(a.getY(), b.getY()); y++) {
					java.util.Map<String, Character> key = new java.util.LinkedHashMap<>();
					StringBuilder out = new StringBuilder("[dumpbox] y" + y + " (rows z " + Math.min(a.getZ(), b.getZ()) + "+, x from " + Math.min(a.getX(), b.getX()) + ")");
					for (int z = Math.min(a.getZ(), b.getZ()); z <= Math.max(a.getZ(), b.getZ()); z++) {
						out.append(System.lineSeparator()).append("  ");
						for (int x = Math.min(a.getX(), b.getX()); x <= Math.max(a.getX(), b.getX()); x++) {
							var state = level.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
							if (state.isAir()) {
								out.append('.');
								continue;
							}

							String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
							out.append(key.computeIfAbsent(id, k -> letters.charAt(Math.min(key.size(), letters.length() - 1))));
						}
					}

					out.append(System.lineSeparator()).append("  key: ").append(key);
					com.dinglebob.drifter.Drifter.LOGGER.info(out.toString());
				}

				return 1;
			}))));
		// Debug: every block a Drifter puts down goes to the server log (who, where, doing what).
		root.then(Commands.literal("placelog").then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(ctx -> {
			boolean on = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on");
			com.dinglebob.drifter.debug.PlaceLog.on = on;
			ctx.getSource().sendSuccess(() -> Component.literal("Blocks Drifters place " + (on ? "now go to the server log ([placelog])" : "no longer go to the server log")), true);
			return 1;
		})));
		// Debug: it asks the nearest Drifter it knows for something to trade, now (milestone 22 groundwork).
		root.then(Commands.literal("barter").executes(ctx -> {
			DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
			var deal = com.dinglebob.drifter.trade.DrifterDeals.of(drifter);
			if (deal != null) {
				ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + " is trading already: " + deal.asker.getName().getString()
					+ " gives " + deal.describe()), false);
				return 0;
			}

			DrifterEntity other = null;
			for (DrifterEntity d : drifter.level().getEntitiesOfClass(DrifterEntity.class, drifter.getBoundingBox().inflate(16.0), d -> d != drifter && d.isAlive())) {
				if (drifter.getBonds().get(d.getUUID()) != null && d.getBonds().get(drifter.getUUID()) != null
					&& (other == null || d.distanceTo(drifter) < other.distanceTo(drifter))) {
					other = d;
				}
			}

			if (other == null) {
				ctx.getSource().sendFailure(Component.literal(DrifterCommands.label(drifter) + " knows no Drifter within 16 blocks"));
				return 0;
			}

			var made = com.dinglebob.drifter.trade.DrifterDeals.ask(drifter, other, other.getBonds().get(drifter.getUUID()));
			String name = other.getName().getString();
			ctx.getSource().sendSuccess(() -> Component.literal(made == null ? DrifterCommands.label(drifter) + " found nothing to trade with " + name
				: DrifterCommands.label(drifter) + " asks " + name + ": " + made.describe()), false);
			return made == null ? 0 : 1;
		}));
		// The news it carries (milestone 22 groundwork), and (debug) news for it to pass on, as another mod would give it.
		root.then(Commands.literal("news").executes(SensesCommands::news)
			.then(Commands.literal("tell").then(Commands.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
				DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
				String what = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "text");
				com.dinglebob.drifter.api.DrifterApi.tell(drifter, "debug:" + what.toLowerCase(Locale.ROOT), null, "", what,
					com.dinglebob.drifter.social.NewsBook.DEFAULT_LASTS, new net.minecraft.nbt.CompoundTag());
				ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + " will pass on: " + what), true);
				return 1;
			}))));
	}

	private static int news(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		long now = drifter.level().getGameTime();
		var news = drifter.getNews().all();
		StringBuilder text = new StringBuilder(DrifterCommands.label(drifter) + " carries " + news.size() + " pieces of news:");
		for (var n : news) {
			text.append(String.format(Locale.ROOT, "\n  %s%s (%s, %ds ago%s)", n.aboutName().isEmpty() ? "" : n.aboutName() + " ", n.what(),
				n.hops() == 0 ? "saw it" : n.hops() == 1 ? "told" : "told, " + n.hops() + " tellings on", (now - n.since()) / 20, n.stale(now) ? ", old news" : ""));
		}

		String message = text.toString();
		ctx.getSource().sendSuccess(() -> Component.literal(message), false);
		return news.size();
	}

	private static int debug(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		DebugOverlayServer.toggle(player, on);
		ctx.getSource().sendSuccess(() -> Component.literal("Drifter debug overlay " + (on ? "on" : "off")
			+ (on ? " (shows only what each Drifter within " + (int)DebugOverlayServer.RANGE + " blocks perceives and remembers)" : "")), false);
		return 1;
	}

	private static int memory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		DrifterMemory memory = drifter.getMemory();
		long now = drifter.level().getGameTime();
		StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "%s remembers %d long-term and %d short-term things, %d chunks explored:",
			DrifterCommands.label(drifter), memory.count(MemoryKind.Tier.LONG), memory.count(MemoryKind.Tier.SHORT), memory.map().exploredCount()));
		Map<MemoryKind, Integer> counts = memory.countsByKind();
		text.append("\n  ");
		counts.forEach((kind, count) -> text.append(kind.id()).append(' ').append(count).append("  "));
		Vec3 here = drifter.position();
		List<MemoryEntry> nearest = memory.all()
			.sorted(Comparator.comparingDouble(entry -> entry.pos.distanceToSqr(here)))
			.limit(MEMORY_LIST_LIMIT)
			.toList();
		for (MemoryEntry entry : nearest) {
			text.append(String.format(Locale.ROOT, "\n  %s %s at %d %d %d, %s, confidence %.2f%s", entry.kind.id(), Senses.shortLabel(entry.label),
				(int)Math.floor(entry.pos.x), (int)Math.floor(entry.pos.y), (int)Math.floor(entry.pos.z), entry.source.name().toLowerCase(Locale.ROOT),
				entry.confidence(now), entry.detail.isEmpty() ? "" : " (" + entry.detail + ")"));
		}

		String message = text.toString();
		ctx.getSource().sendSuccess(() -> Component.literal(message), false);
		return 1;
	}

	private static int thoughts(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		long now = drifter.level().getGameTime();
		StringBuilder text = new StringBuilder(DrifterCommands.label(drifter) + "'s recent thoughts:");
		for (ThoughtLog.Thought thought : drifter.getMemory().thoughts().latest(THOUGHT_LIST_LIMIT)) {
			text.append(String.format(Locale.ROOT, "\n  %ds ago: %s", (now - thought.time()) / 20, thought.text()));
		}

		String message = text.toString();
		ctx.getSource().sendSuccess(() -> Component.literal(message), false);
		return 1;
	}

	private static int senses(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		Senses senses = drifter.getSenses();
		long now = drifter.level().getGameTime();
		TimeSense time = senses.time();
		StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "%s thinks it's %s (%d)%s; actually %d. Sky %s, weather %s.",
			DrifterCommands.label(drifter), TimeSense.describeTimeOfDay(time.believedTimeOfDay()), time.believedTimeOfDay(),
			time.isExact() ? "" : " (guessing)", Math.floorMod(drifter.level().getDayTime(), 24000L), time.canSeeSky() ? "visible" : "not visible",
			time.describeWeather()));
		List<Awareness> tracked = senses.tracked();
		text.append("\n Tracking ").append(tracked.size()).append(':');
		for (Awareness awareness : tracked) {
			text.append(String.format(Locale.ROOT, "\n  %s: %.2f (%s)%s", awareness.identified ? awareness.entity.getName().getString() : "something",
				awareness.value, awareness.level().id(), awareness.visibleNow ? ", in view" : ", out of view"));
		}

		List<HeardSound> sounds = senses.recentSounds();
		text.append("\n Heard recently: ").append(sounds.isEmpty() ? "nothing" : "");
		for (HeardSound sound : sounds) {
			text.append(String.format(Locale.ROOT, "\n  %s %s, ~%d blocks, %ds ago", sound.kind().description, senses.direction(sound.roughPos()),
				(int)sound.roughDistance(), (now - sound.time()) / 20));
		}

		String message = text.toString();
		ctx.getSource().sendSuccess(() -> Component.literal(message), false);
		return 1;
	}
}
