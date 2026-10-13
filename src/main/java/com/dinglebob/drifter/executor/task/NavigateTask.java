package com.dinglebob.drifter.executor.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.hands.Tools;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;
import com.dinglebob.drifter.nav.MoveType;
import com.dinglebob.drifter.nav.NavView;
import com.dinglebob.drifter.nav.PathBudget;
import com.dinglebob.drifter.nav.PathSearch;
import com.dinglebob.drifter.nav.PathStep;
import com.dinglebob.drifter.perception.BlockNotes;
import com.dinglebob.drifter.world.PlayerPlacedBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * MoveTo (DESIGN.md sections 8 and 12): plans a path with {@link PathSearch} and follows it like a player, with the
 * virtual keys and the camera model: looking ahead along the path, strafing diagonally when the path turns, sprinting
 * on long straight stretches, sneaking on bridges, jumping up steps. Digs, bridges, pillars and opens doors where the
 * path says so, using the same hand tasks as everything else.
 *
 * <p>It replans when reality disagrees with what it assumed about unseen terrain, when it gets stuck, and every few
 * seconds while the path still crosses unknown ground (rolling replanning). Stuck handling follows section 12:
 * after 3 s it jumps and strafes, after 8 s it replans, and it gives up after too many replans.
 */
public class NavigateTask extends Task {
	/**
	 * How it moves on this trip (section 10.9): TRAVEL sprints (and usually sprint-jumps) whenever it can; STROLL walks,
	 * for when there's no hurry (lounging, a village); SNEAK sneaks the whole way (creeping up on something).
	 * Until the brain exists, commands choose; TRAVEL is the default.
	 */
	public enum Pace {
		TRAVEL, STROLL, SNEAK;

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	/** Horizontal distance to a step's target at which the step counts as done. */
	private static final double ARRIVE = 0.35;
	/** Looser on straight runs, so it doesn't brake at every block (imperfect corner cutting, section 12). */
	private static final double ARRIVE_STRAIGHT = 0.6;
	private static final int LOOK_AHEAD_STEPS = 3;
	/**
	 * How players travel (user's rule, DESIGN.md section 10.9): most trips are sprint-jumped the whole way when
	 * possible. Rolled once per trip.
	 */
	public static final float SPRINT_JUMP_CHANCE = 0.8F;
	/** Stop sprinting this close to the goal, and stop sprint-jumping a bit earlier. */
	private static final double SPRINT_MIN_REMAINING = 3.0;
	private static final double SPRINT_JUMP_MIN_REMAINING = 5.0;
	/**
	 * Sprint-jump only while the next this-many steps are clear for it: no sharp turn, no door or 2-block doorway, no low
	 * ceiling, no edge. One sprint-jump carries it about 4 blocks, so it stops hopping in time.
	 */
	private static final int HOP_LOOKAHEAD = 5;
	/** Cosine of the sharpest turn it keeps hopping through (45 degrees: a diagonal bend, not a corner). */
	private static final double HOP_MAX_TURN_COS = 0.7;
	/** Sculk sensors or shriekers remembered this close: sneak, never sprint (ancient cities, section 15). */
	private static final double SCULK_CAUTION_RANGE = 12.0;
	private static final int UNSTICK_AFTER = 60;
	private static final int UNSTICK_FOR = 10;
	private static final int REPLAN_AFTER = 160;
	private static final int MAX_REPLANS = 15;
	private static final int ROLLING_REPLAN_TICKS = 100;
	private static final int CHASE_REPATH_TICKS = 10;
	private static final double CHASE_REPATH_DISTANCE = 2.0;
	/** Chasing aims where the target will be in about half a second (section 12). */
	private static final int CHASE_PREDICT_TICKS = 10;
	private static final int WAIT_TO_SEE_TICKS = 15;
	private static final int BRIDGE_CREEP_TICKS = 40;
	/** Further than this to the side of a jump-up step's line, it lines up before jumping. */
	private static final double JUMP_LINE_UP = 0.3;
	/**
	 * Back-bridging pitch: looking this steeply down while facing away from the gap, the side of the block it stands on
	 * comes into view right below its eyes as it leans out backward.
	 */
	private static final float BRIDGE_PITCH = 82.0F;
	/** It keeps moving while it plans for at most this long, then stops to think. */
	private static final int MAX_PLAN_ON_THE_MOVE_TICKS = 10;
	/** After planning on the move, the new path is picked up at the nearest of its first this-many steps. */
	private static final int PICK_UP_STEPS = 8;
	/** Horizontal speed (squared, blocks per tick) below which it counts as standing still. */
	private static final double STOPPED_SPEED_SQR = 1.0E-4;
	/** A drop this deep or deeper next to it makes it sneak (falls of 4+ blocks hurt). */
	private static final int DEEP_DROP = 4;

	private final Supplier<Vec3> target;
	private final double radius;
	private final String label;
	private final Pace pace;
	/** Rolled once per trip: whether this is one of the (most) trips it sprint-jumps. */
	private boolean sprintJumpTrip;
	/**
	 * Sprint-jumping right now. Decided each time it's on the ground; while it is, the jump key stays held down, so it
	 * jumps again the moment it lands, like a player holding space (found in testing: it used to tap jump now and then).
	 */
	private boolean hopping;
	/** Following a moving target: re-path as it moves, and don't finish on arrival (follow mode). */
	private final boolean follow;
	@Nullable
	private final Entity chased;

	@Nullable
	private PathSearch search;
	private List<PathStep> path = List.of();
	private boolean partial;
	private int index;
	private Vec3 plannedTarget = Vec3.ZERO;
	@Nullable
	private Task sub;
	private int sinceProgress;
	private int sinceReplan;
	private int replans;
	private int unstick;
	private int waitToSee;
	private int planningTicks;
	/** The back-bridging head pose was set for this step (set again after each placement). */
	private boolean bridgePoseSet;
	private String lastReplanReason = "";

	// Bridge and pillar sub-steps for the current step.
	private int creep;
	private int pillarStage;
	private int pillarTries;
	private int pillarTicks;

	public NavigateTask(DrifterEntity drifter, Vec3 target, double radius, String label) {
		this(drifter, target, radius, label, Pace.TRAVEL);
	}

	public NavigateTask(DrifterEntity drifter, Vec3 target, double radius, String label, Pace pace) {
		this(drifter, () -> target, radius, label, false, null, pace);
	}

	private NavigateTask(DrifterEntity drifter, Supplier<Vec3> target, double radius, String label, boolean follow, @Nullable Entity chased,
		Pace pace) {
		super(drifter);
		this.target = target;
		this.radius = radius;
		this.label = label;
		this.follow = follow;
		this.chased = chased;
		this.pace = pace;
	}

