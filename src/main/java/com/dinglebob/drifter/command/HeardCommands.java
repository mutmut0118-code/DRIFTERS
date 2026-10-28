package com.dinglebob.drifter.command;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.social.talk.Heard;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * What players told the nearest Drifter in chat ({@code /drifter heard}), for checking it remembers: per player, what it
 * calls them, their base, their plan, promises, likes, news; the places and words about others it heard. {@code forget}
 * wipes it; {@code age <minutes>} makes all of it that much older, in real minutes (a game day is 20): the follow-ups without waiting.
 */
final class HeardCommands {
	private HeardCommands() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("heard")
			.executes(HeardCommands::list)
			.then(Commands.literal("forget").executes(ctx -> {
				DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
				drifter.getHeard().load(new net.minecraft.nbt.CompoundTag());
				ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + " forgot everything players told it"), true);
				return 1;
			}))
			.then(Commands.literal("age").then(Commands.argument("minutes", IntegerArgumentType.integer(1, 60 * 24 * 10)).executes(ctx -> {
				DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
				int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
				drifter.getHeard().age(minutes * 60L * 20L);
				ctx.getSource().sendSuccess(() -> Component.literal("What " + DrifterCommands.label(drifter) + " was told is " + minutes + " minutes older"), true);
				return 1;
			}))));
	}

	private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		Heard heard = drifter.getHeard();
		long now = drifter.level().getGameTime();
		send(ctx, DrifterCommands.label(drifter) + " remembers:");
		for (Heard.Notes n : heard.all()) {
			StringBuilder line = new StringBuilder("  " + n.name + ":");
			if (!n.nick.isEmpty()) {
				line.append(" calls them ").append(n.nick).append(";");
			}

			if (n.base != null) {
				line.append(" base ").append(n.base.toShortString()).append(n.baseExact ? "" : " (roughly)").append(";");
			}

			if (!n.plan.isEmpty()) {
				line.append(" off ").append(n.plan).append(" ").append(ago(now, n.planAt)).append(n.planAsked ? " (asked)" : "").append(";");
			}

			for (Heard.Promise promise : n.promises) {
				line.append(" promised ").append(promise.item.isEmpty() ? "something" : promise.item).append(" x").append(promise.count).append(" ")
					.append(ago(now, promise.at)).append(promise.broken ? " (broken)" : "").append(";");
			}

			if (!n.likes.isEmpty()) {
				line.append(" likes ").append(String.join(", ", n.likes)).append(";");
			}

			if (!n.hates.isEmpty()) {
				line.append(" hates ").append(String.join(", ", n.hates)).append(";");
			}

			if (!n.news.isEmpty()) {
				line.append(" news: ").append(n.news).append(" ").append(ago(now, n.newsAt)).append(";");
			}

			send(ctx, line.toString());
		}

		for (Heard.Place place : heard.places()) {
			send(ctx, "  place: " + place.kind() + " at " + place.pos().toShortString() + (place.exact() ? "" : " (roughly)") + ", from " + place.teller()
				+ (place.hops() > 0 ? " (" + place.hops() + " tellings on)" : "") + ", " + ago(now, place.at()));
		}

		for (Heard.Word word : heard.words()) {
			send(ctx, "  word: " + word.about() + " is " + (word.good() ? "good" : "trouble") + ", says " + word.teller() + ", " + ago(now, word.at()));
		}

		return 1;
	}

	private static String ago(long now, long at) {
		long seconds = (now - at) / 20;
		return seconds < 120 ? seconds + " s ago" : seconds < 7200 ? seconds / 60 + " min ago" : seconds / 1200 + " days ago";
	}

	private static void send(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
	}
}
