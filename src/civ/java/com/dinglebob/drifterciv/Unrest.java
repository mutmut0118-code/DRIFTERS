package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.perception.Awareness;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.social.ChatLines;
import com.dinglebob.drifter.social.Lines;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.server.MinecraftServer;

/**
 * Unrest (step 5, part 3; docs/CIVILIZATION.md "Unpopular leaders lose trust; revolts, coups, assassinations,
 * abdications"). Each of a town's people thinks something of its leader ({@link Town#approval}, 0 to 100; not its trust,
 * which living side by side keeps high): a tax levy, wages it's owed and not paid, a killing in the town wear it down;
 * wages paid and a quiet day without a tax bring it back. Once a day, in the afternoon:
 * <ul>
 *   <li>a third of them unhappy: a careful or forgiving leader with a tax drops it (once a term); an ambitious, aggressive
 *       one of them who can't stand it may seize power (a coup: the leader is out, no vote);</li>
 *   <li>half of them unhappy: a humble leader steps down (the others vote tomorrow), a proud one faces a revolt (no tax
 *       paid, a vote tomorrow, the leader standing against them);</li>
 *   <li>a ruthless one who hates the leader plots to kill it: the next time it sees the leader close, armed, it turns on it (it
 *       leaves the town, and they fight as enemies; a killing seen is murder, {@link Justice}).</li>
 * </ul>
 */
final class Unrest {
	private Unrest() {
	}

	/** What a new leader's people think of it at first. */
	static final double START = 60.0;
	/** Unhappy with the leader at or below this; angry enough for a coup; ready to kill. */
	static final double UNHAPPY = 30.0;
	static final double ANGRY = 10.0;
	static final double MURDEROUS = -10.0;
	/** Liking the leader (for talk) at or above this. */
	static final double LIKES = 55.0;
	/** A levy: this much less per emerald of tax. */
	private static final double LEVY = 3.0;
	/** A day owed this much in wages (points: 3 emeralds) and not paid: this much less. */
	private static final double OWED_A_LOT = 18.0;
	private static final double UNPAID = 2.0;
	/** A day without a tax: a little better (up to {@link #CONTENT}). */
	private static final double QUIET_DAY = 1.0;
	private static final double CONTENT = 75.0;
	/** Wages paid: this much better per emerald. */
	private static final double PAID = 1.0;
	/** One of its people killed: the leader couldn't keep them safe. */
	private static final double KILLED = 5.0;
	/** Dropping the tax. */
	private static final double CONCESSION = 15.0;
	/** Days between upheavals; how long the town talks about one. */
	private static final long CALM_DAYS = 2;
	static final long TALKED_ABOUT = 2;
	/** The daily check: in the afternoon. */
	private static final long AT = 6000L;
	/** A plotter turns on the leader this close (and seeing it). */
	private static final double STRIKE_RANGE = 24.0;
	/** The plotter saw the leader this lately (ticks: a town check's interval). */
	private static final long SEEN_WITHIN = 200;
	/** Its people's trust in one who turned on the leader: this much less. */
	private static final double SHUNNED = 70.0;
	/** A coup: a chance a day, once one could. */
	private static final double COUP_CHANCE = 0.5;

	/** What one of its people thinks of the leader. */
	static double approval(Town town, UUID member) {
		return town.approval.getOrDefault(member, START);
	}

	static void change(Town town, UUID member, double delta) {
		town.approval.put(member, Math.max(-100.0, Math.min(100.0, approval(town, member) + delta)));
	}

	/** Its people (all but the leader). */
	private static List<UUID> people(Town town, DrifterGroups.Group group) {
		return group.members.stream().filter(id -> !id.equals(town.leader)).toList();
	}

	/** How many of its people are unhappy with the leader, of how many, and what they think on average. */
	record Mood(int people, int unhappy, double average) {
		double share() {
			return this.people == 0 ? 0.0 : (double)this.unhappy / this.people;
		}
	}

	static Mood mood(Town town, DrifterGroups.Group group) {
		List<UUID> people = people(town, group);
		int unhappy = (int)people.stream().filter(id -> approval(town, id) <= UNHAPPY).count();
		double average = people.stream().mapToDouble(id -> approval(town, id)).average().orElse(START);
		return new Mood(people.size(), unhappy, average);
	}

	/** A tax levied: every household thinks less of the one who set it. */
	static void levied(Town town, DrifterGroups.Group group) {
		people(town, group).forEach(id -> change(town, id, -LEVY * town.tax));
	}

	/** Wages paid (points). */
	static void wagesPaid(Town town, DrifterEntity drifter, double points) {
		change(town, drifter.getUUID(), PAID * points / 6.0);
	}

