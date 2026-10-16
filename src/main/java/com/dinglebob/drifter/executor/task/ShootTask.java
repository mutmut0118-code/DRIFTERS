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

		if (this.drifter.distanceTo(this.target) < TOO_CLOSE && !Archery.flying(this.target)) {
			this.release(false);
			return this.fail("it came too close for the bow");
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
			if (++this.unseen > LOST_TICKS) {
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
		if (this.drifter.getTicksUsingItem() >= wanted && this.aimedAt(aim)) {
			this.release(true);
			this.shots++;
		}

		return Status.RUNNING;
	}

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

	private boolean aimedAt(Vec3 point) {
		Vec3 look = this.drifter.getViewVector(1.0F);
		Vec3 wanted = point.subtract(this.drifter.getEyePosition()).normalize();
		return Math.toDegrees(Math.acos(Mth.clamp(look.dot(wanted), -1.0, 1.0))) <= AIMED_DEGREES;
	}

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
