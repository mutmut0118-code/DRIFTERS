package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/**
 * A town's customs from what it's done (milestone 23 C6; the user, 2026-11-06: "lets do c, for traditions"; the plan: "what
 * the town did more than once becomes its custom (a fishing contest every week after the first one was fun; never going
 * near the ravine where three died)"):
 * <ul>
 *   <li><b>the weekly fishing contest</b>: two contests won, and they're every six days instead of twelve;</li>
 *   <li><b>the big market</b>: three busy market days (trades at its stall), and market's every third day;</li>
 *   <li><b>Victory Day</b>: a war won, and that day of its year is a festival from then on;</li>
 *   <li><b>Remembrance Day</b>: a leader of it gone for good, and the day it heard is kept each year, a quiet gathering;</li>
 *   <li><b>a place they keep away from</b>: three of its people died there (outside the town), and nobody goes near it
 *       on a long walk (they go round).</li>
 * </ul>
 * Each kept as a word in {@link Town#traditions} ("fishing", "market", "victory:day:name", "remembrance:day:name",
 * "keep away:x:z"), told in the chronicle when it begins.
 */
final class TownTraditions {
	private TownTraditions() {
	}

	/** Contests won before the fishing contest's weekly. */
	private static final int CONTESTS = 2;
	/** Busy market days (this many trades or more in the day) before the big market. */
	private static final int MARKETS = 3;
	private static final int BUSY = 3;
	/** Deaths of its people within this of each other, outside the town, before the place is kept away from. */
	private static final int DEATHS = 3;
	private static final double DEATHS_NEAR = 24.0;
	/** How far round a place kept away from they go. */
	static final double KEEP_AWAY = 40.0;

	static void register() {
		DrifterEvents.DIED.register((drifter, source, comesBack) -> {
			MinecraftServer server = drifter.getServer();
			var group = DrifterApi.group(drifter.getUUID());
			if (server == null || group == null) {
				return;
			}

			for (Town town : Towns.all()) {
				if (group.id.equals(town.group) && town.dimension.equals(drifter.level().dimension().location().toString())
					&& Math.hypot(drifter.getX() - town.center.getX(), drifter.getZ() - town.center.getZ()) > town.land()) {
					died(server, town, drifter.blockPosition());
				}
			}
		});
		// The places a town keeps away from: round them on a long walk (its own people only).
		DrifterApi.setKeepAway((drifter, spot) -> keptAway(drifter, spot));
	}

	static boolean has(Town town, String word) {
		return town.traditions.stream().anyMatch(t -> t.equals(word) || t.startsWith(word + ":"));
	}

	private static void begin(MinecraftServer server, Town town, String word, String told) {
		if (town.traditions.add(word)) {
			Chronicle.add(server, "town", told);
			DrifterCivilizations.LOGGER.info("[culture] {}: a tradition now: {}", town.name, word);
			Towns.save(server);
		}
	}

	/** A fishing contest won. */
	static void contestWon(MinecraftServer server, Town town) {
		if (town.tally.merge("contests", 1, Integer::sum) >= CONTESTS && !has(town, "fishing")) {
			begin(server, town, "fishing", town.name + "'s fishing contest is a weekly custom now");
		}
	}

	/** A market day over, with this many trades at its stall. */
	static void marketOver(MinecraftServer server, Town town, int trades) {
		if (trades >= BUSY && town.tally.merge("markets", 1, Integer::sum) >= MARKETS && !has(town, "market")) {
			begin(server, town, "market", town.name + "'s market is the big market now, every third day");
		}
	}

	/** It won a war over that town, today. */
	static void warWon(MinecraftServer server, Town town, Town over) {
		long of = Math.floorMod(Chronicle.day(server) - town.bornDay, TownCulture.YEAR);
		if (!has(town, "victory")) {
			begin(server, town, "victory:" + of + ":" + over.name, town.name + " will keep Victory Day each year, for its win over " + over.name);
		}
	}

