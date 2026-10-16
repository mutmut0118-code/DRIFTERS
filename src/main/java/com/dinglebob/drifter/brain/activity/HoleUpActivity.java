package com.dinglebob.drifter.brain.activity;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.executor.task.WaitTask;
import com.dinglebob.drifter.hands.Tools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Badly hurt in the Nether with no way home just now: it walls itself in where it stands (blocks round its feet and head
 * and one over its head), eats if it can, and waits until it has healed, the way a player holes up (found in testing: a
 * hurt Drifter that couldn't get to its portal went back to the blazes and died).
 */
public class HoleUpActivity extends Activity {
	/** Healed enough to go on. */
	public static final float HEALED = 16.0F;
	private static final int MAX_TICKS = 20 * 120;

	public HoleUpActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "hole up to heal";
	}

	@Override
	protected int timeoutTicks() {
		return MAX_TICKS + 20 * 30;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		// A wall block it can't place (no face to put it against) is skipped; the rest still helps.
		return false;
	}

	@Override
	protected Status next() {
		if (this.drifter.getHealth() >= HEALED || this.ticksRunning() > MAX_TICKS) {
			this.think("healed up: going on");
			return Status.SUCCESS;
		}

		if (this.ticksRunning() < 2) {
			this.think("badly hurt: walling itself in to heal");
		}

		// Hungry: it eats (healing needs a full stomach).
		if (this.drifter.getFoodData().getFoodLevel() < 18 && com.dinglebob.drifter.brain.Food.choose(this.drifter) != null && !this.ate) {
			this.ate = true;
			return this.begin(new EatActivity(this.drifter));
		}

		// In the middle of its block first: standing near an edge its body is partly in the next block, and no wall can go there
		// (found in testing: the wall on one side never went up, and fireballs came in through the gap).
		net.minecraft.world.phys.Vec3 middle = net.minecraft.world.phys.Vec3.atBottomCenterOf(this.drifter.blockPosition());
		if (!this.centered && this.drifter.onGround() && (Math.abs(this.drifter.getX() - middle.x) > 0.15 || Math.abs(this.drifter.getZ() - middle.z) > 0.15)) {
			this.centered = true;
			return this.begin(new com.dinglebob.drifter.executor.task.CenterOnBlockTask(this.drifter, middle));
		}

		BlockPos gap = this.gap();
		if (gap != null && Tools.buildingBlockCount(this.drifter) > 0) {
			this.tried.merge(gap, 1, Integer::sum);
			return this.begin(new PlaceBlockTask(this.drifter, gap, Tools.BUILDING_BLOCK, "a wall to rest behind"));
		}

		this.ate = false;
		return this.begin(new WaitTask(this.drifter, "rest behind its walls", () -> this.drifter.getHealth() >= HEALED, 20 * 10));
	}

	private boolean ate;
	private boolean centered;
	/** Tries per cell: one that failed gets one more go once its neighbours are up (they give it a face to go against). */
	private final java.util.Map<BlockPos, Integer> tried = new java.util.HashMap<>();
	private static final int TRIES = 2;

	/** An open cell round its feet, then its head, then over its head: each row rests on the one before (found in testing:
	 * head-high blocks first had nothing to go against, and fireballs came in through the gaps). */
	@org.jetbrains.annotations.Nullable
	private BlockPos gap() {
		Level level = this.drifter.level();
		BlockPos feet = this.drifter.blockPosition();
		java.util.List<BlockPos> cells = new java.util.ArrayList<>();
		for (Direction side : Direction.Plane.HORIZONTAL) {
			cells.add(feet.relative(side));
		}

		for (Direction side : Direction.Plane.HORIZONTAL) {
			cells.add(feet.relative(side).above());
		}

		cells.add(feet.above(2));
		for (BlockPos cell : cells) {
			if (level.getBlockState(cell).canBeReplaced() && this.tried.getOrDefault(cell, 0) < TRIES) {
				return cell.immutable();
			}
		}

		return null;
	}
}
