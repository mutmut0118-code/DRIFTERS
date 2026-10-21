package com.dinglebob.drifter.brain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.BarrelBlock;

/**
 * Its home (DESIGN.md sections 9 stage 4, 10.18 and 11): where it settled, the chests it keeps there, and what it put
 * in them. Its record of the contents is its own knowledge: updated every time it looks inside one of its chests, so
 * if someone takes things it finds out when it next opens the chest (and plans again).
 * Saved with the brain.
 */
public final class Home {
	/** Things in its chests count as owned for planning while it's within this distance of home. */
	public static final double STORAGE_RANGE = 96.0;

	public final BlockPos center;
	public final ResourceLocation dimension;
	public final long settledAt;
	/** Its starter hut (section 11 tier 3): which side the door is on (null until it decides), and whether it's finished. */
	@Nullable
	public net.minecraft.core.Direction hutDoor;
	public boolean hutBuilt;
	/** Its hut's half-size: 3 (7x7) or 4 (9x9), or 2 for a hut built before huts got bigger (kept as built). */
	public int hutHalf = com.dinglebob.drifter.building.StarterHut.HALF;
	/**
	 * Its hut's look (user's request, 2026-10-17: huts that vary in size and look, cobblestone or wooden floors, log
	 * corners, glass windows): picked once for a new home; a hut built before keeps the plain look it was built with.
	 */
	public int hutWallHeight = com.dinglebob.drifter.building.StarterHut.WALL_HEIGHT;
	public boolean hutWindows;
	public boolean hutLogCorners;
	/** "stone", "wood", or "any" (whatever it has, the old way). */
	public String hutFloor = "any";
	/**
	 * Its farm's size round the water (user's request, 2026-10-17: farms that vary like a real Drifter's): 2 to 4 (5x5 to
	 * 9x9); 4 for a farm made before farms varied.
	 */
	public int farmRadius = com.dinglebob.drifter.brain.activity.FarmActivity.RADIUS;

	/** A look for a hut not yet started: 7x7 most often, sometimes 9x9; 2 or 3 high; stone or wood floor; log corners or not; windows. */
	public void pickHutStyle(java.util.Random random) {
		this.hutHalf = random.nextFloat() < 0.3F ? 4 : 3;
		this.hutWallHeight = random.nextFloat() < 0.4F ? 3 : 2;
		this.hutWindows = true;
		this.hutLogCorners = random.nextBoolean();
		this.hutFloor = random.nextBoolean() ? "stone" : "wood";
		this.farmRadius = 2 + random.nextInt(3);
	}

	/** The plain look of the first huts (what a hut built before the variety keeps). */
	public void plainHutStyle() {
		this.hutWallHeight = com.dinglebob.drifter.building.StarterHut.WALL_HEIGHT;
		this.hutWindows = false;
		this.hutLogCorners = false;
		this.hutFloor = "any";
	}
	/** Its animal pen (section 9 stage 6): the middle, at the level it stands on; when it last bred animals there. */
	@Nullable
	public BlockPos pen;
	public long penBredAt;
	/** Its wheat farm (section 9 stage 6): the middle, at the level it stands on (the water is just below). */
	@Nullable
	public BlockPos farm;
	/**
	 * Its house (section 11 shelter tier 4; milestone 8 step 8), built near the hut once it's settled: the middle at floor
	 * level, the door side, its half-size and wall height, its style (wood, stone or mixed), and whether it's finished.
	 */
	@Nullable
	public BlockPos houseCenter;
	@Nullable
	public net.minecraft.core.Direction houseDoor;
	public int houseHalf;
	public int houseWall;
	public String houseStyle = "";
	public boolean houseBuilt;

	/**
	 * Its house from a plan (house plans, 2026-10-16): which plan, where its corner is (at the level of its lowest layer),
	 * how it's turned, the woods it's building it in (plan wood → its wood) and deepslate for cobblestone, and whether it's
	 * finished. Fixed when it picks the plan, so the house doesn't change halfway.
	 */
	@Nullable
	public String planId;
	@Nullable
	public BlockPos planOrigin;
	public String planRotation = "NONE";
	public final java.util.Map<String, String> planWoods = new java.util.HashMap<>();
	/**
	 * Trips for a house material with nothing of the house placed since, by item; materials it has given up on for its
	 * house; woods it couldn't get any more of. Not saved: after a restart it tries again.
	 */
	public final java.util.Map<net.minecraft.world.item.Item, Integer> houseTrips = new java.util.HashMap<>();
	public final java.util.Set<net.minecraft.world.item.Item> houseGivenUp = new java.util.HashSet<>();
	public final java.util.Set<String> houseWoodsShort = new java.util.HashSet<>();
	public boolean planDeepslate;
	public boolean planDone;
	/** Its hut (and house) are recorded as Drifter-built (homes from before that was recorded get it once). */
	public boolean builtRecorded;
	/** It just finished (or gave up on) its hut: not back to it before this (not saved). */
	public long hutRestUntil;
	/** Its bed in the planned house, once it has moved in. */
	@Nullable
	public BlockPos planBed;

