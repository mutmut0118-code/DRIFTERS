package com.dinglebob.drifter.brain.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.KnownWorld;
import com.dinglebob.drifter.brain.Planner;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.executor.task.UseItemTask;
import com.dinglebob.drifter.executor.task.UseOnBlockTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The wheat farm (DESIGN.md section 9, stage 6 "Settlement": a 9x9 wheat farm; milestone 8 step 5). Like a player: a flat
 * 9x9 patch of ground near home, water in the middle (that keeps all of it wet), tilled with a hoe, planted with seeds
 * from breaking grass, and later harvested when ripe and replanted. It walks (never jumps) on the farm: jumping on
 * farmland tramples it.
 */
public class FarmActivity extends Activity {
	/** The plot reaches this far from its middle (9x9). */
	public static final int RADIUS = 4;
	/** How far from home it looks for a plot. */
	private static final int[] DISTANCES = {9, 11, 13};
	/** Seeds it gathers at once when it has none. */
	private static final int SEED_BATCH = 16;
	/** Steps per visit at most (it comes back for the rest). */
	private static final int MAX_STEPS = 120;

	private int steps;
	private int walksToWater;
	private int harvested;
	private final Set<BlockPos> skipped = new java.util.HashSet<>();
	private final Set<BlockPos> walkedTo = new java.util.HashSet<>();

