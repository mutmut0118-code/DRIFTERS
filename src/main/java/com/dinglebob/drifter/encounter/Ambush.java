package com.dinglebob.drifter.encounter;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.social.Disposition;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * An ambush on the road (docs/ENCOUNTERS.md 4): a player out travelling, away from home, and bandits lying in wait ahead
 * of them, crouched out of sight. When the player comes close (or hits one of them) they spring it, no warning. If
 * nobody comes by, they give up and go.
 */
final class Ambush extends Encounter {
	private static final String BANDIT = "bandit";
	/** How far ahead of the player they lie in wait. */
	private static final double AHEAD = 55.0;
	/** They spring it when the player is this close and in sight. */
	private static final double SPRING = 20.0;
	/** Nobody came by: the player is this far off, or this long has passed. */
	private static final double PASSED_BY = 140.0;
	private static final long WAITS = 20L * 60 * 4;

	private final Vec3 spot;
	/** The road they watch: the way the player was coming from when they set up. */
	private final Vec3 road;
	private Vec3 lead;
	private boolean sprung;
	/** Someone's coming (a sound, a movement on the road): they step out to see who. */
	private boolean stirring;
	/** Where they heard or glimpsed someone (they go and look there), or null. */
	@Nullable
	private Vec3 noise;

	private Ambush(ServerPlayer player, Vec3 spot) {
		super(Encounters.Kind.AMBUSH, player);
		this.spot = spot;
		this.road = player.position();
		this.lead = spot;
	}

	@Nullable
	static Encounter start(ServerPlayer player, @Nullable Vec3 heading) {
		if (heading == null || heading.lengthSqr() < 1.0) {
			return null;
		}

		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		Vec3 way = heading.normalize();
		BlockPos spot = EncounterSpawner.hiddenSpot(level, player.position().add(way.scale(AHEAD)), 0.0, 12.0, null, random);
		if (spot == null) {
			return null;
		}

		Ambush ambush = new Ambush(player, Vec3.atBottomCenterOf(spot));
		int tier = EncounterSpawner.gearTier(player);
		// One band, in one set of colours.
		var band = EncounterSpawner.band(level, tier, random);
		int count = 2 + random.nextInt(2) + (tier >= 1 ? 1 : 0);
		for (int i = 0; i < count; i++) {
			BlockPos at = EncounterSpawner.ground(level, spot.getX() + random.nextInt(9) - 4, spot.getZ() + random.nextInt(9) - 4);
			boolean archer = i == count - 1 && count >= 3;
			DrifterEntity bandit = EncounterSpawner.spawn(level, at == null || !EncounterSpawner.hidden(level, at) ? spot : at, Archetype.BANDIT, drifter -> {
				drifter.getSocial().setDisposition(Disposition.BANDIT);
				EncounterSpawner.fighter(drifter, band, archer, random);
				EncounterSpawner.traits(drifter, 0.8, 0.9, 0.2, 0.5);
				drifter.getSocial().getOrMeet(player.getUUID(), player.getGameProfile().getName()).trust = -60.0;
			});
			if (bandit != null) {
				ambush.join(bandit, BANDIT);
				EncounterSpawner.add(level, bandit);
				think(bandit, "lying in wait by the road");
			}
		}

		return ambush;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 8;
	}

	@Override
	protected void update() {
		ServerPlayer player = this.player();
		if (this.members.isEmpty()) {
			this.end("the bandits are all dead");
			return;
		}

		if (player == null) {
			return;
		}

		for (DrifterEntity bandit : this.members) {
			boolean hit = bandit.getLastHurtByMob() == player && bandit.tickCount - bandit.getLastHurtByMobTimestamp() < 40;
			if (sees(bandit, player) && (bandit.distanceTo(player) < SPRING || this.sprung) || hit) {
				if (!this.sprung) {
					this.sprung = true;
					say(bandit, bandit.getRandom().nextBoolean() ? "now! get them!" : "your money or your life, " + this.playerName + "!");
				}

				this.lead = player.position();
				bandit.getDrifterBrain().pursue(bandit, player, this.now());
			}
		}

		// Something heard or glimpsed near (footsteps, a shape between the trees): they go and look, as they know it (what
		// they heard, where). Or someone close on the road they watch: they step out toward it.
		if (!this.sprung) {
			for (DrifterEntity bandit : this.members) {
				var awareness = bandit.getSenses().awarenessOf(player);
				if (awareness != null && (awareness.heardWithin(this.now(), 100) || awareness.seenWithin(this.now(), 100))) {
					Vec3 heard = awareness.believedPos(this.now(), 100);
					if (heard != null && heard.distanceTo(this.spot) < 24.0) {
						this.noise = heard;
						this.stirring = true;
					}
				}
			}

			if (player.position().distanceTo(this.spot) < 24.0) {
				this.stirring = true;
			}
		}

		if (!this.sprung && (player.position().distanceTo(this.spot) > PASSED_BY || this.now() - this.startedAt > WAITS)) {
			this.end("nobody came by");
		}
	}

