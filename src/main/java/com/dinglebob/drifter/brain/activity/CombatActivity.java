package com.dinglebob.drifter.brain.activity;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Threats;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.AttackTask;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.LookAroundTask;

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
		if ((step instanceof AttackTask || step instanceof com.dinglebob.drifter.executor.task.ShootTask) && status == Status.SUCCESS) {
			this.kills++;
			// (A line per kill: the blaze tests count them against the rods it picks up.)
			this.think("got it (" + step.describe() + ")");
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
				// A bow at range, at fliers and at creepers still a few blocks off (section 13 "Ranged mechanics").
				String name = target.getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT);
				if (com.dinglebob.drifter.hands.Archery.shouldShoot(this.drifter, target)
					&& (this.bowFailures < MAX_BOW_FAILURES || com.dinglebob.drifter.hands.Archery.flying(target))) {
					return this.begin(new com.dinglebob.drifter.executor.task.ShootTask(this.drifter, target, name));
				}

				return this.begin(new AttackTask(this.drifter, target, target.getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT)));
			}

			this.phase = 1;
			if (this.kills > 0) {
				this.think("the fight is over");
			}
		}

		return switch (this.phase++) {
			case 1 -> this.begin(new LookAroundTask(this.drifter));
			case 2 -> this.begin(new CollectItemsTask(this.drifter, DROP_PICKUP_RADIUS));
			default -> Status.SUCCESS;
		};
	}
}
