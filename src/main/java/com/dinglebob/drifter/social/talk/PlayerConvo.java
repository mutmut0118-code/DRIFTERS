package com.dinglebob.drifter.social.talk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.personality.Mood;
import com.dinglebob.drifter.personality.Trait;
import com.dinglebob.drifter.social.Lines;
import com.dinglebob.drifter.social.Mode;
import com.dinglebob.drifter.social.Relationship;
import com.dinglebob.drifter.social.SocialEvent;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;

/**
 * Holding a conversation with a player (docs/PLAYER_TALK.md, batch 1; the user, 2026-11-12: "they never really stop and
 * talk and wait for an answer"). Who a player is talking with and for how long (so it stops and faces them:
 * {@link ListenGoal}; so the others near keep it down: {@link #hush}), what it said last and why (for "why?" and
 * "wdym"), what its own opener asked (so "i am" answers "new here?"), and a reply to everything said to it: the glue
 * of a talk ("lol", "thats cool", "nvm"), a first set of things to ask and tell it, and a fallback when it can't place
 * a message at all. The lines are {@code convo.*} in the chat data.
 */
public final class PlayerConvo {
	private PlayerConvo() {
	}

	/** Things to ask and tell it (matched before the glue: a real question wins over a filler phrase). */
	static final String[] TOPICS = {"have", "gear", "your_house", "nice_house", "show_house", "today", "good_pvp", "mood_why", "who_is", "grudge",
		"be_friends", "where_food", "danger", "news", "who_died", "new_tips", "im_new", "im_hurt", "im_lost", "im_scared", "stay", "look", "mine_with",
		"hunt_with", "warning", "truce", "offer", "come_in", "sleep_here", "danger_tell", "threat_all", "threat", "wake_up", "jk"};
	/** The glue: reactions, asking it to explain, steering the talk. */
	static final String[] GLUE = {"laugh", "nice", "wow", "agree", "doubt", "sympathy", "ack", "pushback", "why", "wdym", "nvm", "talk_to_me",
		"you_there", "not_you", "wait_up", "sorry", "middle", "brb", "gl", "iam", "im_good", "im_bad", "everyone"};
	/** Things that only count when nothing older is meant by the message (their phrases are in too many others). */
	private static final Set<String> WEAK = Set.of("who_is", "look");
	/** Asked while it sleeps or fights, these still get through. */
	private static final Set<String> PRESSING = Set.of("warning", "offer", "truce", "wake_up", "help");

	/** How long it waits for their next message (each one renews it), and after "wait here". */
	private static final long HOLD = 20L * 22;
	private static final long STAY = 20L * 90;
	/** How close they have to stay for it to keep waiting. */
	private static final double EARSHOT = 14.0;
	/** Others this close to a player in a talk keep it down; right after the player's message, altogether. */
	private static final double HUSH_RANGE = 24.0;
	private static final long HUSH_AFTER = 20L * 5;
	private static final double HUSH_CHANCE = 0.75;
	/** How long its opener's question waits for an answer. */
	private static final long EXPECTS = 20L * 45;

	private static final class Convo {
		UUID drifter;
		long until;
		long lastPlayerAt;
		int exchanges;
		int unknowns;
		boolean hold = true;
		boolean closed;
		/** The mood of the talk, set as it starts and kept: "" (as it is with anyone), "cold" (wary of them, cross), "down" (grieving). */
		String tone = "";
		/** What its opener asked ("new", "pvp", "lost"), till when. */
		@Nullable
		String expecting;
		long expectUntil;
		/** Somewhere they pointed it at, till when. */
		@Nullable
		Vec3 lookAt;
		long lookUntil;
		/** It brings something up itself at this time (a talk going quiet). */
		long followAt = -1;
	}

