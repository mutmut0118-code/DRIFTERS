package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Paper towns: the sketches of the places not yet built and the stretches of road not yet laid, as answers a column at
 * a time (docs/superpowers/specs/2026-11-14-lod-towns-design.md). A far-view mod's hookup asks {@link #column} for the
 * columns of land it's about to draw and puts what comes back on the land's own surface: the block the surface becomes
 * (a street, a plaza, a road's worn mix, a field), what stands on it (a house's walls and roof, a lamp, a bridge's deck)
 * and the light on it. Heights are counted up from the surface the far-view mod has, so nothing floats or is buried.
 *
 * <p>Two of them: {@link #SERVER} (the world this game hosts; kept up from the plan as it's played) and {@link #CLIENT}
 * (a world joined; filled by packets). Single player has both in one program, and they share nothing. What's kept is
 * swapped whole, so any thread may ask.
 */
public final class FarPaint {
	/** What stands on a column: from {@code bottom} to {@code top} blocks over the surface, of this block, lit this much. */
	public record Span(int bottom, int top, BlockState block, int light) {
	}

	/** No height asked for: the surface stays where the far land has it. */
	public static final int NO_Y = Integer.MIN_VALUE;
	/** A height asked for is taken only this near the far land's own (further: the land was read differently; left alone). */
	public static final int MOST_GRADE = 8;

	/**
	 * A column's answer: the block its surface becomes (null: as it is), what stands on it bottom up, the block light on
	 * it, and the height its surface is to be at ({@link #NO_Y}: where it is): a road's graded bed, a house's levelled
	 * plot. The far land is cut down or filled up to it, as the real builder does, and whatever grew there is cleared.
	 */
	public record Column(@Nullable BlockState surface, List<Span> spans, int light, int y) {
		public Column(@Nullable BlockState surface, List<Span> spans, int light) {
			this(surface, spans, light, NO_Y);
		}

		/** Only light on the ground: nothing's cleared, cut or filled. */
		public boolean lightOnly() {
			return this.surface == null && this.spans.isEmpty() && this.y == NO_Y;
		}
	}

	/**
	 * The ground, as against what grows or lies on it: not air, not a tree's leaves or logs, not a plant or snow layer.
	 * (The top of a column of far land is often a treetop; a road painted there ran over the canopy: the user's pictures,
	 * 2026-11-14.) Water counts: a column's surface there is the water's.
	 */
	public static boolean ground(BlockState state) {
		if (state.isAir()) {
			return false;
		}

		if (!state.getFluidState().isEmpty()) {
			return true;
		}

		return !state.is(net.minecraft.tags.BlockTags.LEAVES) && !state.is(net.minecraft.tags.BlockTags.LOGS) && !TownBuilder.plant(state)
			&& !(state.getBlock() instanceof net.minecraft.world.level.block.VineBlock) && !state.is(Blocks.BAMBOO) && !state.is(Blocks.CACTUS)
			&& !state.is(Blocks.SUGAR_CANE) && !state.is(Blocks.MUSHROOM_STEM) && !state.is(Blocks.RED_MUSHROOM_BLOCK) && !state.is(Blocks.BROWN_MUSHROOM_BLOCK);
	}

	/**
	 * A road between two places: its smoothed line, which of its points are a bridge's, which pieces of it are laid, and
	 * the height of its bed at each point (empty: not worked out yet, it lies on the land as it is).
	 */
	record Road(int index, List<double[]> line, boolean[] bridge, boolean[] built, long salt, int[] heights) {
	}

	/** The side of the squares everything's sorted into, to find what's near a column. */
	private static final int CELL = 64;
	/** A road between towns: three wide; its lamps this far off its middle. */
	private static final double ROAD_HALF = 1.5;
	private static final double LAMP_OUT = 4.2;
	/** A lamp: its post's height, its light, how far its glow is drawn; a lit house's windows' light. */
	private static final int LAMP_UP = 3;
	private static final int LAMP_LIGHT = 15;
	private static final int GLOW = 9;
	private static final int WINDOW_LIGHT = 10;

	private static final class PlaceEntry {
		final Sketch sketch;
		volatile boolean built;
		@Nullable
		private volatile Map<Long, Column> columns;

		PlaceEntry(Sketch sketch, boolean built) {
			this.sketch = sketch;
			this.built = built;
		}

		boolean reaches(int x, int z) {
			return Math.abs(x - this.sketch.x()) <= this.sketch.reach() && Math.abs(z - this.sketch.z()) <= this.sketch.reach();
		}

		Map<Long, Column> columns() {
			Map<Long, Column> known = this.columns;
			if (known == null) {
				synchronized (this) {
					known = this.columns;
					if (known == null) {
						known = paintPlace(this.sketch);
						this.columns = known;
					}
				}
			}

			return known;
		}
	}

	private record RoadColumn(Column column, int piece) {
	}

	private static final class RoadEntry {
		final Road road;
		@Nullable
		private volatile Map<Long, RoadColumn> columns;

		RoadEntry(Road road) {
			this.road = road;
		}

		Map<Long, RoadColumn> columns() {
			Map<Long, RoadColumn> known = this.columns;
			if (known == null) {
				synchronized (this) {
					known = this.columns;
					if (known == null) {
						known = paintRoad(this.road);
						this.columns = known;
					}
				}
			}

			return known;
		}
	}

	private record Kept(Map<UUID, PlaceEntry> places, List<RoadEntry> roads, Map<Long, List<PlaceEntry>> placeCells, Map<Long, List<RoadEntry>> roadCells) {
	}

	private static final Kept NOTHING = new Kept(Map.of(), List.of(), Map.of(), Map.of());
	private volatile Kept kept = NOTHING;

	// (After NOTHING: made in the order they're written.)
	public static final FarPaint SERVER = new FarPaint();
	public static final FarPaint CLIENT = new FarPaint();

	private FarPaint() {
	}

	/** All of it anew: these sketches (those of {@code built} places kept but not drawn) and these roads. */
	void set(Collection<Sketch> sketches, Set<UUID> built, List<Road> roads) {
		Kept before = this.kept;
		Map<UUID, PlaceEntry> places = new HashMap<>();
		Map<Long, List<PlaceEntry>> placeCells = new HashMap<>();
		for (Sketch sketch : sketches) {
			// (The same sketch as before: its columns, once worked out, are kept.)
			PlaceEntry old = before.places.get(sketch.place());
			PlaceEntry entry = old != null && old.sketch == sketch ? old : new PlaceEntry(sketch, false);
			entry.built = built.contains(sketch.place());
			places.put(sketch.place(), entry);
			for (int i = Math.floorDiv(sketch.x() - sketch.reach(), CELL); i <= Math.floorDiv(sketch.x() + sketch.reach(), CELL); i++) {
				for (int j = Math.floorDiv(sketch.z() - sketch.reach(), CELL); j <= Math.floorDiv(sketch.z() + sketch.reach(), CELL); j++) {
					placeCells.computeIfAbsent(BlockPos.asLong(i, 0, j), key -> new ArrayList<>()).add(entry);
				}
			}
		}

		List<RoadEntry> roadEntries = new ArrayList<>();
		Map<Long, List<RoadEntry>> roadCells = new HashMap<>();
		for (Road road : roads) {
			RoadEntry old = road.index() < before.roads.size() ? before.roads.get(road.index()) : null;
			RoadEntry entry = old != null && old.road.line() == road.line() && old.road.built() == road.built() && old.road.heights() == road.heights() ? old
				: new RoadEntry(road);
			roadEntries.add(entry);
			for (double[] point : road.line()) {
				for (int i = Math.floorDiv(Mth.floor(point[0]) - 16, CELL); i <= Math.floorDiv(Mth.floor(point[0]) + 16, CELL); i++) {
					for (int j = Math.floorDiv(Mth.floor(point[1]) - 16, CELL); j <= Math.floorDiv(Mth.floor(point[1]) + 16, CELL); j++) {
						List<RoadEntry> cell = roadCells.computeIfAbsent(BlockPos.asLong(i, 0, j), key -> new ArrayList<>());
						if (!cell.contains(entry)) {
							cell.add(entry);
						}
					}
				}
			}
		}

		this.kept = new Kept(places, roadEntries, placeCells, roadCells);
	}

	/** This place is really built: its sketch isn't drawn any more. */
	void built(UUID place) {
		PlaceEntry entry = this.kept.places.get(place);
		if (entry != null) {
			entry.built = true;
		}
	}

	void clear() {
		this.kept = NOTHING;
	}

	/** Anything of a sketch or a road in this box of blocks (cheap: by squares; a box that's all open country is passed over). */
	public boolean touches(int x0, int z0, int x1, int z1) {
		Kept now = this.kept;
		if (now == NOTHING) {
			return false;
		}

		for (int i = Math.floorDiv(x0, CELL); i <= Math.floorDiv(x1, CELL); i++) {
			for (int j = Math.floorDiv(z0, CELL); j <= Math.floorDiv(z1, CELL); j++) {
				long key = BlockPos.asLong(i, 0, j);
				List<PlaceEntry> places = now.placeCells.get(key);
				if (places != null) {
					for (PlaceEntry entry : places) {
						if (!entry.built) {
							return true;
						}
					}
				}

				if (now.roadCells.containsKey(key)) {
					return true;
				}
			}
		}

		return false;
	}

	/** This column's answer, or null where nothing's to be drawn (most of the world). */
	@Nullable
	public Column column(int x, int z) {
		Kept now = this.kept;
		if (now == NOTHING) {
			return null;
		}

		long cell = BlockPos.asLong(Math.floorDiv(x, CELL), 0, Math.floorDiv(z, CELL));
		long key = BlockPos.asLong(x, 0, z);
		List<PlaceEntry> places = now.placeCells.get(cell);
		if (places != null) {
			for (PlaceEntry entry : places) {
				if (!entry.built && entry.reaches(x, z)) {
					Column column = entry.columns().get(key);
					if (column != null) {
						return column;
					}
				}
			}
		}

		List<RoadEntry> roads = now.roadCells.get(cell);
		if (roads != null) {
			for (RoadEntry entry : roads) {
				RoadColumn column = entry.columns().get(key);
				if (column != null && !entry.road.built()[Math.min(column.piece, entry.road.built().length - 1)]) {
					return column.column;
				}
			}
		}

		return null;
	}

	// ---------------------------------------------------------------------------------------------
	// A place's sketch as columns
	// ---------------------------------------------------------------------------------------------

	private static Map<Long, Column> paintPlace(Sketch sketch) {
		TownBuilder.Style style = TownBuilder.Style.byName(sketch.style());
		Map<Long, Column> out = new HashMap<>();
		Map<Long, Integer> light = new HashMap<>();
		for (int[] field : sketch.fields()) {
			Column soil = new Column(Blocks.FARMLAND.defaultBlockState(), List.of(), 0);
			for (int x = field[0]; x <= field[2]; x++) {
				for (int z = field[1]; z <= field[3]; z++) {
					out.put(BlockPos.asLong(x, 0, z), soil);
				}
			}
		}

		Column path = new Column(style.path().defaultBlockState(), List.of(), 0);
		for (int[] street : sketch.streets()) {
			double half = street[0] / 2.0;
			Map<Long, Double> cells = new HashMap<>();
			for (int k = 1; k + 3 < street.length; k += 2) {
				stroke(cells, street[k] + 0.5, street[k + 1] + 0.5, street[k + 2] + 0.5, street[k + 3] + 0.5, half);
			}

			for (long cell : cells.keySet()) {
				out.put(cell, path);
			}
		}

		Column paving = new Column(style.paving().defaultBlockState(), List.of(), 0, sketch.y0());
		int[] plaza = sketch.plaza();
		for (int x = plaza[0]; x <= plaza[2]; x++) {
			for (int z = plaza[1]; z <= plaza[3]; z++) {
				out.put(BlockPos.asLong(x, 0, z), paving);
			}
		}

		for (Sketch.Box box : sketch.boxes()) {
			BlockState walls = Sketch.PALETTE.get(Mth.clamp(box.wallBlock(), 0, Sketch.PALETTE.size() - 1)).defaultBlockState();
			BlockState roof = Sketch.PALETTE.get(Mth.clamp(box.roofBlock(), 0, Sketch.PALETTE.size() - 1)).defaultBlockState();
			for (int x = box.x0(); x <= box.x1(); x++) {
				for (int z = box.z0(); z <= box.z1(); z++) {
					boolean edge = x == box.x0() || x == box.x1() || z == box.z0() || z == box.z1();
					if (box.roof() == 0) {
						// A ruin: broken walls round nothing, each column of them its own height.
						if (edge) {
							int high = 1 + (int)(Math.floorMod(x * 73428767L ^ z * 912931L, 1000) / 1000.0 * box.wall());
							out.put(BlockPos.asLong(x, 0, z), new Column(null, List.of(new Span(0, high, walls, 0)), 0, box.base()));
						} else {
							out.remove(BlockPos.asLong(x, 0, z));
						}

						continue;
					}

					// (Its ridge along its long side: the roof a block higher a block in from the two long edges.)
					int in = box.ridgeAlongX() ? Math.min(z - box.z0(), box.z1() - z) : Math.min(x - box.x0(), box.x1() - x);
					int high = Math.min(in + 1, box.roof());
					out.put(BlockPos.asLong(x, 0, z), new Column(null, List.of(new Span(0, box.wall(), walls, edge && box.lit() ? WINDOW_LIGHT : 0),
						new Span(box.wall(), box.wall() + high, roof, 0)), 0, box.base()));
				}
			}

			if (box.lit()) {
				for (int x = box.x0() - 2; x <= box.x1() + 2; x++) {
					for (int z = box.z0() - 2; z <= box.z1() + 2; z++) {
						if (!box.contains(x, z)) {
							int away = Math.max(Math.max(box.x0() - x, x - box.x1()), Math.max(box.z0() - z, z - box.z1()));
							light.merge(BlockPos.asLong(x, 0, z), WINDOW_LIGHT - 2 * away, Math::max);
						}
					}
				}
			}
		}

		BlockState post = wood(style.main(), "fence").defaultBlockState();
		for (int[] lamp : sketch.lamps()) {
			lamp(out, light, lamp[0], lamp[1], post);
		}

		lit(out, light);
		return out;
	}

	/** A lamp: its post and its lantern here, its glow on the ground round it. */
	private static void lamp(Map<Long, Column> out, Map<Long, Integer> light, int x, int z, BlockState post) {
		out.put(BlockPos.asLong(x, 0, z), new Column(null, List.of(new Span(0, LAMP_UP, post, LAMP_LIGHT - 1),
			new Span(LAMP_UP, LAMP_UP + 1, Blocks.LANTERN.defaultBlockState(), LAMP_LIGHT)), LAMP_LIGHT - 1));
		for (int dx = -GLOW; dx <= GLOW; dx++) {
			for (int dz = -GLOW; dz <= GLOW; dz++) {
				int away = (int)Math.round(Math.hypot(dx, dz));
				if ((dx != 0 || dz != 0) && away <= GLOW) {
					light.merge(BlockPos.asLong(x + dx, 0, z + dz), LAMP_LIGHT - away, Math::max);
				}
			}
		}
	}

	/** The light on the ground put into the columns: one of its own where there's none, more where there's less. */
	private static void lit(Map<Long, Column> out, Map<Long, Integer> light) {
		for (var entry : light.entrySet()) {
			Column column = out.get(entry.getKey());
			if (column == null) {
				out.put(entry.getKey(), new Column(null, List.of(), entry.getValue()));
			} else if (column.spans().isEmpty() && column.light() < entry.getValue()) {
				out.put(entry.getKey(), new Column(column.surface(), List.of(), entry.getValue(), column.y()));
			}
		}
	}

	/** The columns within {@code half} of the line from a to b, each with how far off the line it is (the least, if asked twice). */
	private static void stroke(Map<Long, Double> cells, double ax, double az, double bx, double bz, double half) {
		double length = Math.hypot(bx - ax, bz - az);
		int steps = Math.max(1, (int)Math.ceil(length * 2));
		int r = (int)Math.ceil(half);
		for (int s = 0; s <= steps; s++) {
			double px = ax + (bx - ax) * s / steps;
			double pz = az + (bz - az) * s / steps;
			for (int x = Mth.floor(px) - r; x <= Mth.floor(px) + r; x++) {
				for (int z = Mth.floor(pz) - r; z <= Mth.floor(pz) + r; z++) {
					double off = Math.hypot(x + 0.5 - px, z + 0.5 - pz);
					if (off <= half) {
						cells.merge(BlockPos.asLong(x, 0, z), off, Math::min);
					}
				}
			}
		}
	}

	private static Block wood(String kind, String what) {
		Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(kind + "_" + what));
		return block == Blocks.AIR ? Blocks.OAK_FENCE : block;
	}

	// ---------------------------------------------------------------------------------------------
	// A road between towns as columns
	// ---------------------------------------------------------------------------------------------

	private static Map<Long, RoadColumn> paintRoad(Road road) {
		Map<Long, RoadColumn> out = new HashMap<>();
		List<double[]> line = road.line();
		int n = line.size();
		if (n < 2) {
			return out;
		}

		BlockState deck = Blocks.SPRUCE_PLANKS.defaultBlockState();
		for (int i = 0; i + 1 < n; i++) {
			Map<Long, Double> cells = new HashMap<>();
			stroke(cells, line.get(i)[0], line.get(i)[1], line.get(i + 1)[0], line.get(i + 1)[1], ROAD_HALF);
			boolean bridge = road.bridge()[i] || road.bridge()[i + 1];
			int piece = i / Highways.PIECE;
			boolean graded = road.heights().length == n;
			for (var cell : cells.entrySet()) {
				int x = BlockPos.getX(cell.getKey());
				int z = BlockPos.getZ(cell.getKey());
				// (Its bed's height here: the nearer of the stretch's two ends'.)
				boolean first = Math.hypot(x + 0.5 - line.get(i)[0], z + 0.5 - line.get(i)[1]) <= Math.hypot(x + 0.5 - line.get(i + 1)[0], z + 0.5 - line.get(i + 1)[1]);
				int y = graded ? road.heights()[first ? i : i + 1] : NO_Y;
				// (A bridge: a deck a block over the water it crosses. Else the road's worn mix, as it's really laid.)
				Column column = bridge ? new Column(null, List.of(new Span(1, 2, deck, 0)), 0)
					: new Column(WildRoad.mix(x, z, cell.getValue(), road.salt()), List.of(), 0, y);
				out.putIfAbsent(cell.getKey(), new RoadColumn(column, piece));
			}
		}

		// Its lamps, where the real ones go: every so far along it, a side each in turn, none near its ends or on a bridge.
		double[] s = RoadLamps.along(line);
		double total = s[n - 1];
		Map<Long, Column> lamps = new HashMap<>();
		Map<Long, Integer> light = new HashMap<>();
		Map<Long, Integer> pieces = new HashMap<>();
		BlockState post = Blocks.SPRUCE_FENCE.defaultBlockState();
		int i = 1;
		for (int k = (int)Math.ceil(RoadLamps.END_GAP / (double)RoadLamps.EVERY); k * RoadLamps.EVERY <= total - RoadLamps.END_GAP; k++) {
			double at = k * RoadLamps.EVERY;
			while (i < n - 1 && s[i] < at) {
				i++;
			}

			if (s[i] <= s[i - 1] || road.bridge()[i] || road.bridge()[i - 1]) {
				continue;
			}

			double f = (at - s[i - 1]) / (s[i] - s[i - 1]);
			double px = line.get(i - 1)[0] + (line.get(i)[0] - line.get(i - 1)[0]) * f;
			double pz = line.get(i - 1)[1] + (line.get(i)[1] - line.get(i - 1)[1]) * f;
			double heading = Math.atan2(line.get(i)[1] - line.get(i - 1)[1], line.get(i)[0] - line.get(i - 1)[0]);
			int side = (k & 1) == 0 ? 1 : -1;
			int lx = Mth.floor(px - Math.sin(heading) * side * LAMP_OUT);
			int lz = Mth.floor(pz + Math.cos(heading) * side * LAMP_OUT);
			int before = light.size();
			lamp(lamps, light, lx, lz, post);
			int piece = (i - 1) / Highways.PIECE;
			pieces.putIfAbsent(BlockPos.asLong(lx, 0, lz), piece);
			if (light.size() != before) {
				for (int dx = -GLOW; dx <= GLOW; dx++) {
					for (int dz = -GLOW; dz <= GLOW; dz++) {
						pieces.putIfAbsent(BlockPos.asLong(lx + dx, 0, lz + dz), piece);
					}
				}
			}
		}

		for (var lamp : lamps.entrySet()) {
			out.put(lamp.getKey(), new RoadColumn(lamp.getValue(), pieces.getOrDefault(lamp.getKey(), 0)));
		}

		for (var entry : light.entrySet()) {
			RoadColumn had = out.get(entry.getKey());
			if (had == null) {
				out.put(entry.getKey(), new RoadColumn(new Column(null, List.of(), entry.getValue()), pieces.getOrDefault(entry.getKey(), 0)));
			} else if (had.column.spans().isEmpty() && had.column.light() < entry.getValue()) {
				out.put(entry.getKey(), new RoadColumn(new Column(had.column.surface(), List.of(), entry.getValue(), had.column.y()), had.piece));
			}
		}

		return out;
	}

	// ---------------------------------------------------------------------------------------------
	// The server's, kept up from the plan
	// ---------------------------------------------------------------------------------------------

	private static long stamp = Long.MIN_VALUE;
	/** Each road's bed heights, once worked out (in the background: the generator's asked for the land along it). */
	private static final Map<WorldPlan.Highway, int[]> HEIGHTS = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
	private static volatile int graded;
	private static volatile boolean grading;

	/**
	 * A road's bed: the land's height at each of its points as the generator says it is, smoothed along the road (each the
	 * middle of itself and the two either side), so the far road is a graded way through the land, cut and filled like
	 * the real one, not a stripe laid over every bump.
	 */
	private static int[] grade(TownLand land, List<double[]> line, boolean[] bridge) {
		int n = line.size();
		int[] raw = new int[n];
		for (int i = 0; i < n; i++) {
			raw[i] = land.ground(Mth.floor(line.get(i)[0]), Mth.floor(line.get(i)[1]));
		}

		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			double sum = 0;
			double weights = 0;
			for (int k = -2; k <= 2; k++) {
				int j = i + k;
				if (j >= 0 && j < n && !bridge[j]) {
					double w = 3 - Math.abs(k);
					sum += raw[j] * w;
					weights += w;
				}
			}

			out[i] = weights == 0 ? raw[i] : (int)Math.round(sum / weights);
		}

		return out;
	}
	private static volatile List<Road> serverRoads = List.of();

	/** The roads the server's painter holds now (for sending to a client: {@link FarSync}). */
	static List<Road> serverRoads() {
		return serverRoads;
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				sync(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SERVER.clear();
			serverRoads = List.of();
			HEIGHTS.clear();
			stamp = Long.MIN_VALUE;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> dispatcher.register(
			Commands.literal("drifterciv").requires(source -> source.hasPermission(2)).then(Commands.literal("sketch")
				// Debug: what the painter answers for the column the command's run at.
				.then(Commands.literal("paint").executes(ctx -> {
					BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
					Column column = SERVER.column(at.getX(), at.getZ());
					String text = at.getX() + ", " + at.getZ() + ": " + (column == null ? "nothing" : describe(column));
					DrifterCivilizations.LOGGER.info("[sketch] paint {}", text);
					ctx.getSource().sendSuccess(() -> Component.literal(text), false);
					return column == null ? 0 : 1;
				}))
				// Debug: how much the painter holds.
				.then(Commands.literal("painted").executes(ctx -> {
					Kept now = SERVER.kept;
					long built = now.places.values().stream().filter(entry -> entry.built).count();
					long worked = now.places.values().stream().filter(entry -> entry.columns != null).count();
					int pieces = 0;
					int laid = 0;
					for (RoadEntry entry : now.roads) {
						pieces += entry.road.built().length;
						for (boolean one : entry.road.built()) {
							laid += one ? 1 : 0;
						}
					}

					String text = now.places.size() + " sketches (" + built + " built and not drawn, " + worked + " worked out), " + now.roads.size() + " roads ("
						+ laid + " of " + pieces + " pieces laid and not drawn), " + (now.placeCells.size() + now.roadCells.size()) + " squares";
					DrifterCivilizations.LOGGER.info("[sketch] painted: {}", text);
					ctx.getSource().sendSuccess(() -> Component.literal(text), false);
					return 1;
				}))
				// Debug: the painter's answers put into the world as real blocks, within this far of here (loaded land only): a
				// look at a sketch up close.
				.then(Commands.literal("stamp").executes(ctx -> {
					ServerLevel level = ctx.getSource().getLevel();
					BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
					int count = 0;
					for (int x = at.getX() - 160; x <= at.getX() + 160; x++) {
						for (int z = at.getZ() - 160; z <= at.getZ() + 160; z++) {
							Column column = SERVER.column(x, z);
							if (column == null || !TownBuilder.loadedAt(level, x, z)) {
								continue;
							}

							int ground = TownBuilder.ground(level, x, z);
							if (column.surface() != null) {
								level.setBlock(new BlockPos(x, ground, z), column.surface(), 2);
							}

							for (Span span : column.spans()) {
								for (int y = ground + 1 + span.bottom(); y <= ground + span.top(); y++) {
									level.setBlock(new BlockPos(x, y, z), span.block(), 2);
								}
							}

							count++;
						}
					}

					int stamped = count;
					DrifterCivilizations.LOGGER.info("[sketch] stamped {} columns round {}, {}", stamped, at.getX(), at.getZ());
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + stamped + " columns of sketches and roads put in as blocks"), true);
					return count;
				})))));
	}

	private static String describe(Column column) {
		StringBuilder text = new StringBuilder(column.surface() == null ? "surface as it is" : "surface " + BuiltInRegistries.BLOCK.getKey(column.surface().getBlock()));
		for (Span span : column.spans()) {
			text.append(", ").append(BuiltInRegistries.BLOCK.getKey(span.block().getBlock())).append(' ').append(span.bottom()).append("..").append(span.top())
				.append(span.light() > 0 ? " lit " + span.light() : "");
		}

		return text + ", light " + column.light() + (column.y() == NO_Y ? "" : ", surface at y " + column.y());
	}

	/** The server's painter brought up to the plan: new sketches, places made, another world. Cheap when nothing's changed. */
	private static void sync(MinecraftServer server) {
		if (!WorldPlan.planned || !WorldPlan.ready) {
			if (stamp != Long.MIN_VALUE) {
				SERVER.clear();
				stamp = Long.MIN_VALUE;
			}

			return;
		}

		long now = Sketches.changes() * 1_000_003L + WorldPlan.highways.size() * 7919L + graded * 104_729L;
		List<Object[]> ungraded = new ArrayList<>();
		Set<UUID> built = new java.util.HashSet<>();
		for (WorldPlan.Place place : WorldPlan.places) {
			if (place.made) {
				built.add(place.id);
				now += 31;
			}
		}

		if (now == stamp) {
			return;
		}

		stamp = now;
		List<Road> roads = new ArrayList<>();
		long seed = server.overworld().getSeed();
		for (int h = 0; h < WorldPlan.highways.size(); h++) {
			WorldPlan.Highway road = WorldPlan.highways.get(h);
			List<double[]> line = Highways.smooth(road);
			boolean[] bridge = new boolean[road.points.size()];
			for (int i = 0; i < bridge.length; i++) {
				bridge[i] = road.points.get(i).length > 2 && road.points.get(i)[2] == 1;
			}

			// (Its pieces' laid marks are the plan's own: a piece laid stops being drawn with no word from anyone.)
			int[] heights = HEIGHTS.get(road);
			roads.add(new Road(h, line, bridge, road.built, seed ^ road.from.getLeastSignificantBits() * 31L ^ road.to.getMostSignificantBits(),
				heights == null ? new int[0] : heights));
			if (heights == null) {
				ungraded.add(new Object[] {road, line, bridge});
			}
		}

		// The beds of the roads that have none yet, in the background; the painter's brought up again when they're done.
		TownLand land = Sketches.predicted();
		if (!ungraded.isEmpty() && land != null && !grading) {
			grading = true;
			Thread thread = new Thread(() -> {
				try {
					for (Object[] one : ungraded) {
						@SuppressWarnings("unchecked")
						List<double[]> line = (List<double[]>)one[1];
						HEIGHTS.put((WorldPlan.Highway)one[0], grade(land, line, (boolean[])one[2]));
					}

					graded++;
				} catch (RuntimeException e) {
					DrifterCivilizations.LOGGER.warn("[far] the roads' beds couldn't be worked out ({}); they're drawn on the land as it is", e.toString());
				} finally {
					grading = false;
				}
			}, "Drifter road beds");
			thread.setDaemon(true);
			thread.setPriority(Thread.MIN_PRIORITY);
			thread.start();
		}

		serverRoads = roads;
		SERVER.set(Sketches.all(), built, roads);
	}
}
