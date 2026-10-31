package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A history already written (milestone 23, docs/LIVED_WORLD.md W6 and W7; the user, 2026-10-30: "a history already
 * written"; 2026-10-31: "you are free to start w6 and w7, that being included in the pre generator loading screen").
 *
 * <p>W6: made with the world plan, before anyone plays: about {@link #DAYS} days of its places' past, a day a step, on
 * summaries (no Drifters, no blocks), by the rules the towns live by after (milestone 22 step 6): each place's leader and
 * their nature, who comes after them; between powers (a nation, or a town in none) a standing their rulers' natures move
 * day by day, quarrels at the border that set them back, peace pacts and alliances on good terms, wars on bad ones with an
 * aggressive ruler; battles that cost each side people, a frontier town taken or burned to a ruin, a nation conquered
 * whole; a conquered town rising up to be free; free towns joining a nation. Out of it: each place's nation, whether it
 * fell, its leader, its people, when it was founded; between powers, their standing, pacts and wars; and a chronicle of it
 * all, days before day 1. Towns are made to match ({@link WorldPlan#made}).
 *
 * <p>W7: after day 1 the powers none of whose towns are made yet go on the same way, a step each game day (their news in
 * the chronicle); a town made later is made as its summary has it then. A power with a town made lives by the towns'
 * own diplomacy from then on.
 */
final class History {
	/** How many days of history before day 1. */
	static final int DAYS = 1000;
	private static final double LOWEST = -100;
	private static final double HIGHEST = 100;
	/** Capitals this far apart or more don't grate on each other. */
	private static final double FAR = 2600;

	/** Between two powers (a nation, or a town in none: by its id). */
	static final class Ties {
		double standing;
		boolean peace;
		boolean alliance;
		boolean war;
		/** When its war or its latest pact began (a history day). */
		long since;
		long truceUntil = -1;
		@Nullable
		UUID startedBy;
		/** People killed between them in all their wars: an old grudge. */
		int dead;
	}

	/** Something that happened: the history day, what kind (the chronicle's), the words. */
	record Event(long day, String kind, String text) {
	}

	private final List<WorldPlan.Place> places;
	private final List<WorldPlan.PlannedNation> nations;
	private final RandomSource random;
	/** The history's day now: {@link #DAYS} on day 1, one more each game day after. */
	long day;
	final Map<String, Ties> ties = new HashMap<>();
	final List<Event> events = new ArrayList<>();
	/** Per place: the history day it's founded, falls (a ruin of the plan: abandoned), its leader's last day. */
	private final Map<UUID, long[]> dates = new HashMap<>();

	History(List<WorldPlan.Place> places, List<WorldPlan.PlannedNation> nations, long seed) {
		this.places = places;
		this.nations = nations;
		this.random = RandomSource.create(seed ^ 0x6869737479L);
	}

	// ---------------------------------------------------------------------------------------------
	// Writing it (with the plan, in the background)
	// ---------------------------------------------------------------------------------------------

	/** The whole past, from the first day to day 1. */
	void write() {
		this.found();
		for (this.day = 0; this.day < DAYS; this.day++) {
			this.step(null);
		}
	}

	/** When each place was founded (a capital first, its towns after, hamlets later), its first leader and people. */
	private void found() {
		Map<UUID, Long> capitals = new HashMap<>();
		for (WorldPlan.PlannedNation nation : this.nations) {
			capitals.put(nation.id(), (long)this.random.nextInt(80));
		}

		for (WorldPlan.Place place : this.places) {
			long founded;
			long fallDay = Long.MAX_VALUE;
			if (place.kind.equals("ruin")) {
				founded = this.random.nextInt(300);
				fallDay = founded + 100 + this.random.nextInt(300);
			} else if (place.nation == null) {
				founded = this.random.nextInt(500);
			} else if (place.kind.equals("city")) {
				founded = capitals.getOrDefault(place.nation, 0L);
			} else {
				founded = capitals.getOrDefault(place.nation, 0L) + (place.kind.equals("hamlet") ? 150 + this.random.nextInt(500) : 40 + this.random.nextInt(360));
			}

			this.dates.put(place.id, new long[] {founded, fallDay, 0});
			place.people = Math.max(2, place.households);
			this.newLeader(place, founded);
		}
	}

	/**
	 * A day of it: places founded and fallen, leaders who come and go, people born, standings, pacts, wars and their battles,
	 * risings. {@code present}: after day 1 (W7), only for the powers with no town made, and told as news.
	 */
	void step(@Nullable MinecraftServer present) {
		for (WorldPlan.Place place : this.places) {
			long[] date = this.dates.get(place.id);
			if (date == null || !this.lives(place) && !(date[1] == this.day)) {
				continue;
			}

			if (present != null && (place.made || this.live(this.power(place)))) {
				continue;
			}

			if (this.day == date[0] && place.kind.equals("city") && place.nation != null) {
				WorldPlan.PlannedNation nation = this.nation(place.nation);
				if (nation != null) {
					this.tell(present, "nation", place.name + " was founded, and " + nation.name() + " with it, " + place.leader + " its first ruler");
				}
			}

			if (this.day == date[1]) {
				place.fell = true;
				String[] how = {"after a hard winter", "when its well ran dry", "after a sickness", "when its fields failed", "after a fire"};
				this.tell(present, "town", place.name + " was abandoned " + how[this.random.nextInt(how.length)] + "; its people went their ways");
				continue;
			}

			if (this.day > date[2]) {
				String was = place.leader;
				long since = place.leaderSince;
				this.newLeader(place, this.day);
				WorldPlan.PlannedNation nation = place.nation == null ? null : this.nation(place.nation);
				if (nation != null && nation.capital().equals(place.id)) {
					String[] how = {"died", "died", "stepped down", "was voted out"};
					this.tell(present, "nation", was + ", who had ruled " + nation.name() + " for " + (this.day - since) + " days, " + how[this.random.nextInt(
						how.length)] + "; " + place.leader + " rules it now");
				}
			}

			place.people = Math.min(place.households * 3, place.people + Math.max(0.0, place.people * 0.0015));
			if (place.conquered && place.nation != null) {
				this.rising(place, present);
			}
		}

		List<UUID> powers = this.powers();
		for (int i = 0; i < powers.size(); i++) {
			for (int j = i + 1; j < powers.size(); j++) {
				UUID a = powers.get(i);
				UUID b = powers.get(j);
				if (present != null && (this.live(a) || this.live(b))) {
					continue;
				}

				this.between(a, b, present);
			}
		}
	}

	/** Founded by now and not fallen. */
	private boolean lives(WorldPlan.Place place) {
		long[] date = this.dates.get(place.id);
		return date != null && !place.fell && this.day >= date[0];
	}

	/** A new leader for the place from this day: a name, a nature, how long they'll lead. */
	private void newLeader(WorldPlan.Place place, long from) {
		place.leader = DrifterApi.looseName(this.random);
		place.aggression = this.trait();
		place.warmth = this.trait();
		place.ambition = this.trait();
		place.leaderSince = from;
		long[] date = this.dates.get(place.id);
		if (date != null) {
			date[2] = from + 250 + this.random.nextInt(450);
		}
	}

	private double trait() {
		return Mth.clamp(0.5 + this.random.nextGaussian() * 0.22, 0.0, 1.0);
	}

	// ----- Powers -----

	/** Its power: its nation, or itself if it's in none. */
	UUID power(WorldPlan.Place place) {
		return place.nation != null ? place.nation : place.id;
	}

	/** Every power with a place living now. */
	private List<UUID> powers() {
		List<UUID> list = new ArrayList<>();
		for (WorldPlan.Place place : this.places) {
			UUID power = this.power(place);
			if (this.lives(place) && !list.contains(power)) {
				list.add(power);
			}
		}

		return list;
	}

	/** Its places living now. */
	private List<WorldPlan.Place> of(UUID power) {
		List<WorldPlan.Place> list = new ArrayList<>();
		for (WorldPlan.Place place : this.places) {
			if (this.lives(place) && this.power(place).equals(power)) {
				list.add(place);
			}
		}

		return list;
	}

	/** Its seat: a nation's capital, or the town itself. */
	@Nullable
	private WorldPlan.Place seat(UUID power) {
		WorldPlan.PlannedNation nation = this.nation(power);
		UUID id = nation != null ? nation.capital() : power;
		for (WorldPlan.Place place : this.places) {
			if (place.id.equals(id)) {
				return place;
			}
		}

		return null;
	}

	@Nullable
	private WorldPlan.PlannedNation nation(UUID id) {
		for (WorldPlan.PlannedNation nation : this.nations) {
			if (nation.id().equals(id)) {
				return nation;
			}
		}

		return null;
	}

	/** What it's called: its nation's name, or the town's. */
	String name(UUID power) {
		WorldPlan.PlannedNation nation = this.nation(power);
		if (nation != null) {
			return nation.name();
		}

		WorldPlan.Place seat = this.seat(power);
		return seat != null ? seat.name : "?";
	}

	/** A town of it made (it lives by the towns' own diplomacy now). */
	boolean live(UUID power) {
		for (WorldPlan.Place place : this.places) {
			if (place.made && this.power(place).equals(power)) {
				return true;
			}
		}

		return false;
	}

	private int strength(UUID power) {
		double people = 0;
		for (WorldPlan.Place place : this.of(power)) {
			people += place.people;
		}

		return (int)people;
	}

	static String key(UUID a, UUID b) {
		return a.compareTo(b) < 0 ? a + "-" + b : b + "-" + a;
	}

	/** Their ties, made the first time both are about: by how near their seats are and their rulers' natures. */
	Ties ties(UUID a, UUID b) {
		return this.ties.computeIfAbsent(key(a, b), k -> {
			Ties t = new Ties();
			WorldPlan.Place sa = this.seat(a);
			WorldPlan.Place sb = this.seat(b);
			double near = sa == null || sb == null ? 0 : Math.max(0, 1 - Math.hypot(sa.x - sb.x, sa.z - sb.z) / FAR);
			t.standing = this.random.nextGaussian() * 12 - 15 * near + (sa == null || sb == null ? 0 : 10 * (sa.warmth + sb.warmth - 1));
			return t;
		});
	}

	// ----- Between two powers -----

	private void between(UUID a, UUID b, @Nullable MinecraftServer present) {
		WorldPlan.Place sa = this.seat(a);
		WorldPlan.Place sb = this.seat(b);
		if (sa == null || sb == null) {
			return;
		}

		Ties t = this.ties(a, b);
		double near = Math.max(0, 1 - Math.hypot(sa.x - sb.x, sa.z - sb.z) / FAR);
		t.standing += 0.03 * (sa.warmth + sb.warmth - 1) - 0.05 * (sa.aggression + sb.aggression - 1) - 0.02 * near + (t.peace ? 0.03 : 0)
			+ (t.alliance ? 0.03 : 0) - (t.war ? 0.05 : 0) + this.random.nextGaussian() * 0.6;
		// A quarrel at the border, more often the nearer they are.
		if (!t.war && this.random.nextFloat() < 0.003 * (0.3 + near)) {
			double hurt = 6 + this.random.nextInt(14);
			t.standing -= hurt;
			if (hurt >= 14) {
				this.tell(present, "diplomacy", "A quarrel at the border set " + this.name(a) + " and " + this.name(b) + " against each other");
			}
		}

		t.standing = Mth.clamp(t.standing, LOWEST, HIGHEST);
		if (t.war) {
			this.war(a, b, t, present);
			return;
		}

		if (!t.peace && t.standing > 25 && this.random.nextFloat() < 0.015) {
			t.peace = true;
			t.since = this.day;
			this.tell(present, "diplomacy", this.name(a) + " and " + this.name(b) + " made a peace pact");
		} else if (t.peace && !t.alliance && t.standing > 55 && this.day - t.since > 80 && this.random.nextFloat() < 0.008) {
			t.alliance = true;
			t.since = this.day;
			this.tell(present, "diplomacy", this.name(a) + " and " + this.name(b) + " became allies");
		} else if (t.alliance && t.standing > 70 && this.random.nextFloat() < 0.004) {
			this.union(a, b, present);
		} else if (this.day >= t.truceUntil && t.standing < -35) {
			// The more aggressive of their rulers may go to war over it.
			UUID by = sa.aggression >= sb.aggression ? a : b;
			double aggression = Math.max(sa.aggression, sb.aggression);
			if (aggression > 0.55 && this.random.nextFloat() < 0.006 * aggression) {
				t.war = true;
				t.peace = false;
				t.alliance = false;
				t.since = this.day;
				t.startedBy = by;
				t.standing = Math.max(LOWEST, t.standing - 15);
				UUID other = by.equals(a) ? b : a;
				this.tell(present, "war", this.name(by) + " declared war on " + this.name(other));
			}
		}
	}

	/** A free town joins its ally's nation (or two free towns: the smaller the bigger's... no nation to join: nothing). */
	private void union(UUID a, UUID b, @Nullable MinecraftServer present) {
		WorldPlan.PlannedNation na = this.nation(a);
		WorldPlan.PlannedNation nb = this.nation(b);
		if (na != null && nb != null || na == null && nb == null) {
			return;
		}

		WorldPlan.PlannedNation nation = na != null ? na : nb;
		UUID town = na != null ? b : a;
		for (WorldPlan.Place place : this.of(town)) {
			place.nation = nation.id();
			place.conquered = false;
			place.loyalty = 40;
			this.tell(present, "nation", place.name + " joined " + nation.name());
		}
	}

	/** A day of their war: maybe a battle, a frontier town taken or burned, a nation conquered; peace when they're weary. */
	private void war(UUID a, UUID b, Ties t, @Nullable MinecraftServer present) {
		long days = this.day - t.since;
		if (this.random.nextFloat() < 0.06) {
			this.battle(a, b, t, present);
			if (!t.war) {
				return;
			}
		}

		if (days > 40 && this.random.nextFloat() < 0.004 + (days - 40) / 20000.0) {
			this.peace(a, b, t, present, "after " + days + " days of war");
		}
	}

	private void peace(UUID a, UUID b, Ties t, @Nullable MinecraftServer present, String how) {
		t.war = false;
		t.peace = true;
		t.since = this.day;
		t.truceUntil = this.day + 200;
		t.standing = Math.max(t.standing, -20);
		this.tell(present, "war", this.name(a) + " and " + this.name(b) + " made peace " + how);
	}

	private void battle(UUID a, UUID b, Ties t, @Nullable MinecraftServer present) {
		WorldPlan.Place fa = this.frontier(a, b);
		WorldPlan.Place fb = this.frontier(b, a);
		if (fa == null || fb == null) {
			return;
		}

		double sa = Math.max(1, this.strength(a));
		double sb = Math.max(1, this.strength(b));
		int lostA = (int)Math.round(this.random.nextDouble() * 4 * sb / (sa + sb));
		int lostB = (int)Math.round(this.random.nextDouble() * 4 * sa / (sa + sb));
		fa.people = Math.max(0, fa.people - lostA);
		fb.people = Math.max(0, fb.people - lostB);
		t.dead += lostA + lostB;
		WorldPlan.Place where = lostA >= lostB ? fa : fb;
		if (lostA + lostB >= 4 || lostA + lostB > 0 && this.random.nextFloat() < 0.3) {
			String dead = lostA > 0 && lostB > 0 ? lostA + " of " + this.name(a) + "'s people and " + lostB + " of " + this.name(b) + "'s"
				: lostA > 0 ? lostA + " of " + this.name(a) + "'s people" : lostB + " of " + this.name(b) + "'s people";
			this.tell(present, "war", dead + " died fighting near " + where.name);
		}

		if (this.random.nextFloat() < 0.02) {
			WorldPlan.Place fallen = this.random.nextBoolean() ? fa : fb;
			String was = fallen.leader;
			this.newLeader(fallen, this.day);
			this.tell(present, "war", was + ", leader of " + fallen.name + ", fell in the fighting; " + fallen.leader + " leads it now");
		}

		// The side that lost more: its frontier town may be lost.
		boolean aLost = lostA * sb > lostB * sa;
		UUID winner = aLost ? b : a;
		UUID loser = aLost ? a : b;
		WorldPlan.Place town = aLost ? fa : fb;
		double ratio = (aLost ? sb : sa) / (aLost ? sa : sb);
		if (town.people > 2 && !(ratio > 1.5 && this.random.nextFloat() < 0.05)) {
			return;
		}

		WorldPlan.PlannedNation won = this.nation(winner);
		WorldPlan.PlannedNation lost = this.nation(loser);
		WorldPlan.Place seat = this.seat(loser);
		if (seat == town) {
			// Its seat: a nation conquered whole, now and then, by one much stronger.
			if (won != null && lost != null && ratio > 2 && this.random.nextFloat() < 0.3) {
				for (WorldPlan.Place place : this.of(loser)) {
					place.nation = won.id();
					place.conquered = true;
					place.loyalty = -10;
				}

				this.nations.remove(lost);
				this.tell(present, "war", won.name() + " conquered " + lost.name() + ": its towns are " + won.name() + "'s now");
				t.war = false;
			}

			return;
		}

		if (won != null && this.random.nextFloat() < 0.6) {
			town.nation = won.id();
			town.conquered = true;
			town.loyalty = -10;
			this.tell(present, "war", won.name() + " took " + town.name + " from " + this.name(loser));
		} else {
			town.fell = true;
			this.tell(present, "war", town.name + " was burned in the war between " + this.name(a) + " and " + this.name(b) + "; its people scattered");
		}

		if (this.random.nextFloat() < 0.5) {
			this.peace(a, b, t, present, "(" + this.name(loser) + " asked for it)");
		}
	}

	/** Its living place nearest the other's (where they'd fight). */
	@Nullable
	private WorldPlan.Place frontier(UUID power, UUID enemy) {
		WorldPlan.Place best = null;
		double bestDistance = Double.MAX_VALUE;
		List<WorldPlan.Place> theirs = this.of(enemy);
		for (WorldPlan.Place mine : this.of(power)) {
			for (WorldPlan.Place their : theirs) {
				double d = Math.hypot(mine.x - their.x, mine.z - their.z);
				if (d < bestDistance) {
					bestDistance = d;
					best = mine;
				}
			}
		}

		return best;
	}

	/** A conquered town wearing of its nation; low enough, and its leader ambitious, it rises up. */
	private void rising(WorldPlan.Place place, @Nullable MinecraftServer present) {
		UUID nation = place.nation;
		boolean atWar = false;
		for (UUID other : this.powers()) {
			if (!other.equals(nation) && this.ties.containsKey(key(nation, other)) && this.ties.get(key(nation, other)).war) {
				atWar = true;
			}
		}

		place.loyalty += atWar ? -0.08 : -0.02;
		if (place.loyalty > -40 || place.ambition < 0.55 || this.random.nextFloat() > 0.004) {
			return;
		}

		String was = this.name(nation);
		place.nation = null;
		place.conquered = false;
		Ties t = this.ties(place.id, nation);
		if (this.random.nextBoolean()) {
			t.war = true;
			t.since = this.day;
			t.startedBy = place.id;
			t.standing = -60;
			this.tell(present, "war", place.name + " rose against " + was);
		} else {
			t.standing = -30;
			t.truceUntil = this.day + 150;
			this.tell(present, "nation", place.name + " won its freedom from " + was);
		}
	}

	private void tell(@Nullable MinecraftServer present, String kind, String text) {
		if (present != null) {
			Chronicle.add(present, kind, "News from afar: " + text);
		} else {
			this.events.add(new Event(this.day, kind, text));
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Asked by the towns
	// ---------------------------------------------------------------------------------------------

	/** Days before now this place was founded (its people's past begins then). */
	int foundedAgo(WorldPlan.Place place) {
		long[] date = this.dates.get(place.id);
		return date == null ? 30 : (int)Math.max(5, this.day - date[0]);
	}

	/** The two places' powers fought each other (people died between them) or are on bad terms. */
	boolean foes(WorldPlan.Place a, WorldPlan.Place b) {
		UUID pa = this.power(a);
		UUID pb = this.power(b);
		if (pa.equals(pb)) {
			return false;
		}

		Ties t = this.ties.get(key(pa, pb));
		return t != null && (t.dead > 0 || t.standing < -25 || t.war);
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the plan
	// ---------------------------------------------------------------------------------------------

	void save(CompoundTag tag) {
		tag.putLong("Day", this.day);
		ListTag list = new ListTag();
		for (var entry : this.ties.entrySet()) {
			Ties t = entry.getValue();
			CompoundTag one = new CompoundTag();
			one.putString("Key", entry.getKey());
			one.putDouble("Standing", t.standing);
			one.putBoolean("Peace", t.peace);
			one.putBoolean("Alliance", t.alliance);
			one.putBoolean("War", t.war);
			one.putLong("Since", t.since);
			one.putLong("Truce", t.truceUntil);
			if (t.startedBy != null) {
				one.putUUID("By", t.startedBy);
			}

			one.putInt("Dead", t.dead);
			list.add(one);
		}

		tag.put("Ties", list);
		ListTag dates = new ListTag();
		for (var entry : this.dates.entrySet()) {
			CompoundTag one = new CompoundTag();
			one.putUUID("Place", entry.getKey());
			one.putLongArray("Dates", entry.getValue());
			dates.add(one);
		}

		tag.put("Dates", dates);
	}

	void load(CompoundTag tag) {
		this.day = tag.getLong("Day");
		this.ties.clear();
		for (Tag t : tag.getList("Ties", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag)t;
			Ties ties = new Ties();
			ties.standing = one.getDouble("Standing");
			ties.peace = one.getBoolean("Peace");
			ties.alliance = one.getBoolean("Alliance");
			ties.war = one.getBoolean("War");
			ties.since = one.getLong("Since");
			ties.truceUntil = one.getLong("Truce");
			ties.startedBy = one.hasUUID("By") ? one.getUUID("By") : null;
			ties.dead = one.getInt("Dead");
			this.ties.put(one.getString("Key"), ties);
		}

		this.dates.clear();
		for (Tag t : tag.getList("Dates", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag)t;
			this.dates.put(one.getUUID("Place"), one.getLongArray("Dates"));
		}
	}
}