	/** When it last looked for a spot for a pen and found none (not saved): it looks again later. */
	public long penFailedAt = Long.MIN_VALUE / 2;

	/** Farmland it left on its farm last time it worked it (-1: not yet looked), for spotting trampling (milestone 20). */
	public int farmlandSeen = -1;
	/** The last one it saw standing on its farm (not saved): who it blames for trampling. */
	public java.util.UUID farmVisitor;
	public String farmVisitorName = "";
	public String farmVisitorKind = "";
	public boolean farmVisitorPerson;
	public long farmVisitorAt = Long.MIN_VALUE / 2;
	/** When it last looked for a farm by the water and found nowhere (not saved): it looks again later. */
	public long farmFailedAt = Long.MIN_VALUE / 2;

	/** The player it last saw (or heard) open one of its chests, and when (not saved): suspected if valuables go missing. */
	@Nullable
	public java.util.UUID lastOpenedBy;
	public String lastOpenedByName = "";
	public long lastOpenedAt = Long.MIN_VALUE / 2;
	/** Whether it saw them at it with its own eyes (not only heard the lid), and which chest (not saved). */
	public boolean lastOpenedSeen;
	@Nullable
	public BlockPos lastOpenedChest;
	/** Spots where putting a chest down failed (not saved): it tries elsewhere next time. */
	public final java.util.Set<BlockPos> badChestSpots = new java.util.HashSet<>();
	/** Bases an outpost Bandit gang has found to raid (user's decision 2026-10-17; social/BanditGoals). */
	public final java.util.List<BlockPos> marks = new java.util.ArrayList<>();
	/** A chest it saw someone open that it means to check, and since when (not saved). */
	@Nullable
	public BlockPos checkChest;
	public long checkChestSince = Long.MIN_VALUE / 2;

	/**
	 * Valuable things it found gone from its chests, and when it found out (user's idea, 2026-10-16): anyone it then sees
	 * wearing or holding one of them is taken for the thief, whether they took it or not. Kept for {@link #MISSING_DAYS}.
	 */
	public final Map<Item, Long> missing = new LinkedHashMap<>();
	public static final int MISSING_DAYS = 3;

	public void forgetOldMissing(long now) {
		this.missing.values().removeIf(at -> now - at > 24000L * MISSING_DAYS);
	}

	/**
	 * One of its things (section 14: "its blocks"): its hut, house, chests, farm, pen, enchanting table and shelves. A
	 * player breaking these is noticed.
	 */
	public boolean owns(BlockPos pos) {
		if (this.inOwnBuilding(pos, 0) || this.chests.containsKey(pos)) {
			return true;
		}

		if (this.farm != null && Math.abs(pos.getX() - this.farm.getX()) <= com.dinglebob.drifter.brain.activity.FarmActivity.RADIUS
			&& Math.abs(pos.getZ() - this.farm.getZ()) <= com.dinglebob.drifter.brain.activity.FarmActivity.RADIUS && Math.abs(pos.getY() - this.farm.getY()) <= 1) {
			return true;
		}

		if (this.pen != null && Math.abs(pos.getX() - this.pen.getX()) <= 3 && Math.abs(pos.getZ() - this.pen.getZ()) <= 3
			&& Math.abs(pos.getY() - this.pen.getY()) <= 2) {
			return true;
		}

		return this.enchantingTable != null && this.enchantingTable.closerThan(pos, 3.0);
	}

	/** Visits to its hut or house that ended with blocks it couldn't place (not saved), by building ("hut", "house"). */
	public final java.util.Map<String, Integer> gapVisits = new java.util.HashMap<>();

	/** Blocks of this building ("hut", "house") it lives with missing: 4, or 12 once it has failed to fill them 3 times. */
	public int allowedGaps(String building) {
		return this.gapVisits.getOrDefault(building, 0) >= 3 ? 12 : 4;
	}

