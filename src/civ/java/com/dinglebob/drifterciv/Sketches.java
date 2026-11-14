package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Paper towns (docs/superpowers/specs/2026-11-14-lod-towns-design.md): a rough sketch of every planned place (its main
 * roads, boxes for its houses, its fields, its lamps), made in the background once the world's plan is ready, from the
 * land as the generator says it will be (no chunk is made for it), and saved with the plan. A far-view mod is given
 * them to draw until the place is really built. The real town's job takes its sketch's shape, headings and seed, so it
 * comes out in the same shape. For drawing only: nothing a Drifter decides reads a sketch.
 */
final class Sketches {
	private Sketches() {
	}

	/** The saved sketches' form: another number and they're made again. */
	static final int SKETCH_VERSION = 1;
	/** The land's height is asked for once a square this wide (the generator's answer takes half a millisecond). */
	static final int GRID = 4;
	/** A house box: how long along its road, how deep, how far back from the road's edge, how far from the next. */
	static final int HOUSE_MIN = 7;
	static final int HOUSE_MAX = 13;
	static final int DEPTH_MIN = 7;
	static final int DEPTH_MORE = 3;
	static final int SETBACK = 3;
	static final int HOUSE_GAP = 3;
	/** Its walls' height (some of a city's are taller), its roof's, and a ruin's walls at most. */
	static final int WALL = 4;
	static final int CITY_WALL = 6;
	static final int ROOF = 3;
	static final int RUIN_WALL = 3;
	/** A lamp this often along a road (the real builder lights by darkness, not by a spacing: about this). */
	static final int LAMP_EVERY = 14;
	/** The share of houses with lit windows at night. */
	static final double LIT_SHARE = 0.4;
	/** A road's half width (as {@code TownBuilder}'s) and the width a sketch's street is drawn at. */
	private static final double STREET_HALF = 1.5;
	static final int STREET_WIDTH = 3;
	/**
	 * The real ground stands this much over where the generator's final terrain turns solid at one point (measured with
	 * {@code /drifterciv sketch land} on Tectonic, seed 779, three places: two blocks more often than not, the real land
	 * being smoothed between the generator's points). Without it low beaches were taken for sea, and a harbour's sketch
	 * had its shore blocks nearer than the real one.
	 */
	static final int LIFT = 2;
	/** A place whose sketch took longer than this is logged. */
	private static final long SLOW_MS = 6000;

	private static final Map<UUID, Sketch> MADE = new ConcurrentHashMap<>();
	/** Counted up when a world closes: sketches still being made for it stop, and aren't kept. */
	private static volatile int run;
	private static volatile int makingRun = -1;
	private static volatile boolean ready;
	/** Counted up whenever a sketch is added or they're cleared: what's drawn from them knows to look again. */
	private static volatile int changes;
	@Nullable
	private static volatile Predicted land;

	/** The seed of a planned place's choices (its shape, the ways its roads go, its houses): the same every time. */
	static long seedOf(long worldSeed, UUID place) {
		return worldSeed ^ place.getMostSignificantBits() * 31 ^ place.getLeastSignificantBits();
	}

	/** The land as the generator says it is (null: no planned world open). */
	@Nullable
	static TownLand predicted() {
		return land;
	}

	@Nullable
	static Sketch of(UUID place) {
		return MADE.get(place);
	}

	static Collection<Sketch> all() {
		return MADE.values();
	}

	/** Every planned place has its sketch. */
	static boolean ready() {
		return ready;
	}

	static int changes() {
		return changes;
	}

	/** The world's closing (or another's plan is loading): nothing of this one's kept. */
	static void clear() {
		run++;
		MADE.clear();
		ready = false;
		land = null;
		changes++;
	}

	/** What the sketch maker needs of a place, copied on the server thread. */
	private record Spot(UUID id, String name, int x, int z, int households, boolean ruin, List<Double> gates) {
	}

