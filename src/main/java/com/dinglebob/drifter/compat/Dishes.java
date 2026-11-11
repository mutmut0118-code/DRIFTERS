package com.dinglebob.drifter.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.dinglebob.drifter.brain.Food;
import com.dinglebob.drifter.brain.Kitchen;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Dishes (the Farmer's Delight addons, 2026-11-10: "fold it into everyday cooking"): food a kitchen makes, whichever mod adds
 * it. A dish is food (not bad food) made in a cooking pot, cut on a board from another dish, put together at a crafting table
 * from two or more different things (one of them food), ground in a mortar or served from a feast (those two stations add
 * their results through {@link #addSource}); and it's a proper meal: on Farmer's Delight's meal list, filling
 * ({@link Kitchen#MEAL_NUTRITION} or more), or, from an addon (neither vanilla nor Farmer's Delight),
 * {@link #DISH_SNACK_NUTRITION} or more (a sandwich, toast; not a vanilla cookie, not Farmer's Delight's tomato sauce). Vanilla's food is never a dish unless it's on Farmer's Delight's meal list. That list always counts, however it's made (a feast's roast chicken), so the meals
 * it cooked and ate before still count as meals. Nothing without Farmer's Delight: no kitchen, no dishes.
 *
 * <p>Worked out once per recipe reload (keyed on the recipe manager, like the planner's recipe index), never per tick.
 */
public final class Dishes {
	/** Below this, a mod's food that a kitchen makes is an ingredient or a nibble, not a dish. */
	public static final int DISH_SNACK_NUTRITION = 4;

	public static final String POT = "cooking pot";
	public static final String BOARD = "cutting board";
	public static final String TABLE = "crafting table";
	public static final String OTHER = "other";

	/** One way to make a dish: the station, and what goes in (empty for a station that registered only its results). */
	public record Recipe(String station, List<Ingredient> inputs) {
	}

	private record Source(String station, Function<Level, Collection<Item>> results) {
	}

	private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

	private static volatile RecipeManager indexedFor;
	private static Set<Item> dishes = Set.of();
	/** Dishes a station makes, with each way to make them (the meal list's dishes no station makes aren't here). */
	private static Map<Item, List<Recipe>> recipes = Map.of();

	private Dishes() {
	}

	/** Another station's dishes (Thai's Delight's mortar, feasts' servings): the results it makes, worked out per reload. */
	public static void addSource(Function<Level, Collection<Item>> results) {
		addSource(OTHER, results);
	}

	/** {@link #addSource(Function)} with the station's name (for the debug list). */
	public static synchronized void addSource(String station, Function<Level, Collection<Item>> results) {
		SOURCES.add(new Source(station, results));
		indexedFor = null;
	}

	/** It's a dish (see the class). */
	public static boolean isDish(Level level, Item item) {
		index(level);
		return dishes.contains(item);
	}

	public static boolean isDish(Level level, ItemStack stack) {
		return !stack.isEmpty() && isDish(level, stack.getItem());
	}

	/** A dish from one of the addons: neither vanilla's nor Farmer's Delight's own (the dishes it has to learn). */
	public static boolean isAddonDish(Item item) {
		return dishes.contains(item) && addonNamespace(item);
	}

	/** Every dish (empty without Farmer's Delight). */
	public static Set<Item> all(Level level) {
		index(level);
		return dishes;
	}

	/** Dishes a station makes, and how (for choosing a meal: what it could make). */
	public static Map<Item, List<Recipe>> makeable(Level level) {
		index(level);
		return recipes;
	}

	/** The ways to make this dish (empty if none, or not a dish). */
	public static List<Recipe> recipes(Level level, Item item) {
		index(level);
		return recipes.getOrDefault(item, List.of());
	}

	private static boolean food(Item item) {
		ItemStack stack = new ItemStack(item);
		return stack.get(DataComponents.FOOD) != null && !Food.isBad(stack);
	}

	/** Good enough to be a dish when a kitchen makes it (the meal list, filling, or a mod's sandwich). */
	private static boolean meal(Item item) {
		ItemStack stack = new ItemStack(item);
		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food == null || Food.isBad(stack) || vanilla(item) && !FarmersDelight.isMeal(stack)) {
			return false;
		}

		// (The snack rule only for the addons' food: Farmer's Delight's own tomato sauce and dog food (4) are for cooking with
		// and for dogs, and its meals stay as they were before the addons; the 0.23.22-side.3 fix.)
		return FarmersDelight.isMeal(stack) || food.nutrition() >= Kitchen.MEAL_NUTRITION
			|| food.nutrition() >= DISH_SNACK_NUTRITION && addonNamespace(item);
	}

	/** Vanilla's food is never a dish (a golden carrot, a pumpkin pie: plain food as before; controller ruling), except what's on
	 * Farmer's Delight's meal list (rabbit stew, beetroot soup, mushroom stew), as before. */
	private static boolean vanilla(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft");
	}

	private static boolean addonNamespace(Item item) {
		String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
		return !namespace.equals("minecraft") && !namespace.equals(FarmersDelight.MOD_ID);
	}

	private static void index(Level level) {
		// (Asked for every slot it eats from or counts meals in: the usual case without the lock.)
		if (level.getRecipeManager() != indexedFor) {
			build(level);
		}
	}

	private static synchronized void build(Level level) {
		RecipeManager manager = level.getRecipeManager();
		if (manager == indexedFor) {
			return;
		}

		Map<Item, List<Recipe>> found = new LinkedHashMap<>();
		Set<Item> all = new LinkedHashSet<>();
		if (FarmersDelight.loaded()) {
			for (FarmersDelight.PotRecipe recipe : FarmersDelight.potRecipes(level)) {
				add(found, recipe.result().getItem(), new Recipe(POT, recipe.inputs()));
			}

			for (RecipeHolder<CraftingRecipe> holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
				CraftingRecipe recipe = holder.value();
				ItemStack result = recipe.getResultItem(level.registryAccess());
				if (recipe.isSpecial() || result.isEmpty() || !recipe.canCraftInDimensions(3, 3) || !food(result.getItem())) {
					continue;
				}

				// (Two or more different things, one of them food: a sandwich, a salad; not bread from wheat, not carrots out of a crate.)
				List<Ingredient> inputs = recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
				Set<List<Item>> kinds = new HashSet<>();
				boolean anyFood = false;
				for (Ingredient ingredient : inputs) {
					List<Item> items = java.util.Arrays.stream(ingredient.getItems()).map(ItemStack::getItem).distinct().toList();
					kinds.add(items);
					anyFood |= items.stream().anyMatch(item -> new ItemStack(item).has(DataComponents.FOOD));
				}

				if (kinds.size() >= 2 && anyFood) {
					add(found, result.getItem(), new Recipe(TABLE, inputs));
				}
			}

			for (Source source : SOURCES) {
				for (Item item : source.results().apply(level)) {
					add(found, item, new Recipe(source.station(), List.of()));
				}
			}

			found.keySet().removeIf(item -> !meal(item));
			// (The board's: cut from a dish, a slice of it. Again while that finds more, a slice of a slice.)
			List<FarmersDelight.CutRecipe> cuts = FarmersDelight.cutRecipes(level);
			boolean more = true;
			while (more) {
				more = false;
				for (FarmersDelight.CutRecipe cut : cuts) {
					boolean fromDish = false;
					for (ItemStack input : cut.input().getItems()) {
						fromDish |= found.containsKey(input.getItem());
					}

					if (!fromDish) {
						continue;
					}

					for (ItemStack result : cut.results()) {
						Item item = result.getItem();
						Recipe recipe = new Recipe(BOARD, List.of(cut.input()));
						if (meal(item) && (!found.containsKey(item) || !found.get(item).contains(recipe))) {
							more |= !found.containsKey(item);
							add(found, item, recipe);
						}
					}
				}
			}

			all.addAll(found.keySet());
			// (Farmer's Delight's meal list, however it's made: a feast's servings count before anything cooks feasts.)
			BuiltInRegistries.ITEM.getTagOrEmpty(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
				FarmersDelight.id("meals"))).forEach(holder -> {
					if (food(holder.value())) {
						all.add(holder.value());
					}
				});
		}

		found.replaceAll((item, list) -> List.copyOf(list));
		recipes = Collections.unmodifiableMap(found);
		dishes = Collections.unmodifiableSet(all);
		indexedFor = manager;
	}

	private static void add(Map<Item, List<Recipe>> found, Item item, Recipe recipe) {
		found.computeIfAbsent(item, key -> new ArrayList<>()).add(recipe);
	}
}
