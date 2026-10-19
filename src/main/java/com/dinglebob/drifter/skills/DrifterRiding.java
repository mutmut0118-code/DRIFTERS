package com.dinglebob.drifter.skills;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.mixin.AbstractHorseAccessor;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;

/**
 * A horse ridden by a Drifter, moved as vanilla moves a horse ridden by a player (LivingEntity#travelRidden,
 * AbstractHorse#tickRidden and #getRiddenInput): the rider's strafe at half and its forward (back at a quarter), the horse
 * facing where the rider faces, the horse's own speed, and a jump when the rider jumps on the ground (a full one: a
 * player holding the jump bar). Server side; the horse is the Drifter's input, so its keys steer it.
 */
public final class DrifterRiding {
	private DrifterRiding() {
	}

	/** In the middle of moving a ridden horse (its own travel call shouldn't come back here). */
	private static boolean riding;

	public static boolean riding() {
		return riding;
	}

	public static void travel(AbstractHorse horse, DrifterEntity rider) {
		AbstractHorseAccessor state = (AbstractHorseAccessor)horse;
		Vec3 input;
		if (horse.onGround() && state.drifter$jumpPending() == 0.0F && horse.isStanding() && !state.drifter$allowStandSliding()) {
			input = Vec3.ZERO;
		} else {
			float forward = rider.zza;
			if (forward <= 0.0F) {
				forward *= 0.25F;
			}

			input = new Vec3(rider.xxa * 0.5F, 0.0, forward);
		}

		horse.setYRot(rider.getYRot() % 360.0F);
		horse.setXRot(rider.getXRot() * 0.5F % 360.0F);
		horse.yRotO = horse.yBodyRot = horse.yHeadRot = horse.getYRot();
		if (horse.onGround()) {
			horse.setIsJumping(false);
			// The rider jumping: a full jump (a player holding the jump bar a moment), forward if it's going forward.
			if (rider.isJumpingNow() && !horse.isJumping()) {
				state.drifter$setAllowStandSliding(true);
				state.drifter$playJumpSound();
				state.drifter$executeRidersJump(1.0F, input);
			}

			state.drifter$setJumpPending(0.0F);
		}

		riding = true;
		try {
			horse.setSpeed((float)horse.getAttributeValue(Attributes.MOVEMENT_SPEED));
			horse.travel(input);
		} finally {
			riding = false;
		}
	}
}
