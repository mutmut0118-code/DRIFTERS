package com.dinglebob.drifter.mixin;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.item.ItemEntity;

/** Reads an item entity's pickup target, so Drifter pickup obeys the same "only this player may take it" rule as player pickup. */
@Mixin(ItemEntity.class)
public interface ItemEntityAccessor {
	@Nullable
	@Accessor("target")
	UUID drifter$getTarget();
}
