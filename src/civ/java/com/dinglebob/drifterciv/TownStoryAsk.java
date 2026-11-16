package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterRecord;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Asking about what a town's story told of (the user, 2026-11-16, from the follow-up idea: "you could reply to what it
 * mentions, like 'what happened in the war?' or 'who was mason?', and it would go deeper, so the yapping becomes a real
 * back-and-forth"). After a Drifter has told its town's story ({@link TownStory}), for a few minutes the one it told can ask:
 * <ul>
 *   <li>who someone it named was ("who was mason?", "who's that", "tell me about sabrina"): whether they're alive, what
 *       they are (its leader, a founder, where they live), what the chronicle says of them, how the teller feels about them;</li>
 *   <li>what happened ("what happened in the war?", "tell me about the fire", "the battle?"): the thing told, with what led
 *       to it and what came of it, from the chronicle;</li>
 *   <li>more ("tell me more", "go on", "and then?", "what else"): what it hadn't got to yet;</li>
 *   <li>why, and when, of the last thing told;</li>
 *   <li>another town or the nation it named ("what about shalemead?", "whats the commonwealth of caelberg").</li>
 * </ul>
 * Each told in a line or a few, one after another as typed (stopped like the story, when they say something else).
 */
final class TownStoryAsk {
	private TownStoryAsk() {
	}

	/** How long after the story it can be asked about. */
	private static final long FOLLOW = 20L * 60 * 4;
	/** The most lines of the chronicle told for one question. */
	private static final int MOST = 3;

	/** What a player was told, and by whom. */
	private static final class Told {
		UUID drifter;
		UUID town;
		long at;
		/** The things from its past still to tell ("tell me more"), the biggest first. */
		final List<Chronicle.Entry> untold = new ArrayList<>();
		/** Every name it said, to whose record. */
		final Map<String, UUID> people = new LinkedHashMap<>();
		/** Other towns and the nations it named. */
		final Set<String> places = new LinkedHashSet<>();
		/** What it last told of (for "why?", "when?"), and the last one named (for "who's that?"). */
		@Nullable
		Chronicle.Entry last;
		@Nullable
		String lastName;
		/** Who founded it (as named). */
		final List<String> founders = new ArrayList<>();
		UUID self;
	}

	private static final Map<UUID, Told> told = new HashMap<>();

	// ---------------------------------------------------------------------------------------------
	// What was told
	// ---------------------------------------------------------------------------------------------

	/** It told them this town's story: what it said, to be asked about. */
	static void told(ServerPlayer player, DrifterEntity drifter, Town town, List<Chronicle.Entry> said, List<Chronicle.Entry> rest, List<String> names,
		List<String> founders) {
		Told t = new Told();
		t.drifter = drifter.getUUID();
		t.self = drifter.getUUID();
		t.founders.addAll(founders);
		t.town = town.id;
		t.at = player.level().getGameTime();
		t.untold.addAll(rest);
		Map<String, UUID> byName = byName();
		for (String name : names) {
			UUID id = byName.get(name.toLowerCase(Locale.ROOT));
			if (id != null) {
				t.people.put(name, id);
				if (!id.equals(t.self)) {
					t.lastName = name;
				}
			}
		}

		for (Chronicle.Entry entry : said) {
			mentioned(t, entry, byName);
			t.last = entry;
		}

		told.put(player.getUUID(), t);
	}

	/** The people and places a line names, noted. */
	private static void mentioned(Told t, Chronicle.Entry entry, Map<String, UUID> byName) {
		for (String word : entry.text().split("[^A-Za-z0-9_]+")) {
			UUID id = word.length() >= 3 ? byName.get(word.toLowerCase(Locale.ROOT)) : null;
			if (id != null) {
				t.people.put(word, id);
				if (!id.equals(t.self)) {
					t.lastName = word;
				}
			}
		}

		for (Town town : Towns.all()) {
			if (!town.id.equals(t.town) && entry.text().contains(town.name)) {
				t.places.add(town.name);
			}
		}

		for (Nations.Nation nation : Nations.all()) {
			if (entry.text().contains(nation.name)) {
				t.places.add(nation.name);
			}
		}
	}

	/** Every Drifter the world has known, by name (lower case). */
	private static Map<String, UUID> byName() {
		Map<String, UUID> names = new HashMap<>();
		for (DrifterRecord record : DrifterApi.directory()) {
			names.put(record.name().toLowerCase(Locale.ROOT), record.id());
		}

		return names;
	}

