package com.dinglebob.drifter.encounter;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.ShootTask;
import com.dinglebob.drifter.hands.Archery;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A sniper (docs/ENCOUNTERS.md 8): while the player works in one place, an archer settles on higher ground some way off
 * and takes shots at them. It stays up there while it can see them; come close (or run it out of arrows) and it draws
 * its sword. Out of sight a while, it gives up and goes. Bringing it down is no crime.
 */
final class Sniper extends Encounter {
	private static final String SNIPER = "sniper";
	/** It shoots from this far off, from at least this high above them. */
	private static final double NEAR = 24.0;
	private static final double FAR = 36.0;
	private static final int ABOVE = 1;
	/** They come this close: the bow goes, the sword comes out. */
	private static final double CLOSE = 12.0;
	/** It gives up after this long without seeing them. */
	private static final long LOSES_THEM = 20L * 60;

	private Vec3 perch;
	@Nullable
	private DrifterEntity sniper;
	private boolean exposed;
	private boolean firstShot;
	/** Up at its spot (it gives up only once there and they're out of sight long enough). */
	private boolean perched;
	private long lastSeen;
	/** Times it has moved to a new spot with a view of where it last saw them. */
	private int moves;
	/** Out of sight this long at its spot: it moves to another. */
	private static final long MOVES_AFTER = 20L * 20;
	private static final int MOST_MOVES = 3;

	private Sniper(ServerPlayer player, Vec3 perch) {
		super(Encounters.Kind.SNIPER, player);
		this.perch = perch;
		this.lastSeen = this.now();
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		// The perch: higher ground in sight of them.
		BlockPos perch = perch(level, player.getEyePosition(), player.blockPosition().getY(), random);
		if (perch == null) {
			return null;
		}

		Vec3 perchAt = Vec3.atBottomCenterOf(perch);
		BlockPos from = EncounterSpawner.hiddenSpot(level, perchAt, 8.0, 22.0, perchAt.subtract(player.position()), random);
		if (from == null) {
			return null;
		}

		Sniper encounter = new Sniper(player, perchAt);
		int tier = EncounterSpawner.gearTier(player);
		DrifterEntity sniper = EncounterSpawner.spawn(level, from, Archetype.PVP_SWEAT, drifter -> {
			EncounterSpawner.fighter(drifter, tier, true, random);
			EncounterSpawner.traits(drifter, 0.7, 0.8, 0.6, 0.5);
			var r = drifter.getSocial().getOrMeet(player.getUUID(), player.getGameProfile().getName());
			r.trust = -60.0;
			r.lastSeenPos = player.blockPosition();
		});
		if (sniper == null) {
			return null;
		}

		encounter.join(sniper, SNIPER);
		encounter.sniper = sniper;
		EncounterSpawner.add(level, sniper);
		think(sniper, "spotted " + player.getGameProfile().getName() + " from afar: finding a spot up high");
		return encounter;
	}

	/** The highest ground some way off {@code at} with a clear line to it (standing there), or null. */
	@Nullable
	private static BlockPos perch(ServerLevel level, Vec3 at, int y, net.minecraft.util.RandomSource random) {
		BlockPos perch = null;
		int best = ABOVE - 1;
		for (int i = 0; i < 48; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = NEAR + random.nextDouble() * (FAR - NEAR);
			BlockPos spot = EncounterSpawner.ground(level, (int)Math.floor(at.x + Math.cos(angle) * distance), (int)Math.floor(at.z + Math.sin(angle) * distance));
			int rise = spot == null ? 0 : spot.getY() - y;
			if (spot != null && rise > best && rise <= 20 && clearShot(level, spot, at)) {
				best = rise;
				perch = spot;
			}
		}

		return perch;
	}

