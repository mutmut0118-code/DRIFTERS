package com.dinglebob.drifter.brain.activity;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.Storage;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Going home to put things away (DESIGN.md section 10.18, "Going home: when full, when carrying something precious"):
 * walks to its chests and puts in everything that isn't its working kit ({@link Storage}). A full chest means the next
 * one; all full, it gives up and the settle goal makes another chest.
 */
public class StoreActivity extends Activity {
	private final Set<BlockPos> tried = new HashSet<>();
	@Nullable
	private BlockPos chest;
	private boolean walked;
	private boolean stored;

	public StoreActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "put things away at home";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 3;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof ContainerTask) {
			if (status == Status.SUCCESS) {
				this.stored = true;
			}

			// Done with this chest either way (full, or it took everything): the next pass picks another if needed.
			this.tried.add(this.chest);
			this.chest = null;
			this.walked = false;
		}
	}

	@Override
	protected Status next() {
		Home home = this.drifter.getDrifterBrain().home();
		if (home == null || !home.hasChest()) {
			return this.fail("has no chest at home");
		}

		home.forgetMissingChests(this.drifter);
		Map<Integer, Integer> deposit = Storage.toDeposit(this.drifter);
		if (deposit.isEmpty()) {
			if (this.stored) {
				this.think("put things away at home");
			}

			return Status.SUCCESS;
		}

		if (this.chest == null) {
			this.chest = home.chests().stream().filter(pos -> !this.tried.contains(pos)).findFirst().orElse(null);
			if (this.chest == null) {
				this.drifter.getDrifterBrain().noteStorageFull();
				return this.stored ? Status.SUCCESS : this.fail("its chests are full");
			}
		}

		if (!Reach.canReach(this.drifter, this.chest)) {
			BlockPos spot = this.walked ? null : KnownBlocks.standingSpotFor(this.drifter, this.chest, Set.of());
			if (spot == null) {
				this.tried.add(this.chest);
				this.chest = null;
				return Status.RUNNING;
			}

			this.walked = true;
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk to the chest"));
		}

		Map<ItemStack, Integer> amounts = new IdentityHashMap<>();
		deposit.forEach((slot, count) -> amounts.put(this.drifter.getInventory().getItem(slot), count));
		return this.begin(ContainerTask.deposit(this.drifter, this.chest, amounts, "what it doesn't need on it"));
	}
}
