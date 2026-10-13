package com.dinglebob.drifter.personality;

import java.util.EnumMap;
import java.util.Locale;
import java.util.function.Function;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The habit fingerprint (DESIGN.md section 17): discrete quirks rolled once at birth and never changed. These are what
 * let a player recognize a specific Drifter from its work alone. Rolled from its traits, so habits fit the person
 * (a tidy Drifter is likely to pick its crafting tables back up).
 *
 * <p>Some habits are only used by later milestones (building, torches, chests); they're rolled now so a Drifter's
 * identity never changes when those systems arrive.
 */
public final class Habits {
	public enum Tunnel {
		/** 1 wide, 2 high (most players). */
		ONE_BY_TWO,
		/** Comfort-lovers. */
		TWO_BY_TWO,
		/** Builders. */
		THREE_BY_THREE
	}

	public enum Side {
		LEFT, RIGHT
	}

	public enum Offhand {
		SHIELD, TORCH, FOOD, EMPTY
	}

	public enum DoorEtiquette {
		ALWAYS_CLOSES, CLOSES_AT_NIGHT, NEVER_CLOSES
	}

	public enum Celebration {
		CROUCH_SPAM, SPIN, SWING, QUIET
	}

	public enum Greeting {
		CROUCH_SPAM, NOD, APPROACH_AND_STARE
	}

	public enum SleepTiming {
		AT_DUSK, ONLY_FOR_PHANTOMS
	}

	public enum TrailMarker {
		TORCHES, COBBLESTONE_PILLARS, SIGNS, NOTHING
	}

	public enum HotbarLayout {
		/** Sword 1, pickaxe 2, axe 3, shovel 4, food 5, blocks 6 to 8, torches 9. */
		CLASSIC,
		MIRRORED,
		/** Wherever things land; reshuffles. */
		MESSY
	}