	/** When it last couldn't get at its chests to put things away (not saved): it tries again a little later. */
	public long storeFailedAt = Long.MIN_VALUE / 2;

	/** When it last looked for a spot for its house and found none (not saved): it looks again later. */
	public long houseSiteFailedAt = Long.MIN_VALUE / 2;

	/** When placing a bed last failed (not saved): the hut and house goals leave it for a while instead of retrying. */
	public long bedFailedAt = Long.MIN_VALUE / 2;

	/** It tried to place its bed lately and couldn't. */
	public boolean bedFailedRecently(long now) {
		return now - this.bedFailedAt < 20L * 60 * 5;
	}

	/** Inside or part of its hut or its house (walls, roof, floor, with {@code margin} blocks round them). */
	public boolean inOwnBuilding(BlockPos pos, int margin) {
		if (this.hutBuilt || this.hutDoor != null) {
			int half = this.hutHalf + margin;
			if (Math.abs(pos.getX() - this.center.getX()) <= half && Math.abs(pos.getZ() - this.center.getZ()) <= half
				&& pos.getY() >= this.center.getY() - 1 && pos.getY() <= this.center.getY() + this.hutWallHeight) {
				return true;
			}
		}

		if (this.houseCenter != null) {
			int half = this.houseHalf + margin;
			return Math.abs(pos.getX() - this.houseCenter.getX()) <= half && Math.abs(pos.getZ() - this.houseCenter.getZ()) <= half
				&& pos.getY() >= this.houseCenter.getY() - 1 && pos.getY() <= this.houseCenter.getY() + this.houseWall;
		}

		return false;
	}

	/** The house's blueprint, or null if it hasn't picked a spot. */
	@Nullable
	public com.dinglebob.drifter.building.StarterHut house() {
		return this.houseCenter == null || this.houseDoor == null ? null
			: new com.dinglebob.drifter.building.StarterHut(this.houseCenter, this.houseDoor, this.houseHalf, this.houseWall, true);
	}

	/** Its enchanting table (section 10.13; milestone 8 step 7), outside the hut with room round it for bookshelves. */
	@Nullable
	public BlockPos enchantingTable;
	/** Its group's banner, standing by the hut door (section 21 team wars), if it hung one. */
	@Nullable
	public BlockPos banner;

	/** Its enchanting table, if it's still there. */
	@Nullable
	public BlockPos enchantingTable(net.minecraft.world.level.Level level) {
		return this.enchantingTable != null && level.getBlockState(this.enchantingTable).is(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE)
			? this.enchantingTable : null;
	}

	/** Its Nether portal by home (section 19), from the first block it puts down, so a half-built frame is finished later. */
	@Nullable
	public PortalFrame portal;

	/** A village house it moved into instead of building a hut (user's request, session 2): the head of its bed there. */
	@Nullable
	public BlockPos houseBed;
	/** It lives at a pillager outpost its gang took (user's idea, 2026-10-16): no hut or house of its own there. */
	public boolean outpost;

	/** It lives somewhere it didn't build (a village house, an outpost): it builds no hut or house of its own. */
	public boolean borrowed() {
		return this.houseBed != null || this.outpost;
	}

	/** Whichever bed it has now (its house's, else its hut's), if it's still there: where it comes back after dying. */
	@Nullable
	public BlockPos bedOrNull(net.minecraft.world.level.Level level) {
		BlockPos bed = this.houseBed(level);
		return bed != null ? bed : this.hutBuilt ? com.dinglebob.drifter.building.StarterHut.of(this).bed(level) : null;
	}

	/** Its bed in the village house, or in the house it built from a plan, if it's still there. */
	@Nullable
	public BlockPos houseBed(net.minecraft.world.level.Level level) {
		return this.houseBed != null && level.getBlockState(this.houseBed).getBlock() instanceof net.minecraft.world.level.block.BedBlock
			? this.houseBed : this.planBed(level);
	}

	/** Its bed in the house it built from a plan, once it has moved in (the hut is a shed from then on). */
	@Nullable
	public BlockPos planBed(net.minecraft.world.level.Level level) {
		return this.planDone && this.planBed != null && level.getBlockState(this.planBed).getBlock() instanceof net.minecraft.world.level.block.BedBlock
			? this.planBed : null;
	}
	/** Its mine (section 10.3): the bottom of the staircase it dug from home, which way it went, and trips made from it. */
	@Nullable
	public BlockPos mineBottom;
	@Nullable
	public net.minecraft.core.Direction mineDirection;
	public int mineTrips;

