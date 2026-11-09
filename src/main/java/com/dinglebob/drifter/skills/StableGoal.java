package com.dinglebob.drifter.skills;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.RideTask;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A horse kept in a stall (milestone 23 C5; the user, 2026-11-06: "bees and stables"): a town's stable has its people's
 * horses tied up in it. Off on a long trip, it walks to its horse first and unties it (the lead comes off with it), and
 * rides ({@code NavigateTask} rides its own saddled horse when it's close); back home of an evening, it rides it into its
 * stall, gets off, and ties it up again with its lead.
 */
public final class StableGoal extends Goals.Goal {
	/** A trip this long or longer is worth fetching the horse for. */
	public static final double RIDE_TRIP = 150.0;
	/** Its horse this far from it at most, to go and get it (or bring it back). */
	private static final double NEAR = 96.0;

	@Override
	public String id() {
		return "stable";
	}

	@Override
	public String name(Goals.Context context) {
		return "put its horse in the stable";
	}

	/** Its horse, alive and loaded, or null. */
	@Nullable
	static AbstractHorse horse(DrifterEntity drifter) {
		java.util.UUID id = drifter.getDrifterBrain().horse();
		if (id == null || !(drifter.level() instanceof ServerLevel level) || !(level.getEntity(id) instanceof AbstractHorse horse) || !horse.isAlive()) {
			return null;
		}

		return horse;
	}

	/**
	 * Before a long trip: to its horse in its stall and untie it (then the walk rides it); null if there's no horse kept,
	 * it's far, or it's out already.
	 */
	@Nullable
	public static Task fetch(DrifterEntity drifter, double trip) {
		BlockPos stall = drifter.getDrifterBrain().stall();
		AbstractHorse horse = trip < RIDE_TRIP || stall == null || drifter.isPassenger() ? null : horse(drifter);
		if (horse == null || !horse.isLeashed() || !horse.isSaddled() || horse.distanceTo(drifter) > NEAR) {
			return null;
		}

		if (horse.distanceTo(drifter) > 3.0) {
			return new NavigateTask(drifter, horse.position(), 2.0, "go and get its horse", NavigateTask.Pace.STROLL);
		}

		return new Task(drifter) {
			@Override
			public String describe() {
				return "untie its horse";
			}

			@Override
			protected Status onTick() {
				this.drifter.getCamera().track(horse.getEyePosition(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
				// (The lead off the post, back in its pack: as a player clicks the knot.)
				horse.dropLeash(true, false);
				this.drifter.getInventory().add(new net.minecraft.world.item.ItemStack(Items.LEAD));
				return Status.SUCCESS;
			}
		};
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		BlockPos stall = drifter.getDrifterBrain().stall();
		AbstractHorse horse = stall == null || context.threat().urgent() ? null : horse(drifter);
		if (horse == null || horse.isLeashed() || horse.distanceTo(drifter) > NEAR || !drifter.blockPosition().closerThan(stall, NEAR)
			|| horse.blockPosition().closerThan(stall, 2.5) && !drifter.isPassenger()) {
			return 0.0;
		}

		// Back home with it of an evening (or with nowhere far to go): into its stall.
		long time = context.time();
		boolean evening = time >= Schedule.shelterThinking(drifter) || Schedule.isNight(time);
		return evening ? 0.6 : drifter.isPassenger() && drifter.blockPosition().closerThan(stall, 48.0) ? 0.4 : 0.0;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 20;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		BlockPos stall = drifter.getDrifterBrain().stall();
		AbstractHorse horse = horse(drifter);
		if (stall == null || horse == null) {
			return null;
		}

		return new Activity(drifter) {
			private boolean rode;
			private boolean tied;

			@Override
			public String describe() {
				return "put its horse in the stable";
			}

			@Override
			protected int timeoutTicks() {
				return 20 * 120;
			}

			@Override
			protected Status next() {
				if (!this.rode) {
					this.rode = true;
					return this.begin(new RideTask(this.drifter, horse, Vec3.atBottomCenterOf(stall), 1.0));
				}

				if (this.tied) {
					return Status.SUCCESS;
				}

				this.tied = true;
				if (this.drifter.getVehicle() == horse) {
					this.drifter.stopRiding();
				}

				// Tied to the stall's post with its lead (one it has: the one it untied, or any).
				BlockPos post = post(this.drifter.level(), stall);
				int slot = -1;
				for (int s = 0; s < this.drifter.getInventory().getContainerSize(); s++) {
					if (this.drifter.getInventory().getItem(s).is(Items.LEAD)) {
						slot = s;
						break;
					}
				}

				if (post == null || slot < 0 || !com.dinglebob.drifter.hands.Reach.canReach(this.drifter, post) || horse.distanceTo(this.drifter) > 8.0) {
					this.think("couldn't tie its horse up");
					return Status.SUCCESS;
				}

				this.drifter.getInventory().removeItem(slot, 1);
				horse.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(this.drifter.level(), post), true);
				this.think("put its horse in the stable");
				return Status.SUCCESS;
			}
		};
	}

	/** The fence its stall's horse is tied to (the nearest within three blocks of the stall), or null. */
	@Nullable
	private static BlockPos post(net.minecraft.world.level.Level level, BlockPos stall) {
		BlockPos best = null;
		for (BlockPos pos : BlockPos.betweenClosed(stall.offset(-3, -1, -3), stall.offset(3, 2, 3))) {
			if (level.getBlockState(pos).is(BlockTags.FENCES) && (best == null || pos.distSqr(stall) < best.distSqr(stall))) {
				best = pos.immutable();
			}
		}

		return best;
	}
}
