package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.vehicle.Boat;

/** A boat's paddling, for a Drifter rowing it as a player does (BoatMixin, BoatTask). */
@Mixin(Boat.class)
public interface BoatAccessor {
	@Invoker("controlBoat")
	void drifter$controlBoat();

	/** How fast it's turning (degrees a tick; the water slows it by a tenth a tick). */
	@Accessor("deltaRotation")
	float drifter$deltaRotation();

	@Accessor("status")
	Boat.Status drifter$status();
}
