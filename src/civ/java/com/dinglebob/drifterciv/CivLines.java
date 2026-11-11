package com.dinglebob.drifterciv;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.social.Lines;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Town life's chat lines from the chat data ({@code data/drifter_civ/drifter_chat/town.json}), each with the line the
 * code always said as its fallback (a datapack that empties a kind, or the data not loaded yet).
 */
final class CivLines {
	private CivLines() {
	}

	/** How many lines a bell ringer is handed for one ring (it shouts some of them while it rings). */
	private static final int BELL_LINES = 8;
	private static final Pattern WORD = Pattern.compile("\\{([a-z_.]+)}");

	/** A line of this kind in its voice, words filled in ({@code vars}: name, value, ...); else the fallback. */
	static String pick(DrifterEntity drifter, String key, String fallback, Object... vars) {
		String line = Lines.pick(drifter, key, vars);
		return line == null || line.isEmpty() ? fallback : line;
	}

	/**
	 * Lines for a bell ringer, picked before anyone rings (so no voice): a few of this kind's, every voice's, whose words
	 * it has, filled in; else the fallback.
	 */
	static List<String> bell(String key, List<String> fallback, Object... vars) {
		Map<String, String> have = new HashMap<>();
		for (int i = 0; i + 1 < vars.length; i += 2) {
			if (vars[i + 1] != null) {
				have.put(String.valueOf(vars[i]), String.valueOf(vars[i + 1]));
			}
		}

		List<String> out = new ArrayList<>();
		for (List<String> voice : Lines.voices(key).values()) {
			for (String line : voice) {
				Matcher words = WORD.matcher(line);
				StringBuilder filled = new StringBuilder();
				boolean all = true;
				while (all && words.find()) {
					String value = have.get(words.group(1));
					all = value != null;
					if (all) {
						words.appendReplacement(filled, Matcher.quoteReplacement(value));
					}
				}

				if (all) {
					words.appendTail(filled);
					out.add(filled.toString());
				}
			}
		}

		if (out.isEmpty()) {
			return fallback;
		}

		Collections.shuffle(out, ThreadLocalRandom.current());
		return List.copyOf(out.subList(0, Math.min(BELL_LINES, out.size())));
	}
}
