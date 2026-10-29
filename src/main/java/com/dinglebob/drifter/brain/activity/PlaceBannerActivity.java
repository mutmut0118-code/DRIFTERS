package com.dinglebob.drifter.brain.activity;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.building.StarterHut;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Standing the group's banner outside the hut, beside the door (section 21 team wars: so players can tell the sides
 * apart), on the ground like a player would put it.
 */
public class PlaceBannerActivity extends Activity {
	@Nullable
	private BlockPos spot;
	private boolean walked;
	private boolean placed;

	/** A town's banner (its town's spot and colour) rather than the group's by its hut door; null for the group's. */
	@Nullable
	private final com.dinglebob.drifter.api.TownWork.Banner town;

	public PlaceBannerActivity(DrifterEntity drifter) {
		this(drifter, null);
	}

	public PlaceBannerActivity(DrifterEntity drifter, @Nullable com.dinglebob.drifter.api.TownWork.Banner town) {
		super(drifter);
		this.town = town;
		if (town != null) {
			this.spot = town.spot();
		}
	}

	@Override
	public String describe() {
		return this.town != null ? "put up " + this.town.what() : "put up the group's banner";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof PlaceBlockTask && status == Status.SUCCESS && this.spot != null && this.town != null) {
			var work = this.drifter.getTownWork();
			if (work != null) {
				work.bannerUp(this.drifter, this.spot);
			}

			this.placed = true;
			this.think("put up " + this.town.what());
			return;
		}

		if (step instanceof PlaceBlockTask && status == Status.SUCCESS && this.spot != null) {
			Home home = this.drifter.getDrifterBrain().home();
			if (home != null) {
				home.banner = this.spot;
			}

			this.placed = true;
			this.think("put up the group's banner by the door");
		}
	}

	@Override
	protected Status next() {
		if (this.placed) {
			return Status.SUCCESS;
		}

		if (this.town != null) {
			// (Its town's spot: walked to, then stood on the ground there, of its colour.)
			if (!Reach.canReach(this.drifter, this.spot.below())) {
				if (this.walked) {
					return this.fail("can't get to where the banner goes");
				}

				BlockPos stand = KnownBlocks.standingSpotFor(this.drifter, this.spot, Set.of(this.spot));
				this.walked = true;
				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(stand != null ? stand : this.spot), 2.0, "walk over to where the banner goes"));
			}

			net.minecraft.world.item.Item item = this.town.item();
			return this.begin(new PlaceBlockTask(this.drifter, this.spot, stack -> stack.is(item), this.town.what()));
		}

		Home home = this.drifter.getDrifterBrain().home();
		if (home == null) {
			return this.fail("no home");
		}

		if (this.spot == null) {
			this.spot = spotFor(this.drifter.level(), StarterHut.of(home));
			if (this.spot == null) {
				return this.fail("no room for a banner by the door");
			}
		}

		// (It goes on the ground: that's the block to reach; an empty spot itself never counts as in reach.)
		if (!Reach.canReach(this.drifter, this.spot.below())) {
			if (this.walked) {
				return this.fail("can't get to where the banner goes");
			}

			// Out of the door and a step on (the banner goes beside that).
			StarterHut hut = StarterHut.of(home);
			BlockPos stand = KnownBlocks.standingSpotFor(this.drifter, this.spot, Set.of(this.spot));
			Vec3 to = stand != null ? Vec3.atBottomCenterOf(stand) : Vec3.atBottomCenterOf(hut.doorPos().relative(hut.door, 2));
			this.walked = true;
			return this.begin(new NavigateTask(this.drifter, to, 1.5, "walk out of the door"));
		}

		return this.begin(new PlaceBlockTask(this.drifter, this.spot, stack -> stack.is(ItemTags.BANNERS), "the group's banner"));
	}

	/** Just outside the door, to one side of it (so it's not in the way), on solid ground. */
	@Nullable
	private static BlockPos spotFor(Level level, StarterHut hut) {
		BlockPos out = hut.doorPos().relative(hut.door);
		for (Direction side : new Direction[] {hut.door.getClockWise(), hut.door.getCounterClockWise()}) {
			for (int dy = 0; dy >= -1; dy--) {
				BlockPos spot = out.relative(side).above(dy);
				if (StarterHut.free(level, spot) && StarterHut.free(level, spot.above())
					&& level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)) {
					return spot;
				}
			}
		}

		return null;
	}
}
