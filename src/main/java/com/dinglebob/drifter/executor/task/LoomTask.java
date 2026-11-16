package com.dinglebob.drifter.executor.task;

import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.motor.CameraModel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.phys.Vec3;

/**
 * Puts the next pattern on a banner at a loom in reach (the user, 2026-11-15: nations' banners with a real design), through
 * the loom's own menu run by the player-hands helper: the banner and one dye go in, it looks over the patterns a moment, picks
 * the one it wants, and takes the banner back out (the game uses up the dye, as for a player). One pattern a go.
 */
public class LoomTask extends Task {
	/** Looking over the patterns before picking one. */
	private static final int LOOK_MIN = 15;
	private static final int LOOK_MAX = 40;

	private final BlockPos loom;
	private final TownWork.Banner banner;
	private int pause = -1;

	public LoomTask(DrifterEntity drifter, BlockPos loom, TownWork.Banner banner) {
		super(drifter);
		this.loom = loom;
		this.banner = banner;
	}

	@Override
	public String describe() {
		return "weave a pattern onto " + this.banner.what();
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 10;
	}

	/** How many of the banner's patterns this stack has on already (in order), or -1 if it isn't on its way to it. */
	public static int done(TownWork.Banner banner, ItemStack stack) {
		if (!stack.is(banner.item())) {
			return -1;
		}

		var on = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers();
		if (on.size() > banner.layers().size()) {
			return -1;
		}

		for (int i = 0; i < on.size(); i++) {
			var layer = on.get(i);
			if (layer.color() != banner.layers().get(i).colour()
				|| !layer.pattern().unwrapKey().map(key -> key.location().toString()).orElse("").equals(banner.layers().get(i).pattern())) {
				return -1;
			}
		}

		return on.size();
	}

	@Override
	protected Status onTick() {
		if (!this.drifter.level().getBlockState(this.loom).is(Blocks.LOOM)) {
			return this.fail("the loom is gone");
		}

		if (!Reach.canReach(this.drifter, this.loom)) {
			return this.fail("can't reach the loom");
		}

		this.drifter.getKeys().releaseAll();
		this.drifter.getCamera().track(Vec3.atCenterOf(this.loom), CameraModel.TurnSpeed.NORMAL);
		if (this.pause < 0) {
			if (!this.drifter.getCamera().isSettled()) {
				return Status.RUNNING;
			}

			this.pause = InventoryPause.roll(this.drifter, LOOK_MIN, LOOK_MAX);
			return Status.RUNNING;
		}

		if (--this.pause > 0) {
			return Status.RUNNING;
		}

		// The banner furthest along toward this one (it may have begun it before), and the dye its next pattern wants.
		DrifterInventory inventory = this.drifter.getInventory();
		int bannerSlot = -1;
		int most = -1;
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			int done = done(this.banner, inventory.getItem(slot));
			if (done > most && done < this.banner.layers().size()) {
				most = done;
				bannerSlot = slot;
			}
		}

		if (bannerSlot < 0) {
			return this.fail("has no banner to weave on");
		}

		TownWork.Layer next = this.banner.layers().get(most);
		int dyeSlot = inventory.findSlot(stack -> stack.getItem() instanceof DyeItem dye && dye.getDyeColor() == next.colour(), 0, DrifterInventory.MAIN_SIZE);
		if (dyeSlot < 0) {
			return this.fail("has no " + next.colour().getName() + " dye");
		}

		int from = bannerSlot;
		boolean woven = FakePlayerBridge.act(this.drifter, fake -> {
			LoomMenu menu = new LoomMenu(0, fake.getInventory(), ContainerLevelAccess.create(this.drifter.level(), this.loom));
			var hand = fake.getInventory().items;
			// In: one banner (any more stay in its pocket) and the dyes.
			ItemStack banners = hand.get(from);
			ItemStack one = banners.split(1);
			ItemStack dyes = hand.get(dyeSlot);
			hand.set(dyeSlot, ItemStack.EMPTY);
			menu.getBannerSlot().set(one);
			menu.getDyeSlot().set(dyes);
			int index = -1;
			for (int i = 0; i < menu.getSelectablePatterns().size(); i++) {
				if (menu.getSelectablePatterns().get(i).unwrapKey().map(key -> key.location().toString()).orElse("").equals(next.pattern())) {
					index = i;
					break;
				}
			}

			boolean made = false;
			if (index >= 0 && menu.clickMenuButton(fake, index) && menu.getResultSlot().hasItem()) {
				ItemStack out = menu.getResultSlot().getItem().copy();
				menu.getResultSlot().onTake(fake, out);
				// (The game took the banner and a dye from the loom.)
				if (hand.get(from).isEmpty()) {
					hand.set(from, out);
				} else if (!fake.getInventory().add(out)) {
					fake.drop(out, false);
				}

				made = true;
			}

			// What's left in the loom back out: the banner if nothing was made, the dyes left over.
			ItemStack back = menu.getBannerSlot().getItem();
			ItemStack leftover = menu.getDyeSlot().getItem();
			menu.getBannerSlot().set(ItemStack.EMPTY);
			menu.getDyeSlot().set(ItemStack.EMPTY);
			if (!back.isEmpty()) {
				if (hand.get(from).isEmpty()) {
					hand.set(from, back);
				} else {
					hand.get(from).grow(back.getCount());
				}
			}

			if (!leftover.isEmpty()) {
				hand.set(dyeSlot, leftover);
			}

			return made;
		});
		if (!woven) {
			return this.fail("the loom wouldn't take that pattern");
		}

		this.drifter.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		return Status.SUCCESS;
	}

	@Override
	protected void onStop() {
		this.drifter.getCamera().clearTarget();
	}
}
