package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.dinglebob.drifter.api.TownWork;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * A nation's banner design (the user, 2026-11-15: "i would love to have the town banner to have an actual design instead of
 * their color"): its colour behind, then a division of the field, a charge on it, and now and then a border or a band, in
 * one or two other colours, like heraldry. Only patterns a loom makes with a dye alone (none needing a pattern item), and only
 * dyes a Drifter gets from common flowers and bones, so its people can make it themselves.
 */
final class BannerDesigns {
	private BannerDesigns() {
	}

	/** Splitting the field: half, quarter, diagonal, stripes. */
	private static final String[] DIVISIONS = {"half_horizontal", "half_horizontal_bottom", "half_vertical", "half_vertical_right", "diagonal_left",
		"diagonal_right", "diagonal_up_left", "diagonal_up_right", "stripe_top", "stripe_bottom", "stripe_left", "stripe_right", "square_top_left",
		"square_top_right", "triangle_bottom", "triangle_top", "gradient", "gradient_up", "small_stripes", "bricks"};
	/** The charge on it. */
	private static final String[] CHARGES = {"cross", "straight_cross", "circle", "rhombus", "stripe_center", "stripe_middle", "stripe_downright",
		"stripe_downleft", "triangles_bottom", "triangles_top", "curly_border"};
	/** Last, now and then: a border, or a band across. */
	private static final String[] FINISHES = {"border", "curly_border", "stripe_bottom", "stripe_top", "triangles_bottom"};
	/** Dyes from common flowers and bone meal (and one crafted: purple). */
	private static final DyeColor[] DYES = {DyeColor.WHITE, DyeColor.RED, DyeColor.YELLOW, DyeColor.BLUE, DyeColor.ORANGE, DyeColor.LIGHT_GRAY,
		DyeColor.PINK, DyeColor.MAGENTA, DyeColor.PURPLE, DyeColor.LIGHT_BLUE};

	/** A design for a nation of this colour, its own (not one of {@code taken}), from its id. */
	static List<TownWork.Layer> design(UUID id, DyeColor base, List<List<TownWork.Layer>> taken) {
		// (Mixed: ids side by side gave alike first rolls.)
		long seed = id.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ id.getLeastSignificantBits();
		seed = (seed ^ (seed >>> 31)) * 0xBF58476D1CE4E5B9L;
		Random random = new Random(seed ^ (seed >>> 29));
		random.nextLong();
		for (int attempt = 0; attempt < 50; attempt++) {
			List<TownWork.Layer> layers = new ArrayList<>();
			// Two colours besides its own, the charge never in the division's (it reads as a second thing on it).
			DyeColor first = fresh(random, taken, base);
			DyeColor second = fresh(random, taken, base, first);
			String division = DIVISIONS[random.nextInt(DIVISIONS.length)];
			layers.add(layer(division, first));
			String charge = CHARGES[random.nextInt(CHARGES.length)];
			// (Never in its own colour: on the half of the field still that colour, it vanished.)
			layers.add(layer(charge, second));
			if (random.nextDouble() < 0.45) {
				String finish = FINISHES[random.nextInt(FINISHES.length)];
				if (!finish.equals(charge) && !finish.equals(division)) {
					layers.add(layer(finish, random.nextBoolean() ? first : second));
				}
			}

			if (!taken.contains(layers)) {
				return layers;
			}
		}

		return List.of(layer("cross", other(random, base)), layer("border", other(random, base)));
	}

	private static TownWork.Layer layer(String pattern, DyeColor colour) {
		return new TownWork.Layer("minecraft:" + pattern, colour);
	}

	/** A dye that isn't any of these, and (if it can) one the other nations' banners use least: each its own look. */
	private static DyeColor fresh(Random random, List<List<TownWork.Layer>> taken, DyeColor... not) {
		java.util.Map<DyeColor, Integer> used = new java.util.EnumMap<>(DyeColor.class);
		taken.forEach(design -> design.forEach(layer -> used.merge(layer.colour(), 1, Integer::sum)));
		DyeColor best = other(random, not);
		for (int i = 0; i < 8; i++) {
			DyeColor dye = other(random, not);
			if (used.getOrDefault(dye, 0) < used.getOrDefault(best, 0)) {
				best = dye;
			}
		}

		return best;
	}

	/** A dye that isn't any of these. */
	private static DyeColor other(Random random, DyeColor... not) {
		for (int i = 0; i < 40; i++) {
			DyeColor dye = DYES[random.nextInt(DYES.length)];
			if (!java.util.Arrays.asList(not).contains(dye) && !(contrastless(dye, not[0]))) {
				return dye;
			}
		}

		return not[0] == DyeColor.WHITE ? DyeColor.RED : DyeColor.WHITE;
	}

	/** Two colours too alike to tell apart on a banner. */
	private static boolean contrastless(DyeColor a, DyeColor b) {
		return a == DyeColor.PINK && b == DyeColor.MAGENTA || a == DyeColor.MAGENTA && b == DyeColor.PINK || a == DyeColor.LIGHT_GRAY && b == DyeColor.WHITE
			|| a == DyeColor.WHITE && b == DyeColor.LIGHT_GRAY || a == DyeColor.PURPLE && b == DyeColor.MAGENTA || a == DyeColor.MAGENTA && b == DyeColor.PURPLE;
	}

	/** The banner as an item: its colour, its patterns on (unknown patterns left off). */
	static ItemStack stack(net.minecraft.core.RegistryAccess registries, DyeColor base, List<TownWork.Layer> layers) {
		ItemStack stack = new ItemStack(BannerBlock.byColor(base));
		var patterns = registries.lookupOrThrow(Registries.BANNER_PATTERN);
		BannerPatternLayers.Builder builder = new BannerPatternLayers.Builder();
		for (TownWork.Layer layer : layers) {
			ResourceLocation id = ResourceLocation.tryParse(layer.pattern());
			if (id != null) {
				patterns.get(net.minecraft.resources.ResourceKey.create(Registries.BANNER_PATTERN, id)).ifPresent(holder -> builder.add(holder, layer.colour()));
			}
		}

		stack.set(DataComponents.BANNER_PATTERNS, builder.build());
		return stack;
	}

	/** For the journal's pictures: "pattern.colour|pattern.colour". */
	static String words(List<TownWork.Layer> layers) {
		StringBuilder out = new StringBuilder();
		for (TownWork.Layer layer : layers) {
			out.append('|').append(layer.pattern()).append('.').append(layer.colour().getId());
		}

		return out.toString();
	}

	static ListTag save(List<TownWork.Layer> layers) {
		ListTag list = new ListTag();
		for (TownWork.Layer layer : layers) {
			CompoundTag one = new CompoundTag();
			one.putString("Pattern", layer.pattern());
			one.putString("Colour", layer.colour().getName());
			list.add(one);
		}

		return list;
	}

	static List<TownWork.Layer> load(ListTag list) {
		List<TownWork.Layer> layers = new ArrayList<>();
		for (Tag t : list) {
			CompoundTag one = (CompoundTag)t;
			layers.add(new TownWork.Layer(one.getString("Pattern"), DyeColor.byName(one.getString("Colour"), DyeColor.WHITE)));
		}

		return layers;
	}
}
