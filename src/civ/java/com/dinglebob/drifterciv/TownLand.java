package com.dinglebob.drifterciv;

import net.minecraft.server.level.ServerLevel;

/**
 * The ground a town's street layer asks about: the real land (a level), or the land as the world's generator says it
 * will be, for a place nobody's been near yet (paper towns: a sketch of each planned place, drawn into a far-view mod
 * before the town's really made). The same street-laying code reads either, so a sketch's streets and the real town's
 * come out alike.
 */
interface TownLand {
	/** The height of the ground's top block here. */
	int ground(int x, int z);

	/** Water (or other liquid) on the ground here. */
	boolean wet(int x, int z);

	/** The real land. */
	static TownLand of(ServerLevel level) {
		return new TownLand() {
			@Override
			public int ground(int x, int z) {
				return TownBuilder.ground(level, x, z);
			}

			@Override
			public boolean wet(int x, int z) {
				return TownBuilder.wet(level, x, z);
			}
		};
	}
}
