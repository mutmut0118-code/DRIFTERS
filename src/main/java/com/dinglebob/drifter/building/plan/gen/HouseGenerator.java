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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Makes house plans by the base-building guidebook's rules (the user, 2026-10-22: "they need to be a bit bigger, especially
 * for a town... lets revamp all the house plans using references from my edits but more from this guidebook"):
 * <ul>
 *   <li>a long rectangle (1 : 1.4 to 2), its walls measured by the pillar formula (bays × (gap + 1) + 1, so a post lands on
 *       both corners); squares get a hip roof;</li>
 *   <li>in depth: a stone foundation a block proud of the frame (a stair plinth round it), log posts and a log beam at the
 *       top of each storey on the frame line, the wall itself (infill) a block behind it, the posts carried through inside;
 *       storeys four high (three of room and the beam), a floor beam marking each;</li>
 *   <li>windows centred in each bay, a block clear of the posts, at eye level, the same on every storey (sills and lintels
 *       in some styles); a light on the inside of the wall at each post (the user's edits: torches inside, by the door);</li>
 *   <li>the door centred in the middle bay (a double door in an even bay), or a third of the way along on an even count;
 *       framed in logs, set back, a three-wide stair step (the user's edits), a light, a little roof over it when no eave is;</li>
 *   <li>a 45° gable (or a steep one, two up for one across) with its ridge along the length, a block of overhang all round,
 *       a ridge cap on an odd width, the rake trimmed underneath, the gable a lighter block with a window in it; inside, tie
 *       beams across at the posts; on a roof 15 or wider a second layer underneath (a vaulted ceiling inside);</li>
 *   <li>upstairs: a staircase along the back wall (four up needs four along), not a ladder;</li>
 *   <li>sometimes a wing out of the back (50-70% of the house's width, its ridge at least two lower, an L to one side or a T
 *       in the middle, a door through), a tower at a front corner (on the end away from the door), a chimney outside a gable
 *       end, a wall between two rooms in a long house.</li>
 * </ul>
 * No porches, shed roofs or flat roofs (the user took the porch apart: "no flat roof on a traditional house"). Built with
 * the front as the long north side (or the west gable end, then turned so it faces north): the front is the low-z side.
 */
public final class HouseGenerator {
	private HouseGenerator() {
	}

	/** What the last plan made has (its roof, a wing, a tower...), for the tally and the showcase (debug). */
	static List<String> lastFeatures = List.of();

	/** A storey: three blocks of room and the beam row over it. */
	private static final int STOREY = 4;
	/** A roof this wide (overhang included) or wider gets a second layer under it (guidebook: "roofs over about 15 wide look thin"). */
	private static final int THICK_ROOF = 15;
	/** Narrowest gable with a window in it. */
	private static final int ATTIC_SPAN = 7;
	/** Narrowest room either side of a dividing wall. */
	private static final int ROOM = 5;

	private record Materials(Block foundation, @Nullable Block plinth, Block floor, @Nullable Block border, Block frame, Block infill,
		Block upperInfill, Block gableFill, Block window, @Nullable Block sill, boolean lintels, Block stair, Block roofBlock, Block slab,
		boolean logRidge, Block door, Block chimney, boolean lanterns) {
	}

	private static Materials materials(HouseStyle style, RandomSource random) {
		Block infill = style.material("infill", Blocks.OAK_PLANKS, random);
		Block upper = style.material("upper_infill", infill, random);
		Block stair = style.material("stair", Blocks.SPRUCE_STAIRS, random);
		return new Materials(style.material("foundation", Blocks.COBBLESTONE, random), style.material("plinth", null, random),
			style.material("floor", Blocks.OAK_PLANKS, random), style.material("floor_border", null, random),
			style.material("frame", Blocks.SPRUCE_LOG, random), infill, upper, style.material("gable_fill", upper, random),
			style.material("window", Blocks.GLASS, random), style.material("sill", null, random), style.chance("lintels", 0.0, random), stair,
			style.material("roof_block", fullBlockOf(stair), random), style.material("slab", Blocks.SPRUCE_SLAB, random),
			style.chance("ridge_log", 0.0, random), style.material("door", Blocks.OAK_DOOR, random),
			style.material("chimney_block", Blocks.COBBLESTONE, random), style.chance("lanterns", 0.0, random));
	}

	/** The full block a stair is cut from (spruce stairs: spruce planks; stone brick stairs: stone bricks). */
	private static Block fullBlockOf(Block stair) {
		String path = BuiltInRegistries.BLOCK.getKey(stair).getPath();
		String base = path.endsWith("_stairs") ? path.substring(0, path.length() - "_stairs".length()) : path;
		for (String id : new String[] {base + "_planks", base + "s", base}) {
			ResourceLocation location = ResourceLocation.withDefaultNamespace(id);
			if (BuiltInRegistries.BLOCK.containsKey(location)) {
				return BuiltInRegistries.BLOCK.get(location);
			}
		}

		return Blocks.OAK_PLANKS;
	}

	/** A frame rectangle: the line the posts stand on. */
	private record Rect(int x0, int z0, int x1, int z1) {
		/** How long that side's wall is. */
		int length(Direction side) {
			return side.getAxis() == Direction.Axis.Z ? this.x1 - this.x0 + 1 : this.z1 - this.z0 + 1;
		}

		/** The cell {@code along} that side's wall (from its west or north end), {@code depth} in from the frame line (-1: outside). */
		BlockPos at(Direction side, int along, int y, int depth) {
			return switch (side) {
				case NORTH -> new BlockPos(this.x0 + along, y, this.z0 + depth);
				case SOUTH -> new BlockPos(this.x0 + along, y, this.z1 - depth);
				case WEST -> new BlockPos(this.x0 + depth, y, this.z0 + along);
				default -> new BlockPos(this.x1 - depth, y, this.z0 + along);
			};
		}

		/** How far in from the frame line (0 on it, negative outside). */
		int depth(int x, int z) {
			return Math.min(Math.min(x - this.x0, this.x1 - x), Math.min(z - this.z0, this.z1 - z));
		}
	}

	private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

	/** The way a side's wall runs. */
	private static Direction.Axis along(Direction side) {
		return side.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
	}

	/** The gap between posts on a wall this long: the wanted one if posts then land on both corners, else one that does, else corners only. */
	private static int gapFor(int length, int wanted) {
		for (int gap : new int[] {wanted, 3, 4, 5, 2}) {
			if (length - 1 > gap && (length - 1) % (gap + 1) == 0) {
				return gap;
			}
		}

		return length - 2;
	}

	private static boolean post(int length, int gap, int along) {
		return along == 0 || along == length - 1 || along % (gap + 1) == 0;
	}

	/** The open stretches between posts, as {from, to} along the wall. */
	private static List<int[]> bays(int length, int gap) {
		List<int[]> out = new ArrayList<>();
		int start = 1;
		for (int a = 1; a < length; a++) {
			if (post(length, gap, a)) {
				if (a > start) {
					out.add(new int[] {start, a - 1});
				}

				start = a + 1;
			}
		}

		return out;
	}

	/** A staircase up from a storey, along the inside of a long wall: {@code storey}'s floor to the next, starting {@code start} along, going {@code dir}. */
	private record Flight(int storey, Direction side, int start, int dir) {
		int low() {
			return Math.min(this.start, this.start + this.dir * (STEPS - 1));
		}

		int high() {
			return Math.max(this.start, this.start + this.dir * (STEPS - 1));
		}
	}

	/** Steps in a flight (one a block of storey). */
	private static final int STEPS = STOREY;

	/** How far in from the frame line a storey's room starts: two on the ground storey (its wall set back), one above. */
	private static int inside(int storey) {
		return storey == 0 ? 2 : 1;
	}

	/** One plan in this style, or null if this draw didn't make a sound house (the caller draws again). */
	@Nullable
	public static HousePlan generate(String id, HouseStyle style, RandomSource random) {
		List<String> features = new ArrayList<>();
		lastFeatures = features;
		Materials m = materials(style, random);
		int gap = pick(style.numbers("gaps", List.of(3)), random);
		int longBays = pick(style.numbers("long_bays", List.of(3)), random);
		int shortBays = pick(style.numbers("short_bays", List.of(2)), random);
		int length = Math.max(longBays, shortBays) * (gap + 1) + 1;
		int width = Math.min(longBays, shortBays) * (gap + 1) + 1;
		if ((double)length / width > style.num("max_ratio", 2.1) || length > style.num("max_length", 99)) {
			return null;
		}

		int floors = style.between("floors", 1, 1, random);
		String roof = length == width ? "hip" : pick(random, style.roofs());
		boolean gableFront = length != width && style.chance("gable_front", 0.0, random);
		Direction front = gableFront ? Direction.WEST : Direction.NORTH;
		Draft d = new Draft(m, random, gap);
		Rect main = new Rect(0, 0, length - 1, width - 1);
		int top = floors * STOREY;
		features.add(roof);
		features.add(length + "x" + width);
		if (floors > 1) {
			features.add(floors + " floors");
		}

		if (gableFront) {
			features.add("door in the gable");
		}

		// The door: the middle bay of an odd count, else a third of the way along (from either end).
		List<int[]> frontBays = bays(main.length(front), gap);
		int doorBay = frontBays.size() % 2 == 1 ? frontBays.size() / 2 : (int)(frontBays.size() / 3.0 + 0.5);
		if (frontBays.size() % 2 == 0 && random.nextBoolean()) {
			doorBay = frontBays.size() - 1 - doorBay;
		}

		int[] bay = frontBays.get(doorBay);
		int doorWidth = (bay[1] - bay[0] + 1) % 2 == 0 ? 2 : 1;
		int doorAt = bay[0] + (bay[1] - bay[0] + 1 - doorWidth) / 2;
		// (A gable end with a post in its middle: the door takes the post's place, centred under the ridge.)
		boolean postDoor = gableFront && frontBays.size() % 2 == 0;
		if (postDoor) {
			doorWidth = 1;
			doorAt = (main.length(front) - 1) / 2;
			bay = new int[] {doorAt - 1, doorAt + 1};
		}

		if (doorWidth == 2) {
			features.add("double door");
		}

		if (frontBays.size() % 2 == 0 && !postDoor) {
			features.add("off-centre door");
		}

		// A tower at the front corner away from the door (the door's middle or the other way at random).
		int tw = gap + 2;
		boolean tower = !roof.equals("steep") && floors == 1 && Math.min(length, width) >= tw + 4 && style.chance("tower", 0.0, random);
		int doorMiddle2 = 2 * doorAt + doorWidth - 1;
		boolean towerAtStart = doorMiddle2 == main.length(front) - 1 ? random.nextBoolean() : doorMiddle2 > main.length(front) - 1;
		if (tower && (postDoor || (towerAtStart ? doorBay == 0 : doorBay == frontBays.size() - 1))) {
			tower = false;
		}

		// (Front north: the tower at its west or east end; front west: at its north or south end. Always x0 for the latter.)
		int towerX0 = front == Direction.NORTH && !towerAtStart ? main.x1() - tw + 1 : main.x0();
		int towerZ0 = front == Direction.WEST && !towerAtStart ? main.z1() - tw + 1 : main.z0();

		// A wing out of the back: 50-70% of the width, odd, nine or more (room inside), its ridge two lower.
		int wingWidth = 0;
		int wingX0 = 0;
		boolean gabled = roof.equals("gable") || roof.equals("steep");
		if (gabled && style.chance("wing", 0.0, random)) {
			for (int w = (int)Math.floor(width * 0.7); w >= Math.ceil(width * 0.5); w--) {
				if (w % 2 == 1 && w >= 9 && ridge(roof, STOREY, w) <= ridge(roof, top, width) - 2) {
					wingWidth = w;
					break;
				}
			}
		}

		boolean tWing = false;
		if (wingWidth > 0) {
			tWing = length % 2 == 1 && style.chance("wing_t", 0.3, random);
			boolean left = tower ? towerX0 != main.x0() : random.nextBoolean();
			wingX0 = tWing ? (length - wingWidth) / 2 : left ? main.x0() : main.x1() - wingWidth + 1;
			if (tower && wingX0 <= towerX0 + tw - 1 && wingX0 + wingWidth - 1 >= towerX0) {
				tWing = false;
				wingX0 = towerX0 == main.x0() ? main.x1() - wingWidth + 1 : main.x0();
			}
		}

		int wingX1 = wingX0 + wingWidth - 1;
		int wingDoor = (wingX0 + wingX1) / 2;
		if (wingWidth > 0 && post(length, gap, wingDoor)) {
			wingDoor++;
		}

		// Staircases: up from each storey along a long wall's inside, away from the wing's door (the back first, then the front).
		List<Flight> flights = new ArrayList<>();
		for (int s = 0; s < floors - 1; s++) {
			Direction side = s % 2 == 0 ? Direction.SOUTH : Direction.NORTH;
			boolean east = wingWidth > 0 ? wingDoor < length / 2 : random.nextBoolean();
			if (s > 0) {
				east = flights.get(s - 1).dir() < 0;
			}

			Flight flight = new Flight(s, side, east ? 3 : length - 4, east ? 1 : -1);
			if (wingWidth > 0 && side == Direction.SOUTH && wingDoor >= flight.low() - 1 && wingDoor <= flight.high() + 1) {
				flight = new Flight(s, side, east ? length - 4 : 3, east ? -1 : 1);
				if (wingDoor >= flight.low() - 1 && wingDoor <= flight.high() + 1) {
					wingWidth = 0;
				}
			}

			flights.add(flight);
		}

		if (wingWidth > 0) {
			features.add(tWing ? "T wing" : "L wing");
		}

		// A chimney outside a gable end: not the door's, not the tower's.
		Direction chimney = null;
		if (gabled && style.chance("chimney", 0.0, random)) {
			chimney = front == Direction.WEST || tower && towerX0 == main.x0() ? Direction.EAST
				: tower ? Direction.WEST : random.nextBoolean() ? Direction.EAST : Direction.WEST;
			if (front == Direction.WEST && tower) {
				chimney = Direction.EAST;
			}

			features.add("chimney");
		}

		int chimneyAlong = (width - 1) / 2;
		boolean sills = m.sill() != null && random.nextBoolean();
		boolean tall = style.chance("tall_windows", 0.0, random);
		boolean wide = style.chance("wide_windows", 0.5, random);
		boolean vault = style.chance("vaulted", 0.0, random) || width + 2 >= THICK_ROOF;
		if (sills) {
			features.add("sills");
		}

		if (tall) {
			features.add("tall windows");
		}

		if (m.lintels()) {
			features.add("lintels");
		}

		if (vault && gabled) {
			features.add("vaulted");
		}

		if (m.lanterns()) {
			features.add("lanterns");
		}

		// --- The ground floor and the walls.
		d.foundation(main, null);
		for (int s = 0; s < floors; s++) {
			d.walls(main, List.of(SIDES), s * STOREY, s == 0 ? m.infill() : m.upperInfill(), gap, s > 0);
		}

		// The stairs, and the floors between storeys (beams across at the posts, a hole over the stairs).
		for (Flight flight : flights) {
			d.flight(main, flight);
		}

		for (int s = 1; s < floors; s++) {
			int y = s * STOREY;
			Flight below = flights.get(s - 1);
			int inset = inside(s - 1);
			for (int x = main.x0() + inset; x <= main.x1() - inset; x++) {
				for (int z = main.z0() + inset; z <= main.z1() - inset; z++) {
					int along = x - main.x0();
					boolean hole = z == main.at(below.side(), 0, 0, inside(below.storey())).getZ() && along >= below.low() && along <= below.high()
						&& along != below.start() + below.dir() * (STEPS - 1);
					if (hole) {
						continue;
					}

					d.putIfFree(new BlockPos(x, y, z), post(length, gap, along) ? axis(m.frame(), Direction.Axis.Z) : m.floor().defaultBlockState());
				}
			}
		}

		// --- The door.
		d.door(main, front, doorAt, doorWidth, bay, floors > 1 || front == Direction.WEST, postDoor);

		// (The wing's place; it's built after the main roof.)
		Rect wing = wingWidth > 0 ? new Rect(wingX0, main.z1(), wingX1, main.z1() + pick(List.of(9, 9, 11), random) - 1) : null;

		// --- Windows.
		for (int s = 0; s < floors; s++) {
			for (Direction side : SIDES) {
				int len = main.length(side);
				for (int[] b : bays(len, gapFor(len, gap))) {
					boolean skip = s == 0 && side == front && b[0] == bay[0] && b[1] == bay[1]
						|| s == 0 && wing != null && side == Direction.SOUTH && b[1] >= wing.x0() - main.x0() && b[0] <= wing.x1() - main.x0()
						|| side == chimney && b[0] <= chimneyAlong && b[1] >= chimneyAlong;
					for (Flight flight : flights) {
						skip |= flight.storey() == s && flight.side() == side && b[1] >= flight.low() - 1 && b[0] <= flight.high() + 1;
					}

					if (!skip) {
						d.window(main, side, b, s * STOREY, tall, wide, sills, inside(s) - 1);
					}
				}
			}
		}

		// --- A wall between two rooms in a long house (upstairs if there is one), with a door in the middle.
		if (length >= 17 && style.chance("partition", 0.0, random)) {
			int storey = floors - 1;
			int doorMiddle = doorAt + doorWidth / 2;
			List<Integer> candidates = new ArrayList<>();
			for (int a = 1; a < length - 1; a++) {
				candidates.add(a);
			}

			candidates.sort(java.util.Comparator.comparingInt(a -> Math.abs(2 * a - (length - 1))));
			for (int a : candidates) {
				if (!post(length, gap, a) || a - 2 < ROOM || length - 3 - a < ROOM) {
					continue;
				}

				if (storey == 0 && Math.abs(a - doorMiddle) < 2 || storey == 0 && wing != null && Math.abs(a - wingDoor) < 2) {
					continue;
				}

				boolean nearStairs = false;
				for (Flight flight : flights) {
					nearStairs |= a >= flight.low() - 1 && a <= flight.high() + 1;
				}

				if (nearStairs) {
					continue;
				}

				d.partition(main, a, storey * STOREY, storey == 0 ? m.infill() : m.upperInfill(), inside(storey));
				features.add("two rooms");
				break;
			}
		}

		// --- The roof.
		int ridgeAt;
		if (gabled) {
			d.gable(main, top, roof.equals("steep"), vault);
			d.gableEnds(main, top, chimney != Direction.WEST, chimney != Direction.EAST, inside(floors - 1) - 1);
		} else {
			d.hip(main, top, vault);
		}

		ridgeAt = d.highest();
		// Tie beams across at the posts (a lantern under each in some styles), and under the floor beams below.
		for (int a = 1; a < length - 1; a++) {
			if (!post(length, gap, a)) {
				continue;
			}

			int x = main.x0() + a;
			for (int z = main.z0() + inside(floors - 1); z <= main.z1() - inside(floors - 1); z++) {
				d.putIfFree(new BlockPos(x, top, z), axis(m.frame(), Direction.Axis.Z));
			}

			if (m.lanterns()) {
				for (int s = 0; s < floors; s++) {
					d.putIfFree(new BlockPos(x, (s + 1) * STOREY - 1, (main.z0() + main.z1()) / 2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
				}
			}
		}

		// --- The wing: its floor, walls (its side walls closing on the house's back wall), a door through, windows, and its roof
		// run into the main one's back slope.
		if (wing != null) {
			// (The main roof's eave over where the wing joins.)
			d.cells.entrySet().removeIf(cell -> cell.getKey().getZ() == main.z1() + 1 && cell.getKey().getY() == top + 1
				&& cell.getKey().getX() >= wing.x0() - 1 && cell.getKey().getX() <= wing.x1() + 1 && cell.getValue().getBlock() instanceof StairBlock);
			Map<Integer, Integer> surface = d.surface(top);
			d.foundation(wing, Direction.NORTH);
			d.walls(wing, List.of(Direction.SOUTH, Direction.WEST, Direction.EAST), 0, m.infill(), gap, false);
			// Close the gaps where its side walls meet the house's back wall, and a door through.
			for (int y = 1; y <= STOREY; y++) {
				d.put(new BlockPos(wing.x0(), y, main.z1()), axis(m.frame(), Direction.Axis.Y));
				d.put(new BlockPos(wing.x1(), y, main.z1()), axis(m.frame(), Direction.Axis.Y));
				d.put(new BlockPos(wing.x0() + 1, y, main.z1()), axis(m.frame(), Direction.Axis.Y));
				d.put(new BlockPos(wing.x1() - 1, y, main.z1()), axis(m.frame(), Direction.Axis.Y));
			}

			d.doorway(new BlockPos(wingDoor, 1, main.z1() - 1), Direction.SOUTH);
			d.remove(new BlockPos(wingDoor, 1, main.z1()));
			d.remove(new BlockPos(wingDoor, 2, main.z1()));
			for (Direction side : new Direction[] {Direction.SOUTH, Direction.WEST, Direction.EAST}) {
				int len = wing.length(side);
				for (int[] b : bays(len, gapFor(len, gap))) {
					d.window(wing, side, b, 0, tall, wide, sills, 1);
				}
			}

			Map<BlockPos, BlockState> wingRoof = new HashMap<>();
			// (Drawn as if its ridge ran along x, from the main ridge back: what's under the main roof is left out.)
			Draft w = new Draft(m, random, gap);
			Rect turned = new Rect((main.z0() + main.z1()) / 2, wing.x0(), wing.z1(), wing.x1());
			w.gable(turned, STOREY, roof.equals("steep"), vault);
			w.gableEnds(turned, STOREY, false, true, 1);
			for (Map.Entry<BlockPos, BlockState> entry : w.cells.entrySet()) {
				// (Drawn with its ridge along "x": turn x and z back.)
				BlockPos pos = new BlockPos(entry.getKey().getZ(), entry.getKey().getY(), entry.getKey().getX());
				if (pos.getZ() < main.z1() && pos.getY() <= surface.getOrDefault(pos.getX() * 4096 + pos.getZ(), top)
					|| pos.getZ() == main.z1() && d.cells.containsKey(pos)) {
					continue;
				}

				wingRoof.put(pos, rotateState(entry.getValue()));
			}

			d.cells.putAll(wingRoof);
			// Its tie beams, across at its side posts.
			int sideLength = wing.length(Direction.WEST);
			for (int a = 1; a < sideLength - 1; a++) {
				if (!post(sideLength, gapFor(sideLength, gap), a)) {
					continue;
				}

				for (int x = wing.x0() + 2; x <= wing.x1() - 2; x++) {
					d.put(new BlockPos(x, STOREY, wing.z0() + a), axis(m.frame(), Direction.Axis.X));
				}

				if (m.lanterns()) {
					d.putIfFree(new BlockPos((wing.x0() + wing.x1()) / 2, STOREY - 1, wing.z0() + a), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
				}
			}
		}

		// --- The chimney: from the ground past the ridge, outside a gable end.
		if (chimney != null) {
			int x = chimney == Direction.WEST ? main.x0() - 1 : main.x1() + 1;
			for (int y = 0; y <= ridgeAt + 2; y++) {
				d.put(new BlockPos(x, y, main.z0() + chimneyAlong), m.chimney().defaultBlockState());
			}
		}

		// --- The tower: the house's corner carried on up past the ridge, open inside down to the room, a hip roof.
		if (tower) {
			Rect t = new Rect(towerX0, towerZ0, towerX0 + tw - 1, towerZ0 + tw - 1);
			int xa = t.x0() == main.x0() ? t.x0() - 1 : t.x0();
			int xb = t.x1() == main.x1() ? t.x1() + 1 : t.x1();
			int za = t.z0() == main.z0() ? t.z0() - 1 : t.z0();
			int zb = t.z1() == main.z1() ? t.z1() + 1 : t.z1();
			d.cells.keySet().removeIf(pos -> pos.getX() >= xa && pos.getX() <= xb && pos.getZ() >= za && pos.getZ() <= zb
				&& (pos.getY() > top || pos.getY() == top && main.depth(pos.getX(), pos.getZ()) < 0));
			// The girder its inner wall stands on (the other inner wall stands on a tie beam).
			int girderZ = t.z0() == main.z0() ? t.z1() : t.z0();
			for (int x = t.x0(); x <= t.x1(); x++) {
				d.putIfFree(new BlockPos(x, top, girderZ), axis(m.frame(), Direction.Axis.X));
			}

			int towerTop = top + STOREY * Math.max(1, (int)Math.ceil((ridgeAt + 2.0 - top) / STOREY));
			Direction innerX = t.x0() == main.x0() ? Direction.EAST : Direction.WEST;
			Direction innerZ = t.z0() == main.z0() ? Direction.SOUTH : Direction.NORTH;
			for (int y0 = top; y0 < towerTop; y0 += STOREY) {
				d.walls(t, List.of(SIDES), y0, m.upperInfill(), gap, true);
			}

			for (Direction outer : new Direction[] {innerX.getOpposite(), innerZ.getOpposite()}) {
				int len = t.length(outer);
				for (int[] b : bays(len, gapFor(len, gap))) {
					d.window(t, outer, b, towerTop - STOREY, false, wide, false, 0);
				}
			}

			d.hip(t, towerTop, false);
			features.add("tower");
		}

		// --- Lights inside: on the wall at each post (a torch, as the user put them), and by the wing's door.
		for (int s = 0; s < floors; s++) {
			for (Direction side : SIDES) {
				d.torches(main, side, s * STOREY, inside(s) - 1);
			}
		}

		if (wing != null) {
			for (Direction side : new Direction[] {Direction.SOUTH, Direction.WEST, Direction.EAST}) {
				d.torches(wing, side, 0, 1);
			}

			for (int dx : new int[] {-2, 2}) {
				d.putIfFree(new BlockPos(wingDoor + dx, 2, main.z1()), torch(Direction.SOUTH));
			}
		}

		Map<BlockPos, BlockState> cells = d.cells;
		if (gableFront) {
			// Built with the door on the west end: turned a quarter clockwise, the west faces north (the front).
			Map<BlockPos, BlockState> turned = new HashMap<>();
			cells.forEach((pos, state) -> turned.put(new BlockPos(-pos.getZ(), pos.getY(), pos.getX()), state.rotate(Rotation.CLOCKWISE_90)));
			cells = turned;
		}

		return normalize(id, cells);
	}

	/** Where a gable's ridge is (its top block) on walls {@code top} high, the roof this wide across (frame to frame). */
	private static int ridge(String roof, int top, int across) {
		int last = (across - 1) / 2;
		return roof.equals("steep") ? top + 2 + 2 * last : top + 2 + last;
	}

	/** The plan as it's drawn, with the materials. */
	private static final class Draft {
		final Map<BlockPos, BlockState> cells = new HashMap<>();
		final Materials m;
		final RandomSource random;
		final int gap;

		Draft(Materials m, RandomSource random, int gap) {
			this.m = m;
			this.random = random;
			this.gap = gap;
		}

		void put(BlockPos pos, BlockState state) {
			this.cells.put(pos.immutable(), state);
		}

		void putIfFree(BlockPos pos, BlockState state) {
			this.cells.putIfAbsent(pos.immutable(), state);
		}

		void remove(BlockPos pos) {
			this.cells.remove(pos);
		}

		int highest() {
			return this.cells.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0);
		}

		/** The top of the roof over each column (x * 4096 + z), above {@code top}. */
		Map<Integer, Integer> surface(int top) {
			Map<Integer, Integer> out = new HashMap<>();
			this.cells.keySet().forEach(pos -> {
				if (pos.getY() > top) {
					out.merge(pos.getX() * 4096 + pos.getZ(), pos.getY(), Math::max);
				}
			});
			return out;
		}

		/**
		 * The floor: stone under the walls (the frame line and the wall behind it), a stair plinth a block out round it, the
		 * room's floor inside (a border round its edge in some styles). {@code open}: the side that joins another part (not
		 * walled; the floor runs on into it).
		 */
		void foundation(Rect r, @Nullable Direction open) {
			for (int x = r.x0() - 1; x <= r.x1() + 1; x++) {
				for (int z = r.z0() - 1; z <= r.z1() + 1; z++) {
					if (open == Direction.NORTH && z < r.z0()) {
						continue;
					}

					int depth = Math.min(Math.min(x - r.x0(), r.x1() - x), Math.min(open == Direction.NORTH ? Integer.MAX_VALUE : z - r.z0(), r.z1() - z));
					BlockPos pos = new BlockPos(x, 0, z);
					if (depth < 0) {
						boolean corner = (x < r.x0() || x > r.x1()) && (z < r.z0() || z > r.z1());
						Direction in = x < r.x0() ? Direction.EAST : x > r.x1() ? Direction.WEST : z < r.z0() ? Direction.SOUTH : Direction.NORTH;
						BlockState state = corner || this.m.plinth() == null ? this.m.foundation().defaultBlockState() : stair(this.m.plinth(), in);
						BlockState there = this.cells.get(pos);
						if (there == null || there.getBlock() instanceof StairBlock) {
							this.put(pos, state);
						}
					} else if (depth <= 1) {
						this.put(pos, this.m.foundation().defaultBlockState());
					} else {
						this.put(pos, (depth == 2 && this.m.border() != null ? this.m.border() : this.m.floor()).defaultBlockState());
					}
				}
			}
		}

		/**
		 * One storey's walls on these sides: posts, and a beam at the top, on the frame line; the wall a block in, posts
		 * carried through it; or ({@code flush}: an upper storey, jettied out a block past the one below, as the guidebook's
		 * Tudor houses; a tower) the wall on the frame line between the posts. (Found in testing: set back upstairs, the beam
		 * below made a ledge four up a builder climbed into and couldn't get down from.)
		 */
		void walls(Rect r, Iterable<Direction> sides, int y0, Block infill, int wantedGap, boolean flush) {
			for (Direction side : sides) {
				int len = r.length(side);
				int gap = gapFor(len, wantedGap);
				for (int a = 0; a < len; a++) {
					boolean post = post(len, gap, a);
					for (int row = 1; row <= STOREY; row++) {
						int y = y0 + row;
						if (post) {
							this.put(r.at(side, a, y, 0), axis(this.m.frame(), Direction.Axis.Y));
						} else if (row == STOREY) {
							this.put(r.at(side, a, y, 0), axis(this.m.frame(), along(side)));
						} else if (flush) {
							this.put(r.at(side, a, y, 0), axis(infill, along(side)));
						}

						if (!flush && a >= 1 && a <= len - 2) {
							boolean inner = post || a == 1 || a == len - 2;
							this.put(r.at(side, a, y, 1), inner ? axis(this.m.frame(), Direction.Axis.Y) : axis(infill, along(side)));
						}
					}
				}
			}
		}

		/**
		 * The front door, framed in logs and set back in its bay (or in place of a post: {@code postDoor}); the stair step,
		 * a light, and a little roof over it if asked.
		 */
		void door(Rect r, Direction front, int at, int width, int[] bay, boolean awning, boolean postDoor) {
			Direction facing = front.getOpposite();
			if (postDoor) {
				for (int row = 1; row < STOREY; row++) {
					this.remove(r.at(front, at, row, 0));
				}

				this.put(r.at(front, at, STOREY, 0), axis(this.m.frame(), along(front)));
			}

			for (int i = 0; i < width; i++) {
				int partner = width == 2 ? (i == 0 ? at + 1 : at) : -1;
				boolean right = partner >= 0 && r.at(front, partner, 0, 1).subtract(r.at(front, at + i, 0, 1)).equals(BlockPos.ZERO.relative(facing.getCounterClockWise()));
				BlockState door = this.m.door().defaultBlockState().setValue(DoorBlock.FACING, facing)
					.setValue(DoorBlock.HINGE, right ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false);
				this.put(r.at(front, at + i, 1, 1), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
				this.put(r.at(front, at + i, 2, 1), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
				this.put(r.at(front, at + i, 3, 1), axis(this.m.frame(), along(front)));
			}

			// The frame: logs either side up to the lintel.
			for (int a : new int[] {at - 1, at + width}) {
				if (a >= bay[0] && a <= bay[1]) {
					for (int row = 1; row <= 3; row++) {
						this.put(r.at(front, a, row, 1), axis(this.m.frame(), Direction.Axis.Y));
					}
				}
			}

			// The step: stairs up onto the foundation, a block wider each side than the door.
			Block step = this.m.plinth() != null ? this.m.plinth() : this.m.stair();
			for (int a = at - 1; a <= at + width; a++) {
				this.put(r.at(front, a, 0, -1), stair(step, facing));
				if (awning) {
					this.put(r.at(front, a, STOREY, -1), stair(this.m.stair(), facing));
				}
			}

			// A light: lanterns hanging under the beam in front of it, or torches on the posts either side.
			if (this.m.lanterns()) {
				for (int i = 0; i < width; i++) {
					this.put(r.at(front, at + i, STOREY - 1, 0), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
				}
			} else if (postDoor) {
				// (On the door's frame logs, in the bays either side.)
				this.put(r.at(front, at - 1, 2, 0), torch(front));
				this.put(r.at(front, at + 1, 2, 0), torch(front));
			} else {
				this.put(r.at(front, bay[0] - 1, 2, -1), torch(front));
				this.put(r.at(front, bay[1] + 1, 2, -1), torch(front));
			}
		}

		/** A door inside (between rooms): at {@code lower}, facing that way. */
		void doorway(BlockPos lower, Direction facing) {
			BlockState door = this.m.door().defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
				.setValue(DoorBlock.OPEN, false);
			this.put(lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			this.put(lower.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		}

		/** A staircase along a wall's inside, from a storey's floor up to the next. */
		void flight(Rect r, Flight flight) {
			Direction up = flight.dir() > 0 ? Direction.EAST : Direction.WEST;
			for (int i = 0; i < STEPS; i++) {
				this.put(r.at(flight.side(), flight.start() + flight.dir() * i, flight.storey() * STOREY + 1 + i, inside(flight.storey())), stair(this.m.stair(), up));
			}
		}

		/**
		 * A window in a bay: centred, a block clear of the posts (wider bays: three wide or one with room either side), in
		 * the wall a block in from the frame, at eye level (two high if tall); a sill under it, a lintel over it.
		 */
		void window(Rect r, Direction side, int[] bay, int y0, boolean tall, boolean wide, boolean sills, int depth) {
			int bw = bay[1] - bay[0] + 1;
			int ww = bw >= 6 ? (bw % 2 == 1 ? 3 : 2) : bw == 5 ? (wide ? 3 : 1) : bw - 2;
			if (ww < 1) {
				return;
			}

			int from = bay[0] + (bw - ww) / 2;
			int lowest = y0 + 2;
			int highest = Math.min(y0 + STOREY - 1, lowest + (tall ? 1 : 0));
			Direction in = side.getOpposite();
			for (int a = from; a < from + ww; a++) {
				for (int y = lowest; y <= highest; y++) {
					BlockPos pos = r.at(side, a, y, depth);
					BlockState there = this.cells.get(pos);
					if (there != null && (there.getBlock() instanceof DoorBlock || there.getBlock() instanceof StairBlock)) {
						return;
					}

					this.put(pos, this.m.window().defaultBlockState());
				}

				if (sills && this.m.sill() != null) {
					this.putIfFree(r.at(side, a, lowest - 1, depth - 1), upsideDown(this.m.sill(), in));
				}

				if (this.m.lintels() && highest + 1 < y0 + STOREY) {
					this.putIfFree(r.at(side, a, highest + 1, depth - 1), upsideDown(this.m.stair(), in));
				}
			}
		}

		/** A wall across the house at post {@code along} (under its tie beam), a door in its middle. */
		void partition(Rect r, int along, int y0, Block infill, int inset) {
			int x = r.x0() + along;
			for (int z = r.z0() + inset; z <= r.z1() - inset; z++) {
				for (int y = y0 + 1; y < y0 + STOREY; y++) {
					this.put(new BlockPos(x, y, z), axis(infill, Direction.Axis.Z));
				}
			}

			this.doorway(new BlockPos(x, y0 + 1, (r.z0() + r.z1()) / 2), Direction.EAST);
		}

		/** Torches on the inside of a wall at its posts (as the user put them), at the height the guidebook puts wall lights. */
		void torches(Rect r, Direction side, int y0, int depth) {
			int len = r.length(side);
			int gap = gapFor(len, this.gap);
			for (int a = depth + 1; a <= len - depth - 2; a++) {
				if (post(len, gap, a)) {
					BlockPos pos = r.at(side, a, y0 + 2, depth + 1);
					BlockState behind = this.cells.get(r.at(side, a, y0 + 2, depth));
					if (behind != null && behind.getBlock() instanceof RotatedPillarBlock) {
						this.putIfFree(pos, torch(side.getOpposite()));
					}
				}
			}
		}

		/**
		 * A gable roof on the rect, its ridge along x: rows of stairs climbing in from both sides, resting on a row of the
		 * roof's full block on the top beam (the beam shows below the eaves), the eave a block out beside it; a ridge cap where an odd width leaves a middle row; steep: a block and a stair a block across. Under
		 * each row the rake (the gable's sloping edge) is trimmed with upside-down stairs, and a vaulted roof gets them all
		 * along (the second layer the guidebook asks for on wide roofs; a vaulted ceiling inside).
		 */
		void gable(Rect r, int top, boolean steep, boolean vault) {
			for (int j = -1; ; j++) {
				int low = r.z0() + j;
				int high = r.z1() - j;
				if (low > high) {
					return;
				}

				for (int x = r.x0() - 1; x <= r.x1() + 1; x++) {
					// (The rake and the frame line behind it: the gable itself is a block further in, with the walls.)
					boolean rake = x <= r.x0() || x >= r.x1();
					if (steep) {
						if (low == high) {
							this.put(new BlockPos(x, top + 1 + 2 * j, low), this.m.roofBlock().defaultBlockState());
							this.put(new BlockPos(x, top + 2 + 2 * j, low), this.m.slab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
							continue;
						}

						for (int side = 0; side < 2; side++) {
							int z = side == 0 ? low : high;
							Direction facing = side == 0 ? Direction.SOUTH : Direction.NORTH;
							// (At the rake an upside-down stair, not a block: a smooth edge, not jagged steps.)
							if (j >= 0) {
								this.put(new BlockPos(x, top + 2 + 2 * j, z), rake ? upsideDown(this.m.stair(), facing.getOpposite())
									: this.m.roofBlock().defaultBlockState());
							}

							if (j == 0) {
								this.putIfFree(new BlockPos(x, top + 1, z), this.m.roofBlock().defaultBlockState());
							}

							this.put(new BlockPos(x, top + 3 + 2 * j, z), stair(this.m.stair(), facing));
						}

						continue;
					}

					int y = top + 2 + j;
					if (low == high) {
						this.put(new BlockPos(x, y, low), this.m.logRidge() ? axis(this.m.frame(), Direction.Axis.X)
							: this.m.slab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
						// (A block under the cap always: the ridge closed even before the cap is on, and the cap something to sit on.)
						this.putIfFree(new BlockPos(x, y - 1, low), this.m.roofBlock().defaultBlockState());

						continue;
					}

					this.put(new BlockPos(x, y, low), stair(this.m.stair(), Direction.SOUTH));
					this.put(new BlockPos(x, y, high), stair(this.m.stair(), Direction.NORTH));
					// (On the beam a full block: an upside-down stair there has nothing to be placed against, found in testing: a
					// hip roof never started.)
					if (j == 0) {
						this.putIfFree(new BlockPos(x, y - 1, low), this.m.roofBlock().defaultBlockState());
						this.putIfFree(new BlockPos(x, y - 1, high), this.m.roofBlock().defaultBlockState());
					} else if (j > 0 && (vault || rake)) {
						this.putIfFree(new BlockPos(x, y - 1, low), upsideDown(this.m.stair(), Direction.NORTH));
						this.putIfFree(new BlockPos(x, y - 1, high), upsideDown(this.m.stair(), Direction.SOUTH));
					}
				}

				if (low == high || low + 1 == high) {
					return;
				}
			}
		}

		/**
		 * The gable ends filled (a lighter block) up to the roof, a window in the middle of each asked for. In line with the
		 * top storey's wall, {@code depth} in from the frame (found in testing: on the frame line over a wall set back a block,
		 * a builder can't place them, the wall below hiding them from inside and too high to reach from outside).
		 */
		void gableEnds(Rect r, int top, boolean westWindow, boolean eastWindow, int depth) {
			int span = r.z1() - r.z0() + 1;
			int middle = (r.z0() + r.z1()) / 2;
			for (int x : new int[] {r.x0() + depth, r.x1() - depth}) {
				boolean window = x == r.x0() + depth ? westWindow : eastWindow;
				for (int z = r.z0() + 1; z < r.z1(); z++) {
					for (int y = top + 1; y < top + 64; y++) {
						BlockPos pos = new BlockPos(x, y, z);
						if (this.cells.containsKey(pos)) {
							break;
						}

						boolean glass = window && span >= ATTIC_SPAN && (z == middle || span % 2 == 0 && z == middle + 1)
							&& (y == top + 3 || span >= 11 && y == top + 4);
						this.put(pos, glass ? this.m.window().defaultBlockState() : this.m.gableFill().defaultBlockState());
					}
				}
			}
		}

		/**
		 * A hip roof: rings of stairs climbing in from all four sides (on a ring of the roof's full block on the top beam, the
		 * eave a block out beside it), a slab ridge where the short sides meet. Each ring up has a ring under it to rest on
		 * (upside-down stairs if vaulted, else the gable block).
		 */
		void hip(Rect r, int top, boolean vault) {
			for (int k = -1; ; k++) {
				int ax = r.x0() + k;
				int bx = r.x1() - k;
				int az = r.z0() + k;
				int bz = r.z1() - k;
				if (ax > bx || az > bz) {
					return;
				}

				int y = top + 2 + k;
				Map<BlockPos, Direction> ring = new HashMap<>();
				if (ax == bx || az == bz) {
					for (int x = ax; x <= bx; x++) {
						for (int z = az; z <= bz; z++) {
							this.put(new BlockPos(x, y, z), this.m.slab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
							ring.put(new BlockPos(x, y, z), null);
						}
					}
				} else {
					for (int x = ax; x <= bx; x++) {
						ring.put(new BlockPos(x, y, az), Direction.SOUTH);
						ring.put(new BlockPos(x, y, bz), Direction.NORTH);
					}

					for (int z = az + 1; z < bz; z++) {
						ring.put(new BlockPos(ax, y, z), Direction.EAST);
						ring.put(new BlockPos(bx, y, z), Direction.WEST);
					}

					ring.forEach((pos, facing) -> this.put(pos, stair(this.m.stair(), facing)));
				}

				if (k >= 0) {
					BlockState plate = k == 0 ? this.m.roofBlock().defaultBlockState() : this.m.gableFill().defaultBlockState();
					boolean stairs = vault && k > 0;
					ring.forEach((pos, facing) -> this.putIfFree(pos.below(), stairs && facing != null ? upsideDown(this.m.stair(), facing.getOpposite()) : plate));
				}

				if (ax == bx || az == bz) {
					return;
				}
			}
		}
	}

	private static BlockState torch(Direction facing) {
		return Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing);
	}

	/** A state drawn with x and z swapped (the wing's roof, drawn as if its ridge ran along x): the mirror in the x = z line. */
	private static BlockState rotateState(BlockState state) {
		if (state.hasProperty(StairBlock.FACING)) {
			Direction facing = state.getValue(StairBlock.FACING);
			Direction mirrored = switch (facing) {
				case NORTH -> Direction.WEST;
				case SOUTH -> Direction.EAST;
				case WEST -> Direction.NORTH;
				case EAST -> Direction.SOUTH;
				default -> facing;
			};
			return state.setValue(StairBlock.FACING, mirrored);
		}

		if (state.hasProperty(RotatedPillarBlock.AXIS) && state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y) {
			return state.setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
		}

		return state;
	}

	private static BlockState stair(Block block, Direction facing) {
		BlockState state = block.defaultBlockState();
		return state.hasProperty(StairBlock.FACING) ? state.setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM) : state;
	}

	private static BlockState upsideDown(Block block, Direction facing) {
		BlockState state = block.defaultBlockState();
		return state.hasProperty(StairBlock.FACING) ? state.setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.TOP) : state;
	}

	private static BlockState axis(Block block, Direction.Axis axis) {
		BlockState state = block.defaultBlockState();
		return state.hasProperty(RotatedPillarBlock.AXIS) ? state.setValue(RotatedPillarBlock.AXIS, axis) : state;
	}

	private static int pick(List<Integer> options, RandomSource random) {
		return options.get(random.nextInt(options.size()));
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
}
