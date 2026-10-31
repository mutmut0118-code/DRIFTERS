package com.dinglebob.drifter.brain.activity;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

/** Puts down a crafting table or furnace right next to it (section 10.2, "Portable tables"). */
public class PlaceStationActivity extends Activity {
	private final Item item;
	private final String label;
	private final Set<BlockPos> tried = new HashSet<>();
	private BlockPos placing;
	private boolean placed;

	public PlaceStationActivity(DrifterEntity drifter, Item item) {
		super(drifter);
		this.item = item;
		this.label = item.getDescription().getString().toLowerCase(java.util.Locale.ROOT);
	}

	@Override
	public String describe() {
		return "place a " + this.label;
	}

	@Override
	protected int timeoutTicks() {
		// (Longer when it climbs back up home first.)
		return 20 * 120;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (status == Status.SUCCESS && this.placing != null) {
			this.placed = true;
			// It placed it itself, so it knows it's there (stations memory).
			this.drifter.getSenses().placedBlock(this.placing);
			// A crafting table put down away from home: tidy Drifters pick it back up when they're done (habit).
			com.dinglebob.drifter.brain.Home home = this.drifter.getDrifterBrain().home();
			if (this.item == net.minecraft.world.item.Items.CRAFTING_TABLE
				&& (home == null || !home.near(this.drifter, com.dinglebob.drifter.brain.Home.STORAGE_RANGE))) {
				this.drifter.getDrifterBrain().strayTable = this.placing.immutable();
			}
		}
	}

	private boolean wentUp;
	private int digsDown;
	private int indoorTries;
	private static final int MAX_DIG_DOWN = 8;

	/** Standing on a lone column: a building block underfoot with nothing to stand on beside it. */
	private boolean onPillar(BlockPos below) {
		var level = this.drifter.level();
		if (!com.dinglebob.drifter.hands.Tools.BUILDING_BLOCK.test(new net.minecraft.world.item.ItemStack(level.getBlockState(below).getBlock().asItem()))) {
			return false;
		}

		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			if (!level.getBlockState(below.relative(side)).getCollisionShape(level, below.relative(side)).isEmpty()) {
				return false;
			}
		}

