package com.dinglebob.drifter.whim;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;

/**
 * What a Drifter remembers of its whims: when it last did each (cooldowns), when it last did any, and how many it has
 * done. Saved with the brain.
 */
public final class WhimState {
	private final Map<String, Long> lastDone = new HashMap<>();
	public long lastAny = Long.MIN_VALUE / 2;
	public int done;

	public long lastDone(String id) {
		return this.lastDone.getOrDefault(id, Long.MIN_VALUE / 2);
	}

	public void did(String id, long now) {
		this.lastDone.put(id, now);
		this.lastAny = now;
		this.done++;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		CompoundTag last = new CompoundTag();
		this.lastDone.forEach(last::putLong);
		tag.put("LastDone", last);
		tag.putLong("LastAny", this.lastAny);
		tag.putInt("Done", this.done);
		return tag;
	}

	public void load(CompoundTag tag) {
		this.lastDone.clear();
		CompoundTag last = tag.getCompound("LastDone");
		for (String key : last.getAllKeys()) {
			this.lastDone.put(key, last.getLong(key));
		}

		this.lastAny = tag.contains("LastAny") ? tag.getLong("LastAny") : Long.MIN_VALUE / 2;
		this.done = tag.getInt("Done");
	}
}
