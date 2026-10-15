package com.dinglebob.drifter.world;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.DrifterConfig;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Offline progression (DESIGN.md section 22): the game's clock stops while a world is closed. With the "real time"
 * setting, the real time the world was closed counts too: the real-world time is kept with the world (when it stops,
 * and every minute in case it doesn't stop cleanly), and on the next start the gap becomes extra catch-up for every
 * Drifter saved before then, up to its own cap. A negative or absurd gap (a changed system clock) is ignored.
 */
public final class OfflineClock {
	/** Longer than this (real time) is a broken clock, not a holiday. */
	private static final long MAX_REAL_GAP_MS = 366L * 24 * 60 * 60 * 1000;
	private static final long MS_PER_TICK = 50L;

	/** Catch-up ticks for real time the world was closed, worked out at start (0 unless the setting is real time). */
	private static long offlineTicks;
	/** Game time when this session started: Drifters last saved before it get the offline time too. */
	private static long sessionStart = Long.MAX_VALUE;

	private OfflineClock() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			sessionStart = server.overworld().getGameTime();
			long saved = state(server).realTime;
			long gap = saved <= 0L ? 0L : System.currentTimeMillis() - saved;
			offlineTicks = 0L;
			if (DrifterConfig.offlineProgression() == DrifterConfig.OfflineProgression.REAL_TIME && gap > 0L && gap < MAX_REAL_GAP_MS) {
				offlineTicks = Math.min(gap / MS_PER_TICK, DrifterConfig.realTimeCapTicks());
				Drifter.LOGGER.info("The world was closed for {} min: Drifters catch up on {} more ticks", gap / 60000L, offlineTicks);
			}

			stamp(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(OfflineClock::stamp);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 1200 == 0) {
				stamp(server);
			}
		});
	}

	/** Extra catch-up for a Drifter last running at {@code lastActive} (game time). */
	public static long offlineTicksFor(long lastActive) {
		return lastActive < sessionStart ? offlineTicks : 0L;
	}

	private static void stamp(MinecraftServer server) {
		State state = state(server);
		state.realTime = System.currentTimeMillis();
		state.setDirty();
	}

	private static final class State extends SavedData {
		long realTime;

		static State load(CompoundTag tag, HolderLookup.Provider registries) {
			State state = new State();
			state.realTime = tag.getLong("RealTime");
			return state;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putLong("RealTime", this.realTime);
			return tag;
		}
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "drifter_clock");
	}
}
