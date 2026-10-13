package com.dinglebob.drifter.executor.task;

import java.util.Optional;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.Crafting;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.motor.CameraModel;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Craft (DESIGN.md sections 8 and 10.2): crafts an item some number of times. Uses the 2x2 inventory grid when the
 * recipe fits; otherwise the Drifter must look at a crafting table in reach to "open" it. Either way it then stands
 * still with its head tilted slightly down for 0.5 to 2 s (the inventory screen is open), and each craft after that
 * takes a click.
 * TODO(milestone 3): find tables from memory instead of the reach-limited search here.
 */
public class CraftTask extends Task {
	/** A crafting menu closes when the player is more than this far from the table. */
	private static final double TABLE_MENU_RANGE = 8.0;

	private enum Phase {
		OPEN_TABLE, PAUSE, CRAFT
	}

	private final Predicate<ItemStack> wanted;
	private final String label;
	private final int times;
	@Nullable
	private BlockPos table;
	@Nullable
	private BlockAim tableAim;
	private Phase phase = Phase.PAUSE;
	private int pause;
	private int crafted;
	private int clickCooldown;

	public CraftTask(DrifterEntity drifter, Predicate<ItemStack> wanted, String label, int times, @Nullable BlockPos table) {
		super(drifter);
		this.wanted = wanted;
		this.label = label;
		this.times = times;
		this.table = table == null ? null : table.immutable();
	}

	@Override
	public String describe() {
		return "craft " + this.label + (this.times > 1 ? " x" + this.times : "");
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 8 + this.times * InventoryPause.CLICK_TICKS * 3;
	}

	@Override
	protected void onStart() {
		boolean fitsInventory = Crafting.find(this.drifter, this.wanted, false).isPresent();
		if (fitsInventory) {
			this.table = null;
			this.beginPause();
			return;
		}

		if (Crafting.find(this.drifter, this.wanted, true).isEmpty()) {
			this.fail("doesn't have the ingredients (or knows no recipe)");
			return;
		}

		if (this.table == null) {
			this.table = this.findTableInReach();
		}

		if (this.table == null || !this.drifter.level().getBlockState(this.table).is(Blocks.CRAFTING_TABLE)) {
			this.fail("needs a crafting table within reach");
			return;
		}

		this.tableAim = new BlockAim(this.drifter, this.table, null, CameraModel.TurnSpeed.NORMAL);
		this.phase = Phase.OPEN_TABLE;
	}

	@Override
	protected Status onTick() {
		if (this.table != null) {
			if (!this.drifter.level().getBlockState(this.table).is(Blocks.CRAFTING_TABLE)) {
				return this.fail("the crafting table is gone");
			}

			if (this.drifter.position().distanceTo(this.table.getCenter()) > TABLE_MENU_RANGE) {
				return this.fail("moved too far from the crafting table");
			}
		}

		switch (this.phase) {
			case OPEN_TABLE -> {
				BlockAim.Result result = this.tableAim.tick();
				if (result == BlockAim.Result.UNREACHABLE) {
					return this.fail("can't see or reach the crafting table");
				}

				if (result == BlockAim.Result.ON_TARGET) {
					this.beginPause();
				}

				return Status.RUNNING;
			}
			case PAUSE -> {
				if (--this.pause <= 0) {
					this.phase = Phase.CRAFT;
				}

				return Status.RUNNING;
			}
			default -> {
				if (--this.clickCooldown > 0) {
					return Status.RUNNING;
				}

				this.clickCooldown = InventoryPause.CLICK_TICKS;
				Optional<Crafting.Plan> plan = Crafting.find(this.drifter, this.wanted, this.table != null);
				if (plan.isEmpty()) {
					return this.crafted > 0 ? Status.SUCCESS : this.fail("doesn't have the ingredients");
				}

				ItemStack result = Crafting.craftOnce(this.drifter, plan.get().recipe());
				if (result.isEmpty()) {
					return this.crafted > 0 ? Status.SUCCESS : this.fail("the recipe didn't match");
				}

				return ++this.crafted >= this.times ? Status.SUCCESS : Status.RUNNING;
			}
		}
	}

	private void beginPause() {
		this.drifter.getKeys().releaseAll();
		float pitch = Mth.clamp(this.drifter.getXRot() + InventoryPause.HEAD_TILT_DOWN, -90.0F, 90.0F);
		this.drifter.getCamera().lookAt(this.drifter.getYRot(), pitch, CameraModel.TurnSpeed.IDLE);
		this.pause = InventoryPause.roll(this.drifter, InventoryPause.CRAFT_MIN, InventoryPause.CRAFT_MAX);
		this.phase = Phase.PAUSE;
	}

	@Nullable
	private BlockPos findTableInReach() {
		BlockPos center = BlockPos.containing(this.drifter.getEyePosition());
		int radius = Mth.ceil(this.drifter.blockInteractionRange());
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			if (this.drifter.level().getBlockState(pos).is(Blocks.CRAFTING_TABLE) && Reach.canReach(this.drifter, pos)) {
				double distance = pos.distToCenterSqr(this.drifter.getEyePosition());
				if (distance < bestDistance) {
					bestDistance = distance;
					best = pos.immutable();
				}
			}
		}

		return best;
	}

	@Override
	protected void onStop() {
		this.drifter.getCamera().clearTarget();
	}
}
