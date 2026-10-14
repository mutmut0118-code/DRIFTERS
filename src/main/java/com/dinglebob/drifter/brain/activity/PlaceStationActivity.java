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
		return 20 * 40;
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

	private int digsDown;
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
