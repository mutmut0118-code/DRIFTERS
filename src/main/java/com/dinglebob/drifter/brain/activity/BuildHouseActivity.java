package com.dinglebob.drifter.brain.activity;

import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.building.StarterHut;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.hands.Tools;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * The house (DESIGN.md section 11, shelter tier 4; milestone 8 step 8): a 7x7 box near the hut, walls three high with
 * window gaps, a flat roof, a door facing home, built with the same player's order as the hut (walls from inside, the
 * third layer and the roof from a two-block pillar in the middle). Its style comes from its archetype: wood (planks with
 * log corner posts) for Builders and Homebodies, stone for Miners, Bandits and PvP Sweats, and whatever it has for the rest.
 * Once it's finished it moves its bed in (digs it out of the hut and puts it in the house) and sleeps there.
 * TODO(milestone 8 later): pitched stair roofs, porches, glass by palette, more rooms (section 11 "Blueprint grammar").
 */
public class BuildHouseActivity extends BuildHutActivity {
	/** A 7x7 house. */
	public static final int HALF = 3;
	/** How far from the hut it looks for a flat spot. */
	private static final int[] DISTANCES = {9, 10, 11, 12, 14, 16, 18, 20, 24};

	public static final String WOOD = "wood";
	public static final String STONE = "stone";
	public static final String MIXED = "mixed";

