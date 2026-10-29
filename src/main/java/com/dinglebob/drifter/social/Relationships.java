package com.dinglebob.drifter.social;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

/**
 * Everyone a Drifter has a standing with (DESIGN.md section 14), and its disposition. Events from what players do go
 * through {@link #record}; each day Trust drifts back toward its stranger baseline by its Forgiveness, and grudges fade
 * by its Vengefulness (the severe ones of a vengeful Drifter never do).
 */
public class Relationships {
	private static final int SAVE_VERSION = 1;
	/** Game ticks in a day. */
	private static final long DAY = 24000L;
	/** Most days of drift caught up at once (after a long time unloaded). */
	private static final int MAX_DRIFT_DAYS = 7;
	/** Stranger baseline spread by Sociability: -20 for a wary hermit, +20 for a sociable one (section 14 examples). */
	private static final double BASELINE_SPREAD = 40.0;
	/** A grudge below this severity is gone. */
	private static final double GRUDGE_GONE = 2.0;

	private final DrifterEntity drifter;
	@Nullable
	private Disposition disposition;
	private final Map<UUID, Relationship> byPlayer = new LinkedHashMap<>();
	private long lastDriftDay = -1;
	private final SocialWatcher watcher;

	public Relationships(DrifterEntity drifter) {
		this.drifter = drifter;
		this.watcher = new SocialWatcher(drifter);
	}

	/** What notices players' doings (section 14 events). */
	public SocialWatcher watcher() {
		return this.watcher;
	}

	/** Its disposition, rolled from its personality the first time it's needed (and kept). */
	public Disposition disposition() {
		if (this.disposition == null) {
			this.disposition = Disposition.roll(this.drifter.getPersonality().archetype(), this.drifter.getPersonality().get(Trait.VENGEFULNESS),
				this.drifter.getRandom());
		}

		return this.disposition;
	}

	/** Debug: a new disposition (or a fresh roll with null, as after a personality reroll). */
	public void setDisposition(@Nullable Disposition disposition) {
		this.disposition = disposition;
	}

	/** Starting Trust for a player it has never met: Sociability, shifted by its disposition. */
	public double strangerBaseline() {
		return Mth.clamp(this.temperBaseline() + com.dinglebob.drifter.DrifterConfig.aggressionShift(com.dinglebob.drifter.DrifterConfig.playerAggression()),
			-Relationship.MAX_TRUST, Relationship.MAX_TRUST);
	}

	/** Its own stranger baseline, from its temperament alone (no setting). */
	public double temperBaseline() {
		double sociability = this.drifter.getPersonality().get(Trait.SOCIABILITY);
		return Mth.clamp((sociability - 0.5) * BASELINE_SPREAD + this.disposition().baselineShift, -Relationship.MAX_TRUST, Relationship.MAX_TRUST);
	}

	@Nullable
	public Relationship get(UUID player) {
		return this.byPlayer.get(player);
	}

	/** Its standing with this player, starting from the stranger baseline the first time. */
	public Relationship getOrMeet(UUID player, String name) {
		Relationship r = this.byPlayer.computeIfAbsent(player, id -> new Relationship(id, name, this.strangerBaseline()));
		r.name = name;
		return r;
	}

	public Collection<Relationship> all() {
		return this.byPlayer.values();
	}

	/**
	 * Something this player did (section 14 table). {@code amount} is for the events with a range: a gift's Trust (5 to
	 * 30), the damage behind repeated attacks (Fear), the gear gap (Fear 10 to 30); otherwise ignored. Returns the Trust
	 * change actually made.
	 */
	public double record(UUID player, String name, SocialEvent event, double amount) {
		Relationship r = this.getOrMeet(player, name);
		long now = this.drifter.level().getGameTime();
		r.rollDay(now / DAY);
		double before = r.trust;
		double fearBefore = r.fear;
		double trust = event.trust;
		double fear = event.fear;
		switch (event) {
			case BROKE_BLOCK -> {
				// -5 each, up to -50 a day.
				int room = SocialEvent.BROKE_BLOCK_DAILY_CAP - r.brokeBlocksToday;
				trust = Math.max(event.trust, room);
				r.brokeBlocksToday += (int)trust;
			}
			case CROUCH_SPAM -> {
				if (r.crouchSpamsToday >= SocialEvent.CROUCH_SPAM_DAILY_CAP) {
					trust = 0;
				} else {
					r.crouchSpamsToday++;
				}
			}
			case GIFT -> trust = Mth.clamp(amount, event.trust, SocialEvent.GIFT_MAX);
			case COMPLIMENT -> {
				r.compliments++;
				trust = r.complimentsToday++ < SocialEvent.COMPLIMENT_DAILY_CAP ? event.trust : 0;
			}
			case INSULT -> {
				r.insults++;
				r.lastInsultAt = now;
				trust = r.insultsToday++ < SocialEvent.INSULT_DAILY_CAP ? event.trust : 0;
			}
			// Fear scaled by the damage (a heart of damage is 2 points of Fear, up to 30).
			case ATTACKED -> fear = Mth.clamp(amount * 1.0, 0.0, 30.0);
			default -> {
			}
		}

		// Hurt badly when dying would cost more (stakes, part 2): it takes it harder, and fears them more.
		double wary = com.dinglebob.drifter.brain.Stakes.wariness(this.drifter);
		if (event == SocialEvent.ATTACKED || event == SocialEvent.NEARLY_KILLED) {
			trust *= 1.0 + 0.5 * wary;
			fear *= 1.0 + wary;
		}

		switch (event) {
			case BETTER_GEAR -> {
				// A level: only the part above what their gear already earned.
				int level = (int)Mth.clamp(amount, event.fear, SocialEvent.GEAR_FEAR_MAX);
				fear = Math.max(0, level - r.gearFear);
				r.gearFear = Math.max(r.gearFear, level);
			}
			case PEACEFUL_TIME -> r.addFamiliarity(1.0);
			default -> {
			}
		}

		r.addTrust(trust);
		r.addFear(fear);
		if (event.grudge > 0) {
			boolean forever = this.disposition() == Disposition.GRUDGE_HOLDER
				|| event.grudge >= SocialEvent.SEVERE_GRUDGE && this.drifter.getPersonality().get(Trait.VENGEFULNESS) >= VENGEFUL
				// (Nearly killed when that would have been the end of it: it doesn't forget.)
				|| event == SocialEvent.NEARLY_KILLED && wary >= 0.7;
			r.grudges.add(new Relationship.Grudge(event, event.grudge, now, forever));
		}

		// A wrong (not an accident, not a small suspicion): the telegraph rule no longer applies to this player.
		if (event.trust <= -10 && event != SocialEvent.HIT_ONCE) {
			r.wronged = true;
		}

		double change = r.trust - before;
		if (change != 0.0 || r.fear != fearBefore) {
			this.drifter.getMemory().thoughts().add(now, String.format(Locale.ROOT, "%s %s: trust %.0f -> %.0f, fear %.0f -> %.0f", name, event.what,
				before, r.trust, fearBefore, r.fear));
		}

		// Group reputation (section 21): wrong one member and the others think less of you too, by part of it.
		if (change < 0.0) {
			this.tellGroup(player, name, change);
		}

		return change;
	}

