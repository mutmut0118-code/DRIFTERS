package com.dinglebob.drifter.social;

import java.util.Locale;

import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.util.RandomSource;

/**
 * How a Drifter meets players (DESIGN.md section 14, "Disposition archetypes"), rolled once at birth from its personality
 * archetype (section 17 table). It shifts the stranger baseline and how Trust and Fear turn into behavior; the numbers
 * themselves come from what each player does.
 */
public enum Disposition {
	/** Avoids, never initiates; only defends itself, then flees. */
	PACIFIST(5.0),
	/** Watches, keeps its distance, goes about its work; turns when attacked, robbed, or its base is damaged. */
	WARY_NEUTRAL(0.0),
	/** Approaches, greets, may gift; turns only when seriously wronged. */
	FRIENDLY(10.0),
	/** Ignores players outside its base, warns them inside; turns on one that stays after a warning. */
	TERRITORIAL(-5.0),
	/** Neutral, but sizes players up; turns on one who is weak, distracted, or alone at night. */
	OPPORTUNIST(-5.0),
	/** Stalks players for loot; still telegraphs first contact. */
	BANDIT(-20.0),
	/** Neutral; any wrong is remembered for good. */
	GRUDGE_HOLDER(0.0);

	/** Added to the stranger baseline Trust. */
	public final double baselineShift;

	Disposition(double baselineShift) {
		this.baselineShift = baselineShift;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public String label() {
		return this.id().replace('_', ' ');
	}

	public static Disposition byId(String id) {
		for (Disposition disposition : values()) {
			if (disposition.id().equals(id)) {
				return disposition;
			}
		}

		return WARY_NEUTRAL;
	}

	/**
	 * Section 17's archetype table: "Friendly or Pacifist" picks one of the two at random. A very vengeful neutral is a
	 * Grudge-holder (the table's last archetype has no personality archetype of its own).
	 */
	public static Disposition roll(Archetype archetype, double vengefulness, RandomSource random) {
		boolean coin = random.nextBoolean();
		Disposition picked = switch (archetype) {
			case NEWBIE -> coin ? FRIENDLY : PACIFIST;
			case BUILDER -> coin ? WARY_NEUTRAL : TERRITORIAL;
			case HOMEBODY -> coin ? FRIENDLY : TERRITORIAL;
			case NOMAD -> coin ? PACIFIST : FRIENDLY;
			case HOARDER, HERMIT -> TERRITORIAL;
			case PVP_SWEAT -> OPPORTUNIST;
			case BANDIT -> BANDIT;
			case HELPER -> FRIENDLY;
			default -> WARY_NEUTRAL;
		};

		return picked == WARY_NEUTRAL && vengefulness >= GRUDGE_VENGEFULNESS ? GRUDGE_HOLDER : picked;
	}

	/** Vengefulness at which a neutral one never forgets. */
	public static final double GRUDGE_VENGEFULNESS = 0.8;
}
