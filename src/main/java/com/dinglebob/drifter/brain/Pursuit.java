package com.dinglebob.drifter.brain;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.perception.Awareness;
import com.dinglebob.drifter.perception.HeardSound;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Who it's after, and what it knows of where they went (user, 2026-10-17: "its really easy for it to lose me ... make like
 * a prediction/assumption system where it uses its logic to try and find me better ... the chase lasts more than 5
 * minutes because it keeps trying to look for me"). Only what it saw and heard: where it last saw them and which way they
 * were going, and sounds since from about where they could have got to (footsteps, digging, a door), which it takes to be
 * them, as a player would. From that, where they probably are now. Kept for minutes (longer for a Bandit, a delver, a
 * manhunt hunter, or a vengeful one), renewed each time it sees or hears them. Working memory: never saved.
 */
public final class Pursuit {
	public final LivingEntity target;
	public final String name;
	private long until;
	private Vec3 seenPos;
	private Vec3 seenVelocity = Vec3.ZERO;
	private long seenAt;
	@Nullable
	private Vec3 cluePos;
	private long clueAt = Long.MIN_VALUE / 2;
	/** Spots it has looked in (and when), so it doesn't search the same corner twice. */
	private final List<Vec3> searched = new ArrayList<>();

	/** How fast someone on the run can be, blocks a tick (a sprinting player, with a little over). */
	private static final double RUN_SPEED = 0.3;
	/** It reckons they kept going the way they were for at most this long (ticks) before it has to search. */
	private static final long DEAD_RECKONING = 20 * 3;
	/** A sound counts as theirs within this much of where they could have got to (blocks). */
	private static final double CLUE_SLACK = 6.0;
	/** A sound renews the chase by this much at least. */
	private static final long HEARD_RENEWS = 20 * 60;

	Pursuit(LivingEntity target, long now, long lasts) {
		this.target = target;
		this.name = target instanceof Player player ? player.getGameProfile().getName() : target.getName().getString();
		this.seenPos = target.position();
		this.seenAt = now;
		this.until = now + lasts;
	}

	/** How long (ticks) it keeps after someone out of sight: minutes, more for the ruthless and the vengeful. */
	public static long persistence(DrifterEntity drifter) {
		var personality = drifter.getPersonality();
		boolean relentless = com.dinglebob.drifter.social.BanditGoals.ruthless(drifter) || drifter.isDelver()
			|| com.dinglebob.drifter.social.Manhunt.isHunter(drifter);
		double minutes = (relentless ? 6.0 : 1.5) + (relentless ? 2.0 : 1.5) * personality.get(Trait.VENGEFULNESS)
			+ personality.get(Trait.AGGRESSION) + 0.5 * personality.get(Trait.PATIENCE);
		return (long)(minutes * 1200);
	}

	public boolean active(DrifterEntity drifter, long now) {
		return now < this.until && this.target.isAlive() && !this.target.isRemoved() && this.target.level() == drifter.level()
			&& !(this.target instanceof Player player && (player.isCreative() || player.isSpectator()));
	}

	/** It sees them now: where, and which way they're going (renews the chase). */
	public void sighted(long now, long lasts) {
		this.seenPos = this.target.position();
		this.seenVelocity = this.target.getDeltaMovement().multiply(1.0, 0.0, 1.0);
		this.seenAt = now;
		this.cluePos = null;
		this.until = Math.max(this.until, now + lasts);
		this.searched.clear();
	}

	/**
	 * What it saw and heard since it last looked: updates the sighting while they're in view, and takes the newest sound
	 * from about where they could have got to as theirs. True if that sound is a new clue.
	 */
	public boolean observe(DrifterEntity drifter, long now, long lasts) {
		Awareness awareness = drifter.getSenses().awarenessOf(this.target);
		if (awareness != null && awareness.visibleNow) {
			this.sighted(now, lasts);
			return false;
		}

		// Its own sounds it knew for theirs (a creature's call; not a player's).
		long since = Math.max(this.seenAt, this.clueAt);
		if (awareness != null && awareness.heardPos != null && awareness.heardTick > since) {
			return this.clue(awareness.heardPos, awareness.heardTick, now);
		}

		HeardSound best = null;
		for (HeardSound sound : drifter.getSenses().recentSounds()) {
			if (sound.time() <= since || !(sound.kind() == HeardSound.Kind.MOVEMENT || sound.kind() == HeardSound.Kind.BLOCKS
				|| sound.kind() == HeardSound.Kind.CONTAINER || sound.kind() == HeardSound.Kind.EATING)) {
				continue;
			}

			// Where they could have got to since they were last seen or heard.
			Vec3 from = this.cluePos != null && this.clueAt > this.seenAt ? this.cluePos : this.seenPos;
			double reach = CLUE_SLACK + RUN_SPEED * (sound.time() - since);
			if (sound.roughPos().distanceTo(from) <= reach && (best == null || sound.time() > best.time())) {
				best = sound;
			}
		}

		return best != null && this.clue(best.roughPos(), best.time(), now);
	}

	private boolean clue(Vec3 where, long when, long now) {
		this.cluePos = where;
		this.clueAt = when;
		this.until = Math.max(this.until, now + HEARD_RENEWS);
		return true;
	}

	/** Where it believes they are: a fresh sound, else where it saw them plus a few seconds the way they were going. */
	public Vec3 believed(long now) {
		if (this.cluePos != null && this.clueAt > this.seenAt) {
			return this.cluePos;
		}

		long ahead = Math.min(now - this.seenAt, DEAD_RECKONING);
		return this.seenPos.add(this.seenVelocity.scale(ahead));
	}

	public Vec3 seenPos() {
		return this.seenPos;
	}

	public long seenAt() {
		return this.seenAt;
	}

	/** Which way they were going (flat, unit length), or zero if they stood still. */
	public Vec3 heading() {
		return this.seenVelocity.lengthSqr() < 1.0E-4 ? Vec3.ZERO : this.seenVelocity.normalize();
	}

	@Nullable
	public Vec3 cluePos() {
		return this.cluePos;
	}

	public long clueAt() {
		return this.clueAt;
	}

	public void searchedAt(Vec3 spot) {
		this.searched.add(spot);
	}

	public boolean searchedNear(Vec3 spot, double within) {
		return this.searched.stream().anyMatch(done -> done.distanceToSqr(spot) < within * within);
	}

	public long until() {
		return this.until;
	}
}
