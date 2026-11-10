package com.dinglebob.drifter.world;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.TownUniform;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.gear.ArmorSets;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.minecraft.world.item.armortrim.TrimPattern;
import net.minecraft.world.item.armortrim.TrimPatterns;

/**
 * Town uniforms (modded armor in starting kits, spec 2026-11-10-modded-armor-kits-design.md, goals 1, 2, 3 and 9): a
 * town's guards all wear one armor set, a trimmable modded set at iron's tier when one is installed (trimmed iron when
 * not), with the town's trim on every piece; a town in a nation wears the nation's trim, its material from the nation's
 * colour. The trim is worked out from the town's (or nation's) id, so it is the same every time without being saved;
 * the set is chosen once by {@link #chooseSet} and kept by whoever keeps the town (Drifter: Civilizations, through the API).
 */
public final class Uniforms {
	/** The uniform set when no modded set fits: vanilla iron. */
	public static final String IRON = "minecraft:iron";
	/** The trim materials a town's trim is drawn from (vanilla's ten; a nation's follows its colour instead). */
	private static final List<ResourceKey<TrimMaterial>> MATERIALS = List.of(TrimMaterials.QUARTZ, TrimMaterials.IRON, TrimMaterials.NETHERITE,
		TrimMaterials.REDSTONE, TrimMaterials.COPPER, TrimMaterials.GOLD, TrimMaterials.EMERALD, TrimMaterials.DIAMOND, TrimMaterials.LAPIS,
		TrimMaterials.AMETHYST);
	/**
	 * The trim patterns a town's or nation's trim is drawn from (the same entries and order armies had when uniforms were made).
	 * Never reorder or append: town and nation trims are derived from this order and not saved, so a change would switch every
	 * town's trim and break the joining and leaving switch (it looks for the old trim on what guards wear).
	 */
	private static final List<ResourceKey<TrimPattern>> PATTERNS = List.of(TrimPatterns.SENTRY, TrimPatterns.VEX, TrimPatterns.WILD,
		TrimPatterns.COAST, TrimPatterns.DUNE, TrimPatterns.WARD, TrimPatterns.RIB, TrimPatterns.SNOUT, TrimPatterns.TIDE, TrimPatterns.SPIRE,
		TrimPatterns.FLOW, TrimPatterns.BOLT);

	private Uniforms() {
	}

	/**
	 * A town's uniform set: one of the trimmable modded sets at iron's tier, at random (towns differ, not always the single best
	 * set), or {@link #IRON} when none is installed. A set that isn't trimmable is never a uniform: it couldn't wear the town's
	 * colours.
	 */
	public static String chooseSet(RandomSource random) {
		List<ArmorSets.Set> sets = ArmorSets.at(ArmorSets.Tier.IRON, true);
		return sets.isEmpty() ? IRON : sets.get(random.nextInt(sets.size())).id();
	}

	/**
	 * A town's uniform: that set and the town's own trim, the pattern and the material each picked from its id (the same every
	 * time; the material from the id's hash past the pattern's share, so every pattern and material pair can come up).
	 */
	public static TownUniform townTrim(UUID town, String armorSet) {
		int hash = town.hashCode();
		ResourceKey<TrimPattern> pattern = PATTERNS.get(Math.floorMod(hash, PATTERNS.size()));
		ResourceKey<TrimMaterial> material = MATERIALS.get(Math.floorMod(Math.floorDiv(hash, PATTERNS.size()), MATERIALS.size()));
		return new TownUniform(armorSet, pattern.location().toString(), material.location().toString());
	}

	/** A town in a nation: its own set, the nation's trim (the material from the nation's colour, as an army's; the pattern from its id). */
	public static TownUniform nationTrim(UUID nation, DyeColor colour, String armorSet) {
		ResourceKey<TrimPattern> pattern = PATTERNS.get(Math.floorMod(nation.hashCode(), PATTERNS.size()));
		return new TownUniform(armorSet, pattern.location().toString(), SoldierKit.trimFor(colour).location().toString());
	}

	/**
	 * The uniform's set as worn: a trimmable modded set at iron's tier, or null for iron (the set is {@link #IRON}, or the id
	 * no longer names such a set: its mod removed or changed).
	 */
	@Nullable
	public static ArmorSets.Set set(@Nullable TownUniform uniform) {
		if (uniform == null) {
			return null;
		}

		ArmorSets.Set set = ArmorSets.byId(uniform.armorSet());
		return set != null && set.trimmable() && set.tier() == ArmorSets.Tier.IRON ? set : null;
	}

	/** The uniform's piece for an armor slot: the set's, or iron's (never nothing). */
	public static Item piece(@Nullable ArmorSets.Set set, EquipmentSlot slot) {
		Item item = set == null ? null : set.pieces().get(slot);
		return item != null ? item : ArmorSets.Tier.IRON.vanilla(slot);
	}

	/** The uniform's trim in this world, or null when its pattern or material isn't registered here (no trim then). */
	@Nullable
	public static ArmorTrim trim(RegistryAccess registries, @Nullable TownUniform uniform) {
		if (uniform == null) {
			return null;
		}

		Holder<TrimPattern> pattern = holder(registries, Registries.TRIM_PATTERN, uniform.trimPattern());
		Holder<TrimMaterial> material = holder(registries, Registries.TRIM_MATERIAL, uniform.trimMaterial());
		return pattern == null || material == null ? null : new ArmorTrim(material, pattern, false);
	}

	/** Puts the trim on the stack if it takes one (in {@code minecraft:trimmable_armor}); the same stack back. */
	public static ItemStack trimmed(ItemStack stack, @Nullable ArmorTrim trim) {
		if (trim != null && stack.is(ItemTags.TRIMMABLE_ARMOR)) {
			stack.set(DataComponents.TRIM, trim);
		}

		return stack;
	}

	/**
	 * The trim switched on what it wears (joining or leaving a nation): every worn armor piece carrying {@code from}'s trim
	 * (pattern and material) takes {@code to}'s; pieces with another trim or none are left alone, nothing is added. Returns how
	 * many pieces changed (0 when {@code to}'s trim isn't registered here, or either uniform is null).
	 */
	public static int retrim(DrifterEntity drifter, @Nullable TownUniform from, @Nullable TownUniform to) {
		if (from == null || to == null) {
			return 0;
		}

		RegistryAccess registries = drifter.level().registryAccess();
		ArmorTrim next = trim(registries, to);
		ResourceLocation pattern = ResourceLocation.tryParse(from.trimPattern());
		ResourceLocation material = ResourceLocation.tryParse(from.trimMaterial());
		if (next == null || pattern == null || material == null) {
			return 0;
		}

		int changed = 0;
		for (EquipmentSlot slot : ArmorSets.SLOTS) {
			ItemStack worn = drifter.getItemBySlot(slot);
			ArmorTrim trim = worn.isEmpty() ? null : worn.get(DataComponents.TRIM);
			if (trim != null && trim.pattern().is(pattern) && trim.material().is(material) && !trim.equals(next)) {
				worn.set(DataComponents.TRIM, next);
				changed++;
			}
		}

		return changed;
	}

	@Nullable
	private static <T> Holder<T> holder(RegistryAccess registries, ResourceKey<? extends net.minecraft.core.Registry<T>> registry, @Nullable String id) {
		ResourceLocation location = id == null ? null : ResourceLocation.tryParse(id);
		if (location == null) {
			return null;
		}

		return registries.registry(registry).flatMap(r -> r.getHolder(ResourceKey.create(registry, location))).orElse(null);
	}
}
