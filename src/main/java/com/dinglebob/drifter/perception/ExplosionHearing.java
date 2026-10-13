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
		double radius = HeardSound.Kind.EXPLOSION.radius;
		for (DrifterEntity drifter : level.getEntitiesOfClass(DrifterEntity.class, new AABB(center, center).inflate(radius))) {
			drifter.getSenses().hearing().hearExplosion(center);
		}
	}
}
