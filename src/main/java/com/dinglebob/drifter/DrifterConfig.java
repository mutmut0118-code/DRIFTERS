package com.dinglebob.drifter;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.util.Mth;

/**
 * Settings the user can change while testing (`/drifter config ...`), saved in {@code config/drifter.json}:
 * how far Drifters see, and how far ahead the pathfinder plans. Read through the getters; defaults are the tested
 * values.
 */
public final class DrifterConfig {
	public static final int DEFAULT_SIGHT_CHUNKS = 10;
	public static final int MIN_SIGHT_CHUNKS = 2;
	public static final int MAX_SIGHT_CHUNKS = 16;
	public static final int DEFAULT_PATH_RANGE = 64;
	public static final int MIN_PATH_RANGE = 24;
	public static final int MAX_PATH_RANGE = 160;
	/**
	 * Field of view, like the game's FOV slider (vertical degrees; 70 is "Normal", 110 "Quake Pro"). The width of its
	 * view cone follows from a 16:9 screen, as for a player.
	 */
	public static final int DEFAULT_FOV = 70;
	public static final int MIN_FOV = 30;
	public static final int MAX_FOV = 110;
	private static final double SCREEN_ASPECT = 16.0 / 9.0;
	/** The cone never gets wider than the peripheral zone around it (Senses). */
	private static final double MAX_HALF_YAW = 85.0;
	/** Search nodes per block of path range squared, from the tested 8000 nodes for 48 blocks. */
	private static final double NODES_PER_AREA = 8000.0 / (48.0 * 48.0);
	private static final int MAX_NODES = 60000;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** The saved file's shape. */
	private static final class Data {
		int version = 1;
		int sightChunks = DEFAULT_SIGHT_CHUNKS;
		int pathRange = DEFAULT_PATH_RANGE;
		int fov = DEFAULT_FOV;
		/** Short canned chat lines (hi, ty, gg...), section 14 "Optional chat lines": on by default. */
		boolean chatLines = true;
		/** Players seen together share a little of each other's standing (section 14 "Forgiveness and multiplayer"). */
		boolean shareReputation = true;
		/** Every Drifter thought goes to the game log (user's choice, 2026-10-15: on by default). */
		boolean logThoughts = true;
		/** Bandits and Drifters with a grudge raid players' chests (section 14 griefing; user's choice: on by default). */
		boolean raiding = true;
		/** Drifters away from players that keep running as if a player were there (section 22 hybrid; 0 turns it off). */
		int simulatedDrifters = DEFAULT_SIMULATED;
		/** Catch-up for time away (section 22 offline progression): "off", "game_time" (default) or "real_time". */
		String offlineProgression = "game_time";
		/** Real-time catch-up's own cap, in ticks (default 2 in-game days). */
		int realTimeCapTicks = DEFAULT_REAL_TIME_CAP;
		/** How hostile Drifters are toward players, 0 to 100 (user, 2026-10-16): 0 never hostile, 100 hostile on sight, 50 as
		 * their personalities have it. */
		int playerAggression = DEFAULT_AGGRESSION;
		/** The same toward each other: 0 never enemies, 100 every Drifter outside its group an enemy on sight. */
		int drifterAggression = DEFAULT_AGGRESSION;
		/** A lived-in world (user, 2026-10-16), each on its own: camps, old strip mines, torches in caves. New chunks only. */
		boolean worldgenCamps = true;
		boolean worldgenMines = true;
		boolean worldgenCaveTorches = true;
		/** Drifters living in some villages, Bandit gangs holding some pillager outposts (user, 2026-10-16). */
		boolean worldgenVillages = true;
		boolean worldgenOutposts = true;
		/** Lone Drifters out in the world with no home: fresh, setting off on day one, or passing through (user, 2026-10-17). */
		boolean worldgenWanderers = true;
		/** Well-armed Drifters delving some mineshafts, looting their chests; mostly unfriendly (user, 2026-10-17). */
		boolean worldgenDelvers = true;
		/** How common each kind of new Drifter is, and the most loaded at once (user, 2026-10-17: "make all the spawn
		 * chances configurable"; "10 to 60 drifters in a few minutes"). Keyed by {@link Spawn#id}. */
		java.util.Map<String, Integer> spawn = new java.util.LinkedHashMap<>();
		/** Drifter groups fall into feuds, skirmish and raid each other (section 21 "Drifter team wars"). */
		boolean drifterWars = true;
		/** Whims: the things they do just for fun (milestone 13, docs/WHIMS.md). */
		boolean whims = true;
		/** Outpost Bandits attack on sight and grief bases: doors, chests, fire, blocks (user's decision, 2026-10-17). */
		boolean banditGriefing = true;
		/** Bandits kill anything outside their group on sight: players, Drifters, villagers, golems, pets (user, 2026-10-17). */
		boolean banditsKillOnSight = true;
		/** Gap jumps in pathfinding (user, 2026-10-17: parkour). */
		boolean parkour = true;
		/** A Drifter that dies becomes a ragdoll (and a lootable corpse) when Sable Player Ragdoll is installed (user, 2026-10-17). */
		boolean ragdolls = true;
		/** Threads for path searches (milestone 16): -1 automatic, 0 on the server thread as before. */
		int pathThreads = -1;
		/** Drifters far from every player look about and think every other tick (milestone 16). */
		boolean farHalfRate = true;
		/** Real player skins, downloaded like a multiplayer server's (user, 2026-10-20); off: the game's default skins. */
		boolean realSkins = true;
		/** Encounters (milestone 21, user 2026-10-20): random events with Drifters you meet once and never again. */
		boolean encounters = true;
		/** About one every this many in-game days per player. */
		double encounterDays = DEFAULT_ENCOUNTER_DAYS;
		/** Kinds switched off, by id (bounty_hunters, wanted, injured_traveller, thief, merchant, ambush). */
		java.util.List<String> encountersOff = new java.util.ArrayList<>();
		/** What a Drifter's death means (section 20, user 2026-10-20: "make it configurable between the three"): "bed",
		 * "lives" (default) or "permadeath". */
		String respawnMode = "lives";
		/** Limited lives: how many times it comes back (section 20: 5). */
		int lives = DEFAULT_LIVES;
		/** "<name> was slain by Zombie" in chat, as for a player (section 20). */
		boolean deathMessages = true;
	}