	/** Follows an entity, keeping within {@code radius}, until cancelled. */
	public static NavigateTask follow(DrifterEntity drifter, Entity entity, double radius) {
		return new NavigateTask(drifter, () -> entity.position().add(entity.getDeltaMovement().scale(CHASE_PREDICT_TICKS)), radius,
			"follow " + entity.getName().getString(), true, entity, Pace.TRAVEL);
	}

	@Override
	public String describe() {
		return this.pace == Pace.TRAVEL ? this.label : this.label + " (" + this.pace.id() + ")";
	}

	@Override
	protected int timeoutTicks() {
		return this.follow ? Integer.MAX_VALUE : 20 * 60 * 5;
	}

	@Override
	protected void onStart() {
		this.sprintJumpTrip = this.drifter.getRandom().nextFloat() < SPRINT_JUMP_CHANCE;
		// Planning on the move from the start too: whatever it was doing, it doesn't freeze while it thinks.
		this.plan("start", true);
	}

	private VirtualKeys keys() {
		return this.drifter.getKeys();
	}

	private Level level() {
		return this.drifter.level();
	}

	private BlockPos feet() {
		BlockPos feet = this.drifter.blockPosition();
		// Standing on a slab or a dirt path puts the feet inside that block's space; the path node is the block above.
		// Carpet and thin snow are walked "in" (see NavView.LOW_BLOCK_HEIGHT).
		BlockState state = this.level().getBlockState(feet);
		var shape = state.getCollisionShape(this.level(), feet);
		if (!shape.isEmpty() && shape.max(Direction.Axis.Y) > NavView.LOW_BLOCK_HEIGHT) {
			return feet.above();
		}

		// Leaning out over an edge (sneaking lets it hang 0.3 past it), its middle is over the gap, but it stands on the
		// block beside. Plans must start from there (found in testing: replanning while leaning out to bridge planned
		// from the air over the gap, and every plan after that was wrong).
		if (this.drifter.onGround() && !this.hasFloor(feet)) {
			AABB box = this.drifter.getBoundingBox();
			BlockPos best = null;
			double bestArea = 0.0;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos cell = feet.offset(dx, 0, dz);
					if ((dx != 0 || dz != 0) && this.hasFloor(cell)) {
						double area = overlap(box.minX, box.maxX, cell.getX()) * overlap(box.minZ, box.maxZ, cell.getZ());
						if (area > bestArea) {
							bestArea = area;
							best = cell;
						}
					}
				}
			}

			if (best != null) {
				return best;
			}
		}

