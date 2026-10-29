package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.server.MinecraftServer;

/**
 * A town's leader and laws (step 5, part 1; the user, 2026-10-28: "Founder, then elections"; laws "No stealing, no
 * killing", "Tax", "Farms for citizens only"):
 * <ul>
 *   <li>the founder leads first; every {@link #EVERY} days the town votes. The day before, the leader and up to two of its
 *       most ambitious people (sociable, greedy, bold) stand, and campaign (they talk about it); each of its people votes
 *       for the candidate it trusts most (a candidate for itself). Unopposed, the leader stays;</li>
 *   <li>the leader makes the laws: no stealing and no killing always; a greedy one taxes each household some emeralds every
 *       {@link #TAX_EVERY} days (paid into the till, it pays wages); a cautious one keeps the farms for citizens only.
 *       Breaking them (step 5, part 2) is for the town's guards;</li>
 *   <li>its people talk about it: the election, the candidates, the leader, the tax.</li>
 * </ul>
 */
final class Politics {
	private Politics() {
	}

	static final String NO_STEALING = "no_stealing";
	static final String NO_KILLING = "no_killing";
	static final String TAX = "tax";
	static final String FARMS = "farms_citizens";

	/** Days between elections; the candidates stand the day before. */
	static final long EVERY = 5;
	/** Days between tax levies, and the most tax a household can owe (levies). */
	static final long TAX_EVERY = 3;
	private static final int MOST_LEVIES = 3;
	/** A challenger needs this much ambition to stand. */
	static final double AMBITION = 0.75;
	/** Election day: the vote is in the morning. */
	private static final long VOTE_AT = 1000L;

	/** Its politics this check (leader, laws, election, tax). True if anything changed. */
	static boolean check(MinecraftServer server, Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null || group.members.isEmpty()) {
			return false;
		}

		boolean changed = false;
		long day = Chronicle.day(server);
		long time = server.overworld().getDayTime() % 24000L;
		// Its first leader: its founder (the group's leader). Its leader gone (dead, or left): the group's leader stands in, and
		// the town votes tomorrow morning (step 5, part 3).
		if (town.leader == null || !group.members.contains(town.leader)) {
			String gone = town.leader == null ? null : name(group, town.leader);
			// (Not its crook, if anyone else is there.)
			town.leader = group.members.contains(group.leader) && !town.crooks.contains(group.leader) ? group.leader
				: group.members.stream().filter(id -> !town.crooks.contains(id)).findFirst().orElse(group.members.iterator().next());
			town.leaderSince = day;
			Unrest.newLeader(town, null, town.leader);
			if (gone == null) {
				Chronicle.add(server, "town", name(group, town.leader) + " leads " + town.name);
			} else {
				town.candidates.clear();
				town.nextElection = day + 1;
				Chronicle.add(server, "town", gone + " is gone: " + name(group, town.leader) + " leads " + town.name + " till the vote tomorrow morning");
			}

			changed = true;
		}

		if (town.laws.isEmpty()) {
			DrifterEntity leader = TownFounding.loaded(server, town.leader);
			if (leader != null) {
				laws(server, town, leader);
				changed = true;
			}
		}

		if (town.nextElection < 0) {
			town.nextElection = day + EVERY;
			changed = true;
		}

		// The day before: who stands.
		if (day >= town.nextElection - 1 && town.candidates.isEmpty()) {
			stand(server, town, group, true);
			changed = true;
		}

		// Election day, in the morning: the vote.
		if (day >= town.nextElection && time >= VOTE_AT && !town.candidates.isEmpty()) {
			vote(server, town, group, day);
			changed = true;
		}

		// Tax day: each household owes the tax again (a few levies at most). In revolt: nobody pays till the vote.
		if (town.laws.contains(TAX) && town.tax > 0 && !town.revolt && (town.taxDay < 0 || day - town.taxDay >= TAX_EVERY)) {
			for (UUID member : group.members) {
				town.taxOwed.merge(member, town.tax, (a, b) -> Math.min(town.tax * MOST_LEVIES, a + b));
			}

			Unrest.levied(town, group);
			town.taxDay = day;
			changed = true;
		}

