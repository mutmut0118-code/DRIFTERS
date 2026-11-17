package com.dinglebob.drifterciv;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;
import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterRecord;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;
import net.minecraft.util.RandomSource;

/**
 * A line of the chronicle as a player would tell it (the user, 2026-11-17, of a town's story: "how can we make it easier to
 * read, and more prose like a player is talking"; it was "amethyst635, who had ruled the Draberg League for 281 days, died;
 * saezy4 rules it now"). The chronicle's kinds of line, each said in a few casual ways: names as they're called
 * ("lagoon", not "oeagerlagoon"), itself as "i" or "me", its own town as "the town" or "us" where that reads right. A line
 * it doesn't know is said as before (its first word lower case, no full stop).
 */
final class Retell {
	private Retell() {
	}

	/** One kind of chronicle line and the ways of saying it. */
	private record Way(Pattern pattern, Function<Ctx, List<String>> says) {
	}

	/** A matched line: its parts, who's telling it, its town. */
	private record Ctx(Matcher m, DrifterEntity teller, @Nullable String town) {
		/** A name as it's called; itself "i". */
		String who(int group) {
			return Retell.who(this.m.group(group), this.teller, true);
		}

		/** A name as it's called; itself "me". */
		String whom(int group) {
			return Retell.who(this.m.group(group), this.teller, false);
		}

		/** A place: its own town "the town", others by name. */
		String place(int group) {
			String place = this.m.group(group);
			return place.equals(this.town) ? "the town" : place;
		}

		/** Its own town? */
		boolean ours(int group) {
			return this.m.group(group).equals(this.town);
		}

		String g(int group) {
			return this.m.group(group);
		}
	}

	private static final List<Way> WAYS = List.of(
		way("^(\\S+), who had ruled (.+?) for (\\d+) days, died; (\\S+) rules it now$", c -> List.of(
			c.who(1) + " ruled " + c.g(2) + " for " + c.g(3) + " days, then died. " + c.who(4) + " took over",
			"the old ruler " + c.who(1) + " died after " + c.g(3) + " days in charge. " + c.who(4) + " runs " + c.g(2) + " now",
			c.who(1) + " used to rule " + c.g(2) + ", like " + c.g(3) + " days. then they died and " + c.who(4) + " got the crown")),
		way("^(\\S+) was chosen to lead (.+)$", c -> c.ours(2)
			? List.of("they picked " + c.whom(1) + " to lead us", c.who(1) + " got picked to lead the town", "everyone chose " + c.whom(1) + " to be in charge")
			: List.of(c.g(2) + " picked " + c.whom(1) + " to lead them", c.who(1) + " got chosen to lead " + c.g(2))),
		way("^(\\S+) founded (.+)$", c -> List.of(c.who(1) + " founded " + c.place(2), c.who(1) + " started " + c.place(2))),
		way("^(.+?) was founded by (\\S+) and (\\S+)$", c -> List.of(c.who(2) + " and " + c.who(3) + " founded " + c.place(1),
			c.who(2) + " and " + c.who(3) + " started " + c.place(1) + " together")),
		way("^(\\S+) of (.+?) and (\\S+) of (.+?) have an old grudge$", c -> List.of(c.who(1) + " and " + c.who(3) + " have had beef forever",
			c.who(1) + " and " + c.who(3) + " from " + c.g(4) + " cant stand each other. old grudge")),
		way("^(\\S+) and (\\S+) of (.+?) have been best friends for years$", c -> List.of(c.who(1) + " and " + c.who(2) + " have been best friends forever",
			c.who(1) + " and " + c.who(2) + ", best friends for years")),
		way("^(\\S+) and (\\S+) of (.+?) have never got on$", c -> List.of(c.who(1) + " and " + c.who(2) + " have never got on",
			c.who(1) + " and " + c.who(2) + " never liked each other")),
		way("^(.+?) declared war on (.+)$", c -> List.of(c.place(1) + " went to war with " + c.place(2), c.place(1) + " declared war on " + c.place(2)
			+ ". things got ugly", "war broke out, " + c.place(1) + " against " + c.place(2))),
		way("^(.+?) and (.+?) made peace after (\\d+) days? \\((.+)\\)$", c -> List.of(c.place(1) + " and " + c.place(2) + " made peace after "
			+ days(c.g(3)) + (c.g(4).contains("neither") ? ". nobody paid a thing" : ", " + c.g(4)), "the war ended after " + days(c.g(3))
			+ (c.g(4).contains("neither") ? ", no one paid anything" : ". " + c.g(4)))),
		way("^(.+?) made peace with (.+)$", c -> List.of(c.place(1) + " made peace with " + c.place(2), "peace with " + c.place(2) + " finally")),
		way("^(\\S+) of (.+?) fell in the war with (.+?) \\(killed by (\\S+)\\)$", c -> c.ours(2)
			? List.of("we lost " + c.whom(1) + " in the war with " + c.g(3) + ". " + c.who(4) + " got them", c.who(4) + " killed " + c.whom(1)
				+ " in the war. still hurts")
			: List.of(c.who(1) + " from " + c.g(2) + " died fighting " + c.place(3) + ". " + c.who(4) + " got them")),
		way("^(\\S+) of (.+?) fell in the war with (.+)$", c -> List.of((c.ours(2) ? "we lost " + c.whom(1) : c.who(1) + " from " + c.g(2) + " died")
			+ " in the war with " + c.place(3))),
		way("^A hard winter in (.+?): part of a field failed$", c -> c.ours(1)
			? List.of("we had a brutal winter, lost part of a field", "hard winter that year. part of the crops died")
			: List.of(c.g(1) + " had a rough winter, lost some crops")),
		way("^A quarrel at the border set (.+?) and (.+?) against each other$", c -> List.of("a fight at the border turned " + c.place(1) + " and "
			+ c.place(2) + " against each other", "some border thing started it, " + c.place(1) + " and " + c.place(2) + " have hated each other since")),
		way("^(\\d+) of (.+?)'s people and (\\d+) of (.+?)'s died fighting near (.+)$", c -> List.of("there was a fight near " + c.place(5) + ". "
			+ c.place(2) + " lost " + c.g(1) + ", " + c.place(4) + " lost " + c.g(3), "big fight near " + c.place(5) + ", " + c.g(1) + " of "
			+ c.place(2) + " and " + c.g(3) + " of " + c.place(4) + " died")),
		way("^(\\d+) of (.+?)'s people died fighting near (.+)$", c -> List.of(c.place(2) + " lost " + c.g(1) + " in a fight near " + c.place(3))),
		way("^(.+?) was abandoned when its fields failed; its people went their ways$", c -> List.of(c.g(1)
			+ " got abandoned when the fields failed. everyone just left", "the fields failed at " + c.g(1) + " and the whole place emptied out")),
		way("^(\\S+) was slain by (\\S+)( \\(gone for good\\))?$", c -> List.of(c.who(2) + " killed " + c.whom(1) + (c.g(3) != null ? ". for good" : ""),
			c.whom(1) + " got killed by " + c.whom(2))),
		way("^(\\S+) killed (\\S+) of (.+?) too \\((\\d+) in all\\)$", c -> List.of(c.who(1) + " killed " + c.whom(2) + " too. " + c.g(4)
			+ " people in all")),
		way("^(\\S+) killed (\\S+) of (.+)$", c -> List.of(c.who(1) + " killed " + c.whom(2) + (c.ours(3) ? ", one of ours" : " from " + c.g(3)))),
		way("^(\\S+) of (.+?) was killed by bandits on the road$", c -> List.of("bandits got " + c.whom(1) + " on the road",
			c.who(1) + " got killed by bandits out on the road")),
		way("^(\\S+) of (.+?) fell into a ravine$", c -> List.of(c.who(1) + " fell into a ravine. awful", "we lost " + c.whom(1) + " to a ravine")),
		way("^(\\S+) left (.+?) for good \\(its house stands empty\\)$", c -> List.of(c.who(1) + " left for good. their house is still empty",
			c.who(1) + " packed up and left. never came back")),
		way("^(.+?) set off to raid (.+)$", c -> List.of(c.place(1) + " went off to raid " + c.place(2), c.place(1) + " marched on " + c.place(2))),
		way("^(.+?) burned (.+)$", c -> List.of(c.place(1) + " burned " + c.place(2), c.place(1) + " set " + c.place(2) + " on fire")),
		way("^(.+?) rose against (.+)$", c -> List.of(c.place(1) + " rose up against " + c.place(2), c.place(1) + " rebelled against " + c.place(2))),
		way("^(.+?) won its freedom from (.+)$", c -> List.of(c.place(1) + " won its freedom from " + c.place(2), c.place(1) + " broke free of "
			+ c.place(2))),
		way("^(.+?) joined (.+)$", c -> List.of(c.place(1) + " joined " + c.place(2))),
		way("^The people of (.+?) and (.+?) go back a long way \\((.+)\\)$", c -> List.of(c.place(1) + " and " + c.place(2) + " go way back",
			"people in " + c.place(1) + " and " + c.place(2) + " have been friends a long time")));

