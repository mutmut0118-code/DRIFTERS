package com.dinglebob.drifter.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * The Drifter's short-term and long-term memory (DESIGN.md section 6). Working memory (tracked entities, recent
 * sounds, the current task step) lives in the senses and the executor and is never saved.
 *
 * <p>Everything here was learned through perception. Decisions must read from here, never from the world.
 */
public class DrifterMemory {
	public static final int SAVE_VERSION = 1;
	/** Faded below this, a memory is dropped (unless its kind keeps faded entries). */
	public static final float FORGET_CONFIDENCE = 0.05F;
	/** Short-term memories at least this important are promoted to long-term at consolidation. */
	public static final float PROMOTE_IMPORTANCE = 0.6F;
	/** Short-term memories younger than this survive consolidation (they're still "now"). */
	public static final long KEEP_RECENT_TICKS = 20 * 60;

	private final Map<String, MemoryEntry> shortTerm = new LinkedHashMap<>();
	private final Map<String, MemoryEntry> longTerm = new LinkedHashMap<>();
	private final MentalMap map = new MentalMap();
	private final ThoughtLog thoughts = new ThoughtLog();
	/** Blocks it has walked (its own roads, section 6 "Mental map"): the pathfinder prefers them. Oldest dropped first. */
	/** Its trail, oldest first (no boxing: the pathfinder asks about it for every step it weighs; performance 2026-10-17). */
	private final it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet trail = new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet();
	public static final int MAX_TRAIL = 4096;
	/**
	 * How often it has walked each block lately (feet), halved every day (milestone 22 groundwork, roads): where it walks
	 * day after day it wears a path in ({@code skills.PathGoal}).
	 */
	private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap wear = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
	public static final int MAX_WEAR = 4096;
	/** The in-game day (by its own time sense) of the last consolidation. */
	private long lastConsolidatedDay = -1;

	public MentalMap map() {
		return this.map;
	}

	public ThoughtLog thoughts() {
		return this.thoughts;
	}

	public void walked(BlockPos feet) {
		this.trail.addAndMoveToLast(feet.asLong());
		if (this.trail.size() > MAX_TRAIL) {
			this.trail.removeFirstLong();
		}

		this.wear.addTo(feet.asLong(), 1);
		if (this.wear.size() > MAX_WEAR) {
			// The blocks walked once go first (then twice...): what's left is where it keeps going.
			for (int once = 1; this.wear.size() > MAX_WEAR * 3 / 4 && once < 8; once++) {
				int most = once;
				this.wear.values().removeIf(count -> count <= most);
			}
		}
	}

	/** How often it has walked this block lately. */
	public int wear(BlockPos feet) {
		return this.wear.get(feet.asLong());
	}

	/** Each block it has walked at least {@code times} times lately (feet). */
	public void forEachWorn(int times, java.util.function.LongConsumer action) {
		for (var entry : this.wear.long2IntEntrySet()) {
			if (entry.getIntValue() >= times) {
				action.accept(entry.getLongKey());
			}
		}
	}

	/** Made into a path: counted afresh. */
	public void forgetWear(BlockPos feet) {
		this.wear.remove(feet.asLong());
	}

	/** Its trails, a copy (for a path search on another thread). */
	public it.unimi.dsi.fastutil.longs.LongOpenHashSet trailCopy() {
		return new it.unimi.dsi.fastutil.longs.LongOpenHashSet(this.trail);
	}

	public boolean isOnTrail(BlockPos feet) {
		return this.trail.contains(feet.asLong());
	}

	private static String key(MemoryKind kind, String label, Vec3 pos, @Nullable UUID entity) {
		if (entity != null) {
			return kind.name() + "|" + entity;
		}

		int cell = kind.cellSize;
		return kind.name() + "|" + label + "|" + Math.floorDiv((int)Math.floor(pos.x), cell) + "," + Math.floorDiv((int)Math.floor(pos.y), cell)
			+ "," + Math.floorDiv((int)Math.floor(pos.z), cell);
	}

