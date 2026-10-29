package com.dinglebob.drifter.social.talk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
import com.dinglebob.drifter.social.DrifterBonds;
import com.dinglebob.drifter.social.Lines;
import com.dinglebob.drifter.social.News;
import com.dinglebob.drifter.social.Relationship;

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
 *
 * <p>A conversation can go on from topic to topic (the web; user, 2026-10-28: "a drifter and another have a very long
 * coversation"): when a topic's done, they go on to one it leads to ({@link Topics.Web}), or now and then change the
 * subject, for as long as their interest lasts: strangers a topic or two, friends a few, best friends up to ten. Past the
 * first topic it's a long talk: they go back to what they were doing and keep chatting as they work, as players do, and
 * a long talk ends with a goodbye. Cut short (a fight, one walking off), they pick it up the next time they meet
 * ("anyway, about the iron").
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
	/** How far apart two in a long talk (chatting as they work) can get before it's cut short. */
	private static final double WORKING_ENDS = 48.0;
	/** How long a talk cut short waits to be picked up again (ticks), and how soon the two can pick it up. */
	private static final long RESUME_WITHIN = 24000L;
	private static final long RESUME_GAP = 20 * 30;
	/** The most topics in one conversation, and the longest it goes on (ticks). */
	private static final int MOST_TOPICS = 10;
	private static final long MOST_TICKS = 20 * 60 * 5;
	/** Interest it takes to go on to another topic. */
	private static final double MIN_INTEREST = 0.5;
	/** How often they change the subject rather than go on to a linked topic. */
	private static final double CHANGE_SUBJECT = 0.15;
	/** How often a linked topic is brought in with "speaking of which", and a change of subject with "anyway". */
	private static final double SEGUE_LINKED = 0.45;
	private static final double SEGUE_CHANGE = 0.85;
	/** How much more a topic a topic names in its "next" is picked than one it only shares a tag with. */
	private static final double NEXT_WEIGHT = 3.0;
	/**
	 * How often a topic goes straight on to one it names in its "next" (a story's part two), when one suits them. A
	 * follow-up that names a next (the middle of a story in three parts) always goes on.
	 */
	private static final double NEXT_FIRST = 0.85;
	/** Trust that makes two best friends (the longest talks). */
	private static final double BEST_FRIEND = 80.0;
	/**
	 * How often a talk opens on something real (what one of them did or has, a mood, rain or a storm)
	 * when there is one, over the everyday topics (best ore, fav food...), which mostly come up as a talk wanders on: with
	 * hundreds of everyday topics, weight alone would bury the news.
	 */
	private static final double GROUNDED_SHARE = 0.7;
	/** Weather worth talking about (time of day and a clear sky aren't news). */
	private static final Set<String> NEWS_WEATHER = Set.of("rain", "thunder");

	/** A conversation going on. */
	public static final class Talk {
		/** Who brought up the topic going now ({@code a}) and the other: they swap as the talk goes on. */
		public DrifterEntity a;
		public DrifterEntity b;
		public Topics.Topic topic;
		Facts aFacts;
		Facts bFacts;
		Set<String> world;
		int turn;
		long nextAt;
		/** The answers the last line wants, and who said it (the other answers with these next). */
		@Nullable
		List<String> answers;
		@Nullable
		DrifterEntity asker;
		/** The topics so far (this one last), what the talk's been marked ("disagree"), and what this topic's lines lead on to. */
		final List<String> done = new ArrayList<>();
		final Set<String> marks = new HashSet<>();
		final Set<String> leads = new HashSet<>();
		/** Interest left (each topic uses some), when it started, lines said. */
		double interest;
		final long startedAt;
		int lines;
		/** Said before the topic's first line ("speaking of which"), if anything. */
		@Nullable
		String segue;
		/** Saying goodbye: who said it (the other answers, then it's over). */
		@Nullable
		DrifterEntity leaving;
		boolean closed;

		Talk(DrifterEntity a, DrifterEntity b, Topics.Topic topic, long now) {
			this.a = a;
			this.b = b;
			this.topic = topic;
			this.aFacts = facts(a, b);
			this.bFacts = facts(b, a);
			this.world = world(a, b, this.marks);
			this.nextAt = now + 10 + a.getRandom().nextInt(20);
			this.startedAt = now;
			this.done.add(topic.id());
			this.interest = Math.min(interest(a, b), interest(b, a)) + a.getRandom().nextDouble();
		}

		public DrifterEntity partnerOf(DrifterEntity drifter) {
			return drifter == this.a ? this.b : this.a;
		}

		/** Past its first topic: they go back to what they were doing and chat as they work. */
		public boolean longTalk() {
			return this.done.size() > 1;
		}

		/** The topics talked about so far, in order. */
		public List<String> topics() {
			return this.done;
		}

		public double interestLeft() {
			return this.interest;
		}

		/** Debug: this much more interest (a long talk to watch). */
		public void addInterest(double more) {
			this.interest += more;
		}
	}

	/** What they go on to: a topic, whether the other brings it up, and what's said first ("speaking of which"). */
	private record Choice(Topics.Topic topic, boolean swap, @Nullable String segue) {
	}

	/** A talk cut short, to pick up when the two next meet: the topic it was on, and when. */
	private record Pending(String topic, long at) {
	}

	private static final List<Talk> talks = new ArrayList<>();
	private static final Map<UUID, Long> lastTalk = new HashMap<>();
	private static final Map<String, Long> lastPair = new HashMap<>();
	private static final Map<String, String> lastTopic = new HashMap<>();
	private static final Map<String, Pending> pending = new HashMap<>();

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
				cut(talk, now);
				continue;
			}

			// (One busy with something types as it works, as players do; one that's free walks over and faces the other.)
			if (now >= talk.nextAt && !carryOn(talk, now)) {
				it.remove();
				end(talk, now);
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
			if (now - lastTalk.getOrDefault(a.getUUID(), Long.MIN_VALUE / 2) < gap(a) && near.stream().noneMatch(b -> b != a && resumable(a, b, now))) {
				continue;
			}

			for (DrifterEntity b : near) {
				// (A talk cut short is picked up sooner.)
				boolean resume = resumable(a, b, now);
				if (b == a || a.distanceTo(b) > START || !resume && now - lastTalk.getOrDefault(b.getUUID(), Long.MIN_VALUE / 2) < gap(b)
					|| now - lastPair.getOrDefault(pair(a, b), Long.MIN_VALUE / 2) < (resume ? RESUME_GAP : DrifterConfig.chattyGap(PAIR_GAP)) || !a.hasLineOfSight(b)) {
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

		// A talk they were having, cut short: they pick it up where it was ("anyway, about the iron").
		Pending was = topicId == null ? pending.remove(pair(a, b)) : null;
		if (was != null && now - was.at() <= RESUME_WITHIN && Topics.byId(was.topic()) != null) {
			Talk talk = new Talk(a, b, Topics.byId(was.topic()), now);
			Choice next = linked(talk);
			if (next == null) {
				next = anyTopic(talk);
			}

			if (next != null) {
				String about = talk.topic.web().about();
				begin(talk, next.topic(), next.swap(), now);
				talk.segue = Lines.pick(talk.a, "talk.resume", "about", about, "a", talk.a.getName().getString(), "b", talk.b.getName().getString());
				return started(talk, now);
			}
		}

		Facts aFacts = facts(a, b);
		Facts bFacts = facts(b, a);
		Set<String> world = Facts.world(a, b);
		Topics.Topic topic = topicId != null ? Topics.byId(topicId) : pick(a, b, aFacts, bFacts, world);
		if (topic == null) {
			return null;
		}

		return started(new Talk(a, b, topic, now), now);
	}

	/** It's on: noted, and both turn to it. */
	private static Talk started(Talk talk, long now) {
		DrifterEntity a = talk.a;
		DrifterEntity b = talk.b;
		Topics.Topic topic = talk.topic;
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

	/**
	 * Its facts, talking with the other: what it knows, what players told it that it could pass on, and its kind of player
	 * ({@code voice_miner}: a miner has topics of its own).
	 */
	private static Facts facts(DrifterEntity me, DrifterEntity other) {
		Facts facts = Facts.of(me, other);
		Heard.addDrifterFacts(me, other, facts);
		facts.flags.add("voice_" + me.getPersonality().archetype().name().toLowerCase(java.util.Locale.ROOT));
		return facts;
	}

	/** The world as they see it, and what the talk's been marked ({@code t:disagree}). */
	private static Set<String> world(DrifterEntity a, DrifterEntity b, Set<String> marks) {
		Set<String> world = Facts.world(a, b);
		marks.forEach(mark -> world.add("t:" + mark));
		return world;
	}

	/**
	 * How much it'd talk with the other (topics' worth): the chattier, the more; strangers a topic or two, friends a few,
	 * best friends many; bored, more; angry or frightened, less.
	 */
	private static double interest(DrifterEntity me, DrifterEntity other) {
		double interest = 0.5 + 1.5 * sociability(me);
		Relationship bond = me.getBonds().get(other.getUUID());
		// (Trusted as a friend, it knows them, however it came about.)
		if (bond != null && (bond.familiarity >= 5.0 || bond.trust >= DrifterBonds.FRIEND)) {
			interest += bond.trust >= BEST_FRIEND ? 6.0 : bond.trust >= DrifterBonds.FRIEND ? 2.5 : bond.trust <= DrifterBonds.RIVAL ? 0.5 : 1.0;
		}

		Mood mood = me.getPersonality().mood().current();
		if (mood == Mood.BORED) {
			interest += 1.0;
		} else if (mood == Mood.ANGRY || mood == Mood.FRUSTRATED || mood == Mood.ANXIOUS) {
			interest -= 0.5;
		}

		return interest;
	}

	/**
	 * The next thing said: the topic's next turn; at its end, what it did, then on to another while their interest lasts
	 * (a long talk ending in a goodbye). False when it's over.
	 */
	private static boolean carryOn(Talk talk, long now) {
		if (talk.leaving != null) {
			// (The goodbye's answered, and that's it.)
			DrifterEntity other = talk.partnerOf(talk.leaving);
			talk.leaving = null;
			talk.closed = true;
			String back = Lines.pick(other, "talk.bye_back", "a", other.getName().getString(), "b", talk.partnerOf(other).getName().getString());
			if (back != null) {
				say(talk, other, back, now);
				return true;
			}

			return false;
		}

		if (talk.closed) {
			return false;
		}

		if (step(talk, now)) {
			return true;
		}

		// The topic's done.
		effects(talk, now);
		talk.interest -= talk.topic.web().cost();
		double nextFirst = talk.topic.web().followUp() ? 1.0 : NEXT_FIRST;
		for (int tries = 0; tries < 3 && talk.interest >= MIN_INTEREST && talk.done.size() < MOST_TOPICS && now - talk.startedAt < MOST_TICKS; tries++) {
			Choice next = tries == 0 && talk.a.getRandom().nextDouble() < nextFirst ? continued(talk) : null;
			if (next == null && talk.a.getRandom().nextDouble() >= CHANGE_SUBJECT) {
				next = linked(talk);
			}

			if (next == null) {
				next = anyTopic(talk);
			}

			if (next == null) {
				break;
			}

			begin(talk, next.topic(), next.swap(), now);
			talk.segue = next.segue() == null ? null : Lines.pick(talk.a, next.segue(), "about", next.topic().web().about());
			if (step(talk, now)) {
				talk.a.getMemory().thoughts().add(now, "brought up " + next.topic().id().replace('_', ' ') + " with " + talk.b.getName().getString());
				return true;
			}
		}

		// A long talk ends with a goodbye (from whoever's had enough).
		if (talk.longTalk() && talk.lines > 2) {
			DrifterEntity leaving = talk.a.getRandom().nextBoolean() ? talk.a : talk.b;
			String bye = Lines.pick(leaving, "talk.bye", "a", leaving.getName().getString(), "b", talk.partnerOf(leaving).getName().getString());
			if (bye != null) {
				talk.leaving = leaving;
				say(talk, leaving, bye, now);
				return true;
			}
		}

		return false;
	}

	/** On to this topic (brought up by the other, if swapped): its turns from the top, their facts as they are now. */
	private static void begin(Talk talk, Topics.Topic topic, boolean swap, long now) {
		if (swap) {
			DrifterEntity a = talk.a;
			talk.a = talk.b;
			talk.b = a;
		}

		talk.topic = topic;
		talk.turn = 0;
		talk.answers = null;
		talk.asker = null;
		talk.leads.clear();
		talk.aFacts = facts(talk.a, talk.b);
		talk.bFacts = facts(talk.b, talk.a);
		talk.world = world(talk.a, talk.b, talk.marks);
		talk.done.add(topic.id());
	}

	/**
	 * Straight on to a topic this one names in its "next" (a story's part two), by the same one, with no "speaking of
	 * which" (the other brings it up only if it isn't a follow-up and only the other can). Null if none suits them.
	 */
	@Nullable
	private static Choice continued(Talk talk) {
		List<String> next = new ArrayList<>(talk.topic.web().next());
		java.util.Collections.shuffle(next, new java.util.Random(talk.a.getRandom().nextLong()));
		Facts ab = facts(talk.a, talk.b);
		Facts ba = facts(talk.b, talk.a);
		Set<String> world = world(talk.a, talk.b, talk.marks);
		for (String id : next) {
			Topics.Topic topic = Topics.byId(id);
			if (topic == null || talk.done.contains(id) || topic.web().opener()) {
				continue;
			}

			if (Topics.holds(topic.needs(), ab, ba, world)) {
				return new Choice(topic, false, null);
			}

			if (!topic.web().followUp() && Topics.holds(topic.needs(), ba, ab, world)) {
				return new Choice(topic, true, null);
			}
		}

		return null;
	}

	/**
	 * A topic the one going now leads on to (its "next" most of all, then any with a tag it leads to, or one its lines led
	 * to), that suits them, not had yet this talk; either of them can bring it up. Null if none.
	 */
	@Nullable
	private static Choice linked(Talk talk) {
		Topics.Web web = talk.topic.web();
		Map<Topics.Topic, Double> links = new LinkedHashMap<>();
		for (String id : web.next()) {
			Topics.Topic topic = Topics.byId(id);
			if (topic != null) {
				links.merge(topic, NEXT_WEIGHT, Double::sum);
			}
		}

		Set<String> leads = new HashSet<>(web.leads());
		leads.addAll(talk.leads);
		for (String tag : leads) {
			for (Topics.Topic topic : Topics.tagged(tag)) {
				links.merge(topic, 1.0, Double::sum);
			}
		}

		return choose(talk, links, "talk.segue.linked", SEGUE_LINKED);
	}

	/** Any topic that suits them (a change of subject: "anyway"), not one that only follows another. Null if none. */
	@Nullable
	private static Choice anyTopic(Talk talk) {
		Map<Topics.Topic, Double> any = new LinkedHashMap<>();
		for (Topics.Topic topic : Topics.all()) {
			if (!topic.web().followUp()) {
				any.put(topic, 1.0);
			}
		}

		return choose(talk, any, "talk.segue.change", SEGUE_CHANGE);
	}

	/** One of these (weighted by the topic's weight and how strongly it's linked) that suits either of them to bring up. */
	@Nullable
	private static Choice choose(Talk talk, Map<Topics.Topic, Double> candidates, String segue, double segueChance) {
		Facts ab = facts(talk.a, talk.b);
		Facts ba = facts(talk.b, talk.a);
		Set<String> world = world(talk.a, talk.b, talk.marks);
		List<Choice> fitting = new ArrayList<>();
		List<Double> weights = new ArrayList<>();
		double total = 0.0;
		for (Map.Entry<Topics.Topic, Double> candidate : candidates.entrySet()) {
			Topics.Topic topic = candidate.getKey();
			if (talk.done.contains(topic.id()) || topic.web().opener()) {
				continue;
			}

			// (Either can bring it up; the other a little more often, so it goes back and forth.)
			boolean byA = Topics.holds(topic.needs(), ab, ba, world);
			boolean byB = Topics.holds(topic.needs(), ba, ab, world);
			if (!byA && !byB) {
				continue;
			}

			boolean swap = byB && (!byA || talk.a.getRandom().nextDouble() < 0.6);
			String said = talk.a.getRandom().nextDouble() < segueChance ? segue : null;
			fitting.add(new Choice(topic, swap, said));
			double weight = topic.weight() * candidate.getValue();
			weights.add(weight);
			total += weight;
		}

		double roll = talk.a.getRandom().nextDouble() * total;
		for (int i = 0; i < fitting.size(); i++) {
			roll -= weights.get(i);
			if (roll <= 0.0) {
				return fitting.get(i);
			}
		}

		return fitting.isEmpty() ? null : fitting.get(fitting.size() - 1);
	}

	/** Cut short (a fight, one walking off): it might say so, and they'll pick it up when they next meet. */
	private static void cut(Talk talk, long now) {
		if (talk.lines == 0 || talk.closed || talk.leaving != null) {
			return;
		}

		if (talk.interest >= MIN_INTEREST) {
			pending.put(pair(talk.a, talk.b), new Pending(talk.topic.id(), now));
		}

		// (The one in trouble says so; in a long talk, the one walking off might.)
		DrifterEntity troubled = trouble(talk.a) ? talk.a : trouble(talk.b) ? talk.b : null;
		if (troubled != null && troubled.isAlive()) {
			String line = Lines.pick(troubled, "talk.cut.trouble", "a", troubled.getName().getString(), "b", talk.partnerOf(troubled).getName().getString());
			if (line != null) {
				ChatLines.converse(troubled, line);
			}
		} else if (talk.longTalk() && talk.a.isAlive() && talk.b.isAlive() && talk.a.getRandom().nextBoolean()) {
			String line = Lines.pick(talk.a, "talk.cut.away", "a", talk.a.getName().getString(), "b", talk.b.getName().getString());
			if (line != null) {
				ChatLines.converse(talk.a, line);
			}
		}
	}

	/** These two have a talk cut short lately, to pick up. */
	private static boolean resumable(DrifterEntity a, DrifterEntity b, long now) {
		Pending was = pending.get(pair(a, b));
		return was != null && now - was.at() <= RESUME_WITHIN;
	}

	/** Needs that make a topic pressing: talked about before anything else while they hold. */
	private static final Set<String> PRESSING = Set.of("a:at_war", "a:town_war", "a:town_fell", "a:revolt", "a:after_betrayed", "a:after_coup");

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


	/**
	 * A topic that suits these two now (weighted; not the one they talked about last; not one that only follows another;
	 * something real first, mostly: {@link #GROUNDED_SHARE}).
	 */
	@Nullable
	private static Topics.Topic pick(DrifterEntity a, DrifterEntity b, Facts aFacts, Facts bFacts, Set<String> world) {
		List<Topics.Topic> grounded = new ArrayList<>();
		List<Topics.Topic> everyday = new ArrayList<>();
		String last = lastTopic.get(pair(a, b));
		for (Topics.Topic topic : Topics.all()) {
			if (!topic.id().equals(last) && !topic.web().followUp() && Topics.holds(topic.needs(), aFacts, bFacts, world)) {
				(grounded(topic) ? grounded : everyday).add(topic);
			}
		}

		List<Topics.Topic> fitting = new ArrayList<>(grounded);
		fitting.addAll(everyday);
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

		boolean real = !grounded.isEmpty() && (everyday.isEmpty() || a.getRandom().nextDouble() < GROUNDED_SHARE);
		return weighted(a, real ? grounded : everyday);
	}

	/**
	 * It needs something real to talk about: something about one of them (not what kind of player it is), or rain or a storm
	 * (not who the two are to each other, nor the time of day).
	 */
	private static boolean grounded(Topics.Topic topic) {
		for (String need : topic.needs()) {
			if (NEWS_WEATHER.contains(need) || (need.startsWith("a:") || need.startsWith("b:")) && !need.contains(":voice_")) {
				return true;
			}
		}

		return false;
	}

	@Nullable
	private static Topics.Topic weighted(DrifterEntity a, List<Topics.Topic> fitting) {
		double total = 0.0;
		for (Topics.Topic topic : fitting) {
			total += topic.weight();
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
			// (What this line means for the talk: "disagree"; what it leads on to.)
			talk.marks.addAll(option.marks());
			talk.leads.addAll(option.leads());
			if (!option.marks().isEmpty()) {
				talk.world = world(talk.a, talk.b, talk.marks);
			}

			String line = Lines.filled(speaker, template, vars(talk));
			if (talk.segue != null) {
				line = talk.segue + " " + line;
				talk.segue = null;
			}

			say(talk, speaker, line, now);
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
		talk.lines++;
		// Reading it and typing the answer: longer lines take longer (as they work, longer still).
		talk.nextAt = now + Math.min(140, 30 + line.length() * 2) + speaker.getRandom().nextInt(20) + (talk.longTalk() ? 20 + speaker.getRandom().nextInt(40) : 0);
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

	/** Done talking: someone standing by may add something (about the last topic). */
	private static void end(Talk talk, long now) {
		PlayerChats.chime(talk.a, talk.b, talk.topic.chime(), now, vars(talk));
	}

	/** A topic done: what it did (news passed on, closer or further apart: a disagreement, further). */
	private static void effects(Talk talk, long now) {
		// (Both cleared: a topic's marks are its own, so the next topic's "if"s start fresh.)
		boolean disagreed = talk.marks.remove("disagree");
		boolean agreed = talk.marks.remove("agree");
		if (disagreed) {
			com.dinglebob.drifter.api.DrifterApi.addTrustInDrifter(talk.a, talk.b, -1.5);
			com.dinglebob.drifter.api.DrifterApi.addTrustInDrifter(talk.b, talk.a, -1.5);
		} else if (agreed) {
			com.dinglebob.drifter.api.DrifterApi.addTrustInDrifter(talk.a, talk.b, 1.0);
			com.dinglebob.drifter.api.DrifterApi.addTrustInDrifter(talk.b, talk.a, 1.0);
		}

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
				// What a player told it, passed on (a place; a word about someone).
				case "pass_place" -> Heard.passPlace(talk.a, talk.b, now);
				case "pass_word" -> Heard.passWord(talk.a, talk.b, now);
				default -> {
				}
			}
		}
	}

	/** Still going: both alive, near each other (in a long talk, within earshot as they work), not fighting or in trouble. */
	private static boolean going(Talk talk) {
		return talk.a.isAlive() && talk.b.isAlive() && talk.a.level() == talk.b.level() && talk.a.distanceTo(talk.b) <= (talk.longTalk() ? WORKING_ENDS : ENDS)
			&& !trouble(talk.a) && !trouble(talk.b);
	}

	/** The conversations going on now (for the debug command). */
	public static List<Talk> all() {
		return talks;
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
