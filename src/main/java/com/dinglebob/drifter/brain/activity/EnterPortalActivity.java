package com.dinglebob.drifter.brain.activity;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.WaitTask;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Goes through a Nether portal like a player (DESIGN.md section 19): walks up to it, steps in, and stands still while
 * the portal does its work (a player's wait; see NetherPortalBlockMixin). The game then builds the Drifter afresh on
 * the other side, so this activity simply ends with the old one; what happens next is in its saved trip.
 */
public class EnterPortalActivity extends Activity {
	/** Longer than a player's wait in the portal (4 s) with room for a slow step in. */
	private static final int WAIT_INSIDE_TICKS = 20 * 8;
	private static final int MAX_WALKS = 4;

	private final BlockPos inside;
	private final String why;
	private int walks;

	public EnterPortalActivity(DrifterEntity drifter, BlockPos inside, String why) {
		super(drifter);
		this.inside = inside.immutable();
		this.why = why;
	}

	@Override
	public String describe() {
		return "go through the portal (" + this.why + ")";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 120;
	}

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		if (!level.getBlockState(this.inside).is(Blocks.NETHER_PORTAL)) {
			// Put out (a ghast's fireball does that; section 19 packs flint and steel for it): lit again from the frame's
			// bottom, if the frame's still there.
			if (this.relights++ < 3 && level.getBlockState(this.inside).isAir() && level.getBlockState(this.inside.below()).is(Blocks.OBSIDIAN)
				&& this.drifter.getInventory().count(stack -> stack.is(net.minecraft.world.item.Items.FLINT_AND_STEEL)) > 0) {
				return this.relight(level);
			}

			return this.fail("the portal at " + this.inside.toShortString() + " isn't lit");
		}

		// In the portal: in the middle of the block (the portal itself is a thin sheet through the middle; standing at the
		// edge of its block, it isn't touching it: found in testing, it waited by the sheet forever), then still, and wait.
		BlockPos feet = this.drifter.blockPosition();
		if (level.getBlockState(feet).is(Blocks.NETHER_PORTAL)) {
			// Just came through and never stepped out: a portal won't take you back until you have (it kept standing in the
			// one it arrived by, walling it in, and waited there for nothing). Out a step, then back in.
			BlockPos out = this.pad(level);
			if (this.drifter.isOnPortalCooldown() && out != null && out.distManhattan(feet) <= 2) {
				return this.begin(new com.dinglebob.drifter.executor.task.CenterOnBlockTask(this.drifter, Vec3.atBottomCenterOf(out)) {
					@Override
					public String describe() {
						return "step out of the portal";
					}
				});
			}

			Vec3 middle = Vec3.atBottomCenterOf(feet);
			if (Math.abs(this.drifter.getX() - middle.x) > 0.15 || Math.abs(this.drifter.getZ() - middle.z) > 0.15) {
				return this.begin(new com.dinglebob.drifter.executor.task.CenterOnBlockTask(this.drifter, middle));
			}

			com.dinglebob.drifter.motor.Steering.stopMoving(this.drifter);
			return this.begin(new WaitTask(this.drifter, "wait in the portal", () -> false, WAIT_INSIDE_TICKS));
		}

		BlockPos pad = this.pad(level);
		if (pad == null) {
			return this.fail("nowhere to stand by the portal");
		}

		// Next to it: one step in (the pathfinder never routes through portals, so this last step is by hand).
		if (this.drifter.blockPosition().equals(pad) && this.drifter.onGround()) {
			// Just stepped out of it: a moment clear of the portal first, or stepping back in starts the wait over.
			if (this.drifter.isOnPortalCooldown()) {
				return this.begin(new WaitTask(this.drifter, "wait a moment out of the portal", () -> !this.drifter.isOnPortalCooldown(), 20 * 2));
			}

			return this.begin(new com.dinglebob.drifter.executor.task.CenterOnBlockTask(this.drifter, Vec3.atBottomCenterOf(this.portalCellNextTo(pad))) {
				@Override
				public String describe() {
					return "step into the portal";
				}
			});
		}

		if (this.walks++ >= MAX_WALKS) {
			return this.fail("can't get to the portal");
		}

		// A step away (same level): straight onto it, no route to plan (found in testing: the spot in front of its portal
		// was marked to be kept off for lava that had flowed there, the planner wouldn't step on it, and it stood one block
		// short until the lava came back for it).
		BlockPos at = this.drifter.blockPosition();
		if (at.getY() == pad.getY() && Math.abs(at.getX() - pad.getX()) + Math.abs(at.getZ() - pad.getZ()) <= 2 && this.drifter.onGround()) {
			return this.begin(new com.dinglebob.drifter.executor.task.CenterOnBlockTask(this.drifter, Vec3.atBottomCenterOf(pad)));
		}

		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(pad), 0.4, "walk to the portal"));
	}

	private int relights;

	private Status relight(Level level) {
		if (!com.dinglebob.drifter.hands.Reach.canReach(this.drifter, this.inside.below())) {
			if (this.walks++ >= MAX_WALKS) {
				return this.fail("can't get to the portal to light it again");
			}

			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.inside), 2.5, "walk to the portal"));
		}

		if (!this.drifter.getMainHandItem().is(net.minecraft.world.item.Items.FLINT_AND_STEEL)) {
			return this.begin(new com.dinglebob.drifter.executor.task.SelectItemTask(this.drifter,
				stack -> stack.is(net.minecraft.world.item.Items.FLINT_AND_STEEL), "flint and steel"));
		}

		this.think("the portal's gone out: lighting it again");
		return this.begin(new com.dinglebob.drifter.executor.task.UseOnBlockTask(this.drifter, this.inside.below(), Direction.UP, "light the portal again"));
	}

	/** The bottom portal cell beside this pad. */
	private BlockPos portalCellNextTo(BlockPos pad) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos cell = pad.relative(side);
			if (this.drifter.level().getBlockState(cell).is(Blocks.NETHER_PORTAL)) {
				return cell;
			}
		}

		return this.inside;
	}

	/** A standable cell right in front of or behind the portal's bottom row, nearest to it. */
	private BlockPos pad(Level level) {
		BlockState portal = level.getBlockState(this.inside);
		Direction.Axis axis = portal.hasProperty(NetherPortalBlock.AXIS) ? portal.getValue(NetherPortalBlock.AXIS) : Direction.Axis.X;
		// The bottom of this column of portal.
		BlockPos bottom = this.inside;
		while (level.getBlockState(bottom.below()).is(Blocks.NETHER_PORTAL)) {
			bottom = bottom.below();
		}

		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos cell = bottom; level.getBlockState(cell).is(Blocks.NETHER_PORTAL); cell = cell.relative(axis, 1)) {
			for (Direction out : axis == Direction.Axis.X ? new Direction[] {Direction.NORTH, Direction.SOUTH} : new Direction[] {Direction.WEST, Direction.EAST}) {
				BlockPos pad = cell.relative(out);
				if (KnownBlocks.isStandable(level, pad) && level.getFluidState(pad).isEmpty()) {
					double distance = pad.distSqr(this.drifter.blockPosition());
					if (distance < bestDistance) {
						bestDistance = distance;
						best = pad.immutable();
					}
				}
			}
		}

		return best;
	}
}
