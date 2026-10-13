package com.dinglebob.drifter.brain.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.Stations;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.executor.task.WaitTask;
import com.dinglebob.drifter.hands.Containers;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Smelting a batch (DESIGN.md section 10.4): walks to a furnace it knows, loads the input and just enough fuel,
 * waits by it until the batch is done, and takes the results out.
 * TODO(milestone 8): load it and go do a nearby chore instead of waiting (Efficient Drifters).
 */
public class SmeltActivity extends Activity {
	public static final int FURNACE_INPUT = 0;
	public static final int FURNACE_FUEL = 1;
	public static final int FURNACE_RESULT = 2;
	/** A furnace smelts one item per 10 s. */
	public static final int TICKS_PER_ITEM = 200;

	private final Predicate<ItemStack> input;
	private final String inputLabel;
	private final int count;
	private final Predicate<ItemStack> fuel;
	private final String fuelLabel;
	private final int fuelCount;
	private boolean walked;
	private boolean started;
	@org.jetbrains.annotations.Nullable
	private String stepFailure;

	public SmeltActivity(DrifterEntity drifter, Predicate<ItemStack> input, String inputLabel, int count, Predicate<ItemStack> fuel,
		String fuelLabel, int fuelCount) {
		super(drifter);
		this.input = input;
		this.inputLabel = inputLabel;
		this.count = count;
		this.fuel = fuel;
		this.fuelLabel = fuelLabel;
		this.fuelCount = fuelCount;
	}

	/** How many of this fuel it takes to smelt {@code items} items, or 0 if it doesn't burn. */
	public static int fuelNeeded(Item fuel, int items) {
		Map<Item, Integer> burnTimes = AbstractFurnaceBlockEntity.getFuel();
		Integer ticks = burnTimes.get(fuel);
		return ticks == null || ticks <= 0 ? 0 : Math.max(1, (int)Math.ceil(items * (double)TICKS_PER_ITEM / ticks));
	}

	@Override
	public String describe() {
		return "smelt " + this.count + " " + this.inputLabel;
	}

	@Override
	protected int timeoutTicks() {
		return this.count * TICKS_PER_ITEM * 3 + 20 * 60;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (status == Status.FAILURE) {
			this.stepFailure = step.describe() + ": " + step.failureReason();
		}
	}

	@Override
	protected Status next() {
		if (this.stepFailure != null) {
			return this.fail(this.stepFailure);
		}

		if (this.started) {
			return Status.SUCCESS;
		}

		BlockPos furnace = Stations.nearest(this.drifter, Blocks.FURNACE, Stations.USE_RANGE);
		if (furnace == null) {
			return this.fail("needs a furnace");
		}

		if (!Reach.canReach(this.drifter, furnace)) {
			if (this.walked) {
				return this.fail("can't get within reach of the furnace");
			}

			BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, furnace, Set.of());
			if (spot == null) {
				return this.fail("can't find a spot by the furnace");
			}

			this.walked = true;
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk to the furnace"));
		}

		this.started = true;
		List<Task> steps = new ArrayList<>();
		steps.add(new ContainerTask(this.drifter, furnace, ContainerTask.Mode.PUT, this.input, this.inputLabel, this.count,
			new int[]{FURNACE_INPUT}));
		steps.add(new ContainerTask(this.drifter, furnace, ContainerTask.Mode.PUT, this.fuel, this.fuelLabel, this.fuelCount,
			new int[]{FURNACE_FUEL}));
		steps.add(new WaitTask(this.drifter, "the furnace to finish", () -> finished(furnace), this.count * TICKS_PER_ITEM * 3 + 20 * 10));
		steps.add(new ContainerTask(this.drifter, furnace, ContainerTask.Mode.TAKE, stack -> true, "smelted items", Integer.MAX_VALUE,
			new int[]{FURNACE_RESULT}));
		return this.begin(new SequenceTask(this.drifter, this.describe(), steps));
	}

	private boolean finished(BlockPos furnace) {
		Container container = Containers.get(this.drifter.level(), furnace);
		if (container == null) {
			return true;
		}

		// Done when the last input item is cooked, or the fuel ran out and the fire went out (partial batch).
		boolean lit = this.drifter.level().getBlockState(furnace).getOptionalValue(AbstractFurnaceBlock.LIT).orElse(false);
		return container.getItem(FURNACE_INPUT).isEmpty() || !lit && container.getItem(FURNACE_FUEL).isEmpty();
	}
}
