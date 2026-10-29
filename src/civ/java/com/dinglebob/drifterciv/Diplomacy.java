package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.api.Errand;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/**
 * Diplomacy between towns (milestone 22 step 6, part 1; docs/CIVILIZATION.md "Nations and diplomacy": "trade deals,
 * alliances, insults, broken promises remembered"; "news only travels by someone carrying it"):
 * <ul>
 *   <li>Two towns know of each other once one of them does: people of each who knew each other before (a town made ready),
 *       one of its people seeing the other town, a trade run there, word of its founding.</li>
 *   <li>Each pair has a standing, -100 to 100: their leaders' natures (warm and forgiving, or aggressive) and their pacts
 *       move it a little each day; towns close together grate a little; a killing by one's people of the other's, an insult,
 *       an offer turned down move it at once; trade and an offer taken, up.</li>
 *   <li>Each morning a leader may send an envoy to a town it knows (one at a time, a few days apart): to offer a trade deal,
 *       a peace pact or an alliance, as the standing allows, or (aggressive, and they're on bad terms) to insult them. The
 *       envoy walks there (when it's about; out of sight it takes as long as the walk would), says its piece to their
 *       leader, and their leader takes it or not by the standing and its own nature. The envoy brings the answer home.</li>
 *   <li>Pacts: a trade deal sends their traders to each other first; a peace pact (and an alliance) keeps their people from
 *       fighting (their groups' tension held low); an alliance is for the wars of part 2.</li>
 * </ul>
 * Kept with the towns.
 */
final class Diplomacy {
	private Diplomacy() {
	}

	enum Pact {
		TRADE("a trade deal"), PEACE("a peace pact"), ALLIANCE("an alliance");

		final String words;

		Pact(String words) {
			this.words = words;
		}
	}

	/** Two towns that know of each other (a and b in id order). */
	static final class Relation {
		final UUID a;
		final UUID b;
		double standing;
		final EnumSet<Pact> pacts = EnumSet.noneOf(Pact.class);
		long metDay;
		/** When either last sent the other an envoy (game day). */
		long lastEnvoyDay = -100;
		int insults;
		/** The latest between them, for their people's talk: "trade", "peace", "alliance", "refused", "insult", "killing"; and
		 * the day. */
		String news = "";
		long newsDay = -100;

		Relation(UUID a, UUID b) {
			this.a = a;
			this.b = b;
		}

		UUID other(UUID town) {
			return town.equals(this.a) ? this.b : this.a;
		}
	}

	/** An envoy sent (or walking back). */
	static final class Mission {
		final UUID from;
		final UUID to;
		/** A pact's name, or "INSULT". */
		final String kind;
		final UUID envoy;
		final String envoyName;
		final long sentAt;
		final long arriveAt;
		final long backAt;
		/** Walking it (loaded when sent); else it goes as long as the walk would take. */
		boolean walking;
		boolean answered;

		Mission(UUID from, UUID to, String kind, UUID envoy, String envoyName, long sentAt, long arriveAt, long backAt) {
			this.from = from;
			this.to = to;
			this.kind = kind;
			this.envoy = envoy;
			this.envoyName = envoyName;
			this.sentAt = sentAt;
			this.arriveAt = arriveAt;
			this.backAt = backAt;
		}
	}

	/** Standing a pact needs (offered), and that makes an aggressive leader insult them. */
	private static final double FOR_TRADE = 15.0;
	private static final double FOR_PEACE = 35.0;
	private static final double FOR_ALLIANCE = 65.0;
	private static final double FOR_INSULT = -30.0;
	/** Days between envoys between the same two towns. */
	private static final long ENVOY_EVERY = 3;
	/** How far apart towns can know of each other at all (blocks). */
	private static final double FARTHEST = 3000.0;
	/** Closer than this, they grate (land, wood, game) unless at peace. */
	private static final double CLOSE = 500.0;
	/** One of its people this near another town sees it. */
	private static final double SEES = 64.0;
	/** Walking pace for a trip out of sight (ticks a block: about four blocks a second, with stops). */
	private static final long TICKS_A_BLOCK = 6;
	/** At peace: their groups' tension no higher (DrifterWars: 30 is fighting on sight). */
	private static final double PEACEFUL = 15.0;
	/** Talked about for this many days. */
	static final long NEWS_DAYS = 2;
	private static final long MORNING = 1500L;
	/** An envoy walking it is given this long each way before the town goes on without it. */
	private static final long WALKING_AT_MOST = 24000L;

