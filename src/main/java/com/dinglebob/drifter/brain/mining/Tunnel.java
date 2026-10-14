package com.dinglebob.drifter.brain.mining;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A tunnel it is digging (DESIGN.md section 10.3): a queue of legs, each a straight run from a start point, either a
 * 2-high staircase down to a target height or a level 1x2 tunnel. A branch mine is a main leg plus side legs every few
 * blocks (its branch spacing habit), both ways. Used by the night miner (from the bottom of its shelter) and by mining
 * trips (a staircase from home down to ore depth, then branches).
 * TODO(milestone 8 later steps): tunnel shape from the habit (2x2, 3x3).
 */
public final class Tunnel {
	/** A level leg has no target height. */
	public static final int LEVEL = Integer.MIN_VALUE;

	/** One straight run. {@code targetY}: stairs down until the feet get there, or {@link #LEVEL}. */
	public record Leg(BlockPos start, Direction direction, int length, int targetY) {
		public boolean stairs() {
			return this.targetY != LEVEL;
		}
	}

	/** Where it went in (the bottom of the night shaft, the top of the staircase). */
	public final BlockPos entrance;
	/** Every cell it has opened: air there is its own doing, not a cave. */
	public final Set<BlockPos> opened = new HashSet<>();
	public final Deque<Leg> legs = new ArrayDeque<>();
	public final int maxSteps;
	@Nullable
	public Leg leg;
	/** Where it stands to dig on (feet level). */
	public BlockPos tip;
	public Direction direction;
	public int legSteps;
	public int steps;
	public int sinceTorch;
	public int turns;
	public int breaches;
	/** Finished: blocked, breached too often, or dug enough. */
	public boolean done;

	public Tunnel(BlockPos entrance, Direction direction, int maxSteps) {
		this.entrance = entrance.immutable();
		this.tip = this.entrance;
		this.direction = direction;
		this.maxSteps = maxSteps;
		this.opened.add(this.entrance);
		this.opened.add(this.entrance.above());
	}

	public boolean contains(BlockPos feet) {
		return this.opened.contains(feet);
	}

	/** Queues a branch mine from here: the main leg, then side legs every {@code spacing} blocks, both ways. */
	public void branchMine(BlockPos start, Direction direction, int mainLength, int spacing, int branchLength) {
		this.legs.add(new Leg(start, direction, mainLength, LEVEL));
		for (int along = spacing; along <= mainLength; along += spacing) {
			BlockPos from = start.relative(direction, along);
			this.legs.add(new Leg(from, direction.getClockWise(), branchLength, LEVEL));
			this.legs.add(new Leg(from, direction.getCounterClockWise(), branchLength, LEVEL));
		}
	}
}
