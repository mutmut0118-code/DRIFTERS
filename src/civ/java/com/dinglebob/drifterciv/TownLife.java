package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Comings and goings (milestone 23, docs/LIVED_WORLD.md C3): once a day, in the morning, a town made ready may lose one of
 * its people and take one in.
 * <ul>
 *   <li><b>Leavers:</b> someone who's fallen out with most of the town, a restless nomad, or someone grieving may leave
 *       for good ("X left T for good"): out of its group, no home there, its house standing empty. Never the leader or the
 *       stall's keeper, nor one of the last few.</li>
 *   <li><b>Newcomers:</b> a Drifter about the town with no home and no group, no Bandit, who gets on with its people,
 *       moves into a house standing empty (or a spare room) and is one of them ("X moved to T"). With a house standing
 *       empty and nobody come for a while, a newcomer now and then comes up its road from out of sight (a wanderer as the
 *       world has them, carrying nothing) and moves in. A full town takes nobody.</li>
 * </ul>
 * The chronicle tells of it; its people talk of the newest and the latest to leave.
 */
final class TownLife {
	private TownLife() {
	}

	/** The fewest a town keeps: nobody leaves below this. */
	private static final int FEWEST = 4;
	/** A city takes nobody past this many people. */
	private static final int MOST_PEOPLE = 100;
	/** Days between newcomers come up the road (at least). */
	private static final int ARRIVAL_DAYS = 3;
	/** A day's chance a newcomer comes up the road (with a house empty and the days gone by). */
	private static final double ARRIVAL_CHANCE = 0.3;
	/** How well it gets on with the town's people it knows, on average, to move in; below this, a fallen-out one. */
	private static final double GETS_ON = 15.0;
	private static final double FALLEN_OUT = 5.0;

	/** Once a day, in the morning. */
	static void check(MinecraftServer server, Town town) {
		if (!town.spawned || town.group == null) {
			return;
		}

		ServerLevel level = level(server, town);
		if (level == null || !level.isLoaded(town.center)) {
			return;
		}

		long time = level.getDayTime() % 24000L;
		long day = Chronicle.day(server);
		if (time < 1000L || time > 4000L || town.lifeDay >= day) {
			return;
		}

		// (Not on its first morning: nobody leaves a town the day it's made, nor walks in that day.)
		boolean first = town.lifeDay < 0;
		town.lifeDay = day;
		DrifterGroups.Group group = DrifterApi.groupById(town.group);
		if (group == null || first) {
			return;
		}

		boolean changed = leave(server, town, group);
		changed |= come(server, level, town, group, day);
		if (changed) {
			Towns.save(server);
		}
	}

	/** Debug: one of its people (not the leader or the keeper) leaves now; what happened. */
	static String debugLeave(MinecraftServer server, Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return "it has no people";
		}

		// (One with a house to itself first: its house stands empty after.)
		List<UUID> order = new ArrayList<>(group.members);
		order.sort(java.util.Comparator.comparingInt(id -> town.plots.stream().anyMatch(p -> id.equals(p.owner) && p.residents.isEmpty()) ? 0 : 1));
		for (UUID id : order) {
			DrifterEntity one = TownFounding.loaded(server, id);
			if (one != null && !id.equals(group.leader) && !id.equals(town.leader) && !id.equals(town.keeper)) {
				goes(server, town, group, one, "wanted a change (debug)");
				Towns.save(server);
				return one.getName().getString() + " left";
			}
		}