	private static final Map<String, Relation> relations = new LinkedHashMap<>();
	private static final List<Mission> missions = new ArrayList<>();
	/** Each town's day it last did its diplomacy (not saved). */
	private static final Map<UUID, Long> doneDay = new java.util.HashMap<>();

	static void register() {
		// Word of a town's founding reaching one of another town's people: its town knows of it now.
		DrifterEvents.HEARD_NEWS.register((drifter, news, from) -> {
			if (news.key().startsWith("town_founded:") && drifter.getServer() != null) {
				Town mine = townOf(drifter.getUUID());
				Town theirs = byId(news.key().substring("town_founded:".length()));
				if (mine != null && theirs != null && mine != theirs) {
					meet(drifter.getServer(), mine, theirs, 0.0, drifter.getName().getString() + " heard of it");
				}
			}
		});
	}

	// ---------------------------------------------------------------------------------------------
	// Relations
	// ---------------------------------------------------------------------------------------------

	private static String key(UUID x, UUID y) {
		return x.compareTo(y) < 0 ? x + "|" + y : y + "|" + x;
	}

	@Nullable
	static Relation relation(Town x, Town y) {
		return relations.get(key(x.id, y.id));
	}

	/** The towns this one knows of, with the standing. */
	static List<Relation> of(Town town) {
		return relations.values().stream().filter(r -> r.a.equals(town.id) || r.b.equals(town.id)).toList();
	}

	/** They know of each other from now on (if they didn't), starting at this standing. */
	static Relation meet(MinecraftServer server, Town x, Town y, double standing, @Nullable String how) {
		Relation r = relations.get(key(x.id, y.id));
		if (r != null) {
			return r;
		}

		r = x.id.compareTo(y.id) < 0 ? new Relation(x.id, y.id) : new Relation(y.id, x.id);
		r.standing = Math.max(-100.0, Math.min(100.0, standing));
		r.metDay = Chronicle.day(server);
		relations.put(key(x.id, y.id), r);
		if (how != null) {
			Chronicle.add(server, "diplomacy", x.name + " knows of " + y.name + " now (" + how + ")");
		}

		Towns.save(server);
		return r;
	}

	/** Something between them: the standing moved, and maybe news for their people's talk. */
	static void incident(MinecraftServer server, Town x, Town y, double change, @Nullable String news) {
		Relation r = meet(server, x, y, 0.0, null);
		r.standing = Math.max(-100.0, Math.min(100.0, r.standing + change));
		if (news != null) {
			r.news = news;
			r.newsDay = Chronicle.day(server);
		}

		Towns.save(server);
	}

	static String word(double standing) {
		return standing >= 60 ? "close friends" : standing >= 25 ? "friendly" : standing > -25 ? "neutral" : standing > -60 ? "unfriendly" : "hostile";
	}

	// ---------------------------------------------------------------------------------------------
	// Each check (every 10 s) and once a day
	// ---------------------------------------------------------------------------------------------

	static void check(MinecraftServer server, Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return;
		}

