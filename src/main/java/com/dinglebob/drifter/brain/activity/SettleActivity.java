package com.dinglebob.drifter.brain.activity;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.HomeSite;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.KnownWorld;
import com.dinglebob.drifter.brain.Planner;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Settling down (DESIGN.md section 9, stage 4 "a home site chosen" and "a chest"): picks a site from what it has seen,
 * makes a chest if it has none (through the planner, like any other target), walks home and puts the chest down there.
 * TODO(milestone 8 step 2): the starter hut around it.
 */
public class SettleActivity extends Activity {
	private static final int CHEST_DISTANCE = 2;
	private static final String SITE_SEARCH = "looking for a place to settle";
	private static final int PICKY_LEGS = 2;
	private static final int MAX_SITE_LEGS = 8;
	@Nullable
	private BlockPos placing;
	private final java.util.Set<BlockPos> tried = new java.util.HashSet<>();
	private int placeTries;
	private boolean walked;

	public SettleActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		Home home = this.drifter.getDrifterBrain().home();
		return home == null ? "find a place to settle" : "set up home";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 8;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		Home home = this.drifter.getDrifterBrain().home();
		if (step instanceof PlaceBlockTask && status == Status.SUCCESS && this.placing != null && home != null) {
			home.addChest(this.placing);
			this.drifter.getSenses().placedBlock(this.placing);
			this.drifter.getDrifterBrain().clearStorageFull();
			this.think(home.chests().size() == 1 ? "put a chest down at home" : "put down another chest at home");
		}

		// A spot it couldn't put the chest on: not again (found in testing: a spot by the home's middle it couldn't place
		// on was tried again by every new attempt, forever).
		if (step instanceof PlaceBlockTask && status == Status.FAILURE && this.placing != null && home != null) {
			home.badChestSpots.add(this.placing);
		}

