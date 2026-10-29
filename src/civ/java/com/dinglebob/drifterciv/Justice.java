package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.social.SocialEvent;
import com.dinglebob.drifter.world.DrifterGroups;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;

/**
 * A town's justice (step 5, part 2; the user, 2026-10-28: "Guards and fines", "Exile", "Bounties", prison later): its
 * laws broken where its people see it, and what comes of it.
 * <ul>
 *   <li>Stealing from its people's chests (the town's store too), or, with the farms law, an outsider killing its animals or
 *       trampling its crops: a fine (3 emeralds for a theft, 1 for the farms, more each time), collected by one of its
 *       guards (its two boldest with a weapon), who walks up and asks for it. Not paid, or a third offence: exile.</li>
 *   <li>Killing one of its people: a player is exiled for a week and a price is put on their head (the bounty hunters come);
 *       one of its own people is thrown out of the town; an outsider Drifter is its people's enemy.</li>
 *   <li>Exiled: its people drive them out of the town (not past it) until it's over.</li>
 * </ul>
 * Only what its people saw (or found: valuables gone from a chest after someone was seen at it).
 */
final class Justice {
	private Justice() {
	}

	private static final int FINE_THEFT = 3;
	private static final int FINE_FARMS = 1;
	/** Offences before it's exile instead of a fine. */
	private static final int REPEAT = 3;
	/** Exile (days): for an unpaid fine or offending again, for a killing. */
	private static final long EXILE = 3;
	private static final long MURDER_EXILE = 7;
	/** How close to the town a culprit has to be for a guard to go after them, and a citizen's bed to its middle. */
	private static final double IN_TOWN = 80.0;
	/** Witnesses within this of a killing. */
	private static final double WITNESS = 32.0;
	/** The same deed seen by several of them within this is one crime (ticks). */
	private static final long SAME_CRIME = 20L * 60;

	/** A town's guards, picked each check (not saved), and which guard is after which culprit since when. */
	private static final Map<UUID, List<UUID>> guards = new HashMap<>();
	private static final Map<UUID, Map.Entry<UUID, Long>> chasing = new HashMap<>();

	static void register() {
		// (A town takes no Bandits in: the user, 2026-10-28.)
		DrifterApi.registerJoinRule(TownFounding::mayJoin);
		DrifterEvents.PLAYER_DEED.register((drifter, player, deed, trustAfter) -> {
			Town town = townOf(drifter);
			if (town == null || !(player instanceof ServerPlayer serverPlayer)) {
				return;
			}

			switch (deed) {
				case CAUGHT_STEALING, TOOK_VALUABLES -> crime(serverPlayer.server, town, serverPlayer, "theft", drifter);
				case KILLED_FARM_ANIMAL, TRAMPLED_CROPS -> {
					if (town.laws.contains(Politics.FARMS) && !citizen(serverPlayer, town)) {
						crime(serverPlayer.server, town, serverPlayer, "farms", drifter);
					}
				}
				default -> {
				}
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!(victim instanceof DrifterEntity dead) || dead.isTemporary() || !(victim.level() instanceof ServerLevel level)) {
				return;
			}

			Town town = townOf(dead);
			DrifterEntity drifterKiller = source.getEntity() instanceof DrifterEntity d ? d : com.dinglebob.drifter.hands.FakePlayerBridge.ownerOf(source.getEntity());
			Player playerKiller = drifterKiller == null && source.getEntity() instanceof ServerPlayer p ? p : null;
			if (town == null || drifterKiller == null && playerKiller == null) {
				return;
			}

			// (Self-defence isn't murder: it came at them first. Found: a leader killing the one who turned on it.)
			var killer = playerKiller != null ? playerKiller : drifterKiller;
			if (com.dinglebob.drifter.hands.FakePlayerBridge.ownerOf(killer.getLastHurtByMob()) == dead || dead.getTarget() == killer) {
				return;
			}

			// Someone of the town saw it.
			DrifterEntity witness = null;
			for (DrifterEntity one : level.getEntitiesOfClass(DrifterEntity.class, dead.getBoundingBox().inflate(WITNESS), d -> d.isAlive() && d != dead && d != killer
				&& town.group != null && DrifterGroups.of(d.getUUID()) != null && town.group.equals(DrifterGroups.of(d.getUUID()).id))) {
				var seen = one.getSenses().awarenessOf(killer);
				if (seen != null && seen.identified) {
					witness = one;
					break;
				}
			}

			if (witness == null) {
				return;
			}

			murder(level.getServer(), town, dead, playerKiller instanceof ServerPlayer p ? p : null, drifterKiller, witness);
		});
	}

