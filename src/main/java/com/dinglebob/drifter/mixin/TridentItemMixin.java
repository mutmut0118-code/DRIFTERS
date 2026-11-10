package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/**
 * A Drifter lets go of a trident as a player does (vanilla's trident only throws for players; DESIGN.md section 13 "Ranged
 * mechanics"): held at least {@link TridentItem#THROW_THRESHOLD_TIME} ticks, one point of wear, a ThrownTrident from its eyes
 * where its head points at the player's speed, the trident's own throw sound, and the stack leaves its hands (the thrown one
 * is that item: picked up again, it comes back as it was). Its owner may pick it up (vanilla allows that only for a player's).
 * A Riptide trident is never thrown (a player's isn't either, out of water; in water it would launch the Drifter, which it
 * doesn't do). Any TridentItem that keeps this release (a modded one that doesn't write its own) goes the same way; one with
 * its own is let go by the helper player (DrifterEntity#releaseUsingItem).
 */
@Mixin(TridentItem.class)
public abstract class TridentItemMixin extends Item {
	protected TridentItemMixin(Properties properties) {
		super(properties);
	}

	@Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
	private void drifter$release(ItemStack trident, Level level, LivingEntity entity, int remaining, CallbackInfo ci) {
		if (!(entity instanceof DrifterEntity drifter)) {
			return;
		}

		ci.cancel();
		if (this.getUseDuration(trident, entity) - remaining < TridentItem.THROW_THRESHOLD_TIME
			|| EnchantmentHelper.getTridentSpinAttackStrength(trident, drifter) > 0.0F
			// (TridentItem#isTooDamagedToUse: one point from breaking, it won't go.)
			|| trident.getDamageValue() >= trident.getMaxDamage() - 1 || level.isClientSide) {
			return;
		}

		Holder<SoundEvent> sound = EnchantmentHelper.pickHighestLevel(trident, EnchantmentEffectComponents.TRIDENT_SOUND).orElse(SoundEvents.TRIDENT_THROW);
		trident.hurtAndBreak(1, drifter, LivingEntity.getSlotForHand(drifter.getUsedItemHand()));
		if (trident.isEmpty()) {
			return;
		}

		// (The thrown one carries a copy of the stack; the stack itself leaves the Drifter's hands below.)
		ThrownTrident thrown = new ThrownTrident(level, drifter, trident);
		thrown.shootFromRotation(drifter, drifter.getXRot(), drifter.getYRot(), 0.0F, TridentItem.SHOOT_POWER, 1.0F);
		thrown.pickup = AbstractArrow.Pickup.ALLOWED;
		level.addFreshEntity(thrown);
		level.playSound(null, thrown, sound.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
		drifter.removeStack(trident);
		drifter.setThrownTrident(thrown.getUUID());
	}
}
