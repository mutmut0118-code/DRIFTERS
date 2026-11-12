package com.dinglebob.drifter.social;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Optional chat lines (DESIGN.md section 14): short, lowercase, canned lines tied to events, never generated text, from
 * the chat data ({@link Lines}: each personality its own voice). A strict rate limit; quiet ones (low Sociability) rarely
 * speak. On by default, `/drifter config chat false` turns them off.
 */
public final class ChatLines {
	private ChatLines() {
	}

	/** The canned lines' kinds, each a key in the chat data ({@link Lines}). */
	public enum Kind {
		HI("hi"), THANKS("thanks"), GG_WON("gg_won"), GG_LOST("gg_lost"), STOP("stop"), OUCH("ouch"), HUH("huh"), THIEF("thief"), WAR_CRY("war_cry");

		public final String key;

		Kind(String key) {
			this.key = key;
		}
	}

	/** Least time between two lines from one Drifter. */
	private static final long PER_DRIFTER = 20 * 20;
	/** Least time between two lines of the same kind from one Drifter. */
	private static final long PER_KIND = 20 * 60;
	/** At most this many lines from all Drifters together in {@link #WINDOW}. */
	private static final int SERVER_CAP = 4;
	private static final long WINDOW = 20 * 10;

	private static final Map<UUID, Long> lastLine = new HashMap<>();
	private static final Map<String, Long> lastKind = new HashMap<>();
	private static final java.util.ArrayDeque<Long> recent = new java.util.ArrayDeque<>();

	/**
	 * Talking to someone (an encounter's lines: a demand, an offer, an answer): the chat setting applies, the rate limits
	 * don't (found in a test: "let's go!" after the duel challenge came too soon after it and was never said). Its other
	 * chatter waits after it.
	 */
	public static void speak(DrifterEntity drifter, @org.jetbrains.annotations.Nullable String text) {
		MinecraftServer server = drifter.getServer();
		if (!DrifterConfig.chatLines() || server == null || text == null) {
			return;
		}

		// (A sum named in emeralds is said in coins when Numismatic Overhaul is here.)
		text = com.dinglebob.drifter.trade.Money.inCoins(text);

		lastLine.put(drifter.getUUID(), drifter.level().getGameTime());
		send(drifter, text);
	}

	/**
	 * Its line, to the players near enough to hear it (user, 2026-10-23: only players nearby see what Drifters say), and to
	 * the player an encounter of its is with, wherever they are (it's talking to them: a call for help carries).
	 */
	public static void send(DrifterEntity drifter, String text) {
		// (Typed as players type: maybe a typo, maybe in two parts; see Typing.)
		Typing.type(drifter, text);
	}

	/** One message of it, out now. */
	static void deliver(DrifterEntity drifter, String text) {
		double range = DrifterConfig.chatRange();
		java.util.UUID audience = drifter.encounter() != null ? drifter.encounter().player : null;
		To to = talkingTo(drifter);
		for (net.minecraft.server.level.ServerPlayer player : drifter.getServer().getPlayerList().getPlayers()) {
			if (player.getUUID().equals(audience) || player.level() == drifter.level() && player.distanceTo(drifter) <= range) {
				DrifterSaidPayload.send(player, drifter, text);
				player.sendSystemMessage(line(drifter, text, to, player));
			}
		}

		// (The server's log has it too, as it has players' chat; who it's to after it.)
		String plain = "<" + drifter.getName().getString() + "> " + text;
		drifter.getServer().sendSystemMessage(Component.literal(to == null ? plain : plain + " [to " + to.name() + "]"));
	}

	/**
	 * Its line as this player sees it: its name, and hovering the name says who it's talking to (the user, 2026-11-05: in a
	 * town "there is alot of conversations happening, but i dont know whos talking to who").
	 */
	private static Component line(DrifterEntity drifter, String text, @org.jetbrains.annotations.Nullable To to, net.minecraft.server.level.ServerPlayer player) {
		return Component.literal("<").append(name(drifter, to, player)).append("> " + text);
	}

	/**
	 * Its name as this player sees it in chat: hovering it says who it's talking to, and what an addon notes for this player
	 * ("your friend": {@link com.dinglebob.drifter.api.TalkApi#setNameNote}).
	 */
	private static net.minecraft.network.chat.MutableComponent name(DrifterEntity drifter, @org.jetbrains.annotations.Nullable To to,
		net.minecraft.server.level.ServerPlayer player) {
		net.minecraft.network.chat.MutableComponent name = Component.literal(drifter.getName().getString());
		String hover = to == null ? null : "talking to " + (to.who().equals(player.getUUID()) ? "you" : to.shown());
		String note = com.dinglebob.drifter.api.TalkApi.nameNote(drifter, player);
		if (note != null) {
			hover = hover == null ? note : hover + "\n" + note;
		}

		if (hover != null) {
			String shown = hover;
			name = name.withStyle(style -> style.withHoverEvent(new net.minecraft.network.chat.HoverEvent(
				net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal(shown))));
		}

		return name;
	}

