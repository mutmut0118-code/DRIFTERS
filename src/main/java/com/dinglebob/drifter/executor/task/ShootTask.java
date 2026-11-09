package com.dinglebob.drifter.executor.task;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.Archery;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.Steering;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Shoots a target with a bow, as a player does (DESIGN.md section 13 "Ranged mechanics"): takes out the bow, draws
 * (holding use) for a full second unless the target is close, aims with its head where the target will be when the
 * arrow gets there, a little high for the drop, and lets go once the crosshair is on that point. Aim error shrinks with
 * Skill and grows with distance and how fast the target moves. It stands its ground while it draws; if the target comes
 * close it puts the bow away (the fight goes on with the sword). A crossbow (any mod's) is loaded, then aimed and fired
 * the same way ({@link #crossbow}). A launcher that never looses is given up on for a while (DeadLaunchers, ruling R23).
 */
public class ShootTask extends Task {
	/** A target closer than this: the bow away, melee instead. */
	private static final double TOO_CLOSE = 4.0;
	/** A shorter draw when the target is this close (less power, but it's quicker). */
	private static final double QUICK_DRAW_RANGE = 10.0;
	private static final int QUICK_DRAW_TICKS = 12;
	/** Out of sight this long: it gives up. */
	private static final int LOST_TICKS = 20 * 4;
	/** Out of sight this long with another enemy in sight: it turns to that one. */
	private static final int SWITCH_TICKS = 20;
	/** Close enough to the aim point to let go (degrees). */
	private static final float AIMED_DEGREES = 2.5F;

	private final LivingEntity target;
	private final String label;
	@Nullable
	private SelectItemTask select;
	private int unseen;
	/** This draw's aim error, rolled once per shot (a player's shot is off by a steady amount, not jitter). */
	private Vec3 error = Vec3.ZERO;
	private int shots;

	public ShootTask(DrifterEntity drifter, LivingEntity target, String label) {
		super(drifter);
		this.target = target;
		this.label = label;
	}

	@Override
	public String describe() {
		return "shoot the " + this.label;
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 30;
	}

	@Override
	protected Status onTick() {
		this.checkLoosed();
		if (!this.target.isAlive()) {
			this.release(false);
			return Status.SUCCESS;
		}

		boolean ammo = !Archery.ammo(this.drifter).isEmpty();
		if (!Archery.ready(this.drifter, ammo)) {
			this.release(false);
			return this.fail(ammo ? "nothing to shoot with" : "no arrows left");
		}

		// Something else is hitting it (a zombie behind it while it aims at a creeper): the bow goes, it turns to that.
		net.minecraft.world.entity.LivingEntity hitBy = this.drifter.getLastHurtByMob();
		if (hitBy != null && hitBy != this.target && hitBy.isAlive() && this.drifter.tickCount - this.drifter.getLastHurtByMobTimestamp() < 10
			&& hitBy.distanceTo(this.drifter) < TOO_CLOSE) {
			this.release(false);
			return this.fail("something else is hitting it");
		}

		if (this.drifter.distanceTo(this.target) < TOO_CLOSE && !Archery.flying(this.target)) {
			this.release(false);
			return this.fail("it came too close for the bow");
		}

		// A blaze lit up is about to fire its three fireballs (it glows and smokes for three seconds first, then cools for
		// five): like a player it puts the bow down and holds its shield up until the burst is over, and shoots in between
		// (found in testing: drawing on blazes, it took every burst, and a fireball seen coming is too late to block).
		// (And a little after it stops glowing: the glow goes out as the last fireball leaves, still on its way.)
		long now = this.drifter.level().getGameTime();
		if (com.dinglebob.drifter.hands.Shields.isShield(this.drifter.getOffhandItem()) && Archery.glowing(this.drifter, this.target)) {
			this.guardUntil = now + Archery.BLAZE_GUARD_AFTER_TICKS;
		}

		if (com.dinglebob.drifter.hands.Shields.isShield(this.drifter.getOffhandItem()) && now < this.guardUntil) {
			this.guard();
			return Status.RUNNING;
		}

		if (this.guarding) {
			this.guarding = false;
			if (this.drifter.isUsingItem() && this.drifter.getUsedItemHand() == InteractionHand.OFF_HAND) {
				this.drifter.stopUsingItem();
			}
		}

		// The bow or crossbow in hand first (another one if the one in hand may not be used here and a better one is carried,
		// or if it can't shoot the one in hand: no arrows for it, or one it gave up on).
		if (!Archery.canShoot(this.drifter, this.drifter.getMainHandItem(), ammo) || this.select == null && this.betterBow() || this.swapping) {
			if (this.select == null) {
				int best = com.dinglebob.drifter.hands.Shields.bestBowSlot(this.drifter, this.target);
				if (best < 0) {
					this.release(false);
					return this.fail("nothing to shoot with");
				}

				net.minecraft.world.item.ItemStack bow = this.drifter.getInventory().getItem(best);
				this.select = new SelectItemTask(this.drifter, stack -> stack == bow, "the bow");
				// (From one launcher to another: the hand holds one all along, so it waits for the swap itself.)
				this.swapping = com.dinglebob.drifter.hands.Shields.isLauncher(this.drifter.getMainHandItem());
			}

			Status status = this.select.tick();
			if (status == Status.FAILURE) {
				return this.fail("can't take out the bow");
			}

			if (status == Status.SUCCESS) {
				this.swapping = false;
				// (Done: a new choice if the one in hand can't be shot later on.)
				this.select = null;
			}

			return Status.RUNNING;
		}

		// Only what it can see (section 1).
		boolean seen = this.drifter.getSenses().tracked().stream().anyMatch(a -> a.entity == this.target && a.visibleNow);
		if (!seen) {
			// Another one in sight: it switches after a second, as a player does, instead of standing with its bow lowered
			// waiting for this one (found in testing: four seconds at a time, while the others shot it).
			boolean another = this.drifter.getSenses().tracked().stream().anyMatch(a -> a.entity != this.target && a.visibleNow
				&& a.entity instanceof net.minecraft.world.entity.monster.Enemy && a.entity.isAlive());
			if (++this.unseen > (another ? SWITCH_TICKS : LOST_TICKS)) {
				this.release(false);
				return this.fail("lost sight of it");
			}

			return Status.RUNNING;
		}

		this.unseen = 0;
		Steering.stopMoving(this.drifter);
		// A friend in the line of fire: it holds the draw and steps aside for a clear shot (slowly, as with a bow drawn).
		if (this.blockedTicks > 0) {
			if (this.blockedTicks > MAX_BLOCKED_TICKS) {
				this.release(false);
				return this.fail("friends in the way");
			}

			this.sidestep();
		}

		net.minecraft.world.item.ItemStack held = this.drifter.getMainHandItem();
		boolean crossbow = held.getItem() instanceof CrossbowItem;
		Vec3 aim = this.aimPoint(crossbow ? Archery.CROSSBOW_ARROW_SPEED : Archery.ARROW_SPEED).add(this.error);
		this.drifter.getCamera().track(aim, CameraModel.TurnSpeed.COMBAT);
		if (crossbow) {
			return this.crossbow(held, aim);
		}

		if (!this.drifter.isUsingItem()) {
			// Not a draw for a shot it won't take (gear powers); the check before letting go stays, for a friend walking in.
			if (this.heldFire(this.drifter.getMainHandItem())) {
				return this.fail("held fire");
			}

			this.drifter.startUsingItem(InteractionHand.MAIN_HAND);
			this.error = this.rollError();
			return Status.RUNNING;
		}

		int wanted = this.drifter.distanceTo(this.target) < QUICK_DRAW_RANGE && !Archery.flying(this.target) ? QUICK_DRAW_TICKS : Archery.FULL_DRAW_TICKS;
		if (this.drifter.getTicksUsingItem() >= wanted && this.aimedAt(aim, this.drifter.getTicksUsingItem() - wanted)) {
			// Its bow's power, as it believes it, would hurt a friend, itself or its home: it lowers the bow (gear powers).
			if (this.heldFire(this.drifter.getUseItem())) {
				this.release(false);
				return this.fail("held fire");
			}

			if (Archery.friendInTheWay(this.drifter, this.drifter.getViewVector(1.0F), net.minecraft.world.item.BowItem.getPowerForTime(this.drifter.getTicksUsingItem()),
				this.drifter.distanceTo(this.target))) {
				this.blockedTicks++;
			} else {
				this.blockedTicks = 0;
				net.minecraft.world.item.ItemStack bow = this.drifter.getUseItem();
				this.watchLoose(bow);
				this.release(true);
				this.checkLoosed();
				this.shots++;
			}
		}

		return Status.RUNNING;
	}

	/** Ticks it has aimed its loaded crossbow (it fires on the aim point, or a little off it the longer it aims). */
	private int aimTicks;

	/**
	 * A crossbow, as a player uses one (CrossbowItem): unloaded, it holds use for the charge time and lets go, which loads
	 * it from its arrows ({@code DrifterEntity#getProjectile}); loaded, it aims and fires at once on the aim point. A crossbow
	 * carried loaded stays loaded until it fires (the next fight's first shot needs no load).
	 */
	private Status crossbow(net.minecraft.world.item.ItemStack crossbow, Vec3 aim) {
		if (!CrossbowItem.isCharged(crossbow)) {
			this.aimTicks = 0;
			if (!this.drifter.isUsingItem()) {
				// Not loading for a shot it won't take (gear powers).
				if (this.heldFire(crossbow)) {
					return this.fail("held fire");
				}

				this.drifter.startUsingItem(InteractionHand.MAIN_HAND);
				return Status.RUNNING;
			}

			if (this.drifter.getTicksUsingItem() >= CrossbowItem.getChargeDuration(crossbow, this.drifter)) {
				this.drifter.releaseUsingItem();
				if (CrossbowItem.isCharged(crossbow)) {
					this.drifter.getMemory().thoughts().add(this.drifter.level().getGameTime(), "loaded its " + crossbow.getHoverName().getString());
				} else {
					// Held for the full charge with arrows to hand and nothing loaded: the item doesn't work for it (R23).
					this.drifter.getDeadLaunchers().failed(this.drifter, crossbow);
				}
			}

			return Status.RUNNING;
		}

		if (this.aimTicks++ == 0) {
			this.error = this.rollError();
		}

		if (!this.aimedAt(aim, this.aimTicks)) {
			return Status.RUNNING;
		}

		// Its crossbow's power, as it believes it, would hurt a friend, itself or its home: it keeps the shot (gear powers).
		if (this.heldFire(crossbow)) {
			return this.fail("held fire");
		}

		if (Archery.friendInTheWay(this.drifter, this.drifter.getViewVector(1.0F), (float)(Archery.CROSSBOW_ARROW_SPEED / Archery.ARROW_SPEED),
			this.drifter.distanceTo(this.target))) {
			this.blockedTicks++;
			return Status.RUNNING;
		}

		this.blockedTicks = 0;
		net.minecraft.world.item.component.ChargedProjectiles loaded = crossbow.get(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES);
		double speed = loaded != null && loaded.contains(Items.FIREWORK_ROCKET) ? Archery.CROSSBOW_FIREWORK_SPEED : Archery.CROSSBOW_ARROW_SPEED;
		this.watchLoose(crossbow);
		this.drifter.fireCrossbow(crossbow, (float)speed);
		this.checkLoosed();
		// What follows the shot may show what the crossbow does (its bolts are in the air now).
		com.dinglebob.drifter.gear.PowerWatch.start(this.drifter, crossbow, this.target, com.dinglebob.drifter.gear.PowerWatch.Use.SHOT);
		this.aimTicks = 0;
		this.shots++;
		return Status.RUNNING;
	}

	/** The launcher it just let go of, the tick it did, its wear and its target's health then (null: no shot being watched for). */
	@Nullable
	private net.minecraft.world.item.ItemStack loosing;
	private long loosingAt;
	private int loosingWear;
	private float loosingTargetHealth;

	/** It's letting go of this launcher now: was anything loosed? (DeadLaunchers, ruling R23.) */
	private void watchLoose(net.minecraft.world.item.ItemStack launcher) {
		this.loosing = launcher;
		this.loosingAt = this.drifter.level().getGameTime();
		this.loosingWear = launcher.getDamageValue();
		this.loosingTargetHealth = this.target.getHealth();
	}

	/**
	 * A projectile of its own came into the world since it let go, the launcher wore (as one does per shot) or its target
	 * was hurt (a bow that strikes with a beam, say): it worked. Nothing within {@link com.dinglebob.drifter.hands.DeadLaunchers#LOOSE_WINDOW_TICKS}: one against that item.
	 */
	private void checkLoosed() {
		if (this.loosing == null) {
			return;
		}

		var dead = this.drifter.getDeadLaunchers();
		if (dead.loosedSince(this.loosingAt) || this.loosing.getDamageValue() != this.loosingWear || this.target.getHealth() < this.loosingTargetHealth) {
			dead.worked(this.loosing);
			this.loosing = null;
		} else if (this.drifter.level().getGameTime() - this.loosingAt >= com.dinglebob.drifter.hands.DeadLaunchers.LOOSE_WINDOW_TICKS) {
			dead.failed(this.drifter, this.loosing);
			this.loosing = null;
		}
	}

	/** This bow's power, as it believes it, would hurt a friend, itself or its home on this target: it says so to itself. */
	private boolean heldFire(net.minecraft.world.item.ItemStack bow) {
		String why = com.dinglebob.drifter.gear.Powers.whyNot(this.drifter, bow, this.target);
		if (why == null) {
			return false;
		}

		this.drifter.getMemory().thoughts().add(this.drifter.level().getGameTime(), "held fire: " + bow.getHoverName().getString() + " "
			+ com.dinglebob.drifter.gear.Powers.heldFireWords(this.drifter, bow, why));
		return true;
	}

	/** The bow in hand was looked at once: no second swap in this task. */
	private boolean betterChecked;
	/** Changing from the bow in hand to a better one for this target. */
	private boolean swapping;

	/** The bow in hand may not be used on this target and the best one for it is another (once per task). */
	private boolean betterBow() {
		if (this.betterChecked) {
			return false;
		}

		this.betterChecked = true;
		net.minecraft.world.item.ItemStack held = this.drifter.getMainHandItem();
		if (com.dinglebob.drifter.gear.Powers.mayUse(this.drifter, held, this.target)) {
			return false;
		}

		int best = com.dinglebob.drifter.hands.Shields.bestBowSlot(this.drifter, this.target);
		return best >= 0 && this.drifter.getInventory().getItem(best) != held;
	}

	/** Ticks in a row a friend has stood in its line of fire with the bow drawn. */
	private int blockedTicks;
	/** This long with no clear shot: it gives up the bow for now. */
	private static final int MAX_BLOCKED_TICKS = 20 * 5;
	/** Steps one way this long before trying the other. */
	private static final int SIDESTEP_TICKS = 30;
	private boolean sidestepLeft;
	private int sidestepFor;

	/** A step to one side, safe ground only; the other way after a while or on bumping into something. */
	private void sidestep() {
		Vec3 side = Vec3.directionFromRotation(0.0F, this.drifter.getYRot() + (this.sidestepLeft ? -90.0F : 90.0F));
		if (--this.sidestepFor <= 0 || this.drifter.horizontalCollision || !Steering.safeStep(this.drifter, side)) {
			this.sidestepLeft = this.sidestepFor > 0 ? !this.sidestepLeft : this.drifter.getRandom().nextBoolean();
			this.sidestepFor = SIDESTEP_TICKS;
			side = Vec3.directionFromRotation(0.0F, this.drifter.getYRot() + (this.sidestepLeft ? -90.0F : 90.0F));
			if (!Steering.safeStep(this.drifter, side)) {
				return;
			}
		}

		var keys = this.drifter.getKeys();
		keys.set(com.dinglebob.drifter.motor.VirtualKeys.Key.LEFT, this.sidestepLeft);
		keys.set(com.dinglebob.drifter.motor.VirtualKeys.Key.RIGHT, !this.sidestepLeft);
	}

	private boolean guarding;
	private long guardUntil;

	/** A blaze it can see now, near enough to hit it, glowing to fire. */
	private boolean chargedBlazeInSight() {
		return Archery.chargedBlazeInSight(this.drifter, GUARD_RANGE);
	}

	/** Shield up toward the target: the bow goes (main hand first on right-click), the sword comes out, then it blocks. */
	private void guard() {
		this.guarding = true;
		Steering.stopMoving(this.drifter);
		this.drifter.getCamera().track(this.target.getEyePosition(), CameraModel.TurnSpeed.COMBAT);
		if (this.drifter.getMainHandItem().getUseAnimation() != net.minecraft.world.item.UseAnim.NONE) {
			if (this.drifter.isUsingItem() && this.drifter.getUsedItemHand() == InteractionHand.MAIN_HAND) {
				this.drifter.stopUsingItem();
			}

			int free = com.dinglebob.drifter.hands.Combat.freeHandSlot(this.drifter);
			if (free >= 0) {
				this.drifter.getInventory().requestSelect(free, 1);
			}

			return;
		}

		if (!this.drifter.isUsingItem()) {
			this.drifter.startUsingItem(InteractionHand.OFF_HAND);
		}
	}

	/** Blazes further than this can't reach it with a burst worth blocking (their fireballs fly about 48 blocks). */
	private static final double GUARD_RANGE = 32.0;

	/** Where to point: where it'll be when the arrow (leaving at {@code speed} blocks a tick) arrives, raised for its fall. */
	private Vec3 aimPoint(double speed) {
		Vec3 eye = this.drifter.getEyePosition();
		Vec3 body = this.target.getBoundingBox().getCenter();
		double ticks = eye.distanceTo(body) / speed;
		Vec3 lead = body.add(this.target.getDeltaMovement().multiply(ticks, 0.0, ticks));
		double drop = 0.5 * Archery.ARROW_GRAVITY * ticks * ticks;
		return lead.add(0.0, drop, 0.0);
	}

	/** Off by more for a clumsy archer, a far target and a fast one. */
	private Vec3 rollError() {
		double skill = this.drifter.getPersonality().getF(Trait.SKILL);
		double distance = this.drifter.distanceTo(this.target);
		double speed = this.target.getDeltaMovement().horizontalDistance() * 20.0;
		double radius = distance * Mth.lerp(skill, 0.05, 0.012) + speed * Mth.lerp(skill, 0.4, 0.1);
		var random = this.drifter.getRandom();
		return new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian()).scale(radius * 0.5);
	}

	private boolean aimedAt(Vec3 point, int heldTicks) {
		Vec3 look = this.drifter.getViewVector(1.0F);
		Vec3 wanted = point.subtract(this.drifter.getEyePosition()).normalize();
		// Near enough to land on its body (half its height, as seen from here), or within a little more the longer it has held
		// the full draw, as a player looses at a target that won't keep still (found in testing: on a bobbing blaze it held the
		// draw over a second at a time waiting for a perfect aim, and fired 7 arrows in 50 s).
		double body = Math.toDegrees(Math.atan2(this.target.getBbHeight() * 0.5, Math.max(1.0, this.drifter.distanceTo(this.target))));
		double allowed = Math.max(AIMED_DEGREES, Math.min(body, MAX_AIMED_DEGREES)) + Math.min(heldTicks * AIM_LOOSEN_PER_TICK, MAX_LOOSEN_DEGREES);
		return Math.toDegrees(Math.acos(Mth.clamp(look.dot(wanted), -1.0, 1.0))) <= allowed;
	}

	/** Most a target's size widens the aim it accepts. */
	private static final double MAX_AIMED_DEGREES = 5.0;
	/** Each tick past a full draw widens it by this much, up to {@link #MAX_LOOSEN_DEGREES}. */
	private static final double AIM_LOOSEN_PER_TICK = 0.2;
	private static final double MAX_LOOSEN_DEGREES = 3.0;

	/** Lets go of the bow: {@code shoot} fires the arrow (BowItemMixin), otherwise it lowers the bow without firing. */
	private void release(boolean shoot) {
		if (!this.drifter.isUsingItem()) {
			return;
		}

		if (shoot) {
			net.minecraft.world.item.ItemStack bow = this.drifter.getUseItem();
			this.drifter.releaseUsingItem();
			// What follows the shot may show what the bow does (its arrows are in the air now).
			com.dinglebob.drifter.gear.PowerWatch.start(this.drifter, bow, this.target, com.dinglebob.drifter.gear.PowerWatch.Use.SHOT);
		} else {
			this.drifter.stopUsingItem();
		}
	}

	@Override
	protected void onStop() {
		this.release(false);
		// (A shot still being watched for that did come out counts; one that didn't yet isn't held against the item.)
		if (this.loosing != null && this.drifter.getDeadLaunchers().loosedSince(this.loosingAt)) {
			this.drifter.getDeadLaunchers().worked(this.loosing);
		}

		this.loosing = null;
	}

	/** Shots it got off (for the thought log). */
	public int shots() {
		return this.shots;
	}

}
