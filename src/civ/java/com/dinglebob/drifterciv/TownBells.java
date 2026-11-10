package com.dinglebob.drifterciv;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.BellBlock;

/**
 * A town's bell rung when something's begun (the user, 2026-11-06: "when an event happens (festival, market day, a war,
 * etc) someone should ring the bell to alert everybody"): a festival's morning, market day's, a war declared (on it or by
 * it), its raiders mustering, raiders coming in. The town picks who rings it (its leader, if near enough; else whoever's
 * nearest the bell); they walk over and ring it ({@code BellGoal}). Not saved: a ring not rung by a reload is forgotten.
 * <p>
 * And the alarm (0.23.30; the user, 2026-11-08: "for like major stuff like a massacre or a raid will people spam ring the
 * bell?"): raiders coming in (another town's, or a pillager raid), a killing in the town, a massacre, a killer back about
 * it. Whoever's nearest the bell (not the timid, guards only if nobody else: they're wanted at the fight) runs to it and
 * rings it fast, a dozen times, shouting what's wrong; it's rung again every forty seconds or so while the trouble
 * lasts, and stops when it's over. Everyone in earshot hears an alarm (Drifter's {@code BellHearing}): its guards and
 * brave go to the trouble, the rest go in.
 */
final class TownBells {
	private TownBells() {
	}

	/**
	 * A ring wanted: by whom, for what, the lines to call out, since when (server ticks), how many it's been handed to, and
	 * whether it's the alarm.
	 */
	private record Ring(UUID ringer, String why, List<String> lines, long since, int handed, boolean alarm) {
	}

	/** A town's alarm, on while its trouble lasts (and no longer than {@code until}). */
	private static final class Alarm {
		String why;
		List<String> lines;
		java.util.function.Supplier<BlockPos> trouble;
		java.util.function.BooleanSupplier still;
		long until;
		long nextAt;

		BlockPos trouble() {
			try {
				return this.trouble.get();
			} catch (RuntimeException e) {
				return null;
			}
		}

		boolean on(long now) {
			try {
				return now < this.until && this.still.getAsBoolean();
			} catch (RuntimeException e) {
				return false;
			}
		}
	}

	/** Each town's alarm, while it's on (by its id). */
	private static final Map<UUID, Alarm> alarms = new HashMap<>();
	/** The alarm's rung again this long after it was last rung, while the trouble lasts. */
	private static final long ALARM_AGAIN = 20L * 40;
	/** An alarm not rung in this long goes to someone else. */
	private static final long ALARM_GIVE_UP = 20L * 30;
	/** Nobody farther than this from the bell is sent to ring the alarm (they'd never get there in time). */
	private static final double ALARM_REACH = 128.0;
	/** The longest a raid's alarm stays on. */
	private static final long RAID_ALARM = 20L * 60 * 20;

	/** Each town's ring wanted (by its id). */
	private static final Map<UUID, Ring> rings = new HashMap<>();
	/** The raids each town has rung for already (their start times; by its id). */
	private static final Map<UUID, Long> rungForRaid = new HashMap<>();
	/** A ring not rung in this long goes to someone else (twice at most); then it's dropped. */
	private static final long GIVE_UP = 20L * 120;
	/** The leader rings it if it's this near the bell. */
	private static final double LEADER_NEAR = 64.0;

