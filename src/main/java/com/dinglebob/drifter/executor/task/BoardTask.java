package com.dinglebob.drifter.executor.task;

import java.util.function.Predicate;

import com.dinglebob.drifter.compat.FarmersDelight;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Using a cutting board (Farmer's Delight's CuttingBoardBlock#useItemOn), as a player does: a right-click with the thing to
 * cut puts it on the board ({@code count} of it: the rest it split off in its inventory first), then a right-click with the
 * knife per piece cuts one, and what comes off pops out beside the board. Clicks at a player's pace, through the fake player.
 * Hold the item (or the knife) first ({@link SelectItemTask}).
 */
public class BoardTask extends Task {
	/** Ticks between cuts: a player clicking steadily. */
	private static final int CUT_TICKS = 6;

	private final BlockPos pos;
	private final boolean put;
	private final int count;
	private final Predicate<ItemStack> held;
	private final String label;
	private final BlockAim aim;
	/** What's on it now (the board's piece, the skillet's stack). */
	private java.util.function.BiFunction<net.minecraft.world.level.Level, BlockPos, ItemStack> contents = FarmersDelight::onBoard;
	private String what = "cutting board";
	private int done;
	private int wait;

	private BoardTask(DrifterEntity drifter, BlockPos pos, boolean put, int count, Predicate<ItemStack> held, String label) {
		super(drifter);
		this.pos = pos.immutable();
		this.put = put;
		this.count = count;
		this.held = held;
		this.label = label;
		this.aim = new BlockAim(drifter, this.pos, Direction.UP, CameraModel.TurnSpeed.NORMAL);
	}

	/** Puts {@code count} of the held item on the board. */
	public static BoardTask put(DrifterEntity drifter, BlockPos board, int count, Predicate<ItemStack> item, String label) {
		return new BoardTask(drifter, board, true, count, item, label);
	}

	/** Puts {@code count} of the held raw food in the skillet (Farmer's Delight's SkilletBlock#useItemOn). */
	public static BoardTask skillet(DrifterEntity drifter, BlockPos skillet, int count, Predicate<ItemStack> item, String label) {
		BoardTask task = new BoardTask(drifter, skillet, true, count, item, label);
		task.contents = FarmersDelight::inSkillet;
		task.what = "skillet";
		return task;
	}

	/** Cuts what's on the board, {@code times} pieces, with the held knife. */
	public static BoardTask cut(DrifterEntity drifter, BlockPos board, int times, Predicate<ItemStack> tool, String label) {
		return new BoardTask(drifter, board, false, times, tool, label);
	}

	@Override
	public String describe() {
		return this.put ? "put " + this.count + " " + this.label + " on the " + this.what : "cut the " + this.label + " on the cutting board";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 8 + this.count * (CUT_TICKS + 2);
	}

	@Override
	protected void onStart() {
		// Crouching, the click would go to the knife, not the board.
		this.drifter.getKeys().set(VirtualKeys.Key.SNEAK, false);
	}

	@Override
	protected Status onTick() {
		if (--this.wait > 0) {
			return Status.RUNNING;
		}

		BlockAim.Result result = this.aim.tick();
		if (result == BlockAim.Result.UNREACHABLE) {
			return this.fail("can't see or reach the " + this.what);
		}

		if (result == BlockAim.Result.AIMING) {
			return Status.RUNNING;
		}

		BlockHitResult hit = Reach.crosshair(this.drifter);
		if (hit == null || !hit.getBlockPos().equals(this.pos)) {
			return Status.RUNNING;
		}

		if (!this.held.test(this.drifter.getMainHandItem())) {
			return this.fail("isn't holding the " + this.label);
		}

		return this.put ? this.putOn(hit) : this.cutOnce(hit);
	}

	private Status putOn(BlockHitResult hit) {
		if (!this.contents.apply(this.drifter.level(), this.pos).isEmpty()) {
			return this.fail("something's on the " + this.what + " already");
		}

		InteractionResult used = FakePlayerBridge.act(this.drifter, fake -> {
			// Only as many as it means to cut go on (it split the stack in its inventory, as a player right-click-drags).
			ItemStack stack = fake.getMainHandItem();
			int keep = Math.max(0, stack.getCount() - this.count);
			ItemStack rest = stack.split(keep);
			InteractionResult click = fake.gameMode.useItemOn(fake, this.drifter.level(), fake.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
			ItemStack after = fake.getMainHandItem();
			if (after.isEmpty()) {
				fake.setItemInHand(InteractionHand.MAIN_HAND, rest);
			} else {
				after.grow(rest.getCount());
			}

			return click;
		});
		if (!used.consumesAction() || this.contents.apply(this.drifter.level(), this.pos).isEmpty()) {
			return this.fail("it wouldn't go on the " + this.what);
		}

		this.drifter.swing(InteractionHand.MAIN_HAND);
		return Status.SUCCESS;
	}

	private Status cutOnce(BlockHitResult hit) {
		if (FarmersDelight.onBoard(this.drifter.level(), this.pos).isEmpty()) {
			return this.done > 0 ? Status.SUCCESS : this.fail("nothing on the board to cut");
		}

		int before = FarmersDelight.onBoard(this.drifter.level(), this.pos).getCount();
		FakePlayerBridge.act(this.drifter,
			fake -> fake.gameMode.useItemOn(fake, this.drifter.level(), fake.getMainHandItem(), InteractionHand.MAIN_HAND, hit));
		// (The board takes the click either way; a piece gone is a cut.)
		if (FarmersDelight.onBoard(this.drifter.level(), this.pos).getCount() >= before) {
			return this.done > 0 ? Status.SUCCESS : this.fail("the " + this.label + " won't cut that");
		}

		this.drifter.swing(InteractionHand.MAIN_HAND);
		this.wait = CUT_TICKS;
		return ++this.done >= this.count ? Status.SUCCESS : Status.RUNNING;
	}
}
