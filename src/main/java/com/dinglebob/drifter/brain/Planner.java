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
	/** Rough costs in seconds. */
	private static final double COST_CRAFT = 3.0;
	private static final double COST_PLACE = 3.0;
	private static final double COST_GATHER_EACH = 5.0;
	private static final double COST_GATHER_TRIP = 8.0;
	private static final double COST_DIG_DOWN = 40.0;
	private static final double COST_EXPLORE = 150.0;
	private static final double COST_HUNT_KILL = 25.0;
	private static final double DROPS_PER_KILL = 1.5;
	private static final double COST_SMELT_EACH = 10.0;
	/** Items of each fuel per smelted item: coal and charcoal smelt 8, planks and logs 1.5. */
	private static final double COAL_ITEMS = 8.0;
	private static final double WOOD_ITEMS = 1.5;

	// ---------------------------------------------------------------------------------------------
	// Actions
	// ---------------------------------------------------------------------------------------------

	public sealed interface Action permits Gather, Hunt, Craft, Place, Smelt, Explore {
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
	public record Hunt(Item item, Set<EntityType<?>> prey, Predicate<ItemStack> items, int total, String label) implements Action {
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

	public record Smelt(Predicate<ItemStack> input, String inputLabel, int count, Predicate<ItemStack> fuel, String fuelLabel, int fuelCount)
		implements Action {
		@Override
		public String describe() {
			return "smelt " + this.count + " " + this.inputLabel + " with " + this.fuelCount + " " + this.fuelLabel;
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
		double cost;

		State(Map<Item, Integer> inventory, List<Action> actions, Set<Block> placed, double cost) {
			this.inventory = inventory;
			this.actions = actions;
			this.placed = placed;
			this.cost = cost;
		}

		State copy() {
			return new State(new HashMap<>(this.inventory), new ArrayList<>(this.actions), new HashSet<>(this.placed), this.cost);
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
					this.actions.set(i, new Hunt(a.item(), a.prey(), a.items(), a.total() + b.total(), a.label()));
					return;
				}

				if (earlier instanceof Craft a && action instanceof Craft b && a.item() == b.item()) {
					this.actions.set(i, new Craft(a.item(), a.times() + b.times()));
					return;
				}

				if (earlier instanceof Explore a && action instanceof Explore b && a.reason().equals(b.reason())) {
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
		State start = new State(new HashMap<>(planner.realInventory), new ArrayList<>(), new HashSet<>(), 0.0);
		State end = planner.obtainAny(start, goal, count, 0);
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

		// Then the cheapest way to get the rest of one kind.
		List<Item> options = new ArrayList<>(carried);
		for (Item item : candidates) {
			if (options.size() >= MAX_CANDIDATES) {
				break;
			}

			if (!options.contains(item)) {
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

	/** Gets {@code count} of one item and uses them up (extras made along the way stay in the inventory). */
	@Nullable
	private State obtainItem(State state, Item item, int count, int depth) {
		if (depth > MAX_DEPTH || this.inProgress.contains(item)) {
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

			for (RecipeHolder<? extends AbstractCookingRecipe> recipe : smelting(this.level).getOrDefault(item, List.of())) {
				best = cheaper(best, this.smelt(state.copy(), recipe.value(), item, count, depth));
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
		Predicate<BlockState> blocks = blockState -> source.blockGives(blockState, item);
		BlockState example = this.world.exampleOf(blocks);
		boolean known = example != null;
		boolean digDown = !known && source.blockIds().stream().anyMatch(id -> id.equals("minecraft:stone") || id.equals("minecraft:deepslate"));

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

		boolean wood = new ItemStack(item).is(ItemTags.LOGS);
		// It stops looking once it sees any block of this source it could take (any kind of log, not just this one).
		state.addAction(new Explore("looking for " + (wood ? "trees" : label), wood ? this.world.treesHint : null,
			drifter -> KnownWorld.anyTakeable(drifter, source::matchesBlock)));
		state.cost += COST_EXPLORE + COST_GATHER_EACH * count;
		return state;
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

		if (this.world.knowsAnimal(type)) {
			int kills = (int)Math.ceil(count / DROPS_PER_KILL);
			state.addAction(new Hunt(item, Set.of(type), stack -> stack.is(item), this.targetTotal(state, item, count), name(item)));
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
				bestAction = new Smelt(input::test, name(inputs.get(0)), count, stack -> fuelSet.contains(stack.getItem()),
					fuel.size() == 1 ? name(fuel.get(0)) : (fuel.contains(Items.OAK_PLANKS) ? "planks" : "logs"), fuelCount);
			}
		}

		if (best == null) {
			return null;
		}

		best = this.station(best, Blocks.FURNACE, Items.FURNACE, this.world.furnaceNear, depth);
		if (best == null) {
			return null;
		}

		best.addAction(bestAction);
		best.cost += COST_SMELT_EACH * count;
		return best;
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

		craftingIndex = crafting;
		smeltingIndex = smelting;
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
