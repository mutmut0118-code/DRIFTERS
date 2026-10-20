package com.dinglebob.drifter.command;

import java.util.Arrays;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import com.dinglebob.drifter.encounter.Encounter;
import com.dinglebob.drifter.encounter.Encounters;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Encounter commands (milestone 21): what's going on and what's due (`/drifter encounter`), one now
 * (`/drifter encounter start <kind>`), ending the one going on, a price on your head now, the next one due now. For
 * testing; the director runs them on its own.
 */
public final class EncounterCommands {
	/** Every kind's id (for the config command too). */
	public static final List<String> KINDS = Arrays.stream(Encounters.Kind.values()).filter(Encounters.Kind::random).map(kind -> kind.id)
		.collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

	static {
		KINDS.add(0, Encounters.Kind.BOUNTY_HUNTERS.id);
	}

	private EncounterCommands() {
	}

	public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("encounter")
			.executes(EncounterCommands::report)
			.then(Commands.literal("start").then(Commands.argument("kind", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Encounters.Kind.values()).map(kind -> kind.id), builder))
				.executes(EncounterCommands::start)))
			.then(Commands.literal("stop").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Encounters.stop(player.getUUID());
				say(ctx, "The encounter is over (its Drifters head off)");
				return 1;
			}))
			.then(Commands.literal("bounty").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Encounters.postBountyNow(player);
				say(ctx, "A price on your head: bounty hunters come as soon as someone has seen where you are");
				return 1;
			}))
			// Strikes down the one in this part of your encounter as if you had (debug: stopping a thief, say).
			.then(Commands.literal("hit").then(Commands.argument("role", StringArgumentType.word())
				.executes(ctx -> hit(ctx, 1000.0F))
				.then(Commands.argument("amount", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.5F, 1000.0F))
					.executes(ctx -> hit(ctx, com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "amount"))))))
			// Throws something you carry at the nearest of your encounter's Drifters, as turning to them and pressing Q (debug).
			.then(Commands.literal("throw").then(Commands.argument("item", net.minecraft.commands.arguments.ResourceLocationArgument.id())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet(), builder))
				.executes(ctx -> throwAt(ctx, 1))
				.then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
					.executes(ctx -> throwAt(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count"))))))
			// As a right-click on the nearest of your encounter's Drifters (debug: the merchant's trades).
			.then(Commands.literal("talk").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Encounter encounter = Encounters.active(player.getUUID());
				var nearest = encounter == null ? null : encounter.members().stream()
					.min(java.util.Comparator.comparingDouble(d -> d.distanceTo(player))).orElse(null);
				if (nearest == null) {
					throw new SimpleCommandExceptionType(Component.literal("Nobody in your encounter")).create();
				}

				say(ctx, nearest.getName().getString() + ": " + encounter.interact(nearest, player));
				return 1;
			}))
			.then(Commands.literal("now").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Encounters.dueNow(player);
				say(ctx, "The next encounter is due now (within 10 s, if one fits)");
				return 1;
			})));
	}

	/** The one in this part of your encounter, hit by you for this much (1000: struck down). */
	private static int hit(CommandContext<CommandSourceStack> ctx, float amount) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Encounter encounter = Encounters.active(player.getUUID());
		String role = StringArgumentType.getString(ctx, "role");
		var target = encounter == null ? null : encounter.members().stream().filter(d -> d.encounterRole().equals(role)).findFirst().orElse(null);
		if (target == null) {
			throw new SimpleCommandExceptionType(Component.literal("No " + role + " in your encounter")).create();
		}

		target.hurt(player.damageSources().playerAttack(player), amount);
		say(ctx, "Hit " + target.getName().getString() + " for " + amount);
		return 1;
	}

	private static int throwAt(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Encounter encounter = Encounters.active(player.getUUID());
		var nearest = encounter == null ? null : encounter.members().stream().min(java.util.Comparator.comparingDouble(d -> d.distanceTo(player))).orElse(null);
		var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "item"));
		int slot = -1;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(item)) {
				slot = i;
				break;
			}
		}

		if (nearest == null || slot < 0) {
			throw new SimpleCommandExceptionType(Component.literal(nearest == null ? "Nobody in your encounter" : "You don't carry that")).create();
		}

		// (Landing at its feet: the client, not the server, says where a player looks, so a server-side throw can't aim.)
		var stack = player.getInventory().getItem(slot).split(count);
		String what = stack.getCount() + " " + stack.getHoverName().getString();
		net.minecraft.world.phys.Vec3 past = nearest.position().add(nearest.position().subtract(player.position()).multiply(1.0, 0.0, 1.0).normalize().scale(1.5));
		var thrown = new net.minecraft.world.entity.item.ItemEntity(player.level(), past.x, nearest.getY() + 0.5, past.z, stack);
		thrown.setThrower(player);
		thrown.setPickUpDelay(10);
		player.level().addFreshEntity(thrown);
		say(ctx, "Threw " + what + " at " + nearest.getName().getString());
		return 1;
	}

	private static int report(CommandContext<CommandSourceStack> ctx) {
		for (String line : Encounters.report(ctx.getSource().getServer())) {
			say(ctx, line);
		}

		return 1;
	}

	private static int start(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "kind");
		Encounters.Kind kind = Encounters.Kind.byId(id);
		if (kind == null) {
			throw new SimpleCommandExceptionType(Component.literal("No encounter called " + id)).create();
		}

		if (kind == Encounters.Kind.BOUNTY_HUNTERS) {
			Encounters.postBountyNow(player);
		}

		Encounter encounter = Encounters.start(player, kind);
		if (encounter == null) {
			say(ctx, "Couldn't start " + kind.label + " here (one going on already, nowhere out of sight to come from, or nothing it needs"
				+ (kind == Encounters.Kind.BOUNTY_HUNTERS ? ": no Drifter has seen where you are" : kind == Encounters.Kind.WANTED ? ": no Drifter 150 to 1200 blocks off" : "")
				+ ")");
			return 0;
		}

		say(ctx, "Started: " + kind.label + " (" + encounter.members().size() + " Drifters, coming from out of sight)");
		return 1;
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
	}
}
