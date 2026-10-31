package com.dinglebob.drifter.brain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The planner (DESIGN.md section 8): backward-chains from a wanted item through the ways to get things (the game's
 * own crafting and smelting recipes, plus the curated {@link Sources} table for mining and mob drops) to an ordered
 * list of actions, choosing the cheapest alternative at every step by a rough cost in seconds. It reads only the
 * Drifter's inventory and what it knows ({@link KnownWorld}). Things it hasn't seen yet become an Explore action.
 *
 * <p>Only the first action is carried out; the plan is made again from the new state after each one (replanning).
 */
public final class Planner {
	private static final int MAX_DEPTH = 9;
	/** Ingredient alternatives considered per need (planks: oak, birch, ...); what it carries is always considered. */
	private static final int MAX_CANDIDATES = 12;
	/**
	 * Sub-plans one plan may try before it settles for the best it has (none: no way it can see). A bed with no sheep
	 * about tried every colour of wool and every dye down to the flowers, millions of sub-plans and over four seconds
	 * a look, every time it chose what to do (found in testing: a desert builder ran the game at one tick a second);
	 * an ordinary plan takes a few thousand.
	 */
	private static final int MAX_STEPS = 30_000;
	/** Rough costs in seconds. */
	private static final double COST_CRAFT = 3.0;
	private static final double COST_PLACE = 3.0;
	private static final double COST_GATHER_EACH = 5.0;
	private static final double COST_GATHER_TRIP = 8.0;
	private static final double COST_DIG_DOWN = 40.0;
	private static final double COST_EXPLORE = 150.0;
	private static final double COST_MINE_TRIP = 240.0;
	private static final double COST_HUNT_KILL = 25.0;
	private static final double DROPS_PER_KILL = 1.5;
	private static final double COST_SMELT_EACH = 10.0;
	private static final double COST_COLLECT = 5.0;
	/** Opening its chest, plus the walk (seconds per block at a sprint). */
	private static final double COST_FETCH = 6.0;
	private static final double SECONDS_PER_BLOCK = 0.18;
	/** Items of each fuel per smelted item: coal and charcoal smelt 8, planks and logs 1.5. */
	private static final double COAL_ITEMS = 8.0;
	private static final double WOOD_ITEMS = 1.5;

	// ---------------------------------------------------------------------------------------------
	// Actions
	// ---------------------------------------------------------------------------------------------

	public sealed interface Action permits Gather, Hunt, Craft, Place, Smelt, Explore, Collect, Fetch, Mine, MakeObsidian, TakeBed, Cut, PotCook,
		FillBucket, PotCollect {
		String describe();
	}

	/** Mine known blocks until it carries {@code total} of the items. */
	public record Gather(Item item, String label, Predicate<BlockState> blocks, Predicate<ItemStack> items, int total, boolean digDown)
		implements Action {
		@Override
		public String describe() {
			return (this.digDown ? "dig for " : "gather ") + this.label + " (to " + this.total + ")";
		}
	}

	/** Hunt animals until it carries {@code total} of the items. */
	/** {@code variant}: only animals of this kind (a sheep's wool colour), or null for any. */
	public record Hunt(Item item, Set<EntityType<?>> prey, Predicate<ItemStack> items, int total, String label, @Nullable String variant) implements Action {
		@Override
		public String describe() {
			return "hunt for " + this.label + " (to " + this.total + ")";
		}
	}

	public record Craft(Item item, int times) implements Action {
		@Override
		public String describe() {
			return "craft " + name(this.item) + (this.times > 1 ? " x" + this.times : "");
		}
	}

	public record Place(Item station) implements Action {
		@Override
		public String describe() {
			return "place a " + name(this.station);
		}
	}

	public record Smelt(Item output, Predicate<ItemStack> input, String inputLabel, int count, Predicate<ItemStack> fuel, String fuelLabel,
		int fuelCount) implements Action {
		@Override
		public String describe() {
			return "smelt " + this.count + " " + this.inputLabel + " with " + this.fuelCount + " " + this.fuelLabel;
		}
	}

	/** Go back to a furnace it left cooking and take the results out. */
	public record Collect(FurnaceJob job) implements Action {
		@Override
		public String describe() {
			return "collect " + name(this.job.output) + " from the furnace";
		}
	}

	/** A mining trip down to where this ore is common (section 10.3), until it carries {@code total} of the items. */
	public record Mine(Item item, String label, Predicate<BlockState> blocks, int total, int depth, boolean mountain) implements Action {
		@Override
		public String describe() {
			return "go mining for " + this.label + " (to " + this.total + ", around Y " + this.depth + ")";
		}
	}

	/** A bed from a village house it has seen (user's rule, session 2: it may take a villager's bed as its own). */
	public record TakeBed(net.minecraft.core.BlockPos bed, Item item) implements Action {
		@Override
		public String describe() {
			return "take the " + name(this.item) + " from the village house at " + this.bed.toShortString();
		}
	}

	/**
	 * Cut something on a cutting board with a knife (Farmer's Delight, optional): put {@code times} of it on the board, a
	 * cut each, and pick up what comes off.
	 */
	public record Cut(Item output, Predicate<ItemStack> input, String inputLabel, int times, Predicate<ItemStack> tool, String toolLabel)
		implements Action {
		@Override
		public String describe() {
			return "cut " + this.times + " " + this.inputLabel + " on the cutting board";
		}
	}

	/**
	 * Cook a meal in the cooking pot on the kitchen's stove (Farmer's Delight, optional): one of each ingredient per batch,
	 * the bowls in, then wait by it and take the meals out.
	 */
	public record PotCook(Item meal, List<Predicate<ItemStack>> inputs, List<String> inputLabels, int batches, @Nullable Item container,
		int cookTicks) implements Action {
		@Override
		public String describe() {
			return "cook " + this.batches + " " + name(this.meal) + " in the cooking pot";
		}
	}

