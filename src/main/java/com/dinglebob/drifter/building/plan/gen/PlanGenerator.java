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
 * different infill upstairs), windows spaced along the walls (some two tall, some with sills), a door in the front (in
 * the middle or a little off), sometimes a porch (a slab deck, posts, a slab roof) and a chimney, and a roof: a gable
 * either way round, a hip (sloping on all four sides), a shed slope, or flat with a parapet; a loft under a gable; a
 * corner tower; and sometimes a wing out of the back (an L-shaped house), its
 * own roof running into the main one. Eaves sit level with the top of the wall (beside it, never
 * hanging in the air: what the hand-made designs taught). Each plan picks its materials once from the style's options.
 * The front is the low-z side; the plan is turned to suit its site when it's placed, like any other. Each plan is checked
 * (PlanCheck) before it's used.
 */
public final class PlanGenerator {
	private PlanGenerator() {
	}

	/** What the last plan made has (its roof, a wing, a porch...), for the tally and the showcase (debug). */
	static List<String> lastFeatures = List.of();

	/** Eaves stick out this far past the walls. */
	private static final int OVERHANG = 1;
	private static final int PORCH_DEPTH = 2;

	/** One plan's materials, picked once from the style's options. */
	private record Materials(Block floor, @Nullable Block base, @Nullable Block frame, int postsEvery, boolean beam, Block infill, Block upperInfill,
		Block window, @Nullable Block sill, Block stair, Block slab, Block gableFill, @Nullable Block parapet, Block door, Block upperFloor,
		Block porchFloor, Block porchPost, Block chimney) {
	}

	private static Materials materials(HouseStyle style, RandomSource random) {
		Block infill = style.material("infill", Blocks.OAK_PLANKS, random);
		return new Materials(style.material("floor", Blocks.OAK_PLANKS, random), style.material("base", null, random),
			style.material("frame", null, random), style.between("posts_every", 0, 0, random), style.chance("beam", 0.0, random), infill,
			style.material("upper_infill", infill, random), style.material("window", Blocks.GLASS, random), style.material("sill", null, random),
			style.material("stair", Blocks.SPRUCE_STAIRS, random), style.material("slab", Blocks.SPRUCE_SLAB, random),
			style.material("gable_fill", Blocks.OAK_PLANKS, random), style.material("parapet", null, random),
			style.material("door", Blocks.OAK_DOOR, random), style.material("upper_floor", Blocks.OAK_PLANKS, random),
			style.material("porch_floor", Blocks.SPRUCE_SLAB, random), style.material("porch_post", Blocks.OAK_FENCE, random),
			style.material("chimney_block", Blocks.COBBLESTONE, random));
	}

	/** One plan in this style, or null if this draw didn't make a sound house (the caller draws again). */
	@Nullable
	public static HousePlan generate(String id, HouseStyle style, RandomSource random) {
		Materials m = materials(style, random);
		List<String> features = new ArrayList<>();
		lastFeatures = features;
		int width = style.between("width", 5, 9, random);
		int depth = style.between("depth", 5, 8, random);
		int height = style.between("wall_height", 3, 4, random);
		int floors = style.chance("two_floors", 0.3, random) && width >= 5 && depth >= 5 ? 2 : 1;
		String roof = pick(random, style.roofs());
		// A shed roof climbs half a block a block: on a deep house it gets too tall.
		if (roof.equals("shed") && depth > 7) {
			roof = "gable";
		}

		features.add(roof);
		if (floors == 2) {
			features.add("two floors");
		}

		boolean tall = height >= 4 && style.chance("tall_windows", 0.3, random);
		boolean sills = m.sill() != null && random.nextBoolean();
		int spacing = Math.max(2, style.between("window_spacing", 2, 2, random) + (random.nextInt(3) == 0 ? 1 : 0));
		Map<BlockPos, BlockState> cells = new HashMap<>();
		int doorX = width / 2 + (style.chance("door_off_center", 0.3, random) ? (random.nextBoolean() ? 1 : -1) : 0);
		doorX = Math.max(1, Math.min(width - 2, doorX));
		int ladderX = doorX == 1 ? width - 2 : 1;

		// Floors: the ground floor, and the deck between storeys (with a hole for the ladder).
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < depth; z++) {
				cells.put(new BlockPos(x, 0, z), m.floor().defaultBlockState());
				if (floors == 2 && !(x == ladderX && z == depth - 2)) {
					cells.put(new BlockPos(x, height + 1, z), m.upperFloor().defaultBlockState());
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

						cells.put(new BlockPos(x, y, z), wall(m, storey, x, z, y - y0, width, depth, height));
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
						window(cells, m, new BlockPos(x, y, 0), Direction.NORTH, sills && row == 2);
					}

					if (!(floors == 2 && x == ladderX)) {
						window(cells, m, new BlockPos(x, y, depth - 1), Direction.SOUTH, sills && row == 2);
					}
				}

