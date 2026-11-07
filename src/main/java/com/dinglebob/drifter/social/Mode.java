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
	/** In a town: its house, and this many blocks round it. */
	public static final int IN_TOWN_MARGIN = 2;
	/** Someone wanted in its town is gone after this far past the town's edge. */
	public static final int WANTED_REACH = 48;
	/** Under this Bravery a townsperson (not a guard) keeps clear of someone wanted rather than going for them. */
	public static final double TIMID = 0.35;

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
		// A friendly 1v1 they asked for in chat and it took (chat phase 2): it fights them, to a few hearts; just over, their
		// last swing isn't taken as an attack.
		if (com.dinglebob.drifter.social.talk.Spar.on(drifter, player)) {
			return VENGEFUL;
		}

		if (com.dinglebob.drifter.social.talk.Spar.calming(drifter, player)) {
			return TOLERATING;
		}

		// Exiled from its town (step 5 of milestone 22: a law broken, the fine not paid, or a killing): in the town, it drives
		// them out, as from its own base.
		var town = drifter.getTownWork();
		String standing = town == null ? null : town.standing(drifter, player);
		double fromTown = town == null ? Double.MAX_VALUE : player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(town.center()));
		boolean mayFight = drifter.level().getDifficulty() != Difficulty.PEACEFUL && com.dinglebob.drifter.DrifterConfig.playerAggression() > 0;
		// Wanted there (a killing seen, a massacre, an arrest resisted; the user, 2026-11-07: "enforce and up crime awareness
		// and behavior"): about the town, its guards and its bold go for them on sight; the rest keep well clear.
		if ("wanted".equals(standing) && fromTown <= town.radius() + WANTED_REACH && mayFight) {
			r.wronged = true;
			return timid(drifter) ? AVOIDING : VENGEFUL;
		}

		if (("exiled".equals(standing) || "wanted".equals(standing)) && fromTown <= town.radius() + 8 && mayFight) {
			r.wronged = true;
			return TERRITORIAL;
		}

		// Being hit by them right now: it fights back (and runs if it's losing; the threat assessment decides that).
		if (drifter.getLastHurtByMob() == player && drifter.tickCount - drifter.getLastHurtByMobTimestamp() < 20 * 5) {
			return DEFENSIVE;
		}

		// A manhunt: toward the runner, vengeful for the whole game (both agreed: no warning first).
		if (Manhunt.hunting(drifter, player)) {
			return VENGEFUL;
		}

		// An encounter that came for them (bounty hunters, an ambush): no warning first, that's the point of it. Still never
		// on Peaceful or with aggression to players at 0.
		if (drifter.encounter() != null && drifter.encounter().hostileTo(drifter, player) && drifter.level().getDifficulty() != Difficulty.PEACEFUL
			&& com.dinglebob.drifter.DrifterConfig.playerAggression() > 0) {
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

	/** Not one to go after someone wanted: no guard, and a pacifist or not brave enough. */
	private static boolean timid(DrifterEntity drifter) {
		var town = drifter.getTownWork();
		return (town == null || !town.guard(drifter)) && (drifter.getSocial().disposition() == Disposition.PACIFIST
			|| drifter.getPersonality().get(Trait.BRAVERY) < TIMID);
	}

	/**
	 * It goes after this player wherever about its town they are (not only in sight): they're wanted there, they're about
	 * the town, and it's a guard or bold enough (the alarm tells it where they were; the user, 2026-11-07, "enforce").
	 */
	public static boolean pursuesWanted(DrifterEntity drifter, Player player) {
		var town = drifter.getTownWork();
		return town != null && "wanted".equals(town.standing(drifter, player)) && !timid(drifter)
			&& player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(town.center())) <= town.radius() + WANTED_REACH
			&& drifter.level().getDifficulty() != Difficulty.PEACEFUL && com.dinglebob.drifter.DrifterConfig.playerAggression() > 0;
	}

	/**
	 * At odds with this player, whatever its mode says this moment: no friendly words for them (no "hi stranger", "name's
	 * Ann", "welcome back!", "hey!! no hitting"). Found by the user: a Bandit out to kill them introduced itself mid-hunt,
	 * and greeted them when they logged back in (still looking them over, or not seen yet, its mode wasn't hostile).
	 * Hostile, warning, keeping clear or running from them; trust low; a kill-on-sight Bandit; after them or fighting
	 * them; hit by them in the last half-minute; an encounter that came for them.
	 */
	public static boolean atOdds(DrifterEntity drifter, Player player) {
		Mode mode = toward(drifter, player);
		if (mode.hostile() || mode == WARNING || mode == AVOIDING || mode == FLEEING || mode == DEFENSIVE) {
			return true;
		}

		Relationship r = drifter.getSocial().get(player.getUUID());
		if (r != null && r.trust < HOSTILE_TRUST) {
			return true;
		}

		var pursuit = drifter.getDrifterBrain().pursuit();
		return BanditGoals.ruthless(drifter) && BanditGoals.prey(drifter, player) || pursuit != null && pursuit.target == player
			|| drifter.getTarget() == player || drifter.getLastHurtByMob() == player && drifter.tickCount - drifter.getLastHurtByMobTimestamp() < 20 * 30
			|| drifter.encounter() != null && drifter.encounter().hostileTo(drifter, player);
	}

	private static Mode wanted(DrifterEntity drifter, Player player, Relationship r) {
		Disposition disposition = drifter.getSocial().disposition();
		double bravery = drifter.getPersonality().get(Trait.BRAVERY);
		boolean afraid = r.fear > AVOID_FEAR + 30.0 * bravery;
		Home home = drifter.getDrifterBrain().home();
		// In a town its base is its own house (found by the user: a territorial townsperson warned them off, then chased them,
		// for walking down the street past its house: twenty blocks round its house took in the streets and the plaza).
		boolean inItsBase = home != null && (drifter.getTownWork() != null && home.planBox() != null ? home.inOwnBuilding(player.blockPosition(), IN_TOWN_MARGIN)
			: home.center.closerThan(player.blockPosition(), BASE_RADIUS));
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
