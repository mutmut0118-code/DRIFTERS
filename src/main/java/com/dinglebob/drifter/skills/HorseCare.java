package com.dinglebob.drifter.skills;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.compat.IcyHorses;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;

import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.Item;

/**
 * Looking after its horse (horse culture: docs/superpowers/specs/2026-11-16-horse-culture-design.md, "Bond"): with Icy's
 * Better Horses, once a day by its horse it offers it one of Icy's treats it has (a horse has a favourite of them, found
 * by trying, as a player finds it): its favourite, Icy's own reward (its bond, the treat known); another, it notes the horse
 * didn't care for that one and tries something else another day. Riding together already grows the bond (Icy counts a
 * Drifter's riding).
 */
public final class HorseCare extends Goals.Goal {
	/** A treat a day at most (Icy's own cooldown is about that). */
	private static final long EVERY = 24000L;
	/** Its horse this near to go and see to it. */
	private static final double NEAR = 24.0;

	/** Its horse, and the day it last gave it something. */
	private final Map<UUID, Long> lastFed = new HashMap<>();
	/** The treats each horse didn't care for (tried already). */
	private final Map<UUID, Set<Item>> notIt = new HashMap<>();
	@Nullable
	private AbstractHorse horse;
	@Nullable
	private Item offer;

	@Override
	public String id() {
		return "horsecare";
	}

	@Override
	public String name(Goals.Context context) {
		return "see to its horse";
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		this.horse = null;
		this.offer = null;
		if (!IcyHorses.present() || drifter.isPassenger() || context.threat().urgent() || drifter.inCombat() || Schedule.isNight(drifter)) {
			return 0.0;
		}

		AbstractHorse own = HorseGoal.ownHorse(drifter);
		long now = drifter.level().getGameTime();
		if (own == null || own.distanceTo(drifter) > NEAR || now - this.lastFed.getOrDefault(own.getUUID(), Long.MIN_VALUE / 2) < EVERY) {
			return 0.0;
		}

		// Its favourite, if it knows it and has it; else one it hasn't tried on this horse yet.
		Item known = IcyHorses.favouriteTreat(own);
		Set<Item> tried = this.notIt.getOrDefault(own.getUUID(), Set.of());
		for (Item item : IcyHorses.treats()) {
			if ((known == null ? !tried.contains(item) : item == known) && drifter.getInventory().count(stack -> stack.is(item)) > 0) {
				this.horse = own;
				this.offer = item;
				// (A little thing between its chores.)
				return 0.25;
			}
		}

		return 0.0;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return this.horse != null && this.offer != null ? 1.0 : 0.0;
	}

	@Nullable
	@Override
	public Activity start(Goals.Context context) {
		// (Forced, or picked a while after it was weighed: what to give and to whom worked out again.)
		this.urgency(context);
		return this.horse == null || this.offer == null ? null : new Feed(context.drifter(), this.horse, this.offer, this);
	}

	/** A Drifter's own horse killed: it grieves, its stall let go, its town told ({@link com.dinglebob.drifter.api.DrifterEvents#HORSE_LOST}). */
	public static void register() {
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof AbstractHorse horse && entity.level() instanceof net.minecraft.server.level.ServerLevel level) {
				UUID owner = IcyHorses.owner(horse);
				if (owner == null) {
					owner = horse.getOwnerUUID();
				}

				if (owner != null && level.getEntity(owner) instanceof DrifterEntity drifter) {
					com.dinglebob.drifter.Drifter.LOGGER.info("[horse] {}'s horse {} died: {} (by {})", drifter.getName().getString(), horse.getName().getString(),
						source.getMsgId(), source.getEntity() == null ? "nothing" : source.getEntity().getName().getString());
					lost(drifter, horse, "killed");
				}
			}
		});
	}

	/** Its horse gone: sad for a good while, its stall free, its town told. */
	public static void lost(DrifterEntity drifter, AbstractHorse horse, String how) {
		if (!horse.getUUID().equals(drifter.getDrifterBrain().horse())) {
			return;
		}

		String name = horse.getName().getString();
		drifter.getDrifterBrain().setHorse(null, null);
		drifter.getDrifterBrain().setStall(null);
		drifter.getPersonality().feel(com.dinglebob.drifter.personality.Mood.GRIEVING, 0.8, "lost its horse " + name);
		drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "lost its horse " + name + " (" + how + ")");
		com.dinglebob.drifter.api.DrifterEvents.HORSE_LOST.invoker().onHorseLost(drifter, name, how);
	}

	/** It gave this horse this treat: liked or not. */
	void fed(DrifterEntity drifter, AbstractHorse horse, Item item, boolean liked) {
		this.lastFed.put(horse.getUUID(), drifter.level().getGameTime());
		if (!liked) {
			this.notIt.computeIfAbsent(horse.getUUID(), id -> new HashSet<>()).add(item);
		}
	}

	/** Up to its horse, then the treat from its hand (one used up). */
	static final class Feed extends Activity {
		private final AbstractHorse horse;
		private final Item item;
		private final HorseCare care;
		private boolean walked;

		Feed(DrifterEntity drifter, AbstractHorse horse, Item item, HorseCare care) {
			super(drifter);
			this.horse = horse;
			this.item = item;
			this.care = care;
		}

		@Override
		public String describe() {
			return "give " + this.horse.getName().getString() + " a treat";
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60;
		}

		@Override
		protected void stepDone(Task step, Status status) {
		}

		@Override
		protected Status next() {
			if (!this.horse.isAlive()) {
				return this.fail("its horse is gone");
			}

			// (Within a player's reach of entities.)
			if (this.drifter.distanceTo(this.horse) > 2.5) {
				if (this.walked && this.failures() > 2) {
					return this.fail("can't get to its horse");
				}

				this.walked = true;
				return this.begin(new NavigateTask(this.drifter, this.horse.position(), 1.5, "walk over to its horse"));
			}

			DrifterInventory inventory = this.drifter.getInventory();
			int slot = inventory.findSlot(stack -> stack.is(this.item), 0, DrifterInventory.MAIN_SIZE);
			if (slot < 0) {
				return this.fail("has no " + this.item.getDescription().getString().toLowerCase(java.util.Locale.ROOT));
			}

			this.drifter.getLookControl().setLookAt(this.horse, 30.0F, 30.0F);
			boolean liked = IcyHorses.isTreat(this.horse, inventory.getItem(slot));
			inventory.getItem(slot).shrink(1);
			this.drifter.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			String what = this.item.getDescription().getString().toLowerCase(java.util.Locale.ROOT);
			String name = this.horse.getName().getString();
			if (liked) {
				IcyHorses.fedTreat(this.horse);
				this.think("gave " + name + " a " + what + ", its favourite");
			} else {
				this.think(name + " wasn't keen on the " + what);
			}

			this.care.fed(this.drifter, this.horse, this.item, liked);
			return Status.SUCCESS;
		}
	}
}
