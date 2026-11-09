package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;

/**
 * A town (docs/CIVILIZATION.md part 1; the layout on the town plan the user agreed, 2026-10-21): its name, its group (its
 * people), where its plaza is, when it was founded, and its plots (a house each, its front to a road), its store's chests,
 * its well, fields and pen.
 */
public final class Town {
	/** One household's plot: where its house stands (corner, size once turned, turn, front), which plan, and whose it is. */
	public static final class Plot {
		public final BlockPos origin;
		public final int sizeX;
		public final int sizeZ;
		public final String planId;
		public final Rotation rotation;
		public final Direction front;
		@Nullable
		public UUID owner;
		/** Its house is up (a town founded by its people builds them a block at a time). */
		public boolean built;
		/** Others who live in its house too, a room each (a house with more than one room takes more than one household). */
		public final List<UUID> residents = new ArrayList<>();
		/** How many households its house takes (worked out once it's up; 0 if not yet). */
		public int room;

		public Plot(BlockPos origin, int sizeX, int sizeZ, String planId, Rotation rotation, Direction front, @Nullable UUID owner) {
			this.origin = origin.immutable();
			this.sizeX = sizeX;
			this.sizeZ = sizeZ;
			this.planId = planId;
			this.rotation = rotation;
			this.front = front;
			this.owner = owner;
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("Origin", this.origin.asLong());
			tag.putInt("SizeX", this.sizeX);
			tag.putInt("SizeZ", this.sizeZ);
			tag.putString("Plan", this.planId);
			tag.putString("Rotation", this.rotation.name());
			tag.putString("Front", this.front.getName());
			if (this.owner != null) {
				tag.putUUID("Owner", this.owner);
			}

			tag.putBoolean("Built", this.built);
			ListTag residents = new ListTag();
			for (UUID resident : this.residents) {
				CompoundTag one = new CompoundTag();
				one.putUUID("Id", resident);
				residents.add(one);
			}

			tag.put("Residents", residents);
			tag.putInt("Room", this.room);
			return tag;
		}

		static Plot load(CompoundTag tag) {
			Direction front = Direction.byName(tag.getString("Front"));
			Plot plot = new Plot(BlockPos.of(tag.getLong("Origin")), tag.getInt("SizeX"), tag.getInt("SizeZ"), tag.getString("Plan"),
				Rotation.valueOf(tag.getString("Rotation")), front == null ? Direction.SOUTH : front, tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
			// (Before 0.22.8 only made-ready towns had plots, every house up.)
			plot.built = !tag.contains("Built") || tag.getBoolean("Built");
			for (Tag one : tag.getList("Residents", Tag.TAG_COMPOUND)) {
				plot.residents.add(((CompoundTag)one).getUUID("Id"));
			}

			plot.room = tag.getInt("Room");
			return plot;
		}
	}

	public final UUID id;
	public String name;
	/** Someone it lost before it was found (a town's past: milestone 23 A5): their name, for its people's talk. */
	@Nullable
	public String pastDead;
	@Nullable
	public UUID group;
	public final String dimension;
	public final BlockPos center;
	public final long foundedDay;
	/** Made at once for testing (`/drifterciv town spawn`), not founded by its people. */
	public final boolean spawned;
	public final List<Plot> plots = new ArrayList<>();
	/** Side lanes off its main roads (a town founded by its people lays them out as it grows). */
	public final List<TownRoads.Road> lanes = new ArrayList<>();
	/** A town made ready (0.23.10 on): its shape (crossroads, main_street, winding) and its roads' columns as laid, bends and all. */
	public String shape = "";
	public long[] roadColumns = new long[0];
	/** How far out from its middle its houses reach, and more round them ({@link #land}); worked out again when its plots change. */
	private int landRadius = -1;
	private int landPlots = -1;

