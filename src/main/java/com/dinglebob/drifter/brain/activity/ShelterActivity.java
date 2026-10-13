package com.dinglebob.drifter.brain.activity;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.Shelter;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.executor.task.WaitTask;
import com.dinglebob.drifter.hands.Tools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The night-one shelter (DESIGN.md section 11, tier 1 "panic shelter": "digs down and covers the hole"): digs three
 * blocks straight down where it stands, puts a block over its head, waits there until morning (by its own sense of
 * time), then digs out and climbs back up. Mobs can't reach it and can't spawn in the 1x2 space it fills.
 * TODO(milestone 8): tier 2 hillside dig-in with a 3x3 room, tier 3 starter hut, sleeping in a bed.
 */
public class ShelterActivity extends Activity {
	public static final int DEPTH = 3;
	/** Digging a block slower than this (seconds) isn't worth it for a shelter: find another spot. */
	private static final float MAX_DIG_SECONDS = 4.0F;
	private static final int MAX_SPOT_TRIES = 4;

	private enum Phase {
		DIG, SEAL, WAIT, EXIT, DONE
	}

	private Phase phase = Phase.DIG;
	@Nullable
	private BlockPos top;
	private int spotTries;
	private int offGroundTicks;
	private boolean soughtDryGround;

	public ShelterActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return switch (this.phase) {
			case DIG -> "dig in for the night";
			case SEAL -> "seal the shelter";
			case WAIT -> "wait for morning in the shelter";
			case EXIT, DONE -> "climb out of the shelter";
		};
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 15;
	}

	@Override
	protected void onStart() {
		if (Shelter.isSealedIn(this.drifter)) {
			this.phase = Phase.WAIT;
		}
	}

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		BlockPos feet = this.drifter.blockPosition();
		switch (this.phase) {
			case DIG -> {
				if (this.drifter.isInWater() || this.drifter.onClimbable()) {
					// Can't dig in while swimming or on a ladder: get to dry ground first.
					BlockPos dry = this.soughtDryGround ? null : KnownBlocks.nearestStandable(this.drifter, 16);
					if (dry == null) {
						return this.fail("can't dig in while in water or climbing");
					}

					this.soughtDryGround = true;
					this.top = null;
					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(dry), 1.0, "get to dry ground"));
				}

				if (!this.drifter.onGround()) {
					return ++this.offGroundTicks > 40 ? this.fail("can't get its feet on the ground to dig in") : Status.RUNNING;
				}

				this.offGroundTicks = 0;
				if (this.top == null) {
					this.top = feet;
					this.think("digging in for the night");
				}

				int depth = this.top.getY() - feet.getY();
				if (depth >= DEPTH) {
					this.phase = Phase.SEAL;
					return Status.RUNNING;
				}

				// Stand in the middle of the block first: its body is 0.6 wide, so off-centre it would stay standing on the
				// rim of its own hole instead of dropping in (found in review).
				Vec3 middle = Vec3.atBottomCenterOf(this.top);
				if (depth == 0 && (Math.abs(this.drifter.getX() - middle.x) > 0.2 || Math.abs(this.drifter.getZ() - middle.z) > 0.2)) {
					return this.begin(new CenterOnBlockTask(this.drifter, middle));
				}

				BlockPos below = depth == 0 ? this.top.below() : feet.below();
				if (!this.diggable(level, below) || !this.solidFloor(level, below.below())) {
					return this.tryAnotherSpot(depth);
				}

				return this.begin(this.dig(below));
			}
			case SEAL -> {
				BlockPos cover = feet.above(2);
				if (!level.getBlockState(cover).getCollisionShape(level, cover).isEmpty()) {
					this.phase = Phase.WAIT;
					this.think("sealed in for the night");
					return Status.RUNNING;
				}

				if (this.drifter.getInventory().count(ShelterActivity::isCoverBlock) == 0) {
					return this.fail("has no block to cover the hole with");
				}

				java.util.function.Predicate<ItemStack> block = this.drifter.getInventory().count(Tools.BUILDING_BLOCK) > 0
					? Tools.BUILDING_BLOCK : ShelterActivity::isCoverBlock;
				return this.begin(new PlaceBlockTask(this.drifter, cover, block, "a block over its head"));
			}
			case WAIT -> {
				if (!Schedule.isNight(this.drifter) && Schedule.believedTime(this.drifter) < Schedule.SHELTER_THINKING) {
					this.phase = Phase.EXIT;
					this.think("it should be morning: climbing out");
					return Status.RUNNING;
				}

				return this.begin(new WaitTask(this.drifter, "morning", () -> !Schedule.isNight(this.drifter)
					&& Schedule.believedTime(this.drifter) < Schedule.SHELTER_THINKING, 20 * 60));
			}
			case EXIT -> {
				BlockPos cover = feet.above(2);
				if (!level.getBlockState(cover).getCollisionShape(level, cover).isEmpty()) {
					return this.begin(this.dig(cover));
				}

				this.phase = Phase.DONE;
				BlockPos surface = this.top != null ? this.top : feet.above(DEPTH);
				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(surface), 1.5, "climb out"));
			}
			default -> {
				return Status.SUCCESS;
			}
		}
	}

	@Override
	protected boolean countsAsFailure(com.dinglebob.drifter.executor.Task step) {
		return !(step instanceof WaitTask);
	}

	@Override
	public boolean atCheckpoint() {
		// Only once sealed in (eating can happen in there); never half dug.
		return this.phase == Phase.WAIT && super.atCheckpoint();
	}

	/** Phase for the overlay and the selector: is it already safe? */
	public boolean isSealed() {
		return this.phase == Phase.WAIT;
	}

	private Status tryAnotherSpot(int depth) {
		if (depth > 0 || ++this.spotTries > MAX_SPOT_TRIES) {
			return this.fail("can't dig in here");
		}

		BlockPos here = this.drifter.blockPosition();
		Direction direction = Direction.from2DDataValue(this.drifter.getRandom().nextInt(4));
		BlockPos spot = here.relative(direction, 3);
		this.top = null;
		this.think("the ground here is no good for digging in; trying a few blocks over");
		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 1.5, "find a spot to dig in"));
	}

	/** Shuffles to the middle of the block it stands on, slowly (sneaking), with the movement keys. */
	private static final class CenterOnBlockTask extends com.dinglebob.drifter.executor.Task {
		private final Vec3 middle;

		CenterOnBlockTask(DrifterEntity drifter, Vec3 middle) {
			super(drifter);
			this.middle = middle;
		}

		@Override
		public String describe() {
			return "step to the middle of the block";
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 3;
		}

		@Override
		protected Status onTick() {
			if (Math.abs(this.drifter.getX() - this.middle.x) <= 0.15 && Math.abs(this.drifter.getZ() - this.middle.z) <= 0.15) {
				com.dinglebob.drifter.motor.Steering.stopMoving(this.drifter);
				this.drifter.getKeys().set(com.dinglebob.drifter.motor.VirtualKeys.Key.SNEAK, false);
				return Status.SUCCESS;
			}

			this.drifter.getKeys().set(com.dinglebob.drifter.motor.VirtualKeys.Key.SNEAK, true);
			com.dinglebob.drifter.motor.Steering.pressToward(this.drifter, this.middle);
			return Status.RUNNING;
		}

		@Override
		protected void onStop() {
			com.dinglebob.drifter.motor.Steering.stopMoving(this.drifter);
			this.drifter.getKeys().set(com.dinglebob.drifter.motor.VirtualKeys.Key.SNEAK, false);
		}
	}

	/** Dirt, stone and the like: no sand or gravel (it would pour in), no fluids beside it, quick enough to dig. */
	private boolean diggable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getCollisionShape(level, pos).isEmpty() || state.getBlock() instanceof FallingBlock || !KnownBlocks.mayTake(level, pos)
			|| state.getDestroySpeed(level, pos) < 0.0F || Tools.digSeconds(this.drifter, state, level, pos) > MAX_DIG_SECONDS) {
			return false;
		}

		for (Direction direction : Direction.values()) {
			if (!level.getFluidState(pos.relative(direction)).isEmpty()) {
				return false;
			}
		}

		return true;
	}

	private boolean solidFloor(Level level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
	}

	private static boolean isCoverBlock(ItemStack stack) {
		return Tools.BUILDING_BLOCK.test(stack) || stack.getItem() instanceof BlockItem block
			&& block.getBlock().defaultBlockState().isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
			&& !(block.getBlock() instanceof FallingBlock);
	}
}