	/** Go back to the cooking pot for a meal it left cooking there (Farmer's Delight). */
	public record PotCollect(Brain.PotBatch batch) implements Action {
		@Override
		public String describe() {
			return "take the " + name(this.batch.meal()) + " out of the cooking pot";
		}
	}

	/** Fill buckets with water at water it knows (a cooking pot's recipe takes a water bucket). */
	public record FillBucket(int count) implements Action {
		@Override
		public String describe() {
			return "fill " + (this.count == 1 ? "a bucket" : this.count + " buckets") + " with water";
		}
	}

	/** Obsidian from lava and water with two buckets (section 9 stage 8), until it carries {@code total}. */
	public record MakeObsidian(int total) implements Action {
		@Override
		public String describe() {
			return "make obsidian with lava and water (to " + this.total + ")";
		}
	}

	/** Take something it stored out of its chest at home (section 9: home storage counts as owned). */
	public record Fetch(net.minecraft.core.BlockPos chest, Item item, int count) implements Action {
		@Override
		public String describe() {
			return "fetch " + this.count + " " + name(this.item) + " from its chest";
		}
	}

	/** Look for something; {@code found} says when it has seen what it was looking for. */
	public record Explore(String reason, @Nullable Vec3 toward, @Nullable Predicate<DrifterEntity> found) implements Action {
		@Override
		public String describe() {
			return "explore (" + this.reason + ")";
		}
	}

	/** The result: the actions in order (first one first), or not feasible at all. */
	public record Plan(List<Action> actions, boolean feasible, double cost) {
		@Nullable
		public Action next() {
			return this.actions.isEmpty() ? null : this.actions.get(0);
		}

		public boolean done() {
			return this.feasible && this.actions.isEmpty();
		}

		public boolean needsExploring() {
			return this.actions.stream().anyMatch(action -> action instanceof Explore);
		}
	}

	public static final Plan INFEASIBLE = new Plan(List.of(), false, Double.MAX_VALUE);

	// ---------------------------------------------------------------------------------------------
	// Search state
	// ---------------------------------------------------------------------------------------------

	private static final class State {
		final Map<Item, Integer> inventory;
		final List<Action> actions;
		final Set<Block> placed;
		/** How much of each cooking batch the plan has already counted on. */
		final Map<FurnaceJob, Integer> fromFurnaces;
		/** How much of each stored item the plan has already counted on taking from home. */
		final Map<Item, Integer> fromHome;
		/** The plan counts on the meal cooking in the pot (Farmer's Delight). */
		boolean fromPot;
		double cost;

		State(Map<Item, Integer> inventory, List<Action> actions, Set<Block> placed, Map<FurnaceJob, Integer> fromFurnaces, Map<Item, Integer> fromHome,
			double cost) {
			this.inventory = inventory;
			this.actions = actions;
			this.placed = placed;
			this.fromFurnaces = fromFurnaces;
			this.fromHome = fromHome;
			this.cost = cost;
		}

		State copy() {
			State copy = new State(new HashMap<>(this.inventory), new ArrayList<>(this.actions), new HashSet<>(this.placed), new HashMap<>(this.fromFurnaces),
				new HashMap<>(this.fromHome), this.cost);
			copy.fromPot = this.fromPot;
			return copy;
		}

		int count(Item item) {
			return this.inventory.getOrDefault(item, 0);
		}

		void add(Item item, int count) {
			this.inventory.merge(item, count, Integer::sum);
		}

		/**
		 * Adds an action, merging it into an earlier one of the same kind for the same item, so it gathers all the logs it
		 * needs in one trip and crafts all the planks at once.
		 */
		void addAction(Action action) {
			for (int i = 0; i < this.actions.size(); i++) {
				Action earlier = this.actions.get(i);
				if (earlier instanceof Gather a && action instanceof Gather b && a.item() == b.item() && a.digDown() == b.digDown()) {
					this.actions.set(i, new Gather(a.item(), a.label(), a.blocks(), a.items(), a.total() + b.total(), a.digDown()));
					return;
				}

				if (earlier instanceof Hunt a && action instanceof Hunt b && a.item() == b.item()) {
					this.actions.set(i, new Hunt(a.item(), a.prey(), a.items(), a.total() + b.total(), a.label(), a.variant()));
					return;
				}

				if (earlier instanceof Craft a && action instanceof Craft b && a.item() == b.item()) {
					this.actions.set(i, new Craft(a.item(), a.times() + b.times()));
					return;
				}

				if (earlier instanceof Explore a && action instanceof Explore b && a.reason().equals(b.reason())) {
					return;
				}

				if (earlier instanceof Collect a && action instanceof Collect b && a.job() == b.job()) {
					return;
				}

				if (earlier instanceof MakeObsidian a && action instanceof MakeObsidian b) {
					this.actions.set(i, new MakeObsidian(a.total() + b.total()));
					return;
				}

				if (earlier instanceof Mine a && action instanceof Mine b && a.item() == b.item()) {
					this.actions.set(i, new Mine(a.item(), a.label(), a.blocks(), a.total() + b.total(), a.depth(), a.mountain()));
					return;
				}

				if (earlier instanceof Cut a && action instanceof Cut b && a.output() == b.output()) {
					this.actions.set(i, new Cut(a.output(), a.input(), a.inputLabel(), a.times() + b.times(), a.tool(), a.toolLabel()));
					return;
				}

				if (earlier instanceof PotCook a && action instanceof PotCook b && a.meal() == b.meal()) {
					this.actions.set(i, new PotCook(a.meal(), a.inputs(), a.inputLabels(), a.batches() + b.batches(), a.container(), a.cookTicks()));
					return;
				}

				if (earlier instanceof FillBucket a && action instanceof FillBucket b) {
					this.actions.set(i, new FillBucket(a.count() + b.count()));
					return;
				}

				if (earlier instanceof Fetch a && action instanceof Fetch b && a.item() == b.item()) {
					this.actions.set(i, new Fetch(a.chest(), a.item(), a.count() + b.count()));
					return;
				}
			}

			this.actions.add(action);
		}