	/** Its exiles over, and its guards picked. True if anything changed. */
	static boolean check(MinecraftServer server, Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return false;
		}

		boolean changed = false;
		long day = Chronicle.day(server);
		for (Town.Criminal criminal : town.crimes.values()) {
			if (criminal.exiledUntil > 0 && day >= criminal.exiledUntil) {
				criminal.exiledUntil = 0;
				criminal.offences = 0;
				Chronicle.add(server, "law", criminal.name + " may come back to " + town.name + ": the exile is over");
				changed = true;
			}
		}

		// Thrown-out crooks out of the town: Bandits again.
		leaving.entrySet().removeIf(entry -> {
			DrifterEntity gone = TownFounding.loaded(server, entry.getKey());
			if (gone == null || gone.blockPosition().closerThan(entry.getValue(), WELL_CLEAR)) {
				return false;
			}

			DrifterApi.turnBandit(gone);
			DrifterApi.think(gone, "out of that town for good. no more pretending");
			return true;
		});

		// Its guards: its two boldest with a weapon (not its leader).
		List<DrifterEntity> fit = new ArrayList<>();
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member != null && !id.equals(town.leader) && member.getInventory().count(stack -> stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) > 0) {
				fit.add(member);
			}
		}

		fit.sort(Comparator.comparingDouble((DrifterEntity member) -> DrifterApi.trait(member, "bravery") + DrifterApi.trait(member, "aggression")).reversed());
		guards.put(town.id, fit.stream().limit(2).map(DrifterEntity::getUUID).toList());
		return changed;
	}

	/** A law broken by a player, seen by one of its people. */
	private static void crime(MinecraftServer server, Town town, ServerPlayer player, String kind, DrifterEntity witness) {
		long now = server.overworld().getGameTime();
		Town.Criminal criminal = town.crimes.computeIfAbsent(player.getUUID(), id -> new Town.Criminal(player.getGameProfile().getName()));
		if (criminal.exiledUntil > 0 || now - criminal.lastAt < SAME_CRIME && kind.equals(criminal.crime)) {
			return;
		}

		criminal.offences++;
		criminal.lastAt = now;
		criminal.crime = kind;
		String what = kind.equals("theft") ? "stealing" : "harming the town's farms";
		com.dinglebob.drifter.social.ChatLines.speak(witness, com.dinglebob.drifter.social.Lines.pick(witness, "crime." + kind, "player", criminal.name));
		if (criminal.offences >= REPEAT) {
			exile(server, town, criminal, EXILE, what + " again (" + criminal.offences + " times)");
		} else {
			criminal.fine += (kind.equals("theft") ? FINE_THEFT : FINE_FARMS) + (criminal.offences - 1);
			Chronicle.add(server, "law", criminal.name + " was caught " + what + " in " + town.name + " (a fine of " + criminal.fine + " emeralds)");
		}

		Towns.save(server);
	}

	/** One of its people killed, and seen. */
	private static void murder(MinecraftServer server, Town town, DrifterEntity dead, @Nullable ServerPlayer player, @Nullable DrifterEntity drifter,
		DrifterEntity witness) {
		String victim = dead.getName().getString();
		DrifterGroups.Group townGroup = town.group == null ? null : DrifterApi.groupById(town.group);
		if (townGroup != null) {
			// (The leader couldn't keep them safe: step 5, part 3.)
			Unrest.killed(town, townGroup);
		}

		if (dead.getUUID().equals(town.leader)) {
			Chronicle.add(server, "law", victim + ", leader of " + town.name + ", was murdered by "
				+ (player != null ? player.getGameProfile().getName() : drifter != null ? drifter.getName().getString() : "someone"));
		}

		if (player != null) {
			Town.Criminal criminal = town.crimes.computeIfAbsent(player.getUUID(), id -> new Town.Criminal(player.getGameProfile().getName()));
			criminal.offences++;
			criminal.crime = "murder";
			com.dinglebob.drifter.social.ChatLines.speak(witness, com.dinglebob.drifter.social.Lines.pick(witness, "crime.murder", "player", criminal.name,
				"who", victim));
			exile(server, town, criminal, MURDER_EXILE, "killing " + victim);
			if (DrifterApi.postBounty(player, "the people of " + town.name, "the killing of " + victim)) {
				Chronicle.add(server, "law", "The people of " + town.name + " put a price on " + criminal.name + "'s head for killing " + victim);
			}
		} else if (drifter != null) {
			String name = drifter.getName().getString();
			DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
			if (group != null && group.members.contains(drifter.getUUID())) {
				// One of its own: out of the town.
				DrifterGroups.leave(drifter.getUUID());
				Chronicle.add(server, "law", name + " was thrown out of " + town.name + " for killing " + victim);
				DrifterApi.think(drifter, "was thrown out of " + town.name + " for killing " + victim);
			} else if (group != null) {
				// An outsider: the town's enemy.
				for (UUID id : group.members) {
					DrifterEntity member = TownFounding.loaded(server, id);
					if (member != null) {
						DrifterApi.addTrustInDrifter(member, drifter, -50.0);
					}
				}

				Chronicle.add(server, "law", name + " killed " + victim + " of " + town.name + ": the town's enemy now");
			}
		}

		Towns.save(server);
	}

	// ---------------------------------------------------------------------------------------------
	// Its crooks (a Bandit living in it: the user, 2026-10-28, "if they are in a town they should be commiting several
	// crimes to really give that bandit feel")
	// ---------------------------------------------------------------------------------------------

	private static final int FINE_FIGHT = 2;
	/** What a crook's crime does to the trust of those who saw it (and of the one it wronged). */
	private static final double SAW_IT = -15.0;
	/** Thrown out: what its town mates think of it then (so they don't team up with it again). */
	private static final double THROWN_OUT = -70.0;
	/**
	 * Thrown out and on its way (where the town is): a Bandit again once it's out of the town (found in testing: turned
	 * Bandit on the spot, or after a few minutes still about the town, it killed one of the town's people where it stood).
	 * Not saved: after a reload it stays as it is.
	 */
	private static final Map<UUID, BlockPos> leaving = new HashMap<>();
	/**
	 * How far from the town that is (found in testing: a Bandit again just past the town's edge, it set up a farm there and
	 * killed the town's people who came by; settled nearer, it stays an opportunist).
	 */
	private static final double WELL_CLEAR = IN_TOWN * 2;

	/**
	 * A crook did it ({@code TownWork#crime}): a theft from a chest, or a fight it picked. Only what its people saw counts:
	 * one of them (not the crook) with it in sight just now, near (for a fight, the one it went for too). Caught: a fine,
	 * paid into the till like a tax; a third time, it's thrown out of the town, a Bandit again.
	 */
	static void byCrook(Town town, DrifterEntity crook, String kind, @Nullable DrifterEntity victim, BlockPos where) {
		MinecraftServer server = crook.getServer();
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (server == null || group == null || !(crook.level() instanceof ServerLevel level)) {
			return;
		}

		long now = level.getGameTime();
		List<DrifterEntity> saw = new ArrayList<>();
		for (DrifterEntity one : level.getEntitiesOfClass(DrifterEntity.class, crook.getBoundingBox().inflate(WITNESS),
			d -> d.isAlive() && d != crook && group.members.contains(d.getUUID()))) {
			var seen = one.getSenses().awarenessOf(crook);
			if (one == victim || seen != null && seen.identified && (seen.visibleNow || now - seen.lastSeenTick <= 40L)) {
				saw.add(one);
			}
		}

		String name = crook.getName().getString();
		String whose = owner(server, town, group, where);
		String what = kind.equals("fight") ? "picking a fight with " + (victim == null ? "someone" : victim.getName().getString())
			: "stealing from " + whose;
		if (saw.isEmpty()) {
			DrifterApi.think(crook, "nobody saw it");
			return;
		}

		Town.Criminal criminal = town.crimes.computeIfAbsent(crook.getUUID(), id -> new Town.Criminal(name));
		if (now - criminal.lastAt < SAME_CRIME && kind.equals(criminal.crime)) {
			return;
		}

		criminal.offences++;
		criminal.lastAt = now;
		criminal.crime = kind;
		DrifterEntity witness = saw.stream().filter(one -> one != victim).findFirst().orElse(saw.get(0));
		com.dinglebob.drifter.social.ChatLines.speak(witness, com.dinglebob.drifter.social.Lines.pick(witness,
			kind.equals("fight") ? "crime.fight" : "crime.theft", "player", name, "who", victim == null ? whose : victim.getName().getString()));
		for (DrifterEntity one : saw) {
			DrifterApi.addTrustInDrifter(one, crook, SAW_IT);
			DrifterApi.think(one, "saw " + name + " " + what);
		}

		town.lastCrook = name;
		town.lastCrime = kind;
		town.lastCrimeDay = Chronicle.day(server);
		if (criminal.offences >= REPEAT) {
			throwOut(server, town, group, crook, what + " again (" + criminal.offences + " times)");
			town.crimes.remove(crook.getUUID());
		} else {
			int fine = (kind.equals("fight") ? FINE_FIGHT : FINE_THEFT) + (criminal.offences - 1);
			// (It owes the till, as a tax: it takes the emeralds there, the ones it stole if that's all it has.)
			town.taxOwed.merge(crook.getUUID(), fine, Integer::sum);
			Chronicle.add(server, "law", name + " was caught " + what + " in " + town.name + " (a fine of " + fine + " emeralds)");
			DrifterApi.think(crook, "caught " + what + ": a fine of " + fine + " emeralds");
		}

		Towns.save(server);
	}

	/** Whose chest it was: the town's till, a town mate's (the house it's by), or the town's. */
	private static String owner(MinecraftServer server, Town town, DrifterGroups.Group group, BlockPos chest) {
		if (chest.equals(town.till())) {
			return "the town's till";
		}

		if (town.store.contains(chest)) {
			return "the town's store";
		}

		for (Town.Plot plot : town.plots) {
			if (plot.owner != null && inPlot(plot, chest)) {
				return group.names.getOrDefault(plot.owner, "a neighbour") + "'s chest";
			}
		}

		return "the town";
	}

	/** The town's (its store, its till), or in a neighbour's house: not in the crook's own. */
	static boolean othersChest(Town town, DrifterEntity crook, BlockPos chest) {
		if (town.store.contains(chest)) {
			return true;
		}

		for (Town.Plot plot : town.plots) {
			if (inPlot(plot, chest)) {
				return !crook.getUUID().equals(plot.owner) && !plot.residents.contains(crook.getUUID());
			}
		}

		return false;
	}

	private static boolean inPlot(Town.Plot plot, BlockPos pos) {
		return pos.getX() >= plot.origin.getX() - 1 && pos.getX() <= plot.origin.getX() + plot.sizeX
			&& pos.getZ() >= plot.origin.getZ() - 1 && pos.getZ() <= plot.origin.getZ() + plot.sizeZ;
	}

	/** Out of the town for good: no home there, no friends there, and once it's gone, a Bandit again (it kills on sight). */
	static void throwOut(MinecraftServer server, Town town, DrifterGroups.Group group, DrifterEntity crook, String why) {
		String name = crook.getName().getString();
		DrifterGroups.leave(crook.getUUID());
		town.crooks.remove(crook.getUUID());
		town.taxOwed.remove(crook.getUUID());
		town.lastCrime = "thrown_out";
		DrifterApi.setHome(crook, null);
		DrifterApi.setTownWork(crook, null);
		leaving.put(crook.getUUID(), town.center);
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member != null) {
				DrifterApi.addTrustInDrifter(member, crook, THROWN_OUT);
				DrifterApi.addTrustInDrifter(crook, member, THROWN_OUT);
			}
		}

		com.dinglebob.drifter.social.ChatLines.speak(crook, com.dinglebob.drifter.social.Lines.pick(crook, "crook.thrown_out", "town", town.name));
		DrifterApi.think(crook, "was thrown out of " + town.name + " for " + why);
		Chronicle.add(server, "law", name + " was thrown out of " + town.name + " for " + why + ": a bandit again");
	}

	/**
	 * (Debug: the town crook command.) Its crook goes at it again soon; none (loaded): the nearest of its people (not the
	 * leader, not the stall's keeper) is a crook now.
	 */
	@Nullable
	static String debugCrook(MinecraftServer server, Town town, BlockPos near) {
		for (UUID id : town.crooks) {
			DrifterEntity crook = TownFounding.loaded(server, id);
			if (crook != null) {
				DrifterApi.crookSoon(crook);
				return crook.getName().getString();
			}
		}

		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		DrifterEntity pick = null;
		if (group != null) {
			for (UUID id : group.members) {
				DrifterEntity member = TownFounding.loaded(server, id);
				if (member != null && !id.equals(town.leader) && !id.equals(group.leader) && !id.equals(town.keeper) && (pick == null || member.distanceToSqr(near.getCenter()) < pick.distanceToSqr(near.getCenter()))) {
					pick = member;
				}
			}
		}

		if (pick == null) {
			return null;
		}

		DrifterApi.turnBandit(pick);
		town.crooks.add(pick.getUUID());
		DrifterApi.settleDown(pick);
		DrifterApi.crookSoon(pick);
		Towns.save(server);
		return pick.getName().getString();
	}

	/** (Debug: the town crook out command.) Its crook thrown out now. */
	@Nullable
	static String debugThrowOut(MinecraftServer server, Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		for (UUID id : List.copyOf(town.crooks)) {
			DrifterEntity crook = TownFounding.loaded(server, id);
			if (crook != null && group != null) {
				throwOut(server, town, group, crook, "its crimes (debug)");
				town.crimes.remove(id);
				Towns.save(server);
				return crook.getName().getString();
			}
		}

		return null;
	}

	private static void exile(MinecraftServer server, Town town, Town.Criminal criminal, long days, String why) {
		criminal.exiledUntil = Chronicle.day(server) + days;
		criminal.fine = 0;
		Chronicle.add(server, "law", criminal.name + " was exiled from " + town.name + " for " + days + " days (" + why + ")");
	}

	/** (Debug: `/drifterciv town crime`.) As if the nearest of its people saw this player do it. */
	static boolean debugCrime(MinecraftServer server, Town town, ServerPlayer player, String kind) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		DrifterEntity witness = null;
		if (group != null) {
			for (UUID id : group.members) {
				DrifterEntity member = TownFounding.loaded(server, id);
				if (member != null && (witness == null || member.distanceTo(player) < witness.distanceTo(player))) {
					witness = member;
				}
			}
		}

		if (witness == null) {
			return false;
		}

		switch (kind) {
			case "theft", "farms" -> crime(server, town, player, kind, witness);
			case "murder" -> {
				Town.Criminal criminal = town.crimes.computeIfAbsent(player.getUUID(), id -> new Town.Criminal(player.getGameProfile().getName()));
				criminal.crime = "murder";
				exile(server, town, criminal, MURDER_EXILE, "a killing (debug)");
				DrifterApi.postBounty(player, "the people of " + town.name, "a killing (debug)");
				Towns.save(server);
			}
			default -> {
				return false;
			}
		}

		return true;
	}

	// ---------------------------------------------------------------------------------------------
	// The guards (TownWork)
	// ---------------------------------------------------------------------------------------------

	/** The fine this guard is to collect now (one of its town's guards; one guard to a culprit), or null. */
	@Nullable
	static TownWork.Arrest arrest(Town town, DrifterEntity guard) {
		if (guard.getServer() == null) {
			return null;
		}

		List<UUID> appointed = guards.getOrDefault(town.id, List.of());
		boolean armed = guard.getInventory().count(stack -> stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) > 0;
		if (!appointed.contains(guard.getUUID()) && !armed) {
			return null;
		}

		long now = guard.level().getGameTime();
		for (Map.Entry<UUID, Town.Criminal> entry : town.crimes.entrySet()) {
			Town.Criminal criminal = entry.getValue();
			ServerPlayer player = guard.getServer().getPlayerList().getPlayer(entry.getKey());
			if (criminal.fine <= 0 || criminal.exiledUntil > 0 || player == null || player.level() != guard.level()
				|| !player.blockPosition().closerThan(town.center, IN_TOWN)) {
				continue;
			}

			// Not one of its guards: only when none of them is near (a townsperson with a weapon stands in for them; found in
			// testing: both guards off mining, the thief walked about the town untroubled).
			if (!appointed.contains(guard.getUUID()) && appointed.stream().anyMatch(id -> guard.level() instanceof ServerLevel level
				&& level.getEntity(id) instanceof DrifterEntity one && one.isAlive() && one.distanceTo(player) <= 48.0)) {
				continue;
			}

			// (Another guard is after them already.)
			Map.Entry<UUID, Long> after = chasing.get(entry.getKey());
			if (after != null && !after.getKey().equals(guard.getUUID()) && now - after.getValue() < 20L * 60 * 3) {
				continue;
			}

			chasing.put(entry.getKey(), Map.entry(guard.getUUID(), now));
			return new TownWork.Arrest(entry.getKey(), criminal.name, criminal.fine, criminal.crime.equals("theft") ? "stealing" : "harming the farms");
		}

		return null;
	}

	/** How the guard's errand went: the fine paid (it takes the money to the till), or refused (exile). */
	static void arrested(Town town, DrifterEntity guard, UUID player, int paid, boolean refused) {
		MinecraftServer server = guard.getServer();
		Town.Criminal criminal = town.crimes.get(player);
		if (server == null || criminal == null) {
			return;
		}

		chasing.remove(player);
		if (paid > 0) {
			// (The guard owes it to the town: it puts it in the till, as a tax.)
			town.taxOwed.merge(guard.getUUID(), paid, Integer::sum);
		}

		if (!refused && paid >= criminal.fine) {
			Chronicle.add(server, "law", criminal.name + " paid a fine of " + paid + " emeralds in " + town.name);
			criminal.fine = 0;
		} else {
			criminal.fine = Math.max(0, criminal.fine - paid);
			exile(server, town, criminal, EXILE, "wouldn't pay a fine");
		}

		Towns.save(server);
	}

	/** "exiled" while this player is. */
	@Nullable
	static String standing(Town town, Player player) {
		Town.Criminal criminal = town.crimes.get(player.getUUID());
		return criminal != null && criminal.exiledUntil > 0 ? "exiled" : null;
	}

	/** A citizen: a player whose bed is in the town. */
	static boolean citizen(ServerPlayer player, Town town) {
		BlockPos bed = player.getRespawnPosition();
		return bed != null && bed.closerThan(town.center, IN_TOWN);
	}

	@Nullable
	/** It has a sword or an axe. */
	static boolean armed(DrifterEntity drifter) {
		return drifter.getInventory().count(stack -> stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) > 0;
	}

	private static Town townOf(DrifterEntity drifter) {
		DrifterGroups.Group group = DrifterGroups.of(drifter.getUUID());
		if (group == null) {
			return null;
		}

		for (Town town : Towns.all()) {
			if (group.id.equals(town.group)) {
				return town;
			}
		}

		return null;
	}
}
