package com.dinglebob.drifter.brain.activity;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Gathering blocks it has seen (DESIGN.md sections 10.1 and 10.3): walks to the nearest one, mines what it can reach,
 * lowest first (a tree from the bottom log up, like a player), picks up the drops, and repeats until it has enough.
 * For stone it can also dig a 2-high staircase down from where it stands when it hasn't seen any (players dig down
 * through the dirt to reach stone).
 */
public class GatherActivity extends Activity {
	public static final int SEARCH_RADIUS = 24;
	private static final int MAX_CANDIDATES = 48;
	private static final double DROP_PICKUP_RADIUS = 6.0;
	/** Only blocks this close are checked for reach (reach is 4.5), to keep the raycasts per tick small. */
	private static final double REACH_CHECK_DISTANCE = 6.0;
	private static final int MAX_STAIR_STEPS = 16;
	private static final int MAX_SPOT_SEARCHES = 12;

	private final String label;
	private final Predicate<BlockState> blocks;
	private final Predicate<ItemStack> items;
	private final int wanted;
	private final boolean digDown;
	/** Trees are taken from the bottom up; other blocks at its own level or above, so it doesn't dig pits. */
	private final boolean lowestFirst;
	private int turnsInARow;
	/** The block it last walked toward; if it still can't reach it after arriving, it's blacklisted. */
	@Nullable
	private BlockPos walkedTo;
	@Nullable
	private Direction stairDirection;
	private int stairSteps;
	private boolean collectNext;
	private final Set<BlockPos> badSpots = new HashSet<>();

	public GatherActivity(DrifterEntity drifter, String label, Predicate<BlockState> blocks, Predicate<ItemStack> items, int wanted, boolean digDown,
		boolean lowestFirst) {
		super(drifter);
		this.lowestFirst = lowestFirst;
		this.label = label;
		this.blocks = blocks;
		this.items = items;
		this.wanted = wanted;
		this.digDown = digDown;
	}

	@Override
	public String describe() {
		return "gather " + this.label + " (" + this.have() + "/" + this.wanted + ")";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 4;
	}

	private int have() {
		return this.drifter.getInventory().count(this.items);
	}

	/** Most extra vein blocks it takes after it has what it came for. */
	private static final int MAX_VEIN_EXTRA = 12;
	private int veinExtra;

	/** An ore block of the kind it came for that it can reach from here, if it's mining ore. */
	@Nullable
	private BlockPos restOfVein() {
		if (++this.veinExtra > MAX_VEIN_EXTRA) {
			return null;
		}

		Level level = this.drifter.level();
		Vec3 eye = this.drifter.getEyePosition();
		return KnownBlocks.find(this.drifter, 6, this.blocks, 16).stream()
			.filter(pos -> com.dinglebob.drifter.brain.mining.TunnelDigger.isOre(level.getBlockState(pos)) && KnownBlocks.mayTake(level, pos)
				&& pos.distToCenterSqr(eye) <= REACH_CHECK_DISTANCE * REACH_CHECK_DISTANCE && Reach.canReach(this.drifter, pos))
			.findFirst().orElse(null);
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (status == Status.FAILURE && step instanceof NavigateTask && this.walkedTo != null) {
			this.blacklist().add(this.walkedTo, this.now());
			this.walkedTo = null;
		}
	}

	@Override
	protected Status next() {
		if (this.collectNext) {
			this.collectNext = false;
			return this.begin(new CollectItemsTask(this.drifter, DROP_PICKUP_RADIUS));
		}

		if (this.have() >= this.wanted) {
			// Ore: the rest of the vein right here too, like a player (found by the user: half-mined veins left behind).
			BlockPos rest = this.restOfVein();
			if (rest != null) {
				this.collectNext = true;
				return this.begin(this.dig(rest));
			}

			return Status.SUCCESS;
		}

		Level level = this.drifter.level();
		BlockPos feet = this.drifter.blockPosition();
		BlockPos below = feet.below();
		// Not the block it stands on, nor (when digging down) the next stair it will stand on.
		BlockPos nextStair = this.stairDirection == null ? null : feet.relative(this.stairDirection).below();
		com.dinglebob.drifter.brain.TreeLogs trees = new com.dinglebob.drifter.brain.TreeLogs(level);
		List<BlockPos> candidates = KnownBlocks.find(this.drifter, SEARCH_RADIUS, this.blocks, MAX_CANDIDATES * 4).stream()
			.filter(pos -> !pos.equals(below) && !pos.equals(nextStair) && (this.lowestFirst || !underfoot(pos, feet))
				&& !this.blacklist().contains(pos, this.now()) && KnownBlocks.mayTake(level, pos) && exposed(level, pos)
				&& !(com.dinglebob.drifter.brain.mining.TunnelDigger.isOre(level.getBlockState(pos)) && KnownBlocks.underWater(level, pos))
				&& (!level.getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS) || trees.check(pos)))
			.limit(MAX_CANDIDATES)
			.toList();

