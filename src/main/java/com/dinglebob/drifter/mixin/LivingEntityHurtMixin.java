package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.FakePlayerBridge;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Damage a Drifter does itself (its arrows) counts as a player's, as its sword hits already do through the fake-player
 * helper: so a blaze it shoots down drops its rods and XP like one a player shoots (found by the user: six blazes killed,
 * four with the bow, no rods; blaze rods only drop for a player's kill).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {
	@Inject(method = "hurt", at = @At("RETURN"))
	private void drifter$countAsPlayer(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity)(Object)this;
		if (!cir.getReturnValueZ() || self.level().isClientSide() || !(source.getEntity() instanceof DrifterEntity drifter) || drifter == self) {
			return;
		}

		FakePlayerBridge.act(drifter, fake -> {
			((LivingEntityAccessor)self).drifter$setLastHurtByPlayer(fake);
			((LivingEntityAccessor)self).drifter$setLastHurtByPlayerTime(100);
			return null;
		});
	}
}
