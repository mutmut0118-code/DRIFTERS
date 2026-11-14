package com.dinglebob.drifterciv;

import java.util.UUID;

/**
 * Paper towns (docs/superpowers/specs/2026-11-14-lod-towns-design.md): a rough sketch of every planned place, for the
 * far view. So far only the seed a place's choices are made from, shared by its sketch and by the real town.
 */
final class Sketches {
	private Sketches() {
	}

	/** The seed of a planned place's choices (its shape, the ways its roads go, its houses): the same every time. */
	static long seedOf(long worldSeed, UUID place) {
		return worldSeed ^ place.getMostSignificantBits() * 31 ^ place.getLeastSignificantBits();
	}
}
