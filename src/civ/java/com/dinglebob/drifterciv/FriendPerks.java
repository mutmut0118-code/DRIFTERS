package com.dinglebob.drifterciv;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;

/** TODO(D2): filled in by the next tasks. */
final class FriendPerks {
	private FriendPerks() {
	}

	static void tick(MinecraftServer server, long now) {
	}

	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("perk");
	}
}
