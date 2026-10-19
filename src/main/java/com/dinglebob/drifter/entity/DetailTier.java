package com.dinglebob.drifter.entity;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * How much detail a Drifter runs at (DESIGN.md section 22, "Detail tiers"), by the players round it. Only what nobody
 * close can notice is cut: mannerisms, a few of the eye rays, how often it re-thinks its plans. Movement, path
 * following, danger checks and combat always run every tick, in every tier. Measured with `/drifter perf` (10 Drifters
 * at full detail came to about 2 to 3 ms a tick, so the cuts are mild: a far-away Drifter still plays properly).
 */
public enum DetailTier {
	/** A player within {@link #FULL_RANGE} blocks, or one looking its way: everything. */
	FULL(6, 20, 1),
	/**
	 * No player within {@link #FULL_RANGE}: no mannerisms, fewer eye rays, re-thinks every 1.5 s. (Milestone 16, a thousand
	 * Drifters: 4 rays, was 6.)
	 */
	REDUCED(4, 30, 1),
	/**
	 * No player within {@link #REDUCED_RANGE}: fewer eye rays still (1, was 4), far sight every fourth tick, re-ranks what
	 * it's watching every second and updates it every fourth tick, re-thinks every 2 s. Nobody's there to see the difference;
	 * a fight puts it back to full.
	 */
	FAR(1, 40, 2);

	public static final double FULL_RANGE = 48.0;
	public static final double REDUCED_RANGE = 128.0;
	/** A player looking within this angle of it (degrees from the middle of their view) counts as seeing it. */
	private static final double VIEW_HALF_ANGLE = 60.0;

	/** Eye rays a tick (not counting far sight, which always runs: landmarks are how it finds its way). */
	public final int blockRays;
	/** Ticks between re-scoring its goals (danger is still checked every 5). */
	public final int rescoreTicks;
	/** How much slower than full detail it re-ranks and updates what it's watching, and casts far sight. */
	public final int senseSlowdown;

	DetailTier(int blockRays, int rescoreTicks, int senseSlowdown) {
		this.blockRays = blockRays;
		this.rescoreTicks = rescoreTicks;
		this.senseSlowdown = senseSlowdown;
	}

	public boolean mannerisms() {
		return this == FULL;
	}

	/** From the players round it (in its own dimension); {@code forced} from `/drifter tier`, or null for automatic. */
	public static DetailTier of(DrifterEntity drifter, @Nullable DetailTier forced) {
		if (forced != null) {
			return forced;
		}

		double nearest = Double.MAX_VALUE;
		boolean watched = false;
		for (ServerPlayer player : ((net.minecraft.server.level.ServerLevel)drifter.level()).players()) {
			if (player.isSpectator()) {
				continue;
			}

			double distance = player.distanceTo(drifter);
			nearest = Math.min(nearest, distance);
			if (distance < REDUCED_RANGE && !watched) {
				Vec3 toDrifter = drifter.getEyePosition().subtract(player.getEyePosition()).normalize();
				watched = player.getLookAngle().dot(toDrifter) > Math.cos(Math.toRadians(VIEW_HALF_ANGLE)) && player.hasLineOfSight(drifter);
			}
		}

		if (nearest < FULL_RANGE || watched) {
			return FULL;
		}

		return nearest < REDUCED_RANGE ? REDUCED : FAR;
	}
}
