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
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;

/**
 * Throws its trident at a target, as a player does (DESIGN.md section 13 "Ranged mechanics"): takes the trident out, raises
 * it (holding use) for at least {@link TridentItem#THROW_THRESHOLD_TIME} ticks while it aims where the target will be when the
 * trident gets there (2.5 blocks a tick, falling as an arrow does), and lets go once on target (TridentItemMixin throws it;
 * a modded trident with its own release goes through its helper player). It watches the throw land; the trident is its real
 * item, out in the world until it picks it up again (CollectItemsTask walks over to it; a Loyalty one flies back). A trident
 * that never leaves its hand is given up on for a while, as a bow that never looses is (DeadLaunchers, ruling R23).
 */
public class ThrowTridentTask extends Task {
	/** A target closer than this: the trident stays in hand (the fight goes on in melee). */
	private static final double TOO_CLOSE = 4.0;
	/** Out of sight this long: it gives up. */
	private static final int LOST_TICKS = 20 * 3;
	/** Ticks past the throw threshold it may hold for a better aim before it throws anyway. */
	private static final int MAX_EXTRA_AIM_TICKS = 20;
	/** Close enough to the aim point to let go (degrees), widening by {@link #AIM_LOOSEN_PER_TICK} a tick held past the threshold. */
	private static final double AIMED_DEGREES = 2.5;
	private static final double AIM_LOOSEN_PER_TICK = 0.2;
	/** After the throw it watches this long at most for it to land. */
	private static final int WATCH_TICKS = 30;
	/** This long with a friend in the way: it gives up the throw. */
	private static final int MAX_BLOCKED_TICKS = 20 * 3;

	private final LivingEntity target;
	private final String label;
	@Nullable
	private SelectItemTask select;
	/** The trident it means to throw (the stack in its pack). */
	private ItemStack trident = ItemStack.EMPTY;
	private int unseen;
	private int blockedTicks;
	private Vec3 error = Vec3.ZERO;
	/** The tick it let go (-1: not yet), and whether the throw was seen to work. */
	private long releasedAt = -1L;
	private boolean thrown;
	private boolean killed;

	public ThrowTridentTask(DrifterEntity drifter, LivingEntity target, String label) {
		super(drifter);
		this.target = target;
		this.label = label;
	}

	@Override
	public String describe() {
		return "throw a trident at the " + this.label;
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 15;
	}

	@Override
	protected Status onTick() {
		if (this.releasedAt >= 0L) {
			return this.watch();
		}

		if (!this.target.isAlive()) {
			this.lower();
			return Status.SUCCESS;
		}

		if (this.drifter.distanceTo(this.target) < TOO_CLOSE) {
			this.lower();
			return this.fail("it came too close to throw at");
		}

		// Something else is hitting it: the trident comes down, it turns to that.
		LivingEntity hitBy = this.drifter.getLastHurtByMob();
		if (hitBy != null && hitBy != this.target && hitBy.isAlive() && this.drifter.tickCount - this.drifter.getLastHurtByMobTimestamp() < 10
			&& hitBy.distanceTo(this.drifter) < TOO_CLOSE) {
			this.lower();
			return this.fail("something else is hitting it");
		}

		// The trident in hand first.
		if (this.trident.isEmpty() || this.drifter.getMainHandItem() != this.trident || this.select != null) {
			if (this.select == null) {
				int slot = Archery.throwableTridentSlot(this.drifter);
				if (slot < 0) {
					this.lower();
					return this.fail("no trident to throw");
				}

				ItemStack chosen = this.drifter.getInventory().getItem(slot);
				this.trident = chosen;
				this.select = new SelectItemTask(this.drifter, stack -> stack == chosen, "the trident");
			}

			Status status = this.select.tick();
			if (status == Status.FAILURE) {
				return this.fail("can't take out the trident");
			}

			if (status == Status.SUCCESS) {
				this.select = null;
			}

			return Status.RUNNING;
		}

		// Only what it can see (section 1).
		var seen = this.drifter.getSenses().awarenessOf(this.target);
		if (seen == null || !seen.visibleNow) {
			if (++this.unseen > LOST_TICKS) {
				this.lower();
				return this.fail("lost sight of it");
			}

			return Status.RUNNING;
		}

		this.unseen = 0;
		Steering.stopMoving(this.drifter);
		Vec3 aim = this.aimPoint().add(this.error);
		this.drifter.getCamera().track(aim, CameraModel.TurnSpeed.COMBAT);
		if (!this.drifter.isUsingItem()) {
			// Not raised for a throw it won't make (gear powers).
			if (this.heldFire()) {
				return this.fail("held fire");
			}

			this.drifter.startUsingItem(InteractionHand.MAIN_HAND);
			this.error = this.rollError();
			return Status.RUNNING;
		}

		int held = this.drifter.getTicksUsingItem();
		int past = held - TridentItem.THROW_THRESHOLD_TIME;
		if (past < 0 || !this.aimedAt(aim, past) && past < MAX_EXTRA_AIM_TICKS) {
			return Status.RUNNING;
		}

		// Its trident's power, as it believes it, would hurt a friend, itself or its home: it lowers it (a friend walked in).
		if (this.heldFire()) {
			this.lower();
			return this.fail("held fire");
		}

		if (Archery.friendInTheWay(this.drifter, this.drifter.getViewVector(1.0F), (float)(Archery.TRIDENT_SPEED / Archery.ARROW_SPEED),
			this.drifter.distanceTo(this.target))) {
			if (++this.blockedTicks > MAX_BLOCKED_TICKS) {
				this.lower();
				return this.fail("friends in the way");
			}

			return Status.RUNNING;
		}

		// Let go, watched: a trident that never leaves the hand is given up on for a while (DeadLaunchers, ruling R23).
		var dead = this.drifter.getDeadLaunchers();
		dead.watch(this.drifter, this.trident, this.target, false);
		this.releasedAt = this.drifter.level().getGameTime();
		this.drifter.releaseUsingItem();
		this.thrown = dead.check(this.drifter);
		if (this.thrown) {
			this.thinkThrown();
		}

		return Status.RUNNING;
	}