	// ---------------------------------------------------------------------------------------------
	// Asked
	// ---------------------------------------------------------------------------------------------

	/** Words for "more of it". */
	private static final List<String> MORE = List.of("tell me more", "go on", "and then", "then what", "what else", "anything else", "keep going",
		"what happened next", "what happened after", "more please", "continue", "what next", "and after that", "more", "go on then", "and");
	/** Words for "why". */
	private static final List<String> WHY = List.of("why", "how come", "what for", "why did that happen", "why was that", "what caused it",
		"what started it", "how did it start", "how did that start", "why though", "but why", "for what");
	/** Words for "when". */
	private static final List<String> WHEN = List.of("when", "when was that", "how long ago", "when did that happen", "what year", "how long ago was that",
		"when was it");
	/** Words for "who's that" (the last one named). */
	private static final List<String> WHO_THAT = List.of("who", "whos that", "who is that", "who was that", "who are they", "who was they", "who",
		"whos they", "who were they", "who is they");
	/** Words of the kind of thing that happened, to what it's told as. */
	private static final Map<String, List<String>> EVENTS = new LinkedHashMap<>();

	static {
		EVENTS.put("war", List.of(" war", "fought", "fighting", "against each other", "battle", "died fighting", "fell in the", "declared"));
		EVENTS.put("battle", List.of("fighting", "battle", "died fighting", "fell in the"));
		EVENTS.put("fight", List.of("fighting", "battle", "died fighting", "fell in the", "against each other"));
		EVENTS.put("fighting", List.of("fighting", "battle", "died fighting", "fell in the"));
		EVENTS.put("quarrel", List.of("quarrel", "against each other", "grudge"));
		EVENTS.put("fire", List.of("burned", "fire"));
		EVENTS.put("burn", List.of("burned", "fire"));
		EVENTS.put("burning", List.of("burned", "fire"));
		EVENTS.put("raid", List.of("raid", "burned", "took"));
		EVENTS.put("attack", List.of("raid", "fighting", "burned", "took"));
		EVENTS.put("taken", List.of(" took ", "conquered", "submitted", "taken"));
		EVENTS.put("conquest", List.of(" took ", "conquered", "submitted"));
		EVENTS.put("rebellion", List.of("rose against", "won its freedom", "free", "independence"));
		EVENTS.put("uprising", List.of("rose against", "won its freedom", "free"));
		EVENTS.put("freedom", List.of("won its freedom", "free", "rose against"));
		EVENTS.put("peace", List.of("made peace", "peace"));
		EVENTS.put("winter", List.of("winter", "failed"));
		EVENTS.put("famine", List.of("failed", "winter", "abandoned"));
		EVENTS.put("ruler", List.of(" ruled ", " rules ", "first ruler", "to lead", " leads "));
		EVENTS.put("king", List.of(" ruled ", " rules ", "first ruler"));
		EVENTS.put("queen", List.of(" ruled ", " rules ", "first ruler"));
		EVENTS.put("death", List.of("died", "slain", "fell", "killed"));
		EVENTS.put("massacre", List.of("killed", "massacre", "slain"));
		EVENTS.put("killing", List.of("killed", "slain"));
		EVENTS.put("murder", List.of("killed", "slain"));
		EVENTS.put("alliance", List.of("allied", "joined", "alliance"));
		EVENTS.put("founding", List.of("was founded", "founded"));
	}

