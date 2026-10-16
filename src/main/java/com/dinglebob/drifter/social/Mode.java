package com.dinglebob.drifter.social;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.perception.Awareness;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;

/**
 * How a Drifter behaves toward one player right now (DESIGN.md section 14, "Behavior modes toward one player"). It
 * falls out of Trust and Fear, shifted by temperament: high Trust gives Friendly or Ally; middling gives Tolerating; low
 * Trust with low Fear gives Territorial, Predatory or Vengeful; low Trust with high Fear gives Avoiding.
 */
public enum Mode {
	/** Hasn't detected them. */
	UNAWARE,
	/** Detected: turns to look, pauses, then decides. */
	WATCHING,
	/** Keeps out of their way. */
	AVOIDING,
	/** Carries on working with an eye on them. */
	TOLERATING,
	/** Greets, may follow for a while, gives surplus, helps against mobs. */
	FRIENDLY,
	/** Fights alongside them, shares. */
	ALLY,
	/** The telegraph before any hostility toward someone who never wronged it. */
	WARNING,
	/** Fights back only while attacked. */
	DEFENSIVE,
	/** Drives them out of its base, stops at the boundary. */
	TERRITORIAL,
	/** Stalks and attacks for loot. */
	PREDATORY,
	/** Hunts that player. */
	VENGEFUL,
	/** Losing badly: runs. */
	FLEEING;

	/** Trust from which it's friendly, and an ally. */
	public static final double FRIENDLY_TRUST = 25.0;
	public static final double ALLY_TRUST = 60.0;
	/** Trust under which it's hostile (by disposition). */
	public static final double HOSTILE_TRUST = -25.0;
	/** A Bandit turns on strangers: hostile under this. */
	public static final double BANDIT_HOSTILE_TRUST = 0.0;
	/** Fear over this (plus up to 30 by Bravery) makes it avoid them rather than fight. */
	public static final double AVOID_FEAR = 50.0;
	/** How long it watches someone it has just noticed. */
	public static final int WATCH_TICKS = 50;
	/** A grudge this heavy makes a wronged one vengeful. */
	public static final double VENGEFUL_GRUDGE = 40.0;
	/** How far round home counts as "its base" for a territorial one. */
	public static final double BASE_RADIUS = 20.0;

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public boolean hostile() {
		return this == TERRITORIAL || this == PREDATORY || this == VENGEFUL;
	}

	/**
	 * Its mode toward this player now. Hostile modes come out as {@link #WARNING} while the telegraph rule still applies
	 * (they never wronged it and haven't ignored a warning).
	 */
	public static Mode toward(DrifterEntity drifter, Player player) {
		Awareness awareness = drifter.getSenses().awarenessOf(player);
		if (awareness == null || !awareness.identified) {
			return UNAWARE;
		}

		Relationships social = drifter.getSocial();
		Relationship r = social.getOrMeet(player.getUUID(), player.getGameProfile().getName());
		// Being hit by them right now: it fights back (and runs if it's losing; the threat assessment decides that).
		if (drifter.getLastHurtByMob() == player && drifter.tickCount - drifter.getLastHurtByMobTimestamp() < 20 * 5) {
			return DEFENSIVE;
		}

		// A manhunt: toward the runner, vengeful for the whole game (both agreed: no warning first).
		if (Manhunt.hunting(drifter, player)) {
			return VENGEFUL;
		}

		if (social.watcher().watchingSince(player) >= 0) {
			return WATCHING;
		}

		Mode wanted = wanted(drifter, player, r);
		// Section 14: on Peaceful, never hostile. Nor with aggression toward players set to 0 (user's setting: it still
		// fights back when hit, above).
		int aggression = com.dinglebob.drifter.DrifterConfig.playerAggression();
		if (wanted.hostile() && (drifter.level().getDifficulty() == Difficulty.PEACEFUL || aggression <= 0)) {
			return TOLERATING;
		}

		// Aggression at 100: hostile no matter what, no warning first (user's setting).
		if (aggression >= 100 && drifter.level().getDifficulty() != Difficulty.PEACEFUL) {
			return PREDATORY;
		}

		// The telegraph rule: no hostility toward someone who never wronged it, without a warning they ignored.
		if (wanted.hostile() && !r.wronged && !social.watcher().warningIgnored(player)) {
			return WARNING;
		}

		return wanted;
	}

	private static Mode wanted(DrifterEntity drifter, Player player, Relationship r) {
		Disposition disposition = drifter.getSocial().disposition();
		double bravery = drifter.getPersonality().get(Trait.BRAVERY);
		boolean afraid = r.fear > AVOID_FEAR + 30.0 * bravery;
		Home home = drifter.getDrifterBrain().home();
		boolean inItsBase = home != null && home.center.closerThan(player.blockPosition(), BASE_RADIUS);
		if (r.trust >= ALLY_TRUST) {
			return ALLY;
		}

		if (r.trust >= FRIENDLY_TRUST) {
			return FRIENDLY;
		}

		// A Territorial one warns anyone it doesn't like inside its base (a hermit away from home keeps clear).
		if (disposition == Disposition.TERRITORIAL) {
			if (inItsBase && r.trust < FRIENDLY_TRUST && !afraid) {
				return TERRITORIAL;
			}

			if (drifter.getPersonality().get(Trait.SOCIABILITY) < 0.3 && r.trust < 0.0) {
				return AVOIDING;
			}
		}

		if (disposition == Disposition.BANDIT && r.trust < BANDIT_HOSTILE_TRUST) {
			return afraid ? AVOIDING : PREDATORY;
		}

		if (r.trust >= HOSTILE_TRUST) {
			return disposition == Disposition.PACIFIST && afraid ? AVOIDING : TOLERATING;
		}

		// Low Trust.
		if (afraid || disposition == Disposition.PACIFIST) {
			return AVOIDING;
		}

		if (disposition == Disposition.OPPORTUNIST) {
			return PREDATORY;
		}

		if (r.wronged && (r.grudgeWeight() >= VENGEFUL_GRUDGE || disposition == Disposition.GRUDGE_HOLDER)) {
			return VENGEFUL;
		}

		return inItsBase ? TERRITORIAL : TOLERATING;
	}

	/** The player its mode matters most for right now (the nearest one it can see), or null. */
	@Nullable
	public static Player mostPressing(DrifterEntity drifter) {
		Player best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Awareness awareness : drifter.getSenses().tracked()) {
			if (awareness.entity instanceof Player player && SocialWatcher.isRealPlayer(player) && awareness.identified && awareness.visibleNow) {
				double distance = player.distanceTo(drifter);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = player;
				}
			}
		}

		return best;
	}
}
