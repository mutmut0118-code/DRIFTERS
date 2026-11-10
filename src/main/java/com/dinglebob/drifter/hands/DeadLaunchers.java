package com.dinglebob.drifter.hands;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Launchers that never loose (ruling R23): a modded bow or crossbow whose release doesn't work for a Drifter (one that only
 * fires for real players, or one that fails on a dedicated server), and a trident that never leaves the hand (ThrowTridentTask:
 * a modded one whose own release does nothing for it). Each release or shot is watched: a projectile of its own
 * coming into the world, the launcher wearing or the target hurt means it worked. A modded one that looses nothing is first
 * held longer next time, as a player tries a longer draw (ruling R29: a draw or load longer than the game's own); once even
 * the longest hold looses nothing, each failure counts, and after a few in a row it leaves that item alone for a while, with
 * one thought, instead of drawing it for ever. One per Drifter; not saved (after a reload it learns the item again).
 */
public final class DeadLaunchers {
	/** Failures in a row of one item (at its longest hold) before it gives up on that item for now. */
	public static final int MAX_FAILURES = 3;
	/** How long it leaves a launcher that never looses before trying it again (ten minutes). */
	public static final long REST_TICKS = 20L * 60 * 10;
	/** A shot may leave this long after letting go (some mods loose a moment later). */
	public static final int LOOSE_WINDOW_TICKS = 10;
	/** A modded launcher that loosed nothing is held this much longer next time... */
	public static final int HOLD_STEP_TICKS = 20;
	/** ...up to this much longer than the game's own draw or load (a draw of 100 ticks, five seconds, for a bow). */
	public static final int MAX_EXTRA_HOLD_TICKS = 80;

	/** The game tick its last projectile came into the world (its own, or its helper player's). */
	private long loosedAt = Long.MIN_VALUE;
	/** Items that loosed nothing lately, and how many times in a row each (made the first time one fails). */
	@Nullable
	private java.util.Map<Item, Integer> failures;
	/** Items given up on, to the tick it tries them again (made the first time one is given up on). */
	@Nullable
	private java.util.Map<Item, Long> resting;
	/** Modded items that needed a longer hold, and how much longer (made the first time one does). */
	@Nullable
	private java.util.Map<Item, Integer> holds;

	/** The release or shot being watched (null: none), when, the launcher's wear and its target's health then. */
	@Nullable
	private ItemStack watching;
	private long watchingAt;
	private int watchingWear;
	@Nullable
	private LivingEntity watchingTarget;
	private float watchingHealth;
	/** A release after a hold (a bow's draw): nothing loosed may mean it wasn't held long enough. */
	private boolean watchingHold;

	/** Every projectile that comes into the world: a Drifter's own (or its helper's) marks the tick it loosed. */
	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Projectile shot) {
				DrifterEntity drifter = FakePlayerBridge.ownerOf(shot.getOwner());
				if (drifter != null) {
					drifter.getDeadLaunchers().loosedAt = level.getGameTime();
				}
			}
		});
	}

	/** The game's own bow or crossbow (its timing is known: no longer hold to learn). */
	public static boolean vanilla(ItemStack stack) {
		Class<?> type = stack.getItem().getClass();
		return type == BowItem.class || type == CrossbowItem.class;
	}

	/** Ticks past the game's own draw or load it holds this launcher, as it has learned (0 for most). */
	public int extraHold(ItemStack stack) {
		if (this.holds == null) {
			return 0;
		}

		Integer extra = this.holds.get(stack.getItem());
		return extra == null ? 0 : extra;
	}

	/**
	 * It's letting go of this launcher at {@code target} now ({@code hold}: after a draw it might not have held long enough).
	 * A watch still open is settled first, as a failure if nothing came of it. True if that earlier one turned out to work.
	 */
	public boolean watch(DrifterEntity drifter, ItemStack launcher, LivingEntity target, boolean hold) {
		boolean worked = this.settle(drifter, true);
		this.watching = launcher;
		this.watchingAt = drifter.level().getGameTime();
		this.watchingWear = launcher.getDamageValue();
		this.watchingTarget = target;
		this.watchingHealth = target.getHealth();
		this.watchingHold = hold;
		return worked;
	}

	/** Each tick it shoots: settles the watch once it worked or its window is over. True if it just turned out to work. */
	public boolean check(DrifterEntity drifter) {
		return this.settle(drifter, false);
	}

	/** Nothing left the launcher at all (a crossbow still loaded after firing): the watch is a failure now. */
	public void missed(DrifterEntity drifter) {
		ItemStack launcher = this.watching;
		this.clearWatch();
		if (launcher != null) {
			this.failed(drifter, launcher);
		}
	}

	private boolean settle(DrifterEntity drifter, boolean now) {
		ItemStack launcher = this.watching;
		if (launcher == null) {
			return false;
		}

		LivingEntity target = this.watchingTarget;
		if (this.loosedAt >= this.watchingAt || launcher.getDamageValue() != this.watchingWear || target != null && target.getHealth() < this.watchingHealth) {
			this.clearWatch();
			this.worked(launcher);
			return true;
		}

		if (!now && drifter.level().getGameTime() - this.watchingAt < LOOSE_WINDOW_TICKS) {
			return false;
		}

		boolean hold = this.watchingHold;
		this.clearWatch();
		if (hold) {
			this.failedHold(drifter, launcher);
		} else {
			this.failed(drifter, launcher);
		}

		return false;
	}

	private void clearWatch() {
		this.watching = null;
		this.watchingTarget = null;
	}

	/** It has given up on this launcher for now. */
	public static boolean resting(DrifterEntity drifter, ItemStack stack) {
		DeadLaunchers dead = drifter.getDeadLaunchers();
		return dead.resting != null && dead.restingNow(stack.getItem(), drifter.level().getGameTime());
	}

	private boolean restingNow(Item item, long now) {
		Long until = this.resting.get(item);
		if (until == null) {
			return false;
		}

		// (Rested long enough, or the clock went back: it tries it again.)
		if (now >= until || until - now > REST_TICKS) {
			this.resting.remove(item);
			return false;
		}

		return true;
	}

	/** This launcher loosed: whatever it had against it is forgotten (a longer hold it learned stays). */
	public void worked(ItemStack stack) {
		if (this.failures != null) {
			this.failures.remove(stack.getItem());
		}
	}

	/**
	 * A draw or load of this launcher, held as long as it holds it now, loosed nothing: a modded one is held longer next time
	 * (ruling R29); at the longest hold, or for the game's own, it's a failure.
	 */
	public void failedHold(DrifterEntity drifter, ItemStack stack) {
		if (!vanilla(stack)) {
			int extra = this.extraHold(stack);
			if (extra < MAX_EXTRA_HOLD_TICKS) {
				if (this.holds == null) {
					this.holds = new java.util.HashMap<>();
				}

				int longer = Math.min(MAX_EXTRA_HOLD_TICKS, extra + HOLD_STEP_TICKS);
				this.holds.put(stack.getItem(), longer);
				com.dinglebob.drifter.Drifter.LOGGER.debug("[Drifter] {}: {} loosed nothing; holding it {} ticks longer next time", drifter.getName().getString(),
					net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()), longer);
				return;
			}
		}

		this.failed(drifter, stack);
	}

	/** This launcher loosed nothing however it was used: after {@link #MAX_FAILURES} in a row it gives up on it for now. */
	public void failed(DrifterEntity drifter, ItemStack stack) {
		Item item = stack.getItem();
		if (this.failures == null) {
			this.failures = new java.util.HashMap<>();
		}

		int count = this.failures.merge(item, 1, Integer::sum);
		if (count < MAX_FAILURES) {
			return;
		}

		this.failures.remove(item);
		if (this.resting == null) {
			this.resting = new java.util.HashMap<>();
		}

		long now = drifter.level().getGameTime();
		this.resting.put(item, now + REST_TICKS);
		drifter.getMemory().thoughts().add(now, "gave up on " + stack.getHoverName().getString()
			+ (stack.getItem() instanceof net.minecraft.world.item.TridentItem ? ": it won't throw" : ": it won't shoot"));
	}
}
