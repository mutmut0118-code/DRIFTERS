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

/** Bows and crossbows (DESIGN.md section 13 "Ranged mechanics"): what it shoots with and when a bow beats a sword. */
public final class Archery {
	/** A full draw (BowItem): 20 ticks, 1 s. */
	public static final int FULL_DRAW_TICKS = 20;
	/** Arrow speed at a full draw, blocks a tick (power 1 x 3). */
	public static final double ARROW_SPEED = 3.0;
	/** A crossbow's arrow speed, blocks a tick (CrossbowItem's arrow power; the same at any load, no draw to scale it). */
	public static final double CROSSBOW_ARROW_SPEED = 3.15;
	/** A crossbow's firework rocket speed (CrossbowItem's firework power). */
	public static final double CROSSBOW_FIREWORK_SPEED = 1.6;
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

	/** It has a bow or crossbow and something to shoot from it, or a crossbow already loaded (not one it gave up on). */
	public static boolean ready(DrifterEntity drifter) {
		return ready(drifter, !ammo(drifter).isEmpty());
	}

	/** The same, knowing whether it has arrows. */
	public static boolean ready(DrifterEntity drifter, boolean ammo) {
		DrifterInventory inventory = drifter.getInventory();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			if (canShoot(drifter, inventory.getItem(slot), ammo)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * It could shoot this now: a bow or crossbow with something to shoot from it to hand, or loaded, and not one it gave up
	 * on (DeadLaunchers). {@code ammo}: it has arrows. A modded one that takes something else (its own bolts:
	 * {@code DrifterEntity#getProjectile}) may be shot with that; one that seems to take nothing it has is still tried with
	 * arrows to hand (some shoot with no ammo at all), and given up on if nothing comes of it.
	 */
	public static boolean canShoot(DrifterEntity drifter, ItemStack stack, boolean ammo) {
		if (!com.dinglebob.drifter.hands.Shields.isLauncher(stack) || DeadLaunchers.resting(drifter, stack)) {
			return false;
		}

		return ammo || loaded(stack) || !DeadLaunchers.vanilla(stack) && !drifter.getProjectile(stack).isEmpty();
	}

	/** A modded crossbow whose own use time is longer than this isn't timed by it (it's the "for ever" of a bow). */
	public static final int MAX_LOAD_TICKS = 100;
	/** CrossbowItem's use time is its charge time and this many ticks more. */
	public static final int CROSSBOW_USE_SLACK_TICKS = 3;

	/**
	 * Ticks it holds a crossbow to load it before letting go: its own use time less the few ticks the game's crossbow allows
	 * past its charge (for the vanilla one, exactly its charge time, Quick Charge and all); the vanilla charge time if that
	 * use time says nothing (a modded one held "for ever").
	 */
	public static int loadTicks(ItemStack crossbow, LivingEntity user) {
		int own = crossbow.getUseDuration(user) - CROSSBOW_USE_SLACK_TICKS;
		return own > 0 && own <= MAX_LOAD_TICKS ? own : net.minecraft.world.item.CrossbowItem.getChargeDuration(crossbow, user);
	}

	/** A crossbow with a shot in it (it fires at once, as a player's does). */
	public static boolean loaded(ItemStack stack) {
		return stack.getItem() instanceof net.minecraft.world.item.CrossbowItem && net.minecraft.world.item.CrossbowItem.isCharged(stack);
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

		// (From the tick's shared grid of bodies when it can: milestone 16 crowd pass.)
		var span = new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(3.0);
		java.util.function.Predicate<DrifterEntity> friendly = other -> other != drifter && other.isAlive() && friends(drifter, other);
		var friends = com.dinglebob.drifter.world.BodyGrid.bodiesOf(drifter.level(), DrifterEntity.class, span, friendly);
		if (friends == null) {
			friends = drifter.level().getEntitiesOfClass(DrifterEntity.class, span, friendly);
		}

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

	private static boolean shouldShoot(DrifterEntity drifter, LivingEntity target, boolean checkPowers) {
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

		boolean wants = flying(target) || (target instanceof Creeper ? distance >= 4.0 : distance >= MELEE_RANGE);
		return wants && (!checkPowers || heldFire(drifter, target) == null);
	}

	/** A bow beats a sword here ({@link #shouldShoot}), its bow's powers aside. */
	public static boolean wantsBow(DrifterEntity drifter, LivingEntity target) {
		return shouldShoot(drifter, target, false);
	}

	public static boolean shouldShoot(DrifterEntity drifter, LivingEntity target) {
		return shouldShoot(drifter, target, true);
	}

	/**
	 * Why the bow it would take out may not be used on this target as far as it knows (gear powers: an explosive one not with
	 * a friend by the target, nor near home or itself): "held fire: Bow would blow up Pal"; null when it may (or has no bow).
	 */
	@org.jetbrains.annotations.Nullable
	public static String heldFire(DrifterEntity drifter, LivingEntity target) {
		int slot = com.dinglebob.drifter.hands.Shields.bestBowSlot(drifter, target);
		ItemStack bow = slot >= 0 ? drifter.getInventory().getItem(slot) : ItemStack.EMPTY;
		if (!com.dinglebob.drifter.hands.Shields.isLauncher(bow)) {
			return null;
		}

		String why = com.dinglebob.drifter.gear.Powers.whyNot(drifter, bow, target);
		return why == null ? null : "held fire: " + bow.getHoverName().getString() + " " + com.dinglebob.drifter.gear.Powers.heldFireWords(drifter, bow, why);
	}
}
