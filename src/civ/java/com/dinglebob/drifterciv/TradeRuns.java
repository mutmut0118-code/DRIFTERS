package com.dinglebob.drifterciv;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.TradeRun;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

/**
 * Trade runs between towns (step 4; the user, 2026-10-28: "A trader walks"): every few days, in the morning, a town with a
 * market stall sends its most trader-like person (not the keeper, who keeps the stall) on foot, or horseback if it has a
 * horse, to the nearest other town with a stall, with what its store has plenty of; there it sells what that town's keeper
 * buys and buys what its own town is short of, then brings it all home (the money to the till). Bandits on the road are
 * bandits on the road. The chronicle tells of each run.
 */
final class TradeRuns {
	private TradeRuns() {
	}

	/** A town sends a trader this often (game days), to a town this far at most (blocks). */
	private static final long EVERY = 24000L * 3;
	private static final double FARTHEST = 1600.0;
	/** It sets out between these times of day (morning, so it's there while the other stall is open). */
	private static final long FROM = 200L;
	private static final long UNTIL = 2500L;
	/** What it takes: kinds, and how many of each at most. */
	private static final int KINDS = 3;
	private static final int EACH = 32;
	/** A town's store must have this many of a kind to send some. */
	private static final int SPARE = 24;
	/** Below this its own town is short of a kind it buys. */
	private static final int SHORT = 16;

	/** When each town last sent a trader (not saved: after a restart it may send one sooner). */
	private static final Map<UUID, Long> sent = new HashMap<>();
	/** What each town's trader saw the other town's stall buy and sell, by "from:to" (not saved). */
	private static final Map<String, Set<Item>> buys = new HashMap<>();
	private static final Map<String, Set<Item>> sells = new HashMap<>();

