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
		return drifter.getInventory().count(com.dinglebob.drifter.hands.Shields::isBow) > 0 && !ammo(drifter).isEmpty();
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

	/**
	 * A friend (its battle side, its group) where the arrow would fly if it let go now: the arrow's path traced tick by
	 * tick from its eyes (AbstractArrow: it leaves a little under the eyes at {@code power} times 3 blocks a tick, slows by
	 * 1% and falls 0.05 faster each tick, and hits anyone within 0.3 of its line), out to {@code distance} and a little past
	 * (a miss flies on into whoever stands behind), each friend where it will be by then at the pace it's going, with a
	 * little room to spare. The friends round it and its target are in plain view. A player in a crowd doesn't shoot
	 * through the friend in front either (found in a 50v50: the soldiers at the back, given bows, put 34 arrows into their
	 * own side).
	 */
	public static boolean friendInTheWay(DrifterEntity drifter, net.minecraft.world.phys.Vec3 look, float power, double distance) {
		net.minecraft.world.phys.Vec3 eye = drifter.getEyePosition();
		java.util.List<net.minecraft.world.phys.Vec3> path = new java.util.ArrayList<>(24);
		net.minecraft.world.phys.Vec3 pos = eye.subtract(0.0, 0.1, 0.0);
		// (The game adds the shooter's own movement to the arrow: stepping aside as it lets go carries the arrow with it.)
		net.minecraft.world.phys.Vec3 moving = drifter.getDeltaMovement();
		net.minecraft.world.phys.Vec3 velocity = look.scale(power * ARROW_SPEED).add(moving.x, drifter.onGround() ? 0.0 : moving.y, moving.z);
		path.add(pos);
		double minX = pos.x, minY = pos.y, minZ = pos.z, maxX = pos.x, maxY = pos.y, maxZ = pos.z;
		for (int tick = 0; tick < 40 && pos.distanceTo(eye) < distance + PAST_TARGET; tick++) {
			pos = pos.add(velocity);
			velocity = velocity.scale(ARROW_INERTIA).subtract(0.0, ARROW_GRAVITY, 0.0);
			path.add(pos);
			minX = Math.min(minX, pos.x);
			minY = Math.min(minY, pos.y);
			minZ = Math.min(minZ, pos.z);
			maxX = Math.max(maxX, pos.x);
			maxY = Math.max(maxY, pos.y);
			maxZ = Math.max(maxZ, pos.z);
		}

		var friends = drifter.level().getEntitiesOfClass(DrifterEntity.class, new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(3.0),
			other -> other != drifter && other.isAlive() && friends(drifter, other));
		for (DrifterEntity friend : friends) {
			net.minecraft.world.phys.Vec3 pace = friend.getDeltaMovement().multiply(1.0, 0.0, 1.0);
			for (int tick = 0; tick + 1 < path.size(); tick++) {
				// More room the farther out (the arrow spreads a little, and a friend in a fight doesn't keep its pace), and a
				// friend the arrow would start inside of counts too (ProjectileUtil: it hits at once).
				net.minecraft.world.phys.Vec3 from = path.get(tick);
				var box = friend.getBoundingBox().move(pace.scale(tick + 1)).inflate(ARROW_HIT_MARGIN + FRIEND_ROOM + FRIEND_ROOM_PER_BLOCK * from.distanceTo(eye));
				if (box.contains(from) || box.clip(from, path.get(tick + 1)).isPresent()) {
					return true;
				}
			}
		}

		return false;
	}

	/** How far past its target it makes sure the arrow wouldn't hit a friend. */
	public static final double PAST_TARGET = 6.0;
	/** Room it leaves round a friend beyond where an arrow would just touch it. */
	public static final double FRIEND_ROOM = 0.3;
	public static final double FRIEND_ROOM_PER_BLOCK = 0.015;

	/** On its side: the same battle side or the same group. */
	public static boolean friends(DrifterEntity drifter, DrifterEntity other) {
		if (com.dinglebob.drifter.encounter.Encounter.together(drifter, other)) {
			return true;
		}

		if (com.dinglebob.drifter.world.Battle.inBattle(drifter)) {
			return com.dinglebob.drifter.world.Battle.allies(drifter, other);
		}

		var group = com.dinglebob.drifter.world.DrifterGroups.of(drifter.getUUID());
		return group != null && group == com.dinglebob.drifter.world.DrifterGroups.of(other.getUUID());
	}

	/** An arrow keeps this much of its speed each tick in air (AbstractArrow). */
	public static final double ARROW_INERTIA = 0.99;
	/** How near its line an arrow hits someone (ProjectileUtil's margin). */
	public static final double ARROW_HIT_MARGIN = 0.3;

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
