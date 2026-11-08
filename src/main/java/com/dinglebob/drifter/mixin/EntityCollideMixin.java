package com.dinglebob.drifter.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.RareEntities;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A moving Drifter with no boat, minecart, shulker or other solid entity anywhere near skips the game's search of every
 * entity round it for solid ones (it would find none; RareEntities keeps the short list of those that could be solid).
 * Milestone 16 scale pass, 2026-11-08: in a packed army that search was a twelfth of the server's time. Wrapped, not
 * replaced, so other mods changing the same call still get theirs.
 */
@Mixin(Entity.class)
public abstract class EntityCollideMixin {
	@WrapOperation(method = "collide", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
	private List<VoxelShape> drifter$noSolidsNear(Level level, Entity entity, AABB box, Operation<List<VoxelShape>> original) {
		if ((Object)this instanceof DrifterEntity && RareEntities.noSolidsIn(level, box.inflate(1.0E-7))) {
			return List.of();
		}

		return original.call(level, entity, box);
	}
}
