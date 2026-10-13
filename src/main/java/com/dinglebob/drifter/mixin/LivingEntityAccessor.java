package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.LivingEntity;

/** Lets the fake-player helper carry the Drifter's attack cooldown into a player attack (hands/Combat). */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("attackStrengthTicker")
	void drifter$setAttackStrengthTicker(int ticks);
}
