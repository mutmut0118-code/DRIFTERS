package com.dinglebob.drifter.encounter;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;

import net.minecraft.world.phys.Vec3;

/**
 * Staying put a while, crouched or standing, looking where {@code look} says (read again every second; null: wherever
 * it was looking): a captive kneeling, a scout watching, someone waiting for an answer.
 */
final class Hold extends Task {
	private final String what;
	private final int ticks;
	private final boolean crouch;
	private final Supplier<Vec3> look;
	private int t;

	Hold(DrifterEntity drifter, String what, int ticks, boolean crouch, Supplier<@Nullable Vec3> look) {
		super(drifter);
		this.what = what;
		this.ticks = ticks;
		this.crouch = crouch;
		this.look = look;
	}

	@Override
	public String describe() {
		return this.what;
	}

	@Override
	protected int timeoutTicks() {
		return this.ticks + 20;
	}

	@Override
	protected Status onTick() {
		VirtualKeys keys = this.drifter.getKeys();
		keys.releaseAll();
		keys.set(VirtualKeys.Key.SNEAK, this.crouch);
		if (this.t % 20 == 0) {
			Vec3 at = this.look.get();
			if (at != null) {
				this.drifter.getCamera().track(at, CameraModel.TurnSpeed.IDLE);
			}
		}

		return ++this.t >= this.ticks ? Status.SUCCESS : Status.RUNNING;
	}

	@Override
	protected void onStop() {
		this.drifter.getKeys().releaseAll();
	}
}
