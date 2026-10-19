package com.dinglebob.drifter.memory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The thought log (DESIGN.md section 25): a ring buffer of what the Drifter noticed and decided, in plain words,
 * for debugging ("glimpsed something to the left", "detected Steve"). Not saved.
 */
public class ThoughtLog {
	public static final int CAPACITY = 64;

	public record Thought(long time, String text) {
	}

	/** Debug (`/drifter thoughts log on`): every thought also goes to the server log as it happens. */
	public static boolean echo;

	private final Deque<Thought> thoughts = new ArrayDeque<>();
	/** Whose thoughts these are (for the server log, where several Drifters' thoughts mix). */
	private java.util.function.Supplier<String> owner = () -> "?";

	public void setOwner(java.util.function.Supplier<String> owner) {
		this.owner = owner;
	}

	public void add(long time, String text) {
		Thought last = this.thoughts.peekLast();
		if (last != null && last.text().equals(text) && time - last.time() < 100) {
			return;
		}

		this.thoughts.addLast(new Thought(time, text));
		if (echo && echoAllowed()) {
			com.dinglebob.drifter.Drifter.LOGGER.info("[thought {} @{}] {}", this.owner.get(), time, text);
		}

		while (this.thoughts.size() > CAPACITY) {
			this.thoughts.removeFirst();
		}
	}

	/**
	 * At most this many thoughts a second go to the log, from all Drifters together; the rest stay in each one's own
	 * thoughts (`/drifter thoughts`) and a line says how many were left out. Writing every thought of a 300-Drifter battle
	 * to the log was a twentieth of the server's time (milestone 16).
	 */
	public static final int ECHO_PER_SECOND = 200;
	private static long echoSecond;
	private static int echoed;
	private static int skipped;

	private static boolean echoAllowed() {
		long second = System.currentTimeMillis() / 1000L;
		if (second != echoSecond) {
			if (skipped > 0) {
				com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter] {} more thoughts that second not logged (busy; see /drifter thoughts)", skipped);
			}

			echoSecond = second;
			echoed = 0;
			skipped = 0;
		}

		if (echoed < ECHO_PER_SECOND) {
			echoed++;
			return true;
		}

		skipped++;
		return false;
	}

	/** The newest thoughts, oldest first. */
	public List<Thought> latest(int count) {
		List<Thought> all = new ArrayList<>(this.thoughts);
		return all.subList(Math.max(0, all.size() - count), all.size());
	}
}