	/** A leader of it gone for good (heard today). */
	static void leaderGone(MinecraftServer server, Town town, String name) {
		long of = Math.floorMod(Chronicle.day(server) - town.bornDay, TownCulture.YEAR);
		if (!has(town, "remembrance")) {
			begin(server, town, "remembrance:" + of + ":" + name, town.name + " will remember " + name + " on this day each year");
		}
	}

	/** One of its people died out there: three near one place, and they keep away from it. */
	private static void died(MinecraftServer server, Town town, BlockPos where) {
		town.deathSpots.add(where.immutable());
		if (town.deathSpots.size() > 24) {
			town.deathSpots.remove(0);
		}

		int near = 0;
		for (BlockPos other : town.deathSpots) {
			if (other.closerThan(where, DEATHS_NEAR)) {
				near++;
			}
		}

		String word = "keep away:" + where.getX() + ":" + where.getZ();
		if (near >= DEATHS && town.traditions.stream().noneMatch(t -> t.startsWith("keep away:") && spot(t).closerThan(where, DEATHS_NEAR * 2))) {
			begin(server, town, word, "Three of " + town.name + "'s people have died near " + where.getX() + ", " + where.getZ()
				+ ": its people keep away from there now");
		}

		Towns.save(server);
	}

	private static BlockPos spot(String word) {
		String[] parts = word.split(":");
		try {
			return new BlockPos(Integer.parseInt(parts[1]), 0, Integer.parseInt(parts[2]));
		} catch (RuntimeException e) {
			return BlockPos.ZERO;
		}
	}

	/** A spot near a place its town keeps away from (for its own people's long walks). */
	private static boolean keptAway(DrifterEntity drifter, BlockPos at) {
		var group = DrifterApi.group(drifter.getUUID());
		if (group == null) {
			return false;
		}

		for (Town town : Towns.all()) {
			if (!group.id.equals(town.group)) {
				continue;
			}

			for (String word : town.traditions) {
				if (word.startsWith("keep away:")) {
					BlockPos spot = spot(word);
					if (Math.hypot(spot.getX() - at.getX(), spot.getZ() - at.getZ()) < KEEP_AWAY) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/** Its festival day from its traditions (Victory Day, Remembrance Day) on this day of its year, or null. */
	@Nullable
	static String festival(Town town, long ofYear) {
		for (String word : town.traditions) {
			String[] parts = word.split(":", 3);
			if (parts.length == 3 && (parts[0].equals("victory") || parts[0].equals("remembrance"))) {
				try {
					if (Long.parseLong(parts[1]) == ofYear) {
						return parts[0];
					}
				} catch (NumberFormatException ignored) {
				}
			}
		}

		return null;
	}

	/** Whom or what a Victory or Remembrance Day is for. */
	static String festivalFor(Town town, String festival) {
		for (String word : town.traditions) {
			String[] parts = word.split(":", 3);
			if (parts.length == 3 && parts[0].equals(festival)) {
				return parts[2];
			}
		}

		return "";
	}

	/** Its customs, said (for /drifterciv town and the paper). */
	static List<String> describe(Town town) {
		List<String> said = new ArrayList<>();
		for (String word : town.traditions) {
			String[] parts = word.split(":", 3);
			said.add(switch (parts[0]) {
				case "fishing" -> "the weekly fishing contest";
				case "market" -> "the big market every third day";
				case "victory" -> "Victory Day (over " + (parts.length == 3 ? parts[2] : "?") + ")";
				case "remembrance" -> "Remembrance Day (for " + (parts.length == 3 ? parts[2] : "?") + ")";
				case "keep away" -> "keeping away from " + (parts.length == 3 ? parts[1] + ", " + parts[2] : "somewhere") + " (three died there)";
				default -> word.toLowerCase(Locale.ROOT);
			});
		}

		return said;
	}
}
