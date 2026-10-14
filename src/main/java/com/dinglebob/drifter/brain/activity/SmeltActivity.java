package com.dinglebob.drifter.brain.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.FurnaceJob;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.Stations;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Starting a batch (DESIGN.md section 10.4): walks to a furnace it knows, loads the input and just enough fuel, and
 * leaves it cooking: it remembers the batch ({@link FurnaceJob}) and goes off to do something else, coming back when
 * it should be done (user's rule: "put raw iron in the furnace, go hunt, come back to find it all smelted").
 */
public class SmeltActivity extends Activity {
	public static final int FURNACE_INPUT = 0;
	public static final int FURNACE_FUEL = 1;
	public static final int FURNACE_RESULT = 2;
	/** A furnace smelts one item per 10 s. */
	public static final int TICKS_PER_ITEM = 200;
	/** Smokers (food) and blast furnaces (ores) take half that. */
	public static final int FAST_TICKS_PER_ITEM = 100;

	private final Item output;
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

	@Nullable
	private BlockPos furnace;

	public SmeltActivity(DrifterEntity drifter, Item output, Predicate<ItemStack> input, String inputLabel, int count, Predicate<ItemStack> fuel,
		String fuelLabel, int fuelCount) {
		super(drifter);
		this.output = output;
		this.input = input;
		this.inputLabel = inputLabel;
		this.count = count;
		this.fuel = fuel;
		this.fuelLabel = fuelLabel;
		this.fuelCount = fuelCount;
	}

	/** Ticks per item for the furnace-like block at this spot. */
	public static int ticksPerItem(net.minecraft.world.level.Level level, BlockPos pos) {
		var state = level.getBlockState(pos);
		return state.is(Blocks.SMOKER) || state.is(Blocks.BLAST_FURNACE) ? FAST_TICKS_PER_ITEM : TICKS_PER_ITEM;
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
		return 20 * 60;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (status == Status.FAILURE) {
			this.stepFailure = step.describe() + ": " + step.failureReason();
		} else if (this.started && this.furnace != null) {
			// Loaded: remember the batch and go do something else.
			int ticks = ticksPerItem(this.drifter.level(), this.furnace);
			this.drifter.getDrifterBrain().addFurnaceJob(new FurnaceJob(this.furnace, this.output, this.count,
				this.now() + (long)this.count * ticks + 20));
			this.think("left " + this.count + " " + this.inputLabel + " cooking; back in about " + (this.count * ticks / 20) + " s");
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

		// Food goes in a smoker if it knows one (twice as fast, same fuel per item), otherwise a furnace.
		BlockPos furnace = null;
		if (new ItemStack(this.output).has(net.minecraft.core.component.DataComponents.FOOD)) {
			furnace = Stations.usable(this.drifter, Blocks.SMOKER);
		}

		if (furnace == null) {
			furnace = Stations.usable(this.drifter, Blocks.FURNACE);
		}

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
		this.furnace = furnace;
		List<Task> steps = new ArrayList<>();
		steps.add(new ContainerTask(this.drifter, furnace, ContainerTask.Mode.PUT, this.input, this.inputLabel, this.count,
			new int[]{FURNACE_INPUT}));
		steps.add(new ContainerTask(this.drifter, furnace, ContainerTask.Mode.PUT, this.fuel, this.fuelLabel, this.fuelCount,
			new int[]{FURNACE_FUEL}));
		return this.begin(new SequenceTask(this.drifter, this.describe(), steps));
	}
}
