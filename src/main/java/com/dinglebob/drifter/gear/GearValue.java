package com.dinglebob.drifter.gear;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.compat.TrinketsCompat;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.Combat;
import com.dinglebob.drifter.social.ItemValues;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

/**
 * What a piece of gear is worth, read from the item itself (its attribute modifiers, enchantments, rarity and recipe), so
 * any mod's weapon or armor is valued with no list. Entity-free where it can be: {@link #points} feeds
 * {@code ItemValues}, which has no Drifter to hand.
 */
public final class GearValue {
	/** A player's bare-hand attack damage and speed (the base the item's modifiers add to). */
	public static final double BASE_ATTACK_DAMAGE = 1.0;
	public static final double BASE_ATTACK_SPEED = 4.0;
	/** Armor points count this much each in an armor score, toughness and knockback resistance as stated. */
	public static final double ARMOR_WEIGHT = 10.0;
	public static final double KNOCKBACK_RESISTANCE_WEIGHT = 10.0;
	/** Any other positive plain bonus on armor (health, speed, luck...), per point. */
	public static final double OTHER_MOD_WEIGHT = 2.0;
	public static final double BASE_BOW = 10.0;
	public static final double BASE_CROSSBOW = 12.0;
	public static final double BASE_TRIDENT = 9.0;
	/** Per level of each ranged enchantment. */
	public static final double ENCHANT_STEP = 3.0;
	/** Per rarity step above common (ranged score), and trade points per step for any gear. */
	public static final double RARITY_RANGED_STEP = 5.0;
	public static final double OWN_RELEASE_BONUS = 5.0;
	public static final double POINTS_PER_WEAPON_TIER = 3.0;
	public static final double POINTS_PER_ARMOR_TIER = 4.0;
	public static final double POINTS_PER_RARITY = 5.0;
	/** A modded shield's trade points (vanilla items keep the points ItemValues always gave). */
	public static final double SHIELD_POINTS = 6.0;
	/** A trinket's trade points before rarity (Trinkets, optional). */
	public static final double TRINKET_POINTS = 6.0;
	/** Any trinket that fits a free slot is worth putting on (wearing one costs nothing); per believed power, per rarity step. */
	public static final double TRINKET_BASE = 1.0;
	public static final double TRINKET_POWER_STEP = 5.0;
	public static final double TRINKET_RARITY_STEP = 5.0;

	private static final Map<Item, Double> COSTS = new HashMap<>();

	private GearValue() {
	}

	static void clear() {
		synchronized (COSTS) {
			COSTS.clear();
		}

		TIERS = null;
		TrinketsCompat.clear();
	}

	// ---------------------------------------------------------------------------------------------
	// Melee
	// ---------------------------------------------------------------------------------------------

	/** Damage per second with this in hand. */
	public static double melee(DrifterEntity drifter, ItemStack stack) {
		return Combat.weaponStats(drifter, stack).perSecond();
	}

