package com.dinglebob.drifter.hands;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Bows (DESIGN.md section 13 "Ranged mechanics"): what it shoots with and when a bow beats a sword. */
public final class Archery {
	/** A full draw (BowItem): 20 ticks, 1 s. */
	public static final int FULL_DRAW_TICKS = 20;
	/** Arrow speed at a full draw, blocks a tick (power 1 x 3). */
	public static final double ARROW_SPEED = 3.0;
	/** Arrows fall this much faster every tick (AbstractArrow's gravity). */
	public static final double ARROW_GRAVITY = 0.05;
	/** Closer than this a melee mob is better met with the sword. */
	public static final double MELEE_RANGE = 6.0;
	/** Farther than this it doesn't bother (most shots would miss). */
	public static final double MAX_RANGE = 40.0;

	private Archery() {
	}

	/** The arrows it would shoot: off hand first, then its inventory (as a player's bow picks). */
	public static ItemStack ammo(DrifterEntity drifter) {
		if (drifter.getOffhandItem().getItem() instanceof ArrowItem) {
			return drifter.getOffhandItem();
		}

		DrifterInventory inventory = drifter.getInventory();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.getItem() instanceof ArrowItem) {
				return stack;
			}
		}

		return ItemStack.EMPTY;
	}

	/** It has a bow and something to shoot. */
	public static boolean ready(DrifterEntity drifter) {
		return drifter.getInventory().count(stack -> stack.is(Items.BOW)) > 0 && !ammo(drifter).isEmpty();
	}

	/** Out of sword reach for good (flying, or keeps its distance). */
	public static boolean flying(LivingEntity target) {
		return target instanceof Ghast || target instanceof Blaze || target instanceof Phantom;
	}

	/**
	 * A bow beats a sword here: a flier, a creeper still a few blocks off, or anything hostile at range. Never animals
	 * (it hunts those up close, they'd run).
	 */
	public static boolean shouldShoot(DrifterEntity drifter, LivingEntity target) {
		if (!ready(drifter) || com.dinglebob.drifter.executor.task.AttackTask.Protocol.of(target)
			== com.dinglebob.drifter.executor.task.AttackTask.Protocol.PREY) {
			return false;
		}

		double distance = drifter.distanceTo(target);
		if (distance > MAX_RANGE) {
			return false;
		}

		if (flying(target)) {
			return true;
		}

		return target instanceof Creeper ? distance >= 4.0 : distance >= MELEE_RANGE;
	}
}