		// Seen: one of its people near another town.
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member == null) {
				continue;
			}

			for (Town other : Towns.all()) {
				if (other != town && other.dimension.equals(town.dimension) && relation(town, other) == null
					&& member.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(other.center)) < SEES) {
					meet(server, town, other, 0.0, member.getName().getString() + " came upon it");
				}
			}
		}

		long now = server.overworld().getGameTime();
		// Its envoys out of sight: there and back in the time it'd take.
		for (Mission mission : List.copyOf(missions)) {
			if (!mission.from.equals(town.id)) {
				continue;
			}

			DrifterEntity envoy = TownFounding.loaded(server, mission.envoy);
			// (Back in the running world after a stretch out of it, its errand left behind: given it again, where it was.)
			Town to = byId(mission.to.toString());
			if (mission.walking && envoy != null && DrifterApi.errand(envoy) == null && to != null) {
				DrifterApi.setErrand(envoy, errand(server, town, to, mission, envoy));
			}

			boolean onIt = mission.walking && (envoy == null || DrifterApi.errand(envoy) != null);
			// (Walking it: it gets there when it gets there, a day at most. Found in testing: a walk over hills took longer
			// than the straight line, and the answer came without it.)
			boolean walking = mission.walking && onIt;
			if (!mission.answered && now >= (walking ? mission.sentAt + WALKING_AT_MOST : mission.arriveAt)) {
				answer(server, mission, null);
			}

			if (mission.answered && now >= (walking ? mission.sentAt + 2 * WALKING_AT_MOST : mission.backAt)) {
				if (envoy != null && onIt) {
					DrifterApi.setErrand(envoy, null);
				}

				Diplomacy.home(server, mission);
			}
		}

		long day = Chronicle.day(server);
		long time = server.overworld().getDayTime() % 24000L;
		if (time < MORNING || doneDay.getOrDefault(town.id, -1L) == day) {
			return;
		}

		doneDay.put(town.id, day);
		daily(server, town, group, day);
	}

	private static void daily(MinecraftServer server, Town town, DrifterGroups.Group group, long day) {
		DrifterEntity leader = town.leader == null ? null : TownFounding.loaded(server, town.leader);
		for (Relation r : of(town)) {
			Town other = byId(r.other(town.id));
			if (other == null) {
				continue;
			}

			// Once a day per pair (the one first by id does it): natures, pacts, land.
			if (r.a.equals(town.id)) {
				DrifterEntity theirs = other.leader == null ? null : TownFounding.loaded(server, other.leader);
				double warmth = (trait(leader, "sociability") + trait(leader, "forgiveness") + trait(theirs, "sociability") + trait(theirs, "forgiveness")) / 4.0
					- 0.5;
				double heat = (trait(leader, "aggression") + trait(theirs, "aggression")) / 2.0 - 0.5;
				double drift = 2.0 * warmth - 2.0 * heat + (r.pacts.contains(Pact.TRADE) ? 0.5 : 0.0) + (r.pacts.contains(Pact.PEACE) ? 0.5 : 0.0);
				if (!r.pacts.contains(Pact.PEACE) && town.center.closerThan(other.center, CLOSE)) {
					drift -= 0.5;
				}

				r.standing = Math.max(-100.0, Math.min(100.0, r.standing + drift));
			}

			// At peace: their people don't fight (their groups' tension held down).
			if ((r.pacts.contains(Pact.PEACE) || r.pacts.contains(Pact.ALLIANCE)) && town.group != null && other.group != null
				&& DrifterApi.tension(town.group, other.group) > PEACEFUL) {
				DrifterApi.setTension(server, town.group, other.group, PEACEFUL);
			}
		}

		// The leader's envoy, maybe.
		if (town.leader == null || missions.stream().anyMatch(m -> m.from.equals(town.id))) {
			return;
		}

		Relation best = null;
		String kind = null;
		double bestNeed = 0.0;
		for (Relation r : of(town)) {
			Town other = byId(r.other(town.id));
			if (other == null || other.leader == null || day - r.lastEnvoyDay < ENVOY_EVERY || !other.center.closerThan(town.center, FARTHEST)) {
				continue;
			}

			String want = wants(town, other, r, leader);
			if (want != null) {
				double need = Math.abs(r.standing) + 1.0;
				if (need > bestNeed) {
					bestNeed = need;
					best = r;
					kind = want;
				}
			}
		}

		// (Not every morning: a sociable leader sends envoys more.)
		if (best == null || server.overworld().getRandom().nextDouble() > 0.3 + 0.5 * trait(leader, "sociability")) {
			return;
		}

		send(server, town, group, byId(best.other(town.id)), kind);
	}

	/** What this town's leader would send the other, or null. */
	@Nullable
	private static String wants(Town town, Town other, Relation r, @Nullable DrifterEntity leader) {
		if (r.standing <= FOR_INSULT && trait(leader, "aggression") >= 0.6) {
			return "INSULT";
		}

		if (r.standing >= FOR_ALLIANCE && r.pacts.contains(Pact.PEACE) && !r.pacts.contains(Pact.ALLIANCE)) {
			return Pact.ALLIANCE.name();
		}

		if (r.standing >= FOR_PEACE && !r.pacts.contains(Pact.PEACE)) {
			return Pact.PEACE.name();
		}

		if (r.standing >= FOR_TRADE && !r.pacts.contains(Pact.TRADE) && town.stallBuilt && other.stallBuilt) {
			return Pact.TRADE.name();
		}

		return null;
	}

	/** Its envoy off to the other town with this: walking it if one's about to go, else gone as long as the walk takes. */
	static boolean send(MinecraftServer server, Town town, DrifterGroups.Group group, Town other, String kind) {
		Relation r = meet(server, town, other, 0.0, null);
		DrifterEntity envoy = envoy(server, town, group);
		UUID envoyId;
		String envoyName;
		if (envoy != null) {
			envoyId = envoy.getUUID();
			envoyName = envoy.getName().getString();
		} else {
			// (Out of sight: whoever it'd be, by name.)
			envoyId = group.members.stream().filter(id -> !id.equals(town.leader)).findFirst().orElse(null);
			if (envoyId == null) {
				return false;
			}

			envoyName = group.names.getOrDefault(envoyId, "an envoy");
		}

		long now = server.overworld().getGameTime();
		long walk = (long)(Math.sqrt(town.center.distSqr(other.center)) * TICKS_A_BLOCK);
		Mission mission = new Mission(town.id, other.id, kind, envoyId, envoyName, now, now + walk, now + 2 * walk);
		mission.walking = envoy != null;
		missions.add(mission);
		r.lastEnvoyDay = Chronicle.day(server);
		String what = kind.equals("INSULT") ? "to tell its leader what " + town.name + " thinks of them" : "to offer " + Pact.valueOf(kind).words;
		Chronicle.add(server, "diplomacy", envoyName + " set out from " + town.name + " for " + other.name + " " + what);
		if (envoy != null) {
			DrifterApi.think(envoy, "is to go to " + other.name + " " + what);
			DrifterApi.setErrand(envoy, errand(server, town, other, mission, envoy));
		}

		Towns.save(server);
		return true;
	}

	/** Its most sociable person free to go (not its leader, keeper or crook; not on a trade run), loaded and well. */
	@Nullable
	private static DrifterEntity envoy(MinecraftServer server, Town town, DrifterGroups.Group group) {
		DrifterEntity best = null;
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member == null || id.equals(town.leader) || id.equals(town.keeper) || town.crooks.contains(id) || DrifterApi.tradeRun(member) != null
				|| DrifterApi.errand(member) != null || member.getHealth() < member.getMaxHealth() * 0.7F) {
				continue;
			}

			if (best == null || DrifterApi.trait(member, "sociability") > DrifterApi.trait(best, "sociability")) {
				best = member;
			}
		}

		return best;
	}

	private static Errand errand(MinecraftServer server, Town town, Town other, Mission mission, DrifterEntity envoy) {
		String leaderName = name(town, town.leader);
		String message = line(envoy, "envoy." + mission.kind.toLowerCase(java.util.Locale.ROOT), "leader", leaderName, "town", town.name, "them", other.name);
		return new Errand() {
			@Override
			public BlockPos there() {
				return other.center;
			}

			@Override
			public String thereName() {
				return other.name;
			}

			@Override
			public BlockPos home() {
				return town.center;
			}

			@Nullable
			@Override
			public UUID meet() {
				return other.leader;
			}

			@Override
			public String message() {
				return message;
			}

			@Nullable
			@Override
			public BlockPos meetAt() {
				// (Their leader's house: the middle of its plot.)
				for (Town.Plot plot : other.plots) {
					if (other.leader != null && (other.leader.equals(plot.owner) || plot.residents.contains(other.leader))) {
						return plot.origin.offset(plot.sizeX / 2, 0, plot.sizeZ / 2);
					}
				}

				return null;
			}

			@Override
			public void arrived(DrifterEntity envoy, @Nullable DrifterEntity met) {
				if (!mission.answered) {
					answer(server, mission, met);
				}
			}

			@Override
			public void back(DrifterEntity envoy) {
				Diplomacy.home(server, mission);
			}

			@Override
			public boolean told() {
				return mission.answered;
			}

			@Override
			public void gaveUp(DrifterEntity envoy, String why) {
				// (It goes on out of sight, as long as the walk would take.)
				mission.walking = false;
			}
		};
	}

	/** At the other town: their leader's answer (said by whoever the envoy met, if anyone). */
	static void answer(MinecraftServer server, Mission mission, @Nullable DrifterEntity met) {
		mission.answered = true;
		Town from = byId(mission.from.toString());
		Town to = byId(mission.to.toString());
		if (from == null || to == null) {
			return;
		}

		Relation r = meet(server, from, to, 0.0, null);
		DrifterEntity theirLeader = to.leader == null ? null : TownFounding.loaded(server, to.leader);
		String fromLeader = name(from, from.leader);
		if (mission.kind.equals("INSULT")) {
			r.insults++;
			incident(server, from, to, -12.0, "insult");
			if (met != null) {
				com.dinglebob.drifter.social.ChatLines.speak(met, line(met, "envoy.insulted", "leader", fromLeader, "town", from.name, "them", to.name));
			}

			Chronicle.add(server, "diplomacy", mission.envoyName + " of " + from.name + " insulted " + to.name + "'s leader " + name(to, to.leader)
				+ " to their face");
			return;
		}

		Pact pact = Pact.valueOf(mission.kind);
		double lean = switch (pact) {
			case TRADE -> 0.3 + 0.3 * (trait(theirLeader, "greed") - 0.5);
			case PEACE -> 0.35 + 0.3 * (trait(theirLeader, "caution") - 0.5) - 0.3 * (trait(theirLeader, "aggression") - 0.5);
			case ALLIANCE -> -0.2 + 0.3 * (trait(theirLeader, "sociability") - 0.5);
		};
		boolean yes = server.overworld().getRandom().nextDouble() < lean + r.standing / 100.0;
		if (yes) {
			r.pacts.add(pact);
			if (pact == Pact.ALLIANCE) {
				r.pacts.add(Pact.PEACE);
			}

			incident(server, from, to, 8.0, pact.name().toLowerCase(java.util.Locale.ROOT));
			Chronicle.add(server, "diplomacy", to.name + " agreed to " + pact.words + " with " + from.name);
		} else {
			incident(server, from, to, -4.0, "refused");
			Chronicle.add(server, "diplomacy", to.name + " turned down " + from.name + "'s offer of " + pact.words);
		}

		if (met != null) {
			com.dinglebob.drifter.social.ChatLines.speak(met, line(met, yes ? "envoy.yes" : "envoy.no", "leader", fromLeader, "town", from.name, "them", to.name));
		}
	}

	/** The envoy home with the answer. */
	static void home(MinecraftServer server, Mission mission) {
		missions.remove(mission);
		Town from = byId(mission.from.toString());
		Town to = byId(mission.to.toString());
		if (from != null && to != null) {
			Chronicle.add(server, "diplomacy", mission.envoyName + " came back to " + from.name + " from " + to.name);
		}

		Towns.save(server);
	}

	// ---------------------------------------------------------------------------------------------
	// For the rest of the addon
	// ---------------------------------------------------------------------------------------------

	/**
	 * What its people know of its town's friends and foes to talk about: its ally ({@code ally_town}, word {@code ally}), the
	 * town it's on worst terms with ({@code rival_town}, {@code rival}), and the latest between it and another lately
	 * ({@code town_trade}, {@code town_peace}, {@code town_alliance}, {@code town_refused}, {@code town_insult},
	 * {@code town_killing}; word {@code other}).
	 */
	static void talk(Town town, long day, Map<String, Object> words, java.util.Set<String> flags) {
		Relation friend = null;
		Relation foe = null;
		Relation latest = null;
		for (Relation r : of(town)) {
			if (r.pacts.contains(Pact.ALLIANCE) && (friend == null || r.standing > friend.standing)) {
				friend = r;
			}

			if (r.standing <= FOR_INSULT && (foe == null || r.standing < foe.standing)) {
				foe = r;
			}

			if (!r.news.isEmpty() && day - r.newsDay <= NEWS_DAYS && (latest == null || r.newsDay > latest.newsDay)) {
				latest = r;
			}
		}

		if (friend != null && byId(friend.other(town.id)) != null) {
			flags.add("ally_town");
			words.put("ally", byId(friend.other(town.id)).name);
		}

		if (foe != null && byId(foe.other(town.id)) != null) {
			flags.add("rival_town");
			words.put("rival", byId(foe.other(town.id)).name);
		}

		if (latest != null && byId(latest.other(town.id)) != null) {
			flags.add("town_" + latest.news);
			words.put("other", byId(latest.other(town.id)).name);
		}
	}

	/** Its trade partners' towns (a trade deal), nearest first. */
	static List<Town> partners(Town town) {
		List<Town> list = new ArrayList<>();
		for (Relation r : of(town)) {
			Town other = byId(r.other(town.id));
			if (other != null && r.pacts.contains(Pact.TRADE)) {
				list.add(other);
			}
		}

		list.sort(java.util.Comparator.comparingDouble(other -> other.center.distSqr(town.center)));
		return list;
	}

	/** The town (made ready or founded) whose group this Drifter is in, or null. */
	@Nullable
	static Town townOf(UUID drifter) {
		DrifterGroups.Group group = DrifterApi.group(drifter);
		if (group == null) {
			return null;
		}

		for (Town town : Towns.all()) {
			if (group.id.equals(town.group)) {
				return town;
			}
		}

		return null;
	}

	@Nullable
	static Town byId(String id) {
		for (Town town : Towns.all()) {
			if (town.id.toString().equals(id)) {
				return town;
			}
		}

		return null;
	}

	@Nullable
	private static Town byId(UUID id) {
		return byId(id.toString());
	}

	/** A town's leader by name (its group's names). */
	static String name(Town town, @Nullable UUID id) {
		if (id == null) {
			return "its leader";
		}

		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		return group == null ? "its leader" : group.names.getOrDefault(id, "its leader");
	}

	/** A trait of one loaded, or the middle when it isn't (out of sight, its nature isn't to hand). */
	private static double trait(@Nullable DrifterEntity drifter, String trait) {
		return drifter == null ? 0.5 : DrifterApi.trait(drifter, trait);
	}

	private static String line(DrifterEntity who, String key, Object... vars) {
		String line = com.dinglebob.drifter.social.Lines.pick(who, key, vars);
		return line == null ? "" : line;
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the towns
	// ---------------------------------------------------------------------------------------------

	static void save(CompoundTag tag) {
		ListTag list = new ListTag();
		for (Relation r : relations.values()) {
			CompoundTag one = new CompoundTag();
			one.putUUID("A", r.a);
			one.putUUID("B", r.b);
			one.putDouble("Standing", r.standing);
			ListTag pacts = new ListTag();
			r.pacts.forEach(p -> pacts.add(net.minecraft.nbt.StringTag.valueOf(p.name())));
			one.put("Pacts", pacts);
			one.putLong("MetDay", r.metDay);
			one.putLong("LastEnvoyDay", r.lastEnvoyDay);
			one.putInt("Insults", r.insults);
			one.putString("News", r.news);
			one.putLong("NewsDay", r.newsDay);
			list.add(one);
		}

		tag.put("Relations", list);
		ListTag out = new ListTag();
		for (Mission m : missions) {
			CompoundTag one = new CompoundTag();
			one.putUUID("From", m.from);
			one.putUUID("To", m.to);
			one.putString("Kind", m.kind);
			one.putUUID("Envoy", m.envoy);
			one.putString("EnvoyName", m.envoyName);
			one.putLong("SentAt", m.sentAt);
			one.putLong("ArriveAt", m.arriveAt);
			one.putLong("BackAt", m.backAt);
			one.putBoolean("Answered", m.answered);
			out.add(one);
		}

		tag.put("Missions", out);
	}

	static void load(CompoundTag tag) {
		relations.clear();
		missions.clear();
		doneDay.clear();
		for (Tag t : tag.getList("Relations", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag)t;
			Relation r = new Relation(one.getUUID("A"), one.getUUID("B"));
			r.standing = one.getDouble("Standing");
			for (Tag p : one.getList("Pacts", Tag.TAG_STRING)) {
				try {
					r.pacts.add(Pact.valueOf(p.getAsString()));
				} catch (IllegalArgumentException ignored) {
				}
			}

			r.metDay = one.getLong("MetDay");
			r.lastEnvoyDay = one.getLong("LastEnvoyDay");
			r.insults = one.getInt("Insults");
			r.news = one.getString("News");
			r.newsDay = one.getLong("NewsDay");
			relations.put(key(r.a, r.b), r);
		}

		for (Tag t : tag.getList("Missions", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag)t;
			// (Loaded from a save: out of sight from here on.)
			Mission m = new Mission(one.getUUID("From"), one.getUUID("To"), one.getString("Kind"), one.getUUID("Envoy"), one.getString("EnvoyName"),
				one.getLong("SentAt"), one.getLong("ArriveAt"), one.getLong("BackAt"));
			m.answered = one.getBoolean("Answered");
			missions.add(m);
		}
	}
}
