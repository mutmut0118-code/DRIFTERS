package com.dinglebob.drifter.entity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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

import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * Real player skins (user, 2026-10-20: "lets do 2 ... its just like minecraft multiplayer"): a Drifter wears the skin of
 * the real Minecraft account with its name, if there is one, else of one of a couple of other names like it. The server
 * asks Mojang (the same lookups a server makes for player heads), one Drifter at a time so as not to flood it, and the
 * answer is saved with the Drifter. Every player's game then downloads and caches the picture itself, as for a player
 * it meets. No internet (or no account): it keeps its default skin, and tries again later.
 */
public final class RealSkins {
	/** Lookups running at once. */
	private static final int MAX_IN_FLIGHT = 2;
	/** One new lookup at most this often (ticks): a thousand Drifters take a while, and Mojang's limits stay happy. */
	private static final int START_EVERY = 20;
	/** Other names it tries when no account has its own. */
	private static final int ALTERNATES = 2;
	/** After a failed lookup (no internet, Mojang busy): this long before it tries again (ticks). */
	public static final long RETRY_AFTER = 20L * 60 * 10;
	/** What Mojang allows in a name. */
	private static final Pattern ACCOUNT_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

	private static final Deque<Request> QUEUE = new ArrayDeque<>();
	private static final Set<UUID> QUEUED = new HashSet<>();
	private static int inFlight;
	private static long lastStart;
	/** Names already looked up this run: the answer (a skin, or none). */
	private static final Map<String, Found> KNOWN = new LinkedHashMap<>(256, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Found> eldest) {
			return this.size() > 2048;
		}
	};

	/** A skin found: the account's name, its textures (Mojang's base64 text), and whether it has slim arms. */
	public record Found(String name, String textures, boolean slim) {
		static final Found NONE = new Found("", "", false);
	}

	private record Request(UUID drifter, List<String> names, boolean forced) {
	}

	/** How a lookup went: a skin, no account among those names, or no answer (no internet, Mojang busy). */
	private record Result(@Nullable Found found, boolean noAnswer) {
	}

	private RealSkins() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RealSkins::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			QUEUE.clear();
			QUEUED.clear();
			inFlight = 0;
		});
	}

	/** It would like a real skin (it hasn't one, and hasn't been told there's none for it): queued. */
	public static void want(DrifterEntity drifter) {
		if (!DrifterConfig.realSkins() || QUEUED.contains(drifter.getUUID()) || !(drifter.level() instanceof ServerLevel level)) {
			return;
		}

		List<String> names = new ArrayList<>();
		String own = drifter.getName().getString();
		if (ACCOUNT_NAME.matcher(own).matches()) {
			names.add(own);
		}

		// Other names like the ones it might have had (the same ones each time for this Drifter).
		RandomSource random = RandomSource.create(drifter.getUUID().getLeastSignificantBits());
		for (int i = 0; i < ALTERNATES; i++) {
			String other = com.dinglebob.drifter.identity.Usernames.generate(random, level.getServer());
			if (ACCOUNT_NAME.matcher(other).matches()) {
				names.add(other);
			}
		}

		if (!names.isEmpty()) {
			QUEUED.add(drifter.getUUID());
			QUEUE.addLast(new Request(drifter.getUUID(), names, false));
		}
	}

	/** This account's skin on it (a command): first in line. */
	public static void wear(DrifterEntity drifter, String name) {
		QUEUED.add(drifter.getUUID());
		QUEUE.removeIf(request -> request.drifter().equals(drifter.getUUID()));
		QUEUE.addFirst(new Request(drifter.getUUID(), List.of(name), true));
	}

	public static boolean validName(String name) {
		return ACCOUNT_NAME.matcher(name).matches();
	}

	private static void tick(MinecraftServer server) {
		if (QUEUE.isEmpty() || inFlight >= MAX_IN_FLIGHT || server.getTickCount() - lastStart < START_EVERY) {
			return;
		}

		Request request = QUEUE.pollFirst();
		lastStart = server.getTickCount();
		// Asked before (another Drifter, earlier this run): no need to ask Mojang again.
		for (String name : request.names()) {
			Found known;
			synchronized (KNOWN) {
				known = KNOWN.get(name.toLowerCase(java.util.Locale.ROOT));
			}

			if (known != null && known != Found.NONE) {
				apply(server, request, new Result(known, false));
				return;
			}
		}

		inFlight++;
		CompletableFuture.supplyAsync(() -> lookUp(server, request.names()), Util.backgroundExecutor())
			.whenComplete((result, error) -> server.execute(() -> {
				inFlight--;
				if (error != null) {
					Drifter.LOGGER.debug("[Drifter] skin lookup failed", error);
				}

				apply(server, request, error != null || result == null ? new Result(null, true) : result);
			}));
	}

	/** Off the server thread: the names' accounts, then the first one's skin. */
	private static Result lookUp(MinecraftServer server, List<String> names) {
		Map<String, GameProfile> accounts = new LinkedHashMap<>();
		boolean[] noAnswer = {false};
		server.getProfileRepository().findProfilesByNames(names.toArray(String[]::new), new ProfileLookupCallback() {
			@Override
			public void onProfileLookupSucceeded(GameProfile profile) {
				accounts.put(profile.getName().toLowerCase(java.util.Locale.ROOT), profile);
			}

			@Override
			public void onProfileLookupFailed(String name, Exception exception) {
				if (!(exception instanceof ProfileNotFoundException)) {
					noAnswer[0] = true;
				}
			}
		});

		for (String name : names) {
			GameProfile account = accounts.get(name.toLowerCase(java.util.Locale.ROOT));
			if (account == null) {
				synchronized (KNOWN) {
					if (!noAnswer[0]) {
						KNOWN.put(name.toLowerCase(java.util.Locale.ROOT), Found.NONE);
					}
				}

				continue;
			}

			ProfileResult result = server.getSessionService().fetchProfile(account.getId(), false);
			if (result == null) {
				noAnswer[0] = true;
				continue;
			}

			Property textures = result.profile().getProperties().get("textures").stream().findFirst().orElse(null);
			if (textures == null || !hasSkin(textures.value())) {
				continue;
			}

			Found found = new Found(result.profile().getName(), textures.value(), slim(textures.value()));
			synchronized (KNOWN) {
				KNOWN.put(name.toLowerCase(java.util.Locale.ROOT), found);
			}

			return new Result(found, false);
		}

		return new Result(null, noAnswer[0]);
	}

	private static void apply(MinecraftServer server, Request request, Result result) {
		QUEUED.remove(request.drifter());
		DrifterEntity drifter = find(server, request.drifter());
		if (drifter == null) {
			return;
		}

		if (result.found() != null) {
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

	@Nullable
	private static JsonObject skinOf(String textures) {
		try {
			JsonObject json = JsonParser.parseString(new String(Base64.getDecoder().decode(textures), StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject all = json.getAsJsonObject("textures");
			return all == null ? null : all.getAsJsonObject("SKIN");
		} catch (RuntimeException e) {
			return null;
		}
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