	/** After letting go: did it go, and where did it land? */
	private Status watch() {
		long since = this.drifter.level().getGameTime() - this.releasedAt;
		if (!this.thrown) {
			if (this.drifter.getDeadLaunchers().check(this.drifter)) {
				this.thrown = true;
				this.thinkThrown();
			} else if (since > com.dinglebob.drifter.hands.DeadLaunchers.LOOSE_WINDOW_TICKS) {
				// (Settled as a failure by now: counted against the item.)
				return this.fail("it wouldn't throw");
			} else {
				return Status.RUNNING;
			}
		}

		AbstractArrow flying = this.drifter.thrownTridentEntity();
		boolean landed = flying != null && ((com.dinglebob.drifter.mixin.AbstractArrowAccessor)flying).drifter$isInGround();
		// Watching it land, it knows where to go for it.
		if (landed && this.drifter.canSeeThrownTrident(flying)) {
			this.drifter.sawThrownTrident();
		}

		if (!this.target.isAlive()) {
			this.killed = true;
			// (A moment more for it to land: it watches its trident down, then the kill counts.)
			if (flying == null || landed || flying.isNoPhysics() || since > WATCH_TICKS) {
				return Status.SUCCESS;
			}
		} else if (flying == null || flying.isNoPhysics() || landed || since > WATCH_TICKS) {
			return Status.SUCCESS;
		}

		this.drifter.getCamera().track(flying.position(), CameraModel.TurnSpeed.COMBAT);
		return Status.RUNNING;
	}

	private void thinkThrown() {
		String name = this.trident.is(Items.TRIDENT) && !this.trident.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) ? "trident"
			: this.trident.getHoverName().getString();
		this.drifter.getMemory().thoughts().add(this.drifter.level().getGameTime(), "threw its " + name + " at the " + this.label);
		// What follows the throw may show what the trident does (as a shot does: gear powers).
		com.dinglebob.drifter.gear.PowerWatch.start(this.drifter, this.trident, this.target, com.dinglebob.drifter.gear.PowerWatch.Use.SHOT);
	}

	/** The target died before the trident landed (for the fight's kill count). */
	public boolean killed() {
		return this.killed;
	}

	/** Its trident's power, as it believes it, would hurt a friend, itself or its home on this target: it says so to itself. */
	private boolean heldFire() {
		String why = com.dinglebob.drifter.gear.Powers.whyNot(this.drifter, this.trident, this.target);
		if (why == null) {
			return false;
		}

		this.drifter.getMemory().thoughts().add(this.drifter.level().getGameTime(), "held fire: " + this.trident.getHoverName().getString() + " "
			+ com.dinglebob.drifter.gear.Powers.heldFireWords(this.drifter, this.trident, why));
		return true;
	}

	/** Where to point: where it'll be when the trident gets there, raised for its fall (as an arrow's, ShootTask). */
	private Vec3 aimPoint() {
		Vec3 eye = this.drifter.getEyePosition();
		Vec3 body = this.target.getBoundingBox().getCenter();
		double ticks = eye.distanceTo(body) / Archery.TRIDENT_SPEED;
		Vec3 lead = body.add(this.target.getDeltaMovement().multiply(ticks, 0.0, ticks));
		return lead.add(0.0, 0.5 * Archery.ARROW_GRAVITY * ticks * ticks, 0.0);
	}

	/** Off by more for a clumsy thrower, a far target and a fast one (as ShootTask's). */
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
		double body = Math.toDegrees(Math.atan2(this.target.getBbHeight() * 0.5, Math.max(1.0, this.drifter.distanceTo(this.target))));
		double allowed = Math.max(AIMED_DEGREES, Math.min(body, 5.0)) + heldTicks * AIM_LOOSEN_PER_TICK;
		return Math.toDegrees(Math.acos(Mth.clamp(look.dot(wanted), -1.0, 1.0))) <= allowed;
	}

	/** Lowers the trident without throwing it. */
	private void lower() {
		if (this.drifter.isUsingItem()) {
			this.drifter.stopUsingItem();
		}
	}

	@Override
	protected void onStop() {
		if (this.releasedAt < 0L) {
			this.lower();
		}
	}
}
