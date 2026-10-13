package com.dinglebob.drifter.brain;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;

/**
 * Failed targets go on a short blacklist so it doesn't retry the same unreachable ore or tree forever (DESIGN.md
 * section 8, "Failure handling"). Not saved: a fresh look after a reload is fine.
 */
public class Blacklist {
	/** A few minutes. */
	public static final long DURATION_TICKS = 20 * 60 * 3;

	private final Map<Long, Long> until = new HashMap<>();

	public void add(BlockPos pos, long now) {
		this.until.put(pos.asLong(), now + DURATION_TICKS);
	}

	public boolean contains(BlockPos pos, long now) {
		Long expiry = this.until.get(pos.asLong());
		if (expiry == null) {
			return false;
		}

		if (expiry <= now) {
			this.until.remove(pos.asLong());
			return false;
		}

		return true;
	}

	public int size() {
		return this.until.size();
	}

	public void clear() {
		this.until.clear();
	}
}
