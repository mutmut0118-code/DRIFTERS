package com.dinglebob.drifter.brain.activity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.KnownWorld;
import com.dinglebob.drifter.brain.Planner;
import com.dinglebob.drifter.building.plan.HousePalette;
import com.dinglebob.drifter.building.plan.HousePlan;
import com.dinglebob.drifter.building.plan.HousePlans;
import com.dinglebob.drifter.building.plan.PlacedPlan;
import com.dinglebob.drifter.building.plan.PlanSite;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PillarUpTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.hands.Placement;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import com.dinglebob.drifter.building.StarterHut;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Building its house from a plan (house plans, 2026-10-16), as a player does it:
 * <ol>
 *   <li><b>Choose</b> (once): a plan, a spot near home and which way it faces ({@link PlanSite}), and the woods it'll use
 *       ({@link HousePalette}); all saved on its home.</li>
 *   <li><b>Clear</b>: whatever natural is in the house's space (earth, rock, plants, trees) dug out, top down.</li>
 *   <li><b>Fill</b> (user's rule: level the ground first, never floating): every hole under its footprint filled with
 *       dirt, bottom up, and the plan's ground (its grass) made sure of.</li>
 *   <li><b>Build</b>: the plan's blocks in order, lowest first, each placed the way the plan has it
 *       ({@link PlaceBlockTask#exactly}), from a spot it can reach it from; when it runs out of something it makes or
 *       gathers more (the planner), then carries on.</li>
 * </ol>
 * Blocks it can't place from anywhere it can stand it leaves for now; a few gaps don't stop it finishing.
 */
public class BuildPlanActivity extends Activity {
	private static final int MAX_GAPS = 6;
	private static final int MAX_WALKS = 60;
	private static final int MAX_DEPTH = 5;
	/** Highest it pillars up to reach a block (a fall from there doesn't hurt). */
	private static final int MAX_PILLAR = 3;
	/** Building blocks it keeps by it while building (for pillars and getting down). */
	private static final int SPARE_BLOCKS = 10;
	/** How far above the house's space it takes down the rest of a tree standing in it. */
	private static final int TREE_ABOVE = 6;
	/** Walks for one block before it leaves that one out. */
	private static final int WALKS_PER_BLOCK = 4;

	private final Set<BlockPos> failed = new HashSet<>();
	/** Cells it found no spot for this time round; tried again once something new is placed (it may stand on that). */
	private final Set<BlockPos> stuck = new HashSet<>();
	/** Blocks it has already said it can't get to (said once). */
	private final Set<BlockPos> told = new HashSet<>();
	private final Map<BlockPos, Integer> tries = new HashMap<>();
	private final Map<BlockPos, Integer> walksFor = new HashMap<>();
	@Nullable
	private PlacedPlan placed;
	private int walks;
	private int gathers;
	@Nullable
	private BlockPos placing;
	private String phase = "choose";
	@Nullable
	private Task gathering;
	@Nullable
	private BlockPos digging;
	/** The cell it last walked over to place, so a walk that didn't help isn't made again and again. */
	@Nullable
	private BlockPos walkedFor;
	@Nullable
	private String couldntGet;

	public BuildPlanActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		Home home = this.drifter.getDrifterBrain().home();
		String plan = home == null || home.planId == null ? "its house" : "its house (" + home.planId.replace("personal:", "").replace("world:", "") + ")";
		return switch (this.phase) {
			case "clear" -> "clear the ground for " + plan;
			case "fill" -> "level the ground for " + plan;
			case "tidy" -> "take down the scaffolding round " + plan;
			case "bed" -> "move into " + plan;
			default -> "build " + plan;
		};
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 15;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		// A block it couldn't place or dig, or a walk that went wrong, is counted per block (tries, walks), not toward giving
		// the whole house up (found in testing: a snowy spruce top it couldn't reach ended the clearing).
		return false;
	}

	/** Reasons it has already said a walk or pillar went wrong (said once each). */
	private final Set<String> toldWhy = new HashSet<>();

	@Override
	protected void stepDone(Task step, Status status) {
		if (status == Status.FAILURE && !(step instanceof PlaceBlockTask) && step != this.gathering && this.toldWhy.size() < 12) {
			String why = step.describe() + ": " + step.failureReason();
			if (this.toldWhy.add(why)) {
				this.think("couldn't " + why);
			}
		}

		if (step instanceof PlaceBlockTask place && this.placing != null) {
			if (status == Status.SUCCESS) {
				// Walks count only while nothing gets placed (a big house takes a lot of walking about).
				this.stuck.clear();
				this.walks = 0;
			} else {
				int n = this.tries.merge(this.placing, 1, Integer::sum);
				if (n >= 3) {
					this.failed.add(this.placing);
					this.think("couldn't place a block of its house at " + this.placing.toShortString() + ": " + place.failureReason());
				}
			}

			this.placing = null;
		}

		if (this.digging != null) {
			if (status == Status.SUCCESS) {
				this.walks = 0;
			} else if (this.tries.merge(this.digging, 1, Integer::sum) >= 2) {
				this.failed.add(this.digging);
			}

			this.digging = null;
		}

		if (step == this.gathering) {
			if (status != Status.SUCCESS) {
				this.couldntGet = step.describe();
			}

			this.gathering = null;
		}
	}

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		Home home = this.drifter.getDrifterBrain().home();
		if (home == null || this.drifter.getServer() == null) {
			return this.fail("no home");
		}

		if (this.couldntGet != null) {
			return this.fail("couldn't " + this.couldntGet + "; another go later");
		}

		if (this.placed == null) {
			Status chosen = this.choose(home);
			if (chosen != null) {
				return chosen;
			}
		}

		PlacedPlan plan = this.placed;
		Map<BlockPos, BlockState> want = plan.buildMap();
		Set<BlockPos> ground = new HashSet<>(plan.ground);

		// 1. Clear: natural blocks in the house's space that aren't the plan's, highest first.
		this.phase = "clear";
		BlockPos clear = this.toClear(level, plan, want, ground);
		if (clear != null) {
			return this.workAt(level, plan, clear, () -> this.digAt(clear), "clear the spot");
		}

		// 2. Fill: holes under the footprint and the plan's ground, with dirt, lowest first.
		this.phase = "fill";
		BlockPos fill = this.toFill(level, plan, want, ground);
		if (fill != null) {
			if (fill.equals(BlockPos.ZERO)) {
				return this.fail("the ground there drops away too far to level");
			}

			if (this.drifter.getInventory().count(stack -> stack.is(Items.DIRT)) == 0) {
				return this.gather(Items.DIRT, 32);
			}

			return this.workAt(level, plan, fill, fill.below(), () -> this.place(fill, new PlaceBlockTask(this.drifter, fill, stack -> stack.is(Items.DIRT), "dirt")), "fill the ground");
		}

		// 3. Build. With some spare blocks to pillar up and get back down with (found in testing: out of them, it was stuck
		// on the roof of a tall house with no way down).
		this.phase = "build";
		if (com.dinglebob.drifter.hands.Tools.buildingBlockCount(this.drifter) < SPARE_BLOCKS) {
			return this.gather(Items.DIRT, 32);
		}

		for (PlacedPlan.Cell cell : plan.build) {
			BlockPos pos = cell.pos();
			BlockState have = level.getBlockState(pos);
			if (cell.state().getBlock() instanceof BedBlock) {
				// Its own bed goes there, moved in at the end.
				continue;
			}

			// A stripped log it hasn't got: the log goes in, then an axe on it (the way a player gets one).
			BlockState log = unstripped(cell.state());
			if (log != null && !this.failed.contains(pos) && !Placement.matches(have, cell.state())
				&& this.drifter.getInventory().count(stack -> stack.is(cell.state().getBlock().asItem())) == 0) {
				if (Placement.matches(have, log)) {
					if (this.drifter.getInventory().count(stack -> stack.is(ItemTags.AXES)) == 0) {
						return this.gather(Items.WOODEN_AXE, 1);
					}

					return this.useToolOn(level, plan, pos, stack -> stack.is(ItemTags.AXES), "an axe", "strip the " + log.getBlock().getName().getString().toLowerCase(java.util.Locale.ROOT));
				}

				if (have.isAir() || have.canBeReplaced()) {
					Status logStep = this.placeCell(level, plan, pos, log);
					if (logStep != null) {
						return logStep;
					}

					continue;
				}
			}

			if (Placement.matches(have, cell.state()) && !(cell.state().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)
				&& cell.state().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)
				!= have.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)) || this.failed.contains(pos) || this.stuck.contains(pos)) {
				continue;
			}

			// Something else there: its own scaffolding or something natural goes; anything else (someone's block) it leaves.
			if (!have.isAir() && !have.canBeReplaced() && !have.is(cell.state().getBlock())) {
				if (this.drifter.isScaffold(pos) || KnownBlocks.mayTake(level, pos) && natural(have)) {
					return this.workAt(level, plan, pos, () -> this.digAt(pos), "clear the spot");
				}

				this.failed.add(pos);
				continue;
			}

			Status placing = this.placeCell(level, plan, pos, cell.state());
			if (placing != null) {
				return placing;
			}
		}

		// Its paths: the ground there (dirt or grass by now) made into path with a shovel, if it has one.
		for (BlockPos path : plan.paths) {
			BlockState at = level.getBlockState(path);
			if (this.failed.contains(path) || !(at.is(Blocks.GRASS_BLOCK) || at.is(Blocks.DIRT) || at.is(Blocks.COARSE_DIRT) || at.is(Blocks.PODZOL)
				|| at.is(Blocks.MYCELIUM) || at.is(Blocks.ROOTED_DIRT)) || !level.getBlockState(path.above()).isAir()) {
				continue;
			}

			if (this.drifter.getInventory().count(stack -> stack.is(ItemTags.SHOVELS)) == 0) {
				break;
			}

			return this.useToolOn(level, plan, path, stack -> stack.is(ItemTags.SHOVELS), "a shovel", "make a path");
		}

		// Done (bar a few gaps it really can't fill).
		long missing = plan.build.stream().filter(c -> !(c.state().getBlock() instanceof BedBlock) && !Placement.matches(level.getBlockState(c.pos()), c.state())).count();
		if (missing > MAX_GAPS) {
			return this.fail("couldn't place " + missing + " blocks of the house yet");
		}

		if (!home.planDone) {
			home.planDone = true;
			this.think("finished building its house" + (missing > 0 ? " (bar " + missing + " blocks it couldn't reach)" : ""));
		}

		// Leaves still hanging in the house that haven't withered (a tree's leaves joined to one outside): dug if it can get
		// at them from the ground.
		BlockPos leaves = this.leavesLeft(level, plan, want);
		if (leaves != null) {
			return this.workAt(level, plan, leaves, () -> this.digAt(leaves), "clear the leaves");
		}

		// 4. Moving in: its bed into the house, the hut left as a shed.
		this.phase = "bed";
		if (home.planBed(level) == null && !this.noBed) {
			Status bed = this.moveBed(level, home, plan, want);
			if (bed != null) {
				return bed;
			}
		}

		// 5. Its scaffolding down again, the top first, what it can get at; anything it can't is left for tidying another day
		// (found in testing: two blocks high on a tall house it couldn't reach kept it from ever calling the house done).
		this.phase = "tidy";
		BlockPos scaffold = this.scaffoldLeft(level, plan, want);
		if (scaffold != null) {
			return this.workAt(level, plan, scaffold, () -> this.digAt(scaffold), "take down its scaffolding");
		}

		return Status.SUCCESS;
	}

	/** The next step toward placing this block as {@code want} has it, or null to leave it for now. */
	@Nullable
	private Status placeCell(Level level, PlacedPlan plan, BlockPos pos, BlockState want) {
		BlockState step = Placement.step(level, pos, want);
		Item item = step.getBlock().asItem();
		if (this.drifter.getInventory().count(stack -> stack.is(item)) == 0) {
			// As many as are still to go (logs for stripping too).
			int still = (int)plan.build.stream().filter(c -> (c.state().getBlock().asItem() == item
				|| unstripped(c.state()) != null && unstripped(c.state()).getBlock().asItem() == item) && !Placement.matches(level.getBlockState(c.pos()), c.state())).count();
			return this.gather(item, Math.max(1, Math.min(64, still)));
		}

		List<Placement.Way> ways = Placement.ways(this.drifter, pos, step);
		if (ways.isEmpty()) {
			// Nothing to place it against yet (a roof stair before the one it leans on): later.
			return null;
		}

		if (Placement.reachable(this.drifter, ways) != null) {
			return this.place(pos, PlaceBlockTask.exactly(this.drifter, pos, want, item.getDescription().getString().toLowerCase(java.util.Locale.ROOT)));
		}

		if (pos.equals(this.walkedFor)) {
			// It walked over for this one and still can't reach it: leave it till something else is placed.
			this.walkedFor = null;
			this.stuck.add(pos);
			if (this.told.add(pos)) {
				this.think("walked over but still can't reach " + pos.toShortString() + " (standing at " + this.drifter.blockPosition().toShortString() + ")");
			}
			return null;
		}

		Spot spot = this.spotFor(level, ways, pos, plan);
		if (spot == null || this.walksFor.merge(pos, 1, Integer::sum) > WALKS_PER_BLOCK) {
			this.stuck.add(pos);
			if (spot != null) {
				this.failed.add(pos);
			}

			if (this.told.add(pos)) {
				this.think("can't find anywhere to stand to place " + item.getDescription().getString().toLowerCase(java.util.Locale.ROOT) + " at " + pos.toShortString());
			}
			return null;
		}

		this.walkedFor = pos;
		return this.goTo(spot, "walk over to build");
	}

	// ---------------------------------------------------------------------------------------------
	// Choosing
	// ---------------------------------------------------------------------------------------------

	@Nullable
	private Status choose(Home home) {
		var server = this.drifter.getServer();
		if (home.planId != null && home.planOrigin != null) {
			HousePlan plan = HousePlans.get(server, home.planId);
			if (plan == null) {
				home.planId = null;
				return this.fail("its house plan is gone");
			}

			this.placed = PlacedPlan.of(plan, home);
			return null;
		}

		// The plans it could build, best spot for each; the one with the best spot (a little chance for variety).
		PlanSite.Site bestSite = null;
		HousePlan bestPlan = null;
		HousePalette bestPalette = null;
		double bestCost = Double.MAX_VALUE;
		// A plan picked for it (debug: /drifter house choose) but no spot yet: that plan only.
		java.util.Collection<HousePlan> plans = home.planId != null && HousePlans.get(server, home.planId) != null
			? List.of(HousePlans.get(server, home.planId)) : HousePlans.cached(server).values();
		for (HousePlan plan : plans) {
			HousePalette palette = HousePalette.forDrifter(this.drifter, plan);
			PlanSite.Site site = PlanSite.choose(this.drifter, plan, palette);
			if (site == null) {
				continue;
			}

			double cost = site.cost() + this.drifter.getRandom().nextDouble() * 20.0;
			if (cost < bestCost) {
				bestCost = cost;
				bestSite = site;
				bestPlan = plan;
				bestPalette = palette;
			}
		}

		if (bestPlan == null) {
			return this.fail("nowhere near home it knows is right for a house (" + PlanSite.lastRejections() + ")");
		}

		home.planId = bestPlan.id;
		home.planOrigin = bestSite.origin();
		home.planRotation = bestSite.rotation().name();
		home.planWoods.clear();
		home.planWoods.putAll(bestPalette.woods());
		home.planDeepslate = bestPalette.deepslate();
		home.planDone = false;
		this.placed = PlacedPlan.of(bestPlan, home);
		this.think("going to build a house (" + bestPlan.id.replace("personal:", "").replace("world:", "") + ") at " + bestSite.origin().toShortString()
			+ ", in " + bestPalette.describe());
		return Status.RUNNING;
	}

	// ---------------------------------------------------------------------------------------------
	// Levelling
	// ---------------------------------------------------------------------------------------------

	/** Earth, rock, plants and trees: the land, not something someone built. */
	static boolean natural(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
			|| state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.FLOWERS)
			|| state.is(BlockTags.SAPLINGS) || state.is(Blocks.SNOW) || state.is(Blocks.CLAY) || state.is(BlockTags.COAL_ORES)
			|| state.is(Blocks.SUGAR_CANE) || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON) || state.is(Blocks.MOSS_CARPET);
	}

	/** The highest natural block in the house's space that isn't the plan's (and isn't its ground), or null. */
	@Nullable
	private BlockPos toClear(Level level, PlacedPlan plan, Map<BlockPos, BlockState> want, Set<BlockPos> ground) {
		// Tree trunks first, then the rest. Leaves it leaves: with the trunk gone they wither by themselves (a player doesn't
		// pick a tree's leaves off one by one; found in testing: a spruce's leaves took it four minutes), and any still in
		// the way of a block get dug as it builds. Snow and grass it leaves too (the house's blocks go over them).
		for (boolean logs : new boolean[] {true, false}) {
			BlockPos found = this.toClear(level, plan, want, ground, logs);
			if (found != null) {
				return found;
			}
		}

		return null;
	}

	@Nullable
	private BlockPos toClear(Level level, PlacedPlan plan, Map<BlockPos, BlockState> want, Set<BlockPos> ground, boolean logs) {
		for (int dy = plan.sizeY + TREE_ABOVE; dy >= 0; dy--) {
			for (BlockPos column : plan.footprint()) {
				BlockPos pos = column.above(dy);
				if (this.failed.contains(pos) || ground.contains(pos)) {
					continue;
				}

				BlockState state = level.getBlockState(pos);
				// Above the house's space only the rest of a tree trunk standing in it (no logs left hanging).
				if (dy > plan.sizeY && !(state.is(BlockTags.LOGS) && trunkDown(level, pos, column.above(plan.sizeY)))) {
					continue;
				}

				BlockState planned = want.get(pos);
				// (A log where the plan has it stripped is one it put there to strip: it stays.)
				if (state.isAir() || planned != null && state.is(planned.getBlock()) || !natural(state) || !KnownBlocks.mayTake(level, pos)
					|| planned != null && unstripped(planned) != null && state.is(unstripped(planned).getBlock())
					|| this.drifter.isScaffold(pos) && planned == null) {
					continue;
				}

				if (state.is(BlockTags.LOGS) != logs || state.is(BlockTags.LEAVES) || state.canBeReplaced()) {
					continue;
				}

				return pos.immutable();
			}
		}

		return null;
	}

	/**
	 * The lowest hole under the footprint (or in the plan's ground) to fill with dirt; {@link BlockPos#ZERO} if a column
	 * drops further than it would fill; null when the ground is level.
	 */
	@Nullable
	private BlockPos toFill(Level level, PlacedPlan plan, Map<BlockPos, BlockState> want, Set<BlockPos> ground) {
		BlockPos lowest = null;
		for (BlockPos column : plan.footprint()) {
			// The top of the support: the plan's ground cell if it has one here, otherwise the block under its lowest layer.
			BlockPos top = ground.contains(column) ? column : column.below();
			if (this.failed.contains(top) || solid(level, top)) {
				continue;
			}

			BlockPos hole = top;
			int depth = 0;
			while (!solid(level, hole.below()) && depth < MAX_DEPTH) {
				hole = hole.below();
				depth++;
			}

			if (depth >= MAX_DEPTH) {
				return BlockPos.ZERO;
			}

			if (this.failed.contains(hole)) {
				continue;
			}

			if (lowest == null || hole.getY() < lowest.getY()) {
				lowest = hole.immutable();
			}
		}

		return lowest;
	}

	// ---------------------------------------------------------------------------------------------
	// Moving in
	// ---------------------------------------------------------------------------------------------

	private record BedSpot(BlockPos foot, Direction facing) {
		BlockPos head() {
			return this.foot.relative(this.facing);
		}
	}

	private boolean noBed;
	private boolean bedFetched;
	@Nullable
	private BedSpot bedSpot;

	/** The next step of moving its bed in, or null when it's in (or can't be). */
	@Nullable
	private Status moveBed(Level level, Home home, PlacedPlan plan, Map<BlockPos, BlockState> want) {
		if (this.bedSpot == null) {
			this.bedSpot = this.bedSpot(level, plan, want);
			if (this.bedSpot == null) {
				this.noBed = true;
				this.think("no room for its bed in the house; it keeps sleeping in the hut");
				return null;
			}
		}

		BedSpot spot = this.bedSpot;
		BlockState at = level.getBlockState(spot.foot());
		if (at.getBlock() instanceof BedBlock) {
			home.planBed = at.getValue(BedBlock.PART) == BedPart.HEAD ? spot.foot() : spot.foot().relative(at.getValue(BedBlock.FACING));
			this.think("moved into its new house (the hut's a shed now)");
			return null;
		}

		if (this.failed.contains(spot.foot())) {
			this.noBed = true;
			return null;
		}

		ItemStack carried = null;
		for (int i = 0; i < this.drifter.getInventory().getContainerSize(); i++) {
			ItemStack stack = this.drifter.getInventory().getItem(i);
			if (stack.is(ItemTags.BEDS) && stack.getItem() instanceof BlockItem) {
				carried = stack;
				break;
			}
		}

		if (carried == null) {
			// The one in the hut comes over (a player breaks it and carries it).
			BlockPos hutBed = home.hutBuilt ? StarterHut.of(home).bed(level) : null;
			if (hutBed == null || this.bedFetched) {
				this.noBed = true;
				this.think("has no bed to move into its house");
				return null;
			}

			this.bedFetched = true;
			this.think("moving its bed from the hut into the house");
			return this.begin(new SequenceTask(this.drifter, "fetch the bed from the hut", List.of(
				new NavigateTask(this.drifter, Vec3.atBottomCenterOf(home.center), 1.0, "go into the hut"),
				this.dig(hutBed), new CollectItemsTask(this.drifter, 3.0))));
		}

		BlockState bed = ((BlockItem)carried.getItem()).getBlock().defaultBlockState().setValue(BedBlock.FACING, spot.facing());
		List<Placement.Way> ways = Placement.ways(this.drifter, spot.foot(), bed);
		if (ways.isEmpty()) {
			this.noBed = true;
			this.think("can't put its bed down in the house");
			return null;
		}

		// Not standing where the bed goes.
		net.minecraft.world.phys.AABB body = this.drifter.getBoundingBox();
		boolean inTheWay = body.intersects(new net.minecraft.world.phys.AABB(spot.foot())) || body.intersects(new net.minecraft.world.phys.AABB(spot.head()));
		if (!inTheWay && Placement.reachable(this.drifter, ways) != null) {
			return this.place(spot.foot(), PlaceBlockTask.exactly(this.drifter, spot.foot(), bed, "its bed"));
		}

		Spot stand = this.spotFor(level, ways, spot.foot(), plan, spot.head());
		if (stand == null || stand.height() > 0 || this.walksFor.merge(spot.foot(), 1, Integer::sum) > WALKS_PER_BLOCK) {
			this.noBed = true;
			this.think("can't find anywhere to stand to put its bed down");
			return null;
		}

		return this.walkTo(stand.feet(), "walk over to put its bed down");
	}

	/**
	 * Where its bed goes: the plan's own bed spot if it has one; otherwise a spot on the floor inside, out of the doorway,
	 * head against a wall, as far from the door as it can.
	 */
	@Nullable
	private BedSpot bedSpot(Level level, PlacedPlan plan, Map<BlockPos, BlockState> want) {
		for (PlacedPlan.Cell cell : plan.build) {
			if (cell.state().getBlock() instanceof BedBlock) {
				return new BedSpot(cell.pos(), cell.state().getValue(BedBlock.FACING));
			}
		}

		List<BlockPos> doors = plan.build.stream().filter(c -> c.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
			.map(PlacedPlan.Cell::pos).toList();
		BedSpot best = null;
		double bestScore = -Double.MAX_VALUE;
		for (BlockPos column : plan.footprint()) {
			for (int dy = 0; dy < plan.sizeY - 1; dy++) {
				BlockPos foot = column.above(dy);
				if (!this.roomForBed(level, plan, want, foot)) {
					continue;
				}

				for (Direction facing : Direction.Plane.HORIZONTAL) {
					BlockPos head = foot.relative(facing);
					if (!this.roomForBed(level, plan, want, head)) {
						continue;
					}

					double toDoor = doors.stream().mapToDouble(d -> Math.min(d.distSqr(foot), d.distSqr(head))).min().orElse(100.0);
					if (toDoor <= 2.0) {
						continue;
					}

					BlockPos behind = head.relative(facing);
					boolean againstWall = want.containsKey(behind) && !want.get(behind).getCollisionShape(level, behind).isEmpty();
					double score = Math.sqrt(toDoor) + (againstWall ? 4.0 : 0.0);
					if (score > bestScore) {
						bestScore = score;
						best = new BedSpot(foot.immutable(), facing);
					}
				}
			}
		}

		return best;
	}

	/** Room for half a bed here: empty in the plan and the world (and above it), on a floor, walled in all round. */
	private boolean roomForBed(Level level, PlacedPlan plan, Map<BlockPos, BlockState> want, BlockPos pos) {
		if (!plan.contains(pos, 0) || want.containsKey(pos) || want.containsKey(pos.above()) || !level.getBlockState(pos).isAir()
			|| !level.getBlockState(pos.above()).isAir() || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
			return false;
		}

		// Inside: a wall of the plan's each way along the floor.
		for (Direction side : Direction.Plane.HORIZONTAL) {
			boolean wall = false;
			for (int i = 1; i <= Math.max(plan.sizeX, plan.sizeZ) && !wall; i++) {
				BlockPos p = pos.relative(side, i);
				BlockState planned = want.get(p);
				wall = planned != null && !planned.getCollisionShape(level, p).isEmpty();
			}

			if (!wall) {
				return false;
			}
		}

		return true;
	}

	/** Leaves in the house's space (not on its roof) still there once it's built, or null. */
	@Nullable
	private BlockPos leavesLeft(Level level, PlacedPlan plan, Map<BlockPos, BlockState> want) {
		for (BlockPos column : plan.footprint()) {
			for (int dy = 0; dy < plan.sizeY; dy++) {
				BlockPos pos = column.above(dy);
				if (!this.failed.contains(pos) && !want.containsKey(pos) && level.getBlockState(pos).is(BlockTags.LEAVES) && KnownBlocks.mayTake(level, pos)) {
					return pos.immutable();
				}
			}
		}

		return null;
	}

	/** Logs all the way down from here to {@code to} (one trunk). */
	private static boolean trunkDown(Level level, BlockPos from, BlockPos to) {
		for (BlockPos pos = from; pos.getY() >= to.getY(); pos = pos.below()) {
			if (!level.getBlockState(pos).is(BlockTags.LOGS)) {
				return false;
			}
		}

		return true;
	}

	private static boolean solid(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return (state.isFaceSturdy(level, pos, Direction.UP) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND)) && level.getFluidState(pos).isEmpty();
	}

	// ---------------------------------------------------------------------------------------------
	// Stripped logs and paths: made the player way
	// ---------------------------------------------------------------------------------------------

	/** The log a stripped log or wood is made from (an axe on it), keeping its axis; null for anything else. */
	@Nullable
	static BlockState unstripped(BlockState state) {
		net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
		if (!id.getPath().startsWith("stripped_")) {
			return null;
		}

		net.minecraft.resources.ResourceLocation from = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
			id.getPath().substring("stripped_".length()));
		var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(from);
		if (block.isEmpty()) {
			return null;
		}

		BlockState log = block.get().defaultBlockState();
		if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)
			&& log.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)) {
			log = log.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
				state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS));
		}

		return log;
	}

	/** A face of this block it can click from where it stands, or null. */
	@Nullable
	private Direction faceInReach(BlockPos pos) {
		for (Direction face : Direction.values()) {
			if (Reach.aimPointFrom(this.drifter, this.drifter.getEyePosition(), pos, face).isPresent()) {
				return face;
			}
		}

		return null;
	}

	/** Right-click a block with a tool (an axe to strip a log, a shovel to make a path), walking over first if need be. */
	private Status useToolOn(Level level, PlacedPlan plan, BlockPos pos, java.util.function.Predicate<net.minecraft.world.item.ItemStack> tool,
		String toolName, String what) {
		Direction face = this.faceInReach(pos);
		if (face == null) {
			return this.workAt(level, plan, pos, () -> {
				this.failed.add(pos);
				return Status.RUNNING;
			}, "walk over to " + what);
		}

		this.digging = pos;
		return this.begin(new SequenceTask(this.drifter, what, List.of(new com.dinglebob.drifter.executor.task.SelectItemTask(this.drifter, tool, toolName),
			new com.dinglebob.drifter.executor.task.UseOnBlockTask(this.drifter, pos, face, what))));
	}

	// ---------------------------------------------------------------------------------------------
	// Doing
	// ---------------------------------------------------------------------------------------------

	private Status place(BlockPos pos, PlaceBlockTask task) {
		this.placing = pos;
		return this.begin(task);
	}

	private Status digAt(BlockPos pos) {
		this.digging = pos;
		return this.begin(this.dig(pos));
	}

	/** Do this at that block, walking over (or up a pillar) first if it's out of reach; left out if it can't get to it. */
	private Status workAt(Level level, PlacedPlan plan, BlockPos pos, java.util.function.Supplier<Status> work, String what) {
		return this.workAt(level, plan, pos, pos, work, what);
	}

	/** The same, reaching {@code aim} (the block under a hole it fills, rather than the empty hole). */
	private Status workAt(Level level, PlacedPlan plan, BlockPos pos, BlockPos aim, java.util.function.Supplier<Status> work, String what) {
		if (Reach.canReach(this.drifter, aim)) {
			return work.get();
		}

		// Leaves it can't get at are left to wither once the tree's gone (as a player leaves them).
		BlockState state = level.getBlockState(pos);
		// Nor does it pillar up to take scaffolding down (that's more scaffolding; found in testing: round and round on a
		// tall house): what it can't get at from the ground is left for tidying up another day.
		int most = state.is(BlockTags.LEAVES) || state.canBeReplaced() || this.drifter.isScaffold(pos) ? 0 : MAX_PILLAR;
		Spot spot = this.standFor(level, pos, plan, null, most, eye -> {
			for (Direction face : Direction.values()) {
				if (Reach.aimPointFrom(this.drifter, eye, aim, face).isPresent()) {
					return true;
				}
			}

			return false;
		});
		if (spot == null || this.walksFor.merge(pos, 1, Integer::sum) > WALKS_PER_BLOCK) {
			this.failed.add(pos);
			return Status.RUNNING;
		}

		return this.goTo(spot, what);
	}

	/** Over to a spot, up a pillar of scaffolding there if it's a high one (taken down at the end; the player way). */
	private Status goTo(Spot spot, String what) {
		if (spot.height() == 0) {
			return this.walkTo(spot.feet(), what);
		}

		if (++this.walks > MAX_WALKS) {
			return this.fail("walked about too much; another go later");
		}

		return this.begin(new SequenceTask(this.drifter, "pillar up to " + what.replace("walk over to ", ""), List.of(
			new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot.feet()), 0.3, what),
			new PillarUpTask(this.drifter, spot.height()))));
	}

	private Status walkTo(BlockPos spot, String what) {
		if (++this.walks > MAX_WALKS) {
			return this.fail("walked about too much; another go later");
		}

		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.3, what));
	}

	/** More of a material: made or gathered the way the planner works it out (no long trips just for a house block). */
	private Status gather(Item item, int count) {
		if (++this.gathers > 12) {
			return this.fail("went for materials too many times; another go later");
		}

		Planner.Plan plan = Planner.plan(this.drifter, KnownWorld.scan(this.drifter), item, Math.max(1, count));
		if (!plan.feasible() || plan.next() == null) {
			return this.fail("can't work out how to get " + item.getDescription().getString().toLowerCase(java.util.Locale.ROOT) + " for its house");
		}

		this.think("needs " + count + " " + item.getDescription().getString().toLowerCase(java.util.Locale.ROOT) + " for its house");
		this.gathering = Goals.activityFor(this.drifter, plan.next());
		return this.begin(this.gathering);
	}

	/** Where to stand to place a block: on the ground there, or {@code height} blocks up a pillar from it. */
	private record Spot(BlockPos feet, int height) {
	}

	/**
	 * A spot it knows to stand on from which one of these ways is in reach: the nearest to where it is, on the ground if it
	 * can, otherwise up a pillar (the lowest that does; outside the house rather than in it). Not in the cell itself (it'd
	 * be in the way), not where the plan puts a block at its feet or head, no pillar where the plan has anything.
	 */
	@Nullable
	private Spot spotFor(Level level, List<Placement.Way> ways, BlockPos target, PlacedPlan plan) {
		return this.spotFor(level, ways, target, plan, null);
	}

	@Nullable
	private Spot spotFor(Level level, List<Placement.Way> ways, BlockPos target, PlacedPlan plan, @Nullable BlockPos avoid) {
		List<Placement.Way> some = ways.size() > 8 ? ways.subList(0, 8) : ways;
		return this.standFor(level, target, plan, avoid, MAX_PILLAR, eye -> {
			for (Placement.Way way : some) {
				if (Reach.aimPointFrom(this.drifter, eye, way.support(), way.face(), way.region()).isPresent()) {
					return true;
				}
			}

			return false;
		});
	}

	/** The nearest spot (on the ground, or up to {@code most} blocks up a pillar) from whose eye height {@code canFrom} holds. */
	@Nullable
	private Spot standFor(Level level, BlockPos target, PlacedPlan plan, @Nullable BlockPos avoid, int most, java.util.function.Predicate<Vec3> canFrom) {
		Map<BlockPos, BlockState> want = plan.buildMap();
		for (int height = 0; height <= most; height++) {
			Spot best = null;
			double bestCost = Double.MAX_VALUE;
			for (BlockPos pos : BlockPos.betweenClosed(target.offset(-4, -4 - height, -4), target.offset(4, 1 - height, 4))) {
				if (!KnownBlocks.isStandable(level, pos) || !this.drifter.getSenses().terrain().isKnown(pos.below())) {
					continue;
				}

				BlockPos top = pos.above(height);
				if (!this.clearColumn(level, pos, height, target, want) || avoid != null && (top.equals(avoid) || top.above().equals(avoid))) {
					continue;
				}

				double cost = pos.distSqr(this.drifter.blockPosition()) + (height > 0 && plan.contains(pos, 0) ? 40.0 : 0.0);
				if (cost >= bestCost) {
					continue;
				}

				// From the middle of the block, with a little to spare (it never stands quite in the middle).
				Vec3 eye = new Vec3(top.getX() + 0.5, top.getY() + net.minecraft.world.entity.player.Player.DEFAULT_EYE_HEIGHT, top.getZ() + 0.5);
				if (eye.distanceTo(Vec3.atCenterOf(target)) > this.drifter.blockInteractionRange() - 0.2) {
					continue;
				}

				if (canFrom.test(eye)) {
					best = new Spot(pos.immutable(), height);
					bestCost = cost;
				}
			}

			if (best != null) {
				return best;
			}
		}

		return null;
	}

	/**
	 * Room to stand at {@code pos} up a pillar {@code height} high: the pillar's blocks where the plan has nothing, and its
	 * feet and head clear up top (and not where the target goes or the plan puts something solid).
	 */
	private boolean clearColumn(Level level, BlockPos pos, int height, BlockPos target, Map<BlockPos, BlockState> want) {
		for (int i = 0; i <= height + 1; i++) {
			BlockPos p = pos.above(i);
			if (p.equals(target) || i < height && want.containsKey(p) || !level.getBlockState(p).getCollisionShape(level, p).isEmpty()
				|| !level.getFluidState(p).isEmpty()) {
				return false;
			}

			BlockState planned = want.get(p);
			if (i >= height && planned != null && !planned.getCollisionShape(level, p).isEmpty()) {
				return false;
			}
		}

		return true;
	}

	/** The highest of its scaffold blocks round the house still standing, or null. */
	@Nullable
	private BlockPos scaffoldLeft(Level level, PlacedPlan plan, Map<BlockPos, BlockState> want) {
		BlockPos best = null;
		for (BlockPos pos : this.drifter.scaffold()) {
			if (!plan.contains(pos, 4) || want.containsKey(pos) || this.failed.contains(pos) || level.getBlockState(pos).isAir()) {
				continue;
			}

			if (best == null || pos.getY() > best.getY()) {
				best = pos;
			}
		}

		return best;
	}
}
