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
		root.then(Commands.literal("goto")
			.then(Commands.literal("me").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Vec3 at = player.position();
				return start(ctx, drifter -> new NavigateTask(drifter, at, NEAR_PLAYER,
					String.format(Locale.ROOT, "go to %s at %d %d %d", player.getGameProfile().getName(), (int)Math.floor(at.x), (int)Math.floor(at.y),
						(int)Math.floor(at.z))));
			}))
			.then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> {
				BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
				return start(ctx, drifter -> new NavigateTask(drifter, Vec3.atBottomCenterOf(pos), 0.5, "go to " + pos.toShortString()));
			})));

		root.then(Commands.literal("follow")
			.then(Commands.literal("me").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				return start(ctx, drifter -> NavigateTask.follow(drifter, player, NEAR_PLAYER + 1.0));
			})));
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
