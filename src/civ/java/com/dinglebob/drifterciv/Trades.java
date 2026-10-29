package com.dinglebob.drifterciv;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

/**
 * Lines of work (milestone 23, docs/LIVED_WORLD.md B1: "each townsperson takes up a line by how it plays and what the town
 * needs ... a line is a leaning, not a cage: everyone still eats, sleeps, chats and does whims"): farmer, builder, miner,
 * hunter, woodcutter, guard, and the stall's keeper. Its place first (the keeper, the guards), then its nature (a builder
 * builds, a miner mines, a homebody farms...), then what the town is short of (at least two farmers; the rest to whichever
 * line has fewest). In what it chooses to do, the things of its line count a little more ({@link #leaning}).
 */
final class Trades {
	private Trades() {
	}

	/** Each line's goals (by id): what it leans toward. */
	private static final Map<String, Set<String>> GOALS = Map.of(
		"farmer", Set.of("farm", "pen", "cook", "food"),
		"builder", Set.of("roads", "townbuild", "ease_ground", "light", "house", "planhouse", "scaffold", "tidy"),
		"miner", Set.of("progress", "cavehunch", "store"),
		"hunter", Set.of("hunt", "nighthunt", "prey"),
		"woodcutter", Set.of("clear_trees", "collect"),
		"guard", Set.of("guard", "defend", "helpfight", "driveout"));
	/** How much more a thing of its line counts (urgency, 0 to about 1.3). */
	private static final double LEANING = 0.08;
	/** Lines filled by need, in this order when they're even. */
	private static final List<String> BY_NEED = List.of("farmer", "woodcutter", "hunter", "builder", "miner");

	/** Its line in its town: the one it has, or one given it now. */
	static String of(Town town, DrifterEntity drifter) {
		String line = town.lines.get(drifter.getUUID());
		if (line == null) {
			line = choose(town, drifter);
			town.lines.put(drifter.getUUID(), line);
		}

		return line;
	}

	private static String choose(Town town, DrifterEntity drifter) {
		UUID id = drifter.getUUID();
		if (id.equals(town.keeper)) {
			return "keeper";
		}

		if (id.equals(town.leader)) {
			return "leader";
		}

		long guards = town.lines.values().stream().filter("guard"::equals).count();
		if (guards < 2 && DrifterApi.trait(drifter, "bravery") + DrifterApi.trait(drifter, "aggression") >= 1.3) {
			return "guard";
		}

		long farmers = town.lines.values().stream().filter("farmer"::equals).count();
		if (farmers < 2) {
			return "farmer";
		}

		String nature = switch (drifter.getPersonality().archetype()) {
			case BUILDER -> "builder";
			case MINER, HOARDER, SPEEDRUNNER -> "miner";
			case HOMEBODY, HELPER -> "farmer";
			case HERMIT -> "woodcutter";
			case NOMAD, PVP_SWEAT -> "hunter";
			default -> null;
		};
		// (By nature while its line isn't a quarter of the town already: found in a town of 24, six builders.)
		String byNature = nature;
		var group = town.group == null ? null : DrifterApi.groupById(town.group);
		int people = group == null ? 8 : group.members.size();
		if (byNature != null && town.lines.values().stream().filter(byNature::equals).count() < Math.max(2, people / 4)) {
			return byNature;
		}

		Map<String, Integer> count = new HashMap<>();
		BY_NEED.forEach(line -> count.put(line, 0));
		town.lines.values().forEach(line -> count.computeIfPresent(line, (k, v) -> v + 1));
		return BY_NEED.stream().min(java.util.Comparator.comparingInt(count::get)).orElse("farmer");
	}

	/** How much more this goal counts for it, by its line. */
	static double leaning(Town town, DrifterEntity drifter, String goal) {
		Set<String> goals = GOALS.get(of(town, drifter));
		return goals != null && goals.contains(goal) ? LEANING : 0.0;
	}

	/** Who does what, for /drifterciv town: "farmer: a, b; builder: c". */
	static String describe(Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null || town.lines.isEmpty()) {
			return "no lines of work yet";
		}

		Map<String, List<String>> by = new java.util.TreeMap<>();
		town.lines.forEach((id, line) -> {
			if (group.members.contains(id)) {
				by.computeIfAbsent(line, k -> new java.util.ArrayList<>()).add(group.names.getOrDefault(id, "?"));
			}
		});
		return String.join("; ", by.entrySet().stream().map(e -> e.getKey() + ": " + String.join(", ", e.getValue())).toList());
	}

	/** Its line, for talk ({@code line_<line>}, word {@code line}). */
	static void talk(@Nullable Town town, DrifterEntity drifter, Map<String, Object> words, java.util.Set<String> flags) {
		if (town == null) {
			return;
		}

		String line = of(town, drifter);
		flags.add("line_" + line);
		words.put("line", line);
	}
}
