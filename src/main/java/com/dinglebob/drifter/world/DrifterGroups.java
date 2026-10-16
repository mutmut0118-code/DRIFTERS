package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Drifter groups (DESIGN.md section 21 "Groups", milestone 12 step 2): up to four Drifters who trust each other, with a
 * leader and a name. Kept with the world (members come and go from the loaded world), keyed by member.
 */
public final class DrifterGroups {
	public static final int MAX_SIZE = 4;
	private static final int SAVE_TICKS = 200;

	/** A group: its name, its leader (who sets its base), and its members (leader included). */
	public static final class Group {
		public final UUID id;
		public String name;
		public UUID leader;
		public final List<UUID> members = new ArrayList<>();
		public final Map<UUID, String> names = new HashMap<>();

		Group(UUID id, String name, UUID leader) {
			this.id = id;
			this.name = name;
			this.leader = leader;
		}

		public boolean full() {
			return this.members.size() >= MAX_SIZE;
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putUUID("Id", this.id);
			tag.putString("Name", this.name);
			tag.putUUID("Leader", this.leader);
			ListTag list = new ListTag();
			for (UUID member : this.members) {
				CompoundTag m = new CompoundTag();
				m.putUUID("Id", member);
				m.putString("Name", this.names.getOrDefault(member, "?"));
				list.add(m);
			}

			tag.put("Members", list);
			return tag;
		}

		static Group load(CompoundTag tag) {
			Group group = new Group(tag.getUUID("Id"), tag.getString("Name"), tag.getUUID("Leader"));
			for (Tag t : tag.getList("Members", Tag.TAG_COMPOUND)) {
				CompoundTag m = (CompoundTag)t;
				group.members.add(m.getUUID("Id"));
				group.names.put(m.getUUID("Id"), m.getString("Name"));
			}

			return group;
		}
	}

	private static final Map<UUID, Group> groups = new HashMap<>();
	private static final Map<UUID, Group> byMember = new HashMap<>();
	private static boolean dirty;

	private DrifterGroups() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(DrifterGroups::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(DrifterGroups::save);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			groups.clear();
			byMember.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (dirty && server.getTickCount() % SAVE_TICKS == 0) {
				save(server);
			}
		});
	}

	@Nullable
	public static Group of(UUID member) {
		return byMember.get(member);
	}

	public static List<Group> all() {
		return new ArrayList<>(groups.values());
	}

	/** A new group of two, the first one leading. */
	public static Group form(UUID leader, String leaderName, UUID other, String otherName, String name) {
		Group group = new Group(UUID.randomUUID(), name, leader);
		groups.put(group.id, group);
		join(group, leader, leaderName);
		join(group, other, otherName);
		return group;
	}

	public static void join(Group group, UUID member, String name) {
		if (!group.members.contains(member)) {
			group.members.add(member);
		}

		group.names.put(member, name);
		byMember.put(member, group);
		dirty = true;
	}

	/** It leaves (or died for good): the group goes on without it, or ends when one is left. */
	public static void leave(UUID member) {
		Group group = byMember.remove(member);
		if (group == null) {
			return;
		}

		group.members.remove(member);
		group.names.remove(member);
		if (group.members.size() < 2) {
			group.members.forEach(byMember::remove);
			groups.remove(group.id);
		} else if (group.leader.equals(member)) {
			group.leader = group.members.get(0);
		}

		dirty = true;
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the world
	// ---------------------------------------------------------------------------------------------

	private static final class State extends SavedData {
		ListTag list = new ListTag();

		static State load(CompoundTag tag, HolderLookup.Provider registries) {
			State state = new State();
			state.list = tag.getList("Groups", Tag.TAG_COMPOUND);
			return state;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.put("Groups", this.list);
			return tag;
		}
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "drifter_groups");
	}

	private static void load(MinecraftServer server) {
		groups.clear();
		byMember.clear();
		for (Tag t : state(server).list) {
			Group group = Group.load((CompoundTag)t);
			groups.put(group.id, group);
			group.members.forEach(member -> byMember.put(member, group));
		}
	}

	private static void save(MinecraftServer server) {
		ListTag list = new ListTag();
		for (Group group : groups.values()) {
			list.add(group.save());
		}

		State state = state(server);
		state.list = list;
		state.setDirty();
		dirty = false;
	}
}
