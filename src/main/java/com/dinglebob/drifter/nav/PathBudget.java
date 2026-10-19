package com.dinglebob.drifter.nav;

import net.minecraft.world.level.Level;

/**
 * Shared per-tick pathfinding budget (DESIGN.md section 12, "Performance", and section 22 "Budget"): all Drifters
 * together search for at most {@link #NANOS_PER_TICK} of each server tick, and one search for at most
 * {@link #MAX_NANOS_PER_SEARCH}; searches resume next tick. Measured in time, not nodes: a node's cost varies a lot with
 * the terrain, and 1,500 nodes in one go made 10 to 70 ms ticks in a 10-Drifter test.
 */
public final class PathBudget {
	/**
	 * (3 ms until 2026-10-17; the search got about half again faster that day, so 2 ms covers the same ground and leaves
	 * the server a millisecond a tick.)
	 */
	public static final long NANOS_PER_TICK = 2_000_000L;
	public static final long MAX_NANOS_PER_SEARCH = 1_500_000L;
	/** Nodes per search per tick, whatever the clock says (the old cap). */
	public static final int MAX_NODES_PER_SEARCH_PER_TICK = 1500;
	/** A search skipped for {@link #MAX_SKIPPED_TICKS} ticks gets this many nodes anyway, so the last in line never starves. */
	public static final int MIN_NODES = 40;
	/** Ticks a search may be skipped for lack of time before it takes its {@link #MIN_NODES} anyway. */
	public static final int MAX_SKIPPED_TICKS = 20;

	private static long stampTick = -1;
	private static long used;

	private PathBudget() {
	}

	/** Time kept back each tick for Drifters near a player (far ones don't get it). */
	public static final long NEAR_RESERVE_NANOS = 700_000L;
	/**
	 * The time this search may take now: Drifters near a player first (the ones you can see react without a pause; far
	 * ones get what's left after a share kept back for the near ones). Performance, 2026-10-17.
	 */
	public static long take(Level level, com.dinglebob.drifter.entity.DetailTier tier) {
		long all = take(level);
		if (tier == com.dinglebob.drifter.entity.DetailTier.FULL) {
			return all;
		}

		return Math.max(0L, all - NEAR_RESERVE_NANOS);
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
		totalNanos.add(nanos);
	}

	/**
	 * Nodes expanded and time spent searching since the last report (for `/drifter perf`: how fast it searches). Counted
	 * from path threads too (milestone 16), so these add up safely from any thread.
	 */
	private static final java.util.concurrent.atomic.LongAdder totalNanos = new java.util.concurrent.atomic.LongAdder();
	private static final java.util.concurrent.atomic.LongAdder totalNodes = new java.util.concurrent.atomic.LongAdder();
	private static final java.util.concurrent.atomic.LongAdder workerNanos = new java.util.concurrent.atomic.LongAdder();
	private static final java.util.concurrent.atomic.LongAdder copyNanos = new java.util.concurrent.atomic.LongAdder();

	public static void expanded(int nodes) {
		totalNodes.add(nodes);
	}

	/** A search on a path thread took this long, over this many cells. */
	public static void workerSpent(long nanos, int nodes) {
		totalNanos.add(nanos);
		workerNanos.add(nanos);
		totalNodes.add(nodes);
	}

	/** Copying the world and its knowledge for a search took this long (server thread). */
	public static void copied(long nanos) {
		copyNanos.add(nanos);
	}

	private static final java.util.concurrent.atomic.LongAdder searches = new java.util.concurrent.atomic.LongAdder();
	private static final java.util.concurrent.atomic.LongAdder searchNodes = new java.util.concurrent.atomic.LongAdder();

	/** A search finished, after looking at this many cells. */
	public static void finished(int nodes) {
		searches.increment();
		searchNodes.add(nodes);
	}

	/** "N nodes/ms" since the last call, then starts counting again. */
	public static String speedReport() {
		long nanos = totalNanos.sumThenReset();
		long nodes = totalNodes.sumThenReset();
		long count = searches.sumThenReset();
		long counted = searchNodes.sumThenReset();
		long worker = workerNanos.sumThenReset();
		long copy = copyNanos.sumThenReset();
		String report = nanos == 0 ? "no searches" : String.format(java.util.Locale.ROOT, "%.0f nodes/ms (%d nodes), %d searches, %.0f nodes each",
			nodes / (nanos / 1e6), nodes, count, count == 0 ? 0.0 : (double)counted / count);
		if (worker > 0 || copy > 0) {
			report += String.format(java.util.Locale.ROOT, "; on path threads %.0f ms (%d waiting), copying for them %.1f ms on the server thread",
				worker / 1e6, PathWorkers.waiting(), copy / 1e6);
		}

		return report;
	}
}
