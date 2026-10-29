package com.dinglebob.drifter.brain.activity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.KnownWorld;
import com.dinglebob.drifter.brain.Planner;
import com.dinglebob.drifter.building.ChestLabels;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.CraftTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Labelling its house's chests (the user's furnishing guidebook: "a label on every chest"): signs made or fetched the
 * usual way (planks from its chests, or wood it goes and gets), then a sign on the front of each chest, crouching so the
 * chest doesn't open, saying what's in it; one that says something else now is written over.
 */
public class LabelChestsActivity extends Activity {
	/** Signs it makes at a time (a recipe makes three). */
	private static final int SIGNS = 3;
	private static final int MAX_GETS = 10;
	/** Planks a sign recipe takes; how near its chests the table it makes them at is. */
	private static final int PLANKS = 6;
	private static final double TABLE_NEAR = 12.0;
	private boolean walkedToTable;

	private final Set<BlockPos> tried = new HashSet<>();
	@Nullable
	private ChestLabels.Job job;
	private boolean walked;
	private int gets;
	private int done;

	public LabelChestsActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "label its chests";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 4;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof PlaceBlockTask && this.job != null) {
			if (status == Status.SUCCESS && this.drifter.level().getBlockEntity(this.job.front()) instanceof SignBlockEntity sign) {
				ChestLabels.write(sign, this.job.text());
				this.done++;
				this.think("labelled a chest: " + this.job.text().toLowerCase(java.util.Locale.ROOT));
			}

			this.tried.add(this.job.chest());
			this.job = null;
			this.walked = false;
		} else if (status == Status.FAILURE && !(step instanceof NavigateTask)) {
			// (Getting signs: a step that didn't work ends it, rather than the same step again at once.)
			this.stepFailure = step.describe() + ": " + step.failureReason();
		}
	}

	@Nullable
	private String stepFailure;

	@Override
	protected Status next() {
		Home home = this.drifter.getDrifterBrain().home();
		if (home == null) {
			return this.fail("has no home");
		}

		if (this.stepFailure != null) {
			return this.fail(this.stepFailure);
		}

		if (this.job == null) {
			List<ChestLabels.Job> jobs = ChestLabels.wanted(this.drifter, home).stream().filter(job -> !this.tried.contains(job.chest())).toList();
			if (jobs.isEmpty()) {
				return this.done > 0 ? Status.SUCCESS : this.fail("no chest it could label");
			}

			// The nearest first.
			this.job = jobs.stream().min(java.util.Comparator.comparingDouble(job -> job.front().distSqr(this.drifter.blockPosition()))).get();
			this.walked = false;
		}

		ChestLabels.Job job = this.job;
		Item sign = ChestLabels.carried(this.drifter);
		if (!job.sign() && sign == null) {
			if (++this.gets > MAX_GETS) {
				return this.fail("couldn't get any signs");
			}

			// Six planks of one wood and a stick, made into signs at the crafting table by its chests (the storage wall's).
			Item make = ChestLabels.signToMake(this.drifter);
			Item planks = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(
				BuiltInRegistries.ITEM.getKey(make).getPath().replace("_sign", "_planks")));
			int have = this.drifter.getInventory().count(stack -> stack.is(planks));
			boolean stick = this.drifter.getInventory().count(stack -> stack.is(Items.STICK)) > 0;
			if (have >= PLANKS && stick) {
				MemoryEntry table = this.drifter.getMemory().nearest(MemoryKind.STATION, this.drifter.level().dimension().location(),
					Vec3.atCenterOf(job.chest()), TABLE_NEAR, entry -> entry.label.equals("minecraft:crafting_table"));
				if (table == null) {
					return this.fail("no crafting table by its chests");
				}

				if (!Reach.canReach(this.drifter, table.blockPos())) {
					BlockPos spot = this.walkedToTable ? null : KnownBlocks.standingSpotFor(this.drifter, table.blockPos(), Set.of());
					if (spot == null) {
						return this.fail("can't get to its crafting table");
					}

					this.walkedToTable = true;
					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk to the crafting table"));
				}

				return this.begin(new CraftTask(this.drifter, stack -> stack.is(make), "signs", 1, table.blockPos()));
			}

			// The planks or the stick the usual way (from its chests, or wood it gets).
			Item want = have < PLANKS ? planks : Items.STICK;
			Planner.Plan plan = Planner.plan(this.drifter, KnownWorld.scan(this.drifter), want, have < PLANKS ? PLANKS - have : 1);
			if (!plan.feasible() || plan.next() == null) {
				return this.fail("can't work out how to make signs");
			}

			return this.begin(Goals.activityFor(this.drifter, plan.next()));
		}

		// (The chest's face it clicks, or the sign it writes over.)
		BlockPos at = job.sign() ? job.front() : job.chest();
		if (!Reach.canReach(this.drifter, at)) {
			BlockPos spot = this.walked ? null : KnownBlocks.standingSpotFor(this.drifter, at, Set.of());
			if (spot == null) {
				this.think("can't get at the chest at " + job.chest().toShortString() + " to label it");
				this.tried.add(job.chest());
				this.job = null;
				return Status.RUNNING;
			}

			this.walked = true;
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk to the chest"));
		}

		if (job.sign()) {
			// (Clicked and written over, as a player edits a sign.)
			if (this.drifter.level().getBlockEntity(job.front()) instanceof SignBlockEntity existing) {
				ChestLabels.write(existing, job.text());
				this.done++;
				this.think("changed a chest's label to " + job.text().toLowerCase(java.util.Locale.ROOT));
			}

			this.tried.add(job.chest());
			this.job = null;
			return Status.RUNNING;
		}

		BlockState wall = ChestLabels.wallSign(sign).defaultBlockState().setValue(WallSignBlock.FACING, job.facing());
		// (Crouching: a click on a chest otherwise opens it.)
		return this.begin(PlaceBlockTask.exactly(this.drifter, job.front(), wall, "a sign").single());
	}
}
