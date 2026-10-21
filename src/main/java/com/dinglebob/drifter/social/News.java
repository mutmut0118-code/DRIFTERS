package com.dinglebob.drifter.social;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;

/**
 * A piece of news a Drifter carries and passes on to the ones it meets (milestone 22 groundwork, "news that travels";
 * DESIGN.md section 1: news reaches a Drifter only by its seeing something or someone telling it). What happened, who to,
 * who did it, when, how many tellings ago, how much it mattered, and anything more another mod keeps with it.
 *
 * @param key    the same happening has the same key, however many heard it (heard twice: still once)
 * @param about  a player's deed: the player; a death: the one who died; another mod's: its subject (or null)
 * @param other  a player's deed: the Drifter it happened to; a death: who killed it (or null)
 * @param what   in words, from the teller's side ("killed lanternpanda", "died: slain by a zombie")
 * @param weight a player's deed: the trust it cost or earned the one it happened to; a death: 1 if a player killed it
 * @param lasts  game ticks it's worth telling for, from {@code since}
 */
public record News(String key, Kind kind, @Nullable UUID about, String aboutName, @Nullable UUID other, String otherName, String what, long since,
	int hops, double weight, long lasts, CompoundTag data) {

	public enum Kind {
		/** Something a player did to a Drifter (an attack, a theft, a kill, a rescue, a gift). */
		PLAYER_DEED,
		/** A Drifter died (and who killed it). */
		DEATH,
		/** Another mod's (an election, a war declared). */
		CUSTOM
	}

	/** Passed on once more. */
	public News retold() {
		return new News(this.key, this.kind, this.about, this.aboutName, this.other, this.otherName, this.what, this.since, this.hops + 1, this.weight,
			this.lasts, this.data);
	}

	public boolean stale(long now) {
		return now - this.since > this.lasts;
	}

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Key", this.key);
		tag.putString("Kind", this.kind.name());
		if (this.about != null) {
			tag.putUUID("About", this.about);
		}

		tag.putString("AboutName", this.aboutName);
		if (this.other != null) {
			tag.putUUID("Other", this.other);
		}

		tag.putString("OtherName", this.otherName);
		tag.putString("What", this.what);
		tag.putLong("Since", this.since);
		tag.putInt("Hops", this.hops);
		tag.putDouble("Weight", this.weight);
		tag.putLong("Lasts", this.lasts);
		tag.put("Data", this.data);
		return tag;
	}

	@Nullable
	static News load(CompoundTag tag) {
		try {
			return new News(tag.getString("Key"), Kind.valueOf(tag.getString("Kind")), tag.hasUUID("About") ? tag.getUUID("About") : null,
				tag.getString("AboutName"), tag.hasUUID("Other") ? tag.getUUID("Other") : null, tag.getString("OtherName"), tag.getString("What"),
				tag.getLong("Since"), tag.getInt("Hops"), tag.getDouble("Weight"), tag.getLong("Lasts"), tag.getCompound("Data"));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
