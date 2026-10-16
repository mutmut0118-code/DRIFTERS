package com.dinglebob.drifter.executor.task;

import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.Steering;
import com.dinglebob.drifter.motor.VirtualKeys;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.phys.Vec3;

/**
 * Running away like a player does (user's note from testing: under fire it stood still "thinking" where to go before
 * going): it turns and sprints away from the danger at once, steering with the keys, jumping bumps, and swerving
 * around drops, lava and walls by trying directions to either side of straight away. Only when it's boxed in does it
 * fall back on the pathfinder.
 */
public class RunAwayTask extends Task {
	/** Directions tried, in degrees off straight away from the danger. */
	private static final int[] SWERVES = {0, 30, -30, 60, -60, 90, -90, 120, -120};
	private static final int STUCK_TICKS = 30;

	private final Supplier<List<LivingEntity>> threats;
	private final double safeDistance;
	private final int maxTicks;
	private int stuck;
	@Nullable
	private NavigateTask fallback;
	private Vec3 lastPos = Vec3.ZERO;

	public RunAwayTask(DrifterEntity drifter, Supplier<List<LivingEntity>> threats, double safeDistance, int maxTicks) {
		super(drifter);
		this.threats = threats;
		this.safeDistance = safeDistance;
		this.maxTicks = maxTicks;
	}

	@Override
	public String describe() {
		return "run away";
	}

	@Override
	protected int timeoutTicks() {
		return this.maxTicks;
	}

	@Override
	protected Status onTick() {
		List<LivingEntity> threats = this.threats.get();
		if (threats.isEmpty()) {
			return Status.SUCCESS;
		}

		Vec3 here = this.drifter.position();
		Vec3 centre = Vec3.ZERO;
		double nearest = Double.MAX_VALUE;
		for (LivingEntity threat : threats) {
			centre = centre.add(threat.position());
			nearest = Math.min(nearest, threat.distanceTo(this.drifter));
		}

		if (nearest > this.safeDistance) {
			return Status.SUCCESS;
		}

		if (this.fallback != null) {
			Status status = this.fallback.tick();
			if (status != Status.RUNNING) {
				this.fallback.stop();
				this.fallback = null;
				this.stuck = 0;
			}

			return Status.RUNNING;
		}

		centre = centre.scale(1.0 / threats.size());
		Vec3 away = new Vec3(here.x - centre.x, 0.0, here.z - centre.z);
		if (away.lengthSqr() < 1.0E-4) {
			away = Vec3.directionFromRotation(0.0F, this.drifter.getYRot());
		}

		away = away.normalize();
		Vec3 direction = null;
		// From an archer (a skeleton, a stray) running straight away across open ground gets it shot in the back (found in the
		// user's game: three Drifters died that way on a snowy mountain): like a player it heads for cover, a way that puts
		// a hill, a tree or a wall between them, when there is one within a turn of straight away.
		List<LivingEntity> archers = threats.stream().filter(t -> t instanceof net.minecraft.world.entity.monster.RangedAttackMob).toList();
		for (int swerve : SWERVES) {
			Vec3 candidate = away.yRot((float)Math.toRadians(swerve));
			// Two blocks out too: at a sprint it can't stop or turn within one (found in testing: it ran from a blaze into lava).
			if (Steering.safeStep(this.drifter, candidate) && Steering.safeStep(this.drifter, candidate, 1.9) && !Steering.wallAhead(this.drifter, candidate)) {
				if (direction == null) {
					direction = candidate;
				}

				if (archers.isEmpty() || Math.abs(swerve) > 90) {
					break;
				}

				if (this.hiddenFrom(archers, here.add(candidate.scale(COVER_LOOKAHEAD)))) {
					direction = candidate;
					break;
				}
			}
		}

		// Boxed in (or not getting anywhere): let the pathfinder find a way out.
		// Sideways only: sinking in powder snow or hopping on the spot isn't getting anywhere (found in the user's game).
		this.stuck = new Vec3(here.x - this.lastPos.x, 0.0, here.z - this.lastPos.z).lengthSqr() < 0.01 ? this.stuck + 1 : 0;
		this.lastPos = here;
		if (direction == null || this.stuck > STUCK_TICKS) {
			this.fallback = new NavigateTask(this.drifter, here.add(away.scale(16.0)), 4.0, "find a way out", NavigateTask.Pace.TRAVEL);
			return Status.RUNNING;
		}

		Vec3 target = here.add(direction.scale(4.0));
		this.drifter.getCamera().track(new Vec3(target.x, this.drifter.getEyeY(), target.z), CameraModel.TurnSpeed.COMBAT);
		VirtualKeys keys = this.drifter.getKeys();
		Steering.pressToward(this.drifter, target);
		keys.set(VirtualKeys.Key.SNEAK, false);
		keys.set(VirtualKeys.Key.SPRINT, keys.isDown(VirtualKeys.Key.FORWARD) && this.drifter.getFoodData().getFoodLevel() > FoodConstants.SPRINT_LEVEL);
		// Jump bumps; in water, keep its head above the surface.
		keys.set(VirtualKeys.Key.JUMP, this.drifter.horizontalCollision && this.drifter.onGround() || this.drifter.isUnderWater());
		return Status.RUNNING;
	}

	/** How far ahead it checks for cover from archers. */
	private static final double COVER_LOOKAHEAD = 4.0;

	/** Out of every archer's line of fire at {@code spot} (blocks between the archer's eyes and its chest there). */
	private boolean hiddenFrom(List<LivingEntity> archers, Vec3 spot) {
		Vec3 chest = spot.add(0.0, this.drifter.getBbHeight() * 0.7, 0.0);
		for (LivingEntity archer : archers) {
			var hit = this.drifter.level().clip(new net.minecraft.world.level.ClipContext(archer.getEyePosition(), chest,
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, archer));
			if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
				return false;
			}
		}

		return true;
	}

	@Override
	protected void onStop() {
		if (this.fallback != null) {
			this.fallback.stop();
			this.fallback = null;
		}

		this.drifter.getKeys().releaseAll();
		this.drifter.getCamera().clearTarget();
	}
}
