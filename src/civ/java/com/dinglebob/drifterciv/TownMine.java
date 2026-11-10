package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;

/**
 * The town's mine as its people use it (the user, 2026-11-07: "nobody uses the town mine to start strip mining/exploration
 * mining, still having random strip mines around town"): the faces to dig on from, worked out from how {@link TownBuilder}
 * digs it (its stairs from the entrance, a landing every sixteen steps, the hub at the bottom, the corridor on with a
 * strip off it every four blocks either side), and handed out a few to each of its people: two strips of its own to carry
 * on, a landing or the corridor's end to branch off from, a way on down from the hub. Each its own, so far as they go round:
 * two people at the same face would dig into each other's tunnels and take them for caves.
 */
final class TownMine {
	private TownMine() {
	}

	/** The hub's half-width, the corridor's start past the hub and its length, the strips' spacing, start and end along. */
	private static final int HUB = 3;
	private static final int CORRIDOR_FROM = 4;
	private static final int CORRIDOR = 32;
	private static final int STRIP_EVERY = 4;
	private static final int STRIP_AT = 2;
	private static final int STRIP_END = 17;
	/** A landing every this many steps down, three long. */
	private static final int LANDING_EVERY = 16;
	private static final int LANDING = 3;

	/** Its stairs' slices are this high, the step included (0.23.35: five, room to jump; was four). */
	static final int STAIRS_HIGH = 5;

	/**
	 * Each slice of its stairs, as {@link TownBuilder} digs them: the middle of the slice at its feet (a step's own block),
	 * and whether it's a landing's (level, no step).
	 */
	static void slices(BlockPos entrance, BlockPos hub, Direction way, java.util.function.BiConsumer<BlockPos, Boolean> each) {
		int feet = entrance.getY();
		BlockPos column = entrance;
		int run = 0;
		int guard = 0;
		while (feet > hub.getY() && guard++ < 400) {
			column = column.relative(way);
			if (run == LANDING_EVERY) {
				for (int i = 0; i < LANDING; i++) {
					each.accept(new BlockPos(column.getX(), feet, column.getZ()), true);
					column = column.relative(way);
				}

				run = 0;
			}

			feet--;
			run++;
			each.accept(new BlockPos(column.getX(), feet, column.getZ()), false);
		}
	}

	/**
	 * Its stairs are its people's build (0.23.35, the user: "people keep mining the stairs, the walls in the stairs,
	 * blocking the mines"): each step, the floor under it and the walls beside it, not to be dug for stone or tunnelled
	 * through. Not a landing's walls: they're faces to branch off from.
	 */
	static void protect(net.minecraft.server.level.ServerLevel level, BlockPos entrance, BlockPos hub, Direction way) {
		Direction side = way.getClockWise();
		slices(entrance, hub, way, (feet, landing) -> {
			if (landing) {
				return;
			}

			for (int b = -2; b <= 2; b++) {
				BlockPos cell = feet.relative(side, b);
				if (Math.abs(b) <= 1) {
					DrifterApi.markBuilt(level, cell);
					DrifterApi.markBuilt(level, cell.below());
				} else {
					for (int h = 0; h < STAIRS_HIGH; h++) {
						DrifterApi.markBuilt(level, cell.above(h));
					}
				}
			}
		});
	}

	/** Towns whose mine stairs have been mended this session. */
	private static final java.util.Set<UUID> mended = new java.util.HashSet<>();

