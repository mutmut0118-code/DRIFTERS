package com.dinglebob.drifter.social.talk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.Strategist;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.perception.Awareness;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.social.DrifterBonds;
import com.dinglebob.drifter.social.Lines;
import com.dinglebob.drifter.social.Mode;
import com.dinglebob.drifter.social.Relationship;
import com.dinglebob.drifter.social.SocialWatcher;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Telling a Drifter things, and asking it more (user, 2026-10-28: "remember what I say"). What a player tells it goes in
 * its {@link Heard}: what to call them ("call me Dingle"), where their base is ("my base is at 120 64 -300", "i live
 * here", "my house is up north"), places ("theres a village 300 blocks east", "found dias at 10 -58 40"), what they're
 * off to do ("going mining"), what they promise ("ill bring you 5 iron"), what they like and hate ("i love cats"), their
 * news ("i died", "found diamonds", "im hungry"), and what they think of someone else ("bob is a griefer"). It answers
 * in its voice and brings it up later; places it passes on to other Drifters.
 *
 * <p>And more to ask it: who am i, what do you think of me (or of someone), where's someone, where are you going, what
 * do you need, who are your friends, what did i tell you, where's my base, forget that, what do you like. Phrases and
 * lines in {@code drifter_chat/heard.json}; topics it brings up in {@code drifter_talk/heard.json}.
 */
public final class Telling {
	private Telling() {
	}

	/** The new questions (asked as {@link PlayerTalk}'s are). */
	static final String[] ASKS = {"me", "opinion", "where_is", "going", "need", "friends", "told", "my_base", "forget", "likes"};
	/** What they think of someone else (said to it, about another). */
	static final String[] WORDS = {"word_good", "word_bad"};
	/** Said about someone else: the one named is who it's about, not who it's said to. */
	private static final Set<String> ABOUT_OTHERS = Set.of("opinion", "where_is", "word_good", "word_bad");
	private static final String[] PLACES = {"place_village", "place_diamonds", "place_iron", "place_gold", "place_coal", "place_dungeon", "place_portal",
		"place_fortress", "place_bastion", "place_cave", "place_mineshaft", "place_ravine", "place_temple", "place_shipwreck", "place_monument",
		"place_stronghold", "place_ancient_city", "place_outpost", "place_mansion", "place_trial_chambers", "place_lava", "place_end_city", "place_mountain",
		"place_ocean", "place_desert", "place_jungle", "place_swamp", "place_cherry", "place_mushroom", "place_snow", "place_sheep", "place_cows",
		"place_horses", "place_bees", "place_sugar_cane", "place_pumpkins"};
	private static final String[] PLANS = {"plan_mining", "plan_caving", "plan_nether", "plan_end", "plan_exploring", "plan_building", "plan_fishing",
		"plan_farming", "plan_hunting", "plan_home", "plan_trading", "plan_raid", "plan_stronghold", "plan_chopping", "plan_enchanting"};
	private static final String[] NEWS = {"news_died", "news_diamonds", "news_hungry", "news_built", "news_nether", "news_end", "news_dragon", "news_back",
		"news_tired", "news_lost"};

	/** Places said about this close count as "here". */
	private static final int UNKNOWN_DISTANCE = 150;
	/** Drifters this close read a place said in chat, whoever it was said to (chat is public). */
	private static final double OVERHEARD = 32.0;
	/** Trust in them from which it believes what they say about someone else. */
	private static final double BELIEVES = 10.0;
	/** Liked or disliked this much, it won't take another's word against it. */
	private static final double SURE_OF = 30.0;
	private static final int MOST_PROMISES = 5;

	/** This is said about someone else ("wheres bob", "bob is a griefer"). */
	static boolean aboutOthers(@Nullable String intent) {
		return intent != null && ABOUT_OTHERS.contains(intent);
	}

	// ---------------------------------------------------------------------------------------------
	// Being told things
	// ---------------------------------------------------------------------------------------------

	/**
	 * Something a player said in chat, near Drifters: a place ("theres a village east") is read by every Drifter close
	 * enough (chat is public), whoever it was said to; said and taken in by none of them, nothing.
	 */
	static void overheard(ServerPlayer player, String text, long now) {
		if (question(text)) {
			return;
		}

		Heard.Place place = place(player, text, now);
		if (place == null) {
			return;
		}

		for (DrifterEntity drifter : player.serverLevel().getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(OVERHEARD),
			drifter -> drifter.isAlive() && !drifter.isTemporary())) {
			drifter.getHeard().learn(place);
		}
	}

	/** It's told something ("call me X", "my base is at ...", "going mining"...): it remembers and answers; true if so. */
	static boolean told(DrifterEntity drifter, ServerPlayer player, String text, @Nullable String intent, long now) {
		if (question(text)) {
			return false;
		}

		Mode mode = Mode.toward(drifter, player);
		if (mode.hostile() || mode == Mode.WARNING) {
			return false;
		}

		String realName = player.getGameProfile().getName();
		Relationship r = drifter.getSocial().getOrMeet(player.getUUID(), realName);
		Heard heard = drifter.getHeard();
		Heard.Notes n = heard.of(player);

		// What to call them.
		String nick = nick(text);
		if (nick != null) {
			n.nick = nick;
			n.last = "nick";
			PlayerTalk.reply(drifter, "told.nick", null, "player", nick, "nick", nick);
			thought(drifter, now, realName + " goes by " + nick);
			return true;
		}

		String name = Heard.callName(drifter, player);

		// What they'll bring it.
		if (Lines.answer(text, "promise") != null) {
			String item = itemIn(text);
			Integer count = PlayerTalk.number(text);
			Heard.Promise promise = new Heard.Promise(item == null ? "" : item, count == null ? 1 : count, now);
			if (n.promises.size() >= MOST_PROMISES) {
				n.promises.remove(0);
			}

			n.promises.add(promise);
			n.last = "promise";
			PlayerTalk.reply(drifter, item == null ? "told.promise_any" : "told.promise", null, "player", name, "item", promise.words());
			thought(drifter, now, realName + " promised me " + promise.words());
			return true;
		}

		// Where they live.
		if (Lines.answer(text, "tell_base") != null) {
			Where where = location(player, text);
			if (where != null) {
				n.base = where.pos();
				n.baseDim = player.level().dimension().location().toString();
				n.baseExact = where.exact();
				n.baseAt = now;
				n.last = "base";
				BlockPos at = where.pos();
				String key = where.here() ? "told.base_here" : where.exact() ? "told.base" : "told.base_way";
				PlayerTalk.reply(drifter, key, null, "player", name, "coords", at.getX() + " " + at.getY() + " " + at.getZ(), "way", where.way() == null ? "" : where.way());
				thought(drifter, now, realName + " lives " + (where.exact() ? "at " + at.toShortString() : "about " + where.way()));
				return true;
			}
		}

		// A place they found.
		Heard.Place place = place(player, text, now);
		if (place != null) {
			boolean seen = seenItself(drifter, place);
			// (Everyone near has read it in chat by now: told of it before, by someone else.)
			boolean knew = heard.knows(place, place.teller());
			heard.learn(place);
			n.last = "place";
			String what = Heard.word(drifter, "place.word." + place.kind(), place.kind().replace('_', ' '));
			PlayerTalk.reply(drifter, seen ? "told.place_known" : knew ? "told.place_heard" : "told.place", null, "player", name, "what", what, "where",
				Heard.where(drifter, place, player.position()));
			thought(drifter, now, realName + " says there's " + what + " " + Heard.where(drifter, place, drifter.position()));
			return true;
		}

		// What they're off to do (with a goodbye, the goodbyes answer it: it remembers all the same).
		String plan = Lines.answer(text, PLANS);
		if (plan != null) {
			n.plan = plan.substring("plan_".length());
			n.planAt = now;
			n.planAsked = false;
			n.last = "plan";
			if (Lines.answer(text, "bye", "night") != null) {
				return false;
			}

			String kind = n.plan;
			String key = Lines.has("told.plan." + kind) && drifter.getRandom().nextDouble() < 0.6 ? "told.plan." + kind : "told.plan";
			PlayerTalk.reply(drifter, key, null, "player", name, "plan", Heard.word(drifter, "plan.word." + kind, kind), "plan_go",
				Heard.word(drifter, "plan.go." + kind, kind));
			thought(drifter, now, realName + " is off " + Heard.word(drifter, "plan.go." + kind, kind));
			return true;
		}

		// What they like and hate.
		String feel = Lines.answer(text, "tell_like", "tell_hate");
		if (feel != null) {
			String thing = thing(text);
			if (thing != null) {
				boolean likes = feel.equals("tell_like");
				heard.like(n, thing, likes);
				n.last = likes ? "like" : "hate";
				PlayerTalk.reply(drifter, likes ? "told.like" : "told.hate", null, "player", name, "thing", thing);
				thought(drifter, now, realName + (likes ? " likes " : " hates ") + thing);
				return true;
			}
		}

		// What they think of someone else.
		if (intent != null && (intent.equals("word_good") || intent.equals("word_bad"))) {
			Subject subject = subject(drifter, player, text);
			if (subject != null) {
				word(drifter, player, r, n, subject, intent.equals("word_good"), now);
				return true;
			}
		}

		// Their news.
		String news = Lines.answer(text, NEWS);
		if (news != null) {
			String kind = news.substring("news_".length());
			n.news = kind;
			n.newsAt = now;
			n.newsAsked = false;
			n.last = "news";
			if (kind.equals("hungry")) {
				hungry(drifter, player, r, name, now);
			} else {
				BlockPos at = drifter.blockPosition();
				PlayerTalk.reply(drifter, "told.news." + kind, null, "player", name, "coords", at.getX() + " " + at.getY() + " " + at.getZ());
			}

			thought(drifter, now, realName + " told me: " + kind);
			return true;
		}

		return false;
	}

	/** "im hungry": one that likes them and can spare food offers some. */
	private static void hungry(DrifterEntity drifter, ServerPlayer player, Relationship r, String name, long now) {
		Item food = PlayerTalk.spareFood(drifter);
		if (food == null || r.trust < PlayerTalk.KNOWS_THEM) {
			PlayerTalk.reply(drifter, "told.news.hungry", null, "player", name);
			return;
		}

		String foodName = new ItemStack(food).getHoverName().getString().toLowerCase(Locale.ROOT);
		Object[] vars = {"player", name, "food", foodName};
		PlayerTalk.reply(drifter, "told.news.hungry_offer", null, vars);
		PlayerTalk.ask(drifter, player, new Topics.Question(Lines.voices("told.hungry_yes"), "give_food", Lines.voices("told.hungry_no")), vars, now);
	}

	/**
	 * "bob is a griefer": if it trusts them enough (and isn't sure of bob itself), it thinks a little the less of bob (once
	 * a day for each, from each teller), and may pass it on.
	 */
	private static void word(DrifterEntity drifter, ServerPlayer player, Relationship r, Heard.Notes n, Subject subject, boolean good, long now) {
		String name = Heard.callName(drifter, player);
		Object[] vars = {"player", name, "who", subject.name()};
		double mine = subject.record() == null ? 0.0 : subject.record().trust;
		// It knows better.
		if (good ? mine <= -SURE_OF : mine >= SURE_OF) {
			PlayerTalk.reply(drifter, good ? "told.word_good_disagree" : "told.word_bad_disagree", null, vars);
			return;
		}

		if (r.trust < BELIEVES) {
			PlayerTalk.reply(drifter, "told.word_doubt", null, vars);
			return;
		}

		long day = now / 24000L;
		if (subject.record() != null && n.wordDay.getOrDefault(subject.name(), -1L) != day) {
			n.wordDay.put(subject.name(), day);
			double change = good ? Math.min(4.0, 1.0 + r.trust / 25.0) : -Math.min(6.0, 2.0 + r.trust / 20.0);
			subject.record().addTrust(change);
		}

		drifter.getHeard().learn(new Heard.Word(subject.name(), subject.id(), good, player.getGameProfile().getName(), now));
		n.last = "word";
		PlayerTalk.reply(drifter, good ? "told.word_good" : "told.word_bad", null, vars);
		thought(drifter, now, player.getGameProfile().getName() + " says " + subject.name() + (good ? " is good" : " is trouble"));
	}

	// ---------------------------------------------------------------------------------------------
	// The new questions
	// ---------------------------------------------------------------------------------------------

	/** One of {@link #ASKS} (it isn't hostile to them): it answers; false if it isn't one it can (the asker meant another). */
	static boolean asked(DrifterEntity drifter, ServerPlayer player, String intent, String text, Relationship r, boolean knows, long now) {
		String name = Heard.callName(drifter, player);
		Heard.Notes n = drifter.getHeard().get(player.getUUID());
		boolean helper = drifter.getPersonality().archetype() == Archetype.HELPER;
		switch (intent) {
			case "me" -> {
				boolean known = r.familiarity >= 3.0 || n != null && !n.empty();
				// (Not "you go by Dingle": it just said so.)
				String memory = known ? memory(drifter, player, n, r, false) : null;
				PlayerTalk.reply(drifter, known ? "ask.me_known" : "ask.me_stranger", null, "player", name, "nick", name, "memory", memory);
			}
			case "opinion" -> {
				Subject subject = subject(drifter, player, text);
				String said = Lines.normalize(text);
				String thing = subject == null && !said.contains(" me ") && !said.contains(" we ") && !said.contains(" us ") ? thing(text) : null;
				if (thing != null) {
					// ("do you like cats": cats, not them.)
					PlayerTalk.reply(drifter, "ask.opinion_thing", null, "player", name, "thing", thing);
				} else if (subject == null) {
					PlayerTalk.reply(drifter, "ask.opinion_me_" + bucket(r.trust), null, "player", name);
				} else if (subject.record() == null) {
					PlayerTalk.reply(drifter, "ask.opinion_unknown", null, "player", name, "who", subject.name());
				} else {
					String key = subject.drifter() ? "ask.opinion_drifter_" + bucket(subject.record().trust) : "ask.opinion_" + bucket(subject.record().trust);
					PlayerTalk.reply(drifter, Lines.has(key) ? key : "ask.opinion_" + bucket(subject.record().trust), null, "player", name, "who", subject.name());
				}
			}
			case "where_is" -> {
				Subject subject = subject(drifter, player, text);
				// ("wheres bobby", to Bobby.)
				if (subject == null && SocialWatcher.called(" " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", " ") + " ", drifter.getName().getString())) {
					PlayerTalk.reply(drifter, "ask.where_me", null, "player", name);
					return true;
				}

				if (subject == null) {
					return false;
				}

				whereIs(drifter, player, subject, name, knows || helper, now);
			}
			case "going" -> {
				var goal = drifter.getDrifterBrain().currentGoal();
				String doing = PlayerTalk.doing(drifter);
				String key = goal != null && Lines.has("going." + goal.id()) ? "going." + goal.id() : doing == null ? "ask.going_idle" : "ask.going";
				PlayerTalk.reply(drifter, key, null, "player", name, "doing", doing == null ? "" : doing);
			}
			case "need" -> need(drifter, name);
			case "friends" -> friends(drifter, player, name);
			case "told" -> {
				String memory = n == null ? null : memory(drifter, player, n, r, true);
				PlayerTalk.reply(drifter, memory == null ? "ask.told_none" : "ask.told", null, "player", name, "memory", memory);
			}
			case "my_base" -> {
				if (n != null && n.base != null && n.baseDim.equals(player.level().dimension().location().toString())) {
					Heard.Place base = new Heard.Place("base", n.base, n.baseDim, n.baseExact, "", null, n.baseAt, 0);
					PlayerTalk.reply(drifter, "ask.my_base_told", null, "player", name, "where", Heard.where(drifter, base, player.position()));
				} else if (r.knownBase != null) {
					Heard.Place base = new Heard.Place("base", r.knownBase, "", true, "", null, now, 0);
					PlayerTalk.reply(drifter, "ask.my_base_seen", null, "player", name, "where", Heard.where(drifter, base, player.position()));
				} else {
					PlayerTalk.reply(drifter, "ask.my_base_none", null, "player", name);
				}
			}
			case "forget" -> {
				String said = Lines.normalize(text);
				boolean all = said.contains(" everything ") || said.contains(" all ") || said.contains(" forget me ") || said.contains(" all of it ");
				boolean had = drifter.getHeard().forget(player, all);
				// (Forgotten: it calls them by their name again.)
				PlayerTalk.reply(drifter, !had ? "ask.forget_nothing" : all ? "ask.forget_all" : "ask.forget", null, "player", Heard.callName(drifter, player));
			}
			case "likes" -> PlayerTalk.reply(drifter, "ask.likes", null, "player", name);
			default -> {
				return false;
			}
		}

		return true;
	}

	/** "Where's bob?": in sight, or where and when it last saw them; told as far as it knows the asker. */
	private static void whereIs(DrifterEntity drifter, ServerPlayer player, Subject subject, String name, boolean tells, long now) {
		Object[] vars = {"player", name, "who", subject.name()};
		if (subject.entity() != null) {
			Awareness awareness = drifter.getSenses().awarenessOf(subject.entity());
			if (awareness != null && awareness.visibleNow && drifter.distanceTo(subject.entity()) <= 24.0) {
				PlayerTalk.reply(drifter, "ask.where_here", null, vars);
				return;
			}
		}

		Relationship record = subject.record();
		if (record == null || record.lastSeenAt < 0 || record.lastSeenPos == null) {
			PlayerTalk.reply(drifter, "ask.where_unknown", null, vars);
			return;
		}

		if (!tells) {
			PlayerTalk.reply(drifter, "ask.where_no", null, vars);
			return;
		}

		long ago = now - record.lastSeenAt;
		String when = ago < 20 * 60 * 2 ? "when.just" : ago < 20 * 60 * 15 ? "when.minutes" : ago < 24000L ? "when.today" : ago < 48000L ? "when.yesterday" : "when.days";
		Heard.Place at = new Heard.Place("someone", record.lastSeenPos, "", false, "", null, record.lastSeenAt, 0);
		PlayerTalk.reply(drifter, "ask.where_seen", null, "player", name, "who", subject.name(), "where", Heard.where(drifter, at, player.position()), "when",
			Heard.word(drifter, when, "a while ago"));
	}

	/** "What do you need?": what it's short of, from its own state (hurt, hungry, no home, the next thing it's after). */
	private static void need(DrifterEntity drifter, String name) {
		if (drifter.getHealth() <= drifter.getMaxHealth() * 0.5F) {
			PlayerTalk.reply(drifter, "ask.need_heal", null, "player", name);
			return;
		}

		boolean hasFood = drifter.getInventory().count(stack -> stack.has(net.minecraft.core.component.DataComponents.FOOD)) > 0;
		if (drifter.getFoodData().getFoodLevel() <= 12 || !hasFood) {
			PlayerTalk.reply(drifter, "ask.need_food", null, "player", name);
			return;
		}

		Home home = drifter.getDrifterBrain().home();
		if (home == null && drifter.getPersonality().archetype() != Archetype.NOMAD) {
			PlayerTalk.reply(drifter, "ask.need_home", null, "player", name);
			return;
		}

		Strategist.Target target = Strategist.nextTarget(drifter);
		if (target != null) {
			PlayerTalk.reply(drifter, "ask.need_target", null, "player", name, "what", target.label());
			return;
		}

		PlayerTalk.reply(drifter, "ask.need_nothing", null, "player", name);
	}

	/** "Who are your friends?": the players and Drifters it likes best (the asker as "you"). */
	private static void friends(DrifterEntity drifter, ServerPlayer player, String name) {
		record Friend(String name, double trust) {
		}

		List<Friend> friends = new ArrayList<>();
		for (Relationship r : drifter.getSocial().all()) {
			if (r.trust >= Mode.FRIENDLY_TRUST) {
				friends.add(new Friend(r.player.equals(player.getUUID()) ? "you" : r.name, r.trust));
			}
		}

		for (Relationship r : drifter.getBonds().all()) {
			if (r.trust >= DrifterBonds.FRIEND) {
				friends.add(new Friend(r.name, r.trust));
			}
		}

		friends.sort(Comparator.comparingDouble(Friend::trust).reversed());
		List<String> names = friends.stream().limit(3).map(Friend::name).toList();
		if (names.isEmpty()) {
			PlayerTalk.reply(drifter, "ask.friends_none", null, "player", name);
			return;
		}

		String list = names.size() == 1 ? names.get(0) : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
		PlayerTalk.reply(drifter, names.contains("you") && names.size() == 1 ? "ask.friends_you" : "ask.friends", null, "player", name, "friends", list);
	}

	/** Something it remembers about them, in words ("you live at 120 64 -300", "you owe me 3 iron"), or null. */
	@Nullable
	private static String memory(DrifterEntity drifter, ServerPlayer player, @Nullable Heard.Notes n, Relationship r, boolean withNick) {
		List<String> memories = new ArrayList<>();
		if (n != null) {
			if (!n.nick.isEmpty() && withNick) {
				add(memories, drifter, "memory.nick", "nick", n.nick);
			}

			if (n.base != null) {
				Heard.Place base = new Heard.Place("base", n.base, n.baseDim, n.baseExact, "", null, n.baseAt, 0);
				add(memories, drifter, "memory.base", "where", Heard.where(drifter, base, player.position()));
			}

			if (!n.plan.isEmpty()) {
				add(memories, drifter, "memory.plan", "plan_go", Heard.word(drifter, "plan.go." + n.plan, n.plan));
			}

			if (!n.likes.isEmpty()) {
				add(memories, drifter, "memory.like", "thing", n.likes.get(0));
			}

			if (!n.hates.isEmpty()) {
				add(memories, drifter, "memory.hate", "thing", n.hates.get(0));
			}

			if (!n.promises.isEmpty()) {
				add(memories, drifter, "memory.promise", "item", n.promises.get(0).words());
			}

			if (!n.news.isEmpty()) {
				add(memories, drifter, "memory.news." + n.news);
			}
		}

		if (!r.debts.isEmpty()) {
			add(memories, drifter, "memory.gift", "item", r.debts.get(r.debts.size() - 1).what().toLowerCase(Locale.ROOT));
		}

		if (!r.lastInsult.isEmpty()) {
			add(memories, drifter, "memory.insult", "insult", r.lastInsult);
		}

		return memories.isEmpty() ? null : memories.get(drifter.getRandom().nextInt(memories.size()));
	}

	private static void add(List<String> memories, DrifterEntity drifter, String key, Object... vars) {
		String line = Lines.pick(drifter, key, vars);
		if (line != null) {
			memories.add(line);
		}
	}

	/** How it feels about someone, in a word for the lines: love, like, ok, meh, dislike, hate. */
	private static String bucket(double trust) {
		return trust >= 60.0 ? "love" : trust >= 25.0 ? "like" : trust >= 5.0 ? "ok" : trust > -15.0 ? "meh" : trust > -40.0 ? "dislike" : "hate";
	}

	// ---------------------------------------------------------------------------------------------
	// Reading what they said
	// ---------------------------------------------------------------------------------------------

	/** Asked, not told: a question mark, or a question word first ("where", "is", "do"...). */
	static boolean question(String text) {
		if (text.contains("?")) {
			return true;
		}

		String said = Lines.understood(text).trim();
		String first = said.isEmpty() ? "" : said.split(" ")[0];
		return switch (first) {
			case "where", "wheres", "what", "whats", "who", "whos", "how", "hows", "do", "does", "did", "is", "are", "can", "could", "would", "will", "have",
				"has", "any", "why", "when", "should", "r", "wanna", "want" -> true;
			default -> false;
		};
	}

	private static final Pattern NICK = Pattern.compile("(?i)\\b(?:call me|my name is|my names|my name's|my name’s|i go by|im called|i'm called|i’m called)\\s+([a-z0-9_]{2,16})\\b");
	/** Words after "call me" that aren't a name. */
	private static final Set<String> NOT_A_NAME = Set.of("not", "later", "back", "when", "if", "maybe", "tomorrow", "pls", "please", "bro", "dude", "now",
		"sometime", "whatever", "what", "anything", "something", "a", "an", "the", "that", "this", "it", "so", "just", "too", "in", "on", "man", "lol",
		"crazy", "out", "up", "over", "any", "you", "u", "and", "or", "by", "whenever", "here", "there");

	/** The name they asked to be called ("call me Dingle", "my name is Dingle"), as typed, or null. */
	@Nullable
	static String nick(String text) {
		Matcher matcher = NICK.matcher(text);
		if (!matcher.find()) {
			return null;
		}

		String nick = matcher.group(1);
		return NOT_A_NAME.contains(nick.toLowerCase(Locale.ROOT)) ? null : nick;
	}

	/**
	 * Where they said: exactly (coords, "here") or roughly (a direction and a distance from where they stand); {@code vague}
	 * when it's "here" or "over there" (words said about anything: a place needs more to go on).
	 */
	record Where(BlockPos pos, boolean exact, boolean here, @Nullable String way, boolean vague) {
	}

	private static final Pattern COORDS = Pattern.compile("(?<![\\d.])(-?\\d{1,8})\\s*[, ]\\s*(-?\\d{1,8})(?:\\s*[, ]\\s*(-?\\d{1,8}))?(?![\\d.])");
	private static final Pattern DISTANCE = Pattern.compile("\\b(\\d{1,5})\\s*(k|blocks?|b|m)\\b");
	private static final String[][] WAYS = {{"north east", "north-east"}, {"northeast", "north-east"}, {"north west", "north-west"}, {"northwest", "north-west"},
		{"south east", "south-east"}, {"southeast", "south-east"}, {"south west", "south-west"}, {"southwest", "south-west"}, {"north", "north"},
		{"south", "south"}, {"east", "east"}, {"west", "west"}};

	/** Where the message says, from the player: coords, "here", a direction ("up north", "300 blocks east", "that way"); or null. */
	@Nullable
	static Where location(ServerPlayer player, String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		Matcher coords = COORDS.matcher(lower);
		while (coords.find()) {
			// (Not "1v1", nor a distance: "300 blocks".)
			int end = coords.end();
			String after = lower.substring(end).trim();
			if (after.startsWith("v") || after.startsWith("block") || after.startsWith("b ") || after.equals("b") || after.startsWith("k ") || after.equals("k")) {
				continue;
			}

			int x = Integer.parseInt(coords.group(1));
			if (coords.group(3) != null) {
				int y = Integer.parseInt(coords.group(2));
				int z = Integer.parseInt(coords.group(3));
				if (y >= -64 && y <= 320) {
					return new Where(new BlockPos(x, y, z), true, false, null, false);
				}
			} else {
				int z = Integer.parseInt(coords.group(2));
				return new Where(new BlockPos(x, player.getBlockY(), z), true, false, null, false);
			}
		}

		String said = Lines.normalize(text);
		Vec3 from = player.position();
		int distance = UNKNOWN_DISTANCE;
		Matcher far = DISTANCE.matcher(lower);
		if (far.find()) {
			distance = Integer.parseInt(far.group(1)) * (far.group(2).equals("k") ? 1000 : 1);
		}

		for (String[] way : WAYS) {
			if (said.contains(" " + way[0] + " ")) {
				Vec3 dir = switch (way[1]) {
					case "north" -> new Vec3(0, 0, -1);
					case "south" -> new Vec3(0, 0, 1);
					case "east" -> new Vec3(1, 0, 0);
					case "west" -> new Vec3(-1, 0, 0);
					case "north-east" -> new Vec3(1, 0, -1).normalize();
					case "north-west" -> new Vec3(-1, 0, -1).normalize();
					case "south-east" -> new Vec3(1, 0, 1).normalize();
					default -> new Vec3(-1, 0, 1).normalize();
				};
				return new Where(BlockPos.containing(from.add(dir.scale(distance))), false, false, way[1], false);
			}
		}

		// Pointing: "that way", "over there" (where they're looking).
		if (said.contains(" that way ") || said.contains(" this way ") || said.contains(" over there ") || said.contains(" out there ")) {
			Vec3 look = player.getViewVector(1.0F);
			Vec3 dir = new Vec3(look.x, 0, look.z);
			if (dir.lengthSqr() < 1.0E-4) {
				return null;
			}

			BlockPos at = BlockPos.containing(from.add(dir.normalize().scale(distance)));
			return new Where(at, false, false, PlayerTalk.way(from, Vec3.atCenterOf(at)), true);
		}

		if (said.contains(" here ") || said.contains(" this is ") || said.contains(" welcome to ")) {
			return new Where(player.blockPosition(), true, true, null, true);
		}

		return null;
	}

	/** A place the message tells of ("theres a village up north"), with where; or null. */
	@Nullable
	static Heard.Place place(ServerPlayer player, String text, long now) {
		String kind = Lines.answer(text, PLACES);
		if (kind == null) {
			return null;
		}

		// (Their own base is their base, not a village: "my village is at ...".)
		if (Lines.answer(text, "tell_base") != null) {
			return null;
		}

		Where where = location(player, text);
		// ("i need iron over here" isn't news of iron: "theres iron over here" is.)
		if (where == null || where.vague() && Lines.answer(text, "place_cue") == null) {
			return null;
		}

		return new Heard.Place(kind.substring("place_".length()), where.pos(), player.level().dimension().location().toString(), where.exact(),
			player.getGameProfile().getName(), player.getUUID(), now, 0);
	}

	/** It has seen one of these near there itself (its memory: only what it saw). */
	private static boolean seenItself(DrifterEntity drifter, Heard.Place place) {
		Predicate<MemoryEntry> is;
		MemoryKind kind;
		switch (place.kind()) {
			case "village" -> {
				kind = MemoryKind.LANDMARK;
				is = entry -> entry.label.equals("village");
			}
			case "fortress" -> {
				kind = MemoryKind.LANDMARK;
				is = entry -> entry.label.equals("fortress");
			}
			case "diamonds", "iron", "gold", "coal" -> {
				kind = MemoryKind.ORE;
				String ore = place.kind().equals("diamonds") ? "diamond_ore" : place.kind() + "_ore";
				is = entry -> entry.label.contains(ore);
			}
			case "dungeon" -> {
				kind = MemoryKind.STRUCTURE;
				is = entry -> entry.label.contains("spawner");
			}
			case "portal" -> {
				kind = MemoryKind.STRUCTURE;
				is = entry -> entry.label.contains("nether_portal");
			}
			default -> {
				return false;
			}
		}

		MemoryEntry entry = drifter.getMemory().nearest(kind, drifter.level().dimension().location(), Vec3.atCenterOf(place.pos()), 64.0, is);
		return entry != null;
	}

	/** Someone else named in the message, that it knows of (a player or a Drifter); not the one saying it, nor itself. */
	record Subject(String name, @Nullable UUID id, @Nullable Relationship record, boolean drifter, @Nullable net.minecraft.world.entity.LivingEntity entity) {
	}

	@Nullable
	static Subject subject(DrifterEntity me, ServerPlayer speaker, String text) {
		String words = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", " ") + " ";
		String own = me.getName().getString();
		String theirs = speaker.getGameProfile().getName();
		Subject best = null;
		for (Relationship r : me.getSocial().all()) {
			if (!r.name.equals(theirs) && SocialWatcher.called(words, r.name) && (best == null || r.name.length() > best.name().length())) {
				best = new Subject(r.name, r.player, r, false, me.level().getPlayerByUUID(r.player));
			}
		}

		for (Relationship r : me.getBonds().all()) {
			if (!r.name.equals(own) && SocialWatcher.called(words, r.name) && (best == null || r.name.length() > best.name().length())) {
				var entity = me.level() instanceof net.minecraft.server.level.ServerLevel level ? level.getEntity(r.player) : null;
				best = new Subject(r.name, r.player, r, true, entity instanceof DrifterEntity other ? other : null);
			}
		}

		if (best != null) {
			return best;
		}

		// One it can see but hasn't got to know yet.
		for (DrifterEntity other : me.level().getEntitiesOfClass(DrifterEntity.class, me.getBoundingBox().inflate(32.0), other -> other != me)) {
			if (SocialWatcher.called(words, other.getName().getString())) {
				return new Subject(other.getName().getString(), other.getUUID(), null, true, other);
			}
		}

		return null;
	}

	/** Words the message has for an item: its id (the longest name found), or "food", "wood"; null if none. */
	@Nullable
	static String itemIn(String text) {
		String said = Lines.normalize(text);
		String best = null;
		int bestLength = 0;
		for (Map.Entry<String, String> name : itemNames().entrySet()) {
			String key = name.getKey();
			if (key.length() > bestLength && (said.contains(" " + key + " ") || said.contains(" " + key + "s ") || said.contains(" " + key + "es "))) {
				best = name.getValue();
				bestLength = key.length();
			}
		}

		return best;
	}

	/**
	 * Something they like or hate: from the things players talk about ({@code things} in the chat data: "building", "the
	 * nether"...), an item or a creature; as they said it ("cats"), or null.
	 */
	@Nullable
	static String thing(String text) {
		String said = Lines.normalize(text);
		String best = Lines.phrase(text, "things");
		int bestLength = best == null ? 0 : best.length();
		List<String> names = new ArrayList<>(itemNames().keySet());
		names.addAll(creatureNames());
		for (String key : names) {
			if (key.length() <= bestLength || ALIASES.containsKey(key)) {
				continue;
			}

			for (String form : new String[] {key + "es", key + "s", key}) {
				if (said.contains(" " + form + " ")) {
					best = form;
					bestLength = key.length();
					break;
				}
			}
		}

		return best == null ? null : asTyped(text, best);
	}

	/**
	 * The words of the message that match this (as matched, double letters made single: "crepers"), as they typed them
	 * ("creepers"); or the match itself.
	 */
	private static String asTyped(String text, String matched) {
		String[] words = text.toLowerCase(Locale.ROOT).replace("'", "").replace("’", "").split("[^a-z0-9]+");
		String want = Lines.understood(matched);
		for (int length = 1; length <= 4; length++) {
			for (int i = 0; i + length <= words.length; i++) {
				String typed = String.join(" ", java.util.Arrays.copyOfRange(words, i, i + length)).trim();
				if (!typed.isEmpty() && Lines.understood(typed).equals(want)) {
					return typed;
				}
			}
		}

		return matched;
	}

	/** Short words players use for items (and "food", "wood": any of those). */
	private static final Map<String, String> ALIASES = Map.ofEntries(Map.entry("dias", "minecraft:diamond"), Map.entry("dia", "minecraft:diamond"),
		Map.entry("iron", "minecraft:iron_ingot"), Map.entry("gold", "minecraft:gold_ingot"), Map.entry("netherite", "minecraft:netherite_ingot"),
		Map.entry("pearl", "minecraft:ender_pearl"), Map.entry("obby", "minecraft:obsidian"), Map.entry("xp bottle", "minecraft:experience_bottle"),
		Map.entry("food", "food"), Map.entry("wood", "wood"), Map.entry("log", "wood"), Map.entry("plank", "wood"), Map.entry("steak", "minecraft:cooked_beef"),
		Map.entry("emmy", "minecraft:emerald"), Map.entry("emmies", "minecraft:emerald"), Map.entry("diamond", "minecraft:diamond"));
	@Nullable
	private static Map<String, String> itemNames;
	@Nullable
	private static List<String> creatureNames;

	/** Every item by its name in chat words ("iron ingot", "diamond sword"), and the short words. */
	private static Map<String, String> itemNames() {
		if (itemNames == null) {
			Map<String, String> names = new HashMap<>();
			for (Item item : BuiltInRegistries.ITEM) {
				if (item == Items.AIR) {
					continue;
				}

				String id = BuiltInRegistries.ITEM.getKey(item).toString();
				String name = Lines.normalize(new ItemStack(item).getHoverName().getString()).trim();
				if (!name.isEmpty()) {
					names.putIfAbsent(name, id);
				}

				names.putIfAbsent(Lines.normalize(BuiltInRegistries.ITEM.getKey(item).getPath().replace('_', ' ')).trim(), id);
			}

			ALIASES.forEach((alias, id) -> names.put(Lines.normalize(alias).trim(), id));
			itemNames = names;
		}

		return itemNames;
	}

	/** Every creature's name in chat words ("cat", "iron golem"). */
	private static List<String> creatureNames() {
		if (creatureNames == null) {
			List<String> names = new ArrayList<>();
			for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
				if (type.getCategory() != net.minecraft.world.entity.MobCategory.MISC || type == EntityType.VILLAGER || type == EntityType.IRON_GOLEM) {
					String name = Lines.normalize(type.getDescription().getString()).trim();
					if (!name.isEmpty()) {
						names.add(name);
					}
				}
			}

			creatureNames = names;
		}

		return creatureNames;
	}

	static void thought(DrifterEntity drifter, long now, String what) {
		drifter.getMemory().thoughts().add(now, what);
	}
}
