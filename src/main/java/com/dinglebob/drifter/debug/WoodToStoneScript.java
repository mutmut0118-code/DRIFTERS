package com.dinglebob.drifter.debug;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.CraftTask;
import com.dinglebob.drifter.executor.task.MineTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * DEBUG SCRIPT for milestone 2's "done when" test (DESIGN.md section 25): from punching wood to stone tools.
 * A fixed sequence, not AI: no decisions, no planner (that's milestone 5). It finds trees and stone with
 * {@link DebugScan} (a test shortcut instead of its memory) and walks there with the pathfinder. Every action itself (mining, crafting, placing) follows the
 * player rules.
 */
public class WoodToStoneScript extends Task {
	public static final int SEARCH_RADIUS = 24;
	/** 4 logs = 16 planks: table 4 + sticks 6 (12 sticks) + wooden pickaxe 3 = 13. */
	private static final int LOGS_NEEDED = 4;
	private static final int STICK_CRAFTS = 3;
	/** Stone pickaxe 3 + axe 3 + sword 1 + shovel 1. */
	private static final int COBBLE_NEEDED = 8;
	private static final double DROP_PICKUP_RADIUS = 6.0;
	private static final int MAX_FAILURES = 15;
	/** Only blocks this close are checked for reach (reach is 4.5), to keep the raycasts per tick small. */
	private static final double REACH_CHECK_DISTANCE = 6.0;
	/** How many candidates to try finding a standing spot for before giving up this round. */
	private static final int MAX_SPOT_SEARCHES = 20;
	private static final int MAX_RETURN_ATTEMPTS = 3;

	private static final Predicate<ItemStack> LOGS = stack -> stack.is(ItemTags.LOGS);
	private static final Predicate<ItemStack> STONE_MATERIALS = stack -> stack.is(ItemTags.STONE_TOOL_MATERIALS);
	private static final Predicate<BlockState> STONE_BLOCKS = state -> state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)
		|| state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLED_DEEPSLATE);

	private enum Step {
		LOGS, PLANKS, TABLE, PLACE_TABLE, STICKS, WOODEN_PICKAXE, STONE, RETURN_TO_TABLE,
		STONE_PICKAXE, STONE_AXE, STONE_SWORD, STONE_SHOVEL, DONE
	}

	private final Consumer<Component> progress;
	private final Set<BlockPos> blacklist = new HashSet<>();
	private Step step = Step.LOGS;
	@Nullable
	private Task child;
	@Nullable
	private BlockPos childTarget;
	@Nullable
	private BlockPos table;
	@Nullable
	private BlockPos plannedTable;
	/** The block it last walked toward; if it still can't reach it after arriving, it's skipped. */
	@Nullable
	private BlockPos walkedTo;
	private int failures;
	private int returnAttempts;

	public WoodToStoneScript(DrifterEntity drifter, Consumer<Component> progress) {
		super(drifter);
		this.progress = progress;
	}

	@Override
	public String describe() {
		return "scripted test: wood to stone tools";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 10;
	}

	@Override
	protected void onStart() {
		this.say("Step: logs");
	}

	@Override
	protected Status onTick() {
		if (this.child != null) {
			Status status = this.child.tick();
			if (status == Status.RUNNING) {
				return Status.RUNNING;
			}

			Task finished = this.child;
			this.child = null;
			finished.stop();
			if (status == Status.FAILURE) {
				this.say("  (" + finished.describe() + " failed: " + finished.failureReason() + ")");
				if (this.childTarget != null) {
					this.blacklist.add(this.childTarget);
				}

				if (++this.failures > MAX_FAILURES) {
					return this.fail("too many failed steps; last: " + finished.failureReason());
				}

				if (this.isCraftingStep()) {
					return this.fail(finished.describe() + ": " + finished.failureReason());
				}
			} else if (this.step == Step.PLACE_TABLE) {
				this.table = this.plannedTable;
				this.advance(Step.STICKS);
			} else if (this.isCraftingStep()) {
				this.advance(Step.values()[this.step.ordinal() + 1]);
			}
		}

		if (this.step == Step.DONE) {
			this.say("Wood to stone complete: " + this.drifter.getInventory().count(stack -> stack.is(Items.STONE_PICKAXE)) + " stone pickaxe, plus axe, sword and shovel.");
			return Status.SUCCESS;
		}

		this.childTarget = null;
		this.child = this.nextChild();
		return this.child == null && this.failureReason() != null ? Status.FAILURE : Status.RUNNING;
	}

	private boolean isCraftingStep() {
		return switch (this.step) {
			case PLANKS, TABLE, STICKS, WOODEN_PICKAXE, STONE_PICKAXE, STONE_AXE, STONE_SWORD, STONE_SHOVEL -> true;
			default -> false;
		};
	}

	@Nullable
	private Task nextChild() {
		DrifterInventory inventory = this.drifter.getInventory();
		return switch (this.step) {
			case LOGS -> {
				if (inventory.count(LOGS) >= LOGS_NEEDED) {
					this.advance(Step.PLANKS);
					yield null;
				}

				yield this.gather(state -> state.is(BlockTags.LOGS), null, "logs");
			}
			case PLANKS -> new CraftTask(this.drifter, stack -> stack.is(ItemTags.PLANKS), "planks", inventory.count(LOGS), null);
			case TABLE -> new CraftTask(this.drifter, stack -> stack.is(Items.CRAFTING_TABLE), "a crafting table", 1, null);
			case PLACE_TABLE -> {
				Optional<BlockPos> spot = DebugScan.placementSpotNear(this.drifter, this.blacklist);
				if (spot.isEmpty()) {
					this.fail("no free spot next to it to place the crafting table");
					yield null;
				}

				this.plannedTable = spot.get();
				this.childTarget = spot.get();
				yield new PlaceBlockTask(this.drifter, spot.get(), stack -> stack.is(Items.CRAFTING_TABLE), "crafting table");
			}
			case STICKS -> new CraftTask(this.drifter, stack -> stack.is(Items.STICK), "sticks", STICK_CRAFTS, null);
			case WOODEN_PICKAXE -> this.craftAtTable(Items.WOODEN_PICKAXE, "a wooden pickaxe");
			case STONE -> {
				if (inventory.count(STONE_MATERIALS) >= COBBLE_NEEDED) {
					this.advance(Step.RETURN_TO_TABLE);
					yield null;
				}

				yield this.gather(STONE_BLOCKS, stack -> stack.is(ItemTags.PICKAXES), "stone");
			}
			case RETURN_TO_TABLE -> {
				if (this.table != null && Reach.canReach(this.drifter, this.table)) {
					this.advance(Step.STONE_PICKAXE);
					yield null;
				}

				BlockPos spot = this.table == null ? null : DebugScan.standingSpotFor(this.drifter, this.table);
				if (spot == null || ++this.returnAttempts > MAX_RETURN_ATTEMPTS) {
					this.fail("can't find a way back to the crafting table");
					yield null;
				}

				yield new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk back to the crafting table");
			}
			case STONE_PICKAXE -> this.craftAtTable(Items.STONE_PICKAXE, "a stone pickaxe");
			case STONE_AXE -> this.craftAtTable(Items.STONE_AXE, "a stone axe");
			case STONE_SWORD -> this.craftAtTable(Items.STONE_SWORD, "a stone sword");
			case STONE_SHOVEL -> this.craftAtTable(Items.STONE_SHOVEL, "a stone shovel");
			case DONE -> null;
		};
	}

	private Task craftAtTable(Item item, String label) {
		return new CraftTask(this.drifter, stack -> stack.is(item), label, 1, this.table);
	}

	/**
	 * One round of gathering: mine the best block it can reach right now (lowest first, like chopping a tree from
	 * the bottom), then pick up the drops; or, if it can't reach any, walk to a spot next to the nearest one.
	 */
	@Nullable
	private Task gather(Predicate<BlockState> blocks, @Nullable Predicate<ItemStack> tool, String what) {
		BlockPos below = this.drifter.blockPosition().below();
		List<BlockPos> candidates = DebugScan.findBlocks(this.drifter, SEARCH_RADIUS,
			blocks).stream()
			.filter(pos -> !this.blacklist.contains(pos) && !pos.equals(below) && DebugScan.isExposed(this.drifter.level(), pos))
			.toList();
		if (candidates.isEmpty()) {
			this.fail("can't find any more " + what + " within " + SEARCH_RADIUS + " blocks");
			return null;
		}

		if (this.walkedTo != null) {
			BlockPos arrivedAt = this.walkedTo;
			this.walkedTo = null;
			if (!Reach.canReach(this.drifter, arrivedAt)) {
				this.blacklist.add(arrivedAt);
			}
		}

		Vec3 eye = this.drifter.getEyePosition();
		Optional<BlockPos> inReach = candidates.stream()
			.filter(pos -> !this.blacklist.contains(pos) && pos.distToCenterSqr(eye) <= REACH_CHECK_DISTANCE * REACH_CHECK_DISTANCE)
			.filter(pos -> Reach.canReach(this.drifter, pos))
			.min((a, b) -> a.getY() != b.getY() ? Integer.compare(a.getY(), b.getY())
				: Double.compare(a.distToCenterSqr(this.drifter.position()), b.distToCenterSqr(this.drifter.position())));
		if (inReach.isPresent()) {
			this.childTarget = inReach.get();
			List<Task> steps = tool == null
				? List.of(new MineTask(this.drifter, inReach.get()), new CollectItemsTask(this.drifter, DROP_PICKUP_RADIUS))
				: List.of(new SelectItemTask(this.drifter, tool, "a pickaxe"), new MineTask(this.drifter, inReach.get()),
					new CollectItemsTask(this.drifter, DROP_PICKUP_RADIUS));
			return new SequenceTask(this.drifter, "gather " + what, steps);
		}

		int searched = 0;
		for (BlockPos target : candidates) {
			if (this.blacklist.contains(target)) {
				continue;
			}

			if (++searched > MAX_SPOT_SEARCHES) {
				// Spread the search over ticks; the ones already tried are blacklisted.
				return null;
			}

			BlockPos spot = DebugScan.standingSpotFor(this.drifter, target);
			if (spot != null) {
				this.childTarget = target;
				this.walkedTo = target;
				return new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk to the " + what);
			}

			this.blacklist.add(target);
		}

		this.fail("can't find a spot to stand next to any " + what);
		return null;
	}

	private void advance(Step next) {
		this.step = next;
		this.say("Step: " + next.name().toLowerCase(Locale.ROOT).replace('_', ' '));
	}

	private void say(String message) {
		this.progress.accept(Component.literal("[" + this.drifter.getName().getString() + "] " + message));
	}

	@Override
	protected void onStop() {
		if (this.child != null) {
			this.child.stop();
		}
	}
}
