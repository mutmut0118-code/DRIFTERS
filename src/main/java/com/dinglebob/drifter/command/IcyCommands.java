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
				+ ", favourite " + IcyHorses.favouriteTreat(horse) + ", cart " + IcyHorses.cart(horse) + ", speed "
				+ String.format(java.util.Locale.ROOT, "%.3f", horse.getDeltaMovement().horizontalDistance()) + ", gait " + com.dinglebob.drifter.skills.Gait.of(horse));
			com.dinglebob.drifter.Drifter.LOGGER.info("[icy] {}", line);
			source.sendSuccess(() -> Component.literal(line), false);
			return 1;
		}).then(Commands.literal("own").executes(ctx -> {
			// (Debug: the nearest horse made the player's own, in Icy's terms, to test that Drifters leave it be.)
			var source = ctx.getSource();
			var pos = source.getPosition();
			AbstractHorse horse = source.getLevel().getEntitiesOfClass(AbstractHorse.class, new AABB(pos, pos).inflate(16)).stream()
				.min(Comparator.comparingDouble(h -> h.distanceToSqr(pos))).orElse(null);
			if (horse == null || source.getPlayer() == null) {
				source.sendFailure(Component.literal("No horse near, or not a player"));
				return 0;
			}

			try {
				IcyHorses.setOwner(horse, source.getPlayer().getUUID());
				horse.setOwnerUUID(source.getPlayer().getUUID());
			} catch (RuntimeException e) {
				com.dinglebob.drifter.Drifter.LOGGER.warn("[icy] own failed", e);
				return 0;
			}

			com.dinglebob.drifter.Drifter.LOGGER.info("[icy] {} is the player's now", horse.getName().getString());
			source.sendSuccess(() -> Component.literal("Debug: " + horse.getName().getString() + " is yours now"), false);
			return 1;
		})).then(Commands.literal("keep").executes(ctx -> {
			// (Debug: the nearest horse made the nearest Drifter's own, its stall where it stands.)
			var source = ctx.getSource();
			var pos = source.getPosition();
			AbstractHorse horse = source.getLevel().getEntitiesOfClass(AbstractHorse.class, new AABB(pos, pos).inflate(16)).stream()
				.min(Comparator.comparingDouble(h -> h.distanceToSqr(pos))).orElse(null);
			var drifter = DrifterCommands.nearest(source);
			if (horse == null || drifter == null) {
				return 0;
			}

			horse.setTamed(true);
			com.dinglebob.drifter.api.DrifterApi.keepHorse(drifter, horse, horse.blockPosition());
			// (Debug: and one of its favourite treat in the Drifter's pocket, to see it given.)
			var favourite = IcyHorses.treatFor(horse);
			if (favourite != null) {
				drifter.getInventory().add(new net.minecraft.world.item.ItemStack(favourite));
			}

			com.dinglebob.drifter.Drifter.LOGGER.info("[icy] {} is {}'s now; favourite treat {}; Icy's treats {}", horse.getName().getString(), drifter.getName().getString(),
				IcyHorses.treatFor(horse), IcyHorses.treats());
			return 1;
		})).then(Commands.literal("ride").executes(ctx -> {
			// (Debug: the nearest Drifter told to ride the nearest horse 60 blocks east, whoever's it is.)
			var source = ctx.getSource();
			var pos = source.getPosition();
			AbstractHorse horse = source.getLevel().getEntitiesOfClass(AbstractHorse.class, new AABB(pos, pos).inflate(16)).stream()
				.min(Comparator.comparingDouble(h -> h.distanceToSqr(pos))).orElse(null);
			var drifter = DrifterCommands.nearest(source);
			if (horse == null || drifter == null) {
				return 0;
			}

			drifter.getTasks().start(new com.dinglebob.drifter.executor.task.RideTask(drifter, horse, drifter.position().add(300, 0, 0), 4.0), source::sendSystemMessage);
			return 1;
		}).then(Commands.literal("hurry").executes(ctx -> {
			// (Debug: the same, in a hurry: a gallop.)
			var source = ctx.getSource();
			var pos = source.getPosition();
			AbstractHorse horse = source.getLevel().getEntitiesOfClass(AbstractHorse.class, new AABB(pos, pos).inflate(16)).stream()
				.min(Comparator.comparingDouble(h -> h.distanceToSqr(pos))).orElse(null);
			var drifter = DrifterCommands.nearest(source);
			if (horse == null || drifter == null) {
				return 0;
			}

			drifter.getTasks().start(new com.dinglebob.drifter.executor.task.RideTask(drifter, horse, drifter.position().add(300, 0, 0), 4.0, true),
				source::sendSystemMessage);
			return 1;
		}))));
	}
}