		if (this.walkedTo != null) {
			BlockPos arrivedAt = this.walkedTo;
			this.walkedTo = null;
			if (!Reach.canReach(this.drifter, arrivedAt)) {
				this.blacklist().add(arrivedAt, this.now());
			}
		}

		Vec3 eye = this.drifter.getEyePosition();
		Optional<BlockPos> inReach = candidates.stream()
			.filter(pos -> !this.blacklist().contains(pos, this.now()) && pos.distToCenterSqr(eye) <= REACH_CHECK_DISTANCE * REACH_CHECK_DISTANCE)
			.filter(pos -> Reach.canReach(this.drifter, pos))
			.min((a, b) -> this.lowestFirst && a.getY() != b.getY() ? Integer.compare(a.getY(), b.getY())
				: Double.compare(a.distToCenterSqr(this.drifter.position()), b.distToCenterSqr(this.drifter.position())));
		if (inReach.isPresent()) {
			this.turnsInARow = 0;
			this.collectNext = true;
			return this.begin(this.dig(inReach.get()));
		}

		int searched = 0;
		for (BlockPos target : candidates) {
			if (this.blacklist().contains(target, this.now())) {
				continue;
			}

			if (++searched > MAX_SPOT_SEARCHES) {
				break;
			}

			BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, target, this.badSpots);
			if (spot != null) {
				this.walkedTo = target;
				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk to the " + this.label));
			}

			this.blacklist().add(target, this.now());
		}

		if (this.digDown) {
			return this.stairStep();
		}

		return this.fail("doesn't know where to find any " + this.label);
	}

	/** Below its feet, in its own column or right beside it: digging there just makes a pit to fall into. */
	private static boolean underfoot(BlockPos pos, BlockPos feet) {
		return pos.getY() < feet.getY() && Math.abs(pos.getX() - feet.getX()) <= 1 && Math.abs(pos.getZ() - feet.getZ()) <= 1;
	}

	/** Turns the staircase a quarter turn; boxed in on all four sides, it gives up. */
	private Status turn() {
		this.stairDirection = this.stairDirection.getClockWise();
		if (++this.turnsInARow >= 4) {
			return this.fail("can't dig down here (water, lava, sand or someone's blocks all round)");
		}

		return Status.RUNNING;
	}

	/** Water or lava right next to a block it would dig: digging it would let the fluid in. */
	private static boolean fluidBeside(Level level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			if (!level.getFluidState(pos.relative(direction)).isEmpty()) {
				return true;
			}
		}

		return false;
	}

	/** A block with at least one open side: a player can actually see and hit it. */
	private static boolean exposed(Level level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockPos side = pos.relative(direction);
			if (level.getBlockState(side).getCollisionShape(level, side).isEmpty()) {
				return true;
			}
		}

		return false;
	}

	/**
	 * One step of a 2-high staircase down: clear the head, feet and step blocks in front, then step down. Stops at
	 * caves, fluids and falling blocks, which a careful player doesn't dig into blind.
	 */
	private Status stairStep() {
		if (this.stairSteps >= MAX_STAIR_STEPS) {
			return this.fail("dug " + MAX_STAIR_STEPS + " steps down and found no " + this.label);
		}

		if (this.stairDirection == null) {
			this.stairDirection = this.drifter.getDirection();
			this.think("digging down to find " + this.label);
		}

		Level level = this.drifter.level();
		BlockPos feet = this.drifter.blockPosition();
		BlockPos front = feet.relative(this.stairDirection);
		BlockPos step = front.below();
		for (BlockPos cell : List.of(front.above(), front, step)) {
			if (!level.getFluidState(cell).isEmpty() || fluidBeside(level, cell)) {
				return this.turn();
			}

			BlockState state = level.getBlockState(cell);
			if (!state.getCollisionShape(level, cell).isEmpty()) {
				if (!KnownBlocks.mayTake(level, cell) || state.getDestroySpeed(level, cell) < 0.0F
					|| level.getBlockState(cell.above()).getBlock() instanceof FallingBlock && cell.equals(front.above())) {
					// Something it won't dig (or sand that would pour in on its head): try the next direction.
					return this.turn();
				}

				this.turnsInARow = 0;
				this.collectNext = true;
				return this.begin(this.dig(cell));
			}
		}

		BlockPos floor = step.below();
		if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty() || !level.getFluidState(floor).isEmpty()) {
			return this.fail("digging down opened into a cave or a drop");
		}

		this.turnsInARow = 0;
		this.stairSteps++;
		return this.begin(new SequenceTask(this.drifter, "step down the staircase", List.of(
			new NavigateTask(this.drifter, Vec3.atBottomCenterOf(step), 0.4, "step down"))));
	}
}