	private static final List<Item> PLANKS = List.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS,
		Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS, Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS);
	private static final List<Item> STONES = List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE);

	public BuildHouseActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "build a house";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 10;
	}

	/** The style its archetype builds in (section 11 "Styles by archetype"). */
	public static String styleFor(DrifterEntity drifter) {
		Archetype archetype = drifter.getPersonality().archetype();
		return switch (archetype) {
			case BUILDER, HOMEBODY -> WOOD;
			case MINER, BANDIT, PVP_SWEAT -> STONE;
			default -> MIXED;
		};
	}

	/** Archetypes that never build a house: Nomads keep moving, Hermits stay hidden, Speedrunners skip settling. */
	public static boolean buildsHouses(DrifterEntity drifter) {
		Archetype archetype = drifter.getPersonality().archetype();
		return archetype != Archetype.NOMAD && archetype != Archetype.HERMIT && archetype != Archetype.SPEEDRUNNER;
	}

	@Override
	@Nullable
	protected StarterHut prepare(Level level, Home home) {
		if (home.houseCenter == null) {
			BlockPos site = site(this.drifter, home);
			if (site == null) {
				home.houseSiteFailedAt = level.getGameTime();
				return null;
			}

			home.houseCenter = site;
			home.houseDoor = doorToward(level, site, home.center);
			home.houseHalf = HALF;
			home.houseWall = 3;
			home.houseStyle = styleFor(this.drifter);
			this.think("building a " + home.houseStyle + " house at " + site.toShortString() + ", door on the " + home.houseDoor.getName() + " side");
		}

		return home.house();
	}

	@Override
	protected StarterHut blueprint(Home home) {
		return home.house();
	}

	private String style() {
		Home home = this.drifter.getDrifterBrain().home();
		return home == null || home.houseStyle.isEmpty() ? styleFor(this.drifter) : home.houseStyle;
	}

	/** Its favorite wood or stone (habit fingerprint, section 11 "Palette"), as an item, or null. */
	@Nullable
	private Item favorite() {
		var habits = this.drifter.getPersonality().habits();
		String id = switch (this.style()) {
			case WOOD -> habits.favoriteWood + "_planks";
			case STONE -> habits.favoriteStone;
			default -> null;
		};
		if (id == null) {
			return null;
		}

		Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(id));
		return item == Items.AIR ? null : item;
	}

	@Override
	protected List<Item> materialItems() {
		Item favorite = this.favorite();
		if (favorite != null && WOOD.equals(this.style())) {
			// Its favorite wood first (the planner takes the first of equals), the rest if that's what's about.
			List<Item> planks = new java.util.ArrayList<>(PLANKS);
			planks.remove(favorite);
			planks.add(0, favorite);
			return planks;
		}

		return switch (this.style()) {
			case WOOD -> PLANKS;
			case STONE -> STONES;
			default -> List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS);
		};
	}

	private Predicate<ItemStack> wallBlock() {
		return switch (this.style()) {
			case WOOD -> stack -> stack.is(ItemTags.PLANKS);
			case STONE -> stack -> stack.is(Items.COBBLESTONE) || stack.is(Items.COBBLED_DEEPSLATE) || stack.is(Items.STONE_BRICKS)
				|| stack.is(Items.STONE);
			// Whatever it has but dirt: a house isn't a mud hut.
			default -> stack -> Tools.BUILDING_BLOCK.test(stack) && !stack.is(Items.DIRT) && !stack.is(Items.COARSE_DIRT);
		};
	}

	@Override
	protected int carriedMaterial() {
		return this.drifter.getInventory().count(this.wallBlock());
	}

	/** Log corner posts for a wooden house (when it has logs), the wall block everywhere else. */
	@Override
	protected Predicate<ItemStack> materialFor(BlockPos cell, StarterHut house) {
		boolean corner = Math.abs(cell.getX() - house.center.getX()) == house.half && Math.abs(cell.getZ() - house.center.getZ()) == house.half
			&& cell.getY() < house.center.getY() + house.wallHeight;
		Predicate<ItemStack> logs = stack -> stack.is(ItemTags.LOGS);
		if (corner && WOOD.equals(this.style()) && this.drifter.getInventory().count(logs) > 0) {
			return logs;
		}

		// Its favorite block while it has some; a mix once it runs out (the patchy look of real builds).
		Item favorite = this.favorite();
		if (favorite != null && this.drifter.getInventory().count(stack -> stack.is(favorite)) > 0) {
			return stack -> stack.is(favorite);
		}

		return this.wallBlock();
	}

	@Override
	protected void finished(Home home) {
		home.houseBuilt = true;
		this.think("finished the house");
	}

	@Override
	protected String what() {
		return "house";
	}

	/** Glass in the windows if it has some, a torch inside, and its bed moved over from the hut. */
	@Override
	@Nullable
	protected Status furnish(Level level, StarterHut house, boolean inPosition) {
		Predicate<ItemStack> glass = stack -> stack.is(Items.GLASS) || stack.is(Items.GLASS_PANE);
		for (BlockPos window : house.windowCells()) {
			if (this.drifter.getInventory().count(glass) > 0 && level.getBlockState(window).isAir() && !this.failed.contains(window)) {
				if (!inPosition) {
					return this.walkInside(house);
				}

				this.placing = window;
				return this.begin(new PlaceBlockTask(this.drifter, window, glass, "glass in the window"));
			}
		}

		// The bed: a carried one goes in; otherwise the one in the hut comes over (a player breaks it and carries it).
		if (house.bed(level) == null) {
			Home home = this.drifter.getDrifterBrain().home();
			StarterHut.BedSpot spot = house.bedSpot(level);
			if (this.drifter.getInventory().count(stack -> stack.is(ItemTags.BEDS)) > 0) {
				if (spot != null && !this.failed.contains(spot.foot())) {
					if (!inPosition) {
						return this.walkInside(house);
					}

					Status aside = this.stepAside(house, spot);
					if (aside != null) {
						return aside;
					}

					this.placing = spot.foot();
					return this.begin(new PlaceBlockTask(this.drifter, spot.foot(), stack -> stack.is(ItemTags.BEDS), "a bed").facing(spot.facing()));
				}
			} else if (home != null && home.hutBuilt && spot != null && !this.bedFetched) {
				BlockPos hutBed = StarterHut.of(home).bed(level);
				if (hutBed != null) {
					this.bedFetched = true;
					this.think("moving its bed from the hut into the house");
					return this.begin(new SequenceTask(this.drifter, "fetch the bed from the hut", List.of(
						new com.dinglebob.drifter.executor.task.NavigateTask(this.drifter, net.minecraft.world.phys.Vec3.atBottomCenterOf(home.center), 1.0,
							"go into the hut"),
						dig(hutBed), new CollectItemsTask(this.drifter, 3.0))));
				}
			}
		}

		BlockPos torch = house.torchSpot();
		if (this.drifter.getInventory().count(stack -> stack.is(Items.TORCH)) > 0 && level.getBlockState(torch).isAir() && !this.failed.contains(torch)) {
			if (!inPosition) {
				return this.walkInside(house);
			}

			this.placing = torch;
			return this.begin(new PlaceBlockTask(this.drifter, torch, stack -> stack.is(Items.TORCH), "a torch").preferSupport(house.door.getOpposite()));
		}

		return null;
	}

	private boolean bedFetched;

	/** The door side facing home (the way it'll walk in and out), if there's ground outside it; otherwise the best side. */
	private static Direction doorToward(Level level, BlockPos site, BlockPos home) {
		Direction toward = Direction.getNearest(home.getX() - site.getX(), 0, home.getZ() - site.getZ());
		if (KnownBlocks.isStandable(level, site.relative(toward, HALF + 1))) {
			return toward;
		}

		return StarterHut.chooseDoor(level, site, HALF);
	}

	/**
	 * A spot for the house: flat ground it has seen, 9x9 (the house and a block round it), clear of the hut, the farm,
	 * the pen and the enchanting table, as close to the hut as it can.
	 */
	@Nullable
	public static BlockPos site(DrifterEntity drifter, Home home) {
		Level level = drifter.level();
		for (int distance : DISTANCES) {
			for (int turn = 0; turn < 16; turn++) {
				double angle = Math.PI / 8.0 * turn;
				BlockPos around = home.center.offset((int)Math.round(Math.cos(angle) * distance), 0, (int)Math.round(Math.sin(angle) * distance));
				for (int dy = -2; dy <= 2; dy++) {
					BlockPos spot = around.above(dy);
					if (clear(drifter, level, home, spot)) {
						return spot.immutable();
					}
				}
			}
		}

		return null;
	}

	/** Cells of the 9x9 it may not have seen yet. */
	private static final int MAX_UNSEEN = 8;

	private static boolean clear(DrifterEntity drifter, Level level, Home home, BlockPos spot) {
		// Ground near the floor's height nearly everywhere (it fills dips and digs out bumps as it builds, as for the hut;
		// found in testing: a perfectly flat 9x9 is rare on hilly ground, and it never found a spot), all of it seen.
		if (!KnownBlocks.isStandable(level, spot)) {
			return false;
		}

		// A few cells it hasn't seen (behind a tree, over a rise) don't rule a spot out: it sees them as it builds (found in
		// testing: a Builder on hilly ground had "nowhere to build" all ten days).
		int reach = HALF + 1;
		int good = 0;
		int cells = 0;
		int unseen = 0;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				cells++;
				BlockPos cell = spot.offset(dx, 0, dz);
				if (!drifter.getSenses().terrain().isKnown(cell.below()) && !drifter.getSenses().terrain().isKnown(cell)) {
					if (++unseen > MAX_UNSEEN) {
						return false;
					}

					continue;
				}

				for (int dy : new int[]{0, 1, -1, 2}) {
					if (KnownBlocks.isStandable(level, cell.above(dy)) && level.getFluidState(cell.above(dy).below()).isEmpty()) {
						good++;
						break;
					}
				}
			}
		}

		if (good < (cells - unseen) * 0.85) {
			return false;
		}

		return !overlaps(spot, home.center, StarterHut.HALF + 1) && (home.farm == null || !overlaps(spot, home.farm, FarmActivity.RADIUS + 1))
			&& (home.pen == null || !overlaps(spot, home.pen, 4)) && (home.enchantingTable == null || !overlaps(spot, home.enchantingTable, 3));
	}

	/** The house's footprint (with a block round it) would reach into a square of this half-size round {@code other}. */
	private static boolean overlaps(BlockPos spot, BlockPos other, int otherHalf) {
		int gap = HALF + 1 + otherHalf;
		return Math.abs(spot.getX() - other.getX()) <= gap && Math.abs(spot.getZ() - other.getZ()) <= gap;
	}
}
