package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.FakePlayerBridge;

import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

/**
 * Breaking in a wild horse: with a Drifter on its back it bucks and comes round just as it does with a player (the same
 * chance from its temper, the temper rising each time it throws its rider), tamed to the Drifter (its helper player, the
 * same one every time).
 */
@Mixin(RunAroundLikeCrazyGoal.class)
public abstract class RunAroundLikeCrazyGoalMixin {
	@Shadow
	@Final
	private AbstractHorse horse;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void drifter$drifterRider(CallbackInfo ci) {
		if (!(this.horse.getFirstPassenger() instanceof DrifterEntity drifter)) {
			return;
		}

		ci.cancel();
		// (Goal#adjustedTickDelay(50): this goal runs every other tick.)
		if (this.horse.isTamed() || this.horse.getRandom().nextInt(25) != 0) {
			return;
		}

		int temper = this.horse.getTemper();
		int max = this.horse.getMaxTemper();
		if (max > 0 && this.horse.getRandom().nextInt(max) < temper) {
			FakePlayerBridge.act(drifter, fake -> this.horse.tameWithName(fake));
			drifter.getDrifterBrain().setHorse(this.horse.getUUID(), this.horse.blockPosition());
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "the horse has come round: it's mine");
			return;
		}

		this.horse.modifyTemper(5);
		this.horse.ejectPassengers();
		this.horse.makeMad();
		this.horse.level().broadcastEntityEvent(this.horse, (byte)6);
		drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "thrown off the horse");
	}
}
