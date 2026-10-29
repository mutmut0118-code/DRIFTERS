package com.dinglebob.drifter.brain;

import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.entity.DrifterEntity;

/**
 * What dying would cost it (the user, 2026-10-28: "i want their behavior to change depending on the respawn setting; they
 * act too calm for a person that is never gonna spawn again"): 0 (back at its bed in a moment) to 1 (gone for good). It
 * pulls on its traits ({@code Personality#setStakes}: more Caution, less Bravery, Aggression and Curiosity), so every
 * choice that reads them gets warier (fights, running, the night, paths, caves); and on a few things directly (eating
 * sooner, chasing less, fewer fights it could skip). A reckless one still takes more chances than a careful one. Nothing
 * here makes it talk about it (the user: "every drifter knows they have one life left, they dont gotta say that").
 */
public final class Stakes {
	private Stakes() {
	}

	/** Back at its bed, every time. */
	public static final double RESPAWNS = 0.1;
	/** Limited lives: with all of them left, and with one more after this. */
	public static final double MANY_LIVES = 0.3;
	public static final double FEW_LIVES = 0.7;
	/** The next death is the last (permadeath, its last life, a soldier or a hunter in a game that ends with it). */
	public static final double FINAL = 1.0;

	public static double of(DrifterEntity drifter) {
		if (drifter.isTemporary() || com.dinglebob.drifter.world.Battle.inBattle(drifter)) {
			// (An encounter's or a battle's: it's out to do one thing; it doesn't come back either way.)
			return MANY_LIVES;
		}

		return switch (DrifterConfig.respawnMode()) {
			case BED -> RESPAWNS;
			case PERMADEATH -> FINAL;
			case LIVES -> {
				int lives = DrifterConfig.lives();
				int comebacks = lives - drifter.deaths();
				if (comebacks <= 0) {
					yield FINAL;
				}

				// All of them left: MANY_LIVES; one comeback left: FEW_LIVES.
				yield lives <= 1 ? FEW_LIVES : MANY_LIVES + (FEW_LIVES - MANY_LIVES) * (lives - comebacks) / (double)(lives - 1);
			}
		};
	}

	/**
	 * How far it is from the usual (the default, limited lives with all left, is where everything was tuned): below 0 when
	 * it comes back at its bed every time (a little more carefree), up to {@code FINAL - MANY_LIVES} when the next death is
	 * the last. What pulls on its traits.
	 */
	public static double pull(DrifterEntity drifter) {
		return of(drifter) - MANY_LIVES;
	}

	/** How much warier than usual, 0 (as tuned, or more carefree) to 1 (the next death is the last). */
	public static double wariness(DrifterEntity drifter) {
		return Math.max(0.0, Math.min(1.0, pull(drifter) / (FINAL - MANY_LIVES)));
	}

	/** One line for the debug commands. */
	public static String describe(DrifterEntity drifter) {
		String why = switch (DrifterConfig.respawnMode()) {
			case BED -> "comes back at its bed";
			case PERMADEATH -> "permadeath";
			case LIVES -> Math.max(0, DrifterConfig.lives() - drifter.deaths()) + " of " + DrifterConfig.lives() + " comebacks left";
		};
		return String.format(java.util.Locale.ROOT, "Stakes: %.2f (%s)", of(drifter), why);
	}
}
