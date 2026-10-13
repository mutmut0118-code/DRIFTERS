package com.dinglebob.drifter.memory;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * One memory (DESIGN.md section 6, "Entry format"): kind, position and dimension, when it was recorded and last
 * confirmed, confidence, how it was learned, and importance. Confidence fades with time from the last confirmation.
 */
public class MemoryEntry {
	public final MemoryKind kind;
	/** What it is: a block or entity type id ("minecraft:iron_ore"), a player name, a damage type. */
	public String label;
	public Vec3 pos;
	public final ResourceLocation dimension;
	public final long recordedAt;
	public long confirmedAt;
	/** Confidence at the moment of the last confirmation; fades from there. */
	public float baseConfidence;
	public MemorySource source;
	public float importance;
	/** Free-form detail: what a player was wearing, a chest's contents summary. */
	public String detail;
	@Nullable
	public UUID entityId;

	public MemoryEntry(MemoryKind kind, String label, Vec3 pos, ResourceLocation dimension, long now, MemorySource source, float importance) {
		this.kind = kind;
		this.label = label;
		this.pos = pos;
		this.dimension = dimension;
		this.recordedAt = now;
		this.confirmedAt = now;
		this.baseConfidence = 1.0F;
		this.source = source;
		this.importance = importance;
		this.detail = "";
	}

	public float confidence(long now) {
		if (this.kind.halfLifeTicks <= 0) {
			return this.baseConfidence;
		}

		double age = Math.max(0L, now - this.confirmedAt);
		return (float)(this.baseConfidence * Math.pow(0.5, age / this.kind.halfLifeTicks));
	}

	public BlockPos blockPos() {
		return BlockPos.containing(this.pos);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Kind", this.kind.name());
		tag.putString("Label", this.label);
		tag.putDouble("X", this.pos.x);
		tag.putDouble("Y", this.pos.y);
		tag.putDouble("Z", this.pos.z);
		tag.putString("Dim", this.dimension.toString());
		tag.putLong("Recorded", this.recordedAt);
		tag.putLong("Confirmed", this.confirmedAt);
		tag.putFloat("Confidence", this.baseConfidence);
		tag.putString("Source", this.source.name());
		tag.putFloat("Importance", this.importance);
		if (!this.detail.isEmpty()) {
			tag.putString("Detail", this.detail);
		}

		if (this.entityId != null) {
			tag.putUUID("Entity", this.entityId);
		}

		return tag;
	}

	@Nullable
	public static MemoryEntry load(CompoundTag tag) {
		try {
			MemoryKind kind = MemoryKind.valueOf(tag.getString("Kind"));
			ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dim"));
			if (dimension == null) {
				return null;
			}

			MemoryEntry entry = new MemoryEntry(kind, tag.getString("Label"), new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
				dimension, tag.getLong("Recorded"), MemorySource.valueOf(tag.getString("Source")), tag.getFloat("Importance"));
			entry.confirmedAt = tag.getLong("Confirmed");
			entry.baseConfidence = tag.getFloat("Confidence");
			entry.detail = tag.getString("Detail");
			if (tag.hasUUID("Entity")) {
				entry.entityId = tag.getUUID("Entity");
			}

			return entry;
		} catch (IllegalArgumentException e) {
			// Unknown kind or source from a newer or older save format: drop it rather than fail the load.
			return null;
		}
	}
}
