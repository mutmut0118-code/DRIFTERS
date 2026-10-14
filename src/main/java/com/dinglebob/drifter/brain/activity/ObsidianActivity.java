package com.dinglebob.drifter.brain.activity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.executor.task.UseItemTask;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Obsidian the way players make it (section 9 stage 8, milestone 8 step 7), with two buckets: scoop up lava, pour it
 * into a one-block hole dug in solid floor (it can't run anywhere from there), pour water beside it so it flows over
 * the lava and turns it to obsidian, scoop the water back up, and mine the obsidian with a diamond pickaxe (the drop
 * lands safely in the hole). The hole is reused for the next block.
 *
 * <p>It never pours water straight onto a lava pool: the obsidian that makes sits on more lava, and its drop would burn.
 */
public class ObsidianActivity extends Activity {
	/** How far it looks for lava it remembers. */
	private static final double LAVA_RANGE = 96.0;
	/** The hole stays this far from any lava, so the water it pours doesn't run into a pool. */
	private static final int MOLD_LAVA_CLEARANCE = 3;
	/** Where it looks for a spot for the hole, round where it stands. */
	private static final int MOLD_SEARCH = 6;

	private final int total;
	/** Where it stands to cast, the hole in the floor in front of it, and the block it pours water on beyond the hole. */
	@Nullable
	private BlockPos stand;
	@Nullable
	private BlockPos mold;
	@Nullable
	private BlockPos waterOn;
	private final Set<BlockPos> badLava = new HashSet<>();
	private final Set<BlockPos> badWater = new HashSet<>();
	@Nullable
	private BlockPos waterTarget;
	@Nullable
	private BlockPos lavaTarget;
	private int casts;
	/** Looks for obsidian left lying (an interrupted visit). */
	private int leftovers;
	private static final double LEFTOVER_RANGE = 10.0;
	private int waits;
	private int walks;
	/** Waits of half a second for the water to reach the lava. */
	private static final int MAX_WAITS = 6;
	/** Walks to get into position before it gives up on a spot. */
	private static final int MAX_WALKS = 4;
	/** Walks toward something it wants to reach (water, the obsidian) before it gives up. */
	private static final int MAX_APPROACHES = 8;
	private int approaches;

	public ObsidianActivity(DrifterEntity drifter, int total) {
		super(drifter);
		this.total = total;
	}

	@Override
	public String describe() {
		return "make obsidian (" + this.have() + "/" + this.total + ")";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 8;
	}

	private int have() {
		return this.drifter.getInventory().count(stack -> stack.is(Items.OBSIDIAN));
	}

	private int count(net.minecraft.world.item.Item item) {
		return this.drifter.getInventory().count(stack -> stack.is(item));
	}

	@Override
	protected void stepDone(Task step, Status status) {
		// Water it couldn't scoop from where it stood (something in the way): another source next time.
		if (status == Status.FAILURE && step.describe().equals("fill the bucket") && this.waterTarget != null) {
			this.badWater.add(this.waterTarget);
		}

		if (status == Status.FAILURE) {
			this.think("couldn't " + step.describe() + ": " + step.failureReason());
		}
	}

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		if (this.have() >= this.total) {
			return Status.SUCCESS;
		}

		if (!this.canMineObsidian()) {
			return this.fail("needs a diamond pickaxe to mine obsidian");
		}

		// Obsidian it mined but didn't pick up (it was called away): that first.
		if (this.leftovers++ < 2 && !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
			this.drifter.getBoundingBox().inflate(LEFTOVER_RANGE), item -> item.getItem().is(Items.OBSIDIAN)).isEmpty()) {
			return this.begin(new CollectItemsTask(this.drifter, LEFTOVER_RANGE));
		}

		// Water poured and the lava still lava: the water takes a moment to run over it (5 ticks a block).
		if (this.mold != null && level.getFluidState(this.mold).is(FluidTags.LAVA) && this.waterOn != null
			&& level.getFluidState(this.waterOn.above()).is(FluidTags.WATER) && this.waits++ < MAX_WAITS) {
			BlockPos hole = this.mold;
			return this.begin(new com.dinglebob.drifter.executor.task.WaitTask(this.drifter, "wait for the obsidian",
				() -> !this.drifter.level().getFluidState(hole).is(FluidTags.LAVA), 40));
		}

		// The water it poured: back into the bucket first (it needs it for the next block, and it leaves no mess).
		if (this.waterOn != null && isWaterSource(level, this.waterOn.above()) && this.count(Items.BUCKET) > 0) {
			this.waits = 0;
			return this.reachThen(this.waterOn.above(), () -> new SequenceTask(this.drifter, "scoop the water back up", List.of(
				new SelectItemTask(this.drifter, stack -> stack.is(Items.BUCKET), "a bucket"),
				new LookAtTask(this.drifter, Vec3.atCenterOf(this.waterOn.above()).add(0.0, 0.3, 0.0), 4),
				new UseItemTask(this.drifter))));
		}

		// A block of obsidian in the hole: mine it, and pick it up from the bottom of the hole.
		if (this.mold != null && level.getBlockState(this.mold).is(Blocks.OBSIDIAN)) {
			BlockPos block = this.mold;
			this.think("the lava turned to obsidian: mining it");
			return this.reachThen(block, () -> new SequenceTask(this.drifter, "mine the obsidian", List.of(this.dig(block),
				new CollectItemsTask(this.drifter, 3.0))));
		}

		int buckets = this.count(Items.BUCKET) + this.count(Items.WATER_BUCKET) + this.count(Items.LAVA_BUCKET);
		if (buckets < 2) {
			return this.fail("needs two buckets (one for lava, one for water)");
		}

		if (this.count(Items.WATER_BUCKET) == 0) {
			return this.fillWater(level);
		}

		if (this.count(Items.LAVA_BUCKET) == 0) {
			return this.scoopLava(level);
		}

		return this.cast(level);
	}

	private boolean canMineObsidian() {
		for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = this.drifter.getInventory().getItem(slot);
			if (stack.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())) {
				return true;
			}
		}

		return false;
	}

	private Status fillWater(Level level) {
		BlockPos source = FarmActivity.nearestWater(this.drifter, 64, this.badWater);
		this.waterTarget = source;
		if (source == null) {
			return this.fail("knows no water to fill a bucket at");
		}

		this.think("filling a bucket with water for the obsidian");
		return this.reachThen(source, () -> new SequenceTask(this.drifter, "fill the bucket", List.of(
			new SelectItemTask(this.drifter, stack -> stack.is(Items.BUCKET), "a bucket"),
			new LookAtTask(this.drifter, Vec3.atCenterOf(source).add(0.0, 0.4, 0.0), 4),
			new UseItemTask(this.drifter))));
	}

	private Status scoopLava(Level level) {
		BlockPos lava = this.lavaTarget != null && isLavaSource(level, this.lavaTarget) ? this.lavaTarget : this.rememberedLava(level);
		this.lavaTarget = lava;
		if (lava == null) {
			return this.fail("knows no lava to scoop up");
		}

		// Close enough to reach it, but never right beside it (lava next to it sets off its "block it off" reflex).
		if (!this.nearButNotBeside(lava)) {
			BlockPos spot = this.spotNearLava(level, lava);
			if (spot == null) {
				this.badLava.add(lava);
				this.lavaTarget = null;
				return Status.RUNNING;
			}

			if (++this.walks > MAX_WALKS) {
				this.walks = 0;
				this.badLava.add(lava);
				this.lavaTarget = null;
				return Status.RUNNING;
			}

			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.4, "walk over to the lava"));
		}

		this.think("scooping up lava");
		this.walks = 0;
		this.lavaTarget = null;
		return this.begin(new SequenceTask(this.drifter, "scoop up lava", List.of(
			new SelectItemTask(this.drifter, stack -> stack.is(Items.BUCKET), "a bucket"),
			new LookAtTask(this.drifter, Vec3.atCenterOf(lava).add(0.0, 0.4, 0.0), 4),
			new UseItemTask(this.drifter))));
	}

	/** Lava and water in its buckets: dig the hole, pour the lava in, then the water beside it. */
	private Status cast(Level level) {
		if (this.mold == null || !this.moldStillGood(level)) {
			this.mold = null;
			if (!this.chooseMold(level)) {
				return this.fail("can't find solid ground away from the lava to make obsidian on");
			}
		}

		if (!this.drifter.blockPosition().equals(this.stand)) {
			if (++this.walks > MAX_WALKS) {
				this.walks = 0;
				this.mold = null;
				return this.fail("can't get to the spot for making obsidian");
			}

			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.stand), 0.3, "go to where it makes the obsidian"));
		}

		this.walks = 0;
		BlockPos hole = this.mold;
		if (!level.getBlockState(hole).isAir() && level.getFluidState(hole).isEmpty()) {
			return this.begin(this.dig(hole));
		}

		if (!level.getBlockState(hole).isAir()) {
			// Lava or water left in the hole from a try that didn't take: a fresh hole elsewhere.
			this.mold = null;
			return Status.RUNNING;
		}

		if (++this.casts > this.total * 3) {
			return this.fail("the obsidian won't form");
		}

		this.think("pouring lava into the hole, then water beside it");
		return this.begin(new SequenceTask(this.drifter, "make a block of obsidian", List.of(
			new SelectItemTask(this.drifter, stack -> stack.is(Items.LAVA_BUCKET), "a lava bucket"),
			new LookAtTask(this.drifter, Vec3.atBottomCenterOf(hole).add(0.0, 0.05, 0.0), 4),
			new UseItemTask(this.drifter),
			new SelectItemTask(this.drifter, stack -> stack.is(Items.WATER_BUCKET), "a water bucket"),
			new LookAtTask(this.drifter, Vec3.atBottomCenterOf(this.waterOn.above()).add(0.0, 0.05, 0.0), 4),
			new UseItemTask(this.drifter))));
	}

	/** Still a fine hole (or block to dig it in) with solid ground round it. */
	private boolean moldStillGood(Level level) {
		BlockPos hole = this.mold;
		if (!level.getBlockState(hole).isAir() && !this.diggableFloor(level, hole)) {
			return false;
		}

		return this.walledIn(level, hole) && solid(level, this.waterOn) && level.getBlockState(this.waterOn.above()).isAir()
			&& KnownBlocks.isStandable(level, this.stand);
	}

	/**
	 * A spot near where it stands: it stands on S, the hole is the floor block in front of it, and beyond the hole is a
	 * floor block to pour water on. All solid ground round the hole, and well away from any lava.
	 */
	private boolean chooseMold(Level level) {
		BlockPos feet = this.drifter.blockPosition();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-MOLD_SEARCH, -2, -MOLD_SEARCH), feet.offset(MOLD_SEARCH, 2, MOLD_SEARCH))) {
			if (!KnownBlocks.isStandable(level, pos) || !this.drifter.getSenses().terrain().isKnown(pos.below())) {
				continue;
			}

			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos hole = pos.relative(side).below();
				BlockPos beyond = hole.relative(side);
				if (this.diggableFloor(level, hole) && level.getBlockState(hole.above()).isAir() && level.getBlockState(hole.above(2)).isAir()
					&& this.walledIn(level, hole) && solid(level, beyond) && level.getBlockState(beyond.above()).isAir()
					&& !lavaNear(level, hole, MOLD_LAVA_CLEARANCE) && pos.distSqr(feet) < bestDistance) {
					bestDistance = pos.distSqr(feet);
					best = pos.immutable();
					this.stand = best;
					this.mold = hole.immutable();
					this.waterOn = beyond.immutable();
				}
			}
		}

		if (best != null) {
			this.think("making obsidian in a hole at " + this.mold.toShortString());
		}

		return best != null;
	}

	/** Plain natural ground it can dig a hole in. */
	private boolean diggableFloor(Level level, BlockPos pos) {
		var state = level.getBlockState(pos);
		return solid(level, pos) && level.getBlockEntity(pos) == null && !(state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock)
			&& com.dinglebob.drifter.perception.BlockNotes.isNaturalGround(state) && !state.is(net.minecraft.tags.BlockTags.ICE);
	}

	/** Solid on every side but the top: lava poured in stays put. */
	private boolean walledIn(Level level, BlockPos hole) {
		if (!solid(level, hole.below())) {
			return false;
		}

		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (!solid(level, hole.relative(side))) {
				return false;
			}
		}

		return true;
	}

	private static boolean solid(Level level, BlockPos pos) {
		return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos) && level.getFluidState(pos).isEmpty();
	}

	private static boolean lavaNear(Level level, BlockPos pos, int radius) {
		for (BlockPos near : BlockPos.betweenClosed(pos.offset(-radius, -1, -radius), pos.offset(radius, 2, radius))) {
			if (level.getFluidState(near).is(FluidTags.LAVA)) {
				return true;
			}
		}

		return false;
	}

	private static boolean isLavaSource(Level level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.LAVA) && level.getFluidState(pos).isSource();
	}

	private static boolean isWaterSource(Level level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos).isSource();
	}

	/** The nearest lava source it remembers seeing, with open air above it to scoop it from, or null. */
	@Nullable
	private BlockPos rememberedLava(Level level) {
		var dimension = level.dimension().location();
		MemoryEntry entry = this.drifter.getMemory().nearest(MemoryKind.DANGER, dimension, this.drifter.position(), LAVA_RANGE,
			e -> e.label.equals("lava") && !this.badLava.contains(e.blockPos()) && isLavaSource(level, e.blockPos())
				&& level.getBlockState(e.blockPos().above()).isAir());
		return entry == null ? null : entry.blockPos();
	}

	/**
	 * A liquid's surface in reach with a clear line to it (Reach#canReach aims at a block's shape, and liquids have
	 * none).
	 */
	private boolean canScoop(BlockPos pos) {
		Vec3 eye = this.drifter.getEyePosition();
		Vec3 surface = Vec3.atCenterOf(pos).add(0.0, 0.4, 0.0);
		if (eye.distanceTo(surface) > this.drifter.blockInteractionRange() - 0.3) {
			return false;
		}

		var hit = this.drifter.level().clip(new net.minecraft.world.level.ClipContext(eye, surface, net.minecraft.world.level.ClipContext.Block.OUTLINE,
			net.minecraft.world.level.ClipContext.Fluid.NONE, this.drifter));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}

	/** In reach of the lava, with no lava right beside its body. */
	private boolean nearButNotBeside(BlockPos lava) {
		if (!this.canScoop(lava)) {
			return false;
		}

		return !besideLava(this.drifter.level(), this.drifter.blockPosition()) && this.drifter.onGround();
	}

	private static boolean besideLava(Level level, BlockPos feet) {
		for (BlockPos body : List.of(feet, feet.above())) {
			for (Direction direction : Direction.values()) {
				if (level.getFluidState(body.relative(direction)).is(FluidTags.LAVA)) {
					return true;
				}
			}
		}

		return false;
	}

	/** A known floor spot in reach of the lava but not beside any, nearest to it first. */
	@Nullable
	private BlockPos spotNearLava(Level level, BlockPos lava) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(lava.offset(-4, -2, -4), lava.offset(4, 3, 4))) {
			double eyeDistance = Vec3.atBottomCenterOf(pos).add(0.0, this.drifter.getEyeHeight(), 0.0).distanceTo(Vec3.atCenterOf(lava));
			if (eyeDistance > this.drifter.blockInteractionRange() - 0.8 || pos.distManhattan(lava) < 2 || besideLava(level, pos)
				|| !KnownBlocks.isStandable(level, pos) || !this.drifter.getSenses().terrain().isKnown(pos.below())) {
				continue;
			}

			// Prefer the closer end of the reach (a clear line to the lava), and not under lava.
			double score = eyeDistance + (lavaNear(level, pos.above(2), 0) ? 100.0 : 0.0);
			if (score < bestDistance) {
				bestDistance = score;
				best = pos.immutable();
			}
		}

		return best;
	}

	/** In reach: the step. Otherwise walk to where it can reach it. */
	private Status reachThen(BlockPos pos, java.util.function.Supplier<Task> step) {
		boolean liquid = !this.drifter.level().getFluidState(pos).isEmpty();
		if (liquid ? this.canScoop(pos) : Reach.canReach(this.drifter, pos)) {
			this.approaches = 0;
			return this.begin(step.get());
		}

		if (++this.approaches > MAX_APPROACHES) {
			return this.fail("can't get to " + pos.toShortString());
		}

		BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, pos, Set.of());
		if (spot == null) {
			// It doesn't know the ground round it yet: over there first, then it'll see where to stand (as for the farm's water).
			return this.begin(new NavigateTask(this.drifter, Vec3.atCenterOf(pos), 3.0, "walk over"));
		}

		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk over"));
	}
}