	/** The same without a Drifter: a player's base values plus the item's plain main-hand additions. */
	public static double dps(ItemStack stack) {
		double[] damage = {BASE_ATTACK_DAMAGE};
		double[] speed = {BASE_ATTACK_SPEED};
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (modifier.operation() != AttributeModifier.Operation.ADD_VALUE) {
				return;
			}

			if (attribute.is(Attributes.ATTACK_DAMAGE)) {
				damage[0] += modifier.amount();
			} else if (attribute.is(Attributes.ATTACK_SPEED)) {
				speed[0] += modifier.amount();
			}
		});
		return damage[0] * Math.max(0.1, speed[0]);
	}

	// ---------------------------------------------------------------------------------------------
	// Armor
	// ---------------------------------------------------------------------------------------------

	/**
	 * What this is worth worn in {@code slot}: armor and toughness and knockback resistance, plus other bonuses. Plain
	 * additions only (a multiplier is ignored), never below 0. Vanilla armor: {@code defense * 10 + toughness}.
	 */
	public static double armor(ItemStack stack, EquipmentSlot slot) {
		if (stack.isEmpty() || slot == null) {
			return 0.0;
		}

		double[] sum = {0.0};
		stack.forEachModifier(slot, (attribute, modifier) -> {
			if (modifier.operation() != AttributeModifier.Operation.ADD_VALUE || Double.isNaN(modifier.amount())) {
				return;
			}

			double amount = modifier.amount();
			if (attribute.is(Attributes.ARMOR)) {
				sum[0] += amount * ARMOR_WEIGHT;
			} else if (attribute.is(Attributes.ARMOR_TOUGHNESS)) {
				sum[0] += amount;
			} else if (attribute.is(Attributes.KNOCKBACK_RESISTANCE)) {
				sum[0] += amount * KNOCKBACK_RESISTANCE_WEIGHT;
			} else if (amount > 0.0) {
				sum[0] += amount * OTHER_MOD_WEIGHT;
			}
		});
		return Math.max(0.0, sum[0]);
	}

	/** The body slot an item is made to be worn in (head, chest, legs or feet), without any entity; null if none. */
	@Nullable
	public static EquipmentSlot slotOf(ItemStack stack) {
		Equipable equipable = Equipable.get(stack);
		if (equipable == null) {
			return null;
		}

		EquipmentSlot slot = equipable.getEquipmentSlot();
		return isArmorSlot(slot) ? slot : null;
	}

	private static boolean isArmorSlot(EquipmentSlot slot) {
		return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
	}

	/**
	 * The slot this is armor for, or null: only pieces that give armor count (an elytra, a pumpkin or a mob head has none,
	 * so it is never the choice for an armor slot).
	 */
	@Nullable
	public static EquipmentSlot wornSlot(LivingEntity entity, ItemStack stack) {
		EquipmentSlot slot = entity.getEquipmentSlotForItem(stack);
		return isArmorSlot(slot) && armor(stack, slot) > 0.0 ? slot : null;
	}

	/**
	 * Its armor points worn in the slot it is made for: the plain additions to armor only (a vanilla iron chestplate 6),
	 * as a player's armor bar counts them; 0 if it is not armor.
	 */
	public static double armorPoints(ItemStack stack) {
		EquipmentSlot slot = slotOf(stack);
		if (stack.isEmpty() || slot == null) {
			return 0.0;
		}

		double[] sum = {0.0};
		stack.forEachModifier(slot, (attribute, modifier) -> {
			if (attribute.is(Attributes.ARMOR) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE && !Double.isNaN(modifier.amount())) {
				sum[0] += modifier.amount();
			}
		});
		return Math.max(0.0, sum[0]);
	}

	/** 1 (leather) to 5 (netherite): the best vanilla piece for its slot its armor score matches; 0 if it is not armor. */
	public static int armorTier(ItemStack stack) {
		EquipmentSlot slot = armorSlot(stack);
		return slot == null ? 0 : (int)armorTier(stack, slot);
	}

	/** The armor slot this is armor for, without an entity (same rule as {@link #wornSlot}). */
	@Nullable
	public static EquipmentSlot armorSlot(ItemStack stack) {
		EquipmentSlot slot = slotOf(stack);
		return slot != null && armor(stack, slot) > 0.0 ? slot : null;
	}

	// ---------------------------------------------------------------------------------------------
	// Ranged
	// ---------------------------------------------------------------------------------------------

	/** A score for a bow, crossbow or trident; 0 for anything else. */
	public static double ranged(ItemStack stack) {
		GearKinds.Kind kind = GearKinds.of(stack);
		if (kind == null) {
			return 0.0;
		}

		double base;
		Class<?> stop;
		switch (kind.use()) {
			case BOW -> {
				base = BASE_BOW;
				stop = BowItem.class;
			}
			case CROSSBOW -> {
				base = BASE_CROSSBOW;
				stop = CrossbowItem.class;
			}
			case TRIDENT -> {
				base = BASE_TRIDENT;
				stop = TridentItem.class;
			}
			default -> {
				return 0.0;
			}
		}

		double score = base;
		ItemEnchantments enchantments = stack.getEnchantments();
		for (Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment : enchantments.keySet()) {
			if (enchantment.is(Enchantments.POWER) || enchantment.is(Enchantments.PUNCH) || enchantment.is(Enchantments.FLAME)
				|| enchantment.is(Enchantments.INFINITY) || enchantment.is(Enchantments.MULTISHOT) || enchantment.is(Enchantments.PIERCING)
				|| enchantment.is(Enchantments.QUICK_CHARGE) || enchantment.is(Enchantments.LOYALTY) || enchantment.is(Enchantments.IMPALING)) {
				score += ENCHANT_STEP * enchantments.getLevel(enchantment);
			}
		}

		score += RARITY_RANGED_STEP * rarity(stack);
		Class<?> type = stack.getItem().getClass();
		if (stop.isAssignableFrom(type) && com.dinglebob.drifter.entity.DrifterEntity.ownRelease(type, stop)) {
			score += OWN_RELEASE_BONUS;
		}

		if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
			score += 1.0 - (double)stack.getDamageValue() / stack.getMaxDamage();
		}

		return score;
	}

	/** Common 0, uncommon 1, rare 2, epic 3. */
	public static int rarity(ItemStack stack) {
		return stack.getRarity().ordinal();
	}

	// ---------------------------------------------------------------------------------------------
	// Trinkets (optional)
	// ---------------------------------------------------------------------------------------------

	/** What a trinket is worth to this entity: its best slot's value ({@link #trinketIn}); 0 if it fits none of its slots. */
	public static double trinket(LivingEntity entity, ItemStack stack) {
		if (!TrinketsCompat.isTrinket(stack)) {
			return 0.0;
		}

		double best = 0.0;
		for (TrinketsCompat.Slot slot : TrinketsCompat.slots(entity)) {
			if (TrinketsCompat.fits(entity, slot, stack)) {
				best = Math.max(best, trinketIn(entity, slot, stack));
			}
		}

		return best;
	}

	/**
	 * What a trinket is worth worn in that slot (the caller checks it fits): a little for being one, its attribute modifiers
	 * there ({@code TrinketsCompat.modifierValue}), 5 per power a Drifter believes it has, 5 per rarity step.
	 */
	public static double trinketIn(LivingEntity entity, TrinketsCompat.Slot slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return 0.0;
		}

		double value = TRINKET_BASE + TrinketsCompat.modifierValue(entity, slot, stack) + TRINKET_RARITY_STEP * rarity(stack);
		if (entity instanceof DrifterEntity drifter) {
			value += TRINKET_POWER_STEP * Powers.believed(drifter, stack).size();
		}

		return value;
	}

	// ---------------------------------------------------------------------------------------------
	// Cost and trade points
	// ---------------------------------------------------------------------------------------------

	/** The points of the item's recipe key ingredient (what it is made from); 0 without a recipe. Cached per reload. */
	public static double cost(Level level, Item item) {
		synchronized (COSTS) {
			Double cached = COSTS.get(item);
			if (cached != null) {
				return cached;
			}
		}

		if (level == null || RecipeIndex.depth() >= RecipeIndex.MAX_DEPTH) {
			return 0.0;
		}

		double cost = RecipeIndex.guarded(() -> {
			Ingredient key = RecipeIndex.key(level, item);
			if (key == null) {
				return 0.0;
			}

			ItemStack first = RecipeIndex.first(key);
			return first.isEmpty() ? 0.0 : ItemValues.each(first);
		}, 0.0);
		if (RecipeIndex.depth() == 0) {
			synchronized (COSTS) {
				COSTS.put(item, cost);
			}
		}

		return cost;
	}

	/** Weapon tiers by damage per second, weakest first: wood (1) to netherite (5), read from the vanilla swords. */
	private record Tiers(double[] weapon, Map<EquipmentSlot, double[]> armor) {
	}

	private static volatile Tiers TIERS;

	private static Tiers tiers() {
		Tiers tiers = TIERS;
		if (tiers == null) {
			double[] weapon = {dps(new ItemStack(Items.WOODEN_SWORD)), dps(new ItemStack(Items.STONE_SWORD)), dps(new ItemStack(Items.IRON_SWORD)),
				dps(new ItemStack(Items.DIAMOND_SWORD)), dps(new ItemStack(Items.NETHERITE_SWORD))};
			Map<EquipmentSlot, double[]> armor = new HashMap<>();
			armor.put(EquipmentSlot.HEAD, armorTiers(EquipmentSlot.HEAD, Items.LEATHER_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET,
				Items.DIAMOND_HELMET, Items.NETHERITE_HELMET));
			armor.put(EquipmentSlot.CHEST, armorTiers(EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE,
				Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE));
			armor.put(EquipmentSlot.LEGS, armorTiers(EquipmentSlot.LEGS, Items.LEATHER_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS,
				Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS));
			armor.put(EquipmentSlot.FEET, armorTiers(EquipmentSlot.FEET, Items.LEATHER_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS,
				Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS));
			tiers = new Tiers(weapon, armor);
			TIERS = tiers;
		}

		return tiers;
	}

	private static double[] armorTiers(EquipmentSlot slot, Item... items) {
		double[] values = new double[items.length];
		for (int i = 0; i < items.length; i++) {
			values[i] = armor(new ItemStack(items[i]), slot);
		}

		return values;
	}

	/** 1 (wood) to 5 (netherite): the best vanilla sword this one's damage per second matches. */
	private static double weaponTier(ItemStack stack) {
		double dps = dps(stack);
		double[] steps = tiers().weapon();
		double tier = 1.0;
		for (int i = 0; i < steps.length; i++) {
			if (dps + 1.0E-3 >= steps[i]) {
				tier = i + 1;
			}
		}

		return tier;
	}

	/** 1 (leather) to 5 (netherite): the best vanilla piece for that slot this one's armor score matches. */
	private static double armorTier(ItemStack stack, EquipmentSlot slot) {
		double score = armor(stack, slot);
		double[] steps = tiers().armor().get(slot);
		double tier = 1.0;
		for (int i = 0; i < steps.length; i++) {
			if (score + 1.0E-3 >= steps[i]) {
				tier = i + 1;
			}
		}

		return tier;
	}

	/**
	 * Trade points for gear ItemValues has no rule for (a mod's weapons and armor): a weapon is 3 per sword tier its damage
	 * per second matches, armor 4 per vanilla armor tier its score matches, ranged half its score; 5 more per rarity step,
	 * and never less than what its key ingredient is worth. Never NaN or negative.
	 */
	public static double points(ItemStack stack) {
		double points;
		EquipmentSlot slot = armorSlot(stack);
		GearKinds.Kind kind = GearKinds.of(stack);
		if (slot != null) {
			points = armorTier(stack, slot) * POINTS_PER_ARMOR_TIER;
		} else if (kind != null && (kind.use() == GearKinds.Use.BOW || kind.use() == GearKinds.Use.CROSSBOW || kind.use() == GearKinds.Use.TRIDENT)) {
			points = ranged(stack) / 2.0;
		} else if (kind != null) {
			points = weaponTier(stack) * POINTS_PER_WEAPON_TIER;
		} else if (stack.getItem() instanceof ShieldItem) {
			points = SHIELD_POINTS;
		} else if (TrinketsCompat.isTrinket(stack)) {
			points = TRINKET_POINTS;
		} else {
			points = 0.0;
		}

		points += POINTS_PER_RARITY * rarity(stack);
		points = Math.max(points, cost(GearData.level(), stack.getItem()));
		return Double.isNaN(points) || points < 0.0 ? 0.0 : points;
	}

	/** A weapon of some kind, a piece of armor, a shield, or a trinket (Trinkets, optional). */
	public static boolean isGear(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		return GearKinds.of(stack) != null || armorSlot(stack) != null || stack.getItem() instanceof ShieldItem
			|| TrinketsCompat.isTrinket(stack);
	}
}
