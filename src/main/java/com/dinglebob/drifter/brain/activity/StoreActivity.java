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
	private boolean walkedHome;
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

	private boolean unreachable;
	private boolean sortedPass;
	private final java.util.Set<BlockPos> sortedTried = new java.util.HashSet<>();
	@org.jetbrains.annotations.Nullable
	private java.util.Set<net.minecraft.world.item.Item> onlyMatching;

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof ContainerTask) {
			if (status == Status.SUCCESS) {
				this.stored = true;
			} else if (step.failureReason() != null && !step.failureReason().contains("no room")) {
				// Couldn't get at it (out of view, blocked): that's not the same as full (found in testing: it built chest
				// after chest, or tried to, because one it couldn't see counted as full).
				this.unreachable = true;
			}

			// Done with this chest either way (full, or it took everything): the next pass picks another if needed. After a
			// like-with-like top-up it can still take leftovers later.
			if (this.onlyMatching == null || status != Status.SUCCESS) {
				this.tried.add(this.chest);
			}

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

		// Like with like (section 9 stage 6 "organized chests"): first each chest gets more of what it already holds; then
		// the rest goes wherever there's room.
		if (this.chest == null && !this.sortedPass && home.chests().size() > 1) {
			for (BlockPos pos : home.chests()) {
				if (this.sortedTried.contains(pos)) {
					continue;
				}

				java.util.Map<net.minecraft.world.item.Item, Integer> holds = home.contents(pos);
				boolean match = deposit.keySet().stream().anyMatch(slot -> holds.containsKey(this.drifter.getInventory().getItem(slot).getItem()));
				this.sortedTried.add(pos);
				if (match) {
					this.chest = pos;
					this.onlyMatching = holds.keySet();
					break;
				}
			}

			if (this.chest == null) {
				this.sortedPass = true;
			}
		}

		if (this.chest == null) {
			this.onlyMatching = null;
			this.chest = home.chests().stream().filter(pos -> !this.tried.contains(pos)).findFirst().orElse(null);
			if (this.chest == null) {
				if (this.unreachable) {
					if (!this.stored) {
						home.storeFailedAt = this.drifter.level().getGameTime();
					}

					return this.stored ? Status.SUCCESS : this.fail("couldn't get at its chest");
				}

				this.drifter.getDrifterBrain().noteStorageFull();
				return this.stored ? Status.SUCCESS : this.fail("its chests are full");
			}
		}

		if (!Reach.canReach(this.drifter, this.chest)) {
			BlockPos spot = this.walked ? null : KnownBlocks.standingSpotFor(this.drifter, this.chest, Set.of());
			// From far off (down a mine, out exploring) it can't pick the spot by the chest yet: it heads home first
			// (found by the user's log: "couldn't get at its chest" two ticks after deciding to go, again and again).
			if (spot == null && !this.walkedHome && this.chest.distSqr(this.drifter.blockPosition()) > 12 * 12) {
				this.walkedHome = true;
				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.chest), 3.0, "head home to the chest"));
			}

			if (spot == null) {
				this.tried.add(this.chest);
				this.chest = null;
				this.unreachable = true;
				return Status.RUNNING;
			}

			this.walked = true;
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk to the chest"));
		}

		Map<ItemStack, Integer> amounts = new IdentityHashMap<>();
		deposit.forEach((slot, count) -> {
			ItemStack stack = this.drifter.getInventory().getItem(slot);
			if (this.onlyMatching == null || this.onlyMatching.contains(stack.getItem())) {
				amounts.put(stack, count);
			}
		});
		return this.begin(ContainerTask.deposit(this.drifter, this.chest, amounts, "what it doesn't need on it"));
	}
}
