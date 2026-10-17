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
 * The starter hut (DESIGN.md section 11, shelter tier 3): a 7x7 box of whatever it has (5x5 inside: room for a row of
 * chests, the table, furnace, blast furnace, smoker and bed without being packed; user's request, 2026-10-16; huts built
 * before that stay 5x5), walls two high (the door's height), a flat roof on top, a door, and a torch inside. The cells are worked out
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
	/** Half the outer size: 7x7 outside, 5x5 inside. */
	public static final int HALF = 3;
	/** Huts built before they got bigger: 5x5 outside, 3x3 inside (kept as built). */
	public static final int OLD_HALF = 2;
	public static final int WALL_HEIGHT = 2;
	/** The roof sits on this layer (y offset from the floor level), on top of the walls, over the whole footprint. */
	public static final int ROOF_LAYER = WALL_HEIGHT;
	/** How high it pillars in the middle to put the roof on. */
	public static final int PILLAR = 1;

	public final BlockPos center;
	public final Direction door;
	/** Half the outer size (2 for the 5x5 hut, 3 for a 7x7 house) and the wall height (the roof sits on that layer). */
	public final int half;
	public final int wallHeight;
	/** Leaves window gaps in the walls (houses). */
	public final boolean windows;

	public StarterHut(BlockPos center, Direction door) {
		this(center, door, HALF, WALL_HEIGHT, false);
	}

	public StarterHut(BlockPos center, Direction door, int half, int wallHeight, boolean windows) {
		this.center = center.immutable();
		this.door = door;
		this.half = half;
		this.wallHeight = wallHeight;
		this.windows = windows;
	}

	/** How high it pillars in the middle to put the roof on (its eyes just over the wall tops). */
	public int pillar() {
		return this.wallHeight - 1;
	}

	/**
	 * Window gaps: the middle of each wall except the door's, at eye height (houses only). Glass goes in if it has some;
	 * otherwise they stay open, like a newbie's "hole for a window".
	 */
	public List<BlockPos> windowCells() {
		List<BlockPos> cells = new ArrayList<>();
		if (!this.windows) {
			return cells;
		}

		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (side == this.door) {
				continue;
			}

			BlockPos middle = this.center.relative(side, this.half).above();
			if (this.half >= 4) {
				// A big hut: two windows a wall, either side of the middle.
				cells.add(middle.relative(side.getClockWise(), 2));
				cells.add(middle.relative(side.getCounterClockWise(), 2));
			} else {
				cells.add(middle);
			}
		}

		return cells;
	}

	public static StarterHut of(Home home) {
		return new StarterHut(home.center, home.hutDoor == null ? Direction.SOUTH : home.hutDoor, home.hutHalf, home.hutWallHeight, home.hutWindows);
	}

	/** The door side: the one with the most standable ground just outside it (it has to walk in and out). */
	public static Direction chooseDoor(Level level, BlockPos center) {
		return chooseDoor(level, center, HALF);
	}

	/** The door side for a box of this half-size. */
	public static Direction chooseDoor(Level level, BlockPos center, int half) {
		Direction best = Direction.SOUTH;
		int bestScore = -1;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			int score = 0;
			for (int out = half + 1; out <= half + 3; out++) {
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
	/** Everything the hut takes up, floor to roof (for recording it as built). */
	public net.minecraft.world.level.levelgen.structure.BoundingBox box() {
		java.util.List<BlockPos> cells = new java.util.ArrayList<>(this.floor());
		cells.addAll(this.roof());
		for (int dy = 0; dy <= this.wallHeight; dy++) {
			cells.addAll(this.wallLayer(dy));
		}

		return net.minecraft.world.level.levelgen.structure.BoundingBox.encapsulatingPositions(cells)
			.orElse(new net.minecraft.world.level.levelgen.structure.BoundingBox(this.center));
	}

	public BlockPos doorPos() {
		return this.center.relative(this.door, this.half);
	}

	public boolean isDoorColumn(BlockPos pos) {
		BlockPos lower = this.doorPos();
		return pos.getX() == lower.getX() && pos.getZ() == lower.getZ() && pos.getY() >= this.center.getY() && pos.getY() <= this.center.getY() + 1;
	}

	/** Inside the walls (3x3), any height from the floor up to just under the roof. */
	public boolean isInside(BlockPos pos) {
		return Math.abs(pos.getX() - this.center.getX()) < this.half && Math.abs(pos.getZ() - this.center.getZ()) < this.half
			&& pos.getY() >= this.center.getY() && pos.getY() < this.center.getY() + this.wallHeight;
	}

	/** The floor under the whole footprint (5x5, one below the home's feet level). */
	public List<BlockPos> floor() {
		List<BlockPos> cells = new ArrayList<>();
		for (int dx = -this.half; dx <= this.half; dx++) {
			for (int dz = -this.half; dz <= this.half; dz++) {
				cells.add(this.center.offset(dx, -1, dz));
			}
		}

		return cells;
	}

	/** The 3x3 inside, at feet level up to under the roof: has to be clear to walk and stand in. */
	public List<BlockPos> inside() {
		List<BlockPos> cells = new ArrayList<>();
		for (int dy = 0; dy < this.wallHeight; dy++) {
			for (int dx = -this.half + 1; dx <= this.half - 1; dx++) {
				for (int dz = -this.half + 1; dz <= this.half - 1; dz++) {
					cells.add(this.center.offset(dx, dy, dz));
				}
			}
		}

		return cells;
	}

	/** One ring of wall at this height (corners first, as players build them), leaving the door gap. */
	public List<BlockPos> wallLayer(int dy) {
		List<BlockPos> cells = new ArrayList<>();
		for (int dx = -this.half; dx <= this.half; dx++) {
			for (int dz = -this.half; dz <= this.half; dz++) {
				if (Math.abs(dx) != this.half && Math.abs(dz) != this.half) {
					continue;
				}

				BlockPos cell = this.center.offset(dx, dy, dz);
				if (!this.isDoorColumn(cell) && !this.windowCells().contains(cell)) {
					cells.add(cell);
				}
			}
		}

		// Corners first; above the door last (it has nothing under it, so it goes against its neighbours).
		cells.sort(Comparator.comparingInt((BlockPos cell) -> this.isCorner(cell) ? 0 : this.aboveDoor(cell) ? 2 : 1));
		return cells;
	}

	/** A corner post cell (walls only). */
	public boolean isCornerPost(BlockPos cell) {
		return this.isCorner(cell) && cell.getY() >= this.center.getY() && cell.getY() < this.center.getY() + this.wallHeight;
	}

	private boolean isCorner(BlockPos cell) {
		return Math.abs(cell.getX() - this.center.getX()) == this.half && Math.abs(cell.getZ() - this.center.getZ()) == this.half;
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
		for (int ring = this.half; ring >= 1; ring--) {
			List<BlockPos> layer = new ArrayList<>();
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
						layer.add(this.center.offset(dx, this.wallHeight, dz));
					}
				}
			}

			layer.sort(Comparator.comparingInt((BlockPos cell) -> this.isCorner(cell) ? 0 : 1));
			cells.addAll(layer);
		}

		cells.add(this.center.above(this.wallHeight));
		return cells;
	}

	/** Inside corners for the chest, table and furnace (not the middle, not in front of the door, not the bed's). */
	public List<BlockPos> furnitureSpots() {
		List<BlockPos> spots = new ArrayList<>();
		Direction left = this.door.getCounterClockWise();
		Direction right = this.door.getClockWise();
		Direction back = this.door.getOpposite();
		int in = this.half - 1;
		spots.add(this.center.relative(back, in).relative(left, in));
		spots.add(this.center.relative(back, in).relative(right, in));
		spots.add(this.center.relative(this.door, in).relative(left, in));
		return spots;
	}

	/**
	 * Where the blast furnace and smoker go: the back wall either side of the middle. An old 5x5 hut has only two free
	 * cells for them (the back middle and the left middle): packed, but it works.
	 */
	public List<BlockPos> stationSpots() {
		Direction back = this.door.getOpposite();
		if (this.half < HALF) {
			return List.of(this.center.relative(back, 1), this.center.relative(this.door.getCounterClockWise(), 1));
		}

		return List.of(this.center.relative(back, this.half - 1).relative(this.door.getCounterClockWise()),
			this.center.relative(back, this.half - 1).relative(this.door.getClockWise()));
	}

	/** It has one of these stations to put in the hut, and the hut hasn't one yet. */
	public boolean stationToPlace(Level level, java.util.function.ToIntFunction<net.minecraft.world.item.Item> carried) {
		List<net.minecraft.world.item.Item> stations = List.of(net.minecraft.world.item.Items.BLAST_FURNACE, net.minecraft.world.item.Items.SMOKER);
		for (int i = 0; i < stations.size(); i++) {
			net.minecraft.world.item.Item station = stations.get(i);
			if (carried.applyAsInt(station) > 0 && !this.hasStation(level, station) && free(level, this.stationSpots().get(i))) {
				return true;
			}
		}

		return false;
	}

	/** One of this station is in the hut (anywhere inside, or on its spot). */
	public boolean hasStation(Level level, net.minecraft.world.item.Item station) {
		for (BlockPos cell : this.inside()) {
			if (level.getBlockState(cell).getBlock().asItem() == station) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Where its chests go, first choice first: the back corner, then along the left wall (side by side, as players line
	 * them up), then the right wall's free cells; never the middle, the doorway, the bed or a station's spot.
	 */
	public List<BlockPos> chestSpots() {
		Direction back = this.door.getOpposite();
		Direction left = this.door.getCounterClockWise();
		Direction right = this.door.getClockWise();
		int in = this.half - 1;
		List<BlockPos> taken = new ArrayList<>(this.furnitureSpots().subList(1, 3));
		taken.addAll(this.stationSpots());
		taken.addAll(List.of(this.bedFoot(), this.bedHead(), this.center, this.center.relative(this.door, in)));
		List<BlockPos> spots = new ArrayList<>();
		spots.add(this.furnitureSpots().get(0));
		for (Direction side : List.of(left, right)) {
			for (int k = in - 1; k >= -(in - 1); k--) {
				spots.add(this.center.relative(back, k).relative(side, in));
			}
		}

		for (int dx = -in; dx <= in; dx++) {
			for (int dz = -in; dz <= in; dz++) {
				spots.add(this.center.offset(dx, 0, dz));
			}
		}

		return spots.stream().distinct().filter(spot -> !taken.contains(spot)).toList();
	}

	/** The bed's foot: the corner by the door on the right; it lies along the right wall, head toward the back. */
	public BlockPos bedFoot() {
		return this.center.relative(this.door, this.half - 1).relative(this.door.getClockWise(), this.half - 1);
	}

	public BlockPos bedHead() {
		return this.bedFoot().relative(this.bedFacing());
	}

	/** Its bed in the hut: the head's position, or null if there's none. */
	@org.jetbrains.annotations.Nullable
	public BlockPos bed(Level level) {
		List<BlockPos> spots = new ArrayList<>(List.of(this.bedHead(), this.bedFoot()));
		for (int dx = -this.half + 1; dx <= this.half - 1; dx++) {
			for (int dz = -this.half + 1; dz <= this.half - 1; dz++) {
				spots.add(this.center.offset(dx, 0, dz));
			}
		}

		for (BlockPos pos : spots) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
				return state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD
					? pos : pos.relative(state.getValue(net.minecraft.world.level.block.BedBlock.FACING));
			}
		}

		return null;
	}

	/** Where a bed goes: its foot, and the way it faces (foot to head). */
	public record BedSpot(BlockPos foot, Direction facing) {
	}

	/**
	 * Where it can put a bed now: along the right wall as usual, or (that corner taken, say by a chest it put down before
	 * building) any two free floor cells inside, never the middle (it pillars there) nor just inside the door. Null if
	 * there's no room.
	 */
	@org.jetbrains.annotations.Nullable
	public BedSpot bedSpot(Level level) {
		if (free(level, this.bedFoot()) && free(level, this.bedHead())) {
			return new BedSpot(this.bedFoot(), this.bedFacing());
		}

		BlockPos byDoor = this.center.relative(this.door, this.half - 1);
		for (int dx = -this.half + 1; dx <= this.half - 1; dx++) {
			for (int dz = -this.half + 1; dz <= this.half - 1; dz++) {
				BlockPos foot = this.center.offset(dx, 0, dz);
				for (Direction facing : Direction.Plane.HORIZONTAL) {
					BlockPos head = foot.relative(facing);
					if (this.isInside(head) && !foot.equals(this.center) && !head.equals(this.center) && !foot.equals(byDoor)
						&& !head.equals(byDoor) && free(level, foot) && free(level, head)) {
						return new BedSpot(foot, facing);
					}
				}
			}
		}

		return null;
	}

	public static boolean free(Level level, BlockPos pos) {
		return level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty();
	}

	/** The way the bed faces (foot to head). */
	public Direction bedFacing() {
		return this.door.getOpposite();
	}

	/** Where the torch goes: inside, on the back wall at head height. */
	public BlockPos torchSpot() {
		// On solid back wall, not a window (user, 2026-10-17: "torches are on glass"): the middle if it's wall, else along it.
		Direction back = this.door.getOpposite();
		List<BlockPos> windows = this.windowCells();
		BlockPos middle = this.center.relative(back, this.half - 1);
		for (int along : new int[] {0, 1, -1, 2, -2}) {
			if (Math.abs(along) > this.half - 1) {
				continue;
			}

			for (int up = 1; up < Math.max(2, this.wallHeight); up++) {
				BlockPos spot = middle.relative(back.getClockWise(), along).above(up);
				if (!windows.contains(spot.relative(back))) {
					return spot;
				}
			}
		}

		return middle.above();
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

		for (int dy = 0; dy < this.wallHeight; dy++) {
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

		for (int dy = 0; dy < this.wallHeight; dy++) {
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
