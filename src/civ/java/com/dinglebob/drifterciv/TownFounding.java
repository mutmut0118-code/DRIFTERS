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
	private static final int PLAZA = 12;
	private static final int FARTHEST = 72;
	/** A road's middle to the nearest plot edge, and the gap between plots. */
	private static final int ROAD = 4;
	private static final int GAP = 2;
	/** Plans tried at each spot along the roads. */
	private static final int PLANS_TRIED = 4;
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
			}
		});
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

		if (changed) {
			Towns.save(server);
		}
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

	@Nullable
	private static Town.Plot plotOf(Town town, UUID owner) {
		return town.plots.stream().filter(plot -> owner.equals(plot.owner)).findFirst().orElse(null);
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

	/** One side of one of the two roads: houses along it face {@code front} (toward the road). */
	private record Side(Direction front, int along) {
	}

	private static final List<Side> SIDES = List.of(new Side(Direction.SOUTH, 1), new Side(Direction.SOUTH, -1), new Side(Direction.NORTH, 1),
		new Side(Direction.NORTH, -1), new Side(Direction.EAST, 1), new Side(Direction.EAST, -1), new Side(Direction.WEST, 1), new Side(Direction.WEST, -1));

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

		Town.Plot plot = findPlot(server, town, drifter);
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

	@Nullable
	private static Town.Plot findPlot(MinecraftServer server, Town town, DrifterEntity drifter) {
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
		if (liked.isEmpty()) {
			return null;
		}

		int cx = town.center.getX();
		int cz = town.center.getZ();
		List<DrifterApi.HousePlanInfo> tried = liked.subList(0, Math.min(PLANS_TRIED, liked.size()));
		// (Worked out once per plan: its turns, and what it takes to judge a spot for it.)
		java.util.Map<String, Rotation> turns = new java.util.HashMap<>();
		java.util.Map<String, java.util.function.BiFunction<BlockPos, Rotation, BlockPos>> finders = new java.util.HashMap<>();
		for (int along = PLAZA; along <= FARTHEST; along += 2) {
			for (Side side : SIDES) {
				for (DrifterApi.HousePlanInfo plan : tried) {
					String key = plan.id() + "|" + side.front();
					Rotation rotation = turns.containsKey(key) ? turns.get(key) : turnFor(server, plan.id(), side.front());
					turns.put(key, rotation);
					if (rotation == null) {
						continue;
					}

					boolean turned = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
					int sx = turned ? plan.sizeZ() : plan.sizeX();
					int sz = turned ? plan.sizeX() : plan.sizeZ();
					// Its near end {@code along} blocks out from the plaza's middle, its front {@code ROAD} from the road's.
					int x;
					int z;
					if (side.front().getAxis() == Direction.Axis.Z) {
						x = side.along() > 0 ? cx + along : cx - along - sx + 1;
						z = side.front() == Direction.SOUTH ? cz - ROAD - sz + 1 : cz + ROAD;
					} else {
						z = side.along() > 0 ? cz + along : cz - along - sz + 1;
						x = side.front() == Direction.EAST ? cx - ROAD - sx + 1 : cx + ROAD;
					}

					if (overlaps(town, x, z, sx, sz)) {
						continue;
					}

					BlockPos origin = finders.computeIfAbsent(plan.id(), id -> DrifterApi.siteFinder(drifter, id)).apply(new BlockPos(x, 0, z), rotation);
					if (origin != null) {
						return new Town.Plot(origin, sx, sz, plan.id(), rotation, side.front(), drifter.getUUID());
					}
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

		for (Town.Plot plot : town.plots) {
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
	private static DrifterEntity loaded(MinecraftServer server, UUID id) {
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
