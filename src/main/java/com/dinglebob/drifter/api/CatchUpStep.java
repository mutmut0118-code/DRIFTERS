package com.dinglebob.drifter.api;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.util.RandomSource;

/**
 * A step another mod adds to catch-up (DESIGN.md section 22: a Drifter back after its area was unloaded works through the
 * time it missed, a slice at a time). Run for each slice, after Drifter's own steps.
 */
@FunctionalInterface
public interface CatchUpStep {
	/**
	 * One slice of the time it was away: {@code from} is the game time the slice began, {@code ticks} how long it is.
	 * Keep it quick (a thousand Drifters may catch up at once): no world searches, no route-finding.
	 */
	void slice(DrifterEntity drifter, long from, long ticks, RandomSource random);
}
