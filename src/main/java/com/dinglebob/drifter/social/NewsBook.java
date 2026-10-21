package com.dinglebob.drifter.social;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.personality.Mood;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The news a Drifter carries (milestone 22 groundwork: "news that travels"). What it saw for itself goes in first-hand;
 * what it's told, if it hasn't heard it already, goes in one telling further on, and counts for what the teller's word
 * is worth to it. When it meets another Drifter it gets on with, now and then it passes on the freshest few the other
 * hasn't heard. News fades: a few days, or five tellings on.
 *
 * <p>What news does to the one who hears it: a player's deed moves its view of that player (a part of what it would
 * have been first-hand, more if the one it happened to is a friend); a death saddens a friend (and a player who did it
 * is thought the less of); another mod's news is that mod's to act on ({@code DrifterEvents.HEARD_NEWS}).
 */
public final class NewsBook {
	/** News carried at most (the least worth telling go first). */
	private static final int MOST = 32;
	/** Told this many times over, it's too garbled to pass on. */
	private static final int MOST_HOPS = 5;
	/** How long a player's deed or a death is worth telling (two game days). */
	public static final long DEFAULT_LASTS = 48000L;
	/** It keeps its news from a Drifter it trusts this little (one it "doesn't like the look of"). */
	private static final double DISLIKES = -15.0;
	/** A teller passes on this many at a meeting. */
	private static final int PER_MEETING = 3;
	/** Hearsay counts for this much of seeing it, before trust in the teller and the tellings. */
	private static final double HEARSAY = 0.35;
	private static final double PER_HOP = 0.7;
	/** A death is seen this far off at most (and then only in sight). */
	private static final double WITNESS_RANGE = 32.0;

	private final DrifterEntity drifter;
	private final Map<String, News> news = new LinkedHashMap<>();
	/** When it last told each Drifter something (once a minute per pair at most). */
	private final Map<UUID, Long> toldAt = new java.util.HashMap<>();

	public NewsBook(DrifterEntity drifter) {
		this.drifter = drifter;
	}

	public List<News> all() {
		return List.copyOf(this.news.values());
	}

	public boolean knows(String key) {
		return this.news.containsKey(key);
	}

	/** Something it saw (or was part of) for itself. */
	public void saw(News news) {
		if (!this.news.containsKey(news.key())) {
			this.put(news);
		}
	}

	/** It's told something by {@code teller} (null: another mod's doing). Already heard: nothing. */
	public void hear(News told, @Nullable DrifterEntity teller) {
		if (this.news.containsKey(told.key()) || told.stale(this.drifter.level().getGameTime())) {
			return;
		}

		News news = teller == null ? told : told.retold();
		this.put(news);
		this.react(news, teller);
		DrifterEvents.HEARD_NEWS.invoker().onHeardNews(this.drifter, news, teller);
	}

	private void put(News news) {
		this.news.put(news.key(), news);
		if (this.news.size() > MOST) {
			// The least worth telling go: stale first, then the most retold, then the oldest.
			long now = this.drifter.level().getGameTime();
			this.news.values().stream().min(Comparator.comparing((News n) -> !n.stale(now)).thenComparing(n -> -n.hops()).thenComparing(News::since))
				.ifPresent(n -> this.news.remove(n.key()));
		}
	}

	/** What hearing it does to it. */
	private void react(News news, @Nullable DrifterEntity teller) {
		long now = this.drifter.level().getGameTime();
		String from = teller == null ? "" : " (from " + teller.getName().getString() + ")";
		// What the teller's word is worth: its trust in them (strangers count for a little), less with each telling.
		double belief = teller == null ? 1.0 : Math.max(0.1, (this.drifter.getBonds().get(teller.getUUID()) == null ? 0.0
			: this.drifter.getBonds().get(teller.getUUID()).trust) / 100.0 + 0.5);
		double worth = HEARSAY * Math.min(1.0, belief) * Math.pow(PER_HOP, Math.max(0, news.hops() - 1));
		switch (news.kind()) {
			case PLAYER_DEED -> {
				if (news.about() == null || news.other() != null && news.other().equals(this.drifter.getUUID())) {
					return;
				}

				// (A friend wronged or helped: it takes it to heart more.)
				var bond = news.other() == null ? null : this.drifter.getBonds().get(news.other());
				double care = bond != null && bond.trust >= DrifterBonds.FRIEND ? 1.6 : 1.0;
				Relationship r = this.drifter.getSocial().getOrMeet(news.about(), news.aboutName());
				double before = r.trust;
				r.addTrust(news.weight() * worth * care);
				if (news.weight() < -30.0) {
					r.addFear(-news.weight() * worth * 0.3);
				}

				this.drifter.getMemory().thoughts().add(now, String.format(Locale.ROOT, "heard that %s %s%s: trust %.0f -> %.0f", news.aboutName(), news.what(), from,
					before, r.trust));
			}
			case DEATH -> {
				var bond = news.about() == null ? null : this.drifter.getBonds().get(news.about());
				boolean friend = bond != null && bond.trust >= DrifterBonds.FRIEND;
				this.drifter.getMemory().thoughts().add(now, "heard that " + news.aboutName() + " " + news.what() + from);
				if (friend) {
					this.drifter.getPersonality().feel(Mood.GRIEVING, 0.6, news.aboutName() + " died");
				}

				// Killed by a player: thought the less of (much less by a friend of the dead).
				if (news.weight() > 0.0 && news.other() != null) {
					Relationship r = this.drifter.getSocial().getOrMeet(news.other(), news.otherName());
					double before = r.trust;
					r.addTrust(SocialEvent.KILLED_FRIEND.trust * worth * (friend ? 2.0 : 0.5));
					if (r.trust != before) {
						this.drifter.getMemory().thoughts().add(now, String.format(Locale.ROOT, "%s killed %s: trust %.0f -> %.0f", news.otherName(),
							news.aboutName(), before, r.trust));
					}
				}
			}
			default -> {
			}
		}
	}

