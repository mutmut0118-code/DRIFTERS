package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Drifter team wars (DESIGN.md section 21 "Drifter team wars", milestone 12 step 3): groups fall into feuds on their own.
 * Each pair of groups has a tension score that incidents raise (members hurting or killing each other, a raid, bases
 * close enough to fight over the same land) and quiet lowers. Low tension: they keep apart. Medium: members fight on
 * sight (skirmishes). High: war, and the leader plans raids on the rival base. A truce ends it after heavy losses on one
 * side or a long quiet stretch. Players who aren't in it are left alone. Kept with the world.
 */
public final class DrifterWars {
	/** Tension at which members fight on sight, and at which it's war (raids). */
	public static final double SKIRMISH = 30.0;
	public static final double WAR = 70.0;
	public static final double MAX = 100.0;
	/** Per point of damage one side's member does to the other's. */
	private static final double PER_DAMAGE = 1.5;
	private static final double PER_KILL = 35.0;
	private static final double PER_RAID = 15.0;
	/** Bases closer than this fight over the same land: tension creeps up each in-game hour. */
	private static final double TERRITORY = 96.0;
	private static final double TERRITORY_PER_HOUR = 1.0;
	/** Land alone takes it this far (prickly neighbours who skirmish when they meet); further takes real incidents. */
	private static final double TERRITORY_CAP = 35.0;
	/** After this long with no real incident, tension eases off by this much an hour. */
	private static final long QUIET_AFTER = 1000L * 6;
	private static final double QUIET_PER_HOUR = 0.5;
	private static final long HOUR = 1000L;
	/** Losses on one side that make it sue for peace, and a quiet stretch at war that ends it. */
	private static final int LOSSES_FOR_TRUCE = 2;
	private static final long QUIET_FOR_TRUCE = 24000L * 3;
	/** After a truce: no feud for this long, and tension starts low. */
	private static final long TRUCE = 24000L * 5;
	private static final double AFTER_TRUCE = 10.0;
	/** Days between raids by one group. */
	private static final long RAID_EVERY = 24000L * 2;

	/** The feud between two groups (a and b in id order). */
	public static final class Feud {
		public final UUID a;
		public final UUID b;
		public double tension;
		public long lastIncident;
		public long truceUntil = Long.MIN_VALUE / 2;
		public int lossesA;
		public int lossesB;
		public long warSince = -1;

		Feud(UUID a, UUID b) {
			this.a = a;
			this.b = b;
		}

		public UUID other(UUID group) {
			return group.equals(this.a) ? this.b : this.a;
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putUUID("A", this.a);
			tag.putUUID("B", this.b);
			tag.putDouble("Tension", this.tension);
			tag.putLong("LastIncident", this.lastIncident);
			tag.putLong("TruceUntil", this.truceUntil);
			tag.putInt("LossesA", this.lossesA);
			tag.putInt("LossesB", this.lossesB);
			tag.putLong("WarSince", this.warSince);
			return tag;
		}

		static Feud load(CompoundTag tag) {
			Feud feud = new Feud(tag.getUUID("A"), tag.getUUID("B"));
			feud.tension = tag.getDouble("Tension");
			feud.lastIncident = tag.getLong("LastIncident");
			feud.truceUntil = tag.getLong("TruceUntil");
			feud.lossesA = tag.getInt("LossesA");
			feud.lossesB = tag.getInt("LossesB");
			feud.warSince = tag.getLong("WarSince");
			return feud;
		}
	}

	/** Where a raid is in its course. */
	public enum Phase {
		/** Members come to the leader. */
		GATHER,
		/** On the way, together. */
		TRAVEL,
		/** At the rival base: fighting whoever's there, looting the chests. */
		ATTACK,
		/** Back home. */
		RETREAT
	}

	/** A raid under way (not saved: one cut short by a restart is simply over). */
	public static final class Raid {
		public final UUID attackers;
		public final UUID defenders;
		public final BlockPos target;
		public final BlockPos home;
		public final long startedAt;
		public Phase phase = Phase.GATHER;
		public long phaseSince;
		public boolean warCry;
		public int looted;

		Raid(UUID attackers, UUID defenders, BlockPos target, BlockPos home, long now) {
			this.attackers = attackers;
			this.defenders = defenders;
			this.target = target;
			this.home = home;
			this.startedAt = now;
			this.phaseSince = now;
		}

		public void enter(Phase phase, long now) {
			this.phase = phase;
			this.phaseSince = now;
		}
	}

	/** How long each part of a raid may take at most. */
	public static final long GATHER_TICKS = 20 * 60;
	public static final long TRAVEL_TICKS = 20 * 60 * 6;
	public static final long ATTACK_TICKS = 20 * 60 * 3;
	public static final long RETREAT_TICKS = 20 * 60 * 6;

