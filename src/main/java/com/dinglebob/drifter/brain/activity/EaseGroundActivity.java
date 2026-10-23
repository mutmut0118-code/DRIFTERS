package com.dinglebob.drifter.brain.activity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.building.plan.HousePlan;
import com.dinglebob.drifter.building.plan.HousePlans;
import com.dinglebob.drifter.building.plan.PlacedPlan;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The ground round its finished house eased into the land, as a player tidies round a build on a slope (user, 2026-10-23:
 * "the foundations of the structures need to blend in with the natural terrain"; for founded towns too): the ring next to
 * the house level with its ground (its doorstep all round), the next one halfway between that and the land, so there's no
 * cliff dug straight down beside it or a step straight up. Higher ground dug down (the top block of a column at a time),
 * lower ground filled with dirt (what it dug, or what it has). Only ground it has seen and that's nobody's build; not
 * roads, fields or water. A few blocks a visit, over days; done when there's nothing left to do.
 */
public class EaseGroundActivity extends Activity {
	/** How many rings round the house it eases (the first level with it). */
	public static final int RINGS = 2;
	/** Blocks dug or filled a visit. */
	private static final int PER_VISIT = 20;

	private final Set<BlockPos> tried = new HashSet<>();
	private final Map<BlockPos, Integer> walks = new HashMap<>();
	private int done;

	public EaseGroundActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "even out the ground round its house";
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
	protected void stepDone(Task step, Status status) {
		if (status == Status.SUCCESS && !(step instanceof NavigateTask)) {
			this.done++;
		}
	}

	@Override
	protected Status next() {
		Home home = this.drifter.getDrifterBrain().home();
		if (home == null || this.done >= PER_VISIT) {
			return Status.SUCCESS;
		}

		Work work = next(this.drifter, home, this.tried, this.drifter.getInventory().count(stack -> stack.is(Items.DIRT)) > 0);
		if (work == null) {
			// Nothing it can do round it now: done (a hole it has no dirt for, the rest done, counts as done too).
			if (next(this.drifter, home, this.tried, true) == null) {
				home.groundEased = true;
				this.think("the ground round its house is even with the land now");
			}

			return Status.SUCCESS;
		}

		BlockPos pos = work.pos();
		if (!Reach.canReach(this.drifter, work.fill() ? pos.below() : pos)) {
			if (this.walks.merge(pos, 1, Integer::sum) > 2) {
				this.tried.add(pos);
				return Status.RUNNING;
			}

			BlockPos stand = KnownBlocks.standingSpotFor(this.drifter, pos, Set.of(pos));
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(stand != null ? stand : pos.above()), stand != null ? 0.5 : 2.5,
				"walk round its house"));
		}

		this.tried.add(pos);
		return this.begin(work.fill() ? new PlaceBlockTask(this.drifter, pos, stack -> stack.is(Items.DIRT), "dirt") : this.dig(pos));
	}

	/** A block to dig ({@code fill} false) or a place to fill with dirt. */
	public record Work(BlockPos pos, boolean fill) {
	}

	/** Its house's box from a plan, or null. */
	@Nullable
	private static BoundingBox house(DrifterEntity drifter, Home home) {
		if (home.planBox() == null && home.planId != null && home.planOrigin != null && drifter.getServer() != null) {
			HousePlan plan = HousePlans.get(drifter.getServer(), home.planId);
			if (plan != null) {
				home.setPlanBox(PlacedPlan.of(plan, home).box());
			}
		}

		return home.planBox();
	}

	/**
	 * The next bit of easing round its house: the highest block above where the ground should be (dug first), else the
	 * lowest hole below it (if {@code canFill}); null if there's none it knows of.
	 */
	@Nullable
	public static Work next(DrifterEntity drifter, Home home, Set<BlockPos> skip, boolean canFill) {
		BoundingBox box = house(drifter, home);
		if (box == null || !home.planDone || drifter.getServer() == null) {
			return null;
		}

		HousePlan plan = HousePlans.get(drifter.getServer(), home.planId);
		if (plan == null) {
			return null;
		}

		// Its ground: the plan's own ground layer, or the top of what it stands on.
		PlacedPlan placed = PlacedPlan.of(plan, home);
		int base = placed.ground.isEmpty() ? home.planOrigin.getY() - 1 : home.planOrigin.getY();
		Level level = drifter.level();
		Work dig = null;
		Work fill = null;
		for (int x = box.minX() - RINGS; x <= box.maxX() + RINGS; x++) {
			for (int z = box.minZ() - RINGS; z <= box.maxZ() + RINGS; z++) {
				int d = Math.max(Math.max(box.minX() - x, x - box.maxX()), Math.max(box.minZ() - z, z - box.maxZ()));
				if (d <= 0) {
					continue;
				}

				int ground = ground(level, x, z, base);
				if (ground == Integer.MIN_VALUE) {
					continue;
				}

				int target = base + Math.round((ground - base) * (float)(d - 1) / RINGS);
				if (ground > target) {
					BlockPos top = new BlockPos(x, ground, z);
					if (!skip.contains(top) && diggable(level, top) && drifter.getSenses().terrain().isKnown(top.above())
						&& (dig == null || top.getY() > dig.pos().getY())) {
						dig = new Work(top, false);
					}
				} else if (ground < target && canFill) {
					BlockPos hole = new BlockPos(x, ground + 1, z);
					if (!skip.contains(hole) && level.getBlockState(hole).canBeReplaced() && level.getFluidState(hole).isEmpty()
						&& drifter.getSenses().terrain().isKnown(hole) && (fill == null || hole.getY() < fill.pos().getY())) {
						fill = new Work(hole, true);
					}
				}
			}
		}

		return dig != null ? dig : fill;
	}

	/**
	 * The top of the natural ground in this column near the house's height (not under a build, a road, a field or water),
	 * or {@link Integer#MIN_VALUE} if it isn't ground to ease.
	 */
	private static int ground(Level level, int x, int z, int base) {
		for (int y = base + 8; y >= base - 8; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty()) {
				continue;
			}

			if (!state.getFluidState().isEmpty() || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND) || state.is(BlockTags.LOGS)
				|| state.is(BlockTags.LEAVES) || com.dinglebob.drifter.world.PlayerPlacedBlocks.isBuilt(level, pos)
				|| !(state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.SAND) || state.is(Blocks.GRAVEL))) {
				return Integer.MIN_VALUE;
			}

			return y;
		}

		return Integer.MIN_VALUE;
	}

	private static boolean diggable(Level level, BlockPos pos) {
		return KnownBlocks.mayTake(level, pos) && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
	}
}