		/** Takes up to {@code count}; returns how many it took. */
		int take(Item item, int count) {
			int have = this.count(item);
			int taken = Math.min(have, count);
			if (taken > 0) {
				this.inventory.put(item, have - taken);
			}

			return taken;
		}
	}

	private final DrifterEntity drifter;
	private final KnownWorld world;
	private final Level level;
	private final Map<Item, Integer> realInventory;
	private final Deque<Item> inProgress = new ArrayDeque<>();
	private int steps;

	private Planner(DrifterEntity drifter, KnownWorld world) {
		this.drifter = drifter;
		this.world = world;
		this.level = drifter.level();
		this.realInventory = inventoryCounts(drifter);
	}

	/**
	 * Plans how to end up carrying {@code count} of any of the {@code goal} items (for example any cooked meat).
	 */
	public static Plan plan(DrifterEntity drifter, KnownWorld world, List<Item> goal, int count) {
		Planner planner = new Planner(drifter, world);
		State start = new State(new HashMap<>(planner.realInventory), new ArrayList<>(), new HashSet<>(), new HashMap<>(), new HashMap<>(), 0.0);
		State end = planner.obtainAny(start, goal, count, 0);
		if (planner.steps > MAX_STEPS) {
			com.dinglebob.drifter.Drifter.LOGGER.debug("[Drifter] plan for {} hit the sub-plan cap", goal.get(0));
		}

		return end == null ? INFEASIBLE : new Plan(List.copyOf(end.actions), true, end.cost);
	}

	public static Plan plan(DrifterEntity drifter, KnownWorld world, Item goal, int count) {
		return plan(drifter, world, List.of(goal), count);
	}

	private static Map<Item, Integer> inventoryCounts(DrifterEntity drifter) {
		Map<Item, Integer> counts = new HashMap<>();
		DrifterInventory inventory = drifter.getInventory();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}

