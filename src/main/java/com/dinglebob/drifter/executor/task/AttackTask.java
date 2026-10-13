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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Attack (DESIGN.md sections 8 and 13): chases a living target and fights it until it dies, the way a player who
 * knows 1.9+ combat does:
 * <ul>
 *   <li>takes out its best weapon first (most damage per second);</li>
 *   <li>never spam-clicks: swings only at full attack strength (the cooldown comes from the weapon's attack speed);</li>
 *   <li>critical hits (1.5x damage) with slow weapons: jump as the cooldown is about to refill, let go of sprint in
 *       the air (a sprinting hit can't crit), hit on the way down;</li>
 *   <li>a plain full-strength hit when a jump isn't possible or with a fast item (fist, block);</li>
 *   <li>per-mob protocols (section 13): animals are just chased; melee mobs get hit, then it steps back out of reach
 *       while the cooldown refills, strafing, shield up; archers are approached in a zigzag; creepers get one
 *       sprint-knockback hit, then it backs well off before they can blow.</li>
 * </ul>
 * Far away or out of sight it follows with the pathfinder; close up it steers with the keys, camera on the target.
 * Hits only land under the crosshair within 3 blocks (player reach).
 * TODO(milestone 7): fighting styles (brawler, archer, turtle, coward) and Skill-scaled timing errors.
 */
public class AttackTask extends Task {
	/** How it fights this target (section 13, "Per-mob protocols"). */
	public enum Protocol {
		/** An animal: chase and hit. */
		PREY,
		/** A melee mob: hit, step back out of reach while the cooldown refills, strafe; shield up while backing off. */
		MELEE,
		/** An archer: zigzag in (shield up while it draws), then melee. */
		RANGED,
		/** A creeper: one sprint-knockback hit, then back well off before it can blow; repeat. */
		CREEPER;

		public static Protocol of(LivingEntity target) {
			if (target instanceof Creeper) {
				return CREEPER;
			}

			if (target instanceof RangedAttackMob) {
				return RANGED;
			}

			return target instanceof Enemy || target instanceof Mob mob && mob.isAggressive() ? MELEE : PREY;
		}
	}

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
	/** Melee spacing: it hovers between these distances while the cooldown refills (section 13, "Spacing"). */
	private static final double HOVER_MIN = 2.4;
	private static final double HOVER_MAX = 3.3;
	/** It steps in to hit once the cooldown is this full. */
	private static final float STEP_IN_STRENGTH = 0.6F;
	/** Ticks it backs off after a hit (creepers: until well clear of the blast). */
	private static final int BACKOFF_TICKS = 7;
	private static final int CREEPER_BACKOFF_TICKS = 30;
	private static final double CREEPER_SAFE_DISTANCE = 5.0;
	/** Strafe direction changes every 0.5 to 2 s (section 13, "Strafing"); zigzag legs toward archers. */
	private static final int STRAFE_MIN_TICKS = 10;
	private static final int STRAFE_MAX_TICKS = 40;
	private static final int ZIGZAG_MIN_TICKS = 8;
	private static final int ZIGZAG_MAX_TICKS = 14;

	private final LivingEntity target;
	private final String label;
	private final Protocol protocol;
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
	private int backoff;
	private int strafeTicks;
	private boolean strafeLeft;

	public AttackTask(DrifterEntity drifter, LivingEntity target, String label) {
		super(drifter);
		this.target = target;
		this.label = label;
		this.protocol = Protocol.of(target);
	}

	@Override
	public String describe() {
		return "attack the " + this.label;
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60;
	}

	public LivingEntity target() {
		return this.target;
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
		if ((distance > CLOSE_RANGE || !visible) && this.backoff == 0) {
			this.lowerShield();
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
		boolean fightingMob = this.protocol != Protocol.PREY;

		// A creeper starting to hiss: get clear first (the reflex layer does the sprint; this keeps it from stepping back in).
		if (this.protocol == Protocol.CREEPER && this.target instanceof Creeper creeper && creeper.getSwellDir() > 0) {
			this.backoff = Math.max(this.backoff, CREEPER_BACKOFF_TICKS);
		}

		if (fightingMob && this.backoff > 0) {
			this.backOff(distance);
			return;
		}

		if (--this.strafeTicks <= 0) {
			this.strafeLeft = this.drifter.getRandom().nextBoolean();
			this.strafeTicks = this.protocol == Protocol.RANGED && distance > HOVER_MAX
				? Mth.nextInt(this.drifter.getRandom(), ZIGZAG_MIN_TICKS, ZIGZAG_MAX_TICKS)
				: Mth.nextInt(this.drifter.getRandom(), STRAFE_MIN_TICKS, STRAFE_MAX_TICKS);
		}

		boolean stepIn = !fightingMob || strength >= STEP_IN_STRENGTH;
		if (!stepIn) {
			// Waiting for the cooldown: hover at the edge of reach, circling.
			if (distance < HOVER_MIN) {
				Steering.pressToward(this.drifter, this.drifter.position().subtract(at).scale(2.0).add(at));
			} else if (distance > HOVER_MAX) {
				Steering.pressToward(this.drifter, at);
			} else {
				Steering.stopMoving(this.drifter);
			}

			this.strafe(keys);
			keys.set(VirtualKeys.Key.SPRINT, false);
			keys.set(VirtualKeys.Key.JUMP, false);
			// Shield up while an archer draws or a mob is about to swing in reach.
			this.shieldIf(this.threatening(distance));
			return;
		}

		this.lowerShield();
		if (distance > STOP_RANGE) {
			Steering.pressToward(this.drifter, at);
			if (this.protocol == Protocol.RANGED && distance > HOVER_MAX) {
				// Zigzag in toward an archer.
				this.strafe(keys);
			}
		} else {
			Steering.stopMoving(this.drifter);
		}

		if (onGround) {
			if (this.critJump) {
				// Landed without getting the crit in: take the next plain hit instead of jumping forever.
				this.critJump = false;
				this.missedCrit = true;
			}

			// Sprint in to close the gap (and, at a creeper, for the knockback hit); it lets go of sprint in the air for crits.
			boolean sprint = keys.isDown(VirtualKeys.Key.FORWARD) && (horizontal > JUMP_RANGE || this.protocol == Protocol.CREEPER)
				&& this.drifter.getFoodData().getFoodLevel() > FoodConstants.SPRINT_LEVEL;
			keys.set(VirtualKeys.Key.SPRINT, sprint);
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

	/** After a hit: step back out of reach while the cooldown refills (shield up), strafing; far back from a creeper. */
	private void backOff(double distance) {
		VirtualKeys keys = this.drifter.getKeys();
		this.backoff--;
		if (this.protocol == Protocol.CREEPER && distance < CREEPER_SAFE_DISTANCE) {
			this.backoff = Math.max(this.backoff, 1);
		}

		Vec3 at = this.target.position();
		Steering.pressToward(this.drifter, this.drifter.position().subtract(at).scale(2.0).add(at));
		this.strafe(keys);
		keys.set(VirtualKeys.Key.SPRINT, false);
		keys.set(VirtualKeys.Key.JUMP, this.drifter.horizontalCollision && this.drifter.onGround());
		this.shieldIf(distance < 4.0);
	}

	private void strafe(VirtualKeys keys) {
		keys.set(VirtualKeys.Key.LEFT, this.strafeLeft);
		keys.set(VirtualKeys.Key.RIGHT, !this.strafeLeft);
	}

	/** An archer drawing its bow, or a mob close enough to swing at it. */
	private boolean threatening(double distance) {
		return this.protocol == Protocol.RANGED && this.target.isUsingItem() || distance < 3.0;
	}

	private boolean hasShield() {
		return this.drifter.getOffhandItem().is(Items.SHIELD);
	}

	private void shieldIf(boolean raise) {
		if (raise && this.hasShield()) {
			if (!this.drifter.isUsingItem()) {
				this.drifter.startUsingItem(InteractionHand.OFF_HAND);
			}
		} else {
			this.lowerShield();
		}
	}

	private void lowerShield() {
		if (this.drifter.isUsingItem() && this.drifter.getUseItem().is(Items.SHIELD)) {
			this.drifter.stopUsingItem();
		}
	}

	private void hit() {
		if (Combat.attack(this.drifter, this.target)) {
			this.critJump = false;
			this.missedCrit = false;
			if (this.protocol != Protocol.PREY) {
				this.backoff = this.protocol == Protocol.CREEPER ? CREEPER_BACKOFF_TICKS : BACKOFF_TICKS + this.drifter.getRandom().nextInt(4);
				this.strafeLeft = this.drifter.getRandom().nextBoolean();
			}
		}
	}

	/** Worth jumping for a crit: a slow enough weapon, room to jump, not in water or on a ladder, not at a creeper. */
	private boolean canJump() {
		if (this.protocol == Protocol.CREEPER || Combat.attackDelay(this.drifter) < CRIT_MIN_DELAY_TICKS) {
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

		this.lowerShield();
		this.drifter.getKeys().releaseAll();
		this.drifter.getCamera().clearTarget();
	}
}