	/**
	 * A line to one player only, as a /msg shows ("X whispers to you: ..."); no other player sees it and no Drifter overhears
	 * (the user, 2026-11-10: a friend tells where the diamonds are "using the whisper command"). The server log has it.
	 */
	public static void whisper(DrifterEntity drifter, net.minecraft.server.level.ServerPlayer player, String text) {
		if (!DrifterConfig.chatLines() || drifter.getServer() == null) {
			return;
		}

		player.sendSystemMessage(Component.translatable("commands.message.display.incoming", name(drifter, null, player), Component.literal(text))
			.withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
		drifter.getServer().sendSystemMessage(Component.literal(drifter.getName().getString() + " whispers to " + player.getGameProfile().getName() + ": " + text));
	}

	/** Who it's talking to just now: their UUID, the name it calls them (their nickname) and their username. */
	private record To(UUID who, String name, String username, long until) {
		String shown() {
			return name.equalsIgnoreCase(username) ? name : name + " (" + username + ")";
		}
	}

	private static final Map<UUID, To> talkingTo = new HashMap<>();
	/** How long a line's "talking to" holds: past its last line to them, by then a talk's next line has come. */
	private static final long TALKING_TO = 20 * 20;

	/** It's talking to this one (a Drifter or a player): its name in chat says so (hover) for a while. */
	public static void to(DrifterEntity drifter, net.minecraft.world.entity.Entity who) {
		String username = who.getName().getString();
		talkingTo.put(drifter.getUUID(), new To(who.getUUID(), com.dinglebob.drifter.identity.Nicknames.of(username), username,
			drifter.level().getGameTime() + TALKING_TO));
	}

	/** It's saying something to everyone (no one in particular). */
	public static void toEveryone(DrifterEntity drifter) {
		talkingTo.remove(drifter.getUUID());
	}

	@org.jetbrains.annotations.Nullable
	private static To talkingTo(DrifterEntity drifter) {
		long now = drifter.level().getGameTime();
		To to = talkingTo.get(drifter.getUUID());
		if (to != null && to.until() > now) {
			return to;
		}

		if (to != null) {
			talkingTo.remove(drifter.getUUID());
		}

		// (An encounter's lines are to its player.)
		if (drifter.encounter() != null) {
			net.minecraft.server.level.ServerPlayer player = drifter.getServer().getPlayerList().getPlayer(drifter.encounter().player);
			if (player != null) {
				String username = player.getGameProfile().getName();
				return new To(player.getUUID(), com.dinglebob.drifter.identity.Nicknames.of(username), username, now);
			}
		}

		return null;
	}

	/**
	 * A line in a conversation with another Drifter ({@code talk.Conversations}): the setting applies; the conversation sets
	 * its own pace, so the rate limits don't (its other chatter waits after it).
	 */
	public static void converse(DrifterEntity drifter, String text) {
		if (!DrifterConfig.chatLines() || drifter.getServer() == null) {
			return;
		}

		lastLine.put(drifter.getUUID(), drifter.level().getGameTime());
		send(drifter, text);
	}

	/** A line of its own (a whim's), under the same rate limits and setting as the canned ones. */
	public static void sayLine(DrifterEntity drifter, @org.jetbrains.annotations.Nullable String text) {
		MinecraftServer server = drifter.getServer();
		// (In a conversation it keeps to it.)
		if (!DrifterConfig.chatLines() || server == null || text == null || com.dinglebob.drifter.social.talk.Conversations.of(drifter) != null) {
			return;
		}

		long now = drifter.level().getGameTime();
		if (now - lastLine.getOrDefault(drifter.getUUID(), Long.MIN_VALUE / 2) < DrifterConfig.chattyGap(PER_DRIFTER)
			|| com.dinglebob.drifter.social.talk.PlayerConvo.hush(drifter, now)) {
			return;
		}

		while (!recent.isEmpty() && now - recent.peekFirst() > WINDOW) {
			recent.pollFirst();
		}

		if (recent.size() >= SERVER_CAP) {
			return;
		}

		lastLine.put(drifter.getUUID(), now);
		recent.addLast(now);
		toEveryone(drifter);
		send(drifter, text);
	}

	/** It might say something about this (personality and rate limits decide); {@code vars}: words for the line (player...). */
	public static void say(DrifterEntity drifter, Kind kind, Object... vars) {
		MinecraftServer server = drifter.getServer();
		if (!DrifterConfig.chatLines() || server == null || com.dinglebob.drifter.social.talk.Conversations.of(drifter) != null) {
			return;
		}

		long now = drifter.level().getGameTime();
		String kindKey = drifter.getUUID() + ":" + kind;
		if (now - lastLine.getOrDefault(drifter.getUUID(), Long.MIN_VALUE / 2) < DrifterConfig.chattyGap(PER_DRIFTER)
			|| now - lastKind.getOrDefault(kindKey, Long.MIN_VALUE / 2) < DrifterConfig.chattyGap(PER_KIND)) {
			return;
		}

		while (!recent.isEmpty() && now - recent.peekFirst() > WINDOW) {
			recent.pollFirst();
		}

		if (recent.size() >= SERVER_CAP) {
			return;
		}

		// Quiet ones mostly don't bother (a hermit almost never), chatty ones usually do.
		double sociability = drifter.getPersonality().get(Trait.SOCIABILITY);
		if (drifter.getRandom().nextDouble() > DrifterConfig.chatty(0.15 + 0.8 * sociability)) {
			return;
		}

		String line = Lines.habit(drifter, kind.key, vars);
		if (line == null) {
			return;
		}

		lastLine.put(drifter.getUUID(), now);
		lastKind.put(kindKey, now);
		recent.addLast(now);
		toEveryone(drifter);
		send(drifter, line);
	}
}
