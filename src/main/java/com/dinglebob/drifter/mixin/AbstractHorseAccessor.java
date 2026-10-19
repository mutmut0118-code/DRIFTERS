package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;

/** A horse's ridden state, for a Drifter riding it as a player does (DrifterRiding). */
@Mixin(AbstractHorse.class)
public interface AbstractHorseAccessor {
	@Accessor("playerJumpPendingScale")
	float drifter$jumpPending();

	@Accessor("playerJumpPendingScale")
	void drifter$setJumpPending(float scale);

	@Accessor("allowStandSliding")
	boolean drifter$allowStandSliding();

	@Accessor("allowStandSliding")
	void drifter$setAllowStandSliding(boolean allow);

	@Invoker("executeRidersJump")
	void drifter$executeRidersJump(float scale, Vec3 input);

	@Invoker("playJumpSound")
	void drifter$playJumpSound();
}
