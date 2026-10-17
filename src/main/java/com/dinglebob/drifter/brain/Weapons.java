package com.dinglebob.drifter.brain;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Trait;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Weapon families (DESIGN.md section 21, "Weapon mods like Simply Swords"; user's request, 2026-10-17: "a data driven
 * system for acquiring/crafting modded weapons, starting with simply swords"). Loaded from
 * {@code data/<namespace>/drifter_weapons/*.json}, so other weapon mods are a data file, not code. A family is one kind of
 * weapon (sword, longsword, claymore...) with an item per material tier; each Drifter has a favorite family, picked from
 * its personality (each family says which traits like it), and crafts that at the tiers the family lists. Some families
 * take a fixed share of Drifters (vanilla swords: about one in four stick with them). Unique weapons (loot only) are
 * listed with the tier they count as and, later, when to use their ability. Crafting itself is the game's own recipes.
 */
public final class Weapons {
	public static final String DIRECTORY = "drifter_weapons";
	/** Every melee weapon, vanilla and modded (our tag: swords, and the common sword and melee-weapon tags). */
	public static final TagKey<Item> MELEE = TagKey.create(Registries.ITEM, Drifter.id("melee_weapons"));

	/** Material tiers, weakest first. */
	public enum Tier {
		WOOD, GOLD, STONE, IRON, DIAMOND, NETHERITE;

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		@Nullable
		public static Tier byId(String id) {
			for (Tier tier : values()) {
				if (tier.id().equals(id)) {
					return tier;
				}
			}

			return null;
		}
	}

	/** One kind of weapon. */
	public record Family(String id, String name, boolean twoHanded, Map<Tier, Item> tiers, List<Tier> craftAt, double weight,
		@Nullable Double share, Map<Trait, Double> likes) {
		@Nullable
		public Item at(Tier tier) {
			return this.tiers.get(tier);
		}

		/** "a diamond longsword", "an iron claymore". */
		public String label(Tier tier) {
			String words = tier.id() + " " + this.name;
			return ("aeiou".indexOf(words.charAt(0)) >= 0 ? "an " : "a ") + words;
		}
	}

	/** A loot-only weapon: the tier it counts as (for "has a diamond weapon"), and its ability entry (TODO, next step). */
	public record Unique(Item item, Tier tier, @Nullable JsonObject ability) {
	}

	private static volatile List<Family> families = List.of();
	private static volatile Map<Item, Family> byItem = Map.of();
	private static volatile Map<Item, Unique> uniques = Map.of();
	private static volatile Map<Item, Tier> tierOf = Map.of();
	/** Bumped on every reload, so cached favorites are picked again. */
	private static volatile int version;

	private Weapons() {
	}

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return Drifter.id("weapons");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				load(manager);
			}
		});
	}

	public static List<Family> families() {
		return families;
	}

	public static int version() {
		return version;
	}

	@Nullable
	public static Family family(ItemStack stack) {
		return byItem.get(stack.getItem());
	}

	@Nullable
	public static Unique unique(ItemStack stack) {
		return uniques.get(stack.getItem());
	}

	/** A melee weapon (any sword, or anything a weapon file lists). */
	public static boolean isWeapon(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(MELEE) || byItem.containsKey(stack.getItem()) || uniques.containsKey(stack.getItem()));
	}

	/**
	 * The tier a weapon counts as: from the weapon files; for a melee weapon they don't list, the best vanilla sword its
	 * damage per second matches (so any modded sword counts for what it's worth). Null: not a weapon.
	 */
	@Nullable
	public static Tier tier(DrifterEntity drifter, ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}

		Tier listed = tierOf.get(stack.getItem());
		if (listed != null || !stack.is(MELEE)) {
			return listed;
		}

		double dps = com.dinglebob.drifter.hands.Combat.weaponStats(drifter, stack).perSecond();
		Tier best = Tier.WOOD;
		for (Map.Entry<Tier, Item> vanilla : VANILLA_SWORDS.entrySet()) {
			if (dps + 1.0E-3 >= com.dinglebob.drifter.hands.Combat.weaponStats(drifter, new ItemStack(vanilla.getValue())).perSecond()
				&& vanilla.getKey().ordinal() > best.ordinal()) {
				best = vanilla.getKey();
			}
		}

		return best;
	}

	private static final Map<Tier, Item> VANILLA_SWORDS = new EnumMap<>(Map.of(Tier.WOOD, Items.WOODEN_SWORD, Tier.GOLD, Items.GOLDEN_SWORD,
		Tier.STONE, Items.STONE_SWORD, Tier.IRON, Items.IRON_SWORD, Tier.DIAMOND, Items.DIAMOND_SWORD, Tier.NETHERITE, Items.NETHERITE_SWORD));

	/** It has (carried, worn or stored) a weapon of this tier or better. Gold counts only as gold. */
	public static boolean hasTier(DrifterEntity drifter, Tier tier) {
		return Strategist.has(drifter, stack -> {
			Tier its = tier(drifter, stack);
			return its != null && its.ordinal() >= tier.ordinal() && (its != Tier.GOLD || tier == Tier.GOLD);
		});
	}

	// ---------------------------------------------------------------------------------------------
	// Favorites
	// ---------------------------------------------------------------------------------------------

	/**
	 * Its favorite family: the same for a Drifter every time (seeded by its id), leaning to the families its personality
	 * likes. Fixed-share families (vanilla swords) take their share first. Only families whose items all exist (their mod
	 * is installed) are in the running.
	 */
	public static Family favorite(DrifterEntity drifter) {
		Family cached = drifter.favoriteWeapon(version);
		if (cached != null) {
			return cached;
		}

		Family picked = pick(drifter);
		drifter.setFavoriteWeapon(picked, version);
		return picked;
	}

	private static Family pick(DrifterEntity drifter) {
		List<Family> all = families;
		if (all.isEmpty()) {
			return FALLBACK;
		}

		RandomSource random = RandomSource.create(drifter.getUUID().getLeastSignificantBits() ^ 0x5EA7_B1ADEL);
		List<Family> shared = all.stream().filter(f -> f.share() != null).toList();
		List<Family> rest = all.stream().filter(f -> f.share() == null).toList();
		double roll = random.nextDouble();
		if (rest.isEmpty()) {
			return shared.get(random.nextInt(shared.size()));
		}

		double sharedTotal = 0.0;
		for (Family family : shared) {
			sharedTotal += family.share();
			if (roll < sharedTotal) {
				return family;
			}
		}

		double total = 0.0;
		double[] weights = new double[rest.size()];
		for (int i = 0; i < rest.size(); i++) {
			weights[i] = liking(drifter, rest.get(i));
			total += weights[i];
		}

		double at = random.nextDouble() * total;
		for (int i = 0; i < rest.size(); i++) {
			at -= weights[i];
			if (at <= 0.0) {
				return rest.get(i);
			}
		}

		return rest.get(rest.size() - 1);
	}

	/** How much it likes this family: its weight, times each trait's pull (a liked trait at 1 doubles it, at 0 all but rules it out). */
	public static double liking(DrifterEntity drifter, Family family) {
		double weight = family.weight();
		for (Map.Entry<Trait, Double> like : family.likes().entrySet()) {
			double trait = drifter.getPersonality().getF(like.getKey());
			weight *= Math.max(0.05, 1.0 + 2.0 * like.getValue() * (trait - 0.5));
		}

		// With Better Combat, a two-handed weapon means no shield: a careful Drifter doesn't want one.
		if (family.twoHanded() && com.dinglebob.drifter.compat.BetterCombatCompat.active()) {
			weight *= Math.max(0.1, 1.4 - drifter.getPersonality().getF(Trait.CAUTION));
		}

		return weight;
	}

	/**
	 * The weapon it makes at this tier: its favorite family's, if the family is made at this tier and the game has a
	 * recipe for it (a mod's config can turn recipes off); else the vanilla sword. Null if neither (no stone claymore).
	 */
	@Nullable
	public static Strategist.Target target(DrifterEntity drifter, Tier tier) {
		Family family = favorite(drifter);
		Item item = family.craftAt().contains(tier) ? family.at(tier) : null;
		if (item == null || !craftable(drifter.level(), item)) {
			family = FALLBACK;
			item = FALLBACK.craftAt().contains(tier) ? FALLBACK.at(tier) : null;
			if (item == null) {
				return null;
			}
		}

		return new Strategist.Target(family.label(tier), List.of(item), 1, d -> hasTier(d, tier));
	}

	/** Vanilla swords, if no weapon file loaded. */
	private static final Family FALLBACK = new Family("minecraft:sword", "sword", false, VANILLA_SWORDS, List.of(Tier.STONE, Tier.DIAMOND), 1.0, 0.25,
		Map.of());

	private static final Map<Item, Boolean> CRAFTABLE = new java.util.concurrent.ConcurrentHashMap<>();

	/** The game has a recipe that makes it (cached until the next reload). */
	public static boolean craftable(Level level, Item item) {
		return CRAFTABLE.computeIfAbsent(item, it -> level.getRecipeManager().getRecipes().stream()
			.anyMatch(holder -> holder.value().getResultItem(level.registryAccess()).is(it)));
	}

	private static final Map<Item, Integer> IRON_COST = new java.util.concurrent.ConcurrentHashMap<>();

	/** Iron ingots its recipe takes (nuggets rounded up to ingots); 0 if none or no recipe (cached until the next reload). */
	public static int ironCost(Level level, Item item) {
		return IRON_COST.computeIfAbsent(item, it -> countIron(level, it));
	}

	private static int countIron(Level level, Item item) {
		for (var holder : level.getRecipeManager().getRecipes()) {
			if (!holder.value().getResultItem(level.registryAccess()).is(item)) {
				continue;
			}

			int ingots = 0;
			int nuggets = 0;
			for (var ingredient : holder.value().getIngredients()) {
				if (ingredient.test(new ItemStack(Items.IRON_INGOT))) {
					ingots++;
				} else if (ingredient.test(new ItemStack(Items.IRON_NUGGET))) {
					nuggets++;
				}
			}

			return ingots + (nuggets + 8) / 9;
		}

		return 0;
	}

	// ---------------------------------------------------------------------------------------------
	// Loading
	// ---------------------------------------------------------------------------------------------

	private static void load(ResourceManager manager) {
		Map<String, Family> loaded = new LinkedHashMap<>();
		Map<Item, Unique> loadedUniques = new HashMap<>();
		for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = file.getValue().openAsReader()) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				if (root.has("requires") && !FabricLoader.getInstance().isModLoaded(root.get("requires").getAsString())) {
					continue;
				}

				Tier uniqueTier = root.has("unique_tier") ? Tier.byId(root.get("unique_tier").getAsString()) : Tier.DIAMOND;
				if (root.has("families")) {
					for (JsonElement element : root.getAsJsonArray("families")) {
						Family family = family(element.getAsJsonObject());
						if (family != null) {
							// A later file with the same id replaces it (datapacks).
							loaded.put(family.id(), family);
						}
					}
				}

				if (root.has("uniques")) {
					for (JsonElement element : root.getAsJsonArray("uniques")) {
						JsonObject entry = element.getAsJsonObject();
						Item item = item(entry.get("item").getAsString());
						if (item == null) {
							continue;
						}

						Tier tier = entry.has("tier") ? Tier.byId(entry.get("tier").getAsString()) : uniqueTier;
						loadedUniques.put(item, new Unique(item, tier == null ? Tier.DIAMOND : tier,
							entry.has("ability") ? entry.getAsJsonObject("ability") : null));
					}
				}
			} catch (Exception e) {
				Drifter.LOGGER.error("Couldn't read Drifter weapons file {}", file.getKey(), e);
			}
		}

		Map<Item, Family> items = new HashMap<>();
		Map<Item, Tier> tiers = new HashMap<>();
		for (Family family : loaded.values()) {
			for (Map.Entry<Tier, Item> tier : family.tiers().entrySet()) {
				items.put(tier.getValue(), family);
				tiers.put(tier.getValue(), tier.getKey());
			}
		}

		for (Unique unique : loadedUniques.values()) {
			tiers.putIfAbsent(unique.item(), unique.tier());
		}

		families = List.copyOf(loaded.values());
		byItem = Map.copyOf(items);
		uniques = Map.copyOf(loadedUniques);
		tierOf = Map.copyOf(tiers);
		CRAFTABLE.clear();
		IRON_COST.clear();
		version++;
		Drifter.LOGGER.info("Loaded {} Drifter weapon families and {} unique weapons", families.size(), uniques.size());
	}

	@Nullable
	private static Family family(JsonObject entry) {
		String id = entry.get("id").getAsString();
		Map<Tier, Item> tiers = new EnumMap<>(Tier.class);
		JsonObject tierObject = entry.getAsJsonObject("tiers");
		for (Map.Entry<String, JsonElement> tier : tierObject.entrySet()) {
			Tier key = Tier.byId(tier.getKey());
			Item item = item(tier.getValue().getAsString());
			if (key != null && item != null) {
				tiers.put(key, item);
			}
		}

		if (tiers.isEmpty()) {
			return null;
		}

		List<Tier> craftAt = new ArrayList<>();
		if (entry.has("craft_at")) {
			for (JsonElement tier : entry.getAsJsonArray("craft_at")) {
				Tier key = Tier.byId(tier.getAsString());
				if (key != null && tiers.containsKey(key)) {
					craftAt.add(key);
				}
			}
		}

		Map<Trait, Double> likes = new EnumMap<>(Trait.class);
		if (entry.has("likes")) {
			for (Map.Entry<String, JsonElement> like : entry.getAsJsonObject("likes").entrySet()) {
				for (Trait trait : Trait.values()) {
					if (trait.id().equals(like.getKey())) {
						likes.put(trait, like.getValue().getAsDouble());
					}
				}
			}
		}

		return new Family(id, entry.has("name") ? entry.get("name").getAsString() : id, entry.has("two_handed") && entry.get("two_handed").getAsBoolean(),
			Map.copyOf(tiers), List.copyOf(craftAt), entry.has("weight") ? entry.get("weight").getAsDouble() : 1.0,
			entry.has("share") ? entry.get("share").getAsDouble() : null, Map.copyOf(likes));
	}

	/** The item with this id, or null if it isn't in the game (its mod isn't installed). */
	@Nullable
	private static Item item(String id) {
		ResourceLocation location = ResourceLocation.tryParse(id);
		if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) {
			return null;
		}

		return BuiltInRegistries.ITEM.get(location);
	}
}