	/** From standing there, a clear line to that point (their head), as it would look (leaves and all block it). */
	private static boolean clearShot(ServerLevel level, BlockPos spot, Vec3 at) {
		Vec3 eye = Vec3.atBottomCenterOf(spot).add(0.0, 1.62, 0.0);
		return level.clip(new ClipContext(eye, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()))
			.getType() == HitResult.Type.MISS
			&& level.clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()))
			.getType() == HitResult.Type.MISS;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 8;
	}

	@Override
	protected void update() {
		DrifterEntity sniper = this.sniper;
		ServerPlayer player = this.player();
		if (sniper == null || !sniper.isAlive()) {
			this.end("the sniper is dead");
			return;
		}

		if (player == null) {
			this.end("they're gone");
			return;
		}

		if (sees(sniper, player) || !this.perched) {
			this.lastSeen = this.now();
		}

		this.perched |= sniper.position().distanceTo(this.perch) <= 1.5;

		boolean hit = sniper.getLastHurtByMob() == player && sniper.tickCount - sniper.getLastHurtByMobTimestamp() < 40;
		if (!this.exposed && (player.distanceTo(sniper) < CLOSE && knowsWhere(sniper, player) || hit && player.distanceTo(sniper) < 20.0 || !Archery.ready(sniper))) {
			this.exposed = true;
			think(sniper, Archery.ready(sniper) ? "they're on to it: sword out" : "out of arrows");
		}

		if (this.exposed) {
			if (sees(sniper, player)) {
				sniper.getDrifterBrain().pursue(sniper, player, this.now());
			}

			if (player.distanceTo(sniper) > 48.0) {
				this.end("it slipped away");
			}
		} else if (this.perched && this.now() - this.lastSeen > MOVES_AFTER && this.moves < MOST_MOVES) {
			// No sight of them from here: a spot with a view of where it last saw them.
			var awareness = sniper.getSenses().awarenessOf(player);
			var r = sniper.getSocial().get(player.getUUID());
			Vec3 last = awareness != null && awareness.lastSeenPos != null ? awareness.lastSeenPos
				: r != null && r.lastSeenPos != null ? Vec3.atBottomCenterOf(r.lastSeenPos) : null;
			BlockPos next = last == null ? null : perch(this.level, last.add(0.0, 1.6, 0.0), (int)Math.floor(last.y), sniper.getRandom());
			this.moves++;
			if (next != null) {
				this.perch = Vec3.atBottomCenterOf(next);
				this.perched = false;
				think(sniper, "can't see them from here: moving to " + next.toShortString());
			}

			this.lastSeen = this.now();
		} else if (this.now() - this.lastSeen > LOSES_THEM) {
			this.end("lost them: it gave up");
		}
	}

	@Override
	protected void playerDied() {
		this.end("the player is dead");
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		// Up close, the fight is the chase's.
		return this.exposed ? 0.0 : 0.95;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return !this.exposed;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return drifter.position().distanceTo(this.perch) > 1.5 ? "find a spot up high" : "pick " + this.playerName + " off from up here";
	}

	@Override
	public boolean hostileTo(DrifterEntity drifter, Player player) {
		return this.exposed && player.getUUID().equals(this.player);
	}

	@Override
	public boolean relentless(DrifterEntity drifter) {
		return this.exposed;
	}

	@Override
	public Killing killing(DrifterEntity drifter, Player player) {
		return Killing.NONE;
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		if (player == null || this.exposed) {
			return null;
		}

		if (drifter.position().distanceTo(this.perch) > 1.5) {
			Vec3 perch = this.perch;
			return new Script(drifter, "find a spot up high", 20 * 60)
				.then(() -> new NavigateTask(drifter, perch, 0.5, "up to a spot to shoot from", NavigateTask.Pace.STROLL));
		}

		if (sees(drifter, player) && drifter.distanceTo(player) <= Archery.MAX_RANGE) {
			return new Script(drifter, "pick " + this.playerName + " off", 20 * 25)
				.now(() -> {
					if (!this.firstShot) {
						this.firstShot = true;
						think(drifter, "taking shots at " + this.playerName);
					}
				})
				.then(() -> new ShootTask(drifter, player, this.playerName));
		}

		// Waiting for a shot, watching where it last saw them (standing: crouched, the crest of its hill was in the way).
		return new Script(drifter, "wait for a shot", 20 * 4).until(() -> sees(drifter, player))
			.then(() -> new Hold(drifter, "wait for a shot", 20 * 3, false, () -> {
				var awareness = drifter.getSenses().awarenessOf(player);
				if (awareness != null && awareness.lastSeenPos != null) {
					return awareness.lastSeenPos.add(0.0, 1.5, 0.0);
				}

				// (Where it spotted them from afar.)
				var r = drifter.getSocial().get(player.getUUID());
				return r != null && r.lastSeenPos != null ? Vec3.atCenterOf(r.lastSeenPos).add(0.0, 1.0, 0.0) : null;
			}));
	}

	@Override
	public String status() {
		return super.status() + (this.exposed ? ", fighting up close" : this.firstShot ? ", shooting from " + BlockPos.containing(this.perch).toShortString()
			: ", heading for " + BlockPos.containing(this.perch).toShortString());
	}
}