	/** Its chests, in the order it placed them, with what it last saw in each. */
	private final Map<BlockPos, Map<Item, Integer>> chests = new LinkedHashMap<>();

	public Home(BlockPos center, ResourceLocation dimension, long settledAt) {
		this.center = center.immutable();
		this.dimension = dimension;
		this.settledAt = settledAt;
		// (Seeded by where it is: the same home always gets the same look.)
		this.pickHutStyle(new java.util.Random(center.asLong() * 31L + 17L));
	}

	public List<BlockPos> chests() {
		return new ArrayList<>(this.chests.keySet());
	}

	public boolean hasChest() {
		return !this.chests.isEmpty();
	}

	public void addChest(BlockPos pos) {
		this.chests.putIfAbsent(pos.immutable(), new HashMap<>());
	}

	public void removeChest(BlockPos pos) {
		this.chests.remove(pos);
	}

	public boolean isChest(BlockPos pos) {
		return this.chests.containsKey(pos);
	}

	/** It looked inside one of its chests: this is what's in it now. */
	public void record(BlockPos pos, Container container) {
		Map<Item, Integer> contents = this.chests.get(pos);
		if (contents == null) {
			return;
		}

		contents.clear();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty()) {
				contents.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
	}

	/** What it believes is stored, all chests together. */
	/** What it last saw in this chest (empty if it never looked). */
	public Map<Item, Integer> contents(BlockPos chest) {
		return this.chests.getOrDefault(chest, Map.of());
	}

	public Map<Item, Integer> stored() {
		Map<Item, Integer> total = new HashMap<>();
		this.chests.values().forEach(contents -> contents.forEach((item, count) -> total.merge(item, count, Integer::sum)));
		return total;
	}

	/** Changes whenever what it believes its chests hold changes (for reusing plans). */
	public int storedSignature() {
		return this.chests.hashCode();
	}

	public int stored(Item item) {
		return this.stored().getOrDefault(item, 0);
	}

	/** The chest it believes holds the most of this item, or null. */
	@Nullable
	public BlockPos chestWith(Item item) {
		BlockPos best = null;
		int bestCount = 0;
		for (Map.Entry<BlockPos, Map<Item, Integer>> chest : this.chests.entrySet()) {
			int count = chest.getValue().getOrDefault(item, 0);
			if (count > bestCount) {
				best = chest.getKey();
				bestCount = count;
			}
		}

		return best;
	}

	/** Roughly how full its chests are (0 to 1), by stacks of 64 against 27 slots each. */
	public double fullness() {
		if (this.chests.isEmpty()) {
			return 1.0;
		}

		int slotsUsed = 0;
		for (Map<Item, Integer> contents : this.chests.values()) {
			for (Map.Entry<Item, Integer> entry : contents.entrySet()) {
				slotsUsed += (int)Math.ceil(entry.getValue() / (double)new ItemStack(entry.getKey()).getMaxStackSize());
			}
		}

		return slotsUsed / (27.0 * this.chests.size());
	}

	/** In this dimension and close enough that its storage counts for planning. */
	public boolean near(DrifterEntity drifter, double range) {
		return drifter.level().dimension().location().equals(this.dimension) && drifter.blockPosition().distSqr(this.center) <= range * range;
	}

	/** Its chests that are really still chests (it checks the ones it can see; others it trusts). */
	public void forgetMissingChests(DrifterEntity drifter) {
		this.chests.keySet().removeIf(pos -> drifter.getSenses().terrain().isKnown(pos)
			&& !(drifter.level().getBlockState(pos).getBlock() instanceof ChestBlock || drifter.level().getBlockState(pos).getBlock() instanceof BarrelBlock));
	}

