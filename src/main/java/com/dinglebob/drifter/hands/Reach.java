package com.dinglebob.drifter.hands;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Player reach and line of sight (DESIGN.md section 1): the Drifter can only touch the block under its crosshair,
 * within its block interaction range (4.5), exactly like a player's pick ray. Nothing through walls.
 */
public final class Reach {
	/** How far inside a face to aim, so the ray clearly enters through that face. */
	private static final double FACE_INSET = 0.1;

	private Reach() {
	}

	/** What the Drifter's crosshair is on right now, or null if it's on nothing within reach. */
	@Nullable
	public static BlockHitResult crosshair(DrifterEntity drifter) {
		HitResult hit = drifter.pick(drifter.blockInteractionRange(), 1.0F, false);
		return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK ? blockHit : null;
	}

	public static boolean isLookingAt(DrifterEntity drifter, BlockPos pos) {
		BlockHitResult hit = crosshair(drifter);
		return hit != null && hit.getBlockPos().equals(pos);
	}

	public static boolean isLookingAt(DrifterEntity drifter, BlockPos pos, Direction face) {
		BlockHitResult hit = crosshair(drifter);
		return hit != null && hit.getBlockPos().equals(pos) && hit.getDirection() == face;
	}

	/** A point to aim at so the crosshair lands on this block, through any face the Drifter can see and reach. */
	public static Optional<Vec3> aimPoint(DrifterEntity drifter, BlockPos pos) {
		Vec3 eye = drifter.getEyePosition();
		return Stream.of(Direction.values())
			.map(face -> aimPoint(drifter, pos, face))
			.flatMap(Optional::stream)
			.min(Comparator.comparingDouble(eye::distanceToSqr));
	}

	/** A point to aim at so the crosshair lands on this specific face of the block, if it's visible and in reach. */
	public static Optional<Vec3> aimPoint(DrifterEntity drifter, BlockPos pos, Direction face) {
		return aimPointFrom(drifter, drifter.getEyePosition(), pos, face);
	}

	/**
	 * Points across a face to try, as fractions of its half-size: the centre first, then nearer its edges. From a
	 * steep angle (looking down past the edge of the block it stands on, to bridge) only the lower part of a side face
	 * can be seen, so the centre alone isn't enough (bug found in testing).
	 */
	private static final double[][] FACE_SAMPLES = {
		{0, 0}, {0, -0.7}, {0, 0.7}, {-0.7, 0}, {0.7, 0}, {-0.6, -0.6}, {0.6, -0.6}, {-0.6, 0.6}, {0.6, 0.6}
	};

	/** Same as {@link #aimPoint(DrifterEntity, BlockPos, Direction)}, but from a hypothetical eye position. */
	public static Optional<Vec3> aimPointFrom(DrifterEntity drifter, Vec3 eye, BlockPos pos, Direction face) {
		AABB box = outline(drifter, pos);
		Vec3 center = box.getCenter();
		Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
		double halfDepth = switch (face.getAxis()) {
			case X -> box.getXsize() / 2.0;
			case Y -> box.getYsize() / 2.0;
			case Z -> box.getZsize() / 2.0;
		};
		Vec3 faceCenter = center.add(normal.scale(halfDepth));
		if (eye.subtract(faceCenter).dot(normal) <= 0.0) {
			return Optional.empty();
		}

		// Two in-plane axes of the face, scaled to its half-size: "u" sideways, "v" up (or north-south on top/bottom faces).
		Vec3 u;
		Vec3 v;
		switch (face.getAxis()) {
			case X -> {
				u = new Vec3(0, 0, box.getZsize() / 2.0);
				v = new Vec3(0, box.getYsize() / 2.0, 0);
			}
			case Z -> {
				u = new Vec3(box.getXsize() / 2.0, 0, 0);
				v = new Vec3(0, box.getYsize() / 2.0, 0);
			}
			default -> {
				u = new Vec3(box.getXsize() / 2.0, 0, 0);
				v = new Vec3(0, 0, box.getZsize() / 2.0);
			}
		}

		Vec3 inset = center.add(normal.scale(Math.max(halfDepth - FACE_INSET, 0.0)));
		for (double[] sample : FACE_SAMPLES) {
			Vec3 target = inset.add(u.scale(sample[0])).add(v.scale(sample[1]));
			Vec3 farTarget = eye.add(target.subtract(eye).scale(1.5));
			BlockHitResult hit = drifter.level().clip(new ClipContext(eye, farTarget, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, drifter));
			if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) && hit.getDirection() == face
				&& eye.distanceTo(hit.getLocation()) <= drifter.blockInteractionRange()) {
				return Optional.of(target);
			}
		}

		return Optional.empty();
	}

	public static boolean canReach(DrifterEntity drifter, BlockPos pos) {
		return aimPoint(drifter, pos).isPresent();
	}

	private static AABB outline(DrifterEntity drifter, BlockPos pos) {
		BlockState state = drifter.level().getBlockState(pos);
		VoxelShape shape = state.getShape(drifter.level(), pos);
		return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
	}
}