	public static final int DEFAULT_LIVES = 5;
	public static final int MAX_LIVES = 20;

	public static final double DEFAULT_ENCOUNTER_DAYS = 1.5;
	public static final double MIN_ENCOUNTER_DAYS = 0.25;
	public static final double MAX_ENCOUNTER_DAYS = 10.0;

	public static boolean encounters() {
		return data.encounters;
	}

	public static void setEncounters(boolean on) {
		data.encounters = on;
		save();
	}

	public static double encounterDays() {
		return data.encounterDays <= 0.0 ? DEFAULT_ENCOUNTER_DAYS : data.encounterDays;
	}

	public static void setEncounterDays(double days) {
		data.encounterDays = Math.max(MIN_ENCOUNTER_DAYS, Math.min(MAX_ENCOUNTER_DAYS, days));
		save();
	}

	/** This kind of encounter is switched off. */
	public static boolean encounterOff(String id) {
		return data.encountersOff != null && data.encountersOff.contains(id);
	}

	public static void setEncounterOff(String id, boolean off) {
		if (data.encountersOff == null) {
			data.encountersOff = new java.util.ArrayList<>();
		}

		data.encountersOff.remove(id);
		if (off) {
			data.encountersOff.add(id);
		}

		save();
	}

	public static boolean realSkins() {
		return data.realSkins;
	}

	public static void setRealSkins(boolean on) {
		data.realSkins = on;
		save();
	}

	public static boolean farHalfRate() {
		return data.farHalfRate;
	}

	public static void setFarHalfRate(boolean on) {
		data.farHalfRate = on;
		save();
	}

	public static final int MAX_PATH_THREADS = 16;

