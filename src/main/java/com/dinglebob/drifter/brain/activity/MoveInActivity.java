package com.dinglebob.drifter.brain.activity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownWorld;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Planner;
import com.dinglebob.drifter.building.StarterHut;
import com.dinglebob.drifter.building.plan.HousePlan;
import com.dinglebob.drifter.building.plan.HousePlans;
import com.dinglebob.drifter.building.plan.PlacedPlan;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Moving out of the hut (the user, 2026-10-21: "make sure their hut is mined when they make their house and they can just
 * move their stuff to the house"). Once it sleeps in the house it built, it puts a chest in the house for each one in the
 * hut, carries the hut chests' things over a load at a time, breaks the empty hut chests (and takes them along), then takes
 * the hut down, roof first, and picks up what comes off it. The floor stays (it was the ground).
 */
public class MoveInActivity extends Activity {
	/** Times it goes for a chest before it gives up for now. */
	private static final int MOST_FETCHES = 3;

	/** What it took out of a hut chest and hasn't put in the house yet: its stacks, and how many of each came from the chest. */
	private final Map<ItemStack, Integer> carrying = new IdentityHashMap<>();
	/** Its pockets just before it opened a hut chest (to tell what came out). */
	@Nullable
	private ItemStack[] before;
	@Nullable
	private BlockPos placing;
	@Nullable
	private BlockPos breaking;
	private int fetches;
	private boolean collected;

	public MoveInActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "move its things into the house";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 10;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		// (A walk or a dig that went wrong is counted per block.)
		return false;
	}

	/** Reasons it has already said a step went wrong (said once each). */
	private final java.util.Set<String> told = new java.util.HashSet<>();

	@Override
	protected void stepDone(Task step, Status status) {
		Level level = this.drifter.level();
		Home home = this.drifter.getDrifterBrain().home();
		if (home == null) {
			return;
		}

		if (status == Status.FAILURE && this.told.size() < 10 && this.told.add(step.describe() + ": " + step.failureReason())) {
			this.think("couldn't " + step.describe() + ": " + step.failureReason());
		}

		// A chest that wouldn't go down there: somewhere else next time.
		if (this.placing != null && step instanceof PlaceBlockTask && status == Status.FAILURE) {
			home.badChestSpots.add(this.placing);
		}

		if (this.placing != null && step instanceof PlaceBlockTask) {
			if (level.getBlockState(this.placing).is(Blocks.CHEST)) {
				home.addChest(this.placing);
			}

			this.placing = null;
		}

		if (this.before != null && step instanceof ContainerTask) {
			// What came out of the hut chest: each stack that grew, by how much.
			DrifterInventory inventory = this.drifter.getInventory();
			for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
				ItemStack now = inventory.getItem(slot);
				ItemStack was = this.before[slot];
				int grew = now.isEmpty() ? 0 : was.isEmpty() || !ItemStack.isSameItemSameComponents(now, was) ? now.getCount() : now.getCount() - was.getCount();
				if (grew > 0) {
					this.carrying.merge(now, grew, Integer::sum);
				}
			}

			this.before = null;
		}

		if (this.breaking != null) {
			if (!level.getBlockState(this.breaking).is(Blocks.CHEST)) {
				home.removeChest(this.breaking);
			}

			this.breaking = null;
		}
	}

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		Home home = this.drifter.getDrifterBrain().home();
		if (home == null || !home.hutBuilt) {
			return this.fail("no hut to move out of");
		}

		StarterHut hut = StarterHut.of(home);
		BoundingBox box = hut.box();
		List<BlockPos> hutChests = new ArrayList<>();
		List<BlockPos> houseChests = new ArrayList<>();
		for (BlockPos chest : home.chests()) {
			if (!(level.getBlockEntity(chest) instanceof Container)) {
				home.removeChest(chest);
			} else if (box.isInside(chest)) {
				hutChests.add(chest);
			} else {
				houseChests.add(chest);
			}
		}

		// 1. What it carries from the hut goes into the house's chests.
		this.carrying.entrySet().removeIf(entry -> entry.getKey().isEmpty());
		if (!this.carrying.isEmpty() && !houseChests.isEmpty()) {
			BlockPos to = houseChests.get(0);
			if (!Reach.canReach(this.drifter, to)) {
				return this.walkTo(to, "carry its things to the house");
			}

			Map<ItemStack, Integer> load = new HashMap<>();
			this.carrying.forEach(load::put);
			this.carrying.clear();
			return this.begin(ContainerTask.deposit(this.drifter, to, load, "put its things in the house chest"));
		}

		// 2. A chest in the house for each one in the hut.
		if (!hutChests.isEmpty() && houseChests.size() < hutChests.size()) {
			if (this.drifter.getInventory().count(stack -> stack.is(Items.CHEST)) == 0) {
				if (++this.fetches > MOST_FETCHES) {
					return this.fail("couldn't get a chest for the house; another go later");
				}

				Planner.Plan plan = Planner.plan(this.drifter, KnownWorld.scan(this.drifter), Items.CHEST, 1);
				if (!plan.feasible() || plan.next() == null) {
					return this.fail("can't work out how to get a chest for the house");
				}

				return this.begin(Goals.activityFor(this.drifter, plan.next()));
			}

			// Its house's layout first (a player's furnishing: the storage wall, then the rest), placed from in front so it faces
			// the room (a chest faces whoever puts it down; found by the user: chests facing the corner "look backwards").
			com.dinglebob.drifter.building.Furnishing.Spot laid = laidOut(level, home, this.drifter.getServer(), Blocks.CHEST);
			BlockPos spot = laid != null ? laid.pos() : chestSpot(level, home, this.drifter.getServer());
			if (spot == null) {
				return this.fail("no room for a chest in the house");
			}

			if (laid != null && !this.drifter.blockPosition().equals(spot.relative(laid.facing()))) {
				BlockPos front = spot.relative(laid.facing());
				if (this.walks.merge(front, 1, Integer::sum) > 4) {
					home.badChestSpots.add(spot);
					return this.fail("couldn't get in front of " + spot.toShortString() + " to put a chest there; another go later");
				}

				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(front), 0.4, "take a chest into the house", NavigateTask.Pace.STROLL));
			}

			if (!Reach.canReach(this.drifter, spot.below()) || this.drifter.blockPosition().equals(spot)) {
				return this.walkTo(spot.below(), "take a chest into the house", spot);
			}

			this.placing = spot;
			return this.begin(new PlaceBlockTask(this.drifter, spot, stack -> stack.is(Items.CHEST), "put a chest in the house"));
		}

		// 3. The hut chests emptied, a load at a time, and broken once empty (the chest comes along).
		for (BlockPos chest : hutChests) {
			if (!(level.getBlockEntity(chest) instanceof Container container)) {
				continue;
			}

			if (!container.isEmpty()) {
				// A load at a time, keeping the few free slots it always keeps (a full load and it would start throwing things out).
				int stacks = com.dinglebob.drifter.brain.Storage.freeSlots(this.drifter) - com.dinglebob.drifter.brain.Storage.FULL_FREE_SLOTS;
				if (stacks <= 0) {
					// Its pockets are full of its own things: some go in the house chest first.
					return houseChests.isEmpty() ? this.fail("its pockets are full") : this.storeOwn(houseChests.get(0));
				}

				int load = 0;
				for (int slot = 0, taken = 0; slot < container.getContainerSize() && taken < stacks; slot++) {
					ItemStack stack = container.getItem(slot);
					if (!stack.isEmpty()) {
						load += stack.getCount();
						taken++;
					}
				}

				if (!Reach.canReach(this.drifter, chest)) {
					return this.walkTo(chest, "go to the hut chest");
				}

				this.before = new ItemStack[DrifterInventory.MAIN_SIZE];
				for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
					this.before[slot] = this.drifter.getInventory().getItem(slot).copy();
				}

				return this.begin(new ContainerTask(this.drifter, chest, ContainerTask.Mode.TAKE, stack -> true, "empty the hut chest", Math.max(1, load), null));
			}

			if (!Reach.canReach(this.drifter, chest)) {
				return this.walkTo(chest, "go to the hut chest");
			}

			this.breaking = chest;
			return this.begin(this.dig(chest));
		}

		// 4. The hut taken down: the roof, then the walls from the top, the door and the windows; its furniture too.
		BlockPos next = this.nextToTakeDown(level, hut);
		if (next != null) {
			if (!Reach.canReach(this.drifter, next)) {
				// (Out of reach from anywhere it can get to: left.)
				if (this.walks.getOrDefault(next, 0) >= 4) {
					this.skip.add(next);
					return Status.RUNNING;
				}

				return this.walkTo(next, "take the hut down");
			}

			return this.begin(this.dig(next));
		}

		// 5. What came off it, picked up.
		if (!this.collected) {
			this.collected = true;
			return this.begin(new CollectItemsTask(this.drifter, 8.0));
		}

		home.hutTakenDown = true;
		this.think("took the old hut down and moved its things into the house");
		return Status.SUCCESS;
	}

	/** Blocks it couldn't get at. */
	private final java.util.Set<BlockPos> skip = new java.util.HashSet<>();

	/** The hut's next block to dig: its roof first, then the walls from the top down, the door, the windows, its furniture. */
	@Nullable
	private BlockPos nextToTakeDown(Level level, StarterHut hut) {
		List<BlockPos> cells = new ArrayList<>(hut.roof());
		for (int dy = hut.wallHeight - 1; dy >= 0; dy--) {
			cells.addAll(hut.wallLayer(dy));
		}

		cells.add(hut.doorPos().above());
		cells.add(hut.doorPos());
		cells.addAll(hut.windowCells());
		cells.addAll(hut.inside());
		for (BlockPos cell : cells) {
			BlockState state = level.getBlockState(cell);
			if (this.skip.contains(cell) || state.isAir() || !state.getFluidState().isEmpty() || state.is(Blocks.BEDROCK) || state.is(Blocks.CHEST)
				|| state.canBeReplaced() && !(state.getBlock() instanceof DoorBlock) && !state.is(Blocks.TORCH) && !state.is(Blocks.WALL_TORCH)
				|| com.dinglebob.drifter.world.PlayerPlacedBlocks.isPlayerPlaced(level, cell)) {
				continue;
			}

			// (The lower half of a door comes with the upper; a bed's other half with this one.)
			if (state.getBlock() instanceof DoorBlock && cells.contains(cell.above()) && level.getBlockState(cell.above()).getBlock() instanceof DoorBlock) {
				continue;
			}

			return cell.immutable();
		}

		return null;
	}

	/** Walks to where it can reach {@code target} from (never onto {@code keepClear}); gives up on it after a few goes. */
	private Status walkTo(BlockPos target, String what) {
		return this.walkTo(target, what, null);
	}

	private Status walkTo(BlockPos target, String what, @Nullable BlockPos keepClear) {
		if (this.walks.merge(target, 1, Integer::sum) > 4) {
			this.skip.add(target);
			return this.fail("couldn't get to " + target.toShortString() + " to " + what + "; another go later");
		}

		BlockPos stand = com.dinglebob.drifter.brain.KnownBlocks.standingSpotFor(this.drifter, target,
			keepClear == null ? java.util.Set.of() : java.util.Set.of(keepClear));
		return stand == null ? this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(target), 2.0, what, NavigateTask.Pace.STROLL))
			: this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(stand), 0.5, what, NavigateTask.Pace.STROLL));
	}

	/** Walks per target so far. */
	private final Map<BlockPos, Integer> walks = new HashMap<>();

	/** Some of its own spare things into the house chest (what it would put away anyway), to make room. */
	private Status storeOwn(BlockPos chest) {
		if (!Reach.canReach(this.drifter, chest)) {
			return this.walkTo(chest, "make room in its pockets");
		}

		Map<ItemStack, Integer> load = new HashMap<>();
		com.dinglebob.drifter.brain.Storage.toDeposit(this.drifter).forEach((slot, count) -> load.put(this.drifter.getInventory().getItem(slot), count));
		return load.isEmpty() ? this.fail("its pockets are full") : this.begin(ContainerTask.deposit(this.drifter, chest, load, "make room in its pockets"));
	}

	// ---------------------------------------------------------------------------------------------
	// A chest's place in the house
	// ---------------------------------------------------------------------------------------------

	/**
	 * Where a chest goes in its house: in the house it built from a plan, a free floor cell inside, against a wall, away from
	 * the door and its bed (the ground floor first); in a house of the older kind, its chest corner. Null if none.
	 */
	@Nullable
	public static com.dinglebob.drifter.building.Furnishing.Spot laidOut(Level level, Home home, @Nullable net.minecraft.server.MinecraftServer server,
		net.minecraft.world.level.block.Block block) {
		if (server == null || home.planId == null || home.planOrigin == null || !home.planDone) {
			return null;
		}

		HousePlan plan = HousePlans.get(server, home.planId);
		if (plan == null) {
			return null;
		}

		com.dinglebob.drifter.building.Furnishing.Spot spot = com.dinglebob.drifter.building.Furnishing.next(level, PlacedPlan.of(plan, home), home.planBed,
			block, false);
		return spot == null || home.badChestSpots.contains(spot.pos()) ? null : spot;
	}

	/** The next free place in its house's furnished layout for this (the user's furnishing guidebook), or null. */
	@Nullable
	public static BlockPos chestSpot(Level level, Home home, @Nullable net.minecraft.server.MinecraftServer server) {
		if (home.houseBuilt && home.house() != null) {
			for (BlockPos spot : home.house().chestSpots()) {
				if (level.getBlockState(spot).canBeReplaced() && level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)
					&& !home.isChest(spot)) {
					return spot;
				}
			}

			return null;
		}

		HousePlan plan = server == null || home.planId == null || home.planOrigin == null ? null : HousePlans.get(server, home.planId);
		if (plan == null) {
			return null;
		}

		PlacedPlan placed = PlacedPlan.of(plan, home);
		Map<BlockPos, BlockState> want = placed.buildMap();
		List<BlockPos> doors = placed.build.stream().filter(c -> c.state().getBlock() instanceof DoorBlock).map(PlacedPlan.Cell::pos).toList();
		BlockPos bed = home.planBed;
		BlockPos best = null;
		double bestScore = -Double.MAX_VALUE;
		for (BlockPos column : placed.footprint()) {
			for (int dy = 0; dy < placed.sizeY - 1; dy++) {
				BlockPos pos = column.above(dy);
				if (!BuildPlanActivity.roomInside(level, placed, want, pos) || home.isChest(pos) || home.badChestSpots.contains(pos)
					|| bed != null && (bed.closerThan(pos, 1.5) || level.getBlockState(pos).getBlock() instanceof BedBlock)) {
					continue;
				}

				double toDoor = doors.stream().mapToDouble(d -> d.distSqr(pos)).min().orElse(100.0);
				if (toDoor <= 4.0) {
					continue;
				}

				boolean againstWall = false;
				for (Direction side : Direction.Plane.HORIZONTAL) {
					BlockPos beside = pos.relative(side);
					againstWall |= want.containsKey(beside) && !want.get(beside).getCollisionShape(level, beside).isEmpty();
				}

				double score = (againstWall ? 6.0 : 0.0) + Math.min(4.0, Math.sqrt(toDoor)) - (pos.getY() - placed.origin.getY()) * 2.0
					+ home.chests().stream().filter(c -> c.closerThan(pos, 2.5)).count() * 1.5;
				if (score > bestScore) {
					bestScore = score;
					best = pos.immutable();
				}
			}
		}

		return best;
	}
}
