package com.dinglebob.drifter.perception;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Lets Drifters hear explosions out to 64 blocks; vanilla game events only reach 16. Called from ExplosionMixin. */
public final class ExplosionHearing {
	private ExplosionHearing() {
	}

	public static void onExplosion(ServerLevel level, Vec3 center) {
		onExplosion(level, center, null);
	}

	/** The same, with what set it off (a watching Drifter may learn its item explodes: PowerWatch). */
	public static void onExplosion(ServerLevel level, Vec3 center, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity cause) {
		try {
			com.dinglebob.drifter.gear.PowerWatch.explosion(level, center, cause);
		} catch (RuntimeException e) {
			com.dinglebob.drifter.Drifter.LOGGER.error("[Drifter] couldn't watch an explosion for item powers", e);
		}

		double radius = HeardSound.Kind.EXPLOSION.radius;
		for (DrifterEntity drifter : level.getEntitiesOfClass(DrifterEntity.class, new AABB(center, center).inflate(radius))) {
			drifter.getSenses().hearing().hearExplosion(center);
		}
	}
}
