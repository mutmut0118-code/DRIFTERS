package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

/**
 * A town made ready has a past (the user, 2026-10-28: "for spawned towns, can we already established relationships with
 * one another? that way it seems like it has some history between towns and everybody already knows eachother"):
 * <ul>
 *   <li>its people all know each other, some better than others: how well they get on goes by how alike they are, and
 *       chance; housemates closer; a few best friends, a few who don't get on (never enemies);</li>
 *   <li>a few of them know a few people of the towns already about: mostly old friends, now and then an old grudge.</li>
 * </ul>
 * The chronicle tells of it. A bond with someone not loaded waits ({@link Town#pendingBonds}) till they are.
 */
final class TownHistory {
	private TownHistory() {
	}

	/** How well two of the same town get on, before chance and housemates (trust). */
	private static final double USUAL = 45.0;
	/** Alike or not: this much more or less per {@link #SPREAD} more or less alike than most pairs ({@link #TYPICAL}). */
	private static final double LIKENESS = 18.0;
	/** How alike two people usually are (traits mostly round the middle). */
	private static final double TYPICAL = 0.85;
	/** How much pairs usually differ in how alike they are. */
	private static final double SPREAD = 0.05;
	/** Chance: a spread of about this much. */
	private static final double CHANCE = 18.0;
	/** Sharing a house. */
	private static final double HOUSEMATES = 20.0;
	/** Never lower than this in their own town (no feuds to the death on day one: trust -60 is an enemy on sight). */
	private static final double LOWEST = -25.0;
	private static final double HIGHEST = 98.0;
	/** Towns this far apart at most know each other. */
	private static final double KNOWN_FAR = 3000.0;
	/** The traits two people are alike or not in. */
	private static final String[] TRAITS = {"sociability", "aggression", "tidiness", "greed", "caution", "curiosity", "forgiveness"};

	/** Its people's bonds with each other (who shares a house with whom from its plots). */
	static void neighbours(MinecraftServer server, Town town, List<DrifterEntity> people, RandomSource random) {
		Map<UUID, Integer> house = new HashMap<>();
		for (int i = 0; i < town.plots.size(); i++) {
			Town.Plot plot = town.plots.get(i);
			if (plot.owner != null) {
				house.put(plot.owner, i);
			}

			for (UUID resident : plot.residents) {
				house.put(resident, i);
			}
		}

		String best = null;
		double bestTrust = -1.0;
		String worst = null;
		double worstTrust = 999.0;
		for (int i = 0; i < people.size(); i++) {
			for (int j = i + 1; j < people.size(); j++) {
				DrifterEntity a = people.get(i);
				DrifterEntity b = people.get(j);
				boolean together = house.containsKey(a.getUUID()) && house.get(a.getUUID()).equals(house.get(b.getUUID()));
				double alike = Math.max(-2.0, Math.min(2.0, (likeness(a, b) - TYPICAL) / SPREAD));
				double base = USUAL + LIKENESS * alike + (together ? HOUSEMATES : 0.0);
				// Each sees it a little differently.
				double ab = clamp(base + random.nextGaussian() * CHANCE);
				double ba = clamp(base + random.nextGaussian() * CHANCE * 0.5 + (ab - base) * 0.5);
				double known = together ? 80.0 + random.nextInt(20) : 30.0 + random.nextInt(50);
				DrifterApi.setBond(a, b.getUUID(), b.getName().getString(), ab, known);
				DrifterApi.setBond(b, a.getUUID(), a.getName().getString(), ba, known);
				double both = (ab + ba) / 2.0;
				if (both > bestTrust) {
					bestTrust = both;
					best = a.getName().getString() + " and " + b.getName().getString();
				}

				if (both < worstTrust) {
					worstTrust = both;
					worst = a.getName().getString() + " and " + b.getName().getString();
				}
			}
		}

		if (best != null && bestTrust >= 80.0) {
			Chronicle.add(server, "town", best + " of " + town.name + " have been best friends for years");
		}

		if (worst != null && worstTrust <= 10.0) {
			Chronicle.add(server, "town", worst + " of " + town.name + " have never got on");
		}
	}

	/** How alike two are, 0 (opposites) to 1 (the same), in the traits that make people get on. */
	private static double likeness(DrifterEntity a, DrifterEntity b) {
		double apart = 0.0;
		for (String trait : TRAITS) {
			apart += Math.abs(DrifterApi.trait(a, trait) - DrifterApi.trait(b, trait));
		}

		return 1.0 - apart / TRAITS.length;
	}