	@Override
	public String status() {
		return super.status() + (this.sprung ? ", sprung" : ", waiting at " + BlockPos.containing(this.spot).toShortString());
	}

	@Override
	protected void playerDied() {
		this.end("the player is dead");
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		var pursuit = drifter.getDrifterBrain().pursuit();
		return pursuit != null && pursuit.active(drifter, this.now()) ? 0.0 : 0.9;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return this.sprung;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return this.sprung ? "close in on " + this.playerName : "lie in wait";
	}

	@Override
	public boolean hostileTo(DrifterEntity drifter, Player player) {
		return player.getUUID().equals(this.player);
	}

	@Override
	public boolean relentless(DrifterEntity drifter) {
		return true;
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		if (this.sprung) {
			Vec3 lead = this.lead;
			return new Script(drifter, "close in on " + this.playerName, 20 * 30)
				.then(() -> new NavigateTask(drifter, lead, 3.0, "close in", NavigateTask.Pace.TRAVEL));
		}

		Vec3 road = this.road;
		if (this.stirring) {
			// To what they heard, or out toward the road a few blocks; then a look round (who's there?).
			Vec3 out = this.noise != null ? this.noise
				: this.spot.add(road.subtract(this.spot).multiply(1.0, 0.0, 1.0).normalize().scale(6.0 + drifter.getRandom().nextDouble() * 4.0));
			return new Script(drifter, "see who's coming", 20 * 15)
				.then(() -> new NavigateTask(drifter, out, 2.0, "step out", NavigateTask.Pace.SNEAK))
				.then(() -> new com.dinglebob.drifter.executor.task.LookAroundTask(drifter, 2, 8));
		}

		return new Script(drifter, "lie in wait", 20 * 30).then(() -> new Crouch(drifter, road.add(0.0, 1.6, 0.0), 20 * 20));
	}

	/** Crouched, still, watching the road the player comes along. */
	private static final class Crouch extends Task {
		private final Vec3 toward;
		private final int ticks;
		private int t;

		Crouch(DrifterEntity drifter, Vec3 toward, int ticks) {
			super(drifter);
			this.toward = toward;
			this.ticks = ticks;
		}

		@Override
		public String describe() {
			return "lie in wait";
		}

		@Override
		protected int timeoutTicks() {
			return this.ticks + 20;
		}

		@Override
		protected Status onTick() {
			VirtualKeys keys = this.drifter.getKeys();
			keys.releaseAll();
			keys.set(VirtualKeys.Key.SNEAK, true);
			if (this.t % 40 == 0) {
				// Along the road, then a glance to one side or the other (a wider watch than one point).
				Vec3 dir = this.toward.subtract(this.drifter.getEyePosition()).multiply(1.0, 0.0, 1.0);
				double angle = (this.t / 40) % 3 == 0 ? 0.0 : this.drifter.getRandom().nextBoolean() ? 0.7 : -0.7;
				Vec3 turned = new Vec3(dir.x * Math.cos(angle) - dir.z * Math.sin(angle), 0.0, dir.x * Math.sin(angle) + dir.z * Math.cos(angle));
				this.drifter.getCamera().track(this.drifter.getEyePosition().add(turned), CameraModel.TurnSpeed.IDLE);
			}

			return ++this.t >= this.ticks ? Status.SUCCESS : Status.RUNNING;
		}

		@Override
		protected void onStop() {
			this.drifter.getKeys().releaseAll();
		}
	}
}
