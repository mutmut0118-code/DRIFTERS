package com.dinglebob.drifterciv;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Friendships between players and Drifters (D2). TODO(task 3): records, stages, the loop. */
final class Friends {
	private Friends() {
	}

	static void register() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("friends");
	}
}
