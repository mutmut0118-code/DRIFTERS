package com.dinglebob.drifter.brain;

import com.dinglebob.drifter.entity.DrifterEntity;

/**
 * The day as it lives it (DESIGN.md section 18), by its own sense of time (section 5): it can be wrong underground.
 * Times are in-game ticks of the day. TODO(section 25): move to the Schedule data file.
 */
public final class Schedule {
	/**
	 * It starts thinking about shelter from here (user's rule, session 2: they were sheltering far too early; it should be
	 * around 13000). Caution moves it by up to 1000 either way, and it leaves earlier by its travel time home.
	 */
	public static final long SHELTER_THINKING = 12000;
	/** Shelter becomes urgent: just before mobs come out (about 13200). */
	public static final long SHELTER_URGENT = 12800;
	/** Night: from when beds can be used until dawn. */
	public static final long NIGHT_START = 12542;
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
		SHELTER, HUNT, MINE, WORK;

		public String describe() {
			return switch (this) {
				case SHELTER -> "stays in";
				case HUNT -> "goes out hunting mobs";
				case MINE -> "digs in, then mines";
				case WORK -> "carries on through the night";
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

		// Armed (a stone sword or better, a sword and a shield, or iron armor) and not too careful: like most players, it just
		// carries on with what it was doing, fighting what comes (user's request: too many armed Drifters sat out every
		// night; and 2026-10-20, "a sword and shield is enough for the average drifter"). A shield steadies the careful.
		boolean shield = com.dinglebob.drifter.hands.Shields.has(drifter);
		if (calm && p.get(com.dinglebob.drifter.personality.Trait.CAUTION) < (shield ? WORK_MAX_CAUTION_SHIELD : WORK_MAX_CAUTION)
			&& p.get(com.dinglebob.drifter.personality.Trait.BRAVERY) >= (shield ? WORK_MIN_BRAVERY_SHIELD : WORK_MIN_BRAVERY) && fitToWork(drifter)) {
			return NightStyle.WORK;
		}

		if (calm && p.habits().miningLean >= MINE_LEAN && p.get(com.dinglebob.drifter.personality.Trait.INDUSTRIOUSNESS) >= MINE_INDUSTRIOUSNESS
			&& drifter.getInventory().count(stack -> stack.is(net.minecraft.tags.ItemTags.PICKAXES)) > 0) {
			return NightStyle.MINE;
		}

		return NightStyle.SHELTER;
	}

	/** Caution under which, and Bravery from which, an armed Drifter carries on at night; with a shield too. */
	public static final double WORK_MAX_CAUTION = 0.7;
	public static final double WORK_MIN_BRAVERY = 0.35;
	public static final double WORK_MAX_CAUTION_SHIELD = 0.8;
	public static final double WORK_MIN_BRAVERY_SHIELD = 0.25;

	/** Why it stays in tonight (for its thought), when it does: the first thing that keeps it in. */
	public static String shelterReason(DrifterEntity drifter) {
		com.dinglebob.drifter.personality.Personality p = drifter.getPersonality();
		boolean shield = com.dinglebob.drifter.hands.Shields.has(drifter);
		if (p.is(com.dinglebob.drifter.personality.Mood.ANXIOUS) || p.is(com.dinglebob.drifter.personality.Mood.PANICKED)) {
			return "too shaken to be out after dark";
		}

		if (p.get(com.dinglebob.drifter.personality.Trait.CAUTION) >= (shield ? WORK_MAX_CAUTION_SHIELD : WORK_MAX_CAUTION)) {
			return "too careful to be out after dark";
		}

		if (p.get(com.dinglebob.drifter.personality.Trait.BRAVERY) < (shield ? WORK_MIN_BRAVERY_SHIELD : WORK_MIN_BRAVERY)) {
			return "not brave enough to be out after dark";
		}

		if (drifter.getHealth() < 12.0F) {
			return "too hurt";
		}

		if (drifter.getFoodData().getFoodLevel() < 8) {
			return "too hungry";
		}

		return "nothing good enough to fight with yet (a stone sword, or a sword and a shield)";
	}

	/**
	 * Fit to carry on at night: a weapon at least a stone sword (or two pieces of iron-or-better armor), fairly healthy
	 * and fed. Checked all night; when it stops being true, it goes in.
	 */
	public static boolean fitToWork(DrifterEntity drifter) {
		int weapon = com.dinglebob.drifter.hands.Combat.bestWeaponSlot(drifter);
		double dps = com.dinglebob.drifter.hands.Combat.weaponStats(drifter, weapon < 0 ? net.minecraft.world.item.ItemStack.EMPTY
			: drifter.getInventory().getItem(weapon)).perSecond();
		int armor = 0;
		for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD,
			net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
			if (drifter.getItemBySlot(slot).getItem() instanceof net.minecraft.world.item.ArmorItem piece
				&& com.dinglebob.drifter.social.ItemValues.armorTier(piece) >= 3.0) {
				armor++;
			}
		}

		boolean armed = dps >= WORK_MIN_WEAPON_DPS || dps >= WORK_MIN_WEAPON_DPS_SHIELD && com.dinglebob.drifter.hands.Shields.has(drifter) || armor >= 2;
		return drifter.getHealth() >= 12.0F && drifter.getFoodData().getFoodLevel() >= 8 && armed
			&& !drifter.getPersonality().is(com.dinglebob.drifter.personality.Mood.PANICKED);
	}

