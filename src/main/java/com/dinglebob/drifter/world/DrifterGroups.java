package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Drifter groups (DESIGN.md section 21 "Groups", milestone 12 step 2): Drifters who trust each other (four at most by
 * default; the setting, or a group's own limit), with a leader and a name. Kept with the world (members come and go from
 * the loaded world), keyed by member.
 *
 * <p>Members live at the leader's base (milestone 22 groundwork, "shared things"): every few seconds, the members loaded
 * there share what they know of it with the leader and the leader with them (its chests, the house plan, the farm...:
 * {@link Home#shareFrom}), so they fill one stockpile and build one house; a member with no home moves in.
 */
public final class DrifterGroups {
	private static final int SAVE_TICKS = 200;
	/** How often members at the base share what they know of it. */
	private static final int SHARE_TICKS = 100;

	/** A group: its name, its leader (who sets its base), and its members (leader included). */
	public static final class Group {
		public final UUID id;
		public String name;
		public UUID leader;
		public final List<UUID> members = new ArrayList<>();
		public final Map<UUID, String> names = new HashMap<>();
		/** Its base (the leader's home) and that home's dimension, as last seen; null until known. */
		@Nullable
		public net.minecraft.core.BlockPos base;
		public String baseDimension = "minecraft:overworld";
		/** The base's chests, the group's stockpile, as the leader last knew them. */
		public final List<net.minecraft.core.BlockPos> chests = new ArrayList<>();
		/** Most members it takes (0: the setting, {@link com.dinglebob.drifter.DrifterConfig#groupSize()}). */
		public int limit;

		Group(UUID id, String name, UUID leader) {
			this.id = id;
			this.name = name;
			this.leader = leader;
		}

		public boolean full() {
			return this.members.size() >= this.limit();
		}

		public int limit() {
			return this.limit > 0 ? this.limit : com.dinglebob.drifter.DrifterConfig.groupSize();
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
			if (this.base != null) {
				tag.putLong("Base", this.base.asLong());
				tag.putString("BaseDimension", this.baseDimension);
			}

			tag.putLongArray("Chests", this.chests.stream().mapToLong(net.minecraft.core.BlockPos::asLong).toArray());
			tag.putInt("Limit", this.limit);
			return tag;
		}

		static Group load(CompoundTag tag) {
			Group group = new Group(tag.getUUID("Id"), tag.getString("Name"), tag.getUUID("Leader"));
			if (tag.contains("Base")) {
				group.base = net.minecraft.core.BlockPos.of(tag.getLong("Base"));
				group.baseDimension = tag.getString("BaseDimension");
			}

			for (long chest : tag.getLongArray("Chests")) {
				group.chests.add(net.minecraft.core.BlockPos.of(chest));
			}

			group.limit = tag.getInt("Limit");

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

			if (server.getTickCount() % SHARE_TICKS == 0) {
				groups.values().forEach(group -> shareBase(server, group));
			}
		});
	}

	/** The members loaded at the leader's base and the leader share what they know of it (and the homeless move in). */
	private static void shareBase(MinecraftServer server, Group group) {
		DrifterEntity leader = loaded(server, group.leader);
		var base = leader == null ? null : leader.getDrifterBrain().home();
		if (base == null || base.borrowed()) {
			return;
		}

		net.minecraft.world.level.Level level = leader.level();
		java.util.function.Predicate<net.minecraft.core.BlockPos> chestThere = pos -> level.isLoaded(pos)
			&& level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
		for (UUID id : group.members) {
			DrifterEntity member = id.equals(group.leader) ? null : loaded(server, id);
			if (member == null || member.level() != level) {
				continue;
			}

			var home = member.getDrifterBrain().home();
			if (home == null) {
				// (It joined before the leader had a home.)
				var shared = Home.load(base.save());
				if (shared != null) {
					member.getDrifterBrain().setHome(shared);
					member.getMemory().thoughts().add(level.getGameTime(), "moved in with " + leader.getName().getString());
				}

				continue;
			}

			String plan = home.planId;
			home.shareFrom(base, true, chestThere);
			base.shareFrom(home, false, chestThere);
			if (home.planId != null && !home.planId.equals(plan)) {
				member.getMemory().thoughts().add(level.getGameTime(), "building " + leader.getName().getString() + "'s house plan with the group");
			}
		}

		var chests = base.chests();
		if (!chests.equals(group.chests) || !base.center.equals(group.base)) {
			group.chests.clear();
			group.chests.addAll(chests);
			group.base = base.center;
			group.baseDimension = base.dimension.toString();
			dirty = true;
		}
	}

	@Nullable
	private static DrifterEntity loaded(MinecraftServer server, UUID id) {
		for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof DrifterEntity drifter && drifter.isAlive()) {
				return drifter;
			}
		}

		return null;
	}

	/** Something about a group changed (its base): saved soon. */
	public static void changed() {
		dirty = true;
	}

	@Nullable
	public static Group of(UUID member) {
		return byMember.get(member);
	}

	@Nullable
	public static Group byId(UUID id) {
		return groups.get(id);
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
		com.dinglebob.drifter.api.DrifterEvents.GROUP_CHANGED.invoker().onGroupChanged(member, group.id);
	}

	/** It leaves (or died for good): the group goes on without it, or ends when one is left. */
	public static void leave(UUID member) {
		Group group = byMember.remove(member);
		if (group == null) {
			return;
		}

		group.members.remove(member);
		group.names.remove(member);
		java.util.List<UUID> leftAlone = group.members.size() < 2 ? java.util.List.copyOf(group.members) : java.util.List.of();
		if (group.members.size() < 2) {
			group.members.forEach(byMember::remove);
			groups.remove(group.id);
		} else if (group.leader.equals(member)) {
			group.leader = group.members.get(0);
		}

		dirty = true;
		com.dinglebob.drifter.api.DrifterEvents.GROUP_CHANGED.invoker().onGroupChanged(member, null);
		// (The last one left: the group's gone, so it's out of one too.)
		leftAlone.forEach(last -> com.dinglebob.drifter.api.DrifterEvents.GROUP_CHANGED.invoker().onGroupChanged(last, null));
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
