package com.dinglebob.drifter.motor;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Pressing movement keys toward a point, whichever way the head faces (forward, back and strafe keys combined). */
public final class Steering {
	private Steering() {
	}

	public static void pressToward(DrifterEntity drifter, Vec3 goal) {
		VirtualKeys keys = drifter.getKeys();
		double dx = goal.x - drifter.getX();
		double dz = goal.z - drifter.getZ();
		boolean move = dx * dx + dz * dz > 0.05 * 0.05;
		float wantedYaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float relative = Mth.wrapDegrees(wantedYaw - drifter.getYRot());
		keys.set(VirtualKeys.Key.FORWARD, move && Math.abs(relative) < 67.5F);
		keys.set(VirtualKeys.Key.BACK, move && Math.abs(relative) > 112.5F);
		keys.set(VirtualKeys.Key.LEFT, move && relative < -22.5F && relative > -157.5F);
		keys.set(VirtualKeys.Key.RIGHT, move && relative > 22.5F && relative < 157.5F);
	}

	public static void stopMoving(DrifterEntity drifter) {
		VirtualKeys keys = drifter.getKeys();
		keys.set(VirtualKeys.Key.FORWARD, false);
		keys.set(VirtualKeys.Key.BACK, false);
		keys.set(VirtualKeys.Key.LEFT, false);
		keys.set(VirtualKeys.Key.RIGHT, false);
		keys.set(VirtualKeys.Key.SPRINT, false);
		keys.set(VirtualKeys.Key.JUMP, false);
	}
}