		return feet;
	}

	/** How much of [min, max] lies in the block column starting at {@code start}. */
	private static double overlap(double min, double max, int start) {
		return Math.max(0.0, Math.min(max, start + 1.0) - Math.max(min, start));
	}

	private boolean atGoal() {
		return this.drifter.position().distanceTo(this.target.get()) <= this.radius
			&& (this.drifter.onGround() || this.drifter.isInWater() || this.drifter.onClimbable());
	}

	/** Plans again, stopping first (something is in the way, or it's stuck). */
	private void plan(String reason) {
		this.plan(reason, false);
	}

	/**
	 * Plans again. With {@code onTheMove}, it keeps moving the way it was going while it thinks, like a player (found in
	 * testing: stopping dead at every surprise made trips slow and jerky); the new path then picks up from wherever it
	 * has got to. Either way it keeps sneaking next to a deep drop.
	 */
	private void plan(String reason, boolean onTheMove) {
		this.stopSub();
		if (onTheMove) {
			this.keepSafe();
		} else {
			this.releaseKeys();
		}

		this.planningTicks = 0;
		this.plannedTarget = this.target.get();
		this.search = new PathSearch(this.drifter, this.feet(), this.plannedTarget, Math.max(this.radius, 0.5));
		this.path = List.of();
		this.index = 0;
		this.sinceProgress = 0;
		this.sinceReplan = 0;
		this.lastReplanReason = reason;
		this.hopping = false;
		this.resetStepState();
		this.drifter.setDebugPath(List.of(), 0);
	}

	private void replan(String reason) {
		this.replan(reason, false);
	}

	private void replan(String reason, boolean onTheMove) {
		this.replans++;
		this.plan(reason, onTheMove);
	}

	/**
	 * Lets go of every key, except sneak while a deep drop is right beside or under it: with sneak released, even the
	 * slow creep it had toward the edge carries it over (found in testing: replanning while creeping out to bridge
	 * dropped it into the ravine).
	 */
	private void releaseKeys() {
		this.keys().releaseAll();
		if (this.edgeBeside()) {
			this.keys().set(VirtualKeys.Key.SNEAK, true);
		}
	}

	/** Keeps the keys as they are, but at a deep edge it sneaks and doesn't sprint or jump. */
	private void keepSafe() {
		if (this.edgeBeside()) {
			this.keys().set(VirtualKeys.Key.SNEAK, true);
			this.keys().set(VirtualKeys.Key.SPRINT, false);
			this.keys().set(VirtualKeys.Key.JUMP, false);
		}
	}

	/**
	 * Is now a safe moment to stop walking and do something else (section 7, "Atomic actions finish first")? Not while
	 * digging, bridging or pillaring, and not at a deep edge, where letting go of the keys could drop it.
	 */
	public boolean isSafeToInterrupt() {
		if (this.sub != null || this.edgeBeside()) {
			return false;
		}

		if (this.index < this.path.size()) {
			MoveType type = this.path.get(this.index).type();
			return type != MoveType.BRIDGE && type != MoveType.PILLAR;
		}

		return true;
	}

	/** A deep drop under it (leaning out over an edge) or right beside it, from where it stands. */
	private boolean edgeBeside() {
		BlockPos here = this.feet();
		if (this.isDeepDrop(here)) {
			return true;
		}

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (this.isDeepDrop(here.relative(direction))) {
				return true;
			}
		}

		return false;
	}

	private void resetStepState() {
		this.creep = 0;
		this.pillarStage = 0;
		this.pillarTries = 0;
		this.pillarTicks = 0;
		this.waitToSee = 0;
		this.bridgePoseSet = false;
	}

	@Override
	protected Status onTick() {
		if (this.atGoal()) {
			if (!this.follow) {
				return Status.SUCCESS;
			}

			// Follow mode: wait here until the target moves away again.
			this.stopSub();
			this.releaseKeys();
			if (this.chased != null) {
				this.drifter.getCamera().track(this.chased.getEyePosition(), CameraModel.TurnSpeed.NORMAL);
			}

			this.path = List.of();
			this.search = null;
			return Status.RUNNING;
		}

		if (this.follow && this.chased != null && (this.chased.isRemoved() || !this.chased.isAlive())) {
			return this.fail("lost the one it was following");
		}

		if (this.replans > MAX_REPLANS) {
			return this.fail("stuck: replanned too often (last: " + this.lastReplanReason + ")");
		}

		if (this.search == null && this.path.isEmpty()) {
			this.plan("target moved", true);
		}

		if (this.search != null) {
			return this.tickPlanning();
		}

		this.sinceReplan++;
		if (this.follow && this.sinceReplan % CHASE_REPATH_TICKS == 0 && this.target.get().distanceTo(this.plannedTarget) > CHASE_REPATH_DISTANCE) {
			this.plan("target moved", true);
			return Status.RUNNING;
		}

		if (this.sub != null) {
			return this.tickSub();
		}

		if (this.index >= this.path.size()) {
			this.replan(this.partial ? "reached the end of a partial path" : "path ended short of the goal", true);
			return Status.RUNNING;
		}

		// Not in the middle of a bridge or pillar: those finish first.
		MoveType current = this.path.get(this.index).type();
		if (this.sinceReplan >= ROLLING_REPLAN_TICKS && this.unknownAhead() && current != MoveType.BRIDGE && current != MoveType.PILLAR) {
			this.plan("rolling replan: now it has seen more of the way", true);
			return Status.RUNNING;
		}

		return this.followStep(this.path.get(this.index));
	}

	private Status tickPlanning() {
		PathSearch.Status status = this.search.tick(PathBudget.take(this.level()));
		if (status == PathSearch.Status.RUNNING) {
			// Thinking on the move only for a moment; a long think happens standing still.
			if (++this.planningTicks == MAX_PLAN_ON_THE_MOVE_TICKS) {
				this.releaseKeys();
			} else {
				this.keepSafe();
			}

			return Status.RUNNING;
		}

		if (status == PathSearch.Status.FAILED) {
			this.search = null;
			return this.fail("it knows no way there (" + this.lastReplanReason + ")");
		}

		this.path = new ArrayList<>(this.search.result());
		this.partial = status == PathSearch.Status.PARTIAL;
		this.search = null;
		this.index = this.nearestStep();
		this.drifter.setDebugPath(this.path, 0);
		return Status.RUNNING;
	}

	/** It may have moved on while planning: pick up the path at the step that starts nearest to where it is now. */
	private int nearestStep() {
		int best = 0;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < Math.min(this.path.size(), PICK_UP_STEPS); i++) {
			double distance = this.drifter.position().distanceToSqr(Vec3.atBottomCenterOf(this.path.get(i).from()));
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}

		return best;
	}

	private Status tickSub() {
		Status status = this.sub.tick();
		if (status == Status.RUNNING) {
			return Status.RUNNING;
		}

		Task finished = this.sub;
		this.stopSub();
		this.sinceProgress = 0;
		if (status == Status.FAILURE) {
			this.replan(finished.describe() + " failed: " + finished.failureReason());
		}

		return Status.RUNNING;
	}

	private void startSub(Task task) {
		this.keys().set(VirtualKeys.Key.FORWARD, false);
		this.keys().set(VirtualKeys.Key.BACK, false);
		this.keys().set(VirtualKeys.Key.LEFT, false);
		this.keys().set(VirtualKeys.Key.RIGHT, false);
		this.keys().set(VirtualKeys.Key.SPRINT, false);
		this.keys().set(VirtualKeys.Key.JUMP, false);
		this.sub = task;
	}

	private void stopSub() {
		if (this.sub != null) {
			this.sub.stop();
			this.sub = null;
		}
	}

	private boolean unknownAhead() {
		for (int i = this.index; i < Math.min(this.path.size(), this.index + 12); i++) {
			if (this.path.get(i).unknownCells() > 0) {
				return true;
			}
		}

		return false;
	}

	// ---------------------------------------------------------------------------------------------
	// Following one step
	// ---------------------------------------------------------------------------------------------

	private Status followStep(PathStep step) {
		if (this.arrived(step)) {
			this.drifter.getMemory().walked(step.to());
			this.index++;
			this.sinceProgress = 0;
			this.resetStepState();
			this.drifter.setDebugPath(this.path, this.index);
			// (Sneak is left as it is: the next tick's steering decides. Letting go of it here for a tick, at the end of a
			// bridge, could walk it off.)
			return Status.RUNNING;
		}

		// Reality check: it only digs what the plan chose to dig. Anything else in the way means the plan was based on
		// a guess about unseen ground, so it plans again now that it can see it (section 12; found in testing: it used
		// to dig through whatever it found, including house walls).
		for (BlockPos cell : this.cellsToClear(step)) {
			if (!this.isClear(cell)) {
				if (!step.digs().contains(cell)) {
					this.replan("found " + this.blockName(cell) + " where it expected open space");
					return Status.RUNNING;
				}

				if (!this.canDig(cell)) {
					this.replan("blocked by " + this.blockName(cell) + " it can't dig");
					return Status.RUNNING;
				}

				this.startSub(this.digTask(cell));
				return Status.RUNNING;
			}
		}

		BlockPos door = this.closedDoor(step);
		if (door != null) {
			this.startSub(UseOnBlockTask.useBlock(this.drifter, door, this.faceToward(door), "open the door at " + door.toShortString()));
			return Status.RUNNING;
		}

		// Take out a building block a few steps before a bridge or pillar, on safe ground. Taking one from the inventory
		// opens it, which lets go of every key, sneak included (found in testing: doing it at the edge of a ravine
		// stood it up mid-slide and it fell in).
		if (this.buildStepSoon() && !Tools.BUILDING_BLOCK.test(this.drifter.getMainHandItem()) && !this.nearDeepDrop() && this.drifter.onGround()) {
			this.startSub(new SelectItemTask(this.drifter, Tools.BUILDING_BLOCK, "a building block"));
			return Status.RUNNING;
		}

		if (step.type() == MoveType.PILLAR) {
			return this.pillar(step);
		}

		if (this.needsFloor(step) && !this.hasFloor(step.to())) {
			if (step.type() == MoveType.BRIDGE) {
				// Look down into the gap first, like a player at an edge. If it's only one block deep, step down instead.
				// (Only before the first block: mid-bridge it faces away from the gap and doesn't turn round to look.)
				BlockPos gapFloor = step.to().below(2);
				boolean firstBlock = this.index == 0 || this.path.get(this.index - 1).type() != MoveType.BRIDGE;
				if (firstBlock && !this.drifter.getSenses().terrain().isKnown(gapFloor) && this.waitToSee++ < WAIT_TO_SEE_TICKS) {
					this.drifter.getCamera().track(Vec3.atCenterOf(gapFloor), CameraModel.TurnSpeed.NORMAL);
					this.keys().releaseAll();
					this.keys().set(VirtualKeys.Key.SNEAK, true);
					return Status.RUNNING;
				}

				if (firstBlock && this.drifter.getSenses().terrain().isKnown(gapFloor) && this.hasFloor(step.to().below())) {
					this.replan("the gap at " + step.to().toShortString() + " is only one block deep", true);
					return Status.RUNNING;
				}

				return this.bridge(step);
			}

			// It assumed ground it hadn't seen. It glances down while carrying on, sneaking, so it stops at the edge if the
			// drop turns out deep; then it replans on the move (found in testing: it stopped dead at every step down a
			// hill). A short drop it then just walks off.
			if (!this.drifter.getSenses().terrain().isKnown(step.to().below()) && this.waitToSee++ < WAIT_TO_SEE_TICKS) {
				this.steer(step);
				this.drifter.getCamera().track(Vec3.atCenterOf(step.to().below()), CameraModel.TurnSpeed.NORMAL);
				this.keys().set(VirtualKeys.Key.SNEAK, true);
				this.keys().set(VirtualKeys.Key.SPRINT, false);
				this.keys().set(VirtualKeys.Key.JUMP, false);
				return Status.RUNNING;
			}

			this.replan("no ground where it expected it at " + step.to().below().toShortString(), true);
			return Status.RUNNING;
		}

		if (step.type() == MoveType.BRIDGE) {
			// The block is placed: shuffle backward onto it, still facing away from the gap.
			this.backBridgeMove(step);
			return this.checkStuck();
		}

		this.steer(step);
		return this.checkStuck();
	}

	/** A bridge or pillar step is this one or one of the next two. */
	private boolean buildStepSoon() {
		for (int i = this.index; i < Math.min(this.path.size(), this.index + 3); i++) {
			MoveType type = this.path.get(i).type();
			if (type == MoveType.BRIDGE || type == MoveType.PILLAR) {
				return true;
			}
		}

		return false;
	}

	private boolean arrived(PathStep step) {
		Vec3 center = Vec3.atBottomCenterOf(step.to());
		double dx = this.drifter.getX() - center.x;
		double dz = this.drifter.getZ() - center.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		// Walking a straight line to a point further on: an in-between point counts as reached once it's nearer the far
		// point than that point is (it cut past it), or close.
		if (this.smoothTo > this.index && this.smoothTo < this.path.size() && isFlatWalk(step)) {
			Vec3 far = Vec3.atBottomCenterOf(this.path.get(this.smoothTo).to());
			double mine = horizontalDistance(this.drifter.position(), far);
			double its = horizontalDistance(center, far);
			if (mine < its - 0.1 || horizontal < ARRIVE_STRAIGHT) {
				return true;
			}
		}

		boolean straight = this.index + 1 < this.path.size() && this.flowsInto(step, this.path.get(this.index + 1));
		if (straight && this.passedInAir(step)) {
			return true;
		}

		if (horizontal > (straight ? ARRIVE_STRAIGHT : ARRIVE)) {
			return false;
		}

		boolean supported = this.drifter.onGround() || this.drifter.isInWater() || this.drifter.onClimbable();
		return supported && Math.abs(this.drifter.getY() - step.to().getY()) < 0.6;
	}

	/**
	 * Sprint-jumping sails over path points without standing on them. On a straight run (or a gentle bend), a point
	 * counts as reached once it's behind the Drifter (within a little sideways), even in mid-air.
	 */
	private boolean passedInAir(PathStep step) {
		return this.passedHorizontally(step) && Math.abs(this.drifter.getY() - step.to().getY()) < 1.3;
	}

	/** Its position has reached or passed the step's point along the step's direction, within a little sideways. */
	private boolean passedHorizontally(PathStep step) {
		Vec3 center = Vec3.atBottomCenterOf(step.to());
		double dx = this.drifter.getX() - center.x;
		double dz = this.drifter.getZ() - center.z;
		double stepX = step.to().getX() - step.from().getX();
		double stepZ = step.to().getZ() - step.from().getZ();
		double length = Math.sqrt(stepX * stepX + stepZ * stepZ);
		if (length < 0.5) {
			return false;
		}

		double along = (dx * stepX + dz * stepZ) / length;
		double sideways = Math.abs(-dx * stepZ + dz * stepX) / length;
		return along > -0.1 && sideways < ARRIVE_STRAIGHT;
	}

	/** How many steps ahead it looks for a straight line to walk (string-pulling). */
	private static final int SMOOTH_STEPS = 5;
	/** Path index it's currently walking straight toward (when greater than the current index). */
	private int smoothTo = -1;

	private static boolean isFlatWalk(PathStep step) {
		return (step.type() == MoveType.WALK || step.type() == MoveType.DIAGONAL) && step.from().getY() == step.to().getY();
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** The furthest step within reach of a clear straight line over flat walking steps, or the current index. */
	private int smoothTarget(PathStep step) {
		if (!isFlatWalk(step) || !this.drifter.onGround() && !this.hopping) {
			return this.index;
		}

		int last = this.index;
		for (int i = this.index; i < Math.min(this.path.size(), this.index + SMOOTH_STEPS); i++) {
			PathStep candidate = this.path.get(i);
			if (!isFlatWalk(candidate) || candidate.to().getY() != step.to().getY()) {
				break;
			}

			last = i;
		}

		for (int i = last; i > this.index; i--) {
			if (this.straightLineClear(this.drifter.position(), Vec3.atBottomCenterOf(this.path.get(i).to()), step.to().getY())) {
				return i;
			}
		}

		return this.index;
	}

	/**
	 * Is the straight line from here to there walkable for a 0.6-wide body: open feet and head cells, solid floor, no
	 * fluids, at this floor level?
	 */
	private boolean straightLineClear(Vec3 from, Vec3 to, int feetY) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 0.5) {
			return true;
		}

		double ux = dx / length;
		double uz = dz / length;
		for (double t = 0.0; t <= length; t += 0.4) {
			for (double side = -0.3; side <= 0.3; side += 0.3) {
				double x = from.x + ux * t - uz * side;
				double z = from.z + uz * t + ux * side;
				BlockPos cell = BlockPos.containing(x, feetY + 0.1, z);
				if (!this.isClear(cell) || !this.isClear(cell.above()) || this.isClear(cell.below()) || this.isDoorLike(cell)
					|| !this.level().getFluidState(cell).isEmpty() || !this.level().getFluidState(cell.below()).isEmpty()) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Two steps on foot in a row (walking, or a step up or down) that turn by 45 degrees or less: it runs straight on
	 * through, without slowing to stand on the point between them (found in testing: it paused at every step down).
	 */
	private boolean flowsInto(PathStep a, PathStep b) {
		if (!this.onFoot(a) || !this.onFoot(b)) {
			return false;
		}

		// Into a jump up it must be lined up with the step: cutting the corner on the way, it runs into the block beside
		// the step (often two high on a hillside) and jumps against it (found in testing).
		if (b.type() == MoveType.JUMP_UP) {
			return sameDirection(a, b);
		}

		return this.gentleTurn(a, b);
	}

	private static boolean sameDirection(PathStep a, PathStep b) {
		return a.to().getX() - a.from().getX() == b.to().getX() - b.from().getX() && a.to().getZ() - a.from().getZ() == b.to().getZ() - b.from().getZ();
	}

	/** How far it is to the side of the line a step runs along (from its start's middle through its end's middle). */
	private double offLine(PathStep step) {
		double stepX = step.to().getX() - step.from().getX();
		double stepZ = step.to().getZ() - step.from().getZ();
		double length = Math.sqrt(stepX * stepX + stepZ * stepZ);
		if (length < 0.5) {
			return 0.0;
		}

		double dx = this.drifter.getX() - (step.from().getX() + 0.5);
		double dz = this.drifter.getZ() - (step.from().getZ() + 0.5);
		return Math.abs(-dx * stepZ + dz * stepX) / length;
	}

	private boolean onFoot(PathStep step) {
		return switch (step.type()) {
			case WALK, DIAGONAL, JUMP_UP -> true;
			case STEP_DOWN -> !this.level().getFluidState(step.to()).is(FluidTags.WATER);
			default -> false;
		};
	}

	/** The cells that must be open for this step, in the order to dig them (top first). */
	private List<BlockPos> cellsToClear(PathStep step) {
		BlockPos from = step.from();
		BlockPos to = step.to();
		List<BlockPos> cells = new ArrayList<>();
		switch (step.type()) {
			case WALK, BRIDGE, SWIM -> {
				cells.add(to.above());
				cells.add(to);
			}
			case DIAGONAL -> {
				BlockPos sideX = new BlockPos(to.getX(), from.getY(), from.getZ());
				BlockPos sideZ = new BlockPos(from.getX(), from.getY(), to.getZ());
				cells.add(sideX.above());
				cells.add(sideX);
				cells.add(sideZ.above());
				cells.add(sideZ);
				cells.add(to.above());
				cells.add(to);
			}
			case JUMP_UP -> {
				cells.add(from.above(2));
				cells.add(to.above());
				cells.add(to);
			}
			case STEP_DOWN, SWIM_DOWN -> {
				for (int y = from.getY() + 1; y >= to.getY(); y--) {
					cells.add(new BlockPos(to.getX(), y, to.getZ()));
				}
			}
			case PILLAR -> cells.add(from.above(2));
			case CLIMB_UP, SWIM_UP -> {
				cells.add(to.above());
				cells.add(to);
			}
			case CLIMB_DOWN -> cells.add(to);
			default -> {
			}
		}

		return cells;
	}

	/** Can it walk through this block right now? (Carpet and thin snow count: it walks on them.) */
	private boolean isClear(BlockPos pos) {
		BlockState state = this.level().getBlockState(pos);
		var shape = state.getCollisionShape(this.level(), pos);
		return shape.isEmpty() || shape.max(Direction.Axis.Y) <= NavView.LOW_BLOCK_HEIGHT || state.is(BlockTags.CLIMBABLE)
			|| state.is(BlockTags.WOODEN_DOORS) || state.getBlock() instanceof FenceGateBlock;
	}

	/**
	 * A spot with nothing to land on for 4+ blocks (a cliff edge, a ravine). It only checks the blocks right beside
	 * it, which it always knows (it can see the edge it's standing at).
	 */
	private boolean isDeepDrop(BlockPos pos) {
		if (!this.isClear(pos) || this.level().getFluidState(pos).is(FluidTags.WATER)) {
			return false;
		}

		for (int i = 1; i <= DEEP_DROP; i++) {
			BlockPos below = pos.below(i);
			if (!this.isClear(below) || this.level().getFluidState(below).is(FluidTags.WATER)) {
				return false;
			}
		}

		return true;
	}

	/** Is there a deep drop right beside where it stands or where it's heading? Then it sneaks, like a player. */
	private boolean nearDeepDrop() {
		BlockPos here = this.feet();
		if (this.isDeepDrop(here)) {
			return true;
		}

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (this.isDeepDrop(here.relative(direction))) {
				return true;
			}
		}

		if (this.index < this.path.size()) {
			BlockPos next = this.path.get(this.index).to();
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				if (this.isDeepDrop(next.relative(direction))) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean canDig(BlockPos pos) {
		BlockState state = this.level().getBlockState(pos);
		if (state.getDestroySpeed(this.level(), pos) < 0.0F || this.level().getBlockEntity(pos) != null
			|| PlayerPlacedBlocks.isPlayerPlaced(this.level(), pos) || state.getFluidState().is(FluidTags.LAVA)) {
			return false;
		}

		// Never dig into lava it has seen (section 12 hard ban). Unseen lava is the reflexes' job (milestone 6).
		for (Direction direction : Direction.values()) {
			BlockPos neighbor = pos.relative(direction);
			if (direction != Direction.DOWN && this.level().getFluidState(neighbor).is(FluidTags.LAVA)
				&& this.drifter.getSenses().terrain().isKnown(neighbor)) {
				return false;
			}
		}

		return true;
	}

	private String blockName(BlockPos pos) {
		return this.level().getBlockState(pos).getBlock().getName().getString().toLowerCase(Locale.ROOT);
	}

	/** Takes out the best tool it has for the block, then mines it. */
	private Task digTask(BlockPos cell) {
		BlockState state = this.level().getBlockState(cell);
		int slot = Tools.bestToolSlot(this.drifter, state, this.level(), cell);
		if (slot < 0) {
			return new MineTask(this.drifter, cell);
		}

		ItemStack tool = this.drifter.getInventory().getItem(slot);
		return new SequenceTask(this.drifter, "dig " + this.blockName(cell), List.of(
			new SelectItemTask(this.drifter, stack -> stack == tool, tool.getHoverName().getString()),
			new MineTask(this.drifter, cell)));
	}

	@Nullable
	private BlockPos closedDoor(PathStep step) {
		for (BlockPos cell : this.cellsToClear(step)) {
			BlockState state = this.level().getBlockState(cell);
			if ((state.is(BlockTags.WOODEN_DOORS) || state.getBlock() instanceof FenceGateBlock)
				&& state.hasProperty(BlockStateProperties.OPEN) && !state.getValue(BlockStateProperties.OPEN)) {
				return cell;
			}
		}

		return null;
	}

	private Direction faceToward(BlockPos block) {
		Vec3 offset = this.drifter.getEyePosition().subtract(Vec3.atCenterOf(block));
		return Direction.getNearest(offset.x, 0.0, offset.z);
	}

	private boolean needsFloor(PathStep step) {
		return switch (step.type()) {
			case WALK, DIAGONAL, JUMP_UP, STEP_DOWN, BRIDGE -> !this.level().getFluidState(step.to()).is(FluidTags.WATER);
			default -> false;
		};
	}

	private boolean hasFloor(BlockPos feet) {
		BlockPos floor = feet.below();
		BlockState state = this.level().getBlockState(floor);
		return !state.getCollisionShape(this.level(), floor).isEmpty() || state.is(BlockTags.CLIMBABLE)
			|| this.level().getFluidState(floor).is(FluidTags.WATER);
	}

	// ---------------------------------------------------------------------------------------------
	// Steering with the virtual keys
	// ---------------------------------------------------------------------------------------------

	private void steer(PathStep step) {
		Vec3 here = this.drifter.position();
		Vec3 goal = Vec3.atBottomCenterOf(step.to());
		// Already past this point (still dropping down to it, or in mid-jump): carry on toward the next one instead of
		// turning back to stand on it (found in testing: it fell straight down from every drop).
		if (this.index + 1 < this.path.size() && this.flowsInto(step, this.path.get(this.index + 1)) && this.passedHorizontally(step)) {
			goal = Vec3.atBottomCenterOf(this.path.get(this.index + 1).to());
		}

		// String-pulling (user's note from testing: "too zig-zaggy, it can walk two quarters of a diagonal"): when the
		// straight line to a point a few steps on is clear flat ground, it walks that line at any angle instead of
		// visiting every block centre.
		this.smoothTo = this.smoothTarget(step);
		if (this.smoothTo > this.index) {
			goal = Vec3.atBottomCenterOf(this.path.get(this.smoothTo).to());
		}

		// A jump up from off to the side: line up in front of the step first (its start's middle), then jump.
		boolean liningUp = step.type() == MoveType.JUMP_UP && this.drifter.onGround() && this.offLine(step) > JUMP_LINE_UP;
		if (liningUp) {
			goal = Vec3.atBottomCenterOf(step.from());
		}

		double dx = goal.x - here.x;
		double dz = goal.z - here.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		// Look ahead along the path, not at its feet (section 12).
		PathStep ahead = this.path.get(Math.min(this.path.size() - 1, this.index + LOOK_AHEAD_STEPS));
		Vec3 lookPoint = Vec3.atBottomCenterOf(ahead.to()).add(0.0, 1.2, 0.0);
		if (lookPoint.distanceTo(this.drifter.getEyePosition()) < 1.5) {
			lookPoint = goal.add(0.0, 1.2, 0.0);
		}

		this.drifter.getCamera().track(lookPoint, CameraModel.TurnSpeed.NORMAL);

		VirtualKeys keys = this.keys();
		if (this.unstick > 0) {
			this.unstick--;
			return;
		}

		this.pressToward(goal);

		// Sneak on bridges and whenever a deep drop is right beside it (section 12: "sneaks near edges"), so momentum can
		// never carry it over (found in testing: it ran off a ravine edge). Not on a deliberate step down: sneaking
		// would stop it at the step.
		boolean intentionalDrop = step.type() == MoveType.STEP_DOWN || step.type() == MoveType.SWIM_DOWN;
		boolean nearEdge = !intentionalDrop && this.nearDeepDrop();
		// Magma blocks only burn you when you don't sneak (section 15).
		BlockPos under = this.drifter.getBlockPosBelowThatAffectsMyMovement();
		boolean onMagma = !intentionalDrop && this.level().getBlockState(under).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK);
		boolean sneaking = this.pace == Pace.SNEAK || step.type() == MoveType.BRIDGE || nearEdge || this.nearSculk() || onMagma;
		keys.set(VirtualKeys.Key.SNEAK, sneaking);
		if (sneaking && this.drifter.isSprinting()) {
			// Sprinting carries on while sneaking (as for a player) until forward input stops: let go of the movement keys
			// for a moment, as a player does before creeping to an edge (found in testing: it sprint-sneaked to bridge).
			keys.set(VirtualKeys.Key.FORWARD, false);
			keys.set(VirtualKeys.Key.BACK, false);
			keys.set(VirtualKeys.Key.LEFT, false);
			keys.set(VirtualKeys.Key.RIGHT, false);
		}
		// Section 10.9 pace: on a TRAVEL trip it sprints the whole way when it can. It can't when hungry (food 6 or
		// less) and shouldn't when sneaking, bridging, near an edge or almost there.
		boolean sprint = this.pace == Pace.TRAVEL && !sneaking && keys.isDown(VirtualKeys.Key.FORWARD) && !this.drifter.isInWater()
			&& this.remainingDistance() > SPRINT_MIN_REMAINING && this.drifter.getFoodData().getFoodLevel() > FoodConstants.SPRINT_LEVEL;
		keys.set(VirtualKeys.Key.SPRINT, sprint);

		boolean jump = switch (step.type()) {
			case JUMP_UP -> !liningUp && (horizontal < 1.3 || this.drifter.horizontalCollision);
			case SWIM_UP, CLIMB_UP -> true;
			case SWIM, WALK -> this.drifter.isInWater() && step.to().getY() >= this.feet().getY();
			default -> false;
		};
		// Sprint-jump on most trips, holding the jump key down the whole stretch. Whether to keep going is decided on
		// the ground (in mid-air the key just stays down); it stops before turns, doors, doorways, low ceilings and edges,
		// and near the end of the trip.
		if (!sprint || !this.sprintJumpTrip) {
			this.hopping = false;
		} else if (this.drifter.onGround()) {
			this.hopping = this.remainingDistance() > SPRINT_JUMP_MIN_REMAINING && this.clearToHop();
		}

		if (this.hopping) {
			jump = true;
		}

		// Auto-jump over an unexpected 1-block bump (players jump without thinking). Only a real bump with room to jump:
		// jumping into a door frame or under a low ceiling just bonks its head, again and again (found in testing).
		// Never near an edge or on a bridge: a jump there lifts it off the ground, and sneaking only holds it on the ground.
		if (this.drifter.horizontalCollision && this.drifter.onGround() && step.type() != MoveType.BRIDGE && !nearEdge
			&& this.bumpAhead(dx, dz, horizontal)) {
			jump = true;
		}

		keys.set(VirtualKeys.Key.JUMP, jump);
	}

	/**
	 * Is the next stretch fit for sprint-jumping? Flat walking steps (or single steps up and down) with no sharp turn,
	 * no door, gate or trapdoor, room above the head for the jump (a 2-block doorway or tunnel has none), and no deep
	 * drop beside the way.
	 */
	private boolean clearToHop() {
		if (this.index + HOP_LOOKAHEAD > this.path.size() || this.cramped(this.feet())) {
			return false;
		}

		PathStep first = this.path.get(this.index);
		PathStep last = this.path.get(this.index + HOP_LOOKAHEAD - 1);
		PathStep previous = null;
		for (int i = this.index; i < this.index + HOP_LOOKAHEAD; i++) {
			PathStep step = this.path.get(i);
			boolean stepOk = switch (step.type()) {
				case WALK, DIAGONAL, JUMP_UP -> true;
				case STEP_DOWN -> step.from().getY() - step.to().getY() == 1;
				default -> false;
			};
			if (!stepOk || previous != null && !this.gentleTurn(previous, step)) {
				return false;
			}

			for (BlockPos cell : this.cellsToClear(step)) {
				if (this.isDoorLike(cell)) {
					return false;
				}
			}

			// Room for the jump: the block two above the feet, all along the way (a diagonal passes two corners too).
			BlockPos from = step.from();
			BlockPos to = step.to();
			if (!this.isClear(from.above(2)) || !this.isClear(to.above(2)) || !this.isClear(new BlockPos(to.getX(), from.getY(), from.getZ()).above(2))
				|| !this.isClear(new BlockPos(from.getX(), from.getY(), to.getZ()).above(2))) {
				return false;
			}

			for (Direction direction : Direction.Plane.HORIZONTAL) {
				if (this.isDeepDrop(step.to().relative(direction))) {
					return false;
				}
			}

			if (this.cramped(to)) {
				return false;
			}

			previous = step;
		}

		return this.gentleTurn(first, last);
	}

	/**
	 * Indoors or in a tight cave: a ceiling within 4 blocks of the floor, or walls close on two or more sides. Players
	 * don't sprint-jump there (user's rule).
	 */
	private boolean cramped(BlockPos feet) {
		for (int up = 3; up <= 4; up++) {
			if (!this.isClear(feet.above(up))) {
				return true;
			}
		}

		int walls = 0;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			// A wall at head height (a fence or a bush at foot level doesn't make a corridor).
			if (!this.isClear(feet.relative(direction).above())) {
				walls++;
			}
		}

		return walls >= 2;
	}

	/** The horizontal direction changes by 45 degrees or less from one step to the other. */
	private boolean gentleTurn(PathStep a, PathStep b) {
		double ax = a.to().getX() - a.from().getX();
		double az = a.to().getZ() - a.from().getZ();
		double bx = b.to().getX() - b.from().getX();
		double bz = b.to().getZ() - b.from().getZ();
		double lengths = Math.sqrt(ax * ax + az * az) * Math.sqrt(bx * bx + bz * bz);
		return lengths > 0.0 && (ax * bx + az * bz) / lengths >= HOP_MAX_TURN_COS;
	}

	private boolean isDoorLike(BlockPos pos) {
		BlockState state = this.level().getBlockState(pos);
		return state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES);
	}

	/** A block at foot level right in front, with room above it and above its own head to jump up onto it. */
	private boolean bumpAhead(double dx, double dz, double horizontal) {
		if (horizontal < 0.05) {
			return false;
		}

		BlockPos feet = this.feet();
		if (!this.isClear(feet.above(2))) {
			return false;
		}

		BlockPos front = BlockPos.containing(this.drifter.getX() + dx / horizontal * 0.8, feet.getY() + 0.5, this.drifter.getZ() + dz / horizontal * 0.8);
		return !this.isClear(front) && this.isClear(front.above()) && this.isClear(front.above(2));
	}

	/** Remembered sculk sensors or shriekers nearby (it has seen them): an ancient city or deep dark, so it creeps. */
	private boolean nearSculk() {
		return this.drifter.getMemory().nearest(MemoryKind.DANGER, this.level().dimension().location(), this.drifter.position(),
			SCULK_CAUTION_RANGE, entry -> entry.label.equals(BlockNotes.SCULK)) != null;
	}

	private double remainingDistance() {
		return this.drifter.position().distanceTo(this.target.get());
	}

	private Status checkStuck() {
		if (++this.sinceProgress == UNSTICK_AFTER && this.nearDeepDrop()) {
			// Never jump and strafe blindly next to a drop: plan again instead.
			this.replan("stuck next to a drop");
		} else if (this.sinceProgress == UNSTICK_AFTER) {
			// Section 12: no progress for 3 s, jump and strafe sideways, then try again.
			this.unstick = UNSTICK_FOR;
			this.keys().releaseAll();
			this.keys().set(this.drifter.getRandom().nextBoolean() ? VirtualKeys.Key.LEFT : VirtualKeys.Key.RIGHT, true);
			this.keys().set(VirtualKeys.Key.JUMP, true);
		} else if (this.sinceProgress >= REPLAN_AFTER) {
			this.replan("no progress for " + REPLAN_AFTER / 20 + " s");
		}

		return Status.RUNNING;
	}

	// ---------------------------------------------------------------------------------------------
	// Bridging and pillaring
	// ---------------------------------------------------------------------------------------------

	/**
	 * Bridge: take out a building block, sneak forward to the edge (sneaking never falls off), then place a block
	 * against the side of the block it stands on, under the next step (section 10.9: "bridges with blocks while
	 * sneaking").
	 */
	private Status bridge(PathStep step) {
		// It must be standing on the block it bridges from (it could have slid or been pushed off it).
		Vec3 start = Vec3.atBottomCenterOf(step.from());
		if (Math.abs(this.drifter.getX() - start.x) > 1.0 || Math.abs(this.drifter.getZ() - start.z) > 1.0
			|| Math.abs(this.drifter.getY() - start.y) > 0.6) {
			this.replan("not where it planned to bridge from", true);
			return Status.RUNNING;
		}

		if (!Tools.BUILDING_BLOCK.test(this.drifter.getMainHandItem())) {
			// Opening the inventory lets go of sneak: come to a full stop first, so nothing carries it over the edge.
			this.keys().releaseAll();
			this.keys().set(VirtualKeys.Key.SNEAK, true);
			if (this.drifter.onGround() && this.drifter.getDeltaMovement().horizontalDistanceSqr() < STOPPED_SPEED_SQR) {
				this.startSub(new SelectItemTask(this.drifter, Tools.BUILDING_BLOCK, "a building block"));
			}

			return Status.RUNNING;
		}

		BlockPos support = step.from().below();
		Direction face = Direction.getNearest(step.to().getX() - step.from().getX(), 0, step.to().getZ() - step.from().getZ());
		this.keys().set(VirtualKeys.Key.SNEAK, true);
		this.keys().set(VirtualKeys.Key.SPRINT, false);
		if (Reach.aimPoint(this.drifter, support, face).isPresent()) {
			this.startSub(new UseOnBlockTask(this.drifter, support, face, "bridge toward " + step.to().toShortString()));
			// After placing, take up the bridging pose again (the aim moved the head).
			this.bridgePoseSet = false;
			return Status.RUNNING;
		}

		if (++this.creep > BRIDGE_CREEP_TICKS) {
			// Say how far out it got, to tell "never reached the edge" from "reached it but couldn't see the side".
			double out = this.drifter.getEyePosition().subtract(Vec3.atCenterOf(support)).dot(Vec3.atLowerCornerOf(face.getNormal())) - 0.5;
			this.replan(String.format(Locale.ROOT, "couldn't reach the edge to bridge (eye %.2f past the edge, %s)", out,
				this.drifter.onGround() ? "on the ground" : "in the air"));
			return Status.RUNNING;
		}

		// Sneak backward to the edge, facing away from the gap and looking steeply down, like a player bridging.
		this.backBridgeMove(step);
		return Status.RUNNING;
	}

	/**
	 * Back-bridging, the way players bridge (user's tip from testing): face away from the gap, look steeply down, and
	 * sneak backward. Leaning out backward past the edge, the side of the block it stands on is then right in front
	 * of and below its eyes. Facing the gap instead, that side ends up behind its eyes once it leans out, and it had
	 * to spin round to aim at it (found in testing: the one block it placed was when it happened to face backward).
	 */
	private void backBridgeMove(PathStep step) {
		if (!this.bridgePoseSet) {
			float awayYaw = (float)(Mth.atan2(step.from().getZ() - step.to().getZ(), step.from().getX() - step.to().getX()) * Mth.RAD_TO_DEG) - 90.0F;
			this.drifter.getCamera().lookAt(awayYaw, BRIDGE_PITCH, CameraModel.TurnSpeed.NORMAL);
			this.bridgePoseSet = true;
		}

		VirtualKeys keys = this.keys();
		keys.set(VirtualKeys.Key.SNEAK, true);
		keys.set(VirtualKeys.Key.SPRINT, false);
		keys.set(VirtualKeys.Key.JUMP, false);
		this.pressToward(Vec3.atBottomCenterOf(step.to()));
	}

	/** Movement keys toward a point, whichever way it's facing (backward and sideways keys included). */
	private void pressToward(Vec3 goal) {
		VirtualKeys keys = this.keys();
		double dx = goal.x - this.drifter.getX();
		double dz = goal.z - this.drifter.getZ();
		boolean move = dx * dx + dz * dz > 0.05 * 0.05;
		float wantedYaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float relative = Mth.wrapDegrees(wantedYaw - this.drifter.getYRot());
		keys.set(VirtualKeys.Key.FORWARD, move && Math.abs(relative) < 67.5F);
		keys.set(VirtualKeys.Key.BACK, move && Math.abs(relative) > 112.5F);
		keys.set(VirtualKeys.Key.LEFT, move && relative < -22.5F && relative > -157.5F);
		keys.set(VirtualKeys.Key.RIGHT, move && relative > 22.5F && relative < 157.5F);
	}

	/** Pillar up: take out a building block, look straight down, jump, and place it under itself at the top. */
	private Status pillar(PathStep step) {
		VirtualKeys keys = this.keys();
		switch (this.pillarStage) {
			case 0 -> {
				if (!Tools.BUILDING_BLOCK.test(this.drifter.getMainHandItem())) {
					this.startSub(new SelectItemTask(this.drifter, Tools.BUILDING_BLOCK, "a building block"));
					return Status.RUNNING;
				}

				keys.releaseAll();
				this.drifter.getCamera().lookAt(this.drifter.getYRot(), 90.0F, CameraModel.TurnSpeed.NORMAL);
				this.pillarStage = 1;
			}
			case 1 -> {
				if (this.drifter.getCamera().isSettled() && this.drifter.getXRot() < 80.0F) {
					// Something (a reflex) turned its head: look straight down again before jumping.
					this.pillarStage = 0;
					return Status.RUNNING;
				}

				if (this.drifter.getCamera().isSettled() && this.drifter.onGround()) {
					keys.set(VirtualKeys.Key.JUMP, true);
					this.pillarStage = 2;
					this.pillarTicks = 0;
				}
			}
			case 2 -> {
				keys.set(VirtualKeys.Key.JUMP, false);
				BlockPos from = step.from();
				if (this.drifter.getY() >= from.getY() + 1.0) {
					BlockHitResult hit = Reach.crosshair(this.drifter);
					if (hit != null && hit.getBlockPos().equals(from.below()) && hit.getDirection() == Direction.UP) {
						InteractionResult placed = FakePlayerBridge.act(this.drifter,
							fake -> fake.gameMode.useItemOn(fake, this.level(), fake.getMainHandItem(), InteractionHand.MAIN_HAND, hit));
						if (placed.consumesAction()) {
							this.drifter.swing(InteractionHand.MAIN_HAND);
							this.drifter.getSenses().placedBlock(from);
							this.pillarStage = 3;
							return Status.RUNNING;
						}
					}
				}

				// A few ticks' grace: on the first tick of the jump it is still on the ground.
				if (++this.pillarTicks > 4 && this.drifter.onGround() && this.drifter.getY() < from.getY() + 0.5) {
					// Landed without placing: try again.
					if (++this.pillarTries > 3) {
						this.replan("couldn't pillar up");
					} else {
						this.pillarStage = 1;
					}
				}
			}
			default -> {
				// Placed: wait to land on the new block.
			}
		}

		return this.checkStuck();
	}

	@Override
	protected void onStop() {
		this.stopSub();
		this.releaseKeys();
		this.drifter.getCamera().clearTarget();
		this.drifter.setDebugPath(List.of(), 0);
	}
}