		return counts;
	}

	// ---------------------------------------------------------------------------------------------
	// Backward chaining
	// ---------------------------------------------------------------------------------------------

	/** Ends up having used {@code count} items from the candidates (taken from the inventory, or obtained first). */
	@Nullable
	private State obtainAny(State state, List<Item> candidates, int count, int depth) {
		State base = state.copy();
		int remaining = count;
		// What it carries first, the most plentiful first.
		List<Item> carried = candidates.stream().filter(item -> base.count(item) > 0)
			.sorted((a, b) -> Integer.compare(base.count(b), base.count(a))).toList();
		for (Item item : carried) {
			remaining -= base.take(item, remaining);
			if (remaining <= 0) {
				return base;
			}
		}

		// Then what's cooking in a furnace it loaded: it only has to go back for it.
		for (FurnaceJob job : this.drifter.getDrifterBrain().furnaceJobs()) {
			if (!candidates.contains(job.output)) {
				continue;
			}

			int left = job.count - base.fromFurnaces.getOrDefault(job, 0);
			int take = Math.min(left, remaining);
			if (take > 0) {
				base.fromFurnaces.merge(job, take, Integer::sum);
				base.addAction(new Collect(job));
				base.cost += COST_COLLECT;
				remaining -= take;
				if (remaining <= 0) {
					return base;
				}
			}
		}

		// Then a meal it left cooking in the pot (Farmer's Delight): it only has to go back for it.
		Brain.PotBatch batch = this.drifter.getDrifterBrain().potBatch();
		if (batch != null && !base.fromPot && candidates.contains(batch.meal())) {
			base.fromPot = true;
			base.addAction(new PotCollect(batch));
			base.cost += COST_COLLECT;
			remaining -= batch.count();
			if (remaining <= 0) {
				return base;
			}
		}

		// Then what it stored at home: a walk to the chest.
		Home home = this.drifter.getDrifterBrain().home();
		if (home != null && home.near(this.drifter, Home.STORAGE_RANGE)) {
			double walk = Math.sqrt(this.drifter.blockPosition().distSqr(home.center)) * SECONDS_PER_BLOCK;
			for (Item item : candidates) {
				int left = home.stored(item) - base.fromHome.getOrDefault(item, 0);
				int take = Math.min(left, remaining);
				net.minecraft.core.BlockPos chest = take > 0 ? home.chestWith(item) : null;
				if (chest != null) {
					base.fromHome.merge(item, take, Integer::sum);
					base.addAction(new Fetch(chest, item, take));
					base.cost += COST_FETCH + walk;
					remaining -= take;
					if (remaining <= 0) {
						return base;
					}
				}
			}
		}

		// Then the cheapest way to get the rest of one kind. Of a long list (any log, any planks), only the kinds it has or
		// knows a way to when there are any: the rest all come down to "go and look", dearer, and trying each made one food
		// plan 7,000 sub-plans and 13 ms (section 22 budget).
		boolean anyPromising = candidates.size() > 3 && candidates.stream().anyMatch(item -> this.promising(item, 0));
		List<Item> options = new ArrayList<>(carried);
		for (Item item : candidates) {
			if (options.size() >= MAX_CANDIDATES) {
				break;
			}

			if (!options.contains(item) && (!anyPromising || this.promising(item, 0))) {
				options.add(item);
			}
		}

		State best = null;
		for (Item item : options) {
			State attempt = this.obtainItem(base.copy(), item, remaining, depth + 1);
			if (attempt != null && (best == null || attempt.cost < best.cost)) {
				best = attempt;
			}
		}

		return best;
	}

	private final Map<Item, Boolean> promising = new HashMap<>();

	/**
	 * It carries some, has some cooking, knows where to gather or hunt it, or can make it from something promising (a few
	 * steps deep). Only for narrowing long lists of alternatives; the plan itself still works everything out.
	 */
	private boolean promising(Item item, int depth) {
		Boolean known = this.promising.get(item);
		if (known != null) {
			return known;
		}

		if (depth > 3) {
			return false;
		}

		this.promising.put(item, false);
		boolean result = this.realInventory.getOrDefault(item, 0) > 0
			|| this.drifter.getDrifterBrain().furnaceJobs().stream().anyMatch(job -> job.output == item);
		for (Sources.Source source : Sources.forItem(item)) {
			result |= source.isBlock() ? this.world.knowsBlock(state -> source.blockGives(state, item))
				: source.mobType() != null && this.world.knowsAnimal(source.mobType());
		}

		for (RecipeHolder<CraftingRecipe> recipe : result ? List.<RecipeHolder<CraftingRecipe>>of() : crafting(this.level).getOrDefault(item, List.of())) {
			boolean all = true;
			for (Ingredient ingredient : recipe.value().getIngredients()) {
				all &= ingredient.isEmpty() || itemsOf(ingredient).stream().anyMatch(input -> this.promising(input, depth + 1));
			}

			result |= all;
		}

		for (RecipeHolder<? extends AbstractCookingRecipe> recipe : result ? List.<RecipeHolder<? extends AbstractCookingRecipe>>of()
			: smelting(this.level).getOrDefault(item, List.of())) {
			result |= itemsOf(recipe.value().getIngredients().get(0)).stream().anyMatch(input -> this.promising(input, depth + 1));
		}

		this.promising.put(item, result);
		return result;
	}

	/** Gets {@code count} of one item and uses them up (extras made along the way stay in the inventory). */
	@Nullable
	private State obtainItem(State state, Item item, int count, int depth) {
		if (depth > MAX_DEPTH || this.inProgress.contains(item) || ++this.steps > MAX_STEPS) {
			return null;
		}

		this.inProgress.push(item);
		try {
			State best = null;
			for (Sources.Source source : Sources.forItem(item)) {
				State attempt = source.isBlock() ? this.gather(state.copy(), source, item, count, depth) : this.hunt(state.copy(), source, item, count);
				best = cheaper(best, attempt);
			}

			for (RecipeHolder<CraftingRecipe> recipe : crafting(this.level).getOrDefault(item, List.of())) {
				best = cheaper(best, this.craft(state.copy(), recipe.value(), item, count, depth));
			}

			if (item == Items.OBSIDIAN) {
				best = cheaper(best, this.makeObsidian(state.copy(), count, depth));
			}

			if (count == 1 && new ItemStack(item).is(ItemTags.BEDS)) {
				best = cheaper(best, this.takeBed(state.copy(), item));
			}

			for (RecipeHolder<? extends AbstractCookingRecipe> recipe : smelting(this.level).getOrDefault(item, List.of())) {
				best = cheaper(best, this.smelt(state.copy(), recipe.value(), item, count, depth));
			}

			// The kitchen (Farmer's Delight, optional): the cutting board and the cooking pot.
			for (com.dinglebob.drifter.compat.FarmersDelight.CutRecipe recipe : cutting(this.level).getOrDefault(item, List.of())) {
				best = cheaper(best, this.cut(state.copy(), recipe, item, count, depth));
			}

			for (com.dinglebob.drifter.compat.FarmersDelight.PotRecipe recipe : potCooking(this.level).getOrDefault(item, List.of())) {
				best = cheaper(best, this.potCook(state.copy(), recipe, item, count, depth));
			}

			if (item == Items.WATER_BUCKET && com.dinglebob.drifter.compat.FarmersDelight.loaded()) {
				best = cheaper(best, this.fillBucket(state.copy(), count, depth));
			}

			return best;
		} finally {
			this.inProgress.pop();
		}
	}

	@Nullable
	private static State cheaper(@Nullable State a, @Nullable State b) {
		if (a == null) {
			return b;
		}

		return b == null || a.cost <= b.cost ? a : b;
	}

	/**
	 * How many it must carry in the end for a new gather or hunt action: what it already has that the plan uses, plus
	 * more. (Later actions for the same item merge into the first, adding just their own count.)
	 */
	private int targetTotal(State state, Item item, int more) {
		for (Action action : state.actions) {
			if (action instanceof Gather gather && gather.item() == item || action instanceof Hunt hunt && hunt.item() == item) {
				return more;
			}
		}

		int real = this.realInventory.getOrDefault(item, 0);
		return Math.max(0, real - state.count(item)) + more;
	}

	@Nullable
	private State gather(State state, Sources.Source source, Item item, int count, int depth) {
		// Lapis is only dug for its own sake (enchanting), never as an ingredient: no player goes down to Y 0 for blue dye
		// to make a bed (found in testing: a lapis trip for blue wool, on day 2, before an iron pickaxe).
		if (item == Items.LAPIS_LAZULI && depth > 1) {
			return null;
		}

		Predicate<BlockState> blocks = blockState -> source.blockGives(blockState, item);
		BlockState example = this.world.exampleOf(blocks);
		boolean known = example != null;
		boolean underground = source.blockIds().stream().anyMatch(id -> id.equals("minecraft:stone") || id.equals("minecraft:deepslate"));
		// Stone it hasn't seen: explore first (a cave, a cliff, a hillside) and dig straight down only as a last resort,
		// after a few legs of exploring found nothing (user's rule: learn the land first).
		String searchReason = "looking for " + name(item) + " (a cave or a cliff)";
		boolean digDown = !known && underground
			&& this.drifter.getDrifterBrain().timesExplored(searchReason) >= this.drifter.getDrifterBrain().searchLegsBeforeDigging();

		// A tool that can harvest it (tools aren't used up).
		Item tool = source.toolItem();
		if (tool != null) {
			BlockState sample = example != null ? example : defaultStateOf(source);
			if (sample != null && !this.carriesToolFor(state, sample)) {
				State withTool = this.obtainItem(state, tool, 1, depth + 1);
				if (withTool == null) {
					return null;
				}

				withTool.add(tool, 1);
				state = withTool;
			}
		}

		String label = name(item);
		if (known || digDown) {
			state.addAction(new Gather(item, label, blocks, stack -> stack.is(item), this.targetTotal(state, item, count), digDown));
			state.cost += COST_GATHER_TRIP + COST_GATHER_EACH * count + (digDown ? COST_DIG_DOWN : 0.0);
			return state;
		}

		// An ore it hasn't seen: a mining trip to where it's common, rather than wandering the surface.
		if (source.depth() != null) {
			state.addAction(new Mine(item, label, blocks, this.targetTotal(state, item, count), source.depth(), source.mountain()));
			state.cost += COST_MINE_TRIP + COST_GATHER_EACH * count;
			return state;
		}

		boolean wood = new ItemStack(item).is(ItemTags.LOGS);
		// It stops looking once it sees any block of this source it could take (any kind of log, not just this one).
		// Head for what it saw from afar: a grove, or bare stone and mountains when it needs stone.
		state.addAction(new Explore(wood ? "looking for trees" : underground ? searchReason : "looking for " + label,
			wood ? this.world.treesHint : underground ? this.world.stoneHint : null,
			drifter -> KnownWorld.anyTakeable(drifter, source::matchesBlock)));
		state.cost += COST_EXPLORE + COST_GATHER_EACH * count;
		return state;
	}

	/**
	 * A bed of this colour in a village house it remembers (under a roof, not its own home's), as an action: walk over,
	 * break it, pick it up. Null if it knows none.
	 */
	@Nullable
	private State takeBed(State state, Item item) {
		var dimension = this.drifter.level().dimension().location();
		Home home = this.drifter.getDrifterBrain().home();
		var entry = this.drifter.getMemory().nearest(com.dinglebob.drifter.memory.MemoryKind.STATION, dimension, this.drifter.position(), TAKE_BED_RANGE,
			e -> {
				net.minecraft.core.BlockPos pos = e.blockPos();
				var block = this.drifter.level().getBlockState(pos);
				return block.getBlock() instanceof net.minecraft.world.level.block.BedBlock && block.getBlock().asItem() == item
					&& !this.drifter.level().canSeeSky(pos.above()) && (home == null || !home.inOwnBuilding(pos, 1))
					&& !this.drifter.getDrifterBrain().blacklist().contains(pos, this.drifter.level().getGameTime())
					&& (home == null || home.houseBed == null || !home.houseBed.closerThan(pos, 2.0));
			});
		if (entry == null) {
			return null;
		}

		state.addAction(new TakeBed(entry.blockPos(), item));
		state.cost += COST_GATHER_TRIP + Math.sqrt(entry.blockPos().distSqr(this.drifter.blockPosition())) * SECONDS_PER_BLOCK * 2.0;
		return state;
	}

	/** How far it goes to take a village bed. */
	private static final double TAKE_BED_RANGE = 96.0;

	/** Seconds-ish per block of obsidian made with buckets: scoop, pour, wait, mine (9.4 s with a diamond pickaxe). */
	private static final double COST_OBSIDIAN_EACH = 30.0;

	/**
	 * Obsidian with buckets: a diamond pickaxe to mine it, two buckets (lava and water), and lava it knows of. Without
	 * known lava it explores for some (lava shows up in caves and deep mines).
	 */
	@Nullable
	private State makeObsidian(State state, int count, int depth) {
		BlockState obsidian = net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState();
		if (!this.carriesToolFor(state, obsidian)) {
			State withTool = this.obtainItem(state, Items.DIAMOND_PICKAXE, 1, depth + 1);
			if (withTool == null) {
				return null;
			}

			withTool.add(Items.DIAMOND_PICKAXE, 1);
			state = withTool;
		}

		int buckets = state.count(Items.BUCKET) + state.count(Items.WATER_BUCKET) + state.count(Items.LAVA_BUCKET);
		if (buckets < 2) {
			State withBuckets = this.obtainItem(state, Items.BUCKET, 2 - buckets, depth + 1);
			if (withBuckets == null) {
				return null;
			}

			withBuckets.add(Items.BUCKET, 2 - buckets);
			state = withBuckets;
		}

		if (!knowsLava(this.drifter)) {
			state.addAction(new Explore("looking for lava", null, Planner::knowsLava));
			state.cost += COST_EXPLORE;
		}

		state.addAction(new MakeObsidian(this.targetTotalObsidian(state, count)));
		state.cost += COST_GATHER_TRIP + COST_OBSIDIAN_EACH * count;
		return state;
	}

	private int targetTotalObsidian(State state, int more) {
		for (Action action : state.actions) {
			if (action instanceof MakeObsidian) {
				return more;
			}
		}

		return Math.max(0, this.realInventory.getOrDefault(Items.OBSIDIAN, 0) - state.count(Items.OBSIDIAN)) + more;
	}

	/** It remembers a lava source within a long walk. */
	public static boolean knowsLava(DrifterEntity drifter) {
		var dimension = drifter.level().dimension().location();
		return drifter.getMemory().nearest(com.dinglebob.drifter.memory.MemoryKind.DANGER, dimension, drifter.position(), 96.0,
			e -> e.label.equals("lava") && drifter.level().getFluidState(e.blockPos()).isSource()) != null;
	}

	@Nullable
	private static BlockState defaultStateOf(Sources.Source source) {
		for (String id : source.blockIds()) {
			if (!id.startsWith("#")) {
				return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState();
			}
		}

		return null;
	}

	private boolean carriesToolFor(State state, BlockState block) {
		for (Map.Entry<Item, Integer> entry : state.inventory.entrySet()) {
			if (entry.getValue() > 0 && new ItemStack(entry.getKey()).isCorrectToolForDrops(block)) {
				return true;
			}
		}

		return false;
	}

	@Nullable
	private State hunt(State state, Sources.Source source, Item item, int count) {
		EntityType<?> type = source.mobType();
		if (type == null) {
			return null;
		}

		// Wool of a colour: only sheep of that colour it has seen (a bed needs three of one colour; found as a need in step 4).
		if (source.variant() != null) {
			if (!this.world.knowsAnimal(type, source.variant())) {
				return null;
			}

			int kills = count;
			state.addAction(new Hunt(item, Set.of(type), stack -> stack.is(item), this.targetTotal(state, item, count), name(item), source.variant()));
			state.cost += COST_HUNT_KILL * kills;
			return state;
		}

		if (this.world.knowsAnimal(type)) {
			int kills = (int)Math.ceil(count / DROPS_PER_KILL);
			state.addAction(new Hunt(item, Set.of(type), stack -> stack.is(item), this.targetTotal(state, item, count), name(item), null));
			state.cost += COST_HUNT_KILL * kills;
			return state;
		}

		// It stops looking once it sees any adult animal it could hunt for food or materials.
		state.addAction(new Explore("looking for animals", null, drifter -> {
			long now = drifter.level().getGameTime();
			return drifter.getSenses().tracked().stream().anyMatch(a -> KnownWorld.isHuntable(a, now) && isPrey(a.entity.getType()));
		}));
		state.cost += COST_EXPLORE + COST_HUNT_KILL * count;
		return state;
	}

	/** Any animal the sources table lists as a source of something. */
	private static boolean isPrey(EntityType<?> type) {
		return Sources.all().stream().anyMatch(source -> source.mobType() == type);
	}

	@Nullable
	private State craft(State state, CraftingRecipe recipe, Item item, int count, int depth) {
		ItemStack result = recipe.getResultItem(this.level.registryAccess());
		int times = (int)Math.ceil(count / (double)result.getCount());
		// Group identical ingredients: a pickaxe needs 3 planks and 2 sticks.
		Map<List<Item>, Integer> needs = new LinkedHashMap<>();
		for (Ingredient ingredient : recipe.getIngredients()) {
			if (!ingredient.isEmpty()) {
				needs.merge(itemsOf(ingredient), times, Integer::sum);
			}
		}

		State current = state;
		for (Map.Entry<List<Item>, Integer> need : needs.entrySet()) {
			current = this.obtainAny(current, need.getKey(), need.getValue(), depth);
			if (current == null) {
				return null;
			}
		}

		if (!recipe.canCraftInDimensions(2, 2)) {
			current = this.station(current, Blocks.CRAFTING_TABLE, Items.CRAFTING_TABLE, this.world.tableNear, depth);
			if (current == null) {
				return null;
			}
		}

		current.addAction(new Craft(item, times));
		current.cost += COST_CRAFT;
		current.add(item, times * result.getCount() - count);
		return current;
	}

	@Nullable
	private State smelt(State state, AbstractCookingRecipe recipe, Item item, int count, int depth) {
		Ingredient input = recipe.getIngredients().get(0);
		List<Item> inputs = itemsOf(input);
		State current = this.obtainAny(state, inputs, count, depth);
		if (current == null) {
			return null;
		}

		// Fuel: what it carries first, coal or charcoal, then wood.
		State best = null;
		Smelt bestAction = null;
		List<List<Item>> fuels = List.of(List.of(Items.COAL), List.of(Items.CHARCOAL), tagItems(ItemTags.PLANKS), tagItems(ItemTags.LOGS));
		for (List<Item> fuel : fuels) {
			boolean coal = fuel.contains(Items.COAL) || fuel.contains(Items.CHARCOAL);
			int fuelCount = (int)Math.ceil(count / (coal ? COAL_ITEMS : WOOD_ITEMS));
			State attempt = this.obtainAny(current.copy(), fuel, fuelCount, depth);
			if (attempt != null && (best == null || attempt.cost < best.cost)) {
				best = attempt;
				Set<Item> fuelSet = Set.copyOf(fuel);
				bestAction = new Smelt(item, input::test, name(inputs.get(0)), count, stack -> fuelSet.contains(stack.getItem()),
					fuel.size() == 1 ? name(fuel.get(0)) : (fuel.contains(Items.OAK_PLANKS) ? "planks" : "logs"), fuelCount);
			}
		}

		if (best == null) {
			return null;
		}

		// Food can go in a smoker it knows instead (twice as fast); it doesn't build one yet (milestone 8, home).
		boolean smoker = new ItemStack(item).has(net.minecraft.core.component.DataComponents.FOOD) && this.world.smokerNear;
		best = this.station(best, Blocks.FURNACE, Items.FURNACE, this.world.furnaceNear || smoker, depth);
		if (best == null) {
			return null;
		}

		best.addAction(bestAction);
		best.cost += COST_SMELT_EACH * count;
		return best;
	}

	/** Seconds to put a thing on the board and cut it (a click to put it on, a click a cut). */
	private static final double COST_CUT_EACH = 1.0;
	/** Seconds to load the pot and take the meals out (its window, twice). */
	private static final double COST_POT = 8.0;
	/** Seconds to walk to water and fill a bucket. */
	private static final double COST_FILL_BUCKET = 15.0;

	/**
	 * A cutting board recipe (Farmer's Delight): the things to cut, a knife (or whatever the recipe cuts with; not used up),
	 * and a board it knows, carries or makes. What else comes off the board stays in the inventory.
	 */
	@Nullable
	private State cut(State state, com.dinglebob.drifter.compat.FarmersDelight.CutRecipe recipe, Item item, int count, int depth) {
		int yield = 0;
		for (ItemStack result : recipe.results()) {
			yield += result.is(item) ? result.getCount() : 0;
		}

		List<Item> inputs = itemsOf(recipe.input());
		if (yield <= 0 || inputs.isEmpty()) {
			return null;
		}

		int times = (int)Math.ceil(count / (double)yield);
		State current = this.obtainAny(state, inputs, times, depth);
		if (current == null) {
			return null;
		}

		// What it cuts with: one it carries, or the cheapest it can make (a flint knife).
		Ingredient tool = recipe.tool();
		boolean hasTool = current.inventory.entrySet().stream().anyMatch(e -> e.getValue() > 0 && tool.test(new ItemStack(e.getKey())));
		List<Item> tools = itemsOf(tool);
		if (!hasTool) {
			State withTool = null;
			Item madeTool = null;
			for (Item candidate : tools.stream().limit(3).toList()) {
				State attempt = this.obtainItem(current.copy(), candidate, 1, depth + 1);
				if (attempt != null && (withTool == null || attempt.cost < withTool.cost)) {
					withTool = attempt;
					madeTool = candidate;
				}
			}

			if (withTool == null) {
				return null;
			}

			withTool.add(madeTool, 1);
			current = withTool;
		}

		Item boardItem = com.dinglebob.drifter.compat.FarmersDelight.item("cutting_board");
		Block board = com.dinglebob.drifter.compat.FarmersDelight.block("cutting_board");
		if (boardItem == null || board == null) {
			return null;
		}

		current = this.station(current, board, boardItem, Stations.usable(this.drifter, board) != null, depth);
		if (current == null) {
			return null;
		}

		current.addAction(new Cut(item, recipe.input()::test, name(inputs.get(0)), times, tool::test, tools.isEmpty() ? "a knife" : name(tools.get(0))));
		current.cost += COST_CRAFT + COST_CUT_EACH * times;
		for (ItemStack result : recipe.results()) {
			current.add(result.getItem(), result.getCount() * times - (result.is(item) ? count : 0));
		}

		return current;
	}

	/**
	 * A cooking pot recipe (Farmer's Delight): one of each ingredient per batch, a bowl (or whatever it's served in) per
	 * meal, and a pot on a lit stove it knows. It doesn't set up a kitchen for one meal (its house gets one: KitchenGoal).
	 */
	@Nullable
	private State potCook(State state, com.dinglebob.drifter.compat.FarmersDelight.PotRecipe recipe, Item item, int count, int depth) {
		if (this.pot() == null) {
			return null;
		}

		int batches = (int)Math.ceil(count / (double)Math.max(1, recipe.result().getCount()));
		// Identical ingredients together (apple cider: two apples a batch).
		Map<List<Item>, Integer> needs = new LinkedHashMap<>();
		List<Predicate<ItemStack>> inputs = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		for (Ingredient ingredient : recipe.inputs()) {
			List<Item> items = itemsOf(ingredient);
			if (items.isEmpty()) {
				return null;
			}

			needs.merge(items, batches, Integer::sum);
			inputs.add(ingredient::test);
			labels.add(name(items.get(0)));
		}

		State current = state;
		for (Map.Entry<List<Item>, Integer> need : needs.entrySet()) {
			current = this.obtainAny(current, need.getKey(), need.getValue(), depth);
			if (current == null) {
				return null;
			}
		}

		Item container = recipe.container().isEmpty() ? null : recipe.container().getItem();
		if (container != null) {
			current = this.obtainAny(current, List.of(container), batches, depth);
			if (current == null) {
				return null;
			}
		}

		current.addAction(new PotCook(item, List.copyOf(inputs), List.copyOf(labels), batches, container, recipe.cookTicks()));
		current.cost += COST_POT + recipe.cookTicks() / 20.0 * batches;
		current.add(item, batches * recipe.result().getCount() - count);
		return current;
	}

	@Nullable
	private BlockPos potFound;
	private boolean potLooked;

	/** The cooking pot on a lit stove it would cook in (its kitchen), or null. */
	@Nullable
	private BlockPos pot() {
		if (!this.potLooked) {
			this.potLooked = true;
			this.potFound = usablePot(this.drifter);
		}

		return this.potFound;
	}

	/** A cooking pot it knows that sits on a lit stove (or other heat), near or at home; null if none. */
	@Nullable
	public static BlockPos usablePot(DrifterEntity drifter) {
		Block pot = com.dinglebob.drifter.compat.FarmersDelight.block("cooking_pot");
		if (pot == null) {
			return null;
		}

		BlockPos pos = Stations.usable(drifter, pot);
		// (A stove that's gone out it lights again with flint and steel.)
		return pos != null && (com.dinglebob.drifter.compat.FarmersDelight.heatedFrom(drifter.level(), pos)
			|| com.dinglebob.drifter.compat.FarmersDelight.unlitStove(drifter.level().getBlockState(pos.below()))
				&& drifter.getInventory().count(stack -> stack.is(Items.FLINT_AND_STEEL)) > 0) ? pos : null;
	}

	/** Water buckets: a bucket each, filled at water it has seen. */
	@Nullable
	private State fillBucket(State state, int count, int depth) {
		if (com.dinglebob.drifter.brain.activity.FarmActivity.nearestWater(this.drifter, 48) == null) {
			return null;
		}

		State current = this.obtainAny(state, List.of(Items.BUCKET), count, depth);
		if (current == null) {
			return null;
		}

		current.addAction(new FillBucket(count));
		current.cost += COST_FILL_BUCKET * count;
		return current;
	}

	/** Makes sure a crafting table or furnace is nearby: one it knows, one it plans to place, or a new one. */
	@Nullable
	private State station(State state, Block block, Item item, boolean known, int depth) {
		if (known || state.placed.contains(block)) {
			return state;
		}

		State withItem = this.obtainAny(state, List.of(item), 1, depth);
		if (withItem == null) {
			return null;
		}

		withItem.addAction(new Place(item));
		withItem.placed.add(block);
		withItem.cost += COST_PLACE;
		return withItem;
	}

	// ---------------------------------------------------------------------------------------------
	// Recipe index (from the game's recipe manager, section 8 "Acquisition rules")
	// ---------------------------------------------------------------------------------------------

	private static RecipeManager indexedFor;
	private static Map<Item, List<RecipeHolder<CraftingRecipe>>> craftingIndex = Map.of();
	private static Map<Item, List<RecipeHolder<? extends AbstractCookingRecipe>>> smeltingIndex = Map.of();
	/** Raw food to what it cooks into (from the game's furnace recipes). */
	private static Map<Item, Item> cookedIndex = Map.of();
	/** Farmer's Delight's cutting board and cooking pot recipes by what they make (empty without the mod). */
	private static Map<Item, List<com.dinglebob.drifter.compat.FarmersDelight.CutRecipe>> cuttingIndex = Map.of();
	private static Map<Item, List<com.dinglebob.drifter.compat.FarmersDelight.PotRecipe>> potIndex = Map.of();

	private static synchronized void index(Level level) {
		RecipeManager manager = level.getRecipeManager();
		if (manager == indexedFor) {
			return;
		}

		Map<Item, List<RecipeHolder<CraftingRecipe>>> crafting = new HashMap<>();
		for (RecipeHolder<CraftingRecipe> holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
			CraftingRecipe recipe = holder.value();
			ItemStack result = recipe.getResultItem(level.registryAccess());
			if (!recipe.isSpecial() && !result.isEmpty() && recipe.canCraftInDimensions(3, 3) && !recipe.getIngredients().isEmpty()) {
				crafting.computeIfAbsent(result.getItem(), item -> new ArrayList<>()).add(holder);
			}
		}

		Map<Item, List<RecipeHolder<? extends AbstractCookingRecipe>>> smelting = new HashMap<>();
		for (RecipeHolder<? extends AbstractCookingRecipe> holder : manager.getAllRecipesFor(RecipeType.SMELTING)) {
			ItemStack result = holder.value().getResultItem(level.registryAccess());
			if (!result.isEmpty()) {
				smelting.computeIfAbsent(result.getItem(), item -> new ArrayList<>()).add(holder);
			}
		}

		Map<Item, Item> cooked = new HashMap<>();
		smelting.forEach((output, recipes) -> {
			if (new ItemStack(output).has(net.minecraft.core.component.DataComponents.FOOD)) {
				for (RecipeHolder<? extends AbstractCookingRecipe> holder : recipes) {
					for (Item raw : itemsOf(holder.value().getIngredients().get(0))) {
						if (new ItemStack(raw).has(net.minecraft.core.component.DataComponents.FOOD)) {
							cooked.putIfAbsent(raw, output);
						}
					}
				}
			}
		});

		Map<Item, List<com.dinglebob.drifter.compat.FarmersDelight.CutRecipe>> cutting = new HashMap<>();
		for (com.dinglebob.drifter.compat.FarmersDelight.CutRecipe recipe : com.dinglebob.drifter.compat.FarmersDelight.cutRecipes(level)) {
			for (ItemStack result : recipe.results()) {
				List<com.dinglebob.drifter.compat.FarmersDelight.CutRecipe> list = cutting.computeIfAbsent(result.getItem(), item -> new ArrayList<>());
				if (!list.contains(recipe)) {
					list.add(recipe);
				}
			}
		}

		Map<Item, List<com.dinglebob.drifter.compat.FarmersDelight.PotRecipe>> pot = new HashMap<>();
		for (com.dinglebob.drifter.compat.FarmersDelight.PotRecipe recipe : com.dinglebob.drifter.compat.FarmersDelight.potRecipes(level)) {
			pot.computeIfAbsent(recipe.result().getItem(), item -> new ArrayList<>()).add(recipe);
		}

		craftingIndex = crafting;
		smeltingIndex = smelting;
		cookedIndex = cooked;
		cuttingIndex = cutting;
		potIndex = pot;
		indexedFor = manager;
	}

	private static Map<Item, List<RecipeHolder<CraftingRecipe>>> crafting(Level level) {
		index(level);
		return craftingIndex;
	}

	private static Map<Item, List<RecipeHolder<? extends AbstractCookingRecipe>>> smelting(Level level) {
		index(level);
		return smeltingIndex;
	}

	private static Map<Item, List<com.dinglebob.drifter.compat.FarmersDelight.CutRecipe>> cutting(Level level) {
		index(level);
		return cuttingIndex;
	}

	private static Map<Item, List<com.dinglebob.drifter.compat.FarmersDelight.PotRecipe>> potCooking(Level level) {
		index(level);
		return potIndex;
	}

	/** Every cooking pot recipe by its meal (Farmer's Delight; empty without it). */
	public static Map<Item, List<com.dinglebob.drifter.compat.FarmersDelight.PotRecipe>> potMeals(Level level) {
		return potCooking(level);
	}

	/** What this raw food cooks into in a furnace or smoker, or null if it isn't food that cooks (section 10.5). */
	@Nullable
	public static Item cookedForm(Level level, Item raw) {
		index(level);
		return cookedIndex.get(raw);
	}

	private static List<Item> itemsOf(Ingredient ingredient) {
		return Arrays.stream(ingredient.getItems()).map(ItemStack::getItem).distinct().toList();
	}

	/** Items in a tag that burn in a furnace (crimson and warped wood don't). */
	private static List<Item> tagItems(net.minecraft.tags.TagKey<Item> tag) {
		List<Item> items = new ArrayList<>();
		BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(holder -> {
			if (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.isFuel(new ItemStack(holder.value()))) {
				items.add(holder.value());
			}
		});
		return items;
	}

	public static String name(Item item) {
		return item.getDescription().getString().toLowerCase(Locale.ROOT);
	}
}
