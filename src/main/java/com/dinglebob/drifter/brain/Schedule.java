package com.dinglebob.drifter.brain;

import com.dinglebob.drifter.entity.DrifterEntity;

/**
 * The day as it lives it (DESIGN.md section 18), by its own sense of time (section 5): it can be wrong underground.
 * Times are in-game ticks of the day. TODO(section 25): move to the Schedule data file.
 */
public final class Schedule {
	/** It starts thinking about shelter from here (late afternoon). */
	public static final long SHELTER_THINKING = 10500;
	/** Shelter becomes urgent: dusk, with time to dig in before mobs come out (about 13000). */
	public static final long SHELTER_URGENT = 11500;
	/** Night: from dusk until dawn. */
	public static final long NIGHT_START = 12000;
	public static final long NIGHT_END = 23200;

	private Schedule() {
	}

	public static long believedTime(DrifterEntity drifter) {
		return drifter.getSenses().time().believedTimeOfDay();
	}

	public static boolean isNight(long time) {
		return time >= NIGHT_START && time < NIGHT_END;
	}

	public static boolean isNight(DrifterEntity drifter) {
		return isNight(believedTime(drifter));
	}
}
