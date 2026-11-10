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
 * <p>
 * Stronger (the user, 2026-11-07: "we need to enforce and up crime awareness and behavior towards me/drifter commiting them,
 * especially theft, killing a drifter with witnesses, or committing a massacre"):
 * <ul>
 *   <li>Theft seen as it happens: any of its people who sees a player take things from the town's chests (its store, its
 *       till, a chest in one of its houses, one about its middle) says so; the fine is 3 emeralds and what was taken is
 *       worth, more each time; stealing while exiled makes them wanted.</li>
 *   <li>A killing: everyone of the town who saw it (or was close enough to hear it and knew who), not just one; wanted for
 *       the week of the exile (its guards and bold people go for them on sight about the town, the rest keep clear).</li>
 *   <li>A massacre (three of its people killed within a few days of each other): wanted there for a month, a price on
 *       their head, and word goes round the towns about (wanted in them too for ten days).</li>
 *   <li>A Drifter killer: those who saw it make it their enemy on sight, the whole town nearly so; one of its own is thrown
 *       out; a Drifter who massacres is the enemy of every town about.</li>
 *   <li>A fine refused: exile, and wanted for it.</li>
 * </ul>
 * More (the user, 0.23.35: "i think we should enhance the crime system a bit more"):
 * <ul>
 *   <li>Its leader murdered: the whole town turns on them, wanted there a month, the alarm, a price on their head, and wanted
 *       in every town of its nation for a month too (the towns about, if it has none).</li>
 *   <li>A guard killed: two more picked at once.</li>
 *   <li>A horse of its people's ridden off (5 emeralds), its animals killed (in its pen, or its people's own: 2; any town now,
 *       not only one with the farms law), its alarm bell rung for nothing (1): seen by any of its people.</li>
 * </ul>
 */
final class Justice {
	private Justice() {
	}

	private static final int FINE_THEFT = 3;
	private static final int FINE_FARMS = 1;
	private static final int FINE_HORSE = 5;
	private static final int FINE_LIVESTOCK = 2;
	private static final int FINE_PEACE = 1;

	/** Each lesser crime (by its kind): what it's called, and its fine (emeralds, one more each time after). */
	private record Kind(String what, int fine) {
	}

