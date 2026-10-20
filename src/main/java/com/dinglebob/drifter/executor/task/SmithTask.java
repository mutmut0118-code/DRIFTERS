package com.dinglebob.drifter.executor.task;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.skills.Netherite;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A netherite upgrade at a smithing table (DESIGN.md section 19 "Netherite"): the table opened with a right-click, the
 * template, the diamond piece and an ingot in, the netherite piece out (the enchantments and its name kept), as the
 * smithing screen does it (SmithingMenu: the game's own smithing recipe, one of each used up, the anvil-like clang). Its
 * helper player can't open the screen, so the screen's work is done here, with the pauses a player's clicks take.
 */
public class SmithTask extends Task {
	private enum Phase {
		AIM, LOOK_OVER, DONE
	}

	private final BlockPos table;
	private final int baseSlot;
	private final BlockAim aim;
	private Phase phase = Phase.AIM;
	private int wait;
	private String made = "";

	public SmithTask(DrifterEntity drifter, BlockPos table, int baseSlot) {
		super(drifter);
		this.table = table.immutable();
		this.baseSlot = baseSlot;
		this.aim = new BlockAim(drifter, this.table, null, CameraModel.TurnSpeed.NORMAL);
	}

	@Override
	public String describe() {
		return "upgrade its gear to netherite";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 20;
	}

	/** What it made, for the thought log. */
	public String made() {
		return this.made;
	}

	@Override
	protected Status onTick() {
		if (!this.drifter.level().getBlockState(this.table).is(Blocks.SMITHING_TABLE)) {
			return this.fail("the smithing table is gone");
		}

		switch (this.phase) {
			case AIM -> {
				BlockAim.Result result = this.aim.tick();
				if (result == BlockAim.Result.UNREACHABLE) {
					return this.fail("can't see or reach the smithing table");
				}

				if (result != BlockAim.Result.ON_TARGET) {
					return Status.RUNNING;
				}

				BlockHitResult hit = Reach.crosshair(this.drifter);
				if (hit == null) {
					return Status.RUNNING;
				}

				InteractionResult clicked = FakePlayerBridge.act(this.drifter,
					fake -> fake.gameMode.useItemOn(fake, this.drifter.level(), fake.getMainHandItem(), InteractionHand.MAIN_HAND, hit));
				if (!clicked.consumesAction()) {
					return this.fail("the smithing table won't open (protected?)");
				}

				this.drifter.swing(InteractionHand.MAIN_HAND);
				this.drifter.getKeys().releaseAll();
				// Three items into their places, and the result taken: a few clicks' worth.
				this.wait = InventoryPause.roll(this.drifter, InventoryPause.CONTAINER_MIN, InventoryPause.CONTAINER_MAX) + 4 * InventoryPause.CLICK_TICKS
					+ InventoryPause.roll(this.drifter, InventoryPause.MOVE_ITEM_MIN, InventoryPause.MOVE_ITEM_MAX);
				this.phase = Phase.LOOK_OVER;
				return Status.RUNNING;
			}
			case LOOK_OVER -> {
				if (--this.wait > 0) {
					return Status.RUNNING;
				}

				return this.smith();
			}
			default -> {
				return Status.SUCCESS;
			}
		}
	}

	/** SmithingMenu#createResult and #onTake. */
	private Status smith() {
		DrifterInventory inventory = this.drifter.getInventory();
		ItemStack base = inventory.getItem(this.baseSlot);
		int templateSlot = inventory.findSlot(stack -> stack.is(Netherite.TEMPLATE), 0, DrifterInventory.MAIN_SIZE);
		int ingotSlot = inventory.findSlot(stack -> stack.is(Items.NETHERITE_INGOT), 0, DrifterInventory.MAIN_SIZE);
		if (templateSlot < 0 || ingotSlot < 0) {
			return this.fail("hasn't the template and an ingot");
		}

		var recipe = Netherite.recipe(this.drifter.level(), base);
		if (recipe.isEmpty()) {
			return this.fail("that has no netherite version");
		}

		ItemStack template = inventory.getItem(templateSlot);
		ItemStack ingot = inventory.getItem(ingotSlot);
		ItemStack result = recipe.get().value().assemble(new SmithingRecipeInput(template, base, ingot), this.drifter.level().registryAccess());
		if (result.isEmpty()) {
			return this.fail("the smithing table made nothing");
		}

		FakePlayerBridge.act(this.drifter, fake -> {
			result.onCraftedBy(fake.level(), fake, result.getCount());
			return null;
		});
		this.made = result.getHoverName().getString();
		template.shrink(1);
		ingot.shrink(1);
		// Back where the diamond piece was (worn armour goes back on).
		inventory.setItem(this.baseSlot, result);
		this.drifter.level().levelEvent(1044, this.table, 0);
		this.phase = Phase.DONE;
		return Status.SUCCESS;
	}
}
