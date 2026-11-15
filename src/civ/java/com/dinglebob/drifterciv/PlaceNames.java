package com.dinglebob.drifterciv;

import java.util.Locale;

import net.minecraft.util.RandomSource;

/**
 * Names for places and nations (the user, 2026-11-15: "bigger lists / single word and two word / no doubles / and make the
 * nation name different from the capital name, kinda like a country, and its states"). A place's name is most often two
 * pieces run together (Stonemere, Ashford), now and then one word of its own (Saltmarsh, Greywater) or two words (Highmoor
 * Cross, Upper Ashford); never a piece twice (Hollowhollow) nor three letters the same in a row. A nation's name is its
 * own, a country's (the Kingdom of Valdoria, the Aldmark League), not its capital's.
 */
final class PlaceNames {
	private PlaceNames() {
	}

	/** The share of names that are one word of their own, and two words (the rest: two pieces run together). */
	private static final double SINGLE = 0.1;
	private static final double TWO_WORDS = 0.15;

	private static final String[] FIRST = {"Oak", "Iron", "Stone", "Ash", "Elder", "Raven", "Wolf", "Mill", "Brook", "Fair", "High", "Red", "Cold",
		"West", "Thorn", "Willow", "Copper", "Amber", "Hollow", "Pine", "Alder", "Apple", "Barley", "Bear", "Beech", "Birch", "Black", "Bramble", "Bright",
		"Broad", "Buck", "Burn", "Calder", "Cedar", "Chalk", "Clay", "Clear", "Cliff", "Clover", "Coal", "Crane", "Crow", "Dark", "Deep", "Deer", "Dove",
		"Down", "Dry", "Dun", "Dusk", "East", "Elm", "Ember", "Fallow", "Fen", "Fern", "Finch", "Flint", "Fox", "Frost", "Gold", "Gorse", "Granite",
		"Green", "Grey", "Hare", "Hart", "Hawk", "Hazel", "Heath", "Heron", "Holly", "Honey", "Horn", "Ivy", "Kettle", "King", "Lake", "Lark", "Long",
		"Low", "Maple", "Marsh", "Meadow", "Mist", "Moss", "Mud", "Nettle", "North", "Old", "Otter", "Owl", "Pebble", "Pike", "Plum", "Quarry", "Reed",
		"Ridge", "River", "Rook", "Rose", "Rowan", "Rush", "Rye", "Sage", "Salt", "Sand", "Shale", "Silver", "Sloe", "Snow", "South", "Sparrow", "Spring",
		"Stag", "Still", "Storm", "Summer", "Sun", "Swan", "Tall", "Thistle", "Timber", "Tin", "Wheat", "White", "Wild", "Wind", "Winter", "Wren", "Yew"};
	private static final String[] LAST = {"ford", "vale", "hollow", "stead", "wick", "bury", "field", "ridge", "haven", "moor", "brook", "crest", "dale",
		"gate", "mere", "barrow", "beck", "bourne", "bridge", "burn", "by", "cliff", "combe", "cot", "croft", "cross", "den", "dene", "don", "fall",
		"fell", "fen", "firth", "fold", "garth", "glen", "grove", "ham", "hampton", "heath", "hill", "holm", "hurst", "keep", "lake", "land",
		"lea", "ley", "low", "march", "marsh", "mead", "minster", "mont", "more", "mouth", "ness", "port", "reach", "rock", "shaw", "shire", "side",
		"stone", "stow", "thorpe", "ton", "wall", "ward", "water", "well", "wood", "worth", "wyn", "yard"};
	/** Names of one word, whole (not built from the pieces). */
	private static final String[] SINGLES = {"Saltmarsh", "Greywater", "Highgarden", "Lowmarsh", "Riverbend", "Stillwater", "Blackwater", "Kingsbridge",
		"Oldcastle", "Summerhall", "Winterfold", "Brightwater", "Deepdale", "Silverlake", "Goldenfield", "Thornbury", "Hearthstone", "Crossroads",
		"Fairhaven", "Northwatch", "Southmere", "Eastmarch", "Westerly", "Dunmoor", "Hollowell", "Harrowgate", "Lantern", "Millbrook", "Mossgrove",
		"Wayfarer", "Waymeet", "Ferry", "Landing", "Haystack", "Copperpot", "Tinker", "Lockhart", "Sheepfold", "Shepherd", "Cinder", "Ravensrest",
		"Wolfsden", "Bearsden", "Foxhole", "Hawksnest", "Kingsrest", "Lastlight", "Firstlight", "Dawnmere", "Duskwater", "Starfall", "Moonbrook",
		"Hightower", "Watchfield", "Oxbow", "Gallows", "Pennywhistle", "Bellwether", "Candlewick", "Ashenford"};
	/** Second words, after a name (Highmoor Cross). */
	private static final String[] AFTER = {"Cross", "Crossing", "End", "Green", "Hill", "Bridge", "Mill", "Market", "Landing", "Ferry", "Wells", "Hollow",
		"Heath", "Down", "Common", "Rise", "Gate", "Corner", "Fields", "Falls", "Springs", "Point", "Bay", "Fort", "Abbey", "Priory", "Keep", "Moor"};
	/** First words, before a name (Upper Ashford). */
	private static final String[] BEFORE = {"Upper", "Lower", "Little", "Great", "Old", "New", "East", "West", "North", "South", "Far", "High", "Long",
		"Saint"};

