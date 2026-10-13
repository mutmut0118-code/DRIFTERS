package com.dinglebob.drifter.brain.activity;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.NightTunnel;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.Shelter;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.executor.task.WaitTask;
import com.dinglebob.drifter.hands.Tools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The night-one shelter (DESIGN.md section 11, tier 1 "panic shelter": "digs down and covers the hole"): digs three
 * blocks straight down where it stands, puts a block over its head, waits there until morning (by its own sense of
 * time), then digs out and climbs back up. Mobs can't reach it and can't spawn in the 1x2 space it fills.
 * <p>Night miners (section 18, by personality) don't just wait: once sealed in they dig a tunnel out from the bottom of
 * the shaft, mining the ore they uncover, blocking off any cave or water they break into, and walk back to the shaft
 * in the morning.
 * TODO(milestone 8): tier 2 hillside dig-in with a 3x3 room, tier 3 starter hut, sleeping in a bed.
 */
public class ShelterActivity extends Activity {
	public static final int DEPTH = 3;
	/** Digging a block slower than this (seconds) isn't worth it for a shelter: find another spot. */
	private static final float MAX_DIG_SECONDS = 4.0F;
	private static final int MAX_SPOT_TRIES = 4;
	/** Tries at getting out of the water onto land, searching farther each time. */
	private static final int MAX_DRY_TRIES = 3;
	/** Dry ground for the night: this far from any water. */
	private static final int DRY_MARGIN = 2;

	private enum Phase {
		DIG, BOX, SEAL, WAIT, MINE, RETURN, EXIT, DONE
	}

	/** Digging a tunnel block slower than this (seconds) isn't worth it: it turns. */
	private static final float MAX_TUNNEL_DIG_SECONDS = 6.0F;
	/** What the current step of the tunnel work is, so {@link #stepDone} knows what just finished. */
	@Nullable
	private BlockPos digging;
	@Nullable
	private BlockPos stepping;
	@Nullable
	private BlockPos refill;
	private final java.util.ArrayDeque<BlockPos> ores = new java.util.ArrayDeque<>();
	/** The step running now is an ore out of the tunnel wall (missing one isn't a failure: it may just be out of view). */
	private boolean oreStep;
	/** Ores it couldn't get at from where it stood: tried again once it has moved on a step. */
	private final java.util.Map<BlockPos, Integer> oreTries = new java.util.HashMap<>();
	private final java.util.List<BlockPos> retryOres = new java.util.ArrayList<>();

	private Phase phase = Phase.DIG;
	@Nullable
	private BlockPos top;
	private int spotTries;
	private int offGroundTicks;
	private int dryTries;