		return "nobody loaded to leave";
	}

	/** Debug: a newcomer comes up its road now (a house standing empty needed). */
	static String debugArrive(MinecraftServer server, Town town) {
		ServerLevel level = level(server, town);
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		Town.Plot empty = emptyHouse(town);
		BlockPos at = level == null ? null : roadEnd(level, town);
		if (group == null || empty == null || at == null) {
			return group == null ? "it has no people" : empty == null ? "no house standing empty" : "no road end loaded";
		}

		DrifterEntity one = DrifterApi.spawn(level, at, null);
		if (one == null) {
			return "couldn't make one";
		}

		DrifterApi.notABandit(one);
		town.arrivedDay = Chronicle.day(server);
		boolean in = movesIn(server, level, town, group, one, empty, null, "came up the road looking for a place");
		Towns.save(server);
		return in ? one.getName().getString() + " came up the road and moved in" : "it couldn't move in";
	}

	// ---------------------------------------------------------------------------------------------
	// Leaving
	// ---------------------------------------------------------------------------------------------

	private static boolean leave(MinecraftServer server, Town town, DrifterGroups.Group group) {
		if (group.members.size() <= FEWEST) {
			return false;
		}

		for (UUID id : List.copyOf(group.members)) {
			DrifterEntity one = TownFounding.loaded(server, id);
			if (one == null || id.equals(group.leader) || id.equals(town.leader) || id.equals(town.keeper) || town.crooks.contains(id)) {
				continue;
			}

			String why = null;
			double trust = trustInTown(one, group);
			if (!Double.isNaN(trust) && trust < FALLEN_OUT && one.getRandom().nextFloat() < 0.15F) {
				why = "fallen out with everyone";
			} else if (DrifterApi.archetype(one).equals("nomad") && DrifterApi.mood(one, "bored") > 0.6 && one.getRandom().nextFloat() < 0.04F) {
				why = "restless, off to see the world";
			} else if (DrifterApi.mood(one, "grieving") > 0.5 && one.getRandom().nextFloat() < 0.04F) {
				why = "couldn't stay there after losing a friend";
			}

			if (why != null) {
				goes(server, town, group, one, why);
				return true;
			}
		}

		return false;
	}

	/** It leaves for good: out of the group, its house standing empty, a wanderer again. */
	static void goes(MinecraftServer server, Town town, DrifterGroups.Group group, DrifterEntity one, String why) {
		UUID id = one.getUUID();
		String name = one.getName().getString();
		for (Town.Plot plot : town.plots) {
			if (id.equals(plot.owner)) {
				plot.owner = plot.residents.isEmpty() ? null : plot.residents.remove(0);
			}

			plot.residents.remove(id);
		}

		Chronicle.toldLeaving.add(id);
		DrifterGroups.leave(id);
		DrifterApi.setGroupLimit(group, Math.max(group.members.size(), 2));
		DrifterApi.setHome(one, null);
		DrifterApi.setTownWork(one, null);
		town.lastLeft = name;
		town.lastLeftDay = Chronicle.day(server);
		DrifterApi.think(one, "leaving " + town.name + " for good: " + why);
		Chronicle.add(server, "town", name + " left " + town.name + " for good (" + why + ")");
		DrifterCivilizations.LOGGER.info("[life] {} left {} ({})", name, town.name, why);
	}

	/** How well it gets on with the town's people it knows (its trust in them, on average), or NaN if it knows fewer than 3. */
	private static double trustInTown(DrifterEntity one, DrifterGroups.Group group) {
		double sum = 0;
		int known = 0;
		for (UUID other : group.members) {
			if (!other.equals(one.getUUID())) {
				double trust = DrifterApi.trustInDrifter(one, other);
				if (trust != 0.0) {
					sum += trust;
					known++;
				}
			}
		}

		return known < 3 ? Double.NaN : sum / known;
	}

	// ---------------------------------------------------------------------------------------------
	// Coming
	// ---------------------------------------------------------------------------------------------

	private static boolean come(MinecraftServer server, ServerLevel level, Town town, DrifterGroups.Group group, long day) {
		if (group.members.size() >= MOST_PEOPLE) {
			return false;
		}

		Town.Plot empty = emptyHouse(town);
		Town.Plot spare = empty == null ? spareRoom(level, town) : null;
		if (empty == null && spare == null) {
			return false;
		}

		// One about the town already: no home, no group, no Bandit, and it gets on with its people (or they don't know it yet).
		int land = town.land() + 48;
		for (DrifterEntity one : level.getEntitiesOfClass(DrifterEntity.class, new net.minecraft.world.phys.AABB(town.center).inflate(land, 64, land))) {
			if (!one.isAlive() || DrifterApi.group(one.getUUID()) != null || DrifterApi.bandit(one) || DrifterApi.home(one) != null
				&& DrifterApi.home(one).noHut) {
				continue;
			}

			double trust = trustOf(group, one, server);
			if (!Double.isNaN(trust) && trust < GETS_ON) {
				continue;
			}

			return movesIn(server, level, town, group, one, empty, spare, "came by and stayed");
		}

		// Up the road, now and then, with a house standing empty.
		if (empty == null || day - town.arrivedDay < ARRIVAL_DAYS || level.getRandom().nextFloat() > ARRIVAL_CHANCE) {
			return false;
		}

		BlockPos at = roadEnd(level, town);
		if (at == null) {
			return false;
		}

		DrifterEntity one = DrifterApi.spawn(level, at, null);
		if (one == null) {
			return false;
		}

		DrifterApi.notABandit(one);
		town.arrivedDay = day;
		return movesIn(server, level, town, group, one, empty, null, "came up the road looking for a place");
	}

	/** It's one of the town now: in its group, in the house (or the room). */
	private static boolean movesIn(MinecraftServer server, ServerLevel level, Town town, DrifterGroups.Group group, DrifterEntity one, @Nullable Town.Plot empty,
		@Nullable Town.Plot spare, String how) {
		Town.Plot plot = empty != null ? empty : spare;
		Home home = DrifterApi.moveIntoHouse(one, plot.planId, plot.origin, plot.rotation, town.store);
		if (home == null) {
			return false;
		}

		String name = one.getName().getString();
		DrifterApi.setGroupLimit(group, Math.max(group.limit(), group.members.size() + 1));
		DrifterGroups.join(group, one.getUUID(), name);
		String whose = plot.owner == null ? null : DrifterApi.record(plot.owner) == null ? null : DrifterApi.record(plot.owner).name();
		if (empty != null) {
			plot.owner = one.getUUID();
		} else {
			plot.residents.add(one.getUUID());
		}

		DrifterApi.knowsBox(one, new net.minecraft.world.level.levelgen.structure.BoundingBox(town.center).inflatedBy(8));
		town.newest = name;
		town.newestDay = Chronicle.day(server);
		DrifterApi.think(one, "moved to " + town.name + ": " + how);
		String where = empty != null ? (town.lastLeft != null && town.lastLeftDay >= town.newestDay - 30 ? town.lastLeft + "'s old house" : "a house standing empty")
			: "a room in " + (whose == null ? "a house" : whose + "'s house");
		Chronicle.add(server, "town", name + " moved to " + town.name + " (" + how + "), into " + where);
		DrifterCivilizations.LOGGER.info("[life] {} moved to {} ({}), into {}", name, town.name, how, where);
		return true;
	}

	/** How well the town's people who know it get on with it (their trust in it, on average), or NaN if none know it yet. */
	private static double trustOf(DrifterGroups.Group group, DrifterEntity one, MinecraftServer server) {
		double sum = 0;
		int known = 0;
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			double trust = member == null ? 0.0 : DrifterApi.trustInDrifter(member, one.getUUID());
			if (trust != 0.0) {
				sum += trust;
				known++;
			}
		}

		return known == 0 ? Double.NaN : sum / known;
	}

	/** A house that's up with nobody in it, nearest the middle. */
	@Nullable
	private static Town.Plot emptyHouse(Town town) {
		Town.Plot best = null;
		for (Town.Plot plot : town.plots) {
			if (plot.built && plot.owner == null && plot.residents.isEmpty() && (best == null || plot.origin.distSqr(town.center) < best.origin.distSqr(town.center))) {
				best = plot;
			}
		}

		return best;
	}

	/** A house with a room to spare (more rooms than people in it). */
	@Nullable
	private static Town.Plot spareRoom(ServerLevel level, Town town) {
		for (Town.Plot plot : town.plots) {
			if (!plot.built || plot.owner == null) {
				continue;
			}

			if (plot.room == 0 && level.isLoaded(plot.origin)) {
				plot.room = DrifterApi.households(level, plot.planId, plot.origin, plot.rotation);
			}

			if (1 + plot.residents.size() < plot.room) {
				return plot;
			}
		}

		return null;
	}

	/** The far end of one of its roads, loaded, on its ground (out of sight of its middle). */
	@Nullable
	private static BlockPos roadEnd(ServerLevel level, Town town) {
		List<BlockPos> ends = new ArrayList<>();
		for (long column : town.roadColumns) {
			int x = BlockPos.getX(column);
			int z = BlockPos.getZ(column);
			double d = Math.hypot(x - town.center.getX(), z - town.center.getZ());
			if (d > town.land() - 24 && level.hasChunkAt(new BlockPos(x, 0, z))) {
				ends.add(new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z));
			}
		}

		return ends.isEmpty() ? null : ends.get(level.getRandom().nextInt(ends.size()));
	}

	@Nullable
	private static ServerLevel level(MinecraftServer server, Town town) {
		for (ServerLevel one : server.getAllLevels()) {
			if (one.dimension().location().toString().equals(town.dimension)) {
				return one;
			}
		}

		return null;
	}

	/** Its people's talk: the newest of them and the latest to leave, for a few days. */
	static void talk(Town town, long day, java.util.Map<String, Object> words, java.util.Set<String> flags) {
		if (town.newest != null && day - town.newestDay <= 3) {
			flags.add("town_newcomer");
			words.put("newcomer", town.newest);
		}

		if (town.lastLeft != null && day - town.lastLeftDay <= 3) {
			flags.add("town_left");
			words.put("leaver", town.lastLeft);
		}
	}
}