	/** Share of a wrong to one member that the rest of its group takes on. */
	private static final double GROUP_SHARE = 0.5;

	private void tellGroup(UUID player, String name, double change) {
		var group = com.dinglebob.drifter.world.DrifterGroups.of(this.drifter.getUUID());
		if (group == null || !(this.drifter.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}

		for (UUID member : group.members) {
			if (member.equals(this.drifter.getUUID())) {
				continue;
			}

			for (var any : level.getServer().getAllLevels()) {
				if (any.getEntity(member) instanceof com.dinglebob.drifter.entity.DrifterEntity other) {
					Relationship theirs = other.getSocial().getOrMeet(player, name);
					double before = theirs.trust;
					theirs.addTrust(change * GROUP_SHARE);
					other.getMemory().thoughts().add(level.getGameTime(), String.format(Locale.ROOT, "%s wronged %s, one of its group: trust %.0f -> %.0f",
						name, this.drifter.getName().getString(), before, theirs.trust));
				}
			}
		}
	}

	/** Vengefulness at which its severe grudges never fade. */
	public static final double VENGEFUL = 0.75;

	/** Once a game day: Trust back toward the baseline (minus what grudges hold back), grudges and Fear fading. */
	public void tick() {
		long day = this.drifter.level().getGameTime() / DAY;
		if (this.lastDriftDay < 0) {
			this.lastDriftDay = day;
			return;
		}

		int days = (int)Math.min(MAX_DRIFT_DAYS, day - this.lastDriftDay);
		if (days <= 0) {
			return;
		}

		this.lastDriftDay = day;
		double forgiveness = this.drifter.getPersonality().get(Trait.FORGIVENESS);
		double vengefulness = this.drifter.getPersonality().get(Trait.VENGEFULNESS);
		double baseline = this.strangerBaseline();
		for (int i = 0; i < days; i++) {
			for (Relationship r : this.byPlayer.values()) {
				// Grudges fade (vengeful ones slowly); "never forgets" ones stay.
				r.grudges.replaceAll(g -> g.forever() ? g
					: new Relationship.Grudge(g.event(), g.severity() * (1.0 - (GRUDGE_FADE_MIN + GRUDGE_FADE_SPREAD * (1.0 - vengefulness))), g.since(), false));
				r.grudges.removeIf(g -> g.severity() < GRUDGE_GONE);
				// Trust drifts toward the baseline, held down by what it still holds against them.
				double target = Math.max(-Relationship.MAX_TRUST, baseline - r.grudgeWeight());
				double rate = DRIFT_MIN + DRIFT_SPREAD * forgiveness;
				r.addTrust((target - r.trust) * rate);
				// Fear fades too, down to what their gear earns.
				r.fear = Math.max(r.gearFear, r.fear * FEAR_KEPT);
			}
		}
	}

	/** Share of the way back to the baseline Trust moves in a day: 5% for the least forgiving, 30% for the most. */
	private static final double DRIFT_MIN = 0.05;
	private static final double DRIFT_SPREAD = 0.25;
	/** Share of a grudge that fades in a day: 5% for the most vengeful, 40% for the least. */
	private static final double GRUDGE_FADE_MIN = 0.05;
	private static final double GRUDGE_FADE_SPREAD = 0.35;
	/** Share of Fear kept from one day to the next. */
	private static final double FEAR_KEPT = 0.9;

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", SAVE_VERSION);
		if (this.disposition != null) {
			tag.putString("Disposition", this.disposition.id());
		}

		tag.putLong("LastDriftDay", this.lastDriftDay);
		ListTag list = new ListTag();
		for (Relationship r : this.byPlayer.values()) {
			list.add(r.save());
		}

		tag.put("Players", list);
		return tag;
	}

	public void load(CompoundTag tag) {
		// Version 1 is the first format.
		this.disposition = tag.contains("Disposition") ? Disposition.byId(tag.getString("Disposition")) : null;
		this.lastDriftDay = tag.contains("LastDriftDay") ? tag.getLong("LastDriftDay") : -1;
		this.byPlayer.clear();
		for (Tag t : tag.getList("Players", Tag.TAG_COMPOUND)) {
			Relationship r = Relationship.load((CompoundTag)t);
			this.byPlayer.put(r.player, r);
		}
	}
}
