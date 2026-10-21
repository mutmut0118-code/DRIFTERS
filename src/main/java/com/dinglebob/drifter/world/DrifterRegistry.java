package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterRecord;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Trait;
import com.dinglebob.drifter.social.Relationship;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The world's directory of Drifters (DESIGN.md section 20's world-level registry; milestone 22 groundwork, the user
 * 2026-10-21): every Drifter that has lived here, kept while it's far away and unloaded. Who it is, whether it's alive,
 * dead and coming back, or gone for good; where it lives and was last; its group, how far along it is, its personality,
 * and how it feels about its closest Drifters and the players it knows ({@link DrifterRecord}).
 *
 * <p>Loaded Drifters are brought up to date as they go: their position every five seconds, the rest about once a minute
 * (each its own moment, so a thousand don't all at once), and in full when their area unloads. Saved with the world.
 * Before, this was only names and positions (`/drifter list`, `/drifter tp`), forgotten at death.
 *
 * <p>For the world's own bookkeeping (commands, an addon's town summaries): nothing a Drifter decides reads it.
 */
public final class DrifterRegistry {
	private static final int UPDATE_TICKS = 100;
	/** A full snapshot every this many updates (a minute). */
	private static final int FULL_EVERY = 12;
	/** The closest few kept per Drifter (by how strongly it feels either way). */
	private static final int CLOSEST = 16;

	/** Where one was last (the old shape: commands and the wanted poster use it). */
	public record Known(UUID id, String name, ResourceKey<Level> level, BlockPos pos, long seen) {
	}

	private static final Map<UUID, DrifterRecord> records = new LinkedHashMap<>();
	private static boolean dirty;
	private static int updates;

	private DrifterRegistry() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(DrifterRegistry::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			update(server, true);
			save(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> records.clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % UPDATE_TICKS == 0) {
				update(server, false);
				if (dirty) {
					save(server);
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (!(entity instanceof DrifterEntity drifter) || drifter.isTemporary()) {
				return;
			}

			Entity.RemovalReason reason = entity.getRemovalReason();
			if (reason == Entity.RemovalReason.KILLED) {
				// (Its death is noted as it dies: coming back or gone.)
				return;
			}

			if (reason == Entity.RemovalReason.DISCARDED && !DrifterTravel.isTraveling(entity.getUUID())) {
				// Removed (a command, not travel): gone, kept for the history.
				setStatus(entity.getUUID(), DrifterRecord.Status.GONE, -1);
			} else if (reason != Entity.RemovalReason.CHANGED_DIMENSION) {
				// (Without looking at the world: this runs while the game is unloading chunks, and reading a block in a chunk
				// that's gone loads it again in the middle of that, which crashed a 50-Drifter test, "ConcurrentModificationException
				// in DistanceManager".)
				remember(drifter, true, false);
			}
		});
		// Its death: dead and coming back, or gone for good.
		com.dinglebob.drifter.api.DrifterEvents.DIED.register((drifter, source, comesBack) -> {
			if (!drifter.isTemporary() && !Battle.inBattle(drifter)) {
				// (Its record as it was before it died: its things are on the ground now.)
				if (!records.containsKey(drifter.getUUID())) {
					remember(drifter, true, true);
				}

				setStatus(drifter.getUUID(), comesBack ? DrifterRecord.Status.DEAD : DrifterRecord.Status.GONE, drifter.deaths());
			}
		});
	}

