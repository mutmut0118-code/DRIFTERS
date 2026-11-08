package com.dinglebob.drifter.perception;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.social.ChatLines;
import com.dinglebob.drifter.social.Lines;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Hearing a bell rung as an alarm (the user, 2026-11-08: "for like major stuff like a massacre or a raid will people spam
 * ring the bell?"). A bell struck over and over, fast (five strokes in five seconds: a festival's is three slow ones), is
 * an alarm, whoever rings it: a Drifter its town sent, or a player. Drifters within earshot hear it (vanilla's own: its
 * villagers hear a bell within 32 blocks), and those of a town pass the word on to those near them (a shout, 24 blocks),
 * so it goes round the town in a few seconds; what they do about it is {@link com.dinglebob.drifter.social.AlarmGoal}.
 * What it's for and where, when the one ringing said ({@link #say}: its town's ringer calls it out); else only that
 * something's wrong. Not saved: an alarm is over in a minute unless it's rung again.
 */
public final class BellHearing {
	private BellHearing() {
	}

	/** Who hears a bell: vanilla's (BellBlockEntity: villagers within 32 blocks hear it). */
	public static final double HEAR = 32.0;
	/** This many strokes within this long (ticks): an alarm. */
	public static final int ALARM_STROKES = 5;
	public static final long ALARM_WINDOW = 20L * 5;
	/** Strokes this far apart are another ringing of it (each its own alarm, passed round again). */
	private static final long ANOTHER = 20L * 10;
	/** A shout carries this far: who heard the alarm tells those this near. */
	public static final double SHOUT = 24.0;
	/** Passing the word on takes this long. */
	private static final long PASS_ON = 30L;
	/** Out loud (in chat) for the first few passings-on of each ringing; the rest pass it on without filling the chat. */
	private static final int OUT_LOUD = 2;
	/** What the ringer said counts for strokes this soon after. */
	private static final long SAID_FOR = 20L * 30;

	/**
	 * An alarm it heard: which ringing (they count up), the bell, what for and where the trouble is (if the ringer said),
	 * when, and who told it (null: it heard the bell itself).
	 */
	public record Heard(long alarm, BlockPos bell, @Nullable String why, @Nullable BlockPos trouble, long at, @Nullable String from) {
	}

	private record Said(String why, @Nullable BlockPos trouble, long at) {
	}

	private static final class Ringing {
		final long id;
		long last;
		int outLoud;

		Ringing(long id, long last) {
			this.id = id;
			this.last = last;
		}
	}

	private record PassOn(ServerLevel level, DrifterEntity teller, Heard heard, long at) {
	}

	/** Each bell's strokes lately, what its ringer said, its ringing now (by dimension and spot). */
	private static final Map<String, ArrayDeque<Long>> strokes = new HashMap<>();
	private static final Map<String, Said> said = new HashMap<>();
	private static final Map<String, Ringing> ringings = new HashMap<>();
	private static final List<PassOn> passing = new ArrayList<>();
	private static long nextId = 1;

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			strokes.clear();
			said.clear();
			ringings.clear();
			passing.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 == 0 && !passing.isEmpty()) {
				passOn();
			}

			if (server.getTickCount() % 1200 == 0) {
				long now = server.overworld().getGameTime();
				ringings.values().removeIf(r -> now - r.last > 20L * 600);
				said.values().removeIf(s -> now - s.at > 20L * 600);
				strokes.values().removeIf(q -> q.isEmpty() || now - q.peekLast() > 20L * 600);
			}
		});
	}

	private static String key(ServerLevel level, BlockPos bell) {
		return level.dimension().location() + "|" + bell.asLong();
	}

	/** Its ringer about to ring it as an alarm, calling out what for and where. */
	public static void say(ServerLevel level, BlockPos bell, String why, @Nullable BlockPos trouble) {
		said.put(key(level, bell), new Said(why, trouble == null ? null : trouble.immutable(), level.getGameTime()));
	}

	/** A bell rung (from BellBlock#attemptToRing). */
	public static void rung(ServerLevel level, BlockPos bell, @Nullable Entity by) {
		long now = level.getGameTime();
		String key = key(level, bell);
		ArrayDeque<Long> times = strokes.computeIfAbsent(key, k -> new ArrayDeque<>());
		while (!times.isEmpty() && now - times.peekFirst() > ALARM_WINDOW) {
			times.pollFirst();
		}

		times.addLast(now);
		if (times.size() < ALARM_STROKES) {
			return;
		}

		Ringing ringing = ringings.get(key);
		if (ringing == null || now - ringing.last > ANOTHER) {
			ringing = new Ringing(nextId++, now);
			ringings.put(key, ringing);
		}

		ringing.last = now;
		Said what = said.get(key);
		if (what != null && now - what.at > SAID_FOR) {
			what = null;
		}

		Heard heard = new Heard(ringing.id, bell.immutable(), what == null ? null : what.why, what == null ? null : what.trouble, now, null);
		for (DrifterEntity drifter : level.getEntitiesOfClass(DrifterEntity.class, new AABB(bell).inflate(HEAR),
			d -> d.isAlive() && bell.closerToCenterThan(d.position(), HEAR))) {
			hear(level, drifter, heard);
		}
	}

	/** It hears an alarm (the bell, or someone passing it on); new to it: it passes it on in turn, if it's of a town. */
	private static void hear(ServerLevel level, DrifterEntity drifter, Heard heard) {
		Heard before = drifter.getSenses().heardAlarm();
		drifter.getSenses().heardAlarm(heard);
		if (before != null && before.alarm() == heard.alarm()) {
			return;
		}

		String what = heard.why() == null ? "" : ": " + heard.why();
		drifter.getMemory().thoughts().add(level.getGameTime(), heard.from() == null ? "heard the alarm bell" + what
			: heard.from() + " says the alarm bell's ringing" + what);
		if (drifter.getTownWork() != null && passing.size() < 2000) {
			passing.add(new PassOn(level, drifter, heard, level.getGameTime() + PASS_ON));
		}
	}

	/** Those who heard pass the word on to those near them who haven't. */
	private static void passOn() {
		for (PassOn pass : new ArrayList<>(passing)) {
			long now = pass.level().getGameTime();
			if (now < pass.at()) {
				continue;
			}

			passing.remove(pass);
			DrifterEntity teller = pass.teller();
			if (!teller.isAlive() || teller.isRemoved() || teller.level() != pass.level()) {
				continue;
			}

			Heard told = new Heard(pass.heard().alarm(), pass.heard().bell(), pass.heard().why(), pass.heard().trouble(), now, teller.getName().getString());
			int reached = 0;
			for (DrifterEntity other : pass.level().getEntitiesOfClass(DrifterEntity.class, teller.getBoundingBox().inflate(SHOUT),
				d -> d != teller && d.isAlive() && d.distanceTo(teller) <= SHOUT)) {
				Heard theirs = other.getSenses().heardAlarm();
				if (theirs == null || theirs.alarm() != told.alarm()) {
					hear(pass.level(), other, told);
					reached++;
				}
			}

			Ringing ringing = null;
			for (Ringing r : ringings.values()) {
				if (r.id == told.alarm()) {
					ringing = r;
				}
			}

			if (reached > 0 && ringing != null && ringing.outLoud < OUT_LOUD) {
				ringing.outLoud++;
				ChatLines.speak(teller, Lines.pick(teller, "alarm.heard"));
			}
		}
	}
}
