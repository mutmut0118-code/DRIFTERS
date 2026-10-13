package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.ai.goal.target.TargetGoal;

/** Reads a target goal's line-of-sight and reachability rules (world/MobTargeting). */
@Mixin(TargetGoal.class)
public interface TargetGoalAccessor {
	@Accessor("mustSee")
	boolean drifter$mustSee();

	@Accessor("mustReach")
	boolean drifter$mustReach();
}