	/** Its land: within this of its middle (its furthest house and a margin; 48 at least). */
	public int land() {
		if (this.landPlots != this.plots.size()) {
			int far = 0;
			for (Plot plot : this.plots) {
				far = Math.max(far, (int)Math.hypot(plot.origin.getX() + plot.sizeX / 2.0 - this.center.getX(), plot.origin.getZ() + plot.sizeZ / 2.0
					- this.center.getZ()));
			}

			this.landRadius = Math.max(48, far + 24);
			this.landPlots = this.plots.size();
		}

		return this.landRadius;
	}
	public final List<BlockPos> store = new ArrayList<>();
	public final List<BlockPos> fields = new ArrayList<>();
	@Nullable
	public BlockPos well;
	/** Its gathering fire on the plaza (milestone 23 C2: where its people drift to of an evening), or null (towns made before). */
	@Nullable
	public BlockPos fire;
	@Nullable
	public BlockPos pen;
	/** Its mine (made ready since 0.23.24): the top of its stairs, the hub at their bottom, which way its corridor runs. */
	@Nullable
	public BlockPos mine;
	@Nullable
	public BlockPos mineBottom;
	@Nullable
	public Direction mineWay;
	/**
	 * Its ground kept for what isn't a house (its fields, pen, mine, stable, hives, shrine...; x0, z0, x1, z1 each): no new
	 * house goes over them (found by the user: a stable and a mine on top of homes).
	 */
	public final List<int[]> keepOff = new ArrayList<>();
	/** Where its people are building (or built) its store (step 3c), and whether it's up. */
	@Nullable
	public Plot storePlot;
	public boolean storeBuilt;
	/** Its market stall (step 4), whether it's up, who keeps it, and trades made there with players. */
	@Nullable
	public Plot stallPlot;
	public boolean stallBuilt;
	@Nullable
	public UUID keeper;
	public int trades;
	/**
	 * A founded town's plaza, built piece by piece (the well, the notice board, the lamp posts: {@link Plaza}); whether its
	 * well has its water; the day its board was last brought up to date.
	 */
	public final List<Plot> plaza = new ArrayList<>();
	public boolean wellFilled;
	public long boardDay = -1;
	/** Its Bountiful bounty board (its notices go up on it: TownBounties), and the day it last looked for one. */
	@Nullable
	public BlockPos bountyBoard;
	public long bountyLook = -1;
	/** Comings and goings (milestone 23 C3, {@link TownLife}): the day it last looked, the newest of its people and the latest
	 * to leave (and the days), the day a newcomer last came up its road. */
	public long lifeDay = -1;
	@Nullable
	public String newest;
	public long newestDay = -100;
	@Nullable
	public String lastLeft;
	public long lastLeftDay = -100;
	public long arrivedDay = -100;
	/** Its events (milestone 23 C4, {@link TownEvents}): by water (a harbour, a river: its fishing contests), the day it last
	 * called its events, its last contest's winner and the day. */
	public boolean byWater;
	public long eventDay = -1;
	@Nullable
	public String contestWinner;
	public long contestDay = -100;
	/** Debug: market day on this day too (`/drifterciv town market`). */
	public long forcedMarketDay = -100;
	/** Its culture (milestone 23 C6, {@link TownCulture}): the day it was founded (its past: before day 1 if it has one), the
	 * day of its last festival, its shrine (null: none), its character as last worked out. */
	public long bornDay;
	public long festivalDay = -1;
	@Nullable
	public BlockPos shrine;
	@Nullable
	public String character;
	/** Monuments (C6): the leader it knew last (one of them gone for good: a monument), what the next is for (its sign's
	 * lines, "|" between; null: none due), the one going up, the day the last was finished. */
	@Nullable
	public java.util.UUID knownLeader;
	@Nullable
	public String monumentFor;
	@Nullable
	public Plot monument;
	public long monumentDay = -100;
	/**
	 * Its money (step 4; the user, 2026-10-28: "Town treasury"): wages it owes each of its people for its work (points: an
	 * emerald is 6), and what it has paid out all told. The money itself is in its till ({@link #till()}), real items.
	 */
	public final java.util.Map<UUID, Double> owed = new java.util.HashMap<>();
	public double wagesPaid;
	/** Sales at its stall to its own people (and other towns' traders). */
	public int drifterTrades;
	/** Its book changed since it was last saved (not saved: the town's check saves it). */
	boolean booksChanged;

