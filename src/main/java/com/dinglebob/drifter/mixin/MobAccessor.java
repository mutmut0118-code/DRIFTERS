package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;

/** A mob's goals: target goals so Drifters can be targets (world/MobTargeting), and animals following a Drifter's food. */
@Mixin(Mob.class)
public interface MobAccessor {
	@Accessor("targetSelector")
	GoalSelector drifter$getTargetSelector();

	@Accessor("goalSelector")
	GoalSelector drifter$getGoalSelector();
}
