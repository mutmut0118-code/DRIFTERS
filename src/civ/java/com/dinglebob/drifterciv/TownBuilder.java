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
import net.minecraft.util.Mth;
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
	/** A city's (A4: "40-80 households ... so it feels like a CITY"): its main roads, how far out it goes, most people in it. */
	static final int CITY_ARM = 132;
	static final int CITY_REACH = PLAZA + CITY_ARM + 12;
	static final int CITY_PEOPLE = 100;
	/** This many households or more: a city. */
	static final int CITY = 30;
	/** The town being made's main roads and reach (set by its job each step). */
	private static int arm = ARM;
	private static int reach = REACH;
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
	private static final String[] WOODS = {"oak", "spruce", "birch", "dark_oak", "acacia", "cherry"};

	public record Result(@Nullable Town town, String message) {
	}

	/**
	 * A town's look, by its biome (milestone 23, docs/LIVED_WORLD.md A3: "a plains town of timber and oak, a desert town of
	 * sandstone, a savanna town of acacia, a taiga town of spruce logs, a snowy town of spruce and stone"): its woods (the
	 * first its main one: the store, stall, well, fences, lamps; the rest as often as they're listed, for its houses), the
	 * stone its houses have for cobblestone (null: cobblestone), the store's walls, the plaza's paving, and its paths and what
	 * goes under them.
	 */
	record Style(String name, List<String> woods, @Nullable String stone, Block wall, Block paving, Block pavingToo, Block path, Block fill) {
		static final Style PLAINS = new Style("plains", List.of("oak", "oak", "oak", "birch", "birch", "spruce"), null, Blocks.COBBLESTONE,
			Blocks.STONE_BRICKS, Blocks.COBBLESTONE, Blocks.DIRT_PATH, Blocks.DIRT);
		static final Style DESERT = new Style("desert", List.of("birch", "birch", "acacia"), "sandstone", Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE,
			Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE);
		static final Style SAVANNA = new Style("savanna", List.of("acacia", "acacia", "acacia", "acacia", "oak", "dark_oak"), null, Blocks.COBBLESTONE,
			Blocks.STONE_BRICKS, Blocks.COBBLESTONE, Blocks.DIRT_PATH, Blocks.DIRT);
		static final Style TAIGA = new Style("taiga", List.of("spruce", "spruce", "spruce", "spruce", "dark_oak", "oak"), "mossy_cobblestone",
			Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.DIRT_PATH, Blocks.DIRT);
		static final Style SNOWY = new Style("snowy", List.of("spruce", "spruce", "spruce", "spruce", "birch", "dark_oak"), null, Blocks.COBBLESTONE,
			Blocks.STONE_BRICKS, Blocks.COBBLESTONE, Blocks.DIRT_PATH, Blocks.DIRT);

		String main() {
			return this.woods.get(0);
		}

		String pick(RandomSource random) {
			return this.woods.get(random.nextInt(this.woods.size()));
		}

		/** The style for the biome at this spot. */
		static Style at(ServerLevel level, BlockPos pos) {
			var biome = level.getBiome(pos);
			return biome.is(net.minecraft.tags.BiomeTags.HAS_VILLAGE_DESERT) || biome.is(net.minecraft.tags.BiomeTags.IS_BADLANDS) ? DESERT
				: biome.is(net.minecraft.tags.BiomeTags.HAS_VILLAGE_SAVANNA) || biome.is(net.minecraft.tags.BiomeTags.IS_SAVANNA) ? SAVANNA
				: biome.is(net.minecraft.tags.BiomeTags.HAS_VILLAGE_SNOWY) ? SNOWY
				: biome.is(net.minecraft.tags.BiomeTags.HAS_VILLAGE_TAIGA) || biome.is(net.minecraft.tags.BiomeTags.IS_TAIGA) ? TAIGA : PLAINS;
		}
	}

	/** The style of the town being made (set by its job each step; plains for anything else). */
	private static Style style = Style.PLAINS;
	/** Its roads' and paths' blocks, and the slab for where they climb (set by its job each step: W1, by its size). */
	private static java.util.function.Supplier<BlockState> paving = Blocks.DIRT_PATH::defaultBlockState;
	private static BlockState pavingSlab = Blocks.COBBLESTONE_SLAB.defaultBlockState();
	private static BlockState pavingStairs = Blocks.COBBLESTONE_STAIRS.defaultBlockState();

	/** A block of the town's main wood: "planks", "log", "fence", "door"... (oak if there's no such block). */
	private static Block wood(String kind) {
		Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(style.main() + "_" + kind));
		return block == Blocks.AIR ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace("oak_" + kind))
			: block;
	}


	/** A rectangle of the town's ground (min and max x and z, inclusive), to keep things apart. */
	record Rect(int x0, int z0, int x1, int z1) {
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

	/**
	 * A road (the user, 2026-10-30: "curved and bending roads, roads that follow the land"): its middle line a block a step,
	 * bending as it goes, and the columns it covers (three wide), each with the step of the line it's nearest.
	 */
	private static final class Road {
		final double[] xs;
		final double[] zs;
		final double[] headings;
		final java.util.Map<Long, Integer> cells = new java.util.HashMap<>();
		final Rect bounds;
		/** A ring road: kept whole (never cut back to what it leads to). */
		boolean loop;

		/** From its middle line's points: {x, z, heading} each (a block's middle is x + 0.5). */
		Road(List<double[]> line) {
			int n = line.size();
			this.xs = new double[n];
			this.zs = new double[n];
			this.headings = new double[n];
			java.util.Map<Long, Double> nearest = new java.util.HashMap<>();
			int x0 = Integer.MAX_VALUE;
			int z0 = Integer.MAX_VALUE;
			int x1 = Integer.MIN_VALUE;
			int z1 = Integer.MIN_VALUE;
			for (int t = 0; t < n; t++) {
				this.xs[t] = line.get(t)[0];
				this.zs[t] = line.get(t)[1];
				this.headings[t] = line.get(t)[2];
				int bx = Mth.floor(this.xs[t]);
				int bz = Mth.floor(this.zs[t]);
				for (int x = bx - 2; x <= bx + 2; x++) {
					for (int z = bz - 2; z <= bz + 2; z++) {
						double dx = x + 0.5 - this.xs[t];
						double dz = z + 0.5 - this.zs[t];
						double d = dx * dx + dz * dz;
						if (d > HALF_WIDTH * HALF_WIDTH) {
							continue;
						}

						long key = BlockPos.asLong(x, 0, z);
						Double had = nearest.get(key);
						if (had == null || d < had) {
							nearest.put(key, d);
							this.cells.put(key, t);
						}

						x0 = Math.min(x0, x);
						z0 = Math.min(z0, z);
						x1 = Math.max(x1, x);
						z1 = Math.max(z1, z);
					}
				}
			}

			this.bounds = n == 0 ? new Rect(0, 0, -1, -1) : new Rect(x0, z0, x1, z1);
		}

		int length() {
			return this.xs.length;
		}

		BlockPos at(int t) {
			return new BlockPos(Mth.floor(this.xs[t]), 0, Mth.floor(this.zs[t]));
		}

		double heading(int t) {
			return this.headings[Math.max(0, Math.min(this.headings.length - 1, t))];
		}

		/** The same road, only its first {@code length} steps. */
		Road upTo(int length) {
			if (length >= this.length()) {
				return this;
			}

			List<double[]> line = new ArrayList<>();
			for (int t = 0; t < length; t++) {
				line.add(new double[] {this.xs[t], this.zs[t], this.headings[t]});
			}

			return new Road(line);
		}

		/** The step of its middle line nearest x, z if within {@code by} blocks of it, or -1. */
		int nearest(double x, double z, double by) {
			int best = -1;
			double bestSq = by * by;
			for (int t = 0; t < this.length(); t++) {
				double dx = this.xs[t] - x;
				double dz = this.zs[t] - z;
				if (dx * dx + dz * dz <= bestSq) {
					bestSq = dx * dx + dz * dz;
					best = t;
				}
			}

			return best;
		}
	}

	/** Half a road's width, out from its middle line (three blocks wide). */
	private static final double HALF_WIDTH = 1.5;

	/** One side of a road houses go along (1 or -1: which side of the way it goes), and how far along. */
	private static final class Side {
		final Road road;
		final int sign;
		int cursor;
		boolean full;

		Side(Road road, int sign, int cursor) {
			this.road = road;
			this.sign = sign;
			this.cursor = cursor;
			this.full = cursor >= road.length() - 4;
		}

		/** The way out from the road on this side at this step (the houses' fronts face back to it). */
		double out(int t) {
			return this.road.heading(t) + this.sign * Math.PI / 2;
		}
	}

	/**
	 * A town's shape (the user, 2026-10-30: "can we change the shape to different types? like curved and bending roads, roads
	 * that follow the land, and towns that go down a main street"): how much its roads wander besides following the land
	 * ({@code wander}), how hard they keep to the way they set off ({@code keep}), and how far they turn a block at most
	 * (radians). How many leave the plaza and which ways: {@link Job#mainHeadings}.
	 */
	enum Shape {
		CROSSROADS("crossroads", "four roads out from the plaza, bending with the land", 0.8, 5.0, 0.07),
		MAIN_STREET("main_street", "one long street through the plaza, a few lanes off it", 1.0, 4.0, 0.07),
		WINDING("winding", "three roads winding out from the plaza, lanes off any of them", 2.5, 1.5, 0.1),
		// W3 (the user, 2026-10-30: "add a river town shape too and more shapes"; picked: river, harbour, hill, ring and grid).
		RIVER("river", "on both banks of a river: a bridge by the plaza, roads along the banks, a dock", 1.0, 3.0, 0.07),
		HARBOUR("harbour", "along a shore: a waterfront road, piers out over the water", 1.0, 3.0, 0.07),
		HILL("hill", "on a hill: its streets winding round it, houses on terraces, a lookout", 0.4, 0.8, 0.1),
		RING("ring", "a ring road round the plaza, spokes out from it", 0.8, 5.0, 0.07),
		GRID("grid", "planned streets on a grid round the plaza", 0.0, 10.0, 0.07);

		final String id;
		final String what;
		final double wander;
		final double keep;
		final double turn;

		Shape(String id, String what, double wander, double keep, double turn) {
			this.id = id;
			this.what = what;
			this.wander = wander;
			this.keep = keep;
			this.turn = turn;
		}

		@Nullable
		static Shape byId(String id) {
			for (Shape shape : values()) {
				if (shape.id.equals(id)) {
					return shape;
				}
			}

			return null;
		}
	}

	private static Shape byChance(RandomSource random) {
		double roll = random.nextDouble();
		for (int i = 0; i < SHARES.length; i++) {
			roll -= SHARES[i];
			if (roll < 0) {
				return BY_CHANCE[i];
			}
		}

		return Shape.CROSSROADS;
	}

	/** Debug: the shape every town made from now on has (null: each its own, by its size and chance). */
	@Nullable
	static Shape forcedShape;
	/**
	 * How often a town has each shape where the land doesn't pick one (a river through it: a river town; a shore: a harbour;
	 * a hill: a hill town); a hamlet's is a main street, a city's a grid in the middle with crossroads out.
	 */
	private static final double[] SHARES = {0.25, 0.30, 0.20, 0.15, 0.10};
	private static final Shape[] BY_CHANCE = {Shape.CROSSROADS, Shape.MAIN_STREET, Shape.WINDING, Shape.RING, Shape.GRID};
	/** Grid streets this far apart; a town's grid this far out from the plaza, a city's this far. */
	private static final int GRID_STEP = 24;
	private static final int TOWN_GRID = 30;
	private static final int CITY_GRID = 72;
	/** A ring road this far from the plaza's middle (a city's further). */
	private static final int RING = 24;
	/** How far round the plaza the land's looked over for water and hills (a harbour's shore was 70 blocks off in testing). */
	private static final int SURVEY = 80;
	/** The most water a river town's bridge crosses. */
	private static final int LONGEST_RIVER_BRIDGE = 40;
	/** Side roads at least this far apart. */
	private static final int LANES_APART = 20;
	/** A road leads to a house, the store, a field or the pen this near it; it runs this far past the last, and out of the plaza this far at least. */
	private static final int LEADS_TO = 12;
	private static final int ROAD_BEYOND = 6;
	private static final int ROAD_STUB = 12;
	/** How far round its plaza, store, fields and houses the land is eased to them (W2), and its ground's pieces, a step each. */
	private static final int PAD_REACH = 24;
	private static final int GROUND_PIECE = 24;
	/** This many households or fewer: a hamlet (its roads and paths dirt and gravel). */
	private static final int HAMLET = 5;

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
		Job job = new Job(level, cx, cz, households, wanted, false, false);
		while (job.step()) {
		}

		Result result = job.result();
		return new Result(result.town(), result.message() + warning);
	}

	/** Debug: the ruins of a town made here at once. */
	public static Result ruin(ServerLevel level, BlockPos at) {
		for (int x = at.getX() - REACH - 16; x <= at.getX() + REACH + 16; x += 16) {
			for (int z = at.getZ() - REACH - 16; z <= at.getZ() + REACH + 16; z += 16) {
				level.getChunk(x >> 4, z >> 4);
			}
		}

		Job job = new Job(level, at.getX(), at.getZ(), 4 + level.getRandom().nextInt(4), null, true, true);
		while (job.step()) {
		}

		return job.result();
	}

	/** How far round its middle a town's ground (and what's worked out round it) may reach: its chunks loaded before it's made. */
	static final int MOST_REACH = REACH + 40;
	static final int CITY_MOST_REACH = CITY_REACH + 40;

	/** Debug: a city of this many households to be made here, a step at a time as world generation's are (too big for once). */
	public static Result city(ServerLevel level, BlockPos at, int households) {
		TownSites.debugSite(level, at, households);
		return new Result(null, "a city of " + households + " households will be made at " + at.getX() + ", " + at.getZ() + " (a step at a time; see the log)");
	}

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
		/** A town that failed long ago (A4: "a town that failed (burnt houses, an empty store, graves, a sign): history"):
		 * nobody lives there. */
		final boolean ruin;
		/** A city (A4): longer main roads, more side roads, more people (100 at most). */
		final boolean city;
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
		/** A town of the world plan (W4): its id (its nation knows it by it), and the ways its roads between towns come in. */
		@Nullable
		UUID plannedId;
		List<Double> gates = List.of();
		/** Its shape, and how many of its roads leave the plaza (the rest are side roads off them). */
		Shape shape;
		private int mains;
		private BlockPos storeAt;
		private Direction storeFront = Direction.WEST;
		private Direction penFront = Direction.EAST;
		private int storeBase;
		private Rect storeRect;
		private List<Rect> fieldRects;
		private Rect penRect;
		private final List<Rect> taken = new ArrayList<>();
		/** Every column of its roads, and (worked out when first asked) the town's ground round them. */
		private final java.util.Set<Long> roadCells = new java.util.HashSet<>();
		@Nullable
		private java.util.Set<Long> roadGround;
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
		private Style look = Style.PLAINS;
		private int lamps;
		private int torches;
		/** Its past (A5): how long ago it was founded; the house left empty (its household gone) and the one still going up. */
		private int foundedAgo;
		private int emptyHouse = -1;
		private int buildingHouse = -1;
		private final List<String> owners = new ArrayList<>();
		/** Columns of its door paths (kept clear of lamp posts: one in front of a door blocked the way in, found in a city). */
		private final java.util.Set<Long> pathCells = new java.util.HashSet<>();

		Job(ServerLevel level, int cx, int cz, int households, @Nullable String wanted, boolean world, boolean ruin) {
			this.level = level;
			this.server = level.getServer();
			this.random = level.getRandom();
			this.dimension = level.dimension().location().toString();
			this.name = wanted != null ? wanted : Towns.newName(this.random);
			this.cx = cx;
			this.cz = cz;
			this.households = households;
			this.world = world;
			this.ruin = ruin;
			this.city = households >= CITY && !ruin;
			this.shape = forcedShape != null ? forcedShape : this.city ? Shape.GRID : households <= 5 ? Shape.MAIN_STREET : byChance(this.random);
			this.steps.add(new Step("layout", this::layout));
		}

		/** One step done; false when it's all made (or there's nothing to make it with). */
		boolean step() {
			Step next = this.steps.poll();
			if (next == null) {
				return false;
			}

			long start = System.nanoTime();
			style = this.look;
			paving = this::paving;
			pavingSlab = this.pavingSlab();
			pavingStairs = this.pavingStairs();
			arm = this.city ? CITY_ARM : ARM;
			reach = this.city ? CITY_REACH : REACH;
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
		// 1. The layout, as a village grows, by the town's shape: the plaza in the middle and its roads out from it, following
		// the land; the store, the fields and the pen near the plaza where there's room off the roads; side roads off those;
		// houses along the roads come later, one at a time.
		// -----------------------------------------------------------------------------------------

		private void layout() {
			GROUND.clear();
			arm = this.city ? CITY_ARM : ARM;
			reach = this.city ? CITY_REACH : REACH;
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
			this.taken.add(this.plaza);
			this.survey();
			this.pickShape();
			// (A main street a third longer: it's most of the town.)
			int length = this.shape == Shape.MAIN_STREET ? arm + arm / 3 : arm;
			switch (this.shape) {
				case RIVER -> this.riverRoads(length);
				case HARBOUR -> this.harbourRoads(length);
				default -> {
					for (double heading : this.mainHeadings()) {
						this.main(heading, length, this.laying());
					}
				}
			}

			if (this.shape == Shape.RING) {
				this.addRoad(this.ring(this.city ? RING + 16 : RING));
			} else if (this.shape == Shape.GRID) {
				this.gridStreets(this.city ? CITY_GRID : TOWN_GRID);
			}

			// The roads between towns (W5) come in from these ways: a main road out to meet each (one going that way already does),
			// never cut back.
			for (double gate : this.gates) {
				Road served = null;
				for (Road road : this.roads) {
					if (road.length() > 0 && Math.abs(Math.atan2(Math.sin(road.heading(0) - gate), Math.cos(road.heading(0) - gate))) < 0.5) {
						served = road;
					}
				}

				if (served == null) {
					served = this.main(gate, arm + 24, new Laying(8, 0.3, 0.07, LONGEST_BRIDGE, 3, false));
				}

				if (served != null) {
					served.loop = true;
				}
			}

			// (None out of it: water or the town's edge right by the plaza that way. Any way out it can, two at most.)
			for (int k = 0; k < 8 && this.roads.size() < 2; k++) {
				double heading = k * Math.PI / 4;
				double edge = (PLAZA + 1) / Math.max(Math.abs(Math.cos(heading)), Math.abs(Math.sin(heading)));
				Road main = this.lay(cx + 0.5 + Math.cos(heading) * edge, cz + 0.5 + Math.sin(heading) * edge, heading, length, List.of(this.plaza), 0);
				if (main.length() >= SHORTEST_BRANCH && this.roads.stream().noneMatch(other -> other.bounds.near(main.bounds, 4))) {
					this.addRoad(main);
				}
			}

			this.mains = this.roads.size();
			DrifterCivilizations.LOGGER.info("[town] {}: {} ({} roads from the plaza{})", this.name, this.shape.id, this.mains, this.river ? ", a river "
				+ this.riverWidth + " wide " + (int)this.waterAway + " off" : this.coast ? ", the shore " + (int)this.waterAway + " off" : this.hill ? ", a hill" : "");
			this.then("lots", this::lots);
		}

		/**
		 * The ways its roads leave the plaza: four square (a ring's and a grid's spokes too), a main street's two, a winding
		 * town's three, a hill town's round the hill both ways and down it.
		 */
		private double[] mainHeadings() {
			return switch (this.shape) {
				case CROSSROADS, RING, GRID, RIVER, HARBOUR -> new double[] {0, Math.PI / 2, Math.PI, -Math.PI / 2};
				case HILL -> new double[] {this.uphill + Math.PI / 2, this.uphill - Math.PI / 2, this.uphill + Math.PI};
				case MAIN_STREET -> {
					double way = this.flattestAxis();
					yield new double[] {way, way + Math.PI};
				}
				case WINDING -> {
					double first = this.random.nextDouble() * 2 * Math.PI;
					double[] ways = new double[3];
					for (int i = 0; i < 3; i++) {
						ways[i] = first + i * 2 * Math.PI / 3 + (this.random.nextDouble() - 0.5) * 0.6;
					}

					yield ways;
				}
			};
		}

		/** For a main street: the line through the plaza (of eight) that climbs least either side of it. */
		private double flattestAxis() {
			double best = 0;
			double bestCost = Double.MAX_VALUE;
			int middle = ground(this.level, this.cx, this.cz);
			for (int k = 0; k < 8; k++) {
				double way = k * Math.PI / 8;
				// (Ties broken at random, so a flat plain's streets don't all run east to west.)
				double cost = this.random.nextDouble() * 2;
				for (int sign = -1; sign <= 1; sign += 2) {
					int last = middle;
					for (int d = PLAZA + 6; d <= arm; d += 6) {
						int x = Mth.floor(this.cx + 0.5 + sign * Math.cos(way) * d);
						int z = Mth.floor(this.cz + 0.5 + sign * Math.sin(way) * d);
						if (!this.inReach(x, z, 4)) {
							break;
						}

						int g = ground(this.level, x, z);
						cost += Math.abs(g - last) + (wet(this.level, x, z) ? 10 : 0);
						last = g;
					}
				}

				if (cost < bestCost) {
					bestCost = cost;
					best = way;
				}
			}

			return best;
		}

		/**
		 * How a road's laid: how hard it keeps to the way it set off, how much it wanders, how far it turns a block (radians),
		 * the most water it bridges, what water ahead costs it, and whether it stops where it meets another road.
		 */
		private record Laying(double keep, double wander, double turn, int bridge, double wetCost, boolean junctions) {
		}

		/** The town's shape's way of laying a road. */
		private Laying laying() {
			return new Laying(this.shape.keep, this.shape.wander, this.shape.turn, LONGEST_BRIDGE, 3, true);
		}

		/** A road out of the plaza this way (from its edge), if one goes that way at all. */
		@Nullable
		private Road main(double heading, int length, Laying laying) {
			double edge = (PLAZA + 1) / Math.max(Math.abs(Math.cos(heading)), Math.abs(Math.sin(heading)));
			Road main = this.lay(this.cx + 0.5 + Math.cos(heading) * edge, this.cz + 0.5 + Math.sin(heading) * edge, heading, length, List.of(this.plaza), 0,
				laying);
			if (main.length() == 0) {
				return null;
			}

			this.addRoad(main);
			return main;
		}

		private Road lay(double x, double z, double heading, int length, List<Rect> avoid, int gap) {
			return this.lay(x, z, heading, length, avoid, gap, this.laying());
		}

		/**
		 * A road laid from (x, z) setting off this way, up to so long (the user, 2026-10-30: "roads that follow the land"): every
		 * few blocks it takes the way on that climbs least over the next twelve, so it bends round a hill rather than going
		 * straight up it, kept to the way it set off by {@code keep} and wandering by {@code wander}; it turns a few degrees a
		 * block at most, so its bends are wide. It stops at the town's edge, at water too wide to bridge, short of {@code
		 * avoid} (this far off), and (if it's to) where it meets another road (a junction).
		 */
		private Road lay(double x, double z, double heading, int length, List<Rect> avoid, int gap, Laying laying) {
			List<double[]> line = new ArrayList<>();
			double way = heading;
			double target = heading;
			int wetRun = 0;
			for (int t = 0; t < length; t++) {
				if (t > 0) {
					if (t % 4 == 0) {
						target = this.choose(x, z, way, heading, laying);
					}

					way += Mth.clamp(Math.atan2(Math.sin(target - way), Math.cos(target - way)), -laying.turn(), laying.turn());
					x += Math.cos(way);
					z += Math.sin(way);
				}

				int bx = Mth.floor(x);
				int bz = Mth.floor(z);
				if (!this.inReach(bx, bz, 2) || avoid.stream().anyMatch(rect -> rect.away(bx, bz) <= gap)) {
					break;
				}

				if (wet(this.level, bx, bz)) {
					if (++wetRun > laying.bridge()) {
						break;
					}
				} else {
					wetRun = 0;
				}

				boolean joins = laying.junctions() && t > 4 && this.nearRoad(bx, bz, 2);
				line.add(new double[] {x, z, way});
				if (joins) {
					break;
				}
			}

			// (Never ending in the water: a bridge goes across or not at all.)
			for (int left = Math.min(wetRun, laying.bridge()); left > 0 && !line.isEmpty(); left--) {
				line.remove(line.size() - 1);
			}

			return new Road(line);
		}

		/** The way on from here (of five, up to 30 degrees either side) that climbs least over the next twelve blocks. */
		private double choose(double x, double z, double way, double heading, Laying laying) {
			int here = ground(this.level, Mth.floor(x), Mth.floor(z));
			double best = way;
			double bestCost = Double.MAX_VALUE;
			for (int k = -2; k <= 2; k++) {
				double next = way + k * Math.PI / 12;
				double cost = Math.abs(Math.atan2(Math.sin(next - heading), Math.cos(next - heading))) * laying.keep()
					+ this.random.nextDouble() * laying.wander();
				int last = here;
				for (int ahead = 4; ahead <= 12; ahead += 4) {
					int ax = Mth.floor(x + Math.cos(next) * ahead);
					int az = Mth.floor(z + Math.sin(next) * ahead);
					if (!this.inReach(ax, az, 4)) {
						cost += 6;
						break;
					}

					int g = ground(this.level, ax, az);
					cost += Math.abs(g - last) + (wet(this.level, ax, az) ? laying.wetCost() : 0) + (ahead > 4 && this.nearRoad(ax, az, 3) ? 4 : 0);
					last = g;
				}

				if (cost < bestCost) {
					bestCost = cost;
					best = next;
				}
			}

			return best;
		}

		// -----------------------------------------------------------------------------------------
		// W3: the land round the plaza looked over, the shape it picks, and the roads of the shapes that need their own.
		// -----------------------------------------------------------------------------------------

		/** What the land round the plaza is (see survey). */
		private boolean river;
		private boolean coast;
		private boolean hill;
		/** The way to the nearest water and how far; the river's (or the shore's) line; how wide the river is; the way uphill. */
		private double toWater;
		private double waterAway;
		private double waterLine;
		private int riverWidth;
		private double uphill;

		/**
		 * The land within about 80 blocks of the plaza looked over (every fourth block): water, and whether it's a river (land
		 * again past it, straight on from the plaza, within 40 blocks) or a shore (a lake's, the sea's); how the land rises
		 * and falls (a hill: 14 blocks or more between its highest and lowest).
		 */
		private void survey() {
			int h0 = ground(this.level, this.cx, this.cz);
			int high = h0;
			int low = h0;
			double ux = 0;
			double uz = 0;
			int samples = 0;
			List<int[]> water = new ArrayList<>();
			for (int dx = -SURVEY; dx <= SURVEY; dx += 4) {
				for (int dz = -SURVEY; dz <= SURVEY; dz += 4) {
					int x = this.cx + dx;
					int z = this.cz + dz;
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > SURVEY || !this.inReach(x, z, 4)) {
						continue;
					}

					samples++;
					if (wet(this.level, x, z)) {
						water.add(new int[] {x, z});
						continue;
					}

					int h = ground(this.level, x, z);
					if (d <= 48) {
						high = Math.max(high, h);
						low = Math.min(low, h);
					}

					if (d > 0 && d <= 48) {
						ux += (h - h0) * dx / d;
						uz += (h - h0) * dz / d;
					}
				}
			}

			this.uphill = Math.atan2(uz, ux);
			this.hill = high - low >= 14;
			if (water.size() < 4 || samples == 0) {
				return;
			}

			// Out from the plaza sixteen ways: the nearest river (water 6 to 40 blocks across, land again past it) and the nearest
			// shore (water with no land past it). The nearest water alone was a stream once, with the sea further off another way.
			double riverAt = Double.MAX_VALUE;
			double shoreAt = Double.MAX_VALUE;
			double riverWay = 0;
			double shoreWay = 0;
			int riverAcross = 0;
			for (int k = 0; k < 16; k++) {
				double way = k * Math.PI / 8;
				int first = -1;
				int across = 0;
				boolean landAgain = false;
				for (int d = 0; d <= SURVEY + 40; d++) {
					int x = Mth.floor(this.cx + 0.5 + Math.cos(way) * d);
					int z = Mth.floor(this.cz + 0.5 + Math.sin(way) * d);
					if (!this.inReach(x, z, 2)) {
						break;
					}

					if (wet(this.level, x, z)) {
						first = first < 0 ? d : first;
						across++;
					} else if (first >= 0) {
						landAgain = true;
						break;
					}
				}

				if (first < 0) {
					continue;
				}

				if (landAgain && across >= 6 && across <= LONGEST_RIVER_BRIDGE && first <= 56 && first < riverAt) {
					riverAt = first;
					riverWay = way;
					riverAcross = across;
				} else if (!landAgain && across >= 8 && first <= SURVEY && first < shoreAt) {
					shoreAt = first;
					shoreWay = way;
				}
			}

			// (A stream a few blocks wide is bridged like any water, not a river town: found, a hill town taken for one.)
			this.river = riverAt < Double.MAX_VALUE;
			this.coast = !this.river && shoreAt < Double.MAX_VALUE;
			this.toWater = this.river ? riverWay : shoreWay;
			this.waterAway = this.river ? riverAt : this.coast ? shoreAt : 0;
			this.riverWidth = riverAcross;
			DrifterCivilizations.LOGGER.info("[town] {}: water in {} of {} samples; a river {}, a shore {}", this.name, water.size(), samples,
				this.river ? (int)riverAt + " off, " + riverAcross + " across" : "none near", shoreAt < Double.MAX_VALUE ? (int)shoreAt + " off" : "none near");
			if (!this.river && !this.coast) {
				return;
			}

			int[] nearest = {Mth.floor(this.cx + Math.cos(this.toWater) * this.waterAway), Mth.floor(this.cz + Math.sin(this.toWater) * this.waterAway)};
			// The water's line: the long way of its samples near the nearest (a river's flow, a shore's run).
			double mx = 0;
			double mz = 0;
			int n = 0;
			for (int[] w : water) {
				if (Math.hypot(w[0] - nearest[0], w[1] - nearest[1]) <= 48) {
					mx += w[0];
					mz += w[1];
					n++;
				}
			}

			mx /= Math.max(1, n);
			mz /= Math.max(1, n);
			double sxx = 0;
			double szz = 0;
			double sxz = 0;
			for (int[] w : water) {
				if (Math.hypot(w[0] - nearest[0], w[1] - nearest[1]) <= 48) {
					sxx += (w[0] - mx) * (w[0] - mx);
					szz += (w[1] - mz) * (w[1] - mz);
					sxz += (w[0] - mx) * (w[1] - mz);
				}
			}

			// (A shore runs across the way to the water; a river's line from its samples.)
			this.waterLine = this.river ? 0.5 * Math.atan2(2 * sxz, sxx - szz) : this.toWater + Math.PI / 2;
		}

		/** The shape the land picks (a river, a shore, a hill), unless it's set (debug) or a city; the chance one otherwise. */
		private void pickShape() {
			Shape picked = this.shape;
			if (forcedShape == null && !this.city) {
				picked = this.river ? Shape.RIVER : this.coast ? Shape.HARBOUR : this.hill ? Shape.HILL : this.shape;
			}

			// (Asked for a river or a harbour with no water near: a main street.)
			if (picked == Shape.RIVER && !this.river || picked == Shape.HARBOUR && !this.river && !this.coast) {
				picked = Shape.MAIN_STREET;
			}

			this.shape = picked;
		}

		/**
		 * A river town's roads: one from the plaza across the river by a bridge, keeping straight; along both banks from its
		 * ends, up and down the river, keeping off the water.
		 */
		private void riverRoads(int length) {
			Road bridge = this.main(this.toWater, length + LONGEST_RIVER_BRIDGE, new Laying(12, 0.2, 0.05, LONGEST_RIVER_BRIDGE, 0, false));
			if (bridge == null) {
				return;
			}

			int before = -1;
			int after = -1;
			for (int t = 0; t < bridge.length(); t++) {
				BlockPos at = bridge.at(t);
				boolean w = wet(this.level, at.getX(), at.getZ());
				if (w && before < 0) {
					before = t - 1;
				} else if (!w && before >= 0 && after < 0) {
					after = t;
				}
			}

			Laying bank = new Laying(2.0, 0.6, 0.07, LONGEST_BRIDGE, 12, true);
			for (int foot : new int[] {Math.max(0, before - 3), after + 3}) {
				if (foot < 0 || foot >= bridge.length() || before < 0) {
					continue;
				}

				for (int sign = -1; sign <= 1; sign += 2) {
					double way = this.waterLine + (sign < 0 ? Math.PI : 0);
					Road road = this.lay(bridge.xs[foot] + Math.cos(way) * 4, bridge.zs[foot] + Math.sin(way) * 4, way, length, List.of(this.plaza), 0, bank);
					if (road.length() >= SHORTEST_BRANCH / 2) {
						this.addRoad(road);
						this.banks.add(road);
					}
				}
			}

			// (Away from the water too, so it's a town and not only a riverside.)
			this.main(this.toWater + Math.PI, length, this.laying());
		}

		/** A harbour's roads: one from the plaza down to the shore, along the shore both ways from there, one inland. */
		private void harbourRoads(int length) {
			Road down = this.main(this.toWater, Math.max(length, (int)this.waterAway + 8), new Laying(10, 0.2, 0.05, 0, 0, false));
			if (down != null && down.length() > 4) {
				int foot = Math.max(0, down.length() - 3);
				Laying front = new Laying(1.5, 0.5, 0.08, 3, 14, true);
				for (int sign = -1; sign <= 1; sign += 2) {
					double way = this.waterLine + (sign < 0 ? Math.PI : 0);
					Road road = this.lay(down.xs[foot] + Math.cos(way) * 2, down.zs[foot] + Math.sin(way) * 2, way, length, List.of(this.plaza), 0, front);
					if (road.length() >= SHORTEST_BRANCH / 2) {
						this.addRoad(road);
						this.banks.add(road);
					}
				}
			}

			this.main(this.toWater + Math.PI, length, this.laying());
			this.main(this.toWater + Math.PI / 2, length / 2, this.laying());
			this.main(this.toWater - Math.PI / 2, length / 2, this.laying());
		}

		/** Roads along the water (a river's banks, a harbour's front), for its docks and piers. */
		private final List<Road> banks = new ArrayList<>();

		/** A ring road round the plaza this far out (wavering a little), as far round as the land lets it go. */
		private Road ring(int radius) {
			List<double[]> line = new ArrayList<>();
			int n = (int)Math.round(2 * Math.PI * radius);
			double phase = this.random.nextDouble() * 2 * Math.PI;
			double waver = this.random.nextDouble() * 2 * Math.PI;
			int wetRun = 0;
			for (int i = 0; i <= n; i++) {
				double a = phase + i * 2 * Math.PI / n;
				double r = radius + 2 * Math.sin(3 * a + waver);
				double x = this.cx + 0.5 + Math.cos(a) * r;
				double z = this.cz + 0.5 + Math.sin(a) * r;
				int bx = Mth.floor(x);
				int bz = Mth.floor(z);
				if (!this.inReach(bx, bz, 2)) {
					break;
				}

				wetRun = wet(this.level, bx, bz) ? wetRun + 1 : 0;
				if (wetRun > LONGEST_BRIDGE) {
					break;
				}

				line.add(new double[] {x, z, a + Math.PI / 2});
			}

			for (int left = Math.min(wetRun, LONGEST_BRIDGE); left > 0 && !line.isEmpty(); left--) {
				line.remove(line.size() - 1);
			}

			Road ring = new Road(line);
			ring.loop = true;
			return ring;
		}

		/** Straight streets on a grid round the plaza, every {@link #GRID_STEP} blocks, out this far each way. */
		private void gridStreets(int span) {
			for (int k = -span / GRID_STEP * GRID_STEP; k <= span; k += GRID_STEP) {
				if (k == 0) {
					continue;
				}

				for (int axis = 0; axis < 2; axis++) {
					double x = axis == 0 ? this.cx + 0.5 - span : this.cx + 0.5 + k;
					double z = axis == 0 ? this.cz + 0.5 + k : this.cz + 0.5 - span;
					Road street = this.straight(x, z, axis == 0 ? 0 : Math.PI / 2, 2 * span + 1);
					if (street.length() >= SHORTEST_BRANCH) {
						this.addRoad(street);
					}
				}
			}
		}

		/** A straight road, its longest dry run (bridging a little water) inside the town's reach. */
		private Road straight(double x, double z, double heading, int length) {
			List<double[]> best = new ArrayList<>();
			List<double[]> line = new ArrayList<>();
			int wetRun = 0;
			for (int t = 0; t <= length; t++) {
				double px = x + Math.cos(heading) * t;
				double pz = z + Math.sin(heading) * t;
				int bx = Mth.floor(px);
				int bz = Mth.floor(pz);
				boolean ok = this.inReach(bx, bz, 2);
				wetRun = ok && wet(this.level, bx, bz) ? wetRun + 1 : 0;
				if (!ok || wetRun > LONGEST_BRIDGE) {
					for (int left = Math.min(wetRun, LONGEST_BRIDGE); left > 0 && !line.isEmpty(); left--) {
						line.remove(line.size() - 1);
					}

					if (line.size() > best.size()) {
						best = line;
					}

					line = new ArrayList<>();
					wetRun = 0;
					continue;
				}

				line.add(new double[] {px, pz, heading});
			}

			if (line.size() > best.size()) {
				best = line;
			}

			return new Road(best);
		}

		/** A hill town's lookout's spot (null: none), on the highest free ground near the plaza. */
		@Nullable
		private Rect lookout;

		/**
		 * Docks and piers (W3): a river town's a dock or two from its banks, a harbour's piers along its front every so often
		 * (planks a block over the water, railings, piles, a lantern at the end, a boat tied up at a harbour's).
		 */
		private void waterfront() {
			int made = 0;
			for (Road bank : this.banks) {
				int every = this.shape == Shape.RIVER ? 30 : 20;
				for (int t = 6; t < bank.length() - 4 && made < (this.shape == Shape.RIVER ? 2 : 4); t += every) {
					made += this.pier(bank, t) ? 1 : 0;
				}
			}

			DrifterCivilizations.LOGGER.info("[town] {}: {} piers", this.name, made);
		}

		private boolean pier(Road road, int t) {
			// Which side the water's on: the first side that meets it within ten blocks.
			double heading = road.heading(t);
			for (int sign = -1; sign <= 1; sign += 2) {
				double out = heading + sign * Math.PI / 2;
				Direction way = Direction.getNearest(Math.cos(out), 0, Math.sin(out));
				BlockPos from = road.at(t);
				int edge = -1;
				for (int d = 2; d <= 10 && edge < 0; d++) {
					BlockPos at = from.relative(way, d);
					if (this.roadCells.contains(BlockPos.asLong(at.getX(), 0, at.getZ())) && d > 2) {
						break;
					}

					if (wet(this.level, at.getX(), at.getZ())) {
						edge = d;
					}
				}

				if (edge < 0) {
					continue;
				}

				int length = this.shape == Shape.RIVER ? Math.max(3, Math.min(5, this.riverWidth / 3)) : 8 + this.random.nextInt(7);
				int half = this.shape == Shape.RIVER ? 0 : 1;
				BlockPos start = from.relative(way, edge);
				int deck = ground(this.level, start.getX(), start.getZ()) + 1;
				Direction across = way.getClockWise();
				List<BlockPos> laid = new ArrayList<>();
				for (int k = 0; k < length; k++) {
					BlockPos mid = start.relative(way, k);
					boolean wetHere = true;
					for (int w = -half; w <= half; w++) {
						BlockPos column = mid.relative(across, w);
						wetHere &= wet(this.level, column.getX(), column.getZ());
					}

					if (!wetHere || !this.inReach(mid.getX(), mid.getZ(), 2)) {
						break;
					}

					for (int w = -half; w <= half; w++) {
						BlockPos plank = mid.relative(across, w).atY(deck);
						set(this.level, plank, wood("planks").defaultBlockState());
						for (int y = 1; y <= 3; y++) {
							if (!this.level.getBlockState(plank.above(y)).isAir()) {
								set(this.level, plank.above(y), Blocks.AIR.defaultBlockState());
							}
						}

						this.pathCells.add(BlockPos.asLong(plank.getX(), 0, plank.getZ()));
						// Piles: a log down to the bottom under its edges every few blocks.
						if (k % 3 == 2 && (w == -half || w == half)) {
							for (BlockPos pile = plank.below(); pile.getY() > deck - 24 && !this.level.getFluidState(pile).isEmpty(); pile = pile.below()) {
								set(this.level, pile, wood("log").defaultBlockState());
							}
						}

						// Railings along a wide pier's sides, its walk between them.
						if (half > 0 && w != 0 && k % 2 == 1) {
							place(this.level, plank.above(), wood("fence").defaultBlockState());
						}
					}

					laid.add(mid.atY(deck));
				}

				if (laid.size() < 3) {
					continue;
				}

				BlockPos end = laid.get(laid.size() - 1).relative(across, half > 0 ? half : 0);
				place(this.level, end.above(), wood("fence").defaultBlockState());
				place(this.level, end.above(2), Blocks.LANTERN.defaultBlockState());
				if (this.shape == Shape.HARBOUR) {
					var boat = EntityType.BOAT.create(this.level);
					BlockPos beside = laid.get(laid.size() - 1).relative(across, half + 2);
					if (boat != null && wet(this.level, beside.getX(), beside.getZ())) {
						boat.moveTo(beside.getX() + 0.5, deck - 0.5, beside.getZ() + 0.5, way.toYRot(), 0);
						this.level.addFreshEntity(boat);
					}
				}

				return true;
			}

			return false;
		}

		/** A hill town's lookout: a little tower of logs and planks with a ladder up to a railed floor, a lantern on each corner. */
		private void lookoutTower() {
			Rect spot = this.lookout;
			if (spot == null) {
				return;
			}

			int x0 = spot.x0() + 1;
			int z0 = spot.z0() + 1;
			int h = ground(this.level, x0 + 1, z0 + 1);
			for (int dx = 0; dx <= 2; dx++) {
				for (int dz = 0; dz <= 2; dz++) {
					boolean corner = (dx == 0 || dx == 2) && (dz == 0 || dz == 2);
					for (int y = h + 1; y <= h + 6; y++) {
						if (corner) {
							set(this.level, new BlockPos(x0 + dx, y, z0 + dz), wood("log").defaultBlockState());
						}
					}

					if (!(dx == 1 && dz == 1)) {
						set(this.level, new BlockPos(x0 + dx, h + 5, z0 + dz), wood("planks").defaultBlockState());
					}

					if (!corner && !(dx == 1 && dz == 1)) {
						place(this.level, new BlockPos(x0 + dx, h + 6, z0 + dz), wood("fence").defaultBlockState());
					}

					if (corner) {
						place(this.level, new BlockPos(x0 + dx, h + 7, z0 + dz), Blocks.LANTERN.defaultBlockState());
					}
				}
			}

			// The ladder's wall (the south side's middle), the ladder against it up through the floor.
			for (int y = h + 1; y <= h + 4; y++) {
				set(this.level, new BlockPos(x0 + 1, y, z0 + 2), wood("planks").defaultBlockState());
			}

			for (int y = h + 1; y <= h + 5; y++) {
				place(this.level, new BlockPos(x0 + 1, y, z0 + 1), Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING,
					Direction.NORTH));
			}

			set(this.level, new BlockPos(x0 + 1, h + 6, z0 + 2), wood("planks").defaultBlockState());
		}

		private void addRoad(Road road) {
			this.roads.add(road);
			this.roadCells.addAll(road.cells.keySet());
		}

		/** Inside the town's reach, with this much to spare. */
		private boolean inReach(int x, int z, int spare) {
			return Math.abs(x - this.cx) <= reach - spare && Math.abs(z - this.cz) <= reach - spare;
		}

		private boolean inReach(Rect rect) {
			return this.inReach(rect.x0(), rect.z0(), 0) && this.inReach(rect.x1(), rect.z1(), 0);
		}

		/** A road within this many blocks of x, z. */
		private boolean nearRoad(int x, int z, int by) {
			for (int dx = -by; dx <= by; dx++) {
				for (int dz = -by; dz <= by; dz++) {
					if (this.roadCells.contains(BlockPos.asLong(x + dx, 0, z + dz))) {
						return true;
					}
				}
			}

			return false;
		}

		/** A road within this many blocks of the rectangle. */
		private boolean nearRoad(Rect rect, int by) {
			for (int x = rect.x0() - by; x <= rect.x1() + by; x++) {
				for (int z = rect.z0() - by; z <= rect.z1() + by; z++) {
					if (this.roadCells.contains(BlockPos.asLong(x, 0, z))) {
						return true;
					}
				}
			}

			return false;
		}

		/** On the town's ground by its roads (lit at night). */
		private boolean byRoad(int x, int z) {
			if (this.roadGround == null) {
				this.roadGround = TownRoads.around(this.roadCells.stream().mapToLong(Long::longValue).toArray(), TOWN_GROUND);
			}

			return this.roadGround.contains(BlockPos.asLong(x, 0, z));
		}

		/** Steps out from the middle of this side of the rectangle to a road or the plaza, or {@link Integer#MAX_VALUE} past {@code most}. */
		private int toRoad(Rect rect, Direction side, int most) {
			int x = side == Direction.EAST ? rect.x1() : side == Direction.WEST ? rect.x0() : (rect.x0() + rect.x1()) / 2;
			int z = side == Direction.SOUTH ? rect.z1() : side == Direction.NORTH ? rect.z0() : (rect.z0() + rect.z1()) / 2;
			for (int step = 1; step <= most; step++) {
				int ax = x + side.getStepX() * step;
				int az = z + side.getStepZ() * step;
				if (this.roadCells.contains(BlockPos.asLong(ax, 0, az)) || this.plaza.contains(ax, az)) {
					return step;
				}
			}

			return Integer.MAX_VALUE;
		}

		/** Water anywhere in it (a field with the river in a corner of it was walked to from no side: found in a grid town). */
		private boolean anyWet(Rect rect) {
			for (int x = rect.x0(); x <= rect.x1(); x++) {
				for (int z = rect.z0(); z <= rect.z1(); z++) {
					if (wet(this.level, x, z)) {
						return true;
					}
				}
			}

			return false;
		}

		/** A spot for the store, a field or the pen, the side it faces (the nearest road or the plaza), its ground's height. */
		private record Lot(Rect site, Direction front, @Nullable Integer base) {
		}

		/**
		 * The best spot this size within {@code most} of {@code near}'s middle: off the roads, clear of what's there, on ground
		 * that suits it ({@code flat}: or none) and facing a road or the plaza a few blocks off ({@code facing}: or none), the
		 * nearer the better. Null if there's none.
		 */
		@Nullable
		private Lot lot(int w, int d, Rect near, int most, boolean flat, boolean facing) {
			int mx = (near.x0() + near.x1()) / 2;
			int mz = (near.z0() + near.z1()) / 2;
			List<int[]> spots = new ArrayList<>();
			for (int ox = -most; ox <= most; ox += 2) {
				for (int oz = -most; oz <= most; oz += 2) {
					spots.add(new int[] {ox, oz});
				}
			}

			spots.sort(java.util.Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
			Lot best = null;
			double bestScore = Double.MAX_VALUE;
			int looked = 0;
			for (int[] o : spots) {
				Rect site = new Rect(mx + o[0] - w / 2, mz + o[1] - d / 2, mx + o[0] - w / 2 + w - 1, mz + o[1] - d / 2 + d - 1);
				if (!this.inReach(site) || this.taken.stream().anyMatch(other -> other.near(site, 3)) || this.nearRoad(site, 2)) {
					continue;
				}

				Integer base = siteBase(this.level, site.x0(), site.z0(), site.x1(), site.z1());
				if (base == null && (flat || this.anyWet(site))) {
					continue;
				}

				Direction front = null;
				int away = Integer.MAX_VALUE;
				for (Direction side : Direction.Plane.HORIZONTAL) {
					int steps = this.toRoad(site, side, 10);
					if (steps < away) {
						away = steps;
						front = side;
					}
				}

				if (front == null && facing) {
					continue;
				}

				// (Its door's path climbs a block a step at most: no more above or below its road than it's steps from it. Found in a
				// city: a store three blocks over its road, its path a step too steep to walk up.)
				int rise = 0;
				if (front != null && base != null) {
					int ax = (front == Direction.EAST ? site.x1() : front == Direction.WEST ? site.x0() : (site.x0() + site.x1()) / 2) + front.getStepX() * away;
					int az = (front == Direction.SOUTH ? site.z1() : front == Direction.NORTH ? site.z0() : (site.z0() + site.z1()) / 2) + front.getStepZ() * away;
					rise = Math.abs(ground(this.level, ax, az) - base);
					if (facing && rise > away) {
						continue;
					}
				}

				double score = Math.sqrt(o[0] * o[0] + o[1] * o[1]) + (front != null ? away : 12) + 2 * rise + (base == null ? 20 : 0);
				if (score < bestScore) {
					bestScore = score;
					best = new Lot(site, front != null ? front : Direction.NORTH, base);
				}

				// (The nearest few that suit: enough to pick well from, without trying the whole town.)
				if (++looked >= 24) {
					break;
				}
			}

			return best;
		}

		/** The store by the plaza, the two fields together and the pen near it, where there's room off its roads. */
		private void lots() {
			int cx = this.cx;
			int cz = this.cz;
			Lot store = this.lot(9, 9, this.plaza, 40, true, true);
			if (store == null) {
				store = this.lot(9, 9, this.plaza, 48, false, false);
			}

			Rect storeSite = store != null ? store.site() : new Rect(cx + 4, cz - 28, cx + 12, cz - 20);
			this.storeFront = store != null ? store.front() : Direction.WEST;
			this.storeBase = store != null && store.base() != null ? store.base() : median(this.level, storeSite.x0(), storeSite.z0(), storeSite.x1(),
				storeSite.z1());
			this.storeAt = new BlockPos(storeSite.x0(), this.storeBase + 1, storeSite.z0());
			// (A block round it at its height, a doorstep; the land beyond eased to it.)
			this.storeRect = new Rect(storeSite.x0() - 1, storeSite.z0() - 1, storeSite.x1() + 1, storeSite.z1() + 1);
			this.taken.add(this.storeRect);
			List<Rect> fields = new ArrayList<>();
			Lot first = this.lot(9, 9, this.plaza, 44, false, false);
			if (first != null) {
				fields.add(first.site());
				this.taken.add(first.site());
				Lot second = this.lot(9, 9, first.site(), 20, false, false);
				if (second != null) {
					fields.add(second.site());
					this.taken.add(second.site());
				}
			}

			if (fields.isEmpty()) {
				fields.add(new Rect(cx + 3, cz + 12, cx + 11, cz + 20));
				this.taken.add(fields.get(0));
			}

			this.fieldRects = List.copyOf(fields);
			Lot pen = this.lot(11, 11, this.plaza, 44, false, false);
			this.penRect = pen != null ? pen.site() : new Rect(cx - 13, cz + 12, cx - 3, cz + 22);
			this.penFront = pen != null ? pen.front() : Direction.EAST;
			this.taken.add(this.penRect);
			if (this.shape == Shape.HILL) {
				this.lookout = this.highestSpot();
				if (this.lookout != null) {
					this.taken.add(this.lookout);
				}
			}

			// Side roads off the main ones, out into the land (a village's lanes): a few tries a step (a city's took 2.6 s at once).
			this.then("streets", this::streets);
		}

		private int streetTries;

		/** The highest free 5 by 5 spot within 32 of the plaza (a hill town's lookout), on ground that suits it. */
		@Nullable
		private Rect highestSpot() {
			Rect best = null;
			int bestHeight = Integer.MIN_VALUE;
			for (int dx = -32; dx <= 32; dx += 2) {
				for (int dz = -32; dz <= 32; dz += 2) {
					Rect spot = new Rect(this.cx + dx - 2, this.cz + dz - 2, this.cx + dx + 2, this.cz + dz + 2);
					if (!this.inReach(spot) || this.taken.stream().anyMatch(other -> other.near(spot, 3)) || this.nearRoad(spot, 2)) {
						continue;
					}

					Integer base = siteBase(this.level, spot.x0(), spot.z0(), spot.x1(), spot.z1());
					if (base != null && base > bestHeight) {
						bestHeight = base;
						best = spot;
					}
				}
			}

			return best;
		}

		/** How many side roads it wants to start with (more come if its houses don't fit). */
		private int lanesWanted() {
			return switch (this.shape) {
				case CROSSROADS -> 2 + this.households / 4;
				case MAIN_STREET -> this.households / 6;
				case WINDING, HILL -> 1 + this.households / 4;
				case RIVER, HARBOUR -> 1 + this.households / 5;
				case RING -> this.households / 6;
				case GRID -> this.city ? 2 + this.households / 4 : 1;
			};
		}

		private void streets() {
			int want = this.lanesWanted();
			this.branches(this.taken, want, 6);
			this.streetTries += 6;
			if (this.roads.size() < this.mains + want && this.streetTries < 32 + 4 * want) {
				this.steps.addFirst(new Step("streets", this::streets));
				return;
			}

			int cx = this.cx;
			int cz = this.cz;
			this.y0 = median(this.level, cx - PLAZA, cz - PLAZA, cx + PLAZA, cz + PLAZA) + 1;
			this.look = Style.at(this.level, new BlockPos(cx, this.y0, cz));
			style = this.look;
			DrifterCivilizations.LOGGER.info("[town] {}: a {} town, {} roads", this.name, this.look.name(), this.roads.size());
			this.then("houses", this::houses);
		}

		/**
		 * Up to {@code want} side roads in all (beyond the main ones), off a main road (a city's and a winding town's: off any
		 * road), out into the land, following it as the main roads do, clear of {@code busy} and of each other.
		 */
		private void branches(List<Rect> busy, int want, int most) {
			boolean anyRoad = this.city || this.shape == Shape.WINDING || this.shape == Shape.RING;
			int shortest = this.shape == Shape.MAIN_STREET ? 16 : SHORTEST_BRANCH;
			int longest = (this.shape == Shape.MAIN_STREET ? 30 : LONGEST_BRANCH) * (this.city ? 2 : 1);
			for (int tries = 0; tries < most && this.roads.size() < this.mains + want; tries++) {
				int pick = anyRoad ? this.roads.size() : this.mains;
				if (pick == 0) {
					return;
				}

				Road from = this.roads.get(this.random.nextInt(pick));
				if (from.length() < 24) {
					continue;
				}

				int t = 12 + this.random.nextInt(Math.max(1, from.length() - 20));
				double way = from.heading(t) + (this.random.nextBoolean() ? Math.PI / 2 : -Math.PI / 2);
				int length = shortest + this.random.nextInt(longest - shortest + 1);
				Road lane = this.lay(from.xs[t] + Math.cos(way) * 2, from.zs[t] + Math.sin(way) * 2, way, length, busy, 5);
				if (lane.length() >= shortest / 2 && this.apart(lane, from)) {
					this.addRoad(lane);
				}
			}
		}

		/** A side road far enough from the others, but the one it leaves (where it ends at one, its end aside). */
		private boolean apart(Road lane, Road from) {
			for (int i = this.mains; i < this.roads.size(); i++) {
				Road other = this.roads.get(i);
				if (other == from || !other.bounds.near(lane.bounds, LANES_APART)) {
					continue;
				}

				for (int t = 0; t < lane.length() - 6; t += 2) {
					for (int u = 0; u < other.length(); u += 2) {
						double dx = lane.xs[t] - other.xs[u];
						double dz = lane.zs[t] - other.zs[u];
						if (dx * dx + dz * dz < LANES_APART * LANES_APART) {
							return false;
						}
					}
				}
			}

			return true;
		}

		/** The store, on its ground (shaped by then), its door to its road or the plaza. */
		private void store() {
			this.store = TownBuilder.store(this.level, this.storeAt, this.storeFront);
		}

		/** One of the shared farm's two fields, on its ground at this height (shaped by then). */
		private void field(Rect rect, int fieldBase) {
			BlockPos field = new BlockPos((rect.x0() + rect.x1()) / 2, fieldBase, (rect.z0() + rect.z1()) / 2);
			this.fields.add(field.above());
			if (this.ruin) {
				for (int dx = -4; dx <= 4; dx++) {
					for (int dz = -4; dz <= 4; dz++) {
						set(this.level, field.offset(dx, 0, dz), (this.random.nextFloat() < 0.4F ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK).defaultBlockState());
						if (this.random.nextFloat() < 0.3F) {
							set(this.level, field.offset(dx, 1, dz), Blocks.SHORT_GRASS.defaultBlockState());
						}
					}
				}

				return;
			}

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
			penFence(this.level, this.pen, 5, this.penFront);
			if (this.ruin) {
				for (BlockPos pos : BlockPos.betweenClosed(this.pen.offset(-6, -2, -6), this.pen.offset(6, 4, 6))) {
					if (this.level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FENCES) && this.random.nextFloat() < 0.35F) {
						set(this.level, pos.immutable(), Blocks.AIR.defaultBlockState());
					}
				}

				return;
			}

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
					if (this.round >= 4 || this.pending.size() >= this.households) {
						this.housesDone();
						return;
					}

					if (this.round > 0) {
						List<Rect> busy = new ArrayList<>(this.taken);
						busy.addAll(this.houseRects);
						int more = this.roads.size() - this.mains + 2;
						this.branches(busy, more, 32 + 4 * more);
					}

					for (int i = this.sides.size() / 2; i < this.roads.size(); i++) {
						Road road = this.roads.get(i);
						int startAt = i < this.mains ? 2 : 4;
						this.sides.add(new Side(road, 1, startAt));
						this.sides.add(new Side(road, -1, startAt));
					}

					this.roundOpen = true;
				}

				if (this.pending.size() >= this.households) {
					this.housesDone();
					return;
				}

				Side side = this.trying != null ? this.trying : this.sides.stream().filter(s -> !s.full).min(java.util.Comparator.comparingDouble(s -> {
					BlockPos at = s.road.at(s.cursor);
					return Math.hypot(at.getX() - this.cx, at.getZ() - this.cz);
				})).orElse(null);
				if (side == null) {
					this.roundOpen = false;
					continue;
				}

				if (this.trySpot(side) || this.trying != null) {
					break;
				}
			}

			this.steps.addFirst(new Step("houses", this::houses));
		}

		/** A house at this side's next spot if one fits there (true: one went up); the side moved on either way. */
		/** A spot whose plans were being tried when the step's time ran out, how far through them, and its setback. */
		@Nullable
		private Side trying;
		private int triedPlans;
		/** The houses' spots chosen, to be put up once the ground's shaped. */
		private final List<Pending> pending = new ArrayList<>();
		private int tryingSetback;
		/** A spot's plans tried for this long a step at most (a city's spots took 400 ms going through every plan). */
		private static final long SPOT_NANOS = 15_000_000L;

		private boolean trySpot(Side side) {
			boolean chosen = false;
			Road road = side.road;
			// The way out from the road over this stretch of it, and the straight way nearest it (houses stand square).
			double outWay = side.out(Math.min(road.length() - 1, side.cursor + 6));
			double ox = Math.cos(outWay);
			double oz = Math.sin(outWay);
			Direction out = Direction.getNearest(ox, 0, oz);
			Direction front = out.getOpposite();
			int setback = this.trying == side ? this.tryingSetback : LEAST_SETBACK + this.random.nextInt(MOST_SETBACK - LEAST_SETBACK + 1);
			int from = this.trying == side ? this.triedPlans : 0;
			this.trying = null;
			long start = System.nanoTime();
			for (int tries = from; tries < this.plans.size() && !chosen; tries++) {
				if (System.nanoTime() - start > SPOT_NANOS) {
					// (On next step, from here.)
					this.trying = side;
					this.triedPlans = tries;
					this.tryingSetback = setback;
					return false;
				}

				DrifterApi.HousePlanInfo plan = this.plans.get((this.planIndex + tries) % this.plans.size());
				Rotation rotation = turnFor(this.server, plan.id(), front);
				if (rotation == null) {
					continue;
				}

				boolean turned = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
				int sx = turned ? plan.sizeZ() : plan.sizeX();
				int sz = turned ? plan.sizeX() : plan.sizeZ();
				boolean outX = out.getAxis() == Direction.Axis.X;
				int along = outX ? sz : sx;
				int depth = outX ? sx : sz;
				if (side.cursor + along > road.length()) {
					continue;
				}

				// Its front's middle this far out from the road's middle line, a little further where a bend comes close.
				int mid = side.cursor + along / 2;
				Rect site = null;
				for (int push = 0; push <= 4 && site == null; push++) {
					double d = HALF_WIDTH + 0.5 + setback + push;
					Rect tried = footprint(Mth.floor(road.xs[mid] + ox * d), Mth.floor(road.zs[mid] + oz * d), out, along, depth);
					if (!this.nearRoad(tried, LEAST_SETBACK - 1)) {
						site = tried;
					}
				}

				if (site == null) {
					this.turnedDown[1]++;
					continue;
				}

				Rect at = site;
				int why = !this.inReach(at) ? 0 : this.taken.stream().anyMatch(other -> other.near(at, HOUSE_GAP)) ? 2
					: this.houseRects.stream().anyMatch(other -> other.near(at, HOUSE_GAP)) ? 3 : -1;
				Integer base = why >= 0 ? null : siteBase(this.level, at.x0(), at.z0(), at.x1(), at.z1());
				if (base == null) {
					this.turnedDown[why >= 0 ? why : 4]++;
					continue;
				}

				// Chosen: put up once the town's ground is shaped round all of them (W2).
				this.pending.add(new Pending(at, base, plan, rotation, front, sx, sz));
				this.houseRects.add(at);
				chosen = true;
				this.planIndex = (this.planIndex + tries + 1) % this.plans.size();
				// On along the road as far as the house reaches along it (further where the road runs at a slant to it).
				double slant = Math.abs(outX ? Math.sin(road.heading(mid)) : Math.cos(road.heading(mid)));
				side.cursor += (int)Math.ceil(along / Math.max(0.6, slant)) + HOUSE_GAP + this.random.nextInt(MORE_GAP + 1);
			}

			if (!chosen) {
				side.cursor += SKIP;
			}

			side.full = side.cursor >= road.length() - 4;
			return chosen;
		}

		/**
		 * Its roads cut back to what they lead to (in testing, a hamlet's roads ran on into the wild past its last house): each
		 * as far as its last house, the store, a field or the pen, or a side road that's kept, and a few blocks more; a side
		 * road that leads to nothing, gone; a road out of the plaza, a stub at least.
		 */
		private void trimRoads() {
			List<Rect> places = new ArrayList<>(this.houseRects);
			this.taken.stream().filter(rect -> rect != this.plaza).forEach(places::add);
			int n = this.roads.size();
			int[] need = new int[n];
			for (int i = 0; i < n; i++) {
				Road road = this.roads.get(i);
				need[i] = road.loop ? road.length() - 1 : i < this.mains ? Math.min(road.length() - 1, ROAD_STUB) : -1;
				for (int t = 0; t < road.length(); t++) {
					int x = Mth.floor(road.xs[t]);
					int z = Mth.floor(road.zs[t]);
					if (t > need[i] && places.stream().anyMatch(rect -> rect.away(x, z) <= LEADS_TO)) {
						need[i] = t;
					}
				}
			}

			// A side road that's kept keeps the road it leaves as far as where it leaves it, and one it runs into as far as that.
			for (boolean more = true; more; ) {
				more = false;
				for (int i = this.mains; i < n; i++) {
					Road lane = this.roads.get(i);
					for (int end : need[i] < 0 ? new int[0] : new int[] {0, need[i]}) {
						for (int j = 0; j < n; j++) {
							int t = j == i ? -1 : this.roads.get(j).nearest(lane.xs[end], lane.zs[end], 4);
							if (t > need[j]) {
								need[j] = t;
								more = true;
							}
						}
					}
				}
			}

			List<Road> kept = new ArrayList<>();
			int keptMains = 0;
			for (int i = 0; i < n; i++) {
				if (need[i] >= 0) {
					kept.add(this.roads.get(i).upTo(need[i] + 1 + ROAD_BEYOND));
					keptMains += i < this.mains ? 1 : 0;
				}
			}

			DrifterCivilizations.LOGGER.info("[town] {}: {} of {} roads kept (cut back to what they lead to)", this.name, kept.size(), n);
			this.roads.clear();
			this.roadCells.clear();
			kept.forEach(this::addRoad);
			this.mains = keptMains;
		}

		private void housesDone() {
			DrifterCivilizations.LOGGER.info("[town] {} of {} houses on {} roads; spots turned down: {} out of the town, {} by a road, {} by the store or fields, {} by a house, {} for the ground",
				this.pending.size(), this.households, this.roads.size(), this.turnedDown[0], this.turnedDown[1], this.turnedDown[2], this.turnedDown[3],
				this.turnedDown[4]);
			this.trimRoads();
			this.built.addAll(this.taken);
			this.built.addAll(this.houseRects);
			// The ground shaped once for all of it (W2), a piece a step; then everything put up on it.
			this.ground = this.groundPlan();
			for (var piece : this.ground.pieces(GROUND_PIECE)) {
				this.then("ground", () -> this.ground.apply(piece));
			}

			this.then("store", this::store);
			for (int i = 0; i < this.fieldRects.size(); i++) {
				Rect rect = this.fieldRects.get(i);
				int base = this.fieldBases.get(i);
				this.then("field", () -> this.field(rect, base));
			}

			this.then("pen", this::pen);
			for (Pending spot : this.pending) {
				this.then("house", () -> this.build(spot));
			}

			for (Road road : this.roads) {
				this.then("road", () -> this.road(road));
			}

			if (!this.banks.isEmpty() && !this.ruin) {
				this.then("waterfront", this::waterfront);
			}

			if (this.lookout != null && !this.ruin) {
				this.then("lookout", this::lookoutTower);
			}

			this.then("plazaThings", this::plazaThings);
			// (The paths before the lamps, so no lamp post stands on one: one in front of a door blocked the way in.)
			this.then("stallAndPaths", this::stallAndPaths);
			if (!this.ruin) {
				this.then("roadLamps", this::roadLamps);
				this.then("decorations", this::decorations);
			}

			this.then("areaAndTrees", this::areaAndTrees);
		}

		/** A house's spot, chosen: its ground, its height, its plan, turned for its front to face the road. */
		private record Pending(Rect site, int base, DrifterApi.HousePlanInfo plan, Rotation rotation, Direction front, int sx, int sz) {
		}

		private GroundPlan ground;
		/** Its bridges' decks: each column's height. */
		private final java.util.Map<Long, Integer> bridges = new java.util.HashMap<>();
		private final List<Integer> fieldBases = new ArrayList<>();

		/**
		 * Its ground's plan (W2): the plaza, the store, the fields and each house held at their heights, the land eased to them;
		 * its roads at grades a player walks, worked out on the ground as the rest leaves it (built up over dips a block a step).
		 */
		private GroundPlan groundPlan() {
			List<GroundPlan.Pad> pads = new ArrayList<>();
			pads.add(new GroundPlan.Pad(this.plaza, this.y0 - 1, PAD_REACH, CLEAR_ABOVE));
			pads.add(new GroundPlan.Pad(this.storeRect, this.storeBase, PAD_REACH, 8));
			// (The pen level too: on a hillside its fence ran down a staircase of cut rock, found in a hill town.)
			pads.add(new GroundPlan.Pad(this.penRect, median(this.level, this.penRect.x0(), this.penRect.z0(), this.penRect.x1(), this.penRect.z1()), PAD_REACH, 3));
			if (this.lookout != null) {
				pads.add(new GroundPlan.Pad(this.lookout, median(this.level, this.lookout.x0(), this.lookout.z0(), this.lookout.x1(), this.lookout.z1()), PAD_REACH,
					9));
			}
			for (Rect rect : this.fieldRects) {
				int base = median(this.level, rect.x0(), rect.z0(), rect.x1(), rect.z1());
				this.fieldBases.add(base);
				pads.add(new GroundPlan.Pad(rect, base, PAD_REACH, 4));
			}

			List<GroundPlan.Pad> others = List.copyOf(pads);
			for (Pending spot : this.pending) {
				pads.add(new GroundPlan.Pad(spot.site(), spot.base(), PAD_REACH, spot.plan().sizeY() + 2));
			}

			GroundPlan plan = new GroundPlan(this.level, pads, this.random, this.look == Style.DESERT);
			// Its roads' heights, all of them together (each worked out on its own, a high street and a low one crossing on a
			// mountain stood a wall apart: found in a mountain city): from the ground as the rest leaves it (a bridge's a block
			// over the water at least, level with the plaza where they leave it), then a block a step at most between any two
			// side by side, halfway between built up over dips and cut down through bumps (so a road down a mountain is cut in
			// at the top and built up at the bottom, not one long embankment).
			java.util.Map<Long, Integer> base = new java.util.HashMap<>();
			java.util.Set<Long> level = new java.util.HashSet<>();
			for (Road road : this.roads) {
				for (long cell : road.cells.keySet()) {
					int x = BlockPos.getX(cell);
					int z = BlockPos.getZ(cell);
					if (this.plaza.contains(x, z)) {
						continue;
					}

					int b = plan.wet(x, z) ? plan.natural(x, z) + 1 : plan.base(x, z);
					if (this.plaza.away(x, z) <= 1) {
						b = this.y0 - 1;
						level.add(cell);
					}

					base.merge(cell, b, Math::max);
				}
			}

			java.util.Map<Long, Integer> high = settle(base, true, level);
			java.util.Map<Long, Integer> low = settle(base, false, level);
			java.util.Map<Long, Integer> wants = new java.util.HashMap<>();
			for (long cell : base.keySet()) {
				wants.put(cell, level.contains(cell) ? this.y0 - 1 : Math.floorDiv(high.get(cell) + low.get(cell), 2));
			}

			// Each house at its street's height (within a block or two): its door path climbs a block a step at most, and with
			// streets cut into a hill a house on the hill's own ground stood ten blocks under its street (found in a hill town).
			List<GroundPlan.Pad> rebased = new ArrayList<>(others);
			for (int i = 0; i < this.pending.size(); i++) {
				Pending spot = this.pending.get(i);
				Integer street = this.streetAt(spot, wants);
				int at = street == null ? spot.base() : Mth.clamp(spot.base(), street - 2, street + 1);
				if (at != spot.base()) {
					spot = new Pending(spot.site(), at, spot.plan(), spot.rotation(), spot.front(), spot.sx(), spot.sz());
					this.pending.set(i, spot);
				}

				rebased.add(new GroundPlan.Pad(spot.site(), spot.base(), PAD_REACH, spot.plan().sizeY() + 2));
			}

			plan = new GroundPlan(this.level, rebased, this.random, this.look == Style.DESERT);
			for (long cell : base.keySet()) {
				int x = BlockPos.getX(cell);
				int z = BlockPos.getZ(cell);
				int want = wants.get(cell);
				if (plan.wet(x, z)) {
					// A bridge: its deck at the road's height, a block over the water at least (it ramps down to the water from
					// a high bank; at the water's own level, the plaza's bank stood over its end as a wall: found in a river town).
					this.bridges.merge(cell, Math.max(want, plan.natural(x, z) + 1), Math::max);
				} else {
					plan.road(cell, want);
				}
			}

			return plan;
		}

		/**
		 * Heights over these columns with a block a step at most between any two side by side: {@code up}, each no lower than
		 * its own and built up from its neighbours (the least filling that does it); else no higher, cut down (the least
		 * cutting). Those in {@code fixed} stay as they are (the roads level with the plaza where they leave it: moved, they
		 * left the plaza a step off its own roads, found in a city walked to from nowhere).
		 */
		private static java.util.Map<Long, Integer> settle(java.util.Map<Long, Integer> base, boolean up, java.util.Set<Long> fixed) {
			java.util.Map<Long, Integer> heights = new java.util.HashMap<>(base);
			java.util.PriorityQueue<long[]> queue = new java.util.PriorityQueue<>((a, b) -> up ? Long.compare(b[1], a[1]) : Long.compare(a[1], b[1]));
			base.forEach((cell, height) -> queue.add(new long[] {cell, height}));
			while (!queue.isEmpty()) {
				long[] next = queue.poll();
				int height = (int)next[1];
				if (heights.get(next[0]) != height) {
					continue;
				}

				int x = BlockPos.getX(next[0]);
				int z = BlockPos.getZ(next[0]);
				for (Direction side : Direction.Plane.HORIZONTAL) {
					long other = BlockPos.asLong(x + side.getStepX(), 0, z + side.getStepZ());
					Integer there = heights.get(other);
					int bound = up ? height - 1 : height + 1;
					if (there != null && !fixed.contains(other) && (up ? there < bound : there > bound)) {
						heights.put(other, bound);
						queue.add(new long[] {other, bound});
					}
				}
			}

			return heights;
		}

		/** The height of the street in front of this house (out from the middle of its front, twelve blocks at most), or null. */
		@Nullable
		private Integer streetAt(Pending spot, java.util.Map<Long, Integer> wants) {
			Rect site = spot.site();
			Direction front = spot.front();
			int x = front == Direction.EAST ? site.x1() : front == Direction.WEST ? site.x0() : (site.x0() + site.x1()) / 2;
			int z = front == Direction.SOUTH ? site.z1() : front == Direction.NORTH ? site.z0() : (site.z0() + site.z1()) / 2;
			for (int step = 1; step <= 12; step++) {
				Integer street = wants.get(BlockPos.asLong(x + front.getStepX() * step, 0, z + front.getStepZ() * step));
				if (street != null) {
					return street;
				}
			}

			return null;
		}

		/** A house put up on its spot (its ground shaped by now). */
		private void build(Pending spot) {
			BlockPos origin = new BlockPos(spot.site().x0(), spot.plan().groundLayer() ? spot.base() : spot.base() + 1, spot.site().z0());
			DrifterApi.PlacedHouse house = DrifterApi.placeHouse(this.level, spot.plan().id(), origin, spot.rotation(), this.look.pick(this.random),
				this.look.stone(), spot.front());
			if (house == null) {
				return;
			}

			changed(house.box());
			Town.Plot made = new Town.Plot(origin, spot.sx(), spot.sz(), spot.plan().id(), spot.rotation(), spot.front(), null);
			made.built = true;
			this.plots.add(made);
			this.houses.add(house);
			DrifterCivilizations.LOGGER.info("[town] {} at {}, its door at {}", spot.plan().id(), house.box(), house.door());
		}

		/**
		 * Its roads' blocks (W1; the user, 2026-10-30: "when the roads are mixed, it should be of the same type, like stone
		 * texturing for cities, and planks for towns, just path blocks for hamlets"): a city's stone bricks (some cracked, some
		 * mossy; sandstone in the desert), a town's planks of its own wood, a hamlet's dirt path.
		 */
		private BlockState paving() {
			float roll = this.random.nextFloat();
			if (this.city) {
				return (this.look == Style.DESERT ? roll < 0.7F ? Blocks.SMOOTH_SANDSTONE : Blocks.CUT_SANDSTONE
					: roll < 0.7F ? Blocks.STONE_BRICKS : roll < 0.85F ? Blocks.CRACKED_STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS).defaultBlockState();
			}

			return this.households > HAMLET ? wood("planks").defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
		}

		/**
		 * The slab where its roads and paths climb, half a block between two levels: a city's stone brick (sandstone), a town's
		 * wood, a hamlet's path slab if Terrain Slabs is installed (its wood's otherwise).
		 */
		private BlockState pavingSlab() {
			if (this.city) {
				return (this.look == Style.DESERT ? Blocks.SMOOTH_SANDSTONE_SLAB : Blocks.STONE_BRICK_SLAB).defaultBlockState();
			}

			if (this.households <= HAMLET) {
				var pathSlab = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
					"terrain_slabs", "path_slab"));
				if (pathSlab.isPresent()) {
					return pathSlab.get().defaultBlockState();
				}
			}

			return wood("slab").defaultBlockState();
		}

		/** The stairs up a road or path that climbs a block every block: a city's stone brick (sandstone), the wood's elsewhere. */
		private BlockState pavingStairs() {
			return this.city ? (this.look == Style.DESERT ? Blocks.SMOOTH_SANDSTONE_STAIRS : Blocks.STONE_BRICK_STAIRS).defaultBlockState()
				: wood("stairs").defaultBlockState();
		}

		/** One of the town's lamp posts at {@code feet}: its village's own (W1), else a fence post and a lantern. */
		@Nullable
		private BlockPos lampAt(BlockPos feet) {
			BlockPos light = VillageBits.lamp(this.level, feet, this.look.name(), this.random);
			if (light != null) {
				changed(feet.getX(), feet.getZ());
				return light;
			}

			return lamp(this.level, feet) ? feet.above(2) : null;
		}

		/**
		 * The taiga village's street bits about the town (the user: "add the taiga decorations too"): in front yards beside a
		 * third of the door paths, and one or two on the plaza; each only where it fits, off the roads and paths.
		 */
		private void decorations() {
			int made = 0;
			for (int i = 0; i < this.houses.size(); i++) {
				var house = this.houses.get(i);
				if (house.door() == null || this.random.nextFloat() > 0.35F) {
					continue;
				}

				Direction front = this.plots.get(i).front;
				BlockPos step = house.door();
				while (house.box().isInside(new BlockPos(step.getX(), house.box().minY(), step.getZ()))) {
					step = step.relative(front);
				}

				Direction aside = this.random.nextBoolean() ? front.getClockWise() : front.getCounterClockWise();
				BlockPos spot = step.relative(front, 1).relative(aside, 4);
				made += this.decoration(spot) ? 1 : 0;
			}

			int[][] plazaSpots = {{-4, 2}, {3, -3}, {-2, -4}};
			for (int[] spot : plazaSpots) {
				if (this.random.nextFloat() < 0.5F) {
					made += this.decoration(new BlockPos(this.cx + spot[0], 0, this.cz + spot[1])) ? 1 : 0;
				}
			}

			DrifterCivilizations.LOGGER.info("[town] {}: {} street decorations", this.name, made);
		}

		private boolean decoration(BlockPos column) {
			int x = column.getX();
			int z = column.getZ();
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					long key = BlockPos.asLong(x + dx, 0, z + dz);
					if (this.roadCells.contains(key) || this.pathCells.contains(key)) {
						return false;
					}
				}
			}

			var box = VillageBits.decoration(this.level, onGround(this.level, x, z), this.random, this.look.main(), this.look == Style.DESERT);
			if (box != null) {
				changed(box);
			}

			return box != null;
		}

		// -----------------------------------------------------------------------------------------
		// 3. The roads over the ground as it is now, the plaza paved, the well, bell and board's spot, the stall and the paths
		// to the doors, lamps along the roads.
		// -----------------------------------------------------------------------------------------

		private void road(Road road) {
			for (long cell : road.cells.keySet()) {
				int x = BlockPos.getX(cell);
				int z = BlockPos.getZ(cell);
				Integer deck = this.bridges.get(cell);
				if (deck != null) {
					BlockPos plank = new BlockPos(x, deck, z);
					set(this.level, plank, wood("planks").defaultBlockState());
					for (int y = 1; y <= 4; y++) {
						if (!this.level.getBlockState(plank.above(y)).isAir()) {
							set(this.level, plank.above(y), Blocks.AIR.defaultBlockState());
						}
					}
				} else if (!this.plaza.contains(x, z)) {
					TownBuilder.road(this.level, x, z);
				}
			}

			for (long cell : road.cells.keySet()) {
				int x = BlockPos.getX(cell);
				int z = BlockPos.getZ(cell);
				if (this.plaza.contains(x, z)) {
					continue;
				}

				Integer deck = this.bridges.get(cell);
				if (deck != null) {
					this.railing(x, z, deck);
				}

				// Climbs: on the lower of two levels side by side, a slab (one step) or stairs (a climb that goes on: slabs on every
				// step of it still left a block between them, found by the walk check), up the way the road goes where it can.
				int top = ground(this.level, x, z);
				double heading = road.heading(road.cells.get(cell));
				Direction along = Direction.getNearest(Math.cos(heading), 0, Math.sin(heading));
				for (Direction side : new Direction[] {along, along.getOpposite(), along.getClockWise(), along.getCounterClockWise()}) {
					int nx = x + side.getStepX();
					int nz = z + side.getStepZ();
					if ((this.roadCells.contains(BlockPos.asLong(nx, 0, nz)) || this.plaza.contains(nx, nz)) && ground(this.level, nx, nz) == top + 1) {
						climb(this.level, new BlockPos(x, top + 1, z), side, ground(this.level, nx + side.getStepX(), nz + side.getStepZ()) > top + 1);
						break;
					}
				}
			}
		}

		/**
		 * A bridge's railing beside this plank of its deck (out over the water: a plank at the deck's height and a fence on it),
		 * on a log pier down to the bottom now and then.
		 */
		private void railing(int x, int z, int height) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				int nx = x + side.getStepX();
				int nz = z + side.getStepZ();
				if (this.roadCells.contains(BlockPos.asLong(nx, 0, nz)) || !wet(this.level, nx, nz)) {
					continue;
				}

				BlockPos deck = new BlockPos(nx, height, nz);
				set(this.level, deck, wood("planks").defaultBlockState());
				if (this.level.getBlockState(deck.above()).isAir()) {
					place(this.level, deck.above(), wood("fence").defaultBlockState());
				}

				if (Math.floorMod(nx + nz, 4) == 0) {
					for (BlockPos pier = deck.below(); pier.getY() > deck.getY() - 32
						&& (this.level.getBlockState(pier).isAir() || !this.level.getFluidState(pier).isEmpty()); pier = pier.below()) {
						set(this.level, pier, wood("log").defaultBlockState());
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
					set(this.level, new BlockPos(x, y0 - 1, z), (this.random.nextFloat() < 0.3F ? this.look.pavingToo() : this.look.paving()).defaultBlockState());
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
						set(this.level, pos.below(), this.look.wall().defaultBlockState());
						place(this.level, pos, (Math.abs(dx) == 1 && Math.abs(dz) == 1 ? wood("fence") : this.look.wall() == Blocks.SANDSTONE ? Blocks.SANDSTONE_WALL
							: Blocks.COBBLESTONE_WALL).defaultBlockState());
					}
				}
			}

			for (int dx = -1; dx <= 1; dx += 2) {
				for (int dz = -1; dz <= 1; dz += 2) {
					place(this.level, this.well.offset(dx, 1, dz), wood("fence").defaultBlockState());
				}
			}

			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					set(this.level, this.well.offset(dx, 2, dz), wood("slab").defaultBlockState());
				}
			}

			this.board = new BlockPos(cx - 5, y0, cz - 5);
			if (this.ruin) {
				return;
			}

			place(this.level, new BlockPos(cx + 5, y0, cz - 5), Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, Direction.SOUTH));
			for (int dx : new int[] {-PLAZA, PLAZA}) {
				for (int dz : new int[] {-PLAZA, PLAZA}) {
					if (!this.nearRoad(cx + dx, cz + dz, 1)) {
						this.lampAt(new BlockPos(cx + dx, y0, cz + dz));
					}
				}
			}
		}

		/**
		 * Lamps where a player would put them (the user: "make it make sense"): one beside each door path where it meets its
		 * road, then along each road every twelve blocks, sides in turn, none within six of another.
		 */
		private void roadLamps() {
			for (List<Long> path : this.doorPaths) {
				if (path.size() < 3) {
					continue;
				}

				// (The path's last block before the road, and a block to one side of it.)
				long last = path.get(path.size() - 2);
				long before = path.get(path.size() - 3);
				int dx = BlockPos.getX(last) - BlockPos.getX(before);
				int dz = BlockPos.getZ(last) - BlockPos.getZ(before);
				for (int side = -1; side <= 1; side += 2) {
					if (this.wayLamp(BlockPos.getX(last) - dz * side, BlockPos.getZ(last) + dx * side)) {
						break;
					}
				}
			}

			for (Road road : this.roads) {
				for (int t = 8; t < road.length(); t += 12) {
					double way = road.heading(t) + ((t / 12) % 2 == 0 ? Math.PI / 2 : -Math.PI / 2);
					this.wayLamp(Mth.floor(road.xs[t] + Math.cos(way) * 2.2), Mth.floor(road.zs[t] + Math.sin(way) * 2.2));
				}
			}
		}

		/** A lamp by a road or path here, if it's free ground off them and the buildings, and no lamp's within six. */
		private boolean wayLamp(int x, int z) {
			long column = BlockPos.asLong(x, 0, z);
			if (this.roadCells.contains(column) || this.pathCells.contains(column) || this.built.stream().anyMatch(rect -> rect.away(x, z) <= 1)
				|| this.wayLamps.stream().anyMatch(lamp -> Math.abs(BlockPos.getX(lamp) - x) + Math.abs(BlockPos.getZ(lamp) - z) < 6)) {
				return false;
			}

			if (this.lampAt(onGround(this.level, x, z)) == null) {
				return false;
			}

			this.wayLamps.add(column);
			return true;
		}

		/** Each door path's columns, door out; the lamps put up by the roads and paths. */
		private final List<List<Long>> doorPaths = new ArrayList<>();
		private final List<Long> wayLamps = new ArrayList<>();

		/** Beside one of its roads or paths, or the plaza (within two). */
		private boolean byWay(int x, int z) {
			if (this.plaza.away(x, z) <= 2) {
				return true;
			}

			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					long key = BlockPos.asLong(x + dx, 0, z + dz);
					if (this.roadCells.contains(key) || this.pathCells.contains(key)) {
						return true;
					}
				}
			}

			return false;
		}

		/** A market stall on the plaza, its counter to the well (north): kept by the most trader-like of them (step 4); paths
		 * from each door, the store's too, to its road (or the plaza). */
		private void stallAndPaths() {
			DrifterApi.placeHouse(this.level, DrifterApi.stallPlan(), this.stallAt(), Rotation.NONE, this.look.main(), this.look.stone(), Direction.SOUTH);
			GROUND.clear();
			java.util.function.LongPredicate road = column -> this.roadCells.contains(column) || this.plaza.contains(BlockPos.getX(column), BlockPos.getZ(column));
			for (int i = 0; i < this.houses.size(); i++) {
				var house = this.houses.get(i);
				if (house.door() != null) {
					List<Long> columns = path(this.level, house.door(), house.box(), this.plots.get(i).front, road);
					this.pathCells.addAll(columns);
					this.doorPaths.add(columns);
				}
			}

			BlockPos o = this.storeAt;
			DrifterCivilizations.LOGGER.info("[town] {}: the store at {}, its door to the {}", this.name, o.toShortString(), this.storeFront.getName());
			this.pathCells.addAll(path(this.level, storeDoor(o, this.storeFront), new net.minecraft.world.level.levelgen.structure.BoundingBox(o.getX(), o.getY(),
				o.getZ(), o.getX() + 8, o.getY() + 4, o.getZ() + 8), this.storeFront, road));
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
		/** Smaller for the trees (a wooded town's took 120 ms a piece at 32: found in a taiga town). */
		private static final int TREE_PIECE = 20;

		private void areaAndTrees() {
			int minX = this.cx - PLAZA;
			int maxX = this.cx + PLAZA;
			int minZ = this.cz - PLAZA;
			int maxZ = this.cz + PLAZA;
			this.all.addAll(this.built);
			this.roads.forEach(road -> this.all.add(road.bounds));
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
			for (var piece : this.pieces(TREE_PIECE)) {
				this.then("trees", () -> {
					DrifterApi.tidyTrees(this.level, piece);
					GROUND.clear();
				});
			}

			if (this.ruin) {
				for (int i = 0; i < this.houses.size(); i++) {
					var house = this.houses.get(i);
					this.then("ruin", () -> this.ruinHouse(house));
				}

				this.then("graves", this::graves);
				this.then("finish", this::finish);
				return;
			}

			this.then("peopleStart", this::peopleStart);
		}

		// -----------------------------------------------------------------------------------------
		// A ruin: its houses fallen in, its store emptied, its dead in the ground.
		// -----------------------------------------------------------------------------------------

		/** A house gone to ruin: the roof fallen in (the higher, the more of it gone), holes in the walls, its door and glass
		 * gone, moss on its stone, cobwebs in the corners; nothing left in its chests, its bed gone. */
		private void ruinHouse(DrifterApi.PlacedHouse house) {
			var box = house.box();
			double height = Math.max(1, box.getYSpan() - 1);
			for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				BlockPos pos = at.immutable();
				BlockState state = this.level.getBlockState(pos);
				if (state.isAir()) {
					continue;
				}

				double up = (pos.getY() - box.minY()) / height;
				if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof net.minecraft.world.level.block.BedBlock || state.is(Blocks.TORCH)
					|| state.is(Blocks.WALL_TORCH) || state.is(Blocks.LANTERN) || state.is(net.minecraft.tags.BlockTags.WOOL_CARPETS)) {
					set(this.level, pos, Blocks.AIR.defaultBlockState());
				} else if (state.is(net.minecraft.tags.BlockTags.IMPERMEABLE) || state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock) {
					if (this.random.nextFloat() < 0.7F) {
						set(this.level, pos, Blocks.AIR.defaultBlockState());
					}
				} else if (state.ignitedByLava() && pos.getY() > box.minY() && this.random.nextFloat() < 0.15 + 0.6 * up * up) {
					set(this.level, pos, Blocks.AIR.defaultBlockState());
				} else if (state.getBlock().defaultBlockState().is(Blocks.COBBLESTONE) && this.random.nextFloat() < 0.4F) {
					set(this.level, pos, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
				} else if (this.level.getBlockEntity(pos) instanceof net.minecraft.world.Container container) {
					container.clearContent();
				}
			}

			for (BlockPos at : BlockPos.betweenClosed(box.minX() + 1, box.minY() + 1, box.minZ() + 1, box.maxX() - 1, box.maxY() - 1, box.maxZ() - 1)) {
				if (this.level.getBlockState(at).isAir() && !this.level.getBlockState(at.above()).isAir() && this.random.nextFloat() < 0.03F) {
					set(this.level, at.immutable(), Blocks.COBWEB.defaultBlockState());
				}
			}
		}

		/** Its store emptied (a few bones and rotten flesh left), and two to four graves by the plaza with names on them. */
		private void graves() {
			for (int i = 0; i < this.store.size(); i++) {
				if (this.level.getBlockEntity(this.store.get(i)) instanceof net.minecraft.world.Container chest) {
					chest.clearContent();
					if (i == 0) {
						chest.setItem(0, new ItemStack(Items.BONE, 2 + this.random.nextInt(4)));
						chest.setItem(4, new ItemStack(Items.ROTTEN_FLESH, 1 + this.random.nextInt(3)));
					}
				}
			}

			int want = 2 + this.random.nextInt(3);
			for (int made = 0; made < want; made++) {
				this.grave(DrifterApi.randomName(this.server, this.random));
			}
		}

		/** A grave west of the plaza: a mound of coarse dirt and a sign ("here lies", the name, "rip" or "o7"). */
		private boolean grave(String name) {
			for (int tries = 0; tries < 40; tries++) {
				int x = this.cx - PLAZA - 3 - this.random.nextInt(8);
				int z = this.cz + this.random.nextInt(2 * PLAZA + 1) - PLAZA;
				if (this.built.stream().anyMatch(rect -> rect.away(x, z) <= 1) || this.nearRoad(x, z, 1)) {
					continue;
				}

				BlockPos head = onGround(this.level, x, z);
				BlockPos foot = onGround(this.level, x, z + 1);
				if (head.getY() != foot.getY() || !this.level.getBlockState(head).isAir() || !this.level.getBlockState(foot).isAir() || wet(this.level, x, z)) {
					continue;
				}

				set(this.level, head.below(), Blocks.COARSE_DIRT.defaultBlockState());
				set(this.level, foot.below(), Blocks.COARSE_DIRT.defaultBlockState());
				place(this.level, head, Blocks.OAK_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION, 8));
				if (this.level.getBlockEntity(head) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
					var text = new net.minecraft.world.level.block.entity.SignText()
						.setMessage(0, Component.literal("here lies"))
						.setMessage(1, Component.literal(name))
						.setMessage(3, Component.literal(this.random.nextFloat() < 0.5F ? "rip" : "o7"));
					sign.setText(text, true);
					sign.setText(text, false);
				}

				return true;
			}

			return false;
		}

		private List<net.minecraft.world.level.levelgen.structure.BoundingBox> pieces(int size) {
			List<net.minecraft.world.level.levelgen.structure.BoundingBox> list = new ArrayList<>();
			for (int x = this.area.minX(); x <= this.area.maxX(); x += size) {
				for (int z = this.area.minZ(); z <= this.area.maxZ(); z += size) {
					list.add(new net.minecraft.world.level.levelgen.structure.BoundingBox(x, this.area.minY(), z, Math.min(x + size - 1, this.area.maxX()),
						this.area.maxY(), Math.min(z + size - 1, this.area.maxZ())));
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
			// Its past (A5): one house's people gone for good (left empty), the newest still going up.
			this.foundedAgo = 20 + this.random.nextInt(41);
			if (this.houses.size() >= 4 && this.random.nextFloat() < 0.5F) {
				this.emptyHouse = this.houses.size() - 2;
			}

			if (this.houses.size() >= 3 && this.random.nextFloat() < 0.6F) {
				this.buildingHouse = this.houses.size() - 1;
			}

			for (int i = 0; i < this.houses.size(); i++) {
				int index = i;
				this.then("household", () -> this.household(index));
			}

			this.then("townUp", this::townUp);
		}

		private void household(int i) {
			DrifterApi.PlacedHouse house = this.houses.get(i);
			Town.Plot plot = this.plots.get(i);
			if (i == this.emptyHouse) {
				this.owners.add(DrifterApi.randomName(this.server, this.random));
				return;
			}

			BlockPos stand = doorstep(this.level, house, plot);
			// (A city: a hundred people at most, however many rooms; the rest of its houses stand empty for newcomers.)
			DrifterEntity drifter = this.people.size() >= CITY_PEOPLE ? null : DrifterApi.spawn(this.level, stand, null);
			if (drifter == null) {
				this.owners.add("");
				return;
			}

			this.owners.add(drifter.getName().getString());
			nameSign(this.level, house, plot.front, drifter.getName().getString());
			this.pet(drifter, stand);
			// (The newest house, its top not built yet: its owner puts the rest up over the next days, with what it gathers.)
			if (i == this.buildingHouse) {
				var box = house.box();
				int keep = box.minY() + Math.max(3, box.getYSpan() / 3);
				for (BlockPos at : BlockPos.betweenClosed(box.minX(), keep, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
					if (!this.level.getBlockState(at).isAir()) {
						set(this.level, at.immutable(), Blocks.AIR.defaultBlockState());
					}
				}
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
			if (i == this.buildingHouse) {
				DrifterApi.stillBuilding(drifter, 6);
				return;
			}

			// A house with more than one room takes more households: someone in each other room (user, 2026-10-23: "that way
			// there is more citizens without having more houses").
			plot.room = DrifterApi.households(this.level, house.planId(), house.origin(), house.rotation());
			for (int more = 1; more < plot.room && this.people.size() < CITY_PEOPLE; more++) {
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
			List<UUID> guards = new ArrayList<>();
			for (DrifterEntity one : this.people) {
				String role = group != null && one.getUUID().equals(group.leader) ? "leader" : boldest.indexOf(one) >= 0 && boldest.indexOf(one) < 2 ? "guard"
					: "citizen";
				DrifterApi.equipTown(one, this.random, role);
				if (role.equals("guard")) {
					guards.add(one.getUUID());
				}
			}

			long day = Chronicle.day(this.server);
			Town town = new Town(this.plannedId != null ? this.plannedId : UUID.randomUUID(), this.name, group == null ? null : group.id, this.dimension,
				new BlockPos(this.cx, this.y0, this.cz), day, true);
			this.town = town;
			town.plots.addAll(this.plots);
			town.store.addAll(this.store);
			town.fields.addAll(this.fields);
			town.well = this.well;
			town.pen = this.pen;
			town.stallPlot = new Town.Plot(this.stallAt(), 5, 4, DrifterApi.stallPlan(), Rotation.NONE, Direction.NORTH, null);
			town.stallBuilt = true;
			town.shape = this.shape.id;
			town.roadColumns = this.roadCells.stream().mapToLong(Long::longValue).toArray();
			guards.forEach(id -> town.lines.put(id, "guard"));
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

			this.past(town, group);
			noticeBoard(this.level, this.board, town, this.people);
			// Bountiful's bounty board on the plaza, if it's installed (its boards are in villages, and there are none now: A9).
			var bounties = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
				"bountiful", "bountyboard"));
			if (bounties.isPresent() && !this.ruin) {
				BlockPos at = new BlockPos(this.cx - 5, this.y0, this.cz + 5);
				if (this.level.getBlockState(at).isAir()) {
					place(this.level, at, bounties.get().defaultBlockState());
				}
			}
			// Lit all over, inside the houses and out, so nothing spawns in the town at night (user, 2026-10-23: "the town isnt
			// fully lit up alot, causing a bunch of mobs to spawn and killing half the town"): a piece at a time.
			for (var piece : this.pieces(PIECE)) {
				this.then("light", () -> this.light(piece));
			}

			this.then("walk", this::walk);
			this.then("finish", this::finish);
		}

		/** Names for a household's dog or cat. */
		private static final String[] PET_NAMES = {"Biscuit", "Rex", "Pepper", "Shadow", "Mochi", "Buddy", "Luna", "Bean", "Socks", "Ghost", "Max",
			"Nugget", "Pickles", "Waffles", "Bandit", "Smokey", "Cookie", "Ziggy"};

		/** Now and then (a third of households) a dog or a cat of its own by its door, tamed to it, named (A6). */
		private void pet(DrifterEntity owner, BlockPos at) {
			if (this.random.nextFloat() >= 0.33F) {
				return;
			}

			var type = this.random.nextBoolean() ? EntityType.WOLF : EntityType.CAT;
			var pet = type.create(this.level);
			if (!(pet instanceof net.minecraft.world.entity.TamableAnimal animal)) {
				return;
			}

			animal.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, this.random.nextFloat() * 360.0F, 0.0F);
			animal.finalizeSpawn(this.level, this.level.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null);
			animal.setTame(true, true);
			animal.setOwnerUUID(owner.getUUID());
			animal.setCustomName(Component.literal(PET_NAMES[this.random.nextInt(PET_NAMES.length)]));
			animal.setPersistenceRequired();
			this.level.addFreshEntity(animal);
		}

		/**
		 * Its past (A5: "its history written first, then the town made to match"): its founding some weeks back, its houses
		 * going up, its leader chosen, and things to find in it: a grave, the house its people left, the house still going up,
		 * a field that failed one winter. The chronicle has them, days before the world's first if need be.
		 */
		private void past(Town town, @Nullable com.dinglebob.drifter.world.DrifterGroups.Group group) {
			int ago = this.foundedAgo;
			String leader = group == null ? "its founders" : group.names.getOrDefault(group.leader, "its founders");
			String first = this.owners.stream().filter(n -> !n.isEmpty() && !n.equals(leader)).findFirst().orElse("a friend");
			Chronicle.addPast(this.server, "town", this.name + " was founded by " + leader + " and " + first, ago);
			int built = Math.min(3, this.houses.size());
			for (int i = 0; i < built; i++) {
				if (!this.owners.get(i).isEmpty() && !this.owners.get(i).equals(leader)) {
					Chronicle.addPast(this.server, "town", this.owners.get(i) + " built a house in " + this.name, ago - 2 - i * 2);
				}
			}

			Chronicle.addPast(this.server, "town", leader + " was chosen to lead " + this.name, ago - 5);
			if (this.houses.size() > built) {
				Chronicle.addPast(this.server, "town", this.name + " had " + this.houses.size() + " houses", ago / 2);
			}

			// Someone who died: a grave by the plaza, and its people still speak of them.
			if (this.random.nextFloat() < 0.6F) {
				String dead = DrifterApi.randomName(this.server, this.random);
				String[] how = {"fell into a ravine", "was blown up by a creeper", "drowned crossing the river", "never came back from the mine",
					"was killed by bandits on the road", "froze on the way back from a trip"};
				if (this.grave(dead)) {
					town.pastDead = dead;
					Chronicle.addPast(this.server, "death", dead + " of " + this.name + " " + how[this.random.nextInt(how.length)], 3 + this.random.nextInt(Math.max(1,
						ago - 8)));
				}
			}

			if (this.emptyHouse >= 0) {
				Chronicle.addPast(this.server, "town", this.owners.get(this.emptyHouse) + " left " + this.name + " for good (its house stands empty)",
					1 + this.random.nextInt(Math.max(1, ago / 3)));
			}

			if (this.buildingHouse >= 0 && !this.owners.get(this.buildingHouse).isEmpty()) {
				Chronicle.addPast(this.server, "town", this.owners.get(this.buildingHouse) + " moved to " + this.name + " and started on a house", 2);
			}

			// A hard winter: a corner of a field that failed.
			if (!this.fields.isEmpty() && this.random.nextFloat() < 0.4F) {
				BlockPos field = this.fields.get(this.random.nextInt(this.fields.size())).below();
				for (int dx = 1; dx <= 4; dx++) {
					for (int dz = 1; dz <= 3; dz++) {
						BlockPos pos = field.offset(dx, 0, dz);
						if (this.level.getBlockState(pos).is(Blocks.FARMLAND)) {
							set(this.level, pos.above(), Blocks.AIR.defaultBlockState());
							set(this.level, pos, Blocks.COARSE_DIRT.defaultBlockState());
						}
					}
				}

				Chronicle.addPast(this.server, "town", "A hard winter in " + this.name + ": part of a field failed", ago / 3 + 1);
			}
		}

		private void light(net.minecraft.world.level.levelgen.structure.BoundingBox piece) {
			List<Rect> noLamp = new ArrayList<>(this.fieldRects);
			noLamp.add(this.plaza);
			// (The store and the stall are buildings too: their roofs aren't ground; the store had a lamp on its roof.)
			List<net.minecraft.world.level.levelgen.structure.BoundingBox> buildings = new ArrayList<>(this.houses.stream().map(DrifterApi.PlacedHouse::box)
				.toList());
			BlockPos o = this.storeAt;
			buildings.add(new net.minecraft.world.level.levelgen.structure.BoundingBox(o.getX(), o.getY() - 1, o.getZ(), o.getX() + 8, o.getY() + 5, o.getZ() + 8));
			BlockPos stall = this.stallAt();
			buildings.add(new net.minecraft.world.level.levelgen.structure.BoundingBox(stall.getX(), stall.getY() - 1, stall.getZ(), stall.getX() + 4,
				stall.getY() + 5, stall.getZ() + 3));
			int[] made = TownLights.light(this.level, piece, buildings,
				(x, z) -> this.built.stream().anyMatch(rect -> rect.away(x, z) <= TOWN_GROUND) || this.byRoad(x, z),
				(x, z) -> noLamp.stream().anyMatch(rect -> rect.contains(x, z)) || this.roadCells.contains(BlockPos.asLong(x, 0, z))
					|| this.pathCells.contains(BlockPos.asLong(x, 0, z)), this::byWay, this::lampAt);
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
			for (int i = 0; i < this.houses.size(); i++) {
				var house = this.houses.get(i);
				if (house.door() != null && this.unwalkable.contains("house " + (i + 1) + " (" + house.planId() + ")")) {
					this.logOut("house " + (i + 1), house.door(), this.plots.get(i).front);
				}
			}

			if (this.unwalkable.contains("the store")) {
				// (What's in front of its door, for working out why: each block out, the ground's height and what's on it.)
				StringBuilder out = new StringBuilder();
				BlockPos door = storeDoor(this.storeAt, this.storeFront);
				for (int i = 0; i <= 6; i++) {
					BlockPos at = door.relative(this.storeFront, i);
					int top = ground(this.level, at.getX(), at.getZ());
					out.append(' ').append(top).append(':').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this.level.getBlockState(at.atY(top))
						.getBlock()).getPath()).append('/').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this.level.getBlockState(at.atY(top + 1))
						.getBlock()).getPath());
				}

				DrifterCivilizations.LOGGER.info("[town] {}: the store's door at {} ({}), out from it:{}", this.name, door.toShortString(), this.storeFront.getName(), out);
			}

			DrifterCivilizations.LOGGER.info("[town] {}: walked to {} of {} places from the plaza{}", this.name, places.size() - this.unwalkable.size(),
				places.size(), this.unwalkable.isEmpty() ? "" : "; not: " + String.join(", ", this.unwalkable));
		}

		/** Debug, for a place the walk check couldn't reach: what's out from its door, each block's ground and what's on it. */
		private void logOut(String what, BlockPos door, Direction front) {
			StringBuilder out = new StringBuilder();
			for (int i = 0; i <= 8; i++) {
				BlockPos at = door.relative(front, i);
				int top = ground(this.level, at.getX(), at.getZ());
				out.append(' ').append(top).append(':').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this.level.getBlockState(at.atY(top))
					.getBlock()).getPath()).append('/').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this.level.getBlockState(at.atY(top + 1))
					.getBlock()).getPath());
			}

			DrifterCivilizations.LOGGER.info("[town] {}: {}'s door at {} ({}), out from it:{}", this.name, what, door.toShortString(), front.getName(), out);
		}

		private void finish() {
			Town town = this.town;
			Chronicle.quiet = false;
			if (this.ruin) {
				Chronicle.add(this.server, "town", "The ruins of " + this.name + " stand where a village might have: " + this.houses.size()
					+ " houses fallen in, nobody living there for a long time");
				DrifterCivilizations.LOGGER.info("[town] the ruins of {} made: {} houses, {} ms of work; the longest step {} ms ({})", this.name,
					this.houses.size(), this.worked / 1_000_000, this.longest / 1_000_000, this.longestStep);
				this.result = new Result(null, "the ruins of " + this.name + ": " + this.houses.size() + " houses, the plaza at " + this.cx + ", " + this.y0
					+ ", " + this.cz);
				return;
			}

			Chronicle.add(this.server, "town", this.name + (this.world ? " stands where a village might have" : " was founded") + (this.city ? ": a city" : "")
				+ ", with " + this.people.size() + " people in " + this.houses.size() + " houses" + (this.world ? "" : " (made ready, for testing)"));
			long seconds = Math.round((System.nanoTime() - this.startedAt) / 1e9);
			DrifterCivilizations.LOGGER.info("[town] {} made: {} people in {} houses, {} ms of work over {} s; the longest step {} ms ({}); over 50 ms: {}",
				this.name, this.people.size(), this.houses.size(), this.worked / 1_000_000, seconds, this.longest / 1_000_000, this.longestStep, this.slow);
			this.result = new Result(town, this.name + ": " + this.people.size() + " people in " + this.houses.size() + " houses, the plaza at " + this.cx + ", "
				+ this.y0 + ", " + this.cz + " (" + this.lamps + " lamp posts and " + this.torches + " torches for dark corners)"
				+ (this.unwalkable.isEmpty() ? "" : "; can't walk to " + String.join(", ", this.unwalkable)));
		}
	}

	/** Water a road bridges (planks over it); more and the road ends at the shore (found in testing: roads out over the sea). */
	private static final int LONGEST_BRIDGE = 5;

	/** A house's footprint: its front's middle at fx, fz, going {@code out} from there, {@code along} wide and {@code depth} deep. */
	private static Rect footprint(int fx, int fz, Direction out, int along, int depth) {
		int across = fx - along / 2;
		if (out.getAxis() == Direction.Axis.X) {
			int x0 = out == Direction.EAST ? fx : fx - depth + 1;
			int z0 = fz - along / 2;
			return new Rect(x0, z0, x0 + depth - 1, z0 + along - 1);
		}

		int z0 = out == Direction.SOUTH ? fz : fz - depth + 1;
		return new Rect(across, z0, across + along - 1, z0 + depth - 1);
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
	 * Ground with something now built on it: earth, not grass (user, 2026-10-23: "grass blocks not turning into dirt when
	 * there is a house on top of it, causing two grass blocks on top of eachother").
	 */
	static void buried(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.DIRT_PATH)) {
			set(level, pos, Blocks.DIRT.defaultBlockState());
		}
	}

	/** Trees with a log or leaves in this column between these heights felled whole (a player clears a tree, not half). */
	static void fellIn(ServerLevel level, int x, int z, int fromY, int toY) {
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
	private static void penFence(ServerLevel level, BlockPos middle, int half, Direction front) {
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

		BlockPos gate = new BlockPos(middle.getX() + front.getStepX() * half, 0, middle.getZ() + front.getStepZ() * half);
		for (int i = 0; i < ring.size(); i++) {
			BlockPos column = ring.get(i);
			int up = Math.max(tops[i], Math.max(tops[(i + 1) % ring.size()], tops[(i + ring.size() - 1) % ring.size()]));
			for (int y = tops[i]; y <= up; y++) {
				BlockPos pos = column.atY(y);
				boolean isGate = column.getX() == gate.getX() && column.getZ() == gate.getZ() && y == tops[i];
				place(level, pos, isGate ? wood("fence_gate").defaultBlockState().setValue(FenceGateBlock.FACING, front)
					: wood("fence").defaultBlockState());
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

	/**
	 * A sign with its owner's name by its door path, facing the road (milestone 23 A6: "names on signs by their doors"), if
	 * there's open ground beside the path.
	 */
	private static void nameSign(ServerLevel level, DrifterApi.PlacedHouse house, Direction front, String name) {
		if (house.door() == null) {
			return;
		}

		var box = house.box();
		BlockPos step = house.door().relative(front);
		while (box.isInside(new BlockPos(step.getX(), box.minY(), step.getZ())) && step.distManhattan(house.door()) < LONGEST_PATH) {
			step = step.relative(front);
		}

		int rotation = switch (front) {
			case SOUTH -> 0;
			case WEST -> 4;
			case NORTH -> 8;
			default -> 12;
		};
		for (Direction side : new Direction[] {front.getClockWise(), front.getCounterClockWise()}) {
			BlockPos at = onGround(level, step.relative(side).getX(), step.relative(side).getZ());
			if (!level.getBlockState(at).isAir() || !level.getBlockState(at.below()).isSolid() || box.isInside(at)) {
				continue;
			}

			place(level, at, wood("sign").defaultBlockState().setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION, rotation));
			if (level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
				String short15 = name.length() > 15 ? name.substring(0, 15) : name;
				var text = new net.minecraft.world.level.block.entity.SignText().setMessage(1, Component.literal(short15));
				sign.setText(text, true);
				sign.setText(text, false);
			}

			return;
		}
	}

	/** How far out from a door its path goes looking for the road. */
	private static final int LONGEST_PATH = 16;

	/**
	 * A path from the door out to its road, a block up or down a step at most: the ground cut down or built up along it
	 * and room cleared overhead, so it's walked without pillaring, digging or a jump onto anything taller (milestone 23,
	 * A11; on the user's hilly Tectonic land a door could stand three blocks over its road with nothing between).
	 */
	private static List<Long> path(ServerLevel level, BlockPos door, net.minecraft.world.level.levelgen.structure.BoundingBox box, Direction front,
		java.util.function.LongPredicate road) {
		List<Long> columns = new ArrayList<>();
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
			reached = road.test(BlockPos.asLong(at.getX(), 0, at.getZ()));
			step = step.relative(front);
		}

		BlockPos outside = door.relative(front);
		columns.add(BlockPos.asLong(outside.getX(), 0, outside.getZ()));
		cells.forEach(cell -> columns.add(BlockPos.asLong(cell.getX(), 0, cell.getZ())));
		int n = cells.size();
		int[] top = new int[n];
		for (int i = 0; i < n; i++) {
			top[i] = ground(level, cells.get(i).getX(), cells.get(i).getZ());
		}

		// The levels it's laid at: a block from the one before at most, from the door's floor to the road (fixed ends).
		int start = door.getY() - 1;
		int end = reached ? top[n - 1] : top[n - 1];
		// (Its first block level with the door's floor: a step up right outside a door has no room over it to jump, under the
		// wall above the door; found by the walk check, a store whose way out was a block up.)
		// (Its first two where there's room: a slab or stair right outside the door is still a step up under the wall.)
		int flat = n >= 4 ? 1 : 0;
		for (int pass = 0; pass < 4; pass++) {
			int previous = start;
			for (int i = 0; i < n - (reached ? 1 : 0); i++) {
				top[i] = i <= flat ? start : Math.max(previous - 1, Math.min(previous + 1, top[i]));
				previous = top[i];
			}

			if (reached) {
				int next = end;
				for (int i = n - 2; i > flat; i--) {
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
				set(level, new BlockPos(x, y, z), style.fill().defaultBlockState());
			}

			for (int y = top[i] + 1; y <= g; y++) {
				set(level, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
			}

			set(level, new BlockPos(x, top[i], z), paving.get());
			for (int y = top[i] + 1; y <= top[i] + 3; y++) {
				BlockPos above = new BlockPos(x, y, z);
				BlockState state = level.getBlockState(above);
				if (!state.isAir() && !box.isInside(above) && (state.canBeReplaced() || state.is(net.minecraft.tags.BlockTags.LEAVES) || com.dinglebob.drifter.api.DrifterApi.naturalSlab(state)
					|| state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(Blocks.STONE) || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND))) {
					set(level, above, Blocks.AIR.defaultBlockState());
				}
			}
		}

		// Where it climbs (to the door, along it, to the road): a slab on the lower of a single step, stairs up a climb that
		// goes on; never a block-high step of earth.
		for (int i = 0; i < n - (reached ? 1 : 0); i++) {
			BlockPos column = cells.get(i);
			int before = i == 0 ? start : top[i - 1];
			int beforeThat = i <= 1 ? start : top[i - 2];
			int after = i + 1 < n ? top[i + 1] : top[i];
			int afterThat = i + 2 < n ? top[i + 2] : after;
			BlockPos over = new BlockPos(column.getX(), top[i] + 1, column.getZ());
			if (wet(level, column.getX(), column.getZ())) {
				continue;
			}

			// (Never on the block right outside the door: a half step up there still meets the wall over the door; a whole step
			// up from it, out in the open, is a jump. Found by the walk check, a mountain city's store.)
			if (after == top[i] + 1 && i > 0) {
				climb(level, over, front, afterThat > after);
			} else if (before == top[i] + 1) {
				climb(level, over, front.getOpposite(), i > 0 && beforeThat > before);
			}
		}

		return columns;
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

	/** Which way the store's turned for its door to face {@code front} (it's drawn with its door to the west). */
	private static Rotation storeTurn(Direction front) {
		return switch (front) {
			case NORTH -> Rotation.CLOCKWISE_90;
			case EAST -> Rotation.CLOCKWISE_180;
			case SOUTH -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/** A spot in the store as drawn (dx, dz from its corner, its door to the west), turned for its door to face {@code front}. */
	private static BlockPos inStore(BlockPos o, Direction front, int dx, int dy, int dz) {
		return o.offset(4, dy, 4).offset(new BlockPos(dx - 4, 0, dz - 4).rotate(storeTurn(front)));
	}

	private static BlockPos storeDoor(BlockPos o, Direction front) {
		return inStore(o, front, 0, 0, 4);
	}

	/** The store: 9 by 9, cobblestone walls, a plank roof, its door to {@code front} (a road or the plaza), chests round the walls. */
	private static List<BlockPos> store(ServerLevel level, BlockPos o, Direction front) {
		Rotation turn = storeTurn(front);
		for (int dx = 0; dx <= 8; dx++) {
			for (int dz = 0; dz <= 8; dz++) {
				set(level, o.offset(dx, -1, dz), style.wall().defaultBlockState());
				set(level, o.offset(dx, 4, dz), wood("planks").defaultBlockState());
				boolean wall = dx == 0 || dx == 8 || dz == 0 || dz == 8;
				for (int dy = 0; dy < 4; dy++) {
					boolean corner = (dx == 0 || dx == 8) && (dz == 0 || dz == 8);
					set(level, o.offset(dx, dy, dz), !wall ? Blocks.AIR.defaultBlockState()
						: (corner ? wood("log") : style.wall()).defaultBlockState());
				}
			}
		}

		BlockPos door = storeDoor(o, front);
		set(level, door, wood("door").defaultBlockState().setValue(DoorBlock.FACING, front).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(level, door.above(), wood("door").defaultBlockState().setValue(DoorBlock.FACING, front).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		for (int dz = 3; dz <= 5; dz++) {
			place(level, inStore(o, front, 8, 1, dz), Blocks.GLASS_PANE.defaultBlockState());
		}

		place(level, inStore(o, front, 1, 2, 2), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, turn.rotate(Direction.EAST)));
		place(level, inStore(o, front, 7, 2, 6), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, turn.rotate(Direction.WEST)));
		List<BlockPos> chests = new ArrayList<>();
		int[][] spots = {{7, 1, 2}, {7, 3, 2}, {7, 5, 2}, {7, 7, 2}, {3, 1, 3}, {5, 1, 3}, {3, 7, 1}, {5, 7, 1}};
		for (int[] spot : spots) {
			BlockPos pos = inStore(o, front, spot[0], 0, spot[1]);
			Direction facing = spot[2] == 2 ? Direction.WEST : spot[2] == 3 ? Direction.SOUTH : Direction.NORTH;
			set(level, pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, turn.rotate(facing)));
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

	/** A lamp post: two fence posts and a lantern, if there's room (true: it went up). */
	private static boolean lamp(ServerLevel level, BlockPos pos) {
		if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir() || !level.getBlockState(pos.above(2)).isAir()) {
			return false;
		}

		place(level, pos, wood("fence").defaultBlockState());
		place(level, pos.above(), wood("fence").defaultBlockState());
		place(level, pos.above(2), Blocks.LANTERN.defaultBlockState());
		return true;
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
	static int ground(ServerLevel level, int x, int z) {
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
			// (A slab the land has, Terrain Slabs' half step, is over the ground, like a plant: the ground's the block under it.)
			if (!state.getFluidState().isEmpty() || !(state.isAir() || state.canBeReplaced() || state.is(net.minecraft.tags.BlockTags.LOGS)
				|| state.is(net.minecraft.tags.BlockTags.LEAVES) || com.dinglebob.drifter.api.DrifterApi.naturalSlab(state))) {
				break;
			}

			pos.move(0, -1, 0);
		}

		return pos.getY();
	}

	static boolean wet(ServerLevel level, int x, int z) {
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

	/** One block of road: the town's paving on the ground (planks over water), what grows on it cleared. */
	private static void road(ServerLevel level, int x, int z) {
		int top = ground(level, x, z);
		BlockPos pos = new BlockPos(x, top, z);
		set(level, pos, !level.getFluidState(pos).isEmpty() ? wood("planks").defaultBlockState() : paving.get());
		fellIn(level, x, z, top + 1, top + 4);
		for (int y = top + 1; y <= top + 4; y++) {
			BlockPos above = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(above);
			if (!state.isAir() && (state.canBeReplaced() || state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.LEAVES)
				|| com.dinglebob.drifter.api.DrifterApi.naturalSlab(state))) {
				set(level, above, Blocks.AIR.defaultBlockState());
			}
		}
	}

	/**
	 * A step up {@code up} placed at {@code over} (on the lower of two levels): stairs if the climb goes on past it ({@code
	 * steep}: a block every block, walked without a jump only on stairs), else a slab (half a block up, half again). Only
	 * into air.
	 */
	private static void climb(ServerLevel level, BlockPos over, Direction up, boolean steep) {
		if (!level.getBlockState(over).isAir()) {
			return;
		}

		set(level, over, steep && pavingStairs.hasProperty(net.minecraft.world.level.block.StairBlock.FACING)
			? pavingStairs.setValue(net.minecraft.world.level.block.StairBlock.FACING, up) : pavingSlab);
	}

	/**
	 * A stretch of a road between towns (W5, {@link Highways}) along this line (a point a block, {x, z, heading}): a worn dirt
	 * path three wide (smooth sandstone in a desert), built up a block a step over dips, a slab or stairs where it climbs, a
	 * railed bridge on piers over water. Columns of a town's own roads ({@code keep}) left as they are.
	 */
	static void highway(ServerLevel level, List<double[]> line, java.util.Set<Long> keep) {
		if (line.size() < 2) {
			return;
		}

		double[] mid = line.get(line.size() / 2);
		BlockPos middle = new BlockPos(Mth.floor(mid[0]), 0, Mth.floor(mid[1]));
		style = Style.at(level, middle.atY(ground(level, middle.getX(), middle.getZ()) + 1));
		paving = style.path()::defaultBlockState;
		var pathSlab = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
			"terrain_slabs", "path_slab"));
		pavingSlab = style == Style.DESERT ? Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState() : pathSlab.isPresent() ? pathSlab.get().defaultBlockState()
			: Blocks.COBBLESTONE_SLAB.defaultBlockState();
		pavingStairs = style == Style.DESERT ? Blocks.SANDSTONE_STAIRS.defaultBlockState() : Blocks.COBBLESTONE_STAIRS.defaultBlockState();
		GROUND.clear();
		Road road = new Road(line);
		int n = road.length();
		int[] want = new int[n];
		java.util.Map<Long, Integer> water = new java.util.HashMap<>();
		for (int t = 0; t < n; t++) {
			BlockPos at = road.at(t);
			want[t] = ground(level, at.getX(), at.getZ());
		}

		for (var cell : road.cells.entrySet()) {
			int x = BlockPos.getX(cell.getKey());
			int z = BlockPos.getZ(cell.getKey());
			int g = ground(level, x, z);
			if (wet(level, x, z)) {
				water.put(cell.getKey(), g);
			}

			want[cell.getValue()] = Math.max(want[cell.getValue()], g);
		}

		for (int t = 1; t < n; t++) {
			want[t] = Math.max(want[t], want[t - 1] - 1);
		}

		for (int t = n - 2; t >= 0; t--) {
			want[t] = Math.max(want[t], want[t + 1] - 1);
		}

		java.util.Map<Long, Integer> decks = new java.util.HashMap<>();
		for (var cell : road.cells.entrySet()) {
			if (keep.contains(cell.getKey())) {
				continue;
			}

			int x = BlockPos.getX(cell.getKey());
			int z = BlockPos.getZ(cell.getKey());
			int w = want[cell.getValue()];
			Integer surface = water.get(cell.getKey());
			if (surface != null) {
				int deck = Math.max(w, surface + 1);
				decks.put(cell.getKey(), deck);
				set(level, new BlockPos(x, deck, z), wood("planks").defaultBlockState());
				continue;
			}

			int g = ground(level, x, z);
			if (g < w) {
				buried(level, new BlockPos(x, g, z));
				for (int y = g + 1; y < w; y++) {
					set(level, new BlockPos(x, y, z), style.fill().defaultBlockState());
				}
			}

			BlockPos top = new BlockPos(x, Math.max(g, w), z);
			set(level, top, paving.get());
			fellIn(level, x, z, top.getY() + 1, top.getY() + 4);
			for (int y = top.getY() + 1; y <= top.getY() + 4; y++) {
				BlockPos above = new BlockPos(x, y, z);
				var state = level.getBlockState(above);
				if (!state.isAir() && (state.canBeReplaced() || state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.LEAVES)
					|| com.dinglebob.drifter.api.DrifterApi.naturalSlab(state))) {
					set(level, above, Blocks.AIR.defaultBlockState());
				}
			}
		}

		for (var cell : road.cells.entrySet()) {
			if (keep.contains(cell.getKey())) {
				continue;
			}

			int x = BlockPos.getX(cell.getKey());
			int z = BlockPos.getZ(cell.getKey());
			Integer deck = decks.get(cell.getKey());
			if (deck != null) {
				// A railing over the water beside the deck, on a pier now and then.
				for (Direction side : Direction.Plane.HORIZONTAL) {
					int nx = x + side.getStepX();
					int nz = z + side.getStepZ();
					if (road.cells.containsKey(BlockPos.asLong(nx, 0, nz)) || !wet(level, nx, nz)) {
						continue;
					}

					BlockPos rail = new BlockPos(nx, deck, nz);
					set(level, rail, wood("planks").defaultBlockState());
					if (level.getBlockState(rail.above()).isAir()) {
						place(level, rail.above(), wood("fence").defaultBlockState());
					}

					if (Math.floorMod(nx + nz, 4) == 0) {
						for (BlockPos pier = rail.below(); pier.getY() > deck - 32 && (level.getBlockState(pier).isAir() || !level.getFluidState(pier).isEmpty());
							pier = pier.below()) {
							set(level, pier, wood("log").defaultBlockState());
						}
					}
				}

				continue;
			}

			int top = ground(level, x, z);
			Direction along = Direction.getNearest(Math.cos(road.heading(cell.getValue())), 0, Math.sin(road.heading(cell.getValue())));
			for (Direction side : new Direction[] {along, along.getOpposite(), along.getClockWise(), along.getCounterClockWise()}) {
				int nx = x + side.getStepX();
				int nz = z + side.getStepZ();
				long next = BlockPos.asLong(nx, 0, nz);
				if ((road.cells.containsKey(next) || keep.contains(next)) && ground(level, nx, nz) == top + 1) {
					climb(level, new BlockPos(x, top + 1, z), side, ground(level, nx + side.getStepX(), nz + side.getStepZ()) > top + 1);
					break;
				}
			}
		}
	}

	/** A block set quietly (no neighbour updates): the ground and walls. Only when it changes. */
	static void set(ServerLevel level, BlockPos pos, BlockState state) {
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
