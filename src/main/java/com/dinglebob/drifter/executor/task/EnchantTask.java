package com.dinglebob.drifter.executor.task;

import java.util.function.Predicate;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.motor.CameraModel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Enchants one item at an enchanting table in reach (section 10.13), through the table's own menu run by the player-hands
 * helper: the item and lapis go in, it reads the three offers for a moment, and takes the strongest one it can afford
 * (the game checks its levels and lapis, takes them, and picks the enchantments). Then the item and the leftover lapis
 * come back out.
 */
public class EnchantTask extends Task {
	/** Reading the offers before picking one. */
	private static final int READ_MIN = 20;
	private static final int READ_MAX = 50;

	private final BlockPos table;
	private final Predicate<ItemStack> item;
	private final String label;
	private int pause = -1;
	private String result = "";

	public EnchantTask(DrifterEntity drifter, BlockPos table, Predicate<ItemStack> item, String label) {
		super(drifter);
		this.table = table;
		this.item = item;
		this.label = label;
	}

	@Override
	public String describe() {
		return "enchant " + this.label;
	}

	/** What it got, in words (for its thoughts). */
	public String result() {
		return this.result;
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 10;
	}

	@Override
	protected Status onTick() {
		if (!this.drifter.level().getBlockState(this.table).is(Blocks.ENCHANTING_TABLE)) {
			return this.fail("the enchanting table is gone");
		}

		if (!Reach.canReach(this.drifter, this.table)) {
			return this.fail("can't reach the enchanting table");
		}

		this.drifter.getKeys().releaseAll();
		this.drifter.getCamera().track(Vec3.atCenterOf(this.table), CameraModel.TurnSpeed.NORMAL);
		if (this.pause < 0) {
			if (!this.drifter.getCamera().isSettled()) {
				return Status.RUNNING;
			}

			this.pause = InventoryPause.roll(this.drifter, READ_MIN, READ_MAX);
			return Status.RUNNING;
		}

		if (--this.pause > 0) {
			return Status.RUNNING;
		}

		DrifterInventory inventory = this.drifter.getInventory();
		int itemSlot = inventory.findSlot(this.item, 0, DrifterInventory.MAIN_SIZE);
		int lapisSlot = inventory.findSlot(stack -> stack.is(Items.LAPIS_LAZULI), 0, DrifterInventory.MAIN_SIZE);
		if (itemSlot < 0) {
			return this.fail("has no " + this.label);
		}

		if (lapisSlot < 0) {
			return this.fail("has no lapis");
		}

		String outcome = FakePlayerBridge.act(this.drifter, fake -> {
			EnchantmentMenu menu = new EnchantmentMenu(0, fake.getInventory(), ContainerLevelAccess.create(this.drifter.level(), this.table));
			var hand = fake.getInventory().items;
			// Into the table: the item and the lapis (Slot#set updates the offers, as a click would).
			ItemStack tool = hand.get(itemSlot);
			ItemStack lapis = hand.get(lapisSlot);
			hand.set(itemSlot, ItemStack.EMPTY);
			hand.set(lapisSlot, ItemStack.EMPTY);
			menu.getSlot(0).set(tool);
			menu.getSlot(1).set(lapis);
			int button = -1;
			for (int i = 2; i >= 0; i--) {
				if (menu.costs[i] > 0 && fake.experienceLevel >= menu.costs[i] && fake.experienceLevel >= i + 1 && lapis.getCount() >= i + 1) {
					button = i;
					break;
				}
			}

			String said;
			int cost = button >= 0 ? menu.costs[button] : 0;
			if (button >= 0 && menu.clickMenuButton(fake, button)) {
				said = "enchanted its " + this.label + " (a level " + cost + " offer, " + (button + 1) + " levels and lapis spent)";
			} else {
				said = "";
			}

			// Back out, into the slots they came from.
			ItemStack back = menu.getSlot(0).getItem();
			ItemStack leftover = menu.getSlot(1).getItem();
			menu.getSlot(0).set(ItemStack.EMPTY);
			menu.getSlot(1).set(ItemStack.EMPTY);
			hand.set(itemSlot, back);
			if (!leftover.isEmpty()) {
				hand.set(lapisSlot, leftover);
			}

			return said;
		});
		if (outcome.isEmpty()) {
			return this.fail("no offer it can afford");
		}

		this.result = outcome + describeEnchantments(inventory.getItem(itemSlot));
		this.drifter.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		return Status.SUCCESS;
	}

	private static String describeEnchantments(ItemStack stack) {
		StringBuilder words = new StringBuilder();
		for (var entry : stack.getEnchantments().entrySet()) {
			words.append(words.isEmpty() ? ": " : ", ");
			words.append(net.minecraft.world.item.enchantment.Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString());
		}

		return words.toString();
	}

	@Override
	protected void onStop() {
		this.drifter.getCamera().clearTarget();
	}
}
