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
 * with everything already established, primarily for testing"): `/drifterciv town spawn`. The ground levelled, two roads
 * crossing at a plaza with a well, a bell and a notice board, a store of chests with some stock, two fields and a pen of
 * animals, and a house from the plans for each household, each different, its door to the road; a Drifter moved into
 * each, friends with the rest, one group named after the town. Laid out as on the town plan the user agreed. A debug
 * command: blocks and things appear (a town founded by its people is built block by block).
 */
public final class TownBuilder {
	private TownBuilder() {
	}

	public static final int DEFAULT_HOUSEHOLDS = 6;
	public static final int MAX_HOUSEHOLDS = 12;
	/** Half the town's size each way (blocks) from the plaza's middle (room for twelve of the bigger houses of 0.22.7). */
	private static final int HALF_X = 72;
	private static final int HALF_Z = 64;
	/** A town's houses: the bigger ones (the user, 2026-10-22: "they need to be a bit bigger, especially for a town"). */
	private static final int SMALLEST_HOUSE = 15;
	private static final int BIGGEST_HOUSE = 25;
	private static final int PLAZA = 10;
	/** Air cleared above the town's ground (trees, a hill). */
	private static final int CLEAR_ABOVE = 30;
	private static final String[] WOODS = {"oak", "spruce", "birch", "dark_oak", "acacia", "cherry"};

	public record Result(@Nullable Town town, String message) {
	}

	/** One side of a road houses go along: the way they face (their door), and where the next one goes. */
	private static final class Side {
		final Direction front;
		/** Along the road: +1 or -1 (east/west for the east-west road, south/north for the north-south one). */
		final int step;
		final boolean eastWest;
		int cursor;