	private static void update(MinecraftServer server, boolean full) {
		updates++;
		for (ServerLevel level : server.getAllLevels()) {
			for (DrifterEntity drifter : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(DrifterEntity.class), DrifterEntity::isAlive)) {
				remember(drifter, full || Math.floorMod(updates + drifter.getId(), FULL_EVERY) == 0, true);
			}
		}
	}

	/**
	 * Brings its record up to date: where it is now (cheap), or everything ({@code full}). {@code lookAtWorld} false: its bed
	 * is taken as last recorded (nothing read from the world).
	 */
	private static void remember(DrifterEntity drifter, boolean full, boolean lookAtWorld) {
		// (A delver or an encounter's Drifter is met once: never listed.)
		if (drifter.isTemporary()) {
			return;
		}

		DrifterRecord old = records.get(drifter.getUUID());
		long now = drifter.level().getGameTime();
		if (old != null && !full) {
			records.put(drifter.getUUID(), old.seenAt(drifter.level().dimension(), drifter.blockPosition(), now));
		} else {
			records.put(drifter.getUUID(), snapshot(drifter, old, now, lookAtWorld));
		}

		dirty = true;
	}

	private static DrifterRecord snapshot(DrifterEntity drifter, @Nullable DrifterRecord old, long now, boolean lookAtWorld) {
		Home home = drifter.getDrifterBrain().home();
		// (Its bed is checked only where the chunk is loaded: never loads one.)
		BlockPos bed = home == null ? null : lookAtWorld && drifter.level().isLoaded(home.center) && (home.houseBed == null || drifter.level().isLoaded(home.houseBed))
			? home.bedOrNull(drifter.level()) : old != null ? old.bed() : null;
		var group = DrifterGroups.of(drifter.getUUID());
		Map<String, Double> traits = new LinkedHashMap<>();
		for (Trait trait : Trait.values()) {
			traits.put(trait.name().toLowerCase(Locale.ROOT), drifter.getPersonality().get(trait));
		}

		return new DrifterRecord(drifter.getUUID(), drifter.getName().getString(), DrifterRecord.Status.ALIVE, drifter.level().dimension(), drifter.blockPosition(),
			now, old != null ? old.firstSeen() : now, home == null ? null : home.dimension, home == null ? null : home.center, bed, group == null ? null : group.id,
			com.dinglebob.drifter.brain.Strategist.stage(drifter), drifter.deaths(), drifter.getPersonality().archetype().name().toLowerCase(Locale.ROOT),
			drifter.getSocial().disposition().name().toLowerCase(Locale.ROOT), traits, closest(drifter.getBonds().all()), closest(drifter.getSocial().all()));
	}

	/** The closest few, by how strongly it feels about them either way. */
	private static Map<UUID, Double> closest(Collection<Relationship> all) {
		List<Relationship> sorted = new ArrayList<>(all);
		sorted.sort(Comparator.comparingDouble((Relationship r) -> Math.abs(r.trust)).reversed());
		Map<UUID, Double> closest = new LinkedHashMap<>();
		for (Relationship r : sorted.subList(0, Math.min(CLOSEST, sorted.size()))) {
			closest.put(r.player, r.trust);
		}

		return closest;
	}

	private static void setStatus(UUID id, DrifterRecord.Status status, int deaths) {
		DrifterRecord old = records.get(id);
		if (old != null) {
			records.put(id, old.withStatus(status, deaths >= 0 ? deaths : old.deaths()));
			dirty = true;
		}
	}

	/** Back from the dead: alive again (its full record follows within the minute). */
	public static void respawned(DrifterEntity drifter) {
		remember(drifter, true, true);
	}

	/** Out of the directory altogether (a debug clean-up). */
	public static void forget(UUID id) {
		if (records.remove(id) != null) {
			dirty = true;
		}
	}

	/** Every Drifter in the world now (alive, or dead and coming back), by name: where each was last. */
	public static List<Known> all() {
		List<Known> list = new ArrayList<>();
		for (DrifterRecord r : records.values()) {
			if (r.status() != DrifterRecord.Status.GONE) {
				list.add(new Known(r.id(), r.name(), r.level(), r.lastPos(), r.lastSeen()));
			}
		}

		list.sort(Comparator.comparing(k -> k.name().toLowerCase(Locale.ROOT)));
		return list;
	}

	/** Every Drifter that has lived in this world, gone ones too. */
	public static Collection<DrifterRecord> records() {
		return java.util.Collections.unmodifiableCollection(records.values());
	}

	@Nullable
	public static DrifterRecord record(UUID id) {
		return records.get(id);
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the world
	// ---------------------------------------------------------------------------------------------

	private static final class State extends SavedData {
		ListTag list = new ListTag();

		static State load(CompoundTag tag, HolderLookup.Provider registries) {
			State state = new State();
			state.list = tag.getList("Drifters", Tag.TAG_COMPOUND);
			return state;
		}

		/** Built here, when the world is actually saved, not every time something changed (milestone 16: 1000 Drifters). */
		boolean fresh;

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			if (this.fresh) {
				this.list = listOf();
				this.fresh = false;
			}

			// Version 2: the directory (version 1, before 2026-10-21, was names and positions only).
			tag.putInt("Version", 2);
			tag.put("Drifters", this.list);
			return tag;
		}
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "drifter_registry");
	}

	private static void save(MinecraftServer server) {
		State state = state(server);
		state.fresh = true;
		state.setDirty();
		dirty = false;
	}

	private static ListTag listOf() {
		ListTag list = new ListTag();
		for (DrifterRecord r : records.values()) {
			CompoundTag tag = new CompoundTag();
			tag.putUUID("Id", r.id());
			tag.putString("Name", r.name());
			tag.putString("Status", r.status().name());
			tag.putString("Level", r.level().location().toString());
			tag.putLong("Pos", r.lastPos().asLong());
			tag.putLong("Seen", r.lastSeen());
			tag.putLong("FirstSeen", r.firstSeen());
			if (r.home() != null && r.homeDimension() != null) {
				tag.putString("HomeLevel", r.homeDimension().toString());
				tag.putLong("Home", r.home().asLong());
			}

			if (r.bed() != null) {
				tag.putLong("Bed", r.bed().asLong());
			}

			if (r.group() != null) {
				tag.putUUID("Group", r.group());
			}

			tag.putInt("Stage", r.stage());
			tag.putInt("Deaths", r.deaths());
			tag.putString("Archetype", r.archetype());
			tag.putString("Disposition", r.disposition());
			tag.put("Traits", doubles(r.traits()));
			tag.put("Drifters", byId(r.drifterTrust()));
			tag.put("Players", byId(r.playerTrust()));
			list.add(tag);
		}

		return list;
	}

	private static CompoundTag doubles(Map<String, Double> map) {
		CompoundTag tag = new CompoundTag();
		map.forEach(tag::putDouble);
		return tag;
	}

	private static CompoundTag byId(Map<UUID, Double> map) {
		CompoundTag tag = new CompoundTag();
		map.forEach((id, value) -> tag.putDouble(id.toString(), value));
		return tag;
	}

	private static void load(MinecraftServer server) {
		records.clear();
		for (Tag t : state(server).list) {
			CompoundTag tag = (CompoundTag)t;
			ResourceLocation level = ResourceLocation.tryParse(tag.getString("Level"));
			if (level == null) {
				continue;
			}

			UUID id = tag.getUUID("Id");
			DrifterRecord.Status status;
			try {
				status = tag.contains("Status") ? DrifterRecord.Status.valueOf(tag.getString("Status")) : DrifterRecord.Status.ALIVE;
			} catch (IllegalArgumentException e) {
				status = DrifterRecord.Status.ALIVE;
			}

			ResourceLocation homeLevel = tag.contains("HomeLevel") ? ResourceLocation.tryParse(tag.getString("HomeLevel")) : null;
			Map<String, Double> traits = new LinkedHashMap<>();
			CompoundTag t2 = tag.getCompound("Traits");
			for (String key : t2.getAllKeys()) {
				traits.put(key, t2.getDouble(key));
			}

			long seen = tag.getLong("Seen");
			records.put(id, new DrifterRecord(id, tag.getString("Name"), status, ResourceKey.create(Registries.DIMENSION, level), BlockPos.of(tag.getLong("Pos")),
				seen, tag.contains("FirstSeen") ? tag.getLong("FirstSeen") : seen, homeLevel, homeLevel == null ? null : BlockPos.of(tag.getLong("Home")),
				tag.contains("Bed") ? BlockPos.of(tag.getLong("Bed")) : null, tag.hasUUID("Group") ? tag.getUUID("Group") : null, tag.getInt("Stage"),
				tag.getInt("Deaths"), tag.getString("Archetype"), tag.getString("Disposition"), traits, ids(tag.getCompound("Drifters")),
				ids(tag.getCompound("Players"))));
		}

		// Worlds from before the directory: the ones loaded now are filled in within seconds.
		update(server, true);
	}

	private static Map<UUID, Double> ids(CompoundTag tag) {
		Map<UUID, Double> map = new LinkedHashMap<>();
		for (String key : tag.getAllKeys()) {
			try {
				map.put(UUID.fromString(key), tag.getDouble(key));
			} catch (IllegalArgumentException e) {
				// (A bad entry: skipped.)
			}
		}

		return map;
	}
}
