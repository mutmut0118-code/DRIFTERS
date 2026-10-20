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
 *   <li>a few accounts picked at random from the bundled list (20,000 UUIDs from the user's list of public accounts;
 *   datapacks can add more under drifter_skins/).</li>
 * </ol>
 * The server asks Mojang (the same lookups a server makes for player heads), one Drifter at a time so as not to flood
 * it, and the answer is saved with the Drifter. Every player's game then downloads and caches the picture itself, as for
 * a player it meets. No internet: it keeps its default skin and tries again later.
 */
public final class RealSkins {
	/** Lookups running at once. */
	private static final int MAX_IN_FLIGHT = 2;
	/** One new lookup at most this often (ticks): a thousand Drifters take a while, and Mojang's limits stay happy. */
	private static final int START_EVERY = 20;
	/** Entries from the user's list tried for one Drifter. */
	private static final int FROM_USER_LIST = 2;
	/** Accounts from the bundled list tried for one Drifter (some have no skin of their own, or are gone). */
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
	/** Accounts looked up this run: the answer (a skin, or none), by name and by UUID. */
	private static final Map<String, Found> KNOWN = new LinkedHashMap<>(256, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Found> eldest) {
			return this.size() > 4096;
		}
	};
	/** The bundled list (datapacks) and the user's list (config). */
	private static List<String> bundled = List.of();
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
		ServerLifecycleEvents.SERVER_STARTED.register(server -> loadUserList());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			QUEUE.clear();
			QUEUED.clear();
			WORN.clear();
			RESERVED.clear();
			inFlight = 0;
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

	// ----- Asking -----

	/** It would like a real skin (it hasn't one, and hasn't been told there's none for it): queued. */
	public static void want(DrifterEntity drifter) {
		if (!DrifterConfig.realSkins() || QUEUED.contains(drifter.getUUID()) || !(drifter.level() instanceof ServerLevel)) {
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

		for (int i = 0; i < FROM_BUNDLED && !bundled.isEmpty(); i++) {
			accounts.add(bundled.get(random.nextInt(bundled.size())));
		}

		if (!accounts.isEmpty()) {
			QUEUED.add(drifter.getUUID());
			QUEUE.addLast(new Request(drifter.getUUID(), accounts, false));
		}
	}

	/** This account's skin on it (a command; a name or a UUID): first in line. */
	public static void wear(DrifterEntity drifter, String account) {
		String entry = entry(account);
		if (entry == null) {
			return;
		}

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
		if (QUEUE.isEmpty() || inFlight >= MAX_IN_FLIGHT || server.getTickCount() - lastStart < START_EVERY) {
			return;
		}

		Request queued = QUEUE.pollFirst();
		lastStart = server.getTickCount();
		// The user's list: the ones someone has (or is being looked up for) since this Drifter queued are out.
		List<String> accounts = queued.forced() ? queued.accounts() : queued.accounts().stream()
			.filter(account -> !userList.contains(account) || !WORN.contains(key(account)) && !RESERVED.contains(key(account))).toList();
		Request request = new Request(queued.drifter(), accounts, queued.forced());
		for (String account : accounts) {
			if (userList.contains(account)) {
				RESERVED.add(key(account));
			}
		}

		// Asked before (for another Drifter, earlier this run): no need to ask Mojang again.
		for (String account : request.accounts()) {
			Found known;
			synchronized (KNOWN) {
				known = KNOWN.get(key(account));
			}

			if (known != null && known != Found.NONE) {
				apply(server, request, new Result(known, false));
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

				apply(server, request, error != null || result == null ? new Result(null, true) : result);
			}));
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

	private static void remember(String account, Found found) {
		synchronized (KNOWN) {
			KNOWN.put(key(account), found);
		}
	}

	private static UUID fromHex(String hex) {
		return new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16), Long.parseUnsignedLong(hex.substring(16), 16));
	}

	private static void apply(MinecraftServer server, Request request, Result result) {
		QUEUED.remove(request.drifter());
		request.accounts().forEach(account -> RESERVED.remove(key(account)));
		DrifterEntity drifter = find(server, request.drifter());
		if (drifter == null) {
			return;
		}

		if (result.found() != null) {
			WORN.add(key(result.found().name()));
			WORN.add(key(result.found().id()));
			drifter.setRealSkin(result.found());
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
