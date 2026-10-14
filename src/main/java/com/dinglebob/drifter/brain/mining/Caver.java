package com.dinglebob.drifter.brain.mining;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.perception.TerrainKnowledge;
import com.dinglebob.drifter.personality.Habits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Exploring a cave (DESIGN.md section 10.3 "Caving", the user's request in session 2: "fully explore caves, go deep";
 * since 1.18 most iron is found this way). It only goes where it has looked: the next place to go is a "frontier", a
 * spot it has seen it could stand on, underground, next to blocks it hasn't seen yet, preferring deeper ones toward the
 * ore's depth. It lights the way with torches on its habit's side as it goes (breadcrumbs back), and looks around
 * whenever it gets somewhere new. Ore, fights and turning back are handled by the mining trip around it.
 */
public final class Caver {
	/** How far around itself it looks for the next frontier. */
	private static final int SEARCH_RADIUS = 24;
	/** Regions (this many blocks a side) it has already been to don't count as new. */
	private static final int REGION = 4;
	/** It lights the way where block light at its feet is below this. */
	private static final int DARK = 7;
	/** Blocks walked between torches, at least. */
	private static final double TORCH_SPACING = 6.0;
	/** Legs (walks to a frontier) per cave trip at most. */
	public static final int MAX_LEGS = 48;

	private final DrifterEntity drifter;
	private final int depth;
	private final Set<Long> visited = new HashSet<>();
	private final Set<BlockPos> unreachable = new HashSet<>();
	private final Map<BlockPos, Integer> torchTries = new HashMap<>();
	/** Where it went in. */
	public final BlockPos entrance;
	private BlockPos lastTorch;
	@Nullable
	private BlockPos goingTo;
	private boolean lookPending;
	private int legs;
	@Nullable
	private String lastThought;

	private final int maxLegs;
	private boolean reachedEntrance;
	private boolean peeked;

	public Caver(DrifterEntity drifter, BlockPos entrance, int depth, int maxLegs) {
		this.drifter = drifter;
		this.entrance = entrance.immutable();
		this.depth = depth;
		this.maxLegs = maxLegs;
		this.lastTorch = drifter.blockPosition();
		// It has only glimpsed the cave so far: a good look first (found in testing: straight after breaking in it knew
		// no spot in there to go to, and gave up on the cave at once).
		this.lookPending = true;
	}

