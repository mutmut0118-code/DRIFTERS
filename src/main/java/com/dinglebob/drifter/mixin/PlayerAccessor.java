package com.dinglebob.drifter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.player.Player;

/** Sets the player-hands helper's enchanting seed to the Drifter's own, so the table's offers stay put between visits. */
@Mixin(Player.class)
public interface PlayerAccessor {
	@Accessor("enchantmentSeed")
	void drifter$setEnchantmentSeed(int seed);
}
