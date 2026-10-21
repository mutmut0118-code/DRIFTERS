package com.dinglebob.drifter.brain.activity;

import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.NavigateTask;

import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Exploring (DESIGN.md section 10.8): looks around first, then heads somewhere, toward a place it remembers when
 * it has one in mind (trees it saw earlier), otherwise toward ground it hasn't explored yet. One leg of 20 to 32
 * blocks; the selector decides whether to keep going.
 * TODO(milestone 8): climb high points, follow rivers and ridgelines, mark the way.
 */
public class ExploreActivity extends Activity {
	private static final int MIN_LEG = 20;
	private static final int MAX_LEG = 32;
	private static final int DIRECTION_TRIES = 8;
	/** Legs for one reason before the search turns into long legs on a heading. */
	private static final int LONG_SEARCH_AFTER = 3;
	private static final int LEGS_PER_HEADING = 6;
	private static final double LONG_LEG = 96.0;

	private final String reason;
	private boolean wentUp;
	private boolean dugUp;
	@Nullable
	private final Vec3 toward;
	@Nullable
	private final Predicate<DrifterEntity> found;
	/** 0: look around, 1: walk a leg, 2: look around again, 3: done. */
	private int phase;

	public ExploreActivity(DrifterEntity drifter, String reason, @Nullable Vec3 toward, @Nullable Predicate<DrifterEntity> found) {
		super(drifter);
		this.reason = reason;
		this.toward = toward;
		this.found = found;
	}

	@Override
	protected void onStop() {
		super.onStop();
		this.drifter.getDrifterBrain().noteExplored(this.reason);
	}

	@Override
	protected boolean finishedEarly() {
		// Checked once a second: stops as soon as it has seen what it was looking for.
		if (this.found != null && this.ticksRunning() % 20 == 10 && this.found.test(this.drifter)) {
			this.think("found what it was looking for (" + this.reason + ")");
			return true;
		}

		return false;
	}

	@Override
	public String describe() {
		return "explore (" + this.reason + ")";
	}

	@Override
	protected int timeoutTicks() {
		// (A staircase up from deep down takes minutes.)
		return this.dugUp ? 20 * 60 * 9 : 20 * 90;
	}

	@Override
	protected Status next() {
		// A failed walk still got it somewhere new: it just carries on with the next phase.
		// Underground (the end of a mining trip): up to the surface first, by way of home; looking for animals or trees in
		// the caves gets it lost and near lava (found in testing: it walked into lava "looking for animals" at Y 28).
		// (Not in the Nether: rock overhead everywhere there, and no surface to go up to; found in testing: it walked to its
		// overworld home's coordinates and staircased up to the bedrock roof.)
		boolean roofed = this.drifter.level().dimensionType().hasCeiling();
		if (this.phase == 0 && !this.wentUp && !roofed && !this.reason.contains("cave") && !this.reason.contains("lava")
			&& com.dinglebob.drifter.brain.mining.Caver.underground(this.drifter, this.drifter.blockPosition())) {
			this.wentUp = true;
			com.dinglebob.drifter.brain.Home home = this.drifter.getDrifterBrain().home();
			if (home != null && home.center.getY() > this.drifter.getBlockY() + 4) {
				return this.begin(new com.dinglebob.drifter.executor.task.NavigateTask(this.drifter, net.minecraft.world.phys.Vec3.atBottomCenterOf(home.center),
					3.0, "head up to the surface"));
			}
		}

		// Still deep underground after that (no way home it could find): a staircase up, never exploring the deep caves
		// for things that live on the surface (found by the user: "looking for sugar cane" at Y -54, into lava).
		if (this.phase == 0 && this.wentUp && !this.dugUp && !roofed && !this.reason.contains("cave") && !this.reason.contains("lava")
			&& com.dinglebob.drifter.brain.mining.Caver.underground(this.drifter, this.drifter.blockPosition())
			&& this.drifter.getBlockY() < this.drifter.level().getSeaLevel() - 8) {
			this.dugUp = true;
			return this.begin(new SurfaceActivity(this.drifter));
		}

		return switch (this.phase++) {
			case 0 -> this.begin(new LookAroundTask(this.drifter));
			case 1 -> this.begin(new NavigateTask(this.drifter, aboveLavaSea(this.drifter, this.toward != null ? this.toward : this.pickDirection()),
				this.toward != null ? 4.0 : 6.0, "explore " + this.reason));
			case 2 -> this.begin(new LookAroundTask(this.drifter));
			default -> Status.SUCCESS;
		};
	}

