package com.dinglebob.drifter.executor.task;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.Combat;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.mixin.BoatAccessor;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Across water by boat (DESIGN.md section 10.9: "carries one from early on; uses it on any water wider than a few
 * blocks"), as a player does it: the boat out of its pack onto the water at the bank, in, rowing across (its keys are the
 * paddles: BoatMixin), steering round what's in the way; at the far bank (where its route comes out of the water, or,
 * past the end of what it has planned, wherever the way on is land) out, a few punches to break the boat, and the boat
 * picked up again.
 */
public class BoatTask extends Task {
	private enum Phase {
		PLACE, BOARD, ROW, LAND, BREAK, PICK_UP
	}

	/** The water cell it puts the boat on. */
	private final BlockPos launch;
	/** Where its route comes out of the water on the far side, or null (past the end of the route: toward the target). */
	@Nullable
	private final BlockPos landing;
	private final Vec3 target;
	private Phase phase = Phase.PLACE;
	@Nullable
	private SelectItemTask select;
	@Nullable
	private NavigateTask walk;
	@Nullable
	private Boat boat;
	private int ticks;
	private int tries;
	private final Set<Integer> boatsBefore = new HashSet<>();
	private double bestDistance = Double.MAX_VALUE;
	private int sinceProgress;
	private float heading = Float.NaN;
	private int detourTicks;
	@Nullable
	private Vec3 boatLeftAt;
	private int boatsCarried;
	/** What went wrong on the water, if something did (it still gets out and takes its boat back first). */
	@Nullable
	private String trouble;

	/** Close to its landing, a bank ahead in the way: it gets out there. */
	private static final double LAND_NEAR = 6.0;
	/** A bank this close ahead of the boat's middle (blocks): it's there. */
	private static final double BANK_AHEAD = 2.2;
	private static final int STUCK_TICKS = 20 * 8;
	private static final double PROGRESS = 1.5;
	/** Turning: within this of the way it wants (degrees), it stops pulling round. */
	private static final float TURN_SLACK = 6.0F;
	/** The water slows a boat's turn by a tenth a tick: what turn it has left in it is this many ticks' worth. */
	private static final float TURN_CARRY = 9.0F;
	private static final int PICK_UP_TICKS = 20 * 5;

	public BoatTask(DrifterEntity drifter, BlockPos launch, @Nullable BlockPos landing, Vec3 target) {
		super(drifter);
		this.launch = launch.immutable();
		this.landing = landing == null ? null : landing.immutable();
		this.target = target;
	}

	@Override
	public String describe() {
		return "cross the water by boat";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 4;
	}

	public static boolean isBoat(ItemStack stack) {
		return stack.getItem() instanceof BoatItem;
	}

	public static boolean hasBoat(DrifterEntity drifter) {
		return drifter.getInventory().count(BoatTask::isBoat) > 0;
	}

	@Override
	protected Status onTick() {
		return switch (this.phase) {
			case PLACE -> this.place();
			case BOARD -> this.board();
			case ROW -> this.row();
			case LAND -> this.land();
			case BREAK -> this.breakBoat();
			case PICK_UP -> this.pickUp();
		};
	}