	/** Pieces of a nation's name, run together (Val + doria, Ald + mark). */
	private static final String[] REALM_FIRST = {"Val", "Ald", "Vel", "Nor", "Cael", "Thal", "Ost", "Mer", "Bran", "Dun", "Eld", "Gal", "Hal", "Kor",
		"Lor", "Mar", "Or", "Pel", "Rav", "Sar", "Tor", "Ul", "Wes", "Ys", "Bel", "Cor", "Dra", "Fen", "Gar", "Hel", "Ist", "Kael", "Lys", "Mor", "Nev",
		"Ar", "Ber", "Car", "Der", "Est", "Fal", "Gwen", "Ivar", "Jor", "Kal", "Lan", "Mal", "Nim", "Ros", "Sil", "Tam", "Vor", "Wyn", "Zar", "Ab",
		"Aer", "Alb", "Ambr", "Ast", "Aud", "Bar", "Bor", "Bryn", "Cal", "Cas", "Cind", "Cyr", "Dal", "Dor", "Ed", "Elm", "Ev", "Far", "Fin", "Gil",
		"Gond", "Gry", "Ham", "Har", "Hes", "Hy", "Il", "Ith", "Jas", "Kar", "Ker", "Kyl", "Lae", "Leth", "Lin", "Lund", "Mael", "Myr", "Nar", "Nes",
		"Oak", "Od", "Ol", "Pal", "Per", "Quel", "Rhen", "Rin", "Rud", "Sab", "Sel", "Sten", "Syl", "Tel", "Thor", "Tir", "Tyr", "Ul", "Ur", "Vad",
		"Ven", "Vin", "Wal", "Wil", "Xan", "Yar", "Zel", "Zen"};
	/** A middle syllable now and then (Val + en + doria). */
	private static final String[] REALM_MIDDLE = {"an", "en", "er", "is", "or", "el", "ar", "in", "on", "al", "ir", "ad", "eth", "ul"};
	private static final String[] REALM_LAST = {"doria", "mark", "land", "ia", "oria", "heim", "reach", "wald", "gard", "ovia", "aria", "anor", "mere",
		"wyn", "dor", "ost", "vale", "moor", "ica", "esse", "ara", "endor", "ithia", "oth", "uin", "march", "fell", "holt", "ania", "avia", "berg",
		"burg", "dal", "dell", "enia", "eria", "ford", "garde", "hal", "helm", "holm", "ika", "ion", "istan", "kar", "lund", "mont", "mora", "nia",
		"nor", "ova", "rath", "rian", "rond", "stad", "thar", "thia", "ura", "vel", "via", "wick", "ynn", "zar"};
	/** Its kind of country. */
	private static final String[] REALM_FORMS = {"the Kingdom of %s", "the Realm of %s", "the Crown of %s", "the Duchy of %s", "the Lands of %s",
		"the Principality of %s", "the Commonwealth of %s", "the Free Cities of %s", "the %s Union", "the %s League", "the %s Accord", "the %s Dominion",
		"the %s Confederacy", "the Republic of %s", "the Union of %s", "%s"};

