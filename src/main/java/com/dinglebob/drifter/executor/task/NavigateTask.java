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
	/** Sprint-jump only with at least this many straight steps ahead (no jumping into a turn). */
	private static final int SPRINT_JUMP_MIN_STRAIGHT = 3;
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
	/** A drop this deep or deeper next to it makes it sneak (falls of 4+ blocks hurt). */
	private static final int DEEP_DROP = 4;

	private final Supplier<Vec3> target;
	private final double radius;
	private final String label;
	private final Pace pace;
	/** Rolled once per trip: whether this is one of the (most) trips it sprint-jumps. */
	private boolean sprintJumpTrip;
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
		this.plan("start");
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

		return feet;
	}

	private boolean atGoal() {
		return this.drifter.position().distanceTo(this.target.get()) <= this.radius
			&& (this.drifter.onGround() || this.drifter.isInWater() || this.drifter.onClimbable());
	}

	private void plan(String reason) {
		this.stopSub();
		this.keys().releaseAll();
		this.plannedTarget = this.target.get();
		this.search = new PathSearch(this.drifter, this.feet(), this.plannedTarget, Math.max(this.radius, 0.5));
		this.path = List.of();
		this.index = 0;
		this.sinceProgress = 0;
		this.sinceReplan = 0;
		this.lastReplanReason = reason;
		this.resetStepState();
		this.drifter.setDebugPath(List.of(), 0);
	}

	private void replan(String reason) {
		this.replans++;
		this.plan(reason);
	}

	private void resetStepState() {
		this.creep = 0;
		this.pillarStage = 0;
		this.pillarTries = 0;
		this.pillarTicks = 0;
		this.waitToSee = 0;
	}

	@Override
	protected Status onTick() {
		if (this.atGoal()) {
			if (!this.follow) {
				return Status.SUCCESS;
			}

			// Follow mode: wait here until the target moves away again.
			this.stopSub();
			this.keys().releaseAll();
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
			this.plan("target moved");
		}

		if (this.search != null) {
			return this.tickPlanning();
		}

		this.sinceReplan++;
		if (this.follow && this.sinceReplan % CHASE_REPATH_TICKS == 0 && this.target.get().distanceTo(this.plannedTarget) > CHASE_REPATH_DISTANCE) {
			this.plan("target moved");
			return Status.RUNNING;
		}

		if (this.sub != null) {
			return this.tickSub();
		}

		if (this.index >= this.path.size()) {
			this.replan(this.partial ? "reached the end of a partial path" : "path ended short of the goal");
			return Status.RUNNING;
		}

		if (this.sinceReplan >= ROLLING_REPLAN_TICKS && this.unknownAhead()) {
			this.plan("rolling replan: now it has seen more of the way");
			return Status.RUNNING;
		}

		return this.followStep(this.path.get(this.index));
	}

	private Status tickPlanning() {
		PathSearch.Status status = this.search.tick(PathBudget.take(this.level()));
		if (status == PathSearch.Status.RUNNING) {
			return Status.RUNNING;
		}

		if (status == PathSearch.Status.FAILED) {
			this.search = null;
			return this.fail("it knows no way there (" + this.lastReplanReason + ")");
		}

		this.path = new ArrayList<>(this.search.result());
		this.partial = status == PathSearch.Status.PARTIAL;
		this.search = null;
		this.index = 0;
		this.drifter.setDebugPath(this.path, 0);
		return Status.RUNNING;
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
			if (this.index < this.path.size() && this.path.get(this.index).type() != MoveType.BRIDGE) {
				this.keys().set(VirtualKeys.Key.SNEAK, false);
			}

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
			this.startSub(new UseOnBlockTask(this.drifter, door, this.faceToward(door), "open the door at " + door.toShortString()));
			return Status.RUNNING;
		}

		if (step.type() == MoveType.PILLAR) {
			return this.pillar(step);
		}

		if (this.needsFloor(step) && !this.hasFloor(step.to())) {
			if (step.type() == MoveType.BRIDGE) {
				return this.bridge(step);
			}

			// It assumed ground it hadn't seen: look at it first, then replan if it's really not there.
			if (!this.drifter.getSenses().terrain().isKnown(step.to().below()) && this.waitToSee++ < WAIT_TO_SEE_TICKS) {
				this.drifter.getCamera().track(Vec3.atCenterOf(step.to().below()), CameraModel.TurnSpeed.NORMAL);
				this.keys().releaseAll();
				this.keys().set(VirtualKeys.Key.SNEAK, this.nearDeepDrop());
				return Status.RUNNING;
			}

			this.replan("no ground where it expected it at " + step.to().below().toShortString());
			return Status.RUNNING;
		}

		this.steer(step);
		return this.checkStuck();
	}

	private boolean arrived(PathStep step) {
		Vec3 center = Vec3.atBottomCenterOf(step.to());
		double dx = this.drifter.getX() - center.x;
		double dz = this.drifter.getZ() - center.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		boolean straight = this.index + 1 < this.path.size() && this.sameDirection(step, this.path.get(this.index + 1))
			&& (step.type() == MoveType.WALK || step.type() == MoveType.DIAGONAL);
		if (straight && this.passedInAir(step, dx, dz)) {
			return true;
		}

		if (horizontal > (straight ? ARRIVE_STRAIGHT : ARRIVE)) {
			return false;
		}

		boolean supported = this.drifter.onGround() || this.drifter.isInWater() || this.drifter.onClimbable();
		return supported && Math.abs(this.drifter.getY() - step.to().getY()) < 0.6;
	}

	/**
	 * Sprint-jumping sails over path points without standing on them. On a straight run, a point counts as reached
	 * once it's behind the Drifter (within a little sideways), even in mid-air.
	 */
	private boolean passedInAir(PathStep step, double dx, double dz) {
		double stepX = step.to().getX() - step.from().getX();
		double stepZ = step.to().getZ() - step.from().getZ();
		double length = Math.sqrt(stepX * stepX + stepZ * stepZ);
		if (length < 0.5) {
			return false;
		}

		double along = (dx * stepX + dz * stepZ) / length;
		double sideways = Math.abs(-dx * stepZ + dz * stepX) / length;
		return along > -0.1 && sideways < ARRIVE_STRAIGHT && Math.abs(this.drifter.getY() - step.to().getY()) < 1.3;
	}

	private boolean sameDirection(PathStep a, PathStep b) {
		return a.to().getX() - a.from().getX() == b.to().getX() - b.from().getX() && a.to().getZ() - a.from().getZ() == b.to().getZ() - b.from().getZ();
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

		if (horizontal > 0.05) {
			float wantedYaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			float relative = Mth.wrapDegrees(wantedYaw - this.drifter.getYRot());
			keys.set(VirtualKeys.Key.FORWARD, Math.abs(relative) < 67.5F);
			keys.set(VirtualKeys.Key.BACK, Math.abs(relative) > 112.5F);
			keys.set(VirtualKeys.Key.LEFT, relative < -22.5F && relative > -157.5F);
			keys.set(VirtualKeys.Key.RIGHT, relative > 22.5F && relative < 157.5F);
		} else {
			keys.set(VirtualKeys.Key.FORWARD, false);
			keys.set(VirtualKeys.Key.BACK, false);
			keys.set(VirtualKeys.Key.LEFT, false);
			keys.set(VirtualKeys.Key.RIGHT, false);
		}

		// Sneak on bridges and whenever a deep drop is right beside it (section 12: "sneaks near edges"), so momentum can
		// never carry it over (found in testing: it ran off a ravine edge). Not on a deliberate step down: sneaking
		// would stop it at the step.
		boolean intentionalDrop = step.type() == MoveType.STEP_DOWN || step.type() == MoveType.SWIM_DOWN;
		boolean sneaking = this.pace == Pace.SNEAK || step.type() == MoveType.BRIDGE || !intentionalDrop && this.nearDeepDrop() || this.nearSculk();
		keys.set(VirtualKeys.Key.SNEAK, sneaking);
		// Section 10.9 pace: on a TRAVEL trip it sprints the whole way when it can. It can't when hungry (food 6 or
		// less) and shouldn't when sneaking, bridging, near an edge or almost there.
		boolean sprint = this.pace == Pace.TRAVEL && !sneaking && keys.isDown(VirtualKeys.Key.FORWARD) && !this.drifter.isInWater()
			&& this.remainingDistance() > SPRINT_MIN_REMAINING && this.drifter.getFoodData().getFoodLevel() > FoodConstants.SPRINT_LEVEL;
		keys.set(VirtualKeys.Key.SPRINT, sprint);

		boolean jump = switch (step.type()) {
			case JUMP_UP -> horizontal < 1.3 || this.drifter.horizontalCollision;
			case SWIM_UP, CLIMB_UP -> true;
			case SWIM, WALK -> this.drifter.isInWater() && step.to().getY() >= this.feet().getY();
			default -> false;
		};
		// Sprint-jump on most trips: on flat straight runs with headroom, not into a turn, not right at the end.
		if (sprint && this.sprintJumpTrip && this.drifter.onGround() && (step.type() == MoveType.WALK || step.type() == MoveType.DIAGONAL)
			&& this.straightRunAhead() >= SPRINT_JUMP_MIN_STRAIGHT && this.remainingDistance() > SPRINT_JUMP_MIN_REMAINING && this.headroomAhead()) {
			jump = true;
		}

		// Auto-jump over an unexpected 1-block bump (players jump without thinking).
		if (this.drifter.horizontalCollision && this.drifter.onGround() && step.type() != MoveType.BRIDGE) {
			jump = true;
		}

		keys.set(VirtualKeys.Key.JUMP, jump);
	}

	/** No low ceiling over the next few steps (a sprint-jump would bonk its head). */
	private boolean headroomAhead() {
		for (int i = this.index; i < Math.min(this.path.size(), this.index + SPRINT_JUMP_MIN_STRAIGHT); i++) {
			BlockPos to = this.path.get(i).to();
			if (!this.isClear(to.above(2)) || !this.isClear(this.path.get(i).from().above(2))) {
				return false;
			}
		}

		return true;
	}

	/** Remembered sculk sensors or shriekers nearby (it has seen them): an ancient city or deep dark, so it creeps. */
	private boolean nearSculk() {
		return this.drifter.getMemory().nearest(MemoryKind.DANGER, this.level().dimension().location(), this.drifter.position(),
			SCULK_CAUTION_RANGE, entry -> entry.label.equals(BlockNotes.SCULK)) != null;
	}

	private int straightRunAhead() {
		int run = 0;
		PathStep first = this.path.get(this.index);
		for (int i = this.index; i < this.path.size(); i++) {
			PathStep step = this.path.get(i);
			if (step.type() != MoveType.WALK && step.type() != MoveType.DIAGONAL || !this.sameDirection(first, step)) {
				break;
			}

			run++;
		}

		return run;
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
		if (!Tools.BUILDING_BLOCK.test(this.drifter.getMainHandItem())) {
			this.startSub(new SelectItemTask(this.drifter, Tools.BUILDING_BLOCK, "a building block"));
			return Status.RUNNING;
		}

		BlockPos support = step.from().below();
		Direction face = Direction.getNearest(step.to().getX() - step.from().getX(), 0, step.to().getZ() - step.from().getZ());
		this.keys().set(VirtualKeys.Key.SNEAK, true);
		this.keys().set(VirtualKeys.Key.SPRINT, false);
		if (Reach.aimPoint(this.drifter, support, face).isPresent()) {
			this.startSub(new UseOnBlockTask(this.drifter, support, face, "bridge toward " + step.to().toShortString()));
			return Status.RUNNING;
		}

		if (++this.creep > BRIDGE_CREEP_TICKS) {
			this.replan("couldn't reach the edge to bridge");
			return Status.RUNNING;
		}

		// Creep toward the edge, looking down at where the block will go.
		this.steer(step);
		this.drifter.getCamera().track(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)), CameraModel.TurnSpeed.NORMAL);
		this.keys().set(VirtualKeys.Key.JUMP, false);
		return Status.RUNNING;
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
		this.keys().releaseAll();
		this.drifter.getCamera().clearTarget();
		this.drifter.setDebugPath(List.of(), 0);
	}
}