	public static boolean isStorageBlock(net.minecraft.world.level.block.state.BlockState state) {
		return state.is(Blocks.CHEST) || state.is(Blocks.BARREL) || state.is(Blocks.TRAPPED_CHEST);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.put("Center", NbtUtils.writeBlockPos(this.center));
		tag.putString("Dimension", this.dimension.toString());
		tag.putLong("SettledAt", this.settledAt);
		if (this.hutDoor != null) {
			tag.putString("HutDoor", this.hutDoor.getName());
		}

		tag.putBoolean("HutBuilt", this.hutBuilt);
		tag.putBoolean("BuiltRecorded", this.builtRecorded);
		tag.putInt("HutHalf", this.hutHalf);
		CompoundTag style = new CompoundTag();
		style.putInt("WallHeight", this.hutWallHeight);
		style.putBoolean("Windows", this.hutWindows);
		style.putBoolean("LogCorners", this.hutLogCorners);
		style.putString("Floor", this.hutFloor);
		tag.put("HutStyle", style);
		if (this.portal != null) {
			tag.put("Portal", this.portal.save());
		}

		if (this.houseBed != null) {
			tag.put("HouseBed", NbtUtils.writeBlockPos(this.houseBed));
		}

		if (this.outpost) {
			tag.putBoolean("Outpost", true);
			tag.putLongArray("Marks", this.marks.stream().mapToLong(BlockPos::asLong).toArray());
		}

		if (this.banner != null) {
			tag.put("Banner", NbtUtils.writeBlockPos(this.banner));
		}

		if (!this.missing.isEmpty()) {
			CompoundTag missing = new CompoundTag();
			this.missing.forEach((item, at) -> missing.putLong(BuiltInRegistries.ITEM.getKey(item).toString(), at));
			tag.put("Missing", missing);
		}

		if (this.farm != null) {
			tag.put("Farm", NbtUtils.writeBlockPos(this.farm));
			tag.putInt("FarmRadius", this.farmRadius);
			tag.putInt("FarmlandSeen", this.farmlandSeen);
		}

		if (this.pen != null) {
			tag.put("Pen", NbtUtils.writeBlockPos(this.pen));
		}

		tag.putLong("PenBredAt", this.penBredAt);
		if (this.enchantingTable != null) {
			tag.put("EnchantingTable", NbtUtils.writeBlockPos(this.enchantingTable));
		}

		if (this.houseCenter != null && this.houseDoor != null) {
			tag.put("HouseCenter", NbtUtils.writeBlockPos(this.houseCenter));
			tag.putString("HouseDoor", this.houseDoor.getName());
			tag.putInt("HouseHalf", this.houseHalf);
			tag.putInt("HouseWall", this.houseWall);
			tag.putString("HouseStyle", this.houseStyle);
			tag.putBoolean("HouseBuilt", this.houseBuilt);
		}

		if (this.planId != null && this.planOrigin != null) {
			CompoundTag plan = new CompoundTag();
			plan.putString("Id", this.planId);
			plan.put("Origin", NbtUtils.writeBlockPos(this.planOrigin));
			plan.putString("Rotation", this.planRotation);
			CompoundTag woods = new CompoundTag();
			this.planWoods.forEach(woods::putString);
			plan.put("Woods", woods);
			plan.putBoolean("Deepslate", this.planDeepslate);
			plan.putBoolean("Done", this.planDone);
			if (this.planBed != null) {
				plan.put("Bed", NbtUtils.writeBlockPos(this.planBed));
			}

			tag.put("Plan", plan);
		}

		if (this.mineBottom != null && this.mineDirection != null) {
			tag.put("MineBottom", NbtUtils.writeBlockPos(this.mineBottom));
			tag.putString("MineDirection", this.mineDirection.getName());
		}

		tag.putInt("MineTrips", this.mineTrips);
		ListTag chests = new ListTag();
		this.chests.forEach((pos, contents) -> {
			CompoundTag chest = new CompoundTag();
			chest.put("Pos", NbtUtils.writeBlockPos(pos));
			CompoundTag items = new CompoundTag();
			contents.forEach((item, count) -> items.putInt(BuiltInRegistries.ITEM.getKey(item).toString(), count));
			chest.put("Items", items);
			chests.add(chest);
		});
		tag.put("Chests", chests);
		return tag;
	}

