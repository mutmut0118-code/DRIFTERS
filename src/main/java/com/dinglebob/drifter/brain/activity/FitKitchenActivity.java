package com.dinglebob.drifter.brain.activity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.building.Furnishing;
import com.dinglebob.drifter.building.plan.HousePlan;
import com.dinglebob.drifter.building.plan.HousePlans;
import com.dinglebob.drifter.building.plan.PlacedPlan;
import com.dinglebob.drifter.compat.FarmersDelight;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Putting the kitchen together (Farmer's Delight, optional): each kitchen thing it carries goes in its place in the house's
 * layout ({@link Furnishing}), put down from in front so it faces the room: the stove first and then the pot on it, the
 * cabinet and then the board on it. A cabinet joins its home's storage; the rest it remembers as stations.
 */
public class FitKitchenActivity extends Activity {
	/** Pieces it puts in on one go. */
	private static final int MOST = 8;

	private final Set<BlockPos> failed = new HashSet<>();
	@Nullable
	private BlockPos placing;
	private int placed;
	private int walks;
	private boolean finished;

	public FitKitchenActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "fit out its kitchen";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 120;
	}

	public boolean finished() {
		return this.finished;
	}

	/** A kitchen thing it carries and the free place for it in its house, or null. */
	public record Piece(Furnishing.Spot spot, Block block) {
	}

	@Nullable
	public static Piece nextPiece(DrifterEntity drifter) {
		return nextPiece(drifter, Set.of());
	}

	@Nullable
	private static Piece nextPiece(DrifterEntity drifter, Set<BlockPos> skip) {
		Home home = drifter.getDrifterBrain().home();
		if (!FarmersDelight.loaded() || home == null || home.planId == null || home.planOrigin == null || !home.planDone || drifter.getServer() == null) {
			return null;
		}

		HousePlan plan = HousePlans.get(drifter.getServer(), home.planId);
		if (plan == null) {
			return null;
		}

		PlacedPlan placed = PlacedPlan.of(plan, home);
		// The layout's places in order (stove before the pot on it): the first it has the thing for.
		for (Furnishing.Spot spot : Furnishing.plan(drifter.level(), placed, home.planBed)) {
			if (!spot.use().kitchen() || skip.contains(spot.pos()) || !drifter.level().getBlockState(spot.pos()).canBeReplaced()) {
				continue;
			}

			for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
				ItemStack stack = drifter.getInventory().getItem(slot);
				if (stack.getItem() instanceof BlockItem item && spot.use().takes(item.getBlock()) && Furnishing.baseIn(drifter.level(), spot)) {
					return new Piece(spot, item.getBlock());
				}
			}
		}

		return null;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (this.placing == null || !(step instanceof PlaceBlockTask)) {
			return;
		}

		BlockPos pos = this.placing;
		this.placing = null;
		if (status != Status.SUCCESS) {
			this.failed.add(pos);
			return;
		}

		this.placed++;
		this.drifter.getSenses().placedBlock(pos);
		Home home = this.drifter.getDrifterBrain().home();
		if (home != null && FarmersDelight.isCabinet(this.drifter.level().getBlockState(pos))) {
			home.addChest(pos);
		}

		com.dinglebob.drifter.brain.Kitchen.rethink(this.drifter);
	}

	@Override
	protected Status next() {
		Piece piece = this.placed < MOST ? nextPiece(this.drifter, this.failed) : null;
		if (piece == null) {
			this.finished = true;
			return this.placed > 0 || this.failed.isEmpty() ? Status.SUCCESS : this.fail("couldn't put its kitchen things down");
		}

		Furnishing.Spot spot = piece.spot();
		// Where it stands to put it down: on the floor in front of it (in front of the stove for the pot on top).
		BlockPos front = (spot.upper() || spot.use().base() != null ? spot.pos().below() : spot.pos()).relative(spot.facing());
		if (!this.drifter.blockPosition().equals(front)) {
			if (this.walks++ > MOST * 2) {
				return this.fail("can't get to its kitchen");
			}

			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(front), 0.4, "walk over to the kitchen"));
		}

		String label = piece.block().getName().getString().toLowerCase(java.util.Locale.ROOT);
		this.placing = spot.pos();
		this.think("putting the " + label + " in the kitchen");
		Block block = piece.block();
		return this.begin(new PlaceBlockTask(this.drifter, spot.pos(), stack -> stack.getItem() instanceof BlockItem item && item.getBlock() == block, label));
	}

	/** For reports. */
	List<BlockPos> failedSpots() {
		return List.copyOf(this.failed);
	}
}