	/** One of its people killed. */
	static void killed(Town town, DrifterGroups.Group group) {
		people(town, group).forEach(id -> change(town, id, -KILLED));
	}

	/** A new leader: what they think of it, by how they voted (null: as at first). */
	static void newLeader(Town town, @Nullable java.util.Map<UUID, UUID> votes, UUID winner) {
		town.approval.clear();
		town.conceded = false;
		town.revolt = false;
		town.plotter = null;
		if (votes != null) {
			votes.forEach((voter, choice) -> town.approval.put(voter, choice.equals(winner) ? 70.0 : 45.0));
		}
	}

	/** Its unrest this check: the plotter's chance, and once a day its people's mood. True if anything changed. */
	static boolean check(MinecraftServer server, Town town, DrifterGroups.Group group, long day, long time) {
		if (town.leader == null) {
			return false;
		}

		boolean changed = strike(server, town, group);
		if (town.moodDay == day || time < AT) {
			return changed;
		}

		town.moodDay = day;
		// The day's grievances.
		for (UUID id : people(town, group)) {
			if (town.owed.getOrDefault(id, 0.0) >= OWED_A_LOT) {
				change(town, id, -UNPAID);
			} else if (!town.laws.contains(Politics.TAX) && approval(town, id) < CONTENT) {
				change(town, id, QUIET_DAY);
			}
		}

		today(server, town, group, day, false);
		return true;
	}

	/** What comes of the mood today (debug: {@code force}, at once and whatever the last upheaval). */
	static String today(MinecraftServer server, Town town, DrifterGroups.Group group, long day, boolean force) {
		if (town.revolt || town.candidates.size() > 1) {
			return "an election on";
		}

		if (!force && day - town.upheavalDay < CALM_DAYS) {
			return "calm (an upheaval lately)";
		}

		Mood mood = mood(town, group);
		DrifterEntity leader = TownFounding.loaded(server, town.leader);
		List<DrifterEntity> loaded = new ArrayList<>();
		for (UUID id : people(town, group)) {
			DrifterEntity one = TownFounding.loaded(server, id);
			if (one != null) {
				loaded.add(one);
			}
		}

		// A ruthless one who hates the leader: it plots (nobody knows).
		if (town.plotter == null) {
			loaded.stream().filter(one -> approval(town, one.getUUID()) <= MURDEROUS && ruthless(one)).findFirst().ifPresent(one -> {
				town.plotter = one.getUUID();
				DrifterApi.think(one, "is going to get rid of " + name(group, town.leader) + " itself");
			});
		}

		if (mood.share() < 1.0 / 3.0 || mood.people < 2) {
			return "calm (" + mood.unhappy + " of " + mood.people + " unhappy)";
		}

		// An ambitious, aggressive one who can't stand it: it seizes power.
		DrifterEntity usurper = loaded.stream().filter(one -> approval(town, one.getUUID()) <= ANGRY && Politics.ambition(one) >= Politics.AMBITION
			&& DrifterApi.trait(one, "aggression") >= 0.6).max(Comparator.comparingDouble(Politics::ambition)).orElse(null);
		if (usurper != null && (force || usurper.getRandom().nextDouble() < COUP_CHANCE)) {
			coup(server, town, group, usurper, day);
			return "a coup";
		}

		// A careful or forgiving leader drops the tax (once a term).
		if (leader != null && town.laws.contains(Politics.TAX) && !town.conceded
			&& (DrifterApi.trait(leader, "caution") >= 0.5 || DrifterApi.trait(leader, "forgiveness") >= 0.6)) {
			concede(server, town, group, leader, day);
			return "the tax dropped";
		}

		if (mood.share() < 0.5) {
			return "grumbling (" + mood.unhappy + " of " + mood.people + " unhappy)";
		}

		// Half of them: a humble leader steps down, a proud one faces a revolt.
		if (leader != null && pride(leader) < 0.5) {
			abdicate(server, town, group, leader, day);
			return "the leader stepped down";
		}

		revolt(server, town, group, loaded, mood, day);
		return "a revolt";
	}

	/** Ruthless enough to kill its leader: a bandit, or aggressive, vengeful and unforgiving. */
	private static boolean ruthless(DrifterEntity drifter) {
		return drifter.getPersonality().archetype() == Archetype.BANDIT || DrifterApi.trait(drifter, "aggression") >= 0.7
			&& DrifterApi.trait(drifter, "vengefulness") >= 0.6 && DrifterApi.trait(drifter, "forgiveness") <= 0.3;
	}

