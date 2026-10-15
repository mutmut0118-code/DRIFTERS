package com.dinglebob.drifter.brain.activity;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.PortalFrame;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.UseOnBlockTask;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.hands.Tools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Builds and lights a Nether portal near home (DESIGN.md section 19 "Building the portal"; milestone 11 step 1), the
 * way players do without corner blocks: the two bottom obsidian go into the ground (dug out first), so each side column
 * stands on natural ground; a cobblestone prop on each top corner gives the top obsidian something to go against; then
 * flint and steel on the inside of the bottom row. 10 obsidian. Every block goes against a face it can see and reach.
 */
public class PortalActivity extends Activity {
	/** How far from its home's middle the portal may go (clear of the hut, close enough to be "near the base"). */
	private static final int MIN_FROM_HOME = 6;
	private static final int MAX_FROM_HOME = 16;
	public static final int OBSIDIAN = 10;
	private static final int MAX_WALKS = 8;

	/** A step of the build: a cell, and what goes there (obsidian, or a cobblestone prop). */
	private record Step(BlockPos pos, boolean obsidian) {
	}

	@Nullable
	private PortalFrame frame;
	private int walks;

	public PortalActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "build a Nether portal";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 240;
	}

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		Home home = this.drifter.getDrifterBrain().home();
		if (home == null) {
			return this.fail("no home to build a portal at");
		}

		// Already standing (from before this activity): nothing to do.
		if (this.frame == null && home.portal != null && home.portal.lit(level)) {
			return Status.SUCCESS;
		}

		if (this.frame == null) {
			this.frame = home.portal != null ? home.portal : site(this.drifter, home);
			if (this.frame == null) {
				return this.fail("no clear flat spot for a portal near home");
			}

			// Kept with its home from the first block, so a half-built frame is picked up again later.
			home.portal = this.frame;
			this.think("building a Nether portal at " + this.frame.origin().toShortString());
		}

		PortalFrame f = this.frame;
		int ground = f.origin().getY();
		for (Step step : steps(f)) {
			BlockState there = level.getBlockState(step.pos());
			if (step.obsidian() ? there.is(Blocks.OBSIDIAN) : !there.canBeReplaced()) {
				continue;
			}

			// The top of each column and above: its eyes must be over the face it clicks, so it pillars up two blocks in front
			// first, as a player gets up a little to finish a portal.
			if (step.pos().getY() >= ground + 3 && this.drifter.getBlockY() < ground + 3) {
				return this.climb(f, ground);
			}

			if (!this.inReach(step.pos())) {
				return this.drifter.getBlockY() > ground + 1 ? this.fail("can't reach the top of the portal from its pillar") : this.walkTo(f);
			}

			// The two bottom cells are ground first: dug out, then filled with obsidian.
			if (!there.canBeReplaced()) {
				return this.begin(this.dig(step.pos()));
			}

			if (step.obsidian() && this.drifter.getInventory().count(stack -> stack.is(Items.OBSIDIAN)) == 0) {
				return this.fail("ran out of obsidian for the portal");
			}

			if (!step.obsidian() && this.drifter.getInventory().count(Tools.BUILDING_BLOCK) == 0) {
				return this.fail("no block to prop the portal's top with");
			}

			return this.begin(new PlaceBlockTask(this.drifter, step.pos(), step.obsidian() ? stack -> stack.is(Items.OBSIDIAN) : Tools.BUILDING_BLOCK,
				step.obsidian() ? "obsidian" : "a block to hold the portal's top"));
		}

		// The frame is whole: light it from the inside of the bottom row.
		if (!f.lit(level)) {
			if (this.drifter.getInventory().count(stack -> stack.is(Items.FLINT_AND_STEEL)) == 0) {
				return this.fail("no flint and steel to light the portal");
			}

			BlockPos floor = f.cell(1, 0);
			if (!this.inReach(floor)) {
				return this.walkTo(f);
			}

			if (!this.drifter.getMainHandItem().is(Items.FLINT_AND_STEEL)) {
				return this.begin(new SelectItemTask(this.drifter, stack -> stack.is(Items.FLINT_AND_STEEL), "flint and steel"));
			}

			if (this.failures() > 3) {
				return this.fail("the portal won't light");
			}

			return this.begin(new UseOnBlockTask(this.drifter, floor, Direction.UP, "light the portal"));
		}

		// Down off its pillar, a block at a time.
		BlockPos feet = this.drifter.blockPosition();
		if (feet.getY() > ground + 1 && this.drifter.onGround() && this.drifter.isScaffold(feet.below())) {
			return this.begin(this.dig(feet.below()));
		}

		this.think("lit the Nether portal at " + f.origin().toShortString());
		this.drifter.getMemory().remember(com.dinglebob.drifter.memory.MemoryKind.STATION, "nether_portal", Vec3.atCenterOf(f.inside()),
			level.dimension().location(), level.getGameTime(), com.dinglebob.drifter.memory.MemorySource.SEEN, 1.0F, null);
		return Status.SUCCESS;
	}

	/** A cell a block goes into, empty: near enough to place against a neighbour (the placing finds a face it can see). */
	private static final double PLACE_REACH = 4.0;

	/** A block there: it can see and reach a face of it. An empty cell (to fill): near enough to place into. */
	private boolean inReach(BlockPos pos) {
		BlockState state = this.drifter.level().getBlockState(pos);
		if (!state.canBeReplaced()) {
			return Reach.canReach(this.drifter, pos);
		}

		return this.drifter.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= PLACE_REACH;
	}

	/** To the front of the frame, in the middle, where every cell is in reach. */
	private Status walkTo(PortalFrame f) {
		if (this.walks++ >= MAX_WALKS) {
			return this.fail("can't get to the portal spot");
		}

		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot(f)), 0.4, "walk to the portal spot"));
	}

	/** Where it stands to build: two blocks in front of the middle (or behind, if the front is no good). */
	private BlockPos spot(PortalFrame f) {
		return KnownBlocks.isStandable(this.drifter.level(), f.front(1, 1, 2)) ? f.front(1, 1, 2) : f.front(2, 1, -2);
	}

	/** On its spot in front, pillars up to two blocks above the ground. */
	private Status climb(PortalFrame f, int ground) {
		BlockPos spot = this.spot(f);
		BlockPos feet = this.drifter.blockPosition();
		if (feet.getX() != spot.getX() || feet.getZ() != spot.getZ()) {
			return this.walkTo(f);
		}

		if (this.drifter.getInventory().count(Tools.BUILDING_BLOCK) < ground + 3 - feet.getY() + 2) {
			return this.fail("not enough blocks to get up to the top of the portal");
		}

		return this.begin(new com.dinglebob.drifter.executor.task.PillarUpTask(this.drifter, ground + 3 - feet.getY()));
	}

	/**
	 * In building order: dig and fill the bottom, the lower two blocks of both columns (from the ground), then from its
	 * pillar the top of both columns, the props and the top row.
	 */
	private static List<Step> steps(PortalFrame f) {
		List<Step> steps = new ArrayList<>();
		steps.add(new Step(f.cell(1, 0), true));
		steps.add(new Step(f.cell(2, 0), true));
		for (int along : new int[] {0, 3}) {
			steps.add(new Step(f.cell(along, 1), true));
			steps.add(new Step(f.cell(along, 2), true));
		}

		steps.add(new Step(f.cell(0, 3), true));
		steps.add(new Step(f.cell(3, 3), true));

		steps.add(new Step(f.cell(0, 4), false));
		steps.add(new Step(f.cell(3, 4), false));
		steps.add(new Step(f.cell(1, 4), true));
		steps.add(new Step(f.cell(2, 4), true));
		return steps;
	}

	/**
	 * A spot for the frame near home that it has seen: four ground blocks in a row (solid, dry), the 4 by 4 above them
	 * clear, and standable ground in front to build from. The nearest such spot at least {@link #MIN_FROM_HOME} away.
	 */
	@Nullable
	public static PortalFrame site(DrifterEntity drifter, Home home) {
		Level level = drifter.level();
		var terrain = drifter.getSenses().terrain();
		PortalFrame best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos column : BlockPos.betweenClosed(home.center.offset(-MAX_FROM_HOME, -4, -MAX_FROM_HOME), home.center.offset(MAX_FROM_HOME, 4, MAX_FROM_HOME))) {
			int dx = column.getX() - home.center.getX();
			int dz = column.getZ() - home.center.getZ();
			if (Math.max(Math.abs(dx), Math.abs(dz)) < MIN_FROM_HOME) {
				continue;
			}

			for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
				PortalFrame frame = new PortalFrame(column.immutable(), axis);
				if (fits(level, terrain, frame, home)) {
					double distance = column.distSqr(home.center);
					if (distance < bestDistance) {
						bestDistance = distance;
						best = frame;
					}
				}
			}
		}

		return best;
	}

	private static boolean fits(Level level, com.dinglebob.drifter.perception.TerrainKnowledge terrain, PortalFrame f, Home home) {
		for (int along = 0; along <= 3; along++) {
			BlockPos ground = f.cell(along, 0);
			BlockState state = level.getBlockState(ground);
			if (!terrain.isKnown(ground) || !state.isSolidRender(level, ground) || !level.getFluidState(ground).isEmpty()
				|| state.hasBlockEntity() || home.inOwnBuilding(ground, 1) || !level.canSeeSky(ground.above())) {
				return false;
			}

			for (int up = 1; up <= 4; up++) {
				BlockPos cell = f.cell(along, up);
				if (!terrain.isKnown(cell) || !level.getBlockState(cell).canBeReplaced() || !level.getFluidState(cell).isEmpty()) {
					return false;
				}
			}
		}

		// Somewhere to stand in front or behind, by the middle.
		return KnownBlocks.isStandable(level, f.front(1, 1, 2)) || KnownBlocks.isStandable(level, f.front(2, 1, -2));
	}
}
