package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/**
 * Towns founded by their people (docs/CIVILIZATION.md step 3b; the town plan the user agreed, 2026-10-21: "go with your
 * recommendations on all of them"):
 * <ul>
 *   <li>neighbours who help each other grow friendly faster: a trade, news passed on (a fight side by side already counts
 *       in Drifter), and neighbours who get along don't turn into rivals over the same ground;</li>
 *   <li>friends team up into groups (Drifter does that); a group of three or more, 300 blocks or more from any town,
 *       founds one round its leader's home, its plaza there, two roads crossing it, and the group takes up to twelve;</li>
 *   <li>the first house is the leader's, on a plot along a road, built by them all (members of a group build its base's
 *       house together);</li>
 *   <li>then each of the others takes the next plot along a road, a plan to its own taste (one of the bigger ones, a
 *       different one from its neighbours' if it can), its door to the road, on ground it has seen, and builds its house:
 *       a home of its own from then on (until it's up it sleeps out or digs in), the group's chests still its too. A
 *       newcomer to the group gets the next plot.</li>
 * </ul>
 * Each block placed by a Drifter, from what it has; nothing appears.
 */
public final class TownFounding {
	private TownFounding() {
	}

	private static final int CHECK_TICKS = 200;
	/** Members a group needs to found a town. */
	public static final int FOUNDERS = 3;
	/** Most members a town's group takes. */
	private static final int TOWN_GROUP = 12;
	/** Trust a trade adds (each way), and news passed on (in the one who told it). */
	private static final double TRADE_TRUST = 6.0;
	private static final double NEWS_TRUST = 2.0;
	/** Plots start this far from the plaza's middle (its edge, and a block), and go out along the roads this far at most. */
	private static final int PLAZA = TownRoads.PLAZA;
	private static final int FARTHEST = TownRoads.FARTHEST;
	/**
	 * A road's middle to the nearest plot edge, and the gap between plots (user, 2026-10-23: "every house is way too close
	 * to eachother"; wooden houses close together all burn when one catches fire).
	 */
	private static final int ROAD = 4;
	private static final int GAP = 6;
	/** Plans tried at each spot along the roads. */
	private static final int PLANS_TRIED = 4;
	/** The town build's name for the store. */
	private static final String STORE = "store";

