package com.dinglebob.drifter.hands;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Launchers that never loose (ruling R23): a modded bow or crossbow whose release doesn't work for a Drifter (one that only
 * fires for real players, or one that fails on a dedicated server). A draw or load that ends with nothing loosed counts
 * against that item; after a few in a row it leaves that item alone for a while, with one thought, instead of drawing it
 * for ever. One per Drifter; not saved (after a reload it tries the item again).
 */
public final class DeadLaunchers {
	/** Draws or loads in a row of one item that loosed nothing before it gives up on that item for now. */
	public static final int MAX_FAILURES = 3;
	/** How long it leaves a launcher that never looses before trying it again (ten minutes). */
	public static final long REST_TICKS = 20L * 60 * 10;
	/** A shot may leave this long after letting go (some mods loose a moment later). */
	public static final int LOOSE_WINDOW_TICKS = 10;

	/** The game tick its last projectile came into the world (its own, or its helper player's). */
	private long loosedAt = Long.MIN_VALUE;
	/** The item that loosed nothing lately, and how many times in a row. */
	@Nullable
	private Item failing;
	private int failures;
	/** Items given up on, to the tick it tries them again (made the first time one is given up on). */
	@Nullable
	private java.util.Map<Item, Long> resting;

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

	/** Something it shot came into the world at or after this tick. */
	public boolean loosedSince(long tick) {
		return this.loosedAt >= tick;
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

	/** This launcher loosed: whatever it had against it is forgotten. */
	public void worked(ItemStack stack) {
		if (this.failing == stack.getItem()) {
			this.failing = null;
			this.failures = 0;
		}
	}

	/** A draw or load of this launcher ended with nothing loosed: after {@link #MAX_FAILURES} in a row it gives up on it for now. */
	public void failed(DrifterEntity drifter, ItemStack stack) {
		Item item = stack.getItem();
		if (this.failing != item) {
			this.failing = item;
			this.failures = 0;
		}

		if (++this.failures < MAX_FAILURES) {
			return;
		}

		this.failing = null;
		this.failures = 0;
		if (this.resting == null) {
			this.resting = new java.util.HashMap<>();
		}

		long now = drifter.level().getGameTime();
		this.resting.put(item, now + REST_TICKS);
		drifter.getMemory().thoughts().add(now, "gave up on " + stack.getHoverName().getString() + ": it never shoots");
	}
}
