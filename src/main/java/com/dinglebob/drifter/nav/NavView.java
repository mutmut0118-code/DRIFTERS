package com.dinglebob.drifter.nav;

import java.util.HashMap;
import java.util.Map;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.Tools;
import com.dinglebob.drifter.perception.TerrainKnowledge;
import com.dinglebob.drifter.world.PlayerPlacedBlocks;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The pathfinder's view of terrain (DESIGN.md section 12). Blocks the Drifter has seen ({@link TerrainKnowledge})
 * are read as they really are; everything else is UNKNOWN and assumed passable at a moderate cost.
 * Hard bans: never through lava or fire, never digging player-placed blocks or containers.
 */
public final class NavView {
	/** Blocks no taller than this (carpet, snow layers) are walked on, not climbed or dug. */
	public static final double LOW_BLOCK_HEIGHT = 0.2;

	public enum Kind {
		/** Air or something you walk through (grass, flowers, torches). */
		OPEN,
		/** A thin block you walk on top of: carpet, a thin snow layer. Passable, but not something to stand above. */
		LOW,
		/** Solid: has to be dug ({@link Cell#digSeconds}, infinite if it can't be). */
		SOLID,
		WATER,
		/** Ladders, vines, scaffolding. */
		CLIMB,
		/** A wooden door or fence gate: passable after opening it. */
		DOOR,
		/** Passable but unpleasant: cobwebs, powder snow, sweet berry bushes. */
		AVOID,
		/** Never: lava, fire, cactus, unloaded chunks. */
		BANNED,
		/** Never seen: assumed passable. */
		UNKNOWN
	}

	/**
	 * @param built it plainly belongs to somebody's build (glass, planks, wool, doors, stairs...): players go around
	 *              those rather than through them
	 */
	public record Cell(Kind kind, float digSeconds, boolean fallingAbove, boolean liquidBeside, boolean built) {
		public Cell(Kind kind, float digSeconds, boolean fallingAbove, boolean liquidBeside) {
			this(kind, digSeconds, fallingAbove, liquidBeside, false);
		}

		public boolean passable() {
			return this.kind == Kind.OPEN || this.kind == Kind.LOW || this.kind == Kind.WATER || this.kind == Kind.CLIMB || this.kind == Kind.DOOR
				|| this.kind == Kind.AVOID || this.kind == Kind.UNKNOWN;
		}

		public boolean diggable() {
			return this.kind == Kind.SOLID && Float.isFinite(this.digSeconds);
		}
	}

	private final DrifterEntity drifter;
	private final Level level;
	private final TerrainKnowledge knowledge;
	private final Map<Long, Cell> cache = new HashMap<>();
	private final com.dinglebob.drifter.brain.TreeLogs trees;

	public NavView(DrifterEntity drifter) {
		this.trees = new com.dinglebob.drifter.brain.TreeLogs(drifter.level());
		this.drifter = drifter;
		this.level = drifter.level();
		this.knowledge = drifter.getSenses().terrain();
	}

	public Cell cell(BlockPos pos) {
		return this.cache.computeIfAbsent(pos.asLong(), key -> this.compute(BlockPos.of(key)));
	}

	public boolean isKnown(BlockPos pos) {
		return this.knowledge.isKnown(pos);
	}

	private Cell compute(BlockPos pos) {
		if (!this.level.isLoaded(pos) || this.level.isOutsideBuildHeight(pos)) {
			return new Cell(Kind.BANNED, Float.POSITIVE_INFINITY, false, false);
		}

		if (!this.knowledge.isKnown(pos)) {
			// Under water it has seen, it expects more water, not ground (its sight stops at the surface; found in
			// testing: it planned to walk across a pond on "unseen ground" just under the surface, and couldn't get out).
			BlockPos above = pos.above();
			if (this.knowledge.isKnown(above) && this.level.getFluidState(above).is(FluidTags.WATER)) {
				return new Cell(Kind.WATER, 0.0F, false, false);
			}

			// Beside water it has seen, at the same height: water is level, so it expects more of it (the banks it can see).
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = pos.relative(side);
				if (this.knowledge.isKnown(next) && this.level.getFluidState(next).is(FluidTags.WATER) && this.level.getFluidState(next).isSource()) {
					return new Cell(Kind.WATER, 0.0F, false, false);
				}
			}

			return new Cell(Kind.UNKNOWN, 0.0F, false, false);
		}

