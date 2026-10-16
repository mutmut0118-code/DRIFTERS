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
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Shoots a target with a bow, as a player does (DESIGN.md section 13 "Ranged mechanics"): takes out the bow, draws
 * (holding use) for a full second unless the target is close, aims with its head where the target will be when the
 * arrow gets there, a little high for the drop, and lets go once the crosshair is on that point. Aim error shrinks with
 * Skill and grows with distance and how fast the target moves. It stands its ground while it draws; if the target comes
 * close it puts the bow away (the fight goes on with the sword).
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
		if (!this.target.isAlive()) {
			this.release(false);
			return Status.SUCCESS;
		}

		if (!Archery.ready(this.drifter)) {
			this.release(false);
			return this.fail("no arrows left");
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
		if (this.drifter.getOffhandItem().is(Items.SHIELD) && Archery.glowing(this.drifter, this.target)) {
			this.guardUntil = now + Archery.BLAZE_GUARD_AFTER_TICKS;
		}

		if (this.drifter.getOffhandItem().is(Items.SHIELD) && now < this.guardUntil) {
			this.guard();
			return Status.RUNNING;
		}

		if (this.guarding) {
			this.guarding = false;
			if (this.drifter.isUsingItem() && this.drifter.getUsedItemHand() == InteractionHand.OFF_HAND) {
				this.drifter.stopUsingItem();
			}
		}

		// The bow in hand first.
		if (!this.drifter.getMainHandItem().is(Items.BOW)) {
			if (this.select == null) {
				this.select = new SelectItemTask(this.drifter, stack -> stack.is(Items.BOW), "the bow");
			}

			Status status = this.select.tick();
			if (status == Status.FAILURE) {
				return this.fail("can't take out the bow");
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
		Vec3 aim = this.aimPoint().add(this.error);
		this.drifter.getCamera().track(aim, CameraModel.TurnSpeed.COMBAT);
		if (!this.drifter.isUsingItem()) {
			this.drifter.startUsingItem(InteractionHand.MAIN_HAND);
			this.error = this.rollError();
			return Status.RUNNING;
		}

		int wanted = this.drifter.distanceTo(this.target) < QUICK_DRAW_RANGE && !Archery.flying(this.target) ? QUICK_DRAW_TICKS : Archery.FULL_DRAW_TICKS;
		if (this.drifter.getTicksUsingItem() >= wanted && this.aimedAt(aim, this.drifter.getTicksUsingItem() - wanted)) {
			this.release(true);
			this.shots++;
		}

		return Status.RUNNING;
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

	/** Where to point: where it'll be when the arrow arrives, raised for the arrow's fall. */
	private Vec3 aimPoint() {
		Vec3 eye = this.drifter.getEyePosition();
		Vec3 body = this.target.getBoundingBox().getCenter();
		double ticks = eye.distanceTo(body) / Archery.ARROW_SPEED;
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
			this.drifter.releaseUsingItem();
		} else {
			this.drifter.stopUsingItem();
		}
	}

	@Override
	protected void onStop() {
		this.release(false);
	}

	/** Shots it got off (for the thought log). */
	public int shots() {
		return this.shots;
	}

}
