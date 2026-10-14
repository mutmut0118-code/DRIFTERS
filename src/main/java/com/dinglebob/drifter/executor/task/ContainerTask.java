package com.dinglebob.drifter.executor.task;

import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.Containers;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Container (DESIGN.md section 8): opens a chest, barrel, furnace or other container in reach, moves items in or
 * out a stack per click, and closes it. Opening is a real right-click through the fake player, so protection mods
 * can refuse it and piglins notice. Chest lids and barrels animate while it's open.
 */
public class ContainerTask extends Task {
	public enum Mode {
		PUT, TAKE
	}

	/** A container menu closes when the player is more than this far away. */
	private static final double MENU_RANGE = 8.0;

	private enum Phase {
		AIM, LOOK_OVER, TRANSFER
	}

	private final BlockPos pos;
	private final Mode mode;
	private final Predicate<ItemStack> filter;
	private final String label;
	private final int amount;
	/** Container slots allowed for this transfer (a furnace's input or fuel slot), or null for any. */
	@Nullable
	private final int[] slots;
	private final BlockAim aim;
	/** Putting away exact amounts from particular stacks (storing at home): stack -> how many of it to put in. */
	@Nullable
	private java.util.IdentityHashMap<ItemStack, Integer> quota;
	private Phase phase = Phase.AIM;
	@Nullable
	private Container container;
	private boolean opened;
	private int wait;
	private int moved;

	public ContainerTask(DrifterEntity drifter, BlockPos pos, Mode mode, Predicate<ItemStack> filter, String label, int amount, @Nullable int[] slots) {
		super(drifter);
		this.pos = pos.immutable();
		this.mode = mode;
		this.filter = filter;
		this.label = label;
		this.amount = amount;
		this.slots = slots;
		this.aim = new BlockAim(drifter, this.pos, null, CameraModel.TurnSpeed.NORMAL);
	}

	/**
	 * One visit to a chest putting away these amounts from these stacks (section 10.18, going home): like shift-clicking a
	 * few things in, and leaving part of a stack on it.
	 */
	public static ContainerTask deposit(DrifterEntity drifter, BlockPos pos, java.util.Map<ItemStack, Integer> amounts, String label) {
		ContainerTask task = new ContainerTask(drifter, pos, Mode.PUT, stack -> amounts.containsKey(stack), label, Integer.MAX_VALUE, null);
		task.quota = new java.util.IdentityHashMap<>(amounts);
		return task;
	}

	@Override
	public String describe() {
		String what = this.amount == Integer.MAX_VALUE ? "all " + this.label : this.amount + " " + this.label;
		return (this.mode == Mode.PUT ? "put " + what + " into " : "take " + what + " from ") + "the container at " + this.pos.toShortString();
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 30;
	}

	@Override
	protected void onStart() {
		// Sneak-clicking would skip the container and use the held item instead.
		this.drifter.getKeys().set(VirtualKeys.Key.SNEAK, false);
	}

	@Override
	protected Status onTick() {
		if (this.opened && this.drifter.position().distanceTo(this.pos.getCenter()) > MENU_RANGE) {
			return this.fail("moved too far away");
		}

		switch (this.phase) {
			case AIM -> {
				BlockAim.Result result = this.aim.tick();
				if (result == BlockAim.Result.UNREACHABLE) {
					return this.fail("can't see or reach it");
				}

				return result == BlockAim.Result.ON_TARGET ? this.open() : Status.RUNNING;
			}
			case LOOK_OVER -> {
				if (--this.wait <= 0) {
					this.phase = Phase.TRANSFER;
				}

				return Status.RUNNING;
			}
			default -> {
				if (--this.wait > 0) {
					return Status.RUNNING;
				}

				this.wait = InventoryPause.CLICK_TICKS;
				// Looked up again each click: a double chest is a fresh wrapper each time, and the block may be gone.
				this.container = Containers.get(this.drifter.level(), this.pos);
				if (this.container == null) {
					return this.fail("the container is gone");
				}

				int movedNow = this.mode == Mode.PUT ? this.putOneStack() : this.takeOneStack();
				this.moved += movedNow;
				if (movedNow == 0 || this.moved >= this.amount) {
					if (this.moved == 0) {
						return this.fail(this.mode == Mode.PUT ? "nothing to put in, or no room" : "nothing to take, or no room");
					}

					return Status.SUCCESS;
				}

				return Status.RUNNING;
			}
		}
	}

