package com.dinglebob.drifter.gear;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Weapons;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Each Drifter's taste in gear (spec goal 4): a style drawn from its personality (practical, flashy, loyal, follower), a
 * bonus the style gives a piece, and a "good enough" rule so taste only chooses among items close to the best, never a bad
 * one. Two Drifters with the same pick of items then choose differently, and a server is not all in the same "best" gear.
 * {@link #liking} and {@link #pick} run per candidate item: no allocation beyond one array, no registry scans; the style is
 * cached per Drifter.
 */
public final class GearTaste {
	public enum Style {
		PRACTICAL, FLASHY, LOYAL, FOLLOWER;

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		@Nullable
		public static Style byId(String id) {
			for (Style style : values()) {
				if (style.id().equals(id)) {
					return style;
				}
			}

			return null;
		}
	}

	/** Armor words dropped from an id to leave the set it belongs to (warrior_chestplate and warrior_boots: "warrior_"). */
	private static final Set<String> SLOT_WORDS = Set.of("helmet", "helm", "chestplate", "chest", "leggings", "legging", "pants", "boots", "boot",
		"hat", "cap", "hood", "tunic", "robe", "shoes", "head", "legs", "feet", "armor");
	/** A seed offset, so the style draw is not the favourite-weapon draw. */
	private static final long STYLE_SEED = 0x57E1_E5L;
	/** At least this many worn pieces of one set make a set it can be loyal to. */
	private static final int SET_PIECES = 2;
	private static final double BONUS_MIN = -0.3;
	private static final double BONUS_MAX = 1.0;

	private static final Map<Item, String> SET_KEYS = new ConcurrentHashMap<>();

	private GearTaste() {
	}

	static void clear() {
		SET_KEYS.clear();
	}

	// ---------------------------------------------------------------------------------------------
	// Style
	// ---------------------------------------------------------------------------------------------

	/** Its style: the debug override if set, else the seeded draw (same every time for a Drifter; cached per gear-data reload). */
	public static Style style(DrifterEntity d) {
		GearLore lore = d.getGearLore();
		Style override = lore.styleOverride();
		if (override != null) {
			return override;
		}

		int version = GearData.version();
		Style cached = lore.cachedStyle(version);
		if (cached != null) {
			return cached;
		}

		Style drawn = draw(d);
		lore.cacheStyle(drawn, version);
		return drawn;
	}

	/** The seeded draw, ignoring any override: weights from its traits and archetype, then one roll from its UUID. */
	public static Style draw(DrifterEntity d) {
		Map<Style, GearData.StyleDef> styles = GearData.tables().taste().styles();
		if (styles.isEmpty()) {
			return Style.PRACTICAL;
		}

		Archetype archetype = d.getPersonality().archetype();
		Style[] all = Style.values();
		double[] weights = new double[all.length];
		double total = 0.0;
		for (int i = 0; i < all.length; i++) {
			GearData.StyleDef def = styles.get(all[i]);
			if (def == null) {
				continue;
			}

			double weight = 1.0;
			for (Map.Entry<Trait, Double> like : def.traits().entrySet()) {
				double trait = d.getPersonality().getF(like.getKey());
				weight *= Math.max(0.05, 1.0 + 2.0 * like.getValue() * (trait - 0.5));
			}

			weight *= def.archetypes().getOrDefault(archetype, 1.0);
			weights[i] = weight;
			total += weight;
		}

		if (total <= 0.0) {
			return Style.PRACTICAL;
		}

		double at = RandomSource.create(d.getUUID().getLeastSignificantBits() ^ STYLE_SEED).nextDouble() * total;
		for (int i = 0; i < all.length; i++) {
			at -= weights[i];
			if (at <= 0.0 && weights[i] > 0.0) {
				return all[i];
			}
		}

		return Style.PRACTICAL;
	}

	// ---------------------------------------------------------------------------------------------
	// Bonus and liking
	// ---------------------------------------------------------------------------------------------

	/** What its style adds for this piece, -0.3 to +1.0 (0: no feeling about it). */
	public static double bonus(DrifterEntity d, ItemStack stack) {
		if (stack.isEmpty()) {
			return 0.0;
		}

		GearData.Bonus bonus = GearData.tables().taste().bonus();
		double sum = 0.0;
		Weapons.Family family = Weapons.family(stack);
		if (family != null && family == Weapons.favorite(d)) {
			sum += bonus.favoriteFamily();
		}

		GearLore lore = d.getGearLore();
		switch (style(d)) {
			case FLASHY -> {
				sum += bonus.flashyRarityStep() * GearValue.rarity(stack);
				if (stack.isEnchanted()) {
					sum += bonus.flashyEnchanted();
				}

				if (!"minecraft".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
					sum += bonus.flashyModded();
				}

				if (GearValue.cost(d.level(), stack.getItem()) >= 10.0) {
					sum += bonus.flashyCost();
				}
			}
			case LOYAL -> {
				EquipmentSlot slot = GearValue.armorSlot(stack);
				if (slot != null) {
					String worn = lore.wornSet(d);
					if (worn != null && worn.equals(setKey(stack))) {
						sum += bonus.loyalSet();
					}
				} else {
					GearKinds.Kind kind = GearKinds.of(stack);
					if (kind != null) {
						String loyal = lore.loyalKind();
						if (loyal == null) {
							loyal = lore.favouriteKind(d);
						}

						if (kind.id().equals(loyal)) {
							sum += bonus.loyalKind();
						}
					}
				}
			}
			case FOLLOWER -> {
				// With no friends noted it acts practical.
				String key = followKey(stack);
				if (key != null) {
					sum += bonus.followerShare() * lore.friendShare(key);
				}
			}
			default -> {
			}
		}

		return Math.max(BONUS_MIN, Math.min(BONUS_MAX, sum));
	}

	/** The kind id (weapon) or set key (armor) friends' gear is counted under. */
	@Nullable
	public static String followKey(ItemStack stack) {
		if (GearValue.armorSlot(stack) != null) {
			return setKey(stack);
		}

		GearKinds.Kind kind = GearKinds.of(stack);
		return kind != null && kind.use().usable() ? kind.id() : null;
	}

	/** Its value with its taste in it: {@code value * (1 + strength * bonus + noise)}, never below 0. */
	public static double liking(DrifterEntity d, ItemStack stack, double value) {
		GearData.Taste taste = GearData.tables().taste();
		double factor = 1.0 + taste.strength() * bonus(d, stack) + noise(d, stack.getItem(), taste.noise());
		return Math.max(0.0, value * factor);
	}

	/** Steady for a Drifter and an item, different between Drifters: in [-amount, +amount]. */
	static double noise(DrifterEntity d, Item item, double amount) {
		long h = d.getUUID().getLeastSignificantBits() ^ (BuiltInRegistries.ITEM.getKey(item).hashCode() * 0x9E3779B97F4A7C15L);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return ((h >>> 11) * 0x1.0p-53 * 2.0 - 1.0) * amount;
	}

	/**
	 * The good-enough rule: options worth less than {@code good_enough} of the best are out, and of the rest it takes the one
	 * it likes most. Null for no options.
	 */
	@Nullable
	public static <T> T pick(DrifterEntity d, List<T> options, ToDoubleFunction<T> value, Function<T, ItemStack> stack) {
		int count = options.size();
		if (count == 0) {
			return null;
		}

		double[] values = new double[count];
		double best = 0.0;
		for (int i = 0; i < count; i++) {
			double v = value.applyAsDouble(options.get(i));
			values[i] = Double.isNaN(v) ? 0.0 : v;
			best = Math.max(best, values[i]);
		}

		double threshold = GearData.tables().taste().goodEnough() * best;
		T chosen = null;
		double top = -1.0;
		for (int i = 0; i < count; i++) {
			if (values[i] < threshold) {
				continue;
			}

			T option = options.get(i);
			double liking = liking(d, stack.apply(option), values[i]);
			if (liking > top) {
				top = liking;
				chosen = option;
			}
		}

		return chosen;
	}

	/** True: this is worth considering at all for a pick whose best is worth {@code best}. */
	public static boolean goodEnough(double value, double best) {
		return value >= GearData.tables().taste().goodEnough() * best;
	}

	// ---------------------------------------------------------------------------------------------
	// Sets
	// ---------------------------------------------------------------------------------------------

	/** The armor's id with the slot word removed ({@code immersive_armors:warrior_}); the whole id if it has none. Cached. */
	public static String setKey(ItemStack armor) {
		return SET_KEYS.computeIfAbsent(armor.getItem(), GearTaste::computeSetKey);
	}

	private static String computeSetKey(Item item) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
		StringBuilder out = new StringBuilder();
		boolean dropped = false;
		for (String word : id.getPath().split("_")) {
			if (SLOT_WORDS.contains(word)) {
				dropped = true;
			} else if (!word.isEmpty()) {
				out.append(word).append('_');
			}
		}

		return dropped && out.length() > 0 ? id.getNamespace() + ":" + out : id.toString();
	}
}
