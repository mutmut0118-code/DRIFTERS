package com.dinglebob.drifter.compatmixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Alex's Mobs (optional): a tusklin leaves a player alone who holds one of its mushrooms in either hand, or while it's calmed
 * (fed one), unless that player hit it; its check is for players only, so a Drifter holding a mushroom was gored all the
 * same. Mobs treat a Drifter as a player (DESIGN.md section 1): the same rule for it. Applied only when Alex's Mobs is loaded
 * (CompatMixinPlugin); the method's dev name and its runtime name both, and nothing changes if this version doesn't have it.
 */
@Pseudo
@Mixin(targets = "com.github.alexthe666.alexsmobs.entity.EntityTusklin", remap = false)
public abstract class TusklinMushroomMixin {
	@Shadow(remap = false)
	public abstract boolean isMushroom(ItemStack stack);

	@Shadow(remap = false)
	public abstract int getPassiveTicks();

	@Inject(method = {"canAttack", "method_18395"}, at = @At("RETURN"), cancellable = true, remap = false, require = 0)
	private void drifter$sparesMushroomHolder(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() || !(target instanceof DrifterEntity drifter)) {
			return;
		}

		LivingEntity self = (LivingEntity)(Object)this;
		if (drifter.equals(self.getLastHurtByMob())) {
			return;
		}

		if (this.getPassiveTicks() > 0 || this.isMushroom(drifter.getItemInHand(InteractionHand.MAIN_HAND)) || this.isMushroom(drifter.getItemInHand(InteractionHand.OFF_HAND))) {
			cir.setReturnValue(false);
		}
	}
}
