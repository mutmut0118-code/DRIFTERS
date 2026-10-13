package com.dinglebob.drifter.executor.task;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * UseOn (DESIGN.md section 8): right-clicks one face of a block with the held item, as a player does. Placing a
 * block is this with a block item. The crosshair must be on that exact face within reach, so a block can only go
 * against an existing face, never into thin air (section 1). Sneaks first when the clicked block would otherwise
 * open or toggle (a chest, a door), like a player does.
 */
public class UseOnBlockTask extends Task {
	/** Ticks to let the crouch pose (and its lower eye height) settle before aiming. */
	private static final int SNEAK_SETTLE_TICKS = 2;

	private final BlockPos pos;
	private final Direction face;
	private final String label;
	private final BlockAim aim;
	private boolean sneak;
	private int sneakSettle;

	public UseOnBlockTask(DrifterEntity drifter, BlockPos pos, Direction face, String label) {
		super(drifter);
		this.pos = pos.immutable();
		this.face = face;
		this.label = label;
		this.aim = new BlockAim(drifter, this.pos, face, CameraModel.TurnSpeed.NORMAL);
	}

	@Override
	public String describe() {
		return this.label;
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 6;
	}

	@Override
	protected void onStart() {
		Level level = this.drifter.level();
		this.sneak = hasOwnUse(level, this.pos, level.getBlockState(this.pos));
		if (this.sneak) {
			this.drifter.getKeys().set(VirtualKeys.Key.SNEAK, true);
			this.sneakSettle = SNEAK_SETTLE_TICKS;
		}
	}

	@Override
	protected Status onTick() {
		if (this.sneakSettle > 0) {
			this.sneakSettle--;
			return Status.RUNNING;
		}

		BlockAim.Result result = this.aim.tick();
		if (result == BlockAim.Result.UNREACHABLE) {
			return this.fail("can't see or reach that face");
		}

		if (result == BlockAim.Result.AIMING) {
			return Status.RUNNING;
		}

		BlockHitResult hit = Reach.crosshair(this.drifter);
		if (hit == null) {
			return Status.RUNNING;
		}

		InteractionResult used = FakePlayerBridge.act(this.drifter,
			fake -> fake.gameMode.useItemOn(fake, this.drifter.level(), fake.getMainHandItem(), InteractionHand.MAIN_HAND, hit));
		if (!used.consumesAction()) {
			return this.fail("nothing happened");
		}

		if (used.shouldSwing()) {
			this.drifter.swing(InteractionHand.MAIN_HAND);
		}

		return Status.SUCCESS;
	}

	@Override
	protected void onStop() {
		if (this.sneak) {
			this.drifter.getKeys().set(VirtualKeys.Key.SNEAK, false);
		}
	}

	/** Blocks that do something of their own when right-clicked, so a player sneaks to place against them. */
	static boolean hasOwnUse(Level level, BlockPos pos, BlockState state) {
		return state.getMenuProvider(level, pos) != null
			|| level.getBlockEntity(pos) != null
			|| state.is(BlockTags.DOORS)
			|| state.is(BlockTags.TRAPDOORS)
			|| state.is(BlockTags.FENCE_GATES)
			|| state.is(BlockTags.BUTTONS)
			|| state.is(BlockTags.BEDS)
			|| state.is(BlockTags.ANVIL)
			|| state.is(BlockTags.CANDLE_CAKES);
	}
}
