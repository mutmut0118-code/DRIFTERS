package com.dinglebob.drifter.brain.activity;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.Pursuit;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.AttackTask;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.perception.Awareness;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * After someone it's fighting who got out of sight (user, 2026-10-17: "the chase lasts more than 5 minutes because it
 * keeps trying to look for me"; "sometimes it pauses, and just looks around"). In sight, it fights. Out of sight it reasons
 * as a player does, on what it saw and heard only (Pursuit): to the newest sound from about where they could be (it takes
 * it for them), else where it last saw them, then on the way they were going, then the spots round there it can't see
 * into, ahead of them first (round a corner, behind a rise, into a tunnel), a quick glance at each and on to the next.
 * No standing about. It keeps on till the Pursuit runs out (minutes; a Bandit or delver longer), they're dead, or it's
 * too hurt.
 */
public class PursuitActivity extends Activity {
	/** It glances round at each spot it searches this many times (quick). */
	private static final int GLANCE_TURNS = 3;
	private static final int GLANCE_PAUSE = 5;
	/** How far on the way they were going it follows before searching. */
	private static final double FOLLOW_ON = 16.0;
	/** Search spots: this far from where it thinks they are, at most and at least. */
	private static final double SEARCH_NEAR = 5.0;
	private static final double SEARCH_FAR = 20.0;
	/** Spots within this of one it searched don't count again. */
	private static final double SEARCHED_WITHIN = 6.0;

	private boolean wentToLastSeen;
	private boolean followedOn;
	private long clueTaken = Long.MIN_VALUE;
	private boolean arrived;
	private int searches;
	private boolean told;

	public PursuitActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Nullable
	private Pursuit pursuit() {
		return this.drifter.getDrifterBrain().pursuit();
	}

	@Override
	public String describe() {
		Pursuit pursuit = this.pursuit();
		return pursuit == null ? "chase" : "chase " + pursuit.name;
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 15;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		return false;
	}

	@Override
	public boolean atCheckpoint() {
		return true;
	}

	/** In sight again, or a new sound: it breaks off where it was going. */
	@Override
	protected boolean rethink() {
		if (this.ticksRunning() % 5 != 0) {
			return false;
		}

		Pursuit pursuit = this.pursuit();
		if (pursuit == null) {
			return true;
		}

		long now = this.drifter.level().getGameTime();
		boolean newClue = pursuit.observe(this.drifter, now, Pursuit.persistence(this.drifter)) && pursuit.clueAt() > this.clueTaken;
		return this.sees(pursuit) && !(this.current() instanceof AttackTask) || newClue;
	}

	private boolean sees(Pursuit pursuit) {
		Awareness awareness = this.drifter.getSenses().awarenessOf(pursuit.target);
		return awareness != null && awareness.visibleNow;
	}

	@Override
	protected Status next() {
		Pursuit pursuit = this.pursuit();
		long now = this.drifter.level().getGameTime();
		if (pursuit == null) {
			return Status.SUCCESS;
		}

		if (!pursuit.active(this.drifter, now)) {
			if (!pursuit.target.isAlive()) {
				this.think(pursuit.name + " is dead");
			} else if (now >= pursuit.until()) {
				this.think("gave up looking for " + pursuit.name);
			}

			this.drifter.getDrifterBrain().endPursuit();
			return Status.SUCCESS;
		}

		pursuit.observe(this.drifter, now, Pursuit.persistence(this.drifter));
		if (this.sees(pursuit)) {
			this.arrived = false;
			this.told = false;
			return this.begin(new AttackTask(this.drifter, pursuit.target, pursuit.name));
		}

		if (!this.told) {
			this.told = true;
			this.think("lost sight of " + pursuit.name + ": going after them");
		}

		// A sound from about where they could be: there, at once.
		Vec3 clue = pursuit.cluePos();
		if (clue != null && pursuit.clueAt() > this.clueTaken && pursuit.clueAt() > pursuit.seenAt()) {
			this.clueTaken = pursuit.clueAt();
			this.think("heard something over there: " + pursuit.name + "?");
			return this.go(clue, "check the sound");
		}

		// Where it saw them last.
		if (!this.wentToLastSeen) {
			this.wentToLastSeen = true;
			return this.go(pursuit.seenPos(), "where it last saw " + pursuit.name);
		}

		// On the way they were going.
		Vec3 heading = pursuit.heading();
		if (!this.followedOn && heading.lengthSqr() > 0.0) {
			this.followedOn = true;
			this.think(pursuit.name + " was heading that way");
			return this.go(pursuit.seenPos().add(heading.scale(FOLLOW_ON)), "the way " + pursuit.name + " went");
		}

		// Got to a spot: a quick glance round, then on.
		if (this.arrived) {
			this.arrived = false;
			pursuit.searchedAt(this.drifter.position());
			return this.begin(new LookAroundTask(this.drifter, GLANCE_TURNS, GLANCE_PAUSE));
		}

		if (this.searches++ == 0) {
			this.think("searching round about for " + pursuit.name);
		}

		Vec3 anchor = clue != null && pursuit.clueAt() > pursuit.seenAt() ? clue : pursuit.believed(now);
		Vec3 spot = this.searchSpot(pursuit, anchor);
		return this.go(spot, "search for " + pursuit.name);
	}

