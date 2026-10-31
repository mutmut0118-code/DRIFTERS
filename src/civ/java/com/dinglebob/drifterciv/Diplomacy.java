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
		/** At war (part 2): since when (game day), who started it, each side's dead. */
		boolean war;
		long warSinceDay = -1;
		@Nullable
		UUID startedBy;
		int lossesA;
		int lossesB;
		/** No war again before this day (a peace made). */
		long truceUntilDay = -100;
		/** Allied since this day (nations, part 3: allied a while before joining). */
		long alliedSinceDay = -100;
		/** Emeralds one owes the other by a peace (reparations, or what was demanded), and those taken from its till on the
		 * way to the other's. */
		@Nullable
		UUID owedBy;
		int owed;
		int inTransit;

		Relation(UUID a, UUID b) {
			this.a = a;
			this.b = b;
		}

		UUID other(UUID town) {
			return town.equals(this.a) ? this.b : this.a;
		}

		int losses(UUID town) {
			return town.equals(this.a) ? this.lossesA : this.lossesB;
		}

		void lost(UUID town) {
			if (town.equals(this.a)) {
				this.lossesA++;
			} else {
				this.lossesB++;
			}
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
		/** Peace terms: the emeralds offered (TERMS: paid by its town) or demanded (DEMAND: paid to it); 0, a white peace. */
		int amount;

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

	/**
	 * War (part 2): the standing at which an aggressive leader (0.55) declares it, or a vengeful one (0.6) after a killing
	 * at -40; how weary a side must be to offer peace; the truce after a peace (days).
	 */
	private static final double FOR_WAR = -60.0;
	private static final double FOR_REVENGE = -40.0;
	private static final double WEARY = 40.0;
	private static final int TRUCE_DAYS = 7;
	/** Who goes back on a peace or an alliance by declaring war: every town that knows of it thinks this much less of it. */
	private static final double BROKEN_WORD = -10.0;
	/** Towns that broke their word (a peace, an alliance), how often: they're trusted less with pacts. Saved. */
	private static final Map<UUID, Integer> brokenWord = new java.util.HashMap<>();

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
		// A town's people killed by the other's in a war: its losses, and the chronicle.
		DrifterEvents.DIED.register((drifter, source, comesBack) -> {
			MinecraftServer server = drifter.getServer();
			DrifterEntity killer = source.getEntity() instanceof DrifterEntity d ? d : com.dinglebob.drifter.hands.FakePlayerBridge.ownerOf(source.getEntity());
			// (A thrown potion, an arrow's owner gone: whoever gets the kill.)
			if (killer == null) {
				var credit = drifter.getKillCredit();
				killer = credit instanceof DrifterEntity d ? d : com.dinglebob.drifter.hands.FakePlayerBridge.ownerOf(credit);
			}

			// (Gone for good, it's left its group by now: the one it was in.)
			Town mine = server == null || killer == null ? null : townOf(drifter.getUUID());
			if (mine == null && killer != null && drifter.groupAtDeath() != null) {
				mine = townOfGroup(drifter.groupAtDeath());
			}

			Town theirs = mine == null ? null : townOf(killer.getUUID());
			Relation r = theirs == null || theirs == mine ? null : relation(mine, theirs);
			if (r != null && r.war) {
				r.lost(mine.id);
				Nations.fell(server, mine);
				r.news = "fell";
				r.newsDay = Chronicle.day(server);
				Chronicle.add(server, "war", drifter.getName().getString() + " of " + mine.name + " fell in the war with " + theirs.name + " (killed by "
					+ killer.getName().getString() + ")");
				Towns.save(server);
			}
		});
		// A town's raiders gather at its own plaza (the user, 2026-10-29: "if everyone grouped up at their own town instead of a
		// spot behind a building in the enemies town").
		DrifterApi.setRaidMusters(group -> {
			Town town = townOfGroup(group);
			return town == null ? null : town.center;
		});
		// A town's raids go for the town they're at war with: its store (its till), which everyone round about knows of.
		DrifterApi.setRaidTargets((attackers, defenders) -> {
			Town from = townOfGroup(attackers);
			Town to = townOfGroup(defenders);
			Relation r = from == null || to == null ? null : relation(from, to);
			return r == null || !r.war ? null : to.till() != null ? to.till() : to.center;
		});
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

		// (Its wars are the town's to make and end: no truce of their own.)
		DrifterApi.setWarManaged(group.id, true);
		pay(server, town);
		Nations.check(server, town);
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

			// At war: kept at war (a raid, fighting on sight) till a peace is made.
			if (r.war && town.group != null && other.group != null && DrifterApi.tension(town.group, other.group) < 70.0) {
				DrifterApi.declareWar(server, town.group, other.group);
			}

			// At peace: their people don't fight (their groups' tension held down).
			if (!r.war && (r.pacts.contains(Pact.PEACE) || r.pacts.contains(Pact.ALLIANCE)) && town.group != null && other.group != null
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

			String want = wants(server, town, other, r, leader, day);
			if (want != null) {
				// (War and peace first.)
				double need = Math.abs(r.standing) + 1.0 + (r.war || want.equals("WAR") || want.equals("INDEPENDENCE") ? 200.0 : 0.0);
				if (need > bestNeed) {
					bestNeed = need;
					best = r;
					kind = want;
				}
			}
		}

		// (Not every morning: a sociable leader sends envoys more; war and peace don't wait on that.)
		if (best == null || !best.war && !"WAR".equals(kind) && !"INDEPENDENCE".equals(kind)
			&& server.overworld().getRandom().nextDouble() > 0.3 + 0.5 * trait(leader, "sociability")) {
			return;
		}

		send(server, town, group, byId(best.other(town.id)), kind);
	}

	/** What this town's leader would send the other, or null. */
	@Nullable
	private static String wants(MinecraftServer server, Town town, Town other, Relation r, @Nullable DrifterEntity leader, long day) {
		// Its nation first (part 3): joining, a conqueror's demand, a town wanting out.
		String nation = Nations.wants(server, town, other, r, leader, day);
		if (nation != null) {
			return nation;
		}

		// (Two of one nation: no insults, no wars, no pacts to offer: they have them.)
		boolean fellows = Nations.fellows(town, other);
		if (fellows && !r.war) {
			return null;
		}

		// At war: peace, when it's had enough (terms), or (winning, and greedy) on its terms (a demand).
		if (r.war) {
			if (weariness(town, r, leader, day) >= WEARY) {
				return "TERMS";
			}

			int mine = r.losses(town.id);
			int theirs = r.losses(other.id);
			return theirs >= mine + 2 && day - r.warSinceDay >= 2 && trait(leader, "greed") >= 0.6 ? "DEMAND" : null;
		}

		// War: on terms that bad and an aggressive leader, or a vengeful one after a killing. (In a nation, only its capital
		// starts one.)
		Nations.Nation mine = Nations.of(town);
		if ((mine == null || mine.capital.equals(town.id)) && day >= r.truceUntilDay && (r.standing <= FOR_WAR && trait(leader, "aggression") >= 0.55
			|| r.news.equals("killing") && day - r.newsDay <= 3 && r.standing <= FOR_REVENGE && trait(leader, "vengefulness") >= 0.6)) {
			return "WAR";
		}

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
		// Peace terms: reparations by the side that's lost more, else a white peace; a demand by the winner.
		int mine = r.losses(town.id);
		int theirs = r.losses(other.id);
		mission.amount = kind.equals("TERMS") ? (mine > theirs ? Math.min(24, 4 + 3 * (mine - theirs)) : 0)
			: kind.equals("DEMAND") ? Math.min(30, 6 + 3 * Math.max(0, theirs - mine)) : 0;
		missions.add(mission);
		r.lastEnvoyDay = Chronicle.day(server);
		String what = switch (kind) {
			case "INSULT" -> "to tell its leader what " + town.name + " thinks of them";
			case "WAR" -> "to declare war";
			case "TERMS" -> mission.amount > 0 ? "to offer " + mission.amount + " emeralds for peace" : "to offer peace";
			case "DEMAND" -> "to demand " + mission.amount + " emeralds for peace";
			case "UNION" -> "to propose they join " + (Nations.of(town) != null ? Nations.of(town).name : "together under " + town.name);
			case "SUBMIT" -> "to demand it submit to " + town.name;
			case "INDEPENDENCE" -> "to tell " + other.name + " it's free of it";
			default -> "to offer " + Pact.valueOf(kind).words;
		};
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
		String key = mission.kind.equals("TERMS") && mission.amount == 0 ? "envoy.terms_white" : "envoy." + mission.kind.toLowerCase(java.util.Locale.ROOT);
		String message = line(envoy, key, "leader", leaderName, "town", town.name, "them", other.name, "amount", mission.amount);
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
		long day = Chronicle.day(server);
		if (mission.kind.equals("WAR")) {
			if (!r.war) {
				war(server, from, to, r);
			}

			if (met != null) {
				com.dinglebob.drifter.social.ChatLines.speak(met, line(met, "envoy.war_reply", "leader", fromLeader, "town", from.name, "them", to.name));
			}

			return;
		}

		if (Nations.answer(server, mission, from, to, r, met)) {
			Towns.save(server);
			return;
		}

		if (mission.kind.equals("TERMS") || mission.kind.equals("DEMAND")) {
			if (!r.war) {
				return;
			}

			// Their leader: weary of it, careful, or paid, it takes it; aggressive, it fights on.
			boolean offer = mission.kind.equals("TERMS");
			double lean = offer ? 0.35 + 0.3 * trait(theirLeader, "caution") - 0.3 * trait(theirLeader, "aggression") + (mission.amount > 0 ? 0.25 : 0.0)
				: -0.15 + 0.2 * trait(theirLeader, "caution") - 0.2 * trait(theirLeader, "aggression");
			boolean yes = server.overworld().getRandom().nextDouble() < lean + weariness(to, r, theirLeader, day) / 100.0;
			if (yes) {
				// Who pays whom: the offering side its reparations; the demanded-of side what was demanded.
				peace(server, from, to, r, offer ? from : to, mission.amount);
			} else {
				Chronicle.add(server, "war", to.name + " turned down " + from.name + "'s " + (offer ? "offer of peace" : "demand") + ": the war goes on");
			}

			if (met != null) {
				com.dinglebob.drifter.social.ChatLines.speak(met, line(met, yes ? "envoy.peace_yes" : "envoy.peace_no", "leader", fromLeader, "town", from.name,
					"them", to.name, "amount", mission.amount));
			}

			return;
		}

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
		// (Theirs already: a second envoy with the same offer; nothing new.)
		if (r.pacts.contains(pact) || r.war) {
			return;
		}

		double lean = switch (pact) {
			case TRADE -> 0.3 + 0.3 * (trait(theirLeader, "greed") - 0.5);
			case PEACE -> 0.35 + 0.3 * (trait(theirLeader, "caution") - 0.5) - 0.3 * (trait(theirLeader, "aggression") - 0.5);
			case ALLIANCE -> -0.2 + 0.3 * (trait(theirLeader, "sociability") - 0.5);
		};
		// (A town known to have broken its word is trusted less with a pact.)
		lean -= 0.15 * brokenWord.getOrDefault(from.id, 0);
		boolean yes = server.overworld().getRandom().nextDouble() < lean + r.standing / 100.0;
		if (yes) {
			r.pacts.add(pact);
			if (pact == Pact.ALLIANCE) {
				r.pacts.add(Pact.PEACE);
				r.alliedSinceDay = day;
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
	// War and peace (part 2)
	// ---------------------------------------------------------------------------------------------

	/**
	 * How tired a town is of its war with the other: the days, its dead (less the other's), and its leader's nature
	 * (careful, more; aggressive or vengeful, less).
	 */
	static double weariness(Town town, Relation r, @Nullable DrifterEntity leader, long day) {
		if (!r.war) {
			return 0.0;
		}

		Town other = byId(r.other(town.id).toString());
		int theirs = other == null ? 0 : r.losses(other.id);
		return 6.0 * Math.max(0, day - r.warSinceDay) + 15.0 * r.losses(town.id) - 5.0 * theirs + 30.0 * (trait(leader, "caution") - 0.5)
			- 30.0 * (trait(leader, "aggression") - 0.5) - 10.0 * trait(leader, "vengefulness");
	}

	/** {@code attacker} declares war on {@code defender}: its word broken if they were at peace; their allies called in. */
	static void war(MinecraftServer server, Town attacker, Town defender, Relation r) {
		long day = Chronicle.day(server);
		boolean brokePeace = r.pacts.contains(Pact.PEACE) || r.pacts.contains(Pact.ALLIANCE);
		Chronicle.add(server, "war", attacker.name + " declared war on " + defender.name + (brokePeace ? ", breaking its word" : ""));
		begin(server, attacker, defender, r, day, "war");
		if (brokePeace) {
			brokenWord.merge(attacker.id, 1, Integer::sum);
			for (Relation other : of(attacker)) {
				Town third = byId(other.other(attacker.id).toString());
				if (third != null && third != defender) {
					incident(server, attacker, third, BROKEN_WORD, "broken_word");
				}
			}
		}

		// Their nations' towns (part 3): in it, no choice.
		List<Town> in = Nations.war(server, attacker, defender, day);
		if (Nations.fellows(attacker, defender)) {
			Towns.save(server);
			return;
		}

		// Their allies: the defender's stand by it unless their leader's too careful (then they're resented for it); the
		// attacker's join if theirs is spoiling for it.
		for (Relation ally : of(defender)) {
			Town friend = byId(ally.other(defender.id).toString());
			if (friend == null || friend == attacker || in.contains(friend) || !ally.pacts.contains(Pact.ALLIANCE)) {
				continue;
			}

			DrifterEntity theirLeader = friend.leader == null ? null : TownFounding.loaded(server, friend.leader);
			if (trait(theirLeader, "caution") < 0.7) {
				Relation against = meet(server, friend, attacker, 0.0, null);
				if (!against.war) {
					Chronicle.add(server, "war", friend.name + " stood by its ally " + defender.name + ": at war with " + attacker.name);
					begin(server, friend, attacker, against, day, "war");
				}
			} else {
				Chronicle.add(server, "war", friend.name + " stayed out of " + defender.name + "'s war");
				incident(server, friend, defender, -15.0, "let_down");
			}
		}

		for (Relation ally : of(attacker)) {
			Town friend = byId(ally.other(attacker.id).toString());
			if (friend == null || friend == defender || in.contains(friend) || !ally.pacts.contains(Pact.ALLIANCE)) {
				continue;
			}

			DrifterEntity theirLeader = friend.leader == null ? null : TownFounding.loaded(server, friend.leader);
			if (trait(theirLeader, "aggression") >= 0.5) {
				Relation against = meet(server, friend, defender, 0.0, null);
				if (!against.war) {
					Chronicle.add(server, "war", friend.name + " joined its ally " + attacker.name + "'s war on " + defender.name);
					begin(server, friend, defender, against, day, "war");
				}
			}
		}

		Towns.save(server);
	}

	static void begin(MinecraftServer server, Town x, Town y, Relation r, long day, String news) {
		r.pacts.clear();
		r.war = true;
		r.warSinceDay = day;
		r.startedBy = x.id;
		r.lossesA = 0;
		r.lossesB = 0;
		r.standing = Math.min(r.standing, FOR_WAR);
		r.news = news;
		r.newsDay = day;
		if (x.group != null && y.group != null) {
			DrifterApi.setWarManaged(x.group, true);
			DrifterApi.setWarManaged(y.group, true);
			DrifterApi.declareWar(server, x.group, y.group);
		}
	}

	/** The war over: a peace pact, a truce, and {@code payer} owing the other this much (0: a white peace). */
	static void peace(MinecraftServer server, Town x, Town y, Relation r, @Nullable Town payer, int amount) {
		long day = Chronicle.day(server);
		// (The side that didn't pay won: a monument to it, C6.)
		if (payer != null) {
			TownCulture.won(payer == x ? y : x, payer);
		}

		r.war = false;
		r.truceUntilDay = day + TRUCE_DAYS;
		r.pacts.add(Pact.PEACE);
		r.standing = Math.max(r.standing, -20.0);
		r.news = "peace_made";
		r.newsDay = day;
		if (payer != null && amount > 0) {
			r.owedBy = payer.id;
			r.owed += amount;
		}

		if (x.group != null && y.group != null) {
			DrifterApi.makePeace(server, x.group, y.group, TRUCE_DAYS);
		}

		Town other = payer == x ? y : x;
		Chronicle.add(server, "war", x.name + " and " + y.name + " made peace after " + Math.max(1, day - r.warSinceDay + 1) + " days"
			+ (payer != null && amount > 0 ? " (" + payer.name + " pays " + other.name + " " + amount + " emeralds)" : " (neither side paying)"));
		// (A rising: settled by who asked for it. Part 3.)
		Nations.peace(server, x, y, payer);
		Towns.save(server);
	}

	/**
	 * What its peaces cost it, paid from its till, and what it's owed, put in its till, when its till is loaded: real
	 * emeralds out of one and into the other, never made.
	 */
	private static void pay(MinecraftServer server, Town town) {
		for (Relation r : of(town)) {
			Town other = byId(r.other(town.id).toString());
			if (other == null || r.owedBy == null) {
				continue;
			}

			if (r.owedBy.equals(town.id) && r.owed > 0) {
				int taken = till(server, town, -r.owed);
				if (taken > 0) {
					r.owed -= taken;
					r.inTransit += taken;
					Chronicle.add(server, "war", town.name + " paid " + taken + " emeralds toward its peace with " + other.name
						+ (r.owed > 0 ? " (" + r.owed + " still owed)" : " (all paid)"));
					Towns.save(server);
				}
			} else if (!r.owedBy.equals(town.id) && r.inTransit > 0) {
				int put = till(server, town, r.inTransit);
				if (put > 0) {
					r.inTransit -= put;
					if (r.owed == 0 && r.inTransit == 0) {
						r.owedBy = null;
					}

					Towns.save(server);
				}
			}
		}
	}

	/** Emeralds into its till (count over 0) or out of it (under 0), as many as there are, or room for, if it's loaded. */
	static int till(MinecraftServer server, Town town, int count) {
		BlockPos till = town.till();
		var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
			net.minecraft.resources.ResourceLocation.parse(town.dimension)));
		if (till == null || level == null || !level.isLoaded(till) || !(level.getBlockEntity(till) instanceof net.minecraft.world.Container chest)) {
			return 0;
		}

		int done = 0;
		if (count < 0) {
			for (int i = 0; i < chest.getContainerSize() && done < -count; i++) {
				var stack = chest.getItem(i);
				if (stack.is(net.minecraft.world.item.Items.EMERALD)) {
					int take = Math.min(stack.getCount(), -count - done);
					stack.shrink(take);
					done += take;
				}
			}
		} else {
			for (int i = 0; i < chest.getContainerSize() && done < count; i++) {
				var stack = chest.getItem(i);
				if (stack.isEmpty()) {
					int put = Math.min(64, count - done);
					chest.setItem(i, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, put));
					done += put;
				} else if (stack.is(net.minecraft.world.item.Items.EMERALD) && stack.getCount() < 64) {
					int put = Math.min(64 - stack.getCount(), count - done);
					stack.grow(put);
					done += put;
				}
			}
		}

		if (done > 0) {
			chest.setChanged();
		}

		return done;
	}

	/** The town whose group this is, or null. */
	@Nullable
	static Town townOfGroup(UUID group) {
		for (Town town : Towns.all()) {
			if (group.equals(town.group)) {
				return town;
			}
		}

		return null;
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
		Nations.talk(town, day, words, flags);
		Relation friend = null;
		Relation foe = null;
		Relation latest = null;
		for (Relation r : of(town)) {
			if (r.war && byId(r.other(town.id).toString()) != null) {
				flags.add("at_war");
				words.put("enemy", byId(r.other(town.id).toString()).name);
			}

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
			one.putBoolean("War", r.war);
			one.putLong("WarSinceDay", r.warSinceDay);
			if (r.startedBy != null) {
				one.putUUID("StartedBy", r.startedBy);
			}

			one.putInt("LossesA", r.lossesA);
			one.putInt("LossesB", r.lossesB);
			one.putLong("TruceUntilDay", r.truceUntilDay);
			if (r.owedBy != null) {
				one.putUUID("OwedBy", r.owedBy);
			}

			one.putInt("Owed", r.owed);
			one.putInt("InTransit", r.inTransit);
			one.putLong("AlliedSinceDay", r.alliedSinceDay);
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
			one.putInt("Amount", m.amount);
			out.add(one);
		}

		tag.put("Missions", out);
		CompoundTag broken = new CompoundTag();
		brokenWord.forEach((id, n) -> broken.putInt(id.toString(), n));
		tag.put("BrokenWord", broken);
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
			r.war = one.getBoolean("War");
			r.warSinceDay = one.contains("WarSinceDay") ? one.getLong("WarSinceDay") : -1;
			r.startedBy = one.hasUUID("StartedBy") ? one.getUUID("StartedBy") : null;
			r.lossesA = one.getInt("LossesA");
			r.lossesB = one.getInt("LossesB");
			r.truceUntilDay = one.contains("TruceUntilDay") ? one.getLong("TruceUntilDay") : -100;
			r.owedBy = one.hasUUID("OwedBy") ? one.getUUID("OwedBy") : null;
			r.owed = one.getInt("Owed");
			r.inTransit = one.getInt("InTransit");
			r.alliedSinceDay = one.contains("AlliedSinceDay") ? one.getLong("AlliedSinceDay") : -100;
			relations.put(key(r.a, r.b), r);
		}

		brokenWord.clear();
		CompoundTag broken = tag.getCompound("BrokenWord");
		for (String key : broken.getAllKeys()) {
			try {
				brokenWord.put(UUID.fromString(key), broken.getInt(key));
			} catch (IllegalArgumentException ignored) {
			}
		}

		for (Tag t : tag.getList("Missions", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag)t;
			// (Loaded from a save: out of sight from here on.)
			Mission m = new Mission(one.getUUID("From"), one.getUUID("To"), one.getString("Kind"), one.getUUID("Envoy"), one.getString("EnvoyName"),
				one.getLong("SentAt"), one.getLong("ArriveAt"), one.getLong("BackAt"));
			m.answered = one.getBoolean("Answered");
			m.amount = one.getInt("Amount");
			missions.add(m);
		}
	}
}
