package com.dinglebob.drifter.brain.activity;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.Stations;
import com.dinglebob.drifter.brain.Storage;
import com.dinglebob.drifter.building.ChestLabels;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.CraftTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.hands.Crafting;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Going home to put things away (DESIGN.md section 10.18, "Going home: when full, when carrying something precious"):
 * walks to its chests and puts in everything that isn't its working kit ({@link Storage}). A full chest means the next
 * one; all full, it gives up and the settle goal makes another chest. Done, it labels the house's chests it can reach from
 * there ({@link ChestLabels}: a sign on the front saying what's in it; made from its planks if it has none).
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

	/** Labels it put up or put right this time (a few at a time: it's putting things away, not decorating). */
	private int labels;
	private static final int MAX_LABELS = 4;
	private final Set<BlockPos> labelTried = new HashSet<>();
	private boolean madeSigns;
	@Nullable
	private BlockPos labelling;
	@Nullable
	private String labelText;

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof PlaceBlockTask && this.labelling != null) {
			if (status == Status.SUCCESS && this.drifter.level().getBlockEntity(this.labelling) instanceof SignBlockEntity sign) {
				ChestLabels.write(sign, this.labelText);
				this.labels++;
				this.think("labelled a chest: " + this.labelText.toLowerCase(java.util.Locale.ROOT));
			}

			this.labelling = null;
			return;
		}

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
			Status label = this.label(home);
			if (label != null) {
				return label;
			}

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

	/**
	 * The next step labelling its house's chests in reach (from where it stands: the storage wall from its spot by the
	 * crafting table), or null when that's done: a sign on the front of each that has none (the space in front clear),
	 * made first if it carries none and can (planks and a stick at the table); a sign put right if what it holds changed.
	 */
	@Nullable
	private Status label(Home home) {
		Level level = this.drifter.level();
		if (!home.planDone || this.labels >= MAX_LABELS) {
			return null;
		}

		for (BlockPos pos : home.chests()) {
			BlockState chest = level.getBlockState(pos);
			if (this.labelTried.contains(pos) || !home.inOwnBuilding(pos, 0) || !(chest.getBlock() instanceof ChestBlock)) {
				continue;
			}

			Direction facing = chest.getValue(ChestBlock.FACING);
			BlockPos front = pos.relative(facing);
			String text = ChestLabels.of(home.contents(pos));
			BlockState there = level.getBlockState(front);
			if (text == null || !Reach.canReach(this.drifter, front)) {
				continue;
			}

			if (there.getBlock() instanceof WallSignBlock && there.getValue(WallSignBlock.FACING) == facing) {
				this.labelTried.add(pos);
				if (level.getBlockEntity(front) instanceof SignBlockEntity sign && !ChestLabels.says(sign, text)) {
					// (Clicked and written over, as a player edits a sign.)
					ChestLabels.write(sign, text);
					this.labels++;
					this.think("changed a chest's label to " + text.toLowerCase(java.util.Locale.ROOT));
				}

				continue;
			}

			if (!there.isAir()) {
				this.labelTried.add(pos);
				continue;
			}

			Item sign = this.signCarried();
			if (sign == null) {
				return this.makeSigns();
			}

			this.labelTried.add(pos);
			this.labelling = front;
			this.labelText = text;
			BlockState wall = ChestLabels.wallSign(sign).defaultBlockState().setValue(WallSignBlock.FACING, facing);
			// (Crouching: a click on a chest otherwise opens it.)
			return this.begin(PlaceBlockTask.exactly(this.drifter, front, wall, "a sign").single());
		}

		return null;
	}

	@Nullable
	private Item signCarried() {
		for (int slot = 0; slot < this.drifter.getInventory().getContainerSize(); slot++) {
			Item item = this.drifter.getInventory().getItem(slot).getItem();
			if (ChestLabels.wallSign(item) != null) {
				return item;
			}
		}

		return null;
	}

	/** Signs made at the crafting table in reach (sticks first if it needs them); null if it can't now. */
	@Nullable
	private Status makeSigns() {
		if (this.madeSigns) {
			return null;
		}

		this.madeSigns = true;
		BlockPos table = Stations.usable(this.drifter, Blocks.CRAFTING_TABLE);
		if (table == null || !Reach.canReach(this.drifter, table)) {
			return null;
		}

		java.util.function.Predicate<ItemStack> anySign = stack -> ChestLabels.wallSign(stack.getItem()) != null;
		if (Crafting.find(this.drifter, anySign, true).isEmpty()) {
			if (this.drifter.getInventory().count(stack -> stack.is(Items.STICK)) > 0 || Crafting.find(this.drifter, stack -> stack.is(Items.STICK), true).isEmpty()) {
				return null;
			}

			// Sticks, then the signs on the next pass.
			this.madeSigns = false;
			return this.begin(new CraftTask(this.drifter, stack -> stack.is(Items.STICK), "sticks", 1, table));
		}

		return this.begin(new CraftTask(this.drifter, anySign, "signs", 1, table));
	}
}
