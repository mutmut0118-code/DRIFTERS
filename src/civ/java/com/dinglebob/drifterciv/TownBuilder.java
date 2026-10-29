package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A town made at once, everything already there (the user, 2026-10-21: "can there be an option that a town can spawn?
 * with everything already established, primarily for testing"): `/drifterciv town spawn`. On the land as it is, like a
 * Minecraft village (the user, 2026-10-22: "lets make it go on top of the land"; 2026-10-23: "way more spaced out and
 * dynamic like a village"): only the plaza evened out; four roads out from it and side roads off them, of dirt path over
 * the ground (planks over water); the houses along them with open ground between (and so no fire takes the whole town),
 * each where the ground suits it at its own height, on a stone foundation where the land falls away and the land round it
 * eased up to it; trees in the way felled whole. A plaza with a well, a bell and a notice board, a store of chests with
 * some stock, two fields and a pen of animals, a market stall, and a house from the plans for each household, each
 * different, its door to the road, furnished as a player furnishes a base; a Drifter moved into each, well on in its life,
 * friends with the rest, one group named after the town; lit all over. A debug command: blocks and things appear (a town
 * founded by its people is built block by block).
 */
public final class TownBuilder {
	private TownBuilder() {
	}

	public static final int DEFAULT_HOUSEHOLDS = 6;
	public static final int MAX_HOUSEHOLDS = 12;
	/** A town's houses: the bigger ones (the user, 2026-10-22: "they need to be a bit bigger, especially for a town"). */
	static final int SMALLEST_HOUSE = 15;
	static final int BIGGEST_HOUSE = 25;
	/** The plaza's half-width (user, 2026-10-23: "its way too big and it makes a bit of a hole when its on hill"). */
	private static final int PLAZA = 7;
	/** Air cleared above the plaza (trees). */
	private static final int CLEAR_ABOVE = 12;
	/** Most a house's ground may rise and fall across its footprint (its foundation and the land eased round it make up the rest). */
	private static final int MOST_STEP = 7;
	/** Along the road to the next spot when one doesn't suit a house. */
	private static final int SKIP = 4;
	/** The main roads, each way out from the plaza. */
	private static final int ARM = 72;
	/** Side roads off them. */
	private static final int SHORTEST_BRANCH = 22;
	private static final int LONGEST_BRANCH = 38;
	/** How far out from the plaza a house may go (each way). */
	private static final int REACH = PLAZA + ARM + 12;
	/**
	 * Open ground between houses, at least, and how much more at most (user, 2026-10-23: "every house is way too close to
	 * eachother"; and wooden houses close together all burn when one catches fire).
	 */
	private static final int HOUSE_GAP = 6;
	private static final int MORE_GAP = 6;
	/** From the road's edge to a house's front, at least and at most. */
	private static final int LEAST_SETBACK = 2;
	private static final int MOST_SETBACK = 5;
	/** How far round its houses, roads, fields and the rest the town's ground goes (lit at night). */
	private static final int TOWN_GROUND = 6;
	/** Over how many blocks the land round the plaza, the store and the fields eases to their ground (wider: they're bigger). */
	private static final int WIDE_BLEND = 5;
	/** Over how many blocks the land round a house eases to its ground. */
	private static final int BLEND = 3;
	private static final String[] WOODS = {"oak", "spruce", "birch", "dark_oak", "acacia", "cherry"};

	public record Result(@Nullable Town town, String message) {
	}


	/** A rectangle of the town's ground (min and max x and z, inclusive), to keep things apart. */
	private record Rect(int x0, int z0, int x1, int z1) {
		static Rect of(BlockPos a, BlockPos b) {
			return new Rect(Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ()));
		}

		boolean near(Rect other, int gap) {
			return this.x0 - gap <= other.x1 && other.x0 <= this.x1 + gap && this.z0 - gap <= other.z1 && other.z0 <= this.z1 + gap;
		}

		boolean contains(int x, int z) {
			return x >= this.x0 && x <= this.x1 && z >= this.z0 && z <= this.z1;
		}

		/** Steps out from this rectangle to x, z (0 inside it). */
		int away(int x, int z) {
			return Math.max(Math.max(this.x0 - x, x - this.x1), Math.max(Math.max(this.z0 - z, z - this.z1), 0));
		}
	}

	/** A road: its middle line from (x, z), this way, so long; three wide. */
	private record Road(int x, int z, Direction way, int length) {
		BlockPos at(int t) {
			return new BlockPos(this.x + this.way.getStepX() * t, 0, this.z + this.way.getStepZ() * t);
		}

		Rect rect() {
			Direction across = this.way.getClockWise();
			return Rect.of(this.at(0).relative(across, 1), this.at(this.length - 1).relative(across, -1));
		}
	}

	/** One side of a road houses go along: the way out from the road (their fronts face back to it), and how far along. */
	private static final class Side {
		final Road road;
		final Direction out;
		int cursor;
		boolean full;

		Side(Road road, Direction out, int cursor) {
			this.road = road;
			this.out = out;
			this.cursor = cursor;
		}
	}

	public static Result spawn(ServerLevel level, BlockPos at, int households, @Nullable String wanted) {
		String dimension = level.dimension().location().toString();
		Town near = Towns.nearest(dimension, at);
		String warning = near != null && near.center.closerThan(at, Towns.MIN_SPACING) ? " (" + near.name + " is closer than "
			+ Towns.MIN_SPACING + " blocks; allowed, as this is for testing)" : "";
		int cx = at.getX();
		int cz = at.getZ();
		for (int x = cx - REACH - 16; x <= cx + REACH + 16; x += 16) {
			for (int z = cz - REACH - 16; z <= cz + REACH + 16; z += 16) {
				level.getChunk(x >> 4, z >> 4);
			}
		}

		// (For testing: all of it at once.)
		Job job = new Job(level, cx, cz, households, wanted, false);
		while (job.step()) {
		}

		Result result = job.result();
		return new Result(result.town(), result.message() + warning);
	}

	/** How far round its middle a town's ground (and what's worked out round it) may reach: its chunks loaded before it's made. */
	static final int MOST_REACH = REACH + 40;

	/**
	 * A town made a step at a time (milestone 23, docs/LIVED_WORLD.md A2: "out of sight, never a stall"): laid out first,
	 * then the plaza, the store, the fields, each house, each road, the plaza's things, the trees it cut, each household, the
	 * lights a piece at a time, each step a few to some tens of milliseconds. The land it's on loaded first. {@code
	 * world}: made by world generation (where a village would be) rather than for testing.
	 */
	static final class Job {
		final ServerLevel level;
		final MinecraftServer server;
		final RandomSource random;
		final String dimension;
		final String name;
		final int cx;
		final int cz;
		final int households;
		final boolean world;
		private final java.util.ArrayDeque<Step> steps = new java.util.ArrayDeque<>();
		private long longest;
		private String longestStep = "";
		/** Steps that took over a tick's worth (50 ms), by what they were. */
		private final java.util.Map<String, Integer> slow = new java.util.TreeMap<>();
		private final long startedAt = System.nanoTime();
		private long worked;
		@Nullable
		private Result result;

		// The layout, and what's been made so far.
		private int y0;
		private List<DrifterApi.HousePlanInfo> plans;
		private Rect plaza;
		private final List<Road> roads = new ArrayList<>();
		private int storeZ;
		private int storeBase;
		private Rect storeRect;
		private List<Rect> fieldRects;
		private Rect penRect;
		private final List<Rect> taken = new ArrayList<>();
		private final List<Rect> roadRects = new ArrayList<>();
		private List<BlockPos> store = List.of();
		private final List<BlockPos> fields = new ArrayList<>();
		private BlockPos pen;
		private final List<Side> sides = new ArrayList<>();
		private final List<Town.Plot> plots = new ArrayList<>();
		private final List<DrifterApi.PlacedHouse> houses = new ArrayList<>();
		private final List<Rect> houseRects = new ArrayList<>();
		private int planIndex;
		private final int[] turnedDown = new int[5];
		private int round = -1;
		private boolean roundOpen;
		private BlockPos well;
		private BlockPos board;
		private final List<Rect> built = new ArrayList<>();
		private final List<Rect> all = new ArrayList<>();
		private net.minecraft.world.level.levelgen.structure.BoundingBox area;
		private net.minecraft.world.level.levelgen.structure.BoundingBox storeBox;
		private net.minecraft.world.level.levelgen.structure.BoundingBox plazaBox;
		private final List<DrifterEntity> people = new ArrayList<>();
		@Nullable
		private Town town;
		private int lamps;
		private int torches;

		Job(ServerLevel level, int cx, int cz, int households, @Nullable String wanted, boolean world) {
			this.level = level;
			this.server = level.getServer();
			this.random = level.getRandom();
			this.dimension = level.dimension().location().toString();
			this.name = wanted != null ? wanted : Towns.newName(this.random);
			this.cx = cx;
			this.cz = cz;
			this.households = households;
			this.world = world;
			this.steps.add(new Step("layout", this::layout));
		}

		/** One step done; false when it's all made (or there's nothing to make it with). */
		boolean step() {
			Step next = this.steps.poll();
			if (next == null) {
				return false;
			}

			long start = System.nanoTime();
			// (Its own entries only: its people's comings and goings aren't news while it's being made.)
			Chronicle.quiet = true;
			try {
				next.work().run();
			} finally {
				Chronicle.quiet = false;
				long took = System.nanoTime() - start;
				this.worked += took;
				if (took > 50_000_000L) {
					this.slow.merge(next.what(), 1, Integer::sum);
				}

				if (took > this.longest) {
					this.longest = took;
					this.longestStep = next.what();
				}
			}

			return this.result == null || !this.steps.isEmpty();
		}

		boolean done() {
			return this.result != null && this.steps.isEmpty();
		}

		Result result() {
			return this.result != null ? this.result : new Result(null, this.name + ": not made yet");
		}

		private void then(String what, Runnable step) {
			this.steps.addLast(new Step(what, step));
		}

		private record Step(String what, Runnable work) {
		}

		// -----------------------------------------------------------------------------------------
		// 1. The layout, as a village grows: the plaza in the middle, four roads out from it and side roads off those, the
		// store by the north road, the fields and pen by the south one; houses along the roads come later, one at a time.
		// -----------------------------------------------------------------------------------------

		private void layout() {
			GROUND.clear();
			this.plans = new ArrayList<>(DrifterApi.housePlans(this.server).stream()
				.filter(plan -> Math.max(plan.sizeX(), plan.sizeZ()) >= SMALLEST_HOUSE && Math.max(plan.sizeX(), plan.sizeZ()) <= BIGGEST_HOUSE
					&& plan.sizeY() <= 24).toList());
			if (this.plans.isEmpty()) {
				this.result = new Result(null, "No house plans to build with (see /drifter houses)");
				return;
			}

			int cx = this.cx;
			int cz = this.cz;
			this.plaza = new Rect(cx - PLAZA, cz - PLAZA, cx + PLAZA, cz + PLAZA);
			for (Direction way : Direction.Plane.HORIZONTAL) {
				Road arm = new Road(cx + way.getStepX() * (PLAZA + 1), cz + way.getStepZ() * (PLAZA + 1), way, ARM);
				this.roads.add(new Road(arm.x(), arm.z(), way, Math.max(1, dry(this.level, arm))));
			}

			// The store's spot by the north road: where the ground suits it (found in testing: on a hillside its door opened onto
			// a bank of earth), with a flat apron to the road.
			this.storeZ = cz - 28;
			Integer storeBase = null;
			for (int z = cz - 28; z <= cz - PLAZA - 11 && storeBase == null; z++) {
				storeBase = siteBase(this.level, cx + 4, z, cx + 12, z + 8);
				this.storeZ = storeBase != null ? z : this.storeZ;
			}

			this.storeBase = storeBase != null ? storeBase : median(this.level, cx + 4, this.storeZ, cx + 12, this.storeZ + 8);
			this.storeRect = new Rect(cx + 2, this.storeZ - 1, cx + 13, this.storeZ + 9);
			this.fieldRects = List.of(new Rect(cx + 3, cz + 12, cx + 11, cz + 20), new Rect(cx + 3, cz + 23, cx + 11, cz + 31));
			this.penRect = new Rect(cx - 13, cz + 12, cx - 3, cz + 22);
			this.taken.addAll(List.of(this.plaza, this.storeRect, this.penRect));
			this.taken.addAll(this.fieldRects);
			// Side roads off the main ones, out into the land (a village's lanes).
			branches(this.level, this.roads, this.taken, this.random, 2 + this.households / 4, cx, cz);
			this.roads.forEach(road -> this.roadRects.add(road.rect()));
			this.y0 = median(this.level, cx - PLAZA, cz - PLAZA, cx + PLAZA, cz + PLAZA) + 1;
			this.then("plaza", this::plaza);
			this.then("store", this::store);
			for (Rect rect : this.fieldRects) {
				this.then("field", () -> this.field(rect));
			}

			this.then("pen", this::pen);
			this.then("houses", this::houses);
		}

		/** The plaza at its ground's middle height, a stone edge where the land falls away and the land round it eased up to it
		 * (not dug into a hill as a pit: user, 2026-10-23). */
		private void plaza() {
			foundation(this.level, this.plaza, this.y0 - 1, CLEAR_ABOVE, List.of(), this.random, WIDE_BLEND, true);
		}

		/** The store, at the height of its own ground, eased in the same way. */
		private void store() {
			foundation(this.level, this.storeRect, this.storeBase, 8, List.of(this.plaza), this.random, WIDE_BLEND, true);
			this.store = TownBuilder.store(this.level, new BlockPos(this.cx + 4, this.storeBase + 1, this.storeZ), this.cx);
		}

		/** One of the shared farm's two fields (east of the south road). */
		private void field(Rect rect) {
			int fieldBase = median(this.level, rect.x0(), rect.z0(), rect.x1(), rect.z1());
			// (Eased into the land round it: dug into a slope as a pit, its people climbed out on blocks they put down on the
			// farmland; found by the user.)
			List<Rect> keep = new ArrayList<>(List.of(this.plaza, this.storeRect));
			this.fieldRects.stream().filter(other -> other != rect).forEach(keep::add);
			foundation(this.level, rect, fieldBase, 4, keep, this.random, WIDE_BLEND, false);
			BlockPos field = new BlockPos((rect.x0() + rect.x1()) / 2, fieldBase, (rect.z0() + rect.z1()) / 2);
			this.fields.add(field.above());
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					BlockPos pos = field.offset(dx, 0, dz);
					if (dx == 0 && dz == 0) {
						set(this.level, pos, Blocks.WATER.defaultBlockState());
						continue;
					}

					set(this.level, pos, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE));
					set(this.level, pos.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, this.random.nextInt(8)));
				}
			}
		}

		/** The pen (west of the south road) and its animals. */
		private void pen() {
			this.pen = onGround(this.level, (this.penRect.x0() + this.penRect.x1()) / 2, (this.penRect.z0() + this.penRect.z1()) / 2);
			penFence(this.level, this.pen, 5);
			for (EntityType<?> animal : List.of(EntityType.COW, EntityType.COW, EntityType.SHEEP, EntityType.SHEEP, EntityType.CHICKEN, EntityType.CHICKEN)) {
				animal.spawn(this.level, onGround(this.level, this.pen.getX() + this.random.nextInt(7) - 3, this.pen.getZ() + this.random.nextInt(7) - 3),
					MobSpawnType.COMMAND);
			}
		}

		// -----------------------------------------------------------------------------------------
		// 2. The houses: each from a different plan (as far as they go), its front to the road, along the roads with room
		// between them, the nearest free spot to the plaza first; each on its own ground, eased into the land round it. A few
		// spots tried a step (a house put up ends it); where the land is broken, by water or slopes too steep, more side roads
		// till the houses fit (four rounds at most).
		// -----------------------------------------------------------------------------------------

		/** A step's spots tried for this long at most (a house put up ends it sooner). */
		private static final long TRYING_NANOS = 8_000_000L;

		private void houses() {
			long start = System.nanoTime();
			while (System.nanoTime() - start < TRYING_NANOS) {
				if (!this.roundOpen) {
					this.round++;
					if (this.round >= 4 || this.houses.size() >= this.households) {
						this.housesDone();
						return;
					}

					if (this.round > 0) {
						List<Rect> busy = new ArrayList<>(this.taken);
						busy.addAll(this.houseRects);
						int had = this.roads.size();
						branches(this.level, this.roads, busy, this.random, this.roads.size() - 4 + 2, this.cx, this.cz);
						this.roads.subList(had, this.roads.size()).forEach(road -> this.roadRects.add(road.rect()));
					}

					for (int i = this.sides.size() / 2; i < this.roads.size(); i++) {
						Road road = this.roads.get(i);
						int startAt = i < 4 ? 2 : 4;
						this.sides.add(new Side(road, road.way().getClockWise(), startAt));
						this.sides.add(new Side(road, road.way().getCounterClockWise(), startAt));
					}

					this.roundOpen = true;
				}

				if (this.houses.size() >= this.households) {
					this.housesDone();
					return;
				}

				Side side = this.sides.stream().filter(s -> !s.full).min(java.util.Comparator.comparingDouble(s -> {
					BlockPos at = s.road.at(s.cursor);
					return Math.hypot(at.getX() - this.cx, at.getZ() - this.cz);
				})).orElse(null);
				if (side == null) {
					this.roundOpen = false;
					continue;
				}

				if (this.trySpot(side)) {
					break;
				}
			}

			this.steps.addFirst(new Step("houses", this::houses));
		}

		/** A house at this side's next spot if one fits there (true: one went up); the side moved on either way. */
		private boolean trySpot(Side side) {
			DrifterApi.PlacedHouse house = null;
			Direction front = side.out.getOpposite();
			int setback = LEAST_SETBACK + this.random.nextInt(MOST_SETBACK - LEAST_SETBACK + 1);
			for (int tries = 0; tries < this.plans.size() && house == null; tries++) {
				DrifterApi.HousePlanInfo plan = this.plans.get((this.planIndex + tries) % this.plans.size());
				Rotation rotation = turnFor(this.server, plan.id(), front);
				if (rotation == null) {
					continue;
				}

				boolean turned = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
				int sx = turned ? plan.sizeZ() : plan.sizeX();
				int sz = turned ? plan.sizeX() : plan.sizeZ();
				boolean alongX = side.road.way().getAxis() == Direction.Axis.X;
				int along = alongX ? sx : sz;
				int depth = alongX ? sz : sx;
				if (side.cursor + along > side.road.length()) {
					continue;
				}

				int near = 2 + setback;
				Rect site = Rect.of(side.road.at(side.cursor).relative(side.out, near), side.road.at(side.cursor + along - 1).relative(side.out, near + depth - 1));
				int why = Math.abs(site.x0() - this.cx) > REACH || Math.abs(site.x1() - this.cx) > REACH || Math.abs(site.z0() - this.cz) > REACH
					|| Math.abs(site.z1() - this.cz) > REACH ? 0 : this.roadRects.stream().anyMatch(road -> road.near(site, LEAST_SETBACK - 1)) ? 1
					: this.taken.stream().anyMatch(other -> other.near(site, HOUSE_GAP)) ? 2
					: this.houseRects.stream().anyMatch(other -> other.near(site, HOUSE_GAP)) ? 3 : -1;
				Integer base = why >= 0 ? null : siteBase(this.level, site.x0(), site.z0(), site.x1(), site.z1());
				if (base == null) {
					this.turnedDown[why >= 0 ? why : 4]++;
					continue;
				}

				// Its own ground, eased into the land round it (a plan whose lowest layer is its own ground goes a block down, its
				// ground in place of the land's).
				List<Rect> keep = new ArrayList<>(this.taken);
				keep.addAll(this.houseRects);
				keep.addAll(this.roadRects);
				foundation(this.level, site, base, plan.sizeY() + 2, keep, this.random, BLEND, true);
				BlockPos origin = new BlockPos(site.x0(), plan.groundLayer() ? base : base + 1, site.z0());
				house = DrifterApi.placeHouse(this.level, plan.id(), origin, rotation, WOODS[this.random.nextInt(WOODS.length)], front);
				if (house != null) {
					changed(house.box());
					this.planIndex = (this.planIndex + tries + 1) % this.plans.size();
					side.cursor += along + HOUSE_GAP + this.random.nextInt(MORE_GAP + 1);
					Town.Plot made = new Town.Plot(origin, sx, sz, plan.id(), rotation, front, null);
					made.built = true;
					this.plots.add(made);
					this.houses.add(house);
					this.houseRects.add(site);
					DrifterCivilizations.LOGGER.info("[town] {} at {}, its door at {}", plan.id(), house.box(), house.door());
				}
			}

			if (house == null) {
				side.cursor += SKIP;
			}

			side.full = side.cursor >= side.road.length() - 4;
			return house != null;
		}

		private void housesDone() {
			DrifterCivilizations.LOGGER.info("[town] {} of {} houses on {} roads; spots turned down: {} out of the town, {} by a road, {} by the store or fields, {} by a house, {} for the ground",
				this.houses.size(), this.households, this.roads.size(), this.turnedDown[0], this.turnedDown[1], this.turnedDown[2], this.turnedDown[3],
				this.turnedDown[4]);
			for (Road road : this.roads) {
				this.then("road", () -> this.road(road));
			}

			this.then("plazaThings", this::plazaThings);
			this.then("roadLamps", this::roadLamps);
			this.then("stallAndPaths", this::stallAndPaths);
			this.then("areaAndTrees", this::areaAndTrees);
		}

		// -----------------------------------------------------------------------------------------
		// 3. The roads over the ground as it is now, the plaza paved, the well, bell and board's spot, lamps along the roads,
		// the stall and the paths to the doors.
		// -----------------------------------------------------------------------------------------

		private void road(Road road) {
			easeRoad(this.level, road, this.plaza);
			Rect rect = road.rect();
			for (int x = rect.x0(); x <= rect.x1(); x++) {
				for (int z = rect.z0(); z <= rect.z1(); z++) {
					if (!this.plaza.contains(x, z)) {
						TownBuilder.road(this.level, x, z);
					}
				}
			}
		}

		private void plazaThings() {
			int cx = this.cx;
			int cz = this.cz;
			int y0 = this.y0;
			for (int x = cx - PLAZA; x <= cx + PLAZA; x++) {
				for (int z = cz - PLAZA; z <= cz + PLAZA; z++) {
					set(this.level, new BlockPos(x, y0 - 1, z), (this.random.nextFloat() < 0.3F ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS).defaultBlockState());
				}
			}

			this.well = new BlockPos(cx, y0, cz);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos pos = this.well.offset(dx, 0, dz);
					if (dx == 0 && dz == 0) {
						set(this.level, pos.below(), Blocks.WATER.defaultBlockState());
						set(this.level, pos.below(2), Blocks.WATER.defaultBlockState());
					} else {
						set(this.level, pos.below(), Blocks.COBBLESTONE.defaultBlockState());
						place(this.level, pos, (Math.abs(dx) == 1 && Math.abs(dz) == 1 ? Blocks.OAK_FENCE : Blocks.COBBLESTONE_WALL).defaultBlockState());
					}
				}
			}

			for (int dx = -1; dx <= 1; dx += 2) {
				for (int dz = -1; dz <= 1; dz += 2) {
					place(this.level, this.well.offset(dx, 1, dz), Blocks.OAK_FENCE.defaultBlockState());
				}
			}

			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					set(this.level, this.well.offset(dx, 2, dz), Blocks.OAK_SLAB.defaultBlockState());
				}
			}

			place(this.level, new BlockPos(cx + 5, y0, cz - 5), Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, Direction.SOUTH));
			this.board = new BlockPos(cx - 5, y0, cz - 5);
			for (int dx : new int[] {-PLAZA, PLAZA}) {
				for (int dz : new int[] {-PLAZA, PLAZA}) {
					lamp(this.level, new BlockPos(cx + dx, y0, cz + dz));
				}
			}
		}

		private void roadLamps() {
			this.built.addAll(this.taken);
			this.built.addAll(this.houseRects);
			for (Road road : this.roads) {
				for (int t = 8; t < road.length(); t += 12) {
					Direction out = (t / 12) % 2 == 0 ? road.way().getClockWise() : road.way().getCounterClockWise();
					BlockPos at = road.at(t).relative(out, 2);
					if (this.built.stream().noneMatch(rect -> rect.away(at.getX(), at.getZ()) <= 1)
						&& this.roadRects.stream().noneMatch(rect -> rect.contains(at.getX(), at.getZ()))) {
						lamp(this.level, onGround(this.level, at.getX(), at.getZ()));
					}
				}
			}
		}

		/** A market stall on the plaza, its counter to the well (north): kept by the most trader-like of them (step 4); paths
		 * from each door to its road. */
		private void stallAndPaths() {
			DrifterApi.placeHouse(this.level, DrifterApi.stallPlan(), this.stallAt(), Rotation.NONE, "oak", Direction.SOUTH);
			GROUND.clear();
			for (int i = 0; i < this.houses.size(); i++) {
				path(this.level, this.houses.get(i), this.plots.get(i).front, this.roadRects);
			}
		}

		private BlockPos stallAt() {
			return new BlockPos(this.cx + 2, this.y0, this.cz + 2);
		}

		// -----------------------------------------------------------------------------------------
		// 4. The town's area (everything in it and a little round), the trees it cut through tidied a piece at a time (no
		// trunks or leaves left hanging: user, 2026-10-22).
		// -----------------------------------------------------------------------------------------

		/** The area's pieces, this wide each way, for the steps that go over all of it. */
		private static final int PIECE = 32;

		private void areaAndTrees() {
			int minX = this.cx - PLAZA;
			int maxX = this.cx + PLAZA;
			int minZ = this.cz - PLAZA;
			int maxZ = this.cz + PLAZA;
			this.all.addAll(this.built);
			this.all.addAll(this.roadRects);
			for (Rect rect : this.all) {
				minX = Math.min(minX, rect.x0());
				maxX = Math.max(maxX, rect.x1());
				minZ = Math.min(minZ, rect.z0());
				maxZ = Math.max(maxZ, rect.z1());
			}

			int minY = this.y0;
			int maxY = this.y0;
			for (DrifterApi.PlacedHouse house : this.houses) {
				minY = Math.min(minY, house.box().minY());
				maxY = Math.max(maxY, house.box().maxY());
			}

			this.area = new net.minecraft.world.level.levelgen.structure.BoundingBox(minX - 6, minY - 12, minZ - 6, maxX + 6, maxY + 16, maxZ + 6);
			for (var piece : this.pieces()) {
				this.then("trees", () -> {
					DrifterApi.tidyTrees(this.level, piece);
					GROUND.clear();
				});
			}

			this.then("peopleStart", this::peopleStart);
		}

		private List<net.minecraft.world.level.levelgen.structure.BoundingBox> pieces() {
			List<net.minecraft.world.level.levelgen.structure.BoundingBox> list = new ArrayList<>();
			for (int x = this.area.minX(); x <= this.area.maxX(); x += PIECE) {
				for (int z = this.area.minZ(); z <= this.area.maxZ(); z += PIECE) {
					list.add(new net.minecraft.world.level.levelgen.structure.BoundingBox(x, this.area.minY(), z, Math.min(x + PIECE - 1, this.area.maxX()),
						this.area.maxY(), Math.min(z + PIECE - 1, this.area.maxZ())));
				}
			}

			return list;
		}

		// -----------------------------------------------------------------------------------------
		// 5. Its people: one moved into each house (furnished as a player would, the user's guidebook), a household a step;
		// friends with the rest, one group; well on in their lives, with the gear for it (user, 2026-10-23: "all of the players
		// should be like mid-late game, above tier 2, that way drifters can defend themselves").
		// -----------------------------------------------------------------------------------------

		private void peopleStart() {
			this.storeBox = new net.minecraft.world.level.levelgen.structure.BoundingBox(this.storeRect.x0(), this.storeBase - 2, this.storeRect.z0(),
				this.storeRect.x1(), this.storeBase + 8, this.storeRect.z1());
			this.plazaBox = new net.minecraft.world.level.levelgen.structure.BoundingBox(this.plaza.x0() - 2, this.y0 - 3, this.plaza.z0() - 2,
				this.plaza.x1() + 2, this.y0 + 5, this.plaza.z1() + 2);
			for (int i = 0; i < this.houses.size(); i++) {
				int index = i;
				this.then("household", () -> this.household(index));
			}

			this.then("townUp", this::townUp);
		}

		private void household(int i) {
			DrifterApi.PlacedHouse house = this.houses.get(i);
			Town.Plot plot = this.plots.get(i);
			BlockPos stand = doorstep(this.level, house, plot);
			DrifterEntity drifter = DrifterApi.spawn(this.level, stand, null);
			if (drifter == null) {
				return;
			}

			// (No Bandits in a town: the user, 2026-10-28, "kinda like a skyrim bandit".)
			DrifterApi.notABandit(drifter);
			Home home = DrifterApi.settleIn(drifter, house, this.store);
			home.farm = this.fields.get(i % this.fields.size());
			home.farmRadius = 4;
			home.pen = this.pen;
			plot.owner = drifter.getUUID();
			stock(this.level, home, house.box());
			for (BlockPos chest : home.chests()) {
				DrifterApi.rememberChest(drifter, chest);
			}

			DrifterApi.knowsBox(drifter, this.storeBox);
			DrifterApi.knowsBox(drifter, this.plazaBox);
			this.people.add(drifter);
			// A house with more than one room takes more households: someone in each other room (user, 2026-10-23: "that way
			// there is more citizens without having more houses").
			plot.room = DrifterApi.households(this.level, house.planId(), house.origin(), house.rotation());
			for (int more = 1; more < plot.room; more++) {
				DrifterEntity other = DrifterApi.spawn(this.level, stand, null);
				if (other == null) {
					break;
				}

				DrifterApi.notABandit(other);
				Home shared = DrifterApi.settleInToo(other, house, this.store);
				if (shared == null) {
					other.discard();
					break;
				}

				shared.farm = home.farm;
				shared.farmRadius = 4;
				shared.pen = this.pen;
				plot.residents.add(other.getUUID());
				for (BlockPos chest : shared.chests()) {
					DrifterApi.rememberChest(other, chest);
				}

				DrifterApi.knowsBox(other, this.storeBox);
				DrifterApi.knowsBox(other, this.plazaBox);
				this.people.add(other);
			}
		}

		/** One group, geared by their places; the town itself, its past and its keeper; its notice board. */
		private void townUp() {
			var group = DrifterApi.formGroup(this.people, this.name);
			// Gear by their place in the town (the user, 2026-10-28: everyone in diamond, "i cant tell a guard from a citizen, or
			// a citizen from a leader"): its leader (the group's first), its two boldest as guards, the rest a working life's.
			List<DrifterEntity> boldest = new ArrayList<>(this.people.subList(Math.min(1, this.people.size()), this.people.size()));
			boldest.sort(java.util.Comparator.comparingDouble((DrifterEntity one) -> DrifterApi.trait(one, "bravery") + DrifterApi.trait(one, "aggression"))
				.reversed());
			for (DrifterEntity one : this.people) {
				String role = group != null && one.getUUID().equals(group.leader) ? "leader" : boldest.indexOf(one) >= 0 && boldest.indexOf(one) < 2 ? "guard"
					: "citizen";
				DrifterApi.equipTown(one, this.random, role);
			}

			long day = Chronicle.day(this.server);
			Town town = new Town(UUID.randomUUID(), this.name, group == null ? null : group.id, this.dimension, new BlockPos(this.cx, this.y0, this.cz), day, true);
			this.town = town;
			town.plots.addAll(this.plots);
			town.store.addAll(this.store);
			town.fields.addAll(this.fields);
			town.well = this.well;
			town.pen = this.pen;
			town.stallPlot = new Town.Plot(this.stallAt(), 5, 4, DrifterApi.stallPlan(), Rotation.NONE, Direction.NORTH, null);
			town.stallBuilt = true;
			Towns.add(this.server, town);
			// A past: its people all know each other, some better than others, and a few know people of the towns about.
			TownHistory.neighbours(this.server, town, this.people, this.random);
			TownHistory.acrossTowns(this.server, town, this.people, this.random);
			if (group != null) {
				Market.keeper(this.server, town, group);
				// (Made ready: they stocked the store themselves, and the keeper has a little money to buy with.)
				for (DrifterEntity one : this.people) {
					this.store.forEach(chest -> DrifterApi.rememberChest(one, chest));
					if (one.getUUID().equals(town.keeper)) {
						one.getInventory().add(new ItemStack(Items.EMERALD, 8));
						DrifterApi.wearApron(one);
					}
				}
			}

			noticeBoard(this.level, this.board, town, this.people);
			// Lit all over, inside the houses and out, so nothing spawns in the town at night (user, 2026-10-23: "the town isnt
			// fully lit up alot, causing a bunch of mobs to spawn and killing half the town"): a piece at a time.
			for (var piece : this.pieces()) {
				this.then("light", () -> this.light(piece));
			}

			this.then("walk", this::walk);
			this.then("finish", this::finish);
		}

		private void light(net.minecraft.world.level.levelgen.structure.BoundingBox piece) {
			List<Rect> noLamp = new ArrayList<>(this.roadRects);
			noLamp.addAll(this.fieldRects);
			noLamp.add(this.plaza);
			int[] made = TownLights.light(this.level, piece, this.houses.stream().map(DrifterApi.PlacedHouse::box).toList(),
				(x, z) -> this.all.stream().anyMatch(rect -> rect.away(x, z) <= TOWN_GROUND), (x, z) -> noLamp.stream().anyMatch(rect -> rect.contains(x, z)));
			this.lamps += made[0];
			this.torches += made[1];
		}

		/** Its places walked to from the plaza (A11): which can't be, logged (none, it's walkable). */
		private final List<String> unwalkable = new ArrayList<>();

		private void walk() {
			java.util.Map<String, List<BlockPos>> places = new java.util.LinkedHashMap<>();
			for (int i = 0; i < this.houses.size(); i++) {
				var house = this.houses.get(i);
				if (house.door() != null) {
					places.put("house " + (i + 1) + " (" + house.planId() + ")", List.of(house.door().relative(this.plots.get(i).front)));
				}
			}

			places.put("the store", List.copyOf(this.store));
			BlockPos stall = this.stallAt().offset(2, 0, 2);
			places.put("the stall", List.of(stall.offset(0, 0, 3), stall.offset(0, 0, -3), stall.offset(4, 0, 0), stall.offset(-4, 0, 0)));
			for (int i = 0; i < this.fields.size(); i++) {
				BlockPos field = this.fields.get(i);
				places.put("field " + (i + 1), List.of(field.offset(5, 0, 0), field.offset(-5, 0, 0), field.offset(0, 0, 5), field.offset(0, 0, -5)));
			}

			this.unwalkable.addAll(TownWalk.unreachable(this.level, this.area.inflatedBy(4), new BlockPos(this.cx + 3, this.y0, this.cz), places));
			DrifterCivilizations.LOGGER.info("[town] {}: walked to {} of {} places from the plaza{}", this.name, places.size() - this.unwalkable.size(),
				places.size(), this.unwalkable.isEmpty() ? "" : "; not: " + String.join(", ", this.unwalkable));
		}

		private void finish() {
			Town town = this.town;
			Chronicle.quiet = false;
			Chronicle.add(this.server, "town", this.name + (this.world ? " stands where a village might have" : " was founded") + ", with " + this.people.size()
				+ " people in " + this.houses.size() + " houses" + (this.world ? "" : " (made ready, for testing)"));
			long seconds = Math.round((System.nanoTime() - this.startedAt) / 1e9);
			DrifterCivilizations.LOGGER.info("[town] {} made: {} people in {} houses, {} ms of work over {} s; the longest step {} ms ({}); over 50 ms: {}",
				this.name, this.people.size(), this.houses.size(), this.worked / 1_000_000, seconds, this.longest / 1_000_000, this.longestStep, this.slow);
			this.result = new Result(town, this.name + ": " + this.people.size() + " people in " + this.houses.size() + " houses, the plaza at " + this.cx + ", "
				+ this.y0 + ", " + this.cz + " (" + this.lamps + " lamp posts and " + this.torches + " torches for dark corners)"
				+ (this.unwalkable.isEmpty() ? "" : "; can't walk to " + String.join(", ", this.unwalkable)));
		}
	}

	/**
	 * Up to {@code want} side roads in all (beyond the four main ones) off the main roads, out into the land, clear of
	 * {@code busy} and of each other.
	 */
	private static void branches(ServerLevel level, List<Road> roads, List<Rect> busy, RandomSource random, int want, int cx, int cz) {
		for (int tries = 0; tries < 32 && roads.size() < 4 + want; tries++) {
			Road main = roads.get(random.nextInt(4));
			int t = 16 + random.nextInt(ARM - 24);
			Direction way = random.nextBoolean() ? main.way().getClockWise() : main.way().getCounterClockWise();
			BlockPos from = main.at(t).relative(way, 2);
			Road planned = new Road(from.getX(), from.getZ(), way, SHORTEST_BRANCH + random.nextInt(LONGEST_BRANCH - SHORTEST_BRANCH + 1));
			if (t >= main.length() || dry(level, planned) < SHORTEST_BRANCH / 2) {
				continue;
			}

			Road branch = new Road(planned.x(), planned.z(), way, dry(level, planned));
			Rect rect = branch.rect();
			boolean clear = Math.abs(rect.x0() - cx) <= REACH && Math.abs(rect.x1() - cx) <= REACH && Math.abs(rect.z0() - cz) <= REACH
				&& Math.abs(rect.z1() - cz) <= REACH && busy.stream().noneMatch(other -> other.near(rect, 8))
				&& roads.stream().skip(4).noneMatch(other -> other.rect().near(rect, 20));
			if (clear) {
				roads.add(branch);
			}
		}
	}

	/** Water a road bridges (planks over it); more and the road ends at the shore (found in testing: roads out over the sea). */
	private static final int LONGEST_BRIDGE = 5;

	/** How far a road goes before water too wide to bridge: its whole length if there's none. */
	private static int dry(ServerLevel level, Road road) {
		int wet = 0;
		for (int t = 0; t < road.length(); t++) {
			BlockPos at = road.at(t);
			if (wet(level, at.getX(), at.getZ())) {
				if (++wet > LONGEST_BRIDGE) {
					return t - wet + 1;
				}
			} else {
				wet = 0;
			}
		}

		return road.length() - wet;
	}

	/** What a household well on in its life keeps: building stuff and iron in storage, coal for the furnace, food by the bed. */
	private static void stock(ServerLevel level, Home home, net.minecraft.world.level.levelgen.structure.BoundingBox box) {
		List<List<ItemStack>> kinds = List.of(
			List.of(new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.DIRT, 32)),
			List.of(new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.STICK, 32)),
			List.of(new ItemStack(Items.IRON_INGOT, 18), new ItemStack(Items.RAW_IRON, 12), new ItemStack(Items.GOLD_INGOT, 4), new ItemStack(Items.REDSTONE, 12)),
			List.of(new ItemStack(Items.COAL, 48), new ItemStack(Items.CHARCOAL, 16), new ItemStack(Items.TORCH, 32)),
			List.of(new ItemStack(Items.BREAD, 48), new ItemStack(Items.COOKED_BEEF, 24), new ItemStack(Items.WHEAT_SEEDS, 24)));
		int kind = 0;
		for (BlockPos chest : home.chests()) {
			if (!box.isInside(chest) || !(level.getBlockEntity(chest) instanceof ChestBlockEntity entity) || kind >= kinds.size()) {
				continue;
			}

			List<ItemStack> stacks = kinds.get(kind++);
			for (int slot = 0; slot < stacks.size(); slot++) {
				entity.setItem(slot, stacks.get(slot).copy());
			}
		}
	}

	/**
	 * A house's ground, as a player builds on a slope (user, 2026-10-23: "the foundations of the structures need to blend in
	 * with the natural terrain a bit more"): its footprint at {@code base}, higher ground cut down and lower ground built up,
	 * the edge of the build-up a foundation wall of stone rather than a bank of dirt; and the land round it eased toward it
	 * over {@link #BLEND} blocks instead of a cut straight down or a step straight up (the first ring flat, for its doorstep).
	 * Trees in the way come down whole first. Ground in {@code keep} (roads, other houses, the store...) isn't touched.
	 */
	private static void foundation(ServerLevel level, Rect site, int base, int clear, List<Rect> keep, RandomSource random, int blend,
		boolean stoneEdge) {
		// Wide enough to ease the land in a block at a time (found by the user: the plaza cut into a hill left a cliff of
		// two- and three-block steps round it, and its people pillared up and down it; a player walks a slope of one).
		blend = Math.max(blend, Math.min(MOST_BLEND, steepest(level, site, base, blend, keep) + 1));
		Rect ring = new Rect(site.x0() - blend, site.z0() - blend, site.x1() + blend, site.z1() + blend);
		for (int x = ring.x0(); x <= ring.x1(); x++) {
			for (int z = ring.z0(); z <= ring.z1(); z++) {
				int d = site.away(x, z);
				if (d > 0 && inAny(keep, x, z)) {
					continue;
				}

				int g = ground(level, x, z);
				fellIn(level, x, z, Math.min(g, base) + 1, Math.max(g, base) + (d == 0 ? clear : 3));
			}
		}

		for (int x = ring.x0(); x <= ring.x1(); x++) {
			for (int z = ring.z0(); z <= ring.z1(); z++) {
				int d = site.away(x, z);
				if (d > 0 && inAny(keep, x, z)) {
					continue;
				}

				int g = ground(level, x, z);
				if (d > 0 && wet(level, x, z)) {
					continue;
				}

				// (A block up or down a block out, the first ring flat: a slope it walks.)
				int h = d == 0 ? base : base + Math.max(-(d - 1), Math.min(d - 1, g - base));
				boolean edge = stoneEdge && d == 0 && (x == site.x0() || x == site.x1() || z == site.z0() || z == site.z1());
				// Built up: a stone foundation wall at the house's edge, earth elsewhere, grass on top (the old top buried).
				if (h > g) {
					buried(level, new BlockPos(x, g, z));
				}

				for (int y = g + 1; y <= h; y++) {
					BlockState fill = edge ? (random.nextFloat() < 0.25F ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState()
						: y == h ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
					set(level, new BlockPos(x, y, z), fill);
				}

				// Cut down: what's above cleared (and room over the house's own ground), the new top grass where it's earth.
				for (int y = h + 1; y <= Math.max(g, h) + (d == 0 ? clear : 2); y++) {
					set(level, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
				}

				BlockPos top = new BlockPos(x, h, z);
				if (h < g && level.getBlockState(top).is(Blocks.DIRT)) {
					set(level, top, Blocks.GRASS_BLOCK.defaultBlockState());
				}

				if (d == 0 && !level.getFluidState(top).isEmpty()) {
					set(level, top, Blocks.GRASS_BLOCK.defaultBlockState());
				}
			}
		}
	}

	/** Blocks out at most the land round a site is eased over. */
	private static final int MOST_BLEND = 10;

	/** The most the ground just round a site (its first rings) stands above or below it. */
	private static int steepest(ServerLevel level, Rect site, int base, int blend, List<Rect> keep) {
		int most = 0;
		for (int x = site.x0() - blend; x <= site.x1() + blend; x++) {
			for (int z = site.z0() - blend; z <= site.z1() + blend; z++) {
				int d = site.away(x, z);
				if (d > 0 && !inAny(keep, x, z) && !wet(level, x, z)) {
					most = Math.max(most, Math.abs(ground(level, x, z) - base));
				}
			}
		}

		return most;
	}

	/**
	 * Ground with something now built on it: earth, not grass (user, 2026-10-23: "grass blocks not turning into dirt when
	 * there is a house on top of it, causing two grass blocks on top of eachother").
	 */
	private static void buried(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.DIRT_PATH)) {
			set(level, pos, Blocks.DIRT.defaultBlockState());
		}
	}

	private static boolean inAny(List<Rect> rects, int x, int z) {
		for (Rect rect : rects) {
			if (rect.contains(x, z)) {
				return true;
			}
		}

		return false;
	}

	/** Trees with a log or leaves in this column between these heights felled whole (a player clears a tree, not half). */
	private static void fellIn(ServerLevel level, int x, int z, int fromY, int toY) {
		for (int y = fromY; y <= toY; y++) {
			BlockPos pos = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.LEAVES)) {
				DrifterApi.fellTree(level, pos);
			}
		}
	}

	/**
	 * The pen's fence over the ground, joined up where the ground steps up or down (user, 2026-10-23: "for pens that go on
	 * hills, the fences dont connect when a block goes upward"): at a step the lower post goes up as high as the next one, as
	 * a player builds a fence on a slope. A gate on the east side.
	 */
	private static void penFence(ServerLevel level, BlockPos middle, int half) {
		List<BlockPos> ring = new ArrayList<>();
		for (int i = -half; i < half; i++) {
			ring.add(new BlockPos(middle.getX() + i, 0, middle.getZ() - half));
		}

		for (int i = -half; i < half; i++) {
			ring.add(new BlockPos(middle.getX() + half, 0, middle.getZ() + i));
		}

		for (int i = half; i > -half; i--) {
			ring.add(new BlockPos(middle.getX() + i, 0, middle.getZ() + half));
		}

		for (int i = half; i > -half; i--) {
			ring.add(new BlockPos(middle.getX() - half, 0, middle.getZ() + i));
		}

		int[] tops = new int[ring.size()];
		for (int i = 0; i < ring.size(); i++) {
			tops[i] = ground(level, ring.get(i).getX(), ring.get(i).getZ()) + 1;
		}

		BlockPos gate = new BlockPos(middle.getX() + half, 0, middle.getZ());
		for (int i = 0; i < ring.size(); i++) {
			BlockPos column = ring.get(i);
			int up = Math.max(tops[i], Math.max(tops[(i + 1) % ring.size()], tops[(i + ring.size() - 1) % ring.size()]));
			for (int y = tops[i]; y <= up; y++) {
				BlockPos pos = column.atY(y);
				boolean isGate = column.getX() == gate.getX() && column.getZ() == gate.getZ() && y == tops[i];
				place(level, pos, isGate ? Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST)
					: Blocks.OAK_FENCE.defaultBlockState());
			}
		}
	}

	/** The turn that puts a door of this plan on {@code front}, or null. */
	@Nullable
	private static Rotation turnFor(MinecraftServer server, String plan, Direction front) {
		for (Rotation rotation : Rotation.values()) {
			if (DrifterApi.doorSides(server, plan, rotation).contains(front)) {
				return rotation;
			}
		}

		return null;
	}

	/** How far out from a door its path goes looking for the road. */
	private static final int LONGEST_PATH = 16;

	/**
	 * A path from the door out to its road, a block up or down a step at most: the ground cut down or built up along it
	 * and room cleared overhead, so it's walked without pillaring, digging or a jump onto anything taller (milestone 23,
	 * A11; on the user's hilly Tectonic land a door could stand three blocks over its road with nothing between).
	 */
	private static void path(ServerLevel level, DrifterApi.PlacedHouse house, Direction front, List<Rect> roads) {
		if (house.door() == null) {
			return;
		}

		var box = house.box();
		BlockPos door = house.door();
		// Out of the house first (a porch is part of it): the level stepped down to from its sill is the door's floor.
		BlockPos step = door.relative(front);
		while (box.isInside(new BlockPos(step.getX(), box.minY(), step.getZ())) && step.distManhattan(door) < LONGEST_PATH) {
			step = step.relative(front);
		}

		List<BlockPos> cells = new ArrayList<>();
		boolean reached = false;
		for (int i = 0; i < LONGEST_PATH && !reached; i++) {
			BlockPos at = step;
			cells.add(at);
			reached = roads.stream().anyMatch(rect -> rect.contains(at.getX(), at.getZ()));
			step = step.relative(front);
		}

		int n = cells.size();
		int[] top = new int[n];
		for (int i = 0; i < n; i++) {
			top[i] = ground(level, cells.get(i).getX(), cells.get(i).getZ());
		}

		// The levels it's laid at: a block from the one before at most, from the door's floor to the road (fixed ends).
		int start = door.getY() - 1;
		int end = reached ? top[n - 1] : top[n - 1];
		for (int pass = 0; pass < 4; pass++) {
			int previous = start;
			for (int i = 0; i < n - (reached ? 1 : 0); i++) {
				top[i] = Math.max(previous - 1, Math.min(previous + 1, top[i]));
				previous = top[i];
			}

			if (reached) {
				int next = end;
				for (int i = n - 2; i >= 0; i--) {
					top[i] = Math.max(next - 1, Math.min(next + 1, top[i]));
					next = top[i];
				}
			}
		}

		for (int i = 0; i < n - (reached ? 1 : 0); i++) {
			BlockPos column = cells.get(i);
			int x = column.getX();
			int z = column.getZ();
			int g = ground(level, x, z);
			if (wet(level, x, z)) {
				continue;
			}

			// Built up (dirt under the path), or cut down (the bank dug away), then the path, and room over it.
			for (int y = g + 1; y < top[i]; y++) {
				set(level, new BlockPos(x, y, z), Blocks.DIRT.defaultBlockState());
			}

			for (int y = top[i] + 1; y <= g; y++) {
				set(level, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
			}

			set(level, new BlockPos(x, top[i], z), Blocks.DIRT_PATH.defaultBlockState());
			for (int y = top[i] + 1; y <= top[i] + 3; y++) {
				BlockPos above = new BlockPos(x, y, z);
				BlockState state = level.getBlockState(above);
				if (!state.isAir() && !box.isInside(above) && (state.canBeReplaced() || state.is(net.minecraft.tags.BlockTags.LEAVES)
					|| state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(Blocks.STONE) || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND))) {
					set(level, above, Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	/**
	 * Where its owner first stands: out from its door past the house's eaves, on the ground (user, 2026-10-22: two blocks
	 * out from the door was still under the roof's overhang, and they started on the roof).
	 */
	private static BlockPos doorstep(ServerLevel level, DrifterApi.PlacedHouse house, Town.Plot plot) {
		BlockPos step = house.door() != null ? house.door() : plot.origin;
		var box = house.box();
		while (box.isInside(new BlockPos(step.getX(), box.minY(), step.getZ()))) {
			step = step.relative(plot.front);
		}

		step = step.relative(plot.front);
		return onGround(level, step.getX(), step.getZ());
	}

	/** The store: 9 by 9, cobblestone walls, a plank roof, its door to the north-south road (west), chests round the walls. */
	private static List<BlockPos> store(ServerLevel level, BlockPos o, int cx) {
		int y0 = o.getY();
		for (int dx = 0; dx <= 8; dx++) {
			for (int dz = 0; dz <= 8; dz++) {
				set(level, o.offset(dx, -1, dz), Blocks.COBBLESTONE.defaultBlockState());
				set(level, o.offset(dx, 4, dz), Blocks.OAK_PLANKS.defaultBlockState());
				boolean wall = dx == 0 || dx == 8 || dz == 0 || dz == 8;
				for (int dy = 0; dy < 4; dy++) {
					boolean corner = (dx == 0 || dx == 8) && (dz == 0 || dz == 8);
					set(level, o.offset(dx, dy, dz), !wall ? Blocks.AIR.defaultBlockState()
						: (corner ? Blocks.OAK_LOG : Blocks.COBBLESTONE).defaultBlockState());
				}
			}
		}

		BlockPos door = o.offset(0, 0, 4);
		set(level, door, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(level, door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		for (int dz = 3; dz <= 5; dz++) {
			place(level, o.offset(8, 1, dz), Blocks.GLASS_PANE.defaultBlockState());
		}

		place(level, o.offset(1, 2, 2), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
		place(level, o.offset(7, 2, 6), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST));
		for (int x = door.getX() - 1; x >= cx + 2; x--) {
			BlockPos top = new BlockPos(x, ground(level, x, door.getZ()), door.getZ());
			if (level.getBlockState(top).is(Blocks.GRASS_BLOCK) || level.getBlockState(top).is(Blocks.DIRT)) {
				set(level, top, Blocks.DIRT_PATH.defaultBlockState());
			}
		}

		List<BlockPos> chests = new ArrayList<>();
		int[][] spots = {{7, 1, 2}, {7, 3, 2}, {7, 5, 2}, {7, 7, 2}, {3, 1, 3}, {5, 1, 3}, {3, 7, 1}, {5, 7, 1}};
		for (int[] spot : spots) {
			BlockPos pos = o.offset(spot[0], 0, spot[1]);
			Direction facing = spot[2] == 2 ? Direction.WEST : spot[2] == 3 ? Direction.SOUTH : Direction.NORTH;
			set(level, pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
			chests.add(pos);
		}

		ItemStack[][] stock = {
			{new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_PLANKS, 64)},
			{new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64)},
			{new ItemStack(Items.COAL, 32), new ItemStack(Items.IRON_INGOT, 8), new ItemStack(Items.TORCH, 32)},
			{new ItemStack(Items.BREAD, 32), new ItemStack(Items.WHEAT_SEEDS, 32), new ItemStack(Items.APPLE, 8)}};
		for (int i = 0; i < stock.length; i++) {
			if (level.getBlockEntity(chests.get(i)) instanceof ChestBlockEntity chest) {
				for (int slot = 0; slot < stock[i].length; slot++) {
					chest.setItem(slot, stock[i][slot]);
				}
			}
		}

		return chests;
	}

	/** A lamp post: two fence posts and a lantern, if there's room. */
	private static void lamp(ServerLevel level, BlockPos pos) {
		if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir() || !level.getBlockState(pos.above(2)).isAir()) {
			return;
		}

		place(level, pos, Blocks.OAK_FENCE.defaultBlockState());
		place(level, pos.above(), Blocks.OAK_FENCE.defaultBlockState());
		place(level, pos.above(2), Blocks.LANTERN.defaultBlockState());
	}

	/** The notice board: a lectern with the town's book (who lives there, when it was founded). */
	private static void noticeBoard(ServerLevel level, BlockPos pos, Town town, List<DrifterEntity> people) {
		StringBuilder first = new StringBuilder("The town of " + town.name + "\n\nFounded on day " + town.foundedDay + " (made ready, for testing).\n\n"
			+ people.size() + " households.");
		StringBuilder names = new StringBuilder("Its people:\n");
		people.forEach(person -> names.append(person.getName().getString()).append('\n'));
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(town.name), "the town", 0,
			List.of(Filterable.passThrough(Component.literal(first.toString())), Filterable.passThrough(Component.literal(names.toString()))), true));
		place(level, pos, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH).setValue(LecternBlock.HAS_BOOK, true));
		if (level.getBlockEntity(pos) instanceof LecternBlockEntity lectern) {
			lectern.setBook(book);
		}
	}

	/**
	 * The top of the ground at x, z: the highest block that isn't air, a plant, a log or leaves (the ground under a tree), or
	 * the top of water there.
	 */
	private static int ground(ServerLevel level, int x, int z) {
		long key = BlockPos.asLong(x, 0, z);
		Integer known = GROUND.get(key);
		if (known != null) {
			return known;
		}

		int top = groundNow(level, x, z);
		GROUND.put(key, top);
		return top;
	}

	/**
	 * Each column's ground while a town is laid out (its spots tried by the dozen; found measuring a town), forgotten for a
	 * column as soon as a block in it changes.
	 */
	private static final java.util.Map<Long, Integer> GROUND = new java.util.HashMap<>();

	private static void changed(int x, int z) {
		GROUND.remove(BlockPos.asLong(x, 0, z));
	}

	private static void changed(net.minecraft.world.level.levelgen.structure.BoundingBox box) {
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				changed(x, z);
			}
		}
	}

	private static int groundNow(ServerLevel level, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
		while (pos.getY() > level.getMinBuildHeight()) {
			BlockState state = level.getBlockState(pos);
			if (!state.getFluidState().isEmpty() || !(state.isAir() || state.canBeReplaced() || state.is(net.minecraft.tags.BlockTags.LOGS)
				|| state.is(net.minecraft.tags.BlockTags.LEAVES))) {
				break;
			}

			pos.move(0, -1, 0);
		}

		return pos.getY();
	}

	private static boolean wet(ServerLevel level, int x, int z) {
		return !level.getFluidState(new BlockPos(x, ground(level, x, z), z)).isEmpty();
	}

	/** The block over the ground at x, z (where something stands on it). */
	private static BlockPos onGround(ServerLevel level, int x, int z) {
		return new BlockPos(x, ground(level, x, z) + 1, z);
	}

	/** The ground's middle height over this area. */
	private static int median(ServerLevel level, int x0, int z0, int x1, int z1) {
		List<Integer> heights = new ArrayList<>();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				heights.add(ground(level, x, z));
			}
		}

		heights.sort(Integer::compare);
		return heights.get(heights.size() / 2);
	}

	/** The ground height a house over this area sits at, or null if it doesn't suit one (water, or too steep). */
	@Nullable
	private static Integer siteBase(ServerLevel level, int x0, int z0, int x1, int z1) {
		List<Integer> heights = new ArrayList<>();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (wet(level, x, z)) {
					return null;
				}

				heights.add(ground(level, x, z));
			}
		}

		heights.sort(Integer::compare);
		return heights.get(heights.size() - 1) - heights.get(0) > MOST_STEP ? null : heights.get(heights.size() / 2);
	}

	/**
	 * A road built up where the land jumps, so it climbs and drops a block at a time, as a player makes a way up a bank
	 * (found by the user: Drifters pillaring up from the plaza to the store, its road straight up a cliff of three-block
	 * steps). Only filled, never dug; not over water or in the plaza.
	 */
	private static void easeRoad(ServerLevel level, Road road, Rect plaza) {
		int n = road.length();
		int[] want = new int[n];
		Direction across = road.way().getClockWise();
		for (int t = 0; t < n; t++) {
			BlockPos mid = road.at(t);
			int high = Integer.MIN_VALUE;
			for (int side = -1; side <= 1; side++) {
				BlockPos cell = mid.relative(across, side);
				high = Math.max(high, ground(level, cell.getX(), cell.getZ()));
			}

			want[t] = high;
		}

		for (int t = 1; t < n; t++) {
			want[t] = Math.max(want[t], want[t - 1] - 1);
		}

		for (int t = n - 2; t >= 0; t--) {
			want[t] = Math.max(want[t], want[t + 1] - 1);
		}

		for (int t = 0; t < n; t++) {
			BlockPos mid = road.at(t);
			for (int side = -1; side <= 1; side++) {
				BlockPos cell = mid.relative(across, side);
				if (plaza.contains(cell.getX(), cell.getZ()) || wet(level, cell.getX(), cell.getZ())) {
					continue;
				}

				int g = ground(level, cell.getX(), cell.getZ());
				if (g < want[t]) {
					buried(level, new BlockPos(cell.getX(), g, cell.getZ()));
					for (int y = g + 1; y <= want[t]; y++) {
						set(level, new BlockPos(cell.getX(), y, cell.getZ()), (y == want[t] ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState());
					}
				}
			}
		}
	}

	/** One block of road: dirt path on the ground (planks over water), what grows on it cleared. */
	private static void road(ServerLevel level, int x, int z) {
		int top = ground(level, x, z);
		BlockPos pos = new BlockPos(x, top, z);
		if (!level.getFluidState(pos).isEmpty()) {
			set(level, pos, Blocks.OAK_PLANKS.defaultBlockState());
		} else {
			BlockState state = level.getBlockState(pos);
			set(level, pos, state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.COARSE_DIRT)
				|| state.is(Blocks.MYCELIUM) ? Blocks.DIRT_PATH.defaultBlockState() : state);
		}

		fellIn(level, x, z, top + 1, top + 4);
		for (int y = top + 1; y <= top + 4; y++) {
			BlockPos above = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(above);
			if (!state.isAir() && (state.canBeReplaced() || state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.LEAVES))) {
				set(level, above, Blocks.AIR.defaultBlockState());
			}
		}
	}

	/** A block set quietly (no neighbour updates): the ground and walls. Only when it changes. */
	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		if (level.getBlockState(pos) != state) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			changed(pos.getX(), pos.getZ());
		}
	}

	/** A block set with its neighbours told (fences join up, a lantern sits on its post). */
	private static void place(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_ALL);
		changed(pos.getX(), pos.getZ());
	}
}