	/** The next step, or null when there's nothing more to explore from here (or it has done enough legs). */
	@Nullable
	public Task next() {
		BlockPos feet = this.drifter.blockPosition();
		this.visited.add(region(feet));

		// Light first: it can only see 8 blocks in the dark, and the torches lead it back out.
		Task torch = this.torch(feet);
		if (torch != null) {
			return torch;
		}

		if (this.lookPending) {
			this.lookPending = false;
			return new LookAroundTask(this.drifter);
		}

		// First into the cave it chose.
		// Into the opening first, then a look round from inside (found in testing: looking round from its tunnel, only one
		// way faced the hole, it saw almost nothing of the cave and found nowhere to go).
		// A peek through the opening first: it's often in the floor ahead (a hole down into the cave), which looking round
		// level never shows (found in testing: it knew almost nothing of the caves it broke into).
		if (!this.peeked) {
			this.peeked = true;
			return new com.dinglebob.drifter.executor.task.LookAtTask(this.drifter, Vec3.atCenterOf(this.entrance), 30);
		}

		if (!this.reachedEntrance) {
			this.reachedEntrance = true;
			if (feet.distSqr(this.entrance) > 1) {
				this.goingTo = this.entrance;
				this.lookPending = true;
				return new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.entrance), 1.0, "step into the cave");
			}
		}

		// Loot on the way: a spawner gets broken (no more mobs from it), a dungeon chest or a mineshaft's chest minecarts
		// get looted (section 10.11; user's request in session 2).
		Task loot = this.loot(feet);
		if (loot != null) {
			return loot;
		}

		if (this.legs >= this.maxLegs) {
			this.say("explored enough of this cave for one trip");
			return null;
		}

		BlockPos target = this.frontier(feet);
		if (target == null) {
			this.say("nothing left in this cave it can get to " + debugCounts(this.drifter, feet, SEARCH_RADIUS, this.visited, this.unreachable));
			return null;
		}

		this.legs++;
		this.goingTo = target;
		return new NavigateTask(this.drifter, Vec3.atBottomCenterOf(target), 1.5, "explore the cave");
	}

	public void stepDone(Task step, Task.Status status) {
		if (step instanceof NavigateTask && this.goingTo != null) {
			if (status == Task.Status.SUCCESS) {
				this.lookPending = true;
			} else {
				this.unreachable.add(this.goingTo);
			}

			this.visited.add(region(this.goingTo));
			this.goingTo = null;
		}

		if (step instanceof PlaceBlockTask && status == Task.Status.SUCCESS) {
			this.lastTorch = this.drifter.blockPosition();
		}
	}

	@Nullable
	public String takeThought() {
		String thought = this.lastThought;
		this.lastThought = null;
		return thought;
	}

	private void say(String thought) {
		this.lastThought = this.lastThought == null ? thought : this.lastThought + "; " + thought;
	}

	/** How far it goes for a chest or a spawner it has seen while caving. */
	private static final double LOOT_RANGE = 16.0;
	private final Map<BlockPos, Integer> approaches = new HashMap<>();

	@Nullable
	private Task loot(BlockPos feet) {
		Level level = this.drifter.level();
		var brain = this.drifter.getDrifterBrain();
		var memory = this.drifter.getMemory();
		var dimension = level.dimension().location();
		Vec3 here = this.drifter.position();

		com.dinglebob.drifter.memory.MemoryEntry spawner = memory.nearest(com.dinglebob.drifter.memory.MemoryKind.STRUCTURE, dimension, here, LOOT_RANGE,
			e -> e.label.equals("minecraft:spawner") && !brain.looted.contains(e.blockPos()));
		if (spawner != null) {
			BlockPos pos = spawner.blockPos();
			if (!level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.SPAWNER)) {
				brain.looted.add(pos);
			} else {
				Task step = this.approach(pos, "breaking the spawner at " + pos.toShortString(),
					() -> com.dinglebob.drifter.brain.activity.Activity.digTask(this.drifter, pos));
				if (step != null) {
					return step;
				}
			}
		}

		com.dinglebob.drifter.brain.Home home = brain.home();
		com.dinglebob.drifter.memory.MemoryEntry chest = memory.nearest(com.dinglebob.drifter.memory.MemoryKind.STORAGE, dimension, here, LOOT_RANGE,
			e -> !e.label.equals(com.dinglebob.drifter.perception.Senses.CHEST_CART) && !brain.looted.contains(e.blockPos())
				&& (home == null || !home.chests().contains(e.blockPos())) && underground(this.drifter, e.blockPos()));
		if (chest != null) {
			BlockPos pos = chest.blockPos();
			if (com.dinglebob.drifter.hands.Containers.get(level, pos) == null) {
				brain.looted.add(pos);
			} else {
				Task step = this.approach(pos, "looting a chest at " + pos.toShortString(), () -> {
					brain.looted.add(pos);
					return new com.dinglebob.drifter.executor.task.ContainerTask(this.drifter, pos, com.dinglebob.drifter.executor.task.ContainerTask.Mode.TAKE,
						stack -> !com.dinglebob.drifter.brain.Storage.isJunk(this.drifter, stack), "loot", 64 * 27, null);
				});
				if (step != null) {
					return step;
				}
			}
		}

		com.dinglebob.drifter.memory.MemoryEntry cart = memory.nearest(com.dinglebob.drifter.memory.MemoryKind.STORAGE, dimension, here, LOOT_RANGE,
			e -> e.label.equals(com.dinglebob.drifter.perception.Senses.CHEST_CART) && e.entityId != null && !brain.lootedCarts.contains(e.entityId));
		if (cart != null && level instanceof net.minecraft.server.level.ServerLevel server) {
			if (!(server.getEntity(cart.entityId) instanceof net.minecraft.world.entity.vehicle.MinecartChest minecart) || minecart.isRemoved()) {
				brain.lootedCarts.add(cart.entityId);
				return null;
			}

			double reach = this.drifter.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
			if (minecart.getBoundingBox().distanceToSqr(this.drifter.getEyePosition()) <= reach * reach) {
				brain.lootedCarts.add(cart.entityId);
				this.say("looting a chest minecart");
				return new com.dinglebob.drifter.executor.task.LootCartTask(this.drifter, minecart,
					stack -> !com.dinglebob.drifter.brain.Storage.isJunk(this.drifter, stack));
			}

			if (this.approaches.merge(minecart.blockPosition(), 1, Integer::sum) > 2) {
				brain.lootedCarts.add(cart.entityId);
				return null;
			}

			return new NavigateTask(this.drifter, minecart.position(), 1.5, "walk to the chest minecart");
		}

		return null;
	}

	/** Within reach: the action. Otherwise a walk to where it can reach it (twice at most, then it gives up on it). */
	@Nullable
	private Task approach(BlockPos pos, String thought, java.util.function.Supplier<Task> action) {
		if (com.dinglebob.drifter.hands.Reach.canReach(this.drifter, pos)) {
			this.say(thought);
			return action.get();
		}

		BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, pos, Set.of());
		if (spot == null || this.approaches.merge(pos, 1, Integer::sum) > 2) {
			this.drifter.getDrifterBrain().looted.add(pos);
			return null;
		}

		return new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk over to it");
	}

	/** A torch on the wall beside its head, on its habit's side, when it's dark here and it has walked a bit. */
	@Nullable
	private Task torch(BlockPos feet) {
		Level level = this.drifter.level();
		if (this.drifter.getInventory().count(stack -> stack.is(Items.TORCH)) == 0 || !underground(this.drifter, feet)
			|| level.getBrightness(LightLayer.BLOCK, feet) >= DARK || feet.distSqr(this.lastTorch) < TORCH_SPACING * TORCH_SPACING) {
			return null;
		}

		Direction facing = this.drifter.getDirection();
		Direction side = this.drifter.getPersonality().habits().torchSide == Habits.Side.RIGHT ? facing.getClockWise() : facing.getCounterClockWise();
		for (BlockPos spot : new BlockPos[]{feet.above(), feet}) {
			if (!level.getBlockState(spot).isAir() || this.torchTries.merge(spot.immutable(), 1, Integer::sum) > 1) {
				continue;
			}

			// On the wall at its habit side if there is one, otherwise on the floor.
			BlockPos wall = spot.relative(side);
			boolean hasWall = !level.getBlockState(wall).getCollisionShape(level, wall).isEmpty();
			PlaceBlockTask place = new PlaceBlockTask(this.drifter, spot, stack -> stack.is(Items.TORCH), "a torch");
			return hasWall ? place.preferSupport(side) : place;
		}

		this.lastTorch = feet;
		return null;
	}

	/**
	 * The best spot to go next: one it has seen it can stand on, underground, with unseen blocks next to it, in a
	 * region it hasn't been to. Nearer is better, and deeper is better until the ore's depth.
	 */
	@Nullable
	private BlockPos frontier(BlockPos feet) {
		return findFrontier(this.drifter, feet, SEARCH_RADIUS, this.depth, this.visited, this.unreachable);
	}

	/** A frontier near {@code around} (see {@link #frontier}), or null. Used to find a cave to go to as well. */
	@Nullable
	public static BlockPos findFrontier(DrifterEntity drifter, BlockPos around, int radius, int depth, Set<Long> visited, Set<BlockPos> unreachable) {
		Level level = drifter.level();
		TerrainKnowledge terrain = drifter.getSenses().terrain();
		BlockPos[] best = {null};
		double[] bestScore = {Double.MAX_VALUE};
		terrain.forEachKnown(around, radius, pos -> {
			if (visited.contains(region(pos)) || unreachable.contains(pos) || !terrain.isKnown(pos.below())
				|| !KnownBlocks.isStandable(level, pos) || !underground(drifter, pos)) {
				return;
			}

			boolean edge = false;
			for (Direction direction : Direction.values()) {
				if (!terrain.isKnown(pos.relative(direction)) || !terrain.isKnown(pos.above().relative(direction))) {
					edge = true;
					break;
				}
			}

			if (!edge) {
				return;
			}

			double distance = Math.sqrt(pos.distSqr(around));
			double score = distance + Math.max(0, pos.getY() - depth) * 0.5;
			if (score < bestScore[0]) {
				bestScore[0] = score;
				best[0] = pos.immutable();
			}
		});
		return best[0];
	}

	/** Why no frontier (debug): standable seen spots, of those underground, of those at an edge, of those not visited. */
	private static String debugCounts(DrifterEntity drifter, BlockPos around, int radius, Set<Long> visited, Set<BlockPos> unreachable) {
		Level level = drifter.level();
		TerrainKnowledge terrain = drifter.getSenses().terrain();
		int[] counts = new int[5];
		terrain.forEachKnown(around, radius, pos -> {
			counts[0]++;
			if (!terrain.isKnown(pos.below()) || !KnownBlocks.isStandable(level, pos)) {
				return;
			}

			counts[1]++;
			if (!underground(drifter, pos)) {
				return;
			}

			counts[2]++;
			boolean edge = false;
			for (Direction direction : Direction.values()) {
				if (!terrain.isKnown(pos.relative(direction)) || !terrain.isKnown(pos.above().relative(direction))) {
					edge = true;
					break;
				}
			}

			if (edge) {
				counts[3]++;
				if (!visited.contains(region(pos)) && !unreachable.contains(pos)) {
					counts[4]++;
				}
			}
		});
		return String.format(java.util.Locale.ROOT, "(known %d, standable %d, underground %d, edge %d, new %d)", counts[0], counts[1], counts[2], counts[3], counts[4]);
	}

	/** Open blocks a hole must lead into to count as a cave. */
	private static final int MIN_CAVE_SPACE = 20;

	/**
	 * A real cave at this spot: underground, with enough open space joined to it, none of it open to the sky (a glance
	 * into the opening tells a player that much; found in testing: air under overhangs and pockets of a block or two).
	 */
	public static boolean isCave(DrifterEntity drifter, BlockPos pos) {
		return underground(drifter, pos) && openSpaceBehind(drifter.level(), pos) >= MIN_CAVE_SPACE;
	}

	/** Open (air, not water) blocks joined to this one, nearby, counted up to a little over what makes a cave. */
	private static int openSpaceBehind(net.minecraft.world.level.Level level, BlockPos start) {
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
		java.util.Set<BlockPos> seen = new java.util.HashSet<>();
		queue.add(start);
		seen.add(start);
		while (!queue.isEmpty() && seen.size() <= MIN_CAVE_SPACE * 2) {
			BlockPos pos = queue.poll();
			for (Direction direction : Direction.values()) {
				BlockPos next = pos.relative(direction);
				if (next.distSqr(start) <= 8 * 8 && !seen.contains(next) && level.getBlockState(next).getCollisionShape(level, next).isEmpty()
					&& level.getFluidState(next).isEmpty()) {
					// Open to the sky: air under an overhang or a slope by the surface, not a cave (found in testing).
					if (level.canSeeSky(next)) {
						return 0;
					}

					seen.add(next);
					queue.add(next);
				}
			}
		}

		return seen.size();
	}

	/** Ground over its head (not just leaves, and not its own hut's roof): somewhere a cave is. */
	public static boolean underground(DrifterEntity drifter, BlockPos pos) {
		Level level = drifter.level();
		if (level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) <= pos.getY() + COVER) {
			return false;
		}

		com.dinglebob.drifter.brain.Home home = drifter.getDrifterBrain().home();
		int hut = com.dinglebob.drifter.building.StarterHut.HALF + 1;
		return home == null || Math.abs(pos.getX() - home.center.getX()) > hut || Math.abs(pos.getZ() - home.center.getZ()) > hut
			|| Math.abs(pos.getY() - home.center.getY()) > 4;
	}

	/** A solid block (not leaves) over its head, for a spot to count as in a cave (found in testing: "a cave" under a tree; with 3 blocks of cover required, shallow caves had no spots at all). */
	private static final int COVER = 2;

	private static long region(BlockPos pos) {
		return BlockPos.asLong(Math.floorDiv(pos.getX(), REGION), Math.floorDiv(pos.getY(), REGION), Math.floorDiv(pos.getZ(), REGION));
	}
}
