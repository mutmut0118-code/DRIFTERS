package com.dinglebob.drifter.executor.task;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.motor.CameraModel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Turns the Drifter's head onto a block (or one face of it) through the camera model, then confirms the crosshair
 * really is on it. Used by every task that touches a block, so nothing is ever touched without looking at it.
 */
final class BlockAim {
	enum Result {
		AIMING, ON_TARGET, UNREACHABLE
	}

	/** Re-aims this many times (e.g. after an overshoot settles slightly off) before giving up. */
	private static final int MAX_REAIMS = 4;
	/** Ticks to keep looking for a visible face before giving up. */
	private static final int MAX_NO_VIEW_TICKS = 10;

	private final DrifterEntity drifter;
	private final BlockPos pos;
	@Nullable
	private final Direction face;
	private final CameraModel.TurnSpeed speed;
	@Nullable
	private Vec3 point;
	private int reaims;
	private int noViewTicks;

	BlockAim(DrifterEntity drifter, BlockPos pos, @Nullable Direction face, CameraModel.TurnSpeed speed) {
		this.drifter = drifter;
		this.pos = pos;
		this.face = face;
		this.speed = speed;
	}

	boolean crosshairOnTarget() {
		return this.face == null ? Reach.isLookingAt(this.drifter, this.pos) : Reach.isLookingAt(this.drifter, this.pos, this.face);
	}

	Result tick() {
		if (this.point != null && this.drifter.getCamera().isSettled()) {
			if (this.crosshairOnTarget()) {
				return Result.ON_TARGET;
			}

			// Settled but not on it (moved, or something got in the way): pick a new point.
			this.point = null;
			if (++this.reaims > MAX_REAIMS) {
				return Result.UNREACHABLE;
			}
		}

		if (this.point == null) {
			Optional<Vec3> found = this.face == null ? Reach.aimPoint(this.drifter, this.pos) : Reach.aimPoint(this.drifter, this.pos, this.face);
			if (found.isEmpty()) {
				return ++this.noViewTicks > MAX_NO_VIEW_TICKS ? Result.UNREACHABLE : Result.AIMING;
			}

			this.noViewTicks = 0;
			this.point = found.get();
			this.drifter.getCamera().lookAt(this.point, this.speed);
		}

		return Result.AIMING;
	}

	/** Forget the current aim so the next tick aims again (for example after the crosshair slipped off). */
	void reset() {
		this.point = null;
	}
}
