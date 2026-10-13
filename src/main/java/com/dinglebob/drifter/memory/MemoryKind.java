package com.dinglebob.drifter.memory;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/**
 * Kinds of memory with their tier, decay and capacity (DESIGN.md section 6, "Tiers", "Decay rules by kind" and
 * "Capacity limits"). Confidence halves every {@code halfLifeTicks}; a value of 0 means it never decays.
 * Entries that fade below {@link DrifterMemory#FORGET_CONFIDENCE} are dropped, unless {@code keepWhenFaded}
 * (identity and positions of fixed things outlive the confidence in their details).
 *
 * <p>All numbers here are tunables meant to move to a datapack file later (section 25).
 */
public enum MemoryKind {
	/** A mob seen recently and where (short-term; mobs move, so seconds). */
	MOB(Tier.SHORT, 20 * 10, false, 32, 1, null),
	/** A dropped item seen on the ground (items despawn after 5 minutes). */
	ITEM(Tier.SHORT, 20 * 60 * 2, false, 32, 1, null),
	/** Got hurt here. Bad hits are promoted to DANGER at consolidation. */
	HURT(Tier.SHORT, 24000, false, 32, 4, "DANGER"),
	/** A block it mined today ("areas mined today"). */
	MINED(Tier.SHORT, 24000, false, 64, 1, null),
	/** A player: identity is permanent, the position fades over minutes. */
	PLAYER(Tier.LONG, 20 * 60 * 5, true, 64, 1, null),
	/** An ore it has seen a face of. Blocks don't move, but someone may mine them: very slow decay. */
	ORE(Tier.LONG, 24000 * 10, false, 64, 1, null),
	/** Trees and other gatherable resources, remembered per 8-block patch (groves, not every log). */
	RESOURCE(Tier.LONG, 24000 * 5, false, 64, 8, null),
	/** Crafting tables, furnaces, beds and other stations. Never decay; checked again whenever seen. */
	STATION(Tier.LONG, 0, true, 32, 1, null),
	/** Chests and barrels; the contents summary fades moderately, the position doesn't. */
	STORAGE(Tier.LONG, 24000 * 3, true, 32, 1, null),
	/** Places to fear: lava, bad hits. Fades over days unless renewed. */
	DANGER(Tier.LONG, 24000 * 3, false, 16, 8, null),
	/** Structure clues: spawners, portals, bookshelves, mossy cobblestone. */
	STRUCTURE(Tier.LONG, 24000 * 30, false, 32, 4, null),
	/** Big things seen from afar: mountains, villages. They don't go anywhere, so they don't fade. */
	LANDMARK(Tier.LONG, 0, true, 32, 48, null);

	public enum Tier {
		SHORT, LONG
	}

	public final Tier tier;
	public final long halfLifeTicks;
	public final boolean keepWhenFaded;
	public final int capacity;
	/** Entries of this kind closer than this many blocks merge into one. */
	public final int cellSize;
	@Nullable
	private final String promotesTo;

	MemoryKind(Tier tier, long halfLifeTicks, boolean keepWhenFaded, int capacity, int cellSize, @Nullable String promotesTo) {
		this.tier = tier;
		this.halfLifeTicks = halfLifeTicks;
		this.keepWhenFaded = keepWhenFaded;
		this.capacity = capacity;
		this.cellSize = cellSize;
		this.promotesTo = promotesTo;
	}

	/** The long-term kind an important short-term memory becomes at consolidation, if any. */
	@Nullable
	public MemoryKind promotesTo() {
		return this.promotesTo == null ? null : MemoryKind.valueOf(this.promotesTo);
	}

	public boolean isBlockKind() {
		return this == ORE || this == RESOURCE || this == STATION || this == STORAGE || this == STRUCTURE;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
