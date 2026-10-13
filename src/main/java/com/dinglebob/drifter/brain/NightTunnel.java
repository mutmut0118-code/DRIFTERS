package com.dinglebob.drifter.brain;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The tunnel a night miner digs out from the bottom of its shelter (section 18: "time doesn't matter underground";
 * section 10.3 strip mining). A main leg, then side legs at right angles, 1 wide and 2 high. Kept by the brain for the
 * night so a break (eating, a fight) picks the tunnel up where it left off.
 * TODO(milestone 8): tunnel shape from the habit (2x2, 3x3) and torches on its favorite side.
 */
public final class NightTunnel {
	/** Most steps it digs in one night (it waits out the rest). */
	public static final int MAX_STEPS = 48;
	public static final int MAIN_LEG_MIN = 10;
	public static final int MAIN_LEG_MAX = 18;
	public static final int SIDE_LEG_MIN = 5;
	public static final int SIDE_LEG_MAX = 9;
	public static final int MAX_LEGS = 4;
	/** A torch every this many steps, if it has torches (section 10.3 torch placement). */
	public static final int TORCH_EVERY = 8;

	public final BlockPos shaftBottom;
	/** Every cell it has opened (shaft, tunnel, ores dug out of the walls): air here is its own doing, not a cave. */
	public final Set<BlockPos> opened = new HashSet<>();
	/** Where the tunnel ends now (feet level): it digs on from here. */
	public BlockPos tip;
	public Direction direction;
	public int legSteps;
	public int legLength;
	public int legs;
	public int steps;
	public int sinceTorch;
	/** Finished for tonight (blocked, breached, or dug enough): it waits for morning. */
	public boolean done;
	public int breaches;

	public NightTunnel(BlockPos shaftBottom, Direction direction, int legLength) {
		this.shaftBottom = shaftBottom.immutable();
		this.direction = direction;
		this.legLength = legLength;
		this.tip = this.shaftBottom;
		this.opened.add(this.shaftBottom);
		this.opened.add(this.shaftBottom.above());
	}

	public boolean contains(BlockPos feet) {
		return this.opened.contains(feet);
	}
}
