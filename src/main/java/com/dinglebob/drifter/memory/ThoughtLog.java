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

	public void add(long time, String text) {
		Thought last = this.thoughts.peekLast();
		if (last != null && last.text().equals(text) && time - last.time() < 100) {
			return;
		}

		this.thoughts.addLast(new Thought(time, text));
		if (echo) {
			com.dinglebob.drifter.Drifter.LOGGER.info("[thought @{}] {}", time, text);
		}

		while (this.thoughts.size() > CAPACITY) {
			this.thoughts.removeFirst();
		}
	}

	/** The newest thoughts, oldest first. */
	public List<Thought> latest(int count) {
		List<Thought> all = new ArrayList<>(this.thoughts);
		return all.subList(Math.max(0, all.size() - count), all.size());
	}
}