		this.placing = null;
	}

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		Home home = this.drifter.getDrifterBrain().home();
		// A good look round before choosing (found in testing: it settled beside a village before noticing it).
		if (home == null && !this.lookedRound) {
			this.lookedRound = true;
			return this.begin(new com.dinglebob.drifter.executor.task.LookAroundTask(this.drifter));
		}

		if (home == null) {
			// A village house with a bed: it moves in instead of building (user's request, session 2). Not Builders, who
			// want their own place, nor Hermits, who keep away from villages.
			BlockPos bed = this.villageBed(level);
			// Into a house it has seen the door of: step inside and look round (a player opens a few doors looking for a bed).
			if (bed == null && this.lookedRoundVillage && this.housesVisited < MAX_HOUSES && this.movesIn()) {
				BlockPos inside = this.nextHouse(level);
				if (inside != null) {
					this.housesVisited++;
					this.lookInside = true;
					this.think("looking inside a house");
					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(inside), 0.6, "step into the house"));
				}
			}

			if (bed == null && this.lookInside) {
				this.lookInside = false;
				return this.begin(new com.dinglebob.drifter.executor.task.LookAroundTask(this.drifter));
			}

			// A village it has seen but no bed yet: beds are indoors, so it walks round the village (a few legs, to different
			// parts of it) until it has seen a door or a bed, then looks into the houses.
			if (bed == null && this.villageLooks < MAX_VILLAGE_LOOKS && this.movesIn() && !this.doorToTry(level)) {
				java.util.List<com.dinglebob.drifter.memory.MemoryEntry> villages = this.drifter.getMemory().find(com.dinglebob.drifter.memory.MemoryKind.LANDMARK,
					e -> e.label.equals("village") && e.dimension.equals(level.dimension().location())
						&& e.pos.distanceTo(this.drifter.position()) <= VILLAGE_BED_RANGE + 32);
				if (!villages.isEmpty()) {
					com.dinglebob.drifter.memory.MemoryEntry village = villages.get(0);
					// A different side of the village each time, out where the houses are (its signs are mostly paths in the
					// middle; found in testing: it never got near a house door).
					double angle = Math.toRadians(120.0 * this.villageLooks + this.drifter.getRandom().nextInt(60));
					Vec3 toward = village.pos.add(Math.cos(angle) * VILLAGE_LOOK_OUT, 0.0, Math.sin(angle) * VILLAGE_LOOK_OUT);
					this.villageLooks++;
					this.lookedRoundVillage = true;
					this.think("a village: looking round the houses for a bed");
					return this.begin(new ExploreActivity(this.drifter, "looking round the village", toward,
						drifter -> this.villageBed(drifter.level()) != null || this.doorToTry(drifter.level())));
				}
			}

			if (bed != null) {
				BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, bed, java.util.Set.of());
				if (spot != null) {
					home = new Home(spot, level.dimension().location(), this.now());
					home.houseBed = bed;
					this.drifter.getDrifterBrain().setHome(home);
					this.think("moving into the village house with the bed at " + bed.toShortString());
				}
			}
		}

		if (home == null) {
			// Picky at first; after a couple of looks around it takes bumpier ground.
			boolean picky = this.drifter.getDrifterBrain().timesExplored(SITE_SEARCH) < PICKY_LEGS;
			HomeSite.Site site = HomeSite.choose(this.drifter, picky ? HomeSite.MIN_FLATNESS : HomeSite.FALLBACK_FLATNESS);
			if (site == null) {
				if (this.drifter.getDrifterBrain().timesExplored(SITE_SEARCH) >= MAX_SITE_LEGS) {
					// Looked long enough: anywhere half decent, or right here (it levels the spot as it builds).
					site = HomeSite.choose(this.drifter, 0.25);
					BlockPos here = this.drifter.blockPosition();
					if (site == null && this.drifter.onGround() && !this.drifter.isInWater() && KnownBlocks.isStandable(level, here)) {
						site = new HomeSite.Site(here, 0.0, "nowhere better around: settling right here");
					}

					if (site == null) {
						return this.fail("found nowhere to settle around here");
					}
				} else {
					return this.begin(new ExploreActivity(this.drifter, SITE_SEARCH, null,
						drifter -> HomeSite.choose(drifter, HomeSite.MIN_FLATNESS) != null));
				}
			}

			home = new Home(site.pos(), level.dimension().location(), this.now());
			this.drifter.getDrifterBrain().setHome(home);
			this.think(String.format(Locale.ROOT, "settling at %s (%s)", site.pos().toShortString(), site.why()));
		}

		if (home.hasChest() && !this.drifter.getDrifterBrain().storageFull()) {
			return Status.SUCCESS;
		}

		// No chest yet (or they're all full): make one (the planner works out the planks), then put it down at home.
		if (this.drifter.getInventory().count(stack -> stack.is(Items.CHEST)) == 0) {
			Planner.Plan plan = Planner.plan(this.drifter, KnownWorld.scan(this.drifter), Items.CHEST, 1);
			if (!plan.feasible() || plan.next() == null) {
				return this.fail("can't work out how to make a chest");
			}

			return this.begin(Goals.activityFor(this.drifter, plan.next()));
		}

		if (this.drifter.blockPosition().distSqr(home.center) > 3 * 3) {
			if (this.walked && this.failures() > 2) {
				return this.fail("can't get home");
			}

			this.walked = true;
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(home.center), 1.0, "walk home"));
		}

		BlockPos spot = this.placeTries++ > 6 ? null : this.chestSpot(level, home);
		if (spot == null) {
			// Any free floor right next to it will do, but not where the hut goes: its walls, door, or the middle (found in
			// testing: a chest in the middle of the hut, and jumping off it to put the roof on opened the chest instead).
			java.util.Set<BlockPos> notHere = new java.util.HashSet<>(this.tried);
			notHere.addAll(home.badChestSpots);
			com.dinglebob.drifter.building.StarterHut hut = home.houseBed != null ? null : com.dinglebob.drifter.building.StarterHut.of(home);
			for (BlockPos cell : hut == null ? java.util.List.<BlockPos>of() : java.util.List.of(home.center)) {
				notHere.add(cell);
			}

			if (hut != null) {
			java.util.List<BlockPos> corners = hut.furnitureSpots();
			for (BlockPos cell : hut.inside()) {
				if (!corners.contains(cell)) {
					notHere.add(cell);
				}
			}

			notHere.addAll(hut.wallLayer(0));
			notHere.add(hut.doorPos());
			}
			spot = KnownBlocks.placementSpotNear(this.drifter, notHere).orElse(null);
		}

		if (spot == null) {
			// This spot's no good (a mountaintop, a ledge): look for another place to settle.
			if (!home.hasChest()) {
				this.drifter.getDrifterBrain().setHome(null);
				this.think("no room for a chest here: looking for a better place to settle");
				return Status.RUNNING;
			}

			return this.fail("no free floor next to home for a chest");
		}

		this.placing = spot;
		this.tried.add(spot);
		return this.begin(new PlaceBlockTask(this.drifter, spot, stack -> stack.is(Items.CHEST), "a chest"));
	}

	/** Chests side by side join into a double chest; it keeps them apart so each is its own. */
	private static boolean nextToChest(Level level, BlockPos pos) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (Home.isStorageBlock(level.getBlockState(pos.relative(side)))) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Inside the hut-to-be: its corners (the hut is built around the chest). Failing that, a free spot a little off the
	 * home's middle with a sturdy floor.
	 */
	/** How far it looks for a village bed to move in with. */
	private static final double VILLAGE_BED_RANGE = 64.0;

	private boolean lookedRoundVillage;
	private static final int MAX_VILLAGE_LOOKS = 3;
	/** How far out from the middle of a village it looks for houses. */
	private static final double VILLAGE_LOOK_OUT = 24.0;
	private int villageLooks;

	/** It has seen a house door it hasn't looked into yet. */
	private boolean doorToTry(Level level) {
		return this.drifter.getMemory().nearest(com.dinglebob.drifter.memory.MemoryKind.STRUCTURE, level.dimension().location(),
			this.drifter.position(), VILLAGE_BED_RANGE, e -> e.label.equals("door") && !this.doorsTried.contains(e.blockPos())) != null;
	}
	private static final int MAX_HOUSES = 4;
	private int housesVisited;
	private boolean lookInside;
	private final java.util.Set<BlockPos> doorsTried = new java.util.HashSet<>();

	/** The inside of the nearest house whose door it has seen and hasn't looked into: the side of the door under a roof. */
	@Nullable
	private BlockPos nextHouse(Level level) {
		var dimension = level.dimension().location();
		com.dinglebob.drifter.memory.MemoryEntry door = this.drifter.getMemory().nearest(com.dinglebob.drifter.memory.MemoryKind.STRUCTURE,
			dimension, this.drifter.position(), VILLAGE_BED_RANGE, e -> e.label.equals("door") && !this.doorsTried.contains(e.blockPos()));
		if (door == null) {
			this.think("no house door it has seen to look into");
			return null;
		}

		BlockPos pos = door.blockPos();
		this.doorsTried.add(pos);
		// The top half seen: the door's bottom is the one below.
		net.minecraft.world.level.block.state.BlockState doorState = level.getBlockState(pos);
		if (doorState.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
			&& doorState.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
			pos = pos.below();
			this.doorsTried.add(pos);
		}
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			BlockPos in = pos.relative(side);
			if (!level.canSeeSky(in) && KnownBlocks.isStandable(level, in)) {
				return in;
			}
		}

		this.think("couldn't tell which side of the door at " + pos.toShortString() + " is inside");
		return null;
	}
	private boolean lookedRound;

	/** Moves into a village house rather than building: not Builders (their own place), Hermits or Nomads. */
	private boolean movesIn() {
		var archetype = this.drifter.getPersonality().archetype();
		return archetype != com.dinglebob.drifter.personality.Archetype.BUILDER && archetype != com.dinglebob.drifter.personality.Archetype.HERMIT
			&& archetype != com.dinglebob.drifter.personality.Archetype.NOMAD;
	}

	/** The head of a bed it has seen under a roof (a house), not too far, or null. */
	@Nullable
	private BlockPos villageBed(Level level) {
		if (!this.movesIn()) {
			return null;
		}

		var dimension = level.dimension().location();
		for (com.dinglebob.drifter.memory.MemoryEntry entry : this.drifter.getMemory().find(com.dinglebob.drifter.memory.MemoryKind.STATION,
			e -> e.dimension.equals(dimension) && e.label.endsWith("_bed") && e.pos.distanceTo(this.drifter.position()) <= VILLAGE_BED_RANGE)) {
			BlockPos pos = entry.blockPos();
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock)) {
				continue;
			}

			BlockPos head = state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD
				? pos : pos.relative(state.getValue(net.minecraft.world.level.block.BedBlock.FACING));
			// Under a roof: a house, not a bed someone left in a field.
			if (!level.canSeeSky(head.above())) {
				return head.immutable();
			}
		}

		return null;
	}

	@Nullable
	private BlockPos chestSpot(Level level, Home home) {
		// In a village house: a free spot by the bed (the hut's corners don't apply).
		if (home.houseBed != null) {
			return null;
		}

		if (home.hutDoor == null) {
			home.hutDoor = com.dinglebob.drifter.building.StarterHut.chooseDoor(level, home.center);
		}

		for (BlockPos corner : com.dinglebob.drifter.building.StarterHut.of(home).furnitureSpots()) {
			if (level.getBlockState(corner).canBeReplaced() && level.getFluidState(corner).isEmpty() && !nextToChest(level, corner)
				&& level.getBlockState(corner.below()).isFaceSturdy(level, corner.below(), Direction.UP) && !corner.equals(this.drifter.blockPosition())
				&& !this.tried.contains(corner) && !home.badChestSpots.contains(corner)) {
				return corner;
			}
		}

		return this.offCenterSpot(level, home);
	}

	@Nullable
	private BlockPos offCenterSpot(Level level, Home home) {
		for (int turn = 0; turn < 4; turn++) {
			Direction direction = Direction.from2DDataValue((this.placeTries + turn) % 4);
			// Each extra chest goes a step further round the home's middle.
			int along = home.chests().size() * 2 - 1;
			BlockPos spot = home.center.relative(direction, CHEST_DISTANCE).relative(direction.getClockWise(), along);
			for (int dy = 0; dy >= -1; dy--) {
				BlockPos candidate = spot.above(dy);
				if (level.getBlockState(candidate).canBeReplaced() && level.getFluidState(candidate).isEmpty()
					&& level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(), Direction.UP)
					&& !candidate.equals(this.drifter.blockPosition()) && !candidate.equals(this.drifter.blockPosition().above())
					&& KnownBlocks.isStandable(level, candidate.relative(direction.getOpposite())) && !nextToChest(level, candidate)
					&& !this.tried.contains(candidate) && !home.badChestSpots.contains(candidate)) {
					return candidate;
				}
			}
		}

		return null;
	}
}
