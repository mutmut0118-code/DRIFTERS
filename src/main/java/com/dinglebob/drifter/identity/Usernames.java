package com.dinglebob.drifter.identity;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.registry.ModEntities;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;

/**
 * Player-like usernames (DESIGN.md section 4, "Identity"): 3 to 16 characters of letters, digits and underscores, in
 * the patterns real players use (a word plus digits, two words joined, words with underscores, a first name and a
 * year, the odd styled one like "xXFrostXx"), mostly lowercase. Never a name already in use by a player online or a
 * loaded Drifter, never a listed public figure or creator, and filtered for offensive words. Word lists come from the
 * data files {@code data/<namespace>/drifter_names/} (a datapack can add more).
 * TODO(milestone 12): the world-level identity registry (section 4), so departed Drifters' names stay reserved.
 */
public final class Usernames {
	public static final String DIRECTORY = "drifter_names";
	public static final int MIN_LENGTH = 3;
	public static final int MAX_LENGTH = 16;
	private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_]{" + MIN_LENGTH + "," + MAX_LENGTH + "}");
	private static final int MAX_TRIES = 200;

	record Lists(List<String> words, List<String> adjectives, List<String> firstNames, List<String> prefixes, List<String> suffixes,
		Set<String> banned, List<String> bannedParts, List<String> titles, List<String> sentences, List<String> worldNames, List<String> countryTags,
		List<String> phrases, List<String> channelTags, List<String> emoticons, List<String> mobWords, List<String> mobTails, List<String> places, List<String> placeTails, List<String> jokes) {
	}

	private static final Lists FALLBACK = new Lists(List.of("drift", "stone", "pine", "fox", "moss", "ember"), List.of("lazy", "swift", "quiet"),
		List.of("sam", "alex", "max"), List.of("xX", "itz"), List.of("Xx", "mc"), Set.of(), List.of(), List.of("Dr", "Captain"), List.of("ImNotA%s"),
		List.of("minh", "yuki", "mateo"), List.of("BR", "VN"), List.of("the_real_%s", "Simply%s"), List.of("Playz", "Gaming"), List.of("O_o", "xD"),
		List.of("creeper", "warden"), List.of("slayer", "main"), List.of("boston", "leeds"), List.of("lad", "town"), List.of("Ben_Dover"));
	private static volatile Lists lists = FALLBACK;

	private Usernames() {
	}

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return Drifter.id("names");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				load(manager);
			}
		});
	}

	private static void load(ResourceManager manager) {
		List<String> words = new ArrayList<>();
		List<String> adjectives = new ArrayList<>();
		List<String> firstNames = new ArrayList<>();
		List<String> prefixes = new ArrayList<>();
		List<String> suffixes = new ArrayList<>();
		Set<String> banned = new HashSet<>();
		List<String> bannedParts = new ArrayList<>();
		List<String> titles = new ArrayList<>();
		List<String> sentences = new ArrayList<>();
		List<String> worldNames = new ArrayList<>();
		List<String> countryTags = new ArrayList<>();
		List<String> phrases = new ArrayList<>();
		List<String> channelTags = new ArrayList<>();
		List<String> emoticons = new ArrayList<>();
		List<String> mobWords = new ArrayList<>();
		List<String> mobTails = new ArrayList<>();
		List<String> places = new ArrayList<>();
		List<String> placeTails = new ArrayList<>();
		List<String> jokes = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = file.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				addAll(json, "words", words);
				addAll(json, "adjectives", adjectives);
				addAll(json, "first_names", firstNames);
				addAll(json, "styled_prefixes", prefixes);
				addAll(json, "styled_suffixes", suffixes);
				List<String> bannedNames = new ArrayList<>();
				addAll(json, "banned", bannedNames);
				bannedNames.forEach(name -> banned.add(name.toLowerCase(Locale.ROOT)));
				addAll(json, "banned_parts", bannedParts);
				addAll(json, "titles", titles);
				addAll(json, "sentences", sentences);
				addAll(json, "world_first_names", worldNames);
				addAll(json, "country_tags", countryTags);
				addAll(json, "phrases", phrases);
				addAll(json, "channel_tags", channelTags);
				addAll(json, "emoticons", emoticons);
				addAll(json, "mob_words", mobWords);
				addAll(json, "mob_tails", mobTails);
				addAll(json, "places", places);
				addAll(json, "place_tails", placeTails);
				addAll(json, "jokes", jokes);
			} catch (Exception e) {
				Drifter.LOGGER.error("Couldn't read Drifter names file {}", file.getKey(), e);
			}
		}

		if (words.isEmpty() || adjectives.isEmpty() || firstNames.isEmpty()) {
			Drifter.LOGGER.warn("Drifter name lists are empty; using a small built-in list");
			lists = FALLBACK;
			return;
		}

		bannedParts.replaceAll(part -> part.toLowerCase(Locale.ROOT));
		lists = new Lists(List.copyOf(words), List.copyOf(adjectives), List.copyOf(firstNames), prefixes.isEmpty() ? FALLBACK.prefixes() : List.copyOf(prefixes),
			suffixes.isEmpty() ? FALLBACK.suffixes() : List.copyOf(suffixes), Set.copyOf(banned), List.copyOf(bannedParts),
			titles.isEmpty() ? FALLBACK.titles() : List.copyOf(titles), sentences.isEmpty() ? FALLBACK.sentences() : List.copyOf(sentences),
			or(worldNames, FALLBACK.worldNames()), or(countryTags, FALLBACK.countryTags()), or(phrases, FALLBACK.phrases()), or(channelTags, FALLBACK.channelTags()),
			or(emoticons, FALLBACK.emoticons()), or(mobWords, FALLBACK.mobWords()), or(mobTails, FALLBACK.mobTails()), or(places, FALLBACK.places()),
			or(placeTails, FALLBACK.placeTails()), or(jokes, FALLBACK.jokes()));
		Nicknames.clear();
		Drifter.LOGGER.info("Loaded {} words for Drifter usernames", words.size() + adjectives.size() + firstNames.size());
	}

	private static List<String> or(List<String> list, List<String> fallback) {
		return list.isEmpty() ? fallback : List.copyOf(list);
	}

	private static void addAll(JsonObject json, String key, List<String> into) {
		if (json.has(key)) {
			JsonArray array = json.getAsJsonArray(key);
			array.forEach(element -> into.add(element.getAsString()));
		}
	}

	/** A fresh username not used by anyone the server knows of right now. */
	/** A name from the lists without asking which are taken (off the server thread: an addon's history of the world). */
	public static String loose(RandomSource random) {
		for (int i = 0; i < MAX_TRIES; i++) {
			String name = candidate(random, lists);
			if (allowed(name)) {
				return name;
			}
		}

		return pick(random, lists.words()) + (1000 + random.nextInt(9000));
	}

	public static String generate(RandomSource random, MinecraftServer server) {
		Set<String> taken = taken(server);
		for (int i = 0; i < MAX_TRIES; i++) {
			String name = candidate(random, lists);
			if (allowed(name) && !taken.contains(name.toLowerCase(Locale.ROOT))) {
				return name;
			}
		}

		// Practically never: a plain word with enough digits to be unique.
		return pick(random, lists.words()) + (1000 + random.nextInt(9000));
	}

	/** Valid characters and length, not a banned name, nothing offensive inside. */
	public static boolean allowed(String name) {
		if (!VALID.matcher(name).matches()) {
			return false;
		}

		String lower = name.toLowerCase(Locale.ROOT);
		String squashed = lower.replace("_", "");
		if (lists.banned().contains(lower) || lists.banned().contains(squashed)) {
			return false;
		}

		for (String part : lists.bannedParts()) {
			if (lower.contains(part) || squashed.contains(part)) {
				return false;
			}
		}

		return true;
	}

	/** Someone the server knows of right now already goes by this name. */
	public static boolean inUse(MinecraftServer server, String name) {
		return taken(server).contains(name.toLowerCase(Locale.ROOT));
	}

	/** Names in use: players online and every loaded Drifter. */
	private static Set<String> taken(MinecraftServer server) {
		Set<String> taken = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			taken.add(player.getGameProfile().getName().toLowerCase(Locale.ROOT));
		}

		for (ServerLevel level : server.getAllLevels()) {
			for (DrifterEntity drifter : level.getEntities(ModEntities.DRIFTER, DrifterEntity::hasCustomName)) {
				taken.add(drifter.getName().getString().toLowerCase(Locale.ROOT));
			}
		}

		return taken;
	}

	private static String candidate(RandomSource random, Lists l) {
		// Now and then a prank-call name (the user, 2026-11-12: "cheeky ones like vera bagina, keep them rare"): as it is, run
		// together, lowercase, or with digits.
		if (random.nextInt(JOKE_ONE_IN) == 0) {
			String joke = pick(random, l.jokes());
			return switch (random.nextInt(5)) {
				case 0 -> joke.replace("_", "");
				case 1 -> joke.toLowerCase(Locale.ROOT);
				case 2 -> joke + "_" + (1 + random.nextInt(9));
				default -> joke;
			};
		}

		// (0 to 99 the shapes as before; 100 to 159 the ones taken from real players' names, 0.23.47.)
		int roll = random.nextInt(160);
		String name;
		if (roll >= 100) {
			name = real(random, l, roll - 100);
		} else if (roll < 22) {
			// pinewick42, moss_77, Ember2009
			name = pick(random, l.words()) + (random.nextInt(3) == 0 ? pick(random, l.words()) : "") + (random.nextInt(4) == 0 ? "_" : "") + digits(random);
		} else if (roll < 42) {
			// frostotter, FrostOtter, sleepyfox
			String first = random.nextBoolean() ? pick(random, l.adjectives()) : pick(random, l.words());
			name = random.nextInt(3) == 0 ? capital(first) + capital(pick(random, l.words())) : first + pick(random, l.words());
		} else if (roll < 53) {
			// sleepy_fox, lost_cobble_3
			name = pick(random, l.adjectives()) + "_" + pick(random, l.words()) + (random.nextInt(4) == 0 ? "_" + random.nextInt(10) : "");
		} else if (roll < 67) {
			// leo2011, mia_09, Kai_Otter
			String first = pick(random, l.firstNames());
			name = switch (random.nextInt(4)) {
				case 0 -> first + (2000 + random.nextInt(16));
				case 1 -> first + "_" + String.format(Locale.ROOT, "%02d", random.nextInt(100));
				case 2 -> capital(first) + "_" + capital(pick(random, l.words()));
				default -> first + pick(random, l.words());
			};
		} else if (roll >= 76) {
			name = more(random, l, roll);
		} else {
			// The occasional styled one: xXFrostXx, itzmossy, NoodleMC, TheOtter.
			String word = random.nextBoolean() ? pick(random, l.words()) : pick(random, l.adjectives()) + pick(random, l.words());
			name = switch (random.nextInt(4)) {
				case 0 -> "xX" + capital(word) + "Xx";
				case 1 -> pick(random, l.prefixes()) + capital(word);
				case 2 -> capital(word) + pick(random, l.suffixes()).toUpperCase(Locale.ROOT);
				default -> pick(random, l.prefixes()).toLowerCase(Locale.ROOT) + word + (random.nextBoolean() ? "" : digits(random));
			};
		}

		// Most players type their name in lowercase; a few use capitals all the way.
		if (random.nextInt(25) == 0) {
			name = name.toUpperCase(Locale.ROOT);
		}

		return name;
	}

	/**
	 * More of the shapes real players' names come in (the user, 2026-10-31: "expand this"): letters for numbers (c0bble),
	 * a letter held (slowww), a streamer's tag (FoxTTV), a title (DrSloth), a short sentence (ImNotAFox), initials
	 * (jk_ember), a brand new player's (Player482, Steve_12345) and underscores round it (moss_, __pine__).
	 */
	private static String more(RandomSource random, Lists l, int roll) {
		String word = pick(random, l.words());
		return switch (roll) {
			case 76, 77, 78 -> leet(random, word) + (random.nextBoolean() ? digits(random) : "");
			case 79, 80 -> stretch(random, random.nextBoolean() ? word : pick(random, l.adjectives()));
			case 81, 82, 83 -> capital(word) + pick(random, List.of("TTV", "_TTV", "_yt", "YT", "Live", "_tv", "Plays"));
			case 84, 85, 86, 87 -> pick(random, l.titles()) + (random.nextInt(3) == 0 ? "_" : "") + capital(word);
			case 88, 89, 90, 91 -> String.format(Locale.ROOT, pick(random, l.sentences()), capital(word));
			case 92, 93 -> (char)('a' + random.nextInt(26)) + "" + (char)('a' + random.nextInt(26)) + "_" + word;
			case 94, 95 -> switch (random.nextInt(3)) {
				case 0 -> "Player" + (100 + random.nextInt(900));
				case 1 -> pick(random, List.of("Steve", "Alex", "steve", "alex")) + (random.nextBoolean() ? "_" : "") + (1000 + random.nextInt(99000));
				default -> capital(pick(random, l.firstNames())) + (10000 + random.nextInt(90000));
			};
			default -> switch (random.nextInt(3)) {
				case 0 -> word + "_";
				case 1 -> "__" + word + "__";
				default -> "_" + word + "_" + (random.nextBoolean() ? digits(random) : "");
			};
		};
	}

	/**
	 * Shapes taken from real players' names (the user's /drifter list with the experimental real usernames, 2026-11-12):
	 * short made-up handles (bhub0, qvrxz), a letter held at the end (Blispyy, Kayoticc), little phrases (the_real_yosh,
	 * YourLocalCanada_, NoBurrowNoLife, SimplyCherry), channel tags (PerfectPlayz, CraneGamings, Rohandaily), emoticons
	 * (LivynO_o, PIZZATIMEQWQ), a name from round the world (CuongThiVN, xianjiaojiao, 0Tuukka0), digits at both ends
	 * (4Savage4), mobs (WARDENROBOT_886), a name with an initial and digits (jamesj74), a full year (shrenik2004), initials in
	 * underscores (_SRA_), and now and then just a number.
	 */
	private static String real(RandomSource random, Lists l, int roll) {
		String word = pick(random, l.words());
		return switch (roll / 4) {
			case 0 -> handle(random);
			case 1 -> held(random, random.nextBoolean() ? word : pick(random, l.firstNames()));
			case 2 -> {
				String filler = random.nextInt(3) == 0 ? pick(random, l.firstNames()) : word;
				String form = pick(random, l.phrases());
				// (After a capital or at the start of a capitalised phrase, the word in it capitalised too: YourLocalCanada, Mr_Fox.)
				String phrase = String.format(Locale.ROOT, form, Character.isUpperCase(form.charAt(0)) || form.startsWith("sir") ? capital(filler) : filler);
				yield phrase + (random.nextInt(4) == 0 ? digits(random) : "");
			}
			case 3 -> (random.nextBoolean() ? capital(random.nextBoolean() ? word : pick(random, l.adjectives())) : pick(random, l.firstNames()))
				+ pick(random, l.channelTags());
			case 4 -> (random.nextBoolean() ? capital(pick(random, l.firstNames())) : word) + pick(random, l.emoticons());
			case 5 -> {
				String name = pick(random, l.worldNames());
				yield switch (random.nextInt(5)) {
					case 0 -> capital(name) + capital(pick(random, l.worldNames())) + pick(random, l.countryTags());
					case 1 -> name + pick(random, l.worldNames());
					case 2 -> random.nextInt(10) + capital(name) + random.nextInt(10);
					case 3 -> capital(name) + "_" + pick(random, l.countryTags());
					default -> name + (random.nextBoolean() ? "_" : "") + digits(random);
				};
			}
			case 6 -> {
				int d = random.nextInt(10);
				yield random.nextBoolean() ? d + capital(word) + d : (pick(random, l.mobWords()) + pick(random, l.mobTails())).toUpperCase(Locale.ROOT)
					+ (random.nextBoolean() ? "_" + (100 + random.nextInt(900)) : "");
			}
			case 7 -> pick(random, l.mobWords()) + (random.nextBoolean() ? "_" : "") + pick(random, l.mobTails()) + (random.nextBoolean() ? digits(random) : "");
			case 8 -> {
				String first = pick(random, l.firstNames());
				yield switch (random.nextInt(3)) {
					case 0 -> first + (char)('a' + random.nextInt(26)) + (10 + random.nextInt(90));
					case 1 -> first + (1985 + random.nextInt(30));
					default -> capital(first) + Character.toUpperCase((char)('a' + random.nextInt(26))) + (1 + random.nextInt(99));
				};
			}
			case 9 -> random.nextInt(4) == 0 ? Integer.toString(1000 + random.nextInt(9000))
				: "_" + initials(random, 2 + random.nextInt(2)) + "_";
			// JackThePanda, EnderDanny, QueryDaniel, MiltonBonVoyage
			case 10 -> random.nextBoolean() ? capital(pick(random, l.firstNames())) + "The" + capital(word)
				: capital(word) + capital(pick(random, l.firstNames())) + (random.nextInt(3) == 0 ? digits(random) : "");
			// Boston_Jack, Leedslad86, Steeltown10
			case 11 -> {
				String place = pick(random, l.places());
				yield switch (random.nextInt(3)) {
					case 0 -> capital(place) + "_" + capital(pick(random, l.firstNames()));
					case 1 -> capital(place) + pick(random, l.placeTails()) + (10 + random.nextInt(90));
					default -> capital(place) + (1 + random.nextInt(99));
				};
			}
			// DamaniRD, RobNF, Sko1
			case 12 -> capital(pick(random, l.firstNames())) + initials(random, 2);
			// Lxght_Speed, trypophobia4748, Minecraft__Fox
			case 13 -> switch (random.nextInt(3)) {
				case 0 -> vowelX(random, capital(word)) + "_" + capital(pick(random, l.words()));
				case 1 -> word + (1000 + random.nextInt(9000));
				default -> capital(word) + "__" + capital(pick(random, l.words()));
			};
			// a held letter on a phrase's end too: NoOneImportantt, punknuggettt
			default -> {
				String name = random.nextBoolean() ? pick(random, l.adjectives()) + word : word + pick(random, l.words());
				yield name + String.valueOf(name.charAt(name.length() - 1)).repeat(1 + random.nextInt(2));
			}
		};
	}

	/** One of its vowels as an x (Lxght). */
	private static String vowelX(RandomSource random, String word) {
		java.util.List<Integer> vowels = new java.util.ArrayList<>();
		for (int i = 1; i < word.length(); i++) {
			if ("aeiou".indexOf(word.charAt(i)) >= 0) {
				vowels.add(i);
			}
		}

		if (vowels.isEmpty()) {
			return word;
		}

		int at = vowels.get(random.nextInt(vowels.size()));
		return word.substring(0, at) + "x" + word.substring(at + 1);
	}

	/** A short made-up handle, the way some players pick one (bhub0, dschae, qvrxz, walizy, motix). */
	private static String handle(RandomSource random) {
		String[] starts = {"b", "bh", "d", "dw", "ds", "q", "qv", "bx", "c", "ch", "m", "w", "z", "fq", "k", "sh", "tr", "v", "x", "j", "p", "n", "r", "s", "t", "g"};
		String[] middles = {"a", "e", "i", "o", "u", "y", "ae", "ia", "io", "oo", "ee", "", "r", "x", "f"};
		String[] ends = {"b", "z", "x", "m", "n", "p", "k", "s", "t", "l", "ix", "y", "ie", "e", "o", "in", "us", "tz", "zy", "rq"};
		StringBuilder out = new StringBuilder(starts[random.nextInt(starts.length)]);
		int parts = 1 + random.nextInt(2);
		for (int i = 0; i < parts; i++) {
			out.append(middles[random.nextInt(middles.length)]).append(ends[random.nextInt(ends.length)]);
		}

		if (random.nextInt(3) == 0) {
			out.append(random.nextInt(10));
		}

		return out.length() < MIN_LENGTH ? out + "z" : out.toString();
	}

	/** A letter held at the end (Blispyy, Kayoticc, Akamehh) or doubled in the middle (Minsseeoo). */
	private static String held(RandomSource random, String word) {
		String name = random.nextBoolean() ? capital(word) : word;
		return switch (random.nextInt(4)) {
			case 0 -> name + "yy";
			case 1 -> name + "hh";
			case 2 -> name + String.valueOf(name.charAt(name.length() - 1)).repeat(1 + random.nextInt(3));
			default -> stretch(random, name) + (random.nextBoolean() ? "" : "o");
		};
	}

	/** Capital letters, in underscores between (SRA, M_M_K_W). */
	private static String initials(RandomSource random, int count) {
		StringBuilder out = new StringBuilder();
		boolean spaced = random.nextInt(3) == 0;
		for (int i = 0; i < count; i++) {
			if (spaced && i > 0) {
				out.append('_');
			}

			out.append((char)('A' + random.nextInt(26)));
		}

		return out.toString();
	}

	/** A letter or two as a number (o 0, e 3, a 4, i 1, s 5). */
	private static String leet(RandomSource random, String word) {
		StringBuilder out = new StringBuilder();
		int swaps = 0;
		for (char c : word.toCharArray()) {
			String digit = switch (c) {
				case 'o' -> "0";
				case 'e' -> "3";
				case 'a' -> "4";
				case 'i' -> "1";
				case 's' -> "5";
				case 'l' -> "1";
				case 't' -> "7";
				default -> null;
			};
			if (digit != null && swaps < 2 && random.nextInt(3) > 0) {
				out.append(digit);
				swaps++;
			} else {
				out.append(c);
			}
		}

		return out.toString();
	}

	/** Its last letter held (slowww), or a vowel doubled (kiiwi). */
	private static String stretch(RandomSource random, String word) {
		if (random.nextBoolean()) {
			char last = word.charAt(word.length() - 1);
			return word + String.valueOf(last).repeat(1 + random.nextInt(3));
		}

		for (int i = 0; i < word.length(); i++) {
			if ("aeiou".indexOf(word.charAt(i)) >= 0) {
				return word.substring(0, i + 1) + word.charAt(i) + word.substring(i + 1);
			}
		}

		return word + word.charAt(word.length() - 1);
	}

	/** The lists as loaded (for nicknames: which parts of a name are words, first names, add-ons). */
	static Lists lists() {
		return lists;
	}

	/** One name in this many is a joke name. */
	private static final int JOKE_ONE_IN = 150;

	private static String digits(RandomSource random) {
		// (Now and then the numbers players think are funny.)
		if (random.nextInt(40) == 0) {
			return random.nextBoolean() ? "69" : "420";
		}

		return switch (random.nextInt(4)) {
			case 0 -> Integer.toString(random.nextInt(10));
			case 1, 2 -> Integer.toString(10 + random.nextInt(90));
			default -> Integer.toString(100 + random.nextInt(900));
		};
	}

	private static String pick(RandomSource random, List<String> list) {
		return list.get(random.nextInt(list.size()));
	}

	private static String capital(String word) {
		return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}

	/** Banned name parts that are only swearing or game talk in chat (a Drifter doesn't mind those). */
	private static final Set<String> MILD = Set.of("fuck", "shit", "bitch", "dick", "cock", "kill_", "murder", "suicide", "isis", "twat", "wank", "boob",
		"tits", "cum", "anal", "sex", "coon", "nude");
	/** Short ones that are parts of ordinary words ("grape"): only as a word of their own. */
	private static final Set<String> WHOLE = Set.of("rape", "fag", "kkk");

	/** This chat message has a slur or worse in it (a Drifter won't answer it, nor say it back). */
	public static boolean rude(String text) {
		String lower = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ") + " ";
		for (String part : lists.bannedParts()) {
			if (!MILD.contains(part) && (WHOLE.contains(part) ? lower.contains(" " + part + " ") : lower.contains(part))) {
				return true;
			}
		}

		return false;
	}
}