		return true;
	}

	@Override
	protected Status next() {
		if (this.placed) {
			return Status.SUCCESS;
		}

		// Never the middle of its hut or house: it pillars up there to roof it (a table there broke the roof every day).
		com.dinglebob.drifter.brain.Home home = this.drifter.getDrifterBrain().home();
		if (home != null) {
			this.tried.add(home.center);
			if (home.houseCenter != null) {
				this.tried.add(home.houseCenter);
			}
		}

		// A furnace not down a shallow hole it dug by home: it has to come back to one it loads (found in desert tests: the
		// furnace at the bottom of a shaft through the sand, no way back down, the stone in it never collected). Up and home
		// first, as a player carries it back. (A table's used at once, and one it can't reach it replaces; deep in a mine it
		// stays where it is.)
		int down = home == null ? 0 : home.center.getY() - this.drifter.getBlockY();
		if (!this.wentUp && home != null && this.item != net.minecraft.world.item.Items.CRAFTING_TABLE && down > 3 && down <= 24
			&& home.near(this.drifter, com.dinglebob.drifter.brain.Home.STORAGE_RANGE)
			&& com.dinglebob.drifter.brain.mining.Caver.underground(this.drifter, this.drifter.blockPosition())) {
			this.wentUp = true;
			return this.begin(new com.dinglebob.drifter.executor.task.NavigateTask(this.drifter, net.minecraft.world.phys.Vec3.atBottomCenterOf(home.center), 3.0,
				"go back up home to put the " + this.label + " down"));
		}

		// At home in its house from a plan: the place its layout has for this (the work wall, the crafting table in the storage
		// wall), put down from in front so it faces the room (a player's furnishing guidebook, the user's).
		if (home != null && home.near(this.drifter, 12.0) && this.indoorTries < 3) {
			var laid = MoveInActivity.laidOut(this.drifter.level(), home, this.drifter.getServer(), net.minecraft.world.level.block.Block.byItem(this.item));
			if (laid != null && !this.tried.contains(laid.pos())) {
				BlockPos front = laid.pos().relative(laid.facing());
				if (!this.drifter.blockPosition().equals(front)) {
					this.indoorTries++;
					return this.begin(new com.dinglebob.drifter.executor.task.NavigateTask(this.drifter, net.minecraft.world.phys.Vec3.atBottomCenterOf(front), 0.4,
						"walk over to put the " + this.label + " down"));
				}

				this.placing = laid.pos();
				this.tried.add(laid.pos());
				return this.begin(new PlaceBlockTask(this.drifter, laid.pos(), stack -> stack.is(this.item), this.label));
			}
		}

		// At home in a house it didn't lay out (a village house, its own house from a plan): along a wall with its other
		// station, out of the way (found by the user: a crafting table in the middle of a village house's floor).
		BlockPos houseBed = home != null && home.near(this.drifter, 6.0) ? home.houseBed(this.drifter.level()) : null;
		com.dinglebob.drifter.building.Interior room = houseBed != null ? com.dinglebob.drifter.building.Interior.of(this.drifter.level(), houseBed) : null;
		if (room != null && this.indoorTries < 3) {
			java.util.Set<BlockPos> notHere = new HashSet<>(this.tried);
			notHere.add(this.drifter.blockPosition());
			BlockPos indoors = room.spot(com.dinglebob.drifter.building.Interior.Kind.STATION, notHere);
			if (indoors != null) {
				this.indoorTries++;
				if (!com.dinglebob.drifter.hands.Reach.canReach(this.drifter, indoors.below())) {
					BlockPos stand = KnownBlocks.standingSpotFor(this.drifter, indoors, java.util.Set.of(indoors));
					if (stand != null) {
						return this.begin(new com.dinglebob.drifter.executor.task.NavigateTask(this.drifter, net.minecraft.world.phys.Vec3.atBottomCenterOf(stand), 0.5,
							"walk over to put the " + this.label + " down"));
					}
				}

				this.placing = indoors;
				this.tried.add(indoors);
				return this.begin(new PlaceBlockTask(this.drifter, indoors, stack -> stack.is(this.item), this.label));
			}
		}

		// Not in the space of the house it's building (found in testing: a crafting table put down where a wall went).
		if (home != null && home.planId != null && home.planOrigin != null && !home.planDone && this.drifter.getServer() != null) {
			var plan = com.dinglebob.drifter.building.plan.HousePlans.get(this.drifter.getServer(), home.planId);
			if (plan != null) {
				var placed = com.dinglebob.drifter.building.plan.PlacedPlan.of(plan, home);
				for (BlockPos near : BlockPos.betweenClosed(this.drifter.blockPosition().offset(-2, -1, -2), this.drifter.blockPosition().offset(2, 1, 2))) {
					if (placed.contains(near, 1)) {
						this.tried.add(near.immutable());
					}
				}
			}
		}

		// Not in its house's furniture places or the floor in front of them (found in a client test: a crafting table put
		// down in front of the kitchen's stove, in the way of the cooking).
		if (home != null && home.planId != null && home.planOrigin != null && home.planDone && this.drifter.getServer() != null) {
			var plan = com.dinglebob.drifter.building.plan.HousePlans.get(this.drifter.getServer(), home.planId);
			if (plan != null) {
				this.tried.addAll(com.dinglebob.drifter.building.Furnishing.keepClear(this.drifter.level(),
					com.dinglebob.drifter.building.plan.PlacedPlan.of(plan, home), home.planBed));
			}
		}

		Optional<BlockPos> spot = KnownBlocks.placementSpotNear(this.drifter, this.tried);
		if (spot.isEmpty()) {
			// Up on a pillar (it built one to reach something): down first, digging out its own blocks (found in testing:
			// "no free spot" for a crafting table on top of the pillar it had built to reach a tree).
			BlockPos below = this.drifter.blockPosition().below();
			if (this.digsDown++ < MAX_DIG_DOWN && this.onPillar(below)) {
				return this.begin(this.dig(below));
			}

			return this.fail("no free spot next to it for the " + this.label);
		}

		this.placing = spot.get();
		this.tried.add(spot.get());
		return this.begin(new PlaceBlockTask(this.drifter, spot.get(), stack -> stack.is(this.item), this.label));
	}
}
