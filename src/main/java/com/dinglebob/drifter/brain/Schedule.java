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

	/**
	 * When this Drifter starts thinking about shelter: a cautious one heads in early, a reckless one cuts it fine
	 * (section 17: Caution means "going home early"; section 18: "a Caution margin").
	 */
	public static long shelterThinking(DrifterEntity drifter) {
		return SHELTER_THINKING - Math.round((caution(drifter) - 0.5) * 2000.0) - homeTravelTicks(drifter);
	}

	/** Blocks from home beyond which it no longer heads home for the night (it shelters where it is). */
	public static final double GO_HOME_RANGE = 96.0;

	/**
	 * Section 18: far-away Drifters start heading home based on travel time. Ticks it needs to get home at a sprint
	 * (about 5 blocks a second, with a margin for the way not being straight), or 0 with no home in range.
	 */
	public static long homeTravelTicks(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		if (home == null || !home.near(drifter, GO_HOME_RANGE)) {
			return 0L;
		}

		return Math.round(Math.sqrt(drifter.blockPosition().distSqr(home.center)) / 5.0 * 1.4 * 20.0);
	}

	/** When shelter becomes urgent for this Drifter (never after nightfall). */
	public static long shelterUrgent(DrifterEntity drifter) {
		return Math.min(NIGHT_START, SHELTER_URGENT - Math.round((caution(drifter) - 0.5) * 1000.0));
	}

	private static double caution(DrifterEntity drifter) {
		return drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.CAUTION);
	}

	/**
	 * How it spends the night (section 18 "Night variations by personality", user's notes for milestone 7): most dig in
	 * and wait; brave, confident ones go out after mobs for their drops; strip-mining types dig in and then mine from the
	 * shelter, since time doesn't matter underground. Cautious ones always stay in. Chosen once per night (at dusk).
	 * TODO(milestone 8): sleeping in a bed first; TODO(milestone 9): hostile ones stalking players at night.
	 */
	public enum NightStyle {
		SHELTER, HUNT, MINE;

		public String describe() {
			return switch (this) {
				case SHELTER -> "stays in";
				case HUNT -> "goes out hunting mobs";
				case MINE -> "digs in, then mines";
			};
		}
	}

	/** Bravery (mood included) needed to go out at night; and Caution must be under {@link #HUNT_MAX_CAUTION}. */
	public static final double HUNT_BRAVERY = 0.62;
	public static final double HUNT_MAX_CAUTION = 0.6;
	/** A weapon at least as good as a wooden sword (damage per second). */
	public static final double HUNT_MIN_WEAPON_DPS = 6.0;
	/** Mining lean and Industriousness for the night miners. */
	public static final double MINE_LEAN = 0.55;
	public static final double MINE_INDUSTRIOUSNESS = 0.45;

	public static NightStyle chooseNightStyle(DrifterEntity drifter) {
		com.dinglebob.drifter.personality.Personality p = drifter.getPersonality();
		boolean calm = !p.is(com.dinglebob.drifter.personality.Mood.ANXIOUS) && !p.is(com.dinglebob.drifter.personality.Mood.PANICKED);
		if (calm && p.get(com.dinglebob.drifter.personality.Trait.BRAVERY) >= HUNT_BRAVERY
			&& p.get(com.dinglebob.drifter.personality.Trait.CAUTION) < HUNT_MAX_CAUTION && fitToHunt(drifter)) {
			return NightStyle.HUNT;
		}

		if (calm && p.habits().miningLean >= MINE_LEAN && p.get(com.dinglebob.drifter.personality.Trait.INDUSTRIOUSNESS) >= MINE_INDUSTRIOUSNESS
			&& drifter.getInventory().count(stack -> stack.is(net.minecraft.tags.ItemTags.PICKAXES)) > 0) {
			return NightStyle.MINE;
		}

		return NightStyle.SHELTER;
	}

	/** Fit to be out at night: healthy, fed, armed. Checked all night; when it stops being true, it digs in. */
	public static boolean fitToHunt(DrifterEntity drifter) {
		int weapon = com.dinglebob.drifter.hands.Combat.bestWeaponSlot(drifter);
		double dps = com.dinglebob.drifter.hands.Combat.weaponStats(drifter, weapon < 0 ? net.minecraft.world.item.ItemStack.EMPTY
			: drifter.getInventory().getItem(weapon)).perSecond();
		return drifter.getHealth() >= 14.0F && drifter.getFoodData().getFoodLevel() >= 10 && dps >= HUNT_MIN_WEAPON_DPS
			&& !drifter.getPersonality().is(com.dinglebob.drifter.personality.Mood.PANICKED);
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