	/**
	 * Its politics (step 5; the user, 2026-10-28: "Founder, then elections"): its leader (the founder first; not the
	 * group's leader, whose home is the group's base), since when, the day of its next election and who's standing, how the
	 * last one went (name -> votes), its laws ({@link Politics}) and its tax (emeralds a household every few days), what each
	 * owes of it, and the day it was last levied.
	 */
	@Nullable
	public UUID leader;
	public long leaderSince;
	public long nextElection = -1;
	public final List<UUID> candidates = new ArrayList<>();
	public final java.util.Map<String, Integer> lastVotes = new java.util.LinkedHashMap<>();
	public final java.util.Set<String> laws = new java.util.LinkedHashSet<>();
	public int tax;
	public final java.util.Map<UUID, Integer> taxOwed = new java.util.HashMap<>();
	public long taxDay = -1;

	/** Someone who broke its law (step 5, part 2: {@link Justice}): their name, offences, the fine they owe, until when exiled. */
	public static final class Criminal {
		public final String name;
		public int offences;
		public int fine;
		public String crime = "";
		/** The day the exile ends (0: not exiled). */
		public long exiledUntil;
		/** The day being wanted ends (0: not wanted): its guards and bold people go for them on sight about the town. */
		public long wantedUntil;
		/** Killings of its people seen: all told, lately (each within a few days of the last), and the day of the last. */
		public int kills;
		public int recentKills;
		public long lastKillDay = -100;
		/** When it was last caught (game time; not saved). */
		long lastAt = Long.MIN_VALUE / 2;
		/** When it was last seen stealing (game time; not saved): the same things found gone later aren't another theft. */
		long lastSeenTheft = Long.MIN_VALUE / 2;

		Criminal(String name) {
			this.name = name;
		}
	}

	/** Players who broke its law, by id. */
	public final java.util.Map<UUID, Criminal> crimes = new java.util.HashMap<>();

	/**
	 * Its unrest (step 5, part 3: {@link Unrest}): what each of its people thinks of the leader (0 to 100), the day it last
	 * checked, the last upheaval (coup, abdicated, revolt, tax_dropped, betrayed) and its day, the leader it was about, in
	 * revolt (no tax till the vote), the tax dropped this term, and who plots against the leader.
	 */
	public final java.util.Map<UUID, Double> approval = new java.util.HashMap<>();
	public long moodDay = -1;
	public String upheaval = "";
	public long upheavalDay = -100;
	public String oldLeader = "";
	public boolean revolt;
	public boolean conceded;
	@Nullable
	public UUID plotter;
	/**
	 * Its crooks: Bandits living in it (the user, 2026-10-28: "if they are in a town they should be commiting several
	 * crimes"), who steal and pick fights now and then till it throws them out.
	 */
	public final java.util.Set<UUID> crooks = new java.util.HashSet<>();
	/** Its people who've had their trade's first stock (out of the store into their household's chest: 0.23.33). */
	public final java.util.Set<UUID> lineStocked = new java.util.HashSet<>();
	/** Its customs (C6, {@link TownTraditions}), what it's counted toward them, where its people died out there. */
	public final java.util.Set<String> traditions = new java.util.LinkedHashSet<>();
	public final java.util.Map<String, Integer> tally = new java.util.HashMap<>();
	public final List<BlockPos> deathSpots = new ArrayList<>();
	/** What it's heard of each player from the towns round it (D1, -100 to 100), and the day it last passed its own word on. */
	public final java.util.Map<UUID, Double> heard = new java.util.HashMap<>();
	/** What's been selling at its stall and shops (B4: an item's price, times this), and the day it last settled. */
	public final java.util.Map<String, Double> demand = new java.util.HashMap<>();
	public long demandDay = -1;
	public long wordDay = -1;
	/** Its notices up (D5, {@link TownNotices}), the day they were last put up, and a warning (not saved). */
	public final List<TownNotices.Notice> notices = new ArrayList<>();
	public long noticeDay = -1;
	@Nullable
	public String warning;
	/** Each of its people's line of work (milestone 23 B1: Trades). */
	public final java.util.Map<UUID, String> lines = new java.util.HashMap<>();
	/** The last crime of one of its people that was seen: who, what ("theft", "fight", "thrown_out") and the day. */
	/** A bond of one of its people with someone who wasn't loaded when it was made (a town made ready), set once they are. */
	public record Bond(UUID who, UUID other, String otherName, double trust, double familiarity) {
	}

