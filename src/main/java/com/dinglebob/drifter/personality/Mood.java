package com.dinglebob.drifter.personality;

import java.util.Locale;

/**
 * Moods (DESIGN.md section 17, "Mood"): short-lived states that shift everything for seconds to days. Each one holds
 * a pressure that events push up and that fades with its half-life; the strongest one above the threshold is the mood.
 */
public enum Mood {
	CALM(0),
	/** Recent threats, underground at night, low health. */
	ANXIOUS(60),
	/** Critical danger. */
	PANICKED(15),
	/** Good gear, recent wins. */
	CONFIDENT(120),
	/** Repeated failures, getting stuck, deaths. */
	FRUSTRATED(150),
	/** Long idle, nothing new. Mostly driven by novelty rather than its half-life. */
	BORED(600),
	/** A big find or a kill. */
	TRIUMPHANT(45),
	/** A pet or ally died. TODO(milestone 8/9): pets and allies. */
	GRIEVING(20 * 60 * 3),
	/** Wronged by a player. TODO(milestone 9): relationships. */
	ANGRY(600);

	/** Seconds for the pressure to halve. */
	public final int halfLifeSeconds;

	Mood(int halfLifeSeconds) {
		this.halfLifeSeconds = halfLifeSeconds;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
