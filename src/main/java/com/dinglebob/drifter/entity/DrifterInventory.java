package com.dinglebob.drifter.entity;

import java.util.List;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Drifter's player-style inventory (DESIGN.md sections 2 and 3): 36 main slots (0 to 8 are the hotbar),
 * 4 armor slots and 1 offhand slot, numbered exactly like the vanilla player inventory
 * (main 0-35, armor 36-39 as feet/legs/chest/head, offhand 40). Saved with the same slot ids
 * vanilla uses (armor 100+, offhand 150).
 *
 * <p>The entity reads its equipment slots from here, so the held item, armor and offhand are always
 * this inventory's contents (see {@link DrifterEntity#getItemBySlot}).
 */
public class DrifterInventory implements Container {
	public static final int MAIN_SIZE = 36;
	public static final int HOTBAR_SIZE = 9;
	public static final int ARMOR_SIZE = 4;
	public static final int OFFHAND_SLOT = MAIN_SIZE + ARMOR_SIZE;
	/** Slow hotbar switch: a player switches instantly, the Drifter adds a 1 to 3 tick human delay (section 3). */
	public static final int MIN_SWITCH_DELAY = 1;
	public static final int MAX_SWITCH_DELAY = 3;

	private final DrifterEntity owner;
	final NonNullList<ItemStack> items = NonNullList.withSize(MAIN_SIZE, ItemStack.EMPTY);
	final NonNullList<ItemStack> armor = NonNullList.withSize(ARMOR_SIZE, ItemStack.EMPTY);
	final NonNullList<ItemStack> offhand = NonNullList.withSize(1, ItemStack.EMPTY);
	private final List<NonNullList<ItemStack>> compartments = ImmutableList.of(this.items, this.armor, this.offhand);

	private int selected;
	private int pendingSelected = -1;
	private int switchDelay;

	public DrifterInventory(DrifterEntity owner) {
		this.owner = owner;
	}

	public static boolean isHotbarSlot(int slot) {
		return slot >= 0 && slot < HOTBAR_SIZE;
	}

	public int getSelected() {
		return this.selected;
	}

	public ItemStack getSelectedItem() {
		return this.items.get(this.selected);
	}

	/** Asks to switch to a hotbar slot. Takes effect after the human switch delay. */
	public void requestSelect(int slot) {
		if (!isHotbarSlot(slot) || slot == this.selected && this.pendingSelected == -1) {
			return;
		}

		this.pendingSelected = slot;
		this.switchDelay = MIN_SWITCH_DELAY + this.owner.getRandom().nextInt(MAX_SWITCH_DELAY - MIN_SWITCH_DELAY + 1);
	}

	/** Selects a hotbar slot after exactly {@code delay} ticks (one scroll-wheel notch is a tick). */
	public void requestSelect(int slot, int delay) {
		if (!isHotbarSlot(slot) || slot == this.selected && this.pendingSelected == -1) {
			return;
		}

		this.pendingSelected = slot;
		this.switchDelay = Math.max(1, delay);
	}

	public boolean isSwitching() {
		return this.pendingSelected != -1;
	}

	/** Sets the selected slot with no delay. Only for loading and debug commands. */
	public void setSelectedImmediately(int slot) {
		if (isHotbarSlot(slot)) {
			this.selected = slot;
			this.pendingSelected = -1;
		}
	}

	public ItemStack getArmor(EquipmentSlot slot) {
		return this.armor.get(slot.getIndex());
	}

	public ItemStack getOffhand() {
		return this.offhand.get(0);
	}

	public List<ItemStack> armorList() {
		return this.armor;
	}

	public void tick() {
		if (this.pendingSelected != -1 && --this.switchDelay <= 0) {
			this.selected = this.pendingSelected;
			this.pendingSelected = -1;
		}

		for (NonNullList<ItemStack> list : this.compartments) {
			for (int i = 0; i < list.size(); i++) {
				if (!list.get(i).isEmpty()) {
					list.get(i).inventoryTick(this.owner.level(), this.owner, i, this.selected == i);
				}
			}
		}
	}

	private boolean hasRemainingSpaceForItem(ItemStack existing, ItemStack toAdd) {
		return !existing.isEmpty()
			&& ItemStack.isSameItemSameComponents(existing, toAdd)
			&& existing.isStackable()
			&& existing.getCount() < this.getMaxStackSize(existing);
	}

	public int getFreeSlot() {
		for (int i = 0; i < this.items.size(); i++) {
			if (this.items.get(i).isEmpty()) {
				return i;
			}
		}

		return -1;
	}

	/** Same search order as the player inventory: held slot, offhand, then main slots in order. */
	public int getSlotWithRemainingSpace(ItemStack stack) {
		if (this.hasRemainingSpaceForItem(this.getItem(this.selected), stack)) {
			return this.selected;
		}

		if (this.hasRemainingSpaceForItem(this.getItem(OFFHAND_SLOT), stack)) {
			return OFFHAND_SLOT;
		}

		for (int i = 0; i < this.items.size(); i++) {
			if (this.hasRemainingSpaceForItem(this.items.get(i), stack)) {
				return i;
			}
		}

		return -1;
	}