	/** Boat in hand, a look at the water by the bank, a right-click. */
	private Status place() {
		this.drifter.getKeys().releaseAll();
		if (!isBoat(this.drifter.getMainHandItem())) {
			if (this.select == null) {
				this.select = new SelectItemTask(this.drifter, BoatTask::isBoat, "the boat");
			}

			return this.select.tick() == Status.FAILURE ? this.fail("has no boat") : Status.RUNNING;
		}

		Level level = this.drifter.level();
		Vec3 surface = this.placePoint();
		if (surface == null) {
			return this.fail("no room on the water for the boat");
		}

		this.drifter.getCamera().lookAt(surface, CameraModel.TurnSpeed.NORMAL);
		if (!this.drifter.getCamera().isSettled() || ++this.ticks < 4) {
			return Status.RUNNING;
		}

		if (++this.tries > 3) {
			return this.fail("couldn't put the boat on the water");
		}

		// What it's looking at is that water, in reach (as the boat item's own check: the water's surface, a block in the way).
		Vec3 eye = this.drifter.getEyePosition();
		double reach = this.drifter.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
		var hit = level.clip(new ClipContext(eye, eye.add(this.drifter.getViewVector(1.0F).scale(reach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY,
			this.drifter));
		if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
			return this.fail("can't reach the water with the boat");
		}

		this.boatsBefore.clear();
		AABB around = new AABB(this.launch).inflate(3.0);
		for (Boat boat : level.getEntitiesOfClass(Boat.class, around)) {
			this.boatsBefore.add(boat.getId());
		}

		FakePlayerBridge.act(this.drifter, fake -> fake.gameMode.useItem(fake, fake.level(), fake.getMainHandItem(), InteractionHand.MAIN_HAND));
		this.drifter.swing(InteractionHand.MAIN_HAND);
		this.ticks = 0;
		for (Boat boat : level.getEntitiesOfClass(Boat.class, around, boat -> !this.boatsBefore.contains(boat.getId()) && !boat.isVehicle())) {
			this.boat = boat;
			this.phase = Phase.BOARD;
			this.think("put its boat on the water");
			return Status.RUNNING;
		}

		return Status.RUNNING;
	}

	/**
	 * Where on the water it puts the boat down: out from the bank far enough that the boat (wider than a block, sitting a
	 * little below the bank's top) doesn't catch on it, or the game won't let it down there.
	 */
	@Nullable
	private Vec3 placePoint() {
		Level level = this.drifter.level();
		Vec3 center = Vec3.atBottomCenterOf(this.launch);
		Vec3 out = new Vec3(center.x - this.drifter.getX(), 0.0, center.z - this.drifter.getZ());
		out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize();
		double reach = this.drifter.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) - 0.3;
		for (double along : new double[] {0.6, 1.1, 1.6, 0.2, 2.1}) {
			Vec3 at = center.add(out.scale(along));
			BlockPos cell = BlockPos.containing(at.x, this.launch.getY(), at.z);
			if (!level.getFluidState(cell).is(FluidTags.WATER)) {
				continue;
			}

			Vec3 point = new Vec3(at.x, cell.getY() + level.getFluidState(cell).getHeight(level, cell) - 0.02, at.z);
			if (point.distanceTo(this.drifter.getEyePosition()) > reach) {
				continue;
			}

			// The boat's footprint (1.375 across, a little more for its turning), clear of anything solid at the water's level.
			boolean room = true;
			for (double dx : new double[] {-0.75, 0.75}) {
				for (double dz : new double[] {-0.75, 0.75}) {
					for (int up = 0; up <= 1 && room; up++) {
						BlockPos corner = BlockPos.containing(point.x + dx, cell.getY() + up, point.z + dz);
						room = level.getBlockState(corner).getCollisionShape(level, corner).isEmpty();
					}
				}
			}

			if (room) {
				return point;
			}
		}

		return null;
	}

	/** In: in reach of the boat, a look at it, a right-click (Boat#interact: in, as a player gets in). */
	private Status board() {
		Boat boat = this.boat;
		if (boat == null || !boat.isAlive()) {
			return this.fail("the boat is gone");
		}

		double reach = this.drifter.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - 0.2;
		if (boat.getBoundingBox().distanceToSqr(this.drifter.getEyePosition()) > reach * reach) {
			// It drifted off a little: after it.
			if (this.walk == null) {
				this.walk = NavigateTask.follow(this.drifter, boat, 1.0);
			}

			if (this.walk.tick() == Status.FAILURE || ++this.ticks > 20 * 15) {
				return this.fail("couldn't get to the boat");
			}

			return Status.RUNNING;
		}

		if (this.walk != null) {
			this.walk.stop();
			this.walk = null;
			this.ticks = 0;
		}

		this.drifter.getKeys().releaseAll();
		this.drifter.getCamera().track(boat.getBoundingBox().getCenter(), CameraModel.TurnSpeed.NORMAL);
		if (++this.ticks < 5) {
			return Status.RUNNING;
		}

		if (boat.isVehicle() || this.ticks > 60) {
			return this.fail("couldn't get in the boat");
		}

		this.drifter.meansToRide(true);
		boolean in = this.drifter.startRiding(boat);
		this.drifter.meansToRide(false);
		if (in) {
			this.phase = Phase.ROW;
			this.ticks = 0;
		}

		return Status.RUNNING;
	}

	/** Where it's rowing to: the route's way out of the water, or on toward the place. */
	private Vec3 aim() {
		return this.landing != null ? Vec3.atBottomCenterOf(this.landing) : this.target;
	}

