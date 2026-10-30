package com.dinglebob.drifter.brain.activity;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Stuck up on its own pillars and bridges with no blocks left to build a way down (found by the user: rubydoodle pillared
 * and bridged until it stood on a "reverse spider" of cobblestone and dirt, too high to drop): it digs down through a
 * pillar of its own, a block at a time, as a player does. On a bridge with air under it, it walks to the nearest part
 * that has blocks underneath first. Only as a last resort a bigger drop, and only one that can't kill it.
 */
public class GetDownActivity extends Activity {
	/** How high above ground counts as stuck up there. */
	public static final int STUCK_HEIGHT = 4;
	private static final int SEARCH = 8;

	private int walks;

	public GetDownActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "get down off its pillars";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 120;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		return false;
	}

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		BlockPos feet = this.drifter.blockPosition();
		if (!stuckUp(this.drifter)) {
			// Low enough to drop, but still on its own pillar: the rest of it comes down too, a block at a time (found in a
			// town test: it dug until the drop was safe and jumped, and three blocks of cobblestone stood by the trees for good).
			if (this.drifter.onGround() && this.drifter.isScaffold(feet.below()) && solid(level, feet.below(2))) {
				return this.begin(this.dig(feet.below()));
			}

			this.think("back down");
			return Status.SUCCESS;
		}

		if (!this.drifter.onGround()) {
			return Status.RUNNING;
		}

		if (this.walks == 0 && this.ticksRunning() < 2) {
			this.think("stuck up here with no blocks: digging down through its pillar");
		}

		// A pillar under it: the block it stands on goes, it drops one.
		if (solid(level, feet.below()) && solid(level, feet.below(2))) {
			return this.begin(this.dig(feet.below()));
		}

		// On a bridge: to the nearest spot of its structure with a pillar under it.
		BlockPos pillar = this.pillarSpot(level, feet);
		if (pillar != null && this.walks++ < 4) {
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(pillar), 0.4, "walk to a pillar to dig down"));
		}

		// Nothing better: a drop it can survive (as a player would, at worst a heart or two).
		int drop = dropBelow(level, feet);
		int damage = com.dinglebob.drifter.nav.PathSearch.fallDamage(this.drifter, drop);
		if (solid(level, feet.below()) && damage <= this.drifter.getHealth() - 6.0F) {
			this.think("no pillar to dig down: taking the drop");
			return this.begin(this.dig(feet.below()));
		}

		return this.fail("can't get down from here");
	}

	/** The nearest standable spot within reach on the same structure that has two solid blocks under it. */
	@Nullable
	private BlockPos pillarSpot(Level level, BlockPos feet) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-SEARCH, -3, -SEARCH), feet.offset(SEARCH, 1, SEARCH))) {
			if (com.dinglebob.drifter.brain.KnownBlocks.isStandable(level, pos) && solid(level, pos.below()) && solid(level, pos.below(2))
				&& this.drifter.getSenses().terrain().isKnown(pos.below())) {
				double distance = pos.distSqr(feet);
				if (distance > 0 && distance < bestDistance) {
					bestDistance = distance;
					best = pos.immutable();
				}
			}
		}

		return best;
	}

	/** Stuck up: standing on a block it put down, with real ground at least {@link #STUCK_HEIGHT} below, and no blocks. */
	public static boolean stuckUp(DrifterEntity drifter) {
		BlockPos feet = drifter.blockPosition();
		return drifter.onGround() && drifter.isScaffold(feet.below())
			&& com.dinglebob.drifter.hands.Tools.buildingBlockCount(drifter) <= com.dinglebob.drifter.nav.PathSearch.reserve(drifter)
			&& heightAboveGround(drifter, feet) >= STUCK_HEIGHT;
	}

	/** Blocks from its feet down to ground that isn't its own scaffold (pillar blocks don't count), up to 64. */
	static int heightAboveGround(DrifterEntity drifter, BlockPos feet) {
		Level level = drifter.level();
		for (int down = 1; down <= 64; down++) {
			BlockPos below = feet.below(down);
			if (solid(level, below) && !drifter.isScaffold(below)) {
				return down - 1;
			}
		}

		return 64;
	}

	/** How far it falls if the block under its feet goes: down to the next solid block. */
	static int dropBelow(Level level, BlockPos feet) {
		for (int down = 2; down <= 64; down++) {
			if (solid(level, feet.below(down))) {
				return down - 1;
			}
		}

		return 64;
	}

	private static boolean solid(Level level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}
}
