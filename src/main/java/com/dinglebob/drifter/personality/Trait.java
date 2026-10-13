package com.dinglebob.drifter.personality;

import java.util.Locale;

/**
 * Personality traits (DESIGN.md section 17, "Traits"): continuous numbers from 0 to 1 that tilt every decision.
 * Saved by name, so new traits can be added at the end without breaking old saves.
 */
public enum Trait {
	/** Fighting readiness; how fast low Trust becomes attacking (Trust: milestone 9). */
	AGGRESSION,
	/** How much fear suppresses fighting; the flee threshold. */
	BRAVERY,
	/** Risk costs in planning and pathing; torch density; going home early. */
	CAUTION,
	/** Waiting vs leaving; gathering everything before building. */
	PATIENCE,
	/** Cleanup, sorting, closing doors, replanting. */
	TIDINESS,
	/** Exploration weight; investigating sounds; following players. */
	CURIOSITY,
	/** Stranger baseline Trust; greetings; gifting (milestone 9). */
	SOCIABILITY,
	/** Valuables appetite; looting. */
	GREED,
	/** Grudge weight and duration (milestone 9). */
	VENGEFULNESS,
	/** Trust recovery rate (milestone 9). */
	FORGIVENESS,
	/** Work vs idle time. */
	INDUSTRIOUSNESS,
	/** Build complexity and decoration (milestone 8). */
	CREATIVITY,
	/** How deterministic its choices are; batching chores; how quick it is in menus. */
	EFFICIENCY,
	/** Resource cost weight; tool conservation. */
	THRIFT,
	/** Mannerism frequency. */
	FIDGET,
	/** Mechanical Skill: aim, timing, crits, clutches, reaction time. */
	SKILL,
	/** Recipes and tricks it knows (section 8). */
	KNOWLEDGE;

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