	private static final Map<String, Kind> KINDS = Map.of("theft", new Kind("stealing", FINE_THEFT), "farms", new Kind("harming the town's farms", FINE_FARMS),
		"horse", new Kind("stealing a horse", FINE_HORSE), "livestock", new Kind("killing the town's animals", FINE_LIVESTOCK), "peace",
		new Kind("ringing the alarm for nothing", FINE_PEACE));
	/** Its pen's animals are within this of its pen. */
	private static final double PEN_REACH = 12.0;
	/** Monsters this near its bell: a reason to ring it. */
	private static final double ALARM_REASON = 32.0;
	/** Its leader murdered: wanted there, and in its nation's towns, this long (days). */
	private static final long LEADER_EXILE = 30;
	/** The horses each player has been seen riding off with (not saved): each one theft once. */
	private static final Map<UUID, java.util.Set<UUID>> horsesTaken = new HashMap<>();
	/** A town's guards: this many. */
	private static final int GUARDS = 2;
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
	/** A killing witnessed: seen within this, the killer in sight within the last few seconds; or this close, heard. */
	private static final double MURDER_WITNESS = 48.0;
	private static final long JUST_SEEN = 100L;
	private static final double HEARD_IT = 16.0;
	/** Three of its people killed, each within this many days of the last: a massacre. */
	private static final int MASSACRE = 3;
	private static final long MASSACRE_DAYS = 3;
	/** A massacre: exiled and wanted this long; the towns this near hear of it, and want them this long. */
	private static final long MASSACRE_EXILE = 30;
	private static final double WORD_SPREADS = 1500.0;
	private static final long HEARD_MASSACRE = 10;
	/** What those who saw a theft, a killing, think of the one who did it (and the rest of the town, of a killing). */
	private static final double SAW_THEFT = -20.0;
	private static final double SAW_MURDER = -60.0;
	private static final double HEARD_MURDER = -20.0;
	/** A Drifter's killing: those who saw it, and the rest of the town (at or under -60 it's an enemy on sight). */
	private static final double SAW_DRIFTER_MURDER = -90.0;
	private static final double HEARD_DRIFTER_MURDER = -65.0;
	/** A Drifter's killings lately (not saved): how many, and the day of the last. */
	private static final Map<UUID, long[]> drifterKills = new HashMap<>();
	/** When a town's people last called out someone wanted there (not saved). */
	private static final Map<String, Long> calledOut = new HashMap<>();

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
				case CAUGHT_STEALING, TOOK_VALUABLES -> {
					// (Not when it's what they were seen taking already: found gone later, the same theft.)
					Town.Criminal seen = town.crimes.get(serverPlayer.getUUID());
					if (seen == null || serverPlayer.server.overworld().getGameTime() - seen.lastSeenTheft > 24000L) {
						crime(serverPlayer.server, town, serverPlayer, "theft", drifter);
					}
				}
				case KILLED_FARM_ANIMAL -> {
					// (Its animals: anywhere now, not only with the farms law. 0.23.35.)
					if (!citizen(serverPlayer, town)) {
						crime(serverPlayer.server, town, serverPlayer, "livestock", drifter);
					}
				}
				case TRAMPLED_CROPS -> {
					if (town.laws.contains(Politics.FARMS) && !citizen(serverPlayer, town)) {
						crime(serverPlayer.server, town, serverPlayer, "farms", drifter);
					}
				}
				default -> {
				}
			}
		});

		DrifterEvents.SEEN_TAKING.register((player, chest, taken, saw) -> {
			if (player instanceof ServerPlayer serverPlayer) {
				seenTaking(serverPlayer, chest, taken, saw);
			}
		});

		// The town's animals killed by an outsider, seen (0.23.35, the user: "more crime things too like stealing a horse,
		// killing farm animals"): those in its pen, and its people's own (a horse, a dog), in any town.
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!(victim instanceof net.minecraft.world.entity.animal.Animal animal) || !(source.getEntity() instanceof ServerPlayer player)
				|| !(victim.level() instanceof ServerLevel level)) {
				return;
			}

			Town town = townsAnimal(level, animal);
			if (town == null || citizen(player, town)) {
				return;
			}

			DrifterEntity witness = witness(level.getServer(), town, player);
			if (witness != null) {
				crime(level.getServer(), town, player, "livestock", witness);
			}
		});

		// A player ringing a town's alarm bell for nothing (0.23.35, the user: "spam ringing the bell for no reason"):
		// disturbing the peace, if one of its people saw them at it. Not while its own alarm is on (raiders, a killing), nor
		// with monsters about the bell.
		DrifterEvents.ALARM_RUNG.register((player, bell) -> {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return;
			}

			ServerLevel level = serverPlayer.serverLevel();
			for (Town town : Towns.all()) {
				BlockPos ours = town.dimension.equals(level.dimension().location().toString()) ? TownBells.bell(town, level) : null;
				if (ours == null || !ours.closerThan(bell, 3.0)) {
					continue;
				}

				if (TownBells.alarmed(town) || !level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(bell)
					.inflate(ALARM_REASON), mob -> mob instanceof net.minecraft.world.entity.monster.Enemy && mob.isAlive()).isEmpty()) {
					return;
				}

				DrifterEntity witness = witness(serverPlayer.server, town, serverPlayer);
				if (witness != null) {
					crime(serverPlayer.server, town, serverPlayer, "peace", witness);
				}

				return;
			}
		});

		// Who struck first in each fight (0.23.33: self-defence is killing the one who started it).
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			net.minecraft.world.entity.Entity attacker = source.getEntity();
			DrifterEntity owner = com.dinglebob.drifter.hands.FakePlayerBridge.ownerOf(attacker);
			if (owner != null) {
				attacker = owner;
			}

			if (attacker instanceof net.minecraft.world.entity.LivingEntity hitter && hitter != victim && (victim instanceof DrifterEntity
				|| hitter instanceof DrifterEntity) && victim.level() instanceof ServerLevel level) {
				struck(level.getGameTime(), hitter.getUUID(), victim.getUUID());
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!(victim instanceof DrifterEntity dead) || dead.isTemporary() || !(victim.level() instanceof ServerLevel level)) {
				return;
			}

			// (Gone for good, it's out of its town's group before this: the town it died one of. Found in the user's world,
			// 0.23.32: three of a city's people killed in front of it, nothing done.)
			// (Killed in another town than its own, or with none: a visitor, and murder there too, if its people saw it. Found in
			// testing, 0.23.35: a passer-by killed on a plaza was nothing to the town, its people turned on the killer for it, and
			// then killing them was "self-defence".)
			Town own = townOrWasOf(dead);
			Town town = whereKilled(dead, own);
			boolean visitor = town != null && town != own;
			// One of its guards dead: two picked now, not at the next check (0.23.35: "since i killed the guards nobody else
			// really fights").
			if (town != null && guards.getOrDefault(town.id, List.of()).contains(dead.getUUID())) {
				pickGuards(level.getServer(), town, dead.getUUID());
			}

			DrifterEntity drifterKiller = source.getEntity() instanceof DrifterEntity d ? d : com.dinglebob.drifter.hands.FakePlayerBridge.ownerOf(source.getEntity());
			Player playerKiller = drifterKiller == null && source.getEntity() instanceof ServerPlayer p ? p : null;
			if (town == null || drifterKiller == null && playerKiller == null) {
				return;
			}

			// (Self-defence isn't murder: it came at them first. Found: a leader killing the one who turned on it. Not for someone
			// wanted there, though: the guards who come for them are in the right. Who came at whom first is who struck the first
			// blow of their fight: it was whether the dead was fighting the killer at the end, and anyone struck fights back, so
			// every killing in a fight was self-defence; found in the user's world, 0.23.32.)
			var killer = playerKiller != null ? playerKiller : drifterKiller;
			Town.Criminal record = playerKiller == null ? null : town.crimes.get(playerKiller.getUUID());
			boolean wanted = record != null && record.wantedUntil > 0;
			if (!wanted && dead.getUUID().equals(firstBlow(level.getGameTime(), killer.getUUID(), dead.getUUID()))) {
				return;
			}

			// Those of the town who saw it: the killer in sight just now, near; or close enough to hear it, knowing who.
			List<DrifterEntity> witnesses = new ArrayList<>();
			long now = level.getGameTime();
			for (DrifterEntity one : level.getEntitiesOfClass(DrifterEntity.class, dead.getBoundingBox().inflate(MURDER_WITNESS), d -> d.isAlive() && d != dead
				&& d != killer && town.group != null && DrifterGroups.of(d.getUUID()) != null && town.group.equals(DrifterGroups.of(d.getUUID()).id))) {
				var seen = one.getSenses().awarenessOf(killer);
				if (seen != null && seen.identified && (seen.visibleNow || now - seen.lastSeenTick <= JUST_SEEN || one.distanceTo(dead) <= HEARD_IT)) {
					witnesses.add(one);
				}
			}

			if (witnesses.isEmpty()) {
				return;
			}

			// (A visitor its people had against them, or one at war with them, isn't murdered: a raider, an enemy.)
			if (visitor && (DrifterApi.trustInDrifter(witnesses.get(0), dead.getUUID()) <= -60.0
				|| com.dinglebob.drifter.world.DrifterWars.atWar(witnesses.get(0), dead))) {
				return;
			}

			murder(level.getServer(), town, dead, playerKiller instanceof ServerPlayer p ? p : null, drifterKiller, witnesses, visitor);
		});
	}

	/**
	 * The town a killing was in: its own, if it died about it; else the one it died in (a visitor there), if it isn't a
	 * Bandit; else its own (or none).
	 */
	@Nullable
	private static Town whereKilled(DrifterEntity dead, @Nullable Town own) {
		if (own != null && dead.blockPosition().closerThan(own.center, IN_TOWN) || DrifterApi.bandit(dead)) {
			return own;
		}

		String dimension = dead.level().dimension().location().toString();
		for (Town town : Towns.all()) {
			if (town != own && town.dimension.equals(dimension) && dead.blockPosition().closerThan(town.center, IN_TOWN)) {
				return town;
			}
		}

		return own;
	}

	/** Its town, or (gone for good, out of its group already) the town it died one of. */
	@Nullable
	private static Town townOrWasOf(DrifterEntity dead) {
		Town town = townOf(dead);
		UUID was = DrifterApi.groupAtDeath(dead);
		if (town == null && was != null) {
			for (Town one : Towns.all()) {
				if (was.equals(one.group)) {
					return one;
				}
			}
		}

		return town;
	}

	/** A fight: who struck its first blow, and when its last was (each pair, either way round). */
	private record Fight(UUID first, long last) {
	}

	private static final Map<String, Fight> fights = new HashMap<>();
	/** Blows this far apart are another fight. */
	private static final long FIGHT_GAP = 20L * 30;

	private static String pair(UUID a, UUID b) {
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	/** One struck another. */
	private static void struck(long now, UUID hitter, UUID hit) {
		String key = pair(hitter, hit);
		Fight fight = fights.get(key);
		fights.put(key, new Fight(fight == null || now - fight.last > FIGHT_GAP ? hitter : fight.first, now));
		if (fights.size() > 4096) {
			fights.values().removeIf(f -> now - f.last > FIGHT_GAP);
		}
	}

	/** Who struck the first blow of these two's fight now (null: no fight between them lately). */
	@Nullable
	private static UUID firstBlow(long now, UUID a, UUID b) {
		Fight fight = fights.get(pair(a, b));
		return fight == null || now - fight.last > FIGHT_GAP ? null : fight.first;
	}

	/** Its exiles over, and its guards picked. True if anything changed. */
	static boolean check(MinecraftServer server, Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return false;
		}

		boolean changed = false;
		long day = Chronicle.day(server);
		for (Map.Entry<UUID, Town.Criminal> entry : town.crimes.entrySet()) {
			Town.Criminal criminal = entry.getValue();
			if (criminal.wantedUntil > 0 && day >= criminal.wantedUntil) {
				criminal.wantedUntil = 0;
				changed = true;
			}

			if (criminal.exiledUntil > 0 && day >= criminal.exiledUntil) {
				criminal.exiledUntil = 0;
				criminal.offences = 0;
				Chronicle.add(server, "law", criminal.name + " may come back to " + town.name + ": the exile is over");
				changed = true;
			}

			// Someone wanted, back about the town: whoever of its people sees them first calls it out (now and then).
			ServerPlayer player = criminal.wantedUntil > 0 ? server.getPlayerList().getPlayer(entry.getKey()) : null;
			String key = town.id + "|" + entry.getKey();
			if (player != null && player.blockPosition().closerThan(town.center, IN_TOWN) && server.overworld().getGameTime() - calledOut.getOrDefault(key, -100000L)
				> 20L * 60 * 3) {
				for (UUID id : group.members) {
					DrifterEntity member = TownFounding.loaded(server, id);
					if (member != null && member.level() == player.level() && member.getSocial().watcher().sees(player)) {
						calledOut.put(key, server.overworld().getGameTime());
						com.dinglebob.drifter.social.ChatLines.speak(member, com.dinglebob.drifter.social.Lines.pick(member, "crime.wanted", "player",
							criminal.name));
						alarm(server, town, player);
						if (!TownBells.alarmed(town)) {
							bell(server, town, player, criminal.name + ", wanted here", List.of("it's " + criminal.name + "! they're back!",
								criminal.name + "'s here! guards!", "everyone inside! " + criminal.name + "'s back!"), WANTED_ALARM);
						}

						break;
					}
				}
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

		// A horse of its people's ridden off by a player, seen: horse theft (0.23.35), once each horse.
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.getVehicle() instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse) || horse.getOwnerUUID() == null
				|| !group.members.contains(horse.getOwnerUUID()) || !player.level().dimension().location().toString().equals(town.dimension)
				|| !player.blockPosition().closerThan(town.center, IN_TOWN * 2.0)
				|| horsesTaken.getOrDefault(player.getUUID(), java.util.Set.of()).contains(horse.getUUID())) {
				continue;
			}

			DrifterEntity witness = witness(server, town, player);
			if (witness == null) {
				continue;
			}

			horsesTaken.computeIfAbsent(player.getUUID(), id -> new java.util.HashSet<>()).add(horse.getUUID());
			crime(server, town, player, "horse", witness);
			DrifterEntity owner = TownFounding.loaded(server, horse.getOwnerUUID());
			if (owner != null) {
				DrifterApi.addTrustInPlayer(owner, player.getUUID(), player.getGameProfile().getName(), -30.0);
				DrifterApi.think(owner, player.getGameProfile().getName() + " rode off on its horse");
			}

			changed = true;
		}

		pickGuards(server, town, null);
		return changed;
	}

	/** Its guards: its boldest two with a weapon (not its leader, nor this one: just died). */
	private static void pickGuards(MinecraftServer server, Town town, @Nullable UUID not) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return;
		}

		List<DrifterEntity> fit = new ArrayList<>();
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member != null && member.isAlive() && !id.equals(not) && !id.equals(town.leader) && armed(member)) {
				fit.add(member);
			}
		}

		fit.sort(Comparator.comparingDouble((DrifterEntity member) -> DrifterApi.trait(member, "bravery") + DrifterApi.trait(member, "aggression")).reversed());
		guards.put(town.id, fit.stream().limit(GUARDS).map(DrifterEntity::getUUID).toList());
	}

	/** One of its people who sees this player now (near enough to), or null. */
	@Nullable
	private static DrifterEntity witness(MinecraftServer server, Town town, ServerPlayer player) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return null;
		}

		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member != null && member.isAlive() && member.level() == player.level() && member.distanceTo(player) <= WITNESS
				&& member.getSocial().watcher().sees(player)) {
				return member;
			}
		}

		return null;
	}

	/** The town this animal is one of (in its pen, or one of its people's own), or null. */
	@Nullable
	private static Town townsAnimal(ServerLevel level, net.minecraft.world.entity.animal.Animal animal) {
		String dimension = level.dimension().location().toString();
		UUID owner = animal instanceof net.minecraft.world.entity.OwnableEntity ownable ? ownable.getOwnerUUID() : null;
		for (Town town : Towns.all()) {
			if (!town.dimension.equals(dimension) || !animal.blockPosition().closerThan(town.center, IN_TOWN * 2.0)) {
				continue;
			}

			DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
			if (town.pen != null && town.pen.closerThan(animal.blockPosition(), PEN_REACH) || owner != null && group != null && group.members.contains(owner)) {
				return town;
			}
		}

		return null;
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
		Kind of = KINDS.getOrDefault(kind, KINDS.get("farms"));
		String what = of.what();
		com.dinglebob.drifter.social.ChatLines.speak(witness, com.dinglebob.drifter.social.Lines.pick(witness, "crime." + kind, "player", criminal.name));
		if (criminal.offences >= REPEAT) {
			exile(server, town, criminal, EXILE, what + " again (" + criminal.offences + " times)");
		} else {
			criminal.fine += of.fine() + (criminal.offences - 1);
			Chronicle.add(server, "law", criminal.name + " was caught " + what + " in " + town.name + " (a fine of " + criminal.fine + " emeralds)");
		}

		Towns.save(server);
	}

	/** A player seen taking things out of a chest: theft, if it was one of a town's that saw it (and not theirs to take). */
	private static void seenTaking(ServerPlayer player, BlockPos chest, List<net.minecraft.world.item.ItemStack> taken, List<DrifterEntity> saw) {
		String dimension = player.level().dimension().location().toString();
		for (Town town : Towns.all()) {
			if (!town.dimension.equals(dimension) || town.group == null || !theirs(player, town, chest)) {
				continue;
			}

			List<DrifterEntity> witnesses = saw.stream().filter(one -> {
				DrifterGroups.Group group = DrifterGroups.of(one.getUUID());
				return group != null && town.group.equals(group.id);
			}).toList();
			if (!witnesses.isEmpty()) {
				theft(player.server, town, player, chest, taken, witnesses);
				return;
			}
		}
	}

	/**
	 * The town's chest, not this player's to take from: its till, its store (a citizen may take from that), one in its houses,
	 * or one about its middle the player didn't put there (its mine's, its stable's).
	 */
	private static boolean theirs(ServerPlayer player, Town town, BlockPos chest) {
		if (!chest.closerThan(town.center, IN_TOWN)) {
			return false;
		}

		if (chest.equals(town.till())) {
			return true;
		}

		if (town.store.contains(chest)) {
			return !citizen(player, town);
		}

		for (Town.Plot plot : town.plots) {
			if (inPlot(plot, chest)) {
				return true;
			}
		}

		return !com.dinglebob.drifter.world.PlayerPlacedBlocks.isPlayerPlaced(player.level(), chest);
	}

	/**
	 * Seen stealing: those who saw think less of them, one of them shouts; a fine of 3 emeralds and what it was worth (more
	 * each time; more of the same theft in the next few seconds adds to it), the third time exile; stealing while exiled,
	 * wanted.
	 */
	private static void theft(MinecraftServer server, Town town, ServerPlayer player, BlockPos chest, List<net.minecraft.world.item.ItemStack> taken,
		List<DrifterEntity> saw) {
		long now = server.overworld().getGameTime();
		long day = Chronicle.day(server);
		String name = player.getGameProfile().getName();
		Town.Criminal criminal = town.crimes.computeIfAbsent(player.getUUID(), id -> new Town.Criminal(name));
		double worth = taken.stream().mapToDouble(stack -> DrifterApi.worth(stack) * stack.getCount()).sum();
		int value = (int)Math.ceil(worth / 6.0);
		DrifterGroups.Group group = DrifterApi.groupById(town.group);
		String whose = group == null ? "the town" : owner(server, town, group, chest);
		String what = taken.size() == 1 ? taken.get(0).getCount() + " " + taken.get(0).getHoverName().getString().toLowerCase(java.util.Locale.ROOT)
			: taken.stream().mapToInt(net.minecraft.world.item.ItemStack::getCount).sum() + " things";
		boolean more = now - criminal.lastSeenTheft < 20L * 15 && "theft".equals(criminal.crime);
		criminal.lastSeenTheft = now;
		criminal.lastAt = now;
		for (DrifterEntity one : saw) {
			DrifterApi.addTrustInPlayer(one, player.getUUID(), name, SAW_THEFT);
			DrifterApi.think(one, "saw " + name + " take " + what + " from " + whose);
		}

		if (!more) {
			DrifterEntity witness = saw.get(0);
			com.dinglebob.drifter.social.ChatLines.speak(witness, com.dinglebob.drifter.social.Lines.pick(witness, "crime.theft", "player", name));
		}

		if (criminal.exiledUntil > 0) {
			if (criminal.wantedUntil <= day) {
				Chronicle.add(server, "law", name + " was seen stealing from " + whose + " in " + town.name + " while exiled: wanted there now");
			}

			criminal.wantedUntil = Math.max(criminal.wantedUntil, day + EXILE);
		} else if (more) {
			criminal.fine += value;
		} else {
			criminal.offences++;
			criminal.crime = "theft";
			if (criminal.offences >= REPEAT) {
				exile(server, town, criminal, EXILE, "stealing again (" + criminal.offences + " times)");
			} else {
				criminal.fine += FINE_THEFT + value + 2 * (criminal.offences - 1);
				Chronicle.add(server, "law", name + " was seen stealing " + what + " from " + whose + " in " + town.name + " (a fine of " + criminal.fine
					+ " emeralds)");
			}
		}

		Towns.save(server);
	}

	/** One of its people killed, and seen. */
	private static void murder(MinecraftServer server, Town town, DrifterEntity dead, @Nullable ServerPlayer player, @Nullable DrifterEntity drifter,
		List<DrifterEntity> witnesses, boolean visitor) {
		DrifterEntity witness = witnesses.get(0);
		long day = Chronicle.day(server);
		String victim = dead.getName().getString();
		DrifterGroups.Group townGroup = town.group == null ? null : DrifterApi.groupById(town.group);
		if (visitor) {
			Chronicle.add(server, "law", victim + " was murdered in " + town.name + ", a visitor there, by "
				+ (player != null ? player.getGameProfile().getName() : drifter != null ? drifter.getName().getString() : "someone"));
		}

		if (townGroup != null && !visitor) {
			// (The leader couldn't keep them safe: step 5, part 3.)
			Unrest.killed(town, townGroup);
		}

		if (dead.getUUID().equals(town.leader)) {
			Chronicle.add(server, "law", victim + ", leader of " + town.name + ", was murdered by "
				+ (player != null ? player.getGameProfile().getName() : drifter != null ? drifter.getName().getString() : "someone"));
		}

		if (player != null) {
			String name = player.getGameProfile().getName();
			Town.Criminal criminal = town.crimes.computeIfAbsent(player.getUUID(), id -> new Town.Criminal(name));
			boolean wantedAlready = criminal.wantedUntil > day;
			criminal.offences++;
			criminal.crime = "murder";
			criminal.kills++;
			criminal.recentKills = day - criminal.lastKillDay <= MASSACRE_DAYS ? criminal.recentKills + 1 : 1;
			criminal.lastKillDay = day;
			// Those who saw it, and the rest of the town once it hears.
			for (DrifterEntity one : witnesses) {
				DrifterApi.addTrustInPlayer(one, player.getUUID(), name, SAW_MURDER);
				DrifterApi.think(one, "saw " + name + " kill " + victim);
			}

			if (townGroup != null) {
				for (UUID id : townGroup.members) {
					DrifterEntity member = TownFounding.loaded(server, id);
					if (member != null && !witnesses.contains(member)) {
						DrifterApi.addTrustInPlayer(member, player.getUUID(), name, HEARD_MURDER);
					}
				}
			}

			alarm(server, town, player);
			if (criminal.recentKills == MASSACRE) {
				massacre(server, town, player, criminal, victim, witness);
			} else if (criminal.recentKills > MASSACRE) {
				com.dinglebob.drifter.social.ChatLines.speak(witness, com.dinglebob.drifter.social.Lines.pick(witness, "crime.massacre", "player", name,
					"who", victim));
				criminal.exiledUntil = Math.max(criminal.exiledUntil, day + MASSACRE_EXILE);
				criminal.wantedUntil = Math.max(criminal.wantedUntil, day + MASSACRE_EXILE);
				Chronicle.add(server, "law", name + " killed " + victim + " of " + town.name + " too (" + criminal.recentKills + " in all)");
				bell(server, town, player, "the massacre", massacreLines(name), MASSACRE_ALARM);
			} else {
				com.dinglebob.drifter.social.ChatLines.speak(witness, com.dinglebob.drifter.social.Lines.pick(witness, "crime.murder", "player", name,
					"who", victim));
				exile(server, town, criminal, Math.max(MURDER_EXILE, criminal.exiledUntil - day), "killing " + victim);
				criminal.wantedUntil = Math.max(criminal.wantedUntil, day + MURDER_EXILE);
				bell(server, town, player, "the killing of " + victim, killingLines(name, victim), KILLING_ALARM);
				if (!wantedAlready && DrifterApi.postBounty(player, "the people of " + town.name, "the killing of " + victim)) {
					Chronicle.add(server, "law", "The people of " + town.name + " put a price on " + name + "'s head for killing " + victim);
				}
			}

			if (dead.getUUID().equals(town.leader)) {
				leaderMurdered(server, town, player, criminal, victim);
			}
		} else if (drifter != null) {
			String name = drifter.getName().getString();
			DrifterGroups.Group group = townGroup;
			// Those who saw it make it their enemy on sight, and the rest of the town nearly as much.
			for (DrifterEntity one : witnesses) {
				DrifterApi.addTrustInDrifter(one, drifter, SAW_DRIFTER_MURDER);
				DrifterApi.think(one, "saw " + name + " kill " + victim);
			}

			com.dinglebob.drifter.social.ChatLines.speak(witness, com.dinglebob.drifter.social.Lines.pick(witness, "crime.murder", "player", name,
				"who", victim));
			bell(server, town, drifter, "the killing of " + victim, killingLines(name, victim), KILLING_ALARM);
			if (group != null) {
				for (UUID id : group.members) {
					DrifterEntity member = TownFounding.loaded(server, id);
					if (member != null && member != drifter && !witnesses.contains(member)) {
						DrifterApi.addTrustInDrifter(member, drifter, HEARD_DRIFTER_MURDER);
					}
				}
			}

			long[] kills = drifterKills.computeIfAbsent(drifter.getUUID(), id -> new long[] {0, -100});
			kills[0] = day - kills[1] <= MASSACRE_DAYS ? kills[0] + 1 : 1;
			kills[1] = day;
			if (kills[0] == MASSACRE) {
				drifterMassacre(server, town, drifter, (int)kills[0]);
			}

			if (group != null && group.members.contains(drifter.getUUID())) {
				// One of its own: out of the town, for good.
				throwOut(server, town, group, drifter, "killing " + victim);
				town.crimes.remove(drifter.getUUID());
			} else if (group != null) {
				// An outsider: the town's enemy (its people's trust in it, above).
				Chronicle.add(server, "law", name + " killed " + victim + " of " + town.name + ": the town's enemy now");
				// (One of another town's people: between the towns too.)
				Town theirs = Diplomacy.townOf(drifter.getUUID());
				if (theirs != null && theirs != town) {
					Diplomacy.incident(server, town, theirs, -25.0, "killing");
					Chronicle.add(server, "diplomacy", "A killing stands between " + town.name + " and " + theirs.name + " (" + name + " of " + theirs.name
						+ " killed " + victim + ")");
				}
			}
		}

		Towns.save(server);
	}

	/** Who hears the town's alarm: its people this near where it was raised. */
	private static final double ALARM = 96.0;
	/**
	 * How long the alarm bell can ring (0.23.30): for a killing, a massacre, someone wanted back about the town; each only
	 * while the one it's for is about the town (this near its middle: {@link #ALARM}).
	 */
	private static final long KILLING_ALARM = 20L * 60 * 3;
	private static final long MASSACRE_ALARM = 20L * 60 * 5;
	private static final long WANTED_ALARM = 20L * 60 * 2;

	/** The alarm bell for this one (a player or a Drifter): rung while they're about the town, the trouble where they are. */
	private static void bell(MinecraftServer server, Town town, net.minecraft.world.entity.Entity who, String why, List<String> lines, long lasts) {
		UUID id = who.getUUID();
		boolean player = who instanceof ServerPlayer;
		TownBells.alarm(server, town, why, lines, () -> {
			net.minecraft.world.entity.Entity there = about(server, town, id, player);
			return there == null ? null : there.blockPosition();
		}, () -> about(server, town, id, player) != null, lasts);
	}

	/** This one (a player, or a Drifter) alive about the town; else null. */
	@Nullable
	private static net.minecraft.world.entity.Entity about(MinecraftServer server, Town town, UUID id, boolean player) {
		net.minecraft.world.entity.Entity one = player ? server.getPlayerList().getPlayer(id) : TownFounding.loaded(server, id);
		return one != null && one.isAlive() && !one.isRemoved() && one.level().dimension().location().toString().equals(town.dimension)
			&& one.blockPosition().closerThan(town.center, ALARM) ? one : null;
	}

	private static List<String> killingLines(String killer, String victim) {
		return List.of("MURDER! " + killer + " killed " + victim + "!", "guards! " + killer + "'s killed " + victim + "!", "help! murder! it's " + killer + "!");
	}

	private static List<String> massacreLines(String killer) {
		return List.of("MASSACRE! " + killer + " is killing everyone!", "everyone inside! " + killer + "'s killing us!", "to arms! stop " + killer + "!");
	}

	/**
	 * The town's alarm (a killing seen, someone wanted seen back): its people near hear where the one it's after is, and its
	 * guards and bold ones go looking there (the timid keep clear).
	 */
	private static void alarm(MinecraftServer server, Town town, ServerPlayer player) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return;
		}

		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member != null && member.level() == player.level() && member.distanceTo(player) <= ALARM) {
				DrifterApi.alert(member, player, player.blockPosition());
			}
		}
	}

	/**
	 * A massacre (the user, 2026-11-07): three of its people killed within a few days of each other. Wanted there for a
	 * month, its people's trust gone for good, a price on their head; and word goes round the towns about: wanted in each
	 * of them too, for ten days.
	 */
	private static void massacre(MinecraftServer server, Town town, ServerPlayer player, Town.Criminal criminal, String victim, DrifterEntity witness) {
		long day = Chronicle.day(server);
		String name = player.getGameProfile().getName();
		criminal.crime = "massacre";
		criminal.fine = 0;
		criminal.exiledUntil = Math.max(criminal.exiledUntil, day + MASSACRE_EXILE);
		criminal.wantedUntil = Math.max(criminal.wantedUntil, day + MASSACRE_EXILE);
		com.dinglebob.drifter.social.ChatLines.speak(witness, com.dinglebob.drifter.social.Lines.pick(witness, "crime.massacre", "player", name, "who", victim));
		Chronicle.add(server, "law", "The massacre at " + town.name + ": " + name + " killed " + criminal.recentKills + " of its people. Wanted there for "
			+ MASSACRE_EXILE + " days");
		bell(server, town, player, "the massacre", massacreLines(name), MASSACRE_ALARM);
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group != null) {
			for (UUID id : group.members) {
				DrifterEntity member = TownFounding.loaded(server, id);
				if (member != null) {
					DrifterApi.addTrustInPlayer(member, player.getUUID(), name, -100.0);
				}
			}
		}

		if (DrifterApi.postBounty(player, "the people of " + town.name, "the massacre at " + town.name)) {
			Chronicle.add(server, "law", "The people of " + town.name + " put a price on " + name + "'s head for the massacre");
		}

		town.heard.put(player.getUUID(), -100.0);
		int heard = 0;
		for (Town other : Towns.all()) {
			if (other == town || !other.dimension.equals(town.dimension) || !other.center.closerThan(town.center, WORD_SPREADS)) {
				continue;
			}

			Town.Criminal there = other.crimes.computeIfAbsent(player.getUUID(), id -> new Town.Criminal(name));
			there.crime = "massacre";
			there.exiledUntil = Math.max(there.exiledUntil, day + HEARD_MASSACRE);
			there.wantedUntil = Math.max(there.wantedUntil, day + HEARD_MASSACRE);
			other.heard.merge(player.getUUID(), -60.0, (a, b) -> Math.max(-100.0, a + b));
			heard++;
		}

		if (heard > 0) {
			Chronicle.add(server, "law", "Word of the massacre at " + town.name + " reached " + heard + (heard == 1 ? " town" : " towns") + " about: " + name
				+ " is wanted there too");
		}
	}

	/**
	 * Its leader murdered by a player (0.23.35, the user: "there is also not enough impact killing the leader"): the whole
	 * town turns on them for good, wanted and exiled there a month, the alarm rung for it, a price on their head; and word
	 * goes through its nation (or the towns about, if it has none): wanted in every one of its towns for a month too.
	 */
	private static void leaderMurdered(MinecraftServer server, Town town, ServerPlayer player, Town.Criminal criminal, String victim) {
		long day = Chronicle.day(server);
		String name = player.getGameProfile().getName();
		if (!"massacre".equals(criminal.crime)) {
			criminal.crime = "murder of the leader";
		}

		criminal.fine = 0;
		criminal.exiledUntil = Math.max(criminal.exiledUntil, day + LEADER_EXILE);
		criminal.wantedUntil = Math.max(criminal.wantedUntil, day + LEADER_EXILE);
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group != null) {
			for (UUID id : group.members) {
				DrifterEntity member = TownFounding.loaded(server, id);
				if (member != null) {
					DrifterApi.addTrustInPlayer(member, player.getUUID(), name, -100.0);
					DrifterApi.think(member, name + " murdered " + victim + ", our leader");
				}
			}
		}

		town.heard.put(player.getUUID(), -100.0);
		bell(server, town, player, "the murder of " + victim, List.of("THEY KILLED " + victim.toUpperCase(java.util.Locale.ROOT) + "! get " + name + "!",
			name + " murdered " + victim + "! to arms!", victim + "'s dead! don't let " + name + " get away!"), MASSACRE_ALARM);
		if (DrifterApi.postBounty(player, "the people of " + town.name, "the murder of " + victim + ", leader of " + town.name)) {
			Chronicle.add(server, "law", "The people of " + town.name + " put a price on " + name + "'s head for the murder of " + victim);
		}

		// (A nation of its own alone: the towns about, as without one. Found in testing.)
		Nations.Nation found = Nations.of(town);
		Nations.Nation nation = found != null && found.members.size() > 1 ? found : null;
		int told = 0;
		for (Town other : Towns.all()) {
			boolean hears = nation != null ? nation.members.containsKey(other.id)
				: other.dimension.equals(town.dimension) && other.center.closerThan(town.center, WORD_SPREADS);
			if (other == town || !hears) {
				continue;
			}

			Town.Criminal there = other.crimes.computeIfAbsent(player.getUUID(), id -> new Town.Criminal(name));
			there.crime = "murder of the leader";
			long lasts = nation != null ? LEADER_EXILE : HEARD_MASSACRE;
			there.exiledUntil = Math.max(there.exiledUntil, day + lasts);
			there.wantedUntil = Math.max(there.wantedUntil, day + lasts);
			other.heard.merge(player.getUUID(), nation != null ? -80.0 : -60.0, (a, b) -> Math.max(-100.0, a + b));
			told++;
		}

		Chronicle.add(server, "law", town.name + " mourns " + victim + ". " + name + " is wanted there for " + LEADER_EXILE + " days"
			+ (told == 0 ? "" : nation != null ? ", and in all " + told + " other towns of " + nation.name : ", and in " + told + (told == 1 ? " town" : " towns") + " about"));
	}

	/** A Drifter's massacre: the enemy on sight of every town about (their people's trust in it gone). */
	private static void drifterMassacre(MinecraftServer server, Town town, DrifterEntity drifter, int kills) {
		String name = drifter.getName().getString();
		Chronicle.add(server, "law", "The massacre at " + town.name + ": " + name + " killed " + kills + " of its people. Every town about knows its face now");
		bell(server, town, drifter, "the massacre", massacreLines(name), MASSACRE_ALARM);
		for (Town other : Towns.all()) {
			if (!other.dimension.equals(town.dimension) || !other.center.closerThan(town.center, WORD_SPREADS) || other.group == null) {
				continue;
			}

			DrifterGroups.Group group = DrifterApi.groupById(other.group);
			if (group == null) {
				continue;
			}

			for (UUID id : group.members) {
				DrifterEntity member = TownFounding.loaded(server, id);
				if (member != null && member != drifter) {
					DrifterApi.addTrustInDrifter(member, drifter, -80.0);
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Its crooks (a Bandit living in it: the user, 2026-10-28, "if they are in a town they should be commiting several
	// crimes to really give that bandit feel")
	// ---------------------------------------------------------------------------------------------

	private static final int FINE_FIGHT = 2;
	/** A city's crook goes at it this much sooner than a town's (the user, 2026-11-06: "causing much more crime than usual"). */
	static final double CITY_CROOK_PACE = 0.3;
	/** A city with no crook gets one this many days after the last was thrown out (or at once, the first time). */
	private static final long NEW_CROOK_AFTER = 3;
	/** When each city's last crook was thrown out (the day; not saved: after a reload, a new one may come sooner). */
	private static final Map<UUID, Long> crookOut = new HashMap<>();

	/**
	 * A city always has its crook (the user, 2026-11-06: "a town crook should always be in a city"): none, and none thrown
	 * out lately, one of its people turns to it (the one least like a good neighbour: aggressive, greedy, unforgiving; not its leader,
	 * its keeper or a guard).
	 */
	static boolean cityCrook(MinecraftServer server, Town town) {
		if (!Towns.city(town) || !town.crooks.isEmpty()) {
			return false;
		}

		long day = Chronicle.day(server);
		Long out = crookOut.get(town.id);
		if (out != null && day - out < NEW_CROOK_AFTER) {
			return false;
		}

		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null || group.members.size() < 6) {
			return false;
		}

		DrifterEntity pick = null;
		double best = -Double.MAX_VALUE;
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member == null || id.equals(town.leader) || id.equals(group.leader) || id.equals(town.keeper) || "guard".equals(town.lines.get(id))
				|| DrifterApi.tradeRun(member) != null) {
				continue;
			}

			double score = DrifterApi.trait(member, "aggression") + DrifterApi.trait(member, "greed") - DrifterApi.trait(member, "forgiveness");
			if (score > best) {
				best = score;
				pick = member;
			}
		}

		if (pick == null) {
			return false;
		}

		DrifterApi.turnBandit(pick);
		town.crooks.add(pick.getUUID());
		DrifterApi.settleDown(pick);
		DrifterApi.think(pick, "lives in " + town.name + ". a big city. easy pickings");
		DrifterCivilizations.LOGGER.info("[town] {} is {}'s crook now (a city always has one)", pick.getName().getString(), town.name);
		crookOut.remove(town.id);
		return true;
	}

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
		crookOut.put(town.id, Chronicle.day(server));
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
				criminal.wantedUntil = Math.max(criminal.wantedUntil, Chronicle.day(server) + MURDER_EXILE);
				DrifterApi.postBounty(player, "the people of " + town.name, "a killing (debug)");
				Towns.save(server);
			}
			case "massacre" -> {
				Town.Criminal criminal = town.crimes.computeIfAbsent(player.getUUID(), id -> new Town.Criminal(player.getGameProfile().getName()));
				criminal.kills += MASSACRE;
				criminal.recentKills = MASSACRE;
				criminal.lastKillDay = Chronicle.day(server);
				massacre(server, town, player, criminal, "someone (debug)", witness);
				Towns.save(server);
			}
			case "pardon" -> {
				town.crimes.remove(player.getUUID());
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
			// (And wanted for it: its guards make them go, the hard way; the user, 2026-11-07, "enforce".)
			criminal.wantedUntil = Math.max(criminal.wantedUntil, Chronicle.day(server) + EXILE);
			com.dinglebob.drifter.social.ChatLines.speak(guard, com.dinglebob.drifter.social.Lines.pick(guard, "guard.force", "player", criminal.name,
				"town", town.name));
		}

		Towns.save(server);
	}

	/** "wanted" while this player is (its guards and bold people go for them), "exiled" while they're only that. */
	@Nullable
	static String standing(Town town, Player player) {
		Town.Criminal criminal = town.crimes.get(player.getUUID());
		return criminal == null ? null : criminal.wantedUntil > 0 ? "wanted" : criminal.exiledUntil > 0 ? "exiled" : null;
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