	/** Too proud to step down: aggressive and greedy. */
	private static double pride(DrifterEntity leader) {
		return (DrifterApi.trait(leader, "aggression") + DrifterApi.trait(leader, "greed")) / 2.0;
	}

	private static void upheaval(Town town, String what, long day, @Nullable UUID oldLeader, DrifterGroups.Group group) {
		town.upheaval = what;
		town.upheavalDay = day;
		town.oldLeader = oldLeader == null ? "" : name(group, oldLeader);
		town.boardDay = -1;
	}

	private static void coup(MinecraftServer server, Town town, DrifterGroups.Group group, DrifterEntity usurper, long day) {
		UUID old = town.leader;
		String oldName = name(group, old);
		ChatLines.speak(usurper, Lines.pick(usurper, "unrest.coup", "leader", oldName, "town", town.name));
		Chronicle.add(server, "town", usurper.getName().getString() + " seized power in " + town.name + ": " + oldName + " is leader no more");
		DrifterApi.think(usurper, "took over " + town.name + " from " + oldName);
		DrifterEntity deposed = TownFounding.loaded(server, old);
		if (deposed != null) {
			DrifterApi.think(deposed, usurper.getName().getString() + " took " + town.name + " from it");
			DrifterApi.addTrustInDrifter(deposed, usurper, -40.0);
		}

		// Those who liked the old leader don't like this; those who didn't are glad.
		java.util.Map<UUID, Double> before = new java.util.HashMap<>(town.approval);
		town.leader = usurper.getUUID();
		town.leaderSince = day;
		town.candidates.clear();
		town.nextElection = day + Politics.EVERY;
		newLeader(town, null, usurper.getUUID());
		for (UUID id : people(town, group)) {
			town.approval.put(id, before.getOrDefault(id, START) >= LIKES ? 20.0 : 65.0);
		}

		Politics.laws(server, town, usurper);
		upheaval(town, "coup", day, old, group);
	}

	private static void concede(MinecraftServer server, Town town, DrifterGroups.Group group, DrifterEntity leader, long day) {
		ChatLines.speak(leader, Lines.pick(leader, "unrest.concede", "town", town.name));
		town.laws.remove(Politics.TAX);
		town.tax = 0;
		town.taxOwed.clear();
		town.conceded = true;
		people(town, group).forEach(id -> change(town, id, CONCESSION));
		Chronicle.add(server, "town", leader.getName().getString() + " dropped the tax to calm the people of " + town.name);
		DrifterApi.think(leader, "dropped the tax: the people were unhappy");
		upheaval(town, "tax_dropped", day, null, group);
	}

	private static void abdicate(MinecraftServer server, Town town, DrifterGroups.Group group, DrifterEntity leader, long day) {
		ChatLines.speak(leader, Lines.pick(leader, "unrest.abdicate", "town", town.name));
		Chronicle.add(server, "town", leader.getName().getString() + " stepped down as leader of " + town.name + " (the vote: tomorrow morning)");
		DrifterApi.think(leader, "stepped down as leader of " + town.name);
		town.nextElection = day + 1;
		Politics.stand(server, town, group, false);
		upheaval(town, "abdicated", day, town.leader, group);
	}

	private static void revolt(MinecraftServer server, Town town, DrifterGroups.Group group, List<DrifterEntity> loaded, Mood mood, long day) {
		String leaderName = name(group, town.leader);
		town.revolt = true;
		town.taxOwed.clear();
		town.nextElection = day + 1;
		Politics.stand(server, town, group, true);
		// (Someone has to stand against it: the boldest of the unhappy.)
		if (town.candidates.size() < 2) {
			loaded.stream().filter(one -> approval(town, one.getUUID()) <= UNHAPPY).max(Comparator.comparingDouble(Politics::ambition))
				.ifPresent(one -> town.candidates.add(one.getUUID()));
		}

		loaded.stream().filter(one -> approval(town, one.getUUID()) <= UNHAPPY).findFirst()
			// (Not mid-fight, a war on: once it's out of it.)
			.ifPresent(one -> com.dinglebob.drifter.social.ChatReplies.afterFight(one, Lines.pick(one, "unrest.revolt", "leader", leaderName, "town", town.name), true));
		String against = town.candidates.size() > 1 ? ": " + String.join(" against ", town.candidates.stream().map(id -> name(group, id)).toList()) : "";
		Chronicle.add(server, "town", "The people of " + town.name + " rose up against " + leaderName + " (" + mood.unhappy + " of " + mood.people
			+ " unhappy; no tax paid till the vote tomorrow morning" + against + ")");
		upheaval(town, "revolt", day, null, group);
	}

