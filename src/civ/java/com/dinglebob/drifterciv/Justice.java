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

			// Someone of the town saw it.
			var killer = playerKiller != null ? playerKiller : drifterKiller;
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

		// Its guards: its two boldest with a weapon.
		List<DrifterEntity> fit = new ArrayList<>();
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member != null && member.getInventory().count(stack -> stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) > 0) {
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
