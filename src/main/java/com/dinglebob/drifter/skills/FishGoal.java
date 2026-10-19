package com.dinglebob.drifter.skills;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.brain.activity.CraftActivity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.FishTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.perception.TerrainKnowledge;
import com.dinglebob.drifter.personality.Mood;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Going fishing (DESIGN.md section 10.5): at dusk, in the rain, or bored, with water it knows close by, it sits on the
 * bank with a rod (the patient more often); and when its food runs short and there's water about, for the fish. It makes
 * a rod first if it carries the sticks and string.
 */
public final class FishGoal extends Goals.Goal {
	/** How far it goes to fish. */
	private static final int WATER_RANGE = 32;
	/** Open water: at least this many water surface blocks round the spot it casts to (a pond, not a puddle). */
	private static final int OPEN_WATER = 9;
	private static final long SPOT_KEEP = 20L * 10;

	@Nullable
	private Spot spot;
	private long spotAt = Long.MIN_VALUE / 2;

	/** Where it stands and where it casts. */
	private record Spot(BlockPos stand, Vec3 water) {
	}

	@Override
	public String id() {
		return "fish";
	}

	@Override
	public String name(Goals.Context context) {
		return "go fishing";
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		if (context.threat().urgent() || drifter.inCombat() || com.dinglebob.drifter.world.Battle.inBattle(drifter)
			|| drifter.level().dimension() != Level.OVERWORLD || Schedule.isNight(drifter) && !drifter.level().isRaining()
			|| !hasRod(drifter) && !canMakeRod(drifter)) {
			return 0.0;
		}

		long now = drifter.level().getGameTime();
		if (now - this.spotAt > SPOT_KEEP) {
			this.spotAt = now;
			this.spot = findSpot(drifter);
		}

		if (this.spot == null) {
			return 0.0;
		}

		var personality = drifter.getPersonality();
		long time = Schedule.believedTime(drifter) % 24000L;
		boolean dusk = time >= 11000L && time < 13000L;
		double urgency = 0.12 + 0.12 * personality.get(Trait.PATIENCE) + 0.18 * Math.min(1.0, personality.mood().pressure(Mood.BORED));
		if (dusk || drifter.level().isRaining()) {
			urgency += 0.15;
		}

		int food = drifter.getInventory().count(stack -> stack.has(DataComponents.FOOD) && !stack.is(Items.ROTTEN_FLESH) && !stack.is(Items.SPIDER_EYE));
		if (food < 4) {
			urgency += 0.2;
		}

		return Math.min(0.55, urgency);
	}

	private static boolean hasRod(DrifterEntity drifter) {
		return drifter.getInventory().count(stack -> stack.is(Items.FISHING_ROD)) > 0;
	}

	private static boolean canMakeRod(DrifterEntity drifter) {
		return drifter.getInventory().count(stack -> stack.is(Items.STICK)) >= 3 && drifter.getInventory().count(stack -> stack.is(Items.STRING)) >= 2;
	}

	/** Open water it knows near here, and a spot of dry bank a few blocks from it, nearest it. */
	@Nullable
	private static Spot findSpot(DrifterEntity drifter) {
		Level level = drifter.level();
		BlockPos center = drifter.blockPosition();
		BlockPos[] best = {null};
		Vec3[] water = {null};
		double[] bestDistance = {Double.MAX_VALUE};
		drifter.getSenses().terrain().forEachKnownState(level, center, WATER_RANGE,
			TerrainKnowledge.SectionFilter.palette(level, state -> state.getFluidState().is(FluidTags.WATER)),
			state -> !state.getFluidState().is(FluidTags.WATER) || !state.getFluidState().isSource(),
			(pos, state) -> {
				// Only every few blocks (it's looking for a pond, not every cell of one).
				if (Math.floorMod(pos.getX(), 3) != 0 || Math.floorMod(pos.getZ(), 3) != 0 || !level.getBlockState(pos.above()).isAir()) {
					return;
				}

				double distance = pos.distSqr(center);
				if (distance >= bestDistance[0] || !openWater(level, pos)) {
					return;
				}

				BlockPos stand = bank(drifter, level, pos);
				if (stand != null) {
					bestDistance[0] = distance;
					best[0] = stand;
					water[0] = Vec3.atBottomCenterOf(pos.above());
				}
			});
		return best[0] == null ? null : new Spot(best[0], water[0]);
	}

	/** Water at the surface all round (a 5x5 at this height has enough). */
	private static boolean openWater(Level level, BlockPos pos) {
		int count = 0;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos at = pos.offset(dx, 0, dz);
				if (level.getFluidState(at).is(FluidTags.WATER) && level.getBlockState(at.above()).isAir()) {
					count++;
				}
			}
		}

		return count >= OPEN_WATER;
	}

	/** Dry ground to stand on 2 to 4 blocks from the water spot, level with it or a little above. */
	@Nullable
	private static BlockPos bank(DrifterEntity drifter, Level level, BlockPos water) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			for (int out = 2; out <= 4; out++) {
				for (int dy = 1; dy <= 3; dy++) {
					BlockPos stand = water.relative(side, out).above(dy);
					if (KnownBlocks.isStandable(level, stand) && level.getFluidState(stand.below()).isEmpty() && level.getFluidState(stand).isEmpty()
						&& drifter.getSenses().terrain().isKnown(stand.below())) {
						double distance = stand.distSqr(drifter.blockPosition());
						if (distance < bestDistance) {
							bestDistance = distance;
							best = stand.immutable();
						}
					}
				}
			}
		}

		return best;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 30;
	}

	@Nullable
	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		if (this.spot == null) {
			this.spot = findSpot(drifter);
			this.spotAt = drifter.level().getGameTime();
		}

		if (this.spot == null) {
			return null;
		}

		int wanted = 2 + Math.round(4.0F * (float)drifter.getPersonality().get(Trait.PATIENCE)) + drifter.getRandom().nextInt(3);
		return new Fishing(drifter, this.spot, wanted);
	}

	/** A rod if it has none, to the bank, then the fishing. */
	private static final class Fishing extends Activity {
		private final Spot spot;
		private final int wanted;
		private boolean madeRod;
		private boolean walked;
		private boolean fished;

		Fishing(DrifterEntity drifter, Spot spot, int wanted) {
			super(drifter);
			this.spot = spot;
			this.wanted = wanted;
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "going fishing");
		}

		@Override
		public String describe() {
			return "go fishing";
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60 * 8;
		}

		@Override
		protected void stepDone(Task step, Status status) {
			if (step instanceof FishTask fish) {
				this.fished = true;
				if (fish.caught() > 0) {
					this.think("caught " + fish.caught() + (fish.caught() == 1 ? " fish" : " things") + " fishing");
				}
			}
		}

		@Override
		protected Status next() {
			if (this.fished) {
				return Status.SUCCESS;
			}

			if (!hasRod(this.drifter)) {
				if (this.madeRod || !canMakeRod(this.drifter)) {
					return this.fail("has no fishing rod");
				}

				this.madeRod = true;
				return this.begin(new CraftActivity(this.drifter, Items.FISHING_ROD, 1));
			}

			if (!this.walked && this.drifter.blockPosition().distSqr(this.spot.stand()) > 1.0) {
				this.walked = true;
				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.spot.stand()), 0.6, "walk to the water's edge"));
			}

			return this.begin(new FishTask(this.drifter, this.spot.water(), this.wanted));
		}
	}
}