	/** The plan's ready (made, or loaded): the places with no sketch yet get theirs, in the background, nearest the spawn first. */
	static void start(MinecraftServer server) {
		if (!WorldPlan.planned || !WorldPlan.ready || makingRun == run) {
			return;
		}

		ServerLevel level = server.overworld();
		BlockPos spawn = level.getSharedSpawnPos();
		List<Spot> todo = new ArrayList<>();
		for (WorldPlan.Place place : WorldPlan.places) {
			if (!MADE.containsKey(place.id)) {
				todo.add(new Spot(place.id, place.name, place.x, place.z, place.households, place.ruin(), List.copyOf(place.gates)));
			}
		}

		Predicted predicted = new Predicted(level);
		land = predicted;
		if (todo.isEmpty()) {
			ready = true;
			return;
		}

		todo.sort(Comparator.comparingDouble(spot -> Math.hypot(spot.x - spawn.getX(), spot.z - spawn.getZ())));
		int mine = run;
		makingRun = mine;
		ready = false;
		long worldSeed = level.getSeed();
		// On all but one of the machine's cores, at the lowest priority, nearest the spawn first: the places a new player can
		// see are sketched within a few seconds of the plan, before a far-view mod has drawn their land.
		int threads = Math.max(1, Math.min(todo.size(), Runtime.getRuntime().availableProcessors() - 1));
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads, work -> {
			Thread thread = new Thread(work, "Drifter sketches");
			thread.setDaemon(true);
			thread.setPriority(Thread.MIN_PRIORITY);
			return thread;
		});
		long started = System.nanoTime();
		java.util.concurrent.atomic.AtomicInteger left = new java.util.concurrent.atomic.AtomicInteger(todo.size());
		java.util.concurrent.atomic.AtomicInteger made = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.atomic.AtomicLong slowest = new java.util.concurrent.atomic.AtomicLong();
		for (Spot spot : todo) {
			pool.execute(() -> {
				if (mine == run) {
					long one = System.nanoTime();
					try {
						Sketch sketch = make(predicted, worldSeed, spot);
						if (mine == run) {
							MADE.put(spot.id, sketch);
							changes++;
							made.incrementAndGet();
						}
					} catch (RuntimeException e) {
						DrifterCivilizations.LOGGER.warn("[sketch] {}: couldn't be sketched ({}); it's drawn when it's built", spot.name, e.toString());
					}

					long took = (System.nanoTime() - one) / 1_000_000;
					slowest.accumulateAndGet(took, Math::max);
					if (took > SLOW_MS) {
						DrifterCivilizations.LOGGER.info("[sketch] {} took {} ms", spot.name, took);
					}
				}

				if (left.decrementAndGet() == 0 && mine == run) {
					ready = true;
					changes++;
					DrifterCivilizations.LOGGER.info("[sketch] all made: {} of {} places in {} ms on {} threads (the slowest {} ms); {} heights asked of the generator",
						made.get(), todo.size(), (System.nanoTime() - started) / 1_000_000, threads, slowest.get(), predicted.asked());
					DrifterApi.onServerThread(server, () -> {
						if (mine == run) {
							Towns.save(server);
						}
					});
				}
			});
		}