		return Unrest.check(server, town, group, day, time) || changed;
	}

	/** The leader's laws, by its personality. */
	static void laws(MinecraftServer server, Town town, DrifterEntity leader) {
		town.laws.clear();
		town.laws.add(NO_STEALING);
		town.laws.add(NO_KILLING);
		double greed = DrifterApi.trait(leader, "greed");
		town.tax = 0;
		if (greed >= 0.45) {
			town.laws.add(TAX);
			town.tax = 1 + (int)Math.floor((greed - 0.45) * 6.0);
		}

		if (DrifterApi.trait(leader, "caution") >= 0.55 || DrifterApi.trait(leader, "aggression") >= 0.7) {
			town.laws.add(FARMS);
		}

		town.boardDay = -1;
		Chronicle.add(server, "town", leader.getName().getString() + "'s laws for " + town.name + ": " + lawsLong(town));
		DrifterApi.think(leader, "laid down the laws of " + town.name + ": " + lawsLong(town));
	}

	/** Its people's ambition: how keen it is to lead (sociable, greedy, bold). */
	static double ambition(DrifterEntity drifter) {
		return 0.5 * DrifterApi.trait(drifter, "sociability") + 0.4 * DrifterApi.trait(drifter, "greed") + 0.4 * DrifterApi.trait(drifter, "bravery");
	}

	/**
	 * Who stands: the leader (unless it stepped down), and up to two challengers keen enough. With the leader out of it and
	 * nobody keen, the keenest stands anyway.
	 */
	static void stand(MinecraftServer server, Town town, DrifterGroups.Group group, boolean leaderStands) {
		town.candidates.clear();
		// (Its crook never stands: a leader who turned crook isn't voted in again.)
		if (leaderStands && !town.crooks.contains(town.leader)) {
			town.candidates.add(town.leader);
		}

		List<DrifterEntity> keen = new ArrayList<>();
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member != null && !id.equals(town.leader) && !town.crooks.contains(id) && ambition(member) >= AMBITION) {
				keen.add(member);
			}
		}

		keen.sort(Comparator.comparingDouble(Politics::ambition).reversed());
		keen.stream().limit(2).forEach(member -> town.candidates.add(member.getUUID()));
		if (town.candidates.isEmpty()) {
			group.members.stream().filter(id -> !id.equals(town.leader) && !town.crooks.contains(id)).map(id -> TownFounding.loaded(server, id)).filter(java.util.Objects::nonNull)
				.max(Comparator.comparingDouble(Politics::ambition)).ifPresent(one -> town.candidates.add(one.getUUID()));
		}

		if (town.candidates.size() > 1) {
			List<String> names = town.candidates.stream().map(id -> name(group, id)).toList();
			String standing = names.size() == 2 ? names.get(0) + " and " + names.get(1)
				: String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
			Chronicle.add(server, "town", standing + " stand for leader of " + town.name + " (the vote: tomorrow morning)");
			for (UUID id : town.candidates) {
				DrifterEntity one = TownFounding.loaded(server, id);
				if (one != null) {
					DrifterApi.think(one, "is standing for leader of " + town.name);
				}
			}
		}
	}

	/** The vote: each votes for the candidate it trusts most (a candidate for itself); the most votes leads. */
	private static void vote(MinecraftServer server, Town town, DrifterGroups.Group group, long day) {
		List<UUID> standing = List.copyOf(town.candidates);
		town.candidates.clear();
		town.nextElection = day + EVERY;
		town.revolt = false;
		if (standing.size() == 1 && !standing.get(0).equals(town.leader)) {
			// (The leader stepped down; one stood.)
			UUID one = standing.get(0);
			Chronicle.add(server, "town", name(group, one) + " is the new leader of " + town.name + " (nobody stood against it)");
			town.leader = one;
			town.leaderSince = day;
			Unrest.newLeader(town, null, one);
			DrifterEntity leader = TownFounding.loaded(server, one);
			if (leader != null) {
				laws(server, town, leader);
			}

			town.boardDay = -1;
			return;
		}

		if (standing.size() < 2) {
			Chronicle.add(server, "town", name(group, town.leader) + " stays leader of " + town.name + " (nobody stood against it)");
			return;
		}

		Map<UUID, Integer> votes = new LinkedHashMap<>();
		Map<UUID, UUID> choices = new java.util.HashMap<>();
		standing.forEach(id -> votes.put(id, 0));
		for (UUID id : group.members) {
			DrifterEntity voter = TownFounding.loaded(server, id);
			if (voter == null) {
				continue;
			}

			// (Whom it trusts most, give or take its own leanings: friends all alike, it still has a favourite. The leader: what
			// it thinks of how it led, too.)
			Map<UUID, Double> leaning = new java.util.HashMap<>();
			standing.forEach(one -> leaning.put(one, DrifterApi.trustInDrifter(voter, one) + voter.getRandom().nextDouble() * 8.0
				+ (one.equals(town.leader) ? Unrest.approval(town, id) - Unrest.START : 0.0)));
			UUID choice = standing.contains(id) ? id : standing.stream().max(Comparator.comparingDouble(leaning::get)).orElse(null);
			if (choice != null) {
				votes.merge(choice, 1, Integer::sum);
				choices.put(id, choice);
				DrifterApi.think(voter, "voted for " + name(group, choice) + " for leader of " + town.name);
			}
		}

		// The most votes; a tie keeps the leader it had.
		UUID winner = town.leader;
		int best = votes.getOrDefault(town.leader, 0);
		for (Map.Entry<UUID, Integer> entry : votes.entrySet()) {
			if (entry.getValue() > best) {
				winner = entry.getKey();
				best = entry.getValue();
			}
		}

		town.lastVotes.clear();
		votes.forEach((id, n) -> town.lastVotes.put(name(group, id), n));
		String tally = String.join(", ", votes.entrySet().stream().map(entry -> name(group, entry.getKey()) + " " + entry.getValue()).toList());
		if (winner.equals(town.leader)) {
			Chronicle.add(server, "town", name(group, winner) + " was re-elected leader of " + town.name + " (" + tally + ")");
			// (A fresh start: those who voted for it are happier; the rest give it another chance, warily.)
			choices.forEach((voter, choice) -> Unrest.change(town, voter, choice.equals(town.leader) ? 10.0 : 0.0));
		} else {
			Chronicle.add(server, "town", name(group, winner) + " was elected leader of " + town.name + ", in place of " + name(group, town.leader) + " (" + tally + ")");
			town.leader = winner;
			town.leaderSince = day;
			Unrest.newLeader(town, choices, winner);
			DrifterEntity leader = TownFounding.loaded(server, winner);
			if (leader != null) {
				laws(server, town, leader);
			}
		}

		town.boardDay = -1;
	}

	/** It paid tax into the till. */
	static void taxPaid(@Nullable MinecraftServer server, Town town, DrifterEntity drifter, int emeralds) {
		town.taxOwed.merge(drifter.getUUID(), -emeralds, (a, b) -> Math.max(0, a + b));
		town.booksChanged = true;
		// (Nobody likes paying it: a little less fond of the one who set it.)
		if (town.leader != null && !town.leader.equals(drifter.getUUID()) && drifter.level() instanceof net.minecraft.server.level.ServerLevel level
			&& level.getEntity(town.leader) instanceof DrifterEntity leader) {
			DrifterApi.addTrustInDrifter(drifter, leader, -0.5 * emeralds);
		}
	}

	/** What one of its people knows of its town's affairs, for its talk. */
	static void talk(DrifterEntity drifter, Town town, Map<String, Object> words, Set<String> flags) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null || town.leader == null) {
			return;
		}

		long day = DrifterCivilizations.server() == null ? 0 : Chronicle.day(DrifterCivilizations.server());
		words.put("leader", name(group, town.leader));
		if (town.leader.equals(drifter.getUUID())) {
			flags.add("is_leader");
		} else {
			// (What it thinks of how it leads: step 5, part 3.)
			double approval = Unrest.approval(town, drifter.getUUID());
			flags.add(approval >= Unrest.LIKES ? "likes_leader" : approval <= Unrest.UNHAPPY ? "dislikes_leader" : "leader_so_so");
		}

		if (Unrest.mood(town, group).share() >= 1.0 / 3.0) {
			flags.add("unrest");
		}

		if (town.revolt) {
			flags.add("revolt");
		}

		if (!town.upheaval.isEmpty() && day - town.upheavalDay <= Unrest.TALKED_ABOUT) {
			flags.add("after_" + town.upheaval);
			words.put("old_leader", town.oldLeader.isEmpty() ? "the old leader" : town.oldLeader);
		}

		// (Its town's friends and foes, and the latest between them: step 6.)
		Diplomacy.talk(town, day, words, flags);
		// (Its crook caught at it lately: step 5.)
		if (!town.lastCrime.isEmpty() && day - town.lastCrimeDay <= 1 && !town.lastCrook.equals(drifter.getName().getString())) {
			flags.add("crook_" + town.lastCrime);
			words.put("crook", town.lastCrook);
		}

		if (day - town.leaderSince <= 1 && town.leaderSince > town.foundedDay) {
			flags.add("new_leader");
		}

		if (town.candidates.size() > 1) {
			flags.add("election_soon");
			words.put("candidate_a", name(group, town.candidates.get(0)));
			words.put("candidate_b", name(group, town.candidates.get(1)));
			if (town.candidates.contains(drifter.getUUID())) {
				flags.add("candidate");
				town.candidates.stream().filter(id -> !id.equals(drifter.getUUID())).findFirst().ifPresent(rival -> words.put("rival", name(group, rival)));
			}

			// Whom it means to vote for: whom it trusts most of those standing.
			UUID choice = town.candidates.contains(drifter.getUUID()) ? drifter.getUUID()
				: town.candidates.stream().max(Comparator.comparingDouble(one -> DrifterApi.trustInDrifter(drifter, one))).orElse(town.leader);
			words.put("my_vote", name(group, choice));
		}

		if (town.laws.contains(TAX) && town.tax > 0) {
			flags.add("taxed");
			words.put("tax", town.tax);
			if (town.tax >= 3) {
				flags.add("tax_high");
			}
		}

		if (town.laws.contains(FARMS)) {
			flags.add("farms_closed");
		}
	}

	/** Its laws in a few words, for the notice board (15 letters or so). */
	static String lawsShort(Town town) {
		List<String> parts = new ArrayList<>();
		if (town.laws.contains(TAX)) {
			parts.add("tax " + town.tax);
		}

		if (town.laws.contains(FARMS)) {
			parts.add("farms ours");
		}

		return parts.isEmpty() ? "no tax" : String.join(", ", parts);
	}

	/** Its laws in words. */
	static String lawsLong(Town town) {
		List<String> parts = new ArrayList<>();
		parts.add("no stealing");
		parts.add("no killing");
		if (town.laws.contains(TAX)) {
			parts.add("a tax of " + town.tax + " emerald" + (town.tax == 1 ? "" : "s") + " a household every " + TAX_EVERY + " days");
		}

		if (town.laws.contains(FARMS)) {
			parts.add("the farms for citizens only");
		}

		return String.join(", ", parts);
	}

	static String name(DrifterGroups.Group group, @Nullable UUID id) {
		if (id == null) {
			return "nobody";
		}

		String name = group.names.get(id);
		if (name != null) {
			return name;
		}

		var record = DrifterApi.record(id);
		return record == null ? "someone" : record.name();
	}
}