	/** A town's trader, maybe, this check. */
	static void check(MinecraftServer server, Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		long now = server.overworld().getGameTime();
		long time = server.overworld().getDayTime() % 24000L;
		// (A town round it with its market day today: a run there, whatever the days since the last, once a day; C4.)
		Town market = marketToday(town, Chronicle.day(server));
		long since = now - sent.getOrDefault(town.id, Long.MIN_VALUE / 2);
		if (group == null || !town.stallBuilt || town.stallPlot == null || time < FROM || time > UNTIL || since < EVERY && (market == null || since < 24000L)) {
			return;
		}

		// (One of its people on a run already: not another.)
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member != null && DrifterApi.tradeRun(member) != null) {
				return;
			}
		}

		Town other = nearestMarket(town);
		DrifterEntity trader = trader(server, town, group);
		Home home = trader == null ? null : DrifterApi.home(trader);
		if (other == null || trader == null || home == null) {
			return;
		}

		// What it takes: the town's plenty, what that stall bought last time first (else what stalls buy); and what it brings
		// back: what the town is short of.
		String key = town.id + ":" + other.id;
		Set<Item> theyBuy = buys.getOrDefault(key, Set.of());
		Map<Item, Integer> cargo = new LinkedHashMap<>();
		home.stored().entrySet().stream().filter(entry -> (theyBuy.isEmpty() ? Market.wanted().contains(entry.getKey()) : theyBuy.contains(entry.getKey()))
				&& entry.getValue() >= SPARE)
			.sorted(Map.Entry.<Item, Integer>comparingByValue().reversed()).limit(KINDS)
			.forEach(entry -> cargo.put(entry.getKey(), Math.min(EACH, entry.getValue() / 2)));
		Set<Item> wanted = new LinkedHashSet<>();
		for (Item item : Market.wanted()) {
			if (home.stored(item) < SHORT && !cargo.containsKey(item)) {
				wanted.add(item);
			}
		}

		Set<Item> theySell = sells.getOrDefault(key, Set.of());
		if (cargo.isEmpty() && (wanted.isEmpty() || !theySell.isEmpty() && wanted.stream().noneMatch(theySell::contains))) {
			// (Nothing they'd buy, nothing it needs that they sell: not this time.)
			sent.put(town.id, now);
			return;
		}

		if (cargo.isEmpty() && wanted.isEmpty()) {
			return;
		}

		BlockPos there = DrifterApi.stallStand(server, other.stallPlot.origin, other.stallPlot.rotation);
		String from = town.name;
		String to = other.name;
		sent.put(town.id, now);
		DrifterApi.setTradeRun(trader, new TradeRun() {
			@Override
			public BlockPos there() {
				return there;
			}

			@Override
			public String thereName() {
				return to;
			}

			@Override
			public BlockPos home() {
				return town.center;
			}

			@Nullable
			@Override
			public BlockPos till() {
				return town.till();
			}

			@Override
			public Map<Item, Integer> cargo() {
				return cargo;
			}

			@Override
			public Set<Item> wanted() {
				return wanted;
			}

			@Override
			public void saw(DrifterEntity who, Set<Item> theyBuy, Set<Item> theySell) {
				buys.put(key, Set.copyOf(theyBuy));
				sells.put(key, Set.copyOf(theySell));
			}

			@Override
			public void news(DrifterEntity who, String what) {
				String name = who.getName().getString();
				if (what.equals("set out")) {
					Chronicle.add(server, "trade", name + " set out from " + from + " to trade in " + to);
				} else if (what.startsWith("traded: ")) {
					Chronicle.add(server, "trade", name + " of " + from + " traded in " + to + ": " + what.substring(8));
					// (Trade between them warms the two towns a little.)
					Diplomacy.incident(server, town, other, 3.0, null);
				} else if (what.equals("back home")) {
					Chronicle.add(server, "trade", name + " came back to " + from + " from trading in " + to);
				} else {
					Chronicle.add(server, "trade", name + " of " + from + ", trading in " + to + ": " + what);
				}
			}
		});
		DrifterApi.think(trader, "is to take " + (cargo.isEmpty() ? "the town's purse" : cargo.keySet().stream()
			.map(item -> item.getDescription().getString().toLowerCase(java.util.Locale.ROOT)).toList()) + " to " + to + " to trade");
	}

	/** A town round it (in reach of a run) with its market day today, the nearest. */
	@Nullable
	private static Town marketToday(Town town, long day) {
		Town best = null;
		for (Town other : Towns.all()) {
			if (other != town && other.dimension.equals(town.dimension) && TownEvents.marketDay(other, day) && other.center.closerThan(town.center, FARTHEST)
				&& (best == null || other.center.distSqr(town.center) < best.center.distSqr(town.center))) {
				best = other;
			}
		}

		return best;
	}

	/** The nearest other town with a stall within reach, or null. */
	@Nullable
	private static Town nearestMarket(Town town) {
		// A market day round it first (C4).
		if (DrifterCivilizations.server() != null) {
			Town market = marketToday(town, Chronicle.day(DrifterCivilizations.server()));
			if (market != null) {
				return market;
			}
		}

		// A trade deal's partner first (step 6, part 1).
		for (Town partner : Diplomacy.partners(town)) {
			if (partner.dimension.equals(town.dimension) && partner.stallBuilt && partner.stallPlot != null && partner.center.closerThan(town.center, FARTHEST)) {
				return partner;
			}
		}

		Town best = null;
		for (Town other : Towns.all()) {
			if (other != town && other.dimension.equals(town.dimension) && other.stallBuilt && other.stallPlot != null
				&& other.center.closerThan(town.center, FARTHEST) && (best == null || other.center.distSqr(town.center) < best.center.distSqr(town.center))) {
				best = other;
			}
		}

		return best;
	}

	/** Its most trader-like person (greed and sociability, the bolder the better), not the keeper, fit to go. */
	@Nullable
	private static DrifterEntity trader(MinecraftServer server, Town town, DrifterGroups.Group group) {
		DrifterEntity best = null;
		double bestScore = -1.0;
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member == null || id.equals(town.keeper) || member.getHealth() < member.getMaxHealth() * 0.7F) {
				continue;
			}

			double score = DrifterApi.trait(member, "greed") + DrifterApi.trait(member, "sociability") + 0.5 * DrifterApi.trait(member, "bravery");
			if (score > bestScore) {
				best = member;
				bestScore = score;
			}
		}

		return best;
	}
}
