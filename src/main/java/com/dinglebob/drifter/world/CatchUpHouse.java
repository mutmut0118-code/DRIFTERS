package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.activity.BuildPlanActivity;
import com.dinglebob.drifter.building.StarterHut;
import com.dinglebob.drifter.building.plan.HousePalette;
import com.dinglebob.drifter.building.plan.HousePlan;
import com.dinglebob.drifter.building.plan.HousePlans;
import com.dinglebob.drifter.building.plan.PlacedPlan;
import com.dinglebob.drifter.building.plan.PlanSite;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Catch-up's house (section 22 "Building"; user's request, 2026-10-17: Drifters away a while come back with their house
 * up, and older world-gen camps have one beside the hut). The same house the live builder makes, in one go: when it's far
 * enough along to start one (the plan-house goal's own rule), it picks a plan and a spot near home (ground round its home
 * counts as seen: it lived there), and if it has the raw materials for it, in its pockets and its chests (logs for the
 * woodwork, cobblestone, sand for the glass), it uses them up and the house goes up: the ground cleared and levelled, every
 * block the way the plan has it in its woods, then its bed carried in from the hut. Not enough: no house this time (it
 * builds it live, or next catch-up).
 */
public final class CatchUpHouse {
	private CatchUpHouse() {
	}

	/** What a house's blocks come to in raw materials. */
	record Raw(double logs, double cobble, double sand) {
	}

	/** Builds its house if it's time and it has what it takes; returns a line for the report, or null. */
	@Nullable
	public static String build(DrifterEntity drifter, int stage) {
		Home home = drifter.getDrifterBrain().home();
		Level level = drifter.level();
		var server = drifter.getServer();
		if (home == null || server == null || !home.hutBuilt || home.borrowed() || home.planDone || home.houseBuilt
			|| !home.dimension.equals(level.dimension().location()) || !level.isLoaded(home.center)
			|| !com.dinglebob.drifter.brain.activity.BuildHouseActivity.buildsHouses(drifter)) {
			return null;
		}

		boolean builder = drifter.getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.BUILDER;
		if (stage < (builder ? 4 : 5)) {
			return null;
		}

		// The plan and spot: its own if it had picked one; otherwise the best spot for any plan (some chance for variety).
		HousePlan plan = null;
		PlacedPlan placed = null;
		if (home.planId != null && home.planOrigin != null) {
			plan = HousePlans.get(server, home.planId);
			if (plan != null) {
				placed = PlacedPlan.of(plan, home);
			}
		}

		if (placed == null) {
			double best = Double.MAX_VALUE;
			for (HousePlan candidate : HousePlans.choicesFor(server, drifter)) {
				HousePalette palette = HousePalette.forDrifter(drifter, candidate);
				PlanSite.Site site = PlanSite.choose(drifter, candidate, palette, true);
				if (site == null) {
					continue;
				}

				double cost = site.cost() + drifter.getRandom().nextDouble() * 20.0;
				if (cost < best) {
					best = cost;
					plan = candidate;
					home.planId = candidate.id;
					home.planOrigin = site.origin();
					home.planRotation = site.rotation().name();
					home.planWoods.clear();
					home.planWoods.putAll(palette.woods());
					home.planDeepslate = palette.deepslate();
				}
			}

			if (plan == null) {
				drifter.getMemory().thoughts().add(level.getGameTime(), "catching up: no spot near home for a house");
				return null;
			}

			placed = PlacedPlan.of(plan, home);
		}

		// What it takes, against what it has.
		Raw need = raw(placed);
		List<Container> chests = chests(level, home);
		Predicate<ItemStack> logs = stack -> stack.is(ItemTags.LOGS);
		Predicate<ItemStack> cobble = stack -> stack.is(Items.COBBLESTONE) || stack.is(Items.COBBLED_DEEPSLATE);
		Predicate<ItemStack> sand = stack -> stack.is(Items.SAND) || stack.is(Items.RED_SAND) || stack.is(Items.GLASS);
		if (count(drifter, chests, logs) < Math.ceil(need.logs()) || count(drifter, chests, cobble) < Math.ceil(need.cobble())
			|| count(drifter, chests, sand) < Math.ceil(need.sand())) {
			drifter.getMemory().thoughts().add(level.getGameTime(), String.format(java.util.Locale.ROOT,
				"catching up: not enough for its house yet (needs %.0f logs, %.0f cobblestone, %.0f sand; has %d, %d, %d)", Math.ceil(need.logs()),
				Math.ceil(need.cobble()), Math.ceil(need.sand()), count(drifter, chests, logs), count(drifter, chests, cobble), count(drifter, chests, sand)));
			return null;
		}

		take(drifter, chests, logs, (int)Math.ceil(need.logs()));
		take(drifter, chests, cobble, (int)Math.ceil(need.cobble()));
		take(drifter, chests, sand, (int)Math.ceil(need.sand()));

		Map<BlockPos, BlockState> want = placed.buildMap();
		java.util.Set<BlockPos> ground = new java.util.HashSet<>(placed.ground);
		// Clear: natural blocks in its space that aren't the plan's (tree trunks above it too).
		for (BlockPos column : placed.footprint()) {
			for (int dy = placed.sizeY + 6; dy >= 0; dy--) {
				BlockPos pos = column.above(dy);
				BlockState state = level.getBlockState(pos);
				if (state.isAir() || ground.contains(pos) || want.containsKey(pos) && state.is(want.get(pos).getBlock())) {
					continue;
				}

				if (BuildPlanActivity.natural(state) && KnownBlocks.mayTake(level, pos) && (dy <= placed.sizeY || state.is(BlockTags.LOGS))) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}

		// Level: holes under it (and under each doorstep) filled with dirt.
		List<BlockPos> columns = new ArrayList<>(placed.footprint());
		columns.addAll(BuildPlanActivity.doorsteps(placed, want));
		for (BlockPos column : columns) {
			BlockPos top = ground.contains(column) ? column : column.below();
			for (int depth = 0; depth < 12; depth++) {
				BlockPos pos = top.below(depth);
				if (solid(level, pos)) {
					break;
				}

				if (level.getBlockState(pos).canBeReplaced()) {
					level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
				}
			}
		}

		// The house, lowest first, what hangs on things last (the plan's own order); never over a player's block.
		int placedBlocks = 0;
		for (PlacedPlan.Cell cell : placed.build) {
			BlockState state = cell.state();
			BlockPos pos = cell.pos();
			if (state.getBlock() instanceof BedBlock || PlayerPlacedBlocks.isPlayerPlaced(level, pos) || !level.getBlockState(pos).canBeReplaced()) {
				continue;
			}

			level.setBlock(pos, state, 3);
			if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
				level.setBlock(pos.above(), state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), 3);
			}

			placedBlocks++;
		}