	/**
	 * A town's mine stairs put right, once a session when they're all loaded (0.23.35: dug into, blocked with cobblestone,
	 * low enough to bump a head on): every step back, the way over it clear and five high (torches kept), the floor and the
	 * walls whole again (not out in the open air, nor where someone's standing), and the lot its people's build from now on.
	 * True if anything changed.
	 */
	static boolean mend(net.minecraft.server.level.ServerLevel level, Town town) {
		if (town.mine == null || town.mineBottom == null || town.mineWay == null || mended.contains(town.id)
			|| !town.dimension.equals(level.dimension().location().toString())) {
			return false;
		}

		List<BlockPos> feet = new ArrayList<>();
		List<Boolean> landings = new ArrayList<>();
		slices(town.mine, town.mineBottom, town.mineWay, (at, landing) -> {
			feet.add(at);
			landings.add(landing);
		});
		for (BlockPos at : feet) {
			if (!level.isLoaded(at)) {
				return false;
			}
		}

		mended.add(town.id);
		Direction way = town.mineWay;
		Direction side = way.getClockWise();
		// (Its own stairs' block and wall: what's there already, cobblestone if none is.)
		net.minecraft.world.level.block.state.BlockState steps = net.minecraft.world.level.block.Blocks.COBBLESTONE_STAIRS.defaultBlockState();
		for (BlockPos at : feet) {
			if (level.getBlockState(at).getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
				steps = level.getBlockState(at).getBlock().defaultBlockState();
				break;
			}
		}

		steps = steps.setValue(net.minecraft.world.level.block.StairBlock.FACING, way.getOpposite());
		net.minecraft.world.level.block.state.BlockState wall = steps.is(net.minecraft.world.level.block.Blocks.SANDSTONE_STAIRS)
			? net.minecraft.world.level.block.Blocks.SANDSTONE.defaultBlockState() : net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState();
		int changed = 0;
		for (int i = 0; i < feet.size(); i++) {
			BlockPos at = feet.get(i);
			boolean landing = landings.get(i);
			for (int b = -1; b <= 1; b++) {
				BlockPos cell = at.relative(side, b);
				if (!landing && !(level.getBlockState(cell).getBlock() instanceof net.minecraft.world.level.block.StairBlock) && clear(level, cell)) {
					level.setBlock(cell, steps, 3);
					changed++;
				}

				for (int h = landing ? 0 : 1; h < STAIRS_HIGH; h++) {
					BlockPos over = cell.above(h);
					var state = level.getBlockState(over);
					if (!state.isAir() && !lightOrTorch(state) && state.getFluidState().isEmpty()) {
						level.setBlock(over, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
						changed++;
					}
				}

				// (Over its new top: nothing to fall or pour in.)
				BlockPos top = cell.above(STAIRS_HIGH);
				var above = level.getBlockState(top);
				if (above.getBlock() instanceof net.minecraft.world.level.block.FallingBlock || !above.getFluidState().isEmpty()) {
					level.setBlock(top, wall, 3);
					changed++;
				}

				BlockPos floor = cell.below();
				if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty() && clear(level, floor)) {
					level.setBlock(floor, wall, 3);
					changed++;
				}
			}

			if (!landing) {
				for (int b = -2; b <= 2; b += 4) {
					for (int h = 0; h < STAIRS_HIGH; h++) {
						BlockPos pos = at.relative(side, b).above(h);
						var state = level.getBlockState(pos);
						// (Only a hole dug for its stone: rock behind it. A way through into someone's tunnel stays open: walling it
						// would shut them in.)
						BlockPos behind = at.relative(side, b > 0 ? 3 : -3).above(h);
						if (state.getCollisionShape(level, pos).isEmpty() && !lightOrTorch(state) && !level.canSeeSky(pos) && clear(level, pos)
							&& !level.getBlockState(behind).getCollisionShape(level, behind).isEmpty()) {
							level.setBlock(pos, wall, 3);
							changed++;
						}
					}
				}
			}
		}

		protect(level, town.mine, town.mineBottom, town.mineWay);
		if (changed > 0) {
			DrifterCivilizations.LOGGER.info("[town] {}: its mine stairs mended ({} blocks put right)", town.name, changed);
		}

		return changed > 0;
	}

	/** A torch, a lantern: kept where it is. */
	private static boolean lightOrTorch(net.minecraft.world.level.block.state.BlockState state) {
		return state.getBlock() instanceof net.minecraft.world.level.block.TorchBlock || state.getBlock() instanceof net.minecraft.world.level.block.LanternBlock;
	}

