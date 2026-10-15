package com.dinglebob.drifter.social;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Storage;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.AttackTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.Steering;
import com.dinglebob.drifter.motor.VirtualKeys;
import com.dinglebob.drifter.perception.Awareness;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The friendly behaviors (DESIGN.md section 14): greeting with crouch-spam and a look, paying back gifts (barter: about
 * the same value back), "follow me" (crouch twice near it, then walk off), helping against mobs attacking the player,
 * and warning of a creeper they haven't seen. Non-verbal, as players are.
 */
public final class FriendlyGoals {
	private FriendlyGoals() {
	}

	public static List<Goals.Goal> all() {
		return List.of(new Thank(), new Greet(), new PayBack(), new FollowMe(), new HelpFight(), new WarnOfCreeper());
	}

	private static boolean friendly(Mode mode) {
		return mode == Mode.FRIENDLY || mode == Mode.ALLY;
	}

	/** The nearest player it sees whose mode passes the test, or null. */
	@Nullable
	private static Player nearest(DrifterEntity drifter, double range, java.util.function.Predicate<Mode> modes) {
		Player best = null;
		double bestDistance = range;
		for (Awareness awareness : drifter.getSenses().tracked()) {
			if (awareness.entity instanceof Player player && SocialWatcher.isRealPlayer(player) && awareness.identified && awareness.visibleNow) {
				double distance = player.distanceTo(drifter);
				if (distance < bestDistance && modes.test(Mode.toward(drifter, player))) {
					bestDistance = distance;
					best = player;
				}
			}
		}

		return best;
	}

	// ---------------------------------------------------------------------------------------------
	// Thanks
	// ---------------------------------------------------------------------------------------------

	/** A gift: it stops, looks at them, nods and crouches back (section 14 table: crouch-spam is "thanks"). */
	public static final class Thank extends Goals.Goal {
		@Override
		public String id() {
			return "thank";
		}

		@Override
		public String name(Goals.Context context) {
			return "thank a player for a gift";
		}

		@Override
		public double urgency(Goals.Context context) {
			return context.drifter().getSocial().watcher().owedThanks() != null ? 0.85 : 0.0;
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
			DrifterEntity drifter = context.drifter();
			Player player = drifter.getSocial().watcher().owedThanks();
			if (player == null) {
				return null;
			}

			String what = drifter.getSocial().watcher().respondingTo(player);
			drifter.getSocial().watcher().thanked(player);
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "answering " + player.getGameProfile().getName() + "'s " + what.substring(4));
			return new Activity(drifter) {
				private int step;

				@Override
				public String describe() {
					return "thank " + player.getGameProfile().getName();
				}

				@Override
				protected Status next() {
					return switch (this.step++) {
						case 0 -> this.begin(new Gesture(this.drifter, player, "nod at " + player.getGameProfile().getName(), Gesture.Kind.NOD));
						case 1 -> this.begin(new Gesture(this.drifter, player, "crouch at " + player.getGameProfile().getName(), Gesture.Kind.CROUCH_SPAM));
						default -> Status.SUCCESS;
					};
				}
			};
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Greeting
	// ---------------------------------------------------------------------------------------------

	/** "Greets with crouch-spam and a look." */
	public static final class Greet extends Goals.Goal {
		/** How far off it comes over to greet someone it likes. */
		private static final double GREET_RANGE = 24.0;

		@Override
		public String id() {
			return "greet";
		}

		@Override
		public String name(Goals.Context context) {
			return "greet a player";
		}

		@Nullable
		private static Player target(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			// "Friendly: approaches, greets": it comes over from a way off (not just when they happen to walk up).
			return nearest(drifter, GREET_RANGE, mode -> friendly(mode)) instanceof Player player && !drifter.getSocial().watcher().greetedLately(player)
				? player : null;
		}

		@Override
		public double urgency(Goals.Context context) {
			return target(context) != null ? 0.6 : 0.0;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public int minCommitTicks() {
			return 20;
		}

		@Override
		public Activity start(Goals.Context context) {
			Player player = target(context);
			if (player == null) {
				return null;
			}

			DrifterEntity drifter = context.drifter();
			drifter.getSocial().watcher().greeted(player);
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "going over to say hi to " + player.getGameProfile().getName());
			return new Activity(drifter) {
				private int step;

				@Override
				public String describe() {
					return "greet " + player.getGameProfile().getName();
				}

				@Override
				protected Status next() {
					switch (this.step++) {
						case 0:
							if (player.distanceTo(this.drifter) > 4.0) {
								return this.begin(new NavigateTask(this.drifter, player.position(), 3.0, "walk over to " + player.getGameProfile().getName(),
									NavigateTask.Pace.STROLL));
							}
						case 1:
							ChatLines.say(this.drifter, ChatLines.Kind.HI);
							return this.begin(new Gesture(this.drifter, player, "greet " + player.getGameProfile().getName(), Gesture.Kind.CROUCH_SPAM));
						default:
							return Status.SUCCESS;
					}
				}
			};
		}
	}

