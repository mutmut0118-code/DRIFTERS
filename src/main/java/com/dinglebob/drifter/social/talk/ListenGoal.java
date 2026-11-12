package com.dinglebob.drifter.social.talk;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.Steering;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Talking mode (docs/PLAYER_TALK.md rule 0.3; the user, 2026-11-12: "they never really stop and talk and wait for an
 * answer"): while a player is talking with it ({@link PlayerConvo#listeningTo}), it stops what it's doing, turns to them
 * and waits for their next message; each message renews the wait. Danger comes first (a threat ends it), and so does
 * anything it's doing for them that has it moving (following, leading the way).
 */
public final class ListenGoal extends Goals.Goal {
	@Override
	public String id() {
		return "listen";
	}

	@Override
	public String name(Goals.Context context) {
		return "stand and talk with a player";
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		return context.threat().urgent() || drifter.isSleeping() || drifter.inAFight() || GuideGoal.leading(drifter)
			|| PlayerConvo.listeningTo(drifter) == null ? 0.0 : 0.78;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public boolean urgent(Goals.Context context) {
		// (A player is waiting on it.)
		return true;
	}

	@Override
	public int minCommitTicks() {
		return 20;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		ServerPlayer first = PlayerConvo.listeningTo(drifter);
		if (first == null) {
			return null;
		}

		String name = first.getGameProfile().getName();
		return new Activity(drifter) {
			@Override
			public String describe() {
				return "talk with " + name;
			}

			@Override
			protected int timeoutTicks() {
				return 20 * 60 * 5;
			}

			@Override
			protected Status next() {
				ServerPlayer player = PlayerConvo.listeningTo(this.drifter);
				if (player == null) {
					return Status.SUCCESS;
				}

				Steering.stopMoving(this.drifter);
				Vec3 pointed = PlayerConvo.pointedAt(this.drifter, player);
				this.drifter.getCamera().lookAt(pointed != null ? pointed : player.getEyePosition(), CameraModel.TurnSpeed.NORMAL);
				return Status.RUNNING;
			}
		};
	}
}
