package com.dinglebob.drifter.nav;

import java.util.List;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Going somewhere far, a stretch at a time, as a player crosses country (raids, trade runs, envoys, bandits' trips):
 * <ul>
 *   <li>along a road, when one goes there (an addon's roads between towns: {@link #setRoutes}; the user, 2026-11-06:
 *       "when drifters set off to another town, their priority path should be following the road"): the next stretch is
 *       a bit further along it, joining it first if it's off it;</li>
 *   <li>else straight on, about {@link #STRETCH} blocks, to open ground there: never the top of what stands there (a
 *       stretch's end on a house's roof had a war's leader going in and out of the houses of its own town, the user's
 *       notes).</li>
 * </ul>
 */
public final class Legs {
	private Legs() {
	}

	/** About how far a stretch goes. */
	public static final double STRETCH = 32.0;
	/** A route's point with this height: from it to the next is across water, by boat (a dock to a dock). */
	public static final int FERRY = -1;
	/** Within this, it goes straight there. */
	private static final double NEAR = 40.0;
	/** A road this far off at most is joined; past it, it's not this one's road. */
	private static final double JOIN = 64.0;

	/** The way along roads from here to there (in order: points on the ground's map, y ignored), or empty. */
	private static volatile BiFunction<BlockPos, BlockPos, List<BlockPos>> routes = (from, to) -> List.of();

	/** An addon's roads (Drifter: Civilizations, its world plan's roads between towns). */
	public static void setRoutes(BiFunction<BlockPos, BlockPos, List<BlockPos>> which) {
		routes = which;
	}

	/** Points of the roads between towns within a distance of somewhere (x, z; y ignored), or none. */
	private static volatile BiFunction<BlockPos, Integer, List<BlockPos>> roadPoints = (at, within) -> List.of();

	/** An addon's roads, near a spot (for encounters on the road). */
	public static void setRoadPoints(BiFunction<BlockPos, Integer, List<BlockPos>> which) {
		roadPoints = which;
	}

	/** Points of the roads between towns within this far of here (x, z), or none. */
	public static List<BlockPos> roadPointsNear(BlockPos at, int within) {
		try {
			List<BlockPos> points = roadPoints.apply(at, within);
			return points == null ? List.of() : points;
		} catch (RuntimeException e) {
			return List.of();
		}
	}

	/** On (or by) a road between towns. */
	public static boolean onRoad(BlockPos at) {
		return !roadPointsNear(at, 5).isEmpty();
	}

	/** The next stretch toward {@code to} (within {@code near} of it at the end), saying {@code what} it's doing. */
	public static Task toward(DrifterEntity drifter, BlockPos to, double near, String what) {
		return toward(drifter, to, near, what, NavigateTask.Pace.TRAVEL);
	}

	/** The same, at this pace (a war's leader marches at a walk, its band round it). */
	public static Task toward(DrifterEntity drifter, BlockPos to, double near, String what, NavigateTask.Pace pace) {
		Vec3 from = drifter.position();
		double distance = Math.hypot(to.getX() + 0.5 - from.x, to.getZ() + 0.5 - from.z);
		if (distance <= NEAR) {
			return new NavigateTask(drifter, Vec3.atBottomCenterOf(to), near, what, pace);
		}

		BlockPos road = alongRoad(drifter, to, distance);
		if (road != null) {
			return new NavigateTask(drifter, Vec3.atBottomCenterOf(road), 3.0, what + " (along the road)", pace);
		}

		return new NavigateTask(drifter, Vec3.atBottomCenterOf(straight(drifter, to, distance)), 4.0, what, pace);
	}

	/** A stretch along a road there, or null (no road, or it's not on the way, or the road's done: straight on). */
	@Nullable
	private static BlockPos alongRoad(DrifterEntity drifter, BlockPos to, double distance) {
		List<BlockPos> route;
		try {
			route = routes.apply(drifter.blockPosition(), to);
		} catch (RuntimeException e) {
			return null;
		}

		if (route == null || route.size() < 2) {
			return null;
		}

		BlockPos here = drifter.blockPosition();
		int nearest = -1;
		double best = Double.MAX_VALUE;
		for (int i = 0; i < route.size(); i++) {
			double d = Math.hypot(route.get(i).getX() - here.getX(), route.get(i).getZ() - here.getZ());
			if (d < best) {
				best = d;
				nearest = i;
			}
		}

		if (best > JOIN) {
			return null;
		}

		// At a dock, the way on across water: straight to the far one (its walk takes the boat out: NavigateTask, BoatTask).
		if (route.get(nearest).getY() == FERRY && best <= 12.0 && nearest + 1 < route.size()) {
			BlockPos far = route.get(nearest + 1);
			return ground(drifter.level(), far.getX(), far.getZ());
		}

		// Off it: onto it first. On it: a stretch further along (a point on the way that far on, between two of its points if
		// they're far apart; never past a dock: it stops there for the boat); the road's done within a stretch of the end:
		// straight on.
		double x;
		double z;
		if (best > 8.0) {
			x = route.get(nearest).getX();
			z = route.get(nearest).getZ();
		} else {
			double left = STRETCH;
			int i = nearest;
			x = route.get(i).getX();
			z = route.get(i).getZ();
			while (left > 0) {
				if (i + 1 >= route.size()) {
					return null;
				}

				BlockPos a = route.get(i);
				BlockPos b = route.get(i + 1);
				if (a.getY() == FERRY && i > nearest) {
					x = a.getX();
					z = a.getZ();
					break;
				}

				double length = Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ());
				if (length >= left) {
					x = a.getX() + (b.getX() - a.getX()) * left / length;
					z = a.getZ() + (b.getZ() - a.getZ()) * left / length;
					left = 0;
				} else {
					left -= length;
					i++;
					x = b.getX();
					z = b.getZ();
				}
			}
		}

		return openNear(drifter.level(), (int)Math.floor(x), (int)Math.floor(z), 4);
	}

	/** About a stretch straight toward it, to open ground near there (not a roof, not water). */
	private static BlockPos straight(DrifterEntity drifter, BlockPos to, double distance) {
		Vec3 from = drifter.position();
		double fx = (to.getX() + 0.5 - from.x) / distance;
		double fz = (to.getZ() + 0.5 - from.z) / distance;
		return openNear(drifter.level(), (int)Math.floor(from.x + fx * STRETCH), (int)Math.floor(from.z + fz * STRETCH), 6);
	}

	/** Open ground round this column (rings out to {@code rings}), else its own top. */
	private static BlockPos openNear(Level level, int sx, int sz, int rings) {
		for (int ring = 0; ring <= rings; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || !level.hasChunkAt(new BlockPos(sx + dx, 0, sz + dz))) {
						continue;
					}

					BlockPos feet = ground(level, sx + dx, sz + dz);
					if (open(level, feet)) {
						return feet;
					}
				}
			}
		}

		return ground(level, sx, sz);
	}

	/** Where one stands in this column (on top of it). */
	private static BlockPos ground(Level level, int x, int z) {
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
	}

	/** Open ground: the land, a road or a path under it (not a roof, a wall's top, water). */
	private static boolean open(Level level, BlockPos feet) {
		BlockState under = level.getBlockState(feet.below());
		if (!under.getFluidState().isEmpty() || !under.isFaceSturdy(level, feet.below(), Direction.UP)) {
			return false;
		}

		String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(under.getBlock()).getPath();
		return com.dinglebob.drifter.brain.activity.BuildPlanActivity.natural(under) && !under.is(net.minecraft.tags.BlockTags.LOGS)
			&& !under.is(net.minecraft.tags.BlockTags.LEAVES) || id.contains("path") || id.contains("gravel") || id.contains("cobblestone")
			|| id.contains("stone_brick") || id.contains("mud_brick") || NavView.naturalSlab(under);
	}
}
