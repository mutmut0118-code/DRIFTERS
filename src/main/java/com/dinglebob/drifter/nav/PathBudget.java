package com.dinglebob.drifter.nav;

import net.minecraft.world.level.Level;

/**
 * Shared per-tick pathfinding budget (DESIGN.md section 12, "Performance"): all Drifters together expand at most
 * {@link #NODES_PER_TICK} search nodes per server tick; searches resume next tick.
 */
public final class PathBudget {
	public static final int NODES_PER_TICK = 3000;
	public static final int MAX_PER_SEARCH_PER_TICK = 1500;

	private static long stampTick = -1;
	private static int used;

	private PathBudget() {
	}

	public static int take(Level level) {
		long tick = level.getGameTime();
		if (tick != stampTick) {
			stampTick = tick;
			used = 0;
		}

		int granted = Math.min(MAX_PER_SEARCH_PER_TICK, NODES_PER_TICK - used);
		used += Math.max(granted, 0);
		return Math.max(granted, 0);
	}
}
