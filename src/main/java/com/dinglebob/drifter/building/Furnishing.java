package com.dinglebob.drifter.building;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.building.plan.PlacedPlan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Where a house's things go, laid out the way a player furnishes a base (the user's furnishing guidebook, 2026-10-23:
 * "use that in reference so the houses can look and feel like they are made by a player"):
 * <ul>
 * <li>a dump chest just inside the front door, to one side of the way in;</li>
 * <li>a storage wall in the main room: chests in pairs (double chests), two high where the ceiling allows, the crafting
 * table set in the middle between them (the hub: every recipe pulls from storage);</li>
 * <li>a work wall beside it: the furnace and the smoker side by side, the fuel chest at the end;</li>
 * <li>a bedside chest by the head of the bed.</li>
 * </ul>
 * Each faces the floor it's used from, its back to a wall (placed from where you'd use it, as a player places it; found
 * by the user: chests facing the corner "look backwards"), never in a doorway or at the foot of stairs or a ladder, and
 * always leaving a way from the door to everything in the room. The layout is worked out on the house's own shell, so it's
 * the same each time: a Drifter furnishing its house puts each thing it has in the next free place of the layout
 * ({@link #next}); a town made ready gets all of it at once ({@link #furnish}).
 */
public final class Furnishing {
	private Furnishing() {
	}

	public enum Use {
		STORAGE_CHEST, CRAFTING_TABLE, FURNACE, SMOKER, FUEL_CHEST, DUMP_CHEST, BEDSIDE_CHEST;

		public boolean chest() {
			return this == STORAGE_CHEST || this == FUEL_CHEST || this == DUMP_CHEST || this == BEDSIDE_CHEST;
		}

		public Block block() {
			return switch (this) {
				case CRAFTING_TABLE -> Blocks.CRAFTING_TABLE;
				case FURNACE -> Blocks.FURNACE;
				case SMOKER -> Blocks.SMOKER;
				default -> Blocks.CHEST;
			};
		}
	}

	/** One thing's place: where, what for, which way it faces (into the room), and whether it sits on another chest. */
	public record Spot(BlockPos pos, Use use, Direction facing, boolean upper) {
	}

	/** The smallest room worth furnishing. */
	private static final int SMALLEST_ROOM = 6;
	/** Bigger than this and it isn't a room (it got out through a gap). */
	private static final int BIGGEST_ROOM = 400;

	/** The whole layout of this house, in the order things go in (storage first). {@code bed}: either half, or null. */
	public static List<Spot> plan(BlockGetter level, PlacedPlan placed, @Nullable BlockPos bed) {
		return new Layout(level, placed, bed).spots();
	}

	/**
	 * The next free place in the layout for a thing of this kind (a chest: any chest's place; the lower row before the
	 * upper), or null if the layout has none left. {@code upper}: chests on top of chests too.
	 */
	@Nullable
	public static Spot next(BlockGetter level, PlacedPlan placed, @Nullable BlockPos bed, Block block, boolean upper) {
		for (Spot spot : plan(level, placed, bed)) {
			boolean kind = spot.use().chest() ? block == Blocks.CHEST : spot.use().block() == block;
			if (kind && (upper || !spot.upper()) && level.getBlockState(spot.pos()).canBeReplaced()) {
				return spot;
			}
		}

		return null;
	}

	/** All of it put in at once (a town made ready; not how a Drifter does it). Returns the layout. */
	public static List<Spot> furnish(LevelAccessor level, PlacedPlan placed, @Nullable BlockPos bed, int flags) {
		List<Spot> spots = plan(level, placed, bed);
		for (Spot spot : spots) {
			if (!level.getBlockState(spot.pos()).canBeReplaced()) {
				continue;
			}

			if (spot.use().chest()) {
				Furnish.chest(level, spot.pos(), spot.facing(), flags);
			} else {
				BlockState state = spot.use().block().defaultBlockState();
				if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
					state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, spot.facing());
				}

				level.setBlock(spot.pos(), state, flags);
			}
		}

		return spots;
	}

	/** Things that are furniture: the layout's places count as floor on the shell (so it's the same before and after). */
	static boolean ours(BlockState state) {
		return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL) || state.is(Blocks.CRAFTING_TABLE)
			|| state.getBlock() instanceof AbstractFurnaceBlock;
	}

	private static final class Layout {
		private final BlockGetter level;
		private final PlacedPlan placed;
		private final Map<BlockPos, BlockState> want;
		private final Set<BlockPos> bedCells = new HashSet<>();
		@Nullable
		private BlockPos bedHead;
		@Nullable
		private Direction bedFacing;
		private final List<BlockPos> doors = new ArrayList<>();
		private final Set<BlockPos> taken = new HashSet<>();
		private final List<Spot> spots = new ArrayList<>();

		Layout(BlockGetter level, PlacedPlan placed, @Nullable BlockPos bed) {
			this.level = level;
			this.placed = placed;
			this.want = placed.buildMap();
			if (bed != null && level.getBlockState(bed).getBlock() instanceof BedBlock) {
				BlockState state = level.getBlockState(bed);
				BlockPos other = bed.relative(BedBlock.getConnectedDirection(state));
				this.bedCells.add(bed);
				this.bedCells.add(other);
				this.bedFacing = state.getValue(BedBlock.FACING);
				this.bedHead = state.getValue(BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD ? bed : other;
			}

			for (PlacedPlan.Cell cell : placed.build) {
				if (cell.state().getBlock() instanceof DoorBlock && cell.state().getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
					this.doors.add(cell.pos());
				}
			}

			this.doors.sort(Comparator.comparingInt(BlockPos::getY));
		}

		List<Spot> spots() {
			List<Set<BlockPos>> rooms = this.rooms();
			if (rooms.isEmpty()) {
				return this.spots;
			}

			// The front door: the lowest; the room in from it is the entry. The main room: the biggest on the entry's floor
			// that isn't the bedroom (the entry if there's only one).
			BlockPos front = this.doors.isEmpty() ? null : this.doors.get(0);
			Set<BlockPos> entry = front == null ? null : rooms.stream().filter(room -> this.inside(front, room) != null).findFirst().orElse(null);
			Set<BlockPos> bedroom = rooms.stream().filter(room -> this.bedCells.stream().anyMatch(b -> this.touches(b, room))).findFirst().orElse(null);
			int floorY = entry != null ? entry.iterator().next().getY() : rooms.get(0).iterator().next().getY();
			Set<BlockPos> main = rooms.stream().filter(room -> room.iterator().next().getY() == floorY && room != bedroom)
				.max(Comparator.comparingInt(Set::size)).orElse(entry != null ? entry : rooms.get(0));

			// 1. The storage wall, the crafting table in the middle of it.
			List<Run> runs = this.runs(main);
			Run storage = runs.isEmpty() ? null : runs.get(0);
			if (storage != null) {
				Use[] row = storage.cells.size() >= 5 ? new Use[] {Use.STORAGE_CHEST, Use.STORAGE_CHEST, Use.CRAFTING_TABLE, Use.STORAGE_CHEST, Use.STORAGE_CHEST}
					: storage.cells.size() >= 3 ? new Use[] {Use.STORAGE_CHEST, Use.STORAGE_CHEST, Use.CRAFTING_TABLE}
					: storage.cells.size() == 2 ? new Use[] {Use.STORAGE_CHEST, Use.CRAFTING_TABLE} : new Use[] {Use.CRAFTING_TABLE};
				this.lay(main, storage, row, true);
			}

			// 2. The work wall: furnace and smoker side by side, the fuel chest at the end (another wall of the main room, or of
			// the entry).
			for (Set<BlockPos> room : entry != null && entry != main ? List.of(main, entry) : List.of(main)) {
				Run work = this.runs(room).stream().filter(run -> run.cells.size() >= 2).findFirst().orElse(null);
				if (work != null) {
					this.lay(room, work, work.cells.size() >= 3 ? new Use[] {Use.FUEL_CHEST, Use.FURNACE, Use.SMOKER} : new Use[] {Use.FURNACE, Use.SMOKER}, false);
					break;
				}
			}

			// 3. The dump chest, just inside the front door to one side.
			if (front != null && entry != null) {
				BlockPos in = this.inside(front, entry);
				if (in != null) {
					for (Direction side : Direction.Plane.HORIZONTAL) {
						BlockPos pos = in.relative(side);
						Direction facing = this.facing(pos, entry);
						if (entry.contains(pos) && !this.taken.contains(pos) && facing != null && this.wall(pos.relative(facing.getOpposite()))
							&& this.keep(entry, pos)) {
							this.add(pos, Use.DUMP_CHEST, facing, false);
							break;
						}
					}
				}
			}

			// 4. The bedside chest, beside the head of the bed.
			if (this.bedHead != null && this.bedFacing != null && bedroom != null) {
				for (Direction side : new Direction[] {this.bedFacing.getClockWise(), this.bedFacing.getCounterClockWise()}) {
					BlockPos pos = this.bedHead.relative(side);
					if (bedroom.contains(pos) && !this.taken.contains(pos) && this.keep(bedroom, pos)) {
						this.add(pos, Use.BEDSIDE_CHEST, this.bedFacing.getOpposite(), false);
						break;
					}
				}
			}

			return this.spots;
		}

		/** Puts this row along the run, from its middle, as far as it keeps the room usable; chests two high if {@code stack}. */
		private void lay(Set<BlockPos> room, Run run, Use[] row, boolean stack) {
			int start = (run.cells.size() - row.length) / 2;
			for (int i = 0; i < row.length; i++) {
				BlockPos pos = run.cells.get(start + i);
				if (!this.keep(room, pos)) {
					continue;
				}

				this.add(pos, row[i], run.facing, false);
				// A second chest on top: the top one at eye level, nothing solid over it (a chest won't open under a full block).
				BlockPos up = pos.above();
				if (stack && row[i].chest() && this.air(up) && !this.full(up.above()) && !this.want.containsKey(up)) {
					this.add(up, row[i], run.facing, true);
				}
			}
		}

		private void add(BlockPos pos, Use use, Direction facing, boolean upper) {
			this.taken.add(pos);
			this.spots.add(new Spot(pos.immutable(), use, facing, upper));
		}

		/** The rooms: floor inside the house, in pieces walled off from each other (doors part them), biggest first. */
		private List<Set<BlockPos>> rooms() {
			Set<BlockPos> floor = new HashSet<>();
			for (BlockPos column : this.placed.footprint()) {
				for (int dy = 0; dy < this.placed.sizeY - 1; dy++) {
					BlockPos pos = column.above(dy);
					if (this.floor(pos)) {
						floor.add(pos.immutable());
					}
				}
			}

			List<Set<BlockPos>> rooms = new ArrayList<>();
			Set<BlockPos> seen = new HashSet<>();
			for (BlockPos start : floor) {
				if (!seen.add(start)) {
					continue;
				}

				Set<BlockPos> room = new HashSet<>();
				ArrayDeque<BlockPos> queue = new ArrayDeque<>();
				room.add(start);
				queue.add(start);
				while (!queue.isEmpty()) {
					BlockPos pos = queue.poll();
					for (Direction side : Direction.Plane.HORIZONTAL) {
						BlockPos next = pos.relative(side);
						if (floor.contains(next) && seen.add(next)) {
							room.add(next);
							queue.add(next);
						}
					}
				}

				if (room.size() >= SMALLEST_ROOM && room.size() <= BIGGEST_ROOM) {
					rooms.add(room);
				}
			}

			rooms.sort(Comparator.comparingInt((Set<BlockPos> r) -> -r.size()).thenComparingInt(r -> r.iterator().next().getY()));
			return rooms;
		}

		/** Floor inside the house: free (or one of its things) with headroom, on something firm, under its roof, walled each way. */
		private boolean floor(BlockPos pos) {
			if (!this.placed.contains(pos, 0) || this.want.containsKey(pos) || this.want.containsKey(pos.above()) || this.bedCells.contains(pos)
				|| !this.air(pos) || !this.air(pos.above())) {
				return false;
			}

			BlockState under = this.level.getBlockState(pos.below());
			if (!under.isFaceSturdy(this.level, pos.below(), Direction.UP) || ours(under)) {
				return false;
			}

			boolean roof = false;
			for (int up = 2; up < this.placed.sizeY && !roof; up++) {
				BlockState over = this.want.get(pos.above(up));
				roof = over != null && !over.getCollisionShape(this.level, pos.above(up)).isEmpty();
			}

			if (!roof) {
				return false;
			}

			for (Direction side : Direction.Plane.HORIZONTAL) {
				boolean wall = false;
				for (int i = 1; i <= Math.max(this.placed.sizeX, this.placed.sizeZ) && !wall; i++) {
					BlockPos p = pos.relative(side, i);
					BlockState planned = this.want.get(p);
					wall = planned != null && !planned.getCollisionShape(this.level, p).isEmpty();
				}

				if (!wall) {
					return false;
				}
			}

			return true;
		}

		/** Free here (air, or one of the layout's own things: the shell's the same with them in). */
		private boolean air(BlockPos pos) {
			BlockState state = this.level.getBlockState(pos);
			return (state.getCollisionShape(this.level, pos).isEmpty() || ours(state)) && state.getFluidState().isEmpty()
				&& !(state.getBlock() instanceof BedBlock);
		}

		private boolean full(BlockPos pos) {
			BlockState planned = this.want.get(pos);
			return planned != null && planned.isCollisionShapeFullBlock(this.level, pos);
		}

		/** A wall of the house: one of its solid blocks, not a door (the back of something against it). */
		private boolean wall(BlockPos pos) {
			BlockState planned = this.want.get(pos);
			return planned != null && !(planned.getBlock() instanceof DoorBlock) && !planned.getCollisionShape(this.level, pos).isEmpty();
		}

		/** The floor cell just inside this door in this room, or null. */
		@Nullable
		private BlockPos inside(BlockPos door, Set<BlockPos> room) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				if (room.contains(door.relative(side))) {
					return door.relative(side);
				}
			}

			return null;
		}

		private boolean touches(BlockPos pos, Set<BlockPos> room) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				if (room.contains(pos.relative(side))) {
					return true;
				}
			}

			return false;
		}

		/** Which way a thing here faces: to the room's floor with a wall behind (null if there's no such side). */
		@Nullable
		private Direction facing(BlockPos pos, Set<BlockPos> room) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				if (room.contains(pos.relative(side)) && this.wall(pos.relative(side.getOpposite()))) {
					return side;
				}
			}

			for (Direction side : Direction.Plane.HORIZONTAL) {
				if (room.contains(pos.relative(side))) {
					return side;
				}
			}

			return null;
		}

		/** A straight stretch of floor along a wall: things there face the room with their backs to the wall. */
		private record Run(List<BlockPos> cells, Direction facing) {
		}

		/** The free stretches along the walls of a room, longest first (the ones far from the door first among equals). */
		private List<Run> runs(Set<BlockPos> room) {
			List<Run> runs = new ArrayList<>();
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				Direction along = facing.getClockWise();
				Set<BlockPos> used = new HashSet<>();
				for (BlockPos pos : room) {
					if (used.contains(pos) || !this.usable(room, pos, facing) || this.usable(room, pos.relative(along.getOpposite()), facing)) {
						continue;
					}

					// From the start of a stretch along the wall.
					List<BlockPos> cells = new ArrayList<>();
					BlockPos at = pos;
					while (this.usable(room, at, facing)) {
						cells.add(at);
						used.add(at);
						at = at.relative(along);
					}

					if (!cells.isEmpty()) {
						runs.add(new Run(cells, facing));
					}
				}
			}

			Map<Run, Double> fromDoor = new HashMap<>();
			for (Run run : runs) {
				BlockPos middle = run.cells.get(run.cells.size() / 2);
				fromDoor.put(run, this.doors.stream().mapToDouble(d -> Math.sqrt(d.distSqr(middle))).min().orElse(8.0));
			}

			runs.sort(Comparator.comparingInt((Run run) -> -run.cells.size()).thenComparingDouble(run -> -fromDoor.get(run)));
			return runs;
		}

		/** A place along a wall a thing could go, facing this way: free, the wall behind, floor in front, out of the way. */
		private boolean usable(Set<BlockPos> room, BlockPos pos, Direction facing) {
			return room.contains(pos) && !this.taken.contains(pos) && this.wall(pos.relative(facing.getOpposite())) && room.contains(pos.relative(facing))
				&& !this.byDoor(pos) && !this.atFoot(pos) && !this.byBed(pos);
		}

		/** In a doorway's way: the step in from a door, or beside it. */
		private boolean byDoor(BlockPos pos) {
			for (BlockPos door : this.doors) {
				if (Math.abs(door.getY() - pos.getY()) <= 1 && Math.abs(door.getX() - pos.getX()) + Math.abs(door.getZ() - pos.getZ()) <= 2) {
					return true;
				}
			}

			return false;
		}

		/** Where you step on or off stairs or a ladder. */
		private boolean atFoot(BlockPos pos) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				for (int dy = -1; dy <= 0; dy++) {
					BlockPos next = pos.relative(side).above(dy);
					BlockState state = this.level.getBlockState(next);
					if (state.getBlock() instanceof StairBlock && this.want.containsKey(next) || state.is(net.minecraft.tags.BlockTags.CLIMBABLE)) {
						return true;
					}
				}
			}

			BlockState over = this.level.getBlockState(pos.above(2));
			return over.getBlock() instanceof StairBlock;
		}

		/** Beside the bed's long side (the way into bed; and where it stands up). */
		private boolean byBed(BlockPos pos) {
			for (BlockPos bed : this.bedCells) {
				if (bed.getY() == pos.getY() && Math.abs(bed.getX() - pos.getX()) + Math.abs(bed.getZ() - pos.getZ()) == 1) {
					return true;
				}
			}

			return false;
		}

		/** With this place taken too, there's still a way from the door (or anywhere) to every free floor and every thing. */
		private boolean keep(Set<BlockPos> room, BlockPos taking) {
			Set<BlockPos> free = new HashSet<>();
			for (BlockPos pos : room) {
				if (!pos.equals(taking) && !this.taken.contains(pos)) {
					free.add(pos);
				}
			}

			if (free.isEmpty()) {
				return false;
			}

			BlockPos start = null;
			for (BlockPos door : this.doors) {
				BlockPos in = this.inside(door, room);
				if (in != null && free.contains(in)) {
					start = in;
					break;
				}
			}

			if (start == null) {
				start = free.iterator().next();
			}

			Set<BlockPos> reached = new HashSet<>();
			ArrayDeque<BlockPos> queue = new ArrayDeque<>();
			reached.add(start);
			queue.add(start);
			while (!queue.isEmpty()) {
				BlockPos pos = queue.poll();
				for (Direction side : Direction.Plane.HORIZONTAL) {
					BlockPos next = pos.relative(side);
					if (free.contains(next) && reached.add(next)) {
						queue.add(next);
					}
				}
			}

			if (reached.size() < free.size()) {
				return false;
			}

			// Every thing in the room (and the new one) has free floor beside it to use it from.
			List<BlockPos> things = new ArrayList<>(this.taken.stream().filter(room::contains).toList());
			things.add(taking);
			for (BlockPos thing : things) {
				if (!this.touches(thing, reached)) {
					return false;
				}
			}

			return true;
		}
	}
}