	@Nullable
	public static Home load(CompoundTag tag) {
		BlockPos center = NbtUtils.readBlockPos(tag, "Center").orElse(null);
		ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
		if (center == null || dimension == null) {
			return null;
		}

		Home home = new Home(center, dimension, tag.getLong("SettledAt"));
		home.hutDoor = net.minecraft.core.Direction.byName(tag.getString("HutDoor"));
		home.hutBuilt = tag.getBoolean("HutBuilt");
		home.builtRecorded = tag.getBoolean("BuiltRecorded");
		// Saved before huts got bigger: a hut it has started keeps its size; one it hasn't started is the new size.
		if (tag.contains("HutStyle", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
			CompoundTag style = tag.getCompound("HutStyle");
			home.hutHalf = tag.getInt("HutHalf");
			home.hutWallHeight = style.getInt("WallHeight");
			home.hutWindows = style.getBoolean("Windows");
			home.hutLogCorners = style.getBoolean("LogCorners");
			home.hutFloor = style.getString("Floor");
		} else if (home.hutBuilt || home.hutDoor != null) {
			// Saved before huts varied: a hut it has started keeps its size and plain look.
			home.hutHalf = tag.contains("HutHalf") ? tag.getInt("HutHalf") : com.dinglebob.drifter.building.StarterHut.OLD_HALF;
			home.plainHutStyle();
		}
		home.portal = tag.contains("Portal", net.minecraft.nbt.Tag.TAG_COMPOUND) ? PortalFrame.load(tag.getCompound("Portal")) : null;
		home.houseBed = NbtUtils.readBlockPos(tag, "HouseBed").orElse(null);
		home.outpost = tag.getBoolean("Outpost");
		for (long mark : tag.getLongArray("Marks")) {
			home.marks.add(BlockPos.of(mark));
		}
		home.banner = NbtUtils.readBlockPos(tag, "Banner").orElse(null);
		CompoundTag missing = tag.getCompound("Missing");
		for (String key : missing.getAllKeys()) {
			ResourceLocation id = ResourceLocation.tryParse(key);
			if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
				home.missing.put(BuiltInRegistries.ITEM.get(id), missing.getLong(key));
			}
		}
		home.farm = NbtUtils.readBlockPos(tag, "Farm").orElse(null);
		home.farmlandSeen = tag.contains("FarmlandSeen") ? tag.getInt("FarmlandSeen") : -1;
		if (home.farm != null) {
			// (A farm made before farms varied is the full 9x9.)
			home.farmRadius = tag.contains("FarmRadius") ? tag.getInt("FarmRadius") : com.dinglebob.drifter.brain.activity.FarmActivity.RADIUS;
		}
		home.pen = NbtUtils.readBlockPos(tag, "Pen").orElse(null);
		home.penBredAt = tag.getLong("PenBredAt");
		home.enchantingTable = NbtUtils.readBlockPos(tag, "EnchantingTable").orElse(null);
		home.houseCenter = NbtUtils.readBlockPos(tag, "HouseCenter").orElse(null);
		home.houseDoor = net.minecraft.core.Direction.byName(tag.getString("HouseDoor"));
		home.houseHalf = tag.getInt("HouseHalf");
		home.houseWall = tag.getInt("HouseWall");
		home.houseStyle = tag.getString("HouseStyle");
		home.houseBuilt = tag.getBoolean("HouseBuilt");
		if (tag.contains("Plan", Tag.TAG_COMPOUND)) {
			CompoundTag plan = tag.getCompound("Plan");
			home.planId = plan.getString("Id");
			home.planOrigin = NbtUtils.readBlockPos(plan, "Origin").orElse(null);
			home.planRotation = plan.getString("Rotation").isEmpty() ? "NONE" : plan.getString("Rotation");
			CompoundTag woods = plan.getCompound("Woods");
			for (String key : woods.getAllKeys()) {
				home.planWoods.put(key, woods.getString(key));
			}

			home.planDeepslate = plan.getBoolean("Deepslate");
			home.planDone = plan.getBoolean("Done");
			home.planBed = NbtUtils.readBlockPos(plan, "Bed").orElse(null);
		}
		home.mineBottom = NbtUtils.readBlockPos(tag, "MineBottom").orElse(null);
		home.mineDirection = net.minecraft.core.Direction.byName(tag.getString("MineDirection"));
		home.mineTrips = tag.getInt("MineTrips");
		ListTag chests = tag.getList("Chests", Tag.TAG_COMPOUND);
		for (int i = 0; i < chests.size(); i++) {
			CompoundTag chest = chests.getCompound(i);
			BlockPos pos = NbtUtils.readBlockPos(chest, "Pos").orElse(null);
			if (pos == null) {
				continue;
			}

			Map<Item, Integer> contents = new HashMap<>();
			CompoundTag items = chest.getCompound("Items");
			for (String key : items.getAllKeys()) {
				ResourceLocation id = ResourceLocation.tryParse(key);
				if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
					contents.put(BuiltInRegistries.ITEM.get(id), items.getInt(key));
				}
			}

			home.chests.put(pos, contents);
		}

		return home;
	}
}
