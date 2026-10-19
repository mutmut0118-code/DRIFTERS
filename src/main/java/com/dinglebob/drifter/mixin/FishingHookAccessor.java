package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.projectile.FishingHook;

/** Whether a bobber is dipping with a bite (what a player watches for). */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
	@Accessor("biting")
	boolean drifter$biting();
}