	/** A short non-verbal gesture at a player (section 14 table): crouch-spam, a nod, a head shake. */
	static final class Gesture extends Activity {
		enum Kind { CROUCH_SPAM, NOD, SHAKE }

		private final Player player;
		private final String label;
		private final Kind kind;
		private int ticks;

		Gesture(DrifterEntity drifter, Player player, String label, Kind kind) {
			super(drifter);
			this.player = player;
			this.label = label;
			this.kind = kind;
		}

		@Override
		public String describe() {
			return this.label;
		}

		@Override
		protected Status next() {
			int t = this.ticks++;
			Steering.stopMoving(this.drifter);
			VirtualKeys keys = this.drifter.getKeys();
			Vec3 eyes = this.player.getEyePosition();
			switch (this.kind) {
				case CROUCH_SPAM -> {
					this.drifter.getCamera().lookAt(eyes, CameraModel.TurnSpeed.NORMAL);
					// Look first, then down-up-down-up (about 4 crouches in a second and a half).
					keys.set(VirtualKeys.Key.SNEAK, t >= 10 && (t - 10) / 4 % 2 == 0 && t < 42);
					if (t >= 46) {
						keys.set(VirtualKeys.Key.SNEAK, false);
						return Status.SUCCESS;
					}
				}
				case NOD, SHAKE -> {
					Vec3 to = eyes.subtract(this.drifter.getEyePosition());
					float yaw = (float)(Math.toDegrees(Math.atan2(-to.x, to.z)));
					float pitch = (float)(-Math.toDegrees(Math.atan2(to.y, to.horizontalDistance())));
					float swing = t >= 4 && t < 24 ? ((t / 5) % 2 == 0 ? 1.0F : -1.0F) * 14.0F : 0.0F;
					this.drifter.getCamera().lookAt(this.kind == Kind.SHAKE ? yaw + swing : yaw, this.kind == Kind.NOD ? pitch + swing : pitch,
						CameraModel.TurnSpeed.COMBAT);
					if (t >= 28) {
						return Status.SUCCESS;
					}
				}
			}

			return Status.RUNNING;
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Gifts and barter
	// ---------------------------------------------------------------------------------------------

	/**
	 * "Gifts surplus, especially after being gifted" and barter ("drops back something it values about equally"): it
	 * owes a player for what they gave it, so it walks over, nods, and throws them something of about that worth from
	 * what it doesn't need. Not for players it's hostile to or avoids.
	 */
	public static final class PayBack extends Goals.Goal {
		/** Owed worth below this isn't worth a trip. */
		private static final int MIN_OWED = 2;

		@Override
		public String id() {
			return "payback";
		}

		@Override
		public String name(Goals.Context context) {
			return "pay a player back for a gift";
		}

		@Nullable
		private static Player target(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			Player player = nearest(drifter, 16.0, mode -> mode == Mode.TOLERATING || friendly(mode));
			if (player == null) {
				return null;
			}

			Relationship r = drifter.getSocial().get(player.getUUID());
			return r != null && owed(r) >= MIN_OWED && offer(drifter, owed(r)) != null ? player : null;
		}

		static int owed(Relationship r) {
			return r.debts.stream().mapToInt(Relationship.Debt::value).sum();
		}

		@Override
		public double urgency(Goals.Context context) {
			return target(context) != null ? 0.5 : 0.0;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public Activity start(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			Player player = target(context);
			if (player == null) {
				return null;
			}

			Relationship r = drifter.getSocial().get(player.getUUID());
			return new Activity(drifter) {
				private int step;

				@Override
				public String describe() {
					return "pay " + player.getGameProfile().getName() + " back";
				}

				@Override
				protected Status next() {
					switch (this.step++) {
						case 0:
							if (player.distanceTo(this.drifter) > 3.0) {
								return this.begin(new NavigateTask(this.drifter, player.position(), 2.5, "walk over to " + player.getGameProfile().getName(),
									NavigateTask.Pace.STROLL));
							}
						case 1:
							return this.begin(new Gesture(this.drifter, player, "nod at " + player.getGameProfile().getName(), Gesture.Kind.NOD));
						case 2: {
							int owed = owed(r);
							Offer offer = offer(this.drifter, owed);
							if (offer == null || player.distanceTo(this.drifter) > 5.0) {
								return Status.SUCCESS;
							}

							this.drifter.getCamera().lookAt(player.getEyePosition(), CameraModel.TurnSpeed.NORMAL);
							ItemStack stack = this.drifter.getInventory().getItem(offer.slot());
							ItemStack given = stack.split(offer.count());
							int worth = (int)Math.round(ItemValues.of(given));
							this.think("paying " + player.getGameProfile().getName() + " back: " + given.getCount() + " " + given.getHoverName().getString());
							this.drifter.tossForward(given);
							// What it gave back clears that much of what it owed.
							int left = worth;
							while (left > 0 && !r.debts.isEmpty()) {
								Relationship.Debt first = r.debts.get(0);
								if (first.value() <= left) {
									left -= first.value();
									r.debts.remove(0);
								} else {
									r.debts.set(0, new Relationship.Debt(first.what(), first.value() - left, first.since()));
									left = 0;
								}
							}

							return Status.RUNNING;
						}
						default:
							return Status.SUCCESS;
					}
				}
			};
		}
	}

	record Offer(int slot, int count) {
	}

	/** What it would give for {@code worth} points: from what it doesn't need (its surplus), as close in value as it can. */
	@Nullable
	static Offer offer(DrifterEntity drifter, int worth) {
		Map<Integer, Integer> spare = new java.util.HashMap<>(Storage.toDeposit(drifter));
		// Plus common things it has plenty of (logs, planks, stone, coal, food): it keeps a working stack and can give the
		// rest (found by the user: early on it had nothing "spare" and never paid anyone back).
		for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = drifter.getInventory().getItem(slot);
			int keep = stack.is(net.minecraft.tags.ItemTags.LOGS) || stack.is(net.minecraft.tags.ItemTags.PLANKS) || stack.is(net.minecraft.world.item.Items.COBBLESTONE)
				|| stack.is(net.minecraft.world.item.Items.COAL) ? 16 : stack.has(net.minecraft.core.component.DataComponents.FOOD) ? 8 : -1;
			if (keep >= 0) {
				int total = drifter.getInventory().count(other -> other.is(stack.getItem()));
				int extra = Math.min(stack.getCount(), total - keep);
				if (extra > 0) {
					spare.merge(slot, extra, Math::max);
				}
			}
		}

		Offer best = null;
		double bestGap = Double.MAX_VALUE;
		for (Map.Entry<Integer, Integer> entry : spare.entrySet()) {
			ItemStack stack = drifter.getInventory().getItem(entry.getKey());
			double each = ItemValues.each(stack);
			if (stack.isEmpty() || each <= 0.0) {
				continue;
			}

			int count = (int)Math.max(1, Math.min(entry.getValue(), Math.round(worth / each)));
			double gap = Math.abs(count * each - worth);
			// About equal: within half the worth either way.
			if (gap <= worth * 0.5 + 0.5 && gap < bestGap) {
				bestGap = gap;
				best = new Offer(entry.getKey(), count);
			}
		}

		return best;
	}

	// ---------------------------------------------------------------------------------------------
	// Follow me
	// ---------------------------------------------------------------------------------------------

	/** "Crouching twice near it, then walking away, invites it to follow for a while (Familiarity and Trust gated)." */
	public static final class FollowMe extends Goals.Goal {
		private static final double MIN_TRUST = 10.0;
		private static final double MIN_FAMILIARITY = 3.0;
		/** How long it follows. */
		private static final int FOLLOW_TICKS = 20 * 60 * 3;

		@Override
		public String id() {
			return "followme";
		}

		@Override
		public String name(Goals.Context context) {
			return "follow a player";
		}

		@Nullable
		private static Player target(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			Player player = nearest(drifter, 16.0, mode -> mode == Mode.TOLERATING || friendly(mode));
			if (player == null) {
				return null;
			}

			Vec3 from = drifter.getSocial().watcher().invitedFrom(player);
			Relationship r = drifter.getSocial().get(player.getUUID());
			// Asked, and now walking off.
			return from != null && player.position().distanceTo(from) > 2.5 && r != null
				&& (r.trust >= Mode.FRIENDLY_TRUST || r.trust >= MIN_TRUST && r.familiarity >= MIN_FAMILIARITY) ? player : null;
		}

		@Override
		public double urgency(Goals.Context context) {
			return target(context) != null ? 0.65 : 0.0;
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
			Player player = target(context);
			if (player == null) {
				return null;
			}

			drifter.getSocial().watcher().followed(player);
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "following " + player.getGameProfile().getName() + " for a while");
			return new Activity(drifter) {
				@Override
				public String describe() {
					return "follow " + player.getGameProfile().getName();
				}

				@Override
				protected int timeoutTicks() {
					return FOLLOW_TICKS;
				}

				@Override
				protected Status next() {
					if (!player.isAlive() || player.isRemoved() || this.ticksRunning() >= FOLLOW_TICKS - 20) {
						return Status.SUCCESS;
					}

					return this.begin(NavigateTask.follow(this.drifter, player, 3.0));
				}
			};
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Helping
	// ---------------------------------------------------------------------------------------------

	/** "Helps fight mobs that are attacking the player." */
	public static final class HelpFight extends Goals.Goal {
		@Override
		public String id() {
			return "helpfight";
		}

		@Override
		public String name(Goals.Context context) {
			return "help a player fight";
		}

		/** A mob attacking a friendly player it can see, that it could take on. */
		@Nullable
		private static LivingEntity attacker(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			Player player = nearest(drifter, 20.0, FriendlyGoals::friendly);
			if (player == null || drifter.getHealth() < 10.0F) {
				return null;
			}

			LivingEntity mob = player.getLastHurtByMob();
			boolean recent = mob != null && player.tickCount - player.getLastHurtByMobTimestamp() < 20 * 5;
			if (!recent || !(mob instanceof Enemy) || !mob.isAlive() || mob.distanceTo(drifter) > 20.0) {
				return null;
			}

			Awareness seen = drifter.getSenses().awarenessOf(mob);
			return seen != null && seen.identified ? mob : null;
		}

		@Override
		public double urgency(Goals.Context context) {
			return attacker(context) != null ? 0.85 : 0.0;
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
			LivingEntity mob = attacker(context);
			if (mob == null) {
				return null;
			}

			context.drifter().getMemory().thoughts().add(context.drifter().level().getGameTime(), "helping fight the "
				+ mob.getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT));
			return new Activity(context.drifter()) {
				private boolean attacked;

				@Override
				public String describe() {
					return "help fight";
				}

				@Override
				protected Status next() {
					if (this.attacked || !mob.isAlive()) {
						return Status.SUCCESS;
					}

					this.attacked = true;
					return this.begin(new AttackTask(this.drifter, mob, mob.getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT)));
				}
			};
		}
	}

