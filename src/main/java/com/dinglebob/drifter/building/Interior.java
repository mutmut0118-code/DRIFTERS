package com.dinglebob.drifter.building;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where things go in a room it didn't lay out itself (a village house it moved into; found by the user: chests, a table
 * and a furnace "all over the place" in one, each put down wherever was free next to it at the time). The room is the
 * floor round its bed, inside the walls; its chests go in a row along a wall from a corner, its crafting table and
 * furnace together along another, never in the doorway, and always leaving a way from the door to the bed and to every
 * chest and station. The foot of a ladder stays clear (found by the user: a crafting table and furnace right in front of
 * one).
 */
public final class Interior {
	public enum Kind {
		CHEST, STATION
	}

	/** Bigger than this and it isn't a room (the fill got out through a gap). */
	private static final int MAX_ROOM = 64;

	private final net.minecraft.world.level.BlockGetter level;
	private final Set<BlockPos> room = new HashSet<>();
	private final Set<BlockPos> doorInside = new HashSet<>();
	private final Set<BlockPos> bedSide = new HashSet<>();
	/** Ladders (and other climbables) at floor level round the room. */
	private final Set<BlockPos> ladders = new HashSet<>();

	private Interior(net.minecraft.world.level.BlockGetter level) {
		this.level = level;
	}

