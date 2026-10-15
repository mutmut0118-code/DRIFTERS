package com.dinglebob.drifter.brain.activity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.KnownWorld;
import com.dinglebob.drifter.brain.Planner;
import com.dinglebob.drifter.building.StarterHut;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.hands.Tools;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Building the starter hut (DESIGN.md section 11, tier 3, "Build order"): clear the inside, fill holes in the floor,
 * walls ring by ring standing inside (corners first), up a two-block pillar in the middle for the top two rings and the
 * roof, back down (digging the pillar out), the last roof block over its head, the door, then the chest, table, furnace
 * and a torch inside.
 *
 * <p>Every step is worked out from what's there now, so it can stop (to eat, for the night) and pick up where it left off.
 * Patient Drifters gather all the blocks first; impatient ones build until they run out and go for more (section 11
 * "Materials": both are true to life).
 */
public class BuildHutActivity extends Activity {
	private static final List<Item> MATERIALS = List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT);
	/** A few spare for the pillar and mistakes. */
	private static final int SPARE = 4;
	/** Wall layers it builds standing on the floor (the tops of the second are just above its eyes). */
	private static final int FLOOR_LAYERS = 2;

	/** Cells it couldn't place, and how many times; it gives a cell a second try from somewhere else. */
	private final java.util.Map<BlockPos, Integer> misses = new java.util.HashMap<>();
	/** Cells it has given up on for this visit (two misses, or a dig it already tried). */
	protected final Set<BlockPos> failed = new HashSet<>();
	/** Cells it has broken something out of to build there (once each). */
	private final Set<BlockPos> clearedOnce = new HashSet<>();
	@Nullable
	protected BlockPos placing;
	/** Missed once in this pass: skipped until the next pass of the same list. */
	private final Set<BlockPos> retryLater = new HashSet<>();
	private boolean started;
	private int pillarTries;
	private int walks;

	public BuildHutActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "build a hut at home";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 8;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		// A block it couldn't place from here is tried again from somewhere else; it gives up only when nothing is left.
		return !(step instanceof PlaceBlockTask);
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof PlaceBlockTask && this.placing != null) {
			if (status == Status.SUCCESS) {
				this.started = true;
				this.drifter.getSenses().placedBlock(this.placing);
			} else if (this.misses.merge(this.placing, 1, Integer::sum) >= 2) {
				this.failed.add(this.placing);
				if (((PlaceBlockTask)step).describe().contains("bed")) {
					Home home = this.drifter.getDrifterBrain().home();
					if (home != null) {
						home.bedFailedAt = this.drifter.level().getGameTime();
					}
				}

				this.think("couldn't place a block of the " + this.what() + " at " + this.placing.toShortString() + ": " + step.failureReason());
			} else {
				// One more try later, from wherever it stands then.
				this.retryLater.add(this.placing);
			}
		}

		this.placing = null;
	}

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		Home home = this.drifter.getDrifterBrain().home();
		if (home == null) {
			return this.fail("has no home");
		}

		StarterHut hut = this.prepare(level, home);
		if (hut == null) {
			return this.fail("has nowhere to build");
		}

		BlockPos feet = this.drifter.blockPosition();
		int floorY = hut.center.getY();

		// Blocks: patient ones gather the lot first, impatient ones start with what they have.
		int missing = hut.missingBlocks(level);
		int carried = this.carriedMaterial();
		boolean patient = this.drifter.getPersonality().get(Trait.PATIENCE) >= 0.5;
		if (missing > 0 && (carried == 0 || !this.started && patient && carried < missing + SPARE)) {
			if (feet.getY() > floorY && hut.isInside(feet.below())) {
				return this.begin(this.dig(feet.below()));
			}

			int have = 0;
			for (Item item : this.materialItems()) {
				int count = this.drifter.getInventory().count(stack -> stack.is(item));
				have += count;
			}

			Planner.Plan plan = Planner.plan(this.drifter, KnownWorld.scan(this.drifter), this.materialItems(), have + missing + SPARE - carried);
			if (!plan.feasible() || plan.next() == null) {
				return this.fail("can't work out where to get blocks for the " + this.what());
			}

			return this.begin(Goals.activityFor(this.drifter, plan.next()));
		}

		// A tree where the hut goes: cut it down first, trunk and leaves (found by the user: leaves in the way and it gave up;
		// in testing a trunk ended up as part of the wall and leaves as roof). Worked out once, before any leaves go (bare
		// logs don't look like a tree any more).
		if (this.treeToClear == null) {
			this.treeToClear = this.treeInTheWay(level, hut);
			if (!this.treeToClear.isEmpty()) {
				this.think("cutting down the tree where the " + this.what() + " goes");
			}
		}

		for (java.util.Iterator<BlockPos> it = this.treeToClear.iterator(); it.hasNext(); ) {
			BlockPos cell = it.next();
			BlockState state = level.getBlockState(cell);
			if (!(state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) || this.failed.contains(cell)) {
				it.remove();
				continue;
			}

			if (this.reach(cell)) {
				it.remove();
				return this.begin(this.dig(cell));
			}

			if (++this.walks > 12) {
				it.remove();
				continue;
			}

			return this.walkNear(cell);
		}

		// The inside has to be clear (a tree, a hill's bump, last night's shelter cover), checked from the ground.
		for (BlockPos cell : feet.getY() == floorY ? hut.inside() : List.<BlockPos>of()) {
			if (StarterHut.filled(level, cell) && !this.failed.contains(cell) && this.clearable(level, cell)) {
				if (this.reach(cell)) {
					this.failed.add(cell);
					return this.begin(this.dig(cell));
				}

				if (++this.walks > 6) {
					this.failed.add(cell);
					continue;
				}

				return this.walkNear(cell);
			}
		}

		boolean onFloor = feet.getY() == floorY;
		boolean inside = hut.isInside(feet);
		boolean onPillar = feet.getY() == floorY + hut.pillar() && feet.getX() == hut.center.getX() && feet.getZ() == hut.center.getZ();
		List<BlockPos> roof = hut.roof();
		List<BlockPos> roofButMiddle = roof.subList(0, roof.size() - 1);
		// Wall layers above the second (a house's third): their blocks go on the tops of the layer below, which is above its
		// eyes on the floor, so they go in from the pillar with the roof, before it.
		List<BlockPos> upper = new java.util.ArrayList<>();
		for (int dy = FLOOR_LAYERS; dy < hut.wallHeight; dy++) {
			upper.addAll(hut.wallLayer(dy));
		}

		upper.addAll(roofButMiddle);

		// Roof work left from the pillar: anything not placed yet, or (while up there) a miss it may still fix from there.
		boolean roofToDo = !this.roofPassDone && (this.anyMissing(level, upper)
			|| onPillar && upper.stream().anyMatch(cell -> !StarterHut.filled(level, cell) && !this.failed.contains(cell)));

		// Off the pillar (digging it out) unless it's still putting the roof on.
		if (feet.getY() > floorY && hut.isInside(feet.below()) && !(onPillar && roofToDo)) {
			return this.begin(this.dig(feet.below()));
		}

		// Holes deeper than one block under the floor get filled from the bottom up first (found in testing: a corner over a
		// dip by the water had nothing to place the floor against).
		Status deep = this.placeFirstMissing(level, this.foundation(level, hut), onFloor, false);
		if (deep != null) {
			return deep;
		}

		// The floor and the walls, from the floor (moving to a better spot for any it can't place from where it is).
		Status low = this.placeFirstMissing(level, hut.floor(), onFloor, false);
		if (low != null) {
			return low;
		}

		for (int dy = 0; dy < Math.min(FLOOR_LAYERS, hut.wallHeight); dy++) {
			Status ring = this.placeFirstMissing(level, hut.wallLayer(dy), onFloor, false);
			if (ring != null) {
				return ring;
			}
		}

		// The roof, from a one-block pillar in the middle: the wall tops are just below its eyes there. Once per visit: what it
		// can't place from up there it tries from the floor (found by the user in testing: it pillared up, climbed down
		// breaking the pillar, and pillared up again, over and over, for roof blocks it couldn't reach from either).
		if (roofToDo) {
			if (!onPillar) {
				if (!inside || feet.getX() != hut.center.getX() || feet.getZ() != hut.center.getZ()) {
					return this.walkInside(hut);
				}

				// Its own roof block over the middle in the way (closed too soon on an earlier visit): out with it.
				BlockPos middleRoof = hut.center.above(hut.wallHeight);
				if (StarterHut.filled(level, middleRoof) && this.reach(middleRoof)) {
					return this.begin(this.dig(middleRoof));
				}

				// Dead centre first: off-centre, the pillar goes up in the wrong place and its body blocks the roof next to it.
				Vec3 middle = Vec3.atBottomCenterOf(hut.center);
				if (Math.abs(this.drifter.getX() - middle.x) > 0.15 || Math.abs(this.drifter.getZ() - middle.z) > 0.15) {
					return this.begin(new com.dinglebob.drifter.executor.task.CenterOnBlockTask(this.drifter, middle));
				}

				if (++this.pillarTries > 4) {
					return this.fail("can't get up to put the roof on");
				}

				return this.begin(new com.dinglebob.drifter.executor.task.PillarUpTask(this.drifter, hut.pillar()));
			}

			// An outer roof block it can't see the wall top for, because an inner roof block is already in the way (an
			// earlier visit, a repair): a player knocks the inner one out, places the outer one, and puts the inner one back.
			for (BlockPos outer : roofButMiddle) {
				boolean missed = this.retryLater.contains(outer) || this.failed.contains(outer);
				if (!missed || StarterHut.filled(level, outer) || Math.max(Math.abs(outer.getX() - hut.center.getX()),
					Math.abs(outer.getZ() - hut.center.getZ())) < hut.half) {
					continue;
				}

				// The roof blocks between its eyes and the wall top under it: the nearest one it can get at first (the diagonal
				// one can itself be hidden behind the two beside it, so those come out first). They go back in afterwards.
				List<BlockPos> inners = new java.util.ArrayList<>();
				// (Roof blocks beside it on the edge too: with both in, a corner's wall top shows only along the exact diagonal.)
				for (BlockPos inner : roofButMiddle) {
					if (!inner.equals(outer)) {
						inners.add(inner);
					}
				}

				inners.sort(java.util.Comparator.comparingDouble(inner -> inner.distSqr(outer)));
				for (BlockPos inner : inners) {
					if (inner.distSqr(outer) <= 5 && StarterHut.filled(level, inner) && !this.unblocked.contains(inner) && this.reach(inner)) {
						this.unblocked.add(inner);
						this.failed.remove(outer);
						this.misses.remove(outer);
						this.retryLater.remove(outer);
						return this.begin(this.dig(inner));
					}
				}
			}

			Status top = this.placeFirstMissing(level, upper, true, true);
			if (top != null) {
				return top;
			}

			this.roofPassDone = true;
		}

		// Back on the floor: the block over its head, once everything else is in (found in testing: closed too soon, it
		// couldn't pillar up again for the blocks it had missed).
		this.retryLater.clear();
		// Roof blocks it missed from the pillar: from the floor, against the ones already up (walking to a better spot if needed).
		Status missedRoof = this.placeFirstMissing(level, upper, onFloor && inside, false);
		if (missedRoof != null) {
			return missedRoof;
		}

		if (!this.allFilled(level, upper) || !this.allFilled(level, hut.floor())) {
			// Not leaving a hole over its head: the middle roof block goes back in before it gives up for now.
			if (!this.middleTried) {
				this.middleTried = true;
				Status middle = this.placeFirstMissing(level, roof.subList(roof.size() - 1, roof.size()), onFloor && inside, true);
				if (middle != null) {
					return middle;
				}
			}

			if (!this.acceptableGaps(level, hut)) {
				return this.giveUpForNow();
			}
		}

		for (int dy = 0; dy < hut.wallHeight; dy++) {
			if (!this.allFilled(level, hut.wallLayer(dy)) && !this.acceptableGaps(level, hut)) {
				return this.giveUpForNow();
			}
		}

		Status lastRoof = this.placeFirstMissing(level, roof.subList(roof.size() - 1, roof.size()), onFloor && inside, true);
		if (lastRoof != null) {
			return lastRoof;
		}

		// The door.
		BlockPos door = hut.doorPos();
		if (!(level.getBlockState(door).getBlock() instanceof DoorBlock)) {
			if (this.drifter.getInventory().count(stack -> stack.is(ItemTags.WOODEN_DOORS)) == 0) {
				Planner.Plan plan = Planner.plan(this.drifter, KnownWorld.scan(this.drifter), doors(), 1);
				if (!plan.feasible() || plan.next() == null) {
					return this.fail("can't work out how to make a door");
				}

				return this.begin(Goals.activityFor(this.drifter, plan.next()));
			}

			if (!this.failed.contains(door)) {
				if (!inside || !onFloor) {
					return this.walkInside(hut);
				}

				this.placing = door;
				return this.begin(new PlaceBlockTask(this.drifter, door, stack -> stack.is(ItemTags.WOODEN_DOORS), "a door"));
			}
		}

		// Inside: a table, a furnace and a torch, if it carries them.
		Status furnish = this.furnish(level, hut, inside && onFloor);
		if (furnish != null) {
			return furnish;
		}

		if (hut.shellBuilt(level)) {
			this.finished(home);
			return Status.SUCCESS;
		}

		if (this.acceptableGaps(level, hut)) {
			this.think("left " + hut.missingBlocks(level) + " block(s) of the " + this.what() + " it can't reach");
			this.finished(home);
			return Status.SUCCESS;
		}

		return this.fail("couldn't finish the " + this.what());
	}

	/**
	 * Places the first missing cell of the list it hasn't given up on, walking inside first if needed. A cell it missed
	 * once gets a second try from a spot where it can see a face to click (unless {@code fixedSpot}: up on the pillar it
	 * can't move). Null when none are left.
	 */
	@Nullable
	private boolean middleTried;
	/** It has done what it can from the pillar this visit. */
	private boolean roofPassDone;
	/** Inner roof blocks it has knocked out once already to see past them. */
	private final java.util.Set<BlockPos> unblocked = new java.util.HashSet<>();

	@Nullable
	private List<BlockPos> treeToClear;

	/** Leaves and tree logs in the hut's space (walls, roof, inside), plus the rest of each trunk above, lowest first. */
	private List<BlockPos> treeInTheWay(Level level, StarterHut hut) {
		com.dinglebob.drifter.brain.TreeLogs trees = new com.dinglebob.drifter.brain.TreeLogs(level);
		java.util.Set<BlockPos> cells = new java.util.HashSet<>(hut.inside());
		for (int dy = 0; dy < hut.wallHeight; dy++) {
			cells.addAll(hut.wallLayer(dy));
		}

		cells.addAll(hut.roof());
		java.util.Set<BlockPos> found = new java.util.LinkedHashSet<>();
		for (BlockPos cell : cells) {
			BlockState state = level.getBlockState(cell);
			if (!KnownBlocks.mayTake(level, cell)) {
				continue;
			}

			if (state.is(BlockTags.LEAVES)) {
				found.add(cell.immutable());
			} else if (state.is(BlockTags.LOGS) && trees.check(cell)) {
				for (BlockPos log = cell; level.getBlockState(log).is(BlockTags.LOGS) && log.getY() <= cell.getY() + 10; log = log.above()) {
					found.add(log.immutable());
				}
			}
		}

		List<BlockPos> sorted = new java.util.ArrayList<>(found);
		sorted.sort(java.util.Comparator.comparingInt(BlockPos::getY));
		return sorted;
	}

	/** Most blocks it fills under one floor cell. */
	private static final int FOUNDATION_DEPTH = 3;

	/** Air or water under missing floor blocks, down to solid ground (a few blocks at most), lowest first. */
	private List<BlockPos> foundation(Level level, StarterHut hut) {
		List<BlockPos> cells = new java.util.ArrayList<>();
		for (BlockPos floor : hut.floor()) {
			if (StarterHut.filled(level, floor)) {
				continue;
			}

			List<BlockPos> column = new java.util.ArrayList<>();
			BlockPos below = floor.below();
			while (column.size() < FOUNDATION_DEPTH && !StarterHut.filled(level, below)) {
				column.add(below);
				below = below.below();
			}

			if (StarterHut.filled(level, below)) {
				cells.addAll(column);
			}
		}

		cells.sort(java.util.Comparator.comparingInt(BlockPos::getY));
		return cells;
	}

	private Status placeFirstMissing(Level level, List<BlockPos> cells, boolean inPosition, boolean fixedSpot) {
		Home home = this.drifter.getDrifterBrain().home();
		StarterHut hut = this.blueprint(home);
		for (BlockPos cell : cells) {
			if (StarterHut.filled(level, cell) || this.failed.contains(cell)) {
				continue;
			}

			// Something in the way that a block can't go over (sugar cane, a sapling, a torch): broken first, as a player does
			// (found in testing: "that spot is already taken", sixteen times over).
			BlockState there = level.getBlockState(cell);
			if (!there.isAir() && !there.canBeReplaced() && this.clearable(level, cell) && this.reach(cell) && this.clearedOnce.add(cell)) {
				return this.begin(this.dig(cell));
			}

			if (this.retryLater.contains(cell)) {
				if (fixedSpot) {
					continue;
				}

				BlockPos spot = this.placementSpot(level, cell);
				if (spot == null) {
					this.failed.add(cell);
					this.think("couldn't find anywhere to place a block of the " + this.what() + " at " + cell.toShortString() + " from");
					continue;
				}

				if (!this.drifter.blockPosition().equals(spot)) {
					if (++this.walks > 24) {
						return this.fail("walked around too much");
					}

					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.3, "move to see where the block goes"));
				}

				this.retryLater.remove(cell);
				this.placing = cell;
				return this.begin(new PlaceBlockTask(this.drifter, cell, this.materialFor(cell, hut), "a block of the " + this.what()));
			}

			if (!inPosition) {
				return this.walkInside(hut);
			}

			if (this.drifter.getInventory().count(this.materialFor(cell, hut)) == 0) {
				// Out of blocks: the next pass goes for more.
				return Status.RUNNING;
			}

			this.placing = cell;
			return this.begin(new PlaceBlockTask(this.drifter, cell, this.materialFor(cell, hut), "a block of the " + this.what()));
		}

		return null;
	}

	private boolean allFilled(Level level, List<BlockPos> cells) {
		return cells.stream().allMatch(cell -> StarterHut.filled(level, cell));
	}

	/**
	 * A spot it has seen to stand on (inside or outside the hut) from which it could see and reach a face of a block next
	 * to this cell, to click and place against: the nearest to where it is now. Null if none.
	 */
	@Nullable
	private BlockPos placementSpot(Level level, BlockPos cell) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-4, -2, -4), cell.offset(4, 1, 4))) {
			if (pos.getX() == cell.getX() && pos.getZ() == cell.getZ() || !this.drifter.getSenses().terrain().isKnown(pos.below())
				|| !KnownBlocks.isStandable(level, pos)) {
				continue;
			}

			Vec3 eye = new Vec3(pos.getX() + 0.5, pos.getY() + net.minecraft.world.entity.player.Player.DEFAULT_EYE_HEIGHT, pos.getZ() + 0.5);
			if (eye.distanceTo(Vec3.atCenterOf(cell)) > this.drifter.blockInteractionRange()) {
				continue;
			}

			boolean sees = com.dinglebob.drifter.executor.task.PlaceBlockTask.clickableFace(this.drifter, eye, cell) != null;
			for (net.minecraft.core.Direction side : sees ? new net.minecraft.core.Direction[0] : net.minecraft.core.Direction.values()) {
				BlockPos support = cell.relative(side);
				BlockState state = level.getBlockState(support);
				if (!state.isAir() && !state.canBeReplaced()
					&& com.dinglebob.drifter.hands.Reach.aimPointFrom(this.drifter, eye, support, side.getOpposite()).isPresent()) {
					sees = true;
					break;
				}
			}

			double distance = pos.distSqr(this.drifter.blockPosition());
			if (sees && distance < bestDistance) {
				best = pos.immutable();
				bestDistance = distance;
			}
		}

		return best;
	}

	private boolean anyMissing(Level level, List<BlockPos> cells) {
		return cells.stream().anyMatch(cell -> !StarterHut.filled(level, cell) && !this.failed.contains(cell) && !this.retryLater.contains(cell));
	}

	// ---------------------------------------------------------------------------------------------
	// What it builds: the hut here; a house overrides these (BuildHouseActivity)
	// ---------------------------------------------------------------------------------------------

	/** Works out the blueprint (choosing the door side the first time), or null if there's nowhere to build. */
	@Nullable
	protected StarterHut prepare(Level level, Home home) {
		if (home.hutDoor == null) {
			home.hutDoor = StarterHut.chooseDoor(level, home.center);
			this.think("building a hut at home, door on the " + home.hutDoor.getName() + " side");
		}

		return this.blueprint(home);
	}

	protected StarterHut blueprint(Home home) {
		return StarterHut.of(home);
	}

	/** What the planner gathers when it's short of blocks. */
	protected List<Item> materialItems() {
		return MATERIALS;
	}

	/** Blocks it carries that it can build with. */
	protected int carriedMaterial() {
		return this.drifter.getInventory().count(Tools.BUILDING_BLOCK);
	}

	/** The block for this cell. */
	protected Predicate<ItemStack> materialFor(BlockPos cell, StarterHut hut) {
		return this.material();
	}

	protected void finished(Home home) {
		home.hutBuilt = true;
		this.think("finished the hut");
	}

	/** "hut" or "house", for its thoughts. */
	protected String what() {
		return "hut";
	}

	/** Stone if it has it, then planks, then whatever (dirt): the patchy look of a real first house. */
	private Predicate<ItemStack> material() {
		Predicate<ItemStack> stone = stack -> Tools.BUILDING_BLOCK.test(stack) && !stack.is(ItemTags.PLANKS) && !stack.is(Items.DIRT)
			&& !stack.is(Items.COARSE_DIRT);
		if (this.drifter.getInventory().count(stone) > 0) {
			return stone;
		}

		Predicate<ItemStack> planks = stack -> stack.is(ItemTags.PLANKS);
		return this.drifter.getInventory().count(planks) > 0 ? planks : Tools.BUILDING_BLOCK;
	}

	/**
	 * Standing where the bed goes (its body would be in the way): over to another free spot inside first (found in
	 * testing: "it's standing in the way", again and again).
	 */
	@Nullable
	protected Status stepAside(StarterHut hut, StarterHut.BedSpot bed) {
		BlockPos head = bed.foot().relative(bed.facing());
		net.minecraft.world.phys.AABB body = this.drifter.getBoundingBox();
		if (!body.intersects(new net.minecraft.world.phys.AABB(bed.foot())) && !body.intersects(new net.minecraft.world.phys.AABB(head))) {
			return null;
		}

		Level level = this.drifter.level();
		for (BlockPos cell : hut.inside()) {
			if (cell.getY() == hut.center.getY() && !cell.equals(bed.foot()) && !cell.equals(head) && StarterHut.free(level, cell)
				&& StarterHut.free(level, cell.above()) && !cell.closerThan(bed.foot(), 1.5) && !cell.closerThan(head, 1.5)
				&& ++this.asides <= 3) {
				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(cell), 0.2, "step out of the bed's way"));
			}
		}

		return null;
	}

	private int asides;

	protected Status walkInside(StarterHut hut) {
		if (++this.pillarTries > 6) {
			return this.fail("can't get inside the " + this.what());
		}

		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(hut.center), 0.3, "step inside the " + this.what()));
	}

	protected boolean reach(BlockPos cell) {
		return com.dinglebob.drifter.hands.Reach.canReach(this.drifter, cell);
	}

	private Status walkNear(BlockPos cell) {
		BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, cell, Set.of());
		if (spot == null) {
			return Status.RUNNING;
		}

		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk over to clear the spot"));
	}

	/** Most blocks it leaves missing when it truly can't place them (a corner it can't reach from anywhere). */
	private static final int MAX_GAPS = 4;

	/**
	 * Blocks still missing are only a few it has tried and can't place from anywhere it can stand: the shell counts as done
	 * as it is (found in testing: two corner blocks of a house on a hillside failed it 39 visits running).
	 */
	/** A failed visit with blocks still missing: counted, so after a few it settles for the gaps it can't fill. */
	private Status giveUpForNow() {
		com.dinglebob.drifter.brain.Home home = this.drifter.getDrifterBrain().home();
		if (home != null) {
			home.gapVisits.merge(this.what(), 1, Integer::sum);
		}

		return this.fail("couldn't place every block of the " + this.what() + " yet");
	}

	private boolean acceptableGaps(Level level, StarterHut hut) {
		int missing = hut.missingBlocks(level);
		com.dinglebob.drifter.brain.Home home = this.drifter.getDrifterBrain().home();
		// (Found in testing: six floor blocks of a house over a dip, unreachable from anywhere, failed it 72 visits running.)
		int allowed = home != null ? home.allowedGaps(this.what()) : MAX_GAPS;
		if (missing > allowed) {
			return false;
		}

		java.util.List<BlockPos> cells = new java.util.ArrayList<>(hut.floor());
		cells.addAll(hut.roof());
		for (int dy = 0; dy < hut.wallHeight; dy++) {
			cells.addAll(hut.wallLayer(dy));
		}

		for (BlockPos cell : cells) {
			if (!StarterHut.filled(level, cell) && !this.failed.contains(cell)) {
				return false;
			}
		}

		return true;
	}

	/** Natural blocks in the way it may dig (not its chest, table or furnace, not someone's build). */
	private boolean clearable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return KnownBlocks.mayTake(level, pos) && state.getDestroySpeed(level, pos) >= 0.0F && !state.is(BlockTags.BEDS);
	}

	@Nullable
	protected Status furnish(Level level, StarterHut hut, boolean inPosition) {
		for (Item station : List.of(Items.CRAFTING_TABLE, Items.FURNACE)) {
			if (this.drifter.getInventory().count(stack -> stack.is(station)) == 0 || this.hasInside(level, hut, station)) {
				continue;
			}

			for (BlockPos spot : hut.furnitureSpots()) {
				if (level.getBlockState(spot).canBeReplaced() && !this.failed.contains(spot)) {
					if (!inPosition) {
						return this.walkInside(hut);
					}

					this.placing = spot;
					return this.begin(new PlaceBlockTask(this.drifter, spot, stack -> stack.is(station),
						station.getDescription().getString().toLowerCase(java.util.Locale.ROOT)));
				}
			}
		}

		// The bed along the right wall, head toward the back (it faces the way it's placed).
		StarterHut.BedSpot bed = hut.bed(level) == null && this.drifter.getInventory().count(stack -> stack.is(ItemTags.BEDS)) > 0
			? hut.bedSpot(level) : null;
		if (bed != null && !this.failed.contains(bed.foot())) {
			if (!inPosition) {
				return this.walkInside(hut);
			}

			Status aside = this.stepAside(hut, bed);
			if (aside != null) {
				return aside;
			}

			this.placing = bed.foot();
			return this.begin(new PlaceBlockTask(this.drifter, bed.foot(), stack -> stack.is(ItemTags.BEDS), "a bed").facing(bed.facing()));
		}

		BlockPos torch = hut.torchSpot();
		if (this.drifter.getInventory().count(stack -> stack.is(Items.TORCH)) > 0 && level.getBlockState(torch).isAir() && !this.failed.contains(torch)) {
			if (!inPosition) {
				return this.walkInside(hut);
			}

			this.placing = torch;
			return this.begin(new PlaceBlockTask(this.drifter, torch, stack -> stack.is(Items.TORCH), "a torch").preferSupport(hut.door.getOpposite()));
		}

		return null;
	}

	private boolean hasInside(Level level, StarterHut hut, Item station) {
		for (BlockPos spot : hut.furnitureSpots()) {
			if (level.getBlockState(spot).getBlock().asItem() == station) {
				return true;
			}
		}

		return false;
	}

	private static List<Item> doors() {
		return List.of(Items.OAK_DOOR, Items.SPRUCE_DOOR, Items.BIRCH_DOOR, Items.JUNGLE_DOOR, Items.ACACIA_DOOR, Items.DARK_OAK_DOOR,
			Items.MANGROVE_DOOR, Items.CHERRY_DOOR, Items.BAMBOO_DOOR);
	}
}
