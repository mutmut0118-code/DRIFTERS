package com.dinglebob.drifter.social.talk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Mood;
import com.dinglebob.drifter.personality.Trait;
import com.dinglebob.drifter.social.ChatLines;
import com.dinglebob.drifter.social.Lines;
import com.dinglebob.drifter.social.News;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Drifters talking with each other (the dynamic chat, phase 1; user, 2026-10-23: "lets greatly expand small talk
 * conversations between drifters"): two of them near each other with nothing pressing, a player near enough to overhear,
 * now and then strike up a conversation on a topic one of them has something to say about (what it found, how it died,
 * the night's mobs, the town, a player, the news...). They face each other and take turns, a moment between each as it's
 * typed and read; what one says shapes the other's answer ({@link Topics}). Only players near enough see it (the chat
 * range). Talking builds familiarity; friends grow closer, rivals needle each other; news passes on.
 */
public final class Conversations {
	private Conversations() {
	}

	/** How close two must be to strike up a conversation, and how far apart before it ends. */
	private static final double START = 6.0;
	private static final double ENDS = 16.0;
	/** How often it looks for two to start talking (ticks). */
	private static final int LOOK_EVERY = 40;
	/** Least time between one Drifter's conversations, for the chattiest and the quietest (ticks). */
	private static final long CHATTY_GAP = 20 * 60;
	private static final long QUIET_GAP = 20 * 60 * 6;
	/** Least time before the same two talk again (ticks). */
	private static final long PAIR_GAP = 20 * 60 * 4;

	/** A conversation going on. */
	public static final class Talk {
		public final DrifterEntity a;
		public final DrifterEntity b;
		public final Topics.Topic topic;
		final Facts aFacts;
		final Facts bFacts;
		final Set<String> world;
		int turn;
		long nextAt;
		/** The answers the last line wants, and who said it (the other answers with these next). */
		@Nullable
		List<String> answers;
		@Nullable
		DrifterEntity asker;

		Talk(DrifterEntity a, DrifterEntity b, Topics.Topic topic, long now) {
			this.a = a;
			this.b = b;
			this.topic = topic;
			this.aFacts = Facts.of(a, b);
			this.bFacts = Facts.of(b, a);
			this.world = Facts.world(a, b);
			this.nextAt = now + 10 + a.getRandom().nextInt(20);
		}

		public DrifterEntity partnerOf(DrifterEntity drifter) {
			return drifter == this.a ? this.b : this.a;
		}
	}

	private static final List<Talk> talks = new ArrayList<>();
	private static final Map<UUID, Long> lastTalk = new HashMap<>();
	private static final Map<String, Long> lastPair = new HashMap<>();
	private static final Map<String, String> lastTopic = new HashMap<>();

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Conversations::tick);
	}

	/** The conversation it's in, or null. */
	@Nullable
	public static Talk of(DrifterEntity drifter) {
		for (Talk talk : talks) {
			if (talk.a == drifter || talk.b == drifter) {
				return talk;
			}
		}

		return null;
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (Iterator<Talk> it = talks.iterator(); it.hasNext();) {
			Talk talk = it.next();
			if (!going(talk)) {
				it.remove();
				continue;
			}

			// (One busy with something types as it works, as players do; one that's free walks over and faces the other.)
			if (now >= talk.nextAt && !step(talk, now)) {
				end(talk, now);
				it.remove();
			}
		}

		if (now % LOOK_EVERY == 0 && DrifterConfig.chatLines()) {
			for (ServerLevel level : server.getAllLevels()) {
				for (ServerPlayer player : level.players()) {
					lookForTalk(level, player, now);
				}
			}
		}
	}

	/** Two near this player who'd talk, if any, start (one conversation within earshot of a player at a time). */
	private static void lookForTalk(ServerLevel level, ServerPlayer player, long now) {
		double range = DrifterConfig.chatRange();
		if (talks.stream().anyMatch(talk -> talk.a.level() == level && talk.a.distanceTo(player) <= range)) {
			return;
		}

		List<DrifterEntity> near = level.getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(range), Conversations::free);
		for (DrifterEntity a : near) {
			if (now - lastTalk.getOrDefault(a.getUUID(), Long.MIN_VALUE / 2) < gap(a)) {
				continue;
			}

			for (DrifterEntity b : near) {
				if (b == a || a.distanceTo(b) > START || now - lastTalk.getOrDefault(b.getUUID(), Long.MIN_VALUE / 2) < gap(b)
					|| now - lastPair.getOrDefault(pair(a, b), Long.MIN_VALUE / 2) < DrifterConfig.chattyGap(PAIR_GAP) || !a.hasLineOfSight(b)) {
					continue;
				}

				double chance = DrifterConfig.chatty(0.12 * (sociability(a) + sociability(b)));
				if (a.getRandom().nextDouble() < chance && start(a, b, null, now) != null) {
					return;
				}
			}
		}
	}

	/** Start a conversation between these two on this topic (or one that suits them); the talk, or null if none suits. */
	@Nullable
	public static Talk start(DrifterEntity a, DrifterEntity b, @Nullable String topicId, long now) {
		// (One conversation at a time each; none in the middle of a fight, nor on the way to one: out on a raid.)
		if (of(a) != null || of(b) != null || a.inAFight() || b.inAFight() || onARaid(a) || onARaid(b)) {
			return null;
		}

		Facts aFacts = Facts.of(a, b);
		Facts bFacts = Facts.of(b, a);
		Set<String> world = Facts.world(a, b);
		Topics.Topic topic = topicId != null ? Topics.byId(topicId) : pick(a, b, aFacts, bFacts, world);
		if (topic == null) {
			return null;
		}

		Talk talk = new Talk(a, b, topic, now);
		talks.add(talk);
		lastTalk.put(a.getUUID(), now);
		lastTalk.put(b.getUUID(), now);
		lastPair.put(pair(a, b), now);
		lastTopic.put(pair(a, b), topic.id());
		a.getMemory().thoughts().add(now, "started talking with " + b.getName().getString() + " (" + topic.id().replace('_', ' ') + ")");
		a.getDrifterBrain().poke();
		b.getDrifterBrain().poke();
		return talk;
	}

	/** Needs that make a topic pressing: talked about before anything else while they hold. */
	private static final Set<String> PRESSING = Set.of("a:at_war", "a:town_war", "a:town_fell", "a:revolt", "a:after_betrayed", "a:after_coup",
		"a:nation_rebelled");

	/** Out on a raid with its group now (gathering, on the march, at it, or coming back). */
	private static boolean onARaid(DrifterEntity drifter) {
		var group = com.dinglebob.drifter.world.DrifterGroups.of(drifter.getUUID());
		return group != null && com.dinglebob.drifter.world.DrifterWars.raidOf(group.id) != null
			&& drifter.getDrifterBrain().currentGoal() != null && drifter.getDrifterBrain().currentGoal().id().equals("raid");
	}

	/** Its group is out raiding, or being raided, now. */
	private static boolean raiding(DrifterEntity drifter) {
		var group = com.dinglebob.drifter.world.DrifterGroups.of(drifter.getUUID());
		return group != null && (com.dinglebob.drifter.world.DrifterWars.raidOf(group.id) != null || com.dinglebob.drifter.world.DrifterWars.raidOn(group.id) != null);
	}

	/** A topic that suits these two now (weighted; not the one they talked about last). */
	@Nullable
	private static Topics.Topic pick(DrifterEntity a, DrifterEntity b, Facts aFacts, Facts bFacts, Set<String> world) {
		List<Topics.Topic> fitting = new ArrayList<>();
		double total = 0.0;
		String last = lastTopic.get(pair(a, b));
		for (Topics.Topic topic : Topics.all()) {
			if (!topic.id().equals(last) && Topics.holds(topic.needs(), aFacts, bFacts, world)) {
				fitting.add(topic);
				total += topic.weight();
			}
		}

		// What's pressing (a war, someone fallen, a revolt) crowds out small talk (the user, 2026-10-29: "people were chatting
		// normally like they werent fighting in a war").
		// While a raid is on (theirs, or on them), nothing else at all.
		List<Topics.Topic> pressing = fitting.stream().filter(topic -> topic.needs().stream().anyMatch(PRESSING::contains)).toList();
		if (raiding(a) || raiding(b)) {
			return pressing.isEmpty() ? null : pressing.get(a.getRandom().nextInt(pressing.size()));
		}

		if (!pressing.isEmpty() && a.getRandom().nextDouble() < 0.75) {
			return pressing.get(a.getRandom().nextInt(pressing.size()));
		}

		double roll = a.getRandom().nextDouble() * total;
		for (Topics.Topic topic : fitting) {
			roll -= topic.weight();
			if (roll <= 0.0) {
				return topic;
			}
		}

		return fitting.isEmpty() ? null : fitting.get(fitting.size() - 1);
	}

	/** The next turn said (turns that don't come up skipped); false when there are no more. */
	private static boolean step(Talk talk, long now) {
		while (talk.turn < talk.topic.turns().size()) {
			Topics.Turn turn = talk.topic.turns().get(talk.turn++);
			DrifterEntity speaker = turn.by().equals("b") ? talk.b : talk.a;
			// A question's own answers, by the other one, whatever this turn would have said.
			if (talk.answers != null && speaker != talk.asker) {
				List<String> answers = talk.answers;
				talk.answers = null;
				String answer = Lines.pickFrom(speaker, Map.of("any", answers), vars(talk));
				if (answer != null) {
					say(talk, speaker, answer, now);
					return true;
				}
			}

			if (talk.a.getRandom().nextDouble() > turn.chance() || !Topics.holds(turn.conditions(), talk.aFacts, talk.bFacts, talk.world)) {
				continue;
			}

			Topics.Option option = null;
			for (Topics.Option candidate : turn.options()) {
				if (Topics.holds(candidate.conditions(), talk.aFacts, talk.bFacts, talk.world)) {
					option = candidate;
					break;
				}
			}

			if (option == null) {
				continue;
			}

			String template = Lines.template(speaker, option.lines(), vars(talk));
			if (template == null) {
				continue;
			}

			List<String> answers = option.then().get(template);
			talk.answers = answers;
			talk.asker = answers != null ? speaker : null;
			say(talk, speaker, Lines.filled(speaker, template, vars(talk)), now);
			return true;
		}

		// (A last question still gets its answer.)
		if (talk.answers != null) {
			DrifterEntity other = talk.partnerOf(talk.asker);
			String answer = Lines.pickFrom(other, Map.of("any", talk.answers), vars(talk));
			talk.answers = null;
			if (answer != null) {
				say(talk, other, answer, now);
				return true;
			}
		}

		return false;
	}

	private static void say(Talk talk, DrifterEntity speaker, String line, long now) {
		ChatLines.converse(speaker, line);
		// Reading it and typing the answer: longer lines take longer.
		talk.nextAt = now + Math.min(140, 30 + line.length() * 2) + speaker.getRandom().nextInt(20);
	}

	/** Its words for the lines: each one's facts as a.* and b.*, and their names as {a} and {b}. */
	private static Object[] vars(Talk talk) {
		List<Object> vars = new ArrayList<>();
		vars.add("a");
		vars.add(talk.a.getName().getString());
		vars.add("b");
		vars.add(talk.b.getName().getString());
		talk.aFacts.words.forEach((key, value) -> {
			vars.add("a." + key);
			vars.add(value);
		});
		talk.bFacts.words.forEach((key, value) -> {
			vars.add("b." + key);
			vars.add(value);
		});
		return vars.toArray();
	}

	/** Free to talk (nothing pressing, not talking already): for a talk of its own with a player too (phase 3). */
	public static boolean idle(DrifterEntity drifter) {
		return free(drifter);
	}

	/** Done talking: what it did (news passed on, closer or further apart); someone standing by may add something. */
	private static void end(Talk talk, long now) {
		PlayerChats.chime(talk.a, talk.b, talk.topic.chime(), now, vars(talk));
		for (String effect : talk.topic.effects()) {
			switch (effect) {
				case "news" -> {
					for (News news : talk.a.getNews().all()) {
						if (!news.stale(now) && !talk.b.getNews().knows(news.key()) && String.valueOf(talk.aFacts.words.get("news_who")).equals(news.aboutName())) {
							talk.b.getNews().hear(news, talk.a);
							break;
						}
					}
				}
				case "trust" -> {
					com.dinglebob.drifter.api.DrifterApi.addTrustInDrifter(talk.a, talk.b, 2.0);
					com.dinglebob.drifter.api.DrifterApi.addTrustInDrifter(talk.b, talk.a, 2.0);
				}
				case "rivalry" -> {
					com.dinglebob.drifter.api.DrifterApi.addTrustInDrifter(talk.a, talk.b, -2.0);
					com.dinglebob.drifter.api.DrifterApi.addTrustInDrifter(talk.b, talk.a, -2.0);
				}
				default -> {
				}
			}
		}
	}

	/** Still going: both alive, near each other, not fighting or in trouble. */
	private static boolean going(Talk talk) {
		return talk.a.isAlive() && talk.b.isAlive() && talk.a.level() == talk.b.level() && talk.a.distanceTo(talk.b) <= ENDS && !trouble(talk.a)
			&& !trouble(talk.b);
	}

	/** Free to talk: nothing pressing (not fighting, fleeing, hurt, in an encounter, asleep). */
	private static boolean free(DrifterEntity drifter) {
		return drifter.isAlive() && !drifter.isTemporary() && drifter.encounter() == null && !trouble(drifter) && !drifter.isSleeping() && of(drifter) == null
			&& drifter.getHealth() > drifter.getMaxHealth() * 0.5F && drifter.onGround() && !onDuty(drifter);
	}

	/** On the town's business (collecting a fine, keeping the stall, a trade run): no small talk (found in testing: a guard said "hey there" to the thief it was fining). */
	private static boolean onDuty(DrifterEntity drifter) {
		var goal = drifter.getDrifterBrain().currentGoal();
		return goal != null && (goal.id().equals("guard") || goal.id().equals("stall") || goal.id().equals("trade_run"));
	}

	private static boolean trouble(DrifterEntity drifter) {
		Mood mood = drifter.getPersonality().mood().current();
		return drifter.inCombat() || mood == Mood.PANICKED || drifter.isInLava() || drifter.isOnFire();
	}

	private static double sociability(DrifterEntity drifter) {
		return drifter.getPersonality().get(Trait.SOCIABILITY);
	}

	/** Least time between its conversations: the chattier, the sooner. */
	private static long gap(DrifterEntity drifter) {
		return DrifterConfig.chattyGap((long)(QUIET_GAP - (QUIET_GAP - CHATTY_GAP) * sociability(drifter)));
	}

	private static String pair(DrifterEntity a, DrifterEntity b) {
		return a.getUUID().compareTo(b.getUUID()) < 0 ? a.getUUID() + ":" + b.getUUID() : b.getUUID() + ":" + a.getUUID();
	}
}
