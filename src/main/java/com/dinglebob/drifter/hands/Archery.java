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
	/** A blaze it can see now within {@code range}, glowing to fire a burst (Blaze#isOnFire is its "charged" flag, the fire
	 * on it a player sees). */
	public static boolean chargedBlazeInSight(DrifterEntity drifter, double range) {
		return drifter.getSenses().tracked().stream().anyMatch(a -> a.visibleNow && a.entity instanceof net.minecraft.world.entity.monster.Blaze blaze
			&& blaze.isAlive() && blaze.isOnFire() && blaze.distanceTo(drifter) < range);
	}

	/** That blaze, seen now, glowing to fire. With several blazes one is nearly always glowing, so it guards against the
	 * one it's fighting (the incoming-fireball reflex handles the rest; found in testing: guarding against any of them, it
	 * held its shield up for a minute and never struck). */
	public static boolean glowing(DrifterEntity drifter, LivingEntity target) {
		return target instanceof net.minecraft.world.entity.monster.Blaze blaze && blaze.isAlive() && blaze.isOnFire()
			&& drifter.getSenses().tracked().stream().anyMatch(a -> a.entity == blaze && a.visibleNow);
	}

	/** Ticks the shield stays up after a blaze stops glowing (the last fireball of its burst is still on its way). */
	public static final int BLAZE_GUARD_AFTER_TICKS = 25;

	/** A drop or lava within two steps of it, any way round (on a bridge or a ledge). */
	public static boolean nearDrop(DrifterEntity drifter) {
		for (int angle = 0; angle < 360; angle += 45) {
			net.minecraft.world.phys.Vec3 direction = net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, angle);
			if (!com.dinglebob.drifter.motor.Steering.safeStep(drifter, direction) || !com.dinglebob.drifter.motor.Steering.safeStep(drifter, direction, 2.0)) {
				return true;
			}
		}

		return false;
	}

	/** A blaze nearer than this is fought with the sword. */
	public static final double BLAZE_MELEE_RANGE = 5.0;

	public static boolean shouldShoot(DrifterEntity drifter, LivingEntity target) {
		if (!ready(drifter) || com.dinglebob.drifter.executor.task.AttackTask.Protocol.of(target)
			== com.dinglebob.drifter.executor.task.AttackTask.Protocol.PREY) {
			return false;
		}

		double distance = drifter.distanceTo(target);
		if (distance > MAX_RANGE) {
			return false;
		}

		// A blaze close by gets the sword: it hovers by its spawner, and three hits are quicker than trading shots (how
		// players clear a spawner; found in testing: it stood by the spawner drawing the bow at blazes within reach).
		// (Not at the edge of a drop, a bridge over the lava sea: a blaze's hit knocks it back, and stepping in to swing it
		// went over; found in testing on a fortress bridge. There it stays back and uses the bow.)
		if (target instanceof net.minecraft.world.entity.monster.Blaze && distance < BLAZE_MELEE_RANGE && !nearDrop(drifter)) {
			return false;
		}

		if (flying(target)) {
			return true;
		}

		return target instanceof Creeper ? distance >= 4.0 : distance >= MELEE_RANGE;
	}
}