	private static final Map<String, Feud> feuds = new HashMap<>();
	private static final Map<UUID, Raid> raids = new HashMap<>();
	private static final Map<UUID, Long> lastRaid = new HashMap<>();
	private static boolean dirty;

	private DrifterWars() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(DrifterWars::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(DrifterWars::save);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			feuds.clear();
			raids.clear();
			lastRaid.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				tick(server);
			}

			if (dirty && server.getTickCount() % 200 == 0) {
				save(server);
			}
		});
	}

	private static String key(UUID a, UUID b) {
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	@Nullable
	public static Feud feud(UUID a, UUID b) {
		return feuds.get(key(a, b));
	}

	private static Feud feudOrNew(UUID a, UUID b) {
		return feuds.computeIfAbsent(key(a, b), k -> a.compareTo(b) < 0 ? new Feud(a, b) : new Feud(b, a));
	}

	public static List<Feud> all() {
		return new ArrayList<>(feuds.values());
	}

	public static double tension(UUID a, UUID b) {
		Feud feud = feud(a, b);
		return feud == null ? 0.0 : feud.tension;
	}

	/** Their two groups are skirmishing or at war (members fight on sight); false for the same group or no groups. */
	public static boolean hostile(DrifterEntity one, DrifterEntity other) {
		if (!DrifterConfig.drifterWars()) {
			return false;
		}

		DrifterGroups.Group mine = DrifterGroups.of(one.getUUID());
		DrifterGroups.Group theirs = DrifterGroups.of(other.getUUID());
		return mine != null && theirs != null && mine != theirs && tension(mine.id, theirs.id) >= SKIRMISH
			&& one.level().getGameTime() >= feudOrNew(mine.id, theirs.id).truceUntil;
	}

	@Nullable
	public static Raid raidOf(UUID group) {
		return raids.get(group);
	}

	/** A raid on this group under way, if any. */
	@Nullable
	public static Raid raidOn(UUID group) {
		for (Raid raid : raids.values()) {
			if (raid.defenders.equals(group)) {
				return raid;
			}
		}

		return null;
	}

	// ---------------------------------------------------------------------------------------------
	// Incidents
	// ---------------------------------------------------------------------------------------------

	/** Something between the two groups raised the tension. */
	public static void incident(UUID a, UUID b, double amount, long now, String what) {
		if (!DrifterConfig.drifterWars() || a.equals(b)) {
			return;
		}

		Feud feud = feudOrNew(a, b);
		if (now < feud.truceUntil) {
			return;
		}

		double before = feud.tension;
		feud.tension = Math.min(MAX, feud.tension + amount);
		feud.lastIncident = now;
		dirty = true;
		if (before < WAR && feud.tension >= WAR) {
			feud.warSince = now;
		}

		if (crossed(before, feud.tension)) {
			Drifter.LOGGER.info("[Drifter] {} and {}: {} ({}, tension {})", name(feud.a), name(feud.b), levelName(feud.tension), what,
				Math.round(feud.tension));
		}
	}

	/** Bases close enough to fight over the same trees and ore: a little tension, up to {@link #TERRITORY_CAP}. */
	private static void territory(UUID a, UUID b, long now) {
		if (!DrifterConfig.drifterWars()) {
			return;
		}

		Feud feud = feudOrNew(a, b);
		if (now < feud.truceUntil || feud.tension >= TERRITORY_CAP) {
			return;
		}

		double before = feud.tension;
		feud.tension = Math.min(TERRITORY_CAP, feud.tension + TERRITORY_PER_HOUR);
		dirty = true;
		if (crossed(before, feud.tension)) {
			Drifter.LOGGER.info("[Drifter] {} and {}: {} (their bases are too close)", name(feud.a), name(feud.b), levelName(feud.tension));
		}
	}

	private static boolean crossed(double before, double after) {
		return before < SKIRMISH && after >= SKIRMISH || before < WAR && after >= WAR;
	}

	public static String levelName(double tension) {
		return tension >= WAR ? "at war" : tension >= SKIRMISH ? "skirmishing" : tension >= 10 ? "wary of each other" : "at peace";
	}

	/** One side's member hurt the other's. */
	public static void hurt(DrifterEntity attacker, DrifterEntity victim, float amount) {
		DrifterGroups.Group from = DrifterGroups.of(attacker.getUUID());
		DrifterGroups.Group to = DrifterGroups.of(victim.getUUID());
		if (from != null && to != null && from != to) {
			incident(from.id, to.id, PER_DAMAGE * amount, victim.level().getGameTime(), attacker.getName().getString() + " hit " + victim.getName().getString());
		}
	}

	/** One side's member killed the other's: a big step, and a loss for that side. */
	public static void killed(DrifterEntity killer, DrifterEntity victim) {
		DrifterGroups.Group from = DrifterGroups.of(killer.getUUID());
		DrifterGroups.Group to = DrifterGroups.of(victim.getUUID());
		if (from == null || to == null || from == to) {
			return;
		}

		long now = victim.level().getGameTime();
		incident(from.id, to.id, PER_KILL, now, killer.getName().getString() + " killed " + victim.getName().getString());
		Feud feud = feudOrNew(from.id, to.id);
		if (to.id.equals(feud.a)) {
			feud.lossesA++;
		} else {
			feud.lossesB++;
		}

		dirty = true;
	}

	// ---------------------------------------------------------------------------------------------
	// Over time: territory, quiet, truces, raids
	// ---------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!DrifterConfig.drifterWars()) {
			raids.clear();
			return;
		}

		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		updateBases(server);
		// A group that's gone (its members died or parted): its feuds are over.
		if (feuds.values().removeIf(feud -> DrifterGroups.byId(feud.a) == null || DrifterGroups.byId(feud.b) == null)) {
			dirty = true;
		}

		// Once an in-game hour: land fought over, and quiet.
		if (now % HOUR < 20) {
			List<DrifterGroups.Group> groups = DrifterGroups.all();
			for (int i = 0; i < groups.size(); i++) {
				for (int j = i + 1; j < groups.size(); j++) {
					DrifterGroups.Group a = groups.get(i);
					DrifterGroups.Group b = groups.get(j);
					if (a.base != null && b.base != null && a.baseDimension.equals(b.baseDimension) && a.base.closerThan(b.base, TERRITORY)) {
						territory(a.id, b.id, now);
					}
				}
			}

			for (Feud feud : feuds.values()) {
				if (now - feud.lastIncident > QUIET_AFTER && feud.tension > 0) {
					feud.tension = Math.max(0.0, feud.tension - QUIET_PER_HOUR);
					dirty = true;
				}
			}
		}

		// Truces.
		for (Feud feud : feuds.values()) {
			boolean war = feud.tension >= WAR;
			if (feud.lossesA >= LOSSES_FOR_TRUCE || feud.lossesB >= LOSSES_FOR_TRUCE || war && now - feud.lastIncident > QUIET_FOR_TRUCE) {
				truce(server, feud, now, feud.lossesA >= LOSSES_FOR_TRUCE || feud.lossesB >= LOSSES_FOR_TRUCE ? "heavy losses" : "a long quiet");
			}
		}

		// Raids: a group at war whose leader is about and fit plans one on the rival base it knows.
		for (Feud feud : feuds.values()) {
			if (feud.tension < WAR || now < feud.truceUntil) {
				continue;
			}

			for (UUID attackers : new UUID[] {feud.a, feud.b}) {
				maybeRaid(server, attackers, feud.other(attackers), now);
			}
		}

		raids.values().removeIf(raid -> raidOver(server, raid, now));
	}

	/** Each group's base: its leader's home, while the leader is about. */
	private static void updateBases(MinecraftServer server) {
		for (DrifterGroups.Group group : DrifterGroups.all()) {
			DrifterEntity leader = loaded(server, group.leader);
			var home = leader == null ? null : leader.getDrifterBrain().home();
			if (home != null && !home.center.equals(group.base)) {
				group.base = home.center;
				group.baseDimension = home.dimension.toString();
				DrifterGroups.changed();
			}
		}
	}

	private static void truce(MinecraftServer server, Feud feud, long now, String why) {
		feud.tension = AFTER_TRUCE;
		feud.truceUntil = now + TRUCE;
		feud.lossesA = 0;
		feud.lossesB = 0;
		feud.warSince = -1;
		raids.values().removeIf(raid -> raid.attackers.equals(feud.a) && raid.defenders.equals(feud.b)
			|| raid.attackers.equals(feud.b) && raid.defenders.equals(feud.a));
		dirty = true;
		Drifter.LOGGER.info("[Drifter] {} and {} made peace ({})", name(feud.a), name(feud.b), why);
		for (UUID side : new UUID[] {feud.a, feud.b}) {
			forMembers(server, side, member -> member.getMemory().thoughts().add(now, "made peace with " + name(feud.other(side)) + " (" + why + ")"));
		}
	}

	private static void maybeRaid(MinecraftServer server, UUID attackers, UUID defenders, long now) {
		if (raids.containsKey(attackers) || raidOn(attackers) != null || now - lastRaid.getOrDefault(attackers, Long.MIN_VALUE / 2) < RAID_EVERY) {
			return;
		}

		DrifterGroups.Group group = DrifterGroups.byId(attackers);
		DrifterGroups.Group rival = DrifterGroups.byId(defenders);
		DrifterEntity leader = group == null ? null : loaded(server, group.leader);
		if (leader == null || rival == null || leader.getHealth() < leader.getMaxHealth() * 0.75F
			|| com.dinglebob.drifter.brain.Schedule.isNight(leader) || leader.getDrifterBrain().home() == null
			|| !leader.level().dimension().location().toString().equals(rival.baseDimension)) {
			return;
		}

		// Only a base one of them has seen (knowledge through perception).
		BlockPos target = knownBase(server, group, rival);
		if (target == null) {
			return;
		}

		Raid raid = new Raid(attackers, defenders, target, leader.getDrifterBrain().home().center, now);
		raids.put(attackers, raid);
		lastRaid.put(attackers, now);
		Drifter.LOGGER.info("[Drifter] {} sets off to raid {} at {}", group.name, rival.name, target.toShortString());
		leader.getMemory().thoughts().add(now, "rallying " + group.name + " for a raid on " + rival.name);
	}

	/** Where the rival group lives, if one of this group has seen it (it saw one of them by their home). */
	@Nullable
	private static BlockPos knownBase(MinecraftServer server, DrifterGroups.Group group, DrifterGroups.Group rival) {
		for (UUID id : group.members) {
			DrifterEntity member = loaded(server, id);
			if (member == null) {
				continue;
			}

			for (UUID theirs : rival.members) {
				var bond = member.getBonds().get(theirs);
				if (bond != null && bond.knownBase != null) {
					return bond.knownBase;
				}
			}
		}

		return null;
	}

	private static boolean raidOver(MinecraftServer server, Raid raid, long now) {
		DrifterGroups.Group group = DrifterGroups.byId(raid.attackers);
		if (group == null || DrifterGroups.byId(raid.defenders) == null) {
			return true;
		}

		long limit = switch (raid.phase) {
			case GATHER -> GATHER_TICKS;
			case TRAVEL -> TRAVEL_TICKS;
			case ATTACK -> ATTACK_TICKS;
			case RETREAT -> RETREAT_TICKS;
		};
		if (now - raid.phaseSince <= limit) {
			return false;
		}

		// Out of time for this part: on to the next (gathering waits no longer; a long trip there turns back).
		switch (raid.phase) {
			case GATHER -> raid.enter(Phase.TRAVEL, now);
			case TRAVEL, ATTACK -> raid.enter(Phase.RETREAT, now);
			case RETREAT -> {
				return true;
			}
		}

		return false;
	}

	/** A raid looted a chest: the rivals will hear of it. */
	public static void looted(Raid raid, long now) {
		raid.looted++;
		incident(raid.attackers, raid.defenders, PER_RAID / 3.0, now, "a raid");
	}

	public static void endRaid(Raid raid) {
		raids.remove(raid.attackers);
	}

	@Nullable
	public static DrifterEntity loaded(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof DrifterEntity drifter && drifter.isAlive()) {
				return drifter;
			}
		}

		return null;
	}

	public static void forMembers(MinecraftServer server, UUID group, java.util.function.Consumer<DrifterEntity> action) {
		DrifterGroups.Group g = DrifterGroups.byId(group);
		if (g == null) {
			return;
		}

		for (UUID id : g.members) {
			DrifterEntity member = loaded(server, id);
			if (member != null) {
				action.accept(member);
			}
		}
	}

	public static String name(UUID group) {
		DrifterGroups.Group g = DrifterGroups.byId(group);
		return g == null ? "a group that's gone" : g.name;
	}

	/** Debug: tension between two groups, set outright. */
	public static void setTension(UUID a, UUID b, double tension, long now) {
		Feud feud = feudOrNew(a, b);
		feud.tension = Math.max(0.0, Math.min(MAX, tension));
		feud.lastIncident = now;
		feud.truceUntil = Long.MIN_VALUE / 2;
		feud.warSince = tension >= WAR ? now : -1;
		lastRaid.remove(a);
		lastRaid.remove(b);
		dirty = true;
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the world
	// ---------------------------------------------------------------------------------------------

	private static final class State extends SavedData {
		ListTag list = new ListTag();

		static State load(CompoundTag tag, HolderLookup.Provider registries) {
			State state = new State();
			state.list = tag.getList("Feuds", Tag.TAG_COMPOUND);
			return state;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putInt("Version", 1);
			tag.put("Feuds", this.list);
			return tag;
		}
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "drifter_wars");
	}

	private static void load(MinecraftServer server) {
		feuds.clear();
		raids.clear();
		lastRaid.clear();
		for (Tag t : state(server).list) {
			Feud feud = Feud.load((CompoundTag)t);
			feuds.put(key(feud.a, feud.b), feud);
		}
	}

	private static void save(MinecraftServer server) {
		ListTag list = new ListTag();
		for (Feud feud : feuds.values()) {
			list.add(feud.save());
		}

		State state = state(server);
		state.list = list;
		state.setDirty();
		dirty = false;
	}
}
