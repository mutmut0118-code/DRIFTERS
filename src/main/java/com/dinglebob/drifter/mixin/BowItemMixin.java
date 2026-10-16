package com.dinglebob.drifter.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.Archery;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;

/**
 * A Drifter lets go of a drawn bow as a player does (vanilla's bow only fires for players): the same power for the draw
 * time, the same arrow use (Infinity and all), bow wear, enchantments and crit on a full draw, aimed where its head
 * points (DESIGN.md section 13 "Ranged mechanics").
 */
@Mixin(BowItem.class)
public abstract class BowItemMixin extends ProjectileWeaponItem {
	protected BowItemMixin(Properties properties) {
		super(properties);
	}

	@Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
	private void drifter$release(ItemStack bow, Level level, LivingEntity entity, int remaining, CallbackInfo ci) {
		if (!(entity instanceof DrifterEntity drifter)) {
			return;
		}

		ci.cancel();
		ItemStack ammo = Archery.ammo(drifter);
		if (ammo.isEmpty()) {
			return;
		}

		float power = BowItem.getPowerForTime(this.getUseDuration(bow, entity) - remaining);
		if (power < 0.1F) {
			return;
		}

		List<ItemStack> shots = draw(bow, ammo, drifter);
		if (level instanceof ServerLevel server && !shots.isEmpty()) {
			this.shoot(server, drifter, drifter.getUsedItemHand(), bow, shots, power * 3.0F, 1.0F, power == 1.0F, null);
		}

		level.playSound(null, drifter.getX(), drifter.getY(), drifter.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F,
			1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
	}
}
