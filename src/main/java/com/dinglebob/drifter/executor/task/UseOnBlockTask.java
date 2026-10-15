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
	/** Use the block itself (open a door, a gate): never sneaking, or the click would go to the held item instead. */
	private final boolean useBlock;
	private boolean sneak;
	private int sneakSettle;

	@org.jetbrains.annotations.Nullable
	private Direction facing;

	/** What it places is scaffolding (a bridge block), remembered so it can be taken down later. */
	private boolean scaffold;

	public UseOnBlockTask scaffold() {
		this.scaffold = true;
		return this;
	}

	/** Which way what it places should face (a bed's head). */
	public UseOnBlockTask facing(Direction facing) {
		this.facing = facing;
		return this;
	}

	public UseOnBlockTask(DrifterEntity drifter, BlockPos pos, Direction face, String label) {
		this(drifter, pos, face, label, false);
	}

	private UseOnBlockTask(DrifterEntity drifter, BlockPos pos, Direction face, String label, boolean useBlock) {
		super(drifter);
		this.pos = pos.immutable();
		this.face = face;
		this.label = label;
		this.useBlock = useBlock;
		this.aim = new BlockAim(drifter, this.pos, face, CameraModel.TurnSpeed.NORMAL);
	}

	/**
	 * Right-clicks a block for what it does itself (opens a door or gate), standing up: a player holding an item who
	 * sneak-clicks a door places the item against it instead (found in testing: it sneaked at doors over and over).
	 */
	public static UseOnBlockTask useBlock(DrifterEntity drifter, BlockPos pos, Direction face, String label) {
		return new UseOnBlockTask(drifter, pos, face, label, true);
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
		if (this.useBlock) {
			if (this.drifter.isShiftKeyDown()) {
				this.sneakSettle = SNEAK_SETTLE_TICKS;
			}

			this.drifter.getKeys().set(VirtualKeys.Key.SNEAK, false);
			return;
		}

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

		InteractionResult used = FakePlayerBridge.act(this.drifter, fake -> {
			// The way a placed block faces (a bed's head): a player lines that up by where they stand; here it's set for the
			// click, the aim stays the same.
			if (this.facing != null) {
				fake.setYRot(this.facing.toYRot());
			}

			return fake.gameMode.useItemOn(fake, this.drifter.level(), fake.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
		});
		if (!used.consumesAction()) {
			return this.fail("nothing happened");
		}

		if (used.shouldSwing()) {
			this.drifter.swing(InteractionHand.MAIN_HAND);
		}

		if (this.scaffold) {
			BlockPos placed = hit.getBlockPos().relative(hit.getDirection());
			if (!this.drifter.level().getBlockState(placed).isAir()) {
				this.drifter.placedScaffold(placed);
			}
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
