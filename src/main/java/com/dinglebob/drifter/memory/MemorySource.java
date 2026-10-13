package com.dinglebob.drifter.memory;

/** How a memory was learned (DESIGN.md section 6, entry format). */
public enum MemorySource {
	SEEN, HEARD, INFERRED, TOLD;

	/** Seeing something beats hearing it, which beats inferring or being told. */
	public boolean atLeastAsGoodAs(MemorySource other) {
		return this.ordinal() <= other.ordinal();
	}
}