	private Map<String, MemoryEntry> tier(MemoryKind kind) {
		return kind.tier == MemoryKind.Tier.SHORT ? this.shortTerm : this.longTerm;
	}

	/**
	 * Records or refreshes a memory. Seeing something again resets its confidence; the better source and the higher
	 * importance are kept.
	 *
	 * @return the entry, and whether it was new
	 */
	public Remembered remember(MemoryKind kind, String label, Vec3 pos, ResourceLocation dimension, long now, MemorySource source,
		float importance, @Nullable UUID entity) {
		Map<String, MemoryEntry> tier = this.tier(kind);
		String key = key(kind, label, pos, entity);
		MemoryEntry entry = tier.get(key);
		boolean isNew = entry == null;
		if (isNew) {
			entry = new MemoryEntry(kind, label, pos, dimension, now, source, importance);
			entry.entityId = entity;
			tier.put(key, entry);
			this.trim(kind, now);
		} else {
			entry.label = label;
			entry.pos = pos;
			entry.confirmedAt = now;
			entry.baseConfidence = source == MemorySource.SEEN ? 1.0F : Math.max(entry.confidence(now), 0.7F);
			if (source.atLeastAsGoodAs(entry.source)) {
				entry.source = source;
			}

			entry.importance = Math.max(entry.importance, importance);
		}

		return new Remembered(entry, isNew);
	}

	public record Remembered(MemoryEntry entry, boolean isNew) {
	}

	public void forget(MemoryEntry entry) {
		this.shortTerm.values().remove(entry);
		this.longTerm.values().remove(entry);
	}

	/** Forgets every block memory at this exact block (it was mined, or it turned out to be gone). */
	public List<MemoryEntry> forgetBlockAt(BlockPos pos, ResourceLocation dimension) {
		List<MemoryEntry> removed = new ArrayList<>();
		this.longTerm.values().removeIf(entry -> {
			boolean match = entry.kind.isBlockKind() && entry.kind.cellSize == 1 && entry.dimension.equals(dimension) && entry.blockPos().equals(pos);
			if (match) {
				removed.add(entry);
			}

			return match;
		});
		return removed;
	}

	/** Every memory, short-term then long-term. */
	public Stream<MemoryEntry> all() {
		return Stream.concat(this.shortTerm.values().stream(), this.longTerm.values().stream());
	}

	public List<MemoryEntry> find(MemoryKind kind, Predicate<MemoryEntry> filter) {
		return this.tier(kind).values().stream().filter(entry -> entry.kind == kind && filter.test(entry)).toList();
	}

	/** Nearest remembered entry of a kind matching the filter, in this dimension, within a distance. */
	@Nullable
	public MemoryEntry nearest(MemoryKind kind, ResourceLocation dimension, Vec3 from, double maxDistance, Predicate<MemoryEntry> filter) {
		return this.tier(kind).values().stream()
			.filter(entry -> entry.kind == kind && entry.dimension.equals(dimension) && entry.pos.distanceTo(from) <= maxDistance && filter.test(entry))
			.min(Comparator.comparingDouble(entry -> entry.pos.distanceToSqr(from)))
			.orElse(null);
	}

	public int count(MemoryKind.Tier tier) {
		return tier == MemoryKind.Tier.SHORT ? this.shortTerm.size() : this.longTerm.size();
	}

	public Map<MemoryKind, Integer> countsByKind() {
		Map<MemoryKind, Integer> counts = new EnumMap<>(MemoryKind.class);
		this.all().forEach(entry -> counts.merge(entry.kind, 1, Integer::sum));
		return counts;
	}

	/** Drops faded memories. Call about once a second. */
	public void decay(long now) {
		Predicate<MemoryEntry> faded = entry -> !entry.kind.keepWhenFaded && entry.confidence(now) < FORGET_CONFIDENCE;
		this.shortTerm.values().removeIf(faded);
		this.longTerm.values().removeIf(faded);
	}

