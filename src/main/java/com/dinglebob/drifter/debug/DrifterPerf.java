package com.dinglebob.drifter.debug;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Per-system timing for all Drifters together (DESIGN.md section 22 "Budget": about 3 to 5 ms per server tick for up
 * to 10 Drifters; "every system reports its own timing"). Averages over the last 5 seconds; `/drifter perf` prints it.
 */
public final class DrifterPerf {
	private DrifterPerf() {
	}

	public enum Part {
		TOTAL, SENSES, BRAIN, TASKS, TICS, SOCIAL, TRACE,
		/** Parts of a brain decision: the known-world scan, choosing the next target (planning), scoring the goals. */
		BRAIN_WORLD, BRAIN_TARGET, BRAIN_SCORE,
		/** Path searches (part of tasks). */
		PATH
	}

	private static final int WINDOW_TICKS = 100;

	private static final EnumMap<Part, Long> current = new EnumMap<>(Part.class);
	private static final EnumMap<Part, Long> peakTick = new EnumMap<>(Part.class);
	private static Map<Part, Double> lastAverage = new EnumMap<>(Part.class);
	private static long lastPeakTotal;
	private static int ticks;
	private static long tickTotal;
	private static int drifters;
	private static int lastDrifters;

	/** The slowest single call of each part this window and the one before, with what that Drifter was doing. */
	private record Worst(long nanos, String what) {
	}

	private static final EnumMap<Part, Worst> worst = new EnumMap<>(Part.class);
	private static Map<Part, Worst> lastWorst = new EnumMap<>(Part.class);

	/** As {@link #add(Part, long)}, remembering what it was doing if it's the slowest call of the window. */
	public static void add(Part part, long nanos, Supplier<String> what) {
		add(part, nanos);
		Worst before = worst.get(part);
		if (before == null || nanos > before.nanos()) {
			worst.put(part, new Worst(nanos, what.get()));
		}
	}

	public static void add(Part part, long nanos) {
		current.merge(part, nanos, Long::sum);
		if (part == Part.TOTAL) {
			tickTotal += nanos;
			drifters++;
		}
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			lastPeakTotal = Math.max(lastPeakTotal, tickTotal);
			lastDrifters = Math.max(lastDrifters, drifters);
			tickTotal = 0;
			drifters = 0;
			if (++ticks >= WINDOW_TICKS) {
				Map<Part, Double> average = new EnumMap<>(Part.class);
				current.forEach((part, nanos) -> average.put(part, nanos / (double)ticks / 1_000_000.0));
				lastAverage = average;
				peakTick.put(Part.TOTAL, lastPeakTotal);
				lastWorst = new EnumMap<>(worst);
				worst.clear();
				current.clear();
				ticks = 0;
				lastPeakTotal = 0;
			}
		});
	}

	/** The last window's averages, one line. */
	public static String report() {
		if (lastAverage.isEmpty()) {
			return "No timing yet (give it 5 seconds)";
		}

		StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "Drifters (up to %d loaded): %.2f ms per server tick on average, peak %.2f ms",
			lastDrifters, lastAverage.getOrDefault(Part.TOTAL, 0.0), peakTick.getOrDefault(Part.TOTAL, 0L) / 1_000_000.0));
		for (Part part : Part.values()) {
			if (part != Part.TOTAL && lastAverage.containsKey(part)) {
				line.append(String.format(Locale.ROOT, "; %s %.2f", part.name().toLowerCase(Locale.ROOT), lastAverage.get(part)));
			}
		}

		line.append(" (budget: 3 to 5 ms for 10)");
		lastWorst.forEach((part, w) -> line.append(String.format(Locale.ROOT, "\n  slowest %s: %.2f ms (%s)", part.name().toLowerCase(Locale.ROOT), w.nanos() / 1_000_000.0, w.what())));
		lastDrifters = 0;
		return line.toString();
	}
}
