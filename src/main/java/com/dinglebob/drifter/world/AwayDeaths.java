package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterRecord;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * A Drifter that died far from everyone, by something else's reckoning (an addon's town, living its days on paper while
 * nobody's near: the user, 2026-11-11, "account for all death types (respawn, lives, permanent)"). The death is real, by
 * the world's own rule: decided at once from its record (does it come back?), and done to the Drifter itself when it's
 * next loaded (or at once, if it's loaded now): gone for good, it's removed; coming back, it's at its bed with nothing,
 * one death more behind it, and remembers dying. What it carried is lost (it died far from here, wherever that was), as
 * catch-up's own deaths always had it.
 */
public final class AwayDeaths {
	/** One death waiting for its Drifter to load. */
	private record Entry(UUID id, String cause, boolean gone, long at) {
		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putUUID("Id", this.id);
			tag.putString("Cause", this.cause);
			tag.putBoolean("Gone", this.gone);
			tag.putLong("At", this.at);
			return tag;
		}

		static Entry load(CompoundTag tag) {
			return tag.hasUUID("Id") ? new Entry(tag.getUUID("Id"), tag.getString("Cause"), tag.getBoolean("Gone"), tag.getLong("At")) : null;
		}
	}

	private static final List<Entry> pending = new ArrayList<>();
	private static boolean dirty;
	/** Whose deaths while away are decided elsewhere (catch-up rolls none of its own for them). */
	private static Predicate<DrifterEntity> decidedElsewhere = drifter -> false;

	private AwayDeaths() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(AwayDeaths::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(AwayDeaths::save);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> pending.clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!pending.isEmpty() && server.getTickCount() % 5 == 0) {
				sweep(server);
			}

			if (dirty && server.getTickCount() % 200 == 0) {
				save(server);
			}
		});
	}

	public static void decidedBy(Predicate<DrifterEntity> whose) {
		decidedElsewhere = whose;
	}

	/** Is this one's death while away someone else's to decide? */
	public static boolean decidedElsewhere(DrifterEntity drifter) {
		try {
			return decidedElsewhere.test(drifter);
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Would a death now be one it comes back from (by the setting and the deaths its record has)? */
	public static boolean wouldComeBack(UUID id) {
		DrifterRecord record = DrifterRegistry.record(id);
		int deaths = record == null ? 0 : record.deaths();
		return switch (DrifterConfig.respawnMode()) {
			case BED -> true;
			case LIVES -> deaths + 1 <= DrifterConfig.lives();
			case PERMADEATH -> false;
		};
	}

	/** It died, away. See {@link DrifterApi#dieAway}. */
	public static DrifterApi.AwayDeath die(MinecraftServer server, UUID id, String cause) {
		DrifterRecord record = DrifterRegistry.record(id);
		if (record == null || record.status() != DrifterRecord.Status.ALIVE || gone(id)) {
			return DrifterApi.AwayDeath.UNKNOWN;
		}

		boolean back = wouldComeBack(id);
		DrifterRegistry.diedAway(id, !back);
		if (!back) {
			DrifterGroups.leave(id);
		}

		pending.add(new Entry(id, cause, !back, server.overworld().getGameTime()));
		dirty = true;
		Drifter.LOGGER.info("[Drifter] {} died while away ({}): {}", record.name(), cause, back ? "it comes back" : "gone for good");
		sweep(server);
		return back ? DrifterApi.AwayDeath.BACK : DrifterApi.AwayDeath.GONE;
	}

	/** Is it waiting to be removed (gone for good, not loaded since)? */
	public static boolean gone(UUID id) {
		for (Entry entry : pending) {
			if (entry.gone && entry.id.equals(id)) {
				return true;
			}
		}

		return false;
	}

	/** Those loaded now: what was decided for them is done. */
	private static void sweep(MinecraftServer server) {
		for (Iterator<Entry> it = pending.iterator(); it.hasNext();) {
			Entry entry = it.next();
			DrifterEntity drifter = null;
			for (ServerLevel level : server.getAllLevels()) {
				if (level.getEntity(entry.id) instanceof DrifterEntity found && found.isAlive()) {
					drifter = found;
					break;
				}
			}

			if (drifter == null) {
				continue;
			}

			it.remove();
			dirty = true;
			apply(drifter, entry);
		}
	}

	private static void apply(DrifterEntity drifter, Entry entry) {
		ServerLevel level = (ServerLevel)drifter.level();
		// What it carried: lost where it died.
		var inventory = drifter.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			inventory.setItem(slot, ItemStack.EMPTY);
		}

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			drifter.setItemSlot(slot, ItemStack.EMPTY);
		}

		if (entry.gone) {
			Drifter.LOGGER.info("[Drifter] {} died while away ({}): gone for good, removed", drifter.getName().getString(), entry.cause);
			// (Its record: gone, as it's removed.)
			drifter.discard();
			return;
		}

		drifter.recordDeath(level.getGameTime(), new Respawns.Corpse(drifter.blockPosition(), level.dimension().location(), entry.at, 0, null, entry.cause));
		drifter.setHealth(drifter.getMaxHealth());
		drifter.getFoodData().setFoodLevel(20);
		drifter.clearFire();
		Home home = drifter.getDrifterBrain().home();
		Vec3 spot = home == null ? null : Respawns.spot(level, home);
		if (spot != null && level.isLoaded(net.minecraft.core.BlockPos.containing(spot))) {
			drifter.teleportTo(spot.x, spot.y, spot.z);
			drifter.setDeltaMovement(Vec3.ZERO);
		}

		int lives = DrifterConfig.lives();
		String life = DrifterConfig.respawnMode() == DrifterConfig.RespawnMode.LIVES ? " (" + Math.max(0, lives - drifter.deaths()) + " of " + lives + " lives left)" : "";
		drifter.getMemory().thoughts().add(level.getGameTime(), "died while everyone was away (" + entry.cause + "): back at home, with nothing" + life);
		DrifterRegistry.respawned(drifter);
		Drifter.LOGGER.info("[Drifter] {} died while away ({}): back at home with nothing{}", drifter.getName().getString(), entry.cause, life);
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the world
	// ---------------------------------------------------------------------------------------------

	private static final class State extends SavedData {
		ListTag list = new ListTag();

		static State load(CompoundTag tag, HolderLookup.Provider registries) {
			State state = new State();
			state.list = tag.getList("Pending", Tag.TAG_COMPOUND);
			return state;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putInt("Version", 1);
			tag.put("Pending", this.list);
			return tag;
		}
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "drifter_away_deaths");
	}

	private static void load(MinecraftServer server) {
		pending.clear();
		for (Tag tag : state(server).list) {
			Entry entry = tag instanceof CompoundTag compound ? Entry.load(compound) : null;
			if (entry != null) {
				pending.add(entry);
			}
		}
	}

	private static void save(MinecraftServer server) {
		ListTag list = new ListTag();
		pending.forEach(entry -> list.add(entry.save()));
		State state = state(server);
		state.list = list;
		state.setDirty();
		dirty = false;
	}
}