	private static double clamp(double trust) {
		return Math.max(LOWEST, Math.min(HIGHEST, trust));
	}

	/** A few of its people know a few of each town already about (not too far): old friends mostly, now and then a grudge. */
	static void acrossTowns(MinecraftServer server, Town town, List<DrifterEntity> people, RandomSource random) {
		if (people.isEmpty()) {
			return;
		}

		for (Town other : Towns.all()) {
			if (other == town || !other.dimension.equals(town.dimension) || other.group == null || other.center.distSqr(town.center) > KNOWN_FAR * KNOWN_FAR) {
				continue;
			}

			DrifterGroups.Group theirs = DrifterApi.groupById(other.group);
			if (theirs == null || theirs.members.isEmpty()) {
				continue;
			}

			List<UUID> them = new ArrayList<>(theirs.members);
			int pairs = 2 + random.nextInt(3);
			int friends = 0;
			int grudges = 0;
			String grudge = null;
			for (int k = 0; k < pairs; k++) {
				DrifterEntity mine = people.get(random.nextInt(people.size()));
				UUID their = them.get(random.nextInt(them.size()));
				String theirName = theirs.names.getOrDefault(their, "?");
				float roll = random.nextFloat();
				double trust;
				double known;
				if (roll < 0.6F) {
					trust = 55.0 + random.nextInt(30);
					known = 30.0 + random.nextInt(30);
					friends++;
				} else if (roll < 0.85F) {
					trust = 20.0 + random.nextInt(25);
					known = 15.0 + random.nextInt(15);
				} else {
					trust = -45.0 + random.nextInt(30);
					known = 20.0 + random.nextInt(20);
					grudges++;
					grudge = mine.getName().getString() + " of " + town.name + " and " + theirName + " of " + other.name;
				}

				// Each way, near enough (an old grudge is mostly mutual).
				DrifterApi.setBond(mine, their, theirName, trust, known);
				double back = Math.max(LOWEST * 2, Math.min(HIGHEST, trust + random.nextGaussian() * 8.0));
				DrifterEntity loaded = TownFounding.loaded(server, their);
				if (loaded != null) {
					DrifterApi.setBond(loaded, mine.getUUID(), mine.getName().getString(), back, known);
				} else {
					other.pendingBonds.add(new Town.Bond(their, mine.getUUID(), mine.getName().getString(), back, known));
				}
			}

			// (The two towns know of each other, on the footing their people's past puts them.)
			Diplomacy.meet(server, town, other, 10.0 * friends - 15.0 * grudges, null);
			if (friends > 0) {
				Chronicle.add(server, "town", "The people of " + town.name + " and " + other.name + " go back a long way (" + friends
					+ (friends == 1 ? " old friendship" : " old friendships") + ")");
			}

			if (grudges > 0 && grudge != null) {
				Chronicle.add(server, "town", grudge + " have an old grudge");
			}

			Towns.save(server);
		}
	}

	/** Bonds waiting for someone not loaded when they were made: theirs, now they are. */
	static void applyPending(MinecraftServer server, Town town) {
		if (town.pendingBonds.isEmpty()) {
			return;
		}

		boolean changed = town.pendingBonds.removeIf(bond -> {
			DrifterEntity who = TownFounding.loaded(server, bond.who());
			if (who == null) {
				return false;
			}

			DrifterApi.setBond(who, bond.other(), bond.otherName(), bond.trust(), bond.familiarity());
			return true;
		});
		if (changed) {
			Towns.save(server);
		}
	}

	static ListTag save(List<Town.Bond> bonds) {
		ListTag list = new ListTag();
		for (Town.Bond bond : bonds) {
			CompoundTag one = new CompoundTag();
			one.putUUID("Who", bond.who());
			one.putUUID("Other", bond.other());
			one.putString("Name", bond.otherName());
			one.putDouble("Trust", bond.trust());
			one.putDouble("Known", bond.familiarity());
			list.add(one);
		}

		return list;
	}

	static void load(ListTag list, List<Town.Bond> into) {
		for (Tag t : list) {
			CompoundTag one = (CompoundTag)t;
			into.add(new Town.Bond(one.getUUID("Who"), one.getUUID("Other"), one.getString("Name"), one.getDouble("Trust"), one.getDouble("Known")));
		}
	}
}
