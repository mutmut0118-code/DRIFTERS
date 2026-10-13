package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.projectile.AbstractArrow;

/** Whether an arrow is stuck in a block (the incoming-projectile reflex ignores those). */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
	@Accessor("inGround")
	boolean drifter$isInGround();
}