	/** Threads for path searches: -1 automatic (a quarter of the machine's, 1 to 6), 0 none (on the server thread). */
	public static int pathThreads() {
		return data.pathThreads;
	}

	public static void setPathThreads(int threads) {
		data.pathThreads = Mth.clamp(threads, -1, MAX_PATH_THREADS);
		save();
	}

	public static boolean ragdolls() {
		return data.ragdolls;
	}

	public static void setRagdolls(boolean on) {
		data.ragdolls = on;
		save();
	}

	public static boolean parkour() {
		return data.parkour;
	}

	public static void setParkour(boolean on) {
		data.parkour = on;
		save();
	}

	public static boolean banditsKillOnSight() {
		return data.banditsKillOnSight;
	}

	public static void setBanditsKillOnSight(boolean on) {
		data.banditsKillOnSight = on;
		save();
	}

	public static boolean banditGriefing() {
		return data.banditGriefing;
	}

	public static void setBanditGriefing(boolean on) {
		data.banditGriefing = on;
		save();
	}

	public static boolean whims() {
		return data.whims;
	}

	public static void setWhims(boolean on) {
		data.whims = on;
		save();
	}

	public static boolean drifterWars() {
		return data.drifterWars;
	}

	public static void setDrifterWars(boolean on) {
		data.drifterWars = on;
		save();
	}

	public static boolean worldgenVillages() {
		return data.worldgenVillages;
	}

	public static void setWorldgenVillages(boolean on) {
		data.worldgenVillages = on;
		save();
	}

	public static boolean worldgenWanderers() {
		return data.worldgenWanderers;
	}

	public static void setWorldgenWanderers(boolean on) {
		data.worldgenWanderers = on;
		save();
	}

	/**
	 * Spawn settings (user's request, 2026-10-17): how often each source makes Drifters, and a cap on how many are loaded
	 * at once (world generation makes no more past it; a camp is left empty instead).
	 */
	public enum Spawn {
		MAX_DRIFTERS("max_drifters", "Most Drifters loaded", 0, 200, 24, v -> v == 0 ? "no new ones" : v + " Drifters"),
		CAMPS("camps", "Camps", 0, 100, 12, v -> v + "% of spots"),
		/** About this far apart, camps (one spot per square this size; user, 2026-10-17). */
		CAMP_SPACING("camp_spacing", "Space between camps", 48, 1024, 112, v -> v + " blocks"),
		CAMPS_LIVED_IN("camps_lived_in", "Camps lived in", 0, 100, 85, v -> v + "%"),
		CAMP_HOUSES("camp_houses", "Camps with a house", 0, 100, 80, v -> v + "%"),
		WANDERERS("wanderers", "Wanderers", 0, 50, 4, v -> v + " per 1000 chunks"),
		VILLAGES("villages", "Villages lived in", 0, 100, 30, v -> v + "%"),
		OUTPOSTS("outposts", "Outposts held by Bandits", 0, 100, 50, v -> v + "%"),
		DELVERS("delvers", "Mineshafts with a delver", 0, 100, 25, v -> v + "%"),
		/** Generated house plans in a world (milestone 15), besides the hand-made ones. */
		HOUSE_DESIGNS("house_designs", "Generated house designs", 0, 1000, 300, v -> v == 0 ? "hand-made only" : v + " designs"),
		/** How long a dead Drifter's corpse lasts (Ragdoll Corpse; user, 2026-10-17: "a time limit for corpses after a few days"). */
		CORPSE_DAYS("corpse_days", "Drifter corpses last", 0, 30, 3, v -> v == 0 ? "forever" : v + (v == 1 ? " day" : " days"));

		public final String id;
		public final String label;
		public final int min;
		public final int max;
		public final int fallback;
		public final java.util.function.IntFunction<String> text;

		Spawn(String id, String label, int min, int max, int fallback, java.util.function.IntFunction<String> text) {
			this.id = id;
			this.label = label;
			this.min = min;
			this.max = max;
			this.fallback = fallback;
			this.text = text;
		}

