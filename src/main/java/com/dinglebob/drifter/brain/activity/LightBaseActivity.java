package com.dinglebob.drifter.brain.activity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Lights up the base (section 10.15: "Lights its base inside and out so nothing spawns at light 0"; section 11 tier 5):
 * torches on the ground round home where there's no light of their own, a few per visit. Tidy Drifters put them on a
 * neat grid; others wherever it's dark.
 */
public class LightBaseActivity extends Activity {
	/** How far round home it lights. */
	public static final int RADIUS = 14;
	/** Grid spacing for tidy Drifters (a torch's light reaches about 6 blocks before mobs can spawn). */
	private static final int GRID = 6;
	/** Block light under which mobs can spawn at night (1.18+: block light 0; it lights anything under 1 well clear). */
	private static final int DARK = 1;
	/** Torches it keeps for itself (caves, nights). */
	public static final int KEEP_TORCHES = 16;
	private static final int PER_VISIT = 6;

	private final Set<BlockPos> tried = new HashSet<>();
	private final java.util.Map<BlockPos, Integer> walks = new java.util.HashMap<>();
	private int placed;
	@Nullable
	private BlockPos placing;

	public LightBaseActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "light up the base";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 90;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		return false;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof PlaceBlockTask && status == Status.SUCCESS) {
			this.placed++;
		}
	}

	@Override
	protected Status next() {
		Home home = this.drifter.getDrifterBrain().home();
		if (home == null || this.placed >= PER_VISIT || this.drifter.getInventory().count(stack -> stack.is(Items.TORCH)) <= KEEP_TORCHES) {
			return Status.SUCCESS;
		}

		BlockPos spot = darkSpot(this.drifter, home, this.tried);
		if (spot == null) {
			return Status.SUCCESS;
		}

		if (this.drifter.getEyePosition().distanceTo(Vec3.atBottomCenterOf(spot)) > this.drifter.blockInteractionRange() - 0.5) {
			if (this.walks.merge(spot, 1, Integer::sum) > 2) {
				this.tried.add(spot);
				return Status.RUNNING;
			}

			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 2.5, "walk over to a dark spot"));
		}

		// One try per spot (a torch there lights it, so it isn't picked again).
		this.tried.add(spot);
		return this.begin(new PlaceBlockTask(this.drifter, spot, stack -> stack.is(Items.TORCH), "a torch"));
	}

	/**
	 * The nearest open floor spot round home, outside its buildings, with no block light: on a grid for tidy Drifters,
	 * anywhere for the rest. Null if it's all lit.
	 */
	@Nullable
	public static BlockPos darkSpot(DrifterEntity drifter, Home home, Set<BlockPos> skip) {
		Level level = drifter.level();
		boolean tidy = drifter.getPersonality().get(Trait.TIDINESS) >= 0.6;
		List<BlockPos> candidates = new ArrayList<>();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (tidy && (Math.floorMod(dx, GRID) != 0 || Math.floorMod(dz, GRID) != 0) || !tidy && (dx + dz) % 2 != 0) {
					continue;
				}

				for (int dy = -3; dy <= 3; dy++) {
					BlockPos spot = home.center.offset(dx, dy, dz);
					if (skip.contains(spot)) {
						continue;
					}

					if (KnownBlocks.isStandable(level, spot) && drifter.getSenses().terrain().isKnown(spot.below()) && level.canSeeSky(spot)
						&& level.getBrightness(LightLayer.BLOCK, spot) < DARK && !home.inOwnBuilding(spot, 1) && !onFarmOrPen(home, spot)
						&& level.getFluidState(spot.below()).isEmpty()) {
						candidates.add(spot.immutable());
						break;
					}
				}
			}
		}

		BlockPos feet = drifter.blockPosition();
		return candidates.stream().min(java.util.Comparator.comparingDouble(spot -> spot.distSqr(feet))).orElse(null);
	}

	private static boolean onFarmOrPen(Home home, BlockPos spot) {
		if (home.farm != null && Math.abs(spot.getX() - home.farm.getX()) <= FarmActivity.RADIUS
			&& Math.abs(spot.getZ() - home.farm.getZ()) <= FarmActivity.RADIUS) {
			return true;
		}

		return home.pen != null && Math.abs(spot.getX() - home.pen.getX()) <= 3 && Math.abs(spot.getZ() - home.pen.getZ()) <= 3;
	}
}
