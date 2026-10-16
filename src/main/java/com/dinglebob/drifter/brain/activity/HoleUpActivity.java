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

		BlockPos gap = this.gap();
		if (gap != null && Tools.buildingBlockCount(this.drifter) > 0 && this.tried.add(gap)) {
			return this.begin(new PlaceBlockTask(this.drifter, gap, Tools.BUILDING_BLOCK, "a wall to rest behind"));
		}

		this.ate = false;
		return this.begin(new WaitTask(this.drifter, "rest behind its walls", () -> this.drifter.getHealth() >= HEALED, 20 * 10));
	}

	private boolean ate;
	private final java.util.Set<BlockPos> tried = new java.util.HashSet<>();

	/** An open cell round its feet and head (the head row first: that's where fireballs come in), or over its head. */
	@org.jetbrains.annotations.Nullable
	private BlockPos gap() {
		Level level = this.drifter.level();
		BlockPos feet = this.drifter.blockPosition();
		java.util.List<BlockPos> cells = new java.util.ArrayList<>();
		for (Direction side : Direction.Plane.HORIZONTAL) {
			cells.add(feet.relative(side).above());
		}

		for (Direction side : Direction.Plane.HORIZONTAL) {
			cells.add(feet.relative(side));
		}

		cells.add(feet.above(2));
		for (BlockPos cell : cells) {
			if (level.getBlockState(cell).canBeReplaced() && !this.tried.contains(cell)) {
				return cell.immutable();
			}
		}

		return null;
	}
}
