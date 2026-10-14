package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.ExperienceOrb;

/** An orb's merged count (ExperienceOrb#playerTouch takes one at a time), so a Drifter collects orbs the way a player does. */
@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccessor {
	@Accessor("count")
	int drifter$getCount();

	@Accessor("count")
	void drifter$setCount(int count);
}
