package com.dinglebob.drifter.brain.activity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.executor.task.UseItemTask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Water where it's wanted (a town's well, milestone 22; the user, 2026-10-28: "well with real water"): it fills its bucket
 * at water it knows (a lake, a river; looking for some if it knows none near), carries it over and pours it in, one cell
 * at a time, as a player does. Two cells of a 2x2 basin, corner to corner, are enough: the other two fill themselves and
 * it never runs dry. Done when every cell is water that stays.
 */
public class PourWaterActivity extends Activity {
	private final List<BlockPos> cells;
	private final String what;
	private int searches;
	private int walks;
	/** Water it couldn't fill its bucket at (it tries other water), and the water it's filling at now. */
	private final Set<BlockPos> dry = new HashSet<>();
	private BlockPos filling;
	/** Where it stood when a pour missed (it pours from somewhere else). */
	private final Set<BlockPos> missedFrom = new HashSet<>();
	/** Close enough to pour (eye to the cell's floor). */
	private static final double POUR_REACH = 4.0;

	public PourWaterActivity(DrifterEntity drifter, List<BlockPos> cells, String what) {
		super(drifter);
		this.cells = List.copyOf(cells);
		this.what = what;
	}

	@Override
	public String describe() {
		return "fill " + this.what + " with water";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 4;
	}

	@Override
	protected void stepDone(com.dinglebob.drifter.executor.Task step, Status status) {
		if (status == Status.FAILURE && step.describe().equals("fill the bucket") && this.filling != null) {
			this.dry.add(this.filling);
		}

		if (status == Status.FAILURE && step.describe().equals("pour the water")) {
			this.missedFrom.add(this.drifter.blockPosition());
		}

		if (status == Status.SUCCESS && step.describe().equals("pour the water") && this.drifter.getTownWork() != null) {
			this.drifter.getTownWork().worked(this.drifter, "water", 1);
		}
	}

	@Override
	protected boolean countsAsFailure(com.dinglebob.drifter.executor.Task step) {
		// (A walk that got stuck is tried another way, or to other water: see next.)
		return !(step instanceof NavigateTask);
	}

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		BlockPos cell = this.cells.stream().filter(pos -> !FarmActivity.isWaterSource(level, pos)).findFirst().orElse(null);
		if (cell == null) {
			this.think(this.what + " has its water");
			return Status.SUCCESS;
		}

		if (this.drifter.getInventory().count(stack -> stack.is(Items.WATER_BUCKET)) == 0) {
			return this.fill(level);
		}

		// Over to it (close, somewhere it hasn't missed from), then pour onto the floor of the cell: the bucket pours through
		// water already there, and over a rim it may only see a sliver of the floor past (found in testing: "can't get to the
		// town's well" for its second corner, the first's water and the rim in the way of a strict look).
		BlockPos floor = cell.below();
		Vec3 aim = Vec3.atBottomCenterOf(cell).add(0.0, 0.05, 0.0);
		if (this.drifter.getEyePosition().distanceTo(aim) > POUR_REACH || this.missedFrom.contains(this.drifter.blockPosition())) {
			Set<BlockPos> excluded = new HashSet<>(this.cells);
			excluded.addAll(this.missedFrom);
			BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, floor, excluded);
			if (this.walks++ > 6) {
				return this.fail("can't get to " + this.what);
			}

			return this.begin(spot != null ? new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk over to " + this.what, NavigateTask.Pace.STROLL)
				: new NavigateTask(this.drifter, Vec3.atCenterOf(cell), 2.0, "walk over to " + this.what));
		}

		this.think("pouring water into " + this.what);
		return this.begin(new SequenceTask(this.drifter, "pour the water", List.of(
			new SelectItemTask(this.drifter, stack -> stack.is(Items.WATER_BUCKET), "a water bucket"),
			new LookAtTask(this.drifter, Vec3.atBottomCenterOf(cell).add(0.0, 0.05, 0.0), 4),
			new UseItemTask(this.drifter))));
	}

	/** To water it knows (not what it's filling), and fills the bucket. */
	private Status fill(Level level) {
		if (this.drifter.getInventory().count(stack -> stack.is(Items.BUCKET)) == 0) {
			return this.fail("needs a bucket for " + this.what);
		}

		// Not what it's filling, nor a farm's water (that keeps the crops growing), nor water it couldn't fill at.
		Set<BlockPos> skip = new HashSet<>(this.cells);
		skip.addAll(this.dry);
		com.dinglebob.drifter.brain.Home home = this.drifter.getDrifterBrain().home();
		if (home != null && home.farm != null) {
			BlockPos.betweenClosed(home.farm.offset(-1, -2, -1), home.farm.offset(1, 0, 1)).forEach(pos -> skip.add(pos.immutable()));
		}

		BlockPos source = FarmActivity.nearestWater(this.drifter, 64, skip);
		if (source == null) {
			// Where it remembers water (its map of the area), or a look round for some.
			if (this.searches++ < 3) {
				BlockPos wet = this.wetChunk();
				if (wet != null) {
					this.think("going to fetch water for " + this.what);
					return this.begin(new SequenceTask(this.drifter, "go to the water", List.of(
						new NavigateTask(this.drifter, Vec3.atBottomCenterOf(wet), 8.0, "walk to the water"),
						new LookAroundTask(this.drifter))));
				}

				return this.begin(new ExploreActivity(this.drifter, "looking for water", null, d -> FarmActivity.nearestWater(d, 64, skip) != null));
			}

			return this.fail("knows no water to fill its bucket at");
		}

		if (this.drifter.getEyePosition().distanceTo(Vec3.atCenterOf(source)) > this.drifter.blockInteractionRange() - 0.5) {
			BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, source, Set.of());
			// (Can't get to that water: other water, three goes at most.)
			if (this.walks++ > 6) {
				this.dry.add(source);
				this.walks = 0;
				if (this.dry.size() > 3) {
					return this.fail("can't get to the water");
				}

				return Status.RUNNING;
			}

			return this.begin(spot != null ? new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk to the water")
				: new NavigateTask(this.drifter, Vec3.atCenterOf(source), 3.0, "walk over to the water"));
		}

		this.walks = 0;
		this.filling = source;
		this.think("filling its bucket");
		return this.begin(new SequenceTask(this.drifter, "fill the bucket", List.of(
			new SelectItemTask(this.drifter, stack -> stack.is(Items.BUCKET), "a bucket"),
			new LookAtTask(this.drifter, Vec3.atCenterOf(source).add(0.0, 0.4, 0.0), 4),
			new UseItemTask(this.drifter))));
	}

	/** The middle of the nearest chunk it remembers water in (within about 6 chunks), at its own height, or null. */
	private BlockPos wetChunk() {
		net.minecraft.world.level.ChunkPos centre = new net.minecraft.world.level.ChunkPos(this.drifter.blockPosition());
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				var tile = this.drifter.getMemory().map().get(new net.minecraft.world.level.ChunkPos(centre.x + dx, centre.z + dz));
				if (tile == null || !tile.water) {
					continue;
				}

				BlockPos middle = new BlockPos((centre.x + dx) * 16 + 8, this.drifter.getBlockY(), (centre.z + dz) * 16 + 8);
				double distance = middle.distSqr(this.drifter.blockPosition());
				if (distance < bestDistance) {
					bestDistance = distance;
					best = middle;
				}
			}
		}

		return best;
	}
}
