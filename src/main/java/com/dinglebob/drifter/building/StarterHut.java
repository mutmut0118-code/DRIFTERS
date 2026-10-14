package com.dinglebob.drifter.building;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The starter hut (DESIGN.md section 11, shelter tier 3): a 5x5 box of whatever it has, walls two high (the door's
 * height), a flat 5x5 roof on top, a door, and the chest, table, furnace and a torch inside. The cells are worked out
 * from the home's middle and the door side; the builder ({@link com.dinglebob.drifter.brain.activity.BuildHutActivity})
 * fills them in a player's order.
 *
 * <p>Why this shape (found in testing a taller one): a player standing on the floor can't see the top of a block above
 * eye level, and corners deep in a tall wall are hard to see at all. Two-high walls are built from the floor, the roof
 * from a one-block pillar in the middle (the tops of the walls are just below its eyes there): the outer ring on the
 * wall tops first, then the inner ring against it, and the block over its head last, from below.
 * TODO(milestone 8 later steps): 7x7 for Builders and Homebodies, the habit palette (favorite wood and stone), windows.
 */
public final class StarterHut {
	/** Half the outer size: 5x5 outside, 3x3 inside. */
	public static final int HALF = 2;
	public static final int WALL_HEIGHT = 2;
	/** The roof sits on this layer (y offset from the floor level), on top of the walls, over the whole footprint. */
	public static final int ROOF_LAYER = WALL_HEIGHT;
	/** How high it pillars in the middle to put the roof on. */
	public static final int PILLAR = 1;

	public final BlockPos center;
	public final Direction door;

	public StarterHut(BlockPos center, Direction door) {
		this.center = center.immutable();
		this.door = door;
	}

	public static StarterHut of(Home home) {
		return new StarterHut(home.center, home.hutDoor == null ? Direction.SOUTH : home.hutDoor);
	}

	/** The door side: the one with the most standable ground just outside it (it has to walk in and out). */
	public static Direction chooseDoor(Level level, BlockPos center) {
		Direction best = Direction.SOUTH;
		int bestScore = -1;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			int score = 0;
			for (int out = HALF + 1; out <= HALF + 3; out++) {
				if (KnownBlocks.isStandable(level, center.relative(side, out))) {
					score++;
				}
			}

			if (score > bestScore) {
				best = side;
				bestScore = score;
			}
		}

