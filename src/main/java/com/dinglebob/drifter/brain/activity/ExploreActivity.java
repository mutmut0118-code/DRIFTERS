package com.dinglebob.drifter.brain.activity;

import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.NavigateTask;

import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Exploring (DESIGN.md section 10.8): looks around first, then heads somewhere, toward a place it remembers when
 * it has one in mind (trees it saw earlier), otherwise toward ground it hasn't explored yet. One leg of 20 to 32
 * blocks; the selector decides whether to keep going.
 * TODO(milestone 8): climb high points, follow rivers and ridgelines, mark the way.
 */
public class ExploreActivity extends Activity {
	private static final int MIN_LEG = 20;
	private static final int MAX_LEG = 32;
	private static final int DIRECTION_TRIES = 8;

	private final String reason;
	@Nullable
	private final Vec3 toward;
	@Nullable
	private final Predicate<DrifterEntity> found;
	/** 0: look around, 1: walk a leg, 2: look around again, 3: done. */
	private int phase;

	public ExploreActivity(DrifterEntity drifter, String reason, @Nullable Vec3 toward, @Nullable Predicate<DrifterEntity> found) {
		super(drifter);
		this.reason = reason;
		this.toward = toward;
		this.found = found;
	}

	@Override
	protected boolean finishedEarly() {
		// Checked once a second: stops as soon as it has seen what it was looking for.
		if (this.found != null && this.ticksRunning() % 20 == 10 && this.found.test(this.drifter)) {
			this.think("found what it was looking for (" + this.reason + ")");
			return true;
		}

		return false;
	}

	@Override
	public String describe() {
		return "explore (" + this.reason + ")";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 90;
	}

	@Override
	protected Status next() {
		// A failed walk still got it somewhere new: it just carries on with the next phase.
		return switch (this.phase++) {
			case 0 -> this.begin(new LookAroundTask(this.drifter));
			case 1 -> this.begin(new NavigateTask(this.drifter, this.toward != null ? this.toward : this.pickDirection(),
				this.toward != null ? 4.0 : 6.0, "explore " + this.reason));
			case 2 -> this.begin(new LookAroundTask(this.drifter));
			default -> Status.SUCCESS;
		};
	}

	/** A point 20 to 32 blocks away, preferring a direction whose ground it hasn't explored. */
	private Vec3 pickDirection() {
		Vec3 here = this.drifter.position();
		Vec3 fallback = null;
		for (int i = 0; i < DIRECTION_TRIES; i++) {
			double angle = this.drifter.getRandom().nextDouble() * Math.PI * 2.0;
			double distance = Mth.nextInt(this.drifter.getRandom(), MIN_LEG, MAX_LEG);
			Vec3 point = here.add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
			if (fallback == null) {
				fallback = point;
			}

			if (this.drifter.getMemory().map().get(new ChunkPos(Mth.floor(point.x) >> 4, Mth.floor(point.z) >> 4)) == null) {
				return point;
			}
		}

		return fallback;
	}
}