	/** They said something to this Drifter: about its story? True: answered. */
	static boolean asked(ServerPlayer player, DrifterEntity drifter, String text) {
		Told t = told.get(player.getUUID());
		long now = player.level().getGameTime();
		if (t == null || !t.drifter.equals(drifter.getUUID()) || now - t.at > FOLLOW) {
			return false;
		}

		String said = " " + text.toLowerCase(Locale.ROOT).replace("'", "").replace("\u2019", "").replaceAll("[^a-z0-9_ ]+", " ").replaceAll(" +", " ").trim() + " ";
		String plain = said.trim();
		Town town = Towns.all().stream().filter(one -> one.id.equals(t.town)).findFirst().orElse(null);
		if (town == null) {
			return false;
		}

		t.at = now;
		long day = Chronicle.day(drifter.getServer());

		// Who founded it ("who founded it?", "who built this place"): them, and the first other one of them as anyone asked after.
		if ((said.contains(" who ") || said.contains(" whos ")) && (said.contains(" founded ") || said.contains(" built ") || said.contains(" started ")
			|| said.contains(" made ") || said.contains(" founder")) || said.contains(" the founder") || said.contains(" founders ")) {
			founders(drifter, player, town, t, day);
			return true;
		}

		// Someone it named, or anyone the world knows asked after by name ("who was mason?"); asked, or just the name.
		boolean question = text.contains("?") || said.contains(" who ") || said.contains(" whos ") || said.contains(" what ") || said.contains(" whats ")
			|| said.contains(" tell ") || said.contains(" about ") || said.contains(" is ") || said.contains(" was ") || plain.split(" ").length <= 3;
		String name = question ? named(t, said) : null;
		if (name == null && (WHO_THAT.contains(plain) || plain.startsWith("who was that") || plain.startsWith("whos that")) && t.lastName != null) {
			name = t.lastName;
		}

		if (name == null && (said.contains(" who ") || said.contains(" whos ") || said.startsWith(" tell me about "))) {
			name = anyone(said);
		}

		if (name != null) {
			UUID id = t.people.get(name);
			if (id == null) {
				id = byName().get(name.toLowerCase(Locale.ROOT));
			}

			if (id != null) {
				t.lastName = name;
				person(drifter, player, town, t, name, id, day);
				return true;
			}
		}

		// Another town or a nation (named in it, or any the world has).
		Set<String> places = new LinkedHashSet<>(t.places);
		Towns.all().forEach(one -> places.add(one.name));
		Nations.all().forEach(one -> places.add(one.name));
		for (String place : places) {
			String lower = place.toLowerCase(Locale.ROOT);
			String head = lower.replaceFirst("^(the )?(kingdom|commonwealth|union|dominion|republic|empire|league|federation|realm|crown) of ", "");
			if (!place.equals(town.name) && (said.contains(" " + lower + " ") || head.length() >= 4 && said.contains(" " + head + " "))) {
				place(drifter, player, town, place, day);
				return true;
			}
		}

		// What happened.
		String event = null;
		for (String word : EVENTS.keySet()) {
			if (said.contains(" " + word + " ") || said.contains(" " + word + "s ")) {
				event = word;
				break;
			}
		}

		boolean asking = said.contains(" what ") || said.contains(" whats ") || said.contains(" tell ") || said.contains(" about ") || said.contains(" how ")
			|| said.contains(" more ") || plain.split(" ").length <= 3;
		if (event != null && asking) {
			event(drifter, player, town, t, event, day);
			return true;
		}

		if (WHY.contains(plain) || said.startsWith(" why ") && plain.split(" ").length <= 5) {
			why(drifter, player, t, day);
			return true;
		}

		if (WHEN.contains(plain)) {
			Chronicle.Entry last = t.last;
			return TalkApi.answer(drifter, player, last == null ? "town.story.ask.when_none" : "town.story.ask.when", null, "when",
				last == null ? "" : when(day - last.day()));
		}

		if (MORE.contains(plain) || plain.startsWith("tell me more") || plain.startsWith("what else") || plain.startsWith("go on")) {
			more(drifter, player, town, t, day);
			return true;
		}

		return false;
	}

	/** A name it said (or the nickname of one), in what they said; null if none. */
	@Nullable
	private static String named(Told t, String said) {
		String found = null;
		for (String name : t.people.keySet()) {
			String lower = name.toLowerCase(Locale.ROOT);
			String nick = com.dinglebob.drifter.identity.Nicknames.of(name).toLowerCase(Locale.ROOT);
			String bare = lower.replaceAll("[^a-z]+", " ").trim();
			String first = bare.contains(" ") ? bare.substring(0, bare.indexOf(' ')) : bare;
			if (said.contains(" " + lower + " ") || nick.length() >= 3 && said.contains(" " + nick + " ") || first.length() >= 4 && said.contains(" " + first + " ")) {
				if (found == null || name.length() > found.length()) {
					found = name;
				}
			}
		}

		return found;
	}

	/** Anyone the world knows, asked after by their full name ("who is MrCocoaGuy"). */
	@Nullable
	private static String anyone(String said) {
		for (DrifterRecord record : DrifterApi.directory()) {
			String lower = record.name().toLowerCase(Locale.ROOT);
			if (lower.length() >= 3 && said.contains(" " + lower + " ")) {
				return record.name();
			}
		}

		return null;
	}

	// ---------------------------------------------------------------------------------------------
	// Told
	// ---------------------------------------------------------------------------------------------

	/** A line of it, one after another as typed: each part's key and words; stopped if they say something else. */
	private record Part(String key, Object[] vars) {
	}