	/** Nobody standing in it (no block put on anyone). */
	private static boolean clear(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
		return level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(pos)).isEmpty();
	}

	/** The way down it, a stretch at a time: its mouth, the middle of each landing of its stairs, its hub. */
	static List<BlockPos> path(BlockPos entrance, BlockPos hub, Direction way) {
		List<BlockPos> path = new ArrayList<>();
		path.add(entrance);
		int feet = entrance.getY();
		BlockPos column = entrance;
		int run = 0;
		int guard = 0;
		while (feet > hub.getY() && guard++ < 400) {
			column = column.relative(way);
			if (run == LANDING_EVERY) {
				path.add(new BlockPos(column.getX(), feet, column.getZ()).relative(way));
				column = column.relative(way, LANDING);
				run = 0;
			}

			feet--;
			run++;
		}

		path.add(hub);
		return path;
	}

	/** Every face of a mine dug from this entrance down to this hub, its corridor running {@code way}. */
	static List<Home.MineFace> faces(BlockPos entrance, BlockPos hub, Direction way) {
		Direction side = way.getClockWise();
		List<Home.MineFace> faces = new ArrayList<>();
		// The strips' ends, both sides, along the corridor.
		BlockPos start = hub.relative(way, CORRIDOR_FROM);
		for (int i = STRIP_AT; i < CORRIDOR; i += STRIP_EVERY) {
			for (Direction out : new Direction[] {side, side.getOpposite()}) {
				faces.add(new Home.MineFace(start.relative(way, i).relative(out, STRIP_END), out, Home.Dig.STRIP));
			}
		}

		// The corridor's end, and the landings of its stairs (on along their open side, away from their torches).
		faces.add(new Home.MineFace(start.relative(way, CORRIDOR - 1), way, Home.Dig.BRANCH));
		int feet = entrance.getY();
		BlockPos column = entrance;
		int run = 0;
		int guard = 0;
		while (feet > hub.getY() && guard++ < 400) {
			column = column.relative(way);
			if (run == LANDING_EVERY) {
				BlockPos middle = new BlockPos(column.getX(), feet, column.getZ()).relative(way);
				faces.add(new Home.MineFace(middle.relative(side), side, Home.Dig.BRANCH));
				column = column.relative(way, LANDING);
				run = 0;
			}

			feet--;
			run++;
		}

		// Ways on down from the hub's sides (its corners and between, clear of its torches).
		for (int a = -HUB; a <= HUB; a += 2) {
			for (Direction out : new Direction[] {side, side.getOpposite()}) {
				faces.add(new Home.MineFace(hub.relative(way, a).relative(out, HUB), out, Home.Dig.DOWN));
			}
		}

		return faces;
	}

	/** The faces for the {@code index}th of its people: two strips, a branch face while they go round, a way down. */
	static List<Home.MineFace> share(List<Home.MineFace> all, int index) {
		List<Home.MineFace> strips = all.stream().filter(face -> face.dig() == Home.Dig.STRIP).toList();
		List<Home.MineFace> branches = all.stream().filter(face -> face.dig() == Home.Dig.BRANCH).toList();
		List<Home.MineFace> downs = all.stream().filter(face -> face.dig() == Home.Dig.DOWN).toList();
		List<Home.MineFace> mine = new ArrayList<>();
		if (!strips.isEmpty()) {
			mine.add(strips.get(index % strips.size()));
		}

		if (index < branches.size()) {
			mine.add(branches.get(index));
		}

		if (strips.size() > 1) {
			Home.MineFace second = strips.get((index + strips.size() / 2) % strips.size());
			if (!mine.contains(second)) {
				mine.add(second);
			}
		}

		if (!downs.isEmpty()) {
			mine.add(downs.get(index % downs.size()));
		}

		return mine;
	}

	/** Its home's share of the town's mine (the {@code index}th of its people); nothing if the town has none. */
	static void give(Town town, Home home, int index) {
		if (town.mine == null || town.mineBottom == null || town.mineWay == null || home == null) {
			return;
		}

		home.townMine.clear();
		home.townMine.addAll(share(faces(town.mine, town.mineBottom, town.mineWay), index));
		home.townMinePath.clear();
		home.townMinePath.addAll(path(town.mine, town.mineBottom, town.mineWay));
		// (Not the old way: the hub as a staircase of its own to branch from round the compass.)
		if (town.mineBottom.equals(home.mineBottom)) {
			home.mineBottom = null;
			home.mineDirection = null;
		}
	}

	/**
	 * A town that had its mine before its people knew its faces (a world from before 0.23.29): theirs now, each of its people
	 * with a home that doesn't know them yet. True if anyone did.
	 */
	static boolean check(MinecraftServer server, Town town) {
		if (town.group == null) {
			return false;
		}

		DrifterGroups.Group group = DrifterApi.groupById(town.group);
		if (group == null) {
			return false;
		}

		boolean any = false;
		int index = 0;
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			Home home = member == null ? null : DrifterApi.home(member);
			// (Its store's chests: its town's, not its own: 0.23.30, worlds from before.)
			if (home != null && home.center.closerThan(town.center, 160.0)) {
				for (BlockPos chest : town.store) {
					if (home.isChest(chest) && !home.townChests.contains(chest)) {
						DrifterApi.townChest(home, chest);
						any = true;
					}
				}
			}

			if (home != null && town.mine != null && (home.townMine.isEmpty() || home.townMinePath.isEmpty()) && home.center.closerThan(town.center, 160.0)) {
				give(town, home, index);
				any = true;
			}

			index++;
		}

		return any;
	}

	/**
	 * (Debug: `/drifterciv town check`.) The town looked over for what the user found wrong: torches in its mine (within its
	 * stairs and workings), beds beside stairs or something used from the floor, enchanting tables and anvils not under a
	 * roof, houses on its kept ground.
	 */
	static String check(net.minecraft.server.level.ServerLevel level, Town town) {
		int torches = 0;
		if (town.mine != null && town.mineBottom != null) {
			int x0 = Math.min(town.mine.getX(), town.mineBottom.getX()) - 24;
			int x1 = Math.max(town.mine.getX(), town.mineBottom.getX()) + 60;
			int z0 = Math.min(town.mine.getZ(), town.mineBottom.getZ()) - 24;
			int z1 = Math.max(town.mine.getZ(), town.mineBottom.getZ()) + 60;
			for (BlockPos pos : BlockPos.betweenClosed(x0 - 36, town.mineBottom.getY() - 1, z0 - 36, x1, town.mine.getY() + 4, z1)) {
				if (level.isLoaded(pos) && level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.WALL_TORCH) && pos.getY() < town.mine.getY() - 2) {
					torches++;
				}
			}
		}

		int beds = 0;
		int inTheWay = 0;
		int outdoors = 0;
		List<String> where = new ArrayList<>();
		java.util.Set<BlockPos> seenBeds = new java.util.HashSet<>();
		for (Town.Plot plot : town.plots) {
			for (BlockPos pos : BlockPos.betweenClosed(plot.origin.getX(), plot.origin.getY() - 2, plot.origin.getZ(), plot.origin.getX() + plot.sizeX - 1,
				plot.origin.getY() + 16, plot.origin.getZ() + plot.sizeZ - 1)) {
				if (!level.isLoaded(pos)) {
					continue;
				}

				var state = level.getBlockState(pos);
				if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock && seenBeds.add(pos.immutable())) {
					beds++;
					boolean way = false;
					for (Direction side : Direction.Plane.HORIZONTAL) {
						for (int dy = -1; dy <= 0; dy++) {
							var next = level.getBlockState(pos.relative(side).above(dy));
							way |= next.getBlock() instanceof net.minecraft.world.level.block.StairBlock || next.is(net.minecraft.tags.BlockTags.CLIMBABLE);
						}

						var by = level.getBlockState(pos.relative(side));
						way |= by.getBlock() instanceof net.minecraft.world.level.block.AbstractFurnaceBlock || by.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)
							|| by.is(net.minecraft.world.level.block.Blocks.ANVIL) || by.is(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE);
					}

					inTheWay += way ? 1 : 0;
				} else if (state.is(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE) || state.is(net.minecraft.world.level.block.Blocks.ANVIL)
					|| state.is(net.minecraft.world.level.block.Blocks.SMITHING_TABLE)) {
					boolean roof = false;
					for (int up = 2; up <= 12 && !roof; up++) {
						roof = !level.getBlockState(pos.above(up)).getCollisionShape(level, pos.above(up)).isEmpty();
					}

					if (!roof) {
						outdoors++;
						int over = 0;
						for (int up = 1; up <= 24 && over == 0; up++) {
							over = level.getBlockState(pos.above(up)).getCollisionShape(level, pos.above(up)).isEmpty() ? 0 : up;
						}

						StringBuilder sides = new StringBuilder();
						for (Direction side : Direction.Plane.HORIZONTAL) {
							int wall = 0;
							for (int i = 1; i <= 12 && wall == 0; i++) {
								wall = level.getBlockState(pos.relative(side, i)).getCollisionShape(level, pos.relative(side, i)).isEmpty() ? 0 : i;
							}

							sides.append(side.getName().charAt(0)).append(wall);
						}

						where.add(state.getBlock().getName().getString() + " at " + pos.toShortString() + " (first block over it " + (over == 0 ? "none" : "+" + over)
							+ ", walls " + sides + ", floor " + level.getBlockState(pos.below()).getBlock().getName().getString() + ")");
					}
				}
			}
		}

		int onKept = 0;
		for (Town.Plot plot : town.plots) {
			for (int[] rect : town.keepOff) {
				if (plot.origin.getX() <= rect[2] && plot.origin.getX() + plot.sizeX - 1 >= rect[0] && plot.origin.getZ() <= rect[3]
					&& plot.origin.getZ() + plot.sizeZ - 1 >= rect[1]) {
					onKept++;
				}
			}
		}

		return String.format(java.util.Locale.ROOT, "%d torches in its mine; %d of %d bed halves beside stairs, a ladder or a station; %d workshop blocks"
			+ " not under a roof%s; %d houses on its kept ground (%d kept)", torches, inTheWay, beds, outdoors, where.isEmpty() ? "" : " " + where, onKept,
			town.keepOff.size());
	}
}
