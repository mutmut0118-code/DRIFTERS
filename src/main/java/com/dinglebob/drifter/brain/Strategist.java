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
			drifter -> has(drifter, stack -> stack.is(Items.FURNACE)) || Stations.nearest(drifter, Blocks.FURNACE, Stations.USE_RANGE) != null
				|| furnaceAtHome(drifter)),
		new Target("some cobblestone", List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE), 16,
			drifter -> drifter.getInventory().count(stack -> stack.is(ItemTags.STONE_TOOL_MATERIALS)) >= 16),
		new Target("torches", List.of(Items.TORCH), 8, drifter -> drifter.getInventory().count(stack -> stack.is(Items.TORCH)) >= 8),
		new Target("a stone shovel", List.of(Items.STONE_SHOVEL), 1, drifter -> has(drifter, stack -> stack.is(ItemTags.SHOVELS)
			&& !stack.is(Items.WOODEN_SHOVEL) && !stack.is(Items.GOLDEN_SHOVEL))));

	/** It has a furnace set up at home (it doesn't need another one while it's off mining). */
	private static boolean furnaceAtHome(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		if (home == null) {
			return false;
		}

		var dimension = drifter.level().dimension().location();
		return drifter.getMemory().nearest(com.dinglebob.drifter.memory.MemoryKind.STATION, dimension, net.minecraft.world.phys.Vec3.atCenterOf(home.center),
			Stations.USE_RANGE, entry -> entry.label.equals("minecraft:furnace")) != null;
	}

	/** Iron-age armor: worn or owned, iron or better (diamond, netherite). */
	private static Predicate<DrifterEntity> armor(Item iron, Item diamond, Item netherite) {
		return drifter -> has(drifter, stack -> stack.is(iron) || stack.is(diamond) || stack.is(netherite));
	}

	/**
	 * Stage 5 targets, in the order players make them (section 9 "Within-stage priorities: Iron"): pickaxe, bucket,
	 * shield, chestplate, leggings, helmet, boots, then shears and flint and steel. Full iron armor takes 24 ingots,
	 * so armor is spread over several mining trips.
	 */
	public static final List<Target> IRON_TARGETS = List.of(
		new Target("an iron pickaxe", List.of(Items.IRON_PICKAXE), 1,
			drifter -> has(drifter, stack -> stack.is(ItemTags.PICKAXES) && stack.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()))),
		new Target("a bucket", List.of(Items.BUCKET), 1,
			drifter -> has(drifter, stack -> stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET))),
		new Target("a shield", List.of(Items.SHIELD), 1, drifter -> has(drifter, stack -> stack.is(Items.SHIELD))),
		new Target("an iron chestplate", List.of(Items.IRON_CHESTPLATE), 1, armor(Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE)),
		new Target("iron leggings", List.of(Items.IRON_LEGGINGS), 1, armor(Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS)),
		new Target("an iron helmet", List.of(Items.IRON_HELMET), 1, armor(Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET)),
		new Target("iron boots", List.of(Items.IRON_BOOTS), 1, armor(Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS)),
		new Target("shears", List.of(Items.SHEARS), 1, drifter -> has(drifter, stack -> stack.is(Items.SHEARS))),
		new Target("flint and steel", List.of(Items.FLINT_AND_STEEL), 1, drifter -> has(drifter, stack -> stack.is(Items.FLINT_AND_STEEL))));

	private Strategist() {
	}

	/** Spare pickaxes: start a trip with a half-worn one, and it needs another. */
	public static final double SPARE_WEAR = 0.5;

	/** Its only pickaxe on it is half worn (or it has none on it). */
	public static boolean needsSparePickaxe(DrifterEntity drifter) {
		int carried = 0;
		double leastWorn = 1.0;
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = drifter.getInventory().getItem(slot);
			if (stack.is(ItemTags.PICKAXES)) {
				carried++;
				leastWorn = Math.min(leastWorn, stack.isDamageableItem() ? stack.getDamageValue() / (double)stack.getMaxDamage() : 0.0);
			}
		}

		return carried == 0 || carried == 1 && leastWorn > SPARE_WEAR;
	}

	/** Every colour of bed. */
	public static final List<Item> BEDS = List.of(Items.WHITE_BED, Items.ORANGE_BED, Items.MAGENTA_BED, Items.LIGHT_BLUE_BED, Items.YELLOW_BED,
		Items.LIME_BED, Items.PINK_BED, Items.GRAY_BED, Items.LIGHT_GRAY_BED, Items.CYAN_BED, Items.PURPLE_BED, Items.BLUE_BED, Items.BROWN_BED,
		Items.GREEN_BED, Items.RED_BED, Items.BLACK_BED);

	/** It has a bed: in its hut, carried, or stored. */
	public static boolean hasBed(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		if (home != null && home.hutBuilt && com.dinglebob.drifter.building.StarterHut.of(home).bed(drifter.level()) != null) {
			return true;
		}

		return has(drifter, stack -> stack.is(ItemTags.BEDS));
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

		// The iron age once it has settled (stage 4); nomads, who barely settle, get there anyway after their first night.
		Home home = drifter.getDrifterBrain().home();
		boolean settled = home != null && home.hasChest()
			|| drifter.getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.NOMAD;
		if (settled) {
			// Section 10.17 "Spares: keeps a spare pickaxe": its only pickaxe half worn, it makes a stone one first (found in
			// testing: it came up from the mine with a worn pickaxe and no wood for sticks, and never went down again).
			if (needsSparePickaxe(drifter)) {
				int stone = drifter.getInventory().count(stack -> stack.is(Items.STONE_PICKAXE));
				Target spare = new Target("a spare pickaxe", List.of(Items.STONE_PICKAXE), stone + 1, d -> !needsSparePickaxe(d));
				if (workable.test(spare)) {
					return spare;
				}
			}

			// A bed for the hut (section 9 stage 3/4; step 4 of milestone 8): three wool of one colour and three planks.
			// Only once it has seen sheep of a colour it can use: a player without sheep around gets on with other things
			// (found in testing: it wandered for five days looking for sheep and never went for iron).
			if (home != null && home.hutBuilt && !hasBed(drifter)) {
				Target bed = new Target("a bed", BEDS, 1, Strategist::hasBed);
				Planner.Plan plan = Planner.plan(drifter, KnownWorld.scan(drifter), BEDS, 1);
				if (plan.feasible() && !(plan.next() instanceof Planner.Explore) && workable.test(bed)) {
					return bed;
				}
			}

			for (Target target : IRON_TARGETS) {
				if (!target.satisfied().test(drifter) && workable.test(target)) {
					return target;
				}
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
			// Stage 4, settling: a home with a chest. Stage 5, iron: an iron pickaxe, a bucket, a shield and 2+ armor pieces.
			Home home = drifter.getDrifterBrain().home();
			if (home == null || !home.hasChest()) {
				return 3;
			}

			int armor = 0;
			for (int i = 3; i <= 6; i++) {
				armor += IRON_TARGETS.get(i).satisfied().test(drifter) ? 1 : 0;
			}

			boolean iron = IRON_TARGETS.get(0).satisfied().test(drifter) && IRON_TARGETS.get(1).satisfied().test(drifter)
				&& IRON_TARGETS.get(2).satisfied().test(drifter) && armor >= 2;
			return iron ? 5 : 4;
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
			case 4 -> "settling";
			default -> "iron";
		};
	}
}