	private static Way way(String regex, Function<Ctx, List<String>> says) {
		return new Way(Pattern.compile(regex), says);
	}

	/** The chronicle's line as this one would say it; its own town is {@code town} (null: none). */
	static String tell(String text, DrifterEntity teller, @Nullable String town) {
		String line = text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
		for (Way way : WAYS) {
			Matcher m = way.pattern().matcher(line);
			if (m.matches()) {
				List<String> says = way.says().apply(new Ctx(m, teller, town));
				RandomSource random = teller.getRandom();
				return says.get(random.nextInt(says.size()));
			}
		}

		return null;
	}

	/** Its own town's name, or null. */
	@Nullable
	static String townOf(DrifterEntity drifter) {
		DrifterGroups.Group group = DrifterGroups.of(drifter.getUUID());
		if (group == null) {
			return null;
		}

		for (Town town : Towns.all()) {
			if (group.id.equals(town.group)) {
				return town.name;
			}
		}

		return null;
	}

	/** A name as it's called (a Drifter's nickname); the teller itself "i" (or "me"). */
	static String who(String name, DrifterEntity teller, boolean subject) {
		if (name.equals(teller.getName().getString())) {
			return subject ? "i" : "me";
		}

		for (DrifterRecord record : DrifterApi.directory()) {
			if (record.name().equals(name)) {
				return com.dinglebob.drifter.identity.Nicknames.of(name);
			}
		}

		return name;
	}

	/** "1 days" said right. */
	private static String days(String n) {
		return n.equals("1") ? "a day" : n + " days";
	}

	/** Names joined as said ("lagoon and marble"). */
	static String names(List<String> names, DrifterEntity teller) {
		return String.join(" and ", names.stream().map(name -> who(name, teller, true)).toList());
	}
}
