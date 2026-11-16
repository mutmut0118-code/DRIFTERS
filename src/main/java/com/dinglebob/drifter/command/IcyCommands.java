package com.dinglebob.drifter.command;

import java.util.Comparator;

import com.dinglebob.drifter.compat.IcyHorses;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.AABB;

/**
 * Debug for Icy's Better Horses (horse culture, docs/superpowers/plans/2026-11-16-horse-culture.md): what Drifter reads of
 * the nearest horse through {@link IcyHorses}.
 */
final class IcyCommands {
	private IcyCommands() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("icy").requires(source -> source.hasPermission(2)).executes(ctx -> {
			var source = ctx.getSource();
			var pos = source.getPosition();
			AbstractHorse horse = source.getLevel().getEntitiesOfClass(AbstractHorse.class, new AABB(pos, pos).inflate(16)).stream()
				.min(Comparator.comparingDouble(h -> h.distanceToSqr(pos))).orElse(null);
			String line = "Icy's: present " + IcyHorses.present() + (horse == null ? "; no horse near" : "; " + horse.getName().getString() + ": owner "
				+ IcyHorses.owner(horse) + ", bond " + IcyHorses.bond(horse) + ", breed " + IcyHorses.breed(horse) + ", gait " + IcyHorses.gait(horse)
				+ ", favourite " + IcyHorses.favouriteTreat(horse) + ", cart " + IcyHorses.cart(horse));
			com.dinglebob.drifter.Drifter.LOGGER.info("[icy] {}", line);
			source.sendSuccess(() -> Component.literal(line), false);
			return 1;
		}));
	}
}