	/** Where its bell is (by the plaza's board), or null. */
	@Nullable
	static BlockPos bell(Town town, net.minecraft.world.level.Level level) {
		BlockPos at = town.center.offset(5, 0, -5);
		if (!level.isLoaded(at)) {
			return null;
		}

		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-1, -2, -1), at.offset(1, 2, 1))) {
			if (level.getBlockState(pos).getBlock() instanceof BellBlock) {
				return pos.immutable();
			}
		}

		return null;
	}

	/** A ring for this: someone's picked to ring it (not while the alarm's on: the bell's busy). */
	static void ring(MinecraftServer server, Town town, String why, List<String> lines) {
		if (!alarms.containsKey(town.id)) {
			pick(server, town, why, lines, 0, null);
		}
	}

	/** The town's alarm is on. */
	static boolean alarmed(Town town) {
		return alarms.containsKey(town.id);
	}

	/**
	 * The alarm, for this (raiders, a killing, a massacre): rung now, and again while {@code still} holds (at most
	 * {@code lasts} ticks), each time saying the trouble is where {@code trouble} says. On already: what for and where are
	 * this now, rung again at once if it's something new (a killing become a massacre).
	 */
	static void alarm(MinecraftServer server, Town town, String why, List<String> lines, java.util.function.Supplier<BlockPos> trouble,
		java.util.function.BooleanSupplier still, long lasts) {
		long now = server.getTickCount();
		Alarm alarm = alarms.get(town.id);
		boolean fresh = alarm == null;
		if (fresh) {
			alarm = new Alarm();
			alarms.put(town.id, alarm);
			alarm.nextAt = now;
			DrifterCivilizations.LOGGER.info("[town] {} raised the alarm: {}", town.name, why);
		} else if (!why.equals(alarm.why)) {
			alarm.nextAt = now;
			DrifterCivilizations.LOGGER.info("[town] {}'s alarm is for {} now", town.name, why);
		}

		alarm.why = why;
		alarm.lines = lines;
		alarm.trouble = trouble;
		alarm.still = still;
		alarm.until = Math.max(fresh ? 0L : alarm.until, now + lasts);
		Ring ring = rings.get(town.id);
		DrifterEntity ringer = ring != null && ring.alarm ? TownFounding.loaded(server, ring.ringer) : null;
		if (ring != null && ring.alarm && ringer != null && ringer.isAlive()) {
			rings.put(town.id, new Ring(ring.ringer, why, lines, ring.since, ring.handed, true));
		} else if (ring != null && ring.alarm) {
			rings.remove(town.id);
			pickAlarm(server, town, alarm, ring.handed + 1, ring.ringer);
		} else if (now >= alarm.nextAt) {
			pickAlarm(server, town, alarm, 0, null);
		}
	}

	/** The alarm off (debug). */
	static boolean stopAlarm(Town town) {
		Ring ring = rings.get(town.id);
		if (ring != null && ring.alarm) {
			rings.remove(town.id);
		}

		return alarms.remove(town.id) != null;
	}

	/**
	 * Who runs to the bell for the alarm: whoever's nearest it, but not one too timid to go out (unless nobody else will)
	 * and a guard only if they're much the nearest (they're wanted at the fight).
	 */
	private static void pickAlarm(MinecraftServer server, Town town, Alarm alarm, int handed, @Nullable UUID not) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		BlockPos bell = server.overworld() == null ? null : bell(town, server.overworld());
		if (group == null || bell == null) {
			return;
		}

		DrifterEntity pick = null;
		double best = Double.MAX_VALUE;
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member == null || id.equals(not) || town.crooks.contains(id) || DrifterApi.tradeRun(member) != null || !member.isAlive()) {
				continue;
			}

			double d = Math.sqrt(member.blockPosition().distSqr(bell));
			if (d > ALARM_REACH) {
				continue;
			}

			TownWork work = member.getTownWork();
			double score = d + (DrifterApi.timid(member) ? 1000.0 : 0.0) + (work != null && work.guard(member) ? 32.0 : 0.0);
			if (score < best) {
				best = score;
				pick = member;
			}
		}

		if (pick != null) {
			rings.put(town.id, new Ring(pick.getUUID(), alarm.why, alarm.lines, server.getTickCount(), handed, true));
			DrifterApi.think(pick, "has the alarm bell to ring for " + alarm.why);
		}
	}

	private static void pick(MinecraftServer server, Town town, String why, List<String> lines, int handed, @Nullable UUID not) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		BlockPos bell = server.overworld() == null ? null : bell(town, server.overworld());
		if (group == null || bell == null) {
			return;
		}

		DrifterEntity leader = town.leader == null ? null : TownFounding.loaded(server, town.leader);
		DrifterEntity pick = leader != null && !town.leader.equals(not) && leader.blockPosition().closerThan(bell, LEADER_NEAR)
			&& Math.abs(leader.getY() - bell.getY()) < 12.0 ? leader : null;
		if (pick == null) {
			double best = Double.MAX_VALUE;
			for (UUID id : group.members) {
				DrifterEntity member = TownFounding.loaded(server, id);
				if (member == null || id.equals(not) || town.crooks.contains(id) || DrifterApi.tradeRun(member) != null || !member.isAlive()) {
					continue;
				}

				// (Down a mine counts four times as far: found in testing, 0.23.35, the one picked was digging under the town
				// and the alarm went unrung for two minutes.)
				double dx = member.getX() - bell.getX();
				double dy = (member.getY() - bell.getY()) * 4.0;
				double dz = member.getZ() - bell.getZ();
				double d = dx * dx + dy * dy + dz * dz;
				if (d < best) {
					best = d;
					pick = member;
				}
			}
		}

		if (pick != null) {
			rings.put(town.id, new Ring(pick.getUUID(), why, lines, server.getTickCount(), handed, false));
			DrifterApi.think(pick, "has the bell to ring for " + why);
		}
	}

	/** The bell to ring for its town now, if it's the one to. */
	@Nullable
	static TownWork.Bell bellFor(Town town, DrifterEntity drifter) {
		Ring ring = rings.get(town.id);
		if (ring == null || !ring.ringer.equals(drifter.getUUID())) {
			return null;
		}

		BlockPos bell = bell(town, drifter.level());
		Alarm alarm = ring.alarm ? alarms.get(town.id) : null;
		return bell == null ? null : new TownWork.Bell(bell, ring.why, ring.lines, ring.alarm, alarm == null ? null : alarm.trouble());
	}

	/** It rang it. */
	static void rang(Town town, DrifterEntity drifter) {
		Ring ring = rings.get(town.id);
		if (ring != null && ring.ringer.equals(drifter.getUUID())) {
			rings.remove(town.id);
			Alarm alarm = ring.alarm ? alarms.get(town.id) : null;
			if (alarm != null) {
				alarm.nextAt = drifter.getServer().getTickCount() + ALARM_AGAIN;
			}

			DrifterCivilizations.LOGGER.info("[town] {} rang {}'s {} for {}", drifter.getName().getString(), town.name, ring.alarm ? "alarm bell" : "bell", ring.why);
		}
	}

	/** Each check: rings given up on handed on; a raid by it mustering, or one on it coming in: rung for. */
	static void check(MinecraftServer server, Town town) {
		Ring ring = rings.get(town.id);
		long now = server.getTickCount();
		// The alarm: over when its trouble is; else handed on if not rung, and rung again a while after it was.
		Alarm alarm = alarms.get(town.id);
		if (alarm != null && !alarm.on(now)) {
			stopAlarm(town);
			DrifterCivilizations.LOGGER.info("[town] {}'s alarm is over ({})", town.name, alarm.why);
			ring = rings.get(town.id);
		} else if (alarm != null) {
			// (Its ringer killed on the way, or gone: someone else, now; found in testing, the one sent was the massacre's third
			// victim, and the bell waited half a minute.)
			DrifterEntity ringer = ring != null && ring.alarm ? TownFounding.loaded(server, ring.ringer) : null;
			if (ring != null && ring.alarm && (now - ring.since > ALARM_GIVE_UP || ringer == null || !ringer.isAlive())) {
				rings.remove(town.id);
				pickAlarm(server, town, alarm, ring.handed + 1, ring.ringer);
			} else if ((ring == null || !ring.alarm) && now >= alarm.nextAt) {
				pickAlarm(server, town, alarm, 0, null);
			}

			ring = rings.get(town.id);
		}

		if (ring != null && !ring.alarm && server.getTickCount() - ring.since > GIVE_UP) {
			rings.remove(town.id);
			if (ring.handed < 2) {
				pick(server, town, ring.why, ring.lines, ring.handed + 1, ring.ringer);
			}
		}

		if (town.group == null) {
			return;
		}

		DrifterApi.RaidInfo by = DrifterApi.raidBy(town.group);
		if (by != null && by.phase().equals("gather") && !Long.valueOf(by.startedAt()).equals(rungForRaid.get(town.id))) {
			rungForRaid.put(town.id, by.startedAt());
			String enemy = name(by.defenders());
			ring(server, town, "the raid on " + enemy, List.of("to arms! we march on " + enemy, "everyone who can fight, to the plaza!",
				"gather up, we're going to " + enemy));
		}

		DrifterApi.RaidInfo on = DrifterApi.raidOn(town.group);
		if (on != null && (on.phase().equals("attack") || on.phase().equals("travel") && near(server, town, on))
			&& !Long.valueOf(on.startedAt()).equals(rungForRaid.get(town.id))) {
			rungForRaid.put(town.id, on.startedAt());
			String enemy = name(on.attackers());
			UUID attackers = on.attackers();
			UUID defenders = town.group;
			long started = on.startedAt();
			alarm(server, town, "raiders from " + enemy, List.of("RAIDERS! " + enemy + " is here!", "raid! everyone inside!", "they're coming, " + enemy
				+ " is attacking!"), () -> nearest(server, town, attackers), () -> {
					DrifterApi.RaidInfo still = DrifterApi.raidOn(defenders);
					return still != null && still.startedAt() == started && (still.phase().equals("attack") || still.phase().equals("travel"));
				}, RAID_ALARM);
		}
	}

	/** Where the raiders from this group are: the one of them nearest the town's middle. */
	@Nullable
	private static BlockPos nearest(MinecraftServer server, Town town, UUID attackers) {
		DrifterGroups.Group group = DrifterApi.groupById(attackers);
		if (group == null) {
			return null;
		}

		BlockPos best = null;
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member != null && member.isAlive() && (best == null || member.blockPosition().distSqr(town.center) < best.distSqr(town.center))) {
				best = member.blockPosition();
			}
		}

		return best;
	}

	/** The raiders on their way are near (their leader within a long bowshot of the town). */
	private static boolean near(MinecraftServer server, Town town, DrifterApi.RaidInfo raid) {
		DrifterGroups.Group group = DrifterApi.groupById(raid.attackers());
		DrifterEntity leader = group == null ? null : TownFounding.loaded(server, group.leader);
		return leader != null && leader.blockPosition().closerThan(town.center, 120.0);
	}

	private static String name(UUID group) {
		for (Town town : Towns.all()) {
			if (group.equals(town.group)) {
				return town.name;
			}
		}

		DrifterGroups.Group g = DrifterApi.groupById(group);
		return g == null ? "them" : g.name;
	}
}
