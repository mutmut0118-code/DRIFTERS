package com.dinglebob.drifter.entity;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.ProfileNotFoundException;
import com.mojang.authlib.yggdrasil.ProfileResult;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.DrifterConfig;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;

/**
 * Real player skins (user, 2026-10-20: "its just like minecraft multiplayer"): a Drifter wears a real account's skin.
 * Where from, in order:
 * <ol>
 *   <li>the user's own list (config/drifter/skins.txt: names or UUIDs, a friend's, a favourite player's), each one
 *   on one Drifter at a time;</li>
 *   <li>the account with its own name, if there is one (as a real player would look);</li>
 *   <li>a real player picked at random from the bundled list (20,000 UUIDs from the user's list of public accounts;
 *   datapacks can add more under drifter_skins/).</li>
 * </ol>
 * The server asks Mojang (the same lookups a server makes for player heads) and the answer is saved with the Drifter.
 * Every player's game then downloads and caches the picture itself, as for a player it meets.
 *
 * <p>Fast (the user, 2026-10-20: "figure out how to load skins faster"; before, 30 new Drifters took a minute, one lookup a
 * second): every answer is kept on disk (config/drifter/cache/skins.json) for every world and later runs; a pool of
 * ready skins from the bundled list is kept topped up in the background, so a Drifter whose own name has no account
 * gets one at once; and new Drifters' names are asked about together, ten to a request. No internet: what's on disk
 * still serves; the rest wait and try again later.
 */
public final class RealSkins {
	/** Old-style lookups (a command's, or the pool ran dry) running at once, and one started at most this often (ticks). */
	private static final int MAX_IN_FLIGHT = 3;
	private static final int START_EVERY = 10;
	/** Names asked about together (Mojang takes ten a request), and a batch at most this often (ticks). */
	private static final int BATCH = 20;
	private static final int BATCH_EVERY = 10;
	/** Ready skins kept in the pool, and how many bundled accounts are asked about at a time to top it up (every second). */
	private static final int POOL_TARGET = 40;
	private static final int POOL_FILL = 4;
	private static final int POOL_EVERY = 20;
	/** Mojang said no (busy, or no internet): nothing new is asked for this long (ticks). */
	private static final int BACK_OFF = 20 * 30;
	/** How long a cached answer holds: a skin a month, "no such account" a week (it may be taken by then). */
	private static final long KEEP_SKIN_MILLIS = 30L * 24 * 3600 * 1000;
	private static final long KEEP_NONE_MILLIS = 7L * 24 * 3600 * 1000;
	private static final int CACHE_MOST = 3000;
	/** Entries from the user's list tried for one Drifter. */
	private static final int FROM_USER_LIST = 2;
	/** Accounts from the bundled list tried for one Drifter when the pool is empty (some have no skin, or are gone). */
	private static final int FROM_BUNDLED = 4;
	/** After a failed lookup (no internet, Mojang busy): this long before it tries again (ticks). */
	public static final long RETRY_AFTER = 20L * 60 * 10;
	/** What Mojang allows in a name. */
	private static final Pattern ACCOUNT_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	/** A UUID, with or without its dashes. */
	private static final Pattern ACCOUNT_ID = Pattern.compile("[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}");
	public static final String DIRECTORY = "drifter_skins";
	private static final String USER_FILE = "skins.txt";

