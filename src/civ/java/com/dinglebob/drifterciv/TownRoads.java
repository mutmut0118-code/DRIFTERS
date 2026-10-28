package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;

/**
 * The roads of a town founded by its people (user, 2026-10-23: "give founded towns the side lanes, foundations and lighting
 * too"): four roads out from the plaza and side lanes off them as it grows (a lane for every few households), its plots
 * along them all; and the work its people share: the roads made into path, the town's ground lit, the ground round each
 * house eased into the land ({@link TownWork}), each of it by their own hands.
 */
final class TownRoads {
	private TownRoads() {
	}

	/** The plaza's half-width; the main roads start at its edge and run this far from its middle. */
	static final int PLAZA = 12;
	static final int FARTHEST = 96;
	/** Side lanes: their lengths, how far out along a main road they leave it, and how many the town's households earn. */
	private static final int SHORTEST_LANE = 22;
	private static final int LONGEST_LANE = 38;
	private static final int LANE_FROM = 20;
	private static final int LANE_TO = 60;
	private static final int HOUSEHOLDS_A_LANE = 3;
	private static final int MOST_LANES = 6;
	/** How far round its roads, houses and the rest the town's own ground goes (lit at night), and its roads past the houses. */
	private static final int TOWN_GROUND = 6;
	private static final int ROAD_BEYOND = 8;
	/** How near a plot a road counts as leading to it. */
	private static final int ROAD_TO_PLOT = 6;

	/** A road's middle line: from (x, z) this way, so long; three wide. */
	record Road(int x, int z, Direction way, int length) {
		BlockPos at(int t) {
			return new BlockPos(this.x + this.way.getStepX() * t, 0, this.z + this.way.getStepZ() * t);
		}

		Rect rect() {
			Direction across = this.way.getClockWise();
			return Rect.of(this.at(0).relative(across, 1), this.at(this.length - 1).relative(across, -1));
		}
	}

	/** A rectangle of ground (min and max x and z, inclusive). */
	record Rect(int x0, int z0, int x1, int z1) {
		static Rect of(BlockPos a, BlockPos b) {
			return new Rect(Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ()));
		}

		static Rect of(Town.Plot plot) {
			return new Rect(plot.origin.getX(), plot.origin.getZ(), plot.origin.getX() + plot.sizeX - 1, plot.origin.getZ() + plot.sizeZ - 1);
		}

		boolean near(Rect other, int gap) {
			return this.x0 - gap <= other.x1 && other.x0 <= this.x1 + gap && this.z0 - gap <= other.z1 && other.z0 <= this.z1 + gap;
		}