	private Status row() {
		Boat boat = this.boat;
		if (boat == null || !boat.isAlive()) {
			return this.fail("the boat is gone");
		}

		if (this.drifter.getVehicle() != boat) {
			this.trouble = "fell out of the boat";
			this.phase = Phase.BREAK;
			this.ticks = 0;
			return Status.RUNNING;
		}

		this.drifter.keepRiding();
		Vec3 here = boat.position();
		Vec3 aim = this.aim();
		double distance = Math.sqrt((aim.x - here.x) * (aim.x - here.x) + (aim.z - here.z) * (aim.z - here.z));
		float straight = yawTo(here, aim);
		// The bank ahead, where it means to get out (near its landing; past the end of its route, any bank in the way on).
		boolean nearLanding = this.landing == null || distance <= LAND_NEAR;
		if (nearLanding && (this.bankAhead(straight) || distance < 1.0)) {
			this.heading = straight;
			this.phase = Phase.LAND;
			this.ticks = 0;
			return Status.RUNNING;
		}

		// Its way on runs into a shore it can't get out on (a wall, a cliff): a bank off to one side will do, the side
		// nearer the way it's going first.
		if (nearLanding && !this.openWater(straight)) {
			for (float turn : new float[] {45.0F, -45.0F, 90.0F, -90.0F}) {
				if (this.bankAhead(straight + turn)) {
					this.heading = straight + turn;
					this.phase = Phase.LAND;
					this.ticks = 0;
					return Status.RUNNING;
				}
			}
		}

		if (distance < this.bestDistance - PROGRESS) {
			this.bestDistance = distance;
			this.sinceProgress = 0;
		} else if (++this.sinceProgress > STUCK_TICKS) {
			// Can't get on: out here (on a bank if there's one ahead, else on the boat, to swim from), and the boat back.
			this.trouble = "the boat can't get through this way";
			this.phase = Phase.LAND;
			this.ticks = 0;
			return Status.RUNNING;
		}

		this.steer(boat, here, straight);
		return Status.RUNNING;
	}

	private static float yawTo(Vec3 from, Vec3 to) {
		return (float)Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0F;
	}

	/** Toward the place, round anything the boat can't go through (land, a low bridge). */
	private void steer(Boat boat, Vec3 here, float straight) {
		VirtualKeys keys = this.drifter.getKeys();
		if (Float.isNaN(this.heading) || --this.detourTicks <= 0) {
			this.heading = straight;
		}

		if (!this.openWater(this.heading)) {
			float chosen = Float.NaN;
			for (float turn : new float[] {25.0F, -25.0F, 50.0F, -50.0F, 80.0F, -80.0F, 115.0F, -115.0F, 150.0F, -150.0F}) {
				if (this.openWater(straight + turn)) {
					chosen = straight + turn;
					break;
				}
			}

			if (Float.isNaN(chosen)) {
				keys.releaseAll();
				return;
			}

			this.heading = chosen;
			this.detourTicks = 25;
		}

		this.drifter.getCamera().lookAt(this.heading, 8.0F, CameraModel.TurnSpeed.NORMAL);
		// Pull round toward the heading, letting go early: a turning boat keeps turning a while.
		float turning = ((BoatAccessor)boat).drifter$deltaRotation();
		float off = Mth.wrapDegrees(this.heading - boat.getYRot()) - turning * TURN_CARRY;
		keys.set(VirtualKeys.Key.RIGHT, off > TURN_SLACK);
		keys.set(VirtualKeys.Key.LEFT, off < -TURN_SLACK);
		keys.set(VirtualKeys.Key.FORWARD, Math.abs(Mth.wrapDegrees(this.heading - boat.getYRot())) < 60.0F);
		keys.set(VirtualKeys.Key.BACK, false);
	}

	/** The water's surface cell the boat floats in. */
	private BlockPos waterline(Vec3 at) {
		return BlockPos.containing(at.x, this.boat == null ? at.y : this.boat.getY() + 0.2, at.z);
	}

	/** Open water that way for the next few blocks: water at the boat's level, room above it for the boat and its rower. */
	private boolean openWater(float yaw) {
		Boat boat = this.boat;
		if (boat == null) {
			return false;
		}

		Level level = boat.level();
		Vec3 direction = Vec3.directionFromRotation(0.0F, yaw);
		Vec3 side = new Vec3(-direction.z, 0.0, direction.x).scale(0.6);
		Vec3 here = boat.position();
		for (double out = 1.2; out <= 4.2; out += 1.0) {
			Vec3 ahead = here.add(direction.scale(out));
			for (Vec3 at : out < 2.0 ? new Vec3[] {ahead, ahead.add(side), ahead.subtract(side)} : new Vec3[] {ahead}) {
				BlockPos water = this.waterline(at);
				if (!level.getFluidState(water).is(FluidTags.WATER) || !this.roomAbove(level, water)) {
					return false;
				}
			}
		}

		return true;
	}

	/** Nothing over the water a boat would hit (a lily pad it just breaks). */
	private boolean roomAbove(Level level, BlockPos water) {
		for (int up = 1; up <= 2; up++) {
			BlockPos above = water.above(up);
			var state = level.getBlockState(above);
			if (!state.getCollisionShape(level, above).isEmpty() && !state.is(Blocks.LILY_PAD)) {
				return false;
			}
		}

		return true;
	}