	/**
	 * Adds as much of the stack as fits, shrinking it by the amount taken, like
	 * {@link net.minecraft.world.entity.player.Inventory#add(ItemStack)}.
	 *
	 * @return true if the last pass took something (vanilla semantics: a partial pickup shrinks the stack but returns false)
	 */
	public boolean add(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		if (stack.isDamaged()) {
			int slot = this.getFreeSlot();
			if (slot < 0) {
				return false;
			}

			this.items.set(slot, stack.copyAndClear());
			this.items.get(slot).setPopTime(5);
			return true;
		}

		int remaining;
		do {
			remaining = stack.getCount();
			stack.setCount(this.addResource(stack));
		} while (!stack.isEmpty() && stack.getCount() < remaining);
		return stack.getCount() < remaining;
	}

	private int addResource(ItemStack stack) {
		int slot = this.getSlotWithRemainingSpace(stack);
		if (slot == -1) {
			slot = this.getFreeSlot();
		}

		if (slot == -1) {
			return stack.getCount();
		}

		int count = stack.getCount();
		ItemStack existing = this.getItem(slot);
		if (existing.isEmpty()) {
			existing = stack.copyWithCount(0);
			this.setItem(slot, existing);
		}

		int moved = Math.min(count, this.getMaxStackSize(existing) - existing.getCount());
		if (moved == 0) {
			return count;
		}

		existing.grow(moved);
		existing.setPopTime(5);
		return count - moved;
	}

	/** First slot in [from, to) whose stack matches, or -1. */
	public int findSlot(Predicate<ItemStack> filter, int from, int to) {
		for (int slot = from; slot < to; slot++) {
			ItemStack stack = this.getItem(slot);
			if (!stack.isEmpty() && filter.test(stack)) {
				return slot;
			}
		}

		return -1;
	}

	/** Total count of matching items in the main inventory. */
	public int count(Predicate<ItemStack> filter) {
		int total = 0;
		for (ItemStack stack : this.items) {
			if (!stack.isEmpty() && filter.test(stack)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	public void swap(int slotA, int slotB) {
		ItemStack a = this.getItem(slotA);
		this.setItem(slotA, this.getItem(slotB));
		this.setItem(slotB, a);
	}

	/** The F key: swaps the held item and the offhand item. */
	public void swapHands() {
		this.swap(this.selected, OFFHAND_SLOT);
	}

	public List<ItemStack> removeAll() {
		NonNullList<ItemStack> removed = NonNullList.create();
		for (NonNullList<ItemStack> list : this.compartments) {
			for (int i = 0; i < list.size(); i++) {
				if (!list.get(i).isEmpty()) {
					removed.add(list.get(i));
					list.set(i, ItemStack.EMPTY);
				}
			}
		}

		return removed;
	}

	public ListTag save(HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		saveCompartment(registries, list, this.items, 0);
		saveCompartment(registries, list, this.armor, 100);
		saveCompartment(registries, list, this.offhand, 150);
		return list;
	}

	private static void saveCompartment(HolderLookup.Provider registries, ListTag list, NonNullList<ItemStack> compartment, int slotOffset) {
		for (int i = 0; i < compartment.size(); i++) {
			if (!compartment.get(i).isEmpty()) {
				CompoundTag tag = new CompoundTag();
				tag.putByte("Slot", (byte)(i + slotOffset));
				list.add(compartment.get(i).save(registries, tag));
			}
		}
	}

	public void load(HolderLookup.Provider registries, ListTag list) {
		this.clearContent();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag tag = list.getCompound(i);
			int slot = tag.getByte("Slot") & 255;
			ItemStack stack = ItemStack.parse(registries, tag).orElse(ItemStack.EMPTY);
			if (slot < this.items.size()) {
				this.items.set(slot, stack);
			} else if (slot >= 100 && slot < this.armor.size() + 100) {
				this.armor.set(slot - 100, stack);
			} else if (slot >= 150 && slot < this.offhand.size() + 150) {
				this.offhand.set(slot - 150, stack);
			}
		}
	}

	private NonNullList<ItemStack> compartmentFor(int slot) {
		if (slot < MAIN_SIZE) {
			return this.items;
		}

		return slot < OFFHAND_SLOT ? this.armor : this.offhand;
	}

	private static int indexInCompartment(int slot) {
		if (slot < MAIN_SIZE) {
			return slot;
		}

		return slot < OFFHAND_SLOT ? slot - MAIN_SIZE : 0;
	}

	@Override
	public int getContainerSize() {
		return MAIN_SIZE + ARMOR_SIZE + 1;
	}

	@Override
	public boolean isEmpty() {
		for (NonNullList<ItemStack> list : this.compartments) {
			for (ItemStack stack : list) {
				if (!stack.isEmpty()) {
					return false;
				}
			}
		}

		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		if (slot < 0 || slot >= this.getContainerSize()) {
			return ItemStack.EMPTY;
		}

		return this.compartmentFor(slot).get(indexInCompartment(slot));
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		if (slot < 0 || slot >= this.getContainerSize()) {
			return ItemStack.EMPTY;
		}

		return ContainerHelper.removeItem(this.compartmentFor(slot), indexInCompartment(slot), amount);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		if (slot < 0 || slot >= this.getContainerSize()) {
			return ItemStack.EMPTY;
		}

		NonNullList<ItemStack> list = this.compartmentFor(slot);
		int index = indexInCompartment(slot);
		ItemStack stack = list.get(index);
		list.set(index, ItemStack.EMPTY);
		return stack;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot >= 0 && slot < this.getContainerSize()) {
			this.compartmentFor(slot).set(indexInCompartment(slot), stack);
		}
	}

	@Override
	public void setChanged() {
	}

	@Override
	public boolean stillValid(Player player) {
		return false;
	}

	@Override
	public void clearContent() {
		for (NonNullList<ItemStack> list : this.compartments) {
			list.clear();
		}
	}
}
