package com.dinglebob.drifter.perception;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.GameEventTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * The sounds Drifters can hear (game events: steps, blocks, doors, eating, hits...), kept for a few ticks in a list per
 * chunk, for each Drifter to take in what's round it at its own senses tick (milestone 16). Before, each Drifter was its
 * own vibration listener, and the game handed every step of a crowd to every listener within 16 blocks, one by one:
 * in a 300-Drifter battle a tenth of the server's time. Now each sound is put down once (ServerLevel#gameEvent), and
 * each Drifter looks through the few chunks round its ears for the ones it hasn't had yet (by their numbers). Only
 * Drifters listen here: sculk and the Warden hear as ever.
 */
public final class SoundHub {
	private SoundHub() {
	}

	/** One sound: its number (they count up), its kind, where, and who made it (if anyone). */
	record Sound(long number, HeardSound.Kind kind, Vec3 pos, @Nullable Entity maker) {
	}

	/**
	 * Ticks of sounds kept: away from players a Drifter listens only every few ticks (every 5th at the Far detail tier),
	 * and takes in all it missed since (milestone 16 scale pass, 2026-11-08: two ticks were kept when it listened every
	 * other tick).
	 */
	private static final int KEEP_TICKS = 8;

	private static final class LevelSounds {
		long tick = Long.MIN_VALUE;
		long next = 1;
		/** One map (chunk to sounds) per tick, in a ring by tick; which tick each holds, and its newest sound's number. */
		@SuppressWarnings("unchecked")
		final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<List<Sound>>[] ring = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap[KEEP_TICKS];
		final long[] ringTick = new long[KEEP_TICKS];
		final long[] ringNewest = new long[KEEP_TICKS];

		LevelSounds() {
			for (int i = 0; i < KEEP_TICKS; i++) {
				this.ring[i] = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
				this.ringTick[i] = Long.MIN_VALUE;
			}
		}
	}

	private static final Map<ResourceKey<Level>, LevelSounds> levels = new HashMap<>();

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> levels.clear());
	}

	private static LevelSounds sounds(ServerLevel level) {
		LevelSounds sounds = levels.computeIfAbsent(level.dimension(), key -> new LevelSounds());
		long now = level.getServer().getTickCount();
		if (sounds.tick != now) {
			// This tick's slot in the ring: what it held (KEEP_TICKS ago, or older) goes.
			int slot = (int)Math.floorMod(now, (long)KEEP_TICKS);
			sounds.ring[slot].clear();
			sounds.ringTick[slot] = now;
			sounds.ringNewest[slot] = 0L;
			sounds.tick = now;
		}

		return sounds;
	}

	/** A game event happened (from ServerLevel#gameEvent). */
	public static void post(ServerLevel level, Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
		if (!level.getServer().isSameThread()) {
			return;
		}

		HeardSound.Kind kind = Hearing.kindOf(event);
		if (kind == null) {
			return;
		}

		Entity maker = context.sourceEntity();
		// Sneaking makes no step sounds (the vanilla "ignore vibrations while sneaking" tag).
		if (maker != null && maker.isSteppingCarefully() && event.is(GameEventTags.IGNORE_VIBRATIONS_SNEAKING)) {
			return;
		}

		LevelSounds sounds = sounds(level);
		int slot = (int)Math.floorMod(sounds.tick, (long)KEEP_TICKS);
		var current = sounds.ring[slot];
		long key = ChunkPos.asLong(Mth.floor(pos.x) >> 4, Mth.floor(pos.z) >> 4);
		List<Sound> list = current.get(key);
		if (list == null) {
			list = new ArrayList<>(4);
			current.put(key, list);
		}

		long number = sounds.next++;
		list.add(new Sound(number, kind, pos, maker));
		sounds.ringNewest[slot] = number;
	}

	/** The newest sound's number so far (a listener starting now hears from after it). */
	static long latest(ServerLevel level) {
		return sounds(level).next - 1;
	}

	/** The sounds within {@code radius} of {@code ear} numbered after {@code after}, oldest first, each to {@code hear}. */
	static void near(ServerLevel level, Vec3 ear, double radius, long after, java.util.function.Consumer<Sound> hear) {
		LevelSounds sounds = sounds(level);
		int minCx = Mth.floor(ear.x - radius) >> 4;
		int maxCx = Mth.floor(ear.x + radius) >> 4;
		int minCz = Mth.floor(ear.z - radius) >> 4;
		int maxCz = Mth.floor(ear.z + radius) >> 4;
		double radiusSqr = radius * radius;
		// Oldest tick first; ticks with nothing newer than what it has already had are passed over whole (one that listens
		// every tick reads only this tick's).
		for (int age = KEEP_TICKS - 1; age >= 0; age--) {
			int slot = (int)Math.floorMod(sounds.tick - age, (long)KEEP_TICKS);
			var map = sounds.ring[slot];
			if (sounds.ringTick[slot] != sounds.tick - age || sounds.ringNewest[slot] <= after || map.isEmpty()) {
				continue;
			}

			for (int cx = minCx; cx <= maxCx; cx++) {
				for (int cz = minCz; cz <= maxCz; cz++) {
					List<Sound> list = map.get(ChunkPos.asLong(cx, cz));
					if (list == null) {
						continue;
					}

					for (Sound sound : list) {
						if (sound.number() > after && sound.pos().distanceToSqr(ear) <= radiusSqr) {
							hear.accept(sound);
						}
					}
				}
			}
		}
	}
}
