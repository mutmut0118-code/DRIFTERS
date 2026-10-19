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
	/**
	 * How far inside a face to aim, just enough that the ray enters through that face. It must be tiny: from a steep
	 * angle (leaning out over an edge to bridge), the line toward a point 0.1 inside the block passes over the block's
	 * top corner, so it had to lean about 0.2 blocks out before it could "see" the side (found in testing: bridging
	 * never started). Aiming at the surface itself, 0.03 is enough.
	 */
	private static final double FACE_INSET = 0.01;

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
		// Its head inside the block (sand fell on it, it got pushed into a wall): the crosshair is on that block whichever way
		// it looks, as for a player (the game's ray starts inside it). Found in a test: it suffocated in stone, "can't see or
		// reach it" over and over, because no face of a block can be seen from inside it.
		if (BlockPos.containing(eye).equals(pos)) {
			return Optional.of(eye.add(drifter.getLookAngle()));
		}

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
		return aimPointFrom(drifter, eye, pos, face, null);
	}

	/** A finer grid across a face, centre first, for aiming at one part of it (the upper half of a side, its left side). */
	private static final double[][] FINE_SAMPLES = fineSamples();

	private static double[][] fineSamples() {
		java.util.List<double[]> samples = new java.util.ArrayList<>();
		double[] steps = {0, -0.4, 0.4, -0.8, 0.8};
		for (double a : steps) {
			for (double b : steps) {
				samples.add(new double[] {a, b});
			}
		}

		samples.sort(java.util.Comparator.comparingDouble(s -> s[0] * s[0] + s[1] * s[1]));
		return samples.toArray(new double[0][]);
	}

	/**
	 * A point on that face it can see and reach whose spot on the block passes {@code region} (the hit's position within
	 * the block, 0 to 1 on each axis): where it clicks decides a stair's half or a door's hinge, as for a player.
	 */
	public static Optional<Vec3> aimPointFrom(DrifterEntity drifter, Vec3 eye, BlockPos pos, Direction face,
		@org.jetbrains.annotations.Nullable java.util.function.Predicate<Vec3> region) {
		AABB box = outline(drifter, pos);
		Vec3 center = box.getCenter();
		// Its head inside the block (sand fell on it, a block in its face): the crosshair is on that block whichever way it
		// looks, as for a player, so it can always dig itself out (found in testing: it suffocated "unable to see" it).
		if (box.contains(eye) && face == Direction.UP) {
			return Optional.of(eye.add(0.0, 0.1, 0.0));
		}

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
		for (double[] sample : region == null ? FACE_SAMPLES : FINE_SAMPLES) {
			Vec3 target = inset.add(u.scale(sample[0])).add(v.scale(sample[1]));
			Vec3 farTarget = eye.add(target.subtract(eye).scale(1.5));
			BlockHitResult hit = drifter.level().clip(new ClipContext(eye, farTarget, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, drifter));
			if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) && hit.getDirection() == face
				&& eye.distanceTo(hit.getLocation()) <= drifter.blockInteractionRange()
				&& (region == null || region.test(hit.getLocation().subtract(Vec3.atLowerCornerOf(pos))))) {
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
