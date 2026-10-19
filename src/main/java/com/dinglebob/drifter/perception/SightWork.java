package com.dinglebob.drifter.perception;

import java.util.ArrayList;
import java.util.List;

import com.dinglebob.drifter.nav.NavWorld;
import com.dinglebob.drifter.nav.PathWorkers;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Eye rays traced on the path threads (milestone 16, performance: a thousand Drifters' eye rays were the server thread's
 * biggest single cost). A Drifter's rays this tick are worked out on the server thread as before (where it looks, how far
 * the land is loaded), traced through the path threads' frozen copy of the world ({@link com.dinglebob.drifter.nav.WorldCopy})
 * after the tick, and what they hit is taken in at its next tick (the light, its block knowledge, what it notices): it sees
 * the world as it was a tick ago, 50 ms, well under a person's reaction time. Its far sight (the long rays to the skyline)
 * stays on the server thread: the copy only reaches round it.
 */
public final class SightWork {
	private SightWork() {
	}

	/** One Drifter's rays for a tick: what to trace, and (when {@link #done}) what each hit. */
	public static final class Batch {
		final Vec3 eye;
		final Vec3[] directions;
		final Vec3[] ends;
		final double[] ranges;
		final NavWorld view;
		final BlockHitResult[] hits;
		volatile boolean done;

		Batch(Vec3 eye, Vec3[] directions, Vec3[] ends, double[] ranges, NavWorld view) {
			this.eye = eye;
			this.directions = directions;
			this.ends = ends;
			this.ranges = ranges;
			this.view = view;
			this.hits = new BlockHitResult[directions.length];
		}

		void trace() {
			try {
				for (int i = 0; i < this.directions.length; i++) {
					this.hits[i] = clipThroughGlass(this.view, this.eye, this.ends[i]);
				}
			} catch (RuntimeException e) {
				// (A copy is only a copy: a surprise loses these rays, not the game.)
				for (int i = 0; i < this.hits.length; i++) {
					if (this.hits[i] == null) {
						Vec3 back = this.eye.subtract(this.ends[i]);
						this.hits[i] = BlockHitResult.miss(this.eye, Direction.getNearest(back.x, back.y, back.z), BlockPos.containing(this.eye));
					}
				}
			} finally {
				this.done = true;
			}
		}
	}

	/** Batches made this tick, handed to the path threads at its end (in groups, not one by one). */
	private static final List<Batch> queued = new ArrayList<>();
	private static final int PER_JOB = 32;

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> flush());
	}

	static void queue(Batch batch) {
		queued.add(batch);
	}

	private static void flush() {
		if (queued.isEmpty()) {
			return;
		}

		for (int start = 0; start < queued.size(); start += PER_JOB) {
			List<Batch> group = List.copyOf(queued.subList(start, Math.min(queued.size(), start + PER_JOB)));
			// (Before path searches: these are small and the Drifter looks for them next tick.)
			PathWorkers.submit(() -> group.forEach(Batch::trace), -1);
		}

		queued.clear();
	}

	/** Most see-through blocks (glass, panes) a ray passes, as Senses does. */
	private static final int MAX_SEE_THROUGH = 8;

	/** Senses#clipThroughGlass on the copy. */
	static BlockHitResult clipThroughGlass(BlockGetter world, Vec3 from, Vec3 to) {
		Vec3 start = from;
		Vec3 direction = to.subtract(from).normalize();
		for (int i = 0; i <= MAX_SEE_THROUGH; i++) {
			BlockHitResult hit = clip(world, start, to);
			if (hit.getType() != HitResult.Type.BLOCK || !isSeeThrough(world, hit.getBlockPos())) {
				return hit;
			}

			start = Senses.exitPoint(hit.getLocation(), direction, hit.getBlockPos());
			if (start.distanceToSqr(from) >= to.distanceToSqr(from)) {
				break;
			}
		}

		return BlockHitResult.miss(to, Direction.getNearest(direction.x, direction.y, direction.z), BlockPos.containing(to));
	}

	/** Senses#clip on the copy: outline shapes, any fluid, air passed over at once. */
	private static BlockHitResult clip(BlockGetter world, Vec3 from, Vec3 to) {
		CollisionContext context = CollisionContext.empty();
		return BlockGetter.traverseBlocks(from, to, world, (getter, pos) -> {
			BlockState state = getter.getBlockState(pos);
			if (state.isAir()) {
				return null;
			}

			VoxelShape shape = ClipContext.Block.OUTLINE.get(state, getter, pos, context);
			BlockHitResult blockHit = shape.isEmpty() ? null : getter.clipWithInteractionOverride(from, to, pos, shape, state);
			var fluid = state.getFluidState();
			BlockHitResult fluidHit = fluid.isEmpty() ? null : fluid.getShape(getter, pos).clip(from, to, pos);
			if (blockHit == null) {
				return fluidHit;
			}

			if (fluidHit == null) {
				return blockHit;
			}

			return from.distanceToSqr(blockHit.getLocation()) <= from.distanceToSqr(fluidHit.getLocation()) ? blockHit : fluidHit;
		}, getter -> {
			Vec3 back = from.subtract(to);
			return BlockHitResult.miss(to, Direction.getNearest(back.x, back.y, back.z), BlockPos.containing(to));
		});
	}

	private static boolean isSeeThrough(BlockGetter world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return state.getFluidState().isEmpty() && !state.getCollisionShape(world, pos).isEmpty()
			&& state.getVisualShape(world, pos, CollisionContext.empty()).isEmpty();
	}
}
