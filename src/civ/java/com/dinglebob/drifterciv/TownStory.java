package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;
import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.world.DrifterGroups;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A town's story, told by one of its people when asked about it (the user, 2026-11-15: "i want to have actual history behind
 * these towns. but since every town is loaded everyone thinks its new. i want this to be a 3+ part conversation where a
 * drifter can yap"). From what's so of it: when it was founded (its past: a town made with the world has one, W6) and who
 * by, if that's known; one or two things from its past in the chronicle (a war, being taken, rising up, a ruler fallen);
 * how it is now (its leader, its people, its character, its customs); and how the teller feels about it. A line each, one
 * after the other as they're typed; stopped if they say something else or walk off.
 */
final class TownStory {
	private TownStory() {
	}

	/** The most things from its past told. */
	private static final int MOST_PAST = 2;
	/** Past this far, the listener's gone: the story stops. */
	private static final double GONE = 24.0;
	/** When each player last said anything to a Drifter (a story stops when they say something else). */
	private static final Map<UUID, Long> spoke = new HashMap<>();
	/** "..., Bob its first ruler". */
	private static final Pattern FIRST_RULER = Pattern.compile(", ([^,]+?) its first ruler");

	/** They said something to a Drifter (before it's answered): false, so the chat handlers go on. */
	static boolean spokeNow(ServerPlayer player) {
		spoke.put(player.getUUID(), player.level().getGameTime());
		return false;
	}

	/** A part of the story: its line's key and words. */
	private record Part(String key, Object[] vars) {
	}

	/** Tells them the town's story (true: it's begun). */
	static boolean tell(DrifterEntity drifter, ServerPlayer player, Town town) {
		MinecraftServer server = drifter.getServer();
		if (server == null) {
			return false;
		}

		long day = Chronicle.day(server);
		long ago = Math.max(0, day - Math.min(town.bornDay, town.foundedDay));
		List<Part> parts = new ArrayList<>();
		String nation = nationName(town);

		// Its founding.
		String founder = founder(town);
		if (ago < 3) {
			parts.add(new Part("town.story.new", vars("founder", founder == null ? "" : founder)));
		} else {
			String key = founder != null ? "town.story.founded_by" : "town.story.founded";
			parts.add(new Part(key, vars("ago", ago(ago), "founder", founder == null ? "" : founder)));
		}

		// Its past.
		List<Chronicle.Entry> past = past(town, nation, day);
		for (int i = 0; i < past.size(); i++) {
			Chronicle.Entry entry = past.get(i);
			// (Long ago: "old folks still talk about it"; this year: "not long ago".)
			boolean old = day - entry.day() >= TownCulture.YEAR;
			parts.add(new Part(i == 0 ? old ? "town.story.past" : "town.story.past_recent" : "town.story.past_more", vars("when", when(day - entry.day()), "what",
				mine(spoken(entry.text()), drifter.getName().getString()))));
		}

		// How it is now.
		String leader = TownTalk.leader(town);
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		int people = group == null ? 0 : group.members.size();
		String character = TownCulture.character(server, town);
		boolean leaderItself = town.leader != null && town.leader.equals(drifter.getUUID());
		if (leaderItself) {
			parts.add(new Part(nation != null ? "town.story.now_mine_nation" : "town.story.now_mine", vars("people", people, "character", character,
				"nation", nation == null ? "" : nation)));
		} else if (leader != null) {
			parts.add(new Part(nation != null ? "town.story.now_nation" : "town.story.now", vars("leader", leader, "people", people, "character", character,
				"nation", nation == null ? "" : nation)));
		} else {
			parts.add(new Part("town.story.now_noleader", vars("people", people, "character", character)));
		}

		String custom = custom(town);
		if (custom != null) {
			parts.add(new Part("town.story.custom", vars("custom", custom)));
		}

		// How it feels about it.
		parts.add(new Part(leaderItself ? "town.story.feel_leader" : "town.story.feel", vars()));
		next(drifter, player, parts, 0, player.level().getGameTime());
		return true;
	}

	/** The next part, once the last is said; none if they've said something else since, or gone. */
	private static void next(DrifterEntity drifter, ServerPlayer player, List<Part> parts, int i, long began) {
		if (i >= parts.size() || !drifter.isAlive() || player.isRemoved() || drifter.level() != player.level() || drifter.distanceTo(player) > GONE
			|| i > 0 && spoke.getOrDefault(player.getUUID(), -1L) > began) {
			return;
		}

		Part part = parts.get(i);
		Runnable then = () -> next(drifter, player, parts, i + 1, began);
		if (!TalkApi.answer(drifter, player, part.key(), then, part.vars())) {
			then.run();
		}
	}

	private static Object[] vars(Object... vars) {
		return vars;
	}

	/** Its nation's name, or null (a free town). */
	@Nullable
	private static String nationName(Town town) {
		Nations.Nation nation = Nations.of(town);
		return nation == null ? null : nation.name;
	}

	/** Its first ruler, if the chronicle says ("X was founded, and N with it, Bob its first ruler"; "Ann founded X"). */
	@Nullable
	private static String founder(Town town) {
		for (Chronicle.Entry entry : Chronicle.all()) {
			String text = entry.text();
			if (text.startsWith(town.name + " was founded")) {
				Matcher m = FIRST_RULER.matcher(text);
				if (m.find()) {
					return m.group(1);
				}
			}

			int at = text.indexOf(" founded " + town.name);
			if (at > 0 && !text.substring(0, at).contains(" ")) {
				return text.substring(0, at);
			}
		}

		return null;
	}

