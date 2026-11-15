package com.dinglebob.drifter.brain.activity;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.Stations;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.LoomTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Weaving a banner's patterns at a loom (the user, 2026-11-15: nations' banners with a real design): a loom it knows near by
 * (or near home), or the one it carries put down beside it; then at it, one pattern a go, each with its dye, as a player
 * does, till the banner is done.
 */
public class LoomActivity extends Activity {
	private final TownWork.Banner banner;
	private final Set<BlockPos> tried = new HashSet<>();
	@Nullable
	private BlockPos placing;
	private int woven;
	private boolean wentHome;

	public LoomActivity(DrifterEntity drifter, TownWork.Banner banner) {
		super(drifter);
		this.banner = banner;
	}

	@Override
	public String describe() {
		return "weave " + this.banner.what() + " at a loom";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 3;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof LoomTask && status == Status.SUCCESS) {
			this.woven++;
		}

		if (step instanceof PlaceBlockTask && status == Status.SUCCESS && this.placing != null) {
			// It put it down itself, so it knows it's there.
			this.drifter.getSenses().placedBlock(this.placing);
			this.think("set up a loom at " + this.placing.toShortString());
		}
	}

	/** Carrying the banner finished. */
	public static boolean finished(DrifterEntity drifter, TownWork.Banner banner) {
		return drifter.getInventory().findSlot(banner::is, 0, DrifterInventory.MAIN_SIZE) >= 0;
	}

	/** Carrying a banner on its way to this one (plain, or some of its patterns on). */
	public static boolean begun(DrifterEntity drifter, TownWork.Banner banner) {
		return drifter.getInventory().findSlot(stack -> {
			int done = LoomTask.done(banner, stack);
			return done >= 0 && done < banner.layers().size();
		}, 0, DrifterInventory.MAIN_SIZE) >= 0;
	}

	@Override
	protected Status next() {
		if (finished(this.drifter, this.banner)) {
			if (this.woven > 0) {
				this.think("finished " + this.banner.what() + " at the loom");
			}

			return Status.SUCCESS;
		}

		if (!begun(this.drifter, this.banner)) {
			return this.fail("has no banner to weave on");
		}

		BlockPos loom = Stations.usable(this.drifter, Blocks.LOOM);
		if (loom == null) {
			if (this.drifter.getInventory().count(stack -> stack.is(Items.LOOM)) == 0) {
				return this.fail("has no loom to weave at");
			}

			// Home first, to put it down there (not in a mine or out in the wild).
			var home = this.drifter.getDrifterBrain().home();
			if (home != null && !home.near(this.drifter, 10.0) && !this.wentHome) {
				this.wentHome = true;
				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(home.center), 4.0, "go home to set up the loom"));
			}

			// Its own loom, put down beside it.
			if (this.placing == null || !this.drifter.level().getBlockState(this.placing).canBeReplaced()) {
				Optional<BlockPos> spot = KnownBlocks.placementSpotNear(this.drifter, this.tried);
				if (spot.isEmpty()) {
					return this.fail("no free spot for a loom");
				}

				this.placing = spot.get();
				this.tried.add(this.placing);
			}

			BlockPos at = this.placing;
			return this.begin(new PlaceBlockTask(this.drifter, at, stack -> stack.is(Items.LOOM), "a loom"));
		}

		if (Reach.canReach(this.drifter, loom)) {
			return this.begin(new LoomTask(this.drifter, loom, this.banner));
		}

		if (this.failures() > 3) {
			Stations.giveUp(this.drifter, Blocks.LOOM, loom);
			return this.fail("can't get to the loom");
		}

		BlockPos stand = KnownBlocks.standingSpotFor(this.drifter, loom, Set.of());
		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(stand != null ? stand : loom), stand != null ? 0.5 : 2.0, "walk over to the loom"));
	}
}
