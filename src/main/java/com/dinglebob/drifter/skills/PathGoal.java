package com.dinglebob.drifter.skills;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.executor.task.UseOnBlockTask;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Wearing a path in (milestone 22 groundwork, roads): where it walks day after day near home (to the farm, the mine, the
 * woods, a friend's), it turns the grass into a path with its shovel, a stretch at a time, as players do. Its routes keep
 * to paths it has seen ({@code PathSearch.ROAD_DISCOUNT}), so a path once begun is walked, and worn, the more.
 */
public final class PathGoal extends Goals.Goal {
	/** Walked this many times lately (the count halves each day): worn enough for a path. */
	private static final int WORN = 6;
	/** How far from home it makes paths. */
	private static final double RANGE = 48.0;
	/** Fewer worn blocks than this: not worth getting the shovel out. */
	private static final int AT_LEAST = 4;
	/** Blocks of path in one go. */
	private static final int PER_GO = 16;
	/** Between goes (half a day). */
	private static final long REST = 12000L;
	private static final long LOOK_EVERY = 20L * 30;
	/** Tries to get at one block before it leaves it. */
	private static final int TRIES = 2;

	private long restUntil = Long.MIN_VALUE / 2;
	private long lookedAt = Long.MIN_VALUE / 2;
	private List<BlockPos> worn = List.of();

	@Override
	public String id() {
		return "path";
	}

	@Override
	public String name(Goals.Context context) {
		return "wear a path in near home";
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		Home home = drifter.getDrifterBrain().home();
		long now = drifter.level().getGameTime();
		if (context.threat().urgent() || drifter.inCombat() || com.dinglebob.drifter.world.Battle.inBattle(drifter) || Schedule.isNight(drifter)
			|| drifter.level().dimension() != Level.OVERWORLD || home == null || home.borrowed() || !home.near(drifter, RANGE) || now < this.restUntil
			|| drifter.getInventory().count(stack -> stack.is(ItemTags.SHOVELS)) == 0) {
			return 0.0;
		}

		if (now - this.lookedAt >= LOOK_EVERY) {
			this.lookedAt = now;
			this.worn = worn(drifter, home);
		}

		if (this.worn.size() < AT_LEAST) {
			return 0.0;
		}

		Archetype archetype = drifter.getPersonality().archetype();
		boolean homely = archetype == Archetype.BUILDER || archetype == Archetype.HOMEBODY;
		return 0.14 + (homely ? 0.1 : 0.0) + 0.08 * Math.min(1.0, this.worn.size() / (double)PER_GO);
	}

	/** The ground of the blocks it has walked most near home that a shovel makes a path of, nearest it first. */
	private static List<BlockPos> worn(DrifterEntity drifter, Home home) {
		Level level = drifter.level();
		List<BlockPos> found = new ArrayList<>();
		drifter.getMemory().forEachWorn(WORN, key -> {
			BlockPos feet = BlockPos.of(key);
			BlockPos ground = feet.below();
			if (found.size() >= 64 || !ground.closerThan(home.center, RANGE) || !level.isLoaded(ground) || !pathable(level.getBlockState(ground))
				|| !level.getBlockState(feet).isAir() || home.inOwnBuilding(ground, 1) || onFarm(home, ground)) {
				return;
			}

			found.add(ground);
		});
		found.sort(Comparator.comparingDouble(pos -> pos.distSqr(drifter.blockPosition())));
		return found;
	}

	/** What a shovel flattens into a path (ShovelItem's list). */
	private static boolean pathable(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
			|| state.is(Blocks.ROOTED_DIRT);
	}

	/** On its farm (or right by it): left as it is. */
	private static boolean onFarm(Home home, BlockPos pos) {
		return home.farm != null && Math.abs(pos.getX() - home.farm.getX()) <= home.farmRadius + 1 && Math.abs(pos.getZ() - home.farm.getZ()) <= home.farmRadius + 1
			&& Math.abs(pos.getY() - home.farm.getY()) <= 2;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		long now = drifter.level().getGameTime();
		// (If it comes to nothing, not again straight away.)
		this.restUntil = now + REST / 4;
		Home home = drifter.getDrifterBrain().home();
		// (Started without a look first: /drifter brain goal path.)
		List<BlockPos> todo = new ArrayList<>(this.worn.isEmpty() && home != null ? worn(drifter, home) : this.worn);
		return new Activity(drifter) {
			private final Map<BlockPos, Integer> tries = new HashMap<>();
			private BlockPos doing;
			private int made;

			@Override
			public String describe() {
				return "wear a path in near home";
			}

			@Override
			protected Status next() {
				Level level = this.drifter.level();
				if (this.doing != null) {
					if (level.getBlockState(this.doing).is(Blocks.DIRT_PATH)) {
						this.made++;
						this.drifter.getMemory().forgetWear(this.doing.above());
					}

					this.doing = null;
				}

				todo.removeIf(pos -> !pathable(level.getBlockState(pos)) || !level.getBlockState(pos.above()).isAir());
				if (this.made >= PER_GO || todo.isEmpty()) {
					PathGoal.this.restUntil = this.now() + REST;
					if (this.made > 0) {
						this.think("wore a path in near home (" + this.made + " blocks)");
					}

					return Status.SUCCESS;
				}

				BlockPos next = todo.stream().min(Comparator.comparingDouble(pos -> pos.distSqr(this.drifter.blockPosition()))).orElseThrow();
				if (Reach.aimPointFrom(this.drifter, this.drifter.getEyePosition(), next, Direction.UP).isPresent()) {
					todo.remove(next);
					this.doing = next;
					return this.begin(new SequenceTask(this.drifter, "make a path", List.of(
						new SelectItemTask(this.drifter, stack -> stack.is(ItemTags.SHOVELS), "a shovel"),
						new UseOnBlockTask(this.drifter, next, Direction.UP, "make a path"))));
				}

				if (this.tries.merge(next, 1, Integer::sum) > TRIES) {
					todo.remove(next);
					return Status.RUNNING;
				}

				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(next.above()), 2.0, "walk along to the next bit of path",
					NavigateTask.Pace.STROLL));
			}
		};
	}
}