	private static void chain(DrifterEntity drifter, ServerPlayer player, List<Part> parts) {
		TownStory.chain(drifter, player, parts.stream().map(p -> new TownStory.Line(p.key(), p.vars())).toList());
	}

	/** Who founded it: them (itself as "i"), then the first other one as asked after. */
	private static void founders(DrifterEntity drifter, ServerPlayer player, Town town, Told t, long day) {
		if (t.founders.isEmpty()) {
			TalkApi.answer(drifter, player, "town.story.ask.founders_unknown", null);
			return;
		}

		String me = drifter.getName().getString();
		List<String> others = t.founders.stream().filter(name -> !name.equals(me)).toList();
		boolean mine = others.size() < t.founders.size();
		String said = String.join(" and ", others);
		Part first = new Part(mine ? others.isEmpty() ? "town.story.ask.founders_me" : "town.story.ask.founders_me_with" : "town.story.ask.founders",
			vars("founder", said, "town", town.name));
		Map<String, UUID> byName = byName();
		String other = others.isEmpty() ? null : others.get(0);
		UUID id = other == null ? null : byName.get(other.toLowerCase(Locale.ROOT));
		if (id == null) {
			chain(drifter, player, List.of(first));
			return;
		}

		t.lastName = other;
		person(drifter, player, town, t, other, id, day, first);
	}

	/** Who they were: what they are, what the chronicle says of them, how it feels about them. */
	private static void person(DrifterEntity drifter, ServerPlayer player, Town town, Told t, String name, UUID id, long day) {
		person(drifter, player, town, t, name, id, day, null);
	}

	private static void person(DrifterEntity drifter, ServerPlayer player, Town town, Told t, String name, UUID id, long day, @Nullable Part before) {
		List<Part> parts = new ArrayList<>();
		if (before != null) {
			parts.add(before);
		}

		if (id.equals(drifter.getUUID())) {
			parts.add(new Part("town.story.ask.person_me", vars("person", name)));
		} else {
			DrifterRecord record = DrifterApi.record(id);
			String where = home(id);
			boolean leads = town.leader != null && town.leader.equals(id) || leaderOf(id) != null;
			if (record == null || record.status() == DrifterRecord.Status.GONE) {
				parts.add(new Part("town.story.ask.person_gone", vars("person", name)));
			} else if (leads) {
				parts.add(new Part("town.story.ask.person_leads", vars("person", name, "place", leaderOf(id) == null ? town.name : leaderOf(id))));
			} else if (where != null && where.equals(town.name)) {
				parts.add(new Part("town.story.ask.person_here", vars("person", name)));
			} else if (where != null) {
				parts.add(new Part("town.story.ask.person_there", vars("person", name, "place", where)));
			} else {
				parts.add(new Part("town.story.ask.person_around", vars("person", name)));
			}
		}

		// What the chronicle says of them (not everyday doings), oldest first.
		List<Chronicle.Entry> theirs = new ArrayList<>();
		for (Chronicle.Entry entry : Chronicle.all()) {
			if (entry.day() <= day && words(entry.text()).contains(name) && !TownStory.dull(entry.text()) && !entry.text().contains("'s laws for ")
				&& !entry.text().contains(" was founded") && !entry.text().contains(" leads ")) {
				theirs.add(entry);
			}
		}

		List<Chronicle.Entry> picked = TownStory.pick(theirs, MOST);
		picked.sort((a, b) -> Long.compare(a.day(), b.day()));
		for (int i = 0; i < picked.size(); i++) {
			Chronicle.Entry entry = picked.get(i);
			parts.add(new Part(i == 0 ? "town.story.ask.person_did" : "town.story.ask.person_did_more", vars("when", when(day - entry.day()), "what",
				TownStory.said(entry, drifter))));
			t.last = entry;
		}

		if (picked.isEmpty() && !id.equals(drifter.getUUID())) {
			parts.add(new Part("town.story.ask.person_quiet", vars("person", name)));
		}

		// How it feels about them.
		if (!id.equals(drifter.getUUID())) {
			double trust = DrifterApi.trustInDrifter(drifter, id);
			parts.add(new Part(trust >= 25.0 ? "town.story.ask.person_like" : trust <= -10.0 ? "town.story.ask.person_dislike" : "town.story.ask.person_meh",
				vars("person", name)));
		}

		chain(drifter, player, parts);
	}

