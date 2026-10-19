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
 * The sounds Drifters can hear (game events: steps, blocks, doors, eating, hits...), kept for two ticks in a list per
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

	private static final class LevelSounds {
		long tick = Long.MIN_VALUE;
		long next = 1;
		it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<List<Sound>> current = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
		it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<List<Sound>> previous = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
	}

	private static final Map<ResourceKey<Level>, LevelSounds> levels = new HashMap<>();

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> levels.clear());
	}

	private static LevelSounds sounds(ServerLevel level) {
		LevelSounds sounds = levels.computeIfAbsent(level.dimension(), key -> new LevelSounds());
		long now = level.getServer().getTickCount();
		if (sounds.tick != now) {
			// Last tick's are kept a tick more (a Drifter far from players listens every other tick); older ones go.
			var old = sounds.previous;
			old.clear();
			if (now == sounds.tick + 1) {
				sounds.previous = sounds.current;
				sounds.current = old;
			} else {
				sounds.current.clear();
			}

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
		long key = ChunkPos.asLong(Mth.floor(pos.x) >> 4, Mth.floor(pos.z) >> 4);
		List<Sound> list = sounds.current.get(key);
		if (list == null) {
			list = new ArrayList<>(4);
			sounds.current.put(key, list);
		}

		list.add(new Sound(sounds.next++, kind, pos, maker));
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
		for (int pass = 0; pass < 2; pass++) {
			var map = pass == 0 ? sounds.previous : sounds.current;
			if (map.isEmpty()) {
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
