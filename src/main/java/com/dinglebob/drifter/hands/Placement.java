package com.dinglebob.drifter.hands;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.mixin.BlockItemAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * How to place a block so it comes out a particular way (house plans, user's request 2026-10-16: stairs, upside-down
 * stairs, sideways logs, slabs, doors and their hinges, trapdoors, torches). Rather than a rule per block, it asks the
 * game: for each face it could click, each part of that face, and each way it could be looking, what the item would
 * place there (the item's own placement code, as for a player's click); the ones that give the plan's block are the ways
 * to do it. Modded blocks work the same. Only what matters for the look is compared (facing, axis, half, hinge, slab
 * type, attachment, rotation); connections (fences, stair corners) settle by themselves.
 */
public final class Placement {
	/** One way to place it: click this face of this block, on this spot of it (0 to 1 in the block), looking this way. */
	public record Way(BlockPos support, Direction face, Vec3 local, Direction yaw, float pitch, int rotation16, boolean freeX, boolean freeY, boolean freeZ) {
		/**
		 * The part of the face that gives the same result: the same side of the middle on the face's axes that matter (where
		 * the click lands only matters for some blocks; found in testing: a slab on the floor of a one-block hole, whose
		 * bottom it could only see a sliver of, was "out of reach" for want of one quarter of it).
		 */
		@Nullable
		public Predicate<Vec3> region() {
			Direction.Axis axis = this.face.getAxis();
			boolean fx = this.freeX || axis == Direction.Axis.X;
			boolean fy = this.freeY || axis == Direction.Axis.Y;
			boolean fz = this.freeZ || axis == Direction.Axis.Z;
			if (fx && fy && fz) {
				return null;
			}

			Vec3 want = this.local;
			return hit -> (fx || side(hit.x) == side(want.x)) && (fy || side(hit.y) == side(want.y)) && (fz || side(hit.z) == side(want.z));
		}

		static int side(double v) {
			return v < 0.5 ? 0 : 1;
		}

		/** The yaw it clicks with (16 steps for signs and banners). */
		public float yRot() {
			return this.rotation16 >= 0 ? this.rotation16 * 22.5F : this.yaw.toYRot();
		}
	}

	/** Properties that decide how a placed block looks. */
	private static final List<Property<?>> ORIENTATION = List.of(BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING,
		BlockStateProperties.FACING_HOPPER, BlockStateProperties.AXIS, BlockStateProperties.HORIZONTAL_AXIS, BlockStateProperties.HALF,
		BlockStateProperties.SLAB_TYPE, BlockStateProperties.DOOR_HINGE, BlockStateProperties.ATTACH_FACE, BlockStateProperties.ROTATION_16,
		BlockStateProperties.BELL_ATTACHMENT, BlockStateProperties.HANGING, BlockStateProperties.ORIENTATION);

	private static final double[] SPOTS = {0.25, 0.75};
	private static final float[] PITCHES = {0.0F, 70.0F, -70.0F};

	private Placement() {
	}

	/**
	 * What to place now on the way to {@code want}: itself, or for a double slab not yet half there, a single slab first.
	 */
	public static BlockState step(Level level, BlockPos pos, BlockState want) {
		if (want.hasProperty(BlockStateProperties.SLAB_TYPE) && want.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE
			&& !level.getBlockState(pos).is(want.getBlock())) {
			return want.setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
		}

		return want;
	}

	/** It already stands there as the plan has it (as far as its look goes). */
	public static boolean matches(BlockState have, BlockState want) {
		// (The same block in another wood counts: oak stairs where the plan now has spruce, after it ran out of oak.)
		if (have.getBlock() != want.getBlock() && !sameButWood(have, want)) {
			return false;
		}

		for (Property<?> property : ORIENTATION) {
			if (want.hasProperty(property) && have.hasProperty(property) && !have.getValue(property).equals(want.getValue(property))) {
				return false;
			}
		}

		return true;
	}

	/** The same kind of block in two woods (oak and spruce stairs; not a log and a stripped log). */
	private static boolean sameButWood(BlockState have, BlockState want) {
		String a = woodShape(have);
		return a != null && a.equals(woodShape(want));
	}

	/** A wood block's id with the wood left out ("stripped_*_log"), or null if it isn't one. */
	@Nullable
	private static String woodShape(BlockState state) {
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		String wood = com.dinglebob.drifter.building.plan.HousePalette.woodOf(path);
		return wood == null ? null : path.replaceFirst("^(stripped_)?" + wood + "_", "$1*_");
	}