	/** How long a town's news is worth telling (game ticks: five days). */
	private static final long NEWS_LASTS = 24000L * 5;
	/** A Drifter that found no plot looks again after this (game ticks). */
	private static final long LOOK_AGAIN = 1200L;
	/** When each last looked for a plot and found none, and last said so (not saved). */
	private static final java.util.Map<UUID, Long> lookedAt = new java.util.HashMap<>();
	private static final java.util.Map<UUID, Long> toldAt = new java.util.HashMap<>();
	private static final long TELL_AGAIN = 24000L / 2;

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> DrifterApi.setNeighbourly(true));
		DrifterEvents.TRADED.register((asker, giver, paid, got) -> {
			DrifterApi.addTrustInDrifter(asker, giver, TRADE_TRUST);
			DrifterApi.addTrustInDrifter(giver, asker, TRADE_TRUST);
		});
		DrifterEvents.HEARD_NEWS.register((drifter, news, from) -> {
			if (from != null && from != drifter) {
				DrifterApi.addTrustInDrifter(drifter, from, NEWS_TRUST);
			}
		});
		DrifterEvents.BUILT.register((drifter, what, where) -> {
			if (what.equals("town:" + Plaza.LABEL) && where != null) {
				for (Town town : Towns.all()) {
					for (Town.Plot plot : town.plaza) {
						if (!plot.built && plot.origin.equals(where)) {
							Plaza.built(drifter.getServer(), town, plot);
						}
					}
				}

				return;
			}

			if (what.equals("town:" + Market.STALL) && where != null) {
				for (Town town : Towns.all()) {
					if (!town.stallBuilt && town.stallPlot != null && town.stallPlot.origin.equals(where)) {
						Market.stallBuilt(drifter.getServer(), town);
					}
				}

				return;
			}

			if (what.equals("town:" + STORE) && where != null) {
				for (Town town : Towns.all()) {
					if (!town.storeBuilt && town.storePlot != null && town.storePlot.origin.equals(where)) {
						storeBuilt(drifter.getServer(), town);
					}
				}

				return;
			}

			if (!what.equals("house") || where == null) {
				return;
			}

			for (Town town : Towns.all()) {
				for (Town.Plot plot : town.plots) {
					if (!plot.built && plot.origin.equals(where)) {
						built(drifter.getServer(), town, plot);
					}
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 0) {
				found(server);
				Towns.all().stream().filter(town -> !town.spawned).forEach(town -> grow(server, town));
				// A town made ready (for testing): its keeper's shop and its people's town work (wages, the till) kept set (found:
				// after the world was loaded again, the keeper had no shop).
				Towns.all().stream().filter(town -> town.spawned).forEach(town -> tend(server, town));
				// Trade runs between towns with stalls (step 4); leaders, elections, laws (step 5).
				Towns.all().forEach(town -> TradeRuns.check(server, town));
				// Towns that know of each other: envoys, pacts, standing (step 6, part 1).
				Towns.all().forEach(town -> Diplomacy.check(server, town));
				// Bonds made with people who weren't loaded then (a town made ready): set now they are.
				Towns.all().forEach(town -> TownHistory.applyPending(server, town));
				// Comings and goings (milestone 23 C3): once a day, someone may leave, someone may move in.
				List.copyOf(Towns.all()).forEach(town -> TownLife.check(server, town));
				// Its events (C4): market day, a fishing contest.
				Towns.all().forEach(town -> TownEvents.check(server, town));
				// Its culture (C6): festivals.
				Towns.all().forEach(town -> TownCulture.check(server, town));
				// A Bandit living in a town (one there from before, or one who turned) is its crook: no killing on sight there.
				Towns.all().forEach(town -> settleDown(server, town));
				Towns.all().forEach(town -> {
					boolean politics = Politics.check(server, town);
					if (Justice.check(server, town) || politics) {
						Towns.save(server);
					}
				});
			}
		});
	}

	private static void settleDown(MinecraftServer server, Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return;
		}

		boolean changed = false;
		for (UUID id : group.members) {
			DrifterEntity member = loaded(server, id);
			if (member != null && DrifterApi.bandit(member)) {
				if (town.crooks.add(id)) {
					DrifterApi.think(member, "lives in " + town.name + " now. easy pickings");
					changed = true;
				}

				DrifterApi.settleDown(member);
			}
		}

		if (town.crooks.retainAll(group.members) || changed) {
			Towns.save(server);
		}
	}

	private static void tend(MinecraftServer server, Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return;
		}

		boolean changed = Market.keeper(server, town, group);
		changed |= Trades.tend(server, town, group);
		TownRoads.setWork(server, town, group.members);
		if (changed || town.booksChanged) {
			town.booksChanged = false;
			Towns.save(server);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// The founding
	// ---------------------------------------------------------------------------------------------

	private static void found(MinecraftServer server) {
		for (DrifterGroups.Group group : DrifterApi.groups()) {
			if (group.members.size() < FOUNDERS || Towns.all().stream().anyMatch(town -> group.id.equals(town.group))) {
				continue;
			}

			DrifterEntity leader = loaded(server, group.leader);
			Home home = leader == null ? null : DrifterApi.home(leader);
			if (home == null || home.borrowed() || !home.dimension.equals(net.minecraft.world.level.Level.OVERWORLD.location())) {
				continue;
			}

			// Bandits don't found towns (the user, 2026-10-28: "bandits shouldnt be in towns, kinda like a skyrim bandit"): a
			// Bandit's gang stays a gang, and a Bandit in a group of settlers goes its own way first.
			if (DrifterApi.bandit(leader) || !leaveBandits(server, group) || group.members.size() < FOUNDERS) {
				continue;
			}

			String dimension = home.dimension.toString();
			Town near = Towns.nearest(dimension, home.center);
			if (near != null && near.center.closerThan(home.center, Towns.MIN_SPACING)) {
				continue;
			}

			String name = Towns.newName(leader.getRandom());
			Town town = new Town(UUID.randomUUID(), name, group.id, dimension, home.center, Chronicle.day(server), false);
			group.name = name;
			DrifterApi.setGroupLimit(group, TOWN_GROUP);
			List<String> names = new ArrayList<>();
			for (UUID member : group.members) {
				names.add(group.names.getOrDefault(member, "?"));
				DrifterEntity one = loaded(server, member);
				if (one != null) {
					DrifterApi.setNoHut(one, true);
				}
			}

			String founders = and(names);
			for (UUID member : group.members) {
				DrifterEntity one = loaded(server, member);
				if (one != null) {
					DrifterApi.think(one, "founded the town of " + name + " with " + and(names.stream().filter(n -> !n.equals(one.getName().getString())).toList()));
				}
			}

			Towns.add(server, town);
			Chronicle.add(server, "town", founders + " founded the town of " + name);
			DrifterApi.tell(leader, "town_founded:" + town.id, null, name, name + " was founded by " + founders, NEWS_LASTS, new CompoundTag());
		}
	}

	/**
	 * The Bandits of a group about to found a town leave it (their friends think less of them for it, so they don't team up
	 * again at once). (One not loaded can't be asked: it's let be.) True when it's done.
	 */
	private static boolean leaveBandits(MinecraftServer server, DrifterGroups.Group group) {
		List<DrifterEntity> bandits = new ArrayList<>();
		for (UUID id : group.members) {
			DrifterEntity member = loaded(server, id);
			if (member != null && DrifterApi.bandit(member)) {
				bandits.add(member);
			}
		}

		for (DrifterEntity bandit : bandits) {
			DrifterGroups.leave(bandit.getUUID());
			DrifterApi.setHome(bandit, null);
			DrifterApi.think(bandit, "the others want to settle down in a town. not for it: it went its own way");
			for (UUID id : group.members) {
				DrifterEntity member = loaded(server, id);
				if (member != null) {
					DrifterApi.addTrustInDrifter(member, bandit, -30.0);
					DrifterApi.addTrustInDrifter(bandit, member, -30.0);
				}
			}

			Chronicle.add(server, "town", bandit.getName().getString() + " left " + group.name + ": a bandit doesn't settle in a town");
		}

		return true;
	}

	/** A town takes no Bandits in (its crooks are ones who were there already, or turned). */
	static boolean mayJoin(DrifterEntity drifter, DrifterGroups.Group group) {
		return !DrifterApi.bandit(drifter) || Towns.all().stream().noneMatch(town -> group.id.equals(town.group));
	}

	// ---------------------------------------------------------------------------------------------
	// Plots and houses
	// ---------------------------------------------------------------------------------------------

	/** A founded town's next steps: the leader's house first, then a plot for each of the others, newcomers too. */
	private static void grow(MinecraftServer server, Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return;
		}

		boolean changed = false;
		// A house finished while nobody heard (or before this was saved).
		for (Town.Plot plot : town.plots) {
			DrifterEntity owner = plot.owner == null ? null : loaded(server, plot.owner);
			Home home = owner == null ? null : DrifterApi.home(owner);
			if (!plot.built && home != null && home.planDone && plot.origin.equals(home.planOrigin)) {
				built(server, town, plot);
			}
		}

		// 1. The first house: the leader's (the one it's building already, or one on a plot along a road). Its group mates
		// live at its home, so they build it with it.
		DrifterEntity leader = loaded(server, group.leader);
		if (town.plots.isEmpty() && leader != null) {
			Home home = DrifterApi.home(leader);
			if (home != null && home.planId != null && home.planOrigin != null) {
				Town.Plot plot = plotFor(server, home.planId, home.planOrigin, Rotation.valueOf(home.planRotation), leader.getUUID());
				if (plot != null) {
					plot.built = home.planDone;
					town.plots.add(plot);
					changed = true;
				}
			} else if (home != null) {
				Town.Plot plot = choosePlot(server, town, leader);
				if (plot != null) {
					DrifterApi.buildHouseAt(leader, plot.planId, plot.origin, plot.rotation);
					town.plots.add(plot);
					DrifterApi.think(leader, "marked out the first house of " + town.name + " (" + plot.planId + "): everyone builds it together");
					changed = true;
				}
			}
		}

		// 2. A house each, once the first is up: the next plot along a road, a home of its own there. Newcomers too.
		Home base = leader == null ? null : DrifterApi.home(leader);
		if (!town.plots.isEmpty() && town.plots.get(0).built && base != null) {
			for (UUID id : group.members) {
				DrifterEntity member = loaded(server, id);
				if (member == null || plotOf(town, id) != null) {
					continue;
				}

				// A spare room in a house that's up first (a house with more than one room takes more than one household; user,
				// 2026-10-23: "that way there is more citizens without having more houses"): it moves in, its bed in a room
				// of its own, the house's chests shared.
				Town.Plot spare = spareRoom(server, town);
				if (spare != null) {
					Home shared = DrifterApi.homeAtHouse(member, spare.planId, spare.origin, spare.rotation, base.chests());
					if (shared != null) {
						spare.residents.add(id);
						var owner = spare.owner == null ? null : DrifterApi.record(spare.owner);
						DrifterApi.think(member, "moved into a room in " + (owner == null ? "a house" : owner.name() + "'s house") + " in " + town.name);
						Chronicle.add(server, "town", member.getName().getString() + " moved into a room in " + (owner == null ? "a house" : owner.name()
							+ "'s house") + " in " + town.name);
						changed = true;
						continue;
					}
				}

				Town.Plot plot = choosePlot(server, town, member);
				if (plot == null) {
					continue;
				}

				Home own = DrifterApi.homeAtHouse(member, plot.planId, plot.origin, plot.rotation, base.chests());
				if (own != null) {
					town.plots.add(plot);
					DrifterApi.think(member, "took a plot in " + town.name + " and is building a house of its own there (" + plot.planId + ")");
					Chronicle.add(server, "town", member.getName().getString() + " took a plot in " + town.name);
					changed = true;
				}
			}

			// The group's chests (at the leader's house) are each household's too.
			for (Town.Plot plot : town.plots) {
				DrifterEntity owner = plot.owner == null || plot.owner.equals(group.leader) ? null : loaded(server, plot.owner);
				if (owner != null && DrifterApi.group(owner.getUUID()) == group) {
					DrifterApi.addChests(owner, base.chests());
				}
			}
		}

		changed |= store(server, town, group);
		changed |= Market.stall(server, town, group);
		changed |= Trades.tend(server, town, group);
		// The plaza: a well, a notice board, lamp posts; the board kept up to date.
		changed |= Plaza.build(server, town, group);
		Plaza.board(server, town);
		// Side lanes as it grows, and everyone in on the town's work (its roads, its lights, the ground round their houses).
		changed |= TownRoads.grow(town);
		TownRoads.setWork(server, town, group.members);
		changed |= town.booksChanged;
		town.booksChanged = false;

		if (changed) {
			Towns.save(server);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// The store (step 3c)
	// ---------------------------------------------------------------------------------------------

	/**
	 * The town's store, built by them all once half its households have their houses (two at least): a house of chests (the
	 * smallest one-storey plan there is, chests along its inside walls) on the nearest free plot to the plaza. Each of them
	 * works on it alongside its own house, every chest made and placed by one of them. When it's up its chests are
	 * everyone's (what one puts in, the others can take). True if anything changed.
	 */
	private static boolean store(MinecraftServer server, Town town, DrifterGroups.Group group) {
		long built = town.plots.stream().filter(plot -> plot.built).count();
		if (town.storeBuilt) {
			// (Those away when it was finished too: on with their own houses, the store's chests theirs.)
			for (UUID id : group.members) {
				DrifterEntity member = loaded(server, id);
				if (member != null) {
					var build = DrifterApi.townBuildOf(member);
					if (build != null && build.label.equals(STORE)) {
						DrifterApi.endTownBuild(member);
					}

					DrifterApi.addChests(member, town.store);
				}
			}

			return false;
		}

		boolean changed = false;
		if (town.storePlot == null) {
			DrifterEntity leader = loaded(server, group.leader);
			if (leader == null || built < 2 || built * 2 < town.plots.size()) {
				return false;
			}

			DrifterApi.HousePlanInfo base = DrifterApi.housePlans(server).stream()
				.filter(plan -> plan.id().startsWith("house:") && plan.sizeY() <= 12 && Math.max(plan.sizeX(), plan.sizeZ()) >= TownBuilder.SMALLEST_HOUSE)
				.min(Comparator.comparingInt(plan -> plan.sizeX() * plan.sizeY() * plan.sizeZ())).orElse(null);
			if (base == null) {
				return false;
			}

			DrifterApi.HousePlanInfo store = new DrifterApi.HousePlanInfo(DrifterApi.storePlan(base.id()), base.sizeX(), base.sizeY(), base.sizeZ(),
				base.groundLayer());
			Town.Plot plot = findPlotByAnyone(server, town, group, List.of(store), false);
			if (plot == null) {
				return false;
			}

			plot.owner = null;
			town.storePlot = plot;
			List<DrifterEntity> builders = new ArrayList<>();
			for (UUID id : group.members) {
				DrifterEntity member = loaded(server, id);
				if (member != null && member != leader) {
					builders.add(member);
				}
			}

			builders.add(0, leader);
			DrifterApi.townBuild(builders, plot.planId, plot.origin, plot.rotation, STORE);
			builders.forEach(builder -> DrifterApi.think(builder, "is to help build the store of " + town.name));
			Chronicle.add(server, "town", "The people of " + town.name + " began building its store");
			changed = true;
		}

		// Those not here when it was begun (or newcomers since) join in, in the same woods.
		Home.Build any = null;
		for (UUID id : group.members) {
			DrifterEntity member = loaded(server, id);
			var build = member == null ? null : DrifterApi.townBuildOf(member);
			if (build != null && build.label.equals(STORE)) {
				any = build;
			}
		}

		if (any != null) {
			for (UUID id : group.members) {
				DrifterEntity member = loaded(server, id);
				if (member != null && DrifterApi.townBuildOf(member) == null) {
					DrifterApi.joinTownBuild(member, any.planId, any.origin, Rotation.valueOf(any.rotation), STORE, any.woods, any.deepslate);
				}
			}
		}

		return changed;
	}

	static void storeBuilt(@Nullable MinecraftServer server, Town town) {
		if (server == null || town.storePlot == null) {
			return;
		}

		town.storeBuilt = true;
		town.store.clear();
		town.store.addAll(DrifterApi.chestsIn(server, town.storePlot.planId, town.storePlot.origin, town.storePlot.rotation));
		Chronicle.add(server, "town", "The store of " + town.name + " was finished (" + town.store.size() + " chests)");
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group != null) {
			store(server, town, group);
		}

		Towns.save(server);
	}

	private static void built(@Nullable MinecraftServer server, Town town, Town.Plot plot) {
		plot.built = true;
		var record = plot.owner == null ? null : DrifterApi.record(plot.owner);
		String who = record == null ? "someone" : record.name();
		Chronicle.add(server, "town", (town.plots.indexOf(plot) == 0 ? "The first house in " + town.name + " was finished, " + who + "'s"
			: who + " finished a house in " + town.name));
		if (server != null) {
			Towns.save(server);
		}
	}

	/** Its plot: the one it owns, or the one whose house it lives in. */
	@Nullable
	private static Town.Plot plotOf(Town town, UUID owner) {
		return town.plots.stream().filter(plot -> owner.equals(plot.owner) || plot.residents.contains(owner)).findFirst().orElse(null);
	}

	/** A house that's up with a room nobody lives in yet (the nearest the plaza first), or null. */
	@Nullable
	private static Town.Plot spareRoom(MinecraftServer server, Town town) {
		ServerLevel level = null;
		for (ServerLevel one : server.getAllLevels()) {
			if (one.dimension().location().toString().equals(town.dimension)) {
				level = one;
			}
		}

		if (level == null) {
			return null;
		}

		Town.Plot best = null;
		for (Town.Plot plot : town.plots) {
			if (!plot.built || plot.owner == null) {
				continue;
			}

			if (plot.room == 0 && level.isLoaded(plot.origin)) {
				plot.room = DrifterApi.households(level, plot.planId, plot.origin, plot.rotation);
			}

			if (1 + plot.residents.size() < plot.room && (best == null || plot.origin.distSqr(town.center) < best.origin.distSqr(town.center))) {
				best = plot;
			}
		}

		return best;
	}

	/** A plot record for a house already chosen (its size once turned, the side its door's on). */
	@Nullable
	private static Town.Plot plotFor(MinecraftServer server, String planId, BlockPos origin, Rotation rotation, UUID owner) {
		DrifterApi.HousePlanInfo info = DrifterApi.housePlans(server).stream().filter(plan -> plan.id().equals(planId)).findFirst().orElse(null);
		if (info == null) {
			return null;
		}

		boolean turned = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
		var doors = DrifterApi.doorSides(server, planId, rotation);
		return new Town.Plot(origin, turned ? info.sizeZ() : info.sizeX(), turned ? info.sizeX() : info.sizeZ(), planId, rotation,
			doors.isEmpty() ? Direction.SOUTH : doors.iterator().next(), owner);
	}

	/** A place along one side of a road for the next plot: the road, the way out from it, how far along. */
	private record Spot(TownRoads.Road road, Direction out, int along) {
	}

	/**
	 * The nearest free plot to the plaza along the roads that suits one of the plans it likes (the bigger houses of a
	 * town, a different one from its neighbours' if it can), turned to face the road, on ground it has seen and could build
	 * on. Null if there's none it knows of yet (it learns more of the land round home as days go by).
	 */
	@Nullable
	private static Town.Plot choosePlot(MinecraftServer server, Town town, DrifterEntity drifter) {
		long now = drifter.level().getGameTime();
		if (now - lookedAt.getOrDefault(drifter.getUUID(), Long.MIN_VALUE / 2) < LOOK_AGAIN) {
			return null;
		}

		Town.Plot plot = findPlot(server, town, drifter, liked(town, drifter), false);
		if (plot == null) {
			lookedAt.put(drifter.getUUID(), now);
			if (now - toldAt.getOrDefault(drifter.getUUID(), Long.MIN_VALUE / 2) > TELL_AGAIN) {
				toldAt.put(drifter.getUUID(), now);
				DrifterApi.think(drifter, "found no plot in " + town.name + " it could build on yet (" + DrifterApi.siteRejections() + ")");
			}
		} else {
			lookedAt.remove(drifter.getUUID());
		}

		return plot;
	}

	/**
	 * A plot for something of the town's (its store, its stall) on ground any of its people has seen, the leader's knowledge
	 * first (found in testing: a store never begun in days, the leader having seen too little of the ground along the roads,
	 * "ground it hasn't seen" 528 times).
	 */
	@Nullable
	static Town.Plot findPlotByAnyone(MinecraftServer server, Town town, DrifterGroups.Group group, List<DrifterApi.HousePlanInfo> liked, boolean doorAway) {
		List<UUID> order = new ArrayList<>(List.of(group.leader));
		group.members.stream().filter(id -> !id.equals(group.leader)).forEach(order::add);
		for (UUID id : order) {
			DrifterEntity member = loaded(server, id);
			Town.Plot plot = member == null ? null : findPlot(server, town, member, liked, doorAway);
			if (plot != null) {
				return plot;
			}

		}

		return null;
	}

	@Nullable
	static Town.Plot findPlot(MinecraftServer server, Town town, DrifterEntity drifter, List<DrifterApi.HousePlanInfo> liked, boolean doorAway) {
		if (liked.isEmpty()) {
			return null;
		}

		int cx = town.center.getX();
		int cz = town.center.getZ();
		List<DrifterApi.HousePlanInfo> tried = liked.subList(0, Math.min(PLANS_TRIED, liked.size()));
		return search(server, town, drifter, tried, cx, cz, doorAway);
	}

	/** The town-sized plans it likes, in the order it would try them. */
	private static List<DrifterApi.HousePlanInfo> liked(Town town, DrifterEntity drifter) {
		List<DrifterApi.HousePlanInfo> liked = new ArrayList<>(DrifterApi.housePlansFor(drifter).stream()
			.filter(plan -> Math.max(plan.sizeX(), plan.sizeZ()) >= TownBuilder.SMALLEST_HOUSE && Math.max(plan.sizeX(), plan.sizeZ()) <= TownBuilder.BIGGEST_HOUSE
				&& plan.sizeY() <= 24)
			.toList());
		// (Ones nobody in the town has built first. The town's first house, the one they all build and share at first, the
		// smallest it likes: found in testing, a first house of two storeys and stone bricks kept three founders at it for days.)
		if (town.plots.isEmpty()) {
			liked.sort(Comparator.comparingInt(plan -> plan.sizeX() * plan.sizeY() * plan.sizeZ()));
		} else {
			liked.sort(Comparator.comparingInt(plan -> town.plots.stream().anyMatch(plot -> plot.planId.equals(plan.id())) ? 1 : 0));
		}

		return liked;
	}

	@Nullable
	private static Town.Plot search(MinecraftServer server, Town town, DrifterEntity drifter, List<DrifterApi.HousePlanInfo> tried, int cx, int cz,
		boolean doorAway) {
		// (Worked out once per plan: its turns, and what it takes to judge a spot for it.)
		java.util.Map<String, Rotation> turns = new java.util.HashMap<>();
		java.util.Map<String, java.util.function.BiFunction<BlockPos, Rotation, BlockPos>> finders = new java.util.HashMap<>();
		// Along both sides of every road and lane, the nearest the plaza first.
		List<TownRoads.Road> roads = TownRoads.roads(town);
		List<Spot> spots = new ArrayList<>();
		for (TownRoads.Road road : roads) {
			for (int along = 0; along < road.length(); along += 2) {
				spots.add(new Spot(road, road.way().getClockWise(), along));
				spots.add(new Spot(road, road.way().getCounterClockWise(), along));
			}
		}

		spots.sort(Comparator.comparingDouble(spot -> spot.road().at(spot.along()).distSqr(new BlockPos(cx, 0, cz))));
		for (Spot spot : spots) {
			Direction front = spot.out().getOpposite();
			for (DrifterApi.HousePlanInfo plan : tried) {
				String key = plan.id() + "|" + front;
				Rotation rotation = turns.containsKey(key) ? turns.get(key) : turnFor(server, plan.id(), doorAway ? front.getOpposite() : front);
				turns.put(key, rotation);
				if (rotation == null) {
					continue;
				}

				boolean turned = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
				int sx = turned ? plan.sizeZ() : plan.sizeX();
				int sz = turned ? plan.sizeX() : plan.sizeZ();
				boolean alongX = spot.road().way().getAxis() == Direction.Axis.X;
				int along = alongX ? sx : sz;
				int depth = alongX ? sz : sx;
				if (spot.along() + along > spot.road().length()) {
					continue;
				}

				// Its front {@code ROAD} from the road's middle, running along it from here.
				TownRoads.Rect site = TownRoads.Rect.of(spot.road().at(spot.along()).relative(spot.out(), ROAD),
					spot.road().at(spot.along() + along - 1).relative(spot.out(), ROAD + depth - 1));
				if (overlaps(town, site.x0(), site.z0(), sx, sz) || roads.stream().anyMatch(road -> road.rect().near(site, 1))) {
					continue;
				}

				BlockPos origin = finders.computeIfAbsent(plan.id(), id -> DrifterApi.siteFinder(drifter, id)).apply(new BlockPos(site.x0(), 0, site.z0()), rotation);
				if (origin != null) {
					return new Town.Plot(origin, sx, sz, plan.id(), rotation, front, drifter.getUUID());
				}
			}
		}

		// No plot along the roads suits (a hill, water): the best spot it knows near the plaza, off it and the other plots
		// (found in testing: in hills, the roads' straight lines ran over slopes too steep for any plot).
		for (DrifterApi.HousePlanInfo plan : tried) {
			DrifterApi.HouseSite site = DrifterApi.siteNear(drifter, plan.id());
			if (site == null || !site.origin().closerThan(town.center, FARTHEST)) {
				continue;
			}

			boolean turned = site.rotation() == Rotation.CLOCKWISE_90 || site.rotation() == Rotation.COUNTERCLOCKWISE_90;
			int sx = turned ? plan.sizeZ() : plan.sizeX();
			int sz = turned ? plan.sizeX() : plan.sizeZ();
			if (!overlaps(town, site.origin().getX(), site.origin().getZ(), sx, sz)) {
				var doors = DrifterApi.doorSides(server, plan.id(), site.rotation());
				return new Town.Plot(site.origin(), sx, sz, plan.id(), site.rotation(), doors.isEmpty() ? Direction.SOUTH : doors.iterator().next(),
					drifter.getUUID());
			}
		}

		return null;
	}

	/** Over another plot (with the gap between plots), or on the plaza. */
	private static boolean overlaps(Town town, int x, int z, int sx, int sz) {
		int cx = town.center.getX();
		int cz = town.center.getZ();
		if (x <= cx + PLAZA - 1 && x + sx - 1 >= cx - PLAZA + 1 && z <= cz + PLAZA - 1 && z + sz - 1 >= cz - PLAZA + 1) {
			return true;
		}

		List<Town.Plot> taken = new ArrayList<>(town.plots);
		if (town.storePlot != null) {
			taken.add(town.storePlot);
		}

		if (town.stallPlot != null) {
			taken.add(town.stallPlot);
		}

		for (Town.Plot plot : taken) {
			if (x <= plot.origin.getX() + plot.sizeX - 1 + GAP && x + sx - 1 >= plot.origin.getX() - GAP && z <= plot.origin.getZ() + plot.sizeZ - 1 + GAP
				&& z + sz - 1 >= plot.origin.getZ() - GAP) {
				return true;
			}
		}

		return false;
	}

	/** The turn that puts a door of this plan on {@code front}, or null. */
	@Nullable
	static Rotation turnFor(MinecraftServer server, String plan, Direction front) {
		for (Rotation rotation : Rotation.values()) {
			if (DrifterApi.doorSides(server, plan, rotation).contains(front)) {
				return rotation;
			}
		}

		return null;
	}

	@Nullable
	static DrifterEntity loaded(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof DrifterEntity drifter && drifter.isAlive()) {
				return drifter;
			}
		}

		return null;
	}

	/** "A, B and C". */
	private static String and(List<String> names) {
		if (names.size() <= 1) {
			return names.isEmpty() ? "" : names.get(0);
		}

		return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
	}
}
