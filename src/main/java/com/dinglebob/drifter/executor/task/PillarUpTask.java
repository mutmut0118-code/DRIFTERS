package com.dinglebob.drifter.executor.task;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.hands.Tools;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Pillaring up on the spot (DESIGN.md section 11 build order: "for height, scaffold with a dirt pillar"; section 12
 * pillar move): a building block in hand, look straight down, jump, and at the top of the jump place a block on the one
 * it just left, the player way. Repeats until it stands {@code height} blocks up. The same moves as the pathfinder's
 * pillar step, for when it just needs to go up where it stands (the middle of a hut it's building).
 */
public class PillarUpTask extends Task {
	private final int height;
	private int startY;
	private int stage;
	private int ticks;
	private int tries;
	/** Sneaking for this jump (the block under it opens when clicked). */
	private boolean sneak;
	/** Highest it got above where it jumped from (for the failure message). */
	private double peak;
	/** The feet level it jumped from: the new block goes there. */
	private int jumpFeet;
	private SelectItemTask select;

	public PillarUpTask(DrifterEntity drifter, int height) {
		super(drifter);
		this.height = height;
	}

	@Override
	public String describe() {
		return "pillar up " + this.height + " blocks";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 8 * this.height;
	}

	@Override
	protected void onStart() {
		this.startY = this.drifter.blockPosition().getY();
		if (!this.drifter.onGround()) {
			this.fail("not standing on anything");
		}
	}

	@Override
	protected Status onTick() {
		VirtualKeys keys = this.drifter.getKeys();
		BlockPos feet = this.drifter.blockPosition();
		if (feet.getY() >= this.startY + this.height && this.drifter.onGround()) {
			keys.releaseAll();
			return Status.SUCCESS;
		}

		switch (this.stage) {
			case 0 -> {
				if (!Tools.BUILDING_BLOCK.test(this.drifter.getMainHandItem())) {
					if (this.select == null) {
						this.select = new SelectItemTask(this.drifter, Tools.BUILDING_BLOCK, "a building block");
					}

					Status status = this.select.tick();
					if (status == Status.FAILURE) {
						return this.fail("has no blocks to pillar with");
					}

					return Status.RUNNING;
				}

				this.select = null;
				keys.releaseAll();
				// No room to jump: blocked overhead.
				BlockPos head = feet.above(2);
				if (!this.drifter.level().getBlockState(head).getCollisionShape(this.drifter.level(), head).isEmpty()) {
					return this.fail("something overhead");
				}

				this.drifter.getCamera().lookAt(this.drifter.getYRot(), 90.0F, CameraModel.TurnSpeed.NORMAL);
				this.stage = 1;
			}
			case 1 -> {
				if (this.drifter.getCamera().isSettled() && this.drifter.getXRot() < 80.0F) {
					this.stage = 0;
				} else if (this.drifter.getCamera().isSettled() && this.drifter.onGround()) {
					// Standing on a crafting table, chest, furnace...: sneaking, or the click opens it instead of placing (found
					// in testing: "couldn't get a block under itself" on its own table in the middle of the hut, every day).
					this.sneak = UseOnBlockTask.hasOwnUse(this.drifter.level(), feet.below(), this.drifter.level().getBlockState(feet.below()));
					keys.set(VirtualKeys.Key.SNEAK, this.sneak);
					keys.set(VirtualKeys.Key.JUMP, true);
					this.jumpFeet = feet.getY();
					this.stage = 2;
					this.ticks = 0;
				}
			}
			case 2 -> {
				keys.set(VirtualKeys.Key.JUMP, false);
				keys.set(VirtualKeys.Key.SNEAK, this.sneak);
				this.peak = Math.max(this.peak, this.drifter.getY() - this.jumpFeet);
				// Up a block: the crosshair is on the top of the block it jumped from; put one there.
				if (this.drifter.getY() >= this.jumpFeet + 1.0 && !this.drifter.onGround()) {
					BlockHitResult hit = Reach.crosshair(this.drifter);
					// The ground's top, or snow or grass in the block it jumped from (clicking those puts the block right
					// there, which is where it should go; found by the user: pillaring failed on snow).
					boolean onGround = hit != null && hit.getDirection() == Direction.UP && hit.getBlockPos().getY() == this.jumpFeet - 1;
					boolean inSnow = hit != null && hit.getBlockPos().getY() == this.jumpFeet
						&& PlaceBlockTask.isClickableReplaceable(this.drifter.level(), hit.getBlockPos());
					if (onGround || inSnow) {
						InteractionResult placed = FakePlayerBridge.act(this.drifter,
							fake -> fake.gameMode.useItemOn(fake, this.drifter.level(), fake.getMainHandItem(), InteractionHand.MAIN_HAND, hit));
						if (placed.consumesAction()) {
							this.drifter.swing(InteractionHand.MAIN_HAND);
							this.drifter.getSenses().placedBlock(hit.getBlockPos().above());
							this.stage = 3;
							return Status.RUNNING;
						}
					}
				}

				if (++this.ticks > 4 && this.drifter.onGround()) {
					// Landed without placing: try again.
					if (++this.tries > 4) {
						BlockHitResult seen = Reach.crosshair(this.drifter);
						BlockPos over = this.drifter.blockPosition().above(2);
						return this.fail(String.format(java.util.Locale.ROOT, "couldn't get a block under itself (jumped %.2f, looking at %s, over its head %s)",
							this.peak, seen == null ? "nothing" : seen.getBlockPos().toShortString() + " " + seen.getDirection().getName(),
							this.drifter.level().getBlockState(over).getBlock().getName().getString()));
					}

					this.stage = 1;
				}
			}
			default -> {
				// Placed: once it lands on the new block, go again.
				if (this.drifter.onGround()) {
					this.stage = 1;
				}
			}
		}

		return Status.RUNNING;
	}

	@Override
	protected void onStop() {
		this.drifter.getKeys().releaseAll();
		if (this.select != null) {
			this.select.stop();
		}
	}
}