	/** A stone sword's damage per second (5 damage at 1.6 a second). */
	public static final double WORK_MIN_WEAPON_DPS = 7.5;
	/** With a shield: a wooden sword is enough (4 damage at 1.6 a second). */
	public static final double WORK_MIN_WEAPON_DPS_SHIELD = 6.0;

	/** Fit to be out at night: healthy, fed, armed. Checked all night; when it stops being true, it digs in. */
	public static boolean fitToHunt(DrifterEntity drifter) {
		int weapon = com.dinglebob.drifter.hands.Combat.bestWeaponSlot(drifter);
		double dps = com.dinglebob.drifter.hands.Combat.weaponStats(drifter, weapon < 0 ? net.minecraft.world.item.ItemStack.EMPTY
			: drifter.getInventory().getItem(weapon)).perSecond();
		return drifter.getHealth() >= 14.0F && drifter.getFoodData().getFoodLevel() >= 10 && dps >= HUNT_MIN_WEAPON_DPS
			&& !drifter.getPersonality().is(com.dinglebob.drifter.personality.Mood.PANICKED);
	}

	/**
	 * Its habit (milestone 23 C1, a townsperson's day: "habits differ: an early riser, a night owl"): how far its day runs
	 * ahead of the sun's, in ticks, -1500 (an early riser: up and at it an hour and a half before the rest) to +1500 (a night
	 * owl). Hard-working, careful ones rise earlier; fidgety, curious ones stay up. The same for it every day.
	 */
	public static long habit(DrifterEntity drifter) {
		var p = drifter.getPersonality();
		double lean = 0.6 * (p.get(com.dinglebob.drifter.personality.Trait.FIDGET) + p.get(com.dinglebob.drifter.personality.Trait.CURIOSITY)
			- p.get(com.dinglebob.drifter.personality.Trait.INDUSTRIOUSNESS) - p.get(com.dinglebob.drifter.personality.Trait.CAUTION));
		double own = ((drifter.getUUID().getLeastSignificantBits() >>> 11) % 1000) / 1000.0 - 0.5;
		return Math.round(net.minecraft.util.Mth.clamp(lean + own, -1.0, 1.0) * 1500);
	}

	/** The time of day as its habit has it (0 to 23999; {@link #habit}). */
	public static long ownTime(DrifterEntity drifter) {
		return Math.floorMod(drifter.level().getDayTime() - habit(drifter), 24000L);
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
