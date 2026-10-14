package com.dinglebob.drifter.executor.task;

import java.util.Optional;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Place (DESIGN.md section 8): puts a block from the inventory into an empty spot by taking it in hand and
 * right-clicking a neighbouring block's face that the Drifter can see and reach (section 11 placement rules).
 * Fails if there's no such face: no floating blocks.
 */
public class PlaceBlockTask extends Task {
	private final BlockPos target;
	private final Predicate<ItemStack> item;
	private final String label;
	private final SelectItemTask select;
	@Nullable
	private UseOnBlockTask use;
	/** A support side to use when it can (a torch on the right-hand wall, section 17 torch habit). */
	@Nullable
	private Direction preferred;

	public PlaceBlockTask(DrifterEntity drifter, BlockPos target, Predicate<ItemStack> item, String label) {
		super(drifter);
		this.target = target.immutable();
		this.item = item;
		this.label = label;
		this.select = new SelectItemTask(drifter, item, label);
	}

	/** Place against this side of the target if that face is in view and reach; any other face otherwise. */
	@Nullable
	private Direction facing;

	/** Which way the placed block should face (a bed: toward its head). */
	public PlaceBlockTask facing(Direction facing) {
		this.facing = facing;
		return this;
	}

	public PlaceBlockTask preferSupport(Direction toSupport) {
		this.preferred = toSupport;
		return this;
	}

	@Override
	public String describe() {
		return "place " + this.label + " at " + this.target.toShortString();
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 12;
	}

	@Override
	protected void onStart() {
		if (!this.drifter.level().getBlockState(this.target).canBeReplaced()) {
			this.fail("that spot is already taken");
		}
	}

	@Override
	protected Status onTick() {
		if (this.use == null) {
			Status selected = this.select.tick();
			if (selected != Status.SUCCESS) {
				return selected == Status.FAILURE ? this.fail(this.select.failureReason()) : Status.RUNNING;
			}

			this.select.stop();
			// A solid block can't go where its own body is (found in testing: hut walls "nothing happened" again and again);
			// a torch can, like for a player.
			if (this.drifter.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem blockItem
				&& !blockItem.getBlock().defaultBlockState().getCollisionShape(this.drifter.level(), this.target).isEmpty()
				&& new net.minecraft.world.phys.AABB(this.target).intersects(this.drifter.getBoundingBox().deflate(1.0E-3))) {
				return this.fail("it's standing in the way");
			}

			// Grass, a flower or snow in the spot: a player clicks it and the block goes in its place (the grass is in the
			// way of the face under it anyway; found in testing: hut corners on grassy ground were "unreachable").
			Direction plantFace = clickableFace(this.drifter, this.drifter.getEyePosition(), this.target);
			if (plantFace != null) {
				this.use = new UseOnBlockTask(this.drifter, this.target, plantFace, "click the " + this.drifter.level().getBlockState(this.target).getBlock()
					.getName().getString().toLowerCase(java.util.Locale.ROOT));
			} else {
				Direction[] support = this.chooseSupport();
				if (support == null) {
					return this.fail("no block face to place against within reach");
				}

				BlockPos supportPos = this.target.relative(support[0]);
				this.use = new UseOnBlockTask(this.drifter, supportPos, support[1], "click " + supportPos.toShortString());
			}

			if (this.facing != null) {
				this.use.facing(this.facing);
			}
		}

		Status used = this.use.tick();
		if (used == Status.RUNNING) {
			return Status.RUNNING;
		}

		this.use.stop();
		if (used == Status.FAILURE) {
			return this.fail(this.use.failureReason());
		}

		BlockState placed = this.drifter.level().getBlockState(this.target);
		if (placed.canBeReplaced()) {
			return this.fail("the block didn't end up there");
		}

		this.drifter.getSenses().placedBlock(this.target);
		return Status.SUCCESS;
	}

	/** A replaceable block with a shape you can click (grass, flowers, snow), not air or water. */
	public static boolean isClickableReplaceable(net.minecraft.world.level.Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.isAir() && state.canBeReplaced() && level.getFluidState(pos).isEmpty() && !state.getShape(level, pos).isEmpty();
	}

	/** The face of a clickable replaceable block in the target spot it can see from there, or null. */
	@Nullable
	public static Direction clickableFace(DrifterEntity drifter, Vec3 eye, BlockPos target) {
		if (!isClickableReplaceable(drifter.level(), target)) {
			return null;
		}

		Direction best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Direction face : Direction.values()) {
			Optional<Vec3> point = Reach.aimPointFrom(drifter, eye, target, face);
			if (point.isPresent() && eye.distanceToSqr(point.get()) < bestDistance) {
				bestDistance = eye.distanceToSqr(point.get());
				best = face;
			}
		}

		return best;
	}

	/** {direction from target to the support block, face of the support block to click}, or null. */
	@Nullable
	private Direction[] chooseSupport() {
		Vec3 eye = this.drifter.getEyePosition();
		Direction[] best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Direction toSupport : Direction.values()) {
			BlockPos supportPos = this.target.relative(toSupport);
			BlockState support = this.drifter.level().getBlockState(supportPos);
			if (support.isAir() || support.canBeReplaced()) {
				continue;
			}

			Direction face = toSupport.getOpposite();
			Optional<Vec3> point = Reach.aimPoint(this.drifter, supportPos, face);
			if (point.isPresent() && toSupport == this.preferred) {
				return new Direction[]{toSupport, face};
			}

			if (point.isPresent() && eye.distanceToSqr(point.get()) < bestDistance) {
				bestDistance = eye.distanceToSqr(point.get());
				best = new Direction[]{toSupport, face};
			}
		}

		return best;
	}

	@Override
	protected void onStop() {
		this.select.stop();
		if (this.use != null) {
			this.use.stop();
		}
	}
}
