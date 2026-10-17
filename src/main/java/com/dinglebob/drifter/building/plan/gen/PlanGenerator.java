package com.dinglebob.drifter.building.plan.gen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.building.plan.HousePlan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Makes house plans from parts (milestone 15): a rectangle of one or two storeys (a ladder up through the upper floor),
 * walls of a style's materials (log corner posts and posts along the walls, a stone base row, a log beam at the top,
 * different infill upstairs), windows spaced along the walls (some two tall, some with sills), a door in the front, and a
 * roof: a gable either way round, a shed slope, or flat with a parapet. Eaves sit level with the top of the wall (beside
 * it, never hanging in the air: what the hand-made designs taught). The front is the low-z side; the plan is turned to
 * suit its site when it's placed, like any other. Each plan is checked (PlanCheck) before it's used.
 */
public final class PlanGenerator {
	private PlanGenerator() {
	}

	/** Eaves stick out this far past the walls. */
	private static final int OVERHANG = 1;

	/** One plan in this style, or null if this draw didn't make a sound house (the caller draws again). */
	@Nullable
	public static HousePlan generate(String id, HouseStyle style, RandomSource random) {
		int width = between(random, style.width());
		int depth = between(random, style.depth());
		int height = between(random, style.wallHeight());
		int floors = random.nextDouble() < style.twoFloors() && width >= 5 && depth >= 5 ? 2 : 1;
		String roof = pick(random, style.roofs());
		// A shed roof climbs half a block a block: on a deep house it gets too tall; and a flat roof wants a parapet.
		if (roof.equals("shed") && depth > 7) {
			roof = "gable";
		}

		boolean tall = height >= 4 && random.nextDouble() < style.tallWindows();
		boolean sills = style.sill() != null && random.nextBoolean();
		int spacing = Math.max(2, style.windowSpacing() + (random.nextInt(3) == 0 ? 1 : 0));
		Map<BlockPos, BlockState> cells = new HashMap<>();
		int doorX = width / 2;
		int ladderX = 1;

		// Floors: the ground floor, and the deck between storeys (with a hole for the ladder).
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < depth; z++) {
				cells.put(new BlockPos(x, 0, z), style.floor().defaultBlockState());
				if (floors == 2 && !(x == ladderX && z == depth - 2)) {
					cells.put(new BlockPos(x, height + 1, z), style.upperFloor().defaultBlockState());
				}
			}
		}

		for (int storey = 0; storey < floors; storey++) {
			int y0 = storey * (height + 1);
			for (int y = y0 + 1; y <= y0 + height; y++) {
				for (int x = 0; x < width; x++) {
					for (int z = 0; z < depth; z++) {
						if (x != 0 && x != width - 1 && z != 0 && z != depth - 1) {
							continue;
						}

						BlockPos pos = new BlockPos(x, y, z);
						cells.put(pos, wall(style, storey, x, z, y - y0, width, depth, height));
					}
				}
			}

			// Windows on every side, spaced from the corners, clear of the door and the ladder.
			for (int row = 2; row <= (tall ? 3 : 2); row++) {
				int y = y0 + row;
				for (int x = 1; x < width - 1; x++) {
					if ((x - 1) % spacing != spacing / 2) {
						continue;
					}

					if (!(storey == 0 && Math.abs(x - doorX) <= 1)) {
						window(cells, style, new BlockPos(x, y, 0), Direction.NORTH, sills && row == 2);
					}

					if (!(floors == 2 && x == ladderX)) {
						window(cells, style, new BlockPos(x, y, depth - 1), Direction.SOUTH, sills && row == 2);
					}
				}

				for (int z = 1; z < depth - 1; z++) {
					if ((z - 1) % spacing == spacing / 2) {
						window(cells, style, new BlockPos(0, y, z), Direction.WEST, sills && row == 2);
						window(cells, style, new BlockPos(width - 1, y, z), Direction.EAST, sills && row == 2);
					}
				}
			}
		}

		// The door, in the middle of the front.
		BlockState door = style.door().defaultBlockState();
		if (door.hasProperty(DoorBlock.FACING)) {
			door = door.setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false);
			cells.put(new BlockPos(doorX, 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			cells.put(new BlockPos(doorX, 2, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		}

		// The ladder up, on the back wall inside.
		if (floors == 2) {
			for (int y = 1; y <= height + 1; y++) {
				cells.put(new BlockPos(ladderX, y, depth - 2), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
			}
		}

		int top = (floors - 1) * (height + 1) + height;
		switch (roof) {
			case "gable_z" -> gable(cells, style, width, depth, top, false);
			case "shed" -> shed(cells, style, width, depth, top);
			case "flat" -> flat(cells, style, width, depth, top);
			default -> gable(cells, style, width, depth, top, true);
		}

		return normalize(id, cells);
	}

	private static BlockState wall(HouseStyle style, int storey, int x, int z, int row, int width, int depth, int height) {
		boolean corner = (x == 0 || x == width - 1) && (z == 0 || z == depth - 1);
		boolean alongX = z == 0 || z == depth - 1;
		int along = alongX ? x : z;
		boolean post = style.postsEvery() > 0 && along % style.postsEvery() == 0;
		if (style.frame() != null && (corner || post)) {
			return axis(style.frame(), Direction.Axis.Y);
		}

		if (style.frame() != null && style.beam() && row == height) {
			return axis(style.frame(), alongX ? Direction.Axis.X : Direction.Axis.Z);
		}

		if (storey == 0 && row == 1 && style.base() != null) {
			return style.base().defaultBlockState();
		}

		return (storey == 0 ? style.infill() : style.upperInfill()).defaultBlockState();
	}

	private static void window(Map<BlockPos, BlockState> cells, HouseStyle style, BlockPos pos, Direction out, boolean sill) {
		BlockState there = cells.get(pos);
		// Never in a post or a beam (they hold the frame) or the door.
		if (there == null || there.getBlock() instanceof RotatedPillarBlock || there.getBlock() instanceof DoorBlock) {
			return;
		}

		cells.put(pos, style.window().defaultBlockState());
		if (sill && style.sill() != null) {
			BlockState stair = style.sill().defaultBlockState();
			if (stair.hasProperty(StairBlock.FACING)) {
				stair = stair.setValue(StairBlock.FACING, out.getOpposite()).setValue(StairBlock.HALF, Half.TOP);
			}

			cells.putIfAbsent(pos.below().relative(out), stair);
		}
	}

	/**
	 * A gable roof, its ridge along x (or z): rows of stairs climbing in from both sides, the first beside the top of the
	 * wall, a slab ridge where they meet on an odd width; the triangles at the two ends filled in (they also hold the rows
	 * up while it's built).
	 */
	private static void gable(Map<BlockPos, BlockState> cells, HouseStyle style, int width, int depth, int top, boolean ridgeAlongX) {
		int span = ridgeAlongX ? depth : width;
		int length = ridgeAlongX ? width : depth;
		int low = -OVERHANG;
		int high = span - 1 + OVERHANG;
		for (int k = 0; low <= high; k++, low++, high--) {
			int y = top + k;
			for (int a = -OVERHANG; a < length + OVERHANG; a++) {
				if (low == high) {
					put(cells, ridgeAlongX, a, y, low, style.slab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
					continue;
				}

				put(cells, ridgeAlongX, a, y, low, stair(style.stair(), ridgeAlongX ? Direction.SOUTH : Direction.EAST));
				put(cells, ridgeAlongX, a, y, high, stair(style.stair(), ridgeAlongX ? Direction.NORTH : Direction.WEST));
			}

			if (k >= 1) {
				for (int s = low + 1; s < high; s++) {
					put(cells, ridgeAlongX, 0, y, s, style.gableFill().defaultBlockState());
					put(cells, ridgeAlongX, length - 1, y, s, style.gableFill().defaultBlockState());
				}
			}
		}
	}

	/** Places at (along, y, across) for a ridge along x, or the same turned for one along z. */
	private static void put(Map<BlockPos, BlockState> cells, boolean ridgeAlongX, int along, int y, int across, BlockState state) {
		cells.put(ridgeAlongX ? new BlockPos(along, y, across) : new BlockPos(across, y, along), state);
	}

	/**
	 * A shed roof: one slope rising toward the back, half a block a block (slabs, bottom then top), the side walls built up
	 * under it to hold it.
	 */
	private static void shed(Map<BlockPos, BlockState> cells, HouseStyle style, int width, int depth, int top) {
		for (int z = -OVERHANG; z < depth + OVERHANG; z++) {
			int k = z + OVERHANG;
			// The eave beside the wall top, then over the wall and climbing.
			int y = k == 0 ? top : top + 1 + (k - 1) / 2;
			SlabType type = k == 0 || (k - 1) % 2 == 0 ? SlabType.BOTTOM : SlabType.TOP;
			for (int x = -OVERHANG; x < width + OVERHANG; x++) {
				cells.put(new BlockPos(x, y, z), style.slab().defaultBlockState().setValue(SlabBlock.TYPE, type));
			}

			if (z >= 0 && z < depth) {
				for (int fill = top + 1; fill < y; fill++) {
					cells.put(new BlockPos(0, fill, z), style.gableFill().defaultBlockState());
					cells.put(new BlockPos(width - 1, fill, z), style.gableFill().defaultBlockState());
					if (z == depth - 1) {
						for (int x = 1; x < width - 1; x++) {
							cells.put(new BlockPos(x, fill, z), style.gableFill().defaultBlockState());
						}
					}
				}
			}
		}
	}

	/** A flat roof over the walls, with a parapet round it. */
	private static void flat(Map<BlockPos, BlockState> cells, HouseStyle style, int width, int depth, int top) {
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < depth; z++) {
				cells.put(new BlockPos(x, top + 1, z), style.gableFill().defaultBlockState());
				if (x == 0 || x == width - 1 || z == 0 || z == depth - 1) {
					BlockState edge = style.parapet() != null ? style.parapet().defaultBlockState()
						: style.slab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
					cells.put(new BlockPos(x, top + 2, z), edge);
				}
			}
		}
	}

	private static BlockState stair(Block block, Direction facing) {
		BlockState state = block.defaultBlockState();
		return state.hasProperty(StairBlock.FACING) ? state.setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM) : state;
	}

	private static BlockState axis(Block block, Direction.Axis axis) {
		BlockState state = block.defaultBlockState();
		return state.hasProperty(RotatedPillarBlock.AXIS) ? state.setValue(RotatedPillarBlock.AXIS, axis) : state;
	}

	/** Shifted so its lowest corner is at 0, 0, 0, as a plan's cells are. */
	private static HousePlan normalize(String id, Map<BlockPos, BlockState> cells) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (BlockPos pos : cells.keySet()) {
			minX = Math.min(minX, pos.getX());
			minY = Math.min(minY, pos.getY());
			minZ = Math.min(minZ, pos.getZ());
			maxX = Math.max(maxX, pos.getX());
			maxY = Math.max(maxY, pos.getY());
			maxZ = Math.max(maxZ, pos.getZ());
		}

		List<HousePlan.Cell> list = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> entry : cells.entrySet()) {
			list.add(new HousePlan.Cell(entry.getKey().offset(-minX, -minY, -minZ), entry.getValue()));
		}

		return HousePlan.generated(id, new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1), list);
	}

	private static int between(RandomSource random, int[] range) {
		return range[0] + random.nextInt(Math.max(1, range[1] - range[0] + 1));
	}

	private static String pick(RandomSource random, Map<String, Double> weights) {
		double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
		double at = random.nextDouble() * total;
		for (Map.Entry<String, Double> entry : weights.entrySet()) {
			at -= entry.getValue();
			if (at <= 0.0) {
				return entry.getKey();
			}
		}

		return weights.keySet().iterator().next();
	}
}
