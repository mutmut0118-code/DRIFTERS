package com.dinglebob.drifter.building.plan;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Where a plan's house goes (house plans, 2026-10-16): a spot near home it knows, out of the way of its hut, farm and pen,
 * as flat and dry as it can find, with as few trees in the way as can be; turned whichever of four ways suits, the door
 * toward home if it can. The ground there gets levelled before it builds (user's rule: never floating), so a little
 * unevenness is fine; a lot (more than 4 blocks up or down) isn't.
 */
public final class PlanSite {
	public record Site(BlockPos origin, Rotation rotation, double cost) {
	}

	private static final int MAX_STEP = 4;
	/** Most of a footprint it may not have seen the ground of. */
	private static final double MAX_UNSEEN = 0.35;
	private static final int[] RINGS = {6, 9, 12, 15, 18, 22, 26, 30};
	private static final int ANGLES = 16;

	private PlanSite() {
	}

	/** Why spots were turned down on the last search (for its thoughts: "nowhere ... (too steep 40, ...)"). */
	private static final java.util.Map<String, Integer> REJECTED = new java.util.TreeMap<>();

	/** Why the last search turned spots down, most common first. */
	public static String lastRejections() {
		return REJECTED.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(4)
			.map(e -> e.getKey() + " " + e.getValue()).collect(java.util.stream.Collectors.joining(", "));
	}

	private static Site reject(String why) {
		REJECTED.merge(why, 1, Integer::sum);
		return null;
	}

	/** The best spot for this plan round its home, or null if there's none it knows. */
	@Nullable
	public static Site choose(DrifterEntity drifter, HousePlan plan, HousePalette palette) {
		Home home = drifter.getDrifterBrain().home();
		if (home == null) {
			return null;
		}

		Level level = drifter.level();
		REJECTED.clear();
		boolean groundAtBottom = plan.cells.stream().anyMatch(c -> c.pos().getY() == 0 && PlacedPlan.isGround(c.state()));
		Site best = null;
		for (Rotation rotation : Rotation.values()) {
			PlacedPlan shape = new PlacedPlan(plan, BlockPos.ZERO, rotation, palette);
			BlockPos door = shape.build.stream().filter(c -> c.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
				.map(PlacedPlan.Cell::pos).findFirst().orElse(new BlockPos(shape.sizeX / 2, 0, shape.sizeZ / 2));
			for (int ring : RINGS) {
				for (int a = 0; a < ANGLES; a++) {
					double angle = Math.PI * 2.0 * a / ANGLES;
					int cx = home.center.getX() + (int)Math.round(Math.cos(angle) * (ring + Math.max(shape.sizeX, shape.sizeZ) / 2.0));
					int cz = home.center.getZ() + (int)Math.round(Math.sin(angle) * (ring + Math.max(shape.sizeX, shape.sizeZ) / 2.0));
					BlockPos corner = new BlockPos(cx - shape.sizeX / 2, 0, cz - shape.sizeZ / 2);
					Site site = score(drifter, level, home, shape, corner, rotation, groundAtBottom, door);
					if (site != null && (best == null || site.cost() < best.cost())) {
						best = site;
					}
				}
			}
		}

		return best;
	}

	/**
	 * It has seen this column's ground, or what lies on it (snow, grass, flowers, the leaves of a tree over it: found in
	 * testing, a snowy taiga's ground was all "unseen" because it had only ever seen the snow on it).
	 */
	private static boolean seenDownTo(DrifterEntity drifter, BlockPos from, BlockPos ground) {
		for (BlockPos pos = from; pos.getY() >= ground.getY(); pos = pos.below()) {
			if (drifter.getSenses().terrain().isKnown(pos)) {
				return true;
			}
		}

		return false;
	}

	@Nullable
	private static Site score(DrifterEntity drifter, Level level, Home home, PlacedPlan shape, BlockPos corner, Rotation rotation,
		boolean groundAtBottom, BlockPos door) {
		List<Integer> heights = new ArrayList<>();
		int trees = 0;
		int unknown = 0;
		for (int dx = -1; dx <= shape.sizeX; dx++) {
			for (int dz = -1; dz <= shape.sizeZ; dz++) {
				BlockPos column = corner.offset(dx, 0, dz);
				// Clear of its own things, with a block's margin.
				if (home.inOwnBuilding(column.atY(home.center.getY()), 2) || home.farm != null && home.farm.closerThan(column.atY(home.farm.getY()), 6)
					|| home.pen != null && home.pen.closerThan(column.atY(home.pen.getY()), 7)
					|| home.enchantingTable != null && home.enchantingTable.closerThan(column.atY(home.enchantingTable.getY()), 4)) {
					return reject("in the way of its things");
				}

				if (dx < 0 || dz < 0 || dx >= shape.sizeX || dz >= shape.sizeZ) {
					continue;
				}

				if (!level.isLoaded(column)) {
					return reject("not loaded");
				}

				// The ground under any tree: down past logs, leaves and plants.
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
				boolean tree = false;
				while (top.getY() > level.getMinBuildHeight() && (level.getBlockState(top).is(BlockTags.LOGS) || level.getBlockState(top).is(BlockTags.LEAVES)
					|| level.getBlockState(top).canBeReplaced() && level.getFluidState(top).isEmpty())) {
					tree |= level.getBlockState(top).is(BlockTags.LOGS);
					top = top.below();
				}

				// Ground it hasn't seen (under trees, behind a rise) it guesses is like the rest, as a player sizing up a spot
				// does; only if it has seen too little of it does it look elsewhere.
				if (!seenDownTo(drifter, column.atY(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).getY() - 1), top)) {
					unknown++;
					continue;
				}

				BlockState ground = level.getBlockState(top);
				if (!level.getFluidState(top).isEmpty() || !level.getFluidState(top.above()).isEmpty() || ground.is(net.minecraft.world.level.block.Blocks.ICE)) {
					return reject("water");
				}

				trees += tree ? 1 : 0;
				heights.add(top.getY());
			}
		}

		if (unknown > shape.sizeX * shape.sizeZ * MAX_UNSEEN) {
			return reject("ground it hasn't seen");
		}

		heights.sort(Integer::compare);
		int base = heights.get(heights.size() / 2);
		double cost = 0.0;
		for (int h : heights) {
			if (Math.abs(h - base) > MAX_STEP) {
				return reject("too steep");
			}

			cost += Math.abs(h - base);
		}

		BlockPos origin = corner.atY(groundAtBottom ? base : base + 1);
		double distance = Math.sqrt(origin.offset(shape.sizeX / 2, 0, shape.sizeZ / 2).distSqr(home.center.atY(origin.getY())));
		BlockPos doorAt = origin.offset(door.getX(), 0, door.getZ());
		double doorToHome = Math.sqrt(doorAt.distSqr(home.center.atY(doorAt.getY())));
		return new Site(origin, rotation, cost + trees * 6.0 + distance * 0.3 + doorToHome * 0.4);
	}
}
