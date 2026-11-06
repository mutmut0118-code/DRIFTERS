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
 */
final class TownBells {
	private TownBells() {
	}

	/** A ring wanted: by whom, for what, the lines to call out, since when (server ticks), how many it's been handed to. */
	private record Ring(UUID ringer, String why, List<String> lines, long since, int handed) {
	}

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

	/** A ring for this: someone's picked to ring it. */
	static void ring(MinecraftServer server, Town town, String why, List<String> lines) {
		pick(server, town, why, lines, 0, null);
	}

	private static void pick(MinecraftServer server, Town town, String why, List<String> lines, int handed, @Nullable UUID not) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		BlockPos bell = server.overworld() == null ? null : bell(town, server.overworld());
		if (group == null || bell == null) {
			return;
		}

		DrifterEntity leader = town.leader == null ? null : TownFounding.loaded(server, town.leader);
		DrifterEntity pick = leader != null && !town.leader.equals(not) && leader.blockPosition().closerThan(bell, LEADER_NEAR) ? leader : null;
		if (pick == null) {
			double best = Double.MAX_VALUE;
			for (UUID id : group.members) {
				DrifterEntity member = TownFounding.loaded(server, id);
				if (member == null || id.equals(not) || town.crooks.contains(id) || DrifterApi.tradeRun(member) != null || !member.isAlive()) {
					continue;
				}

				double d = member.blockPosition().distSqr(bell);
				if (d < best) {
					best = d;
					pick = member;
				}
			}
		}

		if (pick != null) {
			rings.put(town.id, new Ring(pick.getUUID(), why, lines, server.getTickCount(), handed));
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
		return bell == null ? null : new TownWork.Bell(bell, ring.why, ring.lines);
	}

	/** It rang it. */
	static void rang(Town town, DrifterEntity drifter) {
		Ring ring = rings.get(town.id);
		if (ring != null && ring.ringer.equals(drifter.getUUID())) {
			rings.remove(town.id);
			DrifterCivilizations.LOGGER.info("[town] {} rang {}'s bell for {}", drifter.getName().getString(), town.name, ring.why);
		}
	}

	/** Each check: rings given up on handed on; a raid by it mustering, or one on it coming in: rung for. */
	static void check(MinecraftServer server, Town town) {
		Ring ring = rings.get(town.id);
		if (ring != null && server.getTickCount() - ring.since > GIVE_UP) {
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
			ring(server, town, "raiders from " + enemy, List.of("RAIDERS! " + enemy + " is here!", "raid! everyone inside!", "they're coming, " + enemy
				+ " is attacking!"));
		}
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
