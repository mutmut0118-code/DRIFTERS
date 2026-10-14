package com.dinglebob.drifter.brain.activity;

import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.Threats;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.RunAwayTask;
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
 * In the evening it runs home if home is away from the mobs, and otherwise gives up on home for the night.
 * TODO(milestone 8): retreat toward light, block itself in.
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

	/** Home counts as "the other way" from the mobs unless it's this close to straight toward them (a cosine). */
	private static final double HOME_BEHIND_MOBS = -0.3;

	private int legs;
	private boolean ranHome;
	private boolean pillared;
	private double pillarStartY;

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
	protected void stepDone(com.dinglebob.drifter.executor.Task step, Status status) {
		// It only waits up there if it really got up (it can be knocked off the column).
		if (this.pillarStartY != 0.0 && !this.pillared && step instanceof NavigateTask) {
			this.pillared = status == Status.SUCCESS && this.drifter.getY() >= this.pillarStartY + 2.0;
		}
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

		if (melee >= SWARM_COUNT && Tools.buildingBlockCount(this.drifter) >= PILLAR_HEIGHT && this.pillarStartY == 0.0) {
			this.pillarStartY = this.drifter.getY();
			this.think("swarmed: pillaring up out of reach");
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.drifter.blockPosition().above(PILLAR_HEIGHT)), 0.6,
				"pillar up out of reach"));
		}

		// In the evening with home close: run home if home is away from the mobs (running away and heading home are then
		// the same thing); if they're between it and home, home is cut off for tonight and it shelters wherever it gets
		// clear (found in testing: it ran off, walked back toward home past the mobs, ran off again, until it died).
		com.dinglebob.drifter.brain.Home home = this.drifter.getDrifterBrain().home();
		long time = Schedule.believedTime(this.drifter);
		boolean evening = time >= Schedule.shelterThinking(this.drifter) && time < Schedule.NIGHT_END;
		if (evening && home != null && home.near(this.drifter, Schedule.GO_HOME_RANGE) && !this.drifter.getDrifterBrain().homeCutOff()) {
			Vec3 here = this.drifter.position();
			Vec3 toHome = new Vec3(home.center.getX() + 0.5 - here.x, 0.0, home.center.getZ() + 0.5 - here.z);
			Vec3 centre = Vec3.ZERO;
			for (LivingEntity hostile : assessment.hostiles()) {
				centre = centre.add(hostile.position());
			}

			centre = assessment.hostiles().isEmpty() ? here : centre.scale(1.0 / assessment.hostiles().size());
			Vec3 away = new Vec3(here.x - centre.x, 0.0, here.z - centre.z);
			boolean homeClear = toHome.lengthSqr() < 1.0E-4 || away.lengthSqr() < 1.0E-4
				|| toHome.normalize().dot(away.normalize()) > HOME_BEHIND_MOBS;
			if (homeClear && !this.ranHome) {
				this.ranHome = true;
				this.legs++;
				this.think("running home");
				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(home.center), 1.0, "run home", NavigateTask.Pace.TRAVEL));
			}

			if (!homeClear) {
				this.drifter.getDrifterBrain().cutOffFromHome();
				this.think("the mobs are between it and home: sheltering where it gets clear");
			}
		}

		// Turn and run, now: no stopping to plan a route (user's note from testing).
		this.legs++;
		this.think("running away from the " + (assessment.main() == null ? "mobs"
			: assessment.main().getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT)));
		return this.begin(new RunAwayTask(this.drifter, () -> Threats.assess(this.drifter).hostiles(), SAFE_DISTANCE, 20 * 8));
	}
}
