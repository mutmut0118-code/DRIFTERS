package com.dinglebob.drifter.brain.activity;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.hands.Tools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Lost underground, the way players get out: a 2-high staircase up to the surface, one step at a time, heading toward
 * home when it has one. It turns away from water, lava and sand or gravel over its head, and puts a block down where a
 * step is missing. Done when it can see the sky (then the ordinary way home works again).
 */
public class SurfaceActivity extends Activity {
	/** Most steps it digs before giving up (from the bottom of the world to well above sea level). */
	private static final int MAX_STEPS = 160;

	private Direction direction;
	private int steps;
	private int turnsInARow;
	@Nullable
	private BlockPos stepTarget;
	private int stepWalks;

	public SurfaceActivity(DrifterEntity drifter) {
		super(drifter);
		Home home = drifter.getDrifterBrain().home();
		Direction toward = drifter.getDirection();
		if (home != null) {
			int dx = home.center.getX() - drifter.getBlockX();
			int dz = home.center.getZ() - drifter.getBlockZ();
			if (dx * dx + dz * dz > 4) {
				toward = Direction.getNearest(dx, 0, dz);
			}
		}

		this.direction = toward;
	}

	@Override
	public String describe() {
		return "dig a staircase up to the surface";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 8;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		return false;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof PlaceBlockTask && status != Status.SUCCESS) {
			this.placeFailed = true;
		}
	}

	private boolean placeFailed;
	private boolean announced;

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		BlockPos feet = this.drifter.blockPosition();
		// Out: under the open sky, or it has dug out onto a cliff face with open air in front (the ordinary way home works
		// from there; found in testing: it kept trying to build a step out over the drop).
		BlockPos ahead = feet.relative(this.direction);
		if (level.canSeeSky(feet) || level.canSeeSky(feet.above()) || this.steps > 0 && level.canSeeSky(ahead.above())
			&& level.getBlockState(ahead.above()).getCollisionShape(level, ahead.above()).isEmpty()
			|| !com.dinglebob.drifter.brain.mining.Caver.underground(this.drifter, feet)) {
			if (this.steps > 0) {
				this.think("out on the surface again");
			}

			return Status.SUCCESS;
		}

		if (this.steps >= MAX_STEPS) {
			return this.fail("dug " + MAX_STEPS + " steps up and still no sky");
		}

		if (!this.announced) {
			this.announced = true;
			this.think("lost down here: digging a staircase up to the surface");
		}

		// Walking up onto the step it just made.
		if (this.stepTarget != null) {
			if (feet.equals(this.stepTarget)) {
				this.stepTarget = null;
				this.stepWalks = 0;
				this.steps++;
				this.turnsInARow = 0;
				return Status.RUNNING;
			}

			if (this.stepWalks++ > 2) {
				this.stepTarget = null;
				this.stepWalks = 0;
				return this.turn();
			}

			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.stepTarget), 0.4, "step up the staircase"));
		}

		// The next step: room over its head to jump (two above its feet), then the two cells it steps into, one up and
		// one along; the block in front at its feet's height is the step itself.
		BlockPos front = feet.relative(this.direction);
		BlockPos[] clear = {feet.above(2), front.above(), front.above(2)};
		for (BlockPos cell : clear) {
			if (!level.getFluidState(cell).isEmpty() || fluidBeside(level, cell)) {
				return this.turn();
			}

			BlockState state = level.getBlockState(cell);
			if (!state.getCollisionShape(level, cell).isEmpty() || !state.canBeReplaced()) {
				if (state.getDestroySpeed(level, cell) < 0.0F || !com.dinglebob.drifter.brain.KnownBlocks.mayTake(level, cell)
					|| level.getBlockState(cell.above()).getBlock() instanceof FallingBlock) {
					return this.turn();
				}

				return this.begin(this.dig(cell));
			}
		}

		// The step to stand on: solid, or a block put there over a small gap (never out over a drop, and not again where
		// it couldn't).
		if (level.getBlockState(front).getCollisionShape(level, front).isEmpty()) {
			boolean smallGap = !level.getBlockState(front.below()).getCollisionShape(level, front.below()).isEmpty()
				|| !level.getBlockState(front.below(2)).getCollisionShape(level, front.below(2)).isEmpty();
			if (!level.getFluidState(front).isEmpty() || Tools.buildingBlockCount(this.drifter) == 0 || !smallGap || this.placeFailed) {
				this.placeFailed = false;
				return this.turn();
			}

			return this.begin(new PlaceBlockTask(this.drifter, front, Tools.BUILDING_BLOCK, "a block for the step"));
		}

		this.stepTarget = front.above();
		return Status.RUNNING;
	}

	/** A quarter turn; boxed in on all four sides, it gives up. */
	private Status turn() {
		this.direction = this.direction.getClockWise();
		if (++this.turnsInARow >= 4) {
			return this.fail("can't dig up from here (water, lava or sand all round)");
		}

		return Status.RUNNING;
	}

	private static boolean fluidBeside(Level level, BlockPos pos) {
		for (Direction side : Direction.values()) {
			if (side != Direction.DOWN && !level.getFluidState(pos.relative(side)).isEmpty()) {
				return true;
			}
		}

		return false;
	}
}