	/** Each player's talk (the one Drifter they're talking with). */
	private static final Map<UUID, Convo> convos = new HashMap<>();
	/** What each Drifter last answered a player: the line's key and the line. */
	private static final Map<UUID, String[]> lastSaid = new HashMap<>();
	/** Things that count once in a while ("drifter|player|what" to when). */
	private static final Map<String, Long> once = new HashMap<>();

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PlayerConvo::tick);
	}

	// ---------------------------------------------------------------------------------------------
	// The talk's state
	// ---------------------------------------------------------------------------------------------

	/** They said something to it, or it to them: the talk goes on. */
	static void touch(ServerPlayer player, DrifterEntity drifter, long now, boolean theirs) {
		// (What it's doing as they speak, kept for "wyd" once it has stopped to talk.)
		PlayerTalk.doing(drifter);
		Convo c = convos.get(player.getUUID());
		if (c == null || !c.drifter.equals(drifter.getUUID()) || now > c.until + 20L * 30) {
			c = new Convo();
			c.drifter = drifter.getUUID();
			c.tone = tone(drifter, player, now);
			convos.put(player.getUUID(), c);
		}

		c.until = Math.max(c.until, now + HOLD);
		c.closed = false;
		c.hold = true;
		if (theirs) {
			c.lastPlayerAt = now;
			c.exchanges++;
			c.followAt = -1;
			// (Talking is getting to know someone.)
			Relationship r = drifter.getSocial().getOrMeet(player.getUUID(), player.getGameProfile().getName());
			if (onceIn(drifter, player, "familiar", now, 20L * 20)) {
				r.addFamiliarity(0.5);
			}
		}
	}

	/**
	 * The mood a talk with them starts in, and stays in (the user, 2026-11-12: "i want the mood to keep going": one that
	 * greets them with "oh. its you" doesn't turn friendly at "you okay?"). Cold: it's wary of them (warning them off its
	 * place, keeping clear), doesn't trust them, was insulted or threatened by them lately, or is in a temper. Down: it's
	 * grieving. Else as it is with anyone.
	 */
	private static String tone(DrifterEntity drifter, ServerPlayer player, long now) {
		Mode mode = Mode.toward(drifter, player);
		Relationship r = drifter.getSocial().get(player.getUUID());
		Mood mood = drifter.getPersonality().mood().current();
		boolean insulted = r != null && r.lastInsultAt >= 0 && now - r.lastInsultAt < 24000L && !r.lastInsult.isEmpty();
		if (mode == Mode.WARNING || mode == Mode.AVOIDING || r != null && r.trust < 0.0 || insulted || lately(drifter, player, "threat", now, 24000L)
			|| mood == Mood.ANGRY || mood == Mood.FRUSTRATED) {
			return "cold";
		}

		return mood == Mood.GRIEVING ? "down" : "";
	}

	/** The line kind to say for this one, in the mood of the talk it's in: its cold or down lines when it has them. */
	static String toned(DrifterEntity drifter, String key) {
		if (convos.isEmpty()) {
			return key;
		}

		long now = drifter.level().getGameTime();
		for (Convo c : convos.values()) {
			if (c.drifter.equals(drifter.getUUID()) && now < c.until + 20L * 30 && !c.tone.isEmpty()) {
				return Lines.has(key + "." + c.tone) ? key + "." + c.tone : key;
			}
		}

		return key;
	}

	/** The talk warms up (an apology taken, a joke owned up to): its mood is as with anyone again. */
	private static void warm(ServerPlayer player) {
		Convo c = convos.get(player.getUUID());
		if (c != null && c.tone.equals("cold")) {
			c.tone = "";
		}
	}

	/** In a cold talk with this player. */
	private static boolean cold(ServerPlayer player) {
		Convo c = convos.get(player.getUUID());
		return c != null && c.tone.equals("cold");
	}

	/**
	 * Out to hurt them, or running from them: no talk at all ("get lost"). Only wary of them (warning them off its house,
	 * keeping clear) isn't that: it talks, coldly (the user, 2026-11-12: "awesome" got "get lost" from one that had just
	 * offered to show them round, for standing by its house).
	 */
	static boolean enemy(DrifterEntity drifter, ServerPlayer player) {
		if (Spar.on(drifter, player)) {
			return false;
		}

		Mode mode = Mode.toward(drifter, player);
		if (mode == Mode.WARNING || mode == Mode.AVOIDING) {
			Relationship r = drifter.getSocial().get(player.getUUID());
			return r != null && r.trust < -40.0 || drifter.getTarget() == player
				|| drifter.getLastHurtByMob() == player && drifter.tickCount - drifter.getLastHurtByMobTimestamp() < 20 * 30;
		}

		return mode.hostile() || Mode.atOdds(drifter, player);
	}

	/** It started a talk with them: this line of its opener, which may have asked something. */
	static void opened(ServerPlayer player, DrifterEntity drifter, String topicId, String line, long now) {
		touch(player, drifter, now, false);
		Convo c = convos.get(player.getUUID());
		String lower = line.toLowerCase(Locale.ROOT);
		String expecting = !topicId.equals("hi_stranger") ? null : lower.contains("pvp") ? "pvp" : lower.contains("lost") ? "lost"
			: lower.contains("new") || lower.contains("seen you") || lower.contains("who are you") || lower.contains("stranger") ? "new" : "hi";
		if (expecting != null && (c.expecting == null || expecting.equals("pvp") || expecting.equals("lost") || !lower.contains("btw") && !lower.contains("name"))) {
			c.expecting = expecting;
		}

		c.expectUntil = now + EXPECTS;
		c.until = Math.max(c.until, now + EXPECTS);
	}

	/** What it's doing for them now has it moving (following, leading, fetching): it doesn't stand and wait. */
	static void moving(ServerPlayer player) {
		Convo c = convos.get(player.getUUID());
		if (c != null) {
			c.hold = false;
		}
	}

	/** The talk's over (a bye, "not you"). */
	static void over(ServerPlayer player) {
		Convo c = convos.get(player.getUUID());
		if (c != null) {
			c.until = 0;
			c.closed = true;
			c.expecting = null;
		}
	}

	/** It answered a player with this (so "why?" and "wdym" have something to go on). */
	static void said(DrifterEntity drifter, String key, String line) {
		// (Not its own "?" or "i said ...": "wdym" after those has nothing to repeat.)
		if (!key.startsWith("convo.unknown") && !key.startsWith("convo.wdym")) {
			lastSaid.put(drifter.getUUID(), new String[] {key, line});
		}
	}

	/** What each Drifter was doing before it stopped to talk. */
	private static final Map<UUID, String> before = new HashMap<>();

	static void doingNow(DrifterEntity drifter, @Nullable String doing) {
		if (doing == null) {
			before.remove(drifter.getUUID());
		} else {
			before.put(drifter.getUUID(), doing);
		}
	}

	@Nullable
	static String wasDoing(DrifterEntity drifter) {
		return before.get(drifter.getUUID());
	}

	/** They've said something to this one since then. */
	static boolean saidSince(ServerPlayer player, DrifterEntity drifter, long since) {
		Convo c = convos.get(player.getUUID());
		return c != null && c.drifter.equals(drifter.getUUID()) && c.exchanges > 0 && c.lastPlayerAt > since;
	}

	/** The player it's standing and talking with, or null: {@link ListenGoal} keeps it there, facing them. */
	@Nullable
	public static ServerPlayer listeningTo(DrifterEntity drifter) {
		MinecraftServer server = drifter.getServer();
		if (server == null || convos.isEmpty()) {
			return null;
		}

		long now = drifter.level().getGameTime();
		for (Map.Entry<UUID, Convo> entry : convos.entrySet()) {
			Convo c = entry.getValue();
			if (c.hold && now < c.until && c.drifter.equals(drifter.getUUID())) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null && player.isAlive() && player.level() == drifter.level() && player.distanceTo(drifter) <= EARSHOT) {
					return player;
				}
			}
		}

		return null;
	}

	/** Where they pointed it at just now, or null (it looks at them). */
	@Nullable
	public static Vec3 pointedAt(DrifterEntity drifter, ServerPlayer player) {
		Convo c = convos.get(player.getUUID());
		return c != null && c.lookAt != null && drifter.level().getGameTime() < c.lookUntil ? c.lookAt : null;
	}

	/** This player is in a talk with a Drifter now. */
	public static boolean talking(ServerPlayer player) {
		Convo c = convos.get(player.getUUID());
		return c != null && player.level().getGameTime() < c.until;
	}

	/** The Drifter this player is talking with now, or null. */
	@Nullable
	public static UUID partner(ServerPlayer player) {
		Convo c = convos.get(player.getUUID());
		return c != null && player.level().getGameTime() < c.until ? c.drifter : null;
	}

	/**
	 * A player near this Drifter is talking with another one: it keeps its own chatter down (always in the moments after
	 * the player said something, mostly otherwise), so its lines don't land as if they were answers.
	 */
	public static boolean hush(DrifterEntity drifter, long now) {
		MinecraftServer server = drifter.getServer();
		if (server == null || convos.isEmpty()) {
			return false;
		}

		for (Map.Entry<UUID, Convo> entry : convos.entrySet()) {
			Convo c = entry.getValue();
			if (now >= c.until || c.drifter.equals(drifter.getUUID())) {
				continue;
			}

			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null && player.level() == drifter.level() && player.distanceTo(drifter) <= HUSH_RANGE
				&& (now - c.lastPlayerAt < HUSH_AFTER || drifter.getRandom().nextDouble() < HUSH_CHANCE)) {
				return true;
			}
		}

		return false;
	}

	private static void tick(MinecraftServer server) {
		if (convos.isEmpty() || server.getTickCount() % 10 != 0) {
			return;
		}

		long now = server.overworld().getGameTime();
		for (Iterator<Map.Entry<UUID, Convo>> it = convos.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Convo> entry = it.next();
			Convo c = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			DrifterEntity drifter = player == null ? null : player.serverLevel().getEntity(c.drifter) instanceof DrifterEntity d && d.isAlive() ? d : null;
			if (drifter == null) {
				it.remove();
				continue;
			}

			// A talk gone quiet: now and then it brings something up itself.
			if (c.followAt > 0 && now >= c.followAt && now < c.until) {
				c.followAt = -1;
				if (Conversations.idle(drifter) && !PlayerTalk.answering(drifter) && player.distanceTo(drifter) <= EARSHOT) {
					PlayerChats.start(drifter, player, null, now);
				}
			}

			if (now >= c.until && !c.closed) {
				c.closed = true;
				// Back to what it was doing: it says so, when they'd been talking a while and they're still there.
				if (c.hold && c.exchanges >= 3 && player.distanceTo(drifter) <= EARSHOT && !drifter.isSleeping() && !drifter.inAFight()
					&& drifter.getRandom().nextDouble() < 0.6) {
					String doing = PlayerTalk.doing(drifter);
					String line = Lines.pick(drifter, doing == null ? "convo.gtg" : "convo.gtg_doing", "player", Heard.callName(drifter, player), "doing",
						doing == null ? "" : doing);
					if (line != null) {
						com.dinglebob.drifter.social.ChatLines.to(drifter, player);
						com.dinglebob.drifter.social.ChatLines.speak(drifter, line);
					}
				}
			}

			if (now > c.until + 20L * 60) {
				it.remove();
			}
		}

		if (server.getTickCount() % 1200 == 0) {
			once.values().removeIf(at -> now - at > 24000L);
		}
	}

	/** True the first time in {@code every} ticks for this pair and thing. */
	private static boolean onceIn(DrifterEntity drifter, ServerPlayer player, String what, long now, long every) {
		String key = drifter.getUUID() + "|" + player.getUUID() + "|" + what;
		Long last = once.get(key);
		if (last != null && now - last < every) {
			return false;
		}

		once.put(key, now);
		return true;
	}

	private static boolean lately(DrifterEntity drifter, ServerPlayer player, String what, long now, long within) {
		Long last = once.get(drifter.getUUID() + "|" + player.getUUID() + "|" + what);
		return last != null && now - last < within;
	}

	// ---------------------------------------------------------------------------------------------
	// What a message means
	// ---------------------------------------------------------------------------------------------

	/**
	 * What this message is, of the things here: a topic when its phrase is longer than the one {@code old} (an older
	 * intent) matched, else the glue when nothing else matched; or null.
	 */
	@Nullable
	static String intent(String text, @Nullable String old) {
		String topic = Lines.answer(text, TOPICS);
		if (topic != null) {
			if (old == null) {
				return topic;
			}

			if (WEAK.contains(topic)) {
				return null;
			}

			String mine = Lines.phrase(text, topic);
			String theirs = Lines.phrase(text, old);
			return mine != null && (theirs == null || mine.length() >= theirs.length()) ? topic : null;
		}

		return old == null ? Lines.answer(text, GLUE) : null;
	}

	/** Said to no one by name, but to all of them ("you guys", "anyone"). */
	static boolean toEveryone(String text) {
		return Lines.answer(text, "everyone") != null;
	}

	// ---------------------------------------------------------------------------------------------
	// Before anything else: asleep, in a fight, rude
	// ---------------------------------------------------------------------------------------------

	/**
	 * Said to it while it can't talk: true when that's all there is to say (asleep: "zzz"; in a fight: "busy!!";
	 * something it won't answer). A talk it's in with another Drifter breaks off for the player.
	 */
	static boolean gate(DrifterEntity drifter, ServerPlayer player, String text, @Nullable String old, @Nullable String mine, long now) {
		String name = Heard.callName(drifter, player);
		if (com.dinglebob.drifter.identity.Usernames.rude(text)) {
			if (onceIn(drifter, player, "gross", now, 20L * 60)) {
				drifter.getSocial().record(player.getUUID(), name, SocialEvent.INSULT, 0.0);
				PlayerTalk.reply(drifter, "convo.gross", null, "player", name);
			}

			return true;
		}

		boolean pressing = old != null && PRESSING.contains(old) || mine != null && PRESSING.contains(mine);
		if (drifter.isSleeping()) {
			Relationship r = drifter.getSocial().getOrMeet(player.getUUID(), player.getGameProfile().getName());
			boolean wakes = pressing && (r.trust >= PlayerTalk.KNOWS_THEM || "help".equals(old) || "warning".equals(mine));
			if (wakes) {
				drifter.stopSleeping();
				PlayerTalk.reply(drifter, "convo.woken", null, "player", name);
				// ("wake up" alone is answered by waking; anything more is answered next.)
				return "wake_up".equals(mine);
			}

			if (onceIn(drifter, player, "asleep", now, 20L * 8)) {
				PlayerTalk.reply(drifter, "convo.asleep", null, "player", name);
			}

			return true;
		}

		if (drifter.inAFight() && !pressing && !Spar.on(drifter, player)) {
			if (onceIn(drifter, player, "fighting", now, 20L * 6)) {
				PlayerTalk.reply(drifter, "convo.fighting", null, "player", name);
			}

			return true;
		}

		Conversations.interrupt(drifter, now);
		return false;
	}

	// ---------------------------------------------------------------------------------------------
	// An answer to its own question
	// ---------------------------------------------------------------------------------------------

	private static final Pattern INTRO = Pattern.compile("\\b(?:im|i'm|i am|its|it's|names|name's|name is|this is|call me|they call me)\\s+([a-z0-9_]{2,16})\\b");

	/** They gave their name ("hi im muto"): a part of their username, or what everyone calls them. */
	private static boolean introduced(ServerPlayer player, String text) {
		Matcher m = INTRO.matcher(text.toLowerCase(Locale.ROOT));
		String user = player.getGameProfile().getName().toLowerCase(Locale.ROOT);
		String nick = com.dinglebob.drifter.identity.Nicknames.of(player.getGameProfile().getName()).toLowerCase(Locale.ROOT);
		while (m.find()) {
			String word = m.group(1);
			if (word.length() >= 3 && (user.contains(word) || nick.contains(word) || word.contains(nick) && nick.length() >= 3)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Their message as the answer to what its opener asked ("hey, new here?" ... "i am"); false when it isn't one (or it
	 * asked nothing).
	 */
	private static boolean expected(DrifterEntity drifter, ServerPlayer player, Convo c, String text, @Nullable String mine, long now) {
		if (c.expecting == null || now >= c.expectUntil) {
			c.expecting = null;
			return false;
		}

		String asked = c.expecting;
		String name = Heard.callName(drifter, player);
		Relationship r = drifter.getSocial().getOrMeet(player.getUUID(), player.getGameProfile().getName());
		if (introduced(player, text)) {
			c.expecting = null;
			r.addFamiliarity(3.0);
			say(drifter, player, "convo.met", null);
			return true;
		}

		String yes = "im_new".equals(mine) || "iam".equals(mine) ? "yes" : Lines.answer(text, "yes", "no", "ready", "unsure", "middle", "greeting");
		if (yes == null || mine != null && !mine.equals("im_new") && !mine.equals("iam") && !mine.equals("middle")) {
			return false;
		}

		c.expecting = null;
		// (They've met now: no "new here?" again.)
		r.addFamiliarity(3.0);
		boolean no = yes.equals("no");
		switch (asked) {
			case "pvp" -> {
				if (no) {
					say(drifter, player, "convo.pvp.you_no", null);
				} else {
					offerDuel(drifter, player, now);
				}
			}
			case "lost" -> {
				if (no || yes.equals("greeting")) {
					say(drifter, player, "convo.ack", null);
				} else {
					lost(drifter, player, r);
				}
			}
			default -> {
				if (yes.equals("greeting")) {
					say(drifter, player, "convo.met_hi", null);
				} else if (no) {
					say(drifter, player, "convo.new_no", null);
				} else {
					welcome(drifter, player, "convo.new_yes", now);
				}
			}
		}

		return true;
	}

	/** A welcome to someone new, and (where there's a town to show) an offer to show them round. */
	private static void welcome(DrifterEntity drifter, ServerPlayer player, String key, long now) {
		boolean tour = !Mode.atOdds(drifter, player) && !cold(player) && !com.dinglebob.drifter.api.TalkApi.places(drifter).isEmpty()
			&& drifter.getPersonality().get(Trait.SOCIABILITY) >= 0.35;
		say(drifter, player, tour ? key + "_tour" : key, null);
		if (tour) {
			PlayerTalk.ask(drifter, player, new Topics.Question(Map.of(), "tour", Lines.voices("convo.tour.declined")), vars(drifter, player), now, "tour_offer");
		}
	}

	private static void offerDuel(DrifterEntity drifter, ServerPlayer player, long now) {
		say(drifter, player, "convo.pvp.offer", null);
		PlayerTalk.ask(drifter, player, new Topics.Question(Map.of(), "duel", Lines.voices("convo.pvp.declined")), vars(drifter, player), now, "pvp_offer");
	}

	// ---------------------------------------------------------------------------------------------
	// What it says and does
	// ---------------------------------------------------------------------------------------------

	/**
	 * Said to it, and not one of the older things to ask: {@code mine} is what it is of the things here (null: nothing it
	 * knows). It always says something.
	 */
	static void handle(DrifterEntity drifter, ServerPlayer player, String text, @Nullable String mine, long now) {
		Convo c = convos.get(player.getUUID());
		if (c != null && c.drifter.equals(drifter.getUUID()) && expected(drifter, player, c, text, mine, now)) {
			return;
		}

		Relationship r = drifter.getSocial().getOrMeet(player.getUUID(), player.getGameProfile().getName());
		String name = Heard.callName(drifter, player);
		Mode mode = Mode.toward(drifter, player);
		boolean hostile = enemy(drifter, player);
		boolean friend = r.trust >= Mode.FRIENDLY_TRUST;
		boolean knows = r.trust >= PlayerTalk.KNOWS_THEM;
		boolean helper = drifter.getPersonality().archetype() == Archetype.HELPER;
		double bravery = drifter.getPersonality().get(Trait.BRAVERY);
		if (c != null && mine != null) {
			c.unknowns = 0;
		}

		if (mine == null) {
			// The chat rituals, said to this one: it answers, not the room.
			String ritual = Lines.answer(text, "greeting", "night", "bye", "thanks");
			if (ritual != null && !hostile) {
				switch (ritual) {
					case "greeting" -> say(drifter, player, introduced(player, text) ? "convo.met" : "greet_back", null);
					case "night" -> {
						say(drifter, player, "night_back", null);
						over(player);
					}
					case "bye" -> {
						say(drifter, player, "bye_back", null);
						over(player);
					}
					default -> say(drifter, player, "you_welcome", null);
				}

				return;
			}

			if (introduced(player, text) && !hostile) {
				r.addFamiliarity(1.0);
				say(drifter, player, "convo.met", null);
				return;
			}

			if (hostile) {
				say(drifter, player, "ask.hostile", null);
				return;
			}

			// Nothing it knows: it says so, or brings something up itself.
			int unknowns = c == null ? 0 : c.unknowns++;
			say(drifter, player, unknowns == 0 ? "convo.unknown" : "convo.unknown_again", null);
			followUp(drifter, c, now, 0.5);
			return;
		}

		// (Things said to an enemy that it still takes in.)
		switch (mine) {
			case "threat", "threat_all" -> {
				threat(drifter, player, r, mine.equals("threat_all"), bravery, now);
				return;
			}
			case "truce" -> {
				truce(drifter, player, r, hostile, now);
				return;
			}
			case "sorry" -> {
				sorry(drifter, player, r, now);
				return;
			}
			case "jk" -> {
				joking(drifter, player, r, now);
				return;
			}
			case "grudge" -> {
				grudge(drifter, player, r, now);
				return;
			}
			case "warning" -> {
				warning(drifter, player, r, now);
				return;
			}
			default -> {
			}
		}

		if (hostile) {
			say(drifter, player, "ask.hostile", null);
			return;
		}

		switch (mine) {
			// ----- The glue -----
			case "laugh", "nice", "wow", "agree", "doubt", "sympathy", "pushback", "gl", "brb", "nvm", "you_there" -> {
				say(drifter, player, "convo." + mine, null);
				if (mine.equals("brb") && c != null) {
					c.until = Math.max(c.until, now + STAY);
				}
			}
			case "ack", "middle", "iam" -> {
				say(drifter, player, mine.equals("ack") ? "convo.ack" : "convo.asked.middle", null);
				followUp(drifter, c, now, 0.45);
			}
			case "im_good", "im_bad" -> {
				say(drifter, player, "convo." + mine, null);
				followUp(drifter, c, now, 0.35);
			}
			case "everyone" -> say(drifter, player, "greet_back", null);
			case "why" -> why(drifter, player, r, c, now);
			case "wdym" -> {
				String[] last = lastSaid.get(drifter.getUUID());
				if (last == null || !say(drifter, player, "convo.wdym", null, "last", last[1])) {
					say(drifter, player, "convo.wdym_nothing", null);
				}
			}
			case "talk_to_me" -> {
				if (!Conversations.idle(drifter) || PlayerChats.start(drifter, player, null, now) == null) {
					say(drifter, player, "convo.talk_to_me", null);
				}
			}
			case "not_you" -> {
				say(drifter, player, "convo.not_you", null);
				over(player);
				PlayerTalk.unfocus(player);
			}
			case "wait_up", "stay" -> {
				say(drifter, player, "convo." + mine, null);
				drifter.getSocial().watcher().toldToStop(player);
				Convo held = convos.get(player.getUUID());
				if (held != null) {
					held.hold = true;
					held.until = Math.max(held.until, now + STAY);
				}
			}

			// ----- About it -----
			case "have" -> have(drifter, player, text);
			case "gear" -> gear(drifter, player, text);
			case "your_house", "nice_house" -> {
				Home home = drifter.getDrifterBrain().home();
				boolean here = home != null && home.center.closerThan(player.blockPosition(), 18.0);
				String key = "convo." + mine + (home == null ? ".none" : here ? ".here" : ".elsewhere");
				say(drifter, player, key, null, "way", home == null ? "" : PlayerTalk.way(player.position(), Vec3.atCenterOf(home.center)));
				if (mine.equals("nice_house") && home != null) {
					drifter.getSocial().record(player.getUUID(), name, SocialEvent.COMPLIMENT, 0.0);
				}
			}
			case "show_house" -> {
				Home home = drifter.getDrifterBrain().home();
				if (home == null) {
					say(drifter, player, "convo.show_house.none", null);
				} else if (friend || knows || helper && r.trust >= 0.0) {
					BlockPos at = home.center;
					say(drifter, player, "convo.show_house.yes", () -> GuideGoal.lead(drifter, player, List.of(new GuideGoal.Stop(at, "convo.guide.home", 6.0)), "its house"));
					moving(player);
				} else {
					say(drifter, player, "convo.show_house.no", null);
				}
			}
			case "today" -> today(drifter, player);
			case "good_pvp" -> {
				Archetype kind = drifter.getPersonality().archetype();
				boolean fighter = kind == Archetype.PVP_SWEAT || kind == Archetype.BANDIT || bravery + drifter.getPersonality().get(Trait.AGGRESSION) >= 1.1;
				if (fighter && armed(drifter)) {
					offerDuel(drifter, player, now);
				} else {
					say(drifter, player, armed(drifter) ? "convo.pvp.ok" : "convo.pvp.bad", null);
				}
			}
			case "mood_why" -> {
				String key = drifter.getHealth() <= drifter.getMaxHealth() * 0.4F ? "hurt" : drifter.getFoodData().getFoodLevel() <= 8 ? "hungry"
					: moodKey(drifter.getPersonality().mood().current());
				say(drifter, player, "convo.mood." + key, null);
			}
			case "who_is" -> whoIs(drifter, player, text);
			case "be_friends" -> {
				if (friend) {
					say(drifter, player, "convo.friends.already", null);
				} else if (knows) {
					if (onceIn(drifter, player, "befriend", now, 24000L)) {
						r.addTrust(2.0);
					}

					say(drifter, player, "convo.friends.yes", null);
				} else {
					say(drifter, player, "convo.friends.soon", null);
				}
			}

			// ----- What it knows -----
			case "where_food" -> {
				Item food = PlayerTalk.spareFood(drifter);
				if (food != null && (friend || helper && r.trust >= 0.0 || knows && drifter.getPersonality().get(Trait.GREED) < 0.5)) {
					say(drifter, player, "convo.food.offer", null);
					PlayerTalk.ask(drifter, player, new Topics.Question(Lines.voices("convo.food.give"), "give_food", Lines.voices("convo.food.declined")),
						vars(drifter, player), now, "food_offer");
				} else {
					say(drifter, player, "convo.food.where", null);
				}
			}
			case "danger" -> danger(drifter, player);
			case "news", "who_died" -> {
				if (!say(drifter, player, "convo.news.yes", null)) {
					say(drifter, player, mine.equals("news") ? "convo.news.none" : "convo.who_died.none", null);
				}
			}
			case "new_tips" -> welcome(drifter, player, "convo.tips", now);
			case "im_new" -> {
				r.addFamiliarity(3.0);
				welcome(drifter, player, "convo.new_yes", now);
			}

			// ----- Telling it how they are -----
			case "im_hurt" -> {
				Item food = PlayerTalk.spareFood(drifter);
				if (food != null && (friend || helper || knows)) {
					say(drifter, player, "convo.im_hurt.offer", null);
					PlayerTalk.ask(drifter, player, new Topics.Question(Lines.voices("convo.food.give"), "give_food", Lines.voices("convo.food.declined")),
						vars(drifter, player), now, "food_offer");
				} else {
					say(drifter, player, "convo.im_hurt", null);
				}
			}
			case "im_lost" -> lost(drifter, player, r);
			case "im_scared" -> say(drifter, player, bravery >= 0.55 ? "convo.im_scared.brave" : "convo.im_scared.same", null);
			case "danger_tell" -> {
				drifter.getHeard().places().add(new Heard.Place("danger", player.blockPosition(), player.level().dimension().location().toString(), false,
					player.getGameProfile().getName(), player.getUUID(), now, 0));
				Telling.thought(drifter, now, player.getGameProfile().getName() + " says it's dangerous near " + player.blockPosition().toShortString());
				say(drifter, player, r.trust >= 0.0 ? "convo.danger_tell.ok" : "convo.danger_tell.doubt", null);
			}

			// ----- Asking it to do things -----
			case "look" -> {
				Vec3 eye = player.getEyePosition();
				var hit = player.level().clip(new net.minecraft.world.level.ClipContext(eye, eye.add(player.getViewVector(1.0F).scale(24.0)),
					net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
				Convo held = convos.get(player.getUUID());
				if (held != null) {
					held.lookAt = hit.getLocation();
					held.lookUntil = now + 20L * 5;
				}

				drifter.getCamera().lookAt(hit.getLocation(), CameraModel.TurnSpeed.NORMAL);
				say(drifter, player, "convo.look", null);
			}
			case "mine_with", "hunt_with" -> outing(drifter, player, r, mine.equals("mine_with"), friend, knows, bravery);
			case "offer" -> {
				if (drifter.inAFight()) {
					say(drifter, player, "convo.offer.fight", null);
				} else {
					say(drifter, player, "convo.offer.yes", () -> RequestGoal.ask(drifter, RequestGoal.Kind.COME, player, null, 0));
					moving(player);
				}
			}
			case "come_in", "sleep_here" -> {
				// (Its own door, and it by it: else there's nothing to come into.)
				Home home = drifter.getDrifterBrain().home();
				if (home == null) {
					say(drifter, player, "convo.your_house.none", null);
					return;
				}

				if (mine.equals("sleep_here")) {
					say(drifter, player, friend ? "convo.sleep_here.yes" : "convo.sleep_here.no", null);
					return;
				}

				boolean night = player.level().getDayTime() % 24000L >= 13000L;
				boolean hoarder = drifter.getPersonality().archetype() == Archetype.HOARDER;
				boolean yes = friend || knows && !night && !hoarder || helper && r.trust >= 0.0 && !night;
				say(drifter, player, yes ? "convo.come_in.yes" : night && knows ? "convo.come_in.night" : "convo.come_in.no", null);
			}
			case "wake_up" -> say(drifter, player, "convo.wake_up.awake", null);
			default -> say(drifter, player, "convo.unknown", null);
		}
	}

	/** Now and then, a talk going quiet, it brings something up itself a few seconds on. */
	private static void followUp(DrifterEntity drifter, @Nullable Convo c, long now, double chance) {
		if (c != null && c.exchanges >= 2 && drifter.getRandom().nextDouble() < chance * (0.4 + drifter.getPersonality().get(Trait.SOCIABILITY))) {
			c.followAt = now + 20L * 5 + drifter.getRandom().nextInt(40);
			c.until = Math.max(c.until, c.followAt + HOLD);
		}
	}

	// ----- why -----

	private static void why(DrifterEntity drifter, ServerPlayer player, Relationship r, @Nullable Convo c, long now) {
		String[] last = lastSaid.get(drifter.getUUID());
		String key = last == null ? "" : last[0];
		boolean refused = key.endsWith("_no") || key.endsWith(".no") || key.contains("refuse") || key.endsWith(".night") || key.endsWith(".soon")
			|| key.contains(".no_") || key.endsWith(".scared") || key.endsWith(".hurt");
		String doing = PlayerTalk.doing(drifter);
		boolean night = player.level().getDayTime() % 24000L >= 13000L;
		String why;
		if (key.equals("convo.pvp.offer") || key.startsWith("ask.duel")) {
			why = "convo.why.pvp";
		} else if (!refused) {
			why = key.isEmpty() ? "convo.why.nothing" : "convo.why.dunno";
		} else if (r.lastInsultAt >= 0 && now - r.lastInsultAt < 24000L && !r.lastInsult.isEmpty()) {
			why = "convo.why.insult";
		} else if (drifter.getHealth() <= drifter.getMaxHealth() * 0.4F) {
			why = "convo.why.hurt";
		} else if (key.endsWith(".scared")) {
			why = "convo.why.scared";
		} else if (key.endsWith(".no_pick") || key.endsWith(".no_weapon")) {
			why = "convo.why.gear";
		} else if (r.trust < PlayerTalk.KNOWS_THEM) {
			why = "convo.why.trust";
		} else if (night) {
			why = "convo.why.night";
		} else if (doing != null) {
			why = "convo.why.busy";
		} else {
			why = "convo.why.mood";
		}

		say(drifter, player, why, null, "insult", r.lastInsult, "doing", doing == null ? "" : doing);
	}

	// ----- its things -----

	private static void have(DrifterEntity drifter, ServerPlayer player, String text) {
		String what = PlayerTalk.named(text);
		if (what == null) {
			say(drifter, player, "convo.have.what", null);
			return;
		}

		int count = drifter.getInventory().count(stack -> what.equals("food") ? stack.has(DataComponents.FOOD)
			: what.equals("wood") ? stack.is(net.minecraft.tags.ItemTags.LOGS) || stack.is(net.minecraft.tags.ItemTags.PLANKS)
			: stack.getHoverName().getString().toLowerCase(Locale.ROOT).equals(what));
		boolean guarded = drifter.getPersonality().archetype() == Archetype.HOARDER
			&& drifter.getSocial().getOrMeet(player.getUUID(), player.getGameProfile().getName()).trust < Mode.FRIENDLY_TRUST;
		say(drifter, player, guarded ? "convo.have.guarded" : count > 0 ? "convo.have.yes" : "convo.have.no", null, "item", what, "count",
			count >= 64 ? "a stack of" : count > 8 ? "plenty of" : count > 1 ? "a few" : "one");
	}

	private static final Map<String, EquipmentSlot> WORN = Map.ofEntries(Map.entry("helmet", EquipmentSlot.HEAD), Map.entry("hat", EquipmentSlot.HEAD),
		Map.entry("cap", EquipmentSlot.HEAD), Map.entry("chestplate", EquipmentSlot.CHEST), Map.entry("armor", EquipmentSlot.CHEST),
		Map.entry("armour", EquipmentSlot.CHEST), Map.entry("leggings", EquipmentSlot.LEGS), Map.entry("pants", EquipmentSlot.LEGS),
		Map.entry("boots", EquipmentSlot.FEET), Map.entry("shoes", EquipmentSlot.FEET), Map.entry("shield", EquipmentSlot.OFFHAND));
	private static final List<String> CARRIED = List.of("sword", "pickaxe", "pick", "axe", "shovel", "bow", "crossbow", "trident", "mace", "hoe", "rod",
		"spyglass", "elytra", "weapon", "tool");

	/** A kind word about something it's wearing or carrying: thanks for what it really has, "what hat?" for what it hasn't. */
	private static void gear(DrifterEntity drifter, ServerPlayer player, String text) {
		String said = " " + Lines.normalize(text) + " ";
		ItemStack found = ItemStack.EMPTY;
		String what = null;
		for (Map.Entry<String, EquipmentSlot> entry : WORN.entrySet()) {
			if (said.contains(" " + Lines.normalize(entry.getKey()).trim() + " ")) {
				what = entry.getKey();
				found = drifter.getItemBySlot(entry.getValue());
				break;
			}
		}

		if (what == null) {
			for (String word : CARRIED) {
				if (!said.contains(" " + Lines.normalize(word).trim() + " ")) {
					continue;
				}

				what = word;
				ItemStack held = drifter.getMainHandItem();
				if (word.equals("weapon") || word.equals("tool")) {
					found = held;
				} else {
					String part = word.equals("pick") ? "pickaxe" : word.equals("rod") ? "fishing_rod" : word;
					for (int i = 0; i < drifter.getInventory().getContainerSize() && found.isEmpty(); i++) {
						ItemStack stack = drifter.getInventory().getItem(i);
						String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
						if (!stack.isEmpty() && (path.endsWith("_" + part) || path.equals(part)) && !(part.equals("axe") && path.endsWith("pickaxe"))) {
							found = stack;
						}
					}
				}

				break;
			}
		}

		String name = Heard.callName(drifter, player);
		if (what == null) {
			// (Its skin, its look: no item to it.)
			drifter.getSocial().record(player.getUUID(), name, SocialEvent.COMPLIMENT, 0.0);
			say(drifter, player, "convo.gear.look", null);
		} else if (found.isEmpty()) {
			say(drifter, player, "convo.gear.none", null, "what", what);
		} else {
			drifter.getSocial().record(player.getUUID(), name, SocialEvent.COMPLIMENT, 0.0);
			say(drifter, player, found.isEnchanted() ? "convo.gear.enchanted" : "convo.gear.thanks", null, "what", what, "item",
				found.getHoverName().getString().toLowerCase(Locale.ROOT));
		}
	}

	private static boolean armed(DrifterEntity drifter) {
		return drifter.getInventory().count(stack -> stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem
			|| stack.is(net.minecraft.world.item.Items.TRIDENT) || stack.is(net.minecraft.world.item.Items.MACE)) > 0;
	}

	// ----- its day, its mood, people -----

	private static void today(DrifterEntity drifter, ServerPlayer player) {
		Facts facts = Facts.of(drifter, null);
		List<String> keys = new ArrayList<>();
		for (String word : new String[] {"close_call", "ore", "built", "mob", "traded_with"}) {
			if (facts.words.containsKey(word)) {
				keys.add("convo.today." + word);
			}
		}

		if (PlayerTalk.doing(drifter) != null) {
			keys.add("convo.today.doing");
		}

		java.util.Collections.shuffle(keys, new java.util.Random(drifter.getRandom().nextLong()));
		for (String key : keys) {
			if (say(drifter, player, key, null)) {
				return;
			}
		}

		say(drifter, player, "convo.today.nothing", null);
	}

	private static String moodKey(Mood mood) {
		return switch (mood) {
			case BORED -> "bored";
			case ANXIOUS, PANICKED -> "scared";
			case ANGRY, FRUSTRATED -> "angry";
			case GRIEVING -> "sad";
			case TRIUMPHANT -> "great";
			default -> "fine";
		};
	}

	private static void whoIs(DrifterEntity drifter, ServerPlayer player, String text) {
		Telling.Subject subject = Telling.subject(drifter, player, text);
		if (subject == null || subject.name().equalsIgnoreCase(drifter.getName().getString())) {
			say(drifter, player, "convo.who.unknown", null);
			return;
		}

		String who = subject.name();
		if (subject.drifter() && subject.id() != null) {
			var bond = drifter.getBonds().get(subject.id());
			double trust = bond == null ? 0.0 : bond.trust;
			String key = bond == null ? "convo.who.heard_of" : trust >= com.dinglebob.drifter.social.DrifterBonds.FRIEND ? "convo.who.friend"
				: trust <= -20.0 ? "convo.who.dislike" : "convo.who.known";
			say(drifter, player, key, null, "who", who);
		} else if (subject.record() != null) {
			say(drifter, player, subject.record().trust >= Mode.FRIENDLY_TRUST ? "convo.who.friend" : subject.record().trust < 0.0 ? "convo.who.dislike"
				: "convo.who.known", null, "who", who);
		} else {
			say(drifter, player, "convo.who.unknown", null, "who", who);
		}
	}

	/** Why it's cold to them, or went for them: what it holds against them, said to their face. */
	private static void grudge(DrifterEntity drifter, ServerPlayer player, Relationship r, long now) {
		Relationship.Grudge worst = null;
		for (Relationship.Grudge grudge : r.grudges) {
			if (worst == null || grudge.severity() > worst.severity()) {
				worst = grudge;
			}
		}

		String key;
		if (worst != null) {
			key = switch (worst.event()) {
				case HIT_ONCE, ATTACKED, NEARLY_KILLED, KILLED -> "convo.grudge.hit";
				case OPENED_CHEST, TOOK_VALUABLES, CAUGHT_STEALING, HAD_ITS_THINGS -> "convo.grudge.theft";
				case KILLED_PET, KILLED_FARM_ANIMAL -> "convo.grudge.animals";
				case BROKE_BLOCK, FIRE_NEAR_BASE -> "convo.grudge.grief";
				case KILLED_FRIEND -> "convo.grudge.friend";
				default -> "convo.grudge.other";
			};
		} else if (r.lastInsultAt >= 0 && now - r.lastInsultAt < 24000L * 3 && !r.lastInsult.isEmpty()) {
			key = "convo.grudge.insult";
		} else if (lately(drifter, player, "threat", now, 24000L)) {
			key = "convo.grudge.threat";
		} else {
			key = r.trust >= PlayerTalk.KNOWS_THEM ? "convo.grudge.none_like" : "convo.grudge.none";
		}

		say(drifter, player, key, null, "insult", r.lastInsult);
	}

	// ----- the world -----

	private static void danger(DrifterEntity drifter, ServerPlayer player) {
		Heard.Place told = null;
		for (Heard.Place place : drifter.getHeard().places()) {
			if (place.kind().equals("danger") && (told == null || place.at() > told.at())) {
				told = place;
			}
		}

		if (told != null && say(drifter, player, "convo.danger.told", null, "who", told.teller(), "way",
			PlayerTalk.way(player.position(), Vec3.atCenterOf(told.pos())))) {
			return;
		}

		if (say(drifter, player, "convo.danger.close_call", null)) {
			return;
		}

		boolean night = player.level().getDayTime() % 24000L >= 12500L;
		say(drifter, player, night ? "convo.danger.night" : "convo.danger.none", null);
	}

	private static void lost(DrifterEntity drifter, ServerPlayer player, Relationship r) {
		Home home = drifter.getDrifterBrain().home();
		if (r.knownBase != null) {
			say(drifter, player, "convo.im_lost.base", null, "way", PlayerTalk.way(player.position(), Vec3.atCenterOf(r.knownBase)), "dist",
				PlayerTalk.distance(player.position(), Vec3.atCenterOf(r.knownBase)));
		} else if (home != null && !home.center.closerThan(player.blockPosition(), 24.0)) {
			say(drifter, player, "convo.im_lost.mine", null, "way", PlayerTalk.way(player.position(), Vec3.atCenterOf(home.center)));
		} else {
			say(drifter, player, "convo.im_lost.none", null);
		}
	}

	/** "creeper!", "behind you": it looks, and thanks them if something was there. */
	private static void warning(DrifterEntity drifter, ServerPlayer player, Relationship r, long now) {
		LivingEntity nearest = null;
		for (LivingEntity mob : drifter.level().getEntitiesOfClass(LivingEntity.class, drifter.getBoundingBox().inflate(12.0, 6.0, 12.0),
			one -> one instanceof Enemy && one.isAlive())) {
			if (nearest == null || mob.distanceToSqr(drifter) < nearest.distanceToSqr(drifter)) {
				nearest = mob;
			}
		}

		Convo c = convos.get(player.getUUID());
		Vec3 at = nearest != null ? nearest.getEyePosition() : drifter.getEyePosition().subtract(drifter.getViewVector(1.0F).scale(6.0));
		if (c != null) {
			c.lookAt = at;
			c.lookUntil = now + 20L * 3;
		}

		drifter.getCamera().lookAt(at, CameraModel.TurnSpeed.COMBAT);
		if (nearest != null) {
			if (onceIn(drifter, player, "warned", now, 20L * 60 * 5)) {
				r.addTrust(2.0);
			}

			say(drifter, player, "convo.warning.real", null);
		} else {
			say(drifter, player, "convo.warning.false", null);
		}
	}

	// ----- going out together -----

	private static void outing(DrifterEntity drifter, ServerPlayer player, Relationship r, boolean mining, boolean friend, boolean knows, double bravery) {
		String key;
		boolean night = player.level().getDayTime() % 24000L >= 13000L;
		if (!(friend || knows && r.familiarity >= 3.0)) {
			key = "convo.outing.no_trust";
		} else if (drifter.getHealth() <= drifter.getMaxHealth() * 0.5F) {
			key = "convo.outing.hurt";
		} else if (mining && drifter.getInventory().count(stack -> stack.getItem() instanceof PickaxeItem) == 0) {
			key = "convo.outing.no_pick";
		} else if (!mining && !armed(drifter)) {
			key = "convo.outing.no_weapon";
		} else if (!mining && bravery < 0.3 && night) {
			key = "convo.outing.scared";
		} else {
			say(drifter, player, mining ? "convo.outing.mine_yes" : "convo.outing.hunt_yes", () -> drifter.getSocial().watcher().invite(player, 20 * 30, 20 * 60 * 15));
			moving(player);
			return;
		}

		say(drifter, player, key, null);
	}

	// ----- threats, truces, sorry, just kidding -----

	private static void threat(DrifterEntity drifter, ServerPlayer player, Relationship r, boolean all, double bravery, long now) {
		if (onceIn(drifter, player, "threat", now, 20L * 30)) {
			r.addTrust(all ? -15.0 : -10.0);
			r.addFear((all ? 18.0 : 12.0) * (1.0 - bravery));
			Telling.thought(drifter, now, player.getGameProfile().getName() + " threatened " + (all ? "everyone" : "it"));
			com.dinglebob.drifter.api.TalkApi.threatened(drifter, player, all);
		}

		Archetype kind = drifter.getPersonality().archetype();
		String how = kind == Archetype.PVP_SWEAT || kind == Archetype.BANDIT || bravery >= 0.65 ? "brave" : bravery <= 0.35 ? "scared" : "wary";
		say(drifter, player, (all ? "convo.threat_all." : "convo.threat.") + how, null);
	}

	private static void joking(DrifterEntity drifter, ServerPlayer player, Relationship r, long now) {
		boolean threatened = lately(drifter, player, "threat", now, 20L * 60 * 2);
		boolean insulted = r.lastInsultAt >= 0 && now - r.lastInsultAt < 20L * 60 * 2;
		if (!threatened && !insulted) {
			say(drifter, player, "convo.jk.what", null);
			return;
		}

		// (Mostly undone, the first time or two in a day.)
		if (onceIn(drifter, player, "jk", now, 20L * 60 * 10)) {
			r.addTrust(threatened ? 8.0 : 4.0);
			r.addFear(-8.0);
			r.lastInsult = "";
			say(drifter, player, "convo.jk.ok", null);
			warm(player);
		} else {
			say(drifter, player, "convo.jk.again", null);
		}
	}

	private static void sorry(DrifterEntity drifter, ServerPlayer player, Relationship r, long now) {
		boolean hitOnce = r.grudges.stream().anyMatch(grudge -> grudge.event() == SocialEvent.HIT_ONCE);
		boolean heavy = r.grudges.stream().anyMatch(grudge -> grudge.event() != SocialEvent.HIT_ONCE && grudge.event() != SocialEvent.BROKE_BLOCK
			&& grudge.event() != SocialEvent.OPENED_CHEST && grudge.event() != SocialEvent.SNUCK_UP && grudge.event() != SocialEvent.AIMED_BOW);
		boolean insulted = r.lastInsultAt >= 0 && now - r.lastInsultAt < 24000L && !r.lastInsult.isEmpty();
		boolean threatened = lately(drifter, player, "threat", now, 24000L);
		double forgiving = drifter.getPersonality().get(Trait.FORGIVENESS);
		if (heavy) {
			say(drifter, player, forgiving >= 0.6 ? "convo.sorry.slowly" : "convo.sorry.not_enough", null);
			if (forgiving >= 0.6 && onceIn(drifter, player, "sorry", now, 24000L)) {
				r.addTrust(3.0);
			}
		} else if (hitOnce || insulted || threatened || r.trust < 0.0) {
			if (onceIn(drifter, player, "sorry", now, 20L * 60 * 10)) {
				r.addTrust(3.0 + 5.0 * forgiving);
				r.addFear(-5.0);
				r.lastInsult = "";
				r.grudges.removeIf(grudge -> grudge.event() == SocialEvent.HIT_ONCE);
				say(drifter, player, "convo.sorry.forgiven", null);
				warm(player);
			} else {
				say(drifter, player, "convo.sorry.again", null);
			}
		} else {
			say(drifter, player, "convo.sorry.nothing", null);
		}
	}

	private static void truce(DrifterEntity drifter, ServerPlayer player, Relationship r, boolean hostile, long now) {
		if (!hostile) {
			say(drifter, player, "convo.truce.no_fight", null);
			return;
		}

		Archetype kind = drifter.getPersonality().archetype();
		boolean heavy = r.grudges.stream().anyMatch(grudge -> grudge.forever() || grudge.severity() >= 60.0);
		if (kind == Archetype.BANDIT || heavy || drifter.getPersonality().get(Trait.FORGIVENESS) < 0.25) {
			say(drifter, player, "convo.truce.no", null);
			return;
		}

		if (onceIn(drifter, player, "truce", now, 24000L)) {
			r.addTrust(10.0);
			r.addFear(-10.0);
		}

		say(drifter, player, "convo.truce.maybe", null);
	}

	// ---------------------------------------------------------------------------------------------
	// Saying it
	// ---------------------------------------------------------------------------------------------

	/** Its words for a line: who's who, and what it knows of itself and its day ({@link Facts}; an addon's too). */
	static Object[] vars(DrifterEntity drifter, ServerPlayer player, Object... more) {
		List<Object> vars = new ArrayList<>(List.of("player", Heard.callName(drifter, player), "name", drifter.getName().getString()));
		for (int i = 0; i + 1 < more.length; i += 2) {
			vars.add(more[i]);
			vars.add(more[i + 1]);
		}

		Map<String, Object> words = new HashMap<>(Facts.of(drifter, null).words);
		// (News it has for them: something it heard about someone else.)
		Facts.player(drifter, player).words.forEach((key, value) -> {
			if (key.startsWith("news_")) {
				words.putIfAbsent(key, value);
			}
		});
		com.dinglebob.drifter.api.TalkApi.convoFacts(drifter, player, words);
		String doing = PlayerTalk.doing(drifter);
		if (doing != null) {
			words.putIfAbsent("doing", doing);
		}

		words.forEach((key, value) -> {
			if (!vars.contains(key) && value != null && !String.valueOf(value).isBlank()) {
				vars.add(key);
				vars.add(value);
			}
		});
		return vars.toArray();
	}

	/** It says a line of this kind (after a moment's typing), then does {@code then}; false if it has no such line. */
	static boolean say(DrifterEntity drifter, ServerPlayer player, String key, @Nullable Runnable then, Object... more) {
		return PlayerTalk.reply(drifter, key, then, vars(drifter, player, more));
	}

	/** For an addon's own answers in a talk: a line of this kind, said as the Drifter's answer (false: no such line). */
	public static boolean answer(DrifterEntity drifter, ServerPlayer player, String key, @Nullable Runnable then, Object... more) {
		long now = drifter.level().getGameTime();
		touch(player, drifter, now, true);
		PlayerTalk.focusOn(player, drifter, now);
		return say(drifter, player, key, then, more);
	}

	/** For an addon: it's off doing what they asked (leading them somewhere), not standing and waiting. */
	public static void nowMoving(ServerPlayer player) {
		moving(player);
	}

	/** For an addon: it asks them this, and takes their yes or no ({@code does}: what a yes does, as the talk topics'). */
	public static void ask(DrifterEntity drifter, ServerPlayer player, String does, String noKey) {
		long now = drifter.level().getGameTime();
		PlayerTalk.ask(drifter, player, new Topics.Question(Map.of(), does, Lines.voices(noKey)), vars(drifter, player), now, does);
	}

	/** For an addon: {@link #tour}, as its answer to them. */
	public static void showRound(DrifterEntity drifter, ServerPlayer player) {
		long now = drifter.level().getGameTime();
		touch(player, drifter, now, true);
		PlayerTalk.focusOn(player, drifter, now);
		tour(drifter, player);
	}

	/** Shows them round: the places an addon knows of (a town's), one after another. */
	static void tour(DrifterEntity drifter, ServerPlayer player) {
		List<GuideGoal.Stop> stops = new ArrayList<>();
		com.dinglebob.drifter.api.TalkApi.places(drifter).forEach((what, pos) -> stops.add(new GuideGoal.Stop(pos, "convo.tour." + what, 5.0)));
		if (stops.isEmpty()) {
			say(drifter, player, "convo.tour.nothing", null);
			return;
		}

		// (The nearest first, then each the nearest to the last.)
		List<GuideGoal.Stop> ordered = new ArrayList<>();
		BlockPos from = drifter.blockPosition();
		while (!stops.isEmpty() && ordered.size() < 7) {
			BlockPos at = from;
			GuideGoal.Stop next = stops.stream().min(java.util.Comparator.comparingDouble(stop -> stop.pos().distSqr(at))).get();
			stops.remove(next);
			ordered.add(next);
			from = next.pos();
		}

		say(drifter, player, "convo.tour.start", () -> GuideGoal.lead(drifter, player, ordered, "the town"));
		moving(player);
	}
}
