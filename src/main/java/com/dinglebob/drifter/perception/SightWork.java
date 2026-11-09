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

	/**
	 * A sight line to a creature (Senses#look: eye to its head, feet or middle), traced on the path threads (milestone 16
	 * crowd pass, 2026-11-08: in a 500v500 the soldiers' sight lines to each other were a twentieth of the server's time).
	 * Each look takes the answer to the line it asked at its last look, if that was asked lately from close to where both
	 * stand now, and asks again; otherwise it traces on the spot as before. So what it sees of a creature it's watching is
	 * at most a look old (a tenth of a second or two, and never from more than a block and a half off), through the world as
	 * it was a tick ago, as its eye rays already are.
	 */
	public static final class Line {
		/** What the last finished line said (1 clear, -1 blocked, 0 none), between which points, and when it was asked. */
		byte known;
		Vec3 knownFrom = Vec3.ZERO;
		Vec3 knownTo = Vec3.ZERO;
		long knownAsked = Long.MIN_VALUE / 2;
		/** The line out on the path threads (when {@link #out}). */
		boolean out;
		Vec3 from = Vec3.ZERO;
		Vec3 to = Vec3.ZERO;
		long asked;
		volatile byte result;
		volatile boolean done;

		/** Takes in a finished line, if one is back. */
		void collect() {
			if (this.out && this.done) {
				this.out = false;
				this.known = this.result;
				this.knownFrom = this.from;
				this.knownTo = this.to;
				this.knownAsked = this.asked;
			}
		}

		/** Last known answer usable for a line between these points now, or 0. */
		byte answer(Vec3 from, Vec3 to, long now) {
			if (this.known == 0 || now - this.knownAsked > MAX_AGE) {
				return 0;
			}

			return this.knownFrom.distanceToSqr(from) <= MAX_DRIFT * MAX_DRIFT && this.knownTo.distanceToSqr(to) <= MAX_DRIFT * MAX_DRIFT ? this.known : 0;
		}

		/** Traced on the spot: the answer to keep. */
		void traced(Vec3 from, Vec3 to, long now, boolean clear) {
			this.known = clear ? (byte)1 : (byte)-1;
			this.knownFrom = from;
			this.knownTo = to;
			this.knownAsked = now;
		}

		/** Sends the line out to be traced for the next look (unless one is out already). */
		void ask(Vec3 from, Vec3 to, long now, net.minecraft.server.level.ServerLevel level) {
			if (this.out) {
				return;
			}

			this.out = true;
			this.done = false;
			this.result = 0;
			this.from = from;
			this.to = to;
			this.asked = now;
			queuedLines.computeIfAbsent(level, key -> new ArrayList<>()).add(this);
		}

		void trace(NavWorld world) {
			try {
				this.result = visualLine(world, this.from, this.to);
			} catch (RuntimeException e) {
				this.result = 0;
			} finally {
				this.done = true;
			}
		}
	}

	/** An answer older than this (ticks) isn't used. */
	static final long MAX_AGE = 10;
	/** Nor one from more than this (blocks) from where either end is now. */
	static final double MAX_DRIFT = 1.5;

	/** A line through a part of the world the copy doesn't have: stop and say "don't know". */
	private static final BlockHitResult NOT_COPIED = BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO);

	/**
	 * Senses#lineOfSight on the copy (the blocks' visual shapes, fluids let through): 1 clear, -1 blocked, 0 when the line
	 * crosses a part of the world the copy doesn't have.
	 */
	static byte visualLine(NavWorld world, Vec3 from, Vec3 to) {
		CollisionContext context = CollisionContext.empty();
		BlockHitResult hit = BlockGetter.traverseBlocks(from, to, world, (getter, pos) -> {
			if (!getter.loaded(pos)) {
				return NOT_COPIED;
			}

			BlockState state = getter.getBlockState(pos);
			if (state.isAir()) {
				return null;
			}

			VoxelShape shape = ClipContext.Block.VISUAL.get(state, getter, pos, context);
			return shape.isEmpty() ? null : getter.clipWithInteractionOverride(from, to, pos, shape, state);
		}, getter -> BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to)));
		if (hit == NOT_COPIED) {
			return 0;
		}

		return hit.getType() == HitResult.Type.MISS ? (byte)1 : (byte)-1;
	}

	/** Batches made this tick, handed to the path threads at its end (in groups, not one by one). */
	private static final List<Batch> queued = new ArrayList<>();
	/** Lines asked this tick, by level. */
	private static final java.util.Map<net.minecraft.server.level.ServerLevel, List<Line>> queuedLines = new java.util.IdentityHashMap<>();
	private static final int PER_JOB = 32;
	private static final int LINES_PER_JOB = 128;

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> flush());
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> queuedLines.clear());
	}

	static void queue(Batch batch) {
		queued.add(batch);
	}

	private static void flush() {
		for (var entry : queuedLines.entrySet()) {
			List<Line> lines = entry.getValue();
			for (int start = 0; start < lines.size(); start += LINES_PER_JOB) {
				List<Line> group = List.copyOf(lines.subList(start, Math.min(lines.size(), start + LINES_PER_JOB)));
				// (A view of the copy for each job: a view remembers the last column it read, so it's one thread's.)
				NavWorld view = com.dinglebob.drifter.nav.WorldCopy.view(entry.getKey());
				PathWorkers.submit(() -> group.forEach(line -> line.trace(view)), -1);
			}
		}

		queuedLines.clear();
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
