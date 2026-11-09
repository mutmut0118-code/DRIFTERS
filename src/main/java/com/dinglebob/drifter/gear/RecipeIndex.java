package com.dinglebob.drifter.gear;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.social.ItemValues;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

/**
 * What the recipes say about gear, worked out once per reload. This part: an item's key ingredient (the one worth most,
 * which says what the item costs) and which items unlock which.
 */
public final class RecipeIndex {
	/**
	 * Values call recipes and recipes call values (a gear item can be an ingredient of other gear): past this depth a
	 * lookup gives up (no key, no cost) instead of looping.
	 */
	public static final int MAX_DEPTH = 3;

	private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
	/** How many times a lookup on this thread was cut short by the depth guard (an answer is complete if this did not move). */
	private static final ThreadLocal<int[]> TRIPS = ThreadLocal.withInitial(() -> new int[1]);
	private static final Map<Item, Optional<Ingredient>> KEYS = new HashMap<>();
	private static Map<Item, RecipeHolder<?>> byResult;
	/** Which modded gear each item is the key ingredient of (built once per reload, on the first ask). */
	private static Map<Item, List<Item>> unlocks;

	private RecipeIndex() {
	}

	static void clear() {
		synchronized (KEYS) {
			KEYS.clear();
			byResult = null;
			unlocks = null;
		}
	}

	/** How deep the current thread's value-and-recipe lookups are. */
	public static int depth() {
		return DEPTH.get()[0];
	}

	/** Runs a lookup one level deeper; {@code fallback} instead if that is past {@link #MAX_DEPTH}. */
	public static <T> T guarded(Supplier<T> lookup, T fallback) {
		int[] depth = DEPTH.get();
		if (depth[0] >= MAX_DEPTH) {
			TRIPS.get()[0]++;
			return fallback;
		}

		depth[0]++;
		try {
			return lookup.get();
		} finally {
			depth[0]--;
		}
	}

	/**
	 * The ingredient of this item's recipe that is worth most (ties: not a stick or planks), or null with no recipe (or a
	 * lookup that went too deep). The first recipe that makes the item counts.
	 */
	@Nullable
	public static Ingredient key(Level level, Item result) {
		synchronized (KEYS) {
			Optional<Ingredient> cached = KEYS.get(result);
			if (cached != null) {
				return cached.orElse(null);
			}

			if (depth() >= MAX_DEPTH) {
				TRIPS.get()[0]++;
				return null;
			}

			int[] trips = TRIPS.get();
			int before = trips[0];
			Ingredient key = guarded(() -> compute(level, result), null);
			// Only a complete answer is kept: one cut short by the depth guard (anywhere inside the lookup) would be wrong for a
			// shallower caller. Any depth may keep a complete one, including calls from the cost path.
			if (trips[0] == before) {
				KEYS.put(result, Optional.ofNullable(key));
			}

			return key;
		}
	}

	/**
	 * The modded gear that holding this item unlocks (the recipe book's rule: you have what the recipe is mostly made of).
	 * One pass over the recipes per reload; vanilla results are left out (every Drifter knows those).
	 */
	public static List<Item> unlockedBy(Level level, Item ingredient) {
		Map<Item, List<Item>> index;
		synchronized (KEYS) {
			index = unlocks;
		}

		if (index == null) {
			index = buildUnlocks(level);
		}

		return index.getOrDefault(ingredient, List.of());
	}

	private static Map<Item, List<Item>> buildUnlocks(Level level) {
		// The recipes by result first (key() fills it); then each modded gear result's key ingredient.
		key(level, Items.AIR);
		List<Item> results;
		synchronized (KEYS) {
			results = new ArrayList<>(byResult == null ? List.of() : byResult.keySet());
		}

		Map<Item, LinkedHashSet<Item>> built = new HashMap<>();
		for (Item result : results) {
			if ("minecraft".equals(BuiltInRegistries.ITEM.getKey(result).getNamespace()) || !GearValue.isGear(new ItemStack(result))) {
				continue;
			}

			Ingredient key = key(level, result);
			if (key == null) {
				continue;
			}

			for (ItemStack option : key.getItems()) {
				built.computeIfAbsent(option.getItem(), item -> new LinkedHashSet<>()).add(result);
			}
		}

		Map<Item, List<Item>> index = new HashMap<>();
		built.forEach((item, set) -> index.put(item, List.copyOf(set)));
		synchronized (KEYS) {
			if (unlocks == null) {
				unlocks = index;
			}

			return unlocks;
		}
	}

	/** The first item an ingredient accepts (empty: nothing registered for a tag). */
	public static ItemStack first(Ingredient ingredient) {
		ItemStack[] items = ingredient.getItems();
		return items.length == 0 ? ItemStack.EMPTY : items[0];
	}

	@Nullable
	private static Ingredient compute(Level level, Item result) {
		if (byResult == null) {
			Map<Item, RecipeHolder<?>> map = new HashMap<>();
			for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
				ItemStack made = holder.value().getResultItem(level.registryAccess());
				if (!made.isEmpty()) {
					map.putIfAbsent(made.getItem(), holder);
				}
			}

			byResult = map;
		}

		RecipeHolder<?> holder = byResult.get(result);
		if (holder == null) {
			return null;
		}

		Ingredient best = null;
		double bestValue = -1.0;
		boolean bestPlain = true;
		for (Ingredient ingredient : holder.value().getIngredients()) {
			ItemStack stack = first(ingredient);
			if (stack.isEmpty()) {
				continue;
			}

			double value = ItemValues.each(stack);
			boolean plain = stack.is(Items.STICK) || stack.is(ItemTags.PLANKS);
			if (value > bestValue || (value == bestValue && bestPlain && !plain)) {
				best = ingredient;
				bestValue = value;
				bestPlain = plain;
			}
		}

		return best;
	}
}