	public ShelterActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return switch (this.phase) {
			case DIG -> "dig in for the night";
			case BOX -> "wall itself in for the night";
			case SEAL -> "seal the shelter";
			case WAIT -> "wait for morning in the shelter";
			case MINE -> "mine from the shelter";
			case RETURN -> "walk back to the shelter shaft";
			case EXIT, DONE -> "climb out of the shelter";
		};
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 15;
	}

	@Override
	protected void onStart() {
		NightTunnel tunnel = this.drifter.getDrifterBrain().nightTunnel();
		if (tunnel != null && tunnel.contains(this.drifter.blockPosition())) {
			// Back from a break (a meal, a fight) in its own night tunnel: carry on.
			this.phase = Schedule.isNight(this.drifter) && !tunnel.done ? Phase.MINE : Phase.RETURN;
		} else if (Shelter.isSealedIn(this.drifter)) {
			this.phase = Phase.WAIT;
		}
	}

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		BlockPos feet = this.drifter.blockPosition();
		switch (this.phase) {
			case DIG -> {
				if (this.drifter.isInWater() || this.drifter.onClimbable()) {
					// Can't dig in while swimming or on a ladder: get all the way onto dry land first, away from the water
					// (found in testing: it stopped at the shore, still in the water, and gave up for the night).
					if (++this.dryTries > MAX_DRY_TRIES) {
						return this.fail("can't get out of the water");
					}

					int radius = 16 + 8 * this.dryTries;
					BlockPos dry = KnownBlocks.nearestDryGround(this.drifter, radius, 0.0, DRY_MARGIN);
					if (dry == null) {
						dry = KnownBlocks.nearestStandable(this.drifter, radius);
					}

					if (dry == null) {
						return this.fail("knows no dry land to get to");
					}

					this.top = null;
					this.think("getting out of the water for the night");
					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(dry), 0.4, "get onto dry land"));
				}

				if (!this.drifter.onGround()) {
					return ++this.offGroundTicks > 40 ? this.fail("can't get its feet on the ground to dig in") : Status.RUNNING;
				}

				this.offGroundTicks = 0;
				if (this.top == null) {
					this.top = feet;
					this.think("digging in for the night");
				}

				int depth = this.top.getY() - feet.getY();
				if (depth >= DEPTH) {
					this.phase = Phase.SEAL;
					return Status.RUNNING;
				}

				// Stand in the middle of the block first: its body is 0.6 wide, so off-centre it would stay standing on the
				// rim of its own hole instead of dropping in (found in review).
				Vec3 middle = Vec3.atBottomCenterOf(this.top);
				if (depth == 0 && (Math.abs(this.drifter.getX() - middle.x) > 0.2 || Math.abs(this.drifter.getZ() - middle.z) > 0.2)) {
					return this.begin(new CenterOnBlockTask(this.drifter, middle));
				}

				// Grass, a flower or a snow layer where it stands is in the way of its crosshair (found in testing: it
				// stood in grass on a mountain and "couldn't see" the block under its feet, over and over). A player just
				// breaks it first.
				BlockState inTheWay = level.getBlockState(feet);
				if (depth == 0 && !inTheWay.isAir() && inTheWay.getCollisionShape(level, feet).isEmpty() && level.getFluidState(feet).isEmpty()
					&& inTheWay.getDestroySpeed(level, feet) >= 0.0F) {
					return this.begin(this.dig(feet));
				}

				BlockPos below = depth == 0 ? this.top.below() : feet.below();
				if (!this.diggable(level, below) || !this.solidFloor(level, below.below())) {
					return this.tryAnotherSpot(depth);
				}

				return this.begin(this.dig(below));
			}
			case BOX -> {
				return this.box();
			}
			case SEAL -> {
				BlockPos cover = feet.above(2);
				if (!level.getBlockState(cover).getCollisionShape(level, cover).isEmpty()) {
					this.phase = Phase.WAIT;
					this.think("sealed in for the night");
					if (this.drifter.getDrifterBrain().nightStyle() == Schedule.NightStyle.MINE
						&& this.drifter.getInventory().count(stack -> stack.is(net.minecraft.tags.ItemTags.PICKAXES)) > 0) {
						Direction direction = Direction.from2DDataValue(this.drifter.getRandom().nextInt(4));
						this.drifter.getDrifterBrain().setNightTunnel(new NightTunnel(feet, direction,
							net.minecraft.util.Mth.nextInt(this.drifter.getRandom(), NightTunnel.MAIN_LEG_MIN, NightTunnel.MAIN_LEG_MAX)));
						this.phase = Phase.MINE;
						this.think("time doesn't matter underground: mining " + direction.getName() + " from the shelter");
					}

					return Status.RUNNING;
				}

				if (this.drifter.getInventory().count(ShelterActivity::isCoverBlock) == 0) {
					return this.fail("has no block to cover the hole with");
				}

				java.util.function.Predicate<ItemStack> block = this.drifter.getInventory().count(Tools.BUILDING_BLOCK) > 0
					? Tools.BUILDING_BLOCK : ShelterActivity::isCoverBlock;
				return this.begin(new PlaceBlockTask(this.drifter, cover, block, "a block over its head"));
			}
			case WAIT -> {
				if (!Schedule.isNight(this.drifter) && Schedule.believedTime(this.drifter) < Schedule.SHELTER_THINKING) {
					this.phase = Phase.EXIT;
					this.think("it should be morning: climbing out");
					return Status.RUNNING;
				}

				return this.begin(new WaitTask(this.drifter, "morning", () -> !Schedule.isNight(this.drifter)
					&& Schedule.believedTime(this.drifter) < Schedule.SHELTER_THINKING, 20 * 60));
			}
			case MINE -> {
				return this.mine(level, feet);
			}
			case RETURN -> {
				NightTunnel tunnel = this.drifter.getDrifterBrain().nightTunnel();
				if (tunnel == null || feet.equals(tunnel.shaftBottom)) {
					this.phase = Schedule.isNight(this.drifter) ? Phase.WAIT : Phase.EXIT;
					return Status.RUNNING;
				}

				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(tunnel.shaftBottom), 0.3, "walk back to the shaft"));
			}
			case EXIT -> {
				if (this.boxedAtSurface(feet)) {
					// Walled in at ground level: break out through one wall (head first, then feet), toward dry ground.
					for (Direction direction : Direction.Plane.HORIZONTAL) {
						BlockPos outside = feet.relative(direction);
						if (!solidAt(level, outside.below()) || !level.getFluidState(outside).isEmpty()) {
							continue;
						}

						if (solidAt(level, outside.above())) {
							return this.begin(this.dig(outside.above()));
						}

						if (solidAt(level, outside)) {
							return this.begin(this.dig(outside));
						}

						this.phase = Phase.DONE;
						this.think("out of the shelter");
						return Status.RUNNING;
					}
				}

				BlockPos cover = feet.above(2);
				if (!level.getBlockState(cover).getCollisionShape(level, cover).isEmpty()) {
					return this.begin(this.dig(cover));
				}

				this.phase = Phase.DONE;
				BlockPos surface = this.top != null ? this.top : feet.above(DEPTH);
				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(surface), 1.5, "climb out"));
			}
			default -> {
				return Status.SUCCESS;
			}
		}
	}

	@Override
	protected boolean countsAsFailure(com.dinglebob.drifter.executor.Task step) {
		return !(step instanceof WaitTask) && !this.oreStep;
	}

	@Override
	public boolean atCheckpoint() {
		// Only once sealed in or mining between blocks (eating can happen in there); never half dug.
		return (this.phase == Phase.WAIT || this.phase == Phase.MINE) && super.atCheckpoint();
	}

	// ---------------------------------------------------------------------------------------------
	// Night mining (section 18 by personality; section 10.3 strip mining): a 1x2 tunnel from the shaft bottom.
	// It only reads blocks mining has revealed (each dug block shows it its six neighbours), never further.
	// ---------------------------------------------------------------------------------------------

	private Status mine(Level level, BlockPos feet) {
		NightTunnel tunnel = this.drifter.getDrifterBrain().nightTunnel();
		if (tunnel == null) {
			this.phase = Phase.WAIT;
			return Status.RUNNING;
		}

		if (!Schedule.isNight(this.drifter)) {
			this.phase = Phase.RETURN;
			this.think("morning: back to the shaft");
			return Status.RUNNING;
		}

		// Broke into a cave or water: block the opening back up first (section 10.3 "blocks flowing water").
		if (this.refill != null) {
			BlockPos cell = this.refill;
			if (solidAt(level, cell) || this.drifter.getInventory().count(ShelterActivity::isCoverBlock) == 0) {
				this.refill = null;
				if (!solidAt(level, cell)) {
					tunnel.done = true;
				}

				return Status.RUNNING;
			}

			java.util.function.Predicate<ItemStack> block = this.drifter.getInventory().count(Tools.BUILDING_BLOCK) > 0
				? Tools.BUILDING_BLOCK : ShelterActivity::isCoverBlock;
			return this.begin(new PlaceBlockTask(this.drifter, cell, block, "a block to close off the opening"));
		}

		// Section 10.3 "Veins: always finishes a visible ore vein before moving on".
		while (!this.ores.isEmpty()) {
			BlockPos ore = this.ores.poll();
			if (isOre(level.getBlockState(ore)) && this.canMineOre(level, ore)
				&& this.drifter.getEyePosition().distanceTo(Vec3.atCenterOf(ore)) <= this.drifter.blockInteractionRange()) {
				this.digging = ore;
				this.oreStep = true;
				return this.begin(this.dig(ore));
			}
		}

		if (tunnel.done || tunnel.steps >= NightTunnel.MAX_STEPS) {
			tunnel.done = true;
			this.phase = Phase.RETURN;
			this.think("done mining for tonight");
			return Status.RUNNING;
		}

		if (!feet.equals(tunnel.tip)) {
			this.stepping = null;
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(tunnel.tip), 0.3, "walk to the end of the tunnel"));
		}

		// A torch now and then, if it has any, always on its habit's side going in (section 10.3: "torches on the right
		// going in" so the way out is on the left; user's note: players use torches to know where they are and the way
		// back). Head height, one step back, on the side wall.
		if (tunnel.sinceTorch >= NightTunnel.TORCH_EVERY
			&& this.drifter.getInventory().count(stack -> stack.is(net.minecraft.world.item.Items.TORCH)) > 0) {
			tunnel.sinceTorch = 0;
			BlockPos spot = feet.relative(tunnel.direction.getOpposite()).above();
			Direction wall = this.drifter.getPersonality().habits().torchSide == com.dinglebob.drifter.personality.Habits.Side.RIGHT
				? tunnel.direction.getClockWise() : tunnel.direction.getCounterClockWise();
			if (tunnel.opened.contains(spot) && level.getBlockState(spot).isAir()) {
				return this.begin(new PlaceBlockTask(this.drifter, spot, stack -> stack.is(net.minecraft.world.item.Items.TORCH), "a torch")
					.preferSupport(wall));
			}
		}

		if (tunnel.legSteps >= tunnel.legLength) {
			if (++tunnel.legs >= NightTunnel.MAX_LEGS) {
				tunnel.done = true;
				return Status.RUNNING;
			}

			tunnel.direction = this.drifter.getRandom().nextBoolean() ? tunnel.direction.getClockWise() : tunnel.direction.getCounterClockWise();
			tunnel.legSteps = 0;
			tunnel.legLength = net.minecraft.util.Mth.nextInt(this.drifter.getRandom(), NightTunnel.SIDE_LEG_MIN, NightTunnel.SIDE_LEG_MAX);
			this.think("turning " + tunnel.direction.getName() + " in the tunnel");
		}

		BlockPos nextFeet = feet.relative(tunnel.direction);
		BlockPos nextHead = nextFeet.above();
		for (BlockPos cell : new BlockPos[]{nextHead, nextFeet}) {
			BlockState state = level.getBlockState(cell);
			boolean open = state.getCollisionShape(level, cell).isEmpty();
			boolean bad = !level.getFluidState(cell).isEmpty() || open && !tunnel.opened.contains(cell) || state.getBlock() instanceof FallingBlock
				|| !open && (!KnownBlocks.mayTake(level, cell) || state.getDestroySpeed(level, cell) < 0.0F
				|| Tools.digSeconds(this.drifter, state, level, cell) > MAX_TUNNEL_DIG_SECONDS);
			if (bad) {
				// Water, a cave, gravel, or rock too hard: this way's no good, turn.
				tunnel.legSteps = tunnel.legLength;
				return Status.RUNNING;
			}
		}

		for (BlockPos cell : new BlockPos[]{nextHead, nextFeet}) {
			if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
				this.digging = cell;
				return this.begin(this.dig(cell));
			}
		}

		this.stepping = nextFeet;
		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(nextFeet), 0.3, "step along the tunnel"));
	}

	@Override
	protected void stepDone(com.dinglebob.drifter.executor.Task step, Status status) {
		NightTunnel tunnel = this.drifter.getDrifterBrain().nightTunnel();
		Level level = this.drifter.level();
		if (tunnel != null && this.digging != null && status == Status.SUCCESS
			&& level.getBlockState(this.digging).getCollisionShape(level, this.digging).isEmpty()) {
			BlockPos dug = this.digging;
			tunnel.opened.add(dug);
			for (Direction direction : Direction.values()) {
				BlockPos neighbor = dug.relative(direction);
				if (tunnel.opened.contains(neighbor)) {
					continue;
				}

				BlockState state = level.getBlockState(neighbor);
				if (!level.getFluidState(neighbor).isEmpty() || state.isAir()) {
					// Broke into a cave or water: close it off, and this way's done.
					this.refill = dug;
					tunnel.opened.remove(dug);
					tunnel.legSteps = tunnel.legLength;
					if (++tunnel.breaches >= 3) {
						tunnel.done = true;
					}

					this.think(state.isAir() ? "broke into a cave: blocking it off" : "hit water: blocking it off");
					break;
				}

				if (isOre(state) && !this.ores.contains(neighbor)) {
					this.ores.add(neighbor);
				}
			}
		}

		if (this.oreStep && status == Status.FAILURE && this.digging != null && this.oreTries.merge(this.digging, 1, Integer::sum) < 2) {
			// Out of view from here: it tries again from the next step along.
			this.retryOres.add(this.digging);
		}

		if (tunnel != null && this.stepping != null && status == Status.SUCCESS && this.drifter.blockPosition().equals(this.stepping)) {
			this.ores.addAll(this.retryOres);
			this.retryOres.clear();
			tunnel.tip = this.stepping;
			tunnel.legSteps++;
			tunnel.steps++;
			tunnel.sinceTorch++;
		}

		this.digging = null;
		this.stepping = null;
		this.oreStep = false;
	}

	private boolean canMineOre(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		int slot = Tools.bestToolSlot(this.drifter, state, level, pos);
		return slot >= 0 && this.drifter.getInventory().getItem(slot).isCorrectToolForDrops(state) && KnownBlocks.mayTake(level, pos);
	}

	private static boolean isOre(BlockState state) {
		return state.is(net.minecraft.tags.BlockTags.COAL_ORES) || state.is(net.minecraft.tags.BlockTags.IRON_ORES)
			|| state.is(net.minecraft.tags.BlockTags.COPPER_ORES) || state.is(net.minecraft.tags.BlockTags.GOLD_ORES)
			|| state.is(net.minecraft.tags.BlockTags.REDSTONE_ORES) || state.is(net.minecraft.tags.BlockTags.LAPIS_ORES)
			|| state.is(net.minecraft.tags.BlockTags.DIAMOND_ORES) || state.is(net.minecraft.tags.BlockTags.EMERALD_ORES);
	}

	/** Phase for the overlay and the selector: is it already safe? */
	public boolean isSealed() {
		return this.phase == Phase.WAIT;
	}

	private Status tryAnotherSpot(int depth) {
		// Half dug, or out of spots: wall itself in right here instead (it never just stands in the open all night).
		if (depth > 0 || ++this.spotTries > MAX_SPOT_TRIES) {
			return this.startBox("can't dig in here");
		}

		// Somewhere dry a few blocks away (away from water, which pours into a hole), else a few blocks in any direction.
		BlockPos here = this.drifter.blockPosition();
		BlockPos spot = KnownBlocks.nearestDryGround(this.drifter, 10, 2.5, DRY_MARGIN);
		if (spot == null) {
			Direction direction = Direction.from2DDataValue(this.drifter.getRandom().nextInt(4));
			spot = here.relative(direction, 3);
		}

		this.top = null;
		this.think("the ground here is no good for digging in; trying a few blocks over");
		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "find a spot to dig in"));
	}

	// ---------------------------------------------------------------------------------------------
	// Walling in: the fallback when it can't dig down (sand, a beach, water nearby). Like a player, it puts blocks on
	// all four sides at foot and head height, one on top of a wall to place against, then one over its head.
	// ---------------------------------------------------------------------------------------------

	private Status startBox(String why) {
		if (!this.drifter.onGround() || this.drifter.isInWater()) {
			return this.fail(why);
		}

		int needed = this.boxCellsNeeded(this.drifter.blockPosition());
		if (this.drifter.getInventory().count(ShelterActivity::isCoverBlock) < needed) {
			return this.fail(why + ", and not enough blocks to wall itself in (needs " + needed + ")");
		}

		this.think(why + ": walling itself in instead");
		this.phase = Phase.BOX;
		return Status.RUNNING;
	}

	/** The cells to fill, in order: four at its feet, four at its head, a step on one wall, the roof. */
	private static java.util.List<BlockPos> boxCells(BlockPos feet, Level level) {
		java.util.List<BlockPos> cells = new java.util.ArrayList<>();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			cells.add(feet.relative(direction));
		}

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			cells.add(feet.above().relative(direction));
		}

		// The roof has nothing to be placed against but this: a block on top of one wall.
		BlockPos roof = feet.above(2);
		boolean roofSupported = false;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (solidAt(level, roof.relative(direction))) {
				roofSupported = true;
			}
		}

		if (!roofSupported) {
			cells.add(roof.relative(Direction.NORTH));
		}

		cells.add(roof);
		return cells;
	}

	private int boxCellsNeeded(BlockPos feet) {
		Level level = this.drifter.level();
		int needed = 0;
		for (BlockPos cell : boxCells(feet, level)) {
			if (!solidAt(level, cell)) {
				needed++;
			}
		}

		return needed;
	}

	private Status box() {
		if (Shelter.isSealedIn(this.drifter)) {
			this.phase = Phase.WAIT;
			this.think("walled in for the night");
			return Status.RUNNING;
		}

		if (this.drifter.getInventory().count(ShelterActivity::isCoverBlock) == 0) {
			return this.fail("ran out of blocks to wall itself in");
		}

		Level level = this.drifter.level();
		BlockPos feet = this.drifter.blockPosition();
		for (BlockPos cell : boxCells(feet, level)) {
			if (solidAt(level, cell)) {
				continue;
			}

			// Something to place against (no floating blocks): the ground, a wall already up.
			boolean supported = false;
			for (Direction direction : Direction.values()) {
				BlockPos neighbor = cell.relative(direction);
				if (!neighbor.equals(feet) && !neighbor.equals(feet.above()) && solidAt(level, neighbor)) {
					supported = true;
					break;
				}
			}

			if (supported) {
				java.util.function.Predicate<ItemStack> block = this.drifter.getInventory().count(Tools.BUILDING_BLOCK) > 0
					? Tools.BUILDING_BLOCK : ShelterActivity::isCoverBlock;
				return this.begin(new PlaceBlockTask(this.drifter, cell, block, "a wall block"));
			}
		}

		return this.fail("can't wall itself in here");
	}

	private static boolean solidAt(Level level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
	}

	/** Walled in at ground level (not down a hole): the walls round its head have open air above them. */
	private boolean boxedAtSurface(BlockPos feet) {
		Level level = this.drifter.level();
		int open = 0;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (!solidAt(level, feet.above(2).relative(direction))) {
				open++;
			}
		}

		return open >= 2;
	}

	/** Shuffles to the middle of the block it stands on, slowly (sneaking), with the movement keys. */
	private static final class CenterOnBlockTask extends com.dinglebob.drifter.executor.Task {
		private final Vec3 middle;

		CenterOnBlockTask(DrifterEntity drifter, Vec3 middle) {
			super(drifter);
			this.middle = middle;
		}

		@Override
		public String describe() {
			return "step to the middle of the block";
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 3;
		}

		@Override
		protected Status onTick() {
			if (Math.abs(this.drifter.getX() - this.middle.x) <= 0.15 && Math.abs(this.drifter.getZ() - this.middle.z) <= 0.15) {
				com.dinglebob.drifter.motor.Steering.stopMoving(this.drifter);
				this.drifter.getKeys().set(com.dinglebob.drifter.motor.VirtualKeys.Key.SNEAK, false);
				return Status.SUCCESS;
			}

			this.drifter.getKeys().set(com.dinglebob.drifter.motor.VirtualKeys.Key.SNEAK, true);
			com.dinglebob.drifter.motor.Steering.pressToward(this.drifter, this.middle);
			return Status.RUNNING;
		}

		@Override
		protected void onStop() {
			com.dinglebob.drifter.motor.Steering.stopMoving(this.drifter);
			this.drifter.getKeys().set(com.dinglebob.drifter.motor.VirtualKeys.Key.SNEAK, false);
		}
	}

	/** Dirt, stone and the like: no sand or gravel (it would pour in), no fluids beside it, quick enough to dig. */
	private boolean diggable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getCollisionShape(level, pos).isEmpty() || state.getBlock() instanceof FallingBlock || !KnownBlocks.mayTake(level, pos)
			|| state.getDestroySpeed(level, pos) < 0.0F || Tools.digSeconds(this.drifter, state, level, pos) > MAX_DIG_SECONDS) {
			return false;
		}

		for (Direction direction : Direction.values()) {
			if (!level.getFluidState(pos.relative(direction)).isEmpty()) {
				return false;
			}
		}

		return true;
	}

	private boolean solidFloor(Level level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
	}

	private static boolean isCoverBlock(ItemStack stack) {
		return Tools.BUILDING_BLOCK.test(stack) || stack.getItem() instanceof BlockItem block
			&& block.getBlock().defaultBlockState().isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
			&& !(block.getBlock() instanceof FallingBlock);
	}
}