	public final List<Bond> pendingBonds = new ArrayList<>();
	public String lastCrook = "";
	public String lastCrime = "";
	public long lastCrimeDay = -100;

	/** Its till: the first chest of its store, or null before it has one. */
	@Nullable
	public BlockPos till() {
		return (this.storeBuilt || this.spawned) && !this.store.isEmpty() ? this.store.get(0) : null;
	}

	public Town(UUID id, String name, @Nullable UUID group, String dimension, BlockPos center, long foundedDay, boolean spawned) {
		this.id = id;
		this.name = name;
		this.group = group;
		this.dimension = dimension;
		this.center = center.immutable();
		this.foundedDay = foundedDay;
		this.spawned = spawned;
	}

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putUUID("Id", this.id);
		tag.putString("Name", this.name);
		if (this.pastDead != null) {
			tag.putString("PastDead", this.pastDead);
		}

		if (this.group != null) {
			tag.putUUID("Group", this.group);
		}

		tag.putString("Dimension", this.dimension);
		tag.putLong("Center", this.center.asLong());
		tag.putLong("FoundedDay", this.foundedDay);
		tag.putBoolean("Spawned", this.spawned);
		ListTag lanes = new ListTag();
		for (TownRoads.Road lane : this.lanes) {
			CompoundTag one = new CompoundTag();
			one.putInt("X", lane.x());
			one.putInt("Z", lane.z());
			one.putString("Way", lane.way().getName());
			one.putInt("Length", lane.length());
			lanes.add(one);
		}

		tag.put("Lanes", lanes);
		if (!this.shape.isEmpty()) {
			tag.putString("Shape", this.shape);
		}

		if (this.roadColumns.length > 0) {
			tag.putLongArray("RoadColumns", this.roadColumns);
		}

		ListTag plots = new ListTag();
		this.plots.forEach(plot -> plots.add(plot.save()));
		tag.put("Plots", plots);
		tag.putLongArray("Store", this.store.stream().mapToLong(BlockPos::asLong).toArray());
		tag.putLongArray("Fields", this.fields.stream().mapToLong(BlockPos::asLong).toArray());
		if (this.well != null) {
			tag.putLong("Well", this.well.asLong());
		}

		if (this.fire != null) {
			tag.putLong("Fire", this.fire.asLong());
		}

		if (this.pen != null) {
			tag.putLong("Pen", this.pen.asLong());
		}

		if (!this.keepOff.isEmpty()) {
			ListTag kept = new ListTag();
			this.keepOff.forEach(rect -> kept.add(new net.minecraft.nbt.IntArrayTag(rect)));
			tag.put("KeepOff", kept);
		}

		if (this.mine != null && this.mineBottom != null && this.mineWay != null) {
			tag.putLong("Mine", this.mine.asLong());
			tag.putLong("MineBottom", this.mineBottom.asLong());
			tag.putString("MineWay", this.mineWay.getName());
		}

		if (this.storePlot != null) {
			tag.put("StorePlot", this.storePlot.save());
		}

		tag.putBoolean("StoreBuilt", this.storeBuilt);
		if (this.stallPlot != null) {
			tag.put("StallPlot", this.stallPlot.save());
		}

		tag.putBoolean("StallBuilt", this.stallBuilt);
		if (this.keeper != null) {
			tag.putUUID("Keeper", this.keeper);
		}