		public static Spawn byId(String id) {
			for (Spawn spawn : values()) {
				if (spawn.id.equals(id)) {
					return spawn;
				}
			}

			return null;
		}
	}

	public static int spawn(Spawn setting) {
		java.util.Map<String, Integer> map = data.spawn;
		Integer value = map == null ? null : map.get(setting.id);
		return value == null ? setting.fallback : Mth.clamp(value, setting.min, setting.max);
	}

	/** As a chance from 0 to 1 (the percentage settings). */
	public static double spawnChance(Spawn setting) {
		return spawn(setting) / 100.0;
	}

	public static void setSpawn(Spawn setting, int value) {
		if (data.spawn == null) {
			data.spawn = new java.util.LinkedHashMap<>();
		}

		data.spawn.put(setting.id, Mth.clamp(value, setting.min, setting.max));
		save();
	}

	public static boolean worldgenDelvers() {
		return data.worldgenDelvers;
	}

	public static void setWorldgenDelvers(boolean on) {
		data.worldgenDelvers = on;
		save();
	}

	public static boolean worldgenOutposts() {
		return data.worldgenOutposts;
	}

	public static void setWorldgenOutposts(boolean on) {
		data.worldgenOutposts = on;
		save();
	}

	public static boolean worldgenCamps() {
		return data.worldgenCamps;
	}

	public static void setWorldgenCamps(boolean on) {
		data.worldgenCamps = on;
		save();
	}

	public static boolean worldgenMines() {
		return data.worldgenMines;
	}

	public static void setWorldgenMines(boolean on) {
		data.worldgenMines = on;
		save();
	}

	public static boolean worldgenCaveTorches() {
		return data.worldgenCaveTorches;
	}

	public static void setWorldgenCaveTorches(boolean on) {
		data.worldgenCaveTorches = on;
		save();
	}

	public static final int DEFAULT_AGGRESSION = 50;

	public static int playerAggression() {
		return data.playerAggression;
	}

	public static void setPlayerAggression(int value) {
		data.playerAggression = Math.max(0, Math.min(100, value));
		save();
	}

	public static int drifterAggression() {
		return data.drifterAggression;
	}

	public static void setDrifterAggression(int value) {
		data.drifterAggression = Math.max(0, Math.min(100, value));
		save();
	}

	/** Trust a stranger starts with, shifted by an aggression setting: +50 at 0, none at 50, -50 at 100. */
	public static double aggressionShift(int aggression) {
		return DEFAULT_AGGRESSION - aggression;
	}

	/** Section 20: what a Drifter's death means. */
	public enum RespawnMode {
		/** Back at its bed (or its base) every time; with no base, it's gone. */
		BED,
		/** As BED, a few times ({@link #lives()}); then it logs off for good. */
		LIVES,
		/** Never back: its base is left as it was, for anyone to find. */
		PERMADEATH
	}

	public static RespawnMode respawnMode() {
		try {
			return RespawnMode.valueOf(data.respawnMode.toUpperCase(java.util.Locale.ROOT));
		} catch (RuntimeException e) {
			return RespawnMode.LIVES;
		}
	}

	public static void setRespawnMode(RespawnMode mode) {
		data.respawnMode = mode.name().toLowerCase(java.util.Locale.ROOT);
		save();
	}

	public static int lives() {
		return Math.max(1, Math.min(MAX_LIVES, data.lives));
	}

	public static void setLives(int lives) {
		data.lives = Math.max(1, Math.min(MAX_LIVES, lives));
		save();
	}

	public static boolean deathMessages() {
		return data.deathMessages;
	}

	public static void setDeathMessages(boolean on) {
		data.deathMessages = on;
		save();
	}

	/** Section 22 offline progression. */
	public enum OfflineProgression {
		OFF, GAME_TIME, REAL_TIME
	}

	public static final int DEFAULT_REAL_TIME_CAP = 48000;

	public static OfflineProgression offlineProgression() {
		try {
			return OfflineProgression.valueOf(data.offlineProgression.toUpperCase(java.util.Locale.ROOT));
		} catch (RuntimeException e) {
			return OfflineProgression.GAME_TIME;
		}
	}