	/**
	 * The plotter, if it sees the leader close: it turns on it. It's out of the town (one of its own wouldn't count as an
	 * enemy) and they're enemies both ways, so they fight as any enemies would. With Drifters set never to fight each other,
	 * it just leaves.
	 */
	private static boolean strike(MinecraftServer server, Town town, DrifterGroups.Group group) {
		if (town.plotter == null) {
			return false;
		}

		if (!group.members.contains(town.plotter) || town.plotter.equals(town.leader)) {
			town.plotter = null;
			return true;
		}

		DrifterEntity plotter = TownFounding.loaded(server, town.plotter);
		DrifterEntity leader = TownFounding.loaded(server, town.leader);
		// (Armed and well: found, unarmed it turned on the leader and then thought better of a fight.)
		if (plotter == null || leader == null || plotter.level() != leader.level() || plotter.distanceTo(leader) > STRIKE_RANGE || !Justice.armed(plotter)
			|| plotter.getHealth() < plotter.getMaxHealth() * 0.75F) {
			return false;
		}

		Awareness seen = plotter.getSenses().awarenessOf(leader);
		// (Seen since the last check: found, looking right at it the moment the town checks was rare.)
		if (seen == null || !seen.identified || plotter.level().getGameTime() - seen.lastSeenTick > SEEN_WITHIN) {
			return false;
		}

		String leaderName = leader.getName().getString();
		town.plotter = null;
		// (Out of the town, and its people want nothing more to do with it: not friends enough to take it back. Found: it
		// joined again at once, so the leader killing it in the fight was murder of one of its own.)
		List<DrifterEntity> people = new ArrayList<>();
		for (UUID id : group.members) {
			DrifterEntity one = id.equals(plotter.getUUID()) ? null : TownFounding.loaded(server, id);
			if (one != null) {
				people.add(one);
			}
		}

		DrifterGroups.leave(plotter.getUUID());
		// (Its house in the town isn't its home any more. Found: it came back to life there and went at the leader again.)
		DrifterApi.setHome(plotter, null);
		for (DrifterEntity one : people) {
			DrifterApi.addTrustInDrifter(one, plotter, -SHUNNED);
		}

		long day = Chronicle.day(server);
		if (com.dinglebob.drifter.DrifterConfig.drifterAggression() <= 0) {
			Chronicle.add(server, "town", plotter.getName().getString() + " left " + town.name + ", hating " + leaderName);
			DrifterApi.think(plotter, "left " + town.name + ": couldn't stand " + leaderName);
			return true;
		}

		ChatLines.speak(plotter, Lines.pick(plotter, "unrest.betray", "leader", leaderName, "town", town.name));
		DrifterApi.setTrustInDrifter(plotter, leader, -100.0);
		DrifterApi.setTrustInDrifter(leader, plotter, -100.0);
		DrifterApi.think(plotter, "turned on " + leaderName);
		Chronicle.add(server, "town", plotter.getName().getString() + " turned on " + leaderName + ", leader of " + town.name + "!");
		upheaval(town, "betrayed", day, null, group);
		return true;
	}

	/** (Debug: `/drifterciv town unrest plot`.) The one who thinks least of the leader plots, ruthless or not. */
	static String debugPlot(MinecraftServer server, Town town, DrifterGroups.Group group) {
		DrifterEntity worst = null;
		for (UUID id : people(town, group)) {
			DrifterEntity one = TownFounding.loaded(server, id);
			if (one != null && (worst == null || approval(town, id) < approval(town, worst.getUUID()))) {
				worst = one;
			}
		}

		if (worst == null) {
			return "none of its people loaded";
		}

		town.plotter = worst.getUUID();
		DrifterApi.think(worst, "is going to get rid of " + name(group, town.leader) + " itself");
		return worst.getName().getString() + " plots against " + name(group, town.leader);
	}

	/** Its unrest in a line, for `/drifterciv town`. */
	static String status(Town town, DrifterGroups.Group group) {
		Mood mood = mood(town, group);
		String recent = town.upheaval.isEmpty() ? "" : "; last: " + town.upheaval.replace('_', ' ') + " (day " + town.upheavalDay + ")";
		return "Mood: " + mood.unhappy + " of " + mood.people + " unhappy with " + name(group, town.leader) + " (approval " + Math.round(mood.average) + ")"
			+ (town.revolt ? "; in revolt" : "") + recent;
	}

	static String name(DrifterGroups.Group group, @Nullable UUID id) {
		return Politics.name(group, id);
	}
}