		tag.putInt("Trades", this.trades);
		ListTag plaza = new ListTag();
		this.plaza.forEach(plot -> plaza.add(plot.save()));
		tag.put("Plaza", plaza);
		tag.putBoolean("WellFilled", this.wellFilled);
		tag.putLong("BoardDay", this.boardDay);
		if (this.bountyBoard != null) {
			tag.putLong("BountyBoard", this.bountyBoard.asLong());
		}
		tag.putLong("LifeDay", this.lifeDay);
		if (this.newest != null) {
			tag.putString("Newest", this.newest);
		}

		tag.putLong("NewestDay", this.newestDay);
		if (this.lastLeft != null) {
			tag.putString("LastLeft", this.lastLeft);
		}

		tag.putLong("LastLeftDay", this.lastLeftDay);
		tag.putLong("ArrivedDay", this.arrivedDay);
		tag.putBoolean("ByWater", this.byWater);
		tag.putLong("EventDay", this.eventDay);
		if (this.contestWinner != null) {
			tag.putString("ContestWinner", this.contestWinner);
		}

		tag.putLong("ContestDay", this.contestDay);
		tag.putLong("BornDay", this.bornDay);
		tag.putLong("FestivalDay", this.festivalDay);
		if (this.shrine != null) {
			tag.putLong("Shrine", this.shrine.asLong());
		}

		if (this.character != null) {
			tag.putString("Character", this.character);
		}

		if (this.knownLeader != null) {
			tag.putUUID("KnownLeader", this.knownLeader);
		}

		if (this.monumentFor != null) {
			tag.putString("MonumentFor", this.monumentFor);
		}

		if (this.monument != null) {
			tag.put("Monument", this.monument.save());
		}

		tag.putLong("MonumentDay", this.monumentDay);
		CompoundTag owed = new CompoundTag();
		this.owed.forEach((id, points) -> owed.putDouble(id.toString(), points));
		tag.put("Owed", owed);
		tag.putDouble("WagesPaid", this.wagesPaid);
		tag.putInt("DrifterTrades", this.drifterTrades);
		if (this.leader != null) {
			tag.putUUID("Leader", this.leader);
		}

		tag.putLong("LeaderSince", this.leaderSince);
		tag.putLong("NextElection", this.nextElection);
		ListTag candidates = new ListTag();
		for (UUID one : this.candidates) {
			CompoundTag c = new CompoundTag();
			c.putUUID("Id", one);
			candidates.add(c);
		}

		tag.put("Candidates", candidates);
		CompoundTag votes = new CompoundTag();
		this.lastVotes.forEach(votes::putInt);
		tag.put("LastVotes", votes);
		ListTag laws = new ListTag();
		this.laws.forEach(law -> laws.add(net.minecraft.nbt.StringTag.valueOf(law)));
		tag.put("Laws", laws);
		tag.putInt("Tax", this.tax);
		CompoundTag owes = new CompoundTag();
		this.taxOwed.forEach((id, n) -> owes.putInt(id.toString(), n));
		tag.put("TaxOwed", owes);
		tag.putLong("TaxDay", this.taxDay);
		CompoundTag approval = new CompoundTag();
		this.approval.forEach((id, n) -> approval.putDouble(id.toString(), n));
		tag.put("Approval", approval);
		tag.putLong("MoodDay", this.moodDay);
		tag.putString("Upheaval", this.upheaval);
		tag.putLong("UpheavalDay", this.upheavalDay);
		tag.putString("OldLeader", this.oldLeader);
		ListTag customs = new ListTag();
		this.traditions.forEach(word -> customs.add(net.minecraft.nbt.StringTag.valueOf(word)));
		tag.put("Traditions", customs);
		CompoundTag tally = new CompoundTag();
		this.tally.forEach(tally::putInt);
		tag.put("Tally", tally);
		tag.putLongArray("DeathSpots", this.deathSpots.stream().mapToLong(BlockPos::asLong).toArray());
		CompoundTag heard = new CompoundTag();
		this.heard.forEach((id, n) -> heard.putDouble(id.toString(), n));
		tag.put("Heard", heard);
		CompoundTag demand = new CompoundTag();
		this.demand.forEach(demand::putDouble);
		tag.put("Demand", demand);
		tag.putLong("DemandDay", this.demandDay);
		tag.putLong("WordDay", this.wordDay);
		ListTag notices = new ListTag();
		this.notices.forEach(notice -> notices.add(notice.save()));
		tag.put("Notices", notices);
		tag.putLong("NoticeDay", this.noticeDay);
		tag.putBoolean("Revolt", this.revolt);
		tag.putBoolean("Conceded", this.conceded);
		if (this.plotter != null) {
			tag.putUUID("Plotter", this.plotter);
		}

