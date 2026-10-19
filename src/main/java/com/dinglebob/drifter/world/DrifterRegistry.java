package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.dinglebob.drifter.entity.DrifterEntity;

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
 * Every Drifter in the world and where it was last (user's request: `/drifter list` and `/drifter tp` lost the ones
 * that wandered off out of the loaded area). Kept up to date every few seconds for loaded ones, kept when their area
 * unloads, forgotten when they die or are removed. Saved with the world. Debug and convenience only: nothing a Drifter
 * decides reads this.
 */
public final class DrifterRegistry {
	private static final int UPDATE_TICKS = 100;

	public record Known(UUID id, String name, ResourceKey<Level> level, BlockPos pos, long seen) {
	}

	private static final Map<UUID, Known> known = new HashMap<>();
	private static boolean dirty;

	private DrifterRegistry() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(DrifterRegistry::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			update(server);
			save(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> known.clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % UPDATE_TICKS == 0) {
				update(server);
				if (dirty) {
					save(server);
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (!(entity instanceof DrifterEntity drifter)) {
				return;
			}

			Entity.RemovalReason reason = entity.getRemovalReason();
			if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED && !DrifterTravel.isTraveling(entity.getUUID())) {
				forget(entity.getUUID());
			} else if (reason != Entity.RemovalReason.CHANGED_DIMENSION) {
				remember(drifter);
			}
		});
	}

	private static void update(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (DrifterEntity drifter : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(DrifterEntity.class), DrifterEntity::isAlive)) {
				remember(drifter);
			}
		}
	}

	private static void remember(DrifterEntity drifter) {
		known.put(drifter.getUUID(), new Known(drifter.getUUID(), drifter.getName().getString(), drifter.level().dimension(), drifter.blockPosition(),
			drifter.level().getGameTime()));
		dirty = true;
	}

	public static void forget(UUID id) {
		if (known.remove(id) != null) {
			dirty = true;
		}
	}

	/** Every Drifter it knows of, by name. */
	public static List<Known> all() {
		List<Known> list = new ArrayList<>(known.values());
		list.sort(Comparator.comparing(k -> k.name().toLowerCase(Locale.ROOT)));
		return list;
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
		for (Known k : known.values()) {
			CompoundTag tag = new CompoundTag();
			tag.putUUID("Id", k.id());
			tag.putString("Name", k.name());
			tag.putString("Level", k.level().location().toString());
			tag.putLong("Pos", k.pos().asLong());
			tag.putLong("Seen", k.seen());
			list.add(tag);
		}

		return list;
	}

	private static void load(MinecraftServer server) {
		known.clear();
		for (Tag t : state(server).list) {
			CompoundTag tag = (CompoundTag)t;
			ResourceLocation level = ResourceLocation.tryParse(tag.getString("Level"));
			if (level != null) {
				UUID id = tag.getUUID("Id");
				known.put(id, new Known(id, tag.getString("Name"), ResourceKey.create(Registries.DIMENSION, level), BlockPos.of(tag.getLong("Pos")),
					tag.getLong("Seen")));
			}
		}

		// Worlds from before this list: the ones loaded now are added within seconds.
		update(server);
	}
}
