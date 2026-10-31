package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * The civilized world (milestone 23, docs/LIVED_WORLD.md W4 and W5; the user, 2026-10-30: "a big city in the middle, with
 * towns in the outskirts, and all the roads are connected. With like long roads connected to different nations across the
 * world", "the civilization to be limited, and have like a max amount of nations and towns"). Made once, as a new world
 * first starts, in the background, from the world generator's own heights (nothing built): a few nations round spawn (the
 * settings: how many, how spread out), each a capital city on the best ground of its land with towns in a ring round it
 * and hamlets further out; free towns between nations; a ruin or two; and roads between them all, routed over the land
 * (W5: {@link Highways} builds them as players come near). Each town is made as a player comes near, as before
 * ({@link TownSites}), in its nation already (made with the world, {@link Nations#planned}). Past them the world stays wild.
 * Old worlds (and the setting off): towns where villages would be (A1).
 */
final class WorldPlan {
	private WorldPlan() {
	}

	/** A settlement in the plan: where, how big, what it's called, its nation (null: free), and the ways its roads come in. */
	static final class Place {
		final UUID id;
		final String name;
		final String kind;
		final int x;
		final int z;
		final int households;
		@Nullable
		final UUID nation;
		boolean made;
		/** The ways (radians, from its middle) its roads arrive from: when it's made, a main road goes out each way to meet them. */
		final List<Double> gates = new ArrayList<>();

		Place(UUID id, String name, String kind, int x, int z, int households, @Nullable UUID nation) {
			this.id = id;
			this.name = name;
			this.kind = kind;
			this.x = x;
			this.z = z;
			this.households = households;
			this.nation = nation;
		}

		boolean ruin() {
			return this.kind.equals("ruin");
		}

		/** How far out from its middle its own roads go (the roads between towns begin past it). */
		int reach() {
			return this.kind.equals("city") ? 150 : this.kind.equals("hamlet") ? 70 : 95;
		}
	}

	/** A nation in the plan (made at once: {@link Nations#planned}). */
	record PlannedNation(UUID id, String name, UUID capital, DyeColor colour) {
	}

	/** A road between two places: its line (x, z every few blocks), from one's edge to the other's, and which stretches are built. */
	static final class Highway {
		final UUID from;
		final UUID to;
		final List<int[]> points;
		final boolean[] built;

		Highway(UUID from, UUID to, List<int[]> points) {
			this.from = from;
			this.to = to;
			this.points = points;
			this.built = new boolean[Math.max(1, (points.size() + Highways.PIECE - 1) / Highways.PIECE)];
		}
	}

	/** This world is a planned one (decided as it loads, before any of it generates). */
	static volatile boolean planned;
	/** The plan's made (places and roads). */
	static volatile boolean ready;
	private static volatile boolean making;
	static final List<Place> places = new ArrayList<>();
	static final List<PlannedNation> nations = new ArrayList<>();
	static final List<Highway> highways = new ArrayList<>();
	private static final TagKey<Biome> TOWN_BIOMES = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("drifter_civ", "has_structure/town"));
	private static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> SETTLEMENTS = TagKey.create(Registries.STRUCTURE,
		ResourceLocation.fromNamespaceAndPath("drifter_civ", "settlements"));

	/** Capitals this far from spawn (the first ring; the second for many nations further), times the spread. */
	private static final int RING = 1100;
	private static final int OUTER_RING = 2300;
	/** No capital within this of another (times the spread). */
	private static final int CAPITALS_APART = 1100;
	/** A nation's towns this far from its capital (times the root of the spread); its hamlets this far past them. */
	private static final int TOWNS_FROM = 550;
	private static final int TOWNS_TO = 950;
	/** Nothing made within this of spawn: you start near a nation, not in it (the user's choice). */
	private static final int CLEAR_OF_SPAWN = 300;
	/**
	 * The land's grid: everything the plan asks of the generator is asked at the middle of a cell this wide, once (a height
	 * on the user's Tectonic is slow to work out: the first plan took 200 s asking at every spot); and how many cells a road's
	 * search may look at.
	 */
	static final int CELL = 32;
	/**
	 * A road's search: its own finer grid (at 32 a road went straight up a mountain and over the sea, the user's pictures, 2026-10-30:
	 * "going AROUND hills and mountains for the easiest way there"), and how many cells it may look at.
	 */
	static final int ROAD_CELL = 16;
	/** A road longer than this is searched for on the plan's own grid ({@link #CELL}). */
	private static final int LONG_ROAD = 1200;
	private static final int MOST_NODES = 25_000;
	/** A road's search leans this much on the straight-line distance left (finds a near-cheapest way, far quicker: 78 s for a world's roads at 1). */
	private static final double GREED = 1.6;
	/** How much a road's search leans on {@link Plan#wander} (a step costs up to this many times more), and how wide its swells. */
	private static final double WANDER = 1.5;
	private static final int WANDER_SIZE = 96;
	/** Two nations' nearest towns further apart than this (times the spread) have no road between them. */
	private static final int BETWEEN_NATIONS = 1600;
	/** A road whose way round is longer than this many times the straight line (and 200) isn't made: round a sea, a gulf. */
	private static final double MOST_ROUND = 1.7;

	static void register() {
		ServerWorldEvents.LOAD.register((server, level) -> {
			if (level.dimension() == Level.OVERWORLD) {
				decide(level);
			}
		});
		ServerWorldEvents.UNLOAD.register((server, level) -> {
			if (level.dimension() == Level.OVERWORLD) {
				planned = false;
				ready = false;
				places.clear();
				nations.clear();
				highways.clear();
			}
		});
		DrifterApi.setNotPlaced(structure -> planned && structure.is(SETTLEMENTS));
	}

	/**
	 * Planned or not, as the overworld loads (before its first chunks generate): a world with a plan saved is; a brand new
	 * one (no time gone by, nothing saved) is if the setting's on; any other (made before the plan, or with it off) isn't.
	 */
	private static void decide(ServerLevel overworld) {
		CompoundTag saved = Towns.savedTag(overworld);
		planned = saved.getBoolean("Planned") || overworld.getGameTime() == 0 && !saved.contains("Towns") && DrifterApi.civilizedWorld();
		DrifterCivilizations.LOGGER.info("[plan] this world is {}", planned ? "a civilized world (nations made with it)" : "not planned (towns where villages would be)");
	}

	/** As the world's towns load: the plan made if this world's planned and it isn't yet (in the background). */
	static void start(MinecraftServer server) {
		if (!planned || ready || making) {
			return;
		}

		making = true;
		ServerLevel level = server.overworld();
		BlockPos spawn = level.getSharedSpawnPos();
		int count = nationsWanted(level.getSeed());
		double spread = 1 + (DrifterApi.spreadSetting() - 1) * 0.75;
		Thread thread = new Thread(() -> {
			long started = System.nanoTime();
			try {
				Plan plan = new Plan(level, spawn, count, spread);
				plan.make();
				server.execute(() -> apply(server, plan, (System.nanoTime() - started) / 1_000_000));
			} catch (Throwable e) {
				DrifterCivilizations.LOGGER.error("[plan] couldn't make the world plan", e);
				making = false;
			}
		}, "Drifter world plan");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	private static int nationsWanted(long seed) {
		Random random = new Random(seed ^ 0x6E6174696F6EL);
		return switch (DrifterApi.nationsSetting()) {
			case 1 -> 2 + random.nextInt(2);
			case 3 -> 6 + random.nextInt(3);
			default -> 4;
		};
	}

	/** The plan's made: its places are spots towns go, its nations made, its roads waiting to be built. */
	private static void apply(MinecraftServer server, Plan plan, long millis) {
		places.clear();
		places.addAll(plan.places);
		nations.clear();
		nations.addAll(plan.nations);
		highways.clear();
		highways.addAll(plan.highways);
		for (Place place : places) {
			TownSites.planned(server.overworld(), place);
		}

		for (PlannedNation nation : nations) {
			List<UUID> towns = places.stream().filter(p -> nation.id().equals(p.nation) && !p.ruin()).map(p -> p.id).toList();
			Nations.planned(server, nation, towns);
		}

		ready = true;
		making = false;
		Towns.save(server);
		long cities = places.stream().filter(p -> p.kind.equals("city")).count();
		DrifterCivilizations.LOGGER.info("[plan] {} nations, {} places ({} cities), {} roads ({} blocks); made in {} ms", nations.size(), places.size(), cities,
			highways.size(), highways.stream().mapToInt(h -> h.points.size() * Plan.STEP).sum(), millis);
		Chronicle.add(server, "plan", "The world has " + nations.size() + " nations: " + String.join(", ", nations.stream().map(PlannedNation::name).toList()));
	}

	@Nullable
	static Place place(@Nullable UUID id) {
		if (id == null) {
			return null;
		}

		for (Place place : places) {
			if (place.id.equals(id)) {
				return place;
			}
		}

		return null;
	}

	@Nullable
	static PlannedNation nation(@Nullable UUID id) {
		for (PlannedNation nation : nations) {
			if (nation.id().equals(id)) {
				return nation;
			}
		}

		return null;
	}

	/** A planned town's just been made: in its nation (at peace and allied with its fellows already made). */
	static void made(MinecraftServer server, UUID id, @Nullable Town town) {
		Place place = place(id);
		if (place == null) {
			return;
		}

		place.made = true;
		if (town != null && place.nation != null) {
			Nations.plannedMade(server, place.nation, town);
		}

		Towns.save(server);
	}

	/** The nearest place of this kind ("city", "town", "hamlet", "ruin"; null: any) to here. */
	@Nullable
	static Place nearest(BlockPos from, @Nullable String kind) {
		Place best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Place place : places) {
			double distance = Math.hypot(place.x - from.getX(), place.z - from.getZ());
			if ((kind == null || place.kind.equals(kind)) && distance < bestDistance) {
				best = place;
				bestDistance = distance;
			}
		}

		return best;
	}

	// ---------------------------------------------------------------------------------------------
	// Making it (in the background)
	// ---------------------------------------------------------------------------------------------

	/**
	 * Sizes of spot: how far its core and its whole reach, and how much each may rise and fall (the core by the land's real
	 * heights, the whole roughly). Flat land only (the user, 2026-10-30, of a capital on a mountain cut into cliffs: "idk if a
	 * city on a mountain or hill is gonna work"): it was 16 and 72 for a city, and twice that if nothing suited; now a place
	 * that finds no flat land near where it was meant to be looks further, and then isn't made.
	 */
	private record Size(int core, int coreRise, int whole, int wholeRise) {
		static final Size CITY = new Size(64, 10, 128, 36);
		static final Size TOWN = new Size(44, 8, 72, 26);
		static final Size HAMLET = new Size(28, 6, 48, 20);
	}

	/** The real heights over a spot's core are looked at this far apart; two of them further apart than {@link #CLIFF} is a cliff in it. */
	private static final int FINE = 16;
	private static final int CLIFF = 5;
	/** A place's core is at most this far over the sea. */
	private static final int LOWLAND = 24;
	/** A place looks for flat land at most this far from where it was meant to be. */
	private static final int MOST_MOVE = 1000;

	private static final class Plan {
		/** Points along a road this far apart. */
		static final int STEP = 4;
		private final ServerLevel level;
		private final ChunkGenerator generator;
		private final RandomState randomState;
		private final BlockPos spawn;
		private final int count;
		private final double spread;
		private final Random random;
		final List<Place> places = new ArrayList<>();
		final List<PlannedNation> nations = new ArrayList<>();
		final List<Highway> highways = new ArrayList<>();
		private final Set<String> names = new HashSet<>();
		private final Map<Long, int[]> heights = new HashMap<>();

		Plan(ServerLevel level, BlockPos spawn, int count, double spread) {
			this.level = level;
			this.generator = level.getChunkSource().getGenerator();
			this.randomState = level.getChunkSource().randomState();
			this.spawn = spawn;
			this.count = count;
			this.spread = spread;
			this.random = new Random(level.getSeed() ^ 0x77726C64706CL);
		}

		void make() {
			long start = System.nanoTime();
			this.capitals();
			long capitals = System.nanoTime();
			for (PlannedNation nation : List.copyOf(this.nations)) {
				this.towns(nation);
			}

			this.freeTowns();
			this.ruins();
			long towns = System.nanoTime();
			this.roads();
			DrifterCivilizations.LOGGER.info("[plan] capitals {} ms, towns {} ms, roads {} ms; {} heights on its grid, {} in all ({} ms)", (capitals - start)
				/ 1_000_000, (towns - capitals) / 1_000_000, (System.nanoTime() - towns) / 1_000_000, this.heights.size(), this.realHeights.size(),
				this.realNanos / 1_000_000);
		}

		// ----- Where things go -----

		private void capitals() {
			double turn = this.random.nextDouble() * 2 * Math.PI;
			int inner = this.count > 5 ? (this.count + 1) / 2 : this.count;
			List<DyeColor> colours = new ArrayList<>(List.of(DyeColor.RED, DyeColor.BLUE, DyeColor.YELLOW, DyeColor.ORANGE, DyeColor.MAGENTA,
				DyeColor.LIGHT_BLUE, DyeColor.PINK, DyeColor.LIGHT_GRAY));
			java.util.Collections.shuffle(colours, this.random);
			for (int i = 0; i < this.count; i++) {
				boolean outer = i >= inner;
				int k = outer ? i - inner : i;
				int ofRing = outer ? this.count - inner : inner;
				int[] spot = null;
				// (A few tries round its part of the ring: the first try found no spot for half the nations on hilly land.)
				for (int attempt = 0; attempt < 8 && spot == null; attempt++) {
					double angle = turn + 2 * Math.PI * k / ofRing + (outer ? Math.PI / ofRing : 0) + (this.random.nextDouble() - 0.5) * (0.4 + 0.3 * attempt);
					double r = (outer ? OUTER_RING : RING) * this.spread * (0.75 + 0.15 * attempt * this.random.nextDouble() + 0.2 * this.random.nextDouble());
					spot = this.findSpot(this.spawn.getX() + (int)(Math.cos(angle) * r), this.spawn.getZ() + (int)(Math.sin(angle) * r), 480 + 60 * attempt, Size.CITY);
					if (spot != null && this.near(spot, CAPITALS_APART * this.spread, "city")) {
						spot = null;
					}
				}

				if (spot == null) {
					continue;
				}

				String name = this.name();
				UUID capital = UUID.randomUUID();
				PlannedNation nation = new PlannedNation(UUID.randomUUID(), this.nationName(name), capital, colours.get(this.nations.size() % colours.size()));
				this.nations.add(nation);
				this.places.add(new Place(capital, name, "city", spot[0], spot[1], 40 + this.random.nextInt(41), nation.id()));
			}
		}

		private void towns(PlannedNation nation) {
			Place capital = this.places.stream().filter(p -> p.id.equals(nation.capital())).findFirst().orElseThrow();
			int towns = 3 + this.random.nextInt(3);
			double turn = this.random.nextDouble() * 2 * Math.PI;
			double far = Math.sqrt(this.spread);
			List<Place> mine = new ArrayList<>();
			for (int i = 0; i < towns; i++) {
				int[] spot = null;
				// (A few tries each, swinging round and in or out: one capital by the sea got none at the first try.)
				for (int attempt = 0; attempt < 4 && spot == null; attempt++) {
					double angle = turn + 2 * Math.PI * i / towns + (this.random.nextDouble() - 0.5) * (0.5 + 0.4 * attempt);
					double r = (TOWNS_FROM + this.random.nextDouble() * (TOWNS_TO - TOWNS_FROM)) * far;
					spot = this.findSpot(capital.x + (int)(Math.cos(angle) * r), capital.z + (int)(Math.sin(angle) * r), 240, Size.TOWN);
					if (spot != null && (this.near(spot, Towns.MIN_SPACING, null) || this.nearerAnother(spot, capital))) {
						spot = null;
					}
				}

				if (spot == null) {
					continue;
				}

				Place town = new Place(UUID.randomUUID(), this.name(), "town", spot[0], spot[1], TownSites.FEWEST + this.random.nextInt(TownSites.MOST
					- TownSites.FEWEST + 1), nation.id());
				this.places.add(town);
				mine.add(town);
			}

			// Hamlets out past some of its towns.
			int hamlets = Math.min(mine.size(), 2 + this.random.nextInt(3));
			for (int i = 0; i < hamlets; i++) {
				Place town = mine.get(i);
				double out = Math.atan2(town.z - capital.z, town.x - capital.x) + (this.random.nextDouble() - 0.5) * 1.2;
				double r = (300 + this.random.nextDouble() * 150) * far;
				int[] spot = this.findSpot(town.x + (int)(Math.cos(out) * r), town.z + (int)(Math.sin(out) * r), 160, Size.HAMLET);
				if (spot == null || this.near(spot, Towns.MIN_SPACING, null) || this.nearerAnother(spot, capital)) {
					continue;
				}

				this.places.add(new Place(UUID.randomUUID(), this.name(), "hamlet", spot[0], spot[1], TownSites.HAMLET_FEWEST + this.random.nextInt(
					TownSites.HAMLET_MOST - TownSites.HAMLET_FEWEST + 1), nation.id()));
			}
		}

		/** Two or three towns in no nation, between nations. */
		private void freeTowns() {
			List<Place> capitals = this.places.stream().filter(p -> p.kind.equals("city")).toList();
			int wanted = 2 + this.random.nextInt(2);
			for (int tries = 0; tries < 12 && wanted > 0 && capitals.size() >= 2; tries++) {
				Place a = capitals.get(this.random.nextInt(capitals.size()));
				Place b = capitals.stream().filter(p -> p != a).min(java.util.Comparator.comparingDouble(p -> Math.hypot(p.x - a.x, p.z - a.z))).orElseThrow();
				double side = Math.atan2(b.z - a.z, b.x - a.x) + Math.PI / 2;
				double off = (this.random.nextDouble() - 0.5) * 600 * this.spread;
				int[] spot = this.findSpot((a.x + b.x) / 2 + (int)(Math.cos(side) * off), (a.z + b.z) / 2 + (int)(Math.sin(side) * off), 260, Size.TOWN);
				if (spot == null || this.near(spot, 450, null)) {
					continue;
				}

				this.places.add(new Place(UUID.randomUUID(), this.name(), "town", spot[0], spot[1], TownSites.FEWEST + this.random.nextInt(TownSites.MOST
					- TownSites.FEWEST + 1), null));
				wanted--;
			}
		}

		/** A ruin or two: a town that failed (W6 will say how). */
		private void ruins() {
			int wanted = Math.max(1, this.nations.size() / 2);
			for (int tries = 0; tries < 10 && wanted > 0 && !this.places.isEmpty(); tries++) {
				Place near = this.places.get(this.random.nextInt(this.places.size()));
				double angle = this.random.nextDouble() * 2 * Math.PI;
				double r = 400 + this.random.nextDouble() * 400;
				int[] spot = this.findSpot(near.x + (int)(Math.cos(angle) * r), near.z + (int)(Math.sin(angle) * r), 200, Size.HAMLET);
				if (spot == null || this.near(spot, Towns.MIN_SPACING, null)) {
					continue;
				}

				this.places.add(new Place(UUID.randomUUID(), this.name(), "ruin", spot[0], spot[1], 4 + this.random.nextInt(4), null));
				wanted--;
			}
		}

		/** Something (of this kind; null: anything) within this of the spot, or the spot too near spawn. */
		private boolean near(int[] spot, double within, @Nullable String kind) {
			if (Math.hypot(spot[0] - this.spawn.getX(), spot[1] - this.spawn.getZ()) < CLEAR_OF_SPAWN) {
				return true;
			}

			return this.places.stream().anyMatch(p -> (kind == null || p.kind.equals(kind)) && Math.hypot(p.x - spot[0], p.z - spot[1]) < within);
		}

		/** Nearer another nation's capital than its own (it'd be theirs). */
		private boolean nearerAnother(int[] spot, Place capital) {
			double mine = Math.hypot(capital.x - spot[0], capital.z - spot[1]);
			return this.places.stream().anyMatch(p -> p.kind.equals("city") && p != capital && Math.hypot(p.x - spot[0], p.z - spot[1]) < mine);
		}

		private String name() {
			for (int i = 0; i < 100; i++) {
				String name = Towns.newName(net.minecraft.util.RandomSource.create(this.random.nextLong()));
				if (this.names.add(name)) {
					return name;
				}
			}

			return "Town " + (this.names.size() + 1);
		}

		private String nationName(String capital) {
			String[] forms = {"the Kingdom of %s", "the %s Union", "the Lands of %s", "the %s League", "the %s Accord", "the Realm of %s", "the %s Dominion",
				"the Crown of %s"};
			return String.format(java.util.Locale.ROOT, forms[this.random.nextInt(forms.length)], capital);
		}

		// ----- The land (from the generator) -----

		/**
		 * The core flat by the land's real heights (the rough surface leaves out the crags: on the user's Tectonic a snowy peak
		 * looked like gentle ground, and a city went on it, nothing in it walkable from its plaza), every {@link #FINE} blocks
		 * over it: within its rise, no cliff between two beside each other, none of it under water.
		 */
		private boolean reallyFlat(int[] c, Size size) {
			int low = Integer.MAX_VALUE;
			int high = Integer.MIN_VALUE;
			int n = size.core() / FINE;
			int[][] ys = new int[2 * n + 1][2 * n + 1];
			for (int a = -n; a <= n; a++) {
				for (int b = -n; b <= n; b++) {
					if (a * a + b * b > n * n) {
						ys[a + n][b + n] = Integer.MIN_VALUE;
						continue;
					}

					int y = this.realLand(c[0] + a * FINE, c[1] + b * FINE)[0];
					if (y <= this.generator.getSeaLevel()) {
						return false;
					}

					ys[a + n][b + n] = y;
					low = Math.min(low, y);
					high = Math.max(high, y);
					if (high - low > size.coreRise()) {
						return false;
					}

					int left = a > -n ? ys[a + n - 1][b + n] : Integer.MIN_VALUE;
					int back = b > -n ? ys[a + n][b + n - 1] : Integer.MIN_VALUE;
					if (left != Integer.MIN_VALUE && Math.abs(left - y) > CLIFF || back != Integer.MIN_VALUE && Math.abs(back - y) > CLIFF) {
						return false;
					}
				}
			}

			return true;
		}

		private final Map<Long, int[]> roadHeights = new HashMap<>();
		private final Map<Long, int[]> realHeights = new HashMap<>();

		/**
		 * The land on a road's grid of cells this wide (the middle of cell i, j), by its real heights: by the rough ones, a road
		 * went over a mountain its search took for gentle ground, cut into cliffs (the user's pictures, 2026-10-30).
		 */
		private int[] roadLand(int i, int j, int cell) {
			long key = BlockPos.asLong(i, cell, j);
			int[] known = this.roadHeights.get(key);
			if (known == null) {
				known = this.realLand(i * cell + cell / 2, j * cell + cell / 2);
				this.roadHeights.put(key, known);
			}

			return known;
		}

		/** The land's real height here (crags and all, {@link #real}) and whether it's water, asked once a spot. */
		private int[] realLand(int x, int z) {
			long key = BlockPos.asLong(x, 0, z);
			int[] known = this.realHeights.get(key);
			if (known == null) {
				int floor = this.real(x, z);
				known = new int[] {floor, floor <= this.generator.getSeaLevel() ? 1 : 0};
				this.realHeights.put(key, known);
			}

			return known;
		}

		/**
		 * The land's real top here: where the generator's final terrain (its crags and cliffs) turns solid, from well over the
		 * sea down, a few blocks at a time, then to the block. Half a millisecond on the user's Tectonic (its own height took 8
		 * ms; the generator's rough terrain, quicker, left out the crags, and with Continents had the land 90 blocks under the
		 * sea). A cave's mouth just under the top can read lower; near enough.
		 */
		private long realNanos;

		private int real(int x, int z) {
			long started = System.nanoTime();
			int y = this.realTop(x, z);
			this.realNanos += System.nanoTime() - started;
			return y;
		}

		private int realTop(int x, int z) {
			net.minecraft.world.level.levelgen.DensityFunction density = this.randomState.router().finalDensity();
			int low = this.level.getMinBuildHeight();
			int high = this.level.getMaxBuildHeight() - 1;
			int y = Math.min(high, this.generator.getSeaLevel() + 96);
			while (y < high && this.solidAt(density, x, y, z)) {
				y = Math.min(high, y + 32);
			}

			int open = y;
			while (y > low && !this.solidAt(density, x, y, z)) {
				open = y;
				y = Math.max(low, y - 6);
			}

			while (open - y > 1) {
				int m = (open + y) / 2;
				if (this.solidAt(density, x, m, z)) {
					y = m;
				} else {
					open = m;
				}
			}

			return y;
		}

		private boolean solidAt(net.minecraft.world.level.levelgen.DensityFunction density, int x, int y, int z) {
			return density.compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, y, z)) > 0;
		}

		/** The ground's height here and whether it's water (at or under the sea's level), on the plan's grid ({@link #real}). */
		private int[] land(int x, int z) {
			int i = Math.floorDiv(x, CELL);
			int j = Math.floorDiv(z, CELL);
			long key = BlockPos.asLong(i, 0, j);
			int[] known = this.heights.get(key);
			if (known == null) {
				known = this.realLand(i * CELL + CELL / 2, j * CELL + CELL / 2);
				this.heights.put(key, known);
			}

			return known;
		}

		private boolean townBiome(int x, int z) {
			int top = this.level.getMaxBuildHeight() - 1;
			return this.generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(top), QuartPos.fromBlock(z),
				this.randomState.sampler()).is(TOWN_BIOMES);
		}

		/**
		 * A flat spot of this size near (x, z): the best in a town's biome; if none, the best further out (twice, then three
		 * times as far); then in any biome. Never on rougher ground (it was, shaped to fit: a capital on a mountain, cliffs cut
		 * all round it). Null if there's no flat land near: it isn't made.
		 */
		@Nullable
		private int[] findSpot(int x, int z, int move, Size size) {
			for (int far = 1; far <= 3; far++) {
				int[] spot = this.best(x, z, Math.min(move * far, MOST_MOVE), size, true);
				if (spot != null) {
					return spot;
				}
			}

			return this.best(x, z, Math.min(move * 3, MOST_MOVE), size, false);
		}

		/**
		 * The best spot of this size within {@code move} of (x, z): in a town's biome, not wet at its core, its core and whole
		 * flat enough roughly, a little water allowed (a river or the sea beside it makes a river town or a harbour); of those,
		 * the flattest and nearest that's really flat ({@link #reallyFlat}). Null if none.
		 */
		@Nullable
		private int[] best(int x0, int z0, int move, Size size, boolean biome) {
			int grid = CELL;
			List<int[]> candidates = new ArrayList<>();
			for (int dx = -move; dx <= move; dx += grid) {
				for (int dz = -move; dz <= move; dz += grid) {
					if (dx * dx + dz * dz <= move * move) {
						candidates.add(new int[] {x0 + dx, z0 + dz});
					}
				}
			}

			for (int[] c : candidates) {
				c[0] = Math.floorDiv(c[0], CELL) * CELL + CELL / 2;
				c[1] = Math.floorDiv(c[1], CELL) * CELL + CELL / 2;
			}

			candidates.sort(java.util.Comparator.comparingDouble(c -> Math.hypot(c[0] - x0, c[1] - z0)));
			List<double[]> suits = new ArrayList<>();
			for (int[] c : candidates) {
				if (this.land(c[0], c[1])[1] == 1 || biome && !this.townBiome(c[0], c[1])) {
					continue;
				}

				int coreLow = Integer.MAX_VALUE;
				int coreHigh = Integer.MIN_VALUE;
				boolean coreWet = false;
				for (int dx = -size.core(); dx <= size.core() && !coreWet; dx += grid) {
					for (int dz = -size.core(); dz <= size.core(); dz += grid) {
						if (dx * dx + dz * dz <= size.core() * size.core()) {
							int[] here = this.land(c[0] + dx, c[1] + dz);
							coreWet |= here[1] == 1;
							coreLow = Math.min(coreLow, here[0]);
							coreHigh = Math.max(coreHigh, here[0]);
						}
					}
				}

				if (coreWet || coreHigh - coreLow > size.coreRise()) {
					continue;
				}

				int low = Integer.MAX_VALUE;
				int high = Integer.MIN_VALUE;
				int cells = 0;
				int wet = 0;
				for (int dx = -size.whole(); dx <= size.whole(); dx += grid) {
					for (int dz = -size.whole(); dz <= size.whole(); dz += grid) {
						if (dx * dx + dz * dz <= size.whole() * size.whole()) {
							int[] here = this.land(c[0] + dx, c[1] + dz);
							cells++;
							wet += here[1];
							low = Math.min(low, here[0]);
							high = Math.max(high, here[0]);
						}
					}
				}

				if (high - low > size.wholeRise() || wet > cells / 3) {
					continue;
				}

				// Low land, by the sea or not far over it (the user, 2026-10-30: "make sure the towns are around sea level, that way
				// it automatically goes around the mountains"): the lower the better.
				int sea = this.generator.getSeaLevel();
				if (coreHigh > sea + LOWLAND) {
					continue;
				}

				suits.add(new double[] {c[0], c[1], 3.0 * (coreHigh - coreLow) + (high - low) / 2.0 + 10.0 * wet / cells + Math.hypot(c[0] - x0, c[1] - z0) / 48.0
					+ (coreLow - sea) / 2.0});
			}

			// (The real heights are slower to work out: only the best few of those that suit roughly.)
			suits.sort(java.util.Comparator.comparingDouble(s -> s[2]));
			for (int k = 0; k < Math.min(10, suits.size()); k++) {
				int[] c = {(int)suits.get(k)[0], (int)suits.get(k)[1]};
				if (this.reallyFlat(c, size)) {
					return c;
				}
			}

			return null;
		}

		// ----- The roads between (W5) -----

		/**
		 * Which places a road joins: a nation's capital to each of its towns, each hamlet to its nearest town (or the capital),
		 * free towns to the nearest place, and each nation to its two nearest neighbours by their nearest two towns (or
		 * capitals), if they're near enough: capital to capital went 3,000 blocks and more, over the sea (the user, 2026-10-30:
		 * "still very long roads").
		 */
		private void roads() {
			Set<String> joined = new HashSet<>();
			List<Place> lived = this.places.stream().filter(p -> !p.ruin()).toList();
			for (Place place : lived) {
				if (place.kind.equals("town") && place.nation != null) {
					lived.stream().filter(p -> p.kind.equals("city") && place.nation.equals(p.nation)).findFirst().ifPresent(capital -> this.join(capital, place,
						joined));
				} else if (place.kind.equals("hamlet")) {
					lived.stream().filter(p -> p != place && !p.kind.equals("hamlet") && place.nation != null && place.nation.equals(p.nation))
						.min(java.util.Comparator.comparingDouble(p -> Math.hypot(p.x - place.x, p.z - place.z))).ifPresent(town -> this.join(town, place, joined));
				} else if (place.nation == null) {
					lived.stream().filter(p -> p != place).min(java.util.Comparator.comparingDouble(p -> Math.hypot(p.x - place.x, p.z - place.z)))
						.ifPresent(other -> this.join(other, place, joined));
				}
			}

			for (PlannedNation nation : this.nations) {
				List<Place> mine = lived.stream().filter(p -> nation.id().equals(p.nation) && !p.kind.equals("hamlet")).toList();
				List<Place[]> nearest = new ArrayList<>();
				for (PlannedNation other : this.nations) {
					if (other == nation) {
						continue;
					}

					Place[] pair = null;
					double best = Double.MAX_VALUE;
					for (Place a : mine) {
						for (Place b : lived) {
							double d = Math.hypot(a.x - b.x, a.z - b.z);
							if (other.id().equals(b.nation) && !b.kind.equals("hamlet") && d < best) {
								best = d;
								pair = new Place[] {a, b};
							}
						}
					}

					if (pair != null && best <= BETWEEN_NATIONS * this.spread) {
						nearest.add(pair);
					}
				}

				nearest.stream().sorted(java.util.Comparator.comparingDouble(p -> Math.hypot(p[0].x - p[1].x, p[0].z - p[1].z))).limit(2).forEach(p -> this.join(p[0],
					p[1], joined));
			}
		}

		private void join(Place a, Place b, Set<String> joined) {
			String key = a.id.compareTo(b.id) < 0 ? a.id + "-" + b.id : b.id + "-" + a.id;
			if (!joined.add(key)) {
				return;
			}

			List<int[]> line = this.route(a.x, a.z, b.x, b.z);
			// Only the stretch between the two towns' own roads (each town's roads go out to meet it).
			int first = 0;
			while (first < line.size() && Math.hypot(line.get(first)[0] - a.x, line.get(first)[1] - a.z) < a.reach()) {
				first++;
			}

			int last = line.size() - 1;
			while (last > first && Math.hypot(line.get(last)[0] - b.x, line.get(last)[1] - b.z) < b.reach()) {
				last--;
			}

			if (last - first < 4) {
				return;
			}

			List<int[]> between = new ArrayList<>(line.subList(first, last + 1));
			a.gates.add(Math.atan2(between.get(0)[1] - a.z, between.get(0)[0] - a.x));
			b.gates.add(Math.atan2(between.get(between.size() - 1)[1] - b.z, between.get(between.size() - 1)[0] - b.x));
			this.highways.add(new Highway(a.id, b.id, between));
		}

		/**
		 * The way between two points over the land, on a grid of cells {@link #ROAD_CELL} wide: the easiest way, as a player
		 * would lay a road (the user, 2026-10-30: "going AROUND hills and mountains for the easiest way there? instead of
		 * splitting a WHOLE mountain, or going across the ocean with a straight line"). Each step costs its length, more the
		 * steeper it is (its grade squared: gentle ways round a hill beat a climb over it; past one in two, a cliff, only if
		 * there's nothing else), a river a little (a bridge now and then), the sea or a lake (deep water) a great deal. The
		 * cheapest found (A*), then its corners rounded off, a point every {@link #STEP} blocks. None found: no road (a
		 * straight line before, over the sea and up the mountains).
		 */
		private List<int[]> route(int ax, int az, int bx, int bz) {
			// (A long road on the coarser grid: on the fine one a capital's road to the next, 3,000 blocks, looked at 40,000
			// cells and found none.)
			int cell = Math.hypot(bx - ax, bz - az) > LONG_ROAD ? CELL : ROAD_CELL;
			int si = Math.floorDiv(ax, cell);
			int sj = Math.floorDiv(az, cell);
			int ti = Math.floorDiv(bx, cell);
			int tj = Math.floorDiv(bz, cell);
			Map<Long, Double> cost = new HashMap<>();
			// (How far along each is: none looked at that can't make it in the longest way round allowed.)
			Map<Long, Double> along = new HashMap<>();
			double longest = MOST_ROUND * Math.hypot(bx - ax, bz - az) + 200;
			Map<Long, Long> from = new HashMap<>();
			PriorityQueue<double[]> open = new PriorityQueue<>(java.util.Comparator.comparingDouble(n -> n[2]));
			long start = BlockPos.asLong(si, 0, sj);
			long goal = BlockPos.asLong(ti, 0, tj);
			cost.put(start, 0.0);
			along.put(start, 0.0);
			open.add(new double[] {si, sj, 0});
			int nodes = 0;
			boolean found = false;
			int sea = this.generator.getSeaLevel();
			while (!open.isEmpty() && nodes++ < MOST_NODES) {
				double[] node = open.poll();
				int i = (int)node[0];
				int j = (int)node[1];
				long key = BlockPos.asLong(i, 0, j);
				double here = cost.get(key);
				if (node[2] > here + GREED * Math.hypot(ti - i, tj - j) * cell + 1e-6) {
					continue;
				}

				if (key == goal) {
					found = true;
					break;
				}

				int[] land = this.roadLand(i, j, cell);
				for (int di = -1; di <= 1; di++) {
					for (int dj = -1; dj <= 1; dj++) {
						if (di == 0 && dj == 0) {
							continue;
						}

						int ni = i + di;
						int nj = j + dj;
						int[] next = this.roadLand(ni, nj, cell);
						if (next[1] == 1 && this.openWater(ni, nj, cell)) {
							continue;
						}

						double step = Math.hypot(di, dj) * cell;
						double grade = Math.abs(next[0] - land[0]) / step;
						double g = here + step * (1 + 40 * grade * grade + WANDER * this.wander(ni * cell, nj * cell)) + (grade > 0.5 ? step * 40 : 0)
							+ (next[1] == 1 ? next[0] < sea - 4 ? 2500 : 60 : 0);
						long nkey = BlockPos.asLong(ni, 0, nj);
						double far = along.get(key) + step;
						if (g < cost.getOrDefault(nkey, Double.MAX_VALUE) && far + Math.hypot(ti - ni, tj - nj) * cell <= longest) {
							cost.put(nkey, g);
							along.put(nkey, far);
							from.put(nkey, key);
							open.add(new double[] {ni, nj, g + GREED * Math.hypot(ti - ni, tj - nj) * cell});
						}
					}
				}
			}

			DrifterCivilizations.LOGGER.info("[plan] road {}, {} to {}, {}: {} cells looked at on a {} grid{}", ax, az, bx, bz, nodes, cell, found ? ""
				: ", no way (none over the land, nor round it in " + (int)longest + " blocks)");
			if (!found) {
				return List.of();
			}

			List<double[]> corners = new ArrayList<>();
			for (long at = goal; ; at = from.get(at)) {
				corners.add(0, new double[] {BlockPos.getX(at) * cell + cell / 2.0, BlockPos.getZ(at) * cell + cell / 2.0});
				if (at == start) {
					break;
				}
			}

			if (corners.size() < 2) {
				corners.add(new double[] {bx, bz});
			}

			corners.set(0, new double[] {ax, az});
			corners.set(corners.size() - 1, new double[] {bx, bz});
			// Corners rounded off (three times cut: curves), then a point every few blocks along.
			for (int round = 0; round < 3 && corners.size() > 2; round++) {
				List<double[]> cut = new ArrayList<>();
				cut.add(corners.get(0));
				for (int k = 0; k < corners.size() - 1; k++) {
					double[] p = corners.get(k);
					double[] q = corners.get(k + 1);
					cut.add(new double[] {0.75 * p[0] + 0.25 * q[0], 0.75 * p[1] + 0.25 * q[1]});
					cut.add(new double[] {0.25 * p[0] + 0.75 * q[0], 0.25 * p[1] + 0.75 * q[1]});
				}

				cut.add(corners.get(corners.size() - 1));
				corners = cut;
			}

			List<int[]> points = new ArrayList<>();
			double next = 0;
			double walked = 0;
			for (int k = 0; k < corners.size() - 1; k++) {
				double[] p = corners.get(k);
				double[] q = corners.get(k + 1);
				double length = Math.hypot(q[0] - p[0], q[1] - p[1]);
				for (; length > 0 && next <= walked + length; next += STEP) {
					double f = (next - walked) / length;
					points.add(new int[] {(int)Math.floor(p[0] + (q[0] - p[0]) * f), (int)Math.floor(p[1] + (q[1] - p[1]) * f)});
				}

				walked += length;
			}

			points.add(new int[] {bx, bz});
			return points;
		}

		/**
		 * A smooth lie of the land a road's search leans on (0 to 1, changing over a couple of hundred blocks): ways that wind
		 * (the user, 2026-10-30: "make the roads windy and scenic"), not straight over flat land, round a hill rather than over.
		 */
		private double wander(int x, int z) {
			double gx = x / (double)WANDER_SIZE;
			double gz = z / (double)WANDER_SIZE;
			int ix = (int)Math.floor(gx);
			int iz = (int)Math.floor(gz);
			double fx = gx - ix;
			double fz = gz - iz;
			fx = fx * fx * (3 - 2 * fx);
			fz = fz * fz * (3 - 2 * fz);
			double a = this.lattice(ix, iz) + (this.lattice(ix + 1, iz) - this.lattice(ix, iz)) * fx;
			double b = this.lattice(ix, iz + 1) + (this.lattice(ix + 1, iz + 1) - this.lattice(ix, iz + 1)) * fx;
			return a + (b - a) * fz;
		}

		private double lattice(int i, int j) {
			long h = this.level.getSeed() ^ i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL;
			h = (h ^ h >>> 31) * 0xBF58476D1CE4E5B9L;
			h = (h ^ h >>> 29) * 0x94D049BB133111EBL;
			return ((h ^ h >>> 32) >>> 11) / (double)(1L << 53);
		}

		private final Map<Long, Boolean> open = new HashMap<>();

		/**
		 * Water with no land within about 50 blocks: the sea or a lake, no road over it (a river's bridged). A road went
		 * straight over the sea between islands, its deep water only dear (the user's pictures, 2026-10-30).
		 */
		private boolean openWater(int i, int j, int cell) {
			long key = BlockPos.asLong(i, cell, j);
			Boolean known = this.open.get(key);
			if (known == null) {
				int r = Math.max(1, 48 / cell);
				known = true;
				for (int a = -r; a <= r && known; a++) {
					for (int b = -r; b <= r && known; b++) {
						known = this.roadLand(i + a, j + b, cell)[1] == 1;
					}
				}

				this.open.put(key, known);
			}

			return known;
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the towns
	// ---------------------------------------------------------------------------------------------

	static void save(CompoundTag tag) {
		tag.putBoolean("Planned", planned);
		if (!ready) {
			return;
		}

		CompoundTag plan = new CompoundTag();
		ListTag list = new ListTag();
		for (Place place : places) {
			CompoundTag one = new CompoundTag();
			one.putUUID("Id", place.id);
			one.putString("Name", place.name);
			one.putString("Kind", place.kind);
			one.putInt("X", place.x);
			one.putInt("Z", place.z);
			one.putInt("Households", place.households);
			if (place.nation != null) {
				one.putUUID("Nation", place.nation);
			}

			one.putBoolean("Made", place.made);
			ListTag gates = new ListTag();
			place.gates.forEach(gate -> gates.add(net.minecraft.nbt.DoubleTag.valueOf(gate)));
			one.put("Gates", gates);
			list.add(one);
		}

		plan.put("Places", list);
		ListTag nationList = new ListTag();
		for (PlannedNation nation : nations) {
			CompoundTag one = new CompoundTag();
			one.putUUID("Id", nation.id());
			one.putString("Name", nation.name());
			one.putUUID("Capital", nation.capital());
			one.putString("Colour", nation.colour().getName());
			nationList.add(one);
		}

		plan.put("Nations", nationList);
		ListTag roads = new ListTag();
		for (Highway highway : highways) {
			CompoundTag one = new CompoundTag();
			one.putUUID("From", highway.from);
			one.putUUID("To", highway.to);
			int[] xs = new int[highway.points.size() * 2];
			for (int i = 0; i < highway.points.size(); i++) {
				xs[2 * i] = highway.points.get(i)[0];
				xs[2 * i + 1] = highway.points.get(i)[1];
			}

			one.putIntArray("Points", xs);
			byte[] built = new byte[highway.built.length];
			for (int i = 0; i < built.length; i++) {
				built[i] = (byte)(highway.built[i] ? 1 : 0);
			}

			one.putByteArray("Built", built);
			roads.add(one);
		}

		plan.put("Roads", roads);
		tag.put("Plan", plan);
	}

	static void load(CompoundTag tag) {
		places.clear();
		nations.clear();
		highways.clear();
		ready = false;
		making = false;
		if (tag.contains("Planned")) {
			planned = tag.getBoolean("Planned");
		}

		if (!tag.contains("Plan")) {
			return;
		}

		CompoundTag plan = tag.getCompound("Plan");
		for (Tag t : plan.getList("Places", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag)t;
			Place place = new Place(one.getUUID("Id"), one.getString("Name"), one.getString("Kind"), one.getInt("X"), one.getInt("Z"), one.getInt("Households"),
				one.hasUUID("Nation") ? one.getUUID("Nation") : null);
			place.made = one.getBoolean("Made");
			for (Tag gate : one.getList("Gates", Tag.TAG_DOUBLE)) {
				place.gates.add(((net.minecraft.nbt.DoubleTag)gate).getAsDouble());
			}

			places.add(place);
		}

		for (Tag t : plan.getList("Nations", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag)t;
			nations.add(new PlannedNation(one.getUUID("Id"), one.getString("Name"), one.getUUID("Capital"), DyeColor.byName(one.getString("Colour"),
				DyeColor.WHITE)));
		}

		for (Tag t : plan.getList("Roads", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag)t;
			int[] xs = one.getIntArray("Points");
			List<int[]> points = new ArrayList<>();
			for (int i = 0; i + 1 < xs.length; i += 2) {
				points.add(new int[] {xs[i], xs[i + 1]});
			}

			Highway highway = new Highway(one.getUUID("From"), one.getUUID("To"), points);
			byte[] built = one.getByteArray("Built");
			for (int i = 0; i < Math.min(built.length, highway.built.length); i++) {
				highway.built[i] = built[i] == 1;
			}

			highways.add(highway);
		}

		ready = true;
	}
}