		ListTag crooks = new ListTag();
		this.crooks.forEach(id -> crooks.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
		tag.put("Crooks", crooks);
		ListTag stocked = new ListTag();
		this.lineStocked.forEach(id -> stocked.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
		tag.put("LineStocked", stocked);
		CompoundTag lines = new CompoundTag();
		this.lines.forEach((id, line) -> lines.putString(id.toString(), line));
		tag.put("Lines", lines);
		tag.put("PendingBonds", TownHistory.save(this.pendingBonds));
		tag.putString("LastCrook", this.lastCrook);
		tag.putString("LastCrime", this.lastCrime);
		tag.putLong("LastCrimeDay", this.lastCrimeDay);
		ListTag crimes = new ListTag();
		this.crimes.forEach((id, c) -> {
			CompoundTag one = new CompoundTag();
			one.putUUID("Id", id);
			one.putString("Name", c.name);
			one.putInt("Offences", c.offences);
			one.putInt("Fine", c.fine);
			one.putString("Crime", c.crime);
			one.putLong("ExiledUntil", c.exiledUntil);
			one.putLong("WantedUntil", c.wantedUntil);
			one.putInt("Kills", c.kills);
			one.putInt("RecentKills", c.recentKills);
			one.putLong("LastKillDay", c.lastKillDay);
			crimes.add(one);
		});
		tag.put("Crimes", crimes);
		return tag;
	}

	static Town load(CompoundTag tag) {
		Town town = new Town(tag.getUUID("Id"), tag.getString("Name"), tag.hasUUID("Group") ? tag.getUUID("Group") : null, tag.getString("Dimension"),
			BlockPos.of(tag.getLong("Center")), tag.getLong("FoundedDay"), tag.getBoolean("Spawned"));
		town.pastDead = tag.contains("PastDead") ? tag.getString("PastDead") : null;
		for (Tag t : tag.getList("Plots", Tag.TAG_COMPOUND)) {
			town.plots.add(Plot.load((CompoundTag)t));
		}

		for (long pos : tag.getLongArray("Store")) {
			town.store.add(BlockPos.of(pos));
		}

		for (long pos : tag.getLongArray("Fields")) {
			town.fields.add(BlockPos.of(pos));
		}

		town.well = tag.contains("Well") ? BlockPos.of(tag.getLong("Well")) : null;
		town.fire = tag.contains("Fire") ? BlockPos.of(tag.getLong("Fire")) : null;
		town.pen = tag.contains("Pen") ? BlockPos.of(tag.getLong("Pen")) : null;
		for (Tag one : tag.getList("KeepOff", Tag.TAG_INT_ARRAY)) {
			int[] rect = ((net.minecraft.nbt.IntArrayTag)one).getAsIntArray();
			if (rect.length == 4) {
				town.keepOff.add(rect);
			}
		}

		if (tag.contains("Mine")) {
			town.mine = BlockPos.of(tag.getLong("Mine"));
			town.mineBottom = BlockPos.of(tag.getLong("MineBottom"));
			town.mineWay = Direction.byName(tag.getString("MineWay"));
		}
		town.storePlot = tag.contains("StorePlot") ? Plot.load(tag.getCompound("StorePlot")) : null;
		town.storeBuilt = tag.getBoolean("StoreBuilt") || town.spawned;
		for (Tag one : tag.getList("Lanes", Tag.TAG_COMPOUND)) {
			CompoundTag lane = (CompoundTag)one;
			Direction way = Direction.byName(lane.getString("Way"));
			if (way != null) {
				town.lanes.add(new TownRoads.Road(lane.getInt("X"), lane.getInt("Z"), way, lane.getInt("Length")));
			}
		}

		town.shape = tag.getString("Shape");
		town.roadColumns = tag.getLongArray("RoadColumns");
		town.stallPlot = tag.contains("StallPlot") ? Plot.load(tag.getCompound("StallPlot")) : null;
		town.stallBuilt = tag.getBoolean("StallBuilt");
		town.keeper = tag.hasUUID("Keeper") ? tag.getUUID("Keeper") : null;
		town.trades = tag.getInt("Trades");
		for (Tag one : tag.getList("Plaza", Tag.TAG_COMPOUND)) {
			town.plaza.add(Plot.load((CompoundTag)one));
		}

		town.wellFilled = tag.getBoolean("WellFilled");
		town.boardDay = tag.contains("BoardDay") ? tag.getLong("BoardDay") : -1;
		town.bountyBoard = tag.contains("BountyBoard") ? BlockPos.of(tag.getLong("BountyBoard")) : null;
		town.lifeDay = tag.contains("LifeDay") ? tag.getLong("LifeDay") : -1;
		town.newest = tag.contains("Newest") ? tag.getString("Newest") : null;
		town.newestDay = tag.contains("NewestDay") ? tag.getLong("NewestDay") : -100;
		town.lastLeft = tag.contains("LastLeft") ? tag.getString("LastLeft") : null;
		town.lastLeftDay = tag.contains("LastLeftDay") ? tag.getLong("LastLeftDay") : -100;
		town.arrivedDay = tag.contains("ArrivedDay") ? tag.getLong("ArrivedDay") : -100;
		town.byWater = tag.getBoolean("ByWater");
		town.eventDay = tag.contains("EventDay") ? tag.getLong("EventDay") : -1;
		town.contestWinner = tag.contains("ContestWinner") ? tag.getString("ContestWinner") : null;
		town.contestDay = tag.contains("ContestDay") ? tag.getLong("ContestDay") : -100;
		town.bornDay = tag.contains("BornDay") ? tag.getLong("BornDay") : town.foundedDay;
		town.festivalDay = tag.contains("FestivalDay") ? tag.getLong("FestivalDay") : -1;
		town.shrine = tag.contains("Shrine") ? BlockPos.of(tag.getLong("Shrine")) : null;
		town.character = tag.contains("Character") ? tag.getString("Character") : null;
		town.knownLeader = tag.hasUUID("KnownLeader") ? tag.getUUID("KnownLeader") : null;
		town.monumentFor = tag.contains("MonumentFor") ? tag.getString("MonumentFor") : null;
		town.monument = tag.contains("Monument") ? Plot.load(tag.getCompound("Monument")) : null;
		town.monumentDay = tag.contains("MonumentDay") ? tag.getLong("MonumentDay") : -100;
		CompoundTag owed = tag.getCompound("Owed");
		for (String key : owed.getAllKeys()) {
			try {
				town.owed.put(UUID.fromString(key), owed.getDouble(key));
			} catch (IllegalArgumentException ignored) {
				// (Not an id.)
			}
		}

		town.wagesPaid = tag.getDouble("WagesPaid");
		town.drifterTrades = tag.getInt("DrifterTrades");
		town.leader = tag.hasUUID("Leader") ? tag.getUUID("Leader") : null;
		town.leaderSince = tag.getLong("LeaderSince");
		town.nextElection = tag.contains("NextElection") ? tag.getLong("NextElection") : -1;
		for (Tag one : tag.getList("Candidates", Tag.TAG_COMPOUND)) {
			town.candidates.add(((CompoundTag)one).getUUID("Id"));
		}

		CompoundTag votes = tag.getCompound("LastVotes");
		for (String key : votes.getAllKeys()) {
			town.lastVotes.put(key, votes.getInt(key));
		}

		for (Tag one : tag.getList("Laws", Tag.TAG_STRING)) {
			town.laws.add(one.getAsString());
		}

		town.tax = tag.getInt("Tax");
		CompoundTag owes = tag.getCompound("TaxOwed");
		for (String key : owes.getAllKeys()) {
			try {
				town.taxOwed.put(UUID.fromString(key), owes.getInt(key));
			} catch (IllegalArgumentException ignored) {
				// (Not an id.)
			}
		}

		town.taxDay = tag.contains("TaxDay") ? tag.getLong("TaxDay") : -1;
		CompoundTag approval = tag.getCompound("Approval");
		for (String key : approval.getAllKeys()) {
			try {
				town.approval.put(UUID.fromString(key), approval.getDouble(key));
			} catch (IllegalArgumentException ignored) {
			}
		}

		town.moodDay = tag.contains("MoodDay") ? tag.getLong("MoodDay") : -1;
		town.upheaval = tag.getString("Upheaval");
		town.upheavalDay = tag.contains("UpheavalDay") ? tag.getLong("UpheavalDay") : -100;
		town.oldLeader = tag.getString("OldLeader");
		for (Tag t : tag.getList("Traditions", Tag.TAG_STRING)) {
			town.traditions.add(t.getAsString());
		}

		CompoundTag tally = tag.getCompound("Tally");
		for (String key : tally.getAllKeys()) {
			town.tally.put(key, tally.getInt(key));
		}

		for (long spot : tag.getLongArray("DeathSpots")) {
			town.deathSpots.add(BlockPos.of(spot));
		}

		CompoundTag heard = tag.getCompound("Heard");
		for (String key : heard.getAllKeys()) {
			try {
				town.heard.put(UUID.fromString(key), heard.getDouble(key));
			} catch (IllegalArgumentException ignored) {
			}
		}

		town.wordDay = tag.contains("WordDay") ? tag.getLong("WordDay") : -1;
		CompoundTag demand = tag.getCompound("Demand");
		for (String key : demand.getAllKeys()) {
			town.demand.put(key, demand.getDouble(key));
		}

		town.demandDay = tag.contains("DemandDay") ? tag.getLong("DemandDay") : -1;
		for (Tag t : tag.getList("Notices", Tag.TAG_COMPOUND)) {
			town.notices.add(TownNotices.Notice.load((CompoundTag)t));
		}

		town.noticeDay = tag.contains("NoticeDay") ? tag.getLong("NoticeDay") : -1;
		town.revolt = tag.getBoolean("Revolt");
		town.conceded = tag.getBoolean("Conceded");
		town.plotter = tag.hasUUID("Plotter") ? tag.getUUID("Plotter") : null;
		for (Tag t : tag.getList("Crooks", Tag.TAG_INT_ARRAY)) {
			town.crooks.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
		}

		for (Tag t : tag.getList("LineStocked", Tag.TAG_INT_ARRAY)) {
			town.lineStocked.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
		}

		CompoundTag lines = tag.getCompound("Lines");
		for (String key : lines.getAllKeys()) {
			try {
				town.lines.put(UUID.fromString(key), lines.getString(key));
			} catch (IllegalArgumentException ignored) {
			}
		}

		TownHistory.load(tag.getList("PendingBonds", Tag.TAG_COMPOUND), town.pendingBonds);
		town.lastCrook = tag.getString("LastCrook");
		town.lastCrime = tag.getString("LastCrime");
		town.lastCrimeDay = tag.contains("LastCrimeDay") ? tag.getLong("LastCrimeDay") : -100;
		for (Tag t : tag.getList("Crimes", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag)t;
			Criminal c = new Criminal(one.getString("Name"));
			c.offences = one.getInt("Offences");
			c.fine = one.getInt("Fine");
			c.crime = one.getString("Crime");
			c.exiledUntil = one.getLong("ExiledUntil");
			c.wantedUntil = one.getLong("WantedUntil");
			c.kills = one.getInt("Kills");
			c.recentKills = one.getInt("RecentKills");
			c.lastKillDay = one.contains("LastKillDay") ? one.getLong("LastKillDay") : -100;
			town.crimes.put(one.getUUID("Id"), c);
		}
		return town;
	}
}
