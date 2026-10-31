package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Towns where villages would be (milestone 23, docs/LIVED_WORLD.md A1, A2 and A11):
 * <ul>
 *   <li>World generation: a {@code drifter_civ:town} structure in village biomes, spaced 2.5 times further apart than
 *       villages (54 chunks, 16 at least: A4), and no villages (Drifter's own hook, {@code DrifterApi.setTownsForVillages}),
 *       no wandering trader, no zombie villagers unless the setting allows them.</li>
 *   <li>The spot (A11, the user: "preferred to be on flattest land given the choice"): the land round where it would go
 *       looked over from the generator's own heights, and the town put on the flattest open ground within about seven
 *       chunks; nothing flat enough, no town.</li>
 *   <li>Nothing is built while the chunk generates: a marker records the spot. When a player comes within about twelve
 *       chunks, the town's land is loaded (out of sight, in the background) and the town made a step at a time
 *       ({@link TownBuilder.Job}), a little each tick, never a stall.</li>
 * </ul>
 * The spots not made yet are kept with the towns.
 */
final class TownSites {
	private TownSites() {
	}

	/** A spot for a town, made or not yet. */
	static final class Site {
		final String dimension;
		final int x;
		final int z;
		final int households;
		/** A town that failed long ago (nobody lives there). */
		boolean ruin;
		/** Its land asked for (loading). */
		boolean loading;
		/** Debug: made now, player or not. */
		boolean forced;
		/** A town of the world plan (W4): its id there. */
		@Nullable
		java.util.UUID planned;

		Site(String dimension, int x, int z, int households) {
			this.dimension = dimension;
			this.x = x;
			this.z = z;
			this.households = households;
		}

		ChunkPos chunk() {
			return new ChunkPos(this.x >> 4, this.z >> 4);
		}

		boolean city() {
			return this.households >= TownBuilder.CITY;
		}

		int loadRadius() {
			return this.city() ? CITY_LOAD_RADIUS : LOAD_RADIUS;
		}
	}

	/** A city's made this much further off than a town (bigger, seen from further). */
	private static final int CITY_MORE = 128;
	/** Chunks loaded round a town's middle while it's made (its reach, and what's looked at round it). */
	private static final int LOAD_RADIUS = TownBuilder.MOST_REACH / 16 + 2;
	private static final int CITY_LOAD_RADIUS = TownBuilder.CITY_MOST_REACH / 16 + 2;
	/** Work done on a town each tick at most (it goes on next tick). */
	private static final long BUDGET_NANOS = 10_000_000L;
	/** While the world's built before it's played (nobody playing yet): more a tick. */
	private static final long BUILDING_BUDGET_NANOS = 40_000_000L;
	/**
	 * Sizes (A4, the user: "small town variations ... like 8-16 household towns"): a small town of 8 to 16 households, or now
	 * and then (a quarter of spots) a hamlet of 2 to 5. Cities (40 to 80) and ruins come later.
	 */
	static final int FEWEST = 8;
	static final int MOST = 16;
	static final double HAMLETS = 0.25;
	static final int HAMLET_FEWEST = 2;
	static final int HAMLET_MOST = 5;
	/** A twentieth of spots: the ruins of a town that failed (4 to 7 houses). */
	static final double RUINS = 0.05;
	/**
	 * Cities (A4: "40-80 households, 100 Drifters at most", "never within about 2,000 blocks of another city"): one spot in
	 * each three by three of spots (about 2,600 blocks across), picked from the world's seed, is a city if a town goes there.
	 */
	static final int CITY_FEWEST = 40;
	static final int CITY_MOST = 80;
	private static final int SPACING = 54;

	private static final TicketType<ChunkPos> TICKET = TicketType.create("drifter_civ_town", Comparator.comparingLong(ChunkPos::toLong));
	private static final ConcurrentLinkedQueue<Site> found = new ConcurrentLinkedQueue<>();
	private static final List<Site> sites = new ArrayList<>();
	@Nullable
	private static TownBuilder.Job job;
	@Nullable
	private static Site making;

	static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("drifter_civ", "town");
	static StructureType<TownStructure> TYPE;
	static StructurePieceType MARKER;

	static void register() {
		TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, ID, () -> TownStructure.CODEC);
		MARKER = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, ResourceLocation.fromNamespaceAndPath("drifter_civ", "town_marker"),
			(StructurePieceType.ContextlessType) Marker::new);
		DrifterApi.setTownsForVillages(true);
		// No zombie villagers turning up to cure (the setting can allow them: players' trading halls and iron farms).
		BiomeModifications.create(ResourceLocation.fromNamespaceAndPath("drifter_civ", "no_zombie_villagers")).add(ModificationPhase.REMOVALS,
			BiomeSelectors.all(), context -> {
				if (DrifterApi.noVillagers()) {
					context.getSpawnSettings().removeSpawnsOfEntityType(EntityType.ZOMBIE_VILLAGER);
				}
			});
		ServerTickEvents.END_SERVER_TICK.register(TownSites::tick);
		// The house plans read (and each one's door sides worked out) as the world opens, not the first time a town is made
		// out in it (found in testing: three seconds in one step).
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			long start = System.nanoTime();
			for (var plan : DrifterApi.housePlans(server)) {
				for (var rotation : net.minecraft.world.level.block.Rotation.values()) {
					DrifterApi.doorSides(server, plan.id(), rotation);
				}
			}

			DrifterCivilizations.LOGGER.info("[town] house plans ready for towns ({} ms)", (System.nanoTime() - start) / 1_000_000);
		});
	}

	// ---------------------------------------------------------------------------------------------
	// Each tick: spots found, land loaded for the near ones, the town being made
	// ---------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		boolean changed = false;
		for (Site site; (site = found.poll()) != null;) {
			Site one = site;
			if (sites.stream().noneMatch(s -> s.dimension.equals(one.dimension) && s.x == one.x && s.z == one.z)
				&& Towns.all().stream().noneMatch(town -> town.dimension.equals(one.dimension) && town.center.closerThan(new BlockPos(one.x, town.center.getY(),
					one.z), 64))) {
				sites.add(site);
				changed = true;
			}
		}

		if (changed) {
			Towns.save(server);
		}

		// The one being made: a little more of it.
		if (job != null) {
			long start = System.nanoTime();
			long budget = WorldBuild.building ? BUILDING_BUDGET_NANOS : BUDGET_NANOS;
			while (!job.done() && job.step() && System.nanoTime() - start < budget) {
			}

			if (job.done()) {
				done(server);
			}

			// (The next town's land is asked for as this one's made: each waited a minute for its land after the last.)
		}

		if (server.getTickCount() % 20 != 0 || sites.isEmpty()) {
			return;
		}

		// Nearest first (to a player, or to spawn while the world's built before it's played).
		List<Site> order = new ArrayList<>(sites);
		order.sort(Comparator.comparingDouble(site -> {
			ServerLevel level = level(server, site.dimension);
			return level == null ? Double.MAX_VALUE : WorldBuild.distance(level, site.x, site.z);
		}));
		for (Site site : order) {
			ServerLevel level = level(server, site.dimension);
			if (level == null || !site.forced && !near(level, site)) {
				continue;
			}

			// Its land: asked for (made out of sight by the generator's own threads), then, once all of it's there, the town.
			// (A town or two's land at a time besides the one being made, not every town ahead's at once.)
			if (!site.loading && sites.stream().filter(other -> other.loading && other != making).count() >= (WorldBuild.building ? 2 : 1)) {
				continue;
			}

			if (!site.loading) {
				level.getChunkSource().addRegionTicket(TICKET, site.chunk(), site.loadRadius(), site.chunk());
				site.loading = true;
				continue;
			}

			// (A town of the plan waits for the whole plan: its ways in come with its roads.)
			if (job != null || !loaded(level, site) || site.planned != null && !WorldPlan.ready) {
				continue;
			}

			making = site;
			WorldPlan.Place place = WorldPlan.place(site.planned);
			// (Its past may have moved on since its land was asked for: fallen since, it's made a ruin.)
			if (place != null) {
				site.ruin = place.ruin();
			}

			job = new TownBuilder.Job(level, site.x, site.z, site.households, place != null ? place.name : null, true, site.ruin);
			if (place != null) {
				job.plannedId = place.id;
				job.gates = List.copyOf(place.gates);
				// (As its past has it: its leader now, founded so long ago.)
				job.leaderName = place.leader.isEmpty() ? null : place.leader;
				job.pastAgo = WorldPlan.foundedAgo(place.id);
			}

			DrifterCivilizations.LOGGER.info("[town] making a town at {}, {} ({} households)", site.x, site.z, site.households);
			return;
		}
	}

	private static void done(MinecraftServer server) {
		Site site = making;
		TownBuilder.Job finished = job;
		job = null;
		making = null;
		if (site == null || finished == null) {
			return;
		}

		sites.remove(site);
		ServerLevel level = level(server, site.dimension);
		if (level != null) {
			level.getChunkSource().removeRegionTicket(TICKET, site.chunk(), site.loadRadius(), site.chunk());
		}

		DrifterCivilizations.LOGGER.info("[town] {}", finished.result().message());
		if (site.planned != null) {
			WorldPlan.made(server, site.planned, finished.result().town());
		}

		Towns.save(server);
	}

	/**
	 * Near enough to make: well out past what a player can see (the user, 2026-10-30: "i dont see everything unfold in my
	 * eyes"; it was twelve chunks, a town put up in plain view), a city further.
	 */
	private static boolean near(ServerLevel level, Site site) {
		return WorldBuild.distance(level, site.x, site.z) < WorldBuild.ahead(level.getServer()) + (site.city() ? CITY_MORE : 0);
	}

	private static boolean loaded(ServerLevel level, Site site) {
		ChunkPos middle = site.chunk();
		int radius = site.loadRadius();
		for (int dx = -radius + 1; dx <= radius - 1; dx++) {
			for (int dz = -radius + 1; dz <= radius - 1; dz++) {
				// (Really there, made and loaded: hasChunk only says it's meant to be, and the town would make it on the spot.)
				if (level.getChunkSource().getChunkNow(middle.x + dx, middle.z + dz) == null) {
					return false;
				}
			}
		}

		return true;
	}

	@Nullable
	private static ServerLevel level(MinecraftServer server, String dimension) {
		return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(dimension)));
	}

	/** The spots not made yet, nearest this first. */
	static List<Site> pending(String dimension, BlockPos from) {
		return sites.stream().filter(site -> site.dimension.equals(dimension))
			.sorted(Comparator.comparingDouble(site -> Math.hypot(site.x - from.getX(), site.z - from.getZ()))).toList();
	}

	/** A spot of the world plan (W4), made as a player comes near like any other. */
	static void planned(ServerLevel level, WorldPlan.Place place) {
		if (place.made) {
			return;
		}

		// (Asked for already, its land loading while the plan was made: a ruin now if its past says so.)
		for (Site site : sites) {
			if (place.id.equals(site.planned)) {
				site.ruin = place.ruin();
				return;
			}
		}

		Site site = new Site(level.dimension().location().toString(), place.x, place.z, place.households);
		site.ruin = place.ruin();
		site.planned = place.id;
		sites.add(site);
	}

	/** Debug: a spot for a town of this many households here, made now (no player needed). */
	static void debugSite(ServerLevel level, BlockPos at, int households) {
		Site site = new Site(level.dimension().location().toString(), at.getX(), at.getZ(), households);
		site.forced = true;
		sites.add(site);
	}

	/** Debug: the nearest spot not made yet made now (no player needed). */
	@Nullable
	static Site makeNearest(ServerLevel level, BlockPos from) {
		String dimension = level.dimension().location().toString();
		List<Site> list = pending(dimension, from);
		if (!list.isEmpty()) {
			list.get(0).forced = true;
			return list.get(0);
		}

		// None found yet (their chunks not made): the nearest town structure, its start read for where the town goes.
		var registry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
		var holder = registry.getHolder(ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, ID)).orElse(null);
		if (holder == null) {
			return null;
		}

		var nearest = level.getChunkSource().getGenerator().findNearestMapStructure(level, net.minecraft.core.HolderSet.direct(holder), from, 100, false);
		if (nearest == null) {
			return null;
		}

		ChunkPos at = new ChunkPos(nearest.getFirst());
		var chunk = level.getChunk(at.x, at.z, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
		var start = chunk.getStartForStructure(holder.value());
		if (start == null || !start.isValid()) {
			return null;
		}

		for (StructurePiece piece : start.getPieces()) {
			if (piece instanceof Marker marker) {
				BoundingBox box = marker.getBoundingBox();
				Site site = new Site(dimension, box.minX(), box.minZ(), marker.households);
				site.ruin = marker.ruin;
				site.forced = true;
				sites.add(site);
				return site;
			}
		}

		return null;
	}

	@Nullable
	static String making() {
		return making == null ? null : making.x + ", " + making.z;
	}

	// ---------------------------------------------------------------------------------------------
	// The structure: where a town goes (A1, A11)
	// ---------------------------------------------------------------------------------------------

	/**
	 * How far a town may move from where it would go to find flat ground (blocks each way), the grid its land is looked
	 * over on, and the reach of its core (the plaza and the first houses round it) and of the whole of it.
	 */
	private static final int MOVE = 112;
	private static final int GRID = 24;
	private static final int CORE = 48;
	private static final int WHOLE = 72;
	/** Most its core's ground may rise and fall (blocks) for a town to go there at all; and the whole town's, much more. */
	private static final int CORE_RISE = 12;
	private static final int WHOLE_RISE = 64;
	/** Most of its ground that may be water. */
	private static final double MOST_WET = 0.25;

	/**
	 * A town spot, or (with {@code "city": true}) a city spot: two structures on the same spots (their sets placed alike), the
	 * town one never where a city goes and the city one only there, so each can be found on its own (the user: "is there a
	 * way we can separate the command to locate towns and cities?": {@code /locate structure drifter_civ:town} and
	 * {@code drifter_civ:city}; both: {@code #drifter_civ:settlements}).
	 */
	public static final class TownStructure extends Structure {
		public static final MapCodec<TownStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(settingsCodec(instance),
			Codec.BOOL.optionalFieldOf("city", false).forGetter(structure -> structure.city)).apply(instance, TownStructure::new));

		private final boolean city;

		public TownStructure(StructureSettings settings, boolean city) {
			super(settings);
			this.city = city;
		}

		@Override
		public StructureType<?> type() {
			return TYPE;
		}

		@Override
		protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
			// (Whether it's a city's spot first: cheap, and the other one's spots aren't looked over at all.)
			// (A civilized world's towns go where its plan says: WorldPlan.)
			if (!DrifterApi.villagesReplaced() || WorldPlan.planned || cityCell(context) != this.city) {
				return Optional.empty();
			}

			BlockPos spot = flattest(context);
			if (spot == null) {
				return Optional.empty();
			}

			double roll = context.random().nextDouble();
			if (this.city) {
				int households = CITY_FEWEST + context.random().nextInt(CITY_MOST - CITY_FEWEST + 1);
				return Optional.of(new GenerationStub(spot, builder -> builder.addPiece(new Marker(spot, households, false))));
			}

			boolean ruin = roll < RUINS;
			int households = ruin ? 4 + context.random().nextInt(4) : roll < RUINS + HAMLETS ? HAMLET_FEWEST + context.random().nextInt(HAMLET_MOST - HAMLET_FEWEST + 1)
				: FEWEST + context.random().nextInt(MOST - FEWEST + 1);
			return Optional.of(new GenerationStub(spot, builder -> builder.addPiece(new Marker(spot, households, ruin))));
		}
	}

	/** This spot is the one in its three by three of spots that's a city (picked from the world's seed). */
	private static boolean cityCell(Structure.GenerationContext context) {
		int i = Math.floorDiv(context.chunkPos().x, SPACING);
		int j = Math.floorDiv(context.chunkPos().z, SPACING);
		int regionI = Math.floorDiv(i, 3);
		int regionJ = Math.floorDiv(j, 3);
		long hash = context.seed() ^ (regionI * 341873128712L + regionJ * 132897987541L) ^ 0x5DEECE66DL;
		hash = (hash ^ (hash >>> 29)) * 0xBF58476D1CE4E5B9L;
		hash ^= hash >>> 32;
		int chosen = (int)Math.floorMod(hash, 9L);
		return Math.floorMod(i, 3) * 3 + Math.floorMod(j, 3) == chosen;
	}

	/** Good enough to stop looking: this flat at the core and over the whole of it. */
	private static final int FLAT_CORE = 4;
	private static final int FLAT_WHOLE = 20;

	/**
	 * The flattest open ground in a town's biome within reach of where it would go, or null (nowhere flat enough). The
	 * nearest spots first; the land's heights worked out only as they're needed (a core too rough isn't looked at further),
	 * and the first spot that's flat enough taken (found in testing: every height round worked out, /locate took 5 s).
	 */
	@Nullable
	private static BlockPos flattest(Structure.GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		int x0 = chunk.getMiddleBlockX();
		int z0 = chunk.getMiddleBlockZ();
		Heights heights = new Heights(context, x0 - MOVE - WHOLE, z0 - MOVE - WHOLE, 2 * (MOVE + WHOLE) / GRID + 1);
		int moveCells = MOVE / GRID;
		int coreCells = CORE / GRID;
		int wholeCells = WHOLE / GRID;
		int middle = (MOVE + WHOLE) / GRID;
		List<int[]> candidates = new ArrayList<>();
		for (int di = -moveCells; di <= moveCells; di++) {
			for (int dj = -moveCells; dj <= moveCells; dj++) {
				candidates.add(new int[] {middle + di, middle + dj});
			}
		}

		candidates.sort(Comparator.comparingInt(c -> (c[0] - middle) * (c[0] - middle) + (c[1] - middle) * (c[1] - middle)));
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		for (int[] c : candidates) {
			int ci = c[0];
			int cj = c[1];
			// (Its biome first, asked high above the land (its surface biome there, however high the land: lower down, under a
			// hill, it's a cave's), before any of the land's heights are worked out.)
			if (!inBiome(context, heights.x(ci), context.heightAccessor().getMaxBuildHeight() - 1, heights.z(cj)) || heights.wet(ci, cj)) {
				continue;
			}

			// The core first: too rough or wet, nothing more worked out here.
			int coreLow = Integer.MAX_VALUE;
			int coreHigh = Integer.MIN_VALUE;
			boolean coreWet = false;
			for (int di = -coreCells; di <= coreCells && !coreWet; di++) {
				for (int dj = -coreCells; dj <= coreCells; dj++) {
					if (di * di + dj * dj <= coreCells * coreCells) {
						int h = heights.ground(ci + di, cj + dj);
						coreWet |= heights.wet(ci + di, cj + dj);
						coreLow = Math.min(coreLow, h);
						coreHigh = Math.max(coreHigh, h);
					}
				}
			}

			int coreRise = coreHigh - coreLow;
			if (coreWet || coreRise > CORE_RISE) {
				continue;
			}

			int low = Integer.MAX_VALUE;
			int high = Integer.MIN_VALUE;
			int cells = 0;
			int wetCells = 0;
			long sum = 0;
			for (int di = -wholeCells; di <= wholeCells; di++) {
				for (int dj = -wholeCells; dj <= wholeCells; dj++) {
					if (di * di + dj * dj <= wholeCells * wholeCells) {
						int h = heights.ground(ci + di, cj + dj);
						cells++;
						wetCells += heights.wet(ci + di, cj + dj) ? 1 : 0;
						sum += h;
						low = Math.min(low, h);
						high = Math.max(high, h);
					}
				}
			}

			if (high - low > WHOLE_RISE || wetCells > MOST_WET * cells) {
				continue;
			}

			// (How rough its ground is: the core most, the whole of it some, water a little, and how far it moved.)
			double mean = sum / (double)cells;
			double rough = 0.0;
			for (int di = -wholeCells; di <= wholeCells; di++) {
				for (int dj = -wholeCells; dj <= wholeCells; dj++) {
					if (di * di + dj * dj <= wholeCells * wholeCells) {
						rough += Math.abs(heights.ground(ci + di, cj + dj) - mean);
					}
				}
			}

			int x = heights.x(ci);
			int z = heights.z(cj);
			int y = heights.ground(ci, cj);
			double score = 3.0 * coreRise + rough / cells + 20.0 * wetCells / cells + Math.hypot(x - x0, z - z0) / 64.0;
			if (score < bestScore) {
				bestScore = score;
				best = new BlockPos(x, y, z);
				if (coreRise <= FLAT_CORE && high - low <= FLAT_WHOLE) {
					break;
				}
			}
		}

		return best;
	}

	/** The land's heights on the grid, worked out from the generator as they're asked for. */
	private static final class Heights {
		private final Structure.GenerationContext context;
		private final int x0;
		private final int z0;
		private final int[][] ground;
		private final byte[][] wet;

		Heights(Structure.GenerationContext context, int x0, int z0, int n) {
			this.context = context;
			this.x0 = x0;
			this.z0 = z0;
			this.ground = new int[n][n];
			this.wet = new byte[n][n];
		}

		int x(int i) {
			return this.x0 + i * GRID;
		}

		int z(int j) {
			return this.z0 + j * GRID;
		}

		int ground(int i, int j) {
			this.fill(i, j);
			return this.ground[i][j];
		}

		boolean wet(int i, int j) {
			this.fill(i, j);
			return this.wet[i][j] == 2;
		}

		private void fill(int i, int j) {
			if (this.wet[i][j] != 0) {
				return;
			}

			// (One height each: the ground under any water; at or under the sea's level it's taken to be wet. Found on the
			// user's Tectonic terrain: each height is worked out down from the top of a very tall world, and two a spot made
			// /locate take most of a minute.)
			ChunkGenerator generator = this.context.chunkGenerator();
			int floor = generator.getBaseHeight(this.x(i), this.z(j), Heightmap.Types.OCEAN_FLOOR_WG, this.context.heightAccessor(),
				this.context.randomState());
			this.ground[i][j] = floor;
			this.wet[i][j] = (byte)(floor <= generator.getSeaLevel() ? 2 : 1);
		}
	}

	private static boolean inBiome(Structure.GenerationContext context, int x, int y, int z) {
		return context.validBiome().test(context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
			context.randomState().sampler()));
	}

	/** Its mark in the world: nothing placed; it tells the addon where the town goes when its chunk is made. */
	public static final class Marker extends StructurePiece {
		private final int households;
		private final boolean ruin;

		Marker(BlockPos spot, int households, boolean ruin) {
			super(MARKER, 0, new BoundingBox(spot.getX(), spot.getY(), spot.getZ(), spot.getX(), spot.getY(), spot.getZ()));
			this.households = households;
			this.ruin = ruin;
		}

		Marker(CompoundTag tag) {
			super(MARKER, tag);
			this.households = tag.getInt("Households");
			this.ruin = tag.getBoolean("Ruin");
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
			tag.putInt("Households", this.households);
			tag.putBoolean("Ruin", this.ruin);
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
			ChunkPos chunk, BlockPos pivot) {
			BoundingBox box = this.getBoundingBox();
			if (chunkBox.isInside(box.getCenter())) {
				Site site = new Site(level.getLevel().dimension().location().toString(), box.minX(), box.minZ(), this.households);
				site.ruin = this.ruin;
				found.add(site);
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the towns
	// ---------------------------------------------------------------------------------------------

	static void save(CompoundTag tag) {
		ListTag list = new ListTag();
		for (Site site : sites) {
			// (A plan's spot before the plan's saved with it: the plan's made again on opening, and its spots with it.)
			if (site.planned != null && !WorldPlan.ready) {
				continue;
			}

			CompoundTag one = new CompoundTag();
			one.putString("Dimension", site.dimension);
			one.putInt("X", site.x);
			one.putInt("Z", site.z);
			one.putInt("Households", site.households);
			one.putBoolean("Ruin", site.ruin);
			if (site.planned != null) {
				one.putUUID("Planned", site.planned);
			}

			list.add(one);
		}

		tag.put("Sites", list);
	}

	static void load(CompoundTag tag) {
		sites.clear();
		found.clear();
		job = null;
		making = null;
		for (Tag t : tag.getList("Sites", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag)t;
			Site site = new Site(one.getString("Dimension"), one.getInt("X"), one.getInt("Z"), one.getInt("Households"));
			site.ruin = one.getBoolean("Ruin");
			site.planned = one.hasUUID("Planned") ? one.getUUID("Planned") : null;
			sites.add(site);
		}
	}
}
