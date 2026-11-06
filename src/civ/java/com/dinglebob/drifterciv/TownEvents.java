package com.dinglebob.drifterciv;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * A town's events (milestone 23, docs/LIVED_WORLD.md C4):
 * <ul>
 *   <li><b>Market day</b> (every {@link #MARKET_EVERY} days, a different day for each town): its keeper keeps the stall
 *       all day, its people come by to see what's for sale (the {@code market} whims), the towns round it send their
 *       traders that day ({@link TradeRuns}); the keeper calls it out in the morning, the chronicle has it, people talk of
 *       it.</li>
 *   <li><b>A fishing contest</b> (every {@link #CONTEST_EVERY} days, in a town by water): everyone with a rod goes fishing
 *       that day; whoever has the most fish by the evening wins, and the town hears of it.</li>
 * </ul>
 * Festivals are the town's culture ({@link TownCulture}).
 */
final class TownEvents {
	private TownEvents() {
	}

	static final int MARKET_EVERY = 4;
	static final int CONTEST_EVERY = 12;
	private static final java.util.Set<Item> FISH = java.util.Set.of(Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH);
	/** A contest's fish counts at its start, by town and person. */
	private static final Map<UUID, Map<UUID, Integer>> contests = new HashMap<>();

	/** Its market day today (a town with a stall up). */
	static boolean marketDay(Town town, long day) {
		return town.stallBuilt && town.stallPlot != null && (Math.floorMod(day + (town.id.hashCode() & 7), MARKET_EVERY) == 0 || town.forcedMarketDay == day);
	}

	/** Its fishing contest today (a town by water: a harbour, a river town, a pier). */
	static boolean contestDay(Town town, long day) {
		return town.byWater && Math.floorMod(day + (town.id.hashCode() >>> 3 & 15), CONTEST_EVERY) == 0;
	}

	/** How much more this counts for it today: the keeper at its stall on market day, fishing on a contest day. */
	static double leaning(Town town, DrifterEntity drifter, String goal, long day) {
		double more = 0.0;
		if (goal.equals("stall") && marketDay(town, day)) {
			more += 0.3;
		}

		if (goal.equals("fish") && contestDay(town, day) && contests.containsKey(town.id)) {
			more += 0.4;
		}

		return more;
	}

	/** Each check: the morning's call, the contest's start and end. */
	static void check(MinecraftServer server, Town town) {
		if (!town.spawned || town.group == null || server.overworld() == null) {
			return;
		}

		long day = Chronicle.day(server);
		long time = server.overworld().getDayTime() % 24000L;
		DrifterGroups.Group group = DrifterApi.groupById(town.group);
		if (group == null) {
			return;
		}

		if (time >= 1000L && time < 3000L && town.eventDay < day) {
			town.eventDay = day;
			if (marketDay(town, day)) {
				DrifterEntity keeper = town.keeper == null ? null : TownFounding.loaded(server, town.keeper);
				if (keeper != null) {
					com.dinglebob.drifter.social.ChatLines.speak(keeper, pick(keeper, "market day! come see whats for sale", "market's open, come on by",
						"its market day, best prices all week", "market day today!"));
				}

				Chronicle.add(server, "market", "Market day in " + town.name);
			}

			if (contestDay(town, day)) {
				// (Those with a rod; fewer than two, no contest.)
				Map<UUID, Integer> start = new HashMap<>();
				for (UUID id : group.members) {
					DrifterEntity one = TownFounding.loaded(server, id);
					if (one != null && one.getInventory().count(stack -> stack.is(Items.FISHING_ROD)) > 0) {
						start.put(id, fish(one));
					}
				}

				if (start.size() >= 2) {
					contests.put(town.id, start);
					Chronicle.add(server, "town", town.name + "'s fishing contest: " + start.size() + " out with their rods, the most fish by evening wins");
				}
			}

			Towns.save(server);
		}

		Map<UUID, Integer> start = contests.get(town.id);
		if (start != null && time >= 11500L) {
			contests.remove(town.id);
			UUID best = null;
			int most = 0;
			for (var entry : start.entrySet()) {
				DrifterEntity one = TownFounding.loaded(server, entry.getKey());
				int caught = one == null ? 0 : fish(one) - entry.getValue();
				if (caught > most) {
					most = caught;
					best = entry.getKey();
				}
			}

			String name = best == null ? null : group.names.getOrDefault(best, "?");
			if (name == null) {
				Chronicle.add(server, "town", "Nobody caught a thing in " + town.name + "'s fishing contest");
			} else {
				DrifterEntity winner = TownFounding.loaded(server, best);
				if (winner != null) {
					com.dinglebob.drifter.social.ChatLines.speak(winner, pick(winner, "i won the fishing contest!! " + most + " fish", "ez, " + most + " fish",
						"fishing champ right here", "gg everyone"));
				}

				town.contestWinner = name;
				town.contestDay = day;
				Chronicle.add(server, "town", name + " won " + town.name + "'s fishing contest with " + most + (most == 1 ? " fish" : " fish"));
			}

			Towns.save(server);
		}
	}

	private static int fish(DrifterEntity one) {
		return one.getInventory().count(stack -> FISH.contains(stack.getItem()));
	}

	private static String pick(DrifterEntity one, String... lines) {
		return lines[one.getRandom().nextInt(lines.length)];
	}

	/** Its people's talk: market day, the contest and who won. */
	static void talk(Town town, long day, Map<String, Object> words, java.util.Set<String> flags) {
		if (marketDay(town, day)) {
			flags.add("market_day");
		}

		if (contestDay(town, day)) {
			flags.add("contest_day");
		}

		if (town.contestWinner != null && day - town.contestDay <= 2) {
			flags.add("contest_won");
			words.put("winner", town.contestWinner);
		}
	}

	/** Where the market is, on market day (its stall); else null. */
	@Nullable
	static net.minecraft.core.BlockPos market(Town town, DrifterEntity drifter) {
		long day = drifter.getServer() == null ? 0 : Chronicle.day(drifter.getServer());
		return marketDay(town, day) ? TownDay.place(town, drifter, "stall") : null;
	}
}