		Side(Direction front, int step, boolean eastWest, int cursor) {
			this.front = front;
			this.step = step;
			this.eastWest = eastWest;
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
		for (int x = cx - HALF_X; x <= cx + HALF_X + 16; x += 16) {
			for (int z = cz - HALF_Z; z <= cz + HALF_Z + 16; z += 16) {
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

		// 1. The ground levelled.
		for (int x = cx - HALF_X; x <= cx + HALF_X; x++) {
			for (int z = cz - HALF_Z; z <= cz + HALF_Z; z++) {
				for (int y = y0; y <= y0 + CLEAR_ABOVE; y++) {
					set(level, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
				}

				set(level, new BlockPos(x, y0 - 1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				for (int y = y0 - 2; y >= y0 - 8; y--) {
					BlockPos pos = new BlockPos(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (!state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced()) {
						break;
					}

					set(level, pos, Blocks.DIRT.defaultBlockState());
				}
			}
		}

		// 2. Roads, the plaza, the well, lamps.
		for (int x = cx - HALF_X + 2; x <= cx + HALF_X - 2; x++) {
			for (int z = cz - 1; z <= cz + 1; z++) {
				set(level, new BlockPos(x, y0 - 1, z), Blocks.DIRT_PATH.defaultBlockState());
			}
		}

		for (int z = cz - HALF_Z + 2; z <= cz + HALF_Z - 2; z++) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				set(level, new BlockPos(x, y0 - 1, z), Blocks.DIRT_PATH.defaultBlockState());
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

		for (int d = 22; d < HALF_X - 2; d += 14) {
			lamp(level, new BlockPos(cx + d, y0, cz + 2));
			lamp(level, new BlockPos(cx - d, y0, cz - 2));
		}

		for (int d = 22; d < HALF_Z - 2; d += 14) {
			lamp(level, new BlockPos(cx + 2, y0, cz - d));
			lamp(level, new BlockPos(cx - 2, y0, cz + d));
		}

		// 3. The store: a stone house of chests by the north road, with some stock in.
		List<BlockPos> store = store(level, new BlockPos(cx + 4, y0, cz - 28), cx);

		// 4. The shared farm (two fields, east of the south road) and the pen (west of it).
		List<BlockPos> fields = new ArrayList<>();
		for (int fz : new int[] {cz + 12, cz + 23}) {
			BlockPos field = new BlockPos(cx + 7, y0 - 1, fz + 4);
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

		BlockPos pen = new BlockPos(cx - 8, y0, cz + 17);
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				if (Math.abs(dx) == 5 || Math.abs(dz) == 5) {
					place(level, pen.offset(dx, 0, dz), dx == 5 && dz == 0
						? Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST)
						: Blocks.OAK_FENCE.defaultBlockState());
				}
			}
		}

		for (EntityType<?> animal : List.of(EntityType.COW, EntityType.COW, EntityType.SHEEP, EntityType.SHEEP, EntityType.CHICKEN, EntityType.CHICKEN)) {
			animal.spawn(level, pen.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3), MobSpawnType.COMMAND);
		}

		// 5. The houses: each from a different plan (as far as they go), its front to the road.
		List<DrifterApi.HousePlanInfo> plans = new ArrayList<>(DrifterApi.housePlans(server).stream()
			.filter(plan -> Math.max(plan.sizeX(), plan.sizeZ()) >= SMALLEST_HOUSE && Math.max(plan.sizeX(), plan.sizeZ()) <= BIGGEST_HOUSE
				&& plan.sizeY() <= 24).toList());
		if (plans.isEmpty()) {
			return new Result(null, "No house plans to build with (see /drifter houses)");
		}

		java.util.Collections.shuffle(plans, new java.util.Random(random.nextLong()));
		List<Side> sides = List.of(
			new Side(Direction.SOUTH, -1, true, cx - 12), new Side(Direction.NORTH, -1, true, cx - 14),
			new Side(Direction.SOUTH, 1, true, cx + 12), new Side(Direction.NORTH, 1, true, cx + 14),
			new Side(Direction.EAST, -1, false, cz - 20), new Side(Direction.WEST, -1, false, cz - 30));
		List<Town.Plot> plots = new ArrayList<>();
		List<DrifterApi.PlacedHouse> houses = new ArrayList<>();
		int planIndex = 0;
		for (int i = 0; i < households; i++) {
			DrifterApi.PlacedHouse house = null;
			for (int s = 0; s < sides.size() && house == null; s++) {
				Side side = sides.get((i + s) % sides.size());
				for (int tries = 0; tries < plans.size() && house == null; tries++) {
					DrifterApi.HousePlanInfo plan = plans.get((planIndex + tries) % plans.size());
					Rotation rotation = turnFor(server, plan.id(), side.front);
					if (rotation == null) {
						continue;
					}

					boolean turned = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
					int sx = turned ? plan.sizeZ() : plan.sizeX();
					int sz = turned ? plan.sizeX() : plan.sizeZ();
					// (A plan whose lowest layer is its own ground goes a block down, its ground in place of the town's.)
					BlockPos origin = originFor(side, cx, cz, plan.groundLayer() ? y0 - 1 : y0, sx, sz);
					if (origin == null) {
						break;
					}

					house = DrifterApi.placeHouse(level, plan.id(), origin, rotation, WOODS[random.nextInt(WOODS.length)], side.front);
					if (house != null) {
						planIndex = (planIndex + tries + 1) % plans.size();
						side.cursor += side.step * ((side.eastWest ? sx : sz) + 2);
						plots.add(new Town.Plot(origin, sx, sz, plan.id(), rotation, side.front, null));
						houses.add(house);
						path(level, house.door(), side.front, cx, cz, y0);
					}
				}
			}
		}

		// 6. Its people: one moved into each house, friends with the rest, one group.
		List<DrifterEntity> people = new ArrayList<>();
		for (int i = 0; i < houses.size(); i++) {
			DrifterApi.PlacedHouse house = houses.get(i);
			Town.Plot plot = plots.get(i);
			BlockPos door = house.door() != null ? house.door() : plot.origin;
			BlockPos stand = new BlockPos(door.getX(), y0, door.getZ()).relative(plot.front, 2);
			DrifterEntity drifter = DrifterApi.spawn(level, stand, null);
			if (drifter == null) {
				continue;
			}

			Home home = DrifterApi.settleIn(drifter, house, store);
			home.farm = fields.get(i % fields.size());
			home.farmRadius = 4;
			home.pen = pen;
			plot.owner = drifter.getUUID();
			for (var item : List.of(Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL, Items.STONE_SWORD, Items.STONE_HOE)) {
				drifter.getInventory().add(new ItemStack(item));
			}

			drifter.getInventory().add(new ItemStack(Items.BREAD, 12));
			drifter.getInventory().add(new ItemStack(Items.TORCH, 8));
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
		Towns.add(server, town);
		noticeBoard(level, board, town, people);
		Chronicle.add(server, "town", name + " was founded with " + people.size() + " households (made ready, for testing)");
		return new Result(town, name + ": " + people.size() + " households, the plaza at " + cx + ", " + y0 + ", " + cz + warning);
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

	/** Where the next house on this side goes (its corner), or null if the side is full. */
	@Nullable
	private static BlockPos originFor(Side side, int cx, int cz, int y0, int sx, int sz) {
		int x;
		int z;
		if (side.eastWest) {
			x = side.step > 0 ? side.cursor : side.cursor - sx + 1;
			z = side.front == Direction.SOUTH ? cz - 4 - sz + 1 : cz + 4;
			if (x < cx - HALF_X + 3 || x + sx - 1 > cx + HALF_X - 3) {
				return null;
			}
		} else {
			z = side.step > 0 ? side.cursor : side.cursor - sz + 1;
			x = side.front == Direction.EAST ? cx - 4 - sx + 1 : cx + 4;
			if (z < cz - HALF_Z + 3 || z + sz - 1 > cz + HALF_Z - 3) {
				return null;
			}
		}

		return new BlockPos(x, y0, z);
	}

	/** A dirt path from the door out to the road. */
	private static void path(ServerLevel level, @Nullable BlockPos door, Direction front, int cx, int cz, int y0) {
		if (door == null) {
			return;
		}

		BlockPos step = new BlockPos(door.getX(), y0 - 1, door.getZ()).relative(front);
		for (int i = 0; i < 8; i++) {
			if (front.getAxis() == Direction.Axis.Z ? Math.abs(step.getZ() - cz) <= 1 : Math.abs(step.getX() - cx) <= 1) {
				break;
			}

			if (level.getBlockState(step).is(Blocks.GRASS_BLOCK) || level.getBlockState(step).is(Blocks.DIRT)) {
				set(level, step, Blocks.DIRT_PATH.defaultBlockState());
			}

			step = step.relative(front);
		}
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
			set(level, new BlockPos(x, y0 - 1, door.getZ()), Blocks.DIRT_PATH.defaultBlockState());
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
