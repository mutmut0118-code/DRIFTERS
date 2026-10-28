package com.dinglebob.drifter.command;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.social.talk.Conversations;
import com.dinglebob.drifter.social.talk.Topics;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * Long conversations between Drifters (the topic web), for checking them: {@code /drifter talk long [topic]} starts the
 * nearest two on a long one (interest for ten topics, whatever their bond); {@code /drifter talk status} shows each
 * conversation going on, its topics so far and the interest left; {@code /drifter talk links <topic>} what a topic leads
 * on to.
 */
final class TalkWebCommands {
	/** Interest a debug long talk gets on top of its own. */
	private static final double LONG = 10.0;

	private TalkWebCommands() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("talk")
			.then(Commands.literal("long")
				.executes(ctx -> longTalk(ctx, null))
				.then(Commands.argument("topic", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Topics.all().stream().map(Topics.Topic::id), builder))
					.executes(ctx -> longTalk(ctx, StringArgumentType.getString(ctx, "topic")))))
			.then(Commands.literal("status").executes(TalkWebCommands::status))
			.then(Commands.literal("links")
				.then(Commands.argument("topic", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Topics.all().stream().map(Topics.Topic::id), builder))
					.executes(TalkWebCommands::links))));
	}

	private static int longTalk(CommandContext<CommandSourceStack> ctx, String topic) throws CommandSyntaxException {
		DrifterEntity a = DrifterCommands.nearest(ctx.getSource());
		DrifterEntity b = a.level().getEntitiesOfClass(DrifterEntity.class, a.getBoundingBox().inflate(16.0), other -> other != a && other.isAlive()).stream()
			.min(java.util.Comparator.comparingDouble(other -> other.distanceToSqr(a))).orElse(null);
		if (b == null) {
			ctx.getSource().sendFailure(Component.literal("No other Drifter within 16 blocks of " + DrifterCommands.label(a)));
			return 0;
		}

		Conversations.Talk talk = Conversations.start(a, b, topic, a.level().getGameTime());
		if (talk == null) {
			ctx.getSource().sendFailure(Component.literal("No topic suits " + DrifterCommands.label(a) + " and " + DrifterCommands.label(b) + " right now"));
			return 0;
		}

		talk.addInterest(LONG);
		ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(a) + " and " + DrifterCommands.label(b) + " start a long talk: " + talk.topic.id()), false);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		if (Conversations.all().isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("Nobody's talking"), false);
			return 0;
		}

		for (Conversations.Talk talk : Conversations.all()) {
			String line = DrifterCommands.label(talk.a) + " and " + DrifterCommands.label(talk.b) + (talk.longTalk() ? " (long talk, chatting as they work)" : "")
				+ ": " + String.join(" > ", talk.topics()) + String.format(" (interest left %.1f)", talk.interestLeft());
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}

		return 1;
	}

	private static int links(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "topic");
		Topics.Topic topic = Topics.byId(id);
		if (topic == null) {
			ctx.getSource().sendFailure(Component.literal("No topic " + id));
			return 0;
		}

		Topics.Web web = topic.web();
		java.util.Set<String> to = new java.util.TreeSet<>(web.next());
		for (String tag : web.leads()) {
			Topics.tagged(tag).forEach(other -> to.add(other.id()));
		}

		to.remove(id);
		String line = id + ": about " + String.join(", ", web.tags()) + "; leads to " + String.join(", ", web.leads()) + (web.opener() ? "; opener" : "")
			+ (web.followUp() ? "; follow-up only" : "") + "\n  " + to.size() + " topics: " + String.join(", ", to);
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}
}