	private Status go(Vec3 where, String what) {
		this.arrived = true;
		return this.begin(new NavigateTask(this.drifter, where, 2.5, what, NavigateTask.Pace.TRAVEL));
	}

	/**
	 * The next spot to search: round where it thinks they are, ahead of the way they were going first, one it can't see
	 * into from here (where someone could be out of sight), on ground it knows, not one it searched already. If it knows of
	 * none, a point out that way to explore toward.
	 */
	private Vec3 searchSpot(Pursuit pursuit, Vec3 anchor) {
		Level level = this.drifter.level();
		Vec3 heading = pursuit.heading();
		if (heading.lengthSqr() == 0.0) {
			Vec3 away = anchor.subtract(this.drifter.position()).multiply(1.0, 0.0, 1.0);
			heading = away.lengthSqr() > 1.0E-4 ? away.normalize() : Vec3.directionFromRotation(0.0F, this.drifter.getRandom().nextFloat() * 360.0F);
		}

		Vec3 eye = this.drifter.getEyePosition();
		Vec3 best = null;
		double bestScore = -Double.MAX_VALUE;
		for (int i = 0; i < 24; i++) {
			// Ahead mostly; wider as the search goes on.
			double spread = 0.6 + 0.15 * Math.min(this.searches, 10);
			double angle = this.drifter.getRandom().nextGaussian() * spread;
			Vec3 direction = heading.yRot((float)angle);
			double distance = SEARCH_NEAR + this.drifter.getRandom().nextDouble() * (SEARCH_FAR - SEARCH_NEAR);
			BlockPos ground = this.groundNear(BlockPos.containing(anchor.add(direction.scale(distance))));
			if (ground == null) {
				continue;
			}

			Vec3 spot = Vec3.atBottomCenterOf(ground);
			if (pursuit.searchedNear(spot, SEARCHED_WITHIN)) {
				continue;
			}

			boolean hidden = level.clip(new ClipContext(eye, spot.add(0.0, 1.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				this.drifter)).getType() != HitResult.Type.MISS;
			double score = (hidden ? 2.0 : 0.0) + direction.dot(heading) - distance * 0.03;
			if (score > bestScore) {
				bestScore = score;
				best = spot;
			}
		}

		return best != null ? best : anchor.add(heading.yRot((float)(this.drifter.getRandom().nextGaussian() * 1.2)).scale(12.0));
	}

	/** Ground it knows it could stand on in this column, within a few blocks up or down, or null. */
	@Nullable
	private BlockPos groundNear(BlockPos around) {
		Level level = this.drifter.level();
		for (int dy = 0; dy <= 6; dy++) {
			for (int sign : new int[] {1, -1}) {
				BlockPos pos = around.above(dy * sign);
				if (this.drifter.getSenses().terrain().isKnown(pos.below()) && KnownBlocks.isStandable(level, pos)) {
					return pos;
				}
			}
		}

		return null;
	}

	/** The chase (goal "pursue"): over work, under running for its life; longer and keener for the relentless. */
	public static final class Goal extends Goals.Goal {
		@Override
		public String id() {
			return "pursue";
		}

		@Override
		public String name(Goals.Context context) {
			Pursuit pursuit = context.drifter().getDrifterBrain().pursuit();
			return pursuit == null ? "chase someone" : "chase " + pursuit.name;
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			Pursuit pursuit = drifter.getDrifterBrain().pursuit();
			if (pursuit == null || !pursuit.active(drifter, drifter.level().getGameTime()) || drifter.getHealth() < drifter.getMaxHealth() * 0.35F) {
				return 0.0;
			}

			boolean relentless = com.dinglebob.drifter.social.BanditGoals.ruthless(drifter) || drifter.isDelver()
				|| com.dinglebob.drifter.social.Manhunt.isHunter(drifter);
			return relentless ? 0.97 : 0.88;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public double risk(Goals.Context context) {
			return 1.0;
		}

		/** The relentless drop what they're doing for it. */
		@Override
		public boolean urgent(Goals.Context context) {
			return this.urgency(context) >= 0.97;
		}

		@Override
		public int minCommitTicks() {
			return 0;
		}

		@Override
		public Activity start(Goals.Context context) {
			return new PursuitActivity(context.drifter());
		}
	}
}
