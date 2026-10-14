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

		this.placing = null;
	}

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		Home home = this.drifter.getDrifterBrain().home();
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
			// Any free floor right next to it will do.
			spot = KnownBlocks.placementSpotNear(this.drifter, this.tried).orElse(null);
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
	@Nullable
	private BlockPos chestSpot(Level level, Home home) {
		if (home.hutDoor == null) {
			home.hutDoor = com.dinglebob.drifter.building.StarterHut.chooseDoor(level, home.center);
		}

		for (BlockPos corner : com.dinglebob.drifter.building.StarterHut.of(home).furnitureSpots()) {
			if (level.getBlockState(corner).canBeReplaced() && level.getFluidState(corner).isEmpty() && !nextToChest(level, corner)
				&& level.getBlockState(corner.below()).isFaceSturdy(level, corner.below(), Direction.UP) && !corner.equals(this.drifter.blockPosition())
				&& !this.tried.contains(corner)) {
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
					&& KnownBlocks.isStandable(level, candidate.relative(direction.getOpposite())) && !nextToChest(level, candidate)) {
					return candidate;
				}
			}
		}

		return null;
	}
}
