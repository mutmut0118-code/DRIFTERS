package com.dinglebob.drifter.brain.activity;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Threats;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.AttackTask;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.ThrowTridentTask;

import net.minecraft.world.entity.LivingEntity;

/**
 * Fighting hostiles (DESIGN.md section 13): takes on the most pressing threat with the right protocol, then the next,
 * and when none is left it does what players do after a fight: a nervous look around, then picks up the drops.
 * Eating to top off health is the eat goal's job (it's hurt, so eating scores high).
 */
public class CombatActivity extends Activity {
	private static final double DROP_PICKUP_RADIUS = 8.0;

	private int phase;
	private int kills;
	/** Bow shots that didn't end the fight; after a few it sticks to the sword (unless it can't reach the target). */
	private int bowFailures;
	private static final int MAX_BOW_FAILURES = 3;
	/** Trident throws that didn't come off (it came close, out of sight, it wouldn't throw); after this many, melee. */
	private int throwFailures;
	private static final int MAX_THROW_FAILURES = 3;
	/** It couldn't get to its target to fight it hand to hand (no path): a trident may be thrown then (Archery.shouldThrow). */
	private boolean meleeFailed;

	public CombatActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "fight";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 2;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		// A throw counts as a kill if the target died before the trident landed.
		if (step instanceof ThrowTridentTask throwing) {
			if (status != Status.SUCCESS) {
				this.throwFailures++;
			} else if (throwing.killed()) {
				this.kills++;
				this.think("got it (" + step.describe() + ")");
			}

			return;
		}

		if ((step instanceof AttackTask || step instanceof com.dinglebob.drifter.executor.task.ShootTask) && status == Status.SUCCESS) {
			this.kills++;
			// (A line per kill: the blaze tests count them against the rods it picks up. Shots too, for a bow or crossbow.)
			int shots = step instanceof com.dinglebob.drifter.executor.task.ShootTask shoot ? shoot.shots() : 0;
			this.think("got it (" + step.describe() + (shots > 0 ? ", " + shots + (shots == 1 ? " shot" : " shots") : "") + ")");
		}

		if (step instanceof AttackTask && status == Status.FAILURE && step.failureReason() != null && step.failureReason().startsWith("can't get to it")) {
			this.meleeFailed = true;
		}

		// The bow didn't work out (it came close, out of sight, out of arrows): the sword for this fight.
		if (step instanceof com.dinglebob.drifter.executor.task.ShootTask && status != Status.SUCCESS) {
			this.bowFailures++;
		}
	}

	@Override
	public boolean atCheckpoint() {
		// A fight can be broken off any time (to run when it turns bad); a swing is instant.
		return true;
	}

	/** Looking around or looting after a fight and something new comes at it: back to fighting (found in review). */
	@Override
	protected boolean rethink() {
		if (this.phase == 0 || this.ticksRunning() % 5 != 0) {
			return false;
		}

		Threats.Assessment assessment = Threats.assess(this.drifter);
		if (assessment.urgent() && assessment.decision().fights()) {
			this.phase = 0;
			return true;
		}

		return false;
	}

	@Override
	protected Status next() {
		if (this.phase == 0) {
			Threats.Assessment assessment = Threats.assess(this.drifter);
			@Nullable LivingEntity target = assessment.decision().fights() ? assessment.main() : null;
			if (target != null) {
				// A bow or crossbow at range, at fliers and at creepers still a few blocks off (section 13 "Ranged mechanics";
				// ShootTask takes out the best launcher it can shoot now: Shields.bestBowSlot).
				String name = target.getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT);
				// No bow ready, or the target is in water (arrows soon stop there, a trident doesn't): its trident, thrown; it
				// fetches it afterwards, a Loyalty one comes back. (Asked before the bow: with a bow ready, Archery.shouldThrow is
				// only true for a target in water, so otherwise the bow comes first as before.)
				if (this.throwFailures < MAX_THROW_FAILURES && com.dinglebob.drifter.hands.Archery.shouldThrow(this.drifter, target, this.meleeFailed)) {
					return this.begin(new ThrowTridentTask(this.drifter, target, name));
				}

				if (com.dinglebob.drifter.hands.Archery.shouldShoot(this.drifter, target)
					&& (this.bowFailures < MAX_BOW_FAILURES || com.dinglebob.drifter.hands.Archery.flying(target))) {
					return this.begin(new com.dinglebob.drifter.executor.task.ShootTask(this.drifter, target, name));
				}

				// It would have shot, but its bow's power (as it believes) would hurt a friend, itself or its home: the sword.
				String held = com.dinglebob.drifter.hands.Archery.wantsBow(this.drifter, target) ? com.dinglebob.drifter.hands.Archery.heldFire(this.drifter, target) : null;
				if (held != null) {
					this.think(held);
				}

				return this.begin(new AttackTask(this.drifter, target, target.getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT)));
			}

			// Someone it's after got away out of sight: no looking round and picking up, the chase goes on (the pursue goal;
			// user, 2026-10-17: "sometimes it pauses, and just looks around").
			var pursuit = this.drifter.getDrifterBrain().pursuit();
			if (pursuit != null && pursuit.active(this.drifter, this.drifter.level().getGameTime())) {
				return Status.SUCCESS;
			}

			this.phase = 1;
			if (this.kills > 0) {
				this.think("the fight is over");
			} else if (this.drifter.wantsTridentBack(this.drifter.level().getGameTime())) {
				// Back only to fetch its thrown trident (Goals.Fight holds for it): no looking round first.
				this.phase = 2;
			}
		}

		return switch (this.phase++) {
			case 1 -> this.begin(new LookAroundTask(this.drifter));
			case 2 -> this.begin(new CollectItemsTask(this.drifter, DROP_PICKUP_RADIUS));
			default -> Status.SUCCESS;
		};
	}
}