		pool.shutdown();
	}

	// ---------------------------------------------------------------------------------------------
	// Making one
	// ---------------------------------------------------------------------------------------------

	private static Sketch make(Predicted land, long worldSeed, Spot spot) {
		long seed = seedOf(worldSeed, spot.id);
		TownBuilder.Streets laid = TownBuilder.streets(land, seed, spot.name, spot.x, spot.z, spot.households, spot.ruin, spot.gates);
		boolean city = spot.households >= TownBuilder.CITY && !spot.ruin;
		int reach = city ? TownBuilder.CITY_REACH : TownBuilder.REACH;
		RandomSource random = RandomSource.create(seed ^ 0x5EEDC0DEL);
		int y0 = land.ground(spot.x, spot.z);
		TownBuilder.Style style = TownBuilder.Style.of(land.biome(spot.x, y0, spot.z));
		int[] plaza = {laid.plaza().x0(), laid.plaza().z0(), laid.plaza().x1(), laid.plaza().z1()};
		Set<Long> roadCells = new HashSet<>();
		List<int[]> streets = new ArrayList<>();
		int longest = 0;
		for (TownBuilder.Road road : laid.roads()) {
			roadCells.addAll(road.cells.keySet());
			int n = road.xs.length;
			longest = Math.max(longest, n);
			if (n < 2) {
				continue;
			}

			List<Integer> line = new ArrayList<>();
			line.add(STREET_WIDTH);
			for (int t = 0; t < n; t += 4) {
				line.add(Mth.floor(road.xs[t]));
				line.add(Mth.floor(road.zs[t]));
			}

			if ((n - 1) % 4 != 0) {
				line.add(Mth.floor(road.xs[n - 1]));
				line.add(Mth.floor(road.zs[n - 1]));
			}

			streets.add(line.stream().mapToInt(Integer::intValue).toArray());
		}

		// Houses along both sides of its roads, from the plaza outwards, as the real lots go.
		List<Sketch.Box> boxes = new ArrayList<>();
		int want = spot.households;
		for (int t = 6; t < longest && boxes.size() < want; t += 2) {
			for (TownBuilder.Road road : laid.roads()) {
				if (t >= road.xs.length - 4) {
					continue;
				}

				for (int side = -1; side <= 1 && boxes.size() < want; side += 2) {
					int along = HOUSE_MIN + random.nextInt(HOUSE_MAX - HOUSE_MIN + 1);
					int depth = DEPTH_MIN + random.nextInt(DEPTH_MORE);
					double heading = road.headings[t];
					double out = STREET_HALF + SETBACK + depth / 2.0;
					double mx = road.xs[t] - Math.sin(heading) * side * out;
					double mz = road.zs[t] + Math.cos(heading) * side * out;
					boolean alongX = Math.abs(Math.cos(heading)) >= Math.abs(Math.sin(heading));
					int w = alongX ? along : depth;
					int d = alongX ? depth : along;
					int x0 = Mth.floor(mx - w / 2.0);
					int z0 = Mth.floor(mz - d / 2.0);
					int[] rect = {x0, z0, x0 + w - 1, z0 + d - 1};
					Integer base = free(land, spot, reach, rect, plaza, roadCells, boxes, HOUSE_GAP, 3);
					if (base == null) {
						continue;
					}

					Block walls = planks(style.pick(random));
					Block roof = planks(style.pick(random));
					if (style == TownBuilder.Style.DESERT) {
						walls = random.nextBoolean() ? Blocks.SANDSTONE : walls;
						roof = random.nextBoolean() ? Blocks.SMOOTH_SANDSTONE : Blocks.CUT_SANDSTONE;
					}

					int wall = spot.ruin ? 1 + random.nextInt(RUIN_WALL) : city && random.nextInt(3) == 0 ? CITY_WALL : WALL;
					boxes.add(new Sketch.Box(rect[0], rect[1], rect[2], rect[3], base, wall, spot.ruin ? 0 : ROOF, Sketch.paletteOf(walls), Sketch.paletteOf(roof),
						alongX, !spot.ruin && random.nextDouble() < LIT_SHARE));
				}
			}
		}

		// Its fields: a few plots off its roads, a little way out from the plaza.
		List<int[]> fields = new ArrayList<>();
		for (int k = 0; k < 12 && fields.size() < 3 && !spot.ruin; k++) {
			double way = random.nextDouble() * Math.PI * 2;
			double far = 26 + random.nextInt(19);
			boolean wide = random.nextBoolean();
			int w = wide ? 11 : 7;
			int d = wide ? 7 : 11;
			int x0 = Mth.floor(spot.x + Math.cos(way) * far) - w / 2;
			int z0 = Mth.floor(spot.z + Math.sin(way) * far) - d / 2;
			int[] rect = {x0, z0, x0 + w - 1, z0 + d - 1};
			List<Sketch.Box> taken = new ArrayList<>(boxes);
			for (int[] field : fields) {
				taken.add(new Sketch.Box(field[0], field[1], field[2], field[3], 0, 0, 0, (byte)0, (byte)0, false, false));
			}

			if (free(land, spot, reach, rect, plaza, roadCells, taken, 2, 2) != null) {
				fields.add(rect);
			}
		}

		// Its lamps: the plaza's corners, and along its roads, a side each in turn.
		List<int[]> lamps = new ArrayList<>();
		if (!spot.ruin) {
			for (int sx = 0; sx <= 1; sx++) {
				for (int sz = 0; sz <= 1; sz++) {
					lamps.add(new int[] {sx == 0 ? plaza[0] - 1 : plaza[2] + 1, sz == 0 ? plaza[1] - 1 : plaza[3] + 1});
				}
			}

			int side = 1;
			for (TownBuilder.Road road : laid.roads()) {
				for (int t = LAMP_EVERY / 2; t < road.xs.length; t += LAMP_EVERY) {
					double heading = road.headings[t];
					int lx = Mth.floor(road.xs[t] - Math.sin(heading) * side * (STREET_HALF + 1.2));
					int lz = Mth.floor(road.zs[t] + Math.cos(heading) * side * (STREET_HALF + 1.2));
					side = -side;
					if (land.wet(lx, lz) || roadCells.contains(BlockPos.asLong(lx, 0, lz)) || boxes.stream().anyMatch(box -> box.contains(lx, lz))) {
						continue;
					}

					lamps.add(new int[] {lx, lz});
				}
			}
		}

		return new Sketch(spot.id, spot.x, spot.z, y0, style.name(), laid.shape().id, laid.headings(), new double[] {laid.river() ? 1 : 0,
			laid.coast() ? 1 : 0, laid.hill() ? 1 : 0, laid.toWater(), laid.waterAway(), laid.waterLine(), laid.riverWidth(), laid.uphill()}, seed, reach, spot.ruin, plaza, streets, boxes, fields,
			lamps);
	}

	/**
	 * This rectangle's ground height if something can stand on it: inside the place's reach, off the plaza and the roads,
	 * this far from every box, dry, and its ground within {@code step} from corner to corner. Null: not here.
	 */
	@Nullable
	private static Integer free(Predicted land, Spot spot, int reach, int[] rect, int[] plaza, Set<Long> roadCells, List<Sketch.Box> boxes, int gap, int step) {
		if (Math.abs(rect[0] - spot.x) > reach - 2 || Math.abs(rect[2] - spot.x) > reach - 2 || Math.abs(rect[1] - spot.z) > reach - 2
			|| Math.abs(rect[3] - spot.z) > reach - 2) {
			return null;
		}

		if (rect[2] >= plaza[0] - 2 && rect[0] <= plaza[2] + 2 && rect[3] >= plaza[1] - 2 && rect[1] <= plaza[3] + 2) {
			return null;
		}

		for (Sketch.Box box : boxes) {
			if (rect[2] >= box.x0() - gap && rect[0] <= box.x1() + gap && rect[3] >= box.z0() - gap && rect[1] <= box.z1() + gap) {
				return null;
			}
		}

		for (int x = rect[0] - 1; x <= rect[2] + 1; x++) {
			for (int z = rect[1] - 1; z <= rect[3] + 1; z++) {
				if (roadCells.contains(BlockPos.asLong(x, 0, z))) {
					return null;
				}
			}
		}

		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		int mx = (rect[0] + rect[2]) / 2;
		int mz = (rect[1] + rect[3]) / 2;
		for (int[] at : new int[][] {{rect[0], rect[1]}, {rect[2], rect[1]}, {rect[0], rect[3]}, {rect[2], rect[3]}, {mx, mz}}) {
			if (land.wet(at[0], at[1])) {
				return null;
			}

			int y = land.ground(at[0], at[1]);
			low = Math.min(low, y);
			high = Math.max(high, y);
		}

		return high - low > step ? null : land.ground(mx, mz);
	}

	private static Block planks(String wood) {
		Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(wood + "_planks"));
		return block == Blocks.AIR ? Blocks.OAK_PLANKS : block;
	}

	// ---------------------------------------------------------------------------------------------
	// The land as the generator says it will be
	// ---------------------------------------------------------------------------------------------

	/**
	 * The land nobody's been near: its height from the generator's final terrain (as the world plan reads it,
	 * {@code WorldPlan.Plan.realTop}), once a square {@link #GRID} wide. Safe off the server thread.
	 */
	static final class Predicted implements TownLand {
		private final ChunkGenerator generator;
		private final RandomState randomState;
		private final int low;
		private final int high;
		private final int sea;
		private final Map<Long, Integer> cells = new ConcurrentHashMap<>();
		private final Map<Long, Integer> hints = new ConcurrentHashMap<>();

		Predicted(ServerLevel level) {
			this.generator = level.getChunkSource().getGenerator();
			this.randomState = level.getChunkSource().randomState();
			this.low = level.getMinBuildHeight();
			this.high = level.getMaxBuildHeight() - 1;
			this.sea = this.generator.getSeaLevel();
		}

		int asked() {
			return this.cells.size();
		}

		/** (Water's top where it's water, as the real land's ground is read: {@code TownBuilder.ground}.) */
		@Override
		public int ground(int x, int z) {
			int floor = this.floor(x, z);
			return floor < this.sea - 1 ? this.sea - 1 : floor;
		}

		private int floor(int x, int z) {
			int i = Math.floorDiv(x, GRID);
			int j = Math.floorDiv(z, GRID);
			long key = BlockPos.asLong(i, 0, j);
			Integer known = this.cells.get(key);
			if (known == null) {
				known = this.top(i * GRID + GRID / 2, j * GRID + GRID / 2) + LIFT;
				this.cells.put(key, known);
			}

			return known;
		}

		/** (Water over it: its ground a block or more under the sea's top water block.) */
		@Override
		public boolean wet(int x, int z) {
			return this.floor(x, z) < this.sea - 1;
		}

		Holder<Biome> biome(int x, int y, int z) {
			return this.generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), this.randomState.sampler());
		}

		private int top(int x, int z) {
			DensityFunction density = this.randomState.router().finalDensity();
			long near = BlockPos.asLong(x >> 6, 0, z >> 6);
			Integer hint = this.hints.get(near);
			int y = Math.min(this.high, hint != null ? hint + 24 : this.sea + 96);
			while (y < this.high && solid(density, x, y, z)) {
				y = Math.min(this.high, y + 32);
			}

			int open = y;
			while (y > this.low && !solid(density, x, y, z)) {
				open = y;
				y = Math.max(this.low, y - 6);
			}

			while (open - y > 1) {
				int m = (open + y) / 2;
				if (solid(density, x, m, z)) {
					y = m;
				} else {
					open = m;
				}
			}

			this.hints.put(near, y);
			return y;
		}

		private static boolean solid(DensityFunction density, int x, int y, int z) {
			return density.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0;
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the plan
	// ---------------------------------------------------------------------------------------------

	static void save(CompoundTag plan) {
		ListTag list = new ListTag();
		for (Sketch sketch : MADE.values()) {
			list.add(sketch.save());
		}

		plan.putInt("SketchVersion", SKETCH_VERSION);
		plan.put("Sketches", list);
	}

	/** (An older form, or none saved: none loaded, and they're made again in the background.) */
	static void load(CompoundTag plan) {
		if (plan.getInt("SketchVersion") != SKETCH_VERSION) {
			return;
		}

		for (Tag tag : plan.getList("Sketches", Tag.TAG_COMPOUND)) {
			try {
				Sketch sketch = Sketch.load((CompoundTag)tag);
				MADE.put(sketch.place(), sketch);
			} catch (RuntimeException e) {
				DrifterCivilizations.LOGGER.warn("[sketch] a saved sketch couldn't be read ({}); made again", e.toString());
			}
		}

		changes++;
	}

	// ---------------------------------------------------------------------------------------------
	// Debug commands
	// ---------------------------------------------------------------------------------------------

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> dispatcher.register(
			Commands.literal("drifterciv").requires(source -> source.hasPermission(2)).then(Commands.literal("sketch")
				// Debug: the nearest planned place's sketch.
				.then(Commands.literal("info").executes(ctx -> {
					WorldPlan.Place place = WorldPlan.nearest(BlockPos.containing(ctx.getSource().getPosition()), null);
					Sketch sketch = place == null ? null : MADE.get(place.id);
					if (sketch == null) {
						ctx.getSource().sendFailure(Component.literal(place == null ? "No planned place" : "No sketch of " + place.name + " yet ("
							+ MADE.size() + " of " + WorldPlan.places.size() + " made)"));
						return 0;
					}

					String text = place.name + " (" + place.kind + (sketch.ruin() ? ", a ruin" : "") + ") at " + sketch.x() + ", " + sketch.y0() + ", " + sketch.z()
						+ ": " + sketch.shape() + ", " + sketch.style() + ", " + sketch.streets().size() + " streets, " + sketch.boxes().size() + " houses of "
						+ place.households + ", " + sketch.fields().size() + " fields, " + sketch.lamps().size() + " lamps" + (place.made ? "; built" : "");
					DrifterCivilizations.LOGGER.info("[sketch] {}", text);
					ctx.getSource().sendSuccess(() -> Component.literal(text), false);
					return 1;
				}))
				// Debug: the land within 90 blocks as the generator says it is against the land that's there (loaded columns only).
				.then(Commands.literal("land").executes(ctx -> {
					Predicted predicted = land;
					ServerLevel level = ctx.getSource().getLevel();
					BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
					if (predicted == null) {
						ctx.getSource().sendFailure(Component.literal("No planned world"));
						return 0;
					}

					int both = 0;
					int saidWet = 0;
					int wasWet = 0;
					int dry = 0;
					long off = 0;
					int worst = 0;
					int[] by = new int[7];
					int[] signed = new int[11];
					for (int x = at.getX() - 90; x <= at.getX() + 90; x += 2) {
						for (int z = at.getZ() - 90; z <= at.getZ() + 90; z += 2) {
							if (!TownBuilder.loadedAt(level, x, z) || TownBuilder.built(level, x, z)) {
								continue;
							}

							boolean said = predicted.wet(x, z);
							boolean was = TownBuilder.wet(level, x, z);
							if (said && was) {
								both++;
							} else if (said) {
								saidWet++;
								by[Mth.clamp(TownBuilder.ground(level, x, z) - predicted.ground(x, z) + 3, 0, 6)]++;
							} else if (was) {
								wasWet++;
							} else {
								dry++;
								signed[Mth.clamp(TownBuilder.ground(level, x, z) - predicted.ground(x, z) + 5, 0, 10)]++;
								int d = Math.abs(predicted.ground(x, z) - TownBuilder.ground(level, x, z));
								off += d;
								worst = Math.max(worst, d);
							}
						}
					}

					String text = "land round " + at.getX() + ", " + at.getZ() + ": water both " + both + ", only said " + saidWet + " (real minus said height, -3..+3: "
						+ java.util.Arrays.toString(by) + "), only real " + wasWet + "; dry " + dry + ", height off " + String.format("%.2f", off / (double)Math.max(1, dry))
						+ " on average, " + worst + " at worst (real minus said, -5..+5: " + java.util.Arrays.toString(signed) + "); sea level " + level.getSeaLevel();
					DrifterCivilizations.LOGGER.info("[sketch] {}", text);
					ctx.getSource().sendSuccess(() -> Component.literal(text), false);
					return 1;
				}))
				// Debug: every sketch looked over for things that shouldn't be (0 wanted of each).
				.then(Commands.literal("check").executes(ctx -> {
					Predicted predicted = land;
					int wetBoxes = 0;
					int onStreet = 0;
					int overlapping = 0;
					int noStreets = 0;
					int houses = 0;
					Map<String, Integer> shapes = new java.util.TreeMap<>();
					for (Sketch sketch : MADE.values()) {
						shapes.merge(sketch.shape(), 1, Integer::sum);
						houses += sketch.boxes().size();
						if (sketch.streets().isEmpty()) {
							noStreets++;
						}

						for (int i = 0; i < sketch.boxes().size(); i++) {
							Sketch.Box box = sketch.boxes().get(i);
							if (predicted != null && predicted.wet((box.x0() + box.x1()) / 2, (box.z0() + box.z1()) / 2)) {
								wetBoxes++;
							}

							for (int j = i + 1; j < sketch.boxes().size(); j++) {
								Sketch.Box other = sketch.boxes().get(j);
								if (box.x1() >= other.x0() && box.x0() <= other.x1() && box.z1() >= other.z0() && box.z0() <= other.z1()) {
									overlapping++;
								}
							}

							street:
							for (int[] street : sketch.streets()) {
								for (int k = 1; k + 1 < street.length; k += 2) {
									if (box.contains(street[k], street[k + 1])) {
										onStreet++;
										break street;
									}
								}
							}
						}
					}

					String text = MADE.size() + " sketches of " + WorldPlan.places.size() + " places" + (ready ? "" : " (still being made)") + ", " + houses
						+ " houses; in water " + wetBoxes + ", on a street " + onStreet + ", overlapping " + overlapping + ", places with no streets " + noStreets
						+ "; shapes " + shapes;
					DrifterCivilizations.LOGGER.info("[sketch] check: {}", text);
					ctx.getSource().sendSuccess(() -> Component.literal(text), false);
					return 1;
				})))));
	}
}