		return best;
	}

	/** The door's lower block (the upper one is above it). */
	public BlockPos doorPos() {
		return this.center.relative(this.door, HALF);
	}

	public boolean isDoorColumn(BlockPos pos) {
		BlockPos lower = this.doorPos();
		return pos.getX() == lower.getX() && pos.getZ() == lower.getZ() && pos.getY() >= this.center.getY() && pos.getY() <= this.center.getY() + 1;
	}

	/** Inside the walls (3x3), any height from the floor up to just under the roof. */
	public boolean isInside(BlockPos pos) {
		return Math.abs(pos.getX() - this.center.getX()) < HALF && Math.abs(pos.getZ() - this.center.getZ()) < HALF
			&& pos.getY() >= this.center.getY() && pos.getY() < this.center.getY() + ROOF_LAYER;
	}

	/** The floor under the whole footprint (5x5, one below the home's feet level). */
	public List<BlockPos> floor() {
		List<BlockPos> cells = new ArrayList<>();
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				cells.add(this.center.offset(dx, -1, dz));
			}
		}

		return cells;
	}

	/** The 3x3 inside, at feet level up to under the roof: has to be clear to walk and stand in. */
	public List<BlockPos> inside() {
		List<BlockPos> cells = new ArrayList<>();
		for (int dy = 0; dy < ROOF_LAYER; dy++) {
			for (int dx = -HALF + 1; dx <= HALF - 1; dx++) {
				for (int dz = -HALF + 1; dz <= HALF - 1; dz++) {
					cells.add(this.center.offset(dx, dy, dz));
				}
			}
		}

		return cells;
	}

	/** One ring of wall at this height (corners first, as players build them), leaving the door gap. */
	public List<BlockPos> wallLayer(int dy) {
		List<BlockPos> cells = new ArrayList<>();
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				if (Math.abs(dx) != HALF && Math.abs(dz) != HALF) {
					continue;
				}

				BlockPos cell = this.center.offset(dx, dy, dz);
				if (!this.isDoorColumn(cell)) {
					cells.add(cell);
				}
			}
		}

		// Corners first; above the door last (it has nothing under it, so it goes against its neighbours).
		cells.sort(Comparator.comparingInt((BlockPos cell) -> this.isCorner(cell) ? 0 : this.aboveDoor(cell) ? 2 : 1));
		return cells;
	}

	private boolean isCorner(BlockPos cell) {
		return Math.abs(cell.getX() - this.center.getX()) == HALF && Math.abs(cell.getZ() - this.center.getZ()) == HALF;
	}

	private boolean aboveDoor(BlockPos cell) {
		BlockPos door = this.doorPos();
		return cell.getX() == door.getX() && cell.getZ() == door.getZ();
	}

	/**
	 * The roof over the whole 5x5: the outer ring on the wall tops first (corners first), then the inner ring against it,
	 * the middle one last (it's over its head while it builds the rest).
	 */
	public List<BlockPos> roof() {
		List<BlockPos> cells = new ArrayList<>();
		for (int ring = HALF; ring >= 1; ring--) {
			List<BlockPos> layer = new ArrayList<>();
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
						layer.add(this.center.offset(dx, ROOF_LAYER, dz));
					}
				}
			}

			layer.sort(Comparator.comparingInt((BlockPos cell) -> this.isCorner(cell) ? 0 : 1));
			cells.addAll(layer);
		}

		cells.add(this.center.above(ROOF_LAYER));
		return cells;
	}

	/** Inside corners for the chest, table and furnace (not the middle, not in front of the door, not the bed's). */
	public List<BlockPos> furnitureSpots() {
		List<BlockPos> spots = new ArrayList<>();
		Direction left = this.door.getCounterClockWise();
		Direction right = this.door.getClockWise();
		Direction back = this.door.getOpposite();
		spots.add(this.center.relative(back).relative(left));
		spots.add(this.center.relative(back).relative(right));
		spots.add(this.center.relative(this.door).relative(left));
		return spots;
	}

	/** The bed's foot: the corner by the door on the right; it lies along the right wall, head toward the back. */
	public BlockPos bedFoot() {
		return this.center.relative(this.door).relative(this.door.getClockWise());
	}

	public BlockPos bedHead() {
		return this.bedFoot().relative(this.bedFacing());
	}

	/** Its bed in the hut: the head's position, or null if there's none. */
	@org.jetbrains.annotations.Nullable
	public BlockPos bed(Level level) {
		for (BlockPos pos : new BlockPos[]{this.bedHead(), this.bedFoot()}) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
				return state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD
					? pos : pos.relative(state.getValue(net.minecraft.world.level.block.BedBlock.FACING));
			}
		}

		return null;
	}

	/** The way the bed faces (foot to head). */
	public Direction bedFacing() {
		return this.door.getOpposite();
	}

	/** Where the torch goes: inside, on the back wall at head height. */
	public BlockPos torchSpot() {
		return this.center.relative(this.door.getOpposite()).above();
	}

	/** Is this cell filled with something solid already (a wall block, or one of its chests standing in for one)? */
	public static boolean filled(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
	}

	/** Is the whole shell there: floor, walls, roof (not counting the door)? */
	public boolean shellBuilt(Level level) {
		for (BlockPos cell : this.floor()) {
			if (!filled(level, cell)) {
				return false;
			}
		}

		for (int dy = 0; dy < WALL_HEIGHT; dy++) {
			for (BlockPos cell : this.wallLayer(dy)) {
				if (!filled(level, cell)) {
					return false;
				}
			}
		}

		for (BlockPos cell : this.roof()) {
			if (!filled(level, cell)) {
				return false;
			}
		}

		return true;
	}

	/** Blocks still needed for the shell. */
	public int missingBlocks(Level level) {
		int missing = 0;
		for (BlockPos cell : this.floor()) {
			missing += filled(level, cell) ? 0 : 1;
		}

		for (int dy = 0; dy < WALL_HEIGHT; dy++) {
			for (BlockPos cell : this.wallLayer(dy)) {
				missing += filled(level, cell) ? 0 : 1;
			}
		}

		for (BlockPos cell : this.roof()) {
			missing += filled(level, cell) ? 0 : 1;
		}

		return missing;
	}
}
