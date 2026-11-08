package com.dinglebob.drifter.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.dinglebob.drifter.perception.BellHearing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;

/** A bell rung (struck by anyone, an arrow, a blast): Drifters within earshot hear it ({@link BellHearing}). */
@Mixin(BellBlock.class)
public abstract class BellBlockMixin {
	@Inject(method = "attemptToRing(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
		at = @At("RETURN"))
	private void drifter$rung(@Nullable Entity entity, Level level, BlockPos pos, @Nullable Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && level instanceof ServerLevel server) {
			BellHearing.rung(server, pos, entity);
		}
	}
}
