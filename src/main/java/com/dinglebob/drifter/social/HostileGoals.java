package com.dinglebob.drifter.social;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.AttackTask;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.perception.Awareness;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The hostile modes (DESIGN.md section 14): Territorial drives a player out of its base and stops at the boundary;
 * Predatory (Bandits, Opportunists) stalks and attacks for loot when there's an opening; Vengeful hunts the one who
 * wronged it. All of them only after the telegraph rule is satisfied ({@link Mode#toward} gives WARNING until then), and
 * the threat assessment still sends it running when it's losing (Fleeing).
 */
public final class HostileGoals {
	private HostileGoals() {
	}

	public static List<Goals.Goal> all() {
		return List.of(new DriveOut(), new Prey(), new Hunt());
	}

	/** Urgency for a hostile goal: above work, below fleeing for its life. */
	private static final double URGENCY = 0.8;
	/** Health under which it doesn't start a fight with a player. */
	private static final float FIT_HEALTH = 12.0F;

	/** The nearest player it sees in this mode, or null. */
	@Nullable
	private static Player inMode(DrifterEntity drifter, Mode mode) {
		Player best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Awareness awareness : drifter.getSenses().tracked()) {
			if (awareness.entity instanceof Player player && SocialWatcher.isRealPlayer(player) && awareness.identified
				&& Mode.toward(drifter, player) == mode) {
				double distance = player.distanceTo(drifter);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = player;
				}
			}
		}

		return best;
	}

	private static boolean fit(DrifterEntity drifter) {
		return drifter.getHealth() >= FIT_HEALTH;
	}

	/** Territorial: attacks to drive them out of its base, stops at the boundary. */
	public static final class DriveOut extends Goals.Goal {
		@Override
		public String id() {
			return "driveout";
		}

		@Override
		public String name(Goals.Context context) {
			return "drive a player out of its base";
		}

		@Override
		public double urgency(Goals.Context context) {
			Player player = inMode(context.drifter(), Mode.TERRITORIAL);
			return player != null && fit(context.drifter()) && player.distanceTo(context.drifter()) < 24.0 ? URGENCY : 0.0;
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
		public Activity start(Goals.Context context) {
			Player player = inMode(context.drifter(), Mode.TERRITORIAL);
			return player == null ? null : new Fight(context.drifter(), player, "drive " + player.getGameProfile().getName() + " out of its base") {
				@Override
				boolean done() {
					// Out of its base: it stops at the boundary.
					return Mode.toward(this.drifter, this.player) != Mode.TERRITORIAL;
				}

				@Override
				String doneThought() {
					return "drove " + this.player.getGameProfile().getName() + " out of its base";
				}
			};
		}
	}

	/** Predatory (Bandits, Opportunists): stalks, waits for an opening, then attacks; takes the loot. */
	public static final class Prey extends Goals.Goal {
		@Override
		public String id() {
			return "prey";
		}

		@Override
		public String name(Goals.Context context) {
			return "go after a player";
		}

		@Override
		public double urgency(Goals.Context context) {
			Player player = inMode(context.drifter(), Mode.PREDATORY);
			if (player == null || !fit(context.drifter())) {
				return 0.0;
			}

			// An Opportunist only moves when there's an opening; a Bandit stalks until there is one.
			boolean bandit = context.drifter().getSocial().disposition() == Disposition.BANDIT;
			return opening(context.drifter(), player) ? URGENCY : bandit ? 0.5 : 0.0;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public Activity start(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			Player player = inMode(drifter, Mode.PREDATORY);
			if (player == null) {
				return null;
			}

			if (opening(drifter, player)) {
				drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "an opening: going after " + player.getGameProfile().getName());
				return new Fight(drifter, player, "go after " + player.getGameProfile().getName()) {
					@Override
					boolean done() {
						return Mode.toward(this.drifter, this.player) != Mode.PREDATORY;
					}
				};
			}

			return new Stalk(drifter, player);
		}
	}

	/**
	 * An opening (section 14, "Waiting for an opening"): they look weak (gear well under its own), they're busy fighting
	 * mobs, or they're out alone at night. Never in bed or at their spawn (bed ambushes and spawn camping are banned).
	 */
	static boolean opening(DrifterEntity drifter, Player player) {
		if (player.isSleeping()) {
			return false;
		}

		Relationship r = drifter.getSocial().get(player.getUUID());
		boolean weak = r != null && r.strength < 0.8;
		boolean busy = player.getLastHurtByMob() != null && player.tickCount - player.getLastHurtByMobTimestamp() < 20 * 5
			|| player.getLastHurtMob() != null && player.tickCount - player.getLastHurtMobTimestamp() < 20 * 5;
		boolean aloneAtNight = Schedule.isNight(drifter) && drifter.level().players().stream()
			.noneMatch(other -> other != player && SocialWatcher.isRealPlayer(other) && other.distanceTo(player) < 32.0);
		return weak || busy || aloneAtNight;
	}

	/** Follows 24 to 40 blocks back, keeping out of their sight (stalking). */
	private static final class Stalk extends Activity {
		private final Player player;
		private int legs;

		Stalk(DrifterEntity drifter, Player player) {
			super(drifter);
			this.player = player;
		}

		@Override
		public String describe() {
			return "stalk " + this.player.getGameProfile().getName();
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60;
		}

		@Override
		protected boolean countsAsFailure(Task step) {
			return false;
		}

		@Override
		protected Status next() {
			if (!this.player.isAlive() || this.legs++ > 6 || opening(this.drifter, this.player)) {
				return Status.SUCCESS;
			}

			if (this.legs == 1) {
				this.think("stalking " + this.player.getGameProfile().getName() + ", waiting for an opening");
			}

			// A spot about 30 blocks back from them, on its side.
			Vec3 back = this.drifter.position().subtract(this.player.position()).multiply(1.0, 0.0, 1.0);
			if (back.lengthSqr() < 1.0E-4) {
				back = Vec3.directionFromRotation(0.0F, this.player.getYRot() + 180.0F);
			}

			double distance = this.player.distanceTo(this.drifter);
			if (distance >= 24.0 && distance <= 40.0) {
				// In the band: it waits and watches.
				this.drifter.getCamera().lookAt(this.player.getEyePosition(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
				return this.begin(new com.dinglebob.drifter.executor.task.WaitTask(this.drifter, "keep watch", () -> false, 40));
			}

			Vec3 spot = this.player.position().add(back.normalize().scale(30.0));
			return this.begin(new NavigateTask(this.drifter, spot, 4.0, "keep its distance", NavigateTask.Pace.SNEAK));
		}
	}

	/** Vengeful: hunts that player; can travel to their known base. */
	public static final class Hunt extends Goals.Goal {
		@Override
		public String id() {
			return "hunt";
		}

		@Override
		public String name(Goals.Context context) {
			return "hunt down a player";
		}

		@Nullable
		private static Relationship quarry(DrifterEntity drifter) {
			Relationship best = null;
			for (Relationship r : drifter.getSocial().all()) {
				Player player = drifter.level().getPlayerByUUID(r.player);
				if (player == null || !SocialWatcher.isRealPlayer(player)) {
					continue;
				}

				Awareness awareness = drifter.getSenses().awarenessOf(player);
				boolean known = awareness != null && awareness.identified;
				if (known ? Mode.toward(drifter, player) != Mode.VENGEFUL : !(r.wronged && r.grudgeWeight() >= Mode.VENGEFUL_GRUDGE && r.trust < Mode.HOSTILE_TRUST)) {
					continue;
				}

				if (best == null || r.grudgeWeight() > best.grudgeWeight()) {
					best = r;
				}
			}

			return best;
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			Relationship r = quarry(drifter);
			if (r == null || !fit(drifter) || drifter.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
				return 0.0;
			}

			Player player = drifter.level().getPlayerByUUID(r.player);
			Awareness awareness = player == null ? null : drifter.getSenses().awarenessOf(player);
			boolean inSight = awareness != null && awareness.identified && awareness.visibleNow;
			if (inSight) {
				return URGENCY;
			}

			// Out of sight: a vengeful one goes looking, where it last saw them or at their base.
			boolean vengeful = drifter.getPersonality().get(Trait.VENGEFULNESS) >= 0.6;
			return vengeful && (r.lastSeenPos != null || r.knownBase != null) ? 0.45 : 0.0;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public double risk(Goals.Context context) {
			return Goals.lateInTheDayRisk(context, 0.4);
		}

		@Override
		public Activity start(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			Relationship r = quarry(drifter);
			if (r == null) {
				return null;
			}

			Player player = drifter.level().getPlayerByUUID(r.player);
			Awareness awareness = player == null ? null : drifter.getSenses().awarenessOf(player);
			if (player != null && awareness != null && awareness.identified && awareness.visibleNow) {
				drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "it hasn't forgotten what " + r.name + " did: going after them");
				return new Fight(drifter, player, "hunt down " + r.name) {
					@Override
					boolean done() {
						return Mode.toward(this.drifter, this.player) != Mode.VENGEFUL;
					}
				};
			}

			BlockPos where = r.lastSeenPos != null && drifter.level().getGameTime() - r.lastSeenAt < 20 * 60 * 5 || r.knownBase == null ? r.lastSeenPos : r.knownBase;
			if (where == null) {
				return null;
			}

			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "looking for " + r.name + (where.equals(r.knownBase) ? " at their base" : " where it last saw them"));
			return new Activity(drifter) {
				private boolean went;

				@Override
				public String describe() {
					return "look for " + r.name;
				}

				@Override
				protected Status next() {
					if (this.went) {
						return Status.SUCCESS;
					}

					this.went = true;
					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(where), 4.0, "look for " + r.name));
				}
			};
		}
	}

	/** Fights one player until {@link #done} (or it dies, or runs: the flee goal takes over), then picks up the drops. */
	private abstract static class Fight extends Activity {
		protected final Player player;
		private final String label;
		private boolean looted;

		Fight(DrifterEntity drifter, Player player, String label) {
			super(drifter);
			this.player = player;
			this.label = label;
		}

		abstract boolean done();

		String doneThought() {
			return null;
		}

		@Override
		public String describe() {
			return this.label;
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60 * 2;
		}

		@Override
		public boolean atCheckpoint() {
			return true;
		}

		@Override
		protected boolean rethink() {
			return this.player.isAlive() && this.ticksRunning() % 10 == 0 && this.done();
		}

		@Override
		protected Status next() {
			if (!this.player.isAlive() || this.player.isRemoved()) {
				// Looting (section 14): what they dropped.
				if (!this.looted) {
					this.looted = true;
					this.think("took down " + this.player.getGameProfile().getName() + ": picking up what they dropped");
					return this.begin(new CollectItemsTask(this.drifter, 8.0));
				}

				return Status.SUCCESS;
			}

			if (this.done()) {
				String thought = this.doneThought();
				if (thought != null) {
					this.think(thought);
				}

				return Status.SUCCESS;
			}

			return this.begin(new AttackTask(this.drifter, this.player, this.player.getGameProfile().getName()));
		}
	}
}
