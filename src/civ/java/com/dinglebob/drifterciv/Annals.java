package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterRecord;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The world's annals (the chronicle revamp, RPG profile part 3; the user, 2026-11-12): what a history book would keep,
 * kept as stories, not lines. A war is one thing that grows (how it began, its battles, the towns it took, its dead, its
 * end); so is a town taken, a leader killed, a nation formed, someone gone for good, a player's deed. Written as things
 * happen, from the summary's far news ({@link History}), the towns' own doings and their days on paper ({@link TownAway}).
 *
 * <p>Each player has their own knowledge of it ("only what you'd know"): a story is theirs once they've been in a town
 * that knows of it (its own, its power's, its enemies' and allies', its near neighbours': the summary's reach for news),
 * within about the last hundred days; their own deeds at once. Their journal's Chronicle bookmark lists what they know
 * and opens each as pages (a front page, how it began, the fallen, a life, voices, a nation): {@link #page}.
 *
 * <p>The flat chronicle ({@link Chronicle}) stays as the full log.
 */
final class Annals {
	/** A town tells of what it knows from this many days back. */
	static final int TOLD_DAYS = 100;
	/** A year, in words: as the towns' festivals count it. */
	private static final int YEAR = TownCulture.YEAR;
	/** Voices kept with a story a player learned. */
	private static final int VOICES = 3;
	/** Heads on a page at most. */
	private static final int HEADS = 8;
	/** Stories kept at most (the oldest go first). */
	private static final int MOST = 1500;
	private static final int SAVE_VERSION = 1;

	/** Someone in a story: who (null: only a name, a person of a town not made), their name, what they were to it. */
	record Person(@Nullable UUID id, String name, String role, long day) {
		/** One there from the story's start (the day: -1, always known with it). */
		Person(@Nullable UUID id, String name, String role) {
			this(id, name, role, -1L);
		}
	}

	/** A turn in a story: the day, its kind ("battle", "took", "began", ...), and the words. */
	record Beat(long day, String what, String text) {
	}

	/** What one of a town's people said of a story, kept as a player learned it. */
	record Voice(@Nullable UUID id, String name, String caption, String line) {
	}

	static final class Event {
		long id;
		/** "war", "taken", "nation", "fallen", "leader", "overthrown", "death", "founded", "abandoned", "freed", "deed". */
		String kind = "";
		long day;
		long last;
		String title = "";
		final List<UUID> powers = new ArrayList<>();
		/** Its powers' names and banner colours as they were (a nation since fallen is still named and flown in its story). */
		final Map<UUID, String> names = new HashMap<>();
		final Map<UUID, Integer> colours = new HashMap<>();
		/** The town it's about most (null: none: a war between powers). */
		@Nullable UUID town;
		String townName = "";
		final List<Beat> beats = new ArrayList<>();
		final List<Person> people = new ArrayList<>();
		boolean over;
		/** A war: who struck first, and how many lay dead between them from before. */
		@Nullable UUID by;
		int grudge;
		/** A deed: whose. */
		@Nullable UUID player;
	}

	/** What a player knows of a story: when and where they learned it, up to which day, and what was said. */
	static final class Seen {
		long learned;
		long asOf;
		String where = "";
		String quote = "";
		String quoteBy = "";
		@Nullable UUID quoteId;
		final List<Voice> voices = new ArrayList<>();
	}

	private static final Map<Long, Event> events = new LinkedHashMap<>();
	private static final Map<UUID, Map<Long, Seen>> seen = new HashMap<>();
	/** The nations (and free towns) whose past each player has come to know. */
	private static final Map<UUID, Set<UUID>> pasts = new HashMap<>();
	/** The latest quarrel between two powers, for how their next war began. */
	private static final Map<String, Beat> quarrels = new HashMap<>();
	private static long nextId = 1;
	private static boolean dirty;

	private Annals() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(Annals::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(Annals::save);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			events.clear();
			seen.clear();
			pasts.clear();
			quarrels.clear();
			nextId = 1;
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (dirty && server.getTickCount() % 600 == 0) {
				save(server);
			}
		});
		// Debug: `/drifterciv annals` (every story, whoever knows it), `annals learn` (the nearest town tells you now, as
		// if you'd walked in), `annals all` (you know every story: for looking at pages).
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
			net.minecraft.commands.Commands.literal("drifterciv").requires(source -> source.hasPermission(2))
				.then(net.minecraft.commands.Commands.literal("annals").executes(ctx -> {
					List<String> lines = new ArrayList<>();
					for (Event e : events.values()) {
						lines.add("#" + e.id + " " + e.kind + " day " + e.day + (e.last != e.day ? "-" + e.last : "") + ": " + e.title + (e.over ? " (over)" : "")
							+ ", " + e.beats.size() + " turns, " + e.people.size() + " people");
					}

					DrifterCivilizations.LOGGER.info("[annals] {} stories: {}", events.size(), String.join(" | ", lines));
					List<String> shown = lines.subList(Math.max(0, lines.size() - 30), lines.size());
					ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(events.size() + " stories (the latest):\n"
						+ String.join("\n", shown)), false);
					return 1;
				}).then(net.minecraft.commands.Commands.literal("learn").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Town town = Towns.nearest(player.level().dimension().location().toString(), player.blockPosition());
					int before = seen.getOrDefault(player.getUUID(), Map.of()).size();
					if (town != null) {
						visit(ctx.getSource().getServer(), player, town);
					}

					int now = seen.getOrDefault(player.getUUID(), Map.of()).size();
					String text = town == null ? "No town near" : town.name + " told you of " + (now - before) + " things (you know of " + now + ")";
					DrifterCivilizations.LOGGER.info("[annals] learn: {}", text);
					ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(text), false);
					return 1;
				})).then(net.minecraft.commands.Commands.literal("all").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					MinecraftServer server = ctx.getSource().getServer();
					long today = Chronicle.day(server);
					for (Event e : events.values()) {
						learn(server, player.getUUID(), e, today, "", null);
					}

					for (WorldPlan.PlannedNation nation : WorldPlan.plannedNations()) {
						pasts.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(nation.id());
					}

					dirty = true;
					ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Debug: you know all " + events.size() + " stories"), false);
					return 1;
				})).then(net.minecraft.commands.Commands.literal("page").then(net.minecraft.commands.Commands.argument("id",
					com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						Journal.Page page = page(player, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"));
						List<String> lines = Journal.text(page);
						DrifterCivilizations.LOGGER.info("[annals] page {}: {}", page.id(), String.join(" | ", lines));
						ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(String.join("\n", lines)), false);
						return 1;
					}))))));
	}

	// ---------------------------------------------------------------------------------------------
	// Written as things happen
	// ---------------------------------------------------------------------------------------------

	private static Event add(MinecraftServer server, String kind, String title, @Nullable Town town, UUID... powers) {
		Event e = new Event();
		e.id = nextId++;
		e.kind = kind;
		e.day = Chronicle.day(server);
		e.last = e.day;
		e.title = title;
		for (UUID power : powers) {
			if (power != null && !e.powers.contains(power)) {
				e.powers.add(power);
				String name = WorldPlan.powerName(power);
				if (!name.equals(WorldPlan.FORGOTTEN)) {
					e.names.put(power, name);
				}

				Integer colour = WorldPlan.powerColour(power);
				if (colour != null) {
					e.colours.put(power, colour);
				}
			}
		}

		if (town != null) {
			e.town = town.id;
			e.townName = town.name;
		}

		events.put(e.id, e);
		if (events.size() > MOST) {
			Long oldest = events.keySet().iterator().next();
			events.remove(oldest);
			seen.values().forEach(mine -> mine.remove(oldest));
		}

		dirty = true;
		return e;
	}

	private static void beat(MinecraftServer server, Event e, String what, String text) {
		e.last = Chronicle.day(server);
		e.beats.add(new Beat(e.last, what, text));
		dirty = true;
	}

	/** A power's name as its story has it. */
	private static String name(Event e, UUID power) {
		String kept = e.names.get(power);
		return kept != null ? kept : WorldPlan.powerName(power);
	}

	/** Are these two at war, by the annals? */
	static boolean atWar(UUID a, UUID b) {
		return !a.equals(b) && war(a, b) != null;
	}

	/** The war on between these two (the latest not over), or null. */
	@Nullable
	private static Event war(UUID a, UUID b) {
		Event found = null;
		for (Event e : events.values()) {
			if (e.kind.equals("war") && !e.over && e.powers.contains(a) && e.powers.contains(b)) {
				found = e;
			}
		}

		return found;
	}

	private static Event warOrNew(MinecraftServer server, UUID a, UUID b, @Nullable UUID by) {
		Event e = war(a, b);
		if (e != null) {
			return e;
		}

		e = add(server, "war", "War: " + shortName(WorldPlan.powerName(a)) + " and " + shortName(WorldPlan.powerName(b)), null, a, b);
		e.by = by;
		History.Ties ties = WorldPlan.history == null ? null : WorldPlan.history.ties.get(History.key(a, b));
		e.grudge = ties == null ? 0 : ties.dead;
		Beat quarrel = quarrels.get(History.key(a, b));
		if (quarrel != null && e.day - quarrel.day <= TOLD_DAYS) {
			e.beats.add(new Beat(quarrel.day, "quarrel", quarrel.text));
		}

		return e;
	}

	/**
	 * Something the summary told of, far away or between powers ({@link History#tell}): what kind ("quarrel", "war",
	 * "peace", "battle", "leaderfell", "conquered", "took", "burned", "rose", "freed", "founded", "abandoned", "ruler",
	 * "pact", "allies", "joined"), the words, and whom it was between.
	 */
	static void far(MinecraftServer server, String what, String text, UUID... powers) {
		UUID a = powers.length > 0 ? powers[0] : null;
		UUID b = powers.length > 1 ? powers[1] : null;
		long today = Chronicle.day(server);
		switch (what) {
			case "quarrel" -> {
				if (a != null && b != null) {
					quarrels.put(History.key(a, b), new Beat(today, "quarrel", text));
					dirty = true;
				}
			}
			case "war" -> {
				if (a != null && b != null) {
					History.Ties ties = WorldPlan.history == null ? null : WorldPlan.history.ties.get(History.key(a, b));
					Event e = warOrNew(server, a, b, ties == null ? null : ties.startedBy);
					beat(server, e, "began", text);
				}
			}
			case "battle", "leaderfell", "took", "burned", "conquered" -> {
				if (a != null && b != null) {
					Event war = warOrNew(server, a, b, null);
					beat(server, war, what, text);
					if (what.equals("conquered")) {
						// (A nation conquered whole: its war is over, and its fall a story of its own, named and flown as it was.)
						war.over = true;
						Event fall = add(server, "fallen", text.contains(":") ? text.substring(0, text.indexOf(':')) : text, null, a, b);
						fall.beats.add(new Beat(today, what, text));
						fall.names.putAll(war.names);
						fall.colours.putAll(war.colours);
						fall.over = true;
					}
				}
			}
			case "peace" -> {
				Event e = a == null || b == null ? null : war(a, b);
				if (e != null) {
					beat(server, e, "peace", text);
					e.over = true;
				}
			}
			case "rose" -> {
				add(server, "rising", text, null, powers).beats.add(new Beat(today, what, text));
				// (A rising is a war from that day: begun here, not found later as one "on a while".)
				if (a != null && b != null && war(a, b) == null) {
					beat(server, warOrNew(server, a, b, a), "began", text);
				}
			}
			case "freed" -> add(server, "freed", text, null, powers).beats.add(new Beat(today, what, text));
			case "founded" -> add(server, "nation", text.contains(",") ? text.substring(0, text.indexOf(',')) : text, null, powers).beats.add(new Beat(today, what, text));
			case "abandoned" -> add(server, "abandoned", text.contains(";") ? text.substring(0, text.indexOf(';')) : text, null, powers).beats.add(new Beat(today, what, text));
			case "allies" -> add(server, "allies", text, null, powers).beats.add(new Beat(today, what, text));
			default -> {
				// (A pact, a ruler dying in bed, a town joining: not a history book's.)
			}
		}
	}

	/** War begun between two made towns' powers by their own diplomacy (in front of someone). */
	static void warBegan(MinecraftServer server, Town attacker, Town defender) {
		UUID a = WorldPlan.powerOf(attacker);
		UUID b = WorldPlan.powerOf(defender);
		if (!a.equals(b) && war(a, b) == null) {
			Event e = warOrNew(server, a, b, a);
			beat(server, e, "began", WorldPlan.powerName(a) + " declared war on " + WorldPlan.powerName(b));
		}
	}

	/** Peace made between two made towns' powers by their own diplomacy. */
	static void peaceMade(MinecraftServer server, Town x, Town y) {
		Event e = WorldPlan.powerOf(x).equals(WorldPlan.powerOf(y)) ? null : war(WorldPlan.powerOf(x), WorldPlan.powerOf(y));
		if (e != null) {
			beat(server, e, "peace", WorldPlan.powerName(e.powers.get(0)) + " and " + WorldPlan.powerName(e.powers.get(1)) + " made peace");
			e.over = true;
		}
	}

	/**
	 * One of a town's people gone for good (or its leader killed, whatever the world's rule): in the war that took them if
	 * there's one on, else a story of its own.
	 */
	static void death(MinecraftServer server, Town town, UUID id, String name, String cause, boolean leader, boolean gone) {
		UUID own = WorldPlan.powerOf(town);
		String line = town.lines.get(id);
		String role = (leader ? "leader" : line == null || line.isEmpty() ? "one" : article(line)) + " of " + town.name;
		Person person = new Person(id, name, role + "|" + cause + "|" + (gone ? "gone" : "back"), Chronicle.day(server));
		Event in = null;
		if (!leader && cause.startsWith("fighting")) {
			// (The war against whoever it died fighting, if it's told; else the latest its power is in.)
			boolean named = false;
			for (Event e : events.values()) {
				if (e.kind.equals("war") && !e.over && e.powers.contains(own) && !named) {
					in = e;
					for (UUID power : e.powers) {
						named |= !power.equals(own) && cause.contains(name(e, power));
					}
				}
			}
		}

		if (in != null) {
			if (gone) {
				in.people.add(person);
				in.last = Chronicle.day(server);
				dirty = true;
			}

			return;
		}

		if (!gone && !leader) {
			return;
		}

		Event e = add(server, leader ? "leader" : "death", leader ? name + " killed" : name + " died", town, own);
		e.people.add(person);
		e.beats.add(new Beat(e.day, leader ? "killed" : "died", leader ? name + " was killed by an unknown hand" : name + " died: " + cause));
		e.over = true;
	}

	/** Something of a town's own worth keeping: its leader overthrown, it founded, it risen up. */
	static void local(MinecraftServer server, String kind, Town town, String title, String text, @Nullable UUID who, String role) {
		Event e = add(server, kind, title, town, WorldPlan.powerOf(town));
		e.beats.add(new Beat(e.day, kind, text));
		if (who != null) {
			DrifterRecord record = DrifterApi.record(who);
			e.people.add(new Person(who, record == null ? "someone" : record.name(), role + " of " + town.name + "||"));
		}

		e.over = true;
	}

	/** A player's big deed: theirs to know at once. */
	static void deed(MinecraftServer server, ServerPlayer player, String text, @Nullable DrifterEntity drifter) {
		Town town = drifter == null ? null : Diplomacy.townOf(drifter.getUUID());
		Event e = add(server, "deed", "You " + text, town);
		e.player = player.getUUID();
		e.beats.add(new Beat(e.day, "deed", "You " + text));
		if (drifter != null) {
			e.people.add(new Person(drifter.getUUID(), drifter.getName().getString(), (town == null ? "a wanderer" : "one of " + town.name) + "||"));
		}

		e.over = true;
		learn(server, player.getUUID(), e, e.day, town == null ? "" : town.name, null);
	}

	private static String article(String line) {
		return ("aeiou".indexOf(line.charAt(0)) >= 0 ? "an " : "a ") + line;
	}

	// ---------------------------------------------------------------------------------------------
	// What a player comes to know
	// ---------------------------------------------------------------------------------------------

	/** A player in a town: what it knows from the last hundred days is theirs, as of today; and its power's past. */
	static void visit(MinecraftServer server, ServerPlayer player, Town town) {
		long today = Chronicle.day(server);
		UUID own = WorldPlan.powerOf(town);
		if (pasts.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(own)) {
			dirty = true;
		}

		for (Event e : events.values()) {
			if (e.last < today - TOLD_DAYS || e.player != null) {
				continue;
			}

			boolean knows = town.id.equals(e.town);
			for (UUID power : e.powers) {
				knows |= WorldPlan.hears(town, power);
			}

			if (knows) {
				learn(server, player.getUUID(), e, today, town.name, town);
			}
		}
	}

	/** A citizen: their nation's past is theirs from the start. */
	static void citizen(UUID player, UUID nation) {
		if (pasts.computeIfAbsent(player, k -> new HashSet<>()).add(nation)) {
			dirty = true;
		}
	}

	private static void learn(MinecraftServer server, UUID player, Event e, long today, String where, @Nullable Town town) {
		Map<Long, Seen> mine = seen.computeIfAbsent(player, k -> new LinkedHashMap<>());
		Seen s = mine.get(e.id);
		if (s == null) {
			s = new Seen();
			s.learned = today;
			s.where = where;
			mine.put(e.id, s);
			if (town != null) {
				voices(server, e, s, town);
			}

			dirty = true;
		}

		if (s.asOf != today) {
			s.asOf = today;
			dirty = true;
		}
	}

	/** Someone told a player of one of its people's fate, in these words: the story's quote, if it has none. */
	static void quoted(ServerPlayer player, UUID about, DrifterEntity speaker, String line) {
		Map<Long, Seen> mine = seen.get(player.getUUID());
		if (mine == null) {
			return;
		}

		for (Event e : events.values()) {
			Seen s = mine.get(e.id);
			if (s != null && s.quote.isEmpty() && e.people.stream().anyMatch(person -> about.equals(person.id))) {
				s.quote = line;
				s.quoteBy = speaker.getName().getString();
				s.quoteId = speaker.getUUID();
				dirty = true;
			}
		}
	}

	/** A few of the town's people on it, each in their own way: picked once, as the player learns of it. */
	private static void voices(MinecraftServer server, Event e, Seen s, Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null || e.kind.equals("deed")) {
			return;
		}

		RandomSource random = server.overworld().getRandom();
		List<UUID> people = new ArrayList<>(group.members);
		java.util.Collections.shuffle(people, new java.util.Random(random.nextLong()));
		// (Its leader first, if it has one: the town's word on it.)
		if (town.leader != null && people.remove(town.leader)) {
			people.add(0, town.leader);
		}

		String kind = switch (e.kind) {
			case "war" -> e.over ? "peace" : "war";
			case "death", "leader" -> "death";
			default -> "other";
		};
		Set<String> said = new HashSet<>();
		for (UUID id : people) {
			if (s.voices.size() >= VOICES) {
				break;
			}

			DrifterRecord record = DrifterApi.record(id);
			if (record == null || record.status() != DrifterRecord.Status.ALIVE || e.people.stream().anyMatch(person -> id.equals(person.id))) {
				continue;
			}

			boolean grieving = e.people.stream().anyMatch(person -> person.id != null && record.drifterTrust().getOrDefault(person.id, 0.0) >= 30.0);
			double caution = record.traits().getOrDefault("caution", 0.5);
			double bold = Math.max(record.traits().getOrDefault("bravery", 0.5), record.traits().getOrDefault("aggression", 0.5));
			String mood = grieving ? "grieving" : id.equals(town.leader) ? "leader" : caution >= 0.6 && caution >= bold ? "careful" : bold >= 0.6 ? "proud" : "plain";
			String caption = grieving ? "lost a friend" : id.equals(town.leader) ? "leader of " + town.name
				: mood.equals("careful") ? "a careful one" : mood.equals("proud") ? "a bold one" : "of " + town.name;
			String fallen = e.people.isEmpty() ? "" : e.people.get(random.nextInt(e.people.size())).name;
			String enemy = "";
			UUID own = WorldPlan.powerOf(town);
			for (UUID power : e.powers) {
				if (!power.equals(own)) {
					enemy = WorldPlan.powerName(power);
				}
			}

			for (UUID power : e.powers) {
				if (!power.equals(own)) {
					enemy = name(e, power);
				}
			}

			List<String> lines = CivLines.bell("chronicle.voice." + kind + "." + mood, List.of(), "town", town.name, "who", fallen.isEmpty() ? null : fallen,
				"enemy", enemy.isEmpty() ? null : enemy);
			lines = new ArrayList<>(lines);
			lines.removeAll(said);
			if (lines.isEmpty()) {
				continue;
			}

			String line = lines.get(random.nextInt(lines.size()));
			said.add(line);
			s.voices.add(new Voice(id, record.name(), caption, line));
		}
	}

	// ---------------------------------------------------------------------------------------------
	// The bookmark and its pages
	// ---------------------------------------------------------------------------------------------

	/** How long ago, in words. */
	static String ago(long today, long day) {
		long days = today - day;
		String[] words = {"", "", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve"};
		return days <= 0 ? "today" : days == 1 ? "yesterday" : days <= 12 ? words[(int)days] + " days ago" : days < 30 ? "some weeks ago"
			: days < YEAR ? "this year" : days < 2 * YEAR ? "last year" : days < 3 * YEAR ? "two years ago" : "years ago";
	}

	/** The Chronicle bookmark: what this player knows, the latest first, by when; then the pasts they know. */
	static Journal.Tab tab(ServerPlayer player) {
		MinecraftServer server = player.server;
		long today = Chronicle.day(server);
		Map<Long, Seen> mine = seen.getOrDefault(player.getUUID(), Map.of());
		List<Event> known = new ArrayList<>();
		for (Event e : events.values()) {
			if (mine.containsKey(e.id)) {
				known.add(e);
			}
		}

		known.sort((x, y) -> Long.compare(lastKnown(y, mine.get(y.id)), lastKnown(x, mine.get(x.id))));
		List<Journal.Line> lines = new ArrayList<>();
		String group = "";
		for (Event e : known) {
			Seen s = mine.get(e.id);
			long at = lastKnown(e, s);
			String now = today - at < YEAR ? "This year" : today - at < 2 * YEAR ? "Last year" : "Earlier";
			if (!now.equals(group)) {
				group = now;
				lines.add(new Journal.Line(now, "", Journal.FAINT, List.of()));
			}

			boolean on = e.kind.equals("war") && !overAsOf(e, s);
			String right = on ? "at war" : e.kind.equals("deed") ? "yours" : ago(today, at);
			lines.add(new Journal.Line(e.title, right, on ? Journal.BAD : e.kind.equals("deed") ? Journal.GOOD : Journal.FAINT, List.of("@page:e:" + e.id)));
		}

		// (A citizen from before the annals: their nation's past is theirs.)
		Nations.Nation theirs = Citizens.nation(player.getUUID());
		if (theirs != null) {
			citizen(player.getUUID(), theirs.id);
		}

		Set<UUID> known2 = pasts.getOrDefault(player.getUUID(), Set.of());
		boolean first = true;
		for (UUID power : known2) {
			if (WorldPlan.nation(power) == null) {
				continue;
			}

			if (first) {
				lines.add(new Journal.Line("Before your time", "", Journal.FAINT, List.of()));
				first = false;
			}

			lines.add(new Journal.Line(cap(WorldPlan.powerName(power)), "its past", Journal.FAINT, List.of("@page:n:" + power)));
		}

		return new Journal.Tab("Chronicle", lines, "Nothing yet. Towns know what's happened: walk into one.");
	}

	private static String cap(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private static long lastKnown(Event e, Seen s) {
		long last = e.day;
		for (Beat beat : e.beats) {
			if (beat.day <= s.asOf) {
				last = Math.max(last, beat.day);
			}
		}

		return last;
	}

	private static boolean overAsOf(Event e, Seen s) {
		return e.over && e.last <= s.asOf;
	}

	private static Journal.El el(int type, String a, String b, int ink, List<String> more) {
		return new Journal.El(type, a, b, ink, more);
	}

	private static Journal.El text(String text) {
		return el(Journal.TEXT, text, "", Journal.PLAIN, List.of());
	}

	private static Journal.El faint(String text) {
		return el(Journal.TEXT, text, "", Journal.FAINT, List.of());
	}

	private static Journal.El sub(String text) {
		return el(Journal.SUB, text, "", Journal.FAINT, List.of());
	}

	private static Journal.El link(String text, String page) {
		return el(Journal.LINK, text, page, Journal.PLAIN, List.of());
	}

	private static final Journal.El RULE = el(Journal.RULE, "", "", 0, List.of());
	private static final Journal.El THIN = el(Journal.THIN, "", "", 0, List.of());
	private static final Journal.El BREAK = el(Journal.BREAK, "", "", 0, List.of());

	/** A person's skin for a head, as the client draws it ("": none known). */
	private static String skin(@Nullable UUID id) {
		DrifterRecord record = id == null ? null : DrifterApi.record(id);
		return record == null ? "" : record.skin();
	}

	/** A power's picture: its banner's colour ("b" and the dye's id), or a free town's leader's head ("s" and the skin), or "-". */
	private static String picture(UUID power) {
		Integer colour = WorldPlan.powerColour(power);
		if (colour != null) {
			return "b" + colour;
		}

		Town town = Diplomacy.byId(power.toString());
		String skin = town == null ? "" : skin(town.leader);
		return skin.isEmpty() ? "-" : "s" + skin;
	}

	/** A power's picture as its story has it. */
	private static String picture(Event e, UUID power) {
		Integer colour = e.colours.get(power);
		return colour != null ? "b" + colour : picture(power);
	}

	private static Journal.El banners(Event e, Set<UUID> known) {
		List<String> more = new ArrayList<>();
		for (UUID power : e.powers) {
			more.add(picture(e, power) + "\t" + cap(shortName(name(e, power))) + "\t" + (WorldPlan.nation(power) != null && known.contains(power) ? "n:" + power : ""));
		}

		return el(Journal.BANNERS, "", "", 0, more);
	}

	/** "the Realm of Willowgate" as "Willowgate" (under a banner there's little room). */
	private static String shortName(String name) {
		int of = name.lastIndexOf(" of ");
		String s = of >= 0 ? name.substring(of + 4) : name.startsWith("the ") ? name.substring(4) : name;
		for (String tail : new String[] {" Union", " Accord", " League", " Pact"}) {
			if (s.endsWith(tail)) {
				s = s.substring(0, s.length() - tail.length());
			}
		}

		return s;
	}

	private static Journal.El heads(List<Person> people, boolean lives) {
		List<String> more = new ArrayList<>();
		for (Person person : people) {
			if (more.size() >= HEADS) {
				break;
			}

			more.add(skin(person.id) + "\t" + person.name + "\t" + (lives && person.id != null ? "p:" + person.id : "") + "\t");
		}

		return el(Journal.HEADS, "", "", 0, more);
	}

	/** A page of this player's Chronicle: "e:<story>", "e:<story>:began", "e:<story>:voices", "p:<person>", "n:<power>". */
	static Journal.Page page(ServerPlayer player, String id) {
		MinecraftServer server = player.server;
		long today = Chronicle.day(server);
		List<Journal.El> els = new ArrayList<>();
		try {
			String[] parts = id.split(":");
			if (parts[0].equals("e") && parts.length >= 2) {
				Event e = events.get(Long.parseLong(parts[1]));
				Seen s = e == null ? null : seen.getOrDefault(player.getUUID(), Map.of()).get(e.id);
				if (e != null && s != null) {
					if (parts.length == 2) {
						front(els, player, e, s, today);
					} else if (parts[2].equals("began")) {
						began(els, e, s, today);
					} else {
						voices(els, e, s);
					}
				}
			} else if (parts[0].equals("p") && parts.length == 2) {
				life(els, player, UUID.fromString(parts[1]), today);
			} else if (parts[0].equals("n") && parts.length == 2) {
				nation(els, player, UUID.fromString(parts[1]), today);
			}
		} catch (IllegalArgumentException bad) {
			els.clear();
		}

		if (els.isEmpty()) {
			els.add(faint("Nothing's known of this."));
		}

		return new Journal.Page(id, els);
	}

	private static String headline(Event e) {
		return switch (e.kind) {
			case "war" -> "WAR";
			case "death" -> "A DEATH";
			case "leader" -> "A LEADER KILLED";
			case "overthrown" -> "OVERTHROWN";
			case "nation" -> "A NATION FOUNDED";
			case "fallen" -> "A NATION FALLS";
			case "rising" -> "A RISING";
			case "freed" -> "FREE";
			case "abandoned" -> "ABANDONED";
			case "allies" -> "ALLIES";
			case "founded" -> "A TOWN FOUNDED";
			case "deed" -> "YOUR DEED";
			default -> e.title.toUpperCase(Locale.ROOT);
		};
	}

	private static void front(List<Journal.El> els, ServerPlayer player, Event e, Seen s, long today) {
		List<Beat> beats = e.beats.stream().filter(beat -> beat.day <= s.asOf).toList();
		boolean over = overAsOf(e, s);
		els.add(RULE);
		els.add(el(Journal.HEADLINE, headline(e), "", 0, List.of()));
		els.add(el(Journal.DATELINE, e.kind.equals("deed") ? "you were there" : s.where.isEmpty() ? "" : "heard in " + s.where, ago(today, e.day), 0, List.of()));
		els.add(RULE);
		if (!e.powers.isEmpty() && !e.kind.equals("deed")) {
			els.add(banners(e, pasts.getOrDefault(player.getUUID(), Set.of())));
			els.add(THIN);
		}

		if (e.kind.equals("war")) {
			Beat began = beats.stream().filter(beat -> beat.what.equals("began")).findFirst().orElse(null);
			els.add(text(sentence(began != null ? began.text : e.title)));
			long battles = beats.stream().filter(beat -> beat.what.equals("battle")).count();
			if (battles > 0) {
				els.add(text("They've fought " + times(battles) + "."));
			}

			for (Beat beat : beats) {
				if (beat.what.equals("took") || beat.what.equals("burned") || beat.what.equals("conquered") || beat.what.equals("leaderfell")) {
					els.add(text(sentence(beat.text)));
				}
			}

			Beat peace = over ? beats.stream().filter(beat -> beat.what.equals("peace")).reduce((x, y) -> y).orElse(null) : null;
			els.add(over ? text(sentence(peace != null ? peace.text : "It's over") + " (" + ago(today, e.last) + ")") : el(Journal.TEXT, "Still being fought.", "", Journal.BAD,
				List.of()));
		} else {
			for (Beat beat : beats) {
				els.add(text(sentence(beat.text)));
			}
		}

		els.add(BREAK);
		// (Only those gone by the day they last heard of it.)
		List<Person> fallen = e.people.stream().filter(person -> person.id != null && person.day <= s.asOf).toList();
		if (!fallen.isEmpty()) {
			els.add(sub(e.kind.equals("war") ? "The fallen" : e.kind.equals("deed") ? "Who" : "Who it was"));
			els.add(heads(fallen, true));
			if (fallen.size() > HEADS) {
				els.add(faint("and " + (fallen.size() - HEADS) + " more"));
			}
		}

		if (!s.quote.isEmpty()) {
			els.add(el(Journal.QUOTE, s.quote, s.quoteBy + ", told you in " + s.where, 0, List.of(skin(s.quoteId))));
		}

		els.add(THIN);
		if (e.kind.equals("war")) {
			els.add(link("How it began", "e:" + e.id + ":began"));
		}

		if (!s.voices.isEmpty()) {
			els.add(link("Voices", "e:" + e.id + ":voices"));
		}
	}

	private static String sentence(String text) {
		String s = cap(text.trim());
		return s.endsWith(".") || s.endsWith("!") || s.endsWith("?") ? s : s + ".";
	}

	private static String times(long n) {
		return n == 1 ? "once" : n == 2 ? "twice" : n <= 12 ? new String[] {"", "", "", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven",
			"twelve"}[(int)n] + " times" : "many times";
	}

	/** A ruler's nature in a few words, from the summary's numbers (0 to 1). */
	private static String nature(double aggression, double warmth, double ambition) {
		List<String> words = new ArrayList<>();
		words.add(aggression >= 0.65 ? "quick to anger" : aggression <= 0.35 ? "slow to anger" : "even-tempered");
		words.add(warmth >= 0.65 ? "warm" : warmth <= 0.35 ? "cold" : "fair");
		if (ambition >= 0.65) {
			words.add("hungry for more");
		} else if (ambition <= 0.35) {
			words.add("content with what's theirs");
		}

		return cap(String.join(", ", words));
	}

	/** A Drifter's nature in a few words, from its record's traits. */
	private static String nature(DrifterRecord record) {
		String[][] ends = {{"bravery", "bold", "careful of their skin"}, {"aggression", "quick to anger", "slow to anger"}, {"sociability", "good company", "kept to themself"},
			{"greed", "fond of emeralds", "open-handed"}, {"caution", "careful", "reckless"}, {"patience", "patient", "restless"},
			{"forgiveness", "forgiving", "one to hold a grudge"}};
		List<String> words = new ArrayList<>();
		List<double[]> far = new ArrayList<>();
		for (int i = 0; i < ends.length; i++) {
			Double value = record.traits().get(ends[i][0]);
			if (value != null) {
				far.add(new double[] {i, value});
			}
		}

		far.sort((x, y) -> Double.compare(Math.abs(y[1] - 0.5), Math.abs(x[1] - 0.5)));
		for (double[] one : far.subList(0, Math.min(3, far.size()))) {
			if (Math.abs(one[1] - 0.5) >= 0.12) {
				words.add(ends[(int)one[0]][one[1] > 0.5 ? 1 : 2]);
			}
		}

		return words.isEmpty() ? "Like most" : cap(String.join(", ", words));
	}

	private static void began(List<Journal.El> els, Event e, Seen s, long today) {
		els.add(RULE);
		els.add(el(Journal.HEADLINE, "HOW IT BEGAN", "", 0, List.of()));
		els.add(RULE);
		for (UUID power : e.powers) {
			WorldPlan.Place seat = WorldPlan.seatOf(power);
			Town made = seat != null && seat.made ? Diplomacy.byId(seat.id.toString()) : Diplomacy.byId(power.toString());
			String ruler = made != null && made.leader != null && DrifterApi.record(made.leader) != null ? DrifterApi.record(made.leader).name()
				: seat == null ? "" : seat.leader;
			String skin = made == null ? "" : skin(made.leader);
			DrifterRecord record = made == null || made.leader == null ? null : DrifterApi.record(made.leader);
			String what = "rules " + shortName(name(e, power)) + ". " + (record != null ? nature(record)
				: seat != null ? nature(seat.aggression, seat.warmth, seat.ambition) : "");
			els.add(el(Journal.PERSON, skin, ruler.isEmpty() ? cap(name(e, power)) : ruler, 0, List.of(picture(e, power), what)));
		}

		els.add(THIN);
		if (e.grudge > 0) {
			els.add(text("They had fought before: " + e.grudge + " lay dead between them from older wars."));
		}

		Beat quarrel = e.beats.stream().filter(beat -> beat.what.equals("quarrel")).findFirst().orElse(null);
		if (quarrel != null) {
			els.add(text(sentence(quarrel.text) + " (" + ago(today, quarrel.day) + ")"));
		}

		els.add(text(e.by == null ? "Nobody now says who struck first." : cap(name(e, e.by)) + " struck first."));
		els.add(BREAK);
		els.add(sub("The fighting"));
		int shown = 0;
		for (Beat beat : e.beats) {
			if (beat.day <= s.asOf && !beat.what.equals("quarrel") && !beat.what.equals("began") && shown++ < 14) {
				els.add(text(sentence(beat.text)));
			}
		}

		if (shown == 0) {
			els.add(faint("No battle you've heard of yet."));
		}
	}

	private static void voices(List<Journal.El> els, Event e, Seen s) {
		els.add(RULE);
		els.add(el(Journal.HEADLINE, "VOICES", "", 0, List.of()));
		els.add(RULE);
		if (!s.quote.isEmpty()) {
			els.add(el(Journal.QUOTE, s.quote, s.quoteBy + ", told you in " + s.where, 0, List.of(skin(s.quoteId))));
		}

		for (Voice voice : s.voices) {
			els.add(el(Journal.QUOTE, voice.line, voice.name + ", " + voice.caption, 0, List.of(skin(voice.id))));
		}
	}

	private static void life(List<Journal.El> els, ServerPlayer player, UUID id, long today) {
		Map<Long, Seen> mine = seen.getOrDefault(player.getUUID(), Map.of());
		// (Only one they've heard of, by a story they know.)
		Person as = null;
		Event in = null;
		List<Event> others = new ArrayList<>();
		for (Event e : events.values()) {
			if (!mine.containsKey(e.id)) {
				continue;
			}

			for (Person person : e.people) {
				if (id.equals(person.id) && person.day <= mine.get(e.id).asOf) {
					if (as == null || person.role.split("\\|", -1).length > 1 && !person.role.split("\\|", -1)[1].isEmpty()) {
						as = person;
						in = e;
					}

					others.add(e);
				}
			}
		}

		DrifterRecord record = DrifterApi.record(id);
		if (as == null) {
			return;
		}

		String[] role = as.role.split("\\|", -1);
		final String theirName = as.name;
		String cause = role.length > 1 ? role[1] : "";
		// (As the story they know has it: not what's become of them since.)
		boolean gone = role.length > 2 && role[2].equals("gone");
		els.add(RULE);
		els.add(el(Journal.PERSON, skin(id), as.name.toUpperCase(Locale.ROOT), 0, List.of("", role[0])));
		els.add(RULE);
		if (record != null) {
			long days = Math.max(0, (player.server.overworld().getGameTime() - record.firstSeen()) / 24000L);
			els.add(text(nature(record) + "." + (days > YEAR ? " Known in these parts for years." : days > 20 ? " Known in these parts a good while." : " New to these parts.")));
		}

		List<Journal.El> time = new ArrayList<>();
		for (Event e : others) {
			if (e != in) {
				time.add(link(sentence(e.title), "e:" + e.id));
			}
		}

		if (!time.isEmpty()) {
			els.add(sub("In their time"));
			els.addAll(time);
		}

		Friends.Bond bond = CivConfig.friends().on ? Friends.bond(id, player.getUUID()) : null;
		els.add(sub("To you"));
		if (bond != null && bond.stage != Friends.Stage.STRANGER) {
			els.add(el(Journal.TEXT, cap(article(bond.stage.word)) + "." + (bond.saved ? " You saved their life once." : ""), "", Journal.GOOD, List.of()));
		} else {
			els.add(faint(Journal.met(player.getUUID(), id) ? "Someone you'd met." : "You never met."));
		}

		els.add(BREAK);
		if (!cause.isEmpty()) {
			els.add(sub(gone ? "The end" : "What happened"));
			String how = cause.startsWith("killed") ? "Was " + cause : cause.startsWith("fighting") ? "Died " + cause
				: cause.startsWith("a ") || cause.startsWith("an ") ? "Died of " + cause : cap(cause);
			els.add(text(sentence(how) + " (" + ago(today, as.day >= 0 ? as.day : in.day) + ")"));
			if (!gone) {
				els.add(text("And came back."));
			}

			Town town = in.town == null ? null : Diplomacy.byId(in.town.toString());
			if (town == null) {
				for (Town one : Towns.all()) {
					if (one.graves.stream().anyMatch(grave -> grave.name().equals(theirName))) {
						town = one;
					}
				}
			}

			if (gone && town != null) {
				final String name = as.name;
				boolean stone = town.graves.stream().anyMatch(grave -> grave.name().equals(name) && grave.stone() != null);
				els.add(text(stone ? "Lies in " + town.name + "'s graveyard, at the edge of town." : "No stone stands for them yet."));
			}
		}

		if (record != null) {
			List<Person> left = new ArrayList<>();
			record.drifterTrust().entrySet().stream().sorted((x, y) -> Double.compare(y.getValue(), x.getValue())).limit(4).forEach(entry -> {
				DrifterRecord other = DrifterApi.record(entry.getKey());
				if (other != null && other.status() == DrifterRecord.Status.ALIVE && entry.getValue() >= 15.0 && left.size() < 3) {
					left.add(new Person(other.id(), other.name(), entry.getValue() >= 40.0 ? "a friend" : "a neighbour"));
				}
			});
			if (!left.isEmpty() && gone) {
				els.add(THIN);
				els.add(sub("Those they left"));
				List<String> more = new ArrayList<>();
				for (Person person : left) {
					more.add(skin(person.id) + "\t" + person.name + "\t\t" + person.role);
				}

				els.add(el(Journal.HEADS, "", "", 0, more));
			}
		}

		els.add(THIN);
		els.add(link(in.kind.equals("war") ? "The war that took them" : sentence(in.title), "e:" + in.id));
	}

	private static void nation(List<Journal.El> els, ServerPlayer player, UUID power, long today) {
		if (!pasts.getOrDefault(player.getUUID(), Set.of()).contains(power)) {
			return;
		}

		String name = WorldPlan.powerName(power);
		els.add(RULE);
		els.add(el(Journal.HEADLINE, name.toUpperCase(Locale.ROOT), "", 0, List.of()));
		els.add(RULE);
		WorldPlan.Place seat = WorldPlan.seatOf(power);
		Town made = seat != null && seat.made ? Diplomacy.byId(seat.id.toString()) : null;
		DrifterRecord record = made == null || made.leader == null ? null : DrifterApi.record(made.leader);
		String ruler = record != null ? record.name() : seat == null ? "" : seat.leader;
		els.add(el(Journal.PERSON, record == null ? "" : record.skin(), ruler.isEmpty() ? "No ruler" : ruler, 0, List.of(picture(power),
			(seat == null ? "rules it" : "rules from " + seat.name) + ". " + (record != null ? nature(record) : seat != null ? nature(seat.aggression, seat.warmth,
				seat.ambition) : ""))));
		List<String> towns = new ArrayList<>();
		for (Town town : Towns.all()) {
			if (WorldPlan.powerOf(town).equals(power) && Journal.been(player.getUUID(), town.id)) {
				towns.add(town.name);
			}
		}

		if (!towns.isEmpty()) {
			els.add(sub("Its towns you've been to"));
			els.add(text(String.join(", ", towns) + "."));
		}

		Map<Long, Seen> mine = seen.getOrDefault(player.getUUID(), Map.of());
		List<Journal.El> now = new ArrayList<>();
		for (Event e : events.values()) {
			Seen s = mine.get(e.id);
			if (s != null && e.powers.contains(power) && (e.kind.equals("war") || e.kind.equals("allies") || e.kind.equals("fallen"))) {
				now.add(link(sentence(e.title) + (e.kind.equals("war") ? overAsOf(e, s) ? " (over)" : " (still on)" : ""), "e:" + e.id));
			}
		}

		if (!now.isEmpty()) {
			els.add(sub("In your time"));
			els.addAll(now.subList(Math.max(0, now.size() - 6), now.size()));
		}

		els.add(BREAK);
		els.add(sub("Its past"));
		List<String> past = past(name);
		if (past.isEmpty()) {
			els.add(faint("Nothing's written of its past."));
		}

		for (String line : past) {
			els.add(text(line));
		}
	}

	/** The biggest things in a power's written past (before day 1), the oldest first: a handful. */
	private static List<String> past(String name) {
		String[] big = {" was founded", " conquered ", " was burned ", " took ", " rose against ", " won its freedom ", " declared war ", " made peace ", " became allies"};
		List<Chronicle.Entry> found = new ArrayList<>();
		for (Chronicle.Entry entry : Chronicle.all()) {
			if (entry.day() >= 1 || !entry.text().contains(name)) {
				continue;
			}

			for (String kind : big) {
				if (entry.text().contains(kind)) {
					found.add(entry);
					break;
				}
			}
		}

		// (A handful: the founding, and the latest of the rest.)
		List<String> lines = new ArrayList<>();
		int from = Math.max(found.isEmpty() ? 0 : 1, found.size() - 9);
		if (!found.isEmpty()) {
			lines.add(sentence(found.get(0).text()) + " (long ago)");
		}

		for (Chronicle.Entry entry : found.subList(Math.min(from, found.size()), found.size())) {
			lines.add(sentence(entry.text()));
		}

		return lines;
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the world
	// ---------------------------------------------------------------------------------------------

	private static final class State extends SavedData {
		CompoundTag data = new CompoundTag();

		static State load(CompoundTag tag, HolderLookup.Provider registries) {
			State state = new State();
			state.data = tag;
			return state;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			for (String key : this.data.getAllKeys()) {
				tag.put(key, this.data.get(key));
			}

			return tag;
		}
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "drifter_civ_annals");
	}

	private static void save(MinecraftServer server) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", SAVE_VERSION);
		tag.putLong("Next", nextId);
		ListTag list = new ListTag();
		for (Event e : events.values()) {
			CompoundTag t = new CompoundTag();
			t.putLong("Id", e.id);
			t.putString("Kind", e.kind);
			t.putLong("Day", e.day);
			t.putLong("Last", e.last);
			t.putString("Title", e.title);
			ListTag powers = new ListTag();
			e.powers.forEach(power -> powers.add(NbtUtils.createUUID(power)));
			t.put("Powers", powers);
			if (e.town != null) {
				t.putUUID("Town", e.town);
			}

			t.putString("TownName", e.townName);
			ListTag beats = new ListTag();
			for (Beat beat : e.beats) {
				CompoundTag b = new CompoundTag();
				b.putLong("Day", beat.day);
				b.putString("What", beat.what);
				b.putString("Text", beat.text);
				beats.add(b);
			}

			t.put("Beats", beats);
			ListTag people = new ListTag();
			for (Person person : e.people) {
				CompoundTag p = new CompoundTag();
				if (person.id != null) {
					p.putUUID("Id", person.id);
				}

				p.putString("Name", person.name);
				p.putString("Role", person.role);
				p.putLong("Day", person.day);
				people.add(p);
			}

			t.put("People", people);
			CompoundTag names = new CompoundTag();
			e.names.forEach((power, name) -> names.putString(power.toString(), name));
			t.put("Names", names);
			CompoundTag colours = new CompoundTag();
			e.colours.forEach((power, colour) -> colours.putInt(power.toString(), colour));
			t.put("Colours", colours);
			t.putBoolean("Over", e.over);
			if (e.by != null) {
				t.putUUID("By", e.by);
			}

			t.putInt("Grudge", e.grudge);
			if (e.player != null) {
				t.putUUID("Player", e.player);
			}

			list.add(t);
		}

		tag.put("Events", list);
		CompoundTag players = new CompoundTag();
		for (Map.Entry<UUID, Map<Long, Seen>> entry : seen.entrySet()) {
			ListTag mine = new ListTag();
			for (Map.Entry<Long, Seen> one : entry.getValue().entrySet()) {
				Seen s = one.getValue();
				CompoundTag t = new CompoundTag();
				t.putLong("Id", one.getKey());
				t.putLong("Learned", s.learned);
				t.putLong("AsOf", s.asOf);
				t.putString("Where", s.where);
				t.putString("Quote", s.quote);
				t.putString("QuoteBy", s.quoteBy);
				if (s.quoteId != null) {
					t.putUUID("QuoteId", s.quoteId);
				}

				ListTag voices = new ListTag();
				for (Voice voice : s.voices) {
					CompoundTag v = new CompoundTag();
					if (voice.id != null) {
						v.putUUID("Id", voice.id);
					}

					v.putString("Name", voice.name);
					v.putString("Caption", voice.caption);
					v.putString("Line", voice.line);
					voices.add(v);
				}

				t.put("Voices", voices);
				mine.add(t);
			}

			players.put(entry.getKey().toString(), mine);
		}

		tag.put("Seen", players);
		CompoundTag known = new CompoundTag();
		for (Map.Entry<UUID, Set<UUID>> entry : pasts.entrySet()) {
			ListTag ids = new ListTag();
			entry.getValue().forEach(id -> ids.add(NbtUtils.createUUID(id)));
			known.put(entry.getKey().toString(), ids);
		}

		tag.put("Pasts", known);
		ListTag old = new ListTag();
		quarrels.forEach((key, beat) -> {
			CompoundTag q = new CompoundTag();
			q.putString("Key", key);
			q.putLong("Day", beat.day);
			q.putString("Text", beat.text);
			old.add(q);
		});
		tag.put("Quarrels", old);
		State state = state(server);
		state.data = tag;
		state.setDirty();
		dirty = false;
	}

	private static void load(MinecraftServer server) {
		events.clear();
		seen.clear();
		pasts.clear();
		quarrels.clear();
		CompoundTag tag = state(server).data;
		nextId = Math.max(1, tag.getLong("Next"));
		for (Tag raw : tag.getList("Events", Tag.TAG_COMPOUND)) {
			CompoundTag t = (CompoundTag)raw;
			Event e = new Event();
			e.id = t.getLong("Id");
			e.kind = t.getString("Kind");
			e.day = t.getLong("Day");
			e.last = t.getLong("Last");
			e.title = t.getString("Title");
			for (Tag power : t.getList("Powers", Tag.TAG_INT_ARRAY)) {
				e.powers.add(NbtUtils.loadUUID(power));
			}

			e.town = t.hasUUID("Town") ? t.getUUID("Town") : null;
			e.townName = t.getString("TownName");
			for (Tag b : t.getList("Beats", Tag.TAG_COMPOUND)) {
				CompoundTag beat = (CompoundTag)b;
				e.beats.add(new Beat(beat.getLong("Day"), beat.getString("What"), beat.getString("Text")));
			}

			for (Tag p : t.getList("People", Tag.TAG_COMPOUND)) {
				CompoundTag person = (CompoundTag)p;
				e.people.add(new Person(person.hasUUID("Id") ? person.getUUID("Id") : null, person.getString("Name"), person.getString("Role"),
					person.contains("Day") ? person.getLong("Day") : -1L));
			}

			for (UUID power : e.powers) {
				if (t.getCompound("Names").contains(power.toString())) {
					e.names.put(power, t.getCompound("Names").getString(power.toString()));
				}

				if (t.getCompound("Colours").contains(power.toString())) {
					e.colours.put(power, t.getCompound("Colours").getInt(power.toString()));
				}
			}

			e.over = t.getBoolean("Over");
			e.by = t.hasUUID("By") ? t.getUUID("By") : null;
			e.grudge = t.getInt("Grudge");
			e.player = t.hasUUID("Player") ? t.getUUID("Player") : null;
			events.put(e.id, e);
			nextId = Math.max(nextId, e.id + 1);
		}

		CompoundTag players = tag.getCompound("Seen");
		for (String key : players.getAllKeys()) {
			Map<Long, Seen> mine = new LinkedHashMap<>();
			for (Tag raw : players.getList(key, Tag.TAG_COMPOUND)) {
				CompoundTag t = (CompoundTag)raw;
				Seen s = new Seen();
				s.learned = t.getLong("Learned");
				s.asOf = t.getLong("AsOf");
				s.where = t.getString("Where");
				s.quote = t.getString("Quote");
				s.quoteBy = t.getString("QuoteBy");
				s.quoteId = t.hasUUID("QuoteId") ? t.getUUID("QuoteId") : null;
				for (Tag v : t.getList("Voices", Tag.TAG_COMPOUND)) {
					CompoundTag voice = (CompoundTag)v;
					s.voices.add(new Voice(voice.hasUUID("Id") ? voice.getUUID("Id") : null, voice.getString("Name"), voice.getString("Caption"), voice.getString("Line")));
				}

				mine.put(t.getLong("Id"), s);
			}

			try {
				seen.put(UUID.fromString(key), mine);
			} catch (IllegalArgumentException bad) {
				// (Not a player's id: left out.)
			}
		}

		CompoundTag known = tag.getCompound("Pasts");
		for (String key : known.getAllKeys()) {
			Set<UUID> ids = new HashSet<>();
			for (Tag id : known.getList(key, Tag.TAG_INT_ARRAY)) {
				ids.add(NbtUtils.loadUUID(id));
			}

			try {
				pasts.put(UUID.fromString(key), ids);
			} catch (IllegalArgumentException bad) {
				// (Not a player's id: left out.)
			}
		}

		for (Tag raw : tag.getList("Quarrels", Tag.TAG_COMPOUND)) {
			CompoundTag q = (CompoundTag)raw;
			quarrels.put(q.getString("Key"), new Beat(q.getLong("Day"), "quarrel", q.getString("Text")));
		}

		// A world from before the annals: the wars on now, by the summary's ties, so they're there to be told of.
		if (!tag.contains("Version") && WorldPlan.history != null) {
			dirty = true;
		}
	}

	/** A world from before the annals (or a war the annals missed): each war on by the summary has its story. Called once the plan's ready. */
	static void catchUp(MinecraftServer server) {
		if (WorldPlan.history == null) {
			return;
		}

		// A war the summary has ended with no peace told (a free town taken, a nation gone): over in its story too.
		for (Event e : events.values()) {
			if (e.kind.equals("war") && !e.over && e.powers.size() == 2) {
				History.Ties ties = WorldPlan.history.ties.get(History.key(e.powers.get(0), e.powers.get(1)));
				boolean gone = WorldPlan.powerName(e.powers.get(0)).equals(WorldPlan.FORGOTTEN) || WorldPlan.powerName(e.powers.get(1)).equals(WorldPlan.FORGOTTEN);
				if (ties != null && !ties.war && e.last < Chronicle.day(server) || gone) {
					e.over = true;
					dirty = true;
				}
			}
		}

		for (Map.Entry<String, History.Ties> entry : WorldPlan.history.ties.entrySet()) {
			String key = entry.getKey();
			if (!entry.getValue().war || key.length() != 73) {
				continue;
			}

			try {
				UUID a = UUID.fromString(key.substring(0, 36));
				UUID b = UUID.fromString(key.substring(37));
				if (war(a, b) == null) {
					Event e = warOrNew(server, a, b, entry.getValue().startedBy);
					e.beats.add(new Beat(e.day, "began", WorldPlan.powerName(a) + " and " + WorldPlan.powerName(b) + " have been at war a while"));
				}
			} catch (IllegalArgumentException bad) {
				// (Not two powers' ids: left out.)
			}
		}
	}
}
