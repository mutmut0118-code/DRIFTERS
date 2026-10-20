package com.dinglebob.drifter.mixin;

import java.util.List;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.FakePlayerBridge;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A boat isn't put down with the player's eyes inside something in the way (BoatItem#use). A Drifter's helper player
 * stands right where the Drifter is, so its eyes are always inside the Drifter's own body: that one doesn't count.
 */
@Mixin(BoatItem.class)
public abstract class BoatItemMixin {
	@Redirect(method = "use", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
	private List<Entity> drifter$notItsOwnBody(Level level, Entity user, AABB box, Predicate<? super Entity> predicate) {
		List<Entity> found = level.getEntities(user, box, predicate);
		DrifterEntity owner = FakePlayerBridge.ownerOf(user);
		if (owner != null && owner != user && found.contains(owner)) {
			found = new java.util.ArrayList<>(found);
			found.remove(owner);
		}

		return found;
	}
}
