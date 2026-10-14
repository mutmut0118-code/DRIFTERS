package com.dinglebob.drifter.brain;

import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The long view (DESIGN.md section 8, layer 1, and section 9): which progression stage it's at, computed from what it
 * owns (never stored as a flag), and the next thing to make, in the order players actually make them.
 * Milestone 5 covers stages 0 to 3 (arrival, wood, stone, first night).
 * TODO(milestone 8): stages 4 to 8, home storage, the base project queue, archetype pacing.
 */
public final class Strategist {
	/** A progression target: end up carrying {@code count} of any of {@code items}. */
	public record Target(String label, List<Item> items, int count, Predicate<DrifterEntity> satisfied) {
	}

	/** Stage 1 to 3 targets, in order (section 9: wooden pickaxe; stone tools and a furnace; torches). */
	public static final List<Target> EARLY_TARGETS = List.of(
		new Target("a wooden pickaxe", List.of(Items.WOODEN_PICKAXE), 1, drifter -> has(drifter, stack -> stack.is(ItemTags.PICKAXES))),
		new Target("a stone pickaxe", List.of(Items.STONE_PICKAXE), 1,
			drifter -> has(drifter, stack -> stack.is(ItemTags.PICKAXES) && stack.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState()))),
		new Target("a stone sword", List.of(Items.STONE_SWORD), 1, drifter -> has(drifter, stack -> stack.is(ItemTags.SWORDS)
			&& !stack.is(Items.WOODEN_SWORD) && !stack.is(Items.GOLDEN_SWORD))),
		new Target("a stone axe", List.of(Items.STONE_AXE), 1, drifter -> has(drifter, stack -> stack.is(ItemTags.AXES)
			&& !stack.is(Items.WOODEN_AXE) && !stack.is(Items.GOLDEN_AXE))),
		new Target("a furnace", List.of(Items.FURNACE), 1,
			drifter -> has(drifter, stack -> stack.is(Items.FURNACE)) || Stations.nearest(drifter, Blocks.FURNACE, Stations.USE_RANGE) != null),
		new Target("some cobblestone", List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE), 16,
			drifter -> drifter.getInventory().count(stack -> stack.is(ItemTags.STONE_TOOL_MATERIALS)) >= 16),
		new Target("torches", List.of(Items.TORCH), 8, drifter -> drifter.getInventory().count(stack -> stack.is(Items.TORCH)) >= 8),
		new Target("a stone shovel", List.of(Items.STONE_SHOVEL), 1, drifter -> has(drifter, stack -> stack.is(ItemTags.SHOVELS)
			&& !stack.is(Items.WOODEN_SHOVEL) && !stack.is(Items.GOLDEN_SHOVEL))));

	private Strategist() {
	}

	/** Carried, worn, or stored in its chests at home (section 9: stage counts home storage). */
	private static boolean has(DrifterEntity drifter, Predicate<ItemStack> filter) {
		DrifterInventory inventory = drifter.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (filter.test(inventory.getItem(slot))) {
				return true;
			}
		}

		Home home = drifter.getDrifterBrain().home();
		if (home != null) {
			for (Item item : home.stored().keySet()) {
				if (filter.test(new ItemStack(item))) {
					return true;
				}
			}
		}

		return false;
	}

	/** The first target it hasn't reached yet, or null when the early game is done. */
	@Nullable
	public static Target nextTarget(DrifterEntity drifter) {
		return nextTarget(drifter, target -> true);
	}

	/** The first target it hasn't reached yet that it can work on now (not just waiting on a furnace), or null. */
	@Nullable
	public static Target nextTarget(DrifterEntity drifter, Predicate<Target> workable) {
		for (Target target : EARLY_TARGETS) {
			if (!target.satisfied().test(drifter) && workable.test(target)) {
				return target;
			}
		}

		return null;
	}

	/**
	 * Section 9 stages, from what it owns: 0 arrival (nothing), 1 wood, 2 stone (has a pickaxe), 3 first night (stone
	 * tools and a furnace).
	 */
	public static int stage(DrifterEntity drifter) {
		if (EARLY_TARGETS.get(1).satisfied().test(drifter) && EARLY_TARGETS.get(4).satisfied().test(drifter)) {
			// Stage 4, settling: a home with a chest.
			Home home = drifter.getDrifterBrain().home();
			return home != null && home.hasChest() ? 4 : 3;
		}

		if (EARLY_TARGETS.get(0).satisfied().test(drifter)) {
			return 2;
		}

		return has(drifter, stack -> stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS) || stack.is(Items.CRAFTING_TABLE)) ? 1 : 0;
	}

	public static String stageName(int stage) {
		return switch (stage) {
			case 0 -> "arrival";
			case 1 -> "wood";
			case 2 -> "stone";
			case 3 -> "first night";
			default -> "settling";
		};
	}
}
