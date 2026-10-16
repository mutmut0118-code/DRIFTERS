package com.dinglebob.drifter.motor;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Pressing movement keys toward a point, whichever way the head faces (forward, back and strafe keys combined). */
public final class Steering {
	private Steering() {
	}

	/** Presses toward {@code goal}; the keys are aimed again after the head turns this tick ({@link #reaim}). */
	public static void pressToward(DrifterEntity drifter, Vec3 goal) {
		drifter.steeringToward(goal);
		aim(drifter, goal);
	}

	/**
	 * After the head has turned this tick: the direction keys still held toward the goal pressed this tick are aimed
	 * from where it faces now, as a player's fingers follow what they see (found in testing: keys chosen before a fast
	 * turn sent it the other way off a mountain ledge mid-jump). Keys a task let go of stay up.
	 */
	public static void reaim(DrifterEntity drifter, Vec3 goal) {
		VirtualKeys keys = drifter.getKeys();
		if (keys.isDown(VirtualKeys.Key.FORWARD) || keys.isDown(VirtualKeys.Key.BACK) || keys.isDown(VirtualKeys.Key.LEFT)
			|| keys.isDown(VirtualKeys.Key.RIGHT)) {
			aim(drifter, goal);
		}
	}

	private static void aim(DrifterEntity drifter, Vec3 goal) {
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

	/**
	 * Is a step in this direction safe: ground within 3 blocks below (or water), and no lava or fire? Used by fighting
	 * footwork and running away, which steer without the pathfinder.
	 */
	public static boolean safeStep(DrifterEntity drifter, Vec3 direction) {
		return safeStep(drifter, direction, 0.9);
	}

	/** The same check {@code ahead} blocks out (a sprint carries it further than one step before it can turn). */
	public static boolean safeStep(DrifterEntity drifter, Vec3 direction, double ahead) {
		Level level = drifter.level();
		BlockPos cell = BlockPos.containing(drifter.getX() + direction.x * ahead, drifter.getY() + 0.1, drifter.getZ() + direction.z * ahead);
		// Powder snow (it has no collision for a check like this one) sinks it without leather boots (found in the user's
		// game: running from a creeper into powder snow, it sank in and the creeper caught it).
		boolean boots = drifter.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(net.minecraft.world.item.Items.LEATHER_BOOTS);
		for (int down = 0; down <= 3; down++) {
			BlockPos pos = cell.below(down);
			if (level.getFluidState(pos).is(FluidTags.LAVA) || level.getBlockState(pos).is(Blocks.FIRE)
				|| !boots && down <= 1 && level.getBlockState(pos).is(Blocks.POWDER_SNOW)) {
				return false;
			}

			if (down > 0 && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty()) {
				return true;
			}
		}

		return false;
	}

	/** A wall it can't just walk or jump over in that direction (two blocks high at its feet). */
	public static boolean wallAhead(DrifterEntity drifter, Vec3 direction) {
		Level level = drifter.level();
		BlockPos cell = BlockPos.containing(drifter.getX() + direction.x * 0.9, drifter.getY() + 0.1, drifter.getZ() + direction.z * 0.9);
		boolean feet = !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty();
		BlockPos head = cell.above();
		boolean high = !level.getBlockState(head).getCollisionShape(level, head).isEmpty();
		return feet && high;
	}

	/**
	 * No face-down sprint-swimming yet (it comes with the rest of section 10.10, milestone 8). In that mode the body
	 * follows where the head points, so holding jump while looking up launched it out of the water and looking down
	 * sank it (found in testing). In water it lets go of sprint, and if it's still sprinting (sprint carries on into
	 * the water, as for a player) it lets go of the movement keys for a tick, which is how a player stops.
	 */
	public static void noSprintInWater(DrifterEntity drifter) {
		if (!drifter.isInWater()) {
			return;
		}

		VirtualKeys keys = drifter.getKeys();
		keys.set(VirtualKeys.Key.SPRINT, false);
		if (drifter.isSprinting() || drifter.isSwimming()) {
			keys.set(VirtualKeys.Key.FORWARD, false);
			keys.set(VirtualKeys.Key.BACK, false);
			keys.set(VirtualKeys.Key.LEFT, false);
			keys.set(VirtualKeys.Key.RIGHT, false);
		}
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
