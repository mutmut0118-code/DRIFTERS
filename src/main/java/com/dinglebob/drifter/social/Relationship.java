package com.dinglebob.drifter.social;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

/**
 * A Drifter's standing with one player (DESIGN.md section 14, "The relationship record"): Trust and Fear, how well it
 * knows them, the wrongs it holds against them and the favors it owes, what it thinks they can do, where they live, and
 * when it last saw them.
 */
public class Relationship {
	public static final double MAX_TRUST = 100.0;
	public static final double MAX_FEAR = 100.0;
	public static final double MAX_FAMILIARITY = 100.0;

	public final UUID player;
	/** The name it knows them by (their name tag). */
	public String name;
	/** -100 to +100: like or dislike. */
	public double trust;
	/** 0 to 100: how dangerous it believes them to be. */
	public double fear;
	/** 0 to 100: time spent around them. */
	public double familiarity;
	/** Wrongs it holds against them, each with a severity that fades (or not). */
	public final List<Grudge> grudges = new ArrayList<>();
	/** Gifts and favors it owes them back. */
	public final List<Debt> debts = new ArrayList<>();
	/** Rolling estimate of their strength, from gear seen and fights watched (its own gear is 1.0). */
	public double strength = 1.0;
	/** Where they live, if it has seen. */
	@Nullable
	public BlockPos knownBase;
	/** When and where it last saw them (game time; -1 never). */
	public long lastSeenAt = -1;
	@Nullable
	public BlockPos lastSeenPos;
	/** Ever wronged it (the telegraph rule: before that, a warning is owed before any hostility). */
	public boolean wronged;

	// Daily caps (section 14 table: "up to -50", "capped per day").
	public long capDay = -1;
	public int brokeBlocksToday;
	public int crouchSpamsToday;
	/** Fear already added for their gear (the table's +10 to +30 is a level, not added again each time). */
	public int gearFear;
	/** The day it last let another player's standing rub off on this one, per other player (not saved). */
	public final java.util.Map<String, Long> sharedDay = new java.util.HashMap<>();
	/** (A Drifter:) when it last asked them for something to trade (not saved). */
	public long tradeAskedAt = Long.MIN_VALUE / 2;

	public Relationship(UUID player, String name, double baseline) {
		this.player = player;
		this.name = name;
		this.trust = baseline;
	}

	public record Grudge(SocialEvent event, double severity, long since, boolean forever) {
	}

	public record Debt(String what, int value, long since) {
	}

	public void addTrust(double change) {
		this.trust = Mth.clamp(this.trust + change, -MAX_TRUST, MAX_TRUST);
	}

	public void addFear(double change) {
		this.fear = Mth.clamp(this.fear + change, 0.0, MAX_FEAR);
	}

	public void addFamiliarity(double change) {
		this.familiarity = Mth.clamp(this.familiarity + change, 0.0, MAX_FAMILIARITY);
	}

	/** Sum of the grudges it still holds. */
	public double grudgeWeight() {
		return this.grudges.stream().mapToDouble(Grudge::severity).sum();
	}

	/** Resets the per-day counters when the day changes. */
	void rollDay(long day) {
		if (day != this.capDay) {
			this.capDay = day;
			this.brokeBlocksToday = 0;
			this.crouchSpamsToday = 0;
		}
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putUUID("Player", this.player);
		tag.putString("Name", this.name);
		tag.putDouble("Trust", this.trust);
		tag.putDouble("Fear", this.fear);
		tag.putDouble("Familiarity", this.familiarity);
		tag.putDouble("Strength", this.strength);
		tag.putBoolean("Wronged", this.wronged);
		tag.putLong("LastSeenAt", this.lastSeenAt);
		if (this.lastSeenPos != null) {
			tag.putLong("LastSeenPos", this.lastSeenPos.asLong());
		}

		if (this.knownBase != null) {
			tag.putLong("KnownBase", this.knownBase.asLong());
		}

		tag.putLong("CapDay", this.capDay);
		tag.putInt("BrokeBlocksToday", this.brokeBlocksToday);
		tag.putInt("CrouchSpamsToday", this.crouchSpamsToday);
		tag.putInt("GearFear", this.gearFear);
		ListTag grudges = new ListTag();
		for (Grudge grudge : this.grudges) {
			CompoundTag g = new CompoundTag();
			g.putString("Event", grudge.event().id());
			g.putDouble("Severity", grudge.severity());
			g.putLong("Since", grudge.since());
			g.putBoolean("Forever", grudge.forever());
			grudges.add(g);
		}

		tag.put("Grudges", grudges);
		ListTag debts = new ListTag();
		for (Debt debt : this.debts) {
			CompoundTag d = new CompoundTag();
			d.putString("What", debt.what());
			d.putInt("Value", debt.value());
			d.putLong("Since", debt.since());
			debts.add(d);
		}

		tag.put("Debts", debts);
		return tag;
	}

	public static Relationship load(CompoundTag tag) {
		Relationship r = new Relationship(tag.getUUID("Player"), tag.getString("Name"), tag.getDouble("Trust"));
		r.fear = tag.getDouble("Fear");
		r.familiarity = tag.getDouble("Familiarity");
		r.strength = tag.contains("Strength") ? tag.getDouble("Strength") : 1.0;
		r.wronged = tag.getBoolean("Wronged");
		r.lastSeenAt = tag.getLong("LastSeenAt");
		r.lastSeenPos = tag.contains("LastSeenPos") ? BlockPos.of(tag.getLong("LastSeenPos")) : null;
		r.knownBase = tag.contains("KnownBase") ? BlockPos.of(tag.getLong("KnownBase")) : null;
		r.capDay = tag.getLong("CapDay");
		r.brokeBlocksToday = tag.getInt("BrokeBlocksToday");
		r.crouchSpamsToday = tag.getInt("CrouchSpamsToday");
		r.gearFear = tag.getInt("GearFear");
		for (Tag t : tag.getList("Grudges", Tag.TAG_COMPOUND)) {
			CompoundTag g = (CompoundTag)t;
			SocialEvent event;
			try {
				event = SocialEvent.valueOf(g.getString("Event").toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException unknown) {
				continue;
			}

			r.grudges.add(new Grudge(event, g.getDouble("Severity"), g.getLong("Since"), g.getBoolean("Forever")));
		}

		for (Tag t : tag.getList("Debts", Tag.TAG_COMPOUND)) {
			CompoundTag d = (CompoundTag)t;
			r.debts.add(new Debt(d.getString("What"), d.getInt("Value"), d.getLong("Since")));
		}

		return r;
	}
}
