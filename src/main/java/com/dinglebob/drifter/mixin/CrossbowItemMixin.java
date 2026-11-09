package com.dinglebob.drifter.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/**
 * A Drifter's crossbow bolts are critical, as a player's are (CrossbowItem#performShooting makes them so only for players;
 * DESIGN.md section 13 "Ranged mechanics"). Loading and the rest of firing are vanilla's own (ShootTask, getProjectile).
 */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
	@ModifyArg(method = "performShooting", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/CrossbowItem;shoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;Ljava/util/List;FFZLnet/minecraft/world/entity/LivingEntity;)V"),
		index = 7)
	private boolean drifter$critical(ServerLevel level, LivingEntity shooter, InteractionHand hand, ItemStack crossbow, List<ItemStack> shots, float speed,
		float spread, boolean critical, LivingEntity target) {
		return critical || shooter instanceof DrifterEntity;
	}
}