	public static void setOfflineProgression(OfflineProgression mode) {
		data.offlineProgression = mode.name().toLowerCase(java.util.Locale.ROOT);
		save();
	}

	public static long realTimeCapTicks() {
		return Math.max(0, data.realTimeCapTicks);
	}

	public static final int DEFAULT_SIMULATED = 3;
	public static final int MAX_SIMULATED = 10;

	public static int simulatedDrifters() {
		return Mth.clamp(data.simulatedDrifters, 0, MAX_SIMULATED);
	}

	public static void setSimulatedDrifters(int count) {
		data.simulatedDrifters = Mth.clamp(count, 0, MAX_SIMULATED);
		save();
	}

	private static Data data = new Data();

	private DrifterConfig() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("drifter.json");
	}

	public static void load() {
		Path file = file();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				Data loaded = GSON.fromJson(reader, Data.class);
				if (loaded != null) {
					data = loaded;
				}
			} catch (Exception e) {
				Drifter.LOGGER.error("Couldn't read {}, using defaults", file, e);
			}
		}

		data.sightChunks = Mth.clamp(data.sightChunks, MIN_SIGHT_CHUNKS, MAX_SIGHT_CHUNKS);
		data.pathRange = Mth.clamp(data.pathRange, MIN_PATH_RANGE, MAX_PATH_RANGE);
		data.fov = data.fov == 0 ? DEFAULT_FOV : Mth.clamp(data.fov, MIN_FOV, MAX_FOV);
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(file().getParent());
			try (Writer writer = Files.newBufferedWriter(file())) {
				GSON.toJson(data, writer);
			}
		} catch (Exception e) {
			Drifter.LOGGER.error("Couldn't save {}", file(), e);
		}
	}

	public static int sightChunks() {
		return data.sightChunks;
	}

	/** Daylight sight range in blocks, for entities and far sight. */
	public static double sightRange() {
		return data.sightChunks * 16.0;
	}

	public static void setSightChunks(int chunks) {
		data.sightChunks = Mth.clamp(chunks, MIN_SIGHT_CHUNKS, MAX_SIGHT_CHUNKS);
		save();
	}

	/** How far (blocks, each way) one path search reaches; farther targets get a partial path and a new search there. */
	public static int pathRange() {
		return data.pathRange;
	}

	public static void setPathRange(int blocks) {
		data.pathRange = Mth.clamp(blocks, MIN_PATH_RANGE, MAX_PATH_RANGE);
		save();
	}

	public static int fov() {
		return data.fov;
	}

	public static void setFov(int degrees) {
		data.fov = Mth.clamp(degrees, MIN_FOV, MAX_FOV);
		save();
	}

	public static boolean chatLines() {
		return data.chatLines;
	}

	public static void setChatLines(boolean on) {
		data.chatLines = on;
		save();
	}

	public static boolean raiding() {
		return data.raiding;
	}

	public static void setRaiding(boolean on) {
		data.raiding = on;
		save();
	}

	public static boolean logThoughts() {
		return data.logThoughts;
	}

	public static void setLogThoughts(boolean on) {
		data.logThoughts = on;
		save();
	}

	public static boolean shareReputation() {
		return data.shareReputation;
	}

	public static void setShareReputation(boolean on) {
		data.shareReputation = on;
		save();
	}

	/** Half the view cone's height, degrees. */
	public static float coneHalfPitch() {
		return data.fov / 2.0F;
	}

	/** Half the view cone's width, degrees: the vertical FOV spread over a 16:9 screen. */
	public static float coneHalfYaw() {
		double halfPitch = Math.toRadians(data.fov / 2.0);
		return (float)Math.min(MAX_HALF_YAW, Math.toDegrees(Math.atan(Math.tan(halfPitch) * SCREEN_ASPECT)));
	}

	/** Search nodes allowed per path, growing with the area the search covers. */
	public static int pathNodes() {
		return (int)Math.min(MAX_NODES, NODES_PER_AREA * data.pathRange * data.pathRange);
	}
}
