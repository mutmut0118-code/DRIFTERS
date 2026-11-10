package com.dinglebob.drifter.gear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The matching armor sets armor mods add (modded armor in starting kits, spec 2026-11-10-modded-armor-kits-design.md),
 * found from the registered items and rated against the vanilla tiers by {@link GearValue#armor}, so any armor mod works
 * with no list. A set is the head, chest, legs and feet items one mod makes together: the same id with the slot word taken
 * out ({@link GearTaste#setKey}: {@code immersive_armors:warrior_helmet} and {@code _boots} are {@code immersive_armors:warrior}),
 * and a piece whose id shares nothing joins a set of the same mod and armor material that lacks its slot (or such pieces make
 * one by material alone). Two slots or more make a set; a missing slot is filled with
 * the vanilla piece of the set's tier. Built once per reload ({@link #clear} from {@link GearData}); every lookup after that
 * reads precomputed lists.
 */
public final class ArmorSets {
	/**
	 * A set is at a tier when its pieces' total is within this share of the same vanilla slots' total (and a piece within the next
	 * tier); a single piece when it is within this share of the tier's vanilla piece, the closest tier's.
	 */
	public static final double SET_TIER_BAND = 0.15;
	/** The armor slots, head to feet (the order sets list their pieces in). */
	public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** The vanilla armor tiers a kit is dressed at, weakest first (gold and netherite are not a kit's tier). */
	public enum Tier {
		LEATHER(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS),
		CHAIN(Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS),
		IRON(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS),
		DIAMOND(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);

		private final Item head;
		private final Item chest;
		private final Item legs;
		private final Item feet;

		Tier(Item head, Item chest, Item legs, Item feet) {
			this.head = head;
			this.chest = chest;
			this.legs = legs;
			this.feet = feet;
		}

		/** The vanilla piece of this tier for an armor slot (the chestplate for any other slot). */
		public Item vanilla(EquipmentSlot slot) {
			return switch (slot) {
				case HEAD -> this.head;
				case LEGS -> this.legs;
				case FEET -> this.feet;
				default -> this.chest;
			};
		}

		/** The tier above, or null for diamond (whose upper bound is its own piece plus the band). */
		@Nullable
		public Tier next() {
			int at = this.ordinal() + 1;
			return at < values().length ? values()[at] : null;
		}

		public String id() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/**
	 * One matching set. {@code pieces} has every armor slot: the mod's own pieces, and the vanilla piece of {@code tier} (iron
	 * when it has no tier) where the mod made none. {@code trimmable}: every one of the mod's pieces takes an armor trim.
	 */
	public record Set(String id, Map<EquipmentSlot, Item> pieces, boolean trimmable, @Nullable Tier tier) {
		/** True: the piece in this slot is a vanilla filler, not the mod's own. */
		public boolean filler(EquipmentSlot slot) {
			Item item = this.pieces.get(slot);
			return item == null || "minecraft".equals(BuiltInRegistries.ITEM.getKey(item).getNamespace());
		}
	}

	/** A modded piece and its value worn in its slot (a prototype stack for taste, never handed out). */
	private record Piece(Item item, ItemStack stack, EquipmentSlot slot, double value) {
	}

	/** Everything worked out at once: the sets by id and by tier, and the single modded pieces per slot and tier. */
	private record Index(List<Set> sets, Map<String, Set> byId, Map<Tier, List<Set>> atTier, Map<Tier, List<Set>> trimmableAtTier,
		Map<EquipmentSlot, Map<Tier, List<Piece>>> pieces) {
	}

	private static volatile Index index;

	private ArmorSets() {
	}

	/** Drops the cache (gear data or recipes reloaded, a server started or stopped); the next lookup builds it again. */
	static void clear() {
		index = null;
	}

	/** Every modded matching set, sorted by id; empty without armor mods. */
	public static List<Set> sets() {
		return index().sets();
	}

	@Nullable
	public static Set byId(String id) {
		return id == null ? null : index().byId().get(id);
	}

	/** The sets at a tier (only the trimmable ones if asked); a set with no tier is never here. */
	public static List<Set> at(Tier tier, boolean trimmableOnly) {
		Index current = index();
		List<Set> list = (trimmableOnly ? current.trimmableAtTier() : current.atTier()).get(tier);
		return list == null ? List.of() : list;
	}

	/**
	 * A single modded piece for that slot whose value fits the tier: closer to the tier's vanilla piece than to any other tier's,
	 * within the band of it, and at most the next tier's piece (diamond: its own plus the band; ruling K11). Random among them; with a Drifter, leaned by its taste
	 * ({@link GearTaste#liking}). Null when no modded piece fits.
	 */
	@Nullable
	public static Item modPieceAt(EquipmentSlot slot, Tier tier, RandomSource random, @Nullable DrifterEntity taste) {
		Map<Tier, List<Piece>> bySlot = index().pieces().get(slot);
		List<Piece> options = bySlot == null ? null : bySlot.get(tier);
		if (options == null || options.isEmpty()) {
			return null;
		}

		if (taste == null || options.size() == 1) {
			return options.get(random.nextInt(options.size())).item();
		}

		double[] weights = new double[options.size()];
		double total = 0.0;
		for (int i = 0; i < weights.length; i++) {
			Piece piece = options.get(i);
			weights[i] = GearTaste.liking(taste, piece.stack(), piece.value());
			total += weights[i];
		}

		if (!(total > 0.0)) {
			return options.get(random.nextInt(options.size())).item();
		}

		double at = random.nextDouble() * total;
		for (int i = 0; i < weights.length; i++) {
			at -= weights[i];
			if (at <= 0.0) {
				return options.get(i).item();
			}
		}

		return options.get(options.size() - 1).item();
	}

	/** How many single modded pieces fit each slot at a tier (debug), head to feet. */
	public static int[] pieceCounts(Tier tier) {
		int[] counts = new int[SLOTS.length];
		Index current = index();
		for (int i = 0; i < SLOTS.length; i++) {
			List<Piece> list = current.pieces().get(SLOTS[i]).get(tier);
			counts[i] = list == null ? 0 : list.size();
		}

		return counts;
	}

	// ---------------------------------------------------------------------------------------------
	// Building
	// ---------------------------------------------------------------------------------------------

	private static Index index() {
		Index current = index;
		if (current == null) {
			synchronized (ArmorSets.class) {
				current = index;
				if (current == null) {
					try {
						current = build();
					} catch (RuntimeException e) {
						// (Never a lookup failing on every spawn: without the index, kits are vanilla until the next reload.)
						Drifter.LOGGER.error("[Drifter] armor sets: couldn't work out the modded armor sets; kits stay vanilla", e);
						current = empty();
					}

					index = current;
				}
			}
		}

		return current;
	}

	/** No modded sets or pieces at all (what a build that failed leaves). */
	private static Index empty() {
		Map<Tier, List<Set>> noSets = new EnumMap<>(Tier.class);
		Map<Tier, List<Piece>> noPieces = new EnumMap<>(Tier.class);
		for (Tier tier : Tier.values()) {
			noSets.put(tier, List.of());
			noPieces.put(tier, List.of());
		}

		Map<EquipmentSlot, Map<Tier, List<Piece>>> pieces = new EnumMap<>(EquipmentSlot.class);
		for (EquipmentSlot slot : SLOTS) {
			pieces.put(slot, Collections.unmodifiableMap(noPieces));
		}

		return new Index(List.of(), Map.of(), Collections.unmodifiableMap(noSets), Collections.unmodifiableMap(noSets),
			Collections.unmodifiableMap(pieces));
	}

	/** The vanilla value of each tier's piece per slot ({@code [tier][slot]}, slots as {@link #SLOTS}). */
	private static double[][] vanillaValues() {
		Tier[] tiers = Tier.values();
		double[][] values = new double[tiers.length][SLOTS.length];
		for (Tier tier : tiers) {
			for (int s = 0; s < SLOTS.length; s++) {
				values[tier.ordinal()][s] = GearValue.armor(new ItemStack(tier.vanilla(SLOTS[s])), SLOTS[s]);
			}
		}

		return values;
	}

	/** The most a piece of that tier may be worth in that slot: the next tier's piece, or diamond's plus the band. */
	private static double upper(double[][] vanilla, Tier tier, int slot) {
		Tier next = tier.next();
		return next != null ? vanilla[next.ordinal()][slot] : vanilla[tier.ordinal()][slot] * (1.0 + SET_TIER_BAND);
	}

	/**
	 * The tier a single modded piece is handed out at (ruling K11, K4's closest-wins per piece): the tier whose vanilla piece of
	 * that slot it is closest to, when it is within the band of that piece and not above the next tier's piece; none otherwise.
	 * Where two tiers' vanilla pieces are worth the same in that slot (chainmail and iron helmets, leather and chainmail boots)
	 * the piece fits both equally, so it is at both; it is never at two tiers of different strength.
	 */
	private static List<Tier> singleTiers(double[][] vanilla, int slot, double value) {
		double closest = Double.MAX_VALUE;
		for (Tier tier : Tier.values()) {
			closest = Math.min(closest, Math.abs(value - vanilla[tier.ordinal()][slot]));
		}

		List<Tier> tiers = new ArrayList<>(2);
		for (Tier tier : Tier.values()) {
			double reference = vanilla[tier.ordinal()][slot];
			double off = Math.abs(value - reference);
			if (off <= closest + 1.0E-6 && off <= reference * SET_TIER_BAND + 1.0E-6 && value <= upper(vanilla, tier, slot) + 1.0E-6) {
				tiers.add(tier);
			}
		}

		return tiers;
	}

	private static int slotIndex(EquipmentSlot slot) {
		for (int i = 0; i < SLOTS.length; i++) {
			if (SLOTS[i] == slot) {
				return i;
			}
		}

		return -1;
	}

	private static Index build() {
		double[][] vanilla = vanillaValues();
		// Every modded armor piece, grouped by its id with the slot word taken out.
		Map<String, Map<EquipmentSlot, List<Piece>>> byKey = new LinkedHashMap<>();
		Map<EquipmentSlot, Map<Tier, List<Piece>>> singles = new EnumMap<>(EquipmentSlot.class);
		for (EquipmentSlot slot : SLOTS) {
			Map<Tier, List<Piece>> perTier = new EnumMap<>(Tier.class);
			for (Tier tier : Tier.values()) {
				perTier.put(tier, new ArrayList<>());
			}

			singles.put(slot, perTier);
		}

		for (Item item : BuiltInRegistries.ITEM) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			if ("minecraft".equals(id.getNamespace())) {
				continue;
			}

			// (One mod's item that breaks when made or read is left out, not every spawn after it failing to build this.)
			Piece piece;
			String key;
			try {
				ItemStack stack = new ItemStack(item);
				EquipmentSlot slot = stack.isEmpty() ? null : GearValue.armorSlot(stack);
				if (slot == null) {
					continue;
				}

				piece = new Piece(item, stack, slot, GearValue.armor(stack, slot));
				key = GearTaste.setKey(stack);
			} catch (RuntimeException e) {
				Drifter.LOGGER.warn("[Drifter] armor sets: couldn't read {} ({}); left out of modded kits", id, e.toString());
				continue;
			}

			byKey.computeIfAbsent(key, k -> new EnumMap<>(EquipmentSlot.class)).computeIfAbsent(piece.slot(), k -> new ArrayList<>()).add(piece);
			for (Tier tier : singleTiers(vanilla, slotIndex(piece.slot()), piece.value())) {
				singles.get(piece.slot()).get(tier).add(piece);
			}
		}

		// Groups of two slots or more are sets by their id. The pieces left over (ids that share no prefix, as a slot word the
		// key doesn't know: bronze_greaves) join a set of the same mod and armor material that lacks their slot (the lowest
		// set id if several), so bronze_helmet/_chestplate/_greaves/_sabatons stay one set; the rest group by material alone.
		Map<String, Map<EquipmentSlot, List<Piece>>> groups = new java.util.TreeMap<>();
		List<Piece> leftovers = new ArrayList<>();
		for (Map.Entry<String, Map<EquipmentSlot, List<Piece>>> group : byKey.entrySet()) {
			if (group.getValue().size() >= 2) {
				groups.put(setId(group.getKey()), group.getValue());
			} else {
				group.getValue().values().forEach(leftovers::addAll);
			}
		}

		leftovers.sort((a, b) -> BuiltInRegistries.ITEM.getKey(a.item()).compareTo(BuiltInRegistries.ITEM.getKey(b.item())));
		Map<String, Map<EquipmentSlot, List<Piece>>> byMaterial = new java.util.TreeMap<>();
		for (Piece piece : leftovers) {
			String material = materialKey(piece.item());
			if (material == null) {
				continue;
			}

			Map<EquipmentSlot, List<Piece>> home = null;
			for (Map<EquipmentSlot, List<Piece>> group : groups.values()) {
				if (!group.containsKey(piece.slot()) && hasMaterial(group, material)) {
					home = group;
					break;
				}
			}

			if (home == null) {
				home = byMaterial.computeIfAbsent(material, k -> new EnumMap<>(EquipmentSlot.class));
			}

			home.computeIfAbsent(piece.slot(), k -> new ArrayList<>()).add(piece);
		}

		for (Map.Entry<String, Map<EquipmentSlot, List<Piece>>> group : byMaterial.entrySet()) {
			if (group.getValue().size() >= 2) {
				// A prefix set of that name is of another material (one of the same took these pieces above): number this one.
				String id = group.getKey();
				for (int n = 2; groups.containsKey(id); n++) {
					id = group.getKey() + "_" + n;
				}

				groups.put(id, group.getValue());
			}
		}

		List<Set> sets = new ArrayList<>();
		for (Map.Entry<String, Map<EquipmentSlot, List<Piece>>> group : groups.entrySet()) {
			try {
				sets.add(rate(group.getKey(), group.getValue(), vanilla));
			} catch (RuntimeException e) {
				Drifter.LOGGER.warn("[Drifter] armor sets: couldn't rate the set {} ({}); left out of modded kits", group.getKey(), e.toString());
			}
		}

		sets.sort((a, b) -> a.id().compareTo(b.id()));
		Map<String, Set> byId = new HashMap<>();
		Map<Tier, List<Set>> atTier = new EnumMap<>(Tier.class);
		Map<Tier, List<Set>> trimmableAtTier = new EnumMap<>(Tier.class);
		for (Tier tier : Tier.values()) {
			List<Set> all = new ArrayList<>();
			List<Set> trimmable = new ArrayList<>();
			for (Set set : sets) {
				if (set.tier() == tier) {
					all.add(set);
					if (set.trimmable()) {
						trimmable.add(set);
					}
				}
			}

			atTier.put(tier, List.copyOf(all));
			trimmableAtTier.put(tier, List.copyOf(trimmable));
		}

		for (Set set : sets) {
			byId.put(set.id(), set);
		}

		Map<EquipmentSlot, Map<Tier, List<Piece>>> frozen = new EnumMap<>(EquipmentSlot.class);
		singles.forEach((slot, perTier) -> {
			Map<Tier, List<Piece>> copy = new EnumMap<>(Tier.class);
			perTier.forEach((tier, list) -> copy.put(tier, List.copyOf(list)));
			frozen.put(slot, Collections.unmodifiableMap(copy));
		});
		return new Index(List.copyOf(sets), Collections.unmodifiableMap(byId), Collections.unmodifiableMap(atTier),
			Collections.unmodifiableMap(trimmableAtTier), Collections.unmodifiableMap(frozen));
	}

	/** {@code immersive_armors:warrior_} (a set key) is the set {@code immersive_armors:warrior}. */
	private static String setId(String key) {
		return key.endsWith("_") ? key.substring(0, key.length() - 1) : key;
	}

	/** True: one of the group's pieces is of that material ({@link #materialKey}). */
	private static boolean hasMaterial(Map<EquipmentSlot, List<Piece>> group, String material) {
		for (List<Piece> pieces : group.values()) {
			for (Piece piece : pieces) {
				if (material.equals(materialKey(piece.item()))) {
					return true;
				}
			}
		}

		return false;
	}

	/** The mod and its armor material ({@code somemod:bronze}) for grouping pieces whose ids share nothing; null if not an ArmorItem. */
	@Nullable
	private static String materialKey(Item item) {
		if (!(item instanceof ArmorItem armor)) {
			return null;
		}

		String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
		return armor.getMaterial().unwrapKey().map(key -> namespace + ":" + key.location().getPath()).orElse(null);
	}

	/**
	 * Rates a group: one piece per slot (the best worth when a mod made two for one slot), its tier judged over the mod's own
	 * pieces only (their total within the band of the same slots' vanilla total, and no piece above the next tier's piece;
	 * where two tiers' bands overlap, the closer total wins), then the missing slots filled with that tier's vanilla piece
	 * (iron with no tier).
	 */
	private static Set rate(String id, Map<EquipmentSlot, List<Piece>> group, double[][] vanilla) {
		Map<EquipmentSlot, Piece> chosen = new EnumMap<>(EquipmentSlot.class);
		group.forEach((slot, pieces) -> {
			Piece best = null;
			for (Piece piece : pieces) {
				if (best == null || piece.value() > best.value()
					|| piece.value() == best.value() && BuiltInRegistries.ITEM.getKey(piece.item()).compareTo(BuiltInRegistries.ITEM.getKey(best.item())) < 0) {
					best = piece;
				}
			}

			chosen.put(slot, best);
		});

		double total = 0.0;
		boolean trimmable = true;
		for (Piece piece : chosen.values()) {
			total += piece.value();
			trimmable &= piece.stack().is(ItemTags.TRIMMABLE_ARMOR);
		}

		Tier tier = null;
		double closest = Double.MAX_VALUE;
		for (Tier candidate : Tier.values()) {
			double reference = 0.0;
			boolean capped = true;
			for (Map.Entry<EquipmentSlot, Piece> entry : chosen.entrySet()) {
				int s = slotIndex(entry.getKey());
				reference += vanilla[candidate.ordinal()][s];
				capped &= entry.getValue().value() <= upper(vanilla, candidate, s) + 1.0E-6;
			}

			double off = Math.abs(total - reference);
			if (capped && reference > 0.0 && off <= reference * SET_TIER_BAND + 1.0E-6 && off < closest) {
				closest = off;
				tier = candidate;
			}
		}

		Tier fill = tier == null ? Tier.IRON : tier;
		Map<EquipmentSlot, Item> pieces = new EnumMap<>(EquipmentSlot.class);
		for (EquipmentSlot slot : SLOTS) {
			Piece piece = chosen.get(slot);
			pieces.put(slot, piece != null ? piece.item() : fill.vanilla(slot));
		}

		return new Set(id, Collections.unmodifiableMap(pieces), trimmable, tier);
	}
}