		/** Steps out from this rectangle to x, z (0 inside it). */
		int away(int x, int z) {
			return Math.max(Math.max(this.x0 - x, x - this.x1), Math.max(Math.max(this.z0 - z, z - this.z1), 0));
		}
	}

	/** The town's four main roads and its lanes. */
	static List<Road> roads(Town town) {
		List<Road> roads = new ArrayList<>();
		for (Direction way : Direction.Plane.HORIZONTAL) {
			roads.add(new Road(town.center.getX() + way.getStepX() * PLAZA, town.center.getZ() + way.getStepZ() * PLAZA, way, FARTHEST - PLAZA));
		}

		roads.addAll(town.lanes);
		return roads;
	}

	static Rect plaza(Town town) {
		return new Rect(town.center.getX() - PLAZA + 1, town.center.getZ() - PLAZA + 1, town.center.getX() + PLAZA - 1, town.center.getZ() + PLAZA - 1);
	}

	/** Everything built or marked out in it: the plaza, its plots, the store and the stall. */
	static List<Rect> taken(Town town) {
		List<Rect> taken = new ArrayList<>();
		taken.add(plaza(town));
		town.plots.forEach(plot -> taken.add(Rect.of(plot)));
		if (town.storePlot != null) {
			taken.add(Rect.of(town.storePlot));
		}

		if (town.stallPlot != null) {
			taken.add(Rect.of(town.stallPlot));
		}

		return taken;
	}

	/**
	 * A side lane more as the town grows (one for every few households, a few at most), off a main road out into the
	 * land, clear of its plots and of the other lanes. True if one was laid out.
	 */
	static boolean grow(Town town) {
		int want = Math.min(MOST_LANES, town.plots.size() / HOUSEHOLDS_A_LANE);
		if (town.lanes.size() >= want) {
			return false;
		}

		Random random = new Random(town.id.getLeastSignificantBits() * 31L + town.lanes.size() * 7919L + town.plots.size());
		List<Road> roads = roads(town);
		List<Rect> taken = taken(town);
		for (int tries = 0; tries < 32; tries++) {
			Road main = roads.get(random.nextInt(4));
			int t = LANE_FROM - PLAZA + random.nextInt(LANE_TO - LANE_FROM);
			Direction way = random.nextBoolean() ? main.way().getClockWise() : main.way().getCounterClockWise();
			BlockPos from = main.at(t).relative(way, 2);
			Road lane = new Road(from.getX(), from.getZ(), way, SHORTEST_LANE + random.nextInt(LONGEST_LANE - SHORTEST_LANE + 1));
			Rect rect = lane.rect();
			if (taken.stream().noneMatch(other -> other.near(rect, 8)) && town.lanes.stream().noneMatch(other -> other.rect().near(rect, 20))) {
				town.lanes.add(lane);
				return true;
			}
		}

		return false;
	}

	/** Its work for this town: its roads, its ground (see {@link TownWork}). */
	static final class Work implements TownWork {
		final Town town;
		private List<BlockPos> roadColumns = List.of();
		private List<Rect> ground = List.of();
		private int radius;
		/** What it was worked out for (plots and lanes so far). */
		private int forPlots = -1;
		private int forLanes = -1;

		Work(Town town) {
			this.town = town;
		}

		/** Worked out again when the town has grown. */
		private void refresh() {
			if (this.forPlots == this.town.plots.size() + (this.town.storePlot != null ? 1 : 0) + (this.town.stallPlot != null ? 1 : 0)
				&& this.forLanes == this.town.lanes.size()) {
				return;
			}

			this.forPlots = this.town.plots.size() + (this.town.storePlot != null ? 1 : 0) + (this.town.stallPlot != null ? 1 : 0);
			this.forLanes = this.town.lanes.size();
			List<Rect> taken = taken(this.town);
			// The roads as far as they lead to something (a few blocks past its last house), the plaza's middle out.
			List<BlockPos> columns = new ArrayList<>();
			List<Rect> roadRects = new ArrayList<>();
			for (Road road : TownRoads.roads(this.town)) {
				int last = -1;
				for (int t = 0; t < road.length(); t++) {
					BlockPos at = road.at(t);
					if (taken.stream().anyMatch(rect -> rect.away(at.getX(), at.getZ()) <= ROAD_TO_PLOT)) {
						last = t;
					}
				}

				int end = Math.min(road.length() - 1, last + ROAD_BEYOND);
				if (last < 0) {
					continue;
				}

				for (int t = 0; t <= end; t++) {
					for (int across = -1; across <= 1; across++) {
						columns.add(road.at(t).relative(road.way().getClockWise(), across));
					}
				}

				Direction across = road.way().getClockWise();
				roadRects.add(Rect.of(road.at(0).relative(across, 1), road.at(end).relative(across, -1)));
			}

			BlockPos middle = this.town.center;
			columns.sort(Comparator.comparingDouble(column -> column.distSqr(new BlockPos(middle.getX(), 0, middle.getZ()))));
			this.roadColumns = List.copyOf(columns);
			List<Rect> ground = new ArrayList<>(taken);
			ground.addAll(roadRects);
			this.ground = List.copyOf(ground);
			int radius = PLAZA;
			for (Rect rect : ground) {
				radius = Math.max(radius, Math.max(Math.max(Math.abs(rect.x0() - middle.getX()), Math.abs(rect.x1() - middle.getX())),
					Math.max(Math.abs(rect.z0() - middle.getZ()), Math.abs(rect.z1() - middle.getZ()))));
			}

			this.radius = radius + TOWN_GROUND;
		}

		@Override
		public List<BlockPos> roads(DrifterEntity drifter) {
			this.refresh();
			return this.roadColumns;
		}

		@Override
		public boolean townGround(int x, int z) {
			this.refresh();
			for (Rect rect : this.ground) {
				if (rect.away(x, z) <= TOWN_GROUND) {
					return true;
				}
			}

			return false;
		}

		@Override
		public BlockPos center() {
			return this.town.center;
		}

		@Override
		public void worked(DrifterEntity drifter, String job, int units) {
			Wages.worked(this.town, drifter, job, units);
		}

		@Override
		public double owed(DrifterEntity drifter) {
			return this.town.owed.getOrDefault(drifter.getUUID(), 0.0);
		}

		@Override
		@org.jetbrains.annotations.Nullable
		public BlockPos till() {
			return this.town.till();
		}

		@Override
		public void paid(DrifterEntity drifter, double points) {
			Wages.paid(drifter.getServer(), this.town, drifter, points);
		}

		@Override
		public int taxOwed(DrifterEntity drifter) {
			return this.town.taxOwed.getOrDefault(drifter.getUUID(), 0);
		}

		@Override
		public void taxPaid(DrifterEntity drifter, int emeralds) {
			Politics.taxPaid(drifter.getServer(), this.town, drifter, emeralds);
		}

		@Override
		public void talk(DrifterEntity drifter, java.util.Map<String, Object> words, java.util.Set<String> flags) {
			Politics.talk(drifter, this.town, words, flags);
		}

		@Override
		@org.jetbrains.annotations.Nullable
		public Arrest arrest(DrifterEntity guard) {
			return Justice.arrest(this.town, guard);
		}

		@Override
		public void arrested(DrifterEntity guard, java.util.UUID player, int paid, boolean refused) {
			Justice.arrested(this.town, guard, player, paid, refused);
		}

		@Override
		@org.jetbrains.annotations.Nullable
		public String standing(DrifterEntity drifter, net.minecraft.world.entity.player.Player player) {
			return Justice.standing(this.town, player);
		}

		@Override
		public boolean clearOfTrees(int x, int z) {
			// (A founded town's plaza, once its stall is up: the plaza's next.)
			return !this.town.spawned && this.town.stallBuilt && plaza(this.town).away(x, z) <= 0;
		}

		@Override
		public List<BlockPos> water(DrifterEntity drifter) {
			return drifter.getServer() == null ? List.of() : Plaza.water(drifter.getServer(), this.town, drifter);
		}

		@Override
		public int radius() {
			this.refresh();
			return this.radius;
		}
	}

	/** Each of its people in the town's work (not saved: set again each check). */
	static void setWork(MinecraftServer server, Town town, Iterable<java.util.UUID> members) {
		for (java.util.UUID id : members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member != null && !(member.getTownWork() instanceof Work work && work.town == town)) {
				DrifterApi.setTownWork(member, works.compute(town.id, (key, work) -> work != null && work.town == town ? work : new Work(town)));
			}
		}
	}

	private static final java.util.Map<java.util.UUID, Work> works = new java.util.HashMap<>();
}