	/** A bank that way, just ahead: not water at the boat's level within a couple of blocks. */
	private boolean bankAhead(float yaw) {
		Boat boat = this.boat;
		if (boat == null) {
			return false;
		}

		Level level = boat.level();
		Vec3 direction = Vec3.directionFromRotation(0.0F, yaw);
		for (double out = 1.0; out <= BANK_AHEAD; out += 0.4) {
			BlockPos at = this.waterline(boat.position().add(direction.scale(out)));
			if (!level.getFluidState(at).is(FluidTags.WATER)) {
				// Ground to stand on there (level with the water, or a step up), not a wall or a drop.
				boolean ground = !level.getBlockState(at).getCollisionShape(level, at).isEmpty()
					|| !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty();
				return ground && !level.getBlockState(at.above(2)).isCollisionShapeFullBlock(level, at.above(2));
			}
		}

		return false;
	}

	/** Letting the boat run up to the bank, facing it, then out (the boat lets it off the way it faces). */
	private Status land() {
		Boat boat = this.boat;
		if (boat == null || !boat.isAlive() || this.drifter.getVehicle() != boat) {
			this.phase = Phase.BREAK;
			this.ticks = 0;
			return Status.RUNNING;
		}

		this.drifter.keepRiding();
		this.drifter.getKeys().releaseAll();
		this.drifter.getCamera().lookAt(this.heading, 10.0F, CameraModel.TurnSpeed.NORMAL);
		double speed = boat.getDeltaMovement().horizontalDistance();
		if (++this.ticks < 6 || speed > 0.06 && this.ticks < 30) {
			return Status.RUNNING;
		}

		this.drifter.stopRiding();
		if (this.trouble == null) {
			this.think("rowed across the water");
		}

		this.phase = Phase.BREAK;
		this.ticks = 0;
		return Status.RUNNING;
	}

	/** A few punches (or one blow with an axe or a sword in hand) and the boat breaks into its item. */
	private Status breakBoat() {
		Boat boat = this.boat;
		this.drifter.getKeys().releaseAll();
		if (boat == null || !boat.isAlive()) {
			this.boatsCarried = this.drifter.getInventory().count(BoatTask::isBoat);
			this.phase = Phase.PICK_UP;
			this.ticks = 0;
			return Status.RUNNING;
		}

		this.boatLeftAt = boat.position();
		this.drifter.getCamera().track(boat.getBoundingBox().getCenter(), CameraModel.TurnSpeed.NORMAL);
		if (++this.ticks > 20 * 8) {
			return this.fail("couldn't break the boat");
		}

		if (this.ticks >= 4 && Combat.attackStrength(this.drifter) >= 1.0F) {
			Combat.attack(this.drifter, boat);
		}

		return Status.RUNNING;
	}

	/** The boat's item off the water (it floats where the boat was): picked up as it passes, or a step or two over to it. */
	private Status pickUp() {
		VirtualKeys keys = this.drifter.getKeys();
		if (this.drifter.getInventory().count(BoatTask::isBoat) > this.boatsCarried) {
			keys.releaseAll();
			return this.trouble == null ? Status.SUCCESS : this.fail(this.trouble);
		}

		ItemEntity item = this.boatItem();
		if (item == null || ++this.ticks > PICK_UP_TICKS) {
			keys.releaseAll();
			this.think("lost its boat");
			return this.trouble == null ? Status.SUCCESS : this.fail(this.trouble);
		}

		this.drifter.getCamera().track(item.position(), CameraModel.TurnSpeed.NORMAL);
		double dx = item.getX() - this.drifter.getX();
		double dz = item.getZ() - this.drifter.getZ();
		boolean far = dx * dx + dz * dz > 0.8 * 0.8;
		keys.set(VirtualKeys.Key.FORWARD, far && this.ticks > 10);
		keys.set(VirtualKeys.Key.JUMP, this.drifter.isInWater());
		return Status.RUNNING;
	}

	/** Its boat's item, where the boat was. */
	@Nullable
	private ItemEntity boatItem() {
		if (this.boatLeftAt == null) {
			return null;
		}

		for (ItemEntity item : this.drifter.level().getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(this.boatLeftAt)).inflate(4.0),
			item -> isBoat(item.getItem()))) {
			return item;
		}

		return null;
	}

	private void think(String thought) {
		this.drifter.getMemory().thoughts().add(this.drifter.level().getGameTime(), thought);
	}

	@Override
	protected void onStop() {
		if (this.select != null) {
			this.select.stop();
		}

		if (this.walk != null) {
			this.walk.stop();
		}

		this.drifter.getKeys().releaseAll();
		// (Cut short in the boat: it gets out, NavigateTask's own way on from there; the boat stays where it is.)
		if (this.boat != null && this.drifter.getVehicle() == this.boat) {
			this.drifter.stopRiding();
		}
	}
}
