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
	private static final int PLAZA = 10;
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
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
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

		int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
		String name = wanted != null ? wanted : Towns.newName(random);
		Chronicle.quiet = true;
		try {
			return build(level, server, random, dimension, warning, cx, cz, y0, households, name);
		} finally {
			Chronicle.quiet = false;
		}
	}

	private static Result build(ServerLevel level, MinecraftServer server, RandomSource random, String dimension, String warning, int cx, int cz, int y0,
		int households, String name) {
		List<DrifterApi.HousePlanInfo> plans = new ArrayList<>(DrifterApi.housePlans(server).stream()
			.filter(plan -> Math.max(plan.sizeX(), plan.sizeZ()) >= SMALLEST_HOUSE && Math.max(plan.sizeX(), plan.sizeZ()) <= BIGGEST_HOUSE
				&& plan.sizeY() <= 24).toList());
		if (plans.isEmpty()) {
			return new Result(null, "No house plans to build with (see /drifter houses)");
		}

		// The plan, as a village grows: the plaza in the middle, four roads out from it and side roads off those, the store
		// by the north road, the fields and pen by the south one, and the houses along the roads with room between them,
		// nearest the plaza first. Nothing is built till it's laid out.
		Rect plaza = new Rect(cx - PLAZA, cz - PLAZA, cx + PLAZA, cz + PLAZA);
		List<Road> roads = new ArrayList<>();
		for (Direction way : Direction.Plane.HORIZONTAL) {
			Road arm = new Road(cx + way.getStepX() * (PLAZA + 1), cz + way.getStepZ() * (PLAZA + 1), way, ARM);
			roads.add(new Road(arm.x(), arm.z(), way, Math.max(1, dry(level, arm))));
		}

		// The store's spot by the north road: where the ground suits it (found in testing: on a hillside its door opened onto a
		// bank of earth), with a flat apron to the road.
		int storeZ = cz - 28;
		Integer storeBase = null;
		for (int z = cz - 28; z <= cz - 14 && storeBase == null; z++) {
			storeBase = siteBase(level, cx + 4, z, cx + 12, z + 8);
			storeZ = storeBase != null ? z : storeZ;
		}

		if (storeBase == null) {
			storeBase = median(level, cx + 4, storeZ, cx + 12, storeZ + 8);
		}

		Rect storeRect = new Rect(cx + 2, storeZ - 1, cx + 13, storeZ + 9);
		List<Rect> fieldRects = List.of(new Rect(cx + 3, cz + 12, cx + 11, cz + 20), new Rect(cx + 3, cz + 23, cx + 11, cz + 31));
		Rect penRect = new Rect(cx - 13, cz + 12, cx - 3, cz + 22);
		List<Rect> taken = new ArrayList<>(List.of(plaza, storeRect, penRect));
		taken.addAll(fieldRects);

		// Side roads off the main ones, out into the land (a village's lanes).
		branches(level, roads, taken, random, 2 + households / 4, cx, cz);
		List<Rect> roadRects = new ArrayList<>(roads.stream().map(Road::rect).toList());

		// 1. The plaza evened out, at its ground's middle height (only it: the rest stays as the land is).
		y0 = median(level, cx - PLAZA, cz - PLAZA, cx + PLAZA, cz + PLAZA) + 1;
		even(level, cx - PLAZA, cz - PLAZA, cx + PLAZA, cz + PLAZA, y0 - 1, CLEAR_ABOVE);

		// 2. The store, at the height of its own ground.
		even(level, storeRect.x0(), storeRect.z0(), storeRect.x1(), storeRect.z1(), storeBase, 8);
		List<BlockPos> store = store(level, new BlockPos(cx + 4, storeBase + 1, storeZ), cx);

		// 3. The shared farm (two fields, east of the south road) and the pen (west of it).
		List<BlockPos> fields = new ArrayList<>();
		for (Rect rect : fieldRects) {
			int fieldBase = median(level, rect.x0(), rect.z0(), rect.x1(), rect.z1());
			even(level, rect.x0(), rect.z0(), rect.x1(), rect.z1(), fieldBase, 4);
			BlockPos field = new BlockPos((rect.x0() + rect.x1()) / 2, fieldBase, (rect.z0() + rect.z1()) / 2);
			fields.add(field.above());
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					BlockPos pos = field.offset(dx, 0, dz);
					if (dx == 0 && dz == 0) {
						set(level, pos, Blocks.WATER.defaultBlockState());
						continue;
					}

					set(level, pos, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE));
					set(level, pos.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, random.nextInt(8)));
				}
			}
		}

		BlockPos pen = onGround(level, (penRect.x0() + penRect.x1()) / 2, (penRect.z0() + penRect.z1()) / 2);
		penFence(level, pen, 5);
		for (EntityType<?> animal : List.of(EntityType.COW, EntityType.COW, EntityType.SHEEP, EntityType.SHEEP, EntityType.CHICKEN, EntityType.CHICKEN)) {
			animal.spawn(level, onGround(level, pen.getX() + random.nextInt(7) - 3, pen.getZ() + random.nextInt(7) - 3), MobSpawnType.COMMAND);
		}

		// 4. The houses: each from a different plan (as far as they go), its front to the road, along the roads with room
		// between them, the nearest free spot to the plaza first; each on its own ground, eased into the land round it.
		java.util.Collections.shuffle(plans, new java.util.Random(random.nextLong()));
		List<Side> sides = new ArrayList<>();
		List<Town.Plot> plots = new ArrayList<>();
		List<DrifterApi.PlacedHouse> houses = new ArrayList<>();
		List<Rect> houseRects = new ArrayList<>();
		int planIndex = 0;
		// Why spots were turned down (for the log): out of the town, by a road, by the store, fields or another house, the ground.
		int[] turnedDown = new int[5];
		// (Where the land is broken, by water or slopes too steep, more side roads till the houses fit.)
		for (int round = 0; round < 4 && houses.size() < households; round++) {
			if (round > 0) {
				List<Rect> busy = new ArrayList<>(taken);
				busy.addAll(houseRects);
				int had = roads.size();
				branches(level, roads, busy, random, roads.size() - 4 + 2, cx, cz);
				roads.subList(had, roads.size()).forEach(road -> roadRects.add(road.rect()));
			}

			for (int i = sides.size() / 2; i < roads.size(); i++) {
				Road road = roads.get(i);
				int start = i < 4 ? 2 : 4;
				sides.add(new Side(road, road.way().getClockWise(), start));
				sides.add(new Side(road, road.way().getCounterClockWise(), start));
			}

			while (houses.size() < households) {
				Side side = sides.stream().filter(s -> !s.full).min(java.util.Comparator.comparingDouble(s -> {
					BlockPos at = s.road.at(s.cursor);
					return Math.hypot(at.getX() - cx, at.getZ() - cz);
				})).orElse(null);
				if (side == null) {
					break;
				}

				DrifterApi.PlacedHouse house = null;
				Direction front = side.out.getOpposite();
				int setback = LEAST_SETBACK + random.nextInt(MOST_SETBACK - LEAST_SETBACK + 1);
				for (int tries = 0; tries < plans.size() && house == null; tries++) {
					DrifterApi.HousePlanInfo plan = plans.get((planIndex + tries) % plans.size());
					Rotation rotation = turnFor(server, plan.id(), front);
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
					Rect site = Rect.of(side.road.at(side.cursor).relative(side.out, near),
						side.road.at(side.cursor + along - 1).relative(side.out, near + depth - 1));
					int why = Math.abs(site.x0() - cx) > REACH || Math.abs(site.x1() - cx) > REACH || Math.abs(site.z0() - cz) > REACH
						|| Math.abs(site.z1() - cz) > REACH ? 0 : roadRects.stream().anyMatch(road -> road.near(site, LEAST_SETBACK - 1)) ? 1
						: taken.stream().anyMatch(other -> other.near(site, HOUSE_GAP)) ? 2 : houseRects.stream().anyMatch(other -> other.near(site, HOUSE_GAP)) ? 3 : -1;
					Integer base = why >= 0 ? null : siteBase(level, site.x0(), site.z0(), site.x1(), site.z1());
					if (base == null) {
						turnedDown[why >= 0 ? why : 4]++;
						continue;
					}

					// Its own ground, eased into the land round it (a plan whose lowest layer is its own ground goes a block down, its
					// ground in place of the land's).
					List<Rect> keep = new ArrayList<>(taken);
					keep.addAll(houseRects);
					keep.addAll(roadRects);
					foundation(level, site, base, plan.sizeY() + 2, keep, random);
					BlockPos origin = new BlockPos(site.x0(), plan.groundLayer() ? base : base + 1, site.z0());
					house = DrifterApi.placeHouse(level, plan.id(), origin, rotation, WOODS[random.nextInt(WOODS.length)], front);
					if (house != null) {
						planIndex = (planIndex + tries + 1) % plans.size();
						side.cursor += along + HOUSE_GAP + random.nextInt(MORE_GAP + 1);
						Town.Plot made = new Town.Plot(origin, sx, sz, plan.id(), rotation, front, null);
						made.built = true;
						plots.add(made);
						houses.add(house);
						houseRects.add(site);
						DrifterCivilizations.LOGGER.info("[town] {} at {}, its door at {}", plan.id(), house.box(), house.door());
					}
				}

				if (house == null) {
					side.cursor += SKIP;
				}

				side.full = side.cursor >= side.road.length() - 4;
			}
		}

		DrifterCivilizations.LOGGER.info("[town] {} of {} houses on {} roads; spots turned down: {} out of the town, {} by a road, {} by the store or fields, {} by a house, {} for the ground",
			houses.size(), households, roads.size(), turnedDown[0], turnedDown[1], turnedDown[2], turnedDown[3], turnedDown[4]);
		// 5. The roads over the ground as it is now, the plaza paved, the well, bell and board, lamps along the roads.
		for (Road road : roads) {
			Rect rect = road.rect();
			for (int x = rect.x0(); x <= rect.x1(); x++) {
				for (int z = rect.z0(); z <= rect.z1(); z++) {
					if (!plaza.contains(x, z)) {
						road(level, x, z);
					}
				}
			}
		}

		for (int x = cx - PLAZA; x <= cx + PLAZA; x++) {
			for (int z = cz - PLAZA; z <= cz + PLAZA; z++) {
				set(level, new BlockPos(x, y0 - 1, z), (random.nextFloat() < 0.3F ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS).defaultBlockState());
			}
		}

		BlockPos well = new BlockPos(cx, y0, cz);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos pos = well.offset(dx, 0, dz);
				if (dx == 0 && dz == 0) {
					set(level, pos.below(), Blocks.WATER.defaultBlockState());
					set(level, pos.below(2), Blocks.WATER.defaultBlockState());
				} else {
					set(level, pos.below(), Blocks.COBBLESTONE.defaultBlockState());
					place(level, pos, (Math.abs(dx) == 1 && Math.abs(dz) == 1 ? Blocks.OAK_FENCE : Blocks.COBBLESTONE_WALL).defaultBlockState());
				}
			}
		}

		for (int dx = -1; dx <= 1; dx += 2) {
			for (int dz = -1; dz <= 1; dz += 2) {
				place(level, well.offset(dx, 1, dz), Blocks.OAK_FENCE.defaultBlockState());
			}
		}

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				set(level, well.offset(dx, 2, dz), Blocks.OAK_SLAB.defaultBlockState());
			}
		}

		place(level, new BlockPos(cx + 6, y0, cz - 6), Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, Direction.SOUTH));
		BlockPos board = new BlockPos(cx - 6, y0, cz - 6);
		for (int dx : new int[] {-PLAZA, PLAZA}) {
			for (int dz : new int[] {-PLAZA, PLAZA}) {
				lamp(level, new BlockPos(cx + dx, y0, cz + dz));
			}
		}

		List<Rect> built = new ArrayList<>(taken);
		built.addAll(houseRects);
		for (Road road : roads) {
			for (int t = 8; t < road.length(); t += 12) {
				Direction out = (t / 12) % 2 == 0 ? road.way().getClockWise() : road.way().getCounterClockWise();
				BlockPos at = road.at(t).relative(out, 2);
				if (built.stream().noneMatch(rect -> rect.away(at.getX(), at.getZ()) <= 1) && roadRects.stream().noneMatch(rect -> rect.contains(at.getX(), at.getZ()))) {
					lamp(level, onGround(level, at.getX(), at.getZ()));
				}
			}
		}

		// A market stall on the plaza, its counter to the well (north): kept by the most trader-like of them (step 4).
		BlockPos stallAt = new BlockPos(cx + 4, y0, cz + 4);
		DrifterApi.placeHouse(level, DrifterApi.stallPlan(), stallAt, Rotation.NONE, "oak", Direction.SOUTH);
		// Paths from each door to its road.
		for (int i = 0; i < houses.size(); i++) {
			path(level, houses.get(i), plots.get(i).front, roadRects);
		}

		// The town's area: everything in it, and a little round.
		int minX = cx - PLAZA;
		int maxX = cx + PLAZA;
		int minZ = cz - PLAZA;
		int maxZ = cz + PLAZA;
		List<Rect> all = new ArrayList<>(built);
		all.addAll(roadRects);
		for (Rect rect : all) {
			minX = Math.min(minX, rect.x0());
			maxX = Math.max(maxX, rect.x1());
			minZ = Math.min(minZ, rect.z0());
			maxZ = Math.max(maxZ, rect.z1());
		}

		int minY = y0;
		int maxY = y0;
		for (DrifterApi.PlacedHouse house : houses) {
			minY = Math.min(minY, house.box().minY());
			maxY = Math.max(maxY, house.box().maxY());
		}

		var area = new net.minecraft.world.level.levelgen.structure.BoundingBox(minX - 6, minY - 12, minZ - 6, maxX + 6, maxY + 16, maxZ + 6);
		// The trees the town cut through: no trunks or leaves left hanging (user, 2026-10-22).
		DrifterApi.tidyTrees(level, area);

		// 6. Its people: one moved into each house (furnished as a player would, the user's guidebook), friends with the
		// rest, one group; well on in their lives, with the gear for it (user, 2026-10-23: "all of the players should be like
		// mid-late game, above tier 2, that way drifters can defend themselves").
		List<DrifterEntity> people = new ArrayList<>();
		var storeBox = new net.minecraft.world.level.levelgen.structure.BoundingBox(storeRect.x0(), storeBase - 2, storeRect.z0(), storeRect.x1(),
			storeBase + 8, storeRect.z1());
		var plazaBox = new net.minecraft.world.level.levelgen.structure.BoundingBox(plaza.x0() - 2, y0 - 3, plaza.z0() - 2, plaza.x1() + 2, y0 + 5,
			plaza.z1() + 2);
		for (int i = 0; i < houses.size(); i++) {
			DrifterApi.PlacedHouse house = houses.get(i);
			Town.Plot plot = plots.get(i);
			BlockPos stand = doorstep(level, house, plot);
			DrifterEntity drifter = DrifterApi.spawn(level, stand, null);
			if (drifter == null) {
				continue;
			}

			Home home = DrifterApi.settleIn(drifter, house, store);
			home.farm = fields.get(i % fields.size());
			home.farmRadius = 4;
			home.pen = pen;
			plot.owner = drifter.getUUID();
			DrifterApi.equipSettled(drifter, random);
			stock(level, home, house.box());
			for (BlockPos chest : home.chests()) {
				DrifterApi.rememberChest(drifter, chest);
			}

			DrifterApi.knowsBox(drifter, storeBox);
			DrifterApi.knowsBox(drifter, plazaBox);
			people.add(drifter);
		}

		var group = DrifterApi.formGroup(people, name);
		for (DrifterEntity one : people) {
			for (DrifterEntity other : people) {
				if (one != other) {
					DrifterApi.setTrustInDrifter(one, other, 60.0);
				}
			}
		}

		long day = Chronicle.day(server);
		Town town = new Town(UUID.randomUUID(), name, group == null ? null : group.id, dimension, new BlockPos(cx, y0, cz), day, true);
		town.plots.addAll(plots);
		town.store.addAll(store);
		town.fields.addAll(fields);
		town.well = well;
		town.pen = pen;
		town.stallPlot = new Town.Plot(stallAt, 5, 4, DrifterApi.stallPlan(), Rotation.NONE, Direction.NORTH, null);
		town.stallBuilt = true;
		Towns.add(server, town);
		if (group != null) {
			Market.keeper(server, town, group);
			// (Made ready: they stocked the store themselves, and the keeper has a little money to buy with.)
			for (DrifterEntity one : people) {
				store.forEach(chest -> DrifterApi.rememberChest(one, chest));
				if (one.getUUID().equals(town.keeper)) {
					one.getInventory().add(new ItemStack(Items.EMERALD, 8));
				}
			}
		}

		noticeBoard(level, board, town, people);
		// 7. Lit all over, inside the houses and out, so nothing spawns in the town at night (user, 2026-10-23: "the town isnt
		// fully lit up alot, causing a bunch of mobs to spawn and killing half the town").
		List<Rect> noLamp = new ArrayList<>(roadRects);
		noLamp.addAll(fieldRects);
		noLamp.add(plaza);
		int[] lights = TownLights.light(level, area, houses.stream().map(DrifterApi.PlacedHouse::box).toList(),
			(x, z) -> all.stream().anyMatch(rect -> rect.away(x, z) <= TOWN_GROUND), (x, z) -> noLamp.stream().anyMatch(rect -> rect.contains(x, z)));
		Chronicle.add(server, "town", name + " was founded with " + people.size() + " households (made ready, for testing)");
		return new Result(town, name + ": " + people.size() + " households, the plaza at " + cx + ", " + y0 + ", " + cz + " (" + lights[0]
			+ " lamp posts and " + lights[1] + " torches for dark corners)" + warning);
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
			List.of(new ItemStack(Items.COAL, 48), new ItemStack(Items.CHARCOAL, 16)),
			List.of(new ItemStack(Items.BREAD, 16), new ItemStack(Items.COOKED_BEEF, 12), new ItemStack(Items.WHEAT_SEEDS, 24)));
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
	private static void foundation(ServerLevel level, Rect site, int base, int clear, List<Rect> keep, RandomSource random) {
		Rect ring = new Rect(site.x0() - BLEND, site.z0() - BLEND, site.x1() + BLEND, site.z1() + BLEND);
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

				int h = d == 0 ? base : base + Math.round((g - base) * (float)(d - 1) / BLEND);
				boolean edge = d == 0 && (x == site.x0() || x == site.x1() || z == site.z0() || z == site.z1());
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

	/** A dirt path from the door out to its road. */
	private static void path(ServerLevel level, DrifterApi.PlacedHouse house, Direction front, List<Rect> roads) {
		if (house.door() == null) {
			return;
		}

		BlockPos step = house.door().relative(front);
		for (int i = 0; i < 16; i++) {
			BlockPos at = step;
			if (roads.stream().anyMatch(rect -> rect.contains(at.getX(), at.getZ()))) {
				break;
			}

			BlockPos top = new BlockPos(step.getX(), ground(level, step.getX(), step.getZ()), step.getZ());
			if (!house.box().isInside(top) && (level.getBlockState(top).is(Blocks.GRASS_BLOCK) || level.getBlockState(top).is(Blocks.DIRT))) {
				set(level, top, Blocks.DIRT_PATH.defaultBlockState());
			}

			step = step.relative(front);
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
	 * This area's ground at {@code base}: dirt filled up to it (grass on top), anything higher cut down to it, and
	 * {@code clear} blocks above it cleared (trees, plants); the land round it left as it is.
	 */
	private static void even(ServerLevel level, int x0, int z0, int x1, int z1, int base, int clear) {
		// (Trees in the way felled whole first: user, 2026-10-23, "trees are always half cutoff".)
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int top = ground(level, x, z);
				fellIn(level, x, z, Math.min(top, base) + 1, Math.max(top, base) + clear);
			}
		}

		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int top = ground(level, x, z);
				if (top < base) {
					buried(level, new BlockPos(x, top, z));
				}

				for (int y = Math.min(top, base) + 1; y <= base; y++) {
					set(level, new BlockPos(x, y, z), y == base ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState());
				}

				for (int y = base + 1; y <= Math.max(top, base) + clear; y++) {
					set(level, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
				}

				// (Water in it: filled in.)
				if (!level.getFluidState(new BlockPos(x, base, z)).isEmpty()) {
					set(level, new BlockPos(x, base, z), Blocks.GRASS_BLOCK.defaultBlockState());
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
		}
	}

	/** A block set with its neighbours told (fences join up, a lantern sits on its post). */
	private static void place(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_ALL);
	}
}