	/** Every way to get {@code want} at {@code pos} with what's round it now; best first (clicking the block below first). */
	public static List<Way> ways(DrifterEntity drifter, BlockPos pos, BlockState want) {
		Level level = drifter.level();
		Item item = want.getBlock().asItem();
		if (!(item instanceof BlockItem blockItem)) {
			return List.of();
		}

		ItemStack stack = new ItemStack(item);
		boolean rotation16 = want.hasProperty(BlockStateProperties.ROTATION_16);
		return FakePlayerBridge.act(drifter, fake -> {
			float yRot = fake.getYRot();
			float xRot = fake.getXRot();
			List<Way> ways = new ArrayList<>();
			try {
				// Clicking the block that's there itself (adding to a slab), then each neighbour's face toward it.
				List<BlockPos> supports = new ArrayList<>();
				List<Direction> faces = new ArrayList<>();
				for (Direction face : new Direction[] {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
					BlockPos support = pos.relative(face.getOpposite());
					if (!level.getBlockState(support).getCollisionShape(level, support).isEmpty() || !level.getBlockState(support).getShape(level, support).isEmpty()) {
						supports.add(support);
						faces.add(face);
					}
				}

				if (!level.getBlockState(pos).isAir()) {
					for (Direction face : Direction.values()) {
						supports.add(pos);
						faces.add(face);
					}
				}

				for (int i = 0; i < supports.size(); i++) {
					BlockPos support = supports.get(i);
					Direction face = faces.get(i);
					for (int yaw = 0; yaw < (rotation16 ? 16 : 4); yaw++) {
						Direction look = Direction.from2DDataValue(yaw % 4);
						for (float pitch : PITCHES) {
							fake.setYRot(rotation16 ? yaw * 22.5F : look.toYRot());
							fake.setXRot(pitch);
							// Which quarters of the face give it, looking this way.
							List<Vec3> good = new ArrayList<>();
							for (Vec3 local : spots(face)) {
								BlockHitResult hit = new BlockHitResult(Vec3.atLowerCornerOf(support).add(local), face, support, false);
								BlockPlaceContext context = new BlockPlaceContext(fake, InteractionHand.MAIN_HAND, stack, hit);
								if (!context.getClickedPos().equals(pos) || !context.canPlace()) {
									continue;
								}

								BlockState result = ((BlockItemAccessor)blockItem).drifter$getPlacementState(context);
								if (result != null && matches(result, want)) {
									good.add(local);
								}
							}

							if (good.isEmpty()) {
								continue;
							}

							// An axis is free if the result doesn't depend on which side of the middle it's clicked.
							Vec3 first = good.get(0);
							boolean freeX = good.stream().anyMatch(v -> Way.side(v.x) != Way.side(first.x)) && good.size() >= 2;
							boolean freeY = good.stream().anyMatch(v -> Way.side(v.y) != Way.side(first.y)) && good.size() >= 2;
							boolean freeZ = good.stream().anyMatch(v -> Way.side(v.z) != Way.side(first.z)) && good.size() >= 2;
							if (good.size() == 4) {
								freeX = freeY = freeZ = true;
							}

							ways.add(new Way(support, face, first, look, pitch, rotation16 ? yaw : -1, freeX, freeY, freeZ));
							break;
						}
					}
				}
			} finally {
				fake.setYRot(yRot);
				fake.setXRot(xRot);
			}

			return ways;
		});
	}

	/** Spots to try on a face, in the block's own 0 to 1 coordinates: the four quarters of it. */
	private static List<Vec3> spots(Direction face) {
		List<Vec3> spots = new ArrayList<>();
		double onFace = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0;
		for (double a : SPOTS) {
			for (double b : SPOTS) {
				spots.add(switch (face.getAxis()) {
					case X -> new Vec3(onFace, a, b);
					case Y -> new Vec3(a, onFace, b);
					case Z -> new Vec3(a, b, onFace);
				});
			}
		}

		return spots;
	}

	/** The first way it can do it from where it stands (the face in reach and the spot visible), or null. */
	@Nullable
	public static Way reachable(DrifterEntity drifter, List<Way> ways) {
		for (Way way : ways) {
			if (Reach.aimPointFrom(drifter, drifter.getEyePosition(), way.support(), way.face(), way.region()).isPresent()) {
				return way;
			}
		}

		return null;
	}
}
