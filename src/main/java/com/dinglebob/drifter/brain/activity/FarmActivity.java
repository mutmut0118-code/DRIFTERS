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
	private static final int[] DISTANCES = {8, 10, 12, 15, 18, 22};
	/** Good cells (of 80) a plot needs: real ground is rarely perfect, and it tills what fits (found in testing). */
	private static final int MIN_GOOD = 20;
	/** Blocks of a patch it must have seen for it to count (it looked at it). */
	private static final int MIN_SEEN = 6;
	/** Seeds it gathers at once when it has none. */
	private static final int SEED_BATCH = 16;
	/** Steps per visit at most (it comes back for the rest). */
	private static final int MAX_STEPS = 120;

	private int steps;
	private int walksToWater;
	private int looks;
	/** Debug: the best plot score the last search found. */
	private static int lastBestScore;
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
			// Without a bucket: by water that's already there (a pond's or river's edge), as players do before they have iron
			// (found in testing: hunger cut every mining trip short, so it never got the iron for a bucket, and had no food).
			boolean bucket = this.drifter.getInventory().count(stack -> stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET)) > 0;
			BlockPos plot = bucket ? choosePlot(this.drifter, home) : choosePlotByWater(this.drifter, home);
			if (plot == null && !bucket) {
				home.farmFailedAt = level.getGameTime();
				return this.fail("no good ground by the water near home for a farm");
			}

			// It looks round home first (days underground push the ground at home out of its memory, found in testing),
			// then walks a little way out each side and looks again.
			if (plot == null && this.looks < 5) {
				this.looks++;
				if (this.looks == 1) {
					if (this.drifter.blockPosition().distSqr(home.center) > 6 * 6) {
						this.looks = 0;
						return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(home.center), 3.0, "go home to plan the farm"));
					}

					return this.begin(new com.dinglebob.drifter.executor.task.LookAroundTask(this.drifter));
				}

				Direction side = Direction.from2DDataValue(this.looks);
				BlockPos out = home.center.relative(side, 12);
				return this.begin(new com.dinglebob.drifter.executor.task.SequenceTask(this.drifter, "look for a place for the farm", List.of(
					new NavigateTask(this.drifter, Vec3.atBottomCenterOf(out), 4.0, "walk out to look for farmland", NavigateTask.Pace.STROLL),
					new com.dinglebob.drifter.executor.task.LookAroundTask(this.drifter))));
			}

			if (plot == null) {
				home.farmFailedAt = level.getGameTime();
				return this.fail("no flat ground near home for a farm (best patch " + lastBestScore + "/80 good)");
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

		// 4. Level the plot like a player: dig the bumps off, fill the dips (and puddles) with dirt (found in testing: real
		// ground near home was never flat enough for a farm).
		BlockPos bump = this.first(level, home.farm, (pos, state) -> pos.getY() == home.farm.getY() - 1 && bump(level, pos));
		if (bump != null) {
			BlockPos top = bump.above();
			return this.reachThen(top, () -> this.dig(top), "level the farm");
		}

		BlockPos dip = this.first(level, home.farm, (pos, state) -> pos.getY() == home.farm.getY() - 1 && dip(level, pos));
		if (dip != null) {
			if (this.drifter.getInventory().count(stack -> stack.is(Items.DIRT)) == 0) {
				return this.planFor(Items.DIRT, 8, "dirt");
			}

			return this.reachThen(dip, () -> new com.dinglebob.drifter.executor.task.PlaceBlockTask(this.drifter, dip, stack -> stack.is(Items.DIRT),
				"dirt to level the farm"), "fill the farm's dips");
		}

		// 5. Till the ground (grass or a flower on it first).
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

		// 6. Plant the farmland.
		BlockPos empty = this.first(level, home.farm, (pos, state) -> state.is(Blocks.FARMLAND) && level.getBlockState(pos.above()).isAir());
		if (empty != null) {
			if (this.drifter.getInventory().count(stack -> stack.is(Items.WHEAT_SEEDS)) == 0) {
				// Seeds from grass it knows of; with none about it doesn't go off looking (found in testing: it explored
				// for seeds until it was lost). The harvest gives more.
				if (!seedsAround(this.drifter)) {
					this.think("no seeds left and no grass about: the rest of the farm can wait");
					return Status.SUCCESS;
				}

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
		Home home = this.drifter.getDrifterBrain().home();
		for (BlockPos cell : cells(farm, home == null ? RADIUS : home.farmRadius)) {
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

	/** The soil cells of a full-size plot (the middle is the water). */
	public static List<BlockPos> cells(BlockPos farm) {
		return cells(farm, RADIUS);
	}

	/** The soil cells of a plot this far out from the water (its home's farm size). */
	public static List<BlockPos> cells(BlockPos farm, int radius) {
		List<BlockPos> cells = new ArrayList<>();
		BlockPos soilMiddle = farm.below();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
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
				// Where it remembers seeing water (its map of the area), or a look round for some (found in testing: a home
				// with no water nearby in view, and the farm never got its water).
				if (this.waterSearches++ < 3) {
					BlockPos wet = this.nearestWetChunk(home(this.drifter));
					if (wet != null) {
						this.think("going to fetch water for the farm");
						return this.begin(new SequenceTask(this.drifter, "go to the water", List.of(
							new NavigateTask(this.drifter, Vec3.atBottomCenterOf(wet), 8.0, "walk to the water"),
							new com.dinglebob.drifter.executor.task.LookAroundTask(this.drifter))));
					}

					return this.begin(new ExploreActivity(this.drifter, "looking for water", null, d -> nearestWater(d, 48) != null));
				}

				com.dinglebob.drifter.brain.Home farmHome = this.drifter.getDrifterBrain().home();
				if (farmHome != null) {
					farmHome.farmFailedAt = level.getGameTime();
				}

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

	private int waterSearches;

	private static BlockPos home(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		return home != null ? home.center : drifter.blockPosition();
	}

	/** The middle of the nearest chunk it remembers water in (within about 6 chunks of home), at home's height, or null. */
	@Nullable
	private BlockPos nearestWetChunk(BlockPos around) {
		net.minecraft.world.level.ChunkPos centre = new net.minecraft.world.level.ChunkPos(around);
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				com.dinglebob.drifter.memory.MentalMap.Tile tile = this.drifter.getMemory().map().get(new net.minecraft.world.level.ChunkPos(centre.x + dx, centre.z + dz));
				if (tile == null || !tile.water) {
					continue;
				}

				BlockPos middle = new BlockPos((centre.x + dx) * 16 + 8, around.getY(), (centre.z + dz) * 16 + 8);
				double distance = middle.distSqr(this.drifter.blockPosition());
				if (distance < bestDistance) {
					bestDistance = distance;
					best = middle;
				}
			}
		}

		return best;
	}

	public static boolean isWaterSource(Level level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos).isSource();
	}

	/** A block of ground sticking up out of the plot (the soil cell is solid ground, and so is the one above it). */
	private static boolean bump(Level level, BlockPos soil) {
		BlockState above = level.getBlockState(soil.above());
		// Only a one-high bump (room above it): bigger hills aren't a farm.
		return isGround(level.getBlockState(soil)) && isGround(above) && KnownBlocks.mayTake(level, soil.above())
			&& level.getBlockState(soil.above(2)).getCollisionShape(level, soil.above(2)).isEmpty();
	}

	/** A hole in the plot: no ground at the soil cell (air, a plant or water), ground under it. */
	private static boolean dip(Level level, BlockPos soil) {
		BlockState state = level.getBlockState(soil);
		boolean open = state.isAir() || state.canBeReplaced();
		return open && isGround(level.getBlockState(soil.below()));
	}

	private static boolean isGround(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
			|| state.is(Blocks.STONE) || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(Blocks.FARMLAND);
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
		return nearestWater(drifter, radius, Set.of());
	}

	/** The nearest water source block it has seen, not one of {@code skip}, or null. */
	@Nullable
	public static BlockPos nearestWater(DrifterEntity drifter, int radius, Set<BlockPos> skip) {
		Level level = drifter.level();
		BlockPos feet = drifter.blockPosition();
		BlockPos[] best = {null};
		double[] bestDistance = {Double.MAX_VALUE};
		drifter.getSenses().terrain().forEachKnown(feet, radius, pos -> {
			// Plain water with air over it: water with seagrass or kelp in it can't be scooped up with a bucket, nor water
			// under another block of water (found in testing: "filling its bucket" again and again).
			if (isWaterSource(level, pos) && level.getBlockState(pos).is(Blocks.WATER) && level.getBlockState(pos.above()).isAir() && !skip.contains(pos)) {
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
					// It has to have looked at the patch (some of it seen); the open ground's surface is then taken in at a glance,
					// as a player does (its sight samples are sparse, so block by block it had seen only 9-16 of 80; found in
					// testing).
					int seen = 0;
					int score = 0;
					if (inFrontOfDoor(home, middle)) {
						continue;
					}

					for (BlockPos soil : cells(middle, home.farmRadius)) {
						if (drifter.getSenses().terrain().isKnown(soil) || drifter.getSenses().terrain().isKnown(soil.above())) {
							seen++;
						}

						if ((canTill(level, soil) || bump(level, soil) || dip(level, soil)) && level.canSeeSky(soil.above(2))) {
							score++;
						}
					}

					if (seen < MIN_SEEN) {
						continue;
					}

					if (canTill(level, middle.below()) && score > bestScore && score >= minGood(home)) {
						bestScore = score;
						best = middle.immutable();
					}
				}
			}

			if (bestScore >= 60) {
				break;
			}
		}

		lastBestScore = bestScore;
		return bestScore >= minGood(home) ? best : null;
	}

	/** Enough good ground for its farm's size (a quarter of it). */
	private static int minGood(Home home) {
		int side = 2 * home.farmRadius + 1;
		return Math.max(4, Math.min(MIN_GOOD, (side * side - 1) / 4));
	}

	/**
	 * In front of its hut's door (user's report, 2026-10-17: a farm right outside the door, walked over on the way in and
	 * out): to the sides or behind instead.
	 */
	private static boolean inFrontOfDoor(Home home, BlockPos middle) {
		if (home.hutDoor == null) {
			return false;
		}

		int dx = middle.getX() - home.center.getX();
		int dz = middle.getZ() - home.center.getZ();
		int along = dx * home.hutDoor.getStepX() + dz * home.hutDoor.getStepZ();
		int across = Math.abs(dx * home.hutDoor.getStepZ() - dz * home.hutDoor.getStepX());
		return along > 0 && across <= along + home.farmRadius;
	}

	/**
	 * A plot round a water source it has seen near home, at the water's level (the middle is the water itself), with
	 * enough ground round it to till. Null if there's none.
	 */
	@Nullable
	public static BlockPos choosePlotByWater(DrifterEntity drifter, Home home) {
		Level level = drifter.level();
		BlockPos best = null;
		int bestScore = 0;
		java.util.List<BlockPos> sources = new java.util.ArrayList<>();
		drifter.getSenses().terrain().forEachKnown(home.center, WATER_PLOT_RANGE, pos -> {
			if (isWaterSource(level, pos) && level.getBlockState(pos.above()).isAir() && sources.size() < 200) {
				sources.add(pos.immutable());
			}
		});

		for (BlockPos water : sources) {
			if (home.inOwnBuilding(water, 2) || inFrontOfDoor(home, water.above())) {
				continue;
			}

			BlockPos middle = water.above();
			int score = 0;
			for (BlockPos soil : cells(middle, home.farmRadius)) {
				if (canTill(level, soil) && level.canSeeSky(soil.above(2))) {
					score++;
				}
			}

			if (score > bestScore) {
				bestScore = score;
				best = middle;
			}
		}

		lastBestScore = bestScore;
		return bestScore >= minGood(home) ? best : null;
	}

	/** How far from home it looks for water to farm by. */
	private static final int WATER_PLOT_RANGE = 24;

	/** Anything to do on the farm right now (for the goal's urgency). */
	public static boolean workToDo(DrifterEntity drifter, Home home) {
		if (home.farm == null) {
			return true;
		}

		Level level = drifter.level();
		if (!isWaterSource(level, home.farm.below())) {
			return true;
		}

		boolean seeds = seedsAround(drifter);

		for (BlockPos soil : cells(home.farm, home.farmRadius)) {
			BlockState crop = level.getBlockState(soil.above());
			if (crop.getBlock() instanceof CropBlock wheat && wheat.isMaxAge(crop)) {
				return true;
			}

			if ((canTill(level, soil) || level.getBlockState(soil).is(Blocks.FARMLAND) && crop.isAir()) && seeds) {
				return true;
			}
		}

		return false;
	}

	/** Seeds on it, or grass it knows of nearby to get some from. */
	public static boolean seedsAround(DrifterEntity drifter) {
		return drifter.getInventory().count(stack -> stack.is(Items.WHEAT_SEEDS)) > 0
			|| KnownWorld.anyTakeable(drifter, state -> state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)
				|| state.is(Blocks.LARGE_FERN));
	}

	static boolean isLeaves(BlockState state) {
		return state.is(BlockTags.LEAVES);
	}
}