	/**
	 * Meeting {@code other} (in sight, close): now and then (by how sociable it is, and if they get on) it passes on the
	 * freshest few the other hasn't heard; with a player near enough to hear, now and then out loud.
	 */
	public void meet(DrifterEntity other, Relationship bond) {
		long now = this.drifter.level().getGameTime();
		if (bond.trust <= DISLIKES || now - this.toldAt.getOrDefault(other.getUUID(), Long.MIN_VALUE / 2) < 20 * 60) {
			return;
		}

		this.toldAt.put(other.getUUID(), now);
		double sociable = this.drifter.getPersonality().get(Trait.SOCIABILITY);
		if (this.drifter.getRandom().nextDouble() > 0.3 + 0.7 * sociable) {
			return;
		}

		NewsBook theirs = other.getNews();
		List<News> fresh = new ArrayList<>();
		for (News news : this.news.values()) {
			if (!news.stale(now) && news.hops() < MOST_HOPS && !theirs.knows(news.key())) {
				fresh.add(news);
			}
		}

		fresh.sort(Comparator.comparingLong(News::since).reversed());
		int told = 0;
		for (News news : fresh.subList(0, Math.min(PER_MEETING, fresh.size()))) {
			theirs.hear(news, this.drifter);
			told++;
			if (news.kind() != News.Kind.CUSTOM && news.hops() <= 1 && this.drifter.getRandom().nextInt(3) == 0 && playerNear(other)) {
				ChatLines.sayLine(this.drifter, "did you hear? " + news.aboutName() + " " + news.what());
			}
		}

		if (told > 0) {
			this.drifter.getMemory().thoughts().add(now, "told " + other.getName().getString() + " the news (" + told + ")");
		}
	}

	private static boolean playerNear(DrifterEntity drifter) {
		Player player = drifter.level().getNearestPlayer(drifter, 16.0);
		return player != null && SocialWatcher.isRealPlayer(player);
	}

	// ----- Making news -----

	/** A player did this to {@code drifter}, which cost or earned them this much of its trust. */
	public static News deed(DrifterEntity drifter, Player player, SocialEvent event, double trustChange) {
		long now = drifter.level().getGameTime();
		String victim = drifter.getName().getString();
		String what = event.what.replaceAll("\\bits\\b", victim + "'s").replaceAll("\\bit\\b", victim);
		String key = "deed:" + player.getUUID() + ":" + drifter.getUUID() + ":" + event.name() + ":" + now / 24000L;
		return new News(key, News.Kind.PLAYER_DEED, player.getUUID(), player.getGameProfile().getName(), drifter.getUUID(), victim, what, now, 0, trustChange,
			DEFAULT_LASTS, new CompoundTag());
	}

	/**
	 * {@code dead} died, as its death message has it: the news, for the Drifters who saw it (in sight, or right beside it).
	 * Who killed it is kept if it was a player or a Drifter.
	 */
	public static void sawDie(DrifterEntity dead, String message, DamageSource source) {
		Entity killer = source.getEntity();
		DrifterEntity owner = FakePlayerBridge.ownerOf(killer);
		if (owner != null) {
			killer = owner;
		} else if (!SocialWatcher.isRealPlayer(killer)) {
			killer = null;
		}

		long now = dead.level().getGameTime();
		String name = dead.getName().getString();
		String what = message.startsWith(name) ? message.substring(name.length()).trim() : "died";
		News news = new News("death:" + dead.getUUID() + ":" + now, News.Kind.DEATH, dead.getUUID(), name, killer == null ? null : killer.getUUID(),
			killer == null ? "" : killer.getName().getString(), what, now, 0, killer instanceof Player ? 1.0 : 0.0, DEFAULT_LASTS, new CompoundTag());
		for (DrifterEntity other : dead.level().getEntitiesOfClass(DrifterEntity.class, dead.getBoundingBox().inflate(WITNESS_RANGE), DrifterEntity::isAlive)) {
			if (other == dead) {
				continue;
			}

			var seen = other.getSenses().awarenessOf(dead);
			if (seen != null && seen.visibleNow || other.distanceTo(dead) < 4.0) {
				other.getNews().saw(news);
			}
		}
	}

	/** Another mod's news, as {@code drifter} hears it (or sees it: its own doing, or right in front of it). */
	public static News custom(String key, @Nullable UUID about, String aboutName, String what, long lasts, CompoundTag data, long now) {
		return new News(key, News.Kind.CUSTOM, about, aboutName, null, "", what, now, 0, 0.0, lasts, data);
	}

	// ----- Saving -----

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		this.news.values().forEach(news -> list.add(news.save()));
		tag.put("News", list);
		return tag;
	}

	public void load(CompoundTag tag) {
		this.news.clear();
		for (Tag t : tag.getList("News", Tag.TAG_COMPOUND)) {
			News news = News.load((CompoundTag)t);
			if (news != null) {
				this.news.put(news.key(), news);
			}
		}
	}
}
