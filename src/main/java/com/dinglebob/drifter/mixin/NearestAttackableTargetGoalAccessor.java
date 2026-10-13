package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

/** Reads a mob's "attack the nearest player" goal so the same goal can be set up for Drifters (world/MobTargeting). */
@Mixin(NearestAttackableTargetGoal.class)
public interface NearestAttackableTargetGoalAccessor {
	@Accessor("targetType")
	Class<?> drifter$getTargetType();

	@Accessor("targetConditions")
	TargetingConditions drifter$getTargetConditions();
}
