package com.dinglebob.drifter.executor.task;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.FakePlayerBridge;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;

/**
 * UseItem (DESIGN.md section 8): right-clicks with the held item in the air.
 * <ul>
 *   <li>Food and potions are held for their full use time (1.6 s for most food) by the Drifter itself, so the
 *       eating animation, particles and sounds show on it and it slows to sneak pace while eating.</li>
 *   <li>Instant items (buckets, ender pearls, snowballs, ...) are used through the fake player.</li>
 * </ul>
 * TODO(milestone 6): bows, crossbows, shields and tridents (hold-and-release items).
 * TODO: milk and honey only give back their bucket or bottle to real players in vanilla; handle them before use.
 */
public class UseItemTask extends Task {
	private ItemStack used = ItemStack.EMPTY;
	private int countBefore;
	private boolean holding;
	private int timeout = 20 * 5;

	public UseItemTask(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return this.used.isEmpty() ? "use held item" : "use " + this.used.getHoverName().getString();
	}

	@Override
	protected int timeoutTicks() {
		return this.timeout;
	}

	@Override
	protected void onStart() {
		this.used = this.drifter.getMainHandItem();
		this.countBefore = this.used.getCount();
		if (this.used.isEmpty()) {
			this.fail("its hand is empty");
			return;
		}

		FoodProperties food = this.used.get(DataComponents.FOOD);
		if (food != null || this.used.getItem() instanceof PotionItem) {
			if (food != null && !this.drifter.canEat(food.canAlwaysEat())) {
				this.fail("it isn't hungry");
				return;
			}

			this.holding = true;
			this.timeout = this.used.getUseDuration(this.drifter) * 3 + 20;
			this.drifter.startUsingItem(InteractionHand.MAIN_HAND);
		}
	}

	@Override
	protected Status onTick() {
		if (this.holding) {
			if (this.drifter.isUsingItem()) {
				return Status.RUNNING;
			}

			boolean consumed = this.drifter.getMainHandItem() != this.used || this.used.getCount() < this.countBefore;
			return consumed ? Status.SUCCESS : this.fail("stopped before finishing");
		}

		InteractionResult result = FakePlayerBridge.act(this.drifter, fake -> {
			InteractionResult r = fake.gameMode.useItem(fake, this.drifter.level(), fake.getMainHandItem(), InteractionHand.MAIN_HAND);
			if (fake.isUsingItem()) {
				fake.stopUsingItem();
				return InteractionResult.FAIL;
			}

			return r;
		});
		if (!result.consumesAction()) {
			return this.fail("nothing happened");
		}

		// (A use that did something swings the arm; on the server it reports "consume", see UseOnBlockTask.)
		this.drifter.swing(InteractionHand.MAIN_HAND);

		return Status.SUCCESS;
	}

	@Override
	protected void onStop() {
		if (this.holding && this.drifter.isUsingItem()) {
			this.drifter.stopUsingItem();
		}
	}
}
