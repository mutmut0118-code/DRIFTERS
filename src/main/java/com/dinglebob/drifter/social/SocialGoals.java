package com.dinglebob.drifter.social;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.Steering;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The behavior modes that don't hurt anyone (DESIGN.md section 14): watching a player it has just noticed, keeping away
 * from one it fears, and the warning (the telegraph rule) before any hostility toward one who never wronged it.
 */
public final class SocialGoals {
	private SocialGoals() {
	}

	public static List<Goals.Goal> all() {
		return List.of(new WatchPlayer(), new WarnPlayer(), new AvoidPlayer());
	}

	@Nullable
	private static Player pressing(Goals.Context context, Mode mode) {
		Player player = Mode.mostPressing(context.drifter());
		return player != null && Mode.toward(context.drifter(), player) == mode ? player : null;
	}

	/** "Watching: detected; turns to look, pauses, then decides" (section 17: "a short look up and down"). */
	public static final class WatchPlayer extends Goals.Goal {
		@Override
		public String id() {
			return "watchplayer";
		}

		@Override
		public String name(Goals.Context context) {
			return "look a player over";
		}

		@Override
		public double urgency(Goals.Context context) {
			return pressing(context, Mode.WATCHING) != null ? 0.7 : 0.0;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public boolean urgent(Goals.Context context) {
			return true;
		}

		@Override
		public int minCommitTicks() {
			return 0;
		}

		@Override
		public Activity start(Goals.Context context) {
			Player player = pressing(context, Mode.WATCHING);
			return player == null ? null : new LookOver(context.drifter(), player);
		}
	}

	/** Stops and looks someone over: face, feet, face again. */
	private static final class LookOver extends Activity {
		private final Player player;
		private int ticks;

		LookOver(DrifterEntity drifter, Player player) {
			super(drifter);
			this.player = player;
		}

		@Override
		public String describe() {
			return "look " + this.player.getGameProfile().getName() + " over";
		}

		@Override
		protected Status next() {
			if (this.ticks == 0) {
				this.think("noticed " + this.player.getGameProfile().getName() + ": looking them over");
			}

			Steering.stopMoving(this.drifter);
			int t = this.ticks++;
			Vec3 at = t >= 18 && t < 30 ? this.player.position() : this.player.getEyePosition();
			this.drifter.getCamera().lookAt(at, CameraModel.TurnSpeed.NORMAL);
			return t >= Mode.WATCH_TICKS || !this.player.isAlive() ? Status.SUCCESS : Status.RUNNING;
		}
	}

	/** "Avoiding: keeps out of their way, changes route, hides if Fear is high." */
	public static final class AvoidPlayer extends Goals.Goal {
		/** Closer than this, it moves off. */
		private static final double TOO_CLOSE = 12.0;

		@Override
		public String id() {
			return "avoidplayer";
		}

		@Override
		public String name(Goals.Context context) {
			return "keep away from a player";
		}

		@Override
		public double urgency(Goals.Context context) {
			Player player = pressing(context, Mode.AVOIDING);
			if (player == null || player.distanceTo(context.drifter()) > TOO_CLOSE) {
				return 0.0;
			}

			Relationship r = context.drifter().getSocial().get(player.getUUID());
			return 0.55 + (r == null ? 0.0 : r.fear / 100.0 * 0.3);
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 4;
		}

		@Override
		public Activity start(Goals.Context context) {
			Player player = pressing(context, Mode.AVOIDING);
			if (player == null) {
				return null;
			}

			DrifterEntity drifter = context.drifter();
			Relationship r = drifter.getSocial().get(player.getUUID());
			boolean scared = r != null && r.fear > 60.0;
			Vec3 away = drifter.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() < 1.0E-4) {
				away = Vec3.directionFromRotation(0.0F, drifter.getYRot());
			}