	private Status open() {
		BlockHitResult hit = Reach.crosshair(this.drifter);
		if (hit == null) {
			return Status.RUNNING;
		}

		if (Containers.get(this.drifter.level(), this.pos) == null) {
			return this.fail("it won't open (blocked, an ender chest, or not a container)");
		}

		InteractionResult clicked = FakePlayerBridge.act(this.drifter,
			fake -> fake.gameMode.useItemOn(fake, this.drifter.level(), fake.getMainHandItem(), InteractionHand.MAIN_HAND, hit));
		this.container = Containers.get(this.drifter.level(), this.pos);
		if (!clicked.consumesAction() || this.container == null) {
			return this.fail("it won't open (protected?)");
		}

		this.drifter.swing(InteractionHand.MAIN_HAND);
		this.drifter.getKeys().releaseAll();
		this.drifter.getSenses().sawContents(this.pos, this.container);
		Containers.playOpen(this.drifter.level(), this.pos);
		this.opened = true;
		this.wait = InventoryPause.roll(this.drifter, InventoryPause.CONTAINER_MIN, InventoryPause.CONTAINER_MAX);
		this.phase = Phase.LOOK_OVER;
		return Status.RUNNING;
	}

	private boolean slotAllowed(int slot) {
		if (this.slots == null) {
			return true;
		}

		for (int allowed : this.slots) {
			if (allowed == slot) {
				return true;
			}
		}

		return false;
	}

	/** Shift-click one matching stack from the Drifter's main inventory into the container. */
	private int putOneStack() {
		DrifterInventory inventory = this.drifter.getInventory();
		int from = this.quota == null ? inventory.findSlot(this.filter, 0, DrifterInventory.MAIN_SIZE)
			: inventory.findSlot(stack -> this.quota.getOrDefault(stack, 0) > 0, 0, DrifterInventory.MAIN_SIZE);
		if (from < 0) {
			return 0;
		}

		ItemStack stack = inventory.getItem(from);
		int wanted = Math.min(stack.getCount(), this.quota == null ? this.amount - this.moved : this.quota.get(stack));
		int before = wanted;
		ItemStack moving = stack.split(wanted);
		for (int slot = 0; slot < this.container.getContainerSize() && !moving.isEmpty(); slot++) {
			if (!this.slotAllowed(slot) || !this.container.canPlaceItem(slot, moving)) {
				continue;
			}

			ItemStack existing = this.container.getItem(slot);
			int limit = Math.min(this.container.getMaxStackSize(moving), moving.getMaxStackSize());
			if (existing.isEmpty()) {
				int count = Math.min(limit, moving.getCount());
				this.container.setItem(slot, moving.split(count));
			} else if (ItemStack.isSameItemSameComponents(existing, moving) && existing.getCount() < limit) {
				int count = Math.min(limit - existing.getCount(), moving.getCount());
				existing.grow(count);
				moving.shrink(count);
			}
		}

		// Whatever didn't fit goes back where it came from.
		stack.grow(moving.getCount());
		this.container.setChanged();
		int moved = before - moving.getCount();
		if (this.quota != null) {
			// Done with this stack whether it all fit or not (a full chest ends the visit on the next click).
			this.quota.put(stack, moved == before ? 0 : -1);
			if (moved < before) {
				return 0;
			}
		}

		return moved;
	}

	/** Shift-click one matching stack from the container into the Drifter's inventory. */
	private int takeOneStack() {
		for (int slot = 0; slot < this.container.getContainerSize(); slot++) {
			ItemStack stack = this.container.getItem(slot);
			if (stack.isEmpty() || !this.slotAllowed(slot) || !this.filter.test(stack)) {
				continue;
			}

			int wanted = Math.min(stack.getCount(), this.amount - this.moved);
			ItemStack moving = stack.copyWithCount(wanted);
			this.drifter.getInventory().add(moving);
			int taken = wanted - moving.getCount();
			if (taken > 0) {
				stack.shrink(taken);
				this.container.setChanged();
				return taken;
			}
		}

		return 0;
	}

	@Override
	protected void onStop() {
		if (this.opened) {
			Container current = Containers.get(this.drifter.level(), this.pos);
			if (current != null) {
				this.drifter.getSenses().sawContents(this.pos, current);
			}

			Containers.playClose(this.drifter.level(), this.pos);
		}
	}
}
