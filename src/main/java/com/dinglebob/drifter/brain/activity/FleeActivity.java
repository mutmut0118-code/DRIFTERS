package com.dinglebob.drifter.brain.activity;

import com.dinglebob.drifter.brain.Threats;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.WaitTask;
import com.dinglebob.drifter.executor.task.AttackTask;
import com.dinglebob.drifter.hands.Tools;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Getting away from a fight it can't win (DESIGN.md section 13, "Retreat and disengagement" and the ratio table):
 * runs away from the hostiles, sprinting; when it's swarmed by melee mobs and has blocks, it pillars three blocks up
 * out of their reach and waits there (section 13, "Defensive tools"). At night the shelter goal digs it in once it's
 * clear. If something keeps hitting it from close by, the assessment calls that "cornered" and the fight goal takes
 * over.
 * TODO(milestone 8): retreat toward home or light, block itself in.
 */
public class FleeActivity extends Activity {
	/** Far enough: the threats are this far away. */
	private static final double SAFE_DISTANCE = 14.0;
	private static final double RUN_DISTANCE = 18.0;
	private static final int MAX_LEGS = 4;
	/** Swarmed: this many melee hostiles this close. */
	private static final int SWARM_COUNT = 3;
	private static final double SWARM_RANGE = 6.0;
	private static final int PILLAR_HEIGHT = 3;

	private int legs;
	private boolean pillared;

	public FleeActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return this.pillared ? "wait on a pillar for the mobs to leave" : "run away";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 90;
	}

	@Override
	protected Status next() {
		Threats.Assessment assessment = Threats.assess(this.drifter);
		if (assessment.decision() == Threats.Decision.NONE || assessment.nearest() > SAFE_DISTANCE || this.legs >= MAX_LEGS) {
			return Status.SUCCESS;
		}

		if (this.pillared) {
			return this.begin(new WaitTask(this.drifter, "the mobs to leave",
				() -> Threats.assess(this.drifter).nearest() > SWARM_RANGE + 2.0, 20 * 20));
		}

		int melee = 0;
		for (LivingEntity hostile : assessment.hostiles()) {
			if (AttackTask.Protocol.of(hostile) == AttackTask.Protocol.MELEE && hostile.distanceTo(this.drifter) <= SWARM_RANGE) {
				melee++;
			}
		}

		if (melee >= SWARM_COUNT && Tools.buildingBlockCount(this.drifter) >= PILLAR_HEIGHT) {
			this.pillared = true;
			this.think("swarmed: pillaring up out of reach");
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.drifter.blockPosition().above(PILLAR_HEIGHT)), 0.6,
				"pillar up out of reach"));
		}

		// Away from where the hostiles are, on average.
		Vec3 here = this.drifter.position();
		Vec3 centre = Vec3.ZERO;
		for (LivingEntity hostile : assessment.hostiles()) {
			centre = centre.add(hostile.position());
		}

		centre = centre.scale(1.0 / assessment.hostiles().size());
		Vec3 away = new Vec3(here.x - centre.x, 0.0, here.z - centre.z);
		if (away.lengthSqr() < 0.01) {
			double angle = this.drifter.getRandom().nextDouble() * Math.PI * 2.0;
			away = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
		}

		Vec3 target = here.add(away.normalize().scale(RUN_DISTANCE));
		this.legs++;
		this.think("running away from the " + (assessment.main() == null ? "mobs"
			: assessment.main().getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT)));
		return this.begin(new NavigateTask(this.drifter, target, 4.0, "run away", NavigateTask.Pace.TRAVEL));
	}
}