	/** "Warns of danger by turning toward an unseen creeper and backing away." */
	public static final class WarnOfCreeper extends Goals.Goal {
		@Override
		public String id() {
			return "warncreeper";
		}

		@Override
		public String name(Goals.Context context) {
			return "warn a player about a creeper";
		}

		private record Danger(Player player, Creeper creeper) {
		}

		/** A creeper close behind a friendly player (out of their view) that it has seen. */
		@Nullable
		private static Danger danger(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			Player player = nearest(drifter, 16.0, mode -> mode == Mode.TOLERATING || friendly(mode));
			if (player == null) {
				return null;
			}

			for (Awareness awareness : drifter.getSenses().tracked()) {
				if (awareness.entity instanceof Creeper creeper && awareness.identified && awareness.visibleNow && creeper.distanceTo(player) < 6.0) {
					Vec3 toCreeper = creeper.position().subtract(player.position()).normalize();
					if (player.getViewVector(1.0F).dot(toCreeper) < 0.0) {
						return new Danger(player, creeper);
					}
				}
			}

			return null;
		}

		/** When it last warned of a creeper (once in a while, not every tick the creeper stays there). */
		private long warnedAt = Long.MIN_VALUE / 2;

		@Override
		public double urgency(Goals.Context context) {
			return context.time() >= 0 && context.drifter().level().getGameTime() - this.warnedAt > 20 * 20 && danger(context) != null ? 0.8 : 0.0;
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
			Danger danger = danger(context);
			if (danger == null) {
				return null;
			}

			DrifterEntity drifter = context.drifter();
			this.warnedAt = drifter.level().getGameTime();
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "warning " + danger.player().getGameProfile().getName() + " about a creeper behind them");
			return new Activity(drifter) {
				private int ticks;

				@Override
				public String describe() {
					return "warn about a creeper";
				}

				@Override
				protected Status next() {
					// Facing the creeper, backing away from it, so the player sees where it's looking.
					this.drifter.getCamera().lookAt(danger.creeper().getEyePosition(), CameraModel.TurnSpeed.COMBAT);
					VirtualKeys keys = this.drifter.getKeys();
					Steering.stopMoving(this.drifter);
					keys.set(VirtualKeys.Key.BACK, true);
					return ++this.ticks >= 30 || !danger.creeper().isAlive() ? Status.SUCCESS : Status.RUNNING;
				}
			};
		}
	}
}
