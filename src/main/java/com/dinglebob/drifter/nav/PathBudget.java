package com.dinglebob.drifter.nav;

import net.minecraft.world.level.Level;

/**
 * Shared per-tick pathfinding budget (DESIGN.md section 12, "Performance", and section 22 "Budget"): all Drifters
 * together search for at most {@link #NANOS_PER_TICK} of each server tick, and one search for at most
 * {@link #MAX_NANOS_PER_SEARCH}; searches resume next tick. Measured in time, not nodes: a node's cost varies a lot with
 * the terrain, and 1,500 nodes in one go made 10 to 70 ms ticks in a 10-Drifter test.
 */
public final class PathBudget {
	public static final long NANOS_PER_TICK = 3_000_000L;
	public static final long MAX_NANOS_PER_SEARCH = 1_500_000L;
	/** Nodes per search per tick, whatever the clock says (the old cap). */
	public static final int MAX_NODES_PER_SEARCH_PER_TICK = 1500;
	/** Even with the tick's time used up, every search gets this many nodes, so the last Drifter in line never starves. */
	public static final int MIN_NODES = 40;

	private static long stampTick = -1;
	private static long used;

	private PathBudget() {
	}

	/** The time this search may take now (nanoseconds); 0 means only {@link #MIN_NODES}. */
	public static long take(Level level) {
		long tick = level.getGameTime();
		if (tick != stampTick) {
			stampTick = tick;
			used = 0;
		}

		return Math.max(0L, Math.min(MAX_NANOS_PER_SEARCH, NANOS_PER_TICK - used));
	}

	/** What a search took (counted against the tick's budget). */
	public static void spent(long nanos) {
		used += nanos;
	}
}