	private static final Deque<Request> QUEUE = new ArrayDeque<>();
	private static final Set<UUID> QUEUED = new HashSet<>();
	private static int inFlight;
	private static long lastStart;
	/** Every answer (a skin, or none) by name and by UUID, with when it came: kept on disk between runs. */
	private static final Map<String, Found> KNOWN = new LinkedHashMap<>(256, 0.75F, true);
	private static final Map<String, Long> KNOWN_AT = new java.util.HashMap<>();
	private static boolean cacheDirty;
	/** Ready skins from the bundled list, nobody wearing them yet. */
	private static final Deque<Found> READY = new ArrayDeque<>();
	/** Drifters waiting on an answer about a name (theirs, or one on the user's list): each with its accounts in order. */
	private static final Map<UUID, List<String>> WAITING = new LinkedHashMap<>();
	/** Names (or UUIDs) to ask about in the next batch. */
	private static final Set<String> TO_CHECK = new java.util.LinkedHashSet<>();
	/** Being asked about right now (in a batch, or its skin on the way): not asked about again meanwhile. */
	private static final Set<String> ASKING = new HashSet<>();
	/** Asked about this run and no answer came (offline, busy): passed over until the next run. */
	private static final Set<String> UNANSWERED = new HashSet<>();
	/** Name batches out now (two at once at most). */
	private static int batchesOut;
	private static final int BATCHES_AT_ONCE = 2;
	private static long lastBatch;
	private static long quietUntil;
	/** The bundled list (datapacks) and the user's list (config). */
	private static List<String> bundled = List.of();
	private static Set<String> bundledSet = Set.of();
	private static List<String> userList = List.of();
	/** Accounts a Drifter wears now (lowercased names and dashless UUIDs): the user's list gives each to one at a time. */
	private static final Set<String> WORN = new HashSet<>();
	/** Accounts from the user's list being looked up for someone right now (not to be given to another meanwhile). */
	private static final Set<String> RESERVED = new HashSet<>();

	/** A skin found: the account's name and UUID, its textures (Mojang's base64 text), and whether it has slim arms. */
	public record Found(String name, String id, String textures, boolean slim) {
		static final Found NONE = new Found("", "", "", false);
	}

	private record Request(UUID drifter, List<String> accounts, boolean forced) {
	}

	/** How a lookup went: a skin, no account with one among those asked about, or no answer (no internet, Mojang busy). */
	private record Result(@Nullable Found found, boolean noAnswer) {
	}

