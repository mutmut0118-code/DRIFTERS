package com.dinglebob.drifter.brain.activity;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Passing through (user's request, 2026-10-17): a Drifter in full kit on its way somewhere far keeps walking one way;
 * once a player has met it and it's well out of their sight, it's gone for good (over the horizon). One nobody meets
 * goes after a long while too.
 */
public class PassThroughActivity extends Activity {
	/** Close enough to count as met. */
	private static final double MET = 48.0;
	/** Out of sight: gone. */
	private static final double GONE = 96.0;
	/** Nobody met it in this long: gone too (it walked on somewhere else). */
	private static final long UNMET_TICKS = 20L * 60 * 15;

	private int stuck;

	public PassThroughActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "pass through";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 5;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		return false;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (status != Status.SUCCESS && ++this.stuck % 2 == 0) {
			// The way's blocked (water, a cliff): it bears off a little.
			this.drifter.turnPassHeading(this.drifter.getRandom().nextBoolean() ? 45.0F : -45.0F);
		}
	}

	@Override
	protected Status next() {
		double nearest = Double.MAX_VALUE;
		for (Player player : this.drifter.level().players()) {
			if (!player.isSpectator() && com.dinglebob.drifter.social.SocialWatcher.isRealPlayer(player)) {
				nearest = Math.min(nearest, player.distanceTo(this.drifter));
			}
		}

		if (nearest < MET) {
			this.drifter.markMetOnItsWay();
		}

		long now = this.drifter.level().getGameTime();
		if (this.drifter.wasMetOnItsWay() && nearest > GONE || now - this.drifter.passingSince() > UNMET_TICKS && nearest > GONE * 1.5) {
			this.drifter.getMemory().thoughts().add(now, "over the horizon, on its way");
			com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter] {} passed through and went on its way", this.drifter.getName().getString());
			this.drifter.discard();
			return Status.SUCCESS;
		}

		double yaw = Math.toRadians(this.drifter.passHeading());
		Vec3 from = this.drifter.position();
		Vec3 step = from.add(-Math.sin(yaw) * 32.0, 0.0, Math.cos(yaw) * 32.0);
		BlockPos ground = this.drifter.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(step));
		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(ground), 4.0, "walk on its way"));
	}

	/** The goal: while it's passing through, this comes first after staying alive. */
	public static final class Goal extends Goals.Goal {
		@Override
		public String id() {
			return "passthrough";
		}

		@Override
		public String name(Goals.Context context) {
			return "pass through";
		}

		@Override
		public double urgency(Goals.Context context) {
			return context.drifter().passingThrough() ? 0.92 : 0.0;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public double risk(Goals.Context context) {
			return 1.0;
		}

		@Override
		public Activity start(Goals.Context context) {
			return new PassThroughActivity(context.drifter());
		}
	}
}