		for (BlockPos path : placed.paths) {
			BlockState at = level.getBlockState(path);
			if ((at.is(Blocks.GRASS_BLOCK) || at.is(Blocks.DIRT)) && level.getBlockState(path.above()).isAir()) {
				level.setBlock(path, Blocks.DIRT_PATH.defaultBlockState(), 3);
			}
		}

		home.planDone = true;
		com.dinglebob.drifter.world.PlayerPlacedBlocks.recordBuiltBox(level, placed.box());
		// Its bed moved in from the hut (the hut's a shed from now on).
		String moved = moveBed(level, home, placed, want);
		return "built its house (" + plan.id.replace("personal:", "").replace("world:", "") + ", " + placedBlocks + " blocks)" + moved;
	}

	private static String moveBed(Level level, Home home, PlacedPlan placed, Map<BlockPos, BlockState> want) {
		BlockPos hutBed = StarterHut.of(home).bed(level);
		BuildPlanActivity.BedSpot spot = BuildPlanActivity.bedSpot(level, placed, want);
		if (hutBed == null || spot == null) {
			return "";
		}

		BlockState old = level.getBlockState(hutBed);
		if (!(old.getBlock() instanceof BedBlock)) {
			return "";
		}

		BlockPos otherHalf = hutBed.relative(BedBlock.getConnectedDirection(old));
		level.setBlock(otherHalf, Blocks.AIR.defaultBlockState(), 2 | 16);
		level.setBlock(hutBed, Blocks.AIR.defaultBlockState(), 2 | 16);
		BlockState foot = old.getBlock().defaultBlockState().setValue(BedBlock.FACING, spot.facing());
		level.setBlock(spot.foot(), foot, 3);
		level.setBlock(spot.head(), foot.setValue(BedBlock.PART, BedPart.HEAD), 3);
		home.planBed = spot.head();
		return "; moved its bed in";
	}

	private static boolean solid(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return (state.isFaceSturdy(level, pos, Direction.UP) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND)) && level.getFluidState(pos).isEmpty();
	}

	/** The raw materials its blocks come to (crafted the player way: planks from logs, stairs from planks, glass from sand). */
	static Raw raw(PlacedPlan placed) {
		double logs = 0.0;
		double cobble = 0.0;
		double sand = 0.0;
		for (PlacedPlan.Cell cell : placed.build) {
			BlockState state = cell.state();
			Item item = state.getBlock().asItem();
			ItemStack stack = new ItemStack(item);
			boolean doubleSlab = state.hasProperty(BlockStateProperties.SLAB_TYPE)
				&& state.getValue(BlockStateProperties.SLAB_TYPE) == net.minecraft.world.level.block.state.properties.SlabType.DOUBLE;
			int n = doubleSlab ? 2 : 1;
			if (stack.is(ItemTags.LOGS)) {
				logs += n;
			} else if (stack.is(ItemTags.PLANKS)) {
				logs += 0.25 * n;
			} else if (stack.is(ItemTags.WOODEN_STAIRS)) {
				logs += 0.375 * n;
			} else if (stack.is(ItemTags.WOODEN_SLABS)) {
				logs += 0.125 * n;
			} else if (stack.is(ItemTags.WOODEN_FENCES) || stack.is(ItemTags.FENCE_GATES)) {
				logs += 0.45 * n;
			} else if (stack.is(ItemTags.WOODEN_DOORS)) {
				logs += 0.5 * n;
			} else if (stack.is(ItemTags.WOODEN_TRAPDOORS)) {
				logs += 0.75 * n;
			} else if (stack.is(Items.LADDER)) {
				logs += 0.15 * n;
			} else if (stack.is(Items.COBBLESTONE) || stack.is(Items.COBBLED_DEEPSLATE) || stack.is(ItemTags.STONE_BRICKS)) {
				cobble += n;
			} else if (stack.is(Items.COBBLESTONE_STAIRS) || stack.is(Items.COBBLED_DEEPSLATE_STAIRS)) {
				cobble += 1.5 * n;
			} else if (stack.is(Items.COBBLESTONE_SLAB) || stack.is(Items.COBBLED_DEEPSLATE_SLAB)) {
				cobble += 0.5 * n;
			} else if (stack.is(Items.COBBLESTONE_WALL) || stack.is(Items.COBBLED_DEEPSLATE_WALL)) {
				cobble += n;
			} else if (stack.is(Items.GLASS)) {
				sand += n;
			} else if (stack.is(Items.GLASS_PANE)) {
				sand += 0.375 * n;
			}
		}

		return new Raw(logs, cobble, sand);
	}

	/** Takes {@code count} matching items from its pockets, then its chests, if it has that many in all; true if it did. */
	public static boolean takeFromAll(DrifterEntity drifter, Predicate<ItemStack> filter, int count) {
		Home home = drifter.getDrifterBrain().home();
		List<Container> chests = home == null ? List.of() : chests(drifter.level(), home);
		if (count(drifter, chests, filter) < count) {
			return false;
		}

		take(drifter, chests, filter, count);
		return true;
	}

	private static List<Container> chests(Level level, Home home) {
		List<Container> chests = new ArrayList<>();
		for (BlockPos pos : home.chests()) {
			if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Container container) {
				chests.add(container);
			}
		}

		return chests;
	}

	private static int count(DrifterEntity drifter, List<Container> chests, Predicate<ItemStack> filter) {
		int count = drifter.getInventory().count(filter);
		for (Container chest : chests) {
			for (int slot = 0; slot < chest.getContainerSize(); slot++) {
				ItemStack stack = chest.getItem(slot);
				if (!stack.isEmpty() && filter.test(stack)) {
					count += stack.getCount();
				}
			}
		}

		return count;
	}

	/** Takes from its pockets first, then its chests. */
	private static void take(DrifterEntity drifter, List<Container> chests, Predicate<ItemStack> filter, int count) {
		int left = count;
		DrifterInventory inventory = drifter.getInventory();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE && left > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && filter.test(stack)) {
				int move = Math.min(stack.getCount(), left);
				stack.shrink(move);
				left -= move;
			}
		}

		for (Container chest : chests) {
			for (int slot = 0; slot < chest.getContainerSize() && left > 0; slot++) {
				ItemStack stack = chest.getItem(slot);
				if (!stack.isEmpty() && filter.test(stack)) {
					int move = Math.min(stack.getCount(), left);
					stack.shrink(move);
					left -= move;
					chest.setChanged();
				}
			}
		}
	}
}
