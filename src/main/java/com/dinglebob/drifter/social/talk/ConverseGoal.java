package com.dinglebob.drifter.social.talk;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;

/**
 * In a conversation ({@link Conversations}): it goes up to whoever it's talking with and stands facing them, as players
 * do when they stop to chat, till the conversation's over. Anything pressing (a fight, hunger) comes first.
 */
public final class ConverseGoal extends Goals.Goal {
	/** Close enough to talk, standing. */
	private static final double CLOSE = 3.5;

	@Override
	public String id() {
		return "converse";
	}

	@Override
	public String name(Goals.Context context) {
		Conversations.Talk talk = Conversations.of(context.drifter());
		return talk == null ? "talk" : "talk with " + talk.partnerOf(context.drifter()).getName().getString();
	}

	@Override
	public double urgency(Goals.Context context) {
		// (As a deal between two: the other's waiting. A danger comes first.)
		return Conversations.of(context.drifter()) == null || context.threat().urgent() || context.drifter().inCombat() ? 0.0 : 0.75;
	}

	@Override
	public boolean urgent(Goals.Context context) {
		// (Someone's waiting: it stops what it's doing at the next checkpoint.)
		return true;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 3;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		return new Activity(drifter) {
			@Override
			public String describe() {
				Conversations.Talk talk = Conversations.of(this.drifter);
				return talk == null ? "talk" : "talk with " + talk.partnerOf(this.drifter).getName().getString();
			}

			@Override
			protected int timeoutTicks() {
				return 20 * 90;
			}

			@Override
			protected boolean countsAsFailure(Task step) {
				return false;
			}

			@Override
			protected Status next() {
				Conversations.Talk talk = Conversations.of(this.drifter);
				if (talk == null) {
					return Status.SUCCESS;
				}

				DrifterEntity partner = talk.partnerOf(this.drifter);
				if (this.drifter.distanceTo(partner) > CLOSE) {
					return this.begin(new NavigateTask(this.drifter, partner.position(), CLOSE - 1.0, "walk over to " + partner.getName().getString()));
				}

				return this.begin(new Face(this.drifter, partner));
			}
		};
	}

	/** Standing facing it a moment (looking at its face, as it moves). */
	private static final class Face extends Task {
		private final DrifterEntity other;
		private int ticks;

		Face(DrifterEntity drifter, DrifterEntity other) {
			super(drifter);
			this.other = other;
		}

		@Override
		public String describe() {
			return "listen to " + this.other.getName().getString();
		}

		@Override
		protected int timeoutTicks() {
			return 80;
		}

		@Override
		protected Status onTick() {
			VirtualKeys keys = this.drifter.getKeys();
			keys.releaseAll();
			this.drifter.getCamera().track(this.other.getEyePosition(), CameraModel.TurnSpeed.IDLE);
			return ++this.ticks >= 40 || Conversations.of(this.drifter) == null ? Status.SUCCESS : Status.RUNNING;
		}

		@Override
		protected void onStop() {
			this.drifter.getKeys().releaseAll();
		}
	}
}
