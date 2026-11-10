package com.dinglebob.drifter.brain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.gear.GearKinds;
import com.dinglebob.drifter.gear.GearLore;
import com.dinglebob.drifter.gear.GearTaste;
import com.dinglebob.drifter.gear.GearValue;
import com.dinglebob.drifter.gear.RecipeIndex;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Modded weapons and armor as progression targets (weapons and armor project, spec goals 2, 3 and 7). The Strategist's
 * iron and diamond targets stay the vanilla pieces; a Drifter that knows (GearLore) a modded piece with a crafting-table
 * recipe, as good as the vanilla one, weighs it against that piece by value and taste (GearTaste.pick) and works toward the
 * one it likes. With nothing modded known, the targets are the very same objects as before.
 *
 * Candidates come from {@link RecipeIndex#craftableGear} (one list per reload), intersected with what it knows: nothing
 * here scans the item registry or the recipes per Drifter. A Drifter's picks are kept for {@link #TTL} ticks.
 * A smithing-only modded item (no crafting-table recipe) is not a candidate: the Planner crafts at a table. It can still
 * be worn if found.
 */
public final class GearPlans {
	/** A modded armor piece counts as an option for a slot at this share of the vanilla piece's value. */
	public static final double ARMOR_FLOOR = 0.9;
	/** How long a Drifter's picks are kept (ticks): the Strategist asks about once a second. */
	public static final long TTL = 100L;
	/** The slots in the order the Strategist's armor targets come: chestplate, leggings, helmet, boots. */
	public static final EquipmentSlot[] SLOTS = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.HEAD, EquipmentSlot.FEET};

	private GearPlans() {
	}

	// ---------------------------------------------------------------------------------------------
	// Vanilla pieces
	// ---------------------------------------------------------------------------------------------

	private static Item piece(EquipmentSlot slot, Weapons.Tier tier) {
		boolean iron = tier == Weapons.Tier.IRON;
		return switch (slot) {
			case CHEST -> iron ? Items.IRON_CHESTPLATE : Items.DIAMOND_CHESTPLATE;
			case LEGS -> iron ? Items.IRON_LEGGINGS : Items.DIAMOND_LEGGINGS;
			case HEAD -> iron ? Items.IRON_HELMET : Items.DIAMOND_HELMET;
			default -> iron ? Items.IRON_BOOTS : Items.DIAMOND_BOOTS;
		};
	}

	private static Item netherite(EquipmentSlot slot) {
		return switch (slot) {
			case CHEST -> Items.NETHERITE_CHESTPLATE;
			case LEGS -> Items.NETHERITE_LEGGINGS;
			case HEAD -> Items.NETHERITE_HELMET;
			default -> Items.NETHERITE_BOOTS;
		};
	}

	private static final double[] VANILLA_VALUE = new double[8];
	private static volatile boolean valuesDone;

	/** What the vanilla iron or diamond piece is worth in this slot (worked out once). */
	public static double vanillaValue(EquipmentSlot slot, Weapons.Tier tier) {
		if (!valuesDone) {
			for (EquipmentSlot s : SLOTS) {
				VANILLA_VALUE[index(s, Weapons.Tier.IRON)] = GearValue.armor(new ItemStack(piece(s, Weapons.Tier.IRON)), s);
				VANILLA_VALUE[index(s, Weapons.Tier.DIAMOND)] = GearValue.armor(new ItemStack(piece(s, Weapons.Tier.DIAMOND)), s);
			}

			valuesDone = true;
		}

		return VANILLA_VALUE[index(slot, tier)];
	}

	private static int index(EquipmentSlot slot, Weapons.Tier tier) {
		return (tier == Weapons.Tier.IRON ? 0 : 4) + switch (slot) {
			case CHEST -> 0;
			case LEGS -> 1;
			case HEAD -> 2;
			default -> 3;
		};
	}

	/**
	 * It has (carried, worn or stored) a piece for this slot at least as good as the tier's vanilla one. A vanilla piece
	 * counts by what it is (iron or better, as before: a gold or chain helmet has the same value as an iron one but is not
	 * iron age); a modded one by its value, so a known modded set counts for what it is worth.
	 */
	public static boolean hasArmorAtLeast(DrifterEntity drifter, EquipmentSlot slot, Weapons.Tier tier) {
		Item base = piece(slot, tier);
		Item diamond = piece(slot, Weapons.Tier.DIAMOND);
		Item netherite = netherite(slot);
		boolean iron = tier == Weapons.Tier.IRON;
		double need = vanillaValue(slot, tier);
		return Strategist.has(drifter, stack -> {
			if (stack.isEmpty()) {
				return false;
			}

			Item item = stack.getItem();
			if (item == base || item == diamond || item == netherite) {
				return true;
			}

			if (drifter.getEquipmentSlotForItem(stack) != slot || "minecraft".equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
				return false;
			}

			return GearValue.wornSlot(drifter, stack) == slot && GearValue.armor(stack, slot) >= need - 1.0E-9;
		});
	}

	// ---------------------------------------------------------------------------------------------
	// What is worth considering (per reload)
	// ---------------------------------------------------------------------------------------------

	/** The craftable modded melee weapons and armor by slot (from {@link RecipeIndex#craftableGear}), split once per reload. */
	private record Split(List<Item> source, int weaponsVersion, List<Item> weapons, Map<EquipmentSlot, List<Item>> armor) {
	}

	private static volatile Split split;

	private static Split split(DrifterEntity drifter) {
		List<Item> all = RecipeIndex.craftableGear(drifter.level());
		Split current = split;
		if (current != null && current.source() == all && current.weaponsVersion() == Weapons.version()) {
			return current;
		}

		List<Item> weapons = new ArrayList<>();
		Map<EquipmentSlot, List<Item>> armor = new EnumMap<>(EquipmentSlot.class);
		for (Item item : all) {
			ItemStack stack = new ItemStack(item);
			EquipmentSlot slot = GearValue.armorSlot(stack);
			if (slot != null) {
				armor.computeIfAbsent(slot, s -> new ArrayList<>()).add(item);
				continue;
			}

			GearKinds.Kind kind = GearKinds.of(stack);
			// (A weapon file's families and uniques are the weapon system's own business: the favorite family decides there.)
			if (kind != null && kind.use() == GearKinds.Use.MELEE && Weapons.isWeapon(stack, kind) && Weapons.family(stack) == null
				&& Weapons.unique(stack) == null) {
				weapons.add(item);
			}
		}

		armor.replaceAll((slot, list) -> List.copyOf(list));
		Split built = new Split(all, Weapons.version(), List.copyOf(weapons), Map.copyOf(armor));
		split = built;
		return built;
	}

	// ---------------------------------------------------------------------------------------------
	// Targets (kept per Drifter for a short while)
	// ---------------------------------------------------------------------------------------------

	private record Key(UUID id, boolean armor, Weapons.Tier tier) {
	}

	private record Kept(long until, Object value) {
	}

	private static final Map<Key, Kept> KEPT = new ConcurrentHashMap<>();

	/** Forgets this Drifter's kept picks (a debug command changed what it knows or likes). */
	public static void refresh(DrifterEntity drifter) {
		UUID id = drifter.getUUID();
		KEPT.keySet().removeIf(key -> key.id().equals(id));
		MAKING.keySet().removeIf(key -> key.id().equals(id));
	}

	// ---------------------------------------------------------------------------------------------
	// What it can make (a town's smith or fletcher: Drifter: Civilizations, through the API)
	// ---------------------------------------------------------------------------------------------

	/** At most this many modded pieces in what it can make (a veteran knows hundreds; a shop sells a few it likes). */
	public static final int MAKE_MODDED = 4;
	/** Of those, this many weapons and this many armor pieces first; a side's unused places go to the other (or the rest). */
	public static final int MAKE_WEAPONS = 2;
	public static final int MAKE_ARMOR = 2;
	/** What it knows is looked at again this often (ticks); its picks change only when that has. */
	public static final long MAKE_RECHECK = 1200L;

	private record MakeKey(UUID id, int maxTier) {
	}

	/** The gear lists it came from, the vanilla pieces, the modded candidates, and those ranked by its taste (best first). */
	private record Making(List<Item> source, List<Item> vanilla, java.util.Set<Item> candidates, List<Item> ranked, long checkedAt) {
	}

	private static final Map<MakeKey, Making> MAKING = new ConcurrentHashMap<>();

	/** A bow or crossbow (vanilla or any mod's). */
	public static boolean isLauncher(ItemStack stack) {
		GearKinds.Kind kind = GearKinds.of(stack);
		return kind != null && (kind.use() == GearKinds.Use.BOW || kind.use() == GearKinds.Use.CROSSBOW);
	}

	/**
	 * 1 (wood) to 5 (netherite): a weapon by {@link Weapons#tier} (gold as wood), armor by the vanilla piece its score
	 * matches, anything else (a bow, a crossbow, a shield, a trinket) as iron.
	 */
	public static int tierEquivalent(DrifterEntity drifter, ItemStack stack) {
		int armor = GearValue.armorTier(stack);
		if (armor > 0) {
			return armor;
		}

		if (isLauncher(stack)) {
			return 3;
		}

		Weapons.Tier tier = Weapons.tier(drifter, stack);
		if (tier == null) {
			return 3;
		}

		return switch (tier) {
			case WOOD, GOLD -> 1;
			case STONE -> 2;
			case IRON -> 3;
			case DIAMOND -> 4;
			case NETHERITE -> 5;
		};
	}

	/**
	 * The gear it knows how to make at a crafting table (the Planner's rule: {@link RecipeIndex#craftableGear}) at
	 * {@code maxTier} or lower ({@link #tierEquivalent}) that {@code which} takes: every such vanilla piece, then at most
	 * {@link #MAKE_MODDED} modded ones it knows (GearLore; {@link #MAKE_WEAPONS} weapons and {@link #MAKE_ARMOR} armor
	 * pieces, a side's unused places going to the other), those it likes best by use value and taste ({@link #useValue},
	 * against the iron piece of the same use). The
	 * picks hold until what it knows changes (no list flickering day to day). Kept per Drifter; cheap to ask often.
	 */
	public static List<Item> canMake(DrifterEntity drifter, int maxTier, java.util.function.Predicate<ItemStack> which) {
		Making making = making(drifter, maxTier);
		List<Item> out = new ArrayList<>();
		for (Item item : making.vanilla()) {
			if (which.test(new ItemStack(item))) {
				out.add(item);
			}
		}

		// The modded places: MAKE_WEAPONS weapons and MAKE_ARMOR armor pieces first (each its best by rank), then any places
		// left (a side with fewer it knows, or neither: a fletcher's bows) to the best of the rest, in rank order.
		List<Item> matching = new ArrayList<>();
		for (Item item : making.ranked()) {
			if (which.test(new ItemStack(item))) {
				matching.add(item);
			}
		}

		List<Item> picked = new ArrayList<>(MAKE_MODDED);
		int weapons = 0;
		int armor = 0;
		for (Item item : matching) {
			ItemStack stack = new ItemStack(item);
			if (GearValue.armorTier(stack) > 0) {
				if (armor < MAKE_ARMOR) {
					picked.add(item);
					armor++;
				}
			} else if (Weapons.isWeapon(stack) && weapons < MAKE_WEAPONS) {
				picked.add(item);
				weapons++;
			}
		}

		for (Item item : matching) {
			if (picked.size() >= MAKE_MODDED) {
				break;
			}

			if (!picked.contains(item)) {
				picked.add(item);
			}
		}

		// (In rank order, as before.)
		for (Item item : matching) {
			if (picked.contains(item)) {
				out.add(item);
			}
		}

		return out;
	}

	/**
	 * What this is worth to it for its use: armor worn in its slot, a trinket in its best slot, a bow, crossbow or trident by
	 * its ranged score, any other weapon kind by its damage per second in its hands; 0 for anything else (a shield).
	 */
	public static double useValue(DrifterEntity drifter, ItemStack stack) {
		if (stack.isEmpty()) {
			return 0.0;
		}

		EquipmentSlot slot = GearValue.armorSlot(stack);
		if (slot != null) {
			return GearValue.armor(stack, slot);
		}

		if (com.dinglebob.drifter.compat.TrinketsCompat.isTrinket(stack)) {
			return GearValue.trinket(drifter, stack);
		}

		GearKinds.Kind kind = GearKinds.of(stack);
		if (kind == null) {
			return 0.0;
		}

		return switch (kind.use()) {
			case BOW, CROSSBOW, TRIDENT -> GearValue.ranged(stack);
			default -> GearValue.melee(drifter, stack);
		};
	}

	/** The iron (or plain vanilla) piece's {@link #useValue} for the same use, to compare across uses; 1 where there is none. */
	private static double reference(ItemStack stack, double ironSword) {
		EquipmentSlot slot = GearValue.armorSlot(stack);
		if (slot != null) {
			return Math.max(1.0E-6, vanillaValue(slot, Weapons.Tier.IRON));
		}

		GearKinds.Kind kind = GearKinds.of(stack);
		if (kind == null || com.dinglebob.drifter.compat.TrinketsCompat.isTrinket(stack)) {
			return 1.0;
		}

		return switch (kind.use()) {
			case BOW -> GearValue.BASE_BOW;
			case CROSSBOW -> GearValue.BASE_CROSSBOW;
			case TRIDENT -> GearValue.BASE_TRIDENT;
			default -> ironSword;
		};
	}

	private static Making making(DrifterEntity drifter, int maxTier) {
		long now = drifter.level().getGameTime();
		MakeKey key = new MakeKey(drifter.getUUID(), maxTier);
		List<Item> source = RecipeIndex.craftableGear(drifter.level());
		Making kept = MAKING.get(key);
		if (kept != null && kept.source() == source && now - kept.checkedAt() >= 0L && now - kept.checkedAt() < MAKE_RECHECK) {
			return kept;
		}

		List<Item> vanilla;
		if (kept != null && kept.source() == source) {
			vanilla = kept.vanilla();
		} else {
			List<Item> found = new ArrayList<>();
			for (Item item : RecipeIndex.craftableVanillaGear(drifter.level())) {
				if (tierEquivalent(drifter, new ItemStack(item)) <= maxTier) {
					found.add(item);
				}
			}

			vanilla = List.copyOf(found);
		}

		java.util.Set<Item> candidates = new java.util.LinkedHashSet<>();
		for (Item item : source) {
			if (drifter.knowsItem(item) && tierEquivalent(drifter, new ItemStack(item)) <= maxTier) {
				candidates.add(item);
			}
		}

		List<Item> ranked;
		if (kept != null && kept.source() == source && kept.candidates().equals(candidates)) {
			ranked = kept.ranked();
		} else {
			// By what each is worth for its use, with its taste in it, against the iron piece of the same use (so armor's bigger
			// numbers don't crowd out weapons); not by trade points, which count what it's made of.
			Map<Item, Double> liking = new java.util.HashMap<>();
			double ironSword = Math.max(1.0E-6, GearValue.melee(drifter, new ItemStack(Items.IRON_SWORD)));
			for (Item item : candidates) {
				ItemStack stack = new ItemStack(item);
				liking.put(item, GearTaste.liking(drifter, stack, useValue(drifter, stack)) / reference(stack, ironSword));
			}

			List<Item> sorted = new ArrayList<>(candidates);
			sorted.sort(java.util.Comparator.<Item>comparingDouble(liking::get).reversed()
				.thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
			ranked = List.copyOf(sorted);
		}

		if (MAKING.size() > 2048) {
			MAKING.clear();
		}

		Making made = new Making(source, vanilla, java.util.Set.copyOf(candidates), ranked, now);
		MAKING.put(key, made);
		return made;
	}

	@Nullable
	@SuppressWarnings("unchecked")
	private static <T> T kept(DrifterEntity drifter, Key key, java.util.function.Supplier<T> compute) {
		long now = drifter.level().getGameTime();
		Kept kept = KEPT.get(key);
		if (kept != null && kept.until() > now && kept.until() - now <= TTL) {
			return (T)kept.value();
		}

		T value = compute.get();
		if (KEPT.size() > 2048) {
			KEPT.clear();
		}

		KEPT.put(key, new Kept(now + TTL, value));
		return value;
	}

	/** "a Warrior Chestplate", "an Iron Longsword". */
	private static String label(Item item) {
		String name = GearLore.nameOf(item);
		return ("AEIOUaeiou".indexOf(name.isEmpty() ? 'x' : name.charAt(0)) >= 0 ? "an " : "a ") + name;
	}

	/**
	 * What it works toward as its melee weapon at this tier: the weapon file's pick ({@link Weapons#target}) and every
	 * craftable modded weapon it knows that counts as this tier, chosen by taste among those close in value. Null: it skips
	 * the tier. Satisfied by any weapon of the tier or better, as before.
	 */
	@Nullable
	public static Strategist.Target weaponTarget(DrifterEntity drifter, Weapons.Tier tier) {
		Strategist.Target base = Weapons.target(drifter, tier);
		List<Item> weapons = split(drifter).weapons();
		if (weapons.isEmpty()) {
			return base;
		}

		return kept(drifter, new Key(drifter.getUUID(), false, tier), () -> computeWeapon(drifter, tier, base));
	}

	@Nullable
	private static Strategist.Target computeWeapon(DrifterEntity drifter, Weapons.Tier tier, @Nullable Strategist.Target base) {
		List<Item> options = new ArrayList<>();
		Item baseItem = base == null ? null : base.items().get(0);
		if (baseItem != null) {
			options.add(baseItem);
		}

		for (Item item : split(drifter).weapons()) {
			if (item != baseItem && drifter.knowsItem(item) && Weapons.tier(drifter, new ItemStack(item)) == tier) {
				options.add(item);
			}
		}

		if (options.size() == (baseItem == null ? 0 : 1)) {
			return base;
		}

		Item chosen = GearTaste.pick(drifter, options, item -> GearValue.melee(drifter, new ItemStack(item)), ItemStack::new);
		if (chosen == null || chosen == baseItem) {
			return base;
		}

		return new Strategist.Target(label(chosen), List.of(chosen), 1, d -> Weapons.hasTier(d, tier));
	}

	/**
	 * Its armor targets at this tier (IRON or DIAMOND), chestplate, leggings, helmet, boots: per slot the vanilla piece or
	 * a craftable modded piece it knows with at least {@link #ARMOR_FLOOR} of its value, chosen by taste. Where it picks the
	 * vanilla piece, the Strategist's own target object is returned.
	 */
	public static List<Strategist.Target> armorTargets(DrifterEntity drifter, Weapons.Tier tier) {
		List<Strategist.Target> base = tier == Weapons.Tier.IRON ? Strategist.IRON_TARGETS.subList(3, 7) : Strategist.DIAMOND_TARGETS.subList(2, 6);
		Split split = split(drifter);
		if (split.armor().isEmpty()) {
			return base;
		}

		return kept(drifter, new Key(drifter.getUUID(), true, tier), () -> computeArmor(drifter, tier, base, split));
	}

	private static List<Strategist.Target> computeArmor(DrifterEntity drifter, Weapons.Tier tier, List<Strategist.Target> base, Split split) {
		List<Strategist.Target> out = new ArrayList<>(4);
		for (int i = 0; i < SLOTS.length; i++) {
			EquipmentSlot slot = SLOTS[i];
			Strategist.Target vanilla = base.get(i);
			double need = vanillaValue(slot, tier);
			List<Item> options = new ArrayList<>();
			Item vanillaItem = vanilla.items().get(0);
			options.add(vanillaItem);
			for (Item item : split.armor().getOrDefault(slot, List.of())) {
				if (drifter.knowsItem(item) && GearValue.armor(new ItemStack(item), slot) >= ARMOR_FLOOR * need) {
					options.add(item);
				}
			}

			Item chosen = options.size() == 1 ? vanillaItem
				: GearTaste.pick(drifter, options, item -> GearValue.armor(new ItemStack(item), slot), ItemStack::new);
			if (chosen == null || chosen == vanillaItem) {
				out.add(vanilla);
			} else {
				out.add(new Strategist.Target(label(chosen), List.of(chosen), 1, vanilla.satisfied()));
			}
		}

		return List.copyOf(out);
	}
}