			Vec3 target = drifter.position().add(away.normalize().scale(18.0));
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "keeping away from " + player.getGameProfile().getName());
			return new Activity(drifter) {
				private boolean walked;

				@Override
				public String describe() {
					return "keep away from " + player.getGameProfile().getName();
				}

				@Override
				protected Status next() {
					if (this.walked) {
						return Status.SUCCESS;
					}

					this.walked = true;
					return this.begin(new NavigateTask(this.drifter, target, 3.0, "keep away from " + player.getGameProfile().getName(),
						scared ? NavigateTask.Pace.TRAVEL : NavigateTask.Pace.STROLL));
				}
			};
		}
	}

	/**
	 * The telegraph rule: "switching to a weapon, facing the player and holding still for 1 to 2 s. If the player backs
	 * off, the Drifter backs off too." Their response decides whether hostility may follow.
	 */
	public static final class WarnPlayer extends Goals.Goal {
		/** It warns someone this close (inside its base, or coming at it). */
		private static final double WARN_RANGE = 14.0;

		@Override
		public String id() {
			return "warnplayer";
		}

		@Override
		public String name(Goals.Context context) {
			return "warn a player off";
		}

		@Nullable
		private static Player target(Goals.Context context) {
			Player player = pressing(context, Mode.WARNING);
			return player != null && player.distanceTo(context.drifter()) < WARN_RANGE && !context.drifter().getSocial().watcher().warnedLately(player)
				? player : null;
		}

		@Override
		public double urgency(Goals.Context context) {
			return target(context) != null ? 0.75 : 0.0;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public boolean urgent(Goals.Context context) {
			return true;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 3;
		}

		@Override
		public Activity start(Goals.Context context) {
			Player player = target(context);
			return player == null ? null : new Warn(context.drifter(), player);
		}
	}

	/** Weapon out, facing them, still; then reads what they did. */
	private static final class Warn extends Activity {
		/** How long it holds the stare (1 to 2 s). */
		private static final int HOLD_TICKS = 35;

		private final Player player;
		private boolean armed;
		private int held;
		private double startDistance;
		private boolean backingOff;

		Warn(DrifterEntity drifter, Player player) {
			super(drifter);
			this.player = player;
		}

		@Override
		public String describe() {
			return "warn " + this.player.getGameProfile().getName() + " off";
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 15;
		}

		@Override
		protected Status next() {
			if (!this.player.isAlive() || this.player.isRemoved()) {
				return Status.SUCCESS;
			}

			if (this.backingOff) {
				return Status.SUCCESS;
			}

			if (!this.armed) {
				this.armed = true;
				this.think("warning " + this.player.getGameProfile().getName() + ": weapon out, staring");
				int slot = com.dinglebob.drifter.hands.Combat.bestWeaponSlot(this.drifter);
				if (slot >= 0) {
					ItemStack weapon = this.drifter.getInventory().getItem(slot);
					return this.begin(new SelectItemTask(this.drifter, stack -> stack == weapon, "a weapon"));
				}
			}

			if (this.held == 0) {
				this.startDistance = this.player.distanceTo(this.drifter);
			}

			// Holding still, eyes on them.
			Steering.stopMoving(this.drifter);
			this.drifter.getCamera().lookAt(this.player.getEyePosition(), CameraModel.TurnSpeed.NORMAL);
			if (++this.held < HOLD_TICKS) {
				return Status.RUNNING;
			}

			// They backed off (stepped back a couple of blocks or left): it backs off too. Stayed or came on: ignored.
			double distance = this.player.distanceTo(this.drifter);
			boolean heeded = distance > this.startDistance + 2.0 || distance > 16.0;
			this.drifter.getSocial().watcher().warned(this.player, !heeded);
			if (!heeded) {
				return Status.SUCCESS;
			}

			this.backingOff = true;
			Vec3 away = this.drifter.position().subtract(this.player.position()).multiply(1.0, 0.0, 1.0);
			Vec3 target = away.lengthSqr() < 1.0E-4 ? this.drifter.position() : this.drifter.position().add(away.normalize().scale(4.0));
			return this.begin(new NavigateTask(this.drifter, target, 1.5, "back off", NavigateTask.Pace.STROLL));
		}
	}
}