	/** The room round this bed (either half), or null if it isn't walled in. */
	@Nullable
	public static Interior of(net.minecraft.world.level.BlockGetter level, BlockPos bed) {
		BlockState state = level.getBlockState(bed);
		if (!(state.getBlock() instanceof BedBlock)) {
			return null;
		}

		BlockPos other = bed.relative(BedBlock.getConnectedDirection(state));
		Interior interior = new Interior(level);
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos half : new BlockPos[] {bed, other}) {
			interior.room.add(half);
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = half.relative(side);
				if (interior.open(next) && !next.equals(bed) && !next.equals(other)) {
					interior.bedSide.add(next);
					if (interior.room.add(next)) {
						queue.add(next);
					}
				}
			}
		}

		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = pos.relative(side);
				if (level.getBlockState(next).getBlock() instanceof DoorBlock) {
					interior.doorInside.add(pos);
					continue;
				}

				if (level.getBlockState(next).is(net.minecraft.tags.BlockTags.CLIMBABLE)) {
					interior.ladders.add(next.immutable());
					continue;
				}

				if ((interior.open(next) || interior.furniture(next)) && interior.room.add(next)) {
					if (interior.room.size() > MAX_ROOM) {
						return null;
					}

					queue.add(next);
				}
			}
		}

		return interior;
	}

	/** Floor that's free to walk on or put something on. */
	private boolean open(BlockPos pos) {
		return this.level.getBlockState(pos).getCollisionShape(this.level, pos).isEmpty() && this.level.getFluidState(pos).isEmpty()
			&& this.level.getBlockState(pos.below()).isFaceSturdy(this.level, pos.below(), Direction.UP);
	}

	/** Something put down in the room (a chest, a station, another bed): part of the room, not a wall. */
	private boolean furniture(BlockPos pos) {
		BlockState state = this.level.getBlockState(pos);
		return com.dinglebob.drifter.brain.Home.isStorageBlock(state) || state.getBlock() instanceof BedBlock
			|| state.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE) || state.getBlock() instanceof net.minecraft.world.level.block.AbstractFurnaceBlock
			|| com.dinglebob.drifter.building.plan.HousePlan.furniture(state);
	}

	private boolean wall(BlockPos pos) {
		return !this.room.contains(pos) && !(this.level.getBlockState(pos).getBlock() instanceof DoorBlock)
			&& !this.level.getBlockState(pos).getCollisionShape(this.level, pos).isEmpty();
	}

	/**
	 * The best free spot for a chest or a station, or null if putting anything more down would block the way. Corners
	 * first, then next to its own kind (a row), away from the door.
	 */
	@Nullable
	public BlockPos spot(Kind kind, Set<BlockPos> excluded) {
		BlockPos best = null;
		double bestScore = -Double.MAX_VALUE;
		for (BlockPos pos : this.room) {
			if (excluded.contains(pos) || !this.open(pos) || this.byDoor(pos) || this.byLadder(pos)
				|| !this.level.getBlockState(pos.above()).getCollisionShape(this.level, pos.above()).isEmpty()) {
				continue;
			}

			int walls = 0;
			int same = 0;
			int other = 0;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = pos.relative(side);
				walls += this.wall(next) ? 1 : 0;
				BlockState state = this.level.getBlockState(next);
				boolean chest = com.dinglebob.drifter.brain.Home.isStorageBlock(state);
				boolean station = state.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)
					|| state.getBlock() instanceof net.minecraft.world.level.block.AbstractFurnaceBlock;
				same += kind == Kind.CHEST ? (chest ? 1 : 0) : (station ? 1 : 0);
				other += kind == Kind.CHEST ? (station ? 1 : 0) : (chest ? 1 : 0);
			}

			if (walls == 0 || !this.stillWalkable(pos)) {
				continue;
			}

			// A chest beside a single chest along the same wall makes a double chest (user, 2026-10-17: "double chests dont
			// work"): that spot first.
			double pairs = kind == Kind.CHEST && this.pairsWithChest(pos) ? 6.0 : 0.0;
			double fromDoor = this.doorInside.stream().mapToDouble(d -> Math.sqrt(d.distSqr(pos))).min().orElse(3.0);
			// Not right by the bed if it can help it (the way into bed).
			double score = pairs + (walls >= 2 ? 3.0 : 0.0) + same * 2.5 - other * 1.0 + fromDoor * 0.3 - (this.bedSide.contains(pos) ? 4.0 : 0.0);
			if (score > bestScore) {
				bestScore = score;
				best = pos.immutable();
			}
		}

		return best;
	}

	/**
	 * A chest here would join a single chest beside it: the neighbor faces the way a chest here would (into the room) and
	 * sits to its side, not in front or behind.
	 */
	private boolean pairsWithChest(BlockPos pos) {
		if (!(this.level instanceof net.minecraft.world.level.LevelAccessor accessor)) {
			return false;
		}

		Direction facing = Furnish.facing(accessor, pos);
		for (Direction side : new Direction[] {facing.getClockWise(), facing.getCounterClockWise()}) {
			BlockState there = this.level.getBlockState(pos.relative(side));
			if (there.is(net.minecraft.world.level.block.Blocks.CHEST) && there.getValue(net.minecraft.world.level.block.ChestBlock.FACING) == facing
				&& there.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
				return true;
			}
		}

		return false;
	}

	/** In the doorway, or the step in from it. */
	private boolean byDoor(BlockPos pos) {
		for (BlockPos inside : this.doorInside) {
			if (Math.abs(inside.getX() - pos.getX()) + Math.abs(inside.getZ() - pos.getZ()) <= 1) {
				return true;
			}
		}

		return false;
	}

	/** Beside the foot of a ladder (where you step on and off it). */
	private boolean byLadder(BlockPos pos) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (this.ladders.contains(pos.relative(side))) {
				return true;
			}
		}

		return false;
	}

	/** With something put down here, it can still walk from the door to the bed and to everything in the room. */
	private boolean stillWalkable(BlockPos taken) {
		Set<BlockPos> reached = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos start : this.doorInside.isEmpty() ? this.bedSide : this.doorInside) {
			if (!start.equals(taken) && this.open(start)) {
				reached.add(start);
				queue.add(start);
			}
		}

		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = pos.relative(side);
				if (this.room.contains(next) && !next.equals(taken) && this.open(next) && reached.add(next)) {
					queue.add(next);
				}
			}
		}

		if (this.bedSide.stream().noneMatch(reached::contains)) {
			return false;
		}

		// And to every ladder.
		for (BlockPos ladder : this.ladders) {
			boolean way = false;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				way |= reached.contains(ladder.relative(side));
			}

			if (!way) {
				return false;
			}
		}

		// Every chest, station and bed (and the new one) still has free floor beside it to use it from.
		List<BlockPos> used = this.room.stream().filter(p -> p.equals(taken) || !this.open(p)).toList();
		for (BlockPos thing : used) {
			if (this.level.getBlockState(thing).getBlock() instanceof BedBlock) {
				continue;
			}

			boolean reachable = false;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				reachable |= reached.contains(thing.relative(side));
			}

			if (!reachable) {
				return false;
			}
		}

		return true;
	}

	/** Whether this spot is in the room. */
	public boolean contains(BlockPos pos) {
		return this.room.contains(pos);
	}
}
