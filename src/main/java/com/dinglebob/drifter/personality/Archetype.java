package com.dinglebob.drifter.personality;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.util.RandomSource;

/**
 * Archetypes (DESIGN.md section 17): a preset of trait values and a spawn weight. Traits not named in a preset sit in
 * the middle (0.5). Disposition (section 14) comes with milestone 9.
 * TODO(section 25): move weights and presets to a data file (and the config's archetype weights, section 23).
 */
public enum Archetype {
	CASUAL(25, Map.of()),
	NEWBIE(10, Map.of(Trait.SKILL, 0.2, Trait.KNOWLEDGE, 0.2, Trait.FIDGET, 0.8, Trait.CURIOSITY, 0.8, Trait.EFFICIENCY, 0.35,
		Trait.CAUTION, 0.4)),
	BUILDER(12, Map.of(Trait.CREATIVITY, 0.85, Trait.TIDINESS, 0.8, Trait.PATIENCE, 0.8, Trait.INDUSTRIOUSNESS, 0.6)),
	MINER(10, Map.of(Trait.INDUSTRIOUSNESS, 0.85, Trait.GREED, 0.75, Trait.CREATIVITY, 0.25, Trait.PATIENCE, 0.65)),
	HOMEBODY(10, Map.of(Trait.TIDINESS, 0.8, Trait.CAUTION, 0.8, Trait.CURIOSITY, 0.25, Trait.BRAVERY, 0.35, Trait.PATIENCE, 0.7)),
	SPEEDRUNNER(5, Map.of(Trait.SKILL, 0.85, Trait.EFFICIENCY, 0.9, Trait.BRAVERY, 0.8, Trait.TIDINESS, 0.15, Trait.PATIENCE, 0.2,
		Trait.CAUTION, 0.3, Trait.KNOWLEDGE, 0.8)),
	NOMAD(7, Map.of(Trait.CURIOSITY, 0.9, Trait.TIDINESS, 0.2, Trait.PATIENCE, 0.35)),
	HOARDER(5, Map.of(Trait.GREED, 0.95, Trait.THRIFT, 0.15, Trait.TIDINESS, 0.4)),
	HERMIT(5, Map.of(Trait.SOCIABILITY, 0.1, Trait.CAUTION, 0.8, Trait.CURIOSITY, 0.35)),
	PVP_SWEAT(4, Map.of(Trait.SKILL, 0.95, Trait.AGGRESSION, 0.9, Trait.BRAVERY, 0.8, Trait.EFFICIENCY, 0.7, Trait.KNOWLEDGE, 0.75)),
	BANDIT(4, Map.of(Trait.GREED, 0.8, Trait.AGGRESSION, 0.8, Trait.TIDINESS, 0.2, Trait.FORGIVENESS, 0.2, Trait.BRAVERY, 0.65)),
	HELPER(3, Map.of(Trait.SOCIABILITY, 0.9, Trait.FORGIVENESS, 0.9, Trait.AGGRESSION, 0.2));

	/** Section 17: traits are the preset plus noise of about 0.1 to 0.15. */
	public static final double TRAIT_SPREAD = 0.13;

	public final int weight;
	private final Map<Trait, Double> preset;

	Archetype(int weight, Map<Trait, Double> preset) {
		this.weight = weight;
		this.preset = preset;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public String displayName() {
		return switch (this) {
			case PVP_SWEAT -> "PvP Sweat";
			case HOMEBODY -> "Homebody";
			default -> this.name().charAt(0) + this.name().substring(1).toLowerCase(Locale.ROOT);
		};
	}

	public double preset(Trait trait) {
		return this.preset.getOrDefault(trait, 0.5);
	}

	/** A weighted pick by the section 17 spawn weights. */
	public static Archetype pick(RandomSource random) {
		int total = 0;
		for (Archetype archetype : values()) {
			total += archetype.weight;
		}

		int roll = random.nextInt(total);
		for (Archetype archetype : values()) {
			roll -= archetype.weight;
			if (roll < 0) {
				return archetype;
			}
		}

		return CASUAL;
	}

	/** Trait values for a new Drifter of this archetype: the preset plus noise, clamped. */
	public EnumMap<Trait, Double> rollTraits(RandomSource random) {
		EnumMap<Trait, Double> traits = new EnumMap<>(Trait.class);
		for (Trait trait : Trait.values()) {
			double noise = (random.nextDouble() * 2.0 - 1.0) * TRAIT_SPREAD;
			traits.put(trait, clamp(this.preset(trait) + noise));
		}

		// Section 17: some traits are pulled against each other so combinations stay plausible. Very cautious and very
		// brave at once doesn't happen: the excess is taken off both.
		double excess = traits.get(Trait.CAUTION) + traits.get(Trait.BRAVERY) - 1.3;
		if (excess > 0.0) {
			traits.put(Trait.CAUTION, clamp(traits.get(Trait.CAUTION) - excess / 2.0));
			traits.put(Trait.BRAVERY, clamp(traits.get(Trait.BRAVERY) - excess / 2.0));
		}

		return traits;
	}

	static double clamp(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}

	public static Archetype byId(String id) {
		for (Archetype archetype : values()) {
			if (archetype.id().equals(id)) {
				return archetype;
			}
		}

		return CASUAL;
	}
}