	private RealSkins() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RealSkins::tick);
		ServerLifecycleEvents.SERVER_STARTING.register(server -> loadCache());
		ServerLifecycleEvents.SERVER_STARTED.register(server -> loadUserList());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> saveCache(false));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			QUEUE.clear();
			QUEUED.clear();
			WORN.clear();
			RESERVED.clear();
			WAITING.clear();
			TO_CHECK.clear();
			UNANSWERED.clear();
			ASKING.clear();
			inFlight = 0;
			batchesOut = 0;
			fillsOut = 0;
		});
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return Drifter.id("skins");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				loadBundled(manager);
				loadUserList();
			}
		});
	}

	// ----- The lists -----

	private static void loadBundled(ResourceManager manager) {
		List<String> all = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".txt")).entrySet()) {
			try (BufferedReader reader = file.getValue().openAsReader()) {
				reader.lines().map(RealSkins::entry).filter(entry -> entry != null).forEach(all::add);
			} catch (IOException | RuntimeException e) {
				Drifter.LOGGER.error("Couldn't read Drifter skins file {}", file.getKey(), e);
			}
		}

		bundled = List.copyOf(all);
		bundledSet = Set.copyOf(all.stream().map(RealSkins::key).toList());
		rebuildPool();
		Drifter.LOGGER.info("Loaded {} accounts for Drifter skins", bundled.size());
	}

	public static Path userFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("drifter").resolve(USER_FILE);
	}

	/** config/drifter/skins.txt: made with a note on first run; names or UUIDs, one a line. */
	private static void loadUserList() {
		Path file = userFile();
		try {
			if (!Files.exists(file)) {
				Files.createDirectories(file.getParent());
				Files.writeString(file, String.join("\n",
					"# Skins for Drifters: Minecraft names or UUIDs, one a line (a friend's, a favourite player's).",
					"# New Drifters wear these first, each one on one Drifter at a time; then the rest get real skins as usual.",
					"# Lines starting with # are notes. Changes count from the next world load (or /reload).",
					""), StandardCharsets.UTF_8);
			}

			userList = Files.readAllLines(file, StandardCharsets.UTF_8).stream().map(RealSkins::entry).filter(entry -> entry != null).toList();
			if (!userList.isEmpty()) {
				Drifter.LOGGER.info("{} skins on the user's list for Drifters", userList.size());
			}
		} catch (IOException e) {
			Drifter.LOGGER.warn("Couldn't read {}", file, e);
			userList = List.of();
		}
	}

	/** One line of a list: a name or a dashless lowercase UUID, or null (a note, a blank, anything else). */
	@Nullable
	private static String entry(String line) {
		String text = line.strip();
		if (text.isEmpty() || text.startsWith("#")) {
			return null;
		}

		if (ACCOUNT_ID.matcher(text).matches()) {
			return text.replace("-", "").toLowerCase(Locale.ROOT);
		}

		return ACCOUNT_NAME.matcher(text).matches() ? text : null;
	}

	private static boolean isId(String account) {
		return account.length() == 32 && ACCOUNT_ID.matcher(account).matches();
	}

	private static String key(String account) {
		return account.toLowerCase(Locale.ROOT);
	}

	/** A name or a UUID (with or without dashes) it can look up. */
	public static boolean valid(String account) {
		return entry(account) != null;
	}

	// ----- The cache on disk -----

	private static Path cacheFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("drifter").resolve("cache").resolve("skins.json");
	}

	/** What's known about an account (a skin, or NONE), or null: never asked, or the answer's too old. */
	@Nullable
	private static Found cached(String account) {
		String key = key(account);
		synchronized (KNOWN) {
			Found found = KNOWN.get(key);
			Long at = KNOWN_AT.get(key);
			if (found == null || at == null) {
				return null;
			}

			long age = System.currentTimeMillis() - at;
			return age > (found == Found.NONE ? KEEP_NONE_MILLIS : KEEP_SKIN_MILLIS) ? null : found;
		}
	}

	private static void remember(String account, Found found) {
		synchronized (KNOWN) {
			KNOWN.put(key(account), found);
			KNOWN_AT.put(key(account), System.currentTimeMillis());
			cacheDirty = true;
		}
	}

	private static void loadCache() {
		Path file = cacheFile();
		synchronized (KNOWN) {
			KNOWN.clear();
			KNOWN_AT.clear();
			if (Files.exists(file)) {
				try {
					JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
					for (Map.Entry<String, com.google.gson.JsonElement> e : json.getAsJsonObject("skins").entrySet()) {
						JsonObject one = e.getValue().getAsJsonObject();
						KNOWN.put(e.getKey(), new Found(one.get("name").getAsString(), one.get("id").getAsString(), one.get("textures").getAsString(),
							one.get("slim").getAsBoolean()));
						KNOWN_AT.put(e.getKey(), one.get("at").getAsLong());
					}

					for (Map.Entry<String, com.google.gson.JsonElement> e : json.getAsJsonObject("none").entrySet()) {
						KNOWN.put(e.getKey(), Found.NONE);
						KNOWN_AT.put(e.getKey(), e.getValue().getAsLong());
					}
				} catch (IOException | RuntimeException e) {
					Drifter.LOGGER.warn("Couldn't read the Drifter skin cache {} (starting afresh)", file, e);
					KNOWN.clear();
					KNOWN_AT.clear();
				}
			}
		}

		rebuildPool();
		Drifter.LOGGER.info("Drifter skin cache: {} accounts known, {} skins ready", KNOWN.size(), READY.size());
	}

	/** Written off the server thread (a periodic save) or on it (stopping). */
	private static void saveCache(boolean async) {
		JsonObject json = new JsonObject();
		json.addProperty("version", 1);
		JsonObject skins = new JsonObject();
		JsonObject none = new JsonObject();
		synchronized (KNOWN) {
			if (!cacheDirty) {
				return;
			}

			cacheDirty = false;
			// The newest few thousand.
			List<Map.Entry<String, Long>> newest = new ArrayList<>(KNOWN_AT.entrySet());
			newest.sort(Map.Entry.<String, Long>comparingByValue().reversed());
			for (Map.Entry<String, Long> e : newest.subList(0, Math.min(CACHE_MOST, newest.size()))) {
				Found found = KNOWN.get(e.getKey());
				if (found == null) {
					continue;
				}

				if (found == Found.NONE) {
					none.addProperty(e.getKey(), e.getValue());
				} else {
					JsonObject one = new JsonObject();
					one.addProperty("name", found.name());
					one.addProperty("id", found.id());
					one.addProperty("textures", found.textures());
					one.addProperty("slim", found.slim());
					one.addProperty("at", e.getValue());
					skins.add(e.getKey(), one);
				}
			}
		}

		json.add("skins", skins);
		json.add("none", none);
		Runnable write = () -> {
			Path file = cacheFile();
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				Drifter.LOGGER.warn("Couldn't save the Drifter skin cache {}", file, e);
			}
		};
		if (async) {
			Util.ioPool().execute(write);
		} else {
			write.run();
		}
	}

	/** The pool from the cache: every skin from the bundled list that's known (and not too old), shuffled. */
	private static void rebuildPool() {
		List<Found> ready = new ArrayList<>();
		synchronized (KNOWN) {
			for (Map.Entry<String, Found> e : KNOWN.entrySet()) {
				if (e.getValue() != Found.NONE && bundledSet.contains(e.getKey())) {
					ready.add(e.getValue());
				}
			}
		}

		Collections.shuffle(ready);
		READY.clear();
		READY.addAll(ready);
	}

	/** A ready skin nobody wears, out of the pool; or null. */
	@Nullable
	private static Found takeReady() {
		while (!READY.isEmpty()) {
			Found found = READY.pollFirst();
			if (!WORN.contains(key(found.id())) && !WORN.contains(key(found.name()))) {
				return found;
			}
		}

		return null;
	}

	// ----- Asking -----

	/** It would like a real skin (it hasn't one, and hasn't been told there's none for it). */
	public static void want(DrifterEntity drifter) {
		if (!DrifterConfig.realSkins() || QUEUED.contains(drifter.getUUID()) || WAITING.containsKey(drifter.getUUID())
			|| !(drifter.level() instanceof ServerLevel level)) {
			return;
		}

		RandomSource random = RandomSource.create(drifter.getUUID().getLeastSignificantBits());
		List<String> accounts = new ArrayList<>();
		// The user's list first: the ones nobody wears now.
		List<String> free = new ArrayList<>(userList.stream().filter(account -> !WORN.contains(key(account))).toList());
		Collections.shuffle(free, new java.util.Random(random.nextLong()));
		accounts.addAll(free.subList(0, Math.min(FROM_USER_LIST, free.size())));
		String own = drifter.getName().getString();
		if (ACCOUNT_NAME.matcher(own).matches()) {
			accounts.add(own);
		}

		WAITING.put(drifter.getUUID(), accounts);
		resolve(level.getServer(), drifter.getUUID());
	}

	/**
	 * As far as what's known goes: the first of its accounts with a skin (the user's list, then its own name), else one
	 * from the pool. An account nobody has asked about yet: it waits for the next batch.
	 */
	private static void resolve(MinecraftServer server, UUID id) {
		List<String> accounts = WAITING.get(id);
		DrifterEntity drifter = accounts == null ? null : find(server, id);
		if (drifter == null) {
			WAITING.remove(id);
			return;
		}

		for (String account : accounts) {
			if (userList.contains(account) && (WORN.contains(key(account)) || RESERVED.contains(key(account))) || UNANSWERED.contains(key(account))) {
				continue;
			}

			Found known = cached(account);
			if (known == null) {
				if (!ASKING.contains(key(account))) {
					TO_CHECK.add(account);
				}

				return;
			}

			if (known != Found.NONE) {
				WAITING.remove(id);
				apply(drifter, known);
				return;
			}
		}

		// Nobody of its own: a real player's from the pool (or, the pool dry, asked about the old way).
		WAITING.remove(id);
		Found pooled = takeReady();
		if (pooled != null) {
			apply(drifter, pooled);
			return;
		}

		RandomSource random = RandomSource.create(id.getMostSignificantBits());
		List<String> picks = new ArrayList<>();
		for (int i = 0; i < FROM_BUNDLED && !bundled.isEmpty(); i++) {
			picks.add(bundled.get(random.nextInt(bundled.size())));
		}

		if (!picks.isEmpty()) {
			QUEUED.add(id);
			QUEUE.addLast(new Request(id, picks, false));
		} else {
			drifter.noRealSkin();
		}
	}

	/** This account's skin on it (a command; a name or a UUID): first in line. */
	public static void wear(DrifterEntity drifter, String account) {
		String entry = entry(account);
		if (entry == null) {
			return;
		}

		WAITING.remove(drifter.getUUID());
		QUEUED.add(drifter.getUUID());
		QUEUE.removeIf(request -> request.drifter().equals(drifter.getUUID()));
		QUEUE.addFirst(new Request(drifter.getUUID(), List.of(entry), true));
	}

	/** A Drifter wearing this skin (loaded from its save, or just found): that account is taken. */
	public static void worn(String textures) {
		JsonObject json = payload(textures);
		if (json != null) {
			if (json.has("profileName")) {
				WORN.add(key(json.get("profileName").getAsString()));
			}

			if (json.has("profileId")) {
				WORN.add(key(json.get("profileId").getAsString().replace("-", "")));
			}
		}
	}

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (now % (20 * 60 * 5) == 0) {
			saveCache(true);
		}

		boolean quiet = now < quietUntil;
		if (!quiet && batchesOut < BATCHES_AT_ONCE && !TO_CHECK.isEmpty() && now - lastBatch >= BATCH_EVERY) {
			startBatch(server);
		}

		if (!quiet && fillsOut < POOL_FILL && READY.size() < POOL_TARGET && !bundled.isEmpty() && now % POOL_EVERY == 0 && DrifterConfig.realSkins()) {
			startFill(server);
		}

		if (quiet || QUEUE.isEmpty() || inFlight >= MAX_IN_FLIGHT || now - lastStart < START_EVERY) {
			return;
		}

		Request queued = QUEUE.pollFirst();
		lastStart = now;
		// The user's list: the ones someone has (or is being looked up for) since this Drifter queued are out.
		List<String> accounts = queued.forced() ? queued.accounts() : queued.accounts().stream()
			.filter(account -> !userList.contains(account) || !WORN.contains(key(account)) && !RESERVED.contains(key(account))).toList();
		Request request = new Request(queued.drifter(), accounts, queued.forced());
		for (String account : accounts) {
			if (userList.contains(account)) {
				RESERVED.add(key(account));
			}
		}

		// Known already: no need to ask Mojang again.
		for (String account : request.accounts()) {
			Found known = cached(account);
			if (known != null && known != Found.NONE) {
				finish(server, request, new Result(known, false));
				return;
			}
		}

		inFlight++;
		CompletableFuture.supplyAsync(() -> lookUp(server, request.accounts()), Util.backgroundExecutor())
			.whenComplete((result, error) -> server.execute(() -> {
				inFlight--;
				if (error != null) {
					Drifter.LOGGER.debug("[Drifter] skin lookup failed", error);
				}

				Result outcome = error != null || result == null ? new Result(null, true) : result;
				if (outcome.noAnswer()) {
					quietUntil = server.getTickCount() + BACK_OFF;
				}

				finish(server, request, outcome);
			}));
	}

	/** Skin fetches run at once (Mojang's profile server, a few at a time). */
	private static final java.util.concurrent.ExecutorService FETCHERS = java.util.concurrent.Executors.newFixedThreadPool(4, runnable -> {
		Thread thread = new Thread(runnable, "Drifter skin lookup");
		thread.setDaemon(true);
		return thread;
	});
	/** Skin fetches for the pool still out. */
	private static int fillsOut;
	/**
	 * Skin fetches in a row that got nothing back. One alone is an account that's gone (the bundled list is old); several
	 * in a row is Mojang busy or no internet: then it pauses (found in testing: pausing at every gone account held every
	 * new Drifter up half a minute).
	 */
	private static int failsInARow;
	private static final int FAILS_TO_PAUSE = 4;

	/** A fetch came back empty (true) or with an answer: several empty in a row and it pauses. */
	private static boolean fetchFailed(MinecraftServer server, boolean failed) {
		failsInARow = failed ? failsInARow + 1 : 0;
		if (failsInARow >= FAILS_TO_PAUSE) {
			failsInARow = 0;
			quietUntil = server.getTickCount() + BACK_OFF;
			Drifter.LOGGER.info("[Drifter] skin lookups getting no answer (no internet, or Mojang busy): a pause of {} s", BACK_OFF / 20);
		}

		return failed;
	}

	/**
	 * The names waiting to be asked about, together (ten to a request). As soon as that's back, the ones with no account
	 * go on down their list (a ready skin from the pool, at once); the ones with an account get its skin, a few at a time.
	 */
	private static void startBatch(MinecraftServer server) {
		List<String> batch = new ArrayList<>();
		for (var it = TO_CHECK.iterator(); it.hasNext() && batch.size() < BATCH; ) {
			batch.add(it.next());
			it.remove();
		}

		batchesOut++;
		lastBatch = server.getTickCount();
		batch.forEach(account -> ASKING.add(key(account)));
		batch.stream().filter(account -> userList.contains(account)).forEach(account -> RESERVED.add(key(account)));
		CompletableFuture.supplyAsync(() -> lookUpNames(server, batch), Util.backgroundExecutor())
			.whenComplete((ids, error) -> server.execute(() -> {
				batchesOut--;
				if (error != null || ids == null) {
					batch.forEach(account -> {
						UNANSWERED.add(key(account));
						RESERVED.remove(key(account));
						ASKING.remove(key(account));
					});
					quietUntil = server.getTickCount() + BACK_OFF;
					resolveAll(server);
					return;
				}

				// Names Mojang couldn't answer about (busy, offline): passed over this run.
				for (String account : batch) {
					if (!isId(account) && !ids.containsKey(key(account)) && cached(account) == null) {
						UNANSWERED.add(key(account));
						quietUntil = server.getTickCount() + BACK_OFF;
					}
				}

				// The ones with no account: on at once.
				for (String account : batch) {
					if (!ids.containsKey(key(account)) && !isId(account)) {
						RESERVED.remove(key(account));
						ASKING.remove(key(account));
					}
				}

				resolveAll(server);
				for (String account : batch) {
					UUID id = isId(account) ? fromHex(account) : ids.get(key(account));
					if (id == null) {
						continue;
					}

					CompletableFuture.supplyAsync(() -> fetchSkin(server, account, id), FETCHERS)
						.whenComplete((answered, failed) -> server.execute(() -> {
							RESERVED.remove(key(account));
							ASKING.remove(key(account));
							if (fetchFailed(server, failed != null || answered == null || !answered)) {
								UNANSWERED.add(key(account));
							}

							resolveAll(server);
						}));
				}
			}));
	}

	private static void resolveAll(MinecraftServer server) {
		for (UUID id : List.copyOf(WAITING.keySet())) {
			resolve(server, id);
		}
	}

	/** A few more bundled accounts' skins into the pool, off the server thread, a few at a time. */
	private static void startFill(MinecraftServer server) {
		RandomSource random = RandomSource.create();
		for (int tries = 0; fillsOut < POOL_FILL && tries < POOL_FILL * 4; tries++) {
			String account = bundled.get(random.nextInt(bundled.size()));
			if (cached(account) != null) {
				continue;
			}

			fillsOut++;
			CompletableFuture.supplyAsync(() -> fetchSkin(server, account, fromHex(account)), FETCHERS)
				.whenComplete((answered, error) -> server.execute(() -> {
					fillsOut--;
					if (fetchFailed(server, error != null || answered == null || !answered)) {
						// (Most likely an account that's gone: not asked about again for a week.)
						if (cached(account) == null) {
							remember(account, Found.NONE);
						}

						return;
					}

					Found found = cached(account);
					if (found != null && found != Found.NONE) {
						READY.addLast(found);
					}
				}));
		}
	}

	/** Off the server thread: these names' accounts, in one go (Mojang takes ten a request); names with none are kept as such. */
	private static Map<String, UUID> lookUpNames(MinecraftServer server, List<String> accounts) {
		Map<String, UUID> ids = new LinkedHashMap<>();
		String[] names = accounts.stream().filter(account -> !isId(account)).toArray(String[]::new);
		if (names.length > 0) {
			server.getProfileRepository().findProfilesByNames(names, new ProfileLookupCallback() {
				@Override
				public void onProfileLookupSucceeded(GameProfile profile) {
					ids.put(key(profile.getName()), profile.getId());
				}

				@Override
				public void onProfileLookupFailed(String name, Exception exception) {
					if (exception instanceof ProfileNotFoundException) {
						remember(name, Found.NONE);
					}
				}
			});
		}

		return ids;
	}

	/** Off the server thread: the account's skin (or none) kept; false if no answer came (offline, Mojang busy, no such UUID). */
	private static boolean fetchSkin(MinecraftServer server, String account, UUID id) {
		ProfileResult result = server.getSessionService().fetchProfile(id, false);
		if (result == null) {
			return false;
		}

		Property textures = result.profile().getProperties().get("textures").stream().findFirst().orElse(null);
		if (textures == null || !hasSkin(textures.value())) {
			remember(account, Found.NONE);
			return true;
		}

		remember(account, new Found(result.profile().getName(), id.toString().replace("-", ""), textures.value(), slim(textures.value())));
		return true;
	}

	/** Off the server thread: the names' accounts (one request for all), then each account's skin in order until one has one. */
	private static Result lookUp(MinecraftServer server, List<String> accounts) {
		Map<String, UUID> ids = new LinkedHashMap<>();
		boolean[] noAnswer = {false};
		// Mojang answered at all (with no internet, every UUID looks like it has no account: that's no answer, not "none").
		boolean[] answered = {false};
		String[] names = accounts.stream().filter(account -> !isId(account)).toArray(String[]::new);
		if (names.length > 0) {
			server.getProfileRepository().findProfilesByNames(names, new ProfileLookupCallback() {
				@Override
				public void onProfileLookupSucceeded(GameProfile profile) {
					ids.put(key(profile.getName()), profile.getId());
					answered[0] = true;
				}

				@Override
				public void onProfileLookupFailed(String name, Exception exception) {
					if (exception instanceof ProfileNotFoundException) {
						answered[0] = true;
					} else {
						noAnswer[0] = true;
					}
				}
			});
		}

		for (String account : accounts) {
			UUID id = isId(account) ? fromHex(account) : ids.get(key(account));
			if (id == null) {
				if (!noAnswer[0]) {
					remember(account, Found.NONE);
				}

				continue;
			}

			ProfileResult result = server.getSessionService().fetchProfile(id, false);
			if (result == null) {
				// (A UUID with no account behind it any more, or no answer: either way, the next one.)
				continue;
			}

			answered[0] = true;
			Property textures = result.profile().getProperties().get("textures").stream().findFirst().orElse(null);
			if (textures == null || !hasSkin(textures.value())) {
				remember(account, Found.NONE);
				continue;
			}

			Found found = new Found(result.profile().getName(), id.toString().replace("-", ""), textures.value(), slim(textures.value()));
			remember(account, found);
			return new Result(found, false);
		}

		return new Result(null, noAnswer[0] || !answered[0]);
	}

	private static UUID fromHex(String hex) {
		return new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16), Long.parseUnsignedLong(hex.substring(16), 16));
	}

	/** A skin on it now: the account is taken. */
	private static void apply(DrifterEntity drifter, Found found) {
		WORN.add(key(found.name()));
		WORN.add(key(found.id()));
		drifter.setRealSkin(found);
	}

	private static void finish(MinecraftServer server, Request request, Result result) {
		QUEUED.remove(request.drifter());
		request.accounts().forEach(account -> RESERVED.remove(key(account)));
		DrifterEntity drifter = find(server, request.drifter());
		if (drifter == null) {
			return;
		}

		if (result.found() != null) {
			apply(drifter, result.found());
		} else if (result.noAnswer()) {
			drifter.realSkinRetryAt(drifter.level().getGameTime() + RETRY_AFTER);
		} else if (!request.forced()) {
			drifter.noRealSkin();
		}
	}

	@Nullable
	private static DrifterEntity find(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof DrifterEntity drifter) {
				return drifter;
			}
		}

		return null;
	}

	// ----- Reading Mojang's textures text -----

	@Nullable
	private static JsonObject payload(String textures) {
		try {
			return JsonParser.parseString(new String(Base64.getDecoder().decode(textures), StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (RuntimeException e) {
			return null;
		}
	}

	@Nullable
	private static JsonObject skinOf(String textures) {
		JsonObject json = payload(textures);
		JsonObject all = json == null ? null : json.getAsJsonObject("textures");
		return all == null ? null : all.getAsJsonObject("SKIN");
	}

	private static boolean hasSkin(String textures) {
		JsonObject skin = skinOf(textures);
		return skin != null && skin.has("url");
	}

	/** Slim ("Alex") arms: the skin says so. */
	public static boolean slim(String textures) {
		JsonObject skin = skinOf(textures);
		if (skin == null || !skin.has("metadata")) {
			return false;
		}

		var model = skin.getAsJsonObject("metadata").get("model");
		return model != null && "slim".equals(model.getAsString());
	}
}
