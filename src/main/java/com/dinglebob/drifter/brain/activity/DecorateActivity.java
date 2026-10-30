package com.dinglebob.drifter.brain.activity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.KnownWorld;
import com.dinglebob.drifter.brain.Planner;
import com.dinglebob.drifter.building.Decor;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.executor.task.UseOnBlockTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Decorating its house (DecorateGoal): a rug of carpet laid on the open floor of the main room, the nearest first, then a
 * painting hung on a blank wall. Carpet and the painting made or fetched the usual way (the planner: wool from its chests or
 * from sheep, sticks from planks); in the colour of the wool it has most of, else white.
 */
public class DecorateActivity extends Activity {
	private static final int MAX_GETS = 12;

	@Nullable
	private List<BlockPos> rug;
	private boolean rugLooked;
	@Nullable
	private Decor.Wall wall;
	private boolean wallLooked;
	@Nullable
	private Item carpet;
	private final Set<BlockPos> walkedTo = new HashSet<>();
	private int gets;
	private int laid;
	private boolean hung;
	@Nullable
	private String stepFailure;

	public DecorateActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "decorate its house";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 5;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		return false;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof PlaceBlockTask && status == Status.SUCCESS) {
			this.laid++;
		} else if (step instanceof SequenceTask && status == Status.SUCCESS && this.wall != null) {
			this.hung = !this.drifter.level().getEntitiesOfClass(net.minecraft.world.entity.decoration.Painting.class,
				new net.minecraft.world.phys.AABB(this.wall.wall().relative(this.wall.face())).inflate(1.5)).isEmpty();
		} else if (status == Status.FAILURE && !(step instanceof NavigateTask) && !(step instanceof PlaceBlockTask)) {
			// (Getting carpet or a painting: a step that didn't work ends it, rather than the same step again at once.)
			this.stepFailure = step.describe() + ": " + step.failureReason();
		}
	}

	@Override
	protected Status next() {
		Home home = this.drifter.getDrifterBrain().home();
		if (home == null) {
			return this.fail("has no home");
		}

		if (this.stepFailure != null) {
			return this.finish(this.stepFailure);
		}

		if (!this.rugLooked) {
			this.rugLooked = true;
			// (As big as the wool it has makes: two wool make three carpet; a 3 by 3 is six.)
			this.carpet = carpetColour(this.drifter);
			Item carpet = this.carpet;
			Item wool = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(BuiltInRegistries.ITEM.getKey(carpet).getPath().replace("_carpet", "_wool")));
			// (A wool kept back for a painting, when it has none and there's a wall for one.)
			int spare = Decor.painting(this.drifter.level(), home) != null && this.drifter.getInventory().count(stack -> stack.is(Items.PAINTING)) == 0 ? 1 : 0;
			int makeable = this.drifter.getInventory().count(stack -> stack.is(carpet))
				+ Math.max(0, this.drifter.getInventory().count(stack -> stack.is(wool)) - spare) / 2 * 3;
			this.rug = Decor.rug(this.drifter.level(), home, makeable);
			if (this.rug != null) {
				this.rug = new ArrayList<>(this.rug);
				this.think("laying a rug in the house");
			}
		}

		if (this.rug != null) {
			this.rug.removeIf(cell -> !this.drifter.level().getBlockState(cell).isAir());
			if (!this.rug.isEmpty()) {
				return this.layRug();
			}

			this.rug = null;
		}

		if (!this.wallLooked) {
			this.wallLooked = true;
			this.wall = Decor.painting(this.drifter.level(), home);
		}

		if (this.wall != null && !this.hung) {
			return this.hangPainting();
		}

		return this.finish(null);
	}

	/** It has done what it came to do (the goal stays keen on it till then). */
	public boolean finished() {
		return this.finished;
	}

	private boolean finished;

	private Status finish(@Nullable String why) {
		this.finished = true;
		if (this.laid > 0 || this.hung) {
			this.think(this.laid > 0 && this.hung ? "laid a rug and hung a painting: feels like home"
				: this.laid > 0 ? "laid a rug: feels more like home" : "hung a painting: feels more like home");
			return Status.SUCCESS;
		}

		return why == null ? Status.SUCCESS : this.fail(why);
	}

	private Status layRug() {
		Item carpet = this.carpet;
		int have = this.drifter.getInventory().count(stack -> stack.is(carpet));
		if (have == 0) {
			return this.get(carpet, this.rug.size());
		}

		BlockPos cell = this.rug.stream().min(java.util.Comparator.comparingDouble(pos -> pos.distSqr(this.drifter.blockPosition()))).get();
		// (It clicks the floor under the spot: that's what has to be in reach.)
		if (!Reach.canReach(this.drifter, cell.below()) || this.drifter.blockPosition().equals(cell)) {
			if (!this.walkedTo.add(cell)) {
				this.think("can't get at the floor at " + cell.toShortString() + " for the rug");
				this.rug.remove(cell);
				return Status.RUNNING;
			}

			// (Not standing on the rug's own cells: it can't put carpet where it stands.)
			BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, cell.below(), new HashSet<>(this.rug));
			if (spot == null) {
				this.think("nowhere to stand to lay the rug at " + cell.toShortString());
				this.rug.remove(cell);
				return Status.RUNNING;
			}

			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.4, "walk over to lay the rug", NavigateTask.Pace.STROLL));
		}

		this.rug.remove(cell);
		return this.begin(new PlaceBlockTask(this.drifter, cell, stack -> stack.is(carpet), "a rug"));
	}

	private Status hangPainting() {
		Decor.Wall wall = this.wall;
		if (this.drifter.getInventory().count(stack -> stack.is(Items.PAINTING)) == 0) {
			return this.get(Items.PAINTING, 1);
		}

		BlockPos front = wall.wall().relative(wall.face());
		if (!Reach.canReach(this.drifter, wall.wall())) {
			if (!this.walkedTo.add(wall.wall())) {
				this.wall = null;
				return Status.RUNNING;
			}

			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(front.below().above()), 1.5, "walk over to hang a painting",
				NavigateTask.Pace.STROLL));
		}

		return this.begin(new SequenceTask(this.drifter, "hang a painting", List.of(
			new SelectItemTask(this.drifter, stack -> stack.is(Items.PAINTING), "a painting"),
			new UseOnBlockTask(this.drifter, wall.wall(), wall.face(), "hang a painting"))));
	}

	/** Carpet or a painting the usual way (made at its crafting table from what it has or fetches, wool from sheep). */
	private Status get(Item item, int count) {
		if (++this.gets > MAX_GETS) {
			return this.finish("couldn't get " + item.getDescription().getString().toLowerCase(java.util.Locale.ROOT));
		}

		Planner.Plan plan = Planner.plan(this.drifter, KnownWorld.scan(this.drifter), item, count);
		if (!plan.feasible() || plan.next() == null) {
			// No way to make it just now: the rug or the painting is left for another day.
			this.think("no way to get " + item.getDescription().getString().toLowerCase(java.util.Locale.ROOT) + " just now ("
				+ (plan.feasible() ? "nothing to do" : "no plan") + ")");
			if (item == Items.PAINTING) {
				this.wall = null;
			} else {
				this.rug = null;
			}

			return Status.RUNNING;
		}

		return this.begin(Goals.activityFor(this.drifter, plan.next()));
	}

	/** The carpet of the wool it has most of (two make three), else white. */
	private static Item carpetColour(DrifterEntity drifter) {
		Item best = Items.WHITE_CARPET;
		int most = 0;
		for (Item wool : BuiltInRegistries.ITEM) {
			if (!new ItemStack(wool).is(ItemTags.WOOL)) {
				continue;
			}

			int have = drifter.getInventory().count(stack -> stack.is(wool));
			Item carpet = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(
				BuiltInRegistries.ITEM.getKey(wool).getPath().replace("_wool", "_carpet")));
			if (have > most && carpet != Items.AIR) {
				best = carpet;
				most = have;
			}
		}

		return best;
	}
}