	/**
	 * The things from its past worth telling, oldest first: what happened to it (taken, burned, rose up, joined, a battle
	 * near it), else its nation's wars and rulers. Not its founding, its laws, who leads it, how it was made.
	 */
	private static List<Chronicle.Entry> past(Town town, @Nullable String nation, long day) {
		List<Chronicle.Entry> ours = new ArrayList<>();
		List<Chronicle.Entry> nations = new ArrayList<>();
		for (Chronicle.Entry entry : Chronicle.all()) {
			String text = entry.text();
			boolean big = switch (entry.kind()) {
				case "war", "nation", "diplomacy", "festival" -> true;
				default -> false;
			};
			if (entry.day() >= day || text.contains(" was founded") || text.contains(" founded " + town.name) || text.contains("stands where a village")
				|| text.contains("'s laws for ") || text.contains(" leads " + town.name) || text.startsWith(History.AFAR) || dull(text)) {
				continue;
			}

			if (text.contains(town.name) && (big || entry.kind().equals("town"))) {
				ours.add(entry);
			} else if (nation != null && big && text.contains(nation) && !entry.kind().equals("festival")) {
				nations.add(entry);
			}
		}

		List<Chronicle.Entry> told = new ArrayList<>(pick(ours, MOST_PAST));
		if (told.size() < MOST_PAST) {
			told.addAll(pick(nations, MOST_PAST - told.size()));
		}

		told.sort((a, b) -> Long.compare(a.day(), b.day()));
		return told;
	}

	/** Everyday doings, not worth a story: a house built, a trade taken up, someone setting out. */
	private static boolean dull(String text) {
		for (String everyday : new String[] {" built a house", " took up the ", " made a home", " set up as", " set out from", " moved into", " left ",
			" arrived in ", " came to live", " was born"}) {
			if (text.contains(everyday)) {
				return true;
			}
		}

		// ("Storyford had 6 houses": how it was made.)
		return text.matches(".* had \\d+ houses.*");
	}

	/** The biggest of these (a war's end or a town taken over a quarrel), at most this many. */
	private static List<Chronicle.Entry> pick(List<Chronicle.Entry> entries, int most) {
		List<Chronicle.Entry> sorted = new ArrayList<>(entries);
		sorted.sort((a, b) -> weight(b) - weight(a));
		return sorted.subList(0, Math.min(most, sorted.size()));
	}

	private static int weight(Chronicle.Entry entry) {
		String text = entry.text();
		if (text.contains(" burned") || text.contains(" took ") || text.contains(" rose against") || text.contains(" won its freedom")
			|| text.contains(" conquered")) {
			return 5;
		}

		if (text.contains(" fell in the fighting") || text.contains(" made peace") || text.contains(" declared war")) {
			return 4;
		}

		if (text.contains(" died fighting") || text.contains(" ruled ") || text.contains(" joined ") || text.contains(" to lead ")) {
			return 3;
		}

		return entry.kind().equals("war") ? 2 : 1;
	}

	/** Of itself, said as itself: "Ann was chosen to lead it" from Ann is "i was chosen to lead it". */
	private static String mine(String text, String name) {
		if (text.startsWith(name + " ")) {
			return "i" + text.substring(name.length());
		}

		return text.replace(" " + name + "'s ", " my ").replace(" " + name, " me");
	}

	/** A chronicle line as said: no full stop, its first word in lower case unless it's a name. */
	private static String spoken(String text) {
		String said = text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
		for (String common : new String[] {"A ", "The "}) {
			if (said.startsWith(common)) {
				return common.toLowerCase(Locale.ROOT) + said.substring(common.length());
			}
		}

		return said;
	}

	/** How long ago it was founded, as said ("about 3 years", "200 days or so"). */
	private static String ago(long days) {
		long years = days / TownCulture.YEAR;
		if (years >= 2) {
			return "about " + years + " years";
		}

		if (years == 1) {
			return "a year and a bit";
		}

		return days >= 20 ? (days / 10 * 10) + " days or so" : days + " days";
	}

	/** When something was ("long before i was born", "a couple of years back", "last season"). */
	private static String when(long days) {
		long years = days / TownCulture.YEAR;
		if (years >= 10) {
			return "way back, " + years + " years ago";
		}

		if (years >= 2) {
			return "about " + years + " years back";
		}

		if (years == 1) {
			return "a year or so ago";
		}

		return days >= 20 ? "earlier this year" : days >= 3 ? "a few days ago" : "just the other day";
	}

	/** One of its customs, as said; null if none. */
	@Nullable
	private static String custom(Town town) {
		List<String> said = new ArrayList<>();
		for (String word : town.traditions) {
			String[] parts = word.split(":");
			switch (parts[0]) {
				case "fishing" -> said.add("theres a fishing contest every week");
				case "market" -> said.add("we have the big market here, every third day");
				case "victory" -> said.add("every year we keep Victory Day, for beating " + (parts.length > 2 ? parts[2] : "them"));
				case "remembrance" -> said.add("we keep a quiet day every year for " + (parts.length > 2 ? parts[2] : "someone we lost"));
				case "keep away" -> said.add("nobody goes near the spot where three of us died, out past town");
				default -> {
				}
			}
		}

		return said.isEmpty() ? null : said.get(Math.floorMod(town.id.hashCode(), said.size()));
	}
}
