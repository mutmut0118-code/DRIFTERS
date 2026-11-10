package com.dinglebob.drifterciv;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.AttackTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.WaitTask;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * The friendship perks a Drifter walks for (D2): coming over when a friend's back after a while, asking them in at night
 * or in the rain (and its spare bed), and fighting whoever's attacking them that Drifter's own help in a fight leaves be
 * (Bandits and hostile Drifters: it helps against monsters already). Registered as addon goals, scored like its own.
 */
final class FriendGoals {
	private FriendGoals() {
	}

	static void register() {
		DrifterApi.registerGoal(WelcomeBack::new);
		DrifterApi.registerGoal(InviteIn::new);
		DrifterApi.registerGoal(BackUp::new);
	}

	// ----- Welcome back -----

	/** A friend it hasn't seen for a day or more turns up: it goes over and asks how it went. */
	static final class WelcomeBack extends Goals.Goal {
		@Override
		public String id() {
			return "friend_welcome";
		}

		@Override
		public String name(Goals.Context context) {
			return "welcome a friend back";
		}

		@Nullable
		private static ServerPlayer back(DrifterEntity drifter) {
			return FriendPerks.welcomeDue(drifter);
		}

		@Override
		public double urgency(Goals.Context context) {
			return CivConfig.friends().perkWelcome && back(context.drifter()) != null ? 0.6 : 0.0;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public Activity start(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			ServerPlayer player = back(drifter);
			if (player == null) {
				return null;
			}

			// (Once a return, whatever comes of it.)
			FriendPerks.welcomed(drifter, player);
			return new Activity(drifter) {
				private int step;

				@Override
				public String describe() {
					return "welcome " + player.getGameProfile().getName() + " back";
				}

				@Override
				protected Status next() {
					if (!player.isAlive() || player.level() != drifter.level() || drifter.distanceTo(player) > 48.0) {
						return Status.FAILURE;
					}

					if (this.step++ == 0 && drifter.distanceTo(player) > 4.0) {
						return this.begin(new NavigateTask(drifter, player.position(), 3.0, "go over to " + player.getGameProfile().getName()));
					}

					TalkApi.startTalk(drifter, player, "welcome_back");
					return Status.SUCCESS;
				}
			};
		}
	}

	// ----- Invite in -----

	/** Night or rain, a friend outside near its house: "come in", and it goes to its door; its spare bed if it has one. */
	static final class InviteIn extends Goals.Goal {
		@Override
		public String id() {
			return "friend_invite";
		}

		@Override
		public String name(Goals.Context context) {
			return "ask a friend in";
		}

		@Override
		public double urgency(Goals.Context context) {
			return CivConfig.friends().perkInvite && FriendPerks.inviteDue(context.drifter()) != null ? 0.98 : 0.0;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public Activity start(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			FriendPerks.Invite invite = FriendPerks.inviteDue(drifter);
			if (invite == null) {
				return null;
			}

			FriendPerks.invited(drifter, invite);
			return new Activity(drifter) {
				private int step;

				@Override
				public String describe() {
					return "ask " + invite.player().getGameProfile().getName() + " in";
				}

				@Override
				protected Status next() {
					ServerPlayer player = invite.player();
					switch (this.step++) {
						case 0 -> {
							return this.begin(new NavigateTask(drifter, Vec3.atBottomCenterOf(invite.inside()), 1.0, "go to its door", NavigateTask.Pace.STROLL));
						}
						case 1 -> {
							// (Waits for them a while, inside its door.)
							return this.begin(new WaitTask(drifter, "wait for " + player.getGameProfile().getName(), () -> !player.isAlive()
								|| player.blockPosition().distSqr(invite.inside()) <= 9.0, 20 * 60));
						}
						case 2 -> {
							if (player.isAlive() && player.blockPosition().distSqr(invite.inside()) <= 9.0 && FriendPerks.spareBed(((ServerLevel)drifter.level()), invite.plot()) != null) {
								FriendPerks.say(drifter, player, "friends.bed", false);
							}

							return Status.SUCCESS;
						}
						default -> {
							return Status.SUCCESS;
						}
					}
				}
			};
		}
	}

	// ----- Backup -----

	/** A friend hurt in the last few seconds by a Drifter it'd fight for them (Drifter's HelpFight takes monsters). */
	static final class BackUp extends Goals.Goal {
		@Override
		public String id() {
			return "friend_backup";
		}

		@Override
		public String name(Goals.Context context) {
			return "back up a friend";
		}

		@Override
		public double urgency(Goals.Context context) {
			return CivConfig.friends().perkBackup && attacker(context.drifter()) != null ? 0.85 : 0.0;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public boolean urgent(Goals.Context context) {
			return true;
		}

		@Nullable
		static LivingEntity attacker(DrifterEntity drifter) {
			if (drifter.getHealth() < 8.0F || !Friends.eligible(drifter)) {
				return null;
			}

			for (ServerPlayer player : ((ServerLevel)drifter.level()).getEntitiesOfClass(ServerPlayer.class, drifter.getBoundingBox().inflate(20.0),
				p -> p.isAlive() && Friends.isFriend(drifter, p))) {
				LivingEntity by = player.getLastHurtByMob();
				if (by == null || by == drifter || !by.isAlive() || by instanceof Enemy || player.tickCount - player.getLastHurtByMobTimestamp() > 100
					|| by.distanceTo(drifter) > 24.0 || !(by instanceof DrifterEntity other) || !foughtFor(drifter, player, other)) {
					continue;
				}

				return other;
			}

			return null;
		}

		/** Whether it'd fight this Drifter for its friend: a Bandit, a delver, or not one of its town (its household never). */
		private static boolean foughtFor(DrifterEntity drifter, ServerPlayer player, DrifterEntity other) {
			if (TalkApi.hostileOnSight(other)) {
				return true;
			}

			Town mine = Market.townOf(drifter);
			Town theirs = Market.townOf(other);
			if (FriendPerks.housemates(drifter, other)) {
				return false;
			}

			if (mine == null || theirs == null || mine != theirs) {
				// (Not one it gets on with better than with them.)
				return DrifterApi.trustInDrifter(drifter, other.getUUID()) < DrifterApi.trustInPlayer(drifter, player.getUUID());
			}

			// One of its own town going for them: only a best friend takes their side, and only over something small.
			return Friends.isBest(drifter.getUUID(), player.getUUID()) && !Justice.killOnSight(mine, player);
		}

		@Override
		public Activity start(Goals.Context context) {
			LivingEntity target = attacker(context.drifter());
			if (target == null) {
				return null;
			}

			DrifterEntity drifter = context.drifter();
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "backing up a friend against " + target.getName().getString());
			return new Activity(drifter) {
				private boolean attacked;

				@Override
				public String describe() {
					return "back up a friend";
				}

				@Override
				protected Status next() {
					if (this.attacked || !target.isAlive() || target.distanceTo(drifter) > 24.0 || drifter.getHealth() < 8.0F) {
						return Status.SUCCESS;
					}

					this.attacked = true;
					return this.begin(new AttackTask(drifter, target, target.getName().getString().toLowerCase(Locale.ROOT)));
				}
			};
		}
	}

	/** The spot just inside a house's front door (null if it can't be worked out). */
	@Nullable
	static BlockPos inside(Town.Plot plot, BlockPos door) {
		return door.relative(plot.front.getOpposite());
	}
}