	private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"};
	private static final String[] STONES = {"cobblestone", "stone_bricks", "stone", "deepslate_bricks", "andesite", "polished_andesite",
		"mossy_cobblestone", "smooth_stone"};
	private static final String[] WOOLS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
		"purple", "blue", "brown", "green", "red", "black"};

	/** Eats when food drops to this (section 17: 12 to 17). */
	public int eatThreshold = 14;
	/** Auto-jump players jump into every 1-block step on contact (section 16.2). */
	public boolean autoJump;
	/** Scrollers visibly cycle through the slots in between; number-keyers snap straight to one (section 16.3). */
	public boolean scroller;
	public boolean sprintToggle;
	public Tunnel tunnel = Tunnel.ONE_BY_TWO;
	/** Blocks between side branches in a branch mine. */
	public int branchSpacing = 3;
	public Side torchSide = Side.RIGHT;
	public Offhand offhand = Offhand.SHIELD;
	public DoorEtiquette doors = DoorEtiquette.CLOSES_AT_NIGHT;
	/** Picks its crafting tables back up instead of leaving them littered around. */
	public boolean picksUpTables;
	public Celebration celebration = Celebration.QUIET;
	public Greeting greeting = Greeting.NOD;
	public SleepTiming sleep = SleepTiming.AT_DUSK;
	public TrailMarker trail = TrailMarker.NOTHING;
	public HotbarLayout hotbar = HotbarLayout.CLASSIC;
	public boolean keepsDirtStack;
	public String favoriteWood = "oak";
	public String favoriteStone = "cobblestone";
	public String favoriteWool = "white";
	/**
	 * How it likes to find stone and ore (user's note for milestone 7, section 10.3 "chosen by personality"): 0 leans on
	 * caving and exploring the surface, 1 on strip and branch mining. Every Drifter's mix is its own.
	 */
	public double miningLean = 0.5;

	public static Habits roll(Archetype archetype, EnumMap<Trait, Double> traits, RandomSource random) {
		Habits habits = new Habits();
		Function<Trait, Double> t = traits::get;
		double caution = t.apply(Trait.CAUTION);
		double tidiness = t.apply(Trait.TIDINESS);
		double patience = t.apply(Trait.PATIENCE);

		// Cautious players top up early; carefree ones let it run down.
		habits.eatThreshold = Mth.clamp((int)Math.round(14.5 + (caution - 0.5) * 3.0 + (random.nextDouble() * 2.0 - 1.0) * 1.5), 12, 17);
		habits.autoJump = random.nextDouble() < (archetype == Archetype.NEWBIE ? 0.35 : 0.12);
		habits.scroller = random.nextDouble() < Mth.lerp(t.apply(Trait.EFFICIENCY), 0.7, 0.2);
		habits.sprintToggle = random.nextBoolean();

		if (archetype == Archetype.BUILDER && random.nextDouble() < 0.6) {
			habits.tunnel = Tunnel.THREE_BY_THREE;
		} else {
			habits.tunnel = random.nextDouble() < 0.12 + 0.2 * patience ? Tunnel.TWO_BY_TWO : Tunnel.ONE_BY_TWO;
		}

		habits.branchSpacing = 2 + random.nextInt(3);
		habits.torchSide = random.nextBoolean() ? Side.RIGHT : Side.LEFT;
		double shield = 0.3 + caution * 0.4;
		double roll = random.nextDouble();
		habits.offhand = roll < shield ? Offhand.SHIELD : roll < shield + 0.2 ? Offhand.TORCH : roll < shield + 0.3 ? Offhand.FOOD : Offhand.EMPTY;

		roll = random.nextDouble();
		habits.doors = roll < tidiness * 0.8 ? DoorEtiquette.ALWAYS_CLOSES : roll < 0.75 ? DoorEtiquette.CLOSES_AT_NIGHT : DoorEtiquette.NEVER_CLOSES;
		habits.picksUpTables = random.nextDouble() < Mth.lerp(tidiness, 0.1, 0.9);

		double fidget = t.apply(Trait.FIDGET);
		roll = random.nextDouble();
		habits.celebration = roll < 0.2 + fidget * 0.3 ? Celebration.CROUCH_SPAM : roll < 0.6 ? Celebration.SPIN : roll < 0.8 ? Celebration.SWING
			: Celebration.QUIET;
		roll = random.nextDouble();
		habits.greeting = roll < 0.4 ? Greeting.CROUCH_SPAM : roll < 0.75 ? Greeting.NOD : Greeting.APPROACH_AND_STARE;
		habits.sleep = random.nextDouble() < 0.4 + caution * 0.4 ? SleepTiming.AT_DUSK : SleepTiming.ONLY_FOR_PHANTOMS;

		roll = random.nextDouble();
		habits.trail = roll < 0.3 ? TrailMarker.TORCHES : roll < 0.45 ? TrailMarker.COBBLESTONE_PILLARS : roll < 0.5 ? TrailMarker.SIGNS
			: TrailMarker.NOTHING;
		roll = random.nextDouble();
		habits.hotbar = roll < tidiness * 0.7 ? HotbarLayout.CLASSIC : roll < 0.7 ? HotbarLayout.MIRRORED : HotbarLayout.MESSY;
		habits.keepsDirtStack = random.nextDouble() < 0.6;
		habits.favoriteWood = WOODS[random.nextInt(WOODS.length)];
		habits.favoriteStone = STONES[random.nextInt(STONES.length)];
		habits.favoriteWool = WOOLS[random.nextInt(WOOLS.length)];

		// Patient, hard-working types settle into strip mining; curious ones go caving and roam the surface.
		double lean = 0.5 + (patience - 0.5) * 0.5 + (t.apply(Trait.INDUSTRIOUSNESS) - 0.5) * 0.5 - (t.apply(Trait.CURIOSITY) - 0.5) * 0.7;
		if (archetype == Archetype.MINER) {
			lean += 0.2;
		} else if (archetype == Archetype.NOMAD) {
			lean -= 0.2;
		}

		habits.miningLean = Archetype.clamp(lean + (random.nextDouble() * 2.0 - 1.0) * 0.25);
		return habits;
	}

	public String miningStyle() {
		return this.miningLean >= 0.65 ? "strip miner" : this.miningLean <= 0.35 ? "caver and surface explorer" : "mixed";
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("EatThreshold", this.eatThreshold);
		tag.putBoolean("AutoJump", this.autoJump);
		tag.putBoolean("Scroller", this.scroller);
		tag.putBoolean("SprintToggle", this.sprintToggle);
		tag.putString("Tunnel", this.tunnel.name());
		tag.putInt("BranchSpacing", this.branchSpacing);
		tag.putString("TorchSide", this.torchSide.name());
		tag.putString("Offhand", this.offhand.name());
		tag.putString("Doors", this.doors.name());
		tag.putBoolean("PicksUpTables", this.picksUpTables);
		tag.putString("Celebration", this.celebration.name());
		tag.putString("Greeting", this.greeting.name());
		tag.putString("Sleep", this.sleep.name());
		tag.putString("Trail", this.trail.name());
		tag.putString("Hotbar", this.hotbar.name());
		tag.putBoolean("KeepsDirt", this.keepsDirtStack);
		tag.putString("FavoriteWood", this.favoriteWood);
		tag.putString("FavoriteStone", this.favoriteStone);
		tag.putString("FavoriteWool", this.favoriteWool);
		tag.putDouble("MiningLean", this.miningLean);
		return tag;
	}

	public static Habits load(CompoundTag tag) {
		Habits habits = new Habits();
		habits.eatThreshold = Mth.clamp(tag.getInt("EatThreshold"), 12, 17);
		habits.autoJump = tag.getBoolean("AutoJump");
		habits.scroller = tag.getBoolean("Scroller");
		habits.sprintToggle = tag.getBoolean("SprintToggle");
		habits.tunnel = parse(Tunnel.class, tag.getString("Tunnel"), habits.tunnel);
		habits.branchSpacing = Mth.clamp(tag.getInt("BranchSpacing"), 2, 4);
		habits.torchSide = parse(Side.class, tag.getString("TorchSide"), habits.torchSide);
		habits.offhand = parse(Offhand.class, tag.getString("Offhand"), habits.offhand);
		habits.doors = parse(DoorEtiquette.class, tag.getString("Doors"), habits.doors);
		habits.picksUpTables = tag.getBoolean("PicksUpTables");
		habits.celebration = parse(Celebration.class, tag.getString("Celebration"), habits.celebration);
		habits.greeting = parse(Greeting.class, tag.getString("Greeting"), habits.greeting);
		habits.sleep = parse(SleepTiming.class, tag.getString("Sleep"), habits.sleep);
		habits.trail = parse(TrailMarker.class, tag.getString("Trail"), habits.trail);
		habits.hotbar = parse(HotbarLayout.class, tag.getString("Hotbar"), habits.hotbar);
		habits.keepsDirtStack = tag.getBoolean("KeepsDirt");
		if (!tag.getString("FavoriteWood").isEmpty()) {
			habits.favoriteWood = tag.getString("FavoriteWood");
		}

		if (!tag.getString("FavoriteStone").isEmpty()) {
			habits.favoriteStone = tag.getString("FavoriteStone");
		}

		if (!tag.getString("FavoriteWool").isEmpty()) {
			habits.favoriteWool = tag.getString("FavoriteWool");
		}

		habits.miningLean = tag.contains("MiningLean") ? Archetype.clamp(tag.getDouble("MiningLean")) : 0.5;
		return habits;
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
		try {
			return Enum.valueOf(type, name);
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}

	static String words(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
	}
}
