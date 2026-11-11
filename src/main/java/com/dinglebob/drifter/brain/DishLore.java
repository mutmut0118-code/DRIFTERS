package com.dinglebob.drifter.brain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.compat.Dishes;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.gear.GearLore;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

/**
 * Which of the Farmer's Delight addons' dishes a Drifter knows how to make (the Delight addons, task 3): a dish from an
 * addon (neither vanilla's nor Farmer's Delight's own, {@link Dishes#isAddonDish}) it learns once it has held one of its
 * ingredients, as the recipe book unlocks a recipe; vanilla food and Farmer's Delight's meals it always knows. Veterans
 * (the same rule as {@link GearLore#innate}) and the "know every recipe" setting know them all from the start. One per
 * Drifter, saved and versioned.
 *
 * <p>Cheap at a couple of hundred loaded Drifters: which dishes each ingredient teaches is worked out once per dish index
 * (a recipe reload), and a Drifter's pockets are looked at only every {@link #CHECK_INTERVAL} ticks.
 */
public final class DishLore {
	public static final int SAVE_VERSION = 1;
	/** The check runs this often per Drifter (ticks); offset from {@code Lore} (0) and {@code GearLore} (100). */
	public static final int CHECK_INTERVAL = 200;
	public static final int CHECK_OFFSET = 50;
	/** At most this many dish names in one thought (bread teaches a dozen sandwiches at once). */
	private static final int NAMES_IN_THOUGHT = 4;

	/** The addon dishes it has learned (saved). */
	private final Set<Item> learned = new LinkedHashSet<>();

	/** Ingredient item to the addon dishes it goes into, for the dish index it was worked out from (shared by every Drifter). */
	private static volatile Map<Item, List<Dishes.Recipe>> teachesFor;
	private static Map<Item, Set<Item>> teaches = Map.of();

	/** It may make this: anything not an addon dish, or an addon dish it knows. The planner's and meal choice's gate. */
	public static boolean allowed(DrifterEntity d, Item output) {
		return d.getDishLore().knows(d, output);
	}

	/** True for everything but an addon dish it hasn't learned (see the class). */
	public boolean knows(DrifterEntity d, Item dish) {
		// (isDish first: it keeps the index current, which isAddonDish only reads.)
		if (!Dishes.isDish(d.level(), dish) || !Dishes.isAddonDish(dish)) {
			return true;
		}

		return this.learned.contains(dish) || innate(d);
	}

	/** It knows every addon dish from the start: the setting, or a veteran (GearLore's rule: never a newbie). */
	public static boolean innate(DrifterEntity d) {
		if (DrifterConfig.knowEveryRecipe()) {
			return true;
		}

		Archetype archetype = d.getPersonality().archetype();
		if (archetype == Archetype.NEWBIE) {
			return false;
		}

		return d.getPersonality().get(Trait.KNOWLEDGE) >= GearLore.VETERAN_KNOWLEDGE
			|| archetype == Archetype.PVP_SWEAT || archetype == Archetype.SPEEDRUNNER;
	}

	/** The addon dishes it has learned (debug). */
	public Set<Item> learned() {
		return Collections.unmodifiableSet(this.learned);
	}

	/** It has learned how to make this addon dish ({@code where}: how, for its thought); false if it knew it already. */
	public boolean learn(DrifterEntity d, Item dish, String where) {
		if (this.knows(d, dish)) {
			return false;
		}

		this.learned.add(dish);
		d.getMemory().thoughts().add(d.level().getGameTime(), "learned how to make " + Planner.name(dish)
			+ (where.isEmpty() ? "" : " (" + where + ")"));
		return true;
	}

	/** What it carries teaches it (every {@link #CHECK_INTERVAL} ticks). */
	public void check(DrifterEntity d) {
		this.learnFrom(d, d.getInventory(), "has ");
	}

	/**
	 * Every addon dish one of whose ingredients is in this container it now knows; one thought per ingredient that taught it
	 * something. {@code how}: the thought's words before the ingredient ("has ", "in the pantry: "). Returns how many it learned.
	 */
	public int learnFrom(DrifterEntity d, Container container, String how) {
		Map<Item, Set<Item>> teaches = teaches(d.level());
		if (teaches.isEmpty() || innate(d)) {
			return 0;
		}

		int total = 0;
		Set<Item> looked = new java.util.HashSet<>();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty() || !looked.add(stack.getItem())) {
				continue;
			}

			Set<Item> dishes = teaches.get(stack.getItem());
			if (dishes == null) {
				continue;
			}

			List<Item> fresh = new ArrayList<>();
			for (Item dish : dishes) {
				if (!this.learned.contains(dish)) {
					fresh.add(dish);
				}
			}

			if (fresh.isEmpty()) {
				continue;
			}

			this.learned.addAll(fresh);
			total += fresh.size();
			String from = how + Planner.name(stack.getItem());
			List<String> names = fresh.stream().limit(NAMES_IN_THOUGHT).map(Planner::name).toList();
			String line = fresh.size() == 1 ? "learned how to make " + names.get(0) + " (" + from + ")"
				: "learned how to make " + fresh.size() + " dishes (" + from + "): " + String.join(", ", names)
					+ (fresh.size() > NAMES_IN_THOUGHT ? "..." : "");
			d.getMemory().thoughts().add(d.level().getGameTime(), line);
		}

		return total;
	}

	/** Ingredient item to the addon dishes it goes into (any station's recipe that lists its inputs), once per dish index. */
	private static Map<Item, Set<Item>> teaches(Level level) {
		Map<Item, List<Dishes.Recipe>> makeable = Dishes.makeable(level);
		if (makeable != teachesFor) {
			synchronized (DishLore.class) {
				if (makeable != teachesFor) {
					Map<Item, Set<Item>> found = new HashMap<>();
					makeable.forEach((dish, recipes) -> {
						if (!Dishes.isAddonDish(dish)) {
							return;
						}

						for (Dishes.Recipe recipe : recipes) {
							for (Ingredient ingredient : recipe.inputs()) {
								for (ItemStack input : ingredient.getItems()) {
									found.computeIfAbsent(input.getItem(), key -> new LinkedHashSet<>()).add(dish);
								}
							}
						}
					});
					found.replaceAll((item, dishes) -> Collections.unmodifiableSet(dishes));
					teaches = Collections.unmodifiableMap(found);
					teachesFor = makeable;
				}
			}
		}

		return teaches;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", SAVE_VERSION);
		ListTag known = new ListTag();
		for (Item dish : this.learned) {
			known.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(dish).toString()));
		}

		tag.put("Known", known);
		return tag;
	}

	/** Dishes whose mod is gone are skipped quietly. No tag (an older save): it knows none yet and learns them again. */
	public void load(CompoundTag tag) {
		this.learned.clear();
		ListTag known = tag.getList("Known", Tag.TAG_STRING);
		for (int i = 0; i < known.size(); i++) {
			ResourceLocation id = ResourceLocation.tryParse(known.getString(i));
			if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
				this.learned.add(BuiltInRegistries.ITEM.get(id));
			}
		}

		if (tag.getInt("Version") > SAVE_VERSION) {
			Drifter.LOGGER.debug("Drifter dish knowledge saved by a newer version; reading what is understood");
		}
	}
}