	/** What happened: the thing told (or the likeliest of its past), what led to it and what came of it. */
	private static void event(DrifterEntity drifter, ServerPlayer player, Town town, Told t, String event, long day) {
		List<String> marks = EVENTS.get(event);
		Chronicle.Entry about = null;
		if (t.last != null && marks.stream().anyMatch(t.last.text()::contains)) {
			about = t.last;
		}

		if (about == null) {
			for (Chronicle.Entry entry : Chronicle.all()) {
				if (entry.day() <= day && concerns(entry, town) && marks.stream().anyMatch(entry.text()::contains)
					&& (about == null || TownStory.weight(entry) > TownStory.weight(about))) {
					about = entry;
				}
			}
		}

		if (about == null) {
			TalkApi.answer(drifter, player, "town.story.ask.event_none", null, "thing", event);
			return;
		}

		// Its parties: the towns and nations it names; what else was between them, near then.
		List<String> sides = sides(about);
		List<Chronicle.Entry> around = new ArrayList<>();
		for (Chronicle.Entry entry : Chronicle.all()) {
			if (entry != about && entry.day() <= day && Math.abs(entry.day() - about.day()) <= TownCulture.YEAR && !TownStory.dull(entry.text())
				&& !sides.isEmpty() && sides.stream().allMatch(entry.text()::contains)) {
				around.add(entry);
			}
		}

		Chronicle.Entry it = about;
		List<Chronicle.Entry> before = new ArrayList<>(around.stream().filter(e -> earlier(e, it) && cause(e.text())).toList());
		List<Chronicle.Entry> after = new ArrayList<>(around.stream().filter(e -> !earlier(e, it)).toList());
		List<Part> parts = new ArrayList<>();
		if (!before.isEmpty()) {
			Chronicle.Entry start = before.get(0);
			parts.add(new Part("town.story.ask.event_start", vars("when", when(day - start.day()), "what", TownStory.said(start, drifter))));
		}

		parts.add(new Part(before.isEmpty() ? "town.story.ask.event_first" : "town.story.ask.event_then", vars("when", when(day - about.day()), "what",
			TownStory.said(about, drifter), "thing", event)));
		after.sort((a, b) -> earlier(a, b) ? -1 : earlier(b, a) ? 1 : 0);
		before.sort((a, b) -> earlier(a, b) ? -1 : earlier(b, a) ? 1 : 0);
		for (Chronicle.Entry entry : after.subList(0, Math.min(MOST - 1, after.size()))) {
			parts.add(new Part("town.story.ask.event_then", vars("when", when(day - entry.day()), "what", TownStory.said(entry, drifter))));
			t.last = entry;
		}

		if (after.isEmpty()) {
			t.last = about;
		}

		parts.add(new Part(parts.size() > 1 ? "town.story.ask.event_end" : "town.story.ask.event_end_short", vars("thing", event)));
		chain(drifter, player, parts);
	}

	/** Why the last thing told happened: what set them against each other, if it's known. */
	private static void why(DrifterEntity drifter, ServerPlayer player, Told t, long day) {
		Chronicle.Entry last = t.last;
		if (last == null) {
			TalkApi.answer(drifter, player, "town.story.ask.why_unknown", null);
			return;
		}

		List<String> sides = sides(last);
		Chronicle.Entry cause = null;
		for (Chronicle.Entry entry : Chronicle.all()) {
			String text = entry.text();
			if (entry != last && earlier(entry, last) && !sides.isEmpty() && sides.stream().allMatch(text::contains) && cause(text)) {
				cause = entry;
			}
		}

		if (cause == null) {
			TalkApi.answer(drifter, player, "town.story.ask.why_unknown", null);
			return;
		}

		t.last = cause;
		TalkApi.answer(drifter, player, "town.story.ask.why", null, "when", when(day - cause.day()), "what", TownStory.said(cause, drifter));
	}

	/** What it hadn't got to: the next things of its past. */
	private static void more(DrifterEntity drifter, ServerPlayer player, Town town, Told t, long day) {
		if (t.untold.isEmpty()) {
			TalkApi.answer(drifter, player, "town.story.ask.more_none", null);
			return;
		}

		List<Chronicle.Entry> next = new ArrayList<>(t.untold.subList(0, Math.min(2, t.untold.size())));
		t.untold.removeAll(next);
		next.sort((a, b) -> Long.compare(a.day(), b.day()));
		Map<String, UUID> byName = byName();
		List<Part> parts = new ArrayList<>();
		for (int i = 0; i < next.size(); i++) {
			Chronicle.Entry entry = next.get(i);
			parts.add(new Part(i == 0 ? "town.story.ask.more" : "town.story.past_more", vars("when", when(day - entry.day()), "what",
				TownStory.said(entry, drifter))));
			mentioned(t, entry, byName);
			t.last = entry;
		}

		if (t.untold.isEmpty()) {
			parts.add(new Part("town.story.ask.more_end", vars()));
		}

		chain(drifter, player, parts);
	}

