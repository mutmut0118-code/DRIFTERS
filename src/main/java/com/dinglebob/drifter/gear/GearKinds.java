package com.dinglebob.drifter.gear;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Weapons;
import com.dinglebob.drifter.compat.BetterCombatCompat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;

/**
 * What kind of weapon an item is, from the item itself (spec: "Kinds for unknown weapons"): a katana, a dagger, a bow...
 * Cached per item until the next reload. Shields are not weapons (they stay with {@code hands/Shields}).
 */
public final class GearKinds {
	/** How a kind of weapon is used. Thrown weapons and staves are known but not wielded yet (spec goal 9). */
	public enum Use {
		MELEE, BOW, CROSSBOW, TRIDENT, THROWN, STAFF;

		public boolean usable() {
			return this != THROWN && this != STAFF;
		}
	}

	public record Kind(String id, Use use) {
	}

	/** A main-hand attack damage this big (a plain add) makes a non-tool item a sword-like weapon. */
	private static final double WEAPON_DAMAGE = 2.0;

	private static final Map<Item, Optional<Kind>> CACHE = new ConcurrentHashMap<>();

	private GearKinds() {
	}

	static void clear() {
		CACHE.clear();
	}

	@Nullable
	public static Kind of(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}

		Item item = stack.getItem();
		Optional<Kind> cached = CACHE.get(item);
		if (cached == null) {
			cached = Optional.ofNullable(compute(item));
			CACHE.put(item, cached);
		}

		Kind kind = cached.orElse(null);
		// A single stack with its own name ("Oak Staff") is read by that name; not cached, it is this stack's.
		if ((stack.getCount() == 1 || item.getDefaultMaxStackSize() == 1) && (kind == null || kind.use() == Use.MELEE || kind.use() == Use.THROWN || kind.use() == Use.STAFF)
			&& hasOwnName(stack)) {
			GearData.KindDef byName = byWords(GearData.tables(), words(stack.getHoverName().getString()));
			if (byName != null) {
				return new Kind(byName.id(), byName.use());
			}
		}

		return kind;
	}

	private static boolean hasOwnName(ItemStack stack) {
		if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
			return true;
		}

		var patch = stack.getComponentsPatch();
		if (patch.isEmpty()) {
			return false;
		}

		var itemName = patch.get(net.minecraft.core.component.DataComponents.ITEM_NAME);
		return itemName != null && itemName.isPresent();
	}

	public static boolean usableWeapon(ItemStack stack) {
		Kind kind = of(stack);
		return kind != null && kind.use().usable();
	}

	@Nullable
	private static Kind compute(Item item) {
		ItemStack stack = new ItemStack(item);
		GearData.Tables tables = GearData.tables();
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();

		// 1. A drifter_weapons family item: always usable in melee, whatever its words say.
		Weapons.Family family = Weapons.family(stack);
		if (family != null) {
			String familyPath = family.id().substring(family.id().indexOf(':') + 1);
			GearData.KindDef byFamily = byWords(tables, words(familyPath));
			return new Kind(byFamily != null ? byFamily.id() : familyPath, Use.MELEE);
		}

		// 2. Shields are not weapons.
		if (item instanceof ShieldItem) {
			return null;
		}

		// 3. The vanilla ranged classes.
		if (item instanceof CrossbowItem) {
			return new Kind("crossbow", Use.CROSSBOW);
		} else if (item instanceof BowItem) {
			return new Kind("bow", Use.BOW);
		} else if (item instanceof TridentItem) {
			return new Kind("trident", Use.TRIDENT);
		}

		// 4. Better Combat's own category.
		String category = BetterCombatCompat.category(stack);
		if (category != null) {
			for (GearData.KindDef kind : tables.kinds()) {
				if (kind.betterCombat().contains(category)) {
					return new Kind(kind.id(), kind.use());
				}
			}
		}

		// 5. A kind's item tag.
		for (GearData.KindDef kind : tables.kinds()) {
			for (var tag : kind.tags()) {
				if (stack.is(tag)) {
					return new Kind(kind.id(), kind.use());
				}
			}
		}

		// 6. A kind word in the id, then in the English name. Weapons do not stack: a pile of "staff heads" is not one.
		if (item.getDefaultMaxStackSize() == 1) {
			GearData.KindDef byId = byWords(tables, words(path));
			if (byId != null) {
				return new Kind(byId.id(), byId.use());
			}

			GearData.KindDef byName = byWords(tables, words(name(item)));
			if (byName != null) {
				return new Kind(byName.id(), byName.use());
			}
		}

		// 7. In our melee tag, or a non-tool with a real attack damage: a sword.
		if (stack.is(Weapons.MELEE) || (!(item instanceof DiggerItem) && mainHandDamage(stack) >= WEAPON_DAMAGE)) {
			return new Kind("sword", Use.MELEE);
		}

		return null;
	}

	private static String name(Item item) {
		try {
			String name = item.getDescription().getString();
			// A dedicated server has no translations: the name is the key ("item.mod.path"), whose mod id could hold any word.
			return name.equals(item.getDescriptionId()) ? "" : name;
		} catch (RuntimeException e) {
			return "";
		}
	}

	private static double mainHandDamage(ItemStack stack) {
		double[] damage = {0.0};
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (attribute.is(Attributes.ATTACK_DAMAGE) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
				damage[0] += modifier.amount();
			}
		});
		return damage[0];
	}

	private static Set<String> words(String text) {
		Set<String> words = new HashSet<>();
		for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
			if (!word.isEmpty()) {
				words.add(word);
			}
		}

		return words;
	}

	/** The kind with the longest word that is one of these whole words. */
	@Nullable
	private static GearData.KindDef byWords(GearData.Tables tables, Set<String> words) {
		for (Map.Entry<String, GearData.KindDef> entry : tables.byWord()) {
			if (words.contains(entry.getKey())) {
				return entry.getValue();
			}
		}

		return null;
	}
}
