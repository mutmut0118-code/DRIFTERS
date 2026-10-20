package com.dinglebob.drifter.command;

import java.util.Locale;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Milestone 4 (Pathfinder) commands. */
final class NavCommands {
	/** How close "goto me" and "follow me" get to you. */
	private static final double NEAR_PLAYER = 2.0;

	private NavCommands() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		// How the pathfinder sees the cells round the nearest Drifter (debug): kind per cell, by layer.
		root.then(Commands.literal("cells").executes(ctx -> {
			com.dinglebob.drifter.entity.DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
			String out = DrifterCommands.label(drifter) + " " + com.dinglebob.drifter.nav.NavView.describeAround(drifter, drifter.blockPosition());
			ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(out), false);
			return 1;
		}));
		// The unseen-ground guess on or off (debug, for comparing routes; milestone 20).
		root.then(Commands.literal("groundguess")
			.then(Commands.literal("on").executes(ctx -> groundGuess(ctx, true)))
			.then(Commands.literal("off").executes(ctx -> groundGuess(ctx, false))));
		root.then(Commands.literal("goto")
			.then(Commands.literal("me").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Vec3 at = player.position();
				return start(ctx, drifter -> new NavigateTask(drifter, at, NEAR_PLAYER,
					String.format(Locale.ROOT, "go to %s at %d %d %d", player.getGameProfile().getName(), (int)Math.floor(at.x), (int)Math.floor(at.y),
						(int)Math.floor(at.z))));
			}))
			.then(Commands.argument("pos", BlockPosArgument.blockPos())
				.executes(ctx -> gotoPos(ctx, NavigateTask.Pace.TRAVEL))
				.then(Commands.literal("travel").executes(ctx -> gotoPos(ctx, NavigateTask.Pace.TRAVEL)))
				.then(Commands.literal("stroll").executes(ctx -> gotoPos(ctx, NavigateTask.Pace.STROLL)))
				.then(Commands.literal("sneak").executes(ctx -> gotoPos(ctx, NavigateTask.Pace.SNEAK)))));

		// Digging for ancient debris from where it stands (debug; milestone 18): stairs to about Y 15, then a strip mine.
		root.then(Commands.literal("debris")
			.executes(ctx -> start(ctx, drifter -> new com.dinglebob.drifter.brain.activity.DebrisActivity(drifter, 4)))
			.then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
				.executes(ctx -> start(ctx, drifter -> new com.dinglebob.drifter.brain.activity.DebrisActivity(drifter,
					com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count"))))));

		root.then(Commands.literal("follow")
			.then(Commands.literal("me").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				return start(ctx, drifter -> NavigateTask.follow(drifter, player, NEAR_PLAYER + 1.0));
			})));
	}

	private static int groundGuess(CommandContext<CommandSourceStack> ctx, boolean on) {
		com.dinglebob.drifter.nav.NavView.GUESS_GROUND = on;
		ctx.getSource().sendSuccess(() -> Component.literal("Guessing unseen ground from the ground seen round about: " + (on ? "on" : "off")), true);
		return 1;
	}

	private static int gotoPos(CommandContext<CommandSourceStack> ctx, NavigateTask.Pace pace) throws CommandSyntaxException {
		BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
		return start(ctx, drifter -> new NavigateTask(drifter, Vec3.atBottomCenterOf(pos), 0.5, "go to " + pos.toShortString(), pace));
	}

	@FunctionalInterface
	private interface TaskFactory {
		Task create(DrifterEntity drifter) throws CommandSyntaxException;
	}

	private static int start(CommandContext<CommandSourceStack> ctx, TaskFactory factory) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		Task task = factory.create(drifter);
		CommandSourceStack source = ctx.getSource();
		drifter.getTasks().start(task, source::sendSystemMessage);
		source.sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + " starts: " + task.describe()), false);
		return 1;
	}
}
