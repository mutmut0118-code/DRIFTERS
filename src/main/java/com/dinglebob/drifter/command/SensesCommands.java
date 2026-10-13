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

		root.then(Commands.literal("thoughts").executes(SensesCommands::thoughts));
		root.then(Commands.literal("senses").executes(SensesCommands::senses));
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
