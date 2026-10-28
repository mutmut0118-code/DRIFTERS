package com.dinglebob.drifterciv;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.server.MinecraftServer;

/**
 * What a town pays its people for its work (step 4; the user, 2026-10-28: "Town treasury"): so much a road block made
 * path, a log of a tree felled on the plaza, a bucket of water poured into the well, a block of a town build placed. It's
 * owed (the town's book), and taken out of the till by the one owed, as far as there's money in it: the money is the
 * stall's takings, the keeper's to put in. Nothing is paid that isn't in the till.
 */
final class Wages {
	private Wages() {
	}

	/** Points a unit of each job pays (an emerald is 6 points: about one for a visit's 24 road blocks). */
	private static final Map<String, Double> RATES = Map.of("roads", 0.25, "trees", 0.3, "water", 3.0, "build", 0.15);
	/** Most the town owes one of them (it doesn't pay more than this much behind). */
	private static final double MOST_OWED = 60.0;

	static void worked(Town town, DrifterEntity drifter, String job, int units) {
		double rate = RATES.getOrDefault(job, 0.0);
		if (rate <= 0.0 || units <= 0) {
			return;
		}

		town.owed.merge(drifter.getUUID(), rate * units, (a, b) -> Math.min(MOST_OWED, a + b));
		town.booksChanged = true;
	}

	static void paid(@Nullable MinecraftServer server, Town town, DrifterEntity drifter, double points) {
		double owed = town.owed.getOrDefault(drifter.getUUID(), 0.0);
		town.owed.put(drifter.getUUID(), Math.max(0.0, owed - points));
		town.wagesPaid += points;
		if (server != null) {
			int emeralds = (int)Math.round(points / 6.0);
			Chronicle.add(server, "town", drifter.getName().getString() + " was paid " + (emeralds >= 1 ? emeralds + (emeralds == 1 ? " emerald's" : " emeralds'")
				: "a little") + " worth of wages in " + town.name);
			Towns.save(server);
		}
	}

	/** All it owes, in points. */
	static double owedAll(Town town) {
		return town.owed.values().stream().mapToDouble(Double::doubleValue).sum();
	}
}