	/** A place's name (not checked against others: the caller tries again if it's taken). */
	static String place(RandomSource random) {
		double roll = random.nextDouble();
		if (roll < SINGLE) {
			return SINGLES[random.nextInt(SINGLES.length)];
		}

		String base = compound(random);
		if (roll < SINGLE + TWO_WORDS) {
			return random.nextBoolean() ? BEFORE[random.nextInt(BEFORE.length)] + " " + base : base + " " + AFTER[random.nextInt(AFTER.length)];
		}

		return base;
	}

	/** Two pieces run together, never a piece twice nor three letters the same in a row. */
	private static String compound(RandomSource random) {
		for (int i = 0; i < 40; i++) {
			String first = FIRST[random.nextInt(FIRST.length)];
			String last = LAST[random.nextInt(LAST.length)];
			if (fits(first, last)) {
				return first + last;
			}
		}

		return "Stoneford";
	}

	/** A nation's name of its own. */
	static String realm(RandomSource random) {
		String base = "Valdoria";
		for (int i = 0; i < 40; i++) {
			String first = REALM_FIRST[random.nextInt(REALM_FIRST.length)];
			// (Now and then a syllable between: Valendoria, Korisheim.)
			if (random.nextDouble() < 0.3) {
				String middle = REALM_MIDDLE[random.nextInt(REALM_MIDDLE.length)];
				if (!fits(first, middle)) {
					continue;
				}

				first = first + middle;
			}

			String last = REALM_LAST[random.nextInt(REALM_LAST.length)];
			if (fits(first, last)) {
				base = first + last;
				break;
			}
		}

		return String.format(Locale.ROOT, REALM_FORMS[random.nextInt(REALM_FORMS.length)], base);
	}

	/** The bare name in a nation's name ("the Kingdom of Valdoria": Valdoria), to tell two apart. */
	static String bare(String realm) {
		String name = realm;
		for (String form : REALM_FORMS) {
			String[] parts = form.split("%s", -1);
			if (name.startsWith(parts[0]) && name.endsWith(parts[1]) && name.length() > parts[0].length() + parts[1].length()) {
				return name.substring(parts[0].length(), name.length() - parts[1].length());
			}
		}

		return name;
	}

	/**
	 * Two pieces that go together: not the same piece twice (Hollow + hollow, Brook + brook, Ridge + ridge), not a piece
	 * ending as the other begins in three letters the same (Fall + low: Falllow), and not a vowel against a vowel that runs
	 * badly (Ivy + ing).
	 */
	static boolean fits(String first, String last) {
		String a = first.toLowerCase(Locale.ROOT);
		String b = last.toLowerCase(Locale.ROOT);
		if (a.equals(b) || a.endsWith(b) || b.startsWith(a)) {
			return false;
		}

		String joined = a + b;
		for (int i = 2; i < joined.length(); i++) {
			if (joined.charAt(i) == joined.charAt(i - 1) && joined.charAt(i) == joined.charAt(i - 2)) {
				return false;
			}
		}

		char end = a.charAt(a.length() - 1);
		char start = b.charAt(0);
		return !("aeiouy".indexOf(end) >= 0 && "aeiouy".indexOf(start) >= 0);
	}
}
