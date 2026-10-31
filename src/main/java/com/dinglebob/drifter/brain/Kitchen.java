package com.dinglebob.drifter.brain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.compat.FarmersDelight;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.memory.MemoryKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * The kitchen (Farmer's Delight, optional; the user, 2026-10-31): a house gets a stove with the cooking pot on it, a cutting
 * board on a cabinet, a skillet and cabinets, the way players set one up (cut the ingredients on the board, cook them in the
 * pot on a lit stove, serve into bowls). This is what the brain knows about it: what it still wants for its kitchen, which
 * meal it would cook, what it has to say about its cooking. Nothing here does anything without the mod.
 */
public final class Kitchen {
	/** A cooking plan dearer than this (seconds) isn't worth it for a meal: it eats what it has. */
	public static final double MEAL_PLAN_LIMIT = 300.0;
	/** Meals it plans for when choosing (the best few by how much they fill). */
	private static final int MEALS_CONSIDERED = 8;
	/** How long a choice of meal stands (ticks). */
	private static final long CHOICE_TICKS = 20L * 30;
	private static final long CHOICE_MIN_TICKS = 20L * 5;
	/** Cooked within this long: something to talk about. */
	public static final long COOKED_LATELY = 24000L * 2;
	/** Below this (and not on the mod's meal list) a pot recipe is an ingredient, not dinner. */
	private static final int MEAL_NUTRITION = 6;
	/** Meals it likes to have on it (or in its cabinets) before it cooks more. */
	public static final int MEALS_WANTED = 3;

	private Kitchen() {
	}

	/** It wants a kitchen: the mod's there, and it lives in a house of its own it built from a plan. */
	public static boolean wanted(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		return FarmersDelight.loaded() && home != null && !home.borrowed() && home.planDone;
	}

	/** It knows one of these at home (stations it remembers there). */
	public static boolean atHome(DrifterEntity drifter, @Nullable Block block) {
		Home home = drifter.getDrifterBrain().home();
		if (home == null || block == null) {
			return false;
		}

		String id = BuiltInRegistries.BLOCK.getKey(block).toString();
		return drifter.getMemory().nearest(MemoryKind.STATION, drifter.level().dimension().location(), Vec3.atCenterOf(home.center), Stations.USE_RANGE,
			entry -> entry.label.equals(id)) != null;
	}

	/** A cabinet among its home's storage. */
	private static boolean cabinetAtHome(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		return home != null && home.chests().stream().anyMatch(pos -> FarmersDelight.isCabinet(drifter.level().getBlockState(pos)));
	}

	private static boolean carries(DrifterEntity drifter, @Nullable Item item) {
		return item != null && drifter.getInventory().count(stack -> stack.is(item)) > 0;
	}

	/**
	 * What it works toward for its kitchen once its house is up (after the iron basics: the pot and stove take iron): a knife
	 * and a cutting board, then the stove and the pot, then a cabinet. Each done once it has one at home or on it.
	 */
	public static List<Strategist.Target> targets(DrifterEntity drifter) {
		if (!wanted(drifter)) {
			return List.of();
		}

		List<Strategist.Target> targets = new ArrayList<>();
		Item knife = FarmersDelight.item("flint_knife");
		if (knife != null) {
			targets.add(new Strategist.Target("a knife", List.of(knife), 1, d -> Strategist.has(d, FarmersDelight::isKnife)));
		}

		station(targets, "a cutting board", "cutting_board");
		station(targets, "a stove", "stove");
		station(targets, "a cooking pot", "cooking_pot");
		Item cabinet = FarmersDelight.item("oak_cabinet");
		if (cabinet != null) {
			targets.add(new Strategist.Target("a kitchen cabinet", List.of(cabinet), 1,
				d -> cabinetAtHome(d) || Strategist.has(d, stack -> FarmersDelight.isCabinet(blockOf(stack)))));
		}

		return targets;
	}

	private static void station(List<Strategist.Target> targets, String label, String path) {
		Item item = FarmersDelight.item(path);
		Block block = FarmersDelight.block(path);
		if (item != null && block != null) {
			targets.add(new Strategist.Target(label, List.of(item), 1, d -> carries(d, item) || atHome(d, block)));
		}
	}

	private static net.minecraft.world.level.block.state.BlockState blockOf(ItemStack stack) {
		return net.minecraft.world.level.block.Block.byItem(stack.getItem()).defaultBlockState();
	}

	// ---------------------------------------------------------------------------------------------
	// Meals
	// ---------------------------------------------------------------------------------------------

	/**
	 * How good a meal is to have: how much it fills, its saturation, and a bit more for the mod's comfort and nourishment
	 * (hunger that stops going down for minutes).
	 */
	public static double value(Item meal) {
		ItemStack stack = new ItemStack(meal);
		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food == null) {
			return 0.0;
		}

		return food.nutrition() + food.saturation() + (food.effects().isEmpty() ? 0.0 : 3.0);
	}

	/** Meals it carries (the mod's meal list). */
	public static int mealsCarried(DrifterEntity drifter) {
		return drifter.getInventory().count(FarmersDelight::isMeal);
	}

	private static final Map<java.util.UUID, Choice> CHOICES = new java.util.concurrent.ConcurrentHashMap<>();

	private record Choice(@Nullable Item meal, long at, long carried) {
	}

	/** A fingerprint of what it carries (a new choice when that changes). */
	private static long carried(DrifterEntity drifter) {
		long key = 17;
		for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = drifter.getInventory().getItem(slot);
			key = key * 31L + (stack.isEmpty() ? 0 : BuiltInRegistries.ITEM.getId(stack.getItem()) * 100L + stack.getCount());
		}

		return key;
	}

	/**
	 * The meal it would cook now: of the best few pot meals, the one worth most for what it takes (a plan within
	 * {@link #MEAL_PLAN_LIMIT} that needs no exploring). Null if it has no kitchen or nothing it could cook soon. Kept for half
	 * a minute while what it carries stays the same.
	 */
	@Nullable
	public static Item chooseMeal(DrifterEntity drifter, KnownWorld world) {
		long now = drifter.level().getGameTime();
		long carried = carried(drifter);
		Choice known = CHOICES.get(drifter.getUUID());
		// (At most every few seconds even while its pockets change: choosing plans several meals.)
		if (known != null && (now - known.at() < CHOICE_MIN_TICKS || now - known.at() < CHOICE_TICKS && known.carried() == carried)) {
			return known.meal();
		}

		Item best = null;
		if (FarmersDelight.loaded() && Planner.usablePot(drifter) != null) {
			double bestScore = Double.NEGATIVE_INFINITY;
			// What it has the makings of first (on it or in its chests), the more filling first among those.
			Map<Item, Double> ready = new java.util.HashMap<>();
			Planner.potMeals(drifter.level()).forEach((meal, recipes) -> ready.put(meal,
				recipes.stream().mapToDouble(recipe -> readiness(drifter, recipe)).max().orElse(0.0)));
			List<Item> meals = new ArrayList<>(ready.keySet());
			// (A real meal: the mod's meal list, or something filling. Tomato sauce or cooked rice is for cooking with.)
			meals.removeIf(meal -> {
				ItemStack stack = new ItemStack(meal);
				FoodProperties food = stack.get(DataComponents.FOOD);
				return food == null || Food.isBad(stack) || !FarmersDelight.isMeal(stack) && food.nutrition() < MEAL_NUTRITION;
			});
			meals.sort(Comparator.comparingDouble((Item meal) -> -ready.get(meal)).thenComparingDouble(meal -> -value(meal)));
			for (Item meal : meals.subList(0, Math.min(MEALS_CONSIDERED, meals.size()))) {
				Planner.Plan plan = Planner.plan(drifter, world, meal, drifter.getInventory().count(stack -> stack.is(meal)) + 1);
				if (!plan.feasible() || plan.needsExploring() || plan.cost() > MEAL_PLAN_LIMIT) {
					continue;
				}

				double score = value(meal) - plan.cost() / 30.0;
				if (score > bestScore) {
					bestScore = score;
					best = meal;
				}
			}
		}

		CHOICES.put(drifter.getUUID(), new Choice(best, now, carried));
		return best;
	}

	/**
	 * It keeps this for the meal it would cook (an ingredient of it): raw beef and potatoes for a stew don't go in the furnace
	 * on their own. Only with a kitchen it can cook in.
	 */
	public static boolean saving(DrifterEntity drifter, KnownWorld world, ItemStack stack) {
		if (!FarmersDelight.loaded() || stack.isEmpty() || Planner.usablePot(drifter) == null) {
			return false;
		}

		Item meal = chooseMeal(drifter, world);
		if (meal == null) {
			return false;
		}

		for (FarmersDelight.PotRecipe recipe : Planner.potMeals(drifter.level()).getOrDefault(meal, List.of())) {
			for (var ingredient : recipe.inputs()) {
				if (ingredient.test(stack)) {
					return true;
				}
			}
		}

		return false;
	}

	/** How much of a recipe it has the makings of (0 to 1): its ingredients it carries or has in its chests at home. */
	private static double readiness(DrifterEntity drifter, FarmersDelight.PotRecipe recipe) {
		Home home = drifter.getDrifterBrain().home();
		int have = 0;
		for (var ingredient : recipe.inputs()) {
			boolean found = drifter.getInventory().count(ingredient::test) > 0;
			if (!found && home != null) {
				for (ItemStack option : ingredient.getItems()) {
					found |= home.stored(option.getItem()) > 0;
				}
			}

			have += found ? 1 : 0;
		}

		return have / (double)Math.max(1, recipe.inputs().size());
	}

	/** Forget the choice (a meal cooked, the kitchen changed). */
	public static void rethink(DrifterEntity drifter) {
		CHOICES.remove(drifter.getUUID());
	}

	// ---------------------------------------------------------------------------------------------
	// Talk
	// ---------------------------------------------------------------------------------------------

	/**
	 * What it has to say about its cooking (chat topics in drifter_talk/topics_kitchen.json): {@code has_kitchen} (a pot on a
	 * stove it knows), {@code cooked_lately} with {@code {a.meal}}, {@code has_meal} (a meal on it to share).
	 */
	public static void addTalkFacts(DrifterEntity me, Set<String> flags, Map<String, Object> words) {
		if (!FarmersDelight.loaded()) {
			return;
		}

		if (atHome(me, FarmersDelight.block("cooking_pot"))) {
			flags.add("has_kitchen");
		}

		Item cooked = me.getDrifterBrain().cookedLately(me.level().getGameTime(), COOKED_LATELY);
		if (cooked != null) {
			flags.add("cooked_lately");
			words.put("meal", Planner.name(cooked));
		}

		if (mealsCarried(me) > 0) {
			flags.add("has_meal");
			if (!words.containsKey("meal")) {
				for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
					ItemStack stack = me.getInventory().getItem(slot);
					if (FarmersDelight.isMeal(stack)) {
						words.put("meal", Planner.name(stack.getItem()));
						break;
					}
				}
			}
		}
	}

	/** The kitchen's pot, for a whim or a look (null if none). */
	@Nullable
	public static BlockPos pot(DrifterEntity drifter) {
		return Planner.usablePot(drifter);
	}
}
