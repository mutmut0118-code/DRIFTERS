package com.dinglebob.drifter.social.talk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.social.Lines;
import com.dinglebob.drifter.social.Relationship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * What players told a Drifter in chat, and what it heard about them from others (user, 2026-10-28: "remember what I
 * say"). Per player: what to call them, where their base is, what they were off to do, what they promised, what they
 * like and hate, their news. And places it was told of ("theres a village north"), with who said so, and what it was
 * told about others ("bob is a griefer"). Hearsay, not sight: it never goes into its memory of the world (DESIGN.md
 * section 5), only into what it says; and it passes places on to other Drifters, one telling further each time.
 */
public final class Heard {
	public static final int VERSION = 1;
	/** It asks how their plan went once they've been at it this long, until a day on. */
	static final long PLAN_ASK_AFTER = 20L * 60 * 3;
	static final long PLAN_KEEPS = 24000L;
	/** A promise is due after a day, and broken after three. */
	static final long PROMISE_DUE = 24000L;
	static final long PROMISE_BROKEN = 24000L * 3;
	/** A broken promise: this much trust lost; kept, this much gained. */
	static final double BROKEN_TRUST = -4.0;
	static final double KEPT_TRUST = 3.0;
	/** Their news is talked about for a day; places for five days; words about others for three. */
	static final long NEWS_KEEPS = 24000L;
	static final long PLACE_KEEPS = 24000L * 5;
	static final long WORD_KEEPS = 24000L * 3;
	/** Places and words kept at most; tellings at most (told on and on, it's too vague to pass on). */
	private static final int MOST_PLACES = 24;
	private static final int MOST_WORDS = 12;
	static final int MOST_HOPS = 3;
	private static final int MOST_LIKES = 4;
	/** News it asks them about later ("you get your stuff back?"), and news it passes on about them. */
	private static final java.util.Set<String> ASKS_AFTER = java.util.Set.of("died", "diamonds", "hungry", "built", "nether", "dragon", "lost", "tired");
	private static final java.util.Set<String> PASSES_ON = java.util.Set.of("died", "diamonds", "built", "nether", "end", "dragon");
	/** Places of a kind this close together are the same place. */
	private static final double SAME_PLACE = 48.0;

	/** What one player told it. */
	public static final class Notes {
		public final UUID player;
		public String name;
		/** What they asked to be called ("" for their name). */
		public String nick = "";
		@Nullable
		public BlockPos base;
		public String baseDim = "";
		public boolean baseExact;
		public long baseAt = -1;
		/** What they said they were off to do (a plan kind: "mining", "nether"...) and when; asked about yet. */
		public String plan = "";
		public long planAt = -1;
		public boolean planAsked;
		public final List<Promise> promises = new ArrayList<>();
		public final List<String> likes = new ArrayList<>();
		public final List<String> hates = new ArrayList<>();
		/** Their news (a kind: "died", "diamonds"...) and when; brought up yet. */
		public String news = "";
		public long newsAt = -1;
		public boolean newsAsked;
		/** The last thing they told it (for "forget that"): "nick", "base", "plan", "promise", "like", "hate", "news", "place". */
		public String last = "";
		/** The day they last told it about each other one (once a day each counts). */
		public final Map<String, Long> wordDay = new HashMap<>();

		Notes(UUID player, String name) {
			this.player = player;
			this.name = name;
		}

		boolean empty() {
			return this.nick.isEmpty() && this.base == null && this.plan.isEmpty() && this.promises.isEmpty() && this.likes.isEmpty() && this.hates.isEmpty()
				&& this.news.isEmpty();
		}
	}

	/**
	 * Something they said they'd bring it: an item id, or "food" or "wood" (any of those), or "" (anything: "i owe you");
	 * how many; when; when it last reminded them; broken (three days and nothing).
	 */
	public static final class Promise {
		public final String item;
		public final int count;
		public long at;
		public long remindedAt = -1;
		public boolean broken;
		public boolean brokenSaid;

		Promise(String item, int count, long at) {
			this.item = item;
			this.count = count;
			this.at = at;
		}

		/** This gift keeps it. */
		boolean keptBy(ItemStack stack) {
			return switch (this.item) {
				case "" -> true;
				case "food" -> stack.has(net.minecraft.core.component.DataComponents.FOOD);
				case "wood" -> stack.is(net.minecraft.tags.ItemTags.LOGS) || stack.is(net.minecraft.tags.ItemTags.PLANKS);
				default -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(this.item);
			};
		}

		/** In words: "3 iron ingot", "some food", "something". */
		String words() {
			String what = switch (this.item) {
				case "" -> "";
				case "food", "wood" -> this.item;
				default -> {
					ResourceLocation id = ResourceLocation.tryParse(this.item);
					var item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
					yield new ItemStack(item).getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
				}
			};
			if (what.isEmpty()) {
				return "something";
			}

			return this.count > 1 ? this.count + " " + what : this.item.equals("food") || this.item.equals("wood") ? "some " + what : what;
		}
	}

	/**
	 * A place it was told of: its kind ("village", "diamonds"...), where (exactly, or roughly: a direction and a distance
	 * from where the teller stood), who said so (a player's name; from a Drifter, the player it came from), and how many
	 * tellings ago.
	 */
	public record Place(String kind, BlockPos pos, String dim, boolean exact, String teller, @Nullable UUID tellerId, long at, int hops) {
	}

	/** What it was told about someone else: good or bad, by whom, when. */
	public record Word(String about, @Nullable UUID aboutId, boolean good, String teller, long at) {
	}

	private final DrifterEntity drifter;
	private final Map<UUID, Notes> notes = new LinkedHashMap<>();
	private final List<Place> places = new ArrayList<>();
	private final List<Word> words = new ArrayList<>();

	public Heard(DrifterEntity drifter) {
		this.drifter = drifter;
	}

	@Nullable
	public Notes get(UUID player) {
		return this.notes.get(player);
	}

	public Notes of(Player player) {
		Notes n = this.notes.computeIfAbsent(player.getUUID(), id -> new Notes(id, player.getGameProfile().getName()));
		n.name = player.getGameProfile().getName();
		return n;
	}

	public Collection<Notes> all() {
		return this.notes.values();
	}

	public List<Place> places() {
		return this.places;
	}

	public List<Word> words() {
		return this.words;
	}

	/** What it calls them: what they asked to be called, else their name. */
	public static String callName(DrifterEntity drifter, Player player) {
		Notes n = drifter.getHeard().get(player.getUUID());
		return n != null && !n.nick.isEmpty() ? n.nick : player.getGameProfile().getName();
	}

	/** A place it was told of (the same place again: the newer, more exact word on it). */
	public void learn(Place place) {
		long now = this.drifter.level().getGameTime();
		this.places.removeIf(old -> now - old.at() > PLACE_KEEPS);
		for (int i = 0; i < this.places.size(); i++) {
			Place old = this.places.get(i);
			if (old.kind().equals(place.kind()) && old.dim().equals(place.dim()) && old.pos().closerThan(place.pos(), SAME_PLACE)) {
				if (place.exact() || !old.exact()) {
					this.places.set(i, place);
				}

				return;
			}
		}

		this.places.add(place);
		while (this.places.size() > MOST_PLACES) {
			this.places.remove(0);
		}
	}

	public void learn(Word word) {
		this.words.removeIf(old -> old.about().equals(word.about()) && old.teller().equals(word.teller()));
		this.words.add(word);
		while (this.words.size() > MOST_WORDS) {
			this.words.remove(0);
		}
	}

	/** It knows of this place already (told of it, or one of its kind close by). */
	public boolean knows(Place place) {
		return this.knows(place, null);
	}

	/** It knows of this place already, from someone other than {@code notFrom}. */
	public boolean knows(Place place, @Nullable String notFrom) {
		for (Place mine : this.places) {
			if (mine.kind().equals(place.kind()) && mine.dim().equals(place.dim()) && mine.pos().closerThan(place.pos(), SAME_PLACE)
				&& !mine.teller().equals(notFrom)) {
				return true;
			}
		}

		return false;
	}

	/** The nearest place of this kind it was told of (fresh), or null. */
	@Nullable
	public Place nearest(String kind, Vec3 from) {
		long now = this.drifter.level().getGameTime();
		String dim = this.drifter.level().dimension().location().toString();
		Place best = null;
		for (Place place : this.places) {
			if (place.kind().equals(kind) && place.dim().equals(dim) && now - place.at() <= PLACE_KEEPS
				&& (best == null || Vec3.atCenterOf(place.pos()).distanceToSqr(from) < Vec3.atCenterOf(best.pos()).distanceToSqr(from))) {
				best = place;
			}
		}

		return best;
	}

	/** It likes or hates this, as they said (the newest first; a few kept). */
	void like(Notes n, String thing, boolean likes) {
		List<String> list = likes ? n.likes : n.hates;
		(likes ? n.hates : n.likes).remove(thing);
		list.remove(thing);
		list.add(0, thing);
		while (list.size() > MOST_LIKES) {
			list.remove(list.size() - 1);
		}
	}

	/** Forget what they told it: all of it, or the last thing. True if there was something. */
	public boolean forget(Player player, boolean all) {
		Notes n = this.notes.get(player.getUUID());
		String name = player.getGameProfile().getName();
		if (all) {
			boolean had = n != null && !n.empty() || this.places.stream().anyMatch(place -> place.teller().equals(name));
			this.notes.remove(player.getUUID());
			this.places.removeIf(place -> place.teller().equals(name));
			this.words.removeIf(word -> word.teller().equals(name));
			return had;
		}

		if (n == null || n.last.isEmpty()) {
			return false;
		}

		switch (n.last) {
			case "nick" -> n.nick = "";
			case "base" -> n.base = null;
			case "plan" -> n.plan = "";
			case "promise" -> {
				if (!n.promises.isEmpty()) {
					n.promises.remove(n.promises.size() - 1);
				}
			}
			case "like" -> {
				if (!n.likes.isEmpty()) {
					n.likes.remove(0);
				}
			}
			case "hate" -> {
				if (!n.hates.isEmpty()) {
					n.hates.remove(0);
				}
			}
			case "news" -> n.news = "";
			case "place" -> {
				for (int i = this.places.size() - 1; i >= 0; i--) {
					if (this.places.get(i).teller().equals(name) && this.places.get(i).hops() == 0) {
						this.places.remove(i);
						break;
					}
				}
			}
			case "word" -> {
				for (int i = this.words.size() - 1; i >= 0; i--) {
					if (this.words.get(i).teller().equals(name)) {
						this.words.remove(i);
						break;
					}
				}
			}
			default -> {
			}
		}

		n.last = "";
		return true;
	}

	// ---------------------------------------------------------------------------------------------
	// Promises
	// ---------------------------------------------------------------------------------------------

	/** Promises a day old are due, three days old broken (and cost it some trust in them, once). */
	void checkPromises(Notes n, @Nullable Relationship r, long now) {
		for (Promise promise : n.promises) {
			if (!promise.broken && now - promise.at > PROMISE_BROKEN) {
				promise.broken = true;
				if (r != null) {
					r.addTrust(BROKEN_TRUST);
				}
			}
		}

		// (Long broken and said: let it go.)
		n.promises.removeIf(promise -> promise.broken && promise.brokenSaid && now - promise.at > PROMISE_BROKEN * 3);
	}

	/**
	 * They gave it this: a promise kept? It says so (in place of a plain thanks) and likes them the more; true if it was.
	 */
	public boolean gift(Player player, ItemStack stack) {
		Notes n = this.notes.get(player.getUUID());
		if (n == null || n.promises.isEmpty()) {
			return false;
		}

		long now = this.drifter.level().getGameTime();
		// (The exact item first, "anything" last.)
		Promise kept = null;
		for (Promise promise : n.promises) {
			if (promise.keptBy(stack) && (kept == null || kept.item.isEmpty())) {
				kept = promise;
			}
		}

		if (kept == null) {
			return false;
		}

		n.promises.remove(kept);
		Relationship r = this.drifter.getSocial().get(player.getUUID());
		if (r != null) {
			r.addTrust(kept.broken ? KEPT_TRUST - BROKEN_TRUST * 0.5 : KEPT_TRUST);
		}

		String name = callName(this.drifter, player);
		String line = Lines.pick(this.drifter, kept.broken || now - kept.at > PROMISE_DUE ? "told.promise_late_kept" : "told.promise_kept", "player", name,
			"item", kept.words());
		if (line != null) {
			PlayerTalk.sayAt(this.drifter, line, now + 20 + this.drifter.getRandom().nextInt(20));
		}

		this.drifter.getMemory().thoughts().add(now, n.name + " kept their word (" + kept.words() + ")");
		return true;
	}

	// ---------------------------------------------------------------------------------------------
	// What it has to talk about
	// ---------------------------------------------------------------------------------------------

	/**
	 * Its words and flags for a talk with a player ({@code a} it, {@code b} them): what they told it ({@code b:told_base},
	 * {@code b:plan_due}, {@code b:promise_due}, {@code b:told_like}, {@code b:told_news}...), and places and words it heard
	 * from others it could pass on ({@code a:heard_place}).
	 */
	public static void addPlayerFacts(DrifterEntity me, Player player, Facts a, Facts b) {
		Heard heard = me.getHeard();
		long now = me.level().getGameTime();
		String name = player.getGameProfile().getName();
		heard.addHearsay(me, null, name, a, now);
		Notes n = heard.get(player.getUUID());
		if (n == null) {
			return;
		}

		if (!n.nick.isEmpty()) {
			b.words.put("nick", n.nick);
			b.flags.add("told_nick");
		}

		if (n.base != null) {
			b.words.put("base", n.base.getX() + " " + n.base.getZ());
			b.flags.add("told_base");
		}

		if (!n.plan.isEmpty() && now - n.planAt <= PLAN_KEEPS) {
			b.words.put("plan", word(me, "plan.word." + n.plan, n.plan));
			b.words.put("plan_go", word(me, "plan.go." + n.plan, n.plan));
			b.flags.add("told_plan");
			b.flags.add("plan_" + n.plan);
			if (!n.planAsked && now - n.planAt >= PLAN_ASK_AFTER) {
				b.flags.add("plan_due");
			}
		}

		heard.checkPromises(n, me.getSocial().get(player.getUUID()), now);
		for (Promise promise : n.promises) {
			b.words.put("promise", promise.words());
			if (promise.broken && !promise.brokenSaid) {
				b.flags.add("promise_broken");
				break;
			}

			if (!promise.broken && now - promise.at > PROMISE_DUE && (promise.remindedAt < 0 || now - promise.remindedAt > PROMISE_DUE)) {
				b.flags.add("promise_due");
				break;
			}
		}

		if (!n.likes.isEmpty()) {
			b.words.put("like", n.likes.get(me.getRandom().nextInt(n.likes.size())));
			b.flags.add("told_like");
		}

		if (!n.hates.isEmpty()) {
			b.words.put("hate", n.hates.get(0));
			b.flags.add("told_hate");
		}

		if (ASKS_AFTER.contains(n.news) && now - n.newsAt <= NEWS_KEEPS && now - n.newsAt >= PLAN_ASK_AFTER && !n.newsAsked) {
			b.flags.add("told_news");
			b.flags.add("news_" + n.news);
		}
	}

	/** Its words and flags for a talk with another Drifter: places, words and players' news it could pass on. */
	public static void addDrifterFacts(DrifterEntity me, DrifterEntity other, Facts a) {
		me.getHeard().addHearsay(me, other, null, a, me.level().getGameTime());
	}

	/**
	 * Hearsay it could pass on (to {@code other}, a Drifter, or a player named {@code listener}): a place the other doesn't
	 * know of ({@code heard_place}, its {@code heard_what}, {@code heard_where}, {@code heard_from}), a word about someone
	 * ({@code heard_bad}/{@code heard_good}, {@code heard_about}), a player's plan or news ({@code heard_plan}, {@code
	 * heard_news}). Not what the listener told it themselves.
	 */
	private void addHearsay(DrifterEntity me, @Nullable DrifterEntity other, @Nullable String listener, Facts a, long now) {
		Place place = this.placeFor(other, listener, now);
		if (place != null) {
			a.words.put("heard_what", word(me, "place.word." + place.kind(), place.kind().replace('_', ' ')));
			a.words.put("heard_where", where(me, place, me.position()));
			a.words.put("heard_from", place.teller());
			a.flags.add("heard_place");
			a.flags.add("heard_place_" + place.kind());
		}

		for (int i = this.words.size() - 1; i >= 0; i--) {
			Word word = this.words.get(i);
			if (now - word.at() <= WORD_KEEPS && !word.teller().equals(listener) && !word.about().equals(listener)
				&& (other == null || !word.about().equals(other.getName().getString()))) {
				a.words.put("heard_about", word.about());
				a.words.put("heard_from", a.words.getOrDefault("heard_from", word.teller()));
				a.words.put("word_from", word.teller());
				a.flags.add(word.good() ? "heard_good" : "heard_bad");
				break;
			}
		}

		for (Notes n : this.notes.values()) {
			if (n.name.equals(listener)) {
				continue;
			}

			if (PASSES_ON.contains(n.news) && now - n.newsAt <= NEWS_KEEPS) {
				a.words.put("news_from", n.name);
				a.flags.add("heard_news");
				a.flags.add("heard_news_" + n.news);
			}

			if (!n.plan.isEmpty() && now - n.planAt <= PLAN_KEEPS) {
				a.words.put("plan_from", n.name);
				a.words.put("heard_plan", word(me, "plan.go." + n.plan, n.plan));
				a.flags.add("heard_plan");
			}
		}
	}

	/** The freshest place it could pass on to this one: one the other hasn't heard of, not told by the listener. */
	@Nullable
	Place placeFor(@Nullable DrifterEntity other, @Nullable String listener, long now) {
		String dim = this.drifter.level().dimension().location().toString();
		Place best = null;
		for (Place place : this.places) {
			if (now - place.at() > PLACE_KEEPS || place.hops() >= MOST_HOPS || !place.dim().equals(dim) || place.teller().equals(listener)
				|| other != null && other.getHeard().knows(place)) {
				continue;
			}

			if (best == null || place.at() > best.at()) {
				best = place;
			}
		}

		return best;
	}

	/** It told {@code other} of a place (a talk's "pass_place"): the other has it, one telling further on. */
	static void passPlace(DrifterEntity from, DrifterEntity to, long now) {
		Place place = from.getHeard().placeFor(to, null, now);
		if (place != null) {
			to.getHeard().learn(new Place(place.kind(), place.pos(), place.dim(), place.exact(), place.teller(), place.tellerId(), place.at(), place.hops() + 1));
		}
	}

	/**
	 * It told {@code other} what it heard about someone (a talk's "pass_word"): if they get along, the other thinks a
	 * little the less (or the more) of them too.
	 */
	static void passWord(DrifterEntity from, DrifterEntity to, long now) {
		for (int i = from.getHeard().words.size() - 1; i >= 0; i--) {
			Word word = from.getHeard().words.get(i);
			if (now - word.at() > WORD_KEEPS || word.about().equals(to.getName().getString())) {
				continue;
			}

			Relationship trust = to.getBonds().get(from.getUUID());
			if (trust != null && trust.trust >= 0.0 && word.aboutId() != null) {
				Relationship about = to.getSocial().get(word.aboutId());
				if (about == null) {
					about = to.getBonds().get(word.aboutId());
				}

				if (about != null) {
					about.addTrust(word.good() ? 1.0 : -1.5);
				}
			}

			return;
		}
	}

	/** It brought this topic up with them: what it asked about is asked (the plan, the promise, the news). */
	public void topicSaid(Player player, String topicId, long now) {
		Notes n = this.notes.get(player.getUUID());
		if (n == null) {
			return;
		}

		switch (topicId) {
			case "heard_plan" -> n.planAsked = true;
			case "heard_news" -> n.newsAsked = true;
			case "heard_promise" -> n.promises.stream().filter(promise -> !promise.broken).forEach(promise -> promise.remindedAt = now);
			case "heard_promise_broken" -> n.promises.stream().filter(promise -> promise.broken).forEach(promise -> promise.brokenSaid = true);
			default -> {
			}
		}
	}

	/** Where a place is, said from here: "at x y z", or a direction and a distance. */
	static String where(DrifterEntity me, Place place, Vec3 from) {
		Vec3 at = Vec3.atCenterOf(place.pos());
		if (place.exact()) {
			String line = Lines.pick(me, "place.where.exact", "coords", place.pos().getX() + " " + place.pos().getY() + " " + place.pos().getZ());
			return line != null ? line : "at " + place.pos().getX() + " " + place.pos().getY() + " " + place.pos().getZ();
		}

		String way = PlayerTalk.way(from, at);
		int dist = PlayerTalk.distance(from, at);
		if (dist < 24) {
			String line = Lines.pick(me, "place.where.near");
			return line != null ? line : "near here";
		}

		String line = Lines.pick(me, "place.where.way", "way", way, "dist", dist);
		return line != null ? line : way + " of here";
	}

	/** A word for something from the lines ({@code plan.word.nether}: "the nether"), or this. */
	static String word(DrifterEntity me, String key, String otherwise) {
		String line = Lines.pick(me, key);
		return line != null ? line : otherwise.replace('_', ' ');
	}

	/** All of it this much older (a debug command: the follow-ups without waiting). */
	public void age(long ticks) {
		for (Notes n : this.notes.values()) {
			n.baseAt -= n.baseAt >= 0 ? ticks : 0;
			n.planAt -= n.planAt >= 0 ? ticks : 0;
			n.newsAt -= n.newsAt >= 0 ? ticks : 0;
			n.promises.forEach(promise -> promise.at -= ticks);
		}

		this.places.replaceAll(p -> new Place(p.kind(), p.pos(), p.dim(), p.exact(), p.teller(), p.tellerId(), p.at() - ticks, p.hops()));
		this.words.replaceAll(w -> new Word(w.about(), w.aboutId(), w.good(), w.teller(), w.at() - ticks));
	}

	// ---------------------------------------------------------------------------------------------
	// Saving
	// ---------------------------------------------------------------------------------------------

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", VERSION);
		ListTag players = new ListTag();
		for (Notes n : this.notes.values()) {
			CompoundTag p = new CompoundTag();
			p.putUUID("Player", n.player);
			p.putString("Name", n.name);
			p.putString("Nick", n.nick);
			if (n.base != null) {
				p.putLong("Base", n.base.asLong());
				p.putString("BaseDim", n.baseDim);
				p.putBoolean("BaseExact", n.baseExact);
				p.putLong("BaseAt", n.baseAt);
			}

			p.putString("Plan", n.plan);
			p.putLong("PlanAt", n.planAt);
			p.putBoolean("PlanAsked", n.planAsked);
			ListTag promises = new ListTag();
			for (Promise promise : n.promises) {
				CompoundTag t = new CompoundTag();
				t.putString("Item", promise.item);
				t.putInt("Count", promise.count);
				t.putLong("At", promise.at);
				t.putLong("RemindedAt", promise.remindedAt);
				t.putBoolean("Broken", promise.broken);
				t.putBoolean("BrokenSaid", promise.brokenSaid);
				promises.add(t);
			}

			p.put("Promises", promises);
			p.put("Likes", strings(n.likes));
			p.put("Hates", strings(n.hates));
			p.putString("News", n.news);
			p.putLong("NewsAt", n.newsAt);
			p.putBoolean("NewsAsked", n.newsAsked);
			p.putString("Last", n.last);
			players.add(p);
		}

		tag.put("Players", players);
		ListTag places = new ListTag();
		for (Place place : this.places) {
			CompoundTag t = new CompoundTag();
			t.putString("Kind", place.kind());
			t.putLong("Pos", place.pos().asLong());
			t.putString("Dim", place.dim());
			t.putBoolean("Exact", place.exact());
			t.putString("Teller", place.teller());
			if (place.tellerId() != null) {
				t.putUUID("TellerId", place.tellerId());
			}

			t.putLong("At", place.at());
			t.putInt("Hops", place.hops());
			places.add(t);
		}

		tag.put("Places", places);
		ListTag words = new ListTag();
		for (Word word : this.words) {
			CompoundTag t = new CompoundTag();
			t.putString("About", word.about());
			if (word.aboutId() != null) {
				t.putUUID("AboutId", word.aboutId());
			}

			t.putBoolean("Good", word.good());
			t.putString("Teller", word.teller());
			t.putLong("At", word.at());
			words.add(t);
		}

		tag.put("Words", words);
		return tag;
	}

	public void load(CompoundTag tag) {
		this.notes.clear();
		this.places.clear();
		this.words.clear();
		// Version 1 is the first format.
		for (Tag t : tag.getList("Players", Tag.TAG_COMPOUND)) {
			CompoundTag p = (CompoundTag)t;
			Notes n = new Notes(p.getUUID("Player"), p.getString("Name"));
			n.nick = p.getString("Nick");
			if (p.contains("Base")) {
				n.base = BlockPos.of(p.getLong("Base"));
				n.baseDim = p.getString("BaseDim");
				n.baseExact = p.getBoolean("BaseExact");
				n.baseAt = p.getLong("BaseAt");
			}

			n.plan = p.getString("Plan");
			n.planAt = p.getLong("PlanAt");
			n.planAsked = p.getBoolean("PlanAsked");
			for (Tag pt : p.getList("Promises", Tag.TAG_COMPOUND)) {
				CompoundTag c = (CompoundTag)pt;
				Promise promise = new Promise(c.getString("Item"), c.getInt("Count"), c.getLong("At"));
				promise.remindedAt = c.getLong("RemindedAt");
				promise.broken = c.getBoolean("Broken");
				promise.brokenSaid = c.getBoolean("BrokenSaid");
				n.promises.add(promise);
			}

			p.getList("Likes", Tag.TAG_STRING).forEach(s -> n.likes.add(s.getAsString()));
			p.getList("Hates", Tag.TAG_STRING).forEach(s -> n.hates.add(s.getAsString()));
			n.news = p.getString("News");
			n.newsAt = p.getLong("NewsAt");
			n.newsAsked = p.getBoolean("NewsAsked");
			n.last = p.getString("Last");
			this.notes.put(n.player, n);
		}

		for (Tag t : tag.getList("Places", Tag.TAG_COMPOUND)) {
			CompoundTag c = (CompoundTag)t;
			this.places.add(new Place(c.getString("Kind"), BlockPos.of(c.getLong("Pos")), c.getString("Dim"), c.getBoolean("Exact"), c.getString("Teller"),
				c.hasUUID("TellerId") ? c.getUUID("TellerId") : null, c.getLong("At"), c.getInt("Hops")));
		}

		for (Tag t : tag.getList("Words", Tag.TAG_COMPOUND)) {
			CompoundTag c = (CompoundTag)t;
			this.words.add(new Word(c.getString("About"), c.hasUUID("AboutId") ? c.getUUID("AboutId") : null, c.getBoolean("Good"), c.getString("Teller"),
				c.getLong("At")));
		}
	}

	private static ListTag strings(List<String> list) {
		ListTag tag = new ListTag();
		list.forEach(s -> tag.add(StringTag.valueOf(s)));
		return tag;
	}
}
