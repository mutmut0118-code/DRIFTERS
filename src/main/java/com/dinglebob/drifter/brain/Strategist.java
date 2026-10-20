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

	/** The stage checks read these two by name (stage 2 from the wooden pickaxe, stage 3 from stone tools and a furnace). */
	private static final Target WOODEN_PICKAXE = new Target("a wooden pickaxe", List.of(Items.WOODEN_PICKAXE), 1,
		drifter -> has(drifter, stack -> stack.is(ItemTags.PICKAXES)));
	private static final Target STONE_PICKAXE = new Target("a stone pickaxe", List.of(Items.STONE_PICKAXE), 1,
		drifter -> has(drifter, stack -> stack.is(ItemTags.PICKAXES) && stack.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState())));
	private static final Target FURNACE = new Target("a furnace", List.of(Items.FURNACE), 1,
		drifter -> has(drifter, stack -> stack.is(Items.FURNACE)) || Stations.nearest(drifter, Blocks.FURNACE, Stations.USE_RANGE) != null
			|| furnaceAtHome(drifter));

	/** Dusk is near (from an hour or so before it starts thinking about shelter, all night). */
	private static boolean nearDusk(DrifterEntity drifter) {
		long time = Schedule.believedTime(drifter);
		return time >= Schedule.shelterThinking(drifter) - 1500L || Schedule.isNight(time);
	}

	/** Stage 1 to 3 targets, in order (section 9: wooden pickaxe; stone tools and a furnace; torches). */
	public static final List<Target> EARLY_TARGETS = List.of(
		WOODEN_PICKAXE,
		// No weapon yet and dusk coming (no stone found in time): a wooden sword, as a player makes one (two planks and a
		// stick). With a shield that's enough to stay out (Schedule.fitToWork). Not wanted before then.
		new Target("a sword before dark", List.of(Items.WOODEN_SWORD), 1, drifter -> !nearDusk(drifter) || Weapons.hasTier(drifter, Weapons.Tier.WOOD)),
		// A cheap shield, if a mod makes one without iron (Spartan Shields: a crude wooden shield, four planks and a stick).
		// A sword and a shield see it through the night (user, 2026-10-20: "a sword and shield is enough for the average
		// drifter to survive the night and still collect resources"). Without such a mod: the game's shield, at iron.
		new Target("a shield", com.dinglebob.drifter.hands.Shields.CRUDE_SHIELD, 1,
			drifter -> com.dinglebob.drifter.hands.Shields.CRUDE_SHIELD.isEmpty() || com.dinglebob.drifter.hands.Shields.has(drifter)),
		STONE_PICKAXE,
		// A stone sword, or any weapon at least that good (a modded one counts for what it's worth: Weapons.tier).
		new Target("a stone sword", List.of(Items.STONE_SWORD), 1, drifter -> Weapons.hasTier(drifter, Weapons.Tier.STONE)),
		// Its crude shield braced with stone (four cobblestone more; Spartan Shields).
		new Target("a stone braced shield", com.dinglebob.drifter.hands.Shields.STONE_SHIELD, 1,
			drifter -> com.dinglebob.drifter.hands.Shields.STONE_SHIELD.isEmpty() || com.dinglebob.drifter.hands.Shields.hasBetterThanCrude(drifter)),
		new Target("a stone axe", List.of(Items.STONE_AXE), 1, drifter -> has(drifter, stack -> stack.is(ItemTags.AXES)
			&& !stack.is(Items.WOODEN_AXE) && !stack.is(Items.GOLDEN_AXE))),
		FURNACE,
		new Target("some cobblestone", List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE), 16,
			drifter -> drifter.getInventory().count(stack -> stack.is(ItemTags.STONE_TOOL_MATERIALS)) >= 16),
		new Target("torches", List.of(Items.TORCH), 8, drifter -> drifter.getInventory().count(stack -> stack.is(Items.TORCH)) >= 8),
		new Target("a stone shovel", List.of(Items.STONE_SHOVEL), 1, drifter -> has(drifter, stack -> stack.is(ItemTags.SHOVELS)
			&& !stack.is(Items.WOODEN_SHOVEL) && !stack.is(Items.GOLDEN_SHOVEL))),
		// A boat, for any water wider than a few blocks (section 10.9: "carries one from early on"): five planks of one wood.
		new Target("a boat", List.of(Items.OAK_BOAT, Items.SPRUCE_BOAT, Items.BIRCH_BOAT, Items.JUNGLE_BOAT, Items.ACACIA_BOAT, Items.CHERRY_BOAT,
			Items.DARK_OAK_BOAT, Items.MANGROVE_BOAT, Items.BAMBOO_RAFT), 1, drifter -> has(drifter, com.dinglebob.drifter.executor.task.BoatTask::isBoat)));

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
		// (As good as the game's own: a crude or stone braced one from early on doesn't count.)
		new Target("a shield", List.of(Items.SHIELD), 1, com.dinglebob.drifter.hands.Shields::hasIronAge),
		new Target("an iron chestplate", List.of(Items.IRON_CHESTPLATE), 1, armor(Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE)),
		new Target("iron leggings", List.of(Items.IRON_LEGGINGS), 1, armor(Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS)),
		new Target("an iron helmet", List.of(Items.IRON_HELMET), 1, armor(Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET)),
		new Target("iron boots", List.of(Items.IRON_BOOTS), 1, armor(Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS)),
		new Target("shears", List.of(Items.SHEARS), 1, drifter -> has(drifter, stack -> stack.is(Items.SHEARS))),
		new Target("flint and steel", List.of(Items.FLINT_AND_STEEL), 1, drifter -> has(drifter, stack -> stack.is(Items.FLINT_AND_STEEL))),
		// A smoker and a blast furnace for its hut (user's request, 2026-10-16): food and ore twice as fast.
		new Target("a smoker", List.of(Items.SMOKER), 1, drifter -> stationAtHome(drifter, Items.SMOKER, Blocks.SMOKER)),
		new Target("a blast furnace", List.of(Items.BLAST_FURNACE), 1, drifter -> stationAtHome(drifter, Items.BLAST_FURNACE, Blocks.BLAST_FURNACE)));

	/**
	 * It has this station at home, or one on it to put there; or it has no hut of its own to put one in (no home yet, a
	 * village house, an outpost), so it doesn't want one.
	 */
	private static boolean stationAtHome(DrifterEntity drifter, Item item, net.minecraft.world.level.block.Block block) {
		Home home = drifter.getDrifterBrain().home();
		if (home == null || home.borrowed() || !home.hutBuilt) {
			return true;
		}

		if (has(drifter, stack -> stack.is(item))) {
			return true;
		}

		String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
		return drifter.getMemory().nearest(com.dinglebob.drifter.memory.MemoryKind.STATION, drifter.level().dimension().location(),
			net.minecraft.world.phys.Vec3.atCenterOf(home.center), Stations.USE_RANGE, entry -> entry.label.equals(id)) != null;
	}

	/** Diamond-age armor: worn or owned, diamond or better. */
	private static Predicate<DrifterEntity> diamondArmor(Item diamond, Item netherite) {
		return drifter -> has(drifter, stack -> stack.is(diamond) || stack.is(netherite));
	}

	/**
	 * Stage 7 targets (section 9 "Within-stage priorities: Diamond"): pickaxe first (it mines obsidian), then sword, then
	 * chestplate, leggings, helmet, boots, spread over many trips (24 diamonds for the armor).
	 */
	public static final List<Target> DIAMOND_TARGETS = List.of(
		new Target("a diamond pickaxe", List.of(Items.DIAMOND_PICKAXE), 1,
			drifter -> has(drifter, stack -> stack.is(Items.DIAMOND_PICKAXE) || stack.is(Items.NETHERITE_PICKAXE))),
		// (Its favorite diamond weapon in its place in the order: laterTargets; any diamond-tier weapon will do.)
		new Target("a diamond sword", List.of(Items.DIAMOND_SWORD), 1, drifter -> Weapons.hasTier(drifter, Weapons.Tier.DIAMOND)),
		new Target("a diamond chestplate", List.of(Items.DIAMOND_CHESTPLATE), 1, diamondArmor(Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE)),
		new Target("diamond leggings", List.of(Items.DIAMOND_LEGGINGS), 1, diamondArmor(Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS)),
		new Target("a diamond helmet", List.of(Items.DIAMOND_HELMET), 1, diamondArmor(Items.DIAMOND_HELMET, Items.NETHERITE_HELMET)),
		new Target("diamond boots", List.of(Items.DIAMOND_BOOTS), 1, diamondArmor(Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS)));

	/**
	 * Stage 8 targets (section 9: enchanting): the enchanting table (4 obsidian, 2 diamonds, a book), and lapis to
	 * enchant with while it has gear to enchant.
	 */
	public static final List<Target> ENCHANTING_TARGETS = List.of(
		new Target("an enchanting table", List.of(Items.ENCHANTING_TABLE), 1, drifter -> has(drifter, stack -> stack.is(Items.ENCHANTING_TABLE))
			|| drifter.getDrifterBrain().home() != null && drifter.getDrifterBrain().home().enchantingTable(drifter.level()) != null),
		new Target("some lapis", List.of(Items.LAPIS_LAZULI), 6, drifter -> has(drifter, stack -> stack.is(Items.LAPIS_LAZULI))
			|| com.dinglebob.drifter.brain.activity.EnchantActivity.nextToEnchant(drifter) == null));

	/**
	 * Stage 9 targets (section 19, milestone 11): what the portal takes, 10 obsidian and flint and steel (both also on the
	 * packing list: flint and steel relights a portal a ghast put out). Done once its portal is lit.
	 */
	public static final List<Target> PORTAL_TARGETS = List.of(
		new Target("obsidian for a Nether portal", List.of(Items.OBSIDIAN), com.dinglebob.drifter.brain.activity.PortalActivity.OBSIDIAN,
			drifter -> portalLit(drifter) || drifter.getInventory().count(stack -> stack.is(Items.OBSIDIAN))
				>= com.dinglebob.drifter.brain.activity.PortalActivity.OBSIDIAN - portalObsidianPlaced(drifter)),
		new Target("flint and steel", List.of(Items.FLINT_AND_STEEL), 1, drifter -> has(drifter, stack -> stack.is(Items.FLINT_AND_STEEL))));

	public static final int NETHER_BLOCKS = 64;
	public static final int NETHER_FOOD = 20;
	public static final int NETHER_ARROWS = 32;

	/**
	 * The Nether packing list (section 19), what it gets once its portal is lit: a gold armor piece (piglins), a stack of
	 * cobblestone (ghast-proof building), 20 cooked food, a spare pickaxe, a bow and arrows. Sword, shield, armor and
	 * flint and steel it has by then. (Gold to barter with comes with the piglin step.)
	 */
	public static final List<Target> NETHER_PACKING = List.of(
		new Target("a gold armor piece for the Nether", List.of(Items.GOLDEN_BOOTS, Items.GOLDEN_HELMET), 1,
			drifter -> has(drifter, stack -> stack.is(Items.GOLDEN_BOOTS) || stack.is(Items.GOLDEN_HELMET) || stack.is(Items.GOLDEN_CHESTPLATE)
				|| stack.is(Items.GOLDEN_LEGGINGS))),
		new Target("cobblestone for the Nether", List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE), NETHER_BLOCKS,
			drifter -> drifter.getInventory().count(stack -> stack.is(Items.COBBLESTONE) || stack.is(Items.COBBLED_DEEPSLATE)) >= NETHER_BLOCKS),
		// Steak (or cooked porkchop, the same): the most food and saturation a bite, for healing between fights (user's request,
		// 2026-10-16).
		new Target("steak for the Nether", List.of(Items.COOKED_BEEF, Items.COOKED_PORKCHOP), NETHER_FOOD,
			drifter -> drifter.getInventory().count(stack -> stack.is(Items.COOKED_BEEF) || stack.is(Items.COOKED_PORKCHOP)) >= NETHER_FOOD),
		new Target("a spare pickaxe for the Nether", List.of(Items.IRON_PICKAXE, Items.STONE_PICKAXE), 1, drifter -> !needsSparePickaxe(drifter)),
		// Bow and arrows (ghasts are out of sword reach, blazes safer from range): string from spiders or cobwebs.
		new Target("a bow", List.of(Items.BOW), 1, drifter -> has(drifter, com.dinglebob.drifter.hands.Shields::isBow)),
		new Target("arrows", List.of(Items.ARROW), NETHER_ARROWS, drifter -> drifter.getInventory().count(stack -> stack.is(Items.ARROW)) >= NETHER_ARROWS));

	/** Gold ingots to barter with (step 6). Wanted on the packing list but not waited for: a trip starts without them. */
	public static final int BARTER_GOLD = 8;
	public static final Target BARTER_GOLD_TARGET = new Target("gold to barter with piglins", List.of(Items.GOLD_INGOT), BARTER_GOLD,
		drifter -> drifter.getInventory().count(stack -> stack.is(Items.GOLD_INGOT)) >= BARTER_GOLD);

	/** Everything on the packing list carried (and flint and steel, a sword and a shield). */
	public static boolean packedForNether(DrifterEntity drifter) {
		for (Target target : NETHER_PACKING) {
			if (!target.satisfied().test(drifter)) {
				return false;
			}
		}

		return drifter.getInventory().count(stack -> stack.is(Items.FLINT_AND_STEEL)) > 0
			&& drifter.getInventory().count(Weapons::isWeapon) > 0
			&& has(drifter, com.dinglebob.drifter.hands.Shields::isShield);
	}

	/** Its portal by home is built and lit. */
	public static boolean portalLit(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		return home != null && home.portal != null && home.dimension.equals(drifter.level().dimension().location())
			&& drifter.level().isLoaded(home.portal.inside()) && home.portal.lit(drifter.level());
	}

	/** Obsidian already in its half-built frame (so it doesn't fetch all 10 again). */
	private static int portalObsidianPlaced(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		if (home == null || home.portal == null || !home.dimension.equals(drifter.level().dimension().location())) {
			return 0;
		}

		int placed = 0;
		for (int along = 0; along <= 3; along++) {
			for (int up = 0; up <= 4; up++) {
				if (drifter.level().getBlockState(home.portal.cell(along, up)).is(net.minecraft.world.level.block.Blocks.OBSIDIAN)) {
					placed++;
				}
			}
		}

		return placed;
	}

	/** A brewing stand carried or stored, or one by home. */
	private static boolean brewingStand(DrifterEntity drifter) {
		if (has(drifter, stack -> stack.is(Items.BREWING_STAND))) {
			return true;
		}

		Home home = drifter.getDrifterBrain().home();
		return home != null && drifter.getMemory().nearest(com.dinglebob.drifter.memory.MemoryKind.STATION, home.dimension,
			net.minecraft.world.phys.Vec3.atCenterOf(home.center), Stations.USE_RANGE, entry -> entry.label.equals("minecraft:brewing_stand")) != null;
	}

	public static final Target BREWING_STAND_TARGET = new Target("a brewing stand", List.of(Items.BREWING_STAND), 1, Strategist::brewingStand);
	/** Bottles to brew in, while there's something it would brew (empty ones come back from the potions it drinks). */
	public static final Target BOTTLES_TARGET = new Target("glass bottles", List.of(Items.GLASS_BOTTLE), 3,
		drifter -> !com.dinglebob.drifter.skills.BrewGoal.wantsBottles(drifter));

	/** What brewing takes that it works toward: a stand once it has a blaze rod, then three bottles. */
	private static List<Target> brewingTargets(DrifterEntity drifter) {
		if (!com.dinglebob.drifter.skills.BrewGoal.brews(drifter)) {
			return List.of();
		}

		boolean stand = brewingStand(drifter);
		if (stand) {
			return List.of(BOTTLES_TARGET);
		}

		return has(drifter, stack -> stack.is(Items.BLAZE_ROD)) ? List.of(BREWING_STAND_TARGET) : List.of();
	}

	/** One more bookshelf for the enchanting table: done when it carries one, or the ring is full (or there's no table). */
	public static final Target BOOKSHELF_TARGET = new Target("a bookshelf", List.of(Items.BOOKSHELF), 1, drifter -> {
		Home home = drifter.getDrifterBrain().home();
		net.minecraft.core.BlockPos table = home == null ? null : home.enchantingTable(drifter.level());
		return table == null || drifter.getInventory().count(stack -> stack.is(Items.BOOKSHELF)) > 0
			|| com.dinglebob.drifter.brain.activity.EnchantActivity.nextShelfSpot(drifter.level(), table, home) == null;
	});

	/**
	 * Iron then diamond targets, in the order it works on them. Miners jump to diamonds fast (section 9 "Pacing by
	 * archetype"): the diamond pickaxe and sword come right after the iron pickaxe, bucket and shield, before the armor.
	 */
	private static List<Target> laterTargets(DrifterEntity drifter) {
		List<Target> order = new java.util.ArrayList<>(IRON_TARGETS);
		// Its favorite weapon (Weapons: a Simply Swords longsword, a claymore...) at iron, right after the shield, if its
		// family is made at iron (vanilla swords skip iron, as most players do: stone, then diamond).
		Target ironWeapon = Weapons.target(drifter, Weapons.Tier.IRON);
		if (ironWeapon != null) {
			order.add(3, ironWeapon);
		}

		List<Target> diamond = new java.util.ArrayList<>(DIAMOND_TARGETS.subList(0, 2));
		Target diamondWeapon = Weapons.target(drifter, Weapons.Tier.DIAMOND);
		if (diamondWeapon != null) {
			diamond.set(1, diamondWeapon);
		}

		if (drifter.getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.MINER) {
			order.addAll(ironWeapon != null ? 4 : 3, diamond);
		} else {
			order.addAll(diamond);
		}

		// The enchanting table after the diamond pickaxe and sword, before the diamond armor (section 9: "some archetypes
		// make an enchanting table before any armor"; it makes the most of the diamonds it has).
		order.addAll(ENCHANTING_TARGETS);
		// Diamond chestplate, leggings and helmet before the Nether (user's request, 2026-10-16: blazes and ghasts hit hard,
		// iron isn't enough there). The boots after: in the Nether its gold piece goes in that slot anyway.
		order.addAll(DIAMOND_TARGETS.subList(2, 5));
		// The Nether once it enchants (stage 8; section 9: "Day 12 onward"), before the diamond boots and bookshelves.
		if (stage(drifter) >= 8) {
			order.addAll(PORTAL_TARGETS);
			if (portalLit(drifter)) {
				order.addAll(NETHER_PACKING);
				order.add(BARTER_GOLD_TARGET);
			}
		}

		// Brewing once it has blaze rods (section 10.14, Knowledge-gated): the stand, then bottles (the wart comes from the
		// Nether trips, the rest BrewGoal makes as it goes).
		order.addAll(brewingTargets(drifter));
		order.addAll(DIAMOND_TARGETS.subList(5, DIAMOND_TARGETS.size()));
		// Then bookshelves round the table, one at a time (3 books each: sugar cane and leather), up to 15.
		order.add(BOOKSHELF_TARGET);
		return order;
	}

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

		if (home != null && home.houseBed(drifter.level()) != null) {
			return true;
		}

		if (home != null && home.house() != null && home.house().bed(drifter.level()) != null) {
			return true;
		}

		return has(drifter, stack -> stack.is(ItemTags.BEDS));
	}

	/** Carried, worn, or stored in its chests at home (section 9: stage counts home storage). */
	public static boolean has(DrifterEntity drifter, Predicate<ItemStack> filter) {
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

			for (Target target : laterTargets(drifter)) {
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
		if (STONE_PICKAXE.satisfied().test(drifter) && FURNACE.satisfied().test(drifter)) {
			// Stage 4, settling: a home with a chest. Stage 5, iron: an iron pickaxe, a bucket, a shield and 2+ armor pieces.
			// Stages 6 and 7 below.
			Home home = drifter.getDrifterBrain().home();
			if (home == null || !home.hasChest()) {
				return 3;
			}

			int armor = 0;
			for (int i = 3; i <= 6; i++) {
				armor += IRON_TARGETS.get(i).satisfied().test(drifter) ? 1 : 0;
			}

			// Stage 7, diamond: a diamond pickaxe and sword, whatever else it has (Miners go for diamonds before iron armor).
			if (DIAMOND_TARGETS.get(0).satisfied().test(drifter) && DIAMOND_TARGETS.get(1).satisfied().test(drifter)) {
				// Stage 8, enchanting: enchanted gear.
				// Stage 9, the Nether: blaze rods (section 9 table: "Done when: blaze rods").
				if (has(drifter, stack -> stack.is(Items.BLAZE_ROD))) {
					return 9;
				}

				return has(drifter, ItemStack::isEnchanted) ? 8 : 7;
			}

			boolean iron = IRON_TARGETS.get(0).satisfied().test(drifter) && IRON_TARGETS.get(1).satisfied().test(drifter)
				&& IRON_TARGETS.get(2).satisfied().test(drifter) && armor >= 2;
			if (!iron) {
				return 4;
			}

			// Stage 6, settlement: a farm, a pen and full iron armor.
			return armor == 4 && home.farm != null && home.pen != null ? 6 : 5;
		}

		if (WOODEN_PICKAXE.satisfied().test(drifter)) {
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
			case 5 -> "iron";
			case 6 -> "settlement";
			case 7 -> "diamond";
			case 8 -> "enchanting";
			default -> "Nether";
		};
	}
}