	public FarmActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "work on the farm";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 6;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		// A cell it couldn't reach or click is skipped; the farm carries on.
		return false;
	}

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		Home home = this.drifter.getDrifterBrain().home();
		if (home == null) {
			return this.fail("has no home");
		}

		if (home.farm == null) {
			BlockPos plot = choosePlot(this.drifter, home);
			if (plot == null) {
				return this.fail("no flat ground near home for a farm");
			}

			home.farm = plot;
			this.think("starting a wheat farm at " + plot.toShortString());
		}

		if (++this.steps > MAX_STEPS) {
			return Status.SUCCESS;
		}

		BlockPos water = home.farm.below();
		// 1. Ripe wheat: harvest it (it drops wheat and seeds), pick the drops up now and then.
		BlockPos ripe = this.first(level, home.farm, (pos, state) -> state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state));
		if (ripe != null) {
			this.harvested++;
			return this.reachThen(ripe, () -> this.dig(ripe), "harvest the wheat");
		}

		if (this.harvested > 0) {
			this.harvested = 0;
			return this.begin(new CollectItemsTask(this.drifter, RADIUS + 3));
		}

		// 2. Water in the middle (a water bucket; it fills its bucket at water it knows first).
		if (!isWaterSource(level, water)) {
			return this.water(level, water);
		}

		// 3. A hoe.
		if (this.drifter.getInventory().count(stack -> stack.is(ItemTags.HOES)) == 0) {
			return this.planFor(Items.WOODEN_HOE, 1, "a hoe");
		}

		// 4. Till the ground (grass or a flower on it first).
		BlockPos till = this.first(level, home.farm, (pos, state) -> canTill(level, pos));
		if (till != null) {
			BlockPos above = till.above();
			if (!level.getBlockState(above).isAir()) {
				return this.reachThen(above, () -> this.dig(above), "clear the plot");
			}

			return this.reachThen(till, () -> new SequenceTask(this.drifter, "till the ground", List.of(
				new SelectItemTask(this.drifter, stack -> stack.is(ItemTags.HOES), "a hoe"),
				new UseOnBlockTask(this.drifter, till, Direction.UP, "till " + till.toShortString()))), "till the ground");
		}

		// 5. Plant the farmland.
		BlockPos empty = this.first(level, home.farm, (pos, state) -> state.is(Blocks.FARMLAND) && level.getBlockState(pos.above()).isAir());
		if (empty != null) {
			if (this.drifter.getInventory().count(stack -> stack.is(Items.WHEAT_SEEDS)) == 0) {
				return this.planFor(Items.WHEAT_SEEDS, SEED_BATCH, "seeds");
			}

			return this.reachThen(empty, () -> new SequenceTask(this.drifter, "plant seeds", List.of(
				new SelectItemTask(this.drifter, stack -> stack.is(Items.WHEAT_SEEDS), "seeds"),
				new UseOnBlockTask(this.drifter, empty, Direction.UP, "plant at " + empty.toShortString()))), "plant seeds");
		}

		return Status.SUCCESS;
	}

	@FunctionalInterface
	private interface CellTest {
		boolean test(BlockPos soil, BlockState state);
	}

	/** The nearest farm cell (the soil level, not the middle) passing the test that it hasn't given up on, or null. */
	@Nullable
	private BlockPos first(Level level, BlockPos farm, CellTest test) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos cell : cells(farm)) {
			BlockPos soil = cell;
			BlockPos crop = cell.above();
			BlockPos pos = test.test(soil, level.getBlockState(soil)) ? soil : test.test(crop, level.getBlockState(crop)) ? crop : null;
			if (pos == null || this.skipped.contains(pos)) {
				continue;
			}

			double distance = pos.distToCenterSqr(this.drifter.position());
			if (distance < bestDistance) {
				bestDistance = distance;
				best = pos.immutable();
			}
		}

		return best;
	}

	/** The soil cells of the plot (the middle is the water). */
	public static List<BlockPos> cells(BlockPos farm) {
		List<BlockPos> cells = new ArrayList<>();
		BlockPos soilMiddle = farm.below();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (dx != 0 || dz != 0) {
					cells.add(soilMiddle.offset(dx, 0, dz));
				}
			}
		}

		return cells;
	}

	/** In reach: the step. Otherwise a walk (never a jump: that tramples farmland) to where it can reach it. */
	private Status reachThen(BlockPos pos, java.util.function.Supplier<Task> step, String what) {
		if (Reach.canReach(this.drifter, pos)) {
			return this.begin(step.get());
		}

		// Walked over once and still can't reach it: it leaves that cell for now.
		BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, pos, Set.of());
		if (spot == null || !this.walkedTo.add(pos)) {
			this.skipped.add(pos);
			return Status.RUNNING;
		}

		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, what, NavigateTask.Pace.STROLL));
	}

	private Status planFor(net.minecraft.world.item.Item item, int count, String label) {
		Planner.Plan plan = Planner.plan(this.drifter, KnownWorld.scan(this.drifter), item, this.drifter.getInventory().count(stack -> stack.is(item)) + count);
		if (!plan.feasible() || plan.next() == null) {
			return this.fail("can't work out how to get " + label);
		}

		return this.begin(Goals.activityFor(this.drifter, plan.next()));
	}

	/** The middle: dig it out and pour water in (filling its bucket first if it has to). */
	private Status water(Level level, BlockPos hole) {
		if (this.drifter.getInventory().count(stack -> stack.is(Items.WATER_BUCKET)) == 0) {
			if (this.drifter.getInventory().count(stack -> stack.is(Items.BUCKET)) == 0) {
				return this.fail("needs a bucket for the farm's water");
			}

			BlockPos source = nearestWater(this.drifter, 48);
			if (source == null) {
				return this.fail("knows no water to fill its bucket at");
			}

			if (this.drifter.getEyePosition().distanceTo(Vec3.atCenterOf(source)) > this.drifter.blockInteractionRange() - 0.5) {
				BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, source, Set.of());
				if (spot != null) {
					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk to the water"));
				}

				// It doesn't know the ground round it yet: over there first, then it'll see where to stand.
				if (this.walksToWater++ > 2) {
					return this.fail("can't get to the water");
				}

				return this.begin(new NavigateTask(this.drifter, Vec3.atCenterOf(source), 3.0, "walk over to the water"));
			}

			this.think("filling its bucket");
			return this.begin(new SequenceTask(this.drifter, "fill the bucket", List.of(
				new SelectItemTask(this.drifter, stack -> stack.is(Items.BUCKET), "a bucket"),
				new LookAtTask(this.drifter, Vec3.atCenterOf(source).add(0.0, 0.4, 0.0), 4),
				new UseItemTask(this.drifter))));
		}

		// A hole first (it pours into the hole, so the water stays put).
		BlockState state = level.getBlockState(hole);
		if (!state.isAir() && level.getFluidState(hole).isEmpty()) {
			return this.reachThen(hole, () -> this.dig(hole), "dig the hole for the water");
		}

		if (this.drifter.getEyePosition().distanceTo(Vec3.atCenterOf(hole)) > 3.5) {
			return this.reachThen(hole.below(), () -> new LookAtTask(this.drifter, Vec3.atCenterOf(hole), 2), "go to the middle of the farm");
		}

		this.think("pouring the water in the middle of the farm");
		return this.begin(new SequenceTask(this.drifter, "pour the water", List.of(
			new SelectItemTask(this.drifter, stack -> stack.is(Items.WATER_BUCKET), "a water bucket"),
			new LookAtTask(this.drifter, Vec3.atBottomCenterOf(hole).add(0.0, 0.05, 0.0), 4),
			new UseItemTask(this.drifter))));
	}

	public static boolean isWaterSource(Level level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos).isSource();
	}

	/** Ground a hoe turns to farmland, with room above (plants it clears first). */
	private static boolean canTill(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		BlockState above = level.getBlockState(pos.above());
		return (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.COARSE_DIRT))
			&& (above.isAir() || above.canBeReplaced() && level.getFluidState(pos.above()).isEmpty());
	}

	/** The nearest water source block it has seen, or null. */
	@Nullable
	public static BlockPos nearestWater(DrifterEntity drifter, int radius) {
		Level level = drifter.level();
		BlockPos feet = drifter.blockPosition();
		BlockPos[] best = {null};
		double[] bestDistance = {Double.MAX_VALUE};
		drifter.getSenses().terrain().forEachKnown(feet, radius, pos -> {
			if (isWaterSource(level, pos) && !level.getFluidState(pos.above()).is(FluidTags.WATER)) {
				double distance = pos.distSqr(feet);
				if (distance < bestDistance[0]) {
					bestDistance[0] = distance;
					best[0] = pos.immutable();
				}
			}
		});
		return best[0];
	}

	/**
	 * A place for the farm near home: the middle of a 9x9 patch of grass or dirt it has seen, nearly all at one height
	 * with open sky, not where the hut is. The middle cell's position is at the level it stands on (the soil is below).
	 */
	@Nullable
	public static BlockPos choosePlot(DrifterEntity drifter, Home home) {
		Level level = drifter.level();
		BlockPos best = null;
		int bestScore = 0;
		for (int distance : DISTANCES) {
			for (int turn = 0; turn < 8; turn++) {
				double angle = Math.PI / 4.0 * turn;
				BlockPos around = home.center.offset((int)Math.round(Math.cos(angle) * distance), 0, (int)Math.round(Math.sin(angle) * distance));
				for (int dy = -2; dy <= 2; dy++) {
					BlockPos middle = around.above(dy);
					int score = 0;
					for (BlockPos soil : cells(middle)) {
						if (drifter.getSenses().terrain().isKnown(soil) && canTill(level, soil) && level.canSeeSky(soil.above())) {
							score++;
						}
					}

					if (canTill(level, middle.below()) && score > bestScore) {
						bestScore = score;
						best = middle.immutable();
					}
				}
			}

			if (bestScore >= 60) {
				break;
			}
		}

		return bestScore >= 50 ? best : null;
	}

	/** Anything to do on the farm right now (for the goal's urgency). */
	public static boolean workToDo(DrifterEntity drifter, Home home) {
		if (home.farm == null) {
			return true;
		}

		Level level = drifter.level();
		if (!isWaterSource(level, home.farm.below())) {
			return true;
		}

		for (BlockPos soil : cells(home.farm)) {
			BlockState crop = level.getBlockState(soil.above());
			if (crop.getBlock() instanceof CropBlock wheat && wheat.isMaxAge(crop)) {
				return true;
			}

			if (canTill(level, soil) || level.getBlockState(soil).is(Blocks.FARMLAND) && crop.isAir()) {
				return true;
			}
		}

		return false;
	}

	static boolean isLeaves(BlockState state) {
		return state.is(BlockTags.LEAVES);
	}
}
