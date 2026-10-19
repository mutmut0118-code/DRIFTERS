package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dinglebob.drifter.perception.SoundHub;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** Every game event (a step, a block broken, a door, a hit) goes down once in SoundHub for Drifters to hear. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelGameEventMixin {
	@Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V", at = @At("HEAD"))
	private void drifter$hear(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
		SoundHub.post((ServerLevel)(Object)this, event, pos, context);
	}
}
