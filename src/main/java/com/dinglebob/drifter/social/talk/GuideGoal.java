package com.dinglebob.drifter.social.talk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.Steering;
import com.dinglebob.drifter.social.ChatLines;
import com.dinglebob.drifter.social.Lines;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Leading a player somewhere (docs/PLAYER_TALK.md E2: "show me your house", "take me to the shop", "show me around"):
 * it walks to each stop in turn at a stroll, waits there for them to catch up, says the stop's line, and goes on. It
 * stops and calls back when they fall behind, and gives up when they wander off. Only places it knows (its own home; a
 * town's places, from an addon): it walks there as it would for itself.
 */
public final class GuideGoal extends Goals.Goal {
	/** A place on the way: where, the chat line said there, and how close counts as there. */
	public record Stop(BlockPos pos, String line, double near) {
	}

	private static final class Guide {
		final UUID player;
		final List<Stop> stops;
		final String what;
		final long since;
		int index;
		long arrivedAt = -1;
		long pauseUntil;
		long calledAt = Long.MIN_VALUE / 2;

		Guide(UUID player, List<Stop> stops, String what, long since) {
			this.player = player;
			this.stops = stops;
			this.what = what;
			this.since = since;
		}
	}

	/** They fall this far behind and it waits; this far off and it gives up. */
	private static final double BEHIND = 12.0;
	private static final double GONE = 48.0;
	/** How long it waits at a stop for them, stands there after its line, and leads at most. */
	private static final int WAITS = 20 * 8;
	private static final int STANDS = 20 * 3;
	private static final long AT_MOST = 20L * 60 * 6;

	private static final Map<UUID, Guide> guides = new HashMap<>();

	/** It leads this player to these places, in order. */
	public static void lead(DrifterEntity drifter, ServerPlayer player, List<Stop> stops, String what) {
		if (!stops.isEmpty()) {
			guides.put(drifter.getUUID(), new Guide(player.getUUID(), List.copyOf(stops), what, drifter.level().getGameTime()));
		}
	}

	public static boolean leading(DrifterEntity drifter) {
		return guides.containsKey(drifter.getUUID());
	}

	/** It stops leading (told to stop). */
	public static void stop(DrifterEntity drifter) {
		guides.remove(drifter.getUUID());
	}

	@Nullable
	private static Player player(DrifterEntity drifter, Guide guide) {
		Player player = drifter.level().getPlayerByUUID(guide.player);
		return player != null && player.isAlive() && player.distanceTo(drifter) <= GONE ? player : null;
	}

	@Override
	public String id() {
		return "guide";
	}

	@Override
	public String name(Goals.Context context) {
		Guide guide = guides.get(context.drifter().getUUID());
		return guide == null ? "show a player the way" : "show a player " + guide.what;
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		Guide guide = guides.get(drifter.getUUID());
		if (guide == null) {
			return 0.0;
		}

		if (player(drifter, guide) == null || drifter.level().getGameTime() - guide.since > AT_MOST) {
			guides.remove(drifter.getUUID());
			return 0.0;
		}

		return context.threat().urgent() ? 0.0 : 0.76;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public boolean urgent(Goals.Context context) {
		// (A player is following it.)
		return true;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 5;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		Guide guide = guides.get(drifter.getUUID());
		if (guide == null) {
			return null;
		}

		return new Activity(drifter) {
			@Override
			public String describe() {
				return "show a player " + guide.what;
			}

			@Override
			protected int timeoutTicks() {
				return (int)AT_MOST;
			}

			@Override
			protected boolean countsAsFailure(Task step) {
				return false;
			}

			private void say(Player player, String key) {
				String line = Lines.pick(this.drifter, key, PlayerConvo.vars(this.drifter, (ServerPlayer)player));
				if (line != null) {
					ChatLines.to(this.drifter, player);
					ChatLines.speak(this.drifter, line);
				}
			}

			@Override
			protected Status next() {
				long now = this.drifter.level().getGameTime();
				Player player = guides.get(this.drifter.getUUID()) == guide ? player(this.drifter, guide) : null;
				if (player == null || guide.index >= guide.stops.size() && now >= guide.pauseUntil) {
					guides.remove(this.drifter.getUUID(), guide);
					return Status.SUCCESS;
				}

				if (now < guide.pauseUntil) {
					Steering.stopMoving(this.drifter);
					this.drifter.getCamera().lookAt(player.getEyePosition(), CameraModel.TurnSpeed.NORMAL);
					return Status.RUNNING;
				}

				Stop stop = guide.stops.get(guide.index);
				Vec3 at = Vec3.atBottomCenterOf(stop.pos());
				double dx = at.x - this.drifter.getX();
				double dz = at.z - this.drifter.getZ();
				double far = Math.sqrt(dx * dx + dz * dz);
				if (far <= stop.near() + 1.5 && Math.abs(at.y - this.drifter.getY()) <= 6.0) {
					// There: once they've caught up (or it has waited long enough), what it has to say about the place.
					Steering.stopMoving(this.drifter);
					this.drifter.getCamera().lookAt(player.getEyePosition(), CameraModel.TurnSpeed.NORMAL);
					if (guide.arrivedAt < 0) {
						guide.arrivedAt = now;
					}

					if (player.distanceTo(this.drifter) <= 8.0 || now - guide.arrivedAt > WAITS) {
						this.say(player, stop.line());
						guide.index++;
						guide.arrivedAt = -1;
						guide.pauseUntil = now + STANDS;
						if (guide.index >= guide.stops.size() && guide.stops.size() > 1) {
							guide.pauseUntil = now + STANDS * 2L;
							String done = Lines.pick(this.drifter, "convo.tour.done", PlayerConvo.vars(this.drifter, (ServerPlayer)player));
							if (done != null) {
								PlayerTalk.sayAt(this.drifter, done, now + STANDS + 20);
							}
						}
					}

					return Status.RUNNING;
				}

				if (player.distanceTo(this.drifter) > BEHIND) {
					// They've fallen behind: it waits, and calls back now and then.
					Steering.stopMoving(this.drifter);
					this.drifter.getCamera().lookAt(player.getEyePosition(), CameraModel.TurnSpeed.NORMAL);
					if (now - guide.calledAt > 20 * 10) {
						guide.calledAt = now;
						this.say(player, "convo.guide.wait");
					}

					return Status.RUNNING;
				}

				// (A stretch at a time, so it looks back for them on the way.)
				if (far <= 20.0) {
					return this.begin(new NavigateTask(this.drifter, at, stop.near(), "lead the way", NavigateTask.Pace.STROLL));
				}

				return this.begin(com.dinglebob.drifter.nav.Legs.toward(this.drifter, stop.pos(), stop.near(), "lead the way", NavigateTask.Pace.STROLL));
			}
		};
	}
}