				for (int z = 1; z < depth - 1; z++) {
					if ((z - 1) % spacing == spacing / 2) {
						window(cells, m, new BlockPos(0, y, z), Direction.WEST, sills && row == 2);
						window(cells, m, new BlockPos(width - 1, y, z), Direction.EAST, sills && row == 2);
					}
				}
			}
		}

		// The door.
		BlockState door = m.door().defaultBlockState();
		if (!door.hasProperty(DoorBlock.FACING)) {
			return null;
		}

		door = door.setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false);
		cells.put(new BlockPos(doorX, 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		cells.put(new BlockPos(doorX, 2, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

		// The ladder up, on the back wall inside.
		if (floors == 2) {
			for (int y = 1; y <= height + 1; y++) {
				cells.put(new BlockPos(ladderX, y, depth - 2), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
			}
		}

		int top = (floors - 1) * (height + 1) + height;
		switch (roof) {
			case "gable_z" -> gable(cells, m, width, depth, top, false, BOTH_ENDS);
			case "shed" -> shed(cells, m, width, depth, top);
			case "hip" -> hip(cells, m, width, depth, top);
			case "flat" -> flat(cells, m, width, depth, top);
			default -> gable(cells, m, width, depth, top, true, BOTH_ENDS);
		}

		// (No porch on walls under three high: its roof would be at head height.)
		if (style.chance("porch", 0.25, random) && height >= 3) {
			porch(cells, m, width, doorX, height);
			features.add("porch");
		}

		boolean chimney = style.chance("chimney", 0.25, random);
		if (chimney) {
			chimney(cells, m, width - 2, depth - 1);
			features.add("chimney");
		}

		// (Drawn last, so a plan without a wing comes out as it did before wings.)
		if (roof.equals("gable") && style.chance("wing", 0.0, random)) {
			String shape = wing(cells, m, style, random, width, depth, height, top, floors == 2 ? ladderX : -1, spacing, chimney);
			if (shape != null) {
				features.add(shape + " wing");
			}
		}

		// A loft under a gable roof with room for one (drawn after the wing, for the same reason; not with a wing).
		boolean gabled = roof.equals("gable") || roof.equals("gable_z");
		int span = roof.equals("gable") ? depth : width;
		if (gabled && floors == 1 && height >= 3 && span >= ATTIC_SPAN && features.stream().noneMatch(f -> f.endsWith("wing"))
			&& style.chance("loft", 0.0, random)) {
			loft(cells, m, width, depth, top, roof.equals("gable"), chimney);
			features.add("loft");
		}

		// A tower at the front corner (drawn last too; not over a loft, nor beside a porch, whose roof it would cut; on one
		// storey only: on two its roof is fourteen up, out of reach from any pillar, found in testing).
		if (!roof.equals("shed") && floors == 1 && width >= 7 && depth >= 6 && !features.contains("loft") && !features.contains("porch")
			&& style.chance("tower", 0.0, random)) {
			tower(cells, m, width, depth, top);
			features.add("tower");
		}

		return normalize(id, cells);
	}

	private static BlockState wall(Materials m, int storey, int x, int z, int row, int width, int depth, int height) {
		boolean corner = (x == 0 || x == width - 1) && (z == 0 || z == depth - 1);
		boolean alongX = z == 0 || z == depth - 1;
		int along = alongX ? x : z;
		boolean post = m.postsEvery() > 0 && along % m.postsEvery() == 0;
		if (m.frame() != null && (corner || post)) {
			return axis(m.frame(), Direction.Axis.Y);
		}

		if (m.frame() != null && m.beam() && row == height) {
			return axis(m.frame(), alongX ? Direction.Axis.X : Direction.Axis.Z);
		}

		if (storey == 0 && row == 1 && m.base() != null) {
			return m.base().defaultBlockState();
		}

		return (storey == 0 ? m.infill() : m.upperInfill()).defaultBlockState();
	}

	private static void window(Map<BlockPos, BlockState> cells, Materials m, BlockPos pos, Direction out, boolean sill) {
		BlockState there = cells.get(pos);
		// Never in a post or a beam (they hold the frame) or the door.
		if (there == null || there.getBlock() instanceof RotatedPillarBlock || there.getBlock() instanceof DoorBlock) {
			return;
		}

		cells.put(pos, m.window().defaultBlockState());
		if (sill && m.sill() != null) {
			BlockState stair = m.sill().defaultBlockState();
			if (stair.hasProperty(StairBlock.FACING)) {
				stair = stair.setValue(StairBlock.FACING, out.getOpposite()).setValue(StairBlock.HALF, Half.TOP);
			}

			cells.putIfAbsent(pos.below().relative(out), stair);
		}
	}

	/**
	 * A gable roof, its ridge along x (or z): rows of stairs climbing in from both sides, the first beside the top of the
	 * wall, a slab ridge where they meet on an odd width; the triangles at the two ends filled in (they also hold the rows
	 * up while it's built), round anything already there (the top of a door), with a window in the middle of the attic on
	 * a wide enough roof (at these ends: {@link #BOTH_ENDS} or only the {@link #FAR_END}).
	 */
	private static void gable(Map<BlockPos, BlockState> cells, Materials m, int width, int depth, int top, boolean ridgeAlongX, int windowEnds) {
		int span = ridgeAlongX ? depth : width;
		int length = ridgeAlongX ? width : depth;
		int low = -OVERHANG;
		int high = span - 1 + OVERHANG;
		for (int k = 0; low <= high; k++, low++, high--) {
			int y = top + k;
			for (int a = -OVERHANG; a < length + OVERHANG; a++) {
				if (low == high) {
					put(cells, ridgeAlongX, a, y, low, m.slab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
					continue;
				}

				put(cells, ridgeAlongX, a, y, low, stair(m.stair(), ridgeAlongX ? Direction.SOUTH : Direction.EAST));
				put(cells, ridgeAlongX, a, y, high, stair(m.stair(), ridgeAlongX ? Direction.NORTH : Direction.WEST));
			}

			if (k >= 1) {
				int middle = (span - 1) / 2;
				boolean attic = k == 2 && span >= ATTIC_SPAN;
				for (int s = low + 1; s < high; s++) {
					BlockState near = attic && s == middle && (windowEnds & NEAR_END) != 0 ? m.window().defaultBlockState() : m.gableFill().defaultBlockState();
					BlockState far = attic && s == middle && (windowEnds & FAR_END) != 0 ? m.window().defaultBlockState() : m.gableFill().defaultBlockState();
					putIfFree(cells, ridgeAlongX, 0, y, s, near);
					putIfFree(cells, ridgeAlongX, length - 1, y, s, far);
				}
			}
		}
	}

	/**
	 * A one-storey wing out of the back, on one side (an L) or in the middle (a T): its own floor, walls and windows, a doorway through the back
	 * wall into it, and a gable roof with its ridge front to back whose slopes run on up the main roof's back slope until
	 * they meet it (cells under the main roof's surface are left out, so the two roofs join with no gap). Only behind a
	 * gable whose ridge runs along the front. Never over the chimney or the ladder.
	 */
	@Nullable
	private static String wing(Map<BlockPos, BlockState> cells, Materials m, HouseStyle style, RandomSource random, int width, int depth, int height,
		int top, int ladderX, int spacing, boolean chimney) {
		int widest = Math.min(width - 2, depth + 1);
		if (chimney) {
			widest = Math.min(widest, width - 3);
		}

		if (widest < 5) {
			return null;
		}

		int w = 5 + random.nextInt(widest - 4);
		int d = style.between("wing_depth", 3, 5, random);
		// Left, right or (a T) the middle; on the left when the chimney is on the right.
		int where = chimney ? 0 : random.nextInt(3);
		boolean left = where == 0;
		int x0 = where == 0 ? 0 : where == 1 ? width - w : (width - w) / 2;
		int x1 = x0 + w - 1;
		int shared = depth - 1;
		int back = shared + d;

		// Clear its room (a window sill outside the back wall, the back eave over it) and the eave along where it joins.
		cells.keySet().removeIf(pos -> pos.getX() > x0 && pos.getX() < x1 && pos.getZ() >= depth && pos.getZ() < back && pos.getY() >= 1
			&& pos.getY() <= height);
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			cells.remove(new BlockPos(x, top, depth));
		}

		// (The eave's corner past the side wall, left on its own by that, would float: found by PlanCheck.)
		if (x0 - 1 == 0) {
			cells.remove(new BlockPos(-1, top, depth));
		}

		if (x1 + 1 == width - 1) {
			cells.remove(new BlockPos(width, top, depth));
		}

		for (int x = x0; x <= x1; x++) {
			for (int z = depth; z <= back; z++) {
				cells.put(new BlockPos(x, 0, z), m.floor().defaultBlockState());
			}
		}

		for (int y = 1; y <= height; y++) {
			for (int x = x0; x <= x1; x++) {
				for (int z = shared; z <= back; z++) {
					if (x == x0 || x == x1 || z == shared || z == back) {
						cells.put(new BlockPos(x, y, z), wall(m, 0, x - x0, z - shared, y, w, d + 1, height));
					}
				}
			}
		}

		// The way through, in the middle of the shared wall (not where the ladder hangs on it).
		int through = (x0 + x1) / 2;
		if (through == ladderX) {
			through += left || where == 2 && through < width / 2 ? 1 : -1;
		}

		cells.remove(new BlockPos(through, 1, shared));
		cells.remove(new BlockPos(through, 2, shared));
		for (int z = depth; z < back; z++) {
			if ((z - depth) % spacing == spacing / 2) {
				window(cells, m, new BlockPos(x0, 2, z), Direction.WEST, false);
				window(cells, m, new BlockPos(x1, 2, z), Direction.EAST, false);
			}
		}

		for (int x = x0 + 1; x < x1; x++) {
			if ((x - x0 - 1) % spacing == spacing / 2) {
				window(cells, m, new BlockPos(x, 2, back), Direction.SOUTH, false);
			}
		}

		Map<BlockPos, BlockState> roof = new HashMap<>();
		gable(roof, m, w, d + 1, height, false, FAR_END);
		for (Map.Entry<BlockPos, BlockState> entry : roof.entrySet()) {
			BlockPos pos = entry.getKey().offset(x0, 0, shared);
			// Over the main house: only what shows above its roof's back slope.
			if (pos.getZ() < depth && pos.getY() <= top + Math.min(pos.getZ() + 1, depth - pos.getZ())) {
				continue;
			}

			cells.put(pos, entry.getValue());
		}

		return where == 2 ? "T" : "L";
	}

	/**
	 * A hip roof: rings of stairs climbing in from all four sides (the first beside the top of the wall), a slab ridge
	 * along the long way where the short sides meet. Each ring from the third up has a ring of the gable-end block under
	 * it, inside the one below, to rest on (stairs climbing in only touch the ring below at an edge: nothing to place
	 * them against, and PlanCheck would call them floating).
	 */
	private static void hip(Map<BlockPos, BlockState> cells, Materials m, int width, int depth, int top) {
		BlockState backing = m.gableFill().defaultBlockState();
		for (int k = 0; ; k++) {
			int ax = -OVERHANG + k;
			int bx = width - 1 + OVERHANG - k;
			int az = -OVERHANG + k;
			int bz = depth - 1 + OVERHANG - k;
			if (ax > bx || az > bz) {
				return;
			}

			int y = top + k;
			List<BlockPos> ring = new ArrayList<>();
			if (ax == bx || az == bz) {
				// The ridge.
				for (int x = ax; x <= bx; x++) {
					for (int z = az; z <= bz; z++) {
						cells.put(new BlockPos(x, y, z), m.slab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
						ring.add(new BlockPos(x, y, z));
					}
				}
			} else {
				for (int x = ax; x <= bx; x++) {
					cells.put(new BlockPos(x, y, az), stair(m.stair(), Direction.SOUTH));
					cells.put(new BlockPos(x, y, bz), stair(m.stair(), Direction.NORTH));
					ring.add(new BlockPos(x, y, az));
					ring.add(new BlockPos(x, y, bz));
				}

				for (int z = az + 1; z < bz; z++) {
					cells.put(new BlockPos(ax, y, z), stair(m.stair(), Direction.EAST));
					cells.put(new BlockPos(bx, y, z), stair(m.stair(), Direction.WEST));
					ring.add(new BlockPos(ax, y, z));
					ring.add(new BlockPos(bx, y, z));
				}
			}

			if (k >= 2) {
				for (BlockPos pos : ring) {
					cells.put(pos.below(), backing);
				}
			}

			if (ax == bx || az == bz) {
				return;
			}
		}
	}

	/**
	 * A loft: a floor at the top of the walls over one end of the room (along the ridge, where there's headroom: one side
	 * end under a ridge along x, the back under one along z), its edge cells resting on the roof and gable round it, and
	 * a ladder up the back wall inside through a hole in it (the back wall there kept solid for the ladder to hang on; on
	 * the side away from the chimney). The attic window in the gable end lights it.
	 */
	private static void loft(Map<BlockPos, BlockState> cells, Materials m, int width, int depth, int top, boolean ridgeAlongX, boolean chimney) {
		int ladderX = chimney ? 1 : width - 2;
		int x0 = 1;
		int x1 = width - 2;
		int z0 = 1;
		if (ridgeAlongX) {
			int length = Math.max(2, (width - 2) / 2);
			if (ladderX == 1) {
				x1 = length;
			} else {
				x0 = width - 1 - length;
			}
		} else {
			z0 = depth - 1 - Math.max(2, (depth - 2) / 2);
		}

		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= depth - 2; z++) {
				if (!(x == ladderX && z == depth - 2)) {
					cells.put(new BlockPos(x, top + 1, z), m.upperFloor().defaultBlockState());
				}
			}
		}

		for (int y = 1; y <= top + 1; y++) {
			BlockPos wall = new BlockPos(ladderX, y, depth - 1);
			BlockState there = cells.get(wall);
			// (Up the wall; the top rung hangs on the back roof row, whose high side is solid.)
			if (y <= top && there != null && !there.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, wall)) {
				cells.put(wall, m.infill().defaultBlockState());
			}

			cells.put(new BlockPos(ladderX, y, depth - 2), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		}

		// (A sill under a window it just walled up would be left hanging.)
		for (int y = 1; y <= top; y++) {
			BlockPos outside = new BlockPos(ladderX, y, depth);
			if (cells.get(outside) != null && cells.get(outside).getBlock() instanceof StairBlock) {
				cells.remove(outside);
			}
		}
	}

	/** A corner tower's outer size. */
	private static final int TOWER = 4;

	/**
	 * A square tower at the front left corner: the house's walls there carried on up past the top of its roof (the roof
	 * over that corner taken out, the tower's walls closing it off), windows near the top on the two outer faces, and a
	 * little hip (pyramid) roof. Inside it's open down into the room below, a tall space like a lantern.
	 */
	private static void tower(Map<BlockPos, BlockState> cells, Materials m, int width, int depth, int top) {
		// Up to a block over the house's highest point (not counting the chimney).
		int highest = top;
		for (BlockPos pos : cells.keySet()) {
			if (cells.get(pos).getBlock() != m.chimney() || pos.getY() <= top) {
				highest = Math.max(highest, pos.getY());
			}
		}

		int towerTop = highest + 1;
		cells.keySet().removeIf(pos -> pos.getX() <= TOWER - 1 && pos.getZ() <= TOWER - 1 && (pos.getY() > top
			|| pos.getY() == top && (pos.getX() < 0 || pos.getZ() < 0)));
		for (int y = top + 1; y <= towerTop; y++) {
			for (int x = 0; x < TOWER; x++) {
				for (int z = 0; z < TOWER; z++) {
					if (x == 0 || x == TOWER - 1 || z == 0 || z == TOWER - 1) {
						cells.put(new BlockPos(x, y, z), wall(m, 1, x, z, y - top, TOWER, TOWER, towerTop - top));
					}
				}
			}
		}

		for (int along = 1; along <= 2; along++) {
			window(cells, m, new BlockPos(0, towerTop - 1, along), Direction.WEST, false);
			window(cells, m, new BlockPos(along, towerTop - 1, 0), Direction.NORTH, false);
		}

		hip(cells, m, TOWER, TOWER, towerTop);
	}

	/** Which gable ends get an attic window. */
	private static final int NEAR_END = 1;
	private static final int FAR_END = 2;
	private static final int BOTH_ENDS = NEAR_END | FAR_END;
	/** Narrowest roof (across the ridge) with an attic window in its gable ends. */
	private static final int ATTIC_SPAN = 7;

	private static void putIfFree(Map<BlockPos, BlockState> cells, boolean ridgeAlongX, int along, int y, int across, BlockState state) {
		cells.putIfAbsent(ridgeAlongX ? new BlockPos(along, y, across) : new BlockPos(across, y, along), state);
	}

	/** Places at (along, y, across) for a ridge along x, or the same turned for one along z. */
	private static void put(Map<BlockPos, BlockState> cells, boolean ridgeAlongX, int along, int y, int across, BlockState state) {
		cells.put(ridgeAlongX ? new BlockPos(along, y, across) : new BlockPos(across, y, along), state);
	}

	/**
	 * A shed roof: one slope rising toward the back, half a block a block (slabs, bottom then top), the side walls built up
	 * under it to hold it.
	 */
	private static void shed(Map<BlockPos, BlockState> cells, Materials m, int width, int depth, int top) {
		for (int z = -OVERHANG; z < depth + OVERHANG; z++) {
			int k = z + OVERHANG;
			// The eave beside the wall top, then over the wall and climbing.
			int y = k == 0 ? top : top + 1 + (k - 1) / 2;
			SlabType type = k == 0 || (k - 1) % 2 == 0 ? SlabType.BOTTOM : SlabType.TOP;
			// (No overhang at the back where the slope steps up there: past the walls nothing holds that row, it would only
			// touch the one before at an edge. Found by PlanCheck.)
			if (z == depth && type == SlabType.BOTTOM) {
				continue;
			}

			for (int x = -OVERHANG; x < width + OVERHANG; x++) {
				cells.put(new BlockPos(x, y, z), m.slab().defaultBlockState().setValue(SlabBlock.TYPE, type));
			}

			if (z >= 0 && z < depth) {
				for (int fill = top + 1; fill < y; fill++) {
					cells.put(new BlockPos(0, fill, z), m.gableFill().defaultBlockState());
					cells.put(new BlockPos(width - 1, fill, z), m.gableFill().defaultBlockState());
					if (z == depth - 1) {
						for (int x = 1; x < width - 1; x++) {
							cells.put(new BlockPos(x, fill, z), m.gableFill().defaultBlockState());
						}
					}
				}
			}
		}
	}

	/** A flat roof over the walls, with a parapet round it. */
	private static void flat(Map<BlockPos, BlockState> cells, Materials m, int width, int depth, int top) {
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < depth; z++) {
				cells.put(new BlockPos(x, top + 1, z), m.gableFill().defaultBlockState());
				if (x == 0 || x == width - 1 || z == 0 || z == depth - 1) {
					BlockState edge = m.parapet() != null ? m.parapet().defaultBlockState()
						: m.slab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
					cells.put(new BlockPos(x, top + 2, z), edge);
				}
			}
		}
	}

	/**
	 * A porch along the front round the door: a slab deck, a post at each outer corner and a slab roof at the top of the
	 * ground-floor wall (where an eave is there already, that is its roof).
	 */
	private static void porch(Map<BlockPos, BlockState> cells, Materials m, int width, int doorX, int height) {
		int x0 = Math.max(0, doorX - 2);
		int x1 = Math.min(width - 1, doorX + 2);
		BlockState deck = m.porchFloor().defaultBlockState();
		if (deck.hasProperty(SlabBlock.TYPE)) {
			deck = deck.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
		}

		BlockState roof = m.slab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
		for (int x = x0; x <= x1; x++) {
			for (int z = -PORCH_DEPTH; z < 0; z++) {
				cells.putIfAbsent(new BlockPos(x, 0, z), deck);
				cells.putIfAbsent(new BlockPos(x, height, z), roof);
			}
		}

		for (int x : new int[] {x0, x1}) {
			for (int y = 1; y < height; y++) {
				cells.putIfAbsent(new BlockPos(x, y, -PORCH_DEPTH), m.porchPost().defaultBlockState());
			}
		}
	}

	/** A chimney up the back wall and through the roof, two blocks proud of it. */
	private static void chimney(Map<BlockPos, BlockState> cells, Materials m, int x, int z) {
		int roofTop = 0;
		for (BlockPos pos : cells.keySet()) {
			if (pos.getX() == x && pos.getZ() == z) {
				roofTop = Math.max(roofTop, pos.getY());
			}
		}

		for (int y = 1; y <= roofTop + 2; y++) {
			cells.put(new BlockPos(x, y, z), m.chimney().defaultBlockState());
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