	/** Capacity limit: when a kind is over its cap, the lowest importance x confidence goes first. */
	private void trim(MemoryKind kind, long now) {
		Map<String, MemoryEntry> tier = this.tier(kind);
		List<MemoryEntry> ofKind = tier.values().stream().filter(entry -> entry.kind == kind).toList();
		int excess = ofKind.size() - kind.capacity;
		if (excess <= 0) {
			return;
		}

		ofKind.stream()
			.sorted(Comparator.comparingDouble(entry -> entry.importance * entry.confidence(now)))
			.limit(excess)
			.forEach(entry -> tier.values().remove(entry));
	}

	/**
	 * Once per in-game day, at bedtime or dusk (DESIGN.md section 6, "Consolidation at bedtime"): important short-term
	 * memories are promoted to long-term, the rest of yesterday's short-term memory is dropped.
	 *
	 * @param believedDay the day number by the Drifter's own sense of time
	 * @return how many memories were promoted, or -1 if it already consolidated today
	 */
	public int consolidate(long believedDay, long now) {
		if (believedDay <= this.lastConsolidatedDay) {
			return -1;
		}

		this.lastConsolidatedDay = believedDay;
		// Wear fades: halved each day.
		for (var entry : this.wear.long2IntEntrySet()) {
			entry.setValue(entry.getIntValue() / 2);
		}

		this.wear.values().removeIf(count -> count <= 0);
		int promoted = 0;
		List<MemoryEntry> toPromote = new ArrayList<>();
		for (MemoryEntry entry : this.shortTerm.values()) {
			if (entry.importance >= PROMOTE_IMPORTANCE && entry.kind.promotesTo() != null) {
				toPromote.add(entry);
			}
		}

		for (MemoryEntry entry : toPromote) {
			MemoryKind target = entry.kind.promotesTo();
			Remembered result = this.remember(target, entry.label, entry.pos, entry.dimension, now, entry.source, entry.importance, entry.entityId);
			result.entry().detail = entry.detail;
			promoted++;
		}

		this.shortTerm.values().removeIf(entry -> now - entry.confirmedAt > KEEP_RECENT_TICKS || toPromote.contains(entry));
		return promoted;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", SAVE_VERSION);
		ListTag entries = new ListTag();
		this.all().forEach(entry -> entries.add(entry.save()));
		tag.put("Entries", entries);
		tag.putLong("LastConsolidatedDay", this.lastConsolidatedDay);
		tag.put("Map", this.map.save());
		tag.putLongArray("Trail", this.trail.toLongArray());
		tag.putLongArray("WearAt", this.wear.keySet().toLongArray());
		tag.putIntArray("Wear", this.wear.values().toIntArray());
		return tag;
	}

	public void load(CompoundTag tag) {
		this.shortTerm.clear();
		this.longTerm.clear();
		// Version 1 is the first format; migrate older versions here when it changes.
		ListTag entries = tag.getList("Entries", Tag.TAG_COMPOUND);
		for (int i = 0; i < entries.size(); i++) {
			MemoryEntry entry = MemoryEntry.load(entries.getCompound(i));
			if (entry != null) {
				this.tier(entry.kind).put(key(entry.kind, entry.label, entry.pos, entry.entityId), entry);
			}
		}

		this.lastConsolidatedDay = tag.getLong("LastConsolidatedDay");
		this.map.load(tag.getList("Map", Tag.TAG_COMPOUND));
		this.trail.clear();
		for (long key : tag.getLongArray("Trail")) {
			this.trail.add(key);
		}

		this.wear.clear();
		long[] wearAt = tag.getLongArray("WearAt");
		int[] wear = tag.getIntArray("Wear");
		for (int i = 0; i < Math.min(wearAt.length, wear.length); i++) {
			this.wear.put(wearAt[i], wear[i]);
		}
	}

	/** Wipes everything (debug command). */
	public void clear() {
		this.shortTerm.clear();
		this.longTerm.clear();
		this.map.clear();
		this.trail.clear();
		this.wear.clear();
		this.lastConsolidatedDay = -1;
	}
}