	/** Another town, or a nation, it named: who leads it, how big, whose. */
	private static void place(DrifterEntity drifter, ServerPlayer player, Town ours, String name, long day) {
		for (Nations.Nation nation : Nations.all()) {
			if (nation.name.equals(name)) {
				Town capital = Towns.all().stream().filter(one -> one.id.equals(nation.capital)).findFirst().orElse(null);
				String leader = capital == null ? null : TownTalk.leader(capital);
				boolean mine = nation.has(ours.id);
				TalkApi.answer(drifter, player, mine ? "town.story.ask.nation_ours" : "town.story.ask.nation", null, "nation", nation.name, "capital",
					capital == null ? "its capital" : capital.name, "leader", leader == null ? "nobody much" : leader, "towns", nation.towns().size(), "ago",
					TownStory.ago(Math.max(0, day - nation.foundedDay)));
				return;
			}
		}

		for (Town town : Towns.all()) {
			if (town.name.equals(name)) {
				String leader = TownTalk.leader(town);
				DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
				Nations.Nation nation = Nations.of(town);
				TalkApi.answer(drifter, player, nation == null ? "town.story.ask.town" : "town.story.ask.town_nation", null, "place", town.name, "leader",
					leader == null ? "nobody" : leader, "people", group == null ? 0 : group.members.size(), "nation", nation == null ? "" : nation.name, "ago",
					TownStory.ago(Math.max(0, day - Math.min(town.bornDay, town.foundedDay))));
				return;
			}
		}

		TalkApi.answer(drifter, player, "town.story.ask.place_none", null, "place", name);
	}

	// ---------------------------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------------------------

	/** What sets towns against each other: a quarrel, a grudge, an insult, a killing between them, a war declared, a demand. */
	private static boolean cause(String text) {
		return text.contains("quarrel") || text.contains("against each other") || text.contains("grudge") || text.contains("insult")
			|| text.contains("declared war") || text.contains("killing stands") || text.contains("demanded") || text.contains("never got on");
	}

	/** Came before that one (by its day, then its moment). */
	private static boolean earlier(Chronicle.Entry a, Chronicle.Entry b) {
		return a.day() < b.day() || a.day() == b.day() && a.time() < b.time();
	}

	private static Object[] vars(Object... vars) {
		return vars;
	}

	private static String when(long days) {
		return TownStory.when(days);
	}

	/** The words of a line, as names are (no full stops, commas or 's). */
	private static List<String> words(String text) {
		return List.of(text.replace("'s ", " ").split("[^A-Za-z0-9_]+"));
	}

	/** Is this about this town (or its nation)? */
	private static boolean concerns(Chronicle.Entry entry, Town town) {
		Nations.Nation nation = Nations.of(town);
		return entry.text().contains(town.name) || nation != null && entry.text().contains(nation.name);
	}

	/** The towns and nations a line names (at most two: the sides of it). */
	private static List<String> sides(Chronicle.Entry entry) {
		List<String> sides = new ArrayList<>();
		for (Nations.Nation nation : Nations.all()) {
			if (entry.text().contains(nation.name)) {
				sides.add(nation.name);
			}
		}

		for (Town town : Towns.all()) {
			if (entry.text().contains(town.name) && sides.stream().noneMatch(side -> side.contains(town.name))) {
				sides.add(town.name);
			}
		}

		return sides.subList(0, Math.min(2, sides.size()));
	}

	/** The town this Drifter lives in, or null. */
	@Nullable
	private static String home(UUID id) {
		DrifterRecord record = DrifterApi.record(id);
		UUID group = record == null ? null : record.group();
		if (group == null) {
			return null;
		}

		for (Town town : Towns.all()) {
			if (group.equals(town.group)) {
				return town.name;
			}
		}

		return null;
	}

	/** The town this one leads, or null. */
	@Nullable
	private static String leaderOf(UUID id) {
		for (Town town : Towns.all()) {
			if (id.equals(town.leader)) {
				return town.name;
			}
		}

		return null;
	}
}
