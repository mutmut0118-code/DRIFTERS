package com.dinglebob.drifter.building.plan.gen;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.building.plan.HousePlan;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Trait;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;

/**
 * The world's generated house plans (milestone 15): as many as the setting says (default 300), made from the house styles
 * once per world (seeded by the world seed, so a world keeps its plans), each checked by PlanCheck. And which of them a
 * Drifter considers: a handful that suit it (a creative or industrious one leans to bigger houses, a thrifty one to
 * smaller), so choosing a house stays quick with hundreds of plans.
 */
public final class GeneratedPlans {
	private GeneratedPlans() {
	}

	public static final String DIRECTORY = "drifter_house_styles";
	/** The first generator's styles (before 0.22.7), kept so a house begun from one of its plans keeps its plan. */
	public static final String LEGACY_DIRECTORY = "drifter_house_styles_legacy";
	public static final String PREFIX = "house:";
	/** The first generator's plans ("gen:cottage_12"): made only when one is asked for by name. */
	public static final String LEGACY_PREFIX = "gen:";
	/** Generated plans a Drifter weighs when it picks a house (besides every hand-made one). */
	private static final int SHORTLIST = 6;
	private static final int TRIES = 8;

	private static volatile Map<String, HouseStyle> styles = Map.of();
	private static volatile Map<String, HouseStyle> legacyStyles = Map.of();
	private static Map<String, HousePlan> legacyPlans = Map.of();
	private static long legacyFor = Long.MIN_VALUE;
	private static int legacyCount = -1;
	private static int legacyVersion = -1;
	private static volatile int stylesVersion;
	private static Map<String, HousePlan> plans = Map.of();
	/** What each generated plan has (its roof, a wing, a porch...), for the showcase (debug). */
	private static Map<String, List<String>> features = Map.of();
	private static List<HousePlan> bySize = List.of();
	private static long madeFor = Long.MIN_VALUE;
	private static int madeCount = -1;
	private static int madeVersion = -1;

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return Drifter.id("house_styles");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				load(manager);
			}
		});
	}

	private static void load(ResourceManager manager) {
		styles = read(manager, DIRECTORY);
		legacyStyles = read(manager, LEGACY_DIRECTORY);
		stylesVersion++;
		Drifter.LOGGER.info("Loaded {} Drifter house styles ({} old ones)", styles.size(), legacyStyles.size());
	}

	private static Map<String, HouseStyle> read(ResourceManager manager, String directory) {
		// Sorted by name: the order styles are drawn in must be the same every time the game starts, or a world's plans (and a
		// Drifter's half-built house) would change between sessions.
		Map<String, HouseStyle> loaded = new java.util.TreeMap<>();
		for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(directory, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = file.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				String path = file.getKey().getPath();
				String id = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
				loaded.put(id, HouseStyle.parse(id, json));
			} catch (Exception e) {
				Drifter.LOGGER.error("Couldn't read Drifter house style {}", file.getKey(), e);
			}
		}

		return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
	}

	/** A plan the first generator made for this world (a house begun before 0.22.7), or null. */
	@org.jetbrains.annotations.Nullable
	public static synchronized HousePlan legacy(MinecraftServer server, String id) {
		long seed = server.getWorldData().worldGenOptions().seed();
		int count = DrifterConfig.spawn(DrifterConfig.Spawn.HOUSE_DESIGNS);
		if (seed != legacyFor || count != legacyCount || stylesVersion != legacyVersion) {
			legacyPlans = make(seed, count, legacyStyles, LEGACY_PREFIX, true).plans();
			legacyFor = seed;
			legacyCount = count;
			legacyVersion = stylesVersion;
		}

		return legacyPlans.get(id);
	}

	private record Made(Map<String, HousePlan> plans, Map<String, List<String>> features) {
	}

	/** {@code count} plans from these styles, seeded by the world (the same every time). */
	private static Made make(long seed, int count, Map<String, HouseStyle> from, String prefix, boolean legacy) {
		long started = System.nanoTime();
		Map<String, HousePlan> made = new LinkedHashMap<>();
		Map<String, Integer> rejected = new LinkedHashMap<>();
		Map<String, List<String>> madeFeatures = new LinkedHashMap<>();
		Map<String, Integer> tally = new java.util.TreeMap<>();
		List<HouseStyle> all = new ArrayList<>(from.values());
		double total = all.stream().mapToDouble(HouseStyle::weight).sum();
		for (int i = 0; i < count && !all.isEmpty(); i++) {
			RandomSource random = randomFor(seed, i);
			HouseStyle style = all.get(all.size() - 1);
			double at = random.nextDouble() * total;
			for (HouseStyle candidate : all) {
				at -= candidate.weight();
				if (at <= 0.0) {
					style = candidate;
					break;
				}
			}

			String id = prefix + style.id() + "_" + i;
			HousePlan plan = draw(id, style, random, legacy, (draft, problem) -> {
				String kind = problem.replaceAll(" [(].*", "").replaceAll("[0-9]+ ", "");
				if (rejected.merge(kind, 1, Integer::sum) <= 3) {
					Drifter.LOGGER.info("[Drifter] house plan draft {} thrown away: {}", draft, problem);
				}
			});
			if (plan != null) {
				List<String> planFeatures = legacy ? PlanGenerator.lastFeatures : HouseGenerator.lastFeatures;
				made.put(id, plan);
				madeFeatures.put(id, planFeatures);
				planFeatures.forEach(feature -> tally.merge(feature, 1, Integer::sum));
			}
		}

		Drifter.LOGGER.info("[Drifter] made {} {}house plans from {} styles in {} ms (drafts thrown away: {}; {})", made.size(), legacy ? "old " : "",
			all.size(), (System.nanoTime() - started) / 1_000_000, rejected, tally);
		return new Made(java.util.Collections.unmodifiableMap(made), madeFeatures);
	}

	/** The random numbers plan {@code number} of a world is drawn from (its style is the first of them). */
	private static RandomSource randomFor(long seed, int number) {
		return RandomSource.create(seed * 31L + number * 0x9E3779B97F4A7C15L);
	}

	/** The first sound draft of this plan (up to {@link #TRIES}), or null; {@code thrownAway} hears of each that wasn't. */
	@org.jetbrains.annotations.Nullable
	private static HousePlan draw(String id, HouseStyle style, RandomSource random, boolean legacy, java.util.function.BiConsumer<String, String> thrownAway) {
		for (int attempt = 0; attempt < TRIES; attempt++) {
			HousePlan plan = legacy ? PlanGenerator.generate(id, style, random) : HouseGenerator.generate(id, style, random);
			String problem = plan == null ? "no plan" : PlanCheck.problem(plan);
			if (problem == null) {
				return plan;
			}

			thrownAway.accept(id, problem);
		}

		return null;
	}

	private static final Map<String, HousePlan> remade = new java.util.HashMap<>();
	private static long remadeFor = Long.MIN_VALUE;
	private static int remadeVersion = -1;

	/**
	 * A plan of this world by its id ("house:cottage_12") that this world's plans don't have (new styles came in since the
	 * house was begun, and the draw for that number fell on another style now; the user's worlds have houses begun from the
	 * first twelve): made again as it was, from the same random numbers, so a half-built house or a town's house keeps its
	 * plan. Null if the style is gone or the draw doesn't make a sound house.
	 */
	@org.jetbrains.annotations.Nullable
	public static synchronized HousePlan again(MinecraftServer server, String id) {
		int cut = id.lastIndexOf('_');
		if (!id.startsWith(PREFIX) || cut < 0) {
			return null;
		}

		HouseStyle style = styles.get(id.substring(PREFIX.length(), cut));
		int number;
		try {
			number = Integer.parseInt(id.substring(cut + 1));
		} catch (NumberFormatException e) {
			return null;
		}

		long seed = server.getWorldData().worldGenOptions().seed();
		if (seed != remadeFor || stylesVersion != remadeVersion) {
			remade.clear();
			remadeFor = seed;
			remadeVersion = stylesVersion;
		}

		if (style == null || number < 0) {
			return null;
		}

		if (!remade.containsKey(id)) {
			RandomSource random = randomFor(seed, number);
			// (The draw that picked its style.)
			random.nextDouble();
			remade.put(id, draw(id, style, random, false, (draft, problem) -> {
			}));
		}

		return remade.get(id);
	}

	/** The house styles, by id (debug: the parts exhibit). */
	public static Map<String, HouseStyle> styles() {
		return styles;
	}

	/** This world's generated plans, by id (made the first time they're asked for, and again if the setting changes). */
	public static synchronized Map<String, HousePlan> all(MinecraftServer server) {
		long seed = server.getWorldData().worldGenOptions().seed();
		int count = DrifterConfig.spawn(DrifterConfig.Spawn.HOUSE_DESIGNS);
		if (seed == madeFor && count == madeCount && stylesVersion == madeVersion) {
			return plans;
		}

		Made made = make(seed, count, styles, PREFIX, false);
		plans = made.plans();
		features = made.features();
		List<HousePlan> sorted = new ArrayList<>(made.plans().values());
		sorted.sort(Comparator.comparingInt(HousePlan::blockCount));
		bySize = List.copyOf(sorted);
		madeFor = seed;
		madeCount = count;
		madeVersion = stylesVersion;
		return plans;
	}

	/** What this generated plan has (its roof, a wing, a porch...), empty if it isn't one. */
	public static List<String> features(String id) {
		return features.getOrDefault(id, List.of());
	}

	/**
	 * The generated plans this Drifter weighs: a few round the size it likes (seeded by who it is, so it doesn't change its
	 * mind each time it looks), from smallest (thrifty) to biggest (creative, industrious, or a Builder).
	 */
	public static List<HousePlan> shortlist(MinecraftServer server, DrifterEntity drifter) {
		all(server);
		List<HousePlan> sized = bySize;
		if (sized.isEmpty()) {
			return List.of();
		}

		var personality = drifter.getPersonality();
		double taste = 0.35 + 0.35 * personality.get(Trait.CREATIVITY) + 0.2 * personality.get(Trait.INDUSTRIOUSNESS) - 0.35 * personality.get(Trait.THRIFT)
			+ (personality.archetype() == com.dinglebob.drifter.personality.Archetype.BUILDER ? 0.2 : 0.0);
		taste = Math.max(0.0, Math.min(1.0, taste));
		int middle = (int)Math.round(taste * (sized.size() - 1));
		int spread = Math.max(SHORTLIST * 2, sized.size() / 6);
		RandomSource random = RandomSource.create(drifter.getUUID().getLeastSignificantBits() ^ sized.size());
		List<HousePlan> picks = new ArrayList<>();
		for (int i = 0; i < SHORTLIST * 4 && picks.size() < SHORTLIST; i++) {
			int index = Math.max(0, Math.min(sized.size() - 1, middle - spread / 2 + random.nextInt(spread + 1)));
			HousePlan plan = sized.get(index);
			if (!picks.contains(plan)) {
				picks.add(plan);
			}
		}

		return picks;
	}
}
