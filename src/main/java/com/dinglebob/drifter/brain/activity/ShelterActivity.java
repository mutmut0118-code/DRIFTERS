package com.dinglebob.drifter.brain.activity;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.mining.Tunnel;
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

	/** Steps of night mining at most (it waits out the rest of the night). */
	private static final int NIGHT_MAX_STEPS = 48;

	private enum Phase {
		HUT, SLEEP, DIG, BOX, SEAL, WAIT, MINE, RETURN, EXIT, DONE
	}

	/** Night mining: the digger for tonight's tunnel (made fresh after a break; the tunnel itself is kept by the brain). */
	@Nullable
	private com.dinglebob.drifter.brain.mining.TunnelDigger digger;

	private Phase phase = Phase.DIG;
	@Nullable
	private BlockPos top;
	private int spotTries;
	private int offGroundTicks;
	private int dryTries;
	private int hutTries;
	private int doorTries;
	/** It has walked home for the night already (or has no home to go to). */
	private boolean wentHome;

	public ShelterActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return switch (this.phase) {
			case DIG -> "dig in for the night";
			case BOX -> "wall itself in for the night";
			case SEAL -> "seal the shelter";
			case HUT -> "go into the hut for the night";
			case SLEEP -> this.drifter.isSleeping() ? "sleep" : "go to bed";
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
		Tunnel tunnel = this.drifter.getDrifterBrain().nightTunnel();
		if (tunnel != null && tunnel.contains(this.drifter.blockPosition())) {
			// Back from a break (a meal, a fight) in its own night tunnel: carry on.
			this.phase = Schedule.isNight(this.drifter) && !tunnel.done ? Phase.MINE : Phase.RETURN;
		} else if (Shelter.isSheltered(this.drifter)) {
			this.phase = Phase.WAIT;
		} else if (this.hut() != null) {
			this.phase = Phase.HUT;
		}
	}

	/** Ticks asleep before most Drifters get up again (they can't skip the night; section 18 "Sleeping without skipping"). */
	private static final int NAP_TICKS = 20 * 6;
	private int slept;
	private int sleepTries;
	/** It has had its sleep tonight. */
	private boolean rested;

	/**
	 * Section 18: into bed by the player's rules (night or a thunderstorm, no monsters within 8 blocks); a short sleep
	 * rests it, then careful and homebody types stay in bed till morning and the rest get up. Hurt, it wakes (the game
	 * does that for any sleeper).
	 */
	private Status sleep(Level level) {
		com.dinglebob.drifter.building.StarterHut hut = this.hut();
		BlockPos bed = hut == null ? null : hut.bed(level);
		boolean morning = level.isDay() && !level.isThundering();
		if (this.drifter.isSleeping()) {
			boolean stays = this.drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.CAUTION) > 0.6
				|| this.drifter.getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.HOMEBODY;
			if (morning || bed == null || !stays && this.slept >= NAP_TICKS) {
				this.rested = true;
				this.drifter.stopSleeping();
				this.think(morning ? "woke up: morning" : "got up after a rest");
				this.phase = Phase.WAIT;
				return Status.RUNNING;
			}

			this.slept += 20;
			return this.begin(new WaitTask(this.drifter, "sleep", () -> false, 20));
		}

		if (bed == null || morning || ++this.sleepTries > 3) {
			this.phase = Phase.WAIT;
			return Status.RUNNING;
		}

		// A player can't rest with monsters about (8 blocks round, 5 up and down).
		if (!level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
			new net.minecraft.world.phys.AABB(bed).inflate(8.0, 5.0, 8.0), net.minecraft.world.entity.Entity::isAlive).isEmpty()) {
			this.think("can't sleep: monsters nearby");
			return this.begin(new WaitTask(this.drifter, "the monsters to go", () -> false, 20 * 10));
		}

		this.drifter.getKeys().releaseAll();
		this.drifter.startSleeping(bed);
		this.slept = 0;
		this.think("going to sleep");
		return Status.RUNNING;
	}

	@Override
	protected void onStop() {
		if (this.drifter.isSleeping()) {
			this.drifter.stopSleeping();
		}

		super.onStop();
	}

	/** Its finished hut, if it has one in reach for tonight (otherwise it digs in, as before). */
	@Nullable
	private com.dinglebob.drifter.building.StarterHut hut() {
		com.dinglebob.drifter.brain.Home home = this.drifter.getDrifterBrain().home();
		if (home == null || !home.hutBuilt || !home.near(this.drifter, Schedule.GO_HOME_RANGE) || this.drifter.getDrifterBrain().homeCutOff()) {
			return null;
		}

		com.dinglebob.drifter.building.StarterHut hut = com.dinglebob.drifter.building.StarterHut.of(home);
		return hut.shellBuilt(this.drifter.level()) ? hut : null;
	}

	/** Into the hut, door shut behind it (section 18: "Dusk: heads home ... closes doors"). */
	private Status enterHut(com.dinglebob.drifter.building.StarterHut hut) {
		Level level = this.drifter.level();
		if (!hut.isInside(this.drifter.blockPosition())) {
			if (++this.hutTries > 4) {
				this.phase = Phase.DIG;
				this.think("can't get into the hut: digging in instead");
				return Status.RUNNING;
			}

			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(hut.center), 0.3, "go into the hut"));
		}

		if (!Shelter.doorShut(level, hut)) {
			if (++this.doorTries > 3) {
				this.phase = Phase.WAIT;
				return Status.RUNNING;
			}

			return this.begin(com.dinglebob.drifter.executor.task.UseOnBlockTask.useBlock(this.drifter, hut.doorPos(), hut.door.getOpposite(),
				"shut the door"));
		}

		this.think("in the hut for the night");
		this.phase = hut.bed(level) != null ? Phase.SLEEP : Phase.WAIT;
		return Status.RUNNING;
	}

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		BlockPos feet = this.drifter.blockPosition();
		switch (this.phase) {
			case HUT -> {
				com.dinglebob.drifter.building.StarterHut hut = this.hut();
				if (hut == null) {
					this.phase = Phase.DIG;
					return Status.RUNNING;
				}

				return this.enterHut(hut);
			}
			case DIG -> {
				if (this.top == null && this.hut() != null && this.hutTries == 0) {
					this.phase = Phase.HUT;
					return Status.RUNNING;
				}

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

					// Nothing it has seen will do (a deep lake in the hills): back to where it last stood on dry ground.
					if (dry == null && this.drifter.lastDryGround() != null
						&& this.drifter.lastDryGround().distSqr(this.drifter.blockPosition()) < 96 * 96) {
						dry = this.drifter.lastDryGround();
						this.think("swimming back to where it came from");
					}

					if (dry == null) {
						return this.fail("knows no dry land to get to");
					}

					this.top = null;
					this.think("getting out of the water for the night");
					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(dry), 0.4, "get onto dry land"));
				}

				// With a home, the night is spent there (section 18: "Dusk: heads home").
				// TODO(milestone 8 step 2): sleep in the starter hut instead of digging in beside it.
				com.dinglebob.drifter.brain.Home home = this.drifter.getDrifterBrain().home();
				if (!this.wentHome && this.top == null) {
					this.wentHome = true;
					if (home != null && home.near(this.drifter, Schedule.GO_HOME_RANGE) && this.drifter.blockPosition().distSqr(home.center) > 4 * 4
						&& !this.drifter.getDrifterBrain().homeCutOff()) {
						this.think("heading home for the night");
						return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(home.center), 1.5, "go home for the night"));
					}
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
					return this.begin(new com.dinglebob.drifter.executor.task.CenterOnBlockTask(this.drifter, middle));
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
						// A small branch mine from the bottom of the shaft (section 10.3), branches at its spacing habit.
						Tunnel tunnel = new Tunnel(feet, direction, NIGHT_MAX_STEPS);
						tunnel.branchMine(feet, direction, net.minecraft.util.Mth.nextInt(this.drifter.getRandom(), 10, 18),
							this.drifter.getPersonality().habits().branchSpacing + 1, net.minecraft.util.Mth.nextInt(this.drifter.getRandom(), 4, 7));
						this.drifter.getDrifterBrain().setNightTunnel(tunnel);
						this.digger = null;
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
			case SLEEP -> {
				return this.sleep(level);
			}
			case WAIT -> {
				// In early at dusk: to bed once it's dark enough for one (found in testing: it never tried again).
				com.dinglebob.drifter.building.StarterHut hut = this.hut();
				if (!this.rested && hut != null && hut.isInside(this.drifter.blockPosition()) && hut.bed(level) != null && !level.isDay()
					&& this.sleepTries < 3) {
					this.phase = Phase.SLEEP;
					return Status.RUNNING;
				}

				if (!Schedule.isNight(this.drifter) && Schedule.believedTime(this.drifter) < Schedule.SHELTER_THINKING) {
					this.phase = Phase.EXIT;
					this.think("it should be morning: climbing out");
					return Status.RUNNING;
				}

				return this.begin(new WaitTask(this.drifter, "morning", () -> !Schedule.isNight(this.drifter)
					&& Schedule.believedTime(this.drifter) < Schedule.SHELTER_THINKING, 20 * 15));
			}
			case MINE -> {
				return this.mine(level, feet);
			}
			case RETURN -> {
				Tunnel tunnel = this.drifter.getDrifterBrain().nightTunnel();
				if (tunnel == null || feet.equals(tunnel.entrance)) {
					this.phase = Schedule.isNight(this.drifter) ? Phase.WAIT : Phase.EXIT;
					return Status.RUNNING;
				}

				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(tunnel.entrance), 0.3, "walk back to the shaft"));
			}
			case EXIT -> {
				// Morning in the hut: it walks out (the door opens on the way).
				com.dinglebob.drifter.brain.Home home = this.drifter.getDrifterBrain().home();
				if (home != null && home.hutBuilt && com.dinglebob.drifter.building.StarterHut.of(home).isInside(feet)) {
					com.dinglebob.drifter.building.StarterHut hut = com.dinglebob.drifter.building.StarterHut.of(home);
					this.phase = Phase.DONE;
					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(hut.doorPos().relative(hut.door, 2)), 0.8, "walk out of the hut"));
				}

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
		return !(step instanceof WaitTask) && (this.digger == null || this.phase != Phase.MINE || this.digger.countsAsFailure(step));
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

	/** Tonight's tunnel, one step at a time through the shared digger (section 10.3 strip mining). */
	private Status mine(Level level, BlockPos feet) {
		Tunnel tunnel = this.drifter.getDrifterBrain().nightTunnel();
		if (tunnel == null) {
			this.phase = Phase.WAIT;
			return Status.RUNNING;
		}

		if (!Schedule.isNight(this.drifter)) {
			this.phase = Phase.RETURN;
			this.think("morning: back to the shaft");
			return Status.RUNNING;
		}

		if (this.digger == null || this.digger.tunnel() != tunnel) {
			this.digger = new com.dinglebob.drifter.brain.mining.TunnelDigger(this.drifter, tunnel);
		}

		com.dinglebob.drifter.executor.Task step = this.digger.next();
		String thought = this.digger.takeThought();
		if (thought != null) {
			this.think(thought);
		}

		if (step == null) {
			tunnel.done = true;
			this.phase = Phase.RETURN;
			this.think("done mining for tonight");
			return Status.RUNNING;
		}

		return this.begin(step);
	}

	@Override
	protected void stepDone(com.dinglebob.drifter.executor.Task step, Status status) {
		if (this.phase == Phase.MINE && this.digger != null) {
			this.digger.stepDone(step, status);
			String thought = this.digger.takeThought();
			if (thought != null) {
				this.think(thought);
			}
		}
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