	/** Blocks above the Nether's lava sea it keeps its exploring (down there every way runs along lava). */
	private static final int ABOVE_LAVA_SEA = 8;

	/**
	 * In a dimension with a lava sea (the Nether), a leg's end no lower than a little above it (found in testing: wandering
	 * at the lava sea's level it got burned and died there).
	 */
	static Vec3 aboveLavaSea(DrifterEntity drifter, Vec3 point) {
		var level = drifter.level();
		if (!level.dimensionType().ultraWarm()) {
			return point;
		}

		return new Vec3(point.x, Math.max(point.y, level.getSeaLevel() + ABOVE_LAVA_SEA), point.z);
	}

	/** A point 20 to 32 blocks away, preferring a direction whose ground it hasn't explored; within where it keeps to. */
	private Vec3 pickDirection() {
		Vec3 point = this.pickDirectionAnywhere();
		var center = this.drifter.getDrifterBrain().roamCenter();
		int radius = this.drifter.getDrifterBrain().roamRadius();
		if (center == null || Math.hypot(point.x - center.getX() - 0.5, point.z - center.getZ() - 0.5) <= radius) {
			return point;
		}

		// Past the edge of where it keeps to (an island's shore): back in, a leg toward the middle and a little aside.
		Vec3 here = this.drifter.position();
		Vec3 in = new Vec3(center.getX() + 0.5 - here.x, 0.0, center.getZ() + 0.5 - here.z);
		double length = in.length();
		if (length < 1.0) {
			return here;
		}

		double turn = (this.drifter.getRandom().nextDouble() - 0.5) * Math.PI / 2.0;
		Vec3 heading = in.scale(1.0 / length).yRot((float)turn);
		double leg = Math.min(length + radius * 0.5, Mth.nextInt(this.drifter.getRandom(), MIN_LEG, MAX_LEG));
		return here.add(heading.scale(leg));
	}

	private Vec3 pickDirectionAnywhere() {
		Vec3 here = this.drifter.position();
		// A long search (no trees in a desert): longer legs on one heading, as a player sets off in a straight line, turning
		// a quarter every few legs in case that way is sea or mountains (found in testing: short random legs in badlands
		// kept it near spawn for days without wood).
		int legs = this.drifter.getDrifterBrain().timesExplored(this.reason);
		if (legs >= LONG_SEARCH_AFTER) {
			double heading = this.drifter.getDrifterBrain().exploreHeading.computeIfAbsent(this.reason,
				reason -> this.drifter.getRandom().nextDouble() * Math.PI * 2.0);
			heading += Math.PI / 2.0 * ((legs - LONG_SEARCH_AFTER) / LEGS_PER_HEADING);
			heading += (this.drifter.getRandom().nextDouble() - 0.5) * 0.4;
			double distance = Math.min(LONG_LEG, MAX_LEG + 12.0 * (legs - LONG_SEARCH_AFTER + 1));
			return here.add(Math.cos(heading) * distance, 0.0, Math.sin(heading) * distance);
		}

		Vec3 fallback = null;
		for (int i = 0; i < DIRECTION_TRIES; i++) {
			double angle = this.drifter.getRandom().nextDouble() * Math.PI * 2.0;
			double distance = Mth.nextInt(this.drifter.getRandom(), MIN_LEG, MAX_LEG);
			Vec3 point = here.add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
			if (fallback == null) {
				fallback = point;
			}

			if (this.drifter.getMemory().map().get(new ChunkPos(Mth.floor(point.x) >> 4, Mth.floor(point.z) >> 4)) == null) {
				return point;
			}
		}

		return fallback;
	}
}
