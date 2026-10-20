package com.dinglebob.drifter.encounter;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The watcher (docs/ENCOUNTERS.md 27): at night, a still figure at the edge of sight, no name over its head, looking your
 * way. Come close and it turns and walks off, gone once you can't see it. Harmless (a Herobrine joke).
 */
final class Watcher extends Encounter {
	private static final String FIGURE = "figure";
	/** It stands this far off, and goes when someone comes this close. */
	private static final double NEAR = 35.0;
	private static final double FAR = 70.0;
	private static final double TOO_CLOSE = 32.0;
	/** It stands there this long at most. */
	private static final long STANDS = 20L * 90;

	private final Vec3 stand;
	@Nullable
	private DrifterEntity figure;
	private long arrivedAt = -1;

	private Watcher(ServerPlayer player, Vec3 stand) {
		super(Encounters.Kind.WATCHER, player);
		this.stand = stand;
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		// Where it'll stand: open ground in sight of the player now, at the edge of what they can make out.
		BlockPos stand = null;
		for (int i = 0; i < 40 && stand == null; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = NEAR + random.nextDouble() * (FAR - NEAR);
			BlockPos spot = EncounterSpawner.ground(level, (int)Math.floor(player.getX() + Math.cos(angle) * distance),
				(int)Math.floor(player.getZ() + Math.sin(angle) * distance));
			if (spot != null && Math.abs(spot.getY() - player.getY()) <= 24 && !EncounterSpawner.hidden(level, spot)) {
				stand = spot;
			}
		}

		if (stand == null) {
			return null;
		}

		// It comes from out of sight beyond that spot.
		Vec3 standAt = Vec3.atBottomCenterOf(stand);
		BlockPos from = EncounterSpawner.hiddenSpot(level, standAt, 6.0, 20.0, standAt.subtract(player.position()), random);
		if (from == null) {
			return null;
		}

		Watcher watcher = new Watcher(player, standAt);
		DrifterEntity figure = EncounterSpawner.spawn(level, from, Archetype.HERMIT, drifter -> {
			EncounterSpawner.traits(drifter, 0.6, 0.1, 0.5, 0.1);
			drifter.clearRealSkin();
			drifter.setCustomNameVisible(false);
		});
		if (figure == null) {
			return null;
		}

		watcher.join(figure, FIGURE);
		watcher.figure = figure;
		EncounterSpawner.add(level, figure);
		return watcher;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 5;
	}

	@Override
	protected void update() {
		ServerPlayer player = this.player();
		DrifterEntity figure = this.figure;
		if (figure == null || !figure.isAlive() || player == null) {
			this.end("gone");
			return;
		}

		if (this.arrivedAt < 0 && figure.position().distanceTo(this.stand) < 2.0) {
			this.arrivedAt = this.now();
		}

		if (player.distanceTo(figure) < TOO_CLOSE) {
			this.end("it went when they came close");
		} else if (this.arrivedAt >= 0 && this.now() - this.arrivedAt > STANDS) {
			this.end("it went back into the dark");
		}
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		return 0.95;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return "watch from afar";
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		if (player == null) {
			return null;
		}

		Script script = new Script(drifter, "watch from afar", 20 * 60);
		if (drifter.position().distanceTo(this.stand) > 2.0) {
			script.then(() -> new NavigateTask(drifter, this.stand, 1.0, "to the edge of sight", NavigateTask.Pace.STROLL));
		}

		return script.then(() -> new Stare(drifter, player, 20 * 30));
	}

	/** Standing still, looking at them. */
	private static final class Stare extends Task {
		private final ServerPlayer at;
		private final int ticks;
		private int t;

		Stare(DrifterEntity drifter, ServerPlayer at, int ticks) {
			super(drifter);
			this.at = at;
			this.ticks = ticks;
		}

		@Override
		public String describe() {
			return "stare";
		}

		@Override
		protected int timeoutTicks() {
			return this.ticks + 20;
		}

		@Override
		protected Status onTick() {
			this.drifter.getKeys().releaseAll();
			if (this.t % 10 == 0 && this.at.isAlive()) {
				this.drifter.getCamera().track(this.at.getEyePosition(), CameraModel.TurnSpeed.IDLE);
			}

			return ++this.t >= this.ticks ? Status.SUCCESS : Status.RUNNING;
		}
	}
}
