package com.dinglebob.drifter.social;

import java.util.Locale;

/**
 * What a player does that moves its standing with a Drifter (DESIGN.md section 14, "Events that move the numbers"). The
 * numbers are the table's, kept here in one place so they can move to a data file later (section 25). Trust and Fear
 * changes for events with a range (gifts, damage, gear) are worked out where the event is seen, within these bounds.
 */
public enum SocialEvent {
	/** Hit it once, then stopped or crouched: read as an accident. */
	HIT_ONCE(-10, 5, 0, "hit it once, then backed off"),
	/** Attacked it repeatedly: opens a grudge; Fear rises with the damage. */
	ATTACKED(-40, 0, 40, "attacked it again and again"),
	/** Nearly killed it: a severe grudge. */
	NEARLY_KILLED(-60, 30, 60, "nearly killed it"),
	/** Killed it: remembered after it comes back. */
	KILLED(-80, 40, 80, "killed it"),
	/** Killed its pet: a severe grudge, even for Pacifists. */
	KILLED_PET(-80, 0, 80, "killed its pet"),
	/** Broke one of its blocks (-5 each, up to -50 a day). */
	BROKE_BLOCK(-5, 0, 0, "broke its blocks"),
	/** Opened one of its chests, seen or heard. */
	OPENED_CHEST(-10, 0, 0, "opened its chest"),
	/** Took a valuable stack from its chests (-20 each), found on its next chest check. */
	TOOK_VALUABLES(-20, 0, 30, "took things from its chest"),
	/** Watched opening its chest, and valuables gone when it checked right after: caught in the act. */
	CAUGHT_STEALING(-45, 0, 60, "was caught stealing from its chest"),
	/** Seen wearing or holding something missing from its chests: taken for the thief, right or wrong. */
	HAD_ITS_THINGS(-40, 0, 50, "turned up with things missing from its chest"),
	/** Killed one of its farm animals. */
	KILLED_FARM_ANIMAL(-15, 0, 0, "killed its animals"),
	/** Fire or lava near its base. */
	FIRE_NEAR_BASE(-40, 10, 40, "set fire near its home"),
	/** Aimed a drawn bow at it. */
	AIMED_BOW(-5, 5, 0, "aimed a bow at it"),
	/** Sneaked up on it: suspicion. */
	SNUCK_UP(-5, 0, 0, "sneaked up on it"),
	/** Gave it a gift: +5 to +30 by value. */
	GIFT(5, 0, 0, "gave it something"),
	/** Crouch-spammed at it: +3, a few times a day. */
	CROUCH_SPAM(3, 0, 0, "crouched at it"),
	/** Killed a mob that was attacking it. */
	KILLED_ATTACKER(20, 0, 0, "killed a mob that was attacking it"),
	/** Saved it from death. */
	SAVED_IT(30, 0, 0, "saved it from death"),
	/** Spent time nearby peacefully: small and slow, and Familiarity. */
	PEACEFUL_TIME(1, 0, 0, "spent time nearby peacefully"),
	/** Killed a Drifter it was friends with: the news spreads within its group. */
	KILLED_FRIEND(-30, 20, 30, "killed a friend of it"),
	/** Wears much better gear than it: Fear +10 to +30, no change in Trust. */
	BETTER_GEAR(0, 10, 0, "wears much better gear");

	/** Trust change (for ranged events, the low end). */
	public final int trust;
	/** Fear change (for ranged events, the low end). */
	public final int fear;
	/** Severity of the grudge it opens (0: none). */
	public final int grudge;
	/** For the thought and the debug command. */
	public final String what;

	SocialEvent(int trust, int fear, int grudge, String what) {
		this.trust = trust;
		this.fear = fear;
		this.grudge = grudge;
		this.what = what;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	/** Most Trust lost to broken blocks in a day. */
	public static final int BROKE_BLOCK_DAILY_CAP = -50;
	/** Most crouch-spam greetings that count in a day. */
	public static final int CROUCH_SPAM_DAILY_CAP = 3;
	/** Gift Trust bounds. */
	public static final int GIFT_MAX = 30;
	/** Fear from gear bounds. */
	public static final int GEAR_FEAR_MAX = 30;
	/** Grudge severity at or above which a vengeful one never lets it fade. */
	public static final int SEVERE_GRUDGE = 60;
}
