package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dinglebob.drifter.perception.CreatureHearing;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;

/**
 * Creature sounds (a moo, a groan, a bone rattle, a creeper's hiss) for Drifter hearing (DESIGN.md section 5, "Mob
 * ambient sounds"): they don't produce game events, so they're passed in from here.
 */
@Mixin(Entity.class)
public abstract class EntitySoundMixin {
	@Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"))
	private void drifter$hearCreature(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
		Entity self = (Entity)(Object)this;
		if (sound != null && !self.level().isClientSide() && !self.isSilent()) {
			CreatureHearing.onSound(self, sound, volume);
		}
	}
}
