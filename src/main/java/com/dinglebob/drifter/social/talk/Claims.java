package com.dinglebob.drifter.social.talk;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

/**
 * A topic line that says something about now is said only when it's so (the user, 2026-11-14, of "nice gear btw" said to
 * them with no armor on: "can you check that for the rest of the lines? so if someone says something it actually makes
 * sense"). The other's armor and what they hold, the rain, the time of day, its own hunger: a line claiming one of them
 * is left out of the pool when it isn't true, and the speaker says another line of the same turn. A topic, turn or
 * option that already names that thing in its conditions is about it on purpose ("nice armor. oh wait" under
 * {@code !b:armor_diamond}), and is left alone.
 */
public final class Claims {
	private Claims() {
	}

	/** One kind of claim: what it looks like, the conditions that already cover it, and whether it's so. */
	private record Claim(Pattern looks, List<String> covered, Check check) {
	}

	@FunctionalInterface
	private interface Check {
		boolean so(Matcher match, Facts speaker, Facts listener, Set<String> world);
	}

	private static Pattern p(String regex) {
		return Pattern.compile(regex);
	}

	private static boolean raining(Set<String> world) {
		return world.contains("rain") || world.contains("thunder");
	}

	private static final List<Claim> CLAIMS = List.of(
		// Praise of the other's armor ("nice gear btw", "your armor looks good"): they wear some.
		new Claim(p("^([a-z/]+[,.!]? |well well\\. )?(nice|cool|sick|fancy|love your|i like your|loving the) (gear|armor|armour)\\b"
			+ "|\\byour (armor|armour|gear)('s| is)? (look|looking|looks) (good|great|nice|sharp|sick|clean)"
			+ "|^where'?d you get (that|all that) (stuff|gear|armor)"), List.of("armor_"),
			(m, speaker, listener, world) -> !listener.flags.contains("armor_none")),
		// "Where's your armor?": they wear none.
		new Claim(p("where'?s your armor|no armor on\\b|not wearing (any )?armor|you have no armor"), List.of("armor_"),
			(m, speaker, listener, world) -> listener.flags.contains("armor_none")),
		// Praise of what they hold ("nice sword"): they hold one.
		new Claim(p("^([a-z/]+[,.!]? )?(nice|cool|sick|fancy|love your|i like your) (sword|bow|axe|pick|pickaxe|shield|crossbow|trident|mace)\\b"),
			List.of("holding", "held"),
			(m, speaker, listener, world) -> String.valueOf(listener.words.getOrDefault("held", "")).contains(m.group(3).equals("pick") ? "pickaxe" : m.group(3))),
		// Rain now ("i'm gonna watch the rain", "this storm is scary", "the rain's getting in"): it's raining.
		new Claim(p("^(i'?m gonna watch|enjoy|listen to) the rain|\\bthis (rain|storm)\\b|the rain'?s getting in|(?<!when )(?<!if )\\b(it'?s|its|still) (raining|pouring)"
			+ "|you'?re soaked|^let'?s stay dry|^rain'?s nice, honestly"), List.of("rain", "thunder", "clear"),
			(m, speaker, listener, world) -> raining(world)),
		// Fine weather now ("nice weather", "sun's out"): not raining (and the sun's up).
		new Claim(p("nice weather|lovely weather(?!\\.+ not)|sun'?s out|beautiful day|nice day out"), List.of("rain", "thunder", "clear"),
			(m, speaker, listener, world) -> !raining(world) && (!m.group().startsWith("sun") || !world.contains("night"))),
		// "Morning!", "gm": it's morning.
		new Claim(p("^(good )?morning([!.]*$|[,!]? (all|everyone|neighbou?r|friend|sunshine|\\{))|^gm\\b"), List.of("morning", "time_"),
			(m, speaker, listener, world) -> world.contains("morning")),
		// "It's getting dark", "night's coming": it's evening or night.
		new Claim(p("^(its|it'?s) (getting )?(dark|late)( out)?([,.!]|$)|^night'?s coming"), List.of("evening", "night", "time_"),
			(m, speaker, listener, world) -> world.contains("evening") || world.contains("night")),
		// Its own armor ("my netherite set is maxed", "my diamond armor"): it wears it (the best it has on, for diamond and up;
		// some armor at all, for the rest).
		new Claim(p("\\bmy (netherite|diamond|iron|gold|golden|chain|chainmail|leather) (set|armor|armour|gear|chestplate|helmet|boots|leggings)\\b"
			+ "|\\bi'?m (in|wearing) (full )?(netherite|diamond|iron)\\b"), List.of("armor_"),
			(m, speaker, listener, world) -> {
				String kind = m.group(1) != null ? m.group(1) : m.group(5);
				return kind.equals("netherite") || kind.equals("diamond") ? speaker.flags.contains("armor_" + kind) : !speaker.flags.contains("armor_none");
			}),
		// "I'm so hungry": it is.
		new Claim(p("(?<!when )\\bi'?m (so |really )?(starving|hungry)\\b|\\bnow i'?m hungry"), List.of("hungry"),
			(m, speaker, listener, world) -> speaker.flags.contains("hungry")));

	/** Whether a claim like this line's would be checked at all (for the load count). */
	public static boolean claims(String line) {
		String low = line.toLowerCase(Locale.ROOT);
		return CLAIMS.stream().anyMatch(claim -> claim.looks().matcher(low).find());
	}

	/** Whether this line is true enough to say, between these two, now. */
	public static boolean holds(String line, Facts speaker, Facts listener, Set<String> world, List<String> conditions) {
		String low = line.toLowerCase(Locale.ROOT);
		for (Claim claim : CLAIMS) {
			Matcher match = claim.looks().matcher(low);
			if (!match.find() || conditions.stream().anyMatch(c -> claim.covered().stream().anyMatch(c::contains))) {
				continue;
			}

			if (!claim.check().so(match, speaker, listener, world)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * These lines with the untrue ones left out; null when that leaves nothing to say. {@code conditions}: the topic's needs
	 * and the turn's and option's conditions together.
	 */
	@Nullable
	public static Map<String, List<String>> sayable(Map<String, List<String>> voices, @Nullable Facts speaker, @Nullable Facts listener,
		@Nullable Set<String> world, List<String> conditions) {
		if (speaker == null || listener == null || world == null) {
			return voices;
		}

		Map<String, List<String>> kept = new HashMap<>();
		boolean dropped = false;
		for (Map.Entry<String, List<String>> voice : voices.entrySet()) {
			List<String> lines = voice.getValue().stream().filter(line -> holds(line, speaker, listener, world, conditions)).toList();
			dropped |= lines.size() != voice.getValue().size();
			if (!lines.isEmpty()) {
				kept.put(voice.getKey(), lines);
			}
		}

		return !dropped ? voices : kept.isEmpty() ? null : kept;
	}
}
