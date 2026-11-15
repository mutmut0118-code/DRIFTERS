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
	/**
	 * The dyes a design may use: from common flowers and bone meal, purple (red and blue), and (the user, 2026-11-15: "add
	 * black and green") black, from ink sacs or wither roses, and green, cactus smelted.
	 */
	private static final DyeColor[] DYES = {DyeColor.WHITE, DyeColor.RED, DyeColor.YELLOW, DyeColor.BLUE, DyeColor.ORANGE, DyeColor.LIGHT_GRAY,
		DyeColor.PINK, DyeColor.MAGENTA, DyeColor.PURPLE, DyeColor.LIGHT_BLUE, DyeColor.BLACK, DyeColor.GREEN};
	/**
	 * The light tinctures, heraldry's metals: gold and silver (yellow, white, light grey) and the pale dyes as light as them
	 * (pink, light blue). The rest, its colours, are dark. Never light on light nor dark on dark.
	 */
	private static final java.util.Set<DyeColor> METALS = java.util.EnumSet.of(DyeColor.WHITE, DyeColor.YELLOW, DyeColor.LIGHT_GRAY, DyeColor.PINK,
		DyeColor.LIGHT_BLUE);
	/** A big shape this much lighter or darker than what it lies on (0 to 1, by the dyes' own colours), at least. */
	private static final double CONTRAST = 0.25;
	/** A charge against the division it partly lies on, at least. */
	private static final double CONTRAST_TO_DIVISION = 0.12;

	/** How a nation came about, for its colours: a hard one's reds and blacks, a peaceable one's blues, whites and greens. */
	enum Temper {
		HARD, PEACEABLE, PLAIN;

		static Temper of(String name) {
			String n = name.toLowerCase(java.util.Locale.ROOT);
			if (n.contains("dominion") || n.contains("realm") || n.contains("crown")) {
				return HARD;
			}

			if (n.contains("league") || n.contains("accord") || n.contains("union") || n.contains("commonwealth") || n.contains("free cities")
				|| n.contains("republic") || n.contains("confederacy")) {
				return PEACEABLE;
			}

			return PLAIN;
		}

		double likes(DyeColor dye) {
			return switch (this) {
				case HARD -> dye == DyeColor.RED || dye == DyeColor.BLACK || dye == DyeColor.ORANGE || dye == DyeColor.PURPLE ? 1.0 : 0.0;
				case PEACEABLE -> dye == DyeColor.BLUE || dye == DyeColor.WHITE || dye == DyeColor.GREEN || dye == DyeColor.LIGHT_BLUE ? 1.0 : 0.0;
				case PLAIN -> 0.4;
			};
		}
	}

	/**
	 * A design for a nation of this colour, its own (not one of {@code taken}), from its id, coherent by colour theory (the
	 * user, 2026-11-15: "how can we make it coherent with color theory", then "go with your recommendation"):
	 * <ul>
	 *   <li>heraldry's rule of tincture: never metal on metal nor colour on colour (a blue field takes a yellow or white
	 *       cross, a white field red or blue stripes), and every big shape well lighter or darker than what it's on;</li>
	 *   <li>the colours in harmony: complementary, analogous or triadic on the colour wheel (white, grey and black go with
	 *       anything);</li>
	 *   <li>by its temper (its name: a Dominion's reds and blacks, a League's blues and whites);</li>
	 *   <li>three colours at most, those the other nations use least where it's a close call.</li>
	 * </ul>
	 */
	static List<TownWork.Layer> design(UUID id, DyeColor base, String name, List<List<TownWork.Layer>> taken) {
		// (Mixed: ids side by side gave alike first rolls.)
		long seed = id.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ id.getLeastSignificantBits();
		seed = (seed ^ (seed >>> 31)) * 0xBF58476D1CE4E5B9L;
		Random random = new Random(seed ^ (seed >>> 29));
		random.nextLong();
		Temper temper = Temper.of(name);
		java.util.Map<DyeColor, Integer> used = new java.util.EnumMap<>(DyeColor.class);
		taken.forEach(design -> design.forEach(layer -> used.merge(layer.colour(), 1, Integer::sum)));

		// Every pair of colours (the division's, the charge's) that keeps the rules, scored for harmony and temper.
		List<DyeColor[]> pairs = new ArrayList<>();
		List<Double> scores = new ArrayList<>();
		for (DyeColor first : DYES) {
			if (!onIt(first, base, CONTRAST)) {
				continue;
			}

			for (DyeColor second : DYES) {
				if (second == first || !onIt(second, base, CONTRAST) || contrast(second, first) < CONTRAST_TO_DIVISION) {
					continue;
				}

				double harmony = harmony(base, first, second);
				if (harmony <= 0) {
					continue;
				}

				double score = harmony + 0.6 * (temper.likes(first) + temper.likes(second)) / 2.0
					- 0.15 * (used.getOrDefault(first, 0) + used.getOrDefault(second, 0)) + 0.25 * random.nextDouble();
				pairs.add(new DyeColor[] {first, second});
				scores.add(score);
			}
		}

		List<Integer> order = new ArrayList<>();
		for (int k = 0; k < pairs.size(); k++) {
			order.add(k);
		}

		order.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
		for (int attempt = 0; attempt < 50 && !pairs.isEmpty(); attempt++) {
			// One of the best few, by chance.
			DyeColor[] pair = pairs.get(order.get(random.nextInt(Math.min(4, order.size()))));
			List<TownWork.Layer> layers = new ArrayList<>();
			String division = DIVISIONS[random.nextInt(DIVISIONS.length)];
			layers.add(layer(division, pair[0]));
			String charge = CHARGES[random.nextInt(CHARGES.length)];
			layers.add(layer(charge, pair[1]));
			// A border or band now and then, in the division's colour (it lies on the field: the rule holds).
			if (random.nextDouble() < 0.45) {
				String finish = FINISHES[random.nextInt(FINISHES.length)];
				if (!finish.equals(charge) && !finish.equals(division)) {
					layers.add(layer(finish, pair[0]));
				}
			}

			if (!taken.contains(layers)) {
				return layers;
			}
		}

		DyeColor metal = metal(base) ? DyeColor.RED : DyeColor.WHITE;
		return List.of(layer("cross", metal), layer("border", metal));
	}

	private static TownWork.Layer layer(String pattern, DyeColor colour) {
		return new TownWork.Layer("minecraft:" + pattern, colour);
	}

	private static boolean metal(DyeColor dye) {
		return METALS.contains(dye);
	}

	/** Fit to lie on {@code under}: metal on colour or colour on metal, and that much lighter or darker. */
	private static boolean onIt(DyeColor over, DyeColor under, double least) {
		return metal(over) != metal(under) && contrast(over, under) >= least;
	}

	/** How much lighter or darker one dye is than another (0 to 1), by the dyes' own colours. */
	static double contrast(DyeColor a, DyeColor b) {
		return Math.abs(lightness(a) - lightness(b));
	}

	private static double lightness(DyeColor dye) {
		int rgb = dye.getTextureDiffuseColor();
		double r = (rgb >> 16 & 255) / 255.0;
		double g = (rgb >> 8 & 255) / 255.0;
		double b = (rgb & 255) / 255.0;
		return 0.2126 * r + 0.7152 * g + 0.0722 * b;
	}

	/** Its hue on the colour wheel (0 to 360), or -1 for white, greys and black (no hue: they go with anything). */
	private static double hue(DyeColor dye) {
		int rgb = dye.getTextureDiffuseColor();
		double r = (rgb >> 16 & 255) / 255.0;
		double g = (rgb >> 8 & 255) / 255.0;
		double b = (rgb & 255) / 255.0;
		double max = Math.max(r, Math.max(g, b));
		double min = Math.min(r, Math.min(g, b));
		if (max - min < 0.18) {
			return -1;
		}

		double h = max == r ? (g - b) / (max - min) : max == g ? 2 + (b - r) / (max - min) : 4 + (r - g) / (max - min);
		return (h * 60 + 360) % 360;
	}

	/**
	 * How well three colours go together (0: they don't): each pair of hues analogous (within 40 degrees), complementary
	 * (about 180 apart) or a third of the way round (about 120); white, grey and black go with anything. One or two hues reads
	 * cleanest, three is bolder.
	 */
	static double harmony(DyeColor... dyes) {
		List<Double> hues = new ArrayList<>();
		for (DyeColor dye : dyes) {
			double h = hue(dye);
			if (h >= 0) {
				hues.add(h);
			}
		}

		double score = 1.0;
		for (int i = 0; i < hues.size(); i++) {
			for (int k = i + 1; k < hues.size(); k++) {
				double d = Math.abs(hues.get(i) - hues.get(k));
				d = Math.min(d, 360 - d);
				double fit = d <= 40 ? 1.0 : Math.abs(d - 180) <= 35 ? 1.2 : Math.abs(d - 120) <= 25 ? 0.9 : 0.0;
				if (fit == 0) {
					return 0;
				}

				score = Math.min(score, fit);
			}
		}

		return score + (hues.size() <= 2 ? 0.2 : 0.0);
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
