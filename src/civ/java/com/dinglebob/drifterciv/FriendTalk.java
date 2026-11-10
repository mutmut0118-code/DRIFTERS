package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Talk that brings a player and a Drifter closer (D2; the user, 2026-11-10: "make friends by conversating ... small talks
 * and responding to their opinions and agreeing on stuff; think of like a Sims 4 system"). Its hot takes end on a question
 * (topics in {@code drifter_civ/drifter_talk/friends_*.json}, do "opinion"): agreeing warms it most, disagreeing by its
 * nature (an argumentative one enjoys it, a thin-skinned one takes it hard), and what they said is kept (shared opinions,
 * brought up later). Talks the same day count less and less. Acquaintances and friends bring things up with them more
 * often, and keep a talk going longer the closer they are; in a bad way it brushes them off.
 */
final class FriendTalk {
	private FriendTalk() {
	}

	/** Worth of an agreed opinion (trust, familiarity) at the Normal weights, and of the rest. */
	private static final double AGREE_TRUST = 3.0;
	private static final double TALK_FAMILIARITY = 1.0;
	private static final double ARGUE_TRUST = 1.0;
	private static final double THIN_SKIN_TRUST = -2.0;
	private static final double DISAGREE_TRUST = -0.5;
	/** Least time between its openings with the same player, by stage (ticks: acquaintance, friend, best friend). */
	private static final long[] PAIR_GAP = {20L * 60 * 6, 20L * 60 * 4, 20L * 60 * 3};
	/** Least time between any friend's openings with one player (ticks). */
	private static final long PLAYER_GAP = 20L * 60;
	/** How near to bring something up (blocks). */
	private static final double NEAR = 8.0;
	/** Topics in one sitting at most, by stage (stranger, acquaintance, friend, best friend). */
	private static final int[] SITTING = {1, 2, 4, 8};
	/** A sitting is over after this long with nothing said (ticks). */
	private static final long SITTING_ENDS = 20L * 90;

	private record Pending(UUID drifter, UUID player, long at, @Nullable String topic, int tries) {
		Pending(UUID drifter, UUID player, long at, @Nullable String topic) {
			this(drifter, player, at, topic, 0);
		}
	}

	private static final Map<String, Long> lastOpening = new HashMap<>();
	private static final Map<UUID, Long> lastPlayerOpening = new HashMap<>();
	/** Topics in this sitting, and when the last one was. */
	private static final Map<String, long[]> sittings = new HashMap<>();
	private static final List<Pending> pending = new ArrayList<>();
	private static final Map<UUID, Long> brushedOff = new HashMap<>();

	static void register() {
		DrifterEvents.PLAYER_ANSWERED.register(FriendTalk::answered);
		TalkApi.registerChatHandler(FriendTalk::heard);
	}

	private static String pair(UUID drifter, UUID player) {
		return drifter + "|" + player;
	}

	// ----- Their answers -----

	private static void answered(DrifterEntity drifter, ServerPlayer player, String topic, String does, boolean yes) {
		if (!Friends.eligible(drifter)) {
			return;
		}

		if (does.startsWith("favor_")) {
			FriendPerks.favorAnswered(drifter, player, does.substring("favor_".length()), yes);
			return;
		}

		if (!does.equals("opinion") && !does.equals("welcome")) {
			return;
		}

		Friends.Bond b = Friends.bondOrNew(drifter, player);
		long now = Friends.now();
		long day = now / 24000L;
		if (b.talkDay != day) {
			b.talkDay = day;
			b.talksToday = 0;
		}

		CivConfig.Friends cfg = CivConfig.friends();
		double worth = Math.pow(cfg.spamFalloff, b.talksToday);
		b.talksToday++;
		String name = player.getGameProfile().getName();
		double trust = 0.0;
		double familiarity = TALK_FAMILIARITY * worth;
		if (does.equals("opinion")) {
			Friends.share(b, topic, yes);
			if (yes) {
				trust = AGREE_TRUST * cfg.agreeWeight * worth;
			} else if (argumentative(drifter)) {
				trust = ARGUE_TRUST * worth;
			} else if (thinSkinned(drifter)) {
				trust = THIN_SKIN_TRUST * cfg.disagreeWeight * worth;
				familiarity = 0.0;
			} else {
				trust = DISAGREE_TRUST * cfg.disagreeWeight * worth;
			}
		}

		if (trust != 0.0) {
			DrifterApi.addTrustInPlayer(drifter, player.getUUID(), name, trust);
		}

		if (familiarity > 0.0) {
			TalkApi.addFamiliarity(drifter, player.getUUID(), name, familiarity);
		}

		DrifterApi.think(drifter, String.format(Locale.ROOT, "%s %s on %s (trust %+.1f, talk %d today)", name, yes ? "agreed" : "disagreed",
			topic.replace("friends_op_", "").replace('_', ' '), trust, b.talksToday));
		Friends.dirty();
		goOn(drifter, player, now);
	}

	/** Enjoys an argument: hot-headed but not one to hold it against them. */
	static boolean argumentative(DrifterEntity drifter) {
		return DrifterApi.trait(drifter, "aggression") >= 0.6 && DrifterApi.trait(drifter, "forgiveness") >= 0.4;
	}

	/** Takes disagreeing to heart. */
	static boolean thinSkinned(DrifterEntity drifter) {
		return DrifterApi.trait(drifter, "patience") < 0.35 && DrifterApi.trait(drifter, "vengefulness") >= 0.6;
	}

	/** A talk goes on to another topic while this sitting's not had its fill for the stage. */
	private static void goOn(DrifterEntity drifter, ServerPlayer player, long now) {
		String key = pair(drifter.getUUID(), player.getUUID());
		long[] sitting = sittings.computeIfAbsent(key, k -> new long[] {0, now});
		if (now - sitting[1] > SITTING_ENDS) {
			sitting[0] = 0;
		}

		sitting[0]++;
		sitting[1] = now;
		Friends.Stage stage = Friends.stage(drifter, player);
		if (sitting[0] < SITTING[stage.ordinal()]) {
			pending.add(new Pending(drifter.getUUID(), player.getUUID(), now + 100 + drifter.getRandom().nextInt(80), null));
		}
	}

	// ----- What they say to it -----

	private static boolean heard(ServerPlayer player, DrifterEntity drifter, String text, @Nullable String intent) {
		if (!Friends.eligible(drifter)) {
			return false;
		}

		Friends.Stage stage = Friends.stage(drifter, player);
		long now = Friends.now();
		// "what do you think about the nether?": its take on it, if it has one (an acquaintance or closer; to a stranger,
		// Drifter's own answer).
		if (stage.ordinal() >= Friends.Stage.ACQUAINTANCE.ordinal() && TalkApi.meaning(text, "friends_think") != null) {
			String topic = takeOn(drifter, player, text);
			if (topic != null) {
				if (brushOff(drifter, player, now)) {
					return true;
				}

				pending.add(new Pending(drifter.getUUID(), player.getUUID(), now + 30 + drifter.getRandom().nextInt(30), topic));
				return true;
			}
		}

		// "how's it going", "wyd": Drifter answers that; one who knows them well may go on to something after.
		if (stage.ordinal() >= Friends.Stage.ACQUAINTANCE.ordinal() && intent != null && (intent.equals("hru") || intent.equals("wyd"))
			&& drifter.getRandom().nextDouble() < 0.6) {
			pending.add(new Pending(drifter.getUUID(), player.getUUID(), now + 120 + drifter.getRandom().nextInt(60), null));
		}

		return false;
	}

	/** A hot take of its on what the message names (a topic's tag or a word of its id), or null. */
	@Nullable
	private static String takeOn(DrifterEntity drifter, ServerPlayer player, String text) {
		List<String> found = new ArrayList<>();
		for (String word : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z_ ]", " ").split("\\s+")) {
			if (word.length() < 3) {
				continue;
			}

			String singular = word.endsWith("s") && word.length() > 4 ? word.substring(0, word.length() - 1) : word;
			for (String w : List.of(word, singular, word + "s")) {
				for (String id : TalkApi.playerTopics(w)) {
					if (id.startsWith("friends_op_") && !found.contains(id)) {
						found.add(id);
					}
				}
			}
		}

		if (found.isEmpty()) {
			return null;
		}

		// (One they've not answered yet first.)
		Friends.Bond b = Friends.bond(drifter, player);
		List<String> fresh = new ArrayList<>(found);
		if (b != null) {
			fresh.removeIf(id -> b.shared.stream().anyMatch(s -> s.startsWith(id + ":")));
		}

		List<String> from = fresh.isEmpty() ? found : fresh;
		return from.get(drifter.getRandom().nextInt(from.size()));
	}

	/** Not now: hungry, hurt, grieving, in a fight or busy. Says so (once in a while) and counts nothing; true if so. */
	static boolean brushOff(DrifterEntity drifter, ServerPlayer player, long now) {
		boolean busy = drifter.getFoodData().getFoodLevel() <= 6 || drifter.getHealth() < drifter.getMaxHealth() * 0.4F
			|| DrifterApi.mood(drifter, "grieving") >= 0.5 || DrifterApi.mood(drifter, "panicked") >= 0.5 || drifter.getTarget() != null;
		if (!busy) {
			return false;
		}

		if (now - brushedOff.getOrDefault(drifter.getUUID(), Long.MIN_VALUE / 2) > 20L * 30) {
			brushedOff.put(drifter.getUUID(), now);
			String line = TalkApi.line(drifter, "friends.brush_off", "player", TalkApi.callName(drifter, player));
			if (line != null) {
				TalkApi.sayTo(drifter, player, line);
			}
		}

		return true;
	}

	// ----- Bringing things up -----

	static void tick(MinecraftServer server, long now) {
		List<Pending> later = new ArrayList<>();
		for (Iterator<Pending> it = pending.iterator(); it.hasNext();) {
			Pending p = it.next();
			if (now < p.at()) {
				continue;
			}

			it.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(p.player());
			if (player == null) {
				continue;
			}

			DrifterEntity drifter = near(player, p.drifter());
			// (Still saying its last bit: a moment later.)
			if (drifter != null && TalkApi.talking(drifter) && p.tries() < 4) {
				later.add(new Pending(p.drifter(), p.player(), now + 40, p.topic(), p.tries() + 1));
				continue;
			}

			if (drifter != null && !TalkApi.talking(drifter) && !brushOff(drifter, player, now)) {
				String topic = p.topic() != null ? p.topic() : pick(drifter, player);
				if (topic != null) {
					start(drifter, player, topic, now);
				}
			}
		}

		pending.addAll(later);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (now - lastPlayerOpening.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) < PLAYER_GAP) {
				continue;
			}

			for (DrifterEntity drifter : player.serverLevel().getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(NEAR), Friends::eligible)) {
				Friends.Stage stage = Friends.stage(drifter, player);
				if (stage == Friends.Stage.STRANGER || TalkApi.talking(drifter) || now - TalkApi.lastSeen(drifter, player.getUUID()) > 40
					|| now - lastOpening.getOrDefault(pair(drifter.getUUID(), player.getUUID()), Long.MIN_VALUE / 2) < PAIR_GAP[stage.ordinal() - 1]
					|| drifter.getFoodData().getFoodLevel() <= 6 || drifter.getTarget() != null) {
					continue;
				}

				if (drifter.getRandom().nextDouble() < 0.05 * (1.0 + DrifterApi.trait(drifter, "sociability"))) {
					String topic = pick(drifter, player);
					if (topic != null && start(drifter, player, topic, now)) {
						break;
					}
				}
			}
		}
	}

	@Nullable
	private static DrifterEntity near(ServerPlayer player, UUID drifter) {
		for (DrifterEntity d : player.serverLevel().getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(NEAR * 2), d -> d.getUUID().equals(drifter))) {
			return d.isAlive() ? d : null;
		}

		return null;
	}

	private static boolean start(DrifterEntity drifter, ServerPlayer player, String topic, long now) {
		if (!TalkApi.startTalk(drifter, player, topic)) {
			return false;
		}

		lastOpening.put(pair(drifter.getUUID(), player.getUUID()), now);
		lastPlayerOpening.put(player.getUUID(), now);
		return true;
	}

	/** A topic for how well it knows them: acquaintances its hot takes, friends personal talk too, best friends their own. */
	@Nullable
	static String pick(DrifterEntity drifter, ServerPlayer player) {
		Friends.Bond b = Friends.bond(drifter, player);
		Friends.Stage stage = Friends.stage(drifter, player);
		if (b == null || stage == Friends.Stage.STRANGER) {
			return null;
		}

		double roll = drifter.getRandom().nextDouble();
		String tag = switch (stage) {
			case ACQUAINTANCE -> roll < 0.8 ? "opinion" : "small_talk";
			case FRIEND -> roll < 0.5 ? "opinion" : roll < 0.9 ? "personal" : "small_talk";
			default -> roll < 0.35 ? "opinion" : roll < 0.7 ? "personal" : "best_friends";
		};
		List<String> ids = new ArrayList<>(TalkApi.playerTopics(tag));
		ids.removeIf(id -> !id.startsWith("friends_"));
		// (The ones that bring up a shared opinion need one: a best friend made by the debug command may have none.)
		if (b.agreed() == 0) {
			ids.removeIf(id -> id.contains("shared") || id.contains("tease"));
		}
		if (tag.equals("opinion")) {
			// (Mostly ones not yet asked; now and then an old one again.)
			List<String> fresh = new ArrayList<>(ids);
			fresh.removeIf(id -> b.shared.stream().anyMatch(s -> s.startsWith(id + ":")));
			if (!fresh.isEmpty() && drifter.getRandom().nextDouble() < 0.85) {
				ids = fresh;
			}
		}

		return ids.isEmpty() ? null : ids.get(drifter.getRandom().nextInt(ids.size()));
	}

	// ----- Debug -----

	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("talk").requires(source -> source.hasPermission(2))
			.executes(ctx -> talk(ctx.getSource(), null))
			.then(Commands.argument("topic", StringArgumentType.word()).executes(ctx -> talk(ctx.getSource(), StringArgumentType.getString(ctx, "topic"))));
	}

	private static int talk(CommandSourceStack source, @Nullable String topic) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		DrifterEntity drifter = Friends.looked(player);
		if (drifter == null) {
			source.sendFailure(Component.literal("No Drifter in front of you"));
			return 0;
		}

		String id = topic != null ? topic : pick(drifter, player);
		boolean started = id != null && TalkApi.startTalk(drifter, player, id);
		source.sendSuccess(() -> Component.literal("Debug: " + drifter.getName().getString() + (started ? " brings up " + id : " has nothing to bring up")), false);
		return started ? 1 : 0;
	}
}
