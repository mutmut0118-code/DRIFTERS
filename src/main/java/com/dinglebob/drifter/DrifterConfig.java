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
	/** Search nodes per block of path range squared, from the tested 8000 nodes for 48 blocks. */
	private static final double NODES_PER_AREA = 8000.0 / (48.0 * 48.0);
	private static final int MAX_NODES = 60000;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** The saved file's shape. */
	private static final class Data {
		int version = 1;
		int sightChunks = DEFAULT_SIGHT_CHUNKS;
		int pathRange = DEFAULT_PATH_RANGE;
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

	/** Search nodes allowed per path, growing with the area the search covers. */
	public static int pathNodes() {
		return (int)Math.min(MAX_NODES, NODES_PER_AREA * data.pathRange * data.pathRange);
	}
}
