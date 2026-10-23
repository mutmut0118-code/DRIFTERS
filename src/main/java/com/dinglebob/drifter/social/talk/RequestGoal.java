package com.dinglebob.drifter.social.talk;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.motor.CameraModel;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Doing what a player asked in chat and it agreed to (chat phase 2, {@link PlayerTalk}): going over to them ("come here"),
 * running to them ("help!"), or bringing them what they asked for and tossing it over ("can i have some bread?": only
 * from what it can spare, as many as it said). Someone's waiting: it gets to it at its next checkpoint. Not done in a
 * minute, it lets it go.
 */
public final class RequestGoal extends Goals.Goal {
	/** What it said it would do. */
	public enum Kind {
		COME, HELP, GIVE
	}

	record Request(Kind kind, UUID player, @Nullable Item item, int count, long at) {
	}

	private static final Map<UUID, Request> requests = new HashMap<>();
	private static final long WITHIN = 20L * 60;
	/** Close enough to hand something over, or to be "here". */
	private static final double CLOSE = 3.0;

	static void ask(DrifterEntity drifter, Kind kind, Player player, @Nullable Item item, int count) {
		requests.put(drifter.getUUID(), new Request(kind, player.getUUID(), item, count, drifter.level().getGameTime()));
		// A call for help: it drops what it's doing and runs. The rest wait for a good moment to stop.
		if (kind == Kind.HELP) {
			drifter.getDrifterBrain().restart("going to help " + player.getGameProfile().getName());
		} else {
			drifter.getDrifterBrain().poke();
		}
	}

	@Nullable
	private static Request request(DrifterEntity drifter) {
		Request request = requests.get(drifter.getUUID());
		if (request != null && drifter.level().getGameTime() - request.at() > WITHIN) {
			requests.remove(drifter.getUUID());
			return null;
		}

		return request;
	}

	@Nullable
	private static Player player(DrifterEntity drifter, Request request) {
		Player player = drifter.level().getPlayerByUUID(request.player());
		return player != null && player.isAlive() ? player : null;
	}

	@Override
	public String id() {
		return "request";
	}

	@Override
	public String name(Goals.Context context) {
		Request request = request(context.drifter());
		return request == null ? "do as asked" : switch (request.kind()) {
			case COME -> "go over to a player who asked";
			case HELP -> "run to help a player";
			case GIVE -> "give a player what they asked for";
		};
	}

	@Override
	public double urgency(Goals.Context context) {
		Request request = request(context.drifter());
		if (request == null || player(context.drifter(), request) == null || context.threat().urgent()) {
			return 0.0;
		}

		return request.kind() == Kind.HELP ? 0.85 : 0.7;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public boolean urgent(Goals.Context context) {
		// (Someone's waiting.)
		return true;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 5;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		Request request = request(drifter);
		Player player = request == null ? null : player(drifter, request);
		if (player == null) {
			return null;
		}

		// (The request stays till it's done: someone's still waiting while it walks over.)
		String name = player.getGameProfile().getName();
		return new Activity(drifter) {
			private boolean done;

			@Override
			public String describe() {
				return switch (request.kind()) {
					case COME -> "go over to " + name;
					case HELP -> "run to help " + name;
					case GIVE -> "bring " + name + " " + request.count() + " " + new ItemStack(request.item()).getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
				};
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
				if (this.done || !player.isAlive() || player.level() != this.drifter.level()) {
					requests.remove(this.drifter.getUUID(), request);
					return Status.SUCCESS;
				}

				if (this.drifter.distanceTo(player) > CLOSE + 0.5) {
					// To where they are (and again if they've moved on; a follow never ends by itself).
					return this.begin(new NavigateTask(this.drifter, player.position(), CLOSE - 0.5, "walk over to " + name,
						request.kind() == Kind.HELP ? NavigateTask.Pace.TRAVEL : NavigateTask.Pace.STROLL));
				}

				this.done = true;
				this.drifter.getCamera().lookAt(player.getEyePosition(), CameraModel.TurnSpeed.NORMAL);
				switch (request.kind()) {
					case GIVE -> this.give(player);
					// (Here: and it sticks around a while, the way it follows when asked.)
					case HELP -> this.drifter.getSocial().watcher().invite(player, 20 * 30, 20 * 60);
					case COME -> {
					}
				}

				return Status.RUNNING;
			}

			/** What it said it would give, from what it can still spare. */
			private void give(Player player) {
				int left = request.count();
				for (Map.Entry<Integer, Integer> spare : com.dinglebob.drifter.brain.Storage.spare(this.drifter).entrySet()) {
					ItemStack stack = this.drifter.getInventory().getItem(spare.getKey());
					if (left <= 0 || !stack.is(request.item())) {
						continue;
					}

					int count = Math.min(left, Math.min(spare.getValue(), stack.getCount()));
					if (count > 0) {
						ItemStack given = stack.split(count);
						left -= count;
						this.drifter.tossForward(given);
					}
				}

				int given = request.count() - left;
				this.drifter.getMemory().thoughts().add(this.drifter.level().getGameTime(), given > 0 ? "gave " + name + " " + given + " "
					+ new ItemStack(request.item()).getHoverName().getString() + " (they asked)" : "had nothing left to give " + name);
			}
		};
	}
}
