package com.dinglebob.drifter.executor.task;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.Combat;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.Steering;
import com.dinglebob.drifter.motor.VirtualKeys;
import com.dinglebob.drifter.perception.Awareness;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Attack (DESIGN.md section 8): chases a living target and hits it until it dies, the way a player who knows 1.9+
 * combat does (section 3, "Weapons and timing"):
 * <ul>
 *   <li>takes out its best weapon first (most damage per second);</li>
 *   <li>never spam-clicks: swings only at full attack strength (the cooldown comes from the weapon's attack speed);</li>
 *   <li>critical hits (1.5x damage): close in, jump as the cooldown is about to refill, let go of sprint in the air
 *       (a sprinting hit can't crit), and hit on the way down, as vanilla requires: full strength, falling, off the
 *       ground, not sprinting, not in water or on a ladder;</li>
 *   <li>a plain full-strength hit when a jump isn't possible (low ceiling, water), when a crit attempt missed, or with a
 *       fast item (fist, block) where plain hits do more damage per second than jumping for crits.</li>
 * </ul>
 * Far away or out of sight it follows with the pathfinder; close up it steers straight at it with the camera on it.
 * Hits only land under the crosshair within 3 blocks (player reach). TODO(milestone 6): strafing, shields, retreating,
 * per-mob protocols.
 */
public class AttackTask extends Task {
	/** Closer than this (and in sight) it steers by hand instead of pathfinding. */
	private static final double CLOSE_RANGE = 5.0;
	/** It stops pressing forward this close, so it doesn't run into the target. */
	private static final double STOP_RANGE = 1.8;
	/** It jumps for a crit this close (horizontal, feet to the target's middle), so it's still in reach coming down. */
	private static final double JUMP_RANGE = 2.6;
	/** It jumps this many ticks before the cooldown is full: about the time from take-off to falling past the top. */
	private static final float JUMP_LEAD_TICKS = 7.0F;
	/**
	 * Crits only pay off with slow weapons: a jump takes about 12 ticks, so with a fist or a block (full strength every
	 * 5 ticks) plain hits do more damage per second. A sword (12.5 ticks) or axe (25) crits every jump.
	 */
	private static final float CRIT_MIN_DELAY_TICKS = 10.0F;
	/** Lost from sight this long, it gives up. */
	private static final int LOST_TICKS = 20 * 6;
	/** Vanilla: a hit stronger than 0.9 counts as full strength (crits and sweeps need it). */
	private static final float FULL_STRENGTH = 0.92F;

	private final LivingEntity target;
	private final String label;
	@Nullable
	private NavigateTask follow;
	@Nullable
	private SelectItemTask selectWeapon;
	private boolean weaponChecked;
	private int unseenTicks;
	private boolean closeMode;
	/** A crit jump is under way; if it lands without hitting, the next hit is a plain one. */
	private boolean critJump;
	private boolean missedCrit;

	public AttackTask(DrifterEntity drifter, LivingEntity target, String label) {
		super(drifter);
		this.target = target;
		this.label = label;
	}

	@Override
	public String describe() {
		return "attack the " + this.label;
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 40;
	}

	@Override
	protected Status onTick() {
		if (!this.target.isAlive() || this.target.isRemoved()) {
			return Status.SUCCESS;
		}

		// Weapon first: like a player switching to the sword before a fight.
		if (!this.weaponChecked) {
			Status selecting = this.selectWeapon();
			if (selecting != null) {
				return selecting;
			}
		}

		Awareness awareness = this.drifter.getSenses().awarenessOf(this.target);
		boolean visible = awareness != null && awareness.visibleNow;
		this.unseenTicks = visible ? 0 : this.unseenTicks + 1;
		if (this.unseenTicks > LOST_TICKS) {
			return this.fail("lost sight of it");
		}

		double distance = this.drifter.getEyePosition().distanceTo(this.target.getBoundingBox().getCenter());
		if (distance > CLOSE_RANGE || !visible) {
			if (this.closeMode || this.follow == null) {
				this.closeMode = false;
				this.follow = NavigateTask.follow(this.drifter, this.target, 1.5);
			}

			Status status = this.follow.tick();
			if (status == Status.FAILURE) {
				return this.fail("can't get to it: " + this.follow.failureReason());
			}

			return Status.RUNNING;
		}

		if (!this.closeMode) {
			this.closeMode = true;
			if (this.follow != null) {
				this.follow.stop();
				this.follow = null;
			}

			this.drifter.getCamera().lookAt(this.target, CameraModel.TurnSpeed.COMBAT);
		}

		this.fight(distance);
		return Status.RUNNING;
	}

	/** Returns a status while still switching to its weapon, or null once it holds it. */
	@Nullable
	private Status selectWeapon() {
		if (this.selectWeapon == null) {
			int slot = Combat.bestWeaponSlot(this.drifter);
			ItemStack weapon = slot < 0 ? ItemStack.EMPTY : this.drifter.getInventory().getItem(slot);
			if (weapon.isEmpty() || weapon == this.drifter.getMainHandItem()) {
				this.weaponChecked = true;
				return null;
			}

			this.selectWeapon = new SelectItemTask(this.drifter, stack -> stack == weapon, weapon.getHoverName().getString());
		}

		Status status = this.selectWeapon.tick();
		if (status == Status.RUNNING) {
			return Status.RUNNING;
		}

		// Got it out (or couldn't: it fights with what it holds).
		this.selectWeapon.stop();
		this.selectWeapon = null;
		this.weaponChecked = true;
		return null;
	}

	private void fight(double distance) {
		VirtualKeys keys = this.drifter.getKeys();
		Vec3 at = this.target.position();
		double dx = at.x - this.drifter.getX();
		double dz = at.z - this.drifter.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		boolean onGround = this.drifter.onGround();
		float strength = Combat.attackStrength(this.drifter);
		float ticksToFull = (1.0F - strength) * Combat.attackDelay(this.drifter);

		if (distance > STOP_RANGE) {
			Steering.pressToward(this.drifter, at);
		} else {
			Steering.stopMoving(this.drifter);
		}

		if (onGround) {
			if (this.critJump) {
				// Landed without getting the crit in: take the next plain hit instead of jumping forever.
				this.critJump = false;
				this.missedCrit = true;
			}

			// Sprint in to close the gap; it lets go of sprint in the air for the crit.
			keys.set(VirtualKeys.Key.SPRINT, keys.isDown(VirtualKeys.Key.FORWARD) && horizontal > JUMP_RANGE
				&& this.drifter.getFoodData().getFoodLevel() > FoodConstants.SPRINT_LEVEL);
			boolean jump = !this.missedCrit && this.canJump() && horizontal <= JUMP_RANGE && ticksToFull <= JUMP_LEAD_TICKS;
			keys.set(VirtualKeys.Key.JUMP, jump || this.drifter.horizontalCollision);
			if (jump) {
				this.critJump = true;
				return;
			}

			// A plain hit when it can't (or failed to) crit, at full strength only.
			if ((this.missedCrit || !this.canJump()) && strength >= FULL_STRENGTH && Combat.canHit(this.drifter, this.target)) {
				this.hit();
			}

			return;
		}

		// In the air: no sprinting (a sprint hit can't crit). Letting go of forward for a tick ends the sprint, as for a
		// player; it keeps its momentum.
		keys.set(VirtualKeys.Key.JUMP, false);
		keys.set(VirtualKeys.Key.SPRINT, false);
		if (this.drifter.isSprinting()) {
			Steering.stopMoving(this.drifter);
		}

		boolean falling = this.drifter.fallDistance > 0.0F && this.drifter.getDeltaMovement().y < 0.0;
		if (falling && !this.drifter.isSprinting() && strength >= FULL_STRENGTH && Combat.canHit(this.drifter, this.target)) {
			this.hit();
		}
	}

	private void hit() {
		if (Combat.attack(this.drifter, this.target)) {
			this.critJump = false;
			this.missedCrit = false;
		}
	}

	/** Worth jumping for a crit: a slow enough weapon, room to jump, not in water or on a ladder. */
	private boolean canJump() {
		if (Combat.attackDelay(this.drifter) < CRIT_MIN_DELAY_TICKS) {
			return false;
		}

		Level level = this.drifter.level();
		BlockPos overhead = this.drifter.blockPosition().above(2);
		return level.getBlockState(overhead).getCollisionShape(level, overhead).isEmpty() && !this.drifter.isInWater()
			&& !this.drifter.onClimbable();
	}

	@Override
	protected void onStop() {
		if (this.follow != null) {
			this.follow.stop();
			this.follow = null;
		}

		if (this.selectWeapon != null) {
			this.selectWeapon.stop();
			this.selectWeapon = null;
		}

		this.drifter.getKeys().releaseAll();
		this.drifter.getCamera().clearTarget();
	}
}
