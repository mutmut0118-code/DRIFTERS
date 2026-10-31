package com.dinglebob.drifter.brain.activity;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Planner;
import com.dinglebob.drifter.brain.Stations;
import com.dinglebob.drifter.compat.FarmersDelight;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.BoardTask;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.executor.task.WaitTask;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Frying in the skillet on the kitchen's stove (Farmer's Delight, optional): a right-click with the raw food puts it in, and
 * the skillet cooks it a piece at a time (about six seconds each), each one popping out beside it. It stays by the stove,
 * picking them up, until the skillet's empty.
 */
public class SkilletActivity extends KitchenActivity {
	/** Most it fries at a go (it stands there for each one). */
	private static final int MOST = 8;
	/** Ticks a piece, with a bit to spare (a campfire's time cut to a fifth). */
	private static final int TICKS_EACH = 20 * 7;

	private final Item raw;
	private boolean started;

	public SkilletActivity(DrifterEntity drifter, Item raw) {
		super(drifter);
		this.raw = raw;
	}

	/** A skillet on a lit stove it knows (its kitchen), or null. */
	@Nullable
	public static BlockPos usable(DrifterEntity drifter) {
		Block skillet = FarmersDelight.block("skillet");
		BlockPos pos = skillet == null ? null : Stations.usable(drifter, skillet);
		return pos != null && FarmersDelight.heatedFrom(drifter.level(), pos) ? pos : null;
	}

	/** This raw food fries in a skillet (it has a campfire recipe). */
	public static boolean fries(Level level, Item raw) {
		return level.getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(new ItemStack(raw)), level).isPresent();
	}

	@Override
	public String describe() {
		return "fry " + Planner.name(this.raw) + " in the skillet";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 40 + MOST * TICKS_EACH;
	}

	@Override
	protected void stepDone(com.dinglebob.drifter.executor.Task step, Status status) {
		// Once it's frying, what's done is done (a wait that ran long still leaves it the pieces it picked up).
		if (!this.started) {
			super.stepDone(step, status);
		}
	}

	@Override
	protected Status next() {
		if (this.stepFailure != null) {
			return this.fail(this.stepFailure);
		}

		BlockPos skillet = usable(this.drifter);
		if (skillet == null) {
			return this.started ? Status.SUCCESS : this.fail("knows no skillet on a lit stove");
		}

		if (this.started) {
			return Status.SUCCESS;
		}

		if (!FarmersDelight.inSkillet(this.drifter.level(), skillet).isEmpty()) {
			return this.fail("the skillet's in use");
		}

		Status walk = this.reach(skillet, "skillet");
		if (walk != null) {
			return walk;
		}

		int count = Math.min(MOST, this.drifter.getInventory().count(stack -> stack.is(this.raw)));
		if (count <= 0) {
			return this.fail("has no " + Planner.name(this.raw));
		}

		this.started = true;
		this.think("frying " + count + " " + Planner.name(this.raw) + " in the skillet");
		BlockPos at = skillet;
		List<com.dinglebob.drifter.executor.Task> steps = new java.util.ArrayList<>(List.of(
			new SelectItemTask(this.drifter, stack -> stack.is(this.raw), Planner.name(this.raw)),
			BoardTask.skillet(this.drifter, skillet, count, stack -> stack.is(this.raw), Planner.name(this.raw))));
		// It waits right in front of the stove: what pops out lands on the stove top or at its feet, in reach of its hands.
		BlockPos front = this.front(skillet.below());
		if (front != null) {
			steps.add(new com.dinglebob.drifter.executor.task.NavigateTask(this.drifter, net.minecraft.world.phys.Vec3.atBottomCenterOf(front), 0.4,
				"step up to the stove"));
		}

		steps.add(new WaitTask(this.drifter, "the skillet to finish", () -> FarmersDelight.inSkillet(this.drifter.level(), at).isEmpty(), count * TICKS_EACH + 40));
		steps.add(new WaitTask(this.drifter, "the last piece to land", landed(), 40));
		steps.add(new CollectItemsTask(this.drifter, 3.0));
		return this.begin(new SequenceTask(this.drifter, this.describe(), steps));
	}

	/** The floor in front of the stove on the side it's on (where a player stands at a stove), or null. */
	@Nullable
	private BlockPos front(BlockPos stove) {
		Direction toward = Direction.getNearest(this.drifter.getX() - stove.getX() - 0.5, 0.0, this.drifter.getZ() - stove.getZ() - 0.5);
		for (Direction side : new Direction[] {toward, toward.getClockWise(), toward.getCounterClockWise(), toward.getOpposite()}) {
			BlockPos pos = stove.relative(side);
			Level level = this.drifter.level();
			if (this.drifter.getSenses().terrain().isKnown(pos.below()) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
				&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
				return pos;
			}
		}

		return null;
	}

	/** A moment for the last piece to land. */
	private static java.util.function.BooleanSupplier landed() {
		int[] ticks = {0};
		return () -> ++ticks[0] >= 20;
	}
}
