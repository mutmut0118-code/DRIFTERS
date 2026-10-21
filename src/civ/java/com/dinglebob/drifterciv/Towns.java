package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

/** The world's towns, saved with it (docs/CIVILIZATION.md part 1). */
public final class Towns {
	private Towns() {
	}

	/** Towns are at least this far apart (the user, 2026-10-21: "go with your recommendations"). */
	public static final int MIN_SPACING = 300;
	private static final int SAVE_VERSION = 1;
	private static final String[] FIRST = {"Oak", "Iron", "Stone", "Ash", "Elder", "Raven", "Wolf", "Mill", "Brook", "Fair", "High", "Red", "Cold",
		"West", "Thorn", "Willow", "Copper", "Amber", "Hollow", "Pine"};
	private static final String[] LAST = {"ford", "vale", "hollow", "stead", "wick", "bury", "field", "ridge", "haven", "moor", "brook", "crest", "dale",
		"gate", "mere"};

	private static final Map<UUID, Town> towns = new LinkedHashMap<>();

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(Towns::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(Towns::save);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> towns.clear());
	}

	public static List<Town> all() {
		return new ArrayList<>(towns.values());
	}

	public static void add(MinecraftServer server, Town town) {
		towns.put(town.id, town);
		save(server);
	}

	/** The nearest town in this dimension, or null. */
	@Nullable
	public static Town nearest(String dimension, BlockPos pos) {
		Town best = null;
		for (Town town : towns.values()) {
			if (town.dimension.equals(dimension) && (best == null || town.center.distSqr(pos) < best.center.distSqr(pos))) {
				best = town;
			}
		}

		return best;
	}

	/** A town's name nobody has yet. */
	public static String newName(RandomSource random) {
		for (int i = 0; i < 50; i++) {
			String name = FIRST[random.nextInt(FIRST.length)] + LAST[random.nextInt(LAST.length)];
			if (towns.values().stream().noneMatch(town -> town.name.equals(name))) {
				return name;
			}
		}

		return "Town " + (towns.size() + 1);
	}

	// ----- Saved with the world -----

	private static final class State extends SavedData {
		CompoundTag tag = new CompoundTag();

		static State load(CompoundTag tag, HolderLookup.Provider registries) {
			State state = new State();
			state.tag = tag;
			return state;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.merge(this.tag);
			return tag;
		}
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "drifter_civ_towns");
	}

	private static void load(MinecraftServer server) {
		towns.clear();
		// Version 1 is the first format.
		for (Tag t : state(server).tag.getList("Towns", Tag.TAG_COMPOUND)) {
			Town town = Town.load((CompoundTag)t);
			towns.put(town.id, town);
		}
	}

	static void save(MinecraftServer server) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", SAVE_VERSION);
		ListTag list = new ListTag();
		towns.values().forEach(town -> list.add(town.save()));
		tag.put("Towns", list);
		State state = state(server);
		state.tag = tag;
		state.setDirty();
	}
}
