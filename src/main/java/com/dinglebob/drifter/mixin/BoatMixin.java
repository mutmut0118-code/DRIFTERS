package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.motor.VirtualKeys;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;

/**
 * A Drifter rows its boat as a player does (milestone 18): its keys are the paddles (forward, back, turn left, turn
 * right), through the boat's own controls. A player's client runs those; a Drifter's boat is the server's, so it runs
 * them here, at the same point in the boat's tick. And a boat it rows doesn't scoop up mobs that bump into it, as a
 * player's doesn't.
 */
@Mixin(Boat.class)
public abstract class BoatMixin {
	@Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/Boat;floatBoat()V", shift = At.Shift.AFTER))
	private void drifter$row(CallbackInfo ci) {
		Boat self = (Boat)(Object)this;
		if (!self.level().isClientSide && self.getFirstPassenger() instanceof DrifterEntity drifter && drifter.canControlVehicle()) {
			VirtualKeys keys = drifter.getKeys();
			self.setInput(keys.isDown(VirtualKeys.Key.LEFT), keys.isDown(VirtualKeys.Key.RIGHT), keys.isDown(VirtualKeys.Key.FORWARD),
				keys.isDown(VirtualKeys.Key.BACK));
			((BoatAccessor)self).drifter$controlBoat();
		}
	}

	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/Boat;getControllingPassenger()Lnet/minecraft/world/entity/LivingEntity;"))
	private LivingEntity drifter$rowerCountsAsPlayer(Boat self) {
		LivingEntity rower = self.getControllingPassenger();
		return rower instanceof DrifterEntity drifter && drifter.helperPlayer != null ? drifter.helperPlayer : rower;
	}
}
