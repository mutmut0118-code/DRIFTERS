package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterRecord;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * "One island" mode (docs/CIVILIZATION.md part 7), the way the civilization videos start: a crowd of new Drifters wakes up
 * in one place with nothing, and the days go by. Where: wherever it's started (stand on an island, or anywhere). They keep
 * to the island (they don't explore past its edge: {@code DrifterApi.setRoam}); its land stays loaded wherever the player
 * goes, so it lives on while they're away (force-loaded chunks, as `/forceload` does). The chronicle tells what happens.
 */
public final class Island {
	private Island() {
	}

	public static final int DEFAULT_COUNT = 50;
	public static final int MAX_COUNT = 100;
	public static final int DEFAULT_RADIUS = 64;
	public static final int MAX_RADIUS = 160;
	/** Land kept loaded past where they keep to (what they see and walk to at the shore). */
	private static final int LOADED_MARGIN = 32;
	private static final int SAVE_VERSION = 1;

	private static boolean active;
	@Nullable
	private static BlockPos center;
	private static int radius;
	private static String dimension = "minecraft:overworld";
	private static long startedDay;
	private static final Set<UUID> islanders = new LinkedHashSet<>();

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(Island::load);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			active = false;
			center = null;
			islanders.clear();
		});
	}

	public static boolean active() {
		return active;
	}

	/**
	 * Starts it round {@code where}: {@code count} new Drifters on dry land within {@code size} blocks, keeping to there; the
	 * land kept loaded. Returns how many it put down, or -1 if one is going already.
	 */
	public static int start(ServerLevel level, BlockPos where, int count, int size) {
		if (active) {
			return -1;
		}

		center = where.immutable();
		radius = size;
		dimension = level.dimension().location().toString();
		islanders.clear();
		setLoaded(level, true);
		RandomSource random = level.getRandom();
		int put = 0;
		for (int i = 0; i < count; i++) {
			for (int attempt = 0; attempt < 20; attempt++) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				double distance = size * 0.8 * Math.sqrt(random.nextDouble());
				int x = (int)Math.floor(where.getX() + 0.5 + Math.cos(angle) * distance);
				int z = (int)Math.floor(where.getZ() + 0.5 + Math.sin(angle) * distance);
				level.getChunk(x >> 4, z >> 4);
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
				// Dry ground: not water or lava, not on leaves.
				if (!level.getFluidState(top.below()).isEmpty() || !level.getFluidState(top).isEmpty()
					|| level.getBlockState(top.below()).getCollisionShape(level, top.below()).isEmpty()) {
					continue;
				}

				DrifterEntity drifter = DrifterApi.spawn(level, top, null);
				if (drifter != null) {
					DrifterApi.setRoam(drifter, center, size);
					islanders.add(drifter.getUUID());
					put++;
				}

				break;
			}
		}

		active = true;
		startedDay = Chronicle.day(level.getServer());
		Chronicle.add(level.getServer(), "island", put + " Drifters woke up on the island with nothing (near " + where.getX() + ", " + where.getZ()
			+ ", " + size + " blocks across each way)");
		save(level.getServer());
		return put;
	}

	/** Ends it: the land is let go (it unloads as usual), and those still loaded may go where they like. */
	public static boolean stop(MinecraftServer server) {
		if (!active) {
			return false;
		}

		ServerLevel level = level(server);
		if (level != null) {
			setLoaded(level, false);
			for (UUID id : islanders) {
				if (level.getEntity(id) instanceof DrifterEntity drifter) {
					DrifterApi.setRoam(drifter, null, 0);
				}
			}
		}

		Chronicle.add(server, "island", "The island was left to itself");
		active = false;
		save(server);
		return true;
	}

	/** How it's going, in a few lines. */
	public static List<String> status(MinecraftServer server) {
		List<String> lines = new ArrayList<>();
		if (center == null) {
			lines.add("No island yet: /drifterciv island start [count] [size]");
			return lines;
		}

		int alive = 0;
		int dead = 0;
		int gone = 0;
		int homes = 0;
		Set<UUID> groups = new LinkedHashSet<>();
		for (UUID id : islanders) {
			DrifterRecord record = DrifterApi.record(id);
			if (record == null) {
				continue;
			}

			switch (record.status()) {
				case ALIVE -> alive++;
				case DEAD -> dead++;
				case GONE -> gone++;
			}

			if (record.home() != null) {
				homes++;
			}

			if (record.group() != null && record.status() != DrifterRecord.Status.GONE) {
				groups.add(record.group());
			}
		}

		long day = Chronicle.day(server) - startedDay + 1;
		lines.add(String.format(java.util.Locale.ROOT, "The island (%s), near %d, %d, %d blocks each way: day %d", active ? "going" : "left to itself",
			center.getX(), center.getZ(), radius, day));
		lines.add(String.format(java.util.Locale.ROOT, "%d of %d alive, %d dead and coming back, %d gone for good; %d with a home, %d groups",
			alive, islanders.size(), dead, gone, homes, groups.size()));
		return lines;
	}

	/** Its land loaded (or let go): every chunk within its size and a margin. */
	private static void setLoaded(ServerLevel level, boolean loaded) {
		if (center == null) {
			return;
		}

		int reach = radius + LOADED_MARGIN;
		for (int cx = (center.getX() - reach) >> 4; cx <= (center.getX() + reach) >> 4; cx++) {
			for (int cz = (center.getZ() - reach) >> 4; cz <= (center.getZ() + reach) >> 4; cz++) {
				level.setChunkForced(cx, cz, loaded);
			}
		}
	}

	@Nullable
	private static ServerLevel level(MinecraftServer server) {
		ResourceLocation id = ResourceLocation.tryParse(dimension);
		return id == null ? null : server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
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
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "drifter_civ_island");
	}

	private static void load(MinecraftServer server) {
		CompoundTag tag = state(server).tag;
		// Version 1 is the first format.
		active = tag.getBoolean("Active");
		center = tag.contains("Center") ? BlockPos.of(tag.getLong("Center")) : null;
		radius = tag.getInt("Radius");
		dimension = tag.contains("Dimension") ? tag.getString("Dimension") : Level.OVERWORLD.location().toString();
		startedDay = tag.getLong("StartedDay");
		islanders.clear();
		for (Tag t : tag.getList("Islanders", Tag.TAG_INT_ARRAY)) {
			islanders.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
		}
	}

	private static void save(MinecraftServer server) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", SAVE_VERSION);
		tag.putBoolean("Active", active);
		if (center != null) {
			tag.putLong("Center", center.asLong());
		}

		tag.putInt("Radius", radius);
		tag.putString("Dimension", dimension);
		tag.putLong("StartedDay", startedDay);
		ListTag list = new ListTag();
		islanders.forEach(id -> list.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
		tag.put("Islanders", list);
		State state = state(server);
		state.tag = tag;
		state.setDirty();
	}
}
