package com.dinglebob.drifter.client.mixin;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A vehicle's riders: a riding Drifter's Touch'n Motion stand-in takes its seat for the moment the addon looks (DrifterTouchNMotion). */
@Mixin(Entity.class)
public interface EntityPassengersAccessor {
	@Accessor("passengers")
	ImmutableList<Entity> drifter$passengers();

	@Mutable
	@Accessor("passengers")
	void drifter$setPassengers(ImmutableList<Entity> passengers);
}