		BlockState state = this.level.getBlockState(pos);
		FluidState fluid = state.getFluidState();
		if (fluid.is(FluidTags.LAVA) || state.is(BlockTags.FIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK)) {
			return new Cell(Kind.BANNED, Float.POSITIVE_INFINITY, false, false);
		}

		if (state.is(BlockTags.WOODEN_DOORS) || state.getBlock() instanceof FenceGateBlock) {
			return new Cell(Kind.DOOR, 0.0F, false, false);
		}

		if (state.is(BlockTags.CLIMBABLE)) {
			return new Cell(Kind.CLIMB, 0.0F, false, false);
		}

		if (state.is(Blocks.COBWEB) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)) {
			return new Cell(Kind.AVOID, 0.0F, false, false);
		}

		VoxelShape collision = state.getCollisionShape(this.level, pos);
		if (collision.isEmpty()) {
			return new Cell(fluid.is(FluidTags.WATER) ? Kind.WATER : Kind.OPEN, 0.0F, false, false);
		}

		if (collision.max(Direction.Axis.Y) <= LOW_BLOCK_HEIGHT) {
			return new Cell(Kind.LOW, 0.0F, false, false);
		}

		boolean protectedBlock = this.level.getBlockEntity(pos) != null || PlayerPlacedBlocks.isPlayerPlaced(this.level, pos);
		float dig = protectedBlock ? Float.POSITIVE_INFINITY : Tools.digSeconds(this.drifter, state, this.level, pos);
		BlockState above = this.level.getBlockState(pos.above());
		boolean falling = above.getBlock() instanceof FallingBlock;
		boolean liquidBeside = false;
		for (Direction direction : Direction.values()) {
			if (direction != Direction.DOWN) {
				FluidState neighbor = this.level.getFluidState(pos.relative(direction));
				if (neighbor.is(FluidTags.LAVA) && this.knowledge.isKnown(pos.relative(direction))) {
					// Digging this would open into known lava: never.
					dig = Float.POSITIVE_INFINITY;
				} else if (!neighbor.isEmpty()) {
					liquidBeside = true;
				}
			}
		}

		// A log that isn't part of a tree is somebody's build (a village house post, a cabin wall).
		boolean built = looksBuilt(state) || state.is(BlockTags.LOGS) && !this.trees.check(pos);
		return new Cell(Kind.SOLID, dig, falling, liquidBeside, built);
	}

	/**
	 * Blocks a player can tell are part of a build just by looking at them. Cobblestone too: in a natural world it only
	 * turns up in villages, dungeons and temples (stone drops it, but stone itself is natural).
	 */
	public static boolean looksBuilt(BlockState state) {
		return state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.SMOOTH_STONE)
			|| state.is(BlockTags.PLANKS) || state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.STAIRS)
			|| state.is(BlockTags.SLABS) || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.WALLS)
			|| state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.BEDS) || state.is(BlockTags.STONE_BRICKS)
			|| state.is(ConventionalBlockTags.GLASS_BLOCKS) || state.is(ConventionalBlockTags.GLASS_PANES) || state.is(Blocks.BRICKS)
			|| state.is(Blocks.BOOKSHELF) || state.is(Blocks.TORCH) || state.is(Blocks.LANTERN);
	}

	/** Something it can stand on top of: a known solid block no taller than a full block, or unknown (assumed). */
	public boolean standable(BlockPos floor) {
		Cell cell = this.cell(floor);
		if (cell.kind() == Kind.UNKNOWN) {
			return true;
		}

		if (cell.kind() == Kind.LOW) {
			// Standing on carpet puts the feet in the carpet's own block, not above it.
			return false;
		}

		if (cell.kind() != Kind.SOLID) {
			return cell.kind() == Kind.CLIMB;
		}

		VoxelShape shape = this.level.getBlockState(floor).getCollisionShape(this.level, floor);
		return !shape.isEmpty() && shape.max(Direction.Axis.Y) <= 1.0;
	}

	public boolean isWater(BlockPos pos) {
		return this.cell(pos).kind() == Kind.WATER;
	}

	/** Water you can float in (a source block), not water running past. */
	public boolean isStillWater(BlockPos pos) {
		return this.isWater(pos) && this.level.getFluidState(pos).isSource();
	}
}
