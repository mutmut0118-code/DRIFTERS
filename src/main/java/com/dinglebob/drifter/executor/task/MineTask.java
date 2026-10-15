package com.dinglebob.drifter.executor.task;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.motor.CameraModel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mine: break one block with whatever is in hand, the way a player holds attack on it (DESIGN.md sections 1, 3, 8).
 * Mirrors the client's MultiPlayerGameMode: progress per tick from the player break-speed formula, the crack overlay
 * broadcast to everyone (stage = progress x 10), an instant break when one tick is enough, and the 5-tick pause
 * after each non-instant break. The block is actually broken by the fake player, so drops, tool damage, the 0.005
 * exhaustion and protection mods all behave as for a player.
 */
public class MineTask extends Task {
	/** MultiPlayerGameMode#destroyDelay: ticks before a player can start the next block after a full break. */
	public static final int DESTROY_DELAY_TICKS = 5;
	private static final int AIM_ALLOWANCE_TICKS = 60;

	private enum Phase {
		AIM, MINE, DELAY
	}

	private final BlockPos pos;
	private final BlockAim aim;
	private BlockState target;
	private Phase phase = Phase.AIM;
	private float progress;
	private int lastStage = -1;
	private int delay;
	private int timeout;

	public MineTask(DrifterEntity drifter, BlockPos pos) {
		super(drifter);
		this.pos = pos.immutable();
		this.aim = new BlockAim(drifter, this.pos, null, CameraModel.TurnSpeed.NORMAL);
	}

	@Override
	public String describe() {
		String name = this.target == null ? "block" : this.target.getBlock().getName().getString();
		return "mine " + name + " at " + this.pos.toShortString();
	}

	@Override
	protected int timeoutTicks() {
		return this.timeout;
	}

	@Override
	protected void onStart() {
		this.target = this.drifter.level().getBlockState(this.pos);
		if (this.target.isAir()) {
			this.fail("there is nothing there");
			return;
		}

		float perTick = this.drifter.getDestroyProgress(this.target, this.drifter.level(), this.pos);
		if (perTick <= 0.0F) {
			this.fail("it can't be broken");
			return;
		}

		int expectedTicks = (int)Math.ceil(1.0F / perTick);
		this.timeout = AIM_ALLOWANCE_TICKS + 3 * expectedTicks + DESTROY_DELAY_TICKS;
	}

	@Override
	protected Status onTick() {
		if (this.phase == Phase.DELAY) {
			return --this.delay <= 0 ? Status.SUCCESS : Status.RUNNING;
		}

		BlockState now = this.drifter.level().getBlockState(this.pos);
		if (!now.is(this.target.getBlock())) {
			return this.fail(now.isAir() ? "someone else broke it" : "the block changed");
		}

		if (this.phase == Phase.AIM) {
			BlockAim.Result result = this.aim.tick();
			if (result == BlockAim.Result.UNREACHABLE) {
				return this.fail("can't see or reach it");
			}

			if (result == BlockAim.Result.ON_TARGET) {
				return this.startMining(now);
			}

			return Status.RUNNING;
		}

		// MINE: like holding attack. If the crosshair slips off, progress is lost, as for a player.
		if (!this.aim.crosshairOnTarget()) {
			this.resetProgress();
			this.aim.reset();
			this.phase = Phase.AIM;
			return Status.RUNNING;
		}

		this.drifter.swing(InteractionHand.MAIN_HAND);
		this.progress += this.drifter.getDestroyProgress(now, this.drifter.level(), this.pos);
		if (this.progress >= 1.0F) {
			return this.breakBlock(true);
		}

		this.broadcastStage();
		return Status.RUNNING;
	}

	private Status startMining(BlockState state) {
		FakePlayerBridge.act(this.drifter, fake -> {
			state.attack(this.drifter.level(), this.pos, fake);
			return null;
		});
		this.drifter.swing(InteractionHand.MAIN_HAND);
		if (this.drifter.getDestroyProgress(state, this.drifter.level(), this.pos) >= 1.0F) {
			return this.breakBlock(false);
		}

		this.progress = 0.0F;
		this.phase = Phase.MINE;
		this.broadcastStage();
		return Status.RUNNING;
	}

	private Status breakBlock(boolean afterHolding) {
		this.resetProgress();
		boolean broken = FakePlayerBridge.act(this.drifter, fake -> fake.gameMode.destroyBlock(this.pos));
		if (!broken) {
			return this.fail("the block can't be broken here (protected?)");
		}

		this.drifter.getSenses().minedBlock(this.pos, this.target);
		this.drifter.dugOut(this.pos);

		if (!afterHolding) {
			return Status.SUCCESS;
		}

		this.phase = Phase.DELAY;
		this.delay = DESTROY_DELAY_TICKS;
		return Status.RUNNING;
	}

	private void broadcastStage() {
		int stage = this.progress > 0.0F ? (int)(this.progress * 10.0F) : -1;
		if (stage != this.lastStage) {
			this.lastStage = stage;
			((ServerLevel)this.drifter.level()).destroyBlockProgress(this.drifter.getId(), this.pos, stage);
		}
	}

	private void resetProgress() {
		this.progress = 0.0F;
		if (this.lastStage != -1) {
			this.lastStage = -1;
			((ServerLevel)this.drifter.level()).destroyBlockProgress(this.drifter.getId(), this.pos, -1);
		}
	}

	@Override
	protected void onStop() {
		this.resetProgress();
	}
}
