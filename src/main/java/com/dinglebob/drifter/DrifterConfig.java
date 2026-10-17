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
		/** Drifter groups fall into feuds, skirmish and raid each other (section 21 "Drifter team wars"). */
		boolean drifterWars = true;
		/** Whims: the things they do just for fun (milestone 13, docs/WHIMS.md). */
		boolean whims = true;
		/** Outpost Bandits attack on sight and grief bases: doors, chests, fire, blocks (user's decision, 2026-10-17). */
		boolean banditGriefing = true;
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
